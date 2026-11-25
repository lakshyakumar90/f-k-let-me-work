package certs

import (
	"crypto/ecdsa"
	"crypto/elliptic"
	"crypto/rand"
	"crypto/tls"
	"crypto/x509"
	"crypto/x509/pkix"
	"encoding/pem"
	"math/big"
	"os"
	"path/filepath"
	"sync"
	"time"
)

type Store struct {
	dir    string
	caCert *x509.Certificate
	caKey  *ecdsa.PrivateKey
	mu     sync.Mutex
	cache  map[string]*tls.Certificate
}

func LoadOrCreate(dataDir string) (*Store, error) {
	dir := filepath.Join(dataDir, "certs")
	if err := os.MkdirAll(dir, 0o700); err != nil {
		return nil, err
	}
	certPath := filepath.Join(dir, "flmw-ca.crt")
	keyPath := filepath.Join(dir, "flmw-ca.key")

	s := &Store{dir: dir, cache: make(map[string]*tls.Certificate)}
	if certPEM, err := os.ReadFile(certPath); err == nil {
		if keyPEM, err2 := os.ReadFile(keyPath); err2 == nil {
			cBlock, _ := pem.Decode(certPEM)
			kBlock, _ := pem.Decode(keyPEM)
			if cBlock != nil && kBlock != nil {
				if cert, err3 := x509.ParseCertificate(cBlock.Bytes); err3 == nil {
					if key, err4 := x509.ParseECPrivateKey(kBlock.Bytes); err4 == nil {
						s.caCert = cert
						s.caKey = key
						return s, nil
					}
				}
			}
		}
	}

	key, err := ecdsa.GenerateKey(elliptic.P256(), rand.Reader)
	if err != nil {
		return nil, err
	}
	serial, _ := rand.Int(rand.Reader, new(big.Int).Lsh(big.NewInt(1), 128))
	tmpl := &x509.Certificate{
		SerialNumber: serial,
		Subject: pkix.Name{
			CommonName:   "flmw Local Root CA (Lakshya Kumar)",
			Organization: []string{"flmw"},
		},
		NotBefore:             time.Now().Add(-time.Hour),
		NotAfter:              time.Now().AddDate(10, 0, 0),
		KeyUsage:              x509.KeyUsageCertSign | x509.KeyUsageCRLSign,
		BasicConstraintsValid: true,
		IsCA:                  true,
	}
	der, err := x509.CreateCertificate(rand.Reader, tmpl, tmpl, &key.PublicKey, key)
	if err != nil {
		return nil, err
	}
	cert, err := x509.ParseCertificate(der)
	if err != nil {
		return nil, err
	}
	keyDER, err := x509.MarshalECPrivateKey(key)
	if err != nil {
		return nil, err
	}
	_ = os.WriteFile(certPath, pem.EncodeToMemory(&pem.Block{Type: "CERTIFICATE", Bytes: der}), 0o644)
	_ = os.WriteFile(keyPath, pem.EncodeToMemory(&pem.Block{Type: "EC PRIVATE KEY", Bytes: keyDER}), 0o600)

	s.caCert = cert
	s.caKey = key
	return s, nil
}

func (s *Store) CAPath() string {
	return filepath.Join(s.dir, "flmw-ca.crt")
}

func (s *Store) GetCertificate(hello *tls.ClientHelloInfo) (*tls.Certificate, error) {
	host := hello.ServerName
	if host == "" {
		host = "localhost"
	}
	s.mu.Lock()
	defer s.mu.Unlock()
	if c, ok := s.cache[host]; ok {
		return c, nil
	}

	key, err := ecdsa.GenerateKey(elliptic.P256(), rand.Reader)
	if err != nil {
		return nil, err
	}
	serial, _ := rand.Int(rand.Reader, new(big.Int).Lsh(big.NewInt(1), 128))
	tmpl := &x509.Certificate{
		SerialNumber: serial,
		Subject:      pkix.Name{CommonName: host},
		DNSNames:     []string{host, "*." + host},
		NotBefore:    time.Now().Add(-time.Hour),
		NotAfter:     time.Now().AddDate(1, 0, 0),
		KeyUsage:     x509.KeyUsageDigitalSignature,
		ExtKeyUsage:  []x509.ExtKeyUsage{x509.ExtKeyUsageServerAuth},
	}
	der, err := x509.CreateCertificate(rand.Reader, tmpl, s.caCert, &key.PublicKey, s.caKey)
	if err != nil {
		return nil, err
	}
	tlsCert := &tls.Certificate{
		Certificate: [][]byte{der},
		PrivateKey:  key,
	}
	s.cache[host] = tlsCert
	return tlsCert, nil
}
