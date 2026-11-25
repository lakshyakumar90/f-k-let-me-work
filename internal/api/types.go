package api

import "time"

type Schedule struct {
	ID       string `json:"id"`
	Cron     string `json:"cron"`
	Duration string `json:"duration"`
	Label    string `json:"label,omitempty"`
	Enabled  bool   `json:"enabled"`
}

type StatusResponse struct {
	Active      bool       `json:"active"`
	Indefinite  bool       `json:"indefinite"`
	StartedAt   *time.Time `json:"startedAt,omitempty"`
	EndsAt      *time.Time `json:"endsAt,omitempty"`
	Source      string     `json:"source,omitempty"`
	Sites       []string   `json:"sites"`
	Apps        []string   `json:"apps"`
	Schedules   []Schedule `json:"schedules"`
	Author      string     `json:"author"`
}

type FocusRequest struct {
	Duration string `json:"duration"`
}

type SiteRequest struct {
	Domain string `json:"domain"`
}

type AppRequest struct {
	Name string `json:"name"`
}

type ScheduleRequest struct {
	Cron     string `json:"cron"`
	Duration string `json:"duration"`
	Label    string `json:"label,omitempty"`
}
