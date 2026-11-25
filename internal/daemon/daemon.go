package daemon

import (
	"context"
	"crypto/tls"
	"log"
	"net/http"
	"sync"
	"time"

	"github.com/lakshyakumar90/fuck-let-me-work/internal/apps"
	"github.com/lakshyakumar90/fuck-let-me-work/internal/certs"
	"github.com/lakshyakumar90/fuck-let-me-work/internal/focus"
	"github.com/lakshyakumar90/fuck-let-me-work/internal/hosts"
)

type Options struct {
	DataDir   string
	HostsPath string
	HTTPAddr  string
	HTTPSAddr string
}

type Daemon struct {
	opt      Options
	manager  *focus.Manager
	certPool *certs.Store
	tickMu   sync.Mutex
	lastOn   bool
	stopping bool
}

func New(opt Options) (*Daemon, error) {
	if opt.HTTPAddr == "" {
		opt.HTTPAddr = "127.0.0.1:80"
	}
	if opt.HTTPSAddr == "" {
		opt.HTTPSAddr = "127.0.0.1:443"
	}
	mgr, err := focus.NewManager(opt.DataDir)
	if err != nil {
		return nil, err
	}
	cp, err := certs.LoadOrCreate(opt.DataDir)
	if err != nil {
		return nil, err
	}
	return &Daemon{opt: opt, manager: mgr, certPool: cp, lastOn: true}, nil
}

func (d *Daemon) Run(ctx context.Context) error {
	handler := d.routes()
	httpSrv := &http.Server{Addr: d.opt.HTTPAddr, Handler: handler}
	httpsSrv := &http.Server{
		Addr:    d.opt.HTTPSAddr,
		Handler: handler,
		TLSConfig: &tls.Config{
			GetCertificate: d.certPool.GetCertificate,
			MinVersion:     tls.VersionTLS12,
		},
	}

	go func() {
		if err := httpSrv.ListenAndServe(); err != nil && err != http.ErrServerClosed {
			log.Printf("http server error: %v", err)
		}
	}()
	go func() {
		if err := httpsSrv.ListenAndServeTLS("", ""); err != nil && err != http.ErrServerClosed {
			log.Printf("https server error: %v", err)
		}
	}()

	d.tick()
	ticker := time.NewTicker(2 * time.Second)
	defer ticker.Stop()

	for {
		select {
		case <-ctx.Done():
			d.tickMu.Lock()
			d.stopping = true
			err := hosts.Apply(d.opt.HostsPath, nil)
			d.tickMu.Unlock()

			shutCtx, cancel := context.WithTimeout(context.Background(), 3*time.Second)
			defer cancel()
			_ = httpSrv.Shutdown(shutCtx)
			_ = httpsSrv.Shutdown(shutCtx)
			return err
		case <-ticker.C:
			d.tick()
		}
	}
}

func (d *Daemon) tick() {
	d.tickMu.Lock()
	defer d.tickMu.Unlock()

	if d.stopping {
		return
	}
	st := d.manager.Status()
	if st.Active {
		if err := hosts.Apply(d.opt.HostsPath, st.Sites); err != nil {
			log.Printf("apply website blocklist: %v", err)
		}
		_ = apps.TerminateBlocked(st.Apps)
		d.lastOn = true
	} else if d.lastOn {
		if err := hosts.Apply(d.opt.HostsPath, nil); err != nil {
			log.Printf("clear website blocklist: %v", err)
			return
		}
		d.lastOn = false
	}
}
