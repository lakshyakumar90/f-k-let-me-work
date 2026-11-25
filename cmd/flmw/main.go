package main

import (
	"bytes"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"os"
	"runtime"
	"strings"
	"time"

	"github.com/lakshyakumar90/fuck-let-me-work/internal/api"
	"github.com/lakshyakumar90/fuck-let-me-work/internal/hosts"
	"github.com/lakshyakumar90/fuck-let-me-work/internal/tray"
	"github.com/lakshyakumar90/fuck-let-me-work/skill"
)

var apiBase = "http://" + httpAddr()

const dashboardAddr = "127.0.0.1:8787"

var dashboardProbe = http.Client{
	Timeout: 400 * time.Millisecond,
	CheckRedirect: func(*http.Request, []*http.Request) error {
		return http.ErrUseLastResponse
	},
}

func dashboardReady(base string) bool {
	resp, err := dashboardProbe.Get(base + "/api/status")
	if err != nil {
		return false
	}
	defer resp.Body.Close()
	var status api.StatusResponse
	return resp.StatusCode == http.StatusOK &&
		json.NewDecoder(io.LimitReader(resp.Body, 1<<20)).Decode(&status) == nil &&
		status.Author == "Lakshya Kumar"
}

func currentAPIBase() string {
	if runtime.GOOS == "windows" && os.Getenv("FLMW_HTTP_PORT") == "" {
		base := "http://" + dashboardAddr
		if dashboardReady(base) {
			return base
		}
	}
	return apiBase
}

func httpAddr() string {
	if port := os.Getenv("FLMW_HTTP_PORT"); port != "" {
		return "127.0.0.1:" + port
	}
	return "127.0.0.1:80"
}

func main() {
	if len(os.Args) < 2 {
		if launchDashboard() {
			return
		}
		printUsage()
		os.Exit(1)
	}

	cmd := os.Args[1]
	args := os.Args[2:]

	switch cmd {
	case "on":
		dur := ""
		if len(args) > 0 {
			dur = args[0]
		}
		mustCall(http.MethodPost, "/api/focus", api.FocusRequest{Duration: dur}, hasFlag(args, "--json"))
	case "off":
		mustCall(http.MethodDelete, "/api/focus", nil, hasFlag(args, "--json"))
	case "status":
		mustCall(http.MethodGet, "/api/status", nil, hasFlag(args, "--json"))
	case "site":
		handleListCmd("sites", args)
	case "app":
		handleListCmd("apps", args)
	case "schedule":
		handleScheduleCmd(args)
	case "open":
		tray.OpenBrowser(currentAPIBase())
	case "tray":
		tray.Run(currentAPIBase())
	case "skill":
		fmt.Print(skill.Content)
	case "daemon":
		must(runDaemon())
	case "dashboard-daemon":
		if !launchWorker(args) {
			must(fmt.Errorf("dashboard launcher is only available on Windows"))
		}
	case "restore-hosts":
		must(hosts.Apply("", nil))
		fmt.Println("FLMW website redirects cleared.")
	case "install":
		svc, err := newService()
		must(err)
		must(svc.Install())
		must(svc.Start())
		fmt.Println("flmw service installed and started.")
	case "uninstall":
		svc, err := newService()
		must(err)
		_ = svc.Stop()
		must(svc.Uninstall())
		fmt.Println("flmw service uninstalled.")
	default:
		printUsage()
		os.Exit(1)
	}
}

func handleListCmd(resource string, args []string) {
	if len(args) == 0 || args[0] == "ls" {
		mustCall(http.MethodGet, "/api/status", nil, hasFlag(args, "--json"))
		return
	}
	switch args[0] {
	case "add":
		if len(args) < 2 {
			fmt.Fprintf(os.Stderr, "Usage: flmw %s add <value>\n", strings.TrimSuffix(resource, "s"))
			os.Exit(1)
		}
		if resource == "sites" {
			mustCall(http.MethodPost, "/api/sites", api.SiteRequest{Domain: args[1]}, hasFlag(args, "--json"))
		} else {
			mustCall(http.MethodPost, "/api/apps", api.AppRequest{Name: args[1]}, hasFlag(args, "--json"))
		}
	case "rm", "remove":
		if len(args) < 2 {
			fmt.Fprintf(os.Stderr, "Usage: flmw %s rm <value>\n", strings.TrimSuffix(resource, "s"))
			os.Exit(1)
		}
		mustCall(http.MethodDelete, "/api/"+resource+"/"+args[1], nil, hasFlag(args, "--json"))
	}
}

func handleScheduleCmd(args []string) {
	if len(args) == 0 || args[0] == "ls" {
		mustCall(http.MethodGet, "/api/status", nil, hasFlag(args, "--json"))
		return
	}
	switch args[0] {
	case "add":
		if len(args) < 3 {
			fmt.Fprintln(os.Stderr, `Usage: flmw schedule add "<cron>" <duration> [label]`)
			os.Exit(1)
		}
		label := ""
		if len(args) > 3 {
			label = strings.Join(args[3:], " ")
		}
		mustCall(http.MethodPost, "/api/schedules", api.ScheduleRequest{
			Cron:     args[1],
			Duration: args[2],
			Label:    label,
		}, hasFlag(args, "--json"))
	case "rm", "remove":
		if len(args) < 2 {
			fmt.Fprintln(os.Stderr, "Usage: flmw schedule rm <id>")
			os.Exit(1)
		}
		mustCall(http.MethodDelete, "/api/schedules/"+args[1], nil, hasFlag(args, "--json"))
	}
}

func mustCall(method, path string, payload any, asJSON bool) {
	var body io.Reader
	if payload != nil {
		b, err := json.Marshal(payload)
		must(err)
		body = bytes.NewReader(b)
	}
	req, err := http.NewRequest(method, currentAPIBase()+path, body)
	must(err)
	req.Header.Set("X-FLMW", "1")
	if payload != nil {
		req.Header.Set("Content-Type", "application/json")
	}
	resp, err := http.DefaultClient.Do(req)
	must(err)
	defer resp.Body.Close()

	raw, err := io.ReadAll(resp.Body)
	must(err)
	if resp.StatusCode >= 400 {
		fmt.Fprintf(os.Stderr, "Error (%d): %s\n", resp.StatusCode, string(raw))
		os.Exit(1)
	}
	if asJSON {
		fmt.Println(string(raw))
		return
	}
	var st api.StatusResponse
	if err := json.Unmarshal(raw, &st); err != nil {
		fmt.Println(string(raw))
		return
	}
	if st.Active {
		fmt.Printf("Focus: ON (%d sites, %d apps blocked)\n", len(st.Sites), len(st.Apps))
	} else {
		fmt.Printf("Focus: OFF (%d sites, %d apps, %d schedules configured)\n", len(st.Sites), len(st.Apps), len(st.Schedules))
	}
}

func hasFlag(args []string, flag string) bool {
	for _, a := range args {
		if a == flag {
			return true
		}
	}
	return false
}

func must(err error) {
	if err != nil {
		fmt.Fprintln(os.Stderr, "Error:", err)
		os.Exit(1)
	}
}

func printUsage() {
	fmt.Println("flmw — Focus & Distraction Blocker (Lakshya Kumar)")
	fmt.Println("Usage: flmw <on [dur]|off|status [--json]|site|app|schedule|open|tray|skill|install|uninstall|daemon|restore-hosts>")
}
