package daemon

import (
	"encoding/json"
	"net/http"
	"strings"

	"github.com/lakshyakumar90/fuck-let-me-work/internal/apps"

	"github.com/lakshyakumar90/fuck-let-me-work/internal/api"
	"github.com/lakshyakumar90/fuck-let-me-work/web"
)

func (d *Daemon) routes() http.Handler {
	mux := http.NewServeMux()

	mux.HandleFunc("GET /api/status", func(w http.ResponseWriter, r *http.Request) {
		writeJSON(w, http.StatusOK, d.manager.Status())
	})

	mux.HandleFunc("POST /api/focus", d.csrf(func(w http.ResponseWriter, r *http.Request) {
		var req api.FocusRequest
		_ = json.NewDecoder(r.Body).Decode(&req)
		if err := d.manager.Start(req.Duration); err != nil {
			writeError(w, http.StatusBadRequest, err.Error())
			return
		}
		go d.tick()
		writeJSON(w, http.StatusOK, d.manager.Status())
	}))

	mux.HandleFunc("DELETE /api/focus", d.csrf(func(w http.ResponseWriter, r *http.Request) {
		if err := d.manager.Stop(); err != nil {
			writeError(w, http.StatusInternalServerError, err.Error())
			return
		}
		go d.tick()
		writeJSON(w, http.StatusOK, d.manager.Status())
	}))

	mux.HandleFunc("POST /api/sites", d.csrf(func(w http.ResponseWriter, r *http.Request) {
		var req api.SiteRequest
		if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
			writeError(w, http.StatusBadRequest, "invalid json")
			return
		}
		if err := d.manager.AddSite(req.Domain); err != nil {
			writeError(w, http.StatusBadRequest, err.Error())
			return
		}
		go d.tick()
		writeJSON(w, http.StatusOK, d.manager.Status())
	}))

	mux.HandleFunc("DELETE /api/sites/{name}", d.csrf(func(w http.ResponseWriter, r *http.Request) {
		_ = d.manager.RemoveSite(r.PathValue("name"))
		go d.tick()
		writeJSON(w, http.StatusOK, d.manager.Status())
	}))

	mux.HandleFunc("GET /api/app-choices", d.csrf(func(w http.ResponseWriter, r *http.Request) {
		items, err := apps.Choices()
		if err != nil {
			writeError(w, http.StatusServiceUnavailable, "Could not load app choices")
			return
		}
		writeJSON(w, http.StatusOK, items)
	}))

	mux.HandleFunc("POST /api/apps", d.csrf(func(w http.ResponseWriter, r *http.Request) {
		var req api.AppRequest
		if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
			writeError(w, http.StatusBadRequest, "invalid json")
			return
		}
		if err := d.manager.AddApp(req.Name); err != nil {
			writeError(w, http.StatusBadRequest, err.Error())
			return
		}
		go d.tick()
		writeJSON(w, http.StatusOK, d.manager.Status())
	}))

	mux.HandleFunc("DELETE /api/apps/{name}", d.csrf(func(w http.ResponseWriter, r *http.Request) {
		_ = d.manager.RemoveApp(r.PathValue("name"))
		writeJSON(w, http.StatusOK, d.manager.Status())
	}))

	mux.HandleFunc("POST /api/schedules", d.csrf(func(w http.ResponseWriter, r *http.Request) {
		var req api.ScheduleRequest
		if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
			writeError(w, http.StatusBadRequest, "invalid json")
			return
		}
		if _, err := d.manager.AddSchedule(req.Cron, req.Duration, req.Label); err != nil {
			writeError(w, http.StatusBadRequest, err.Error())
			return
		}
		go d.tick()
		writeJSON(w, http.StatusOK, d.manager.Status())
	}))

	mux.HandleFunc("DELETE /api/schedules/{id}", d.csrf(func(w http.ResponseWriter, r *http.Request) {
		_ = d.manager.RemoveSchedule(r.PathValue("id"))
		go d.tick()
		writeJSON(w, http.StatusOK, d.manager.Status())
	}))

	fileServer := http.FileServer(http.FS(web.FS))
	mux.HandleFunc("/", func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path == "/" || !strings.Contains(r.URL.Path, ".") {
			r.URL.Path = "/"
		}
		fileServer.ServeHTTP(w, r)
	})
	return mux
}

func (d *Daemon) csrf(next http.HandlerFunc) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		if r.Header.Get("X-FLMW") != "1" {
			writeError(w, http.StatusForbidden, "missing X-FLMW header")
			return
		}
		next(w, r)
	}
}

func writeJSON(w http.ResponseWriter, code int, v any) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(code)
	_ = json.NewEncoder(w).Encode(v)
}

func writeError(w http.ResponseWriter, code int, msg string) {
	writeJSON(w, code, map[string]string{"error": msg})
}
