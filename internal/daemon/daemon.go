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
	return &Daemon{opt: opt, manager: mgr, certPool: cp}, nil
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
			shutCtx, cancel := context.WithTimeout(context.Background(), 3*time.Second)
			defer cancel()
			_ = httpSrv.Shutdown(shutCtx)
			_ = httpsSrv.Shutdown(shutCtx)
			_ = hosts.Apply(d.opt.HostsPath, nil)
			return nil
		case <-ticker.C:
			d.tick()
		}
	}
}

func (d *Daemon) tick() {
	d.tickMu.Lock()
	defer d.tickMu.Unlock()

	st := d.manager.Status()
	if st.Active {
		_ = hosts.Apply(d.opt.HostsPath, st.Sites)
		_ = apps.TerminateBlocked(st.Apps)
		d.lastOn = true
	} else if d.lastOn {
		_ = hosts.Apply(d.opt.HostsPath, nil)
		d.lastOn = false
	}
}
