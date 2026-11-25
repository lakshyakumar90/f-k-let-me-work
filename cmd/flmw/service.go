package main

import (
	"context"
	"os"
	"os/signal"
	"path/filepath"
	"syscall"

	"github.com/kardianos/service"
	"github.com/lakshyakumar90/fuck-let-me-work/internal/daemon"
)

type program struct {
	cancel context.CancelFunc
	done   chan struct{}
}

func defaultDataDir() string {
	if cfg, err := os.UserConfigDir(); err == nil {
		return filepath.Join(cfg, "flmw")
	}
	return "/var/lib/flmw"
}

func (p *program) Start(s service.Service) error {
	ctx, cancel := context.WithCancel(context.Background())
	p.cancel = cancel
	p.done = make(chan struct{})
	d, err := daemon.New(daemon.Options{DataDir: defaultDataDir(), HTTPAddr: httpAddr()})
	if err != nil {
		return err
	}
	go func() {
		defer close(p.done)
		_ = d.Run(ctx)
	}()
	return nil
}

func (p *program) Stop(s service.Service) error {
	if p.cancel != nil {
		p.cancel()
		<-p.done
	}
	return nil
}

func runDaemon() error {
	if !service.Interactive() {
		svc, err := newService()
		if err != nil {
			return err
		}
		return svc.Run()
	}
	return runForegroundDaemon(httpAddr())
}

func runForegroundDaemon(addr string) error {
	// On Windows, closing the console delivers SIGTERM rather than Ctrl+C.
	ctx, stop := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer stop()
	d, err := daemon.New(daemon.Options{DataDir: defaultDataDir(), HTTPAddr: addr})
	if err != nil {
		return err
	}
	return d.Run(ctx)
}

func newService() (service.Service, error) {
	cfg := &service.Config{
		Name:        "flmw",
		DisplayName: "flmw Focus Daemon (Lakshya Kumar)",
		Description: "Blocks distracting websites and apps during focus sessions.",
		Arguments:   []string{"daemon"},
	}
	return service.New(&program{}, cfg)
}
