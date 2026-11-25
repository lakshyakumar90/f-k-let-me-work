package main

import (
	"errors"
	"fmt"
	"net"
	"os"
	"strconv"
	"time"

	"golang.org/x/sys/windows"
)

func launchDashboard() bool {
	// Explorer creates a temporary console for CLI executables. Detach only
	// in launcher mode so the regular commands keep their terminal output.
	_, _, _ = windows.NewLazySystemDLL("kernel32.dll").NewProc("FreeConsole").Call()
	if err := openOrStartDashboard(); err != nil && !errors.Is(err, windows.ERROR_CANCELLED) {
		_, _ = windows.MessageBox(0, wide(err.Error()), wide("flmw"), windows.MB_OK|windows.MB_ICONERROR)
	}
	return true
}

func openOrStartDashboard() error {
	preferred := httpAddr()
	if os.Getenv("FLMW_HTTP_PORT") == "" {
		preferred = dashboardAddr
	}
	_, port, err := net.SplitHostPort(preferred)
	portNumber, parseErr := strconv.ParseUint(port, 10, 16)
	if err != nil || parseErr != nil || portNumber == 0 {
		return errors.New("FLMW_HTTP_PORT must be a port number from 1 to 65535")
	}
	port = strconv.FormatUint(portNumber, 10)
	base := "http://127.0.0.1:" + port
	if dashboardReady(base) {
		return openDashboard(base)
	}
	if os.Getenv("FLMW_HTTP_PORT") == "" && dashboardReady(apiBase) {
		return openDashboard(apiBase)
	}

	exe, err := os.Executable()
	if err != nil {
		return err
	}
	verb := "runas"
	if windows.GetCurrentProcessToken().IsElevated() {
		verb = "open"
	}
	if err := windows.ShellExecute(0, wide(verb), wide(exe), wide("dashboard-daemon "+port), nil, windows.SW_HIDE); err != nil {
		return fmt.Errorf("could not start the FLMW background process: %w", err)
	}

	for deadline := time.Now().Add(12 * time.Second); time.Now().Before(deadline); {
		if dashboardReady(base) {
			return openDashboard(base)
		}
		time.Sleep(200 * time.Millisecond)
	}
	return fmt.Errorf("FLMW did not start at %s. Check whether another app is using that port", base)
}

func openDashboard(base string) error {
	return windows.ShellExecute(0, wide("open"), wide(base+"/"), nil, nil, windows.SW_SHOWNORMAL)
}

func launchWorker(args []string) bool {
	if !windows.GetCurrentProcessToken().IsElevated() {
		must(errors.New("the dashboard background process requires administrator access"))
	}
	if len(args) != 1 {
		must(errors.New("missing dashboard port"))
	}
	port, err := strconv.ParseUint(args[0], 10, 16)
	if err != nil || port == 0 {
		must(errors.New("invalid dashboard port"))
	}
	must(runForegroundDaemon("127.0.0.1:" + strconv.FormatUint(port, 10)))
	return true
}

func wide(s string) *uint16 {
	return windows.StringToUTF16Ptr(s)
}
