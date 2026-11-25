package hosts

import (
	"fmt"
	"os"
	"os/exec"
	"runtime"
	"strings"
)

const (
	beginMarker = "# BEGIN FLMW MANAGED BLOCK (Lakshya Kumar)"
	endMarker   = "# END FLMW MANAGED BLOCK"
)

func DefaultPath() string {
	if runtime.GOOS == "windows" {
		return `C:\Windows\System32\drivers\etc\hosts`
	}
	return "/etc/hosts"
}

func Apply(path string, domains []string) error {
	if path == "" {
		path = DefaultPath()
	}
	raw, err := os.ReadFile(path)
	if err != nil {
		return err
	}

	cleaned := stripManagedBlock(string(raw))
	if len(domains) == 0 {
		if cleaned == string(raw) {
			return nil
		}
		return writeAndFlush(path, cleaned)
	}
	var b strings.Builder
	b.WriteString(strings.TrimRight(cleaned, "\r\n"))
	b.WriteString("\n")

	if len(domains) > 0 {
		b.WriteString("\n" + beginMarker + "\n")
		for _, d := range domains {
			d = strings.TrimSpace(d)
			if d == "" {
				continue
			}
			b.WriteString(fmt.Sprintf("127.0.0.1 %s www.%s\n", d, d))
			b.WriteString(fmt.Sprintf("::1 %s www.%s\n", d, d))
		}
		b.WriteString(endMarker + "\n")
	}

	if b.String() == string(raw) {
		return nil
	}
	return writeAndFlush(path, b.String())
}

func writeAndFlush(path, content string) error {
	if err := os.WriteFile(path, []byte(content), 0o644); err != nil {
		return err
	}
	FlushDNS()
	return nil
}

func stripManagedBlock(content string) string {
	lines := strings.Split(content, "\n")
	var out []string
	inBlock := false
	for _, line := range lines {
		trimmed := strings.TrimSpace(line)
		if trimmed == beginMarker {
			inBlock = true
			continue
		}
		if trimmed == endMarker {
			inBlock = false
			continue
		}
		if !inBlock {
			out = append(out, line)
		}
	}
	return strings.Join(out, "\n")
}

func FlushDNS() {
	switch runtime.GOOS {
	case "darwin":
		_ = exec.Command("dscacheutil", "-flushcache").Run()
		_ = exec.Command("killall", "-HUP", "mDNSResponder").Run()
	case "linux":
		_ = exec.Command("resolvectl", "flush-caches").Run()
	case "windows":
		_ = exec.Command("ipconfig", "/flushdns").Run()
	}
}
