package apps

import (
	"context"
	"encoding/json"
	"os/exec"
	"path/filepath"
	"runtime"
	"sort"
	"strings"
	"time"

	"github.com/shirou/gopsutil/v4/process"
)

type Choice struct {
	Name  string `json:"name"`
	Label string `json:"label"`
}

func Choices() ([]Choice, error) {
	items := make([]Choice, 0)
	if runtime.GOOS == "windows" {
		ctx, cancel := context.WithTimeout(context.Background(), 12*time.Second)
		defer cancel()
		const script = `
[Console]::OutputEncoding = [Text.Encoding]::UTF8
$roots = @([Environment]::GetFolderPath('Programs'), [Environment]::GetFolderPath('CommonPrograms'))
$shell = New-Object -ComObject WScript.Shell
$apps = @(foreach ($root in $roots) {
    if ($root) {
        Get-ChildItem -LiteralPath $root -Filter *.lnk -File -Recurse -ErrorAction SilentlyContinue | ForEach-Object {
            $target = $shell.CreateShortcut($_.FullName).TargetPath
            if ($target -and [IO.Path]::GetExtension($target) -ieq '.exe' -and (Test-Path -LiteralPath $target -PathType Leaf)) {
                @{ name = [IO.Path]::GetFileName($target); label = $_.BaseName }
            }
        }
    }
})
$apps += @(Get-Process | Where-Object MainWindowHandle -ne 0 | ForEach-Object {
    if ($_.Path) {
        @{ name = [IO.Path]::GetFileName($_.Path); label = $_.ProcessName }
    }
})
ConvertTo-Json -InputObject $apps -Compress
`
		raw, err := exec.CommandContext(ctx, "powershell.exe", "-NoProfile", "-NonInteractive", "-Command", script).Output()
		if err != nil {
			return nil, err
		}
		if err := json.Unmarshal(raw, &items); err != nil {
			return nil, err
		}
	} else {
		procs, err := process.Processes()
		if err != nil {
			return nil, err
		}
		for _, proc := range procs {
			if name, err := proc.Name(); err == nil && name != "" {
				items = append(items, Choice{Name: filepath.Base(name), Label: name})
			}
		}
	}

	sort.Slice(items, func(i, j int) bool {
		a, b := strings.ToLower(items[i].Label), strings.ToLower(items[j].Label)
		if a == b {
			return items[i].Name < items[j].Name
		}
		return a < b
	})
	seen := make(map[Choice]bool)
	out := make([]Choice, 0, len(items))
	for _, item := range items {
		key := Choice{Name: strings.ToLower(item.Name), Label: strings.ToLower(item.Label)}
		if !seen[key] {
			out = append(out, item)
			seen[key] = true
		}
	}
	return out, nil
}
