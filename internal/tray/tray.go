package tray

import (
	"bytes"
	"encoding/json"
	"fmt"
	"net/http"
	"os/exec"
	"runtime"
	"time"

	"fyne.io/systray"
	"github.com/lakshyakumar90/fuck-let-me-work/internal/api"
)

const baseURL = "http://127.0.0.1"

func Run() {
	systray.Run(onReady, func() {})
}

func onReady() {
	systray.SetTitle("flmw")
	systray.SetTooltip("flmw — Focus Blocker (Lakshya Kumar)")

	mStatus := systray.AddMenuItem("Focus: checking...", "Current focus state")
	mStatus.Disable()
	systray.AddSeparator()

	m25 := systray.AddMenuItem("Start 25m", "Focus for 25 minutes")
	m50 := systray.AddMenuItem("Start 50m", "Focus for 50 minutes")
	m2h := systray.AddMenuItem("Start 2h", "Focus for 2 hours")
	mInf := systray.AddMenuItem("Until I stop", "Focus indefinitely")
	systray.AddSeparator()

	mStop := systray.AddMenuItem("Stop focus", "End current focus session")
	mOpen := systray.AddMenuItem("Open control panel", "Open http://127.0.0.1")
	systray.AddSeparator()
	mQuit := systray.AddMenuItem("Quit tray", "Close tray indicator")

	go func() {
		ticker := time.NewTicker(3 * time.Second)
		defer ticker.Stop()
		for {
			if st, err := fetchStatus(); err == nil {
				if st.Active {
					systray.SetTitle("● flmw")
					mStatus.SetTitle("Focus: Active")
				} else {
					systray.SetTitle("○ flmw")
					mStatus.SetTitle("Focus: Off")
				}
			}
			<-ticker.C
		}
	}()

	go func() {
		for {
			select {
			case <-m25.ClickedCh:
				postFocus("25m")
			case <-m50.ClickedCh:
				postFocus("50m")
			case <-m2h.ClickedCh:
				postFocus("2h")
			case <-mInf.ClickedCh:
				postFocus("")
			case <-mStop.ClickedCh:
				stopFocus()
			case <-mOpen.ClickedCh:
				OpenBrowser(baseURL)
			case <-mQuit.ClickedCh:
				systray.Quit()
				return
			}
		}
	}()
}

func fetchStatus() (*api.StatusResponse, error) {
	resp, err := http.Get(baseURL + "/api/status")
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()
	var st api.StatusResponse
	return &st, json.NewDecoder(resp.Body).Decode(&st)
}

func postFocus(dur string) {
	body, _ := json.Marshal(api.FocusRequest{Duration: dur})
	req, _ := http.NewRequest(http.MethodPost, baseURL+"/api/focus", bytes.NewReader(body))
	req.Header.Set("X-FLMW", "1")
	req.Header.Set("Content-Type", "application/json")
	_, _ = http.DefaultClient.Do(req)
}

func stopFocus() {
	req, _ := http.NewRequest(http.MethodDelete, baseURL+"/api/focus", nil)
	req.Header.Set("X-FLMW", "1")
	_, _ = http.DefaultClient.Do(req)
}

func OpenBrowser(url string) {
	var cmd *exec.Cmd
	switch runtime.GOOS {
	case "darwin":
		cmd = exec.Command("open", url)
	case "windows":
		cmd = exec.Command("rundll32", "url.dll,FileProtocolHandler", url)
	default:
		cmd = exec.Command("xdg-open", url)
	}
	if err := cmd.Start(); err != nil {
		fmt.Printf("Open %s in your browser\n", url)
	}
}
