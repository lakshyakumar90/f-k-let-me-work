package apps

import (
	"strings"

	"github.com/shirou/gopsutil/v4/process"
)

// TerminateBlocked scans running OS processes and terminates any whose name
// matches a configured blocked application (case-insensitive).
func TerminateBlocked(blocked []string) error {
	if len(blocked) == 0 {
		return nil
	}
	procs, err := process.Processes()
	if err != nil {
		return err
	}

	lowered := make([]string, 0, len(blocked))
	for _, b := range blocked {
		if s := strings.ToLower(strings.TrimSpace(b)); s != "" {
			lowered = append(lowered, s)
		}
	}

	for _, p := range procs {
		name, err := p.Name()
		if err != nil || name == "" {
			continue
		}
		ln := strings.ToLower(name)
		for _, target := range lowered {
			if ln == target || (!strings.HasSuffix(target, ".exe") && (strings.TrimSuffix(ln, ".exe") == target || strings.Contains(ln, target))) {
				_ = p.Terminate()
				break
			}
		}
	}
	return nil
}
