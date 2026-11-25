package focus

import (
	"encoding/json"
	"errors"
	"fmt"
	"os"
	"path/filepath"
	"strings"
	"sync"
	"time"

	"github.com/lakshyakumar90/fuck-let-me-work/internal/api"
	"github.com/robfig/cron/v3"
)

type Config struct {
	ManualActive     bool           `json:"manualActive"`
	ManualIndefinite bool           `json:"manualIndefinite"`
	ManualStartedAt  *time.Time     `json:"manualStartedAt,omitempty"`
	ManualEndsAt     *time.Time     `json:"manualEndsAt,omitempty"`
	PausedUntil      *time.Time     `json:"pausedUntil,omitempty"`
	Sites            []string       `json:"sites"`
	Apps             []string       `json:"apps"`
	Schedules        []api.Schedule `json:"schedules"`
}

type Manager struct {
	mu   sync.RWMutex
	path string
	cfg  Config
}

func NewManager(dataDir string) (*Manager, error) {
	if err := os.MkdirAll(dataDir, 0o755); err != nil {
		return nil, err
	}
	m := &Manager{
		path: filepath.Join(dataDir, "config.json"),
		cfg: Config{
			Sites: []string{"x.com", "reddit.com", "youtube.com", "instagram.com"},
			Apps:  []string{"Discord", "Slack", "Steam"},
		},
	}
	if raw, err := os.ReadFile(m.path); err == nil {
		_ = json.Unmarshal(raw, &m.cfg)
	} else {
		_ = m.saveLocked()
	}
	return m, nil
}

func (m *Manager) saveLocked() error {
	b, err := json.MarshalIndent(m.cfg, "", "  ")
	if err != nil {
		return err
	}
	return os.WriteFile(m.path, b, 0o644)
}

func (m *Manager) Start(durStr string) error {
	m.mu.Lock()
	defer m.mu.Unlock()
	now := time.Now()
	m.cfg.PausedUntil = nil
	m.cfg.ManualActive = true
	m.cfg.ManualStartedAt = &now

	durStr = strings.TrimSpace(durStr)
	if durStr == "" || durStr == "0" || strings.EqualFold(durStr, "forever") {
		m.cfg.ManualIndefinite = true
		m.cfg.ManualEndsAt = nil
	} else {
		d, err := time.ParseDuration(durStr)
		if err != nil || d <= 0 {
			return fmt.Errorf("invalid duration %q (use e.g. 25m, 50m, 2h)", durStr)
		}
		end := now.Add(d)
		m.cfg.ManualIndefinite = false
		m.cfg.ManualEndsAt = &end
	}
	return m.saveLocked()
}

func (m *Manager) Stop() error {
	m.mu.Lock()
	defer m.mu.Unlock()
	m.cfg.ManualActive = false
	m.cfg.ManualIndefinite = false
	m.cfg.ManualStartedAt = nil
	m.cfg.ManualEndsAt = nil

	// If currently inside a scheduled window, pause until that window ends.
	if active, _, end := m.activeScheduleLocked(time.Now()); active && end != nil {
		m.cfg.PausedUntil = end
	}
	return m.saveLocked()
}

func (m *Manager) Status() api.StatusResponse {
	m.mu.Lock()
	defer m.mu.Unlock()
	now := time.Now()

	if m.cfg.ManualActive && !m.cfg.ManualIndefinite && m.cfg.ManualEndsAt != nil && now.After(*m.cfg.ManualEndsAt) {
		m.cfg.ManualActive = false
		m.cfg.ManualStartedAt = nil
		m.cfg.ManualEndsAt = nil
		_ = m.saveLocked()
	}

	resp := api.StatusResponse{
		Sites:     append([]string(nil), m.cfg.Sites...),
		Apps:      append([]string(nil), m.cfg.Apps...),
		Schedules: append([]api.Schedule(nil), m.cfg.Schedules...),
		Author:    "Lakshya Kumar",
	}

	if m.cfg.ManualActive {
		resp.Active = true
		resp.Indefinite = m.cfg.ManualIndefinite
		resp.StartedAt = m.cfg.ManualStartedAt
		resp.EndsAt = m.cfg.ManualEndsAt
		resp.Source = "manual"
		return resp
	}

	if m.cfg.PausedUntil != nil && now.Before(*m.cfg.PausedUntil) {
		return resp
	}

	if ok, start, end := m.activeScheduleLocked(now); ok {
		resp.Active = true
		resp.StartedAt = start
		resp.EndsAt = end
		resp.Source = "schedule"
	}
	return resp
}

func (m *Manager) activeScheduleLocked(now time.Time) (bool, *time.Time, *time.Time) {
	parser := cron.NewParser(cron.Minute | cron.Hour | cron.Dom | cron.Month | cron.Dow)
	for _, s := range m.cfg.Schedules {
		if !s.Enabled {
			continue
		}
		dur, err := time.ParseDuration(s.Duration)
		if err != nil || dur <= 0 {
			continue
		}
		sched, err := parser.Parse(s.Cron)
		if err != nil {
			continue
		}
		probe := now.Add(-dur)
		next := sched.Next(probe)
		if !next.After(now) && next.Add(dur).After(now) {
			end := next.Add(dur)
			return true, &next, &end
		}
	}
	return false, nil, nil
}

func NormalizeDomain(raw string) string {
	s := strings.ToLower(strings.TrimSpace(raw))
	s = strings.TrimPrefix(s, "https://")
	s = strings.TrimPrefix(s, "http://")
	s = strings.TrimPrefix(s, "www.")
	if i := strings.IndexByte(s, '/'); i >= 0 {
		s = s[:i]
	}
	return s
}

func (m *Manager) AddSite(domain string) error {
	d := NormalizeDomain(domain)
	if d == "" || !strings.Contains(d, ".") {
		return errors.New("invalid domain")
	}
	m.mu.Lock()
	defer m.mu.Unlock()
	for _, existing := range m.cfg.Sites {
		if existing == d {
			return nil
		}
	}
	m.cfg.Sites = append(m.cfg.Sites, d)
	return m.saveLocked()
}

func (m *Manager) RemoveSite(domain string) error {
	d := NormalizeDomain(domain)
	m.mu.Lock()
	defer m.mu.Unlock()
	out := m.cfg.Sites[:0]
	for _, s := range m.cfg.Sites {
		if s != d {
			out = append(out, s)
		}
	}
	m.cfg.Sites = out
	return m.saveLocked()
}

func (m *Manager) AddApp(name string) error {
	n := strings.TrimSpace(name)
	if n == "" {
		return errors.New("empty app name")
	}
	m.mu.Lock()
	defer m.mu.Unlock()
	for _, existing := range m.cfg.Apps {
		if strings.EqualFold(existing, n) {
			return nil
		}
	}
	m.cfg.Apps = append(m.cfg.Apps, n)
	return m.saveLocked()
}

func (m *Manager) RemoveApp(name string) error {
	n := strings.TrimSpace(name)
	m.mu.Lock()
	defer m.mu.Unlock()
	out := m.cfg.Apps[:0]
	for _, a := range m.cfg.Apps {
		if !strings.EqualFold(a, n) {
			out = append(out, a)
		}
	}
	m.cfg.Apps = out
	return m.saveLocked()
}

func (m *Manager) AddSchedule(cronExpr, durStr, label string) (api.Schedule, error) {
	parser := cron.NewParser(cron.Minute | cron.Hour | cron.Dom | cron.Month | cron.Dow)
	if _, err := parser.Parse(cronExpr); err != nil {
		return api.Schedule{}, fmt.Errorf("invalid cron expression: %w", err)
	}
	if d, err := time.ParseDuration(durStr); err != nil || d <= 0 {
		return api.Schedule{}, fmt.Errorf("invalid duration: %s", durStr)
	}
	s := api.Schedule{
		ID:       fmt.Sprintf("sch-%d", time.Now().UnixNano()),
		Cron:     strings.TrimSpace(cronExpr),
		Duration: strings.TrimSpace(durStr),
		Label:    strings.TrimSpace(label),
		Enabled:  true,
	}
	m.mu.Lock()
	defer m.mu.Unlock()
	m.cfg.Schedules = append(m.cfg.Schedules, s)
	return s, m.saveLocked()
}

func (m *Manager) RemoveSchedule(id string) error {
	m.mu.Lock()
	defer m.mu.Unlock()
	out := m.cfg.Schedules[:0]
	for _, s := range m.cfg.Schedules {
		if s.ID != id {
			out = append(out, s)
		}
	}
	m.cfg.Schedules = out
	return m.saveLocked()
}
