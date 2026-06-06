package pki

import (
	"crypto/rand"
	"crypto/rsa"
	"crypto/x509"
	"crypto/x509/pkix"
	"encoding/pem"
	"fmt"
	"math/big"
	"os"
	"path/filepath"
	"time"
)

// GeneratePKI is exported (starts with a capital letter). 
// Go functions frequently return (result, error) tuples. Error handling is explicit.
func GeneratePKI(domain, outDir string) error {
	// os.MkdirAll is the equivalent of `mkdir -p`
	if err := os.MkdirAll(outDir, 0755); err != nil {
		return fmt.Errorf("failed to create directory: %w", err) // %w wraps the error for stack tracing
	}

	// 1. Generate Root CA
	caCert, caPrivKey, err := generateCA(domain, outDir)
	if err != nil {
		return err
	}

	// 2. Generate Server Certificate
	err = generateCert(domain, "Cert", domain, []string{domain}, caCert, caPrivKey, outDir, "server")
	if err != nil {
		return err
	}

	// 3. Generate Wildcard Certificate
	err = generateCert("*. "+domain, "Wildcard Cert", "*."+domain, []string{"*." + domain, domain}, caCert, caPrivKey, outDir, "server-wildcard")
	if err != nil {
		return err
	}

	// 4. Generate Client Certificate (mTLS)
	err = generateCert("client."+domain, "Client", "client."+domain, nil, caCert, caPrivKey, outDir, "client")
	if err != nil {
		return err
	}

	return nil
}

// generateCA is private to this package.
func generateCA(domain, outDir string) (*x509.Certificate, *rsa.PrivateKey, error) {
	// Generate RSA 4096 Key. `:=` is the short variable declaration operator (infers type).
	priv, err := rsa.GenerateKey(rand.Reader, 4096)
	if err != nil {
		return nil, nil, err
	}

	// Struct instantiation. Notice the trailing commas; they are strictly required in Go.
	template := x509.Certificate{
		SerialNumber: big.NewInt(1),
		Subject: pkix.Name{
			Organization: []string{"XXX Root CA"},
			CommonName:   domain + " Root CA",
		},
		NotBefore:             time.Now(),
		NotAfter:              time.Now().AddDate(1, 0, 0), // 365 days
		IsCA:                  true,
		ExtKeyUsage:           []x509.ExtKeyUsage{x509.ExtKeyUsageClientAuth, x509.ExtKeyUsageServerAuth},
		KeyUsage:              x509.KeyUsageDigitalSignature | x509.KeyUsageCertSign,
		BasicConstraintsValid: true,
	}

	derBytes, err := x509.CreateCertificate(rand.Reader, &template, &template, &priv.PublicKey, priv)
	if err != nil {
		return nil, nil, err
	}

	// Defer ensures the file is closed when the function exits, even if it panics. 
	// This is Go's standard resource management pattern.
	certOut, _ := os.Create(filepath.Join(outDir, "ca-cert.pem"))
	defer certOut.Close()
	pem.Encode(certOut, &pem.Block{Type: "CERTIFICATE", Bytes: derBytes})

	keyOut, _ := os.Create(filepath.Join(outDir, "ca.key"))
	defer keyOut.Close()
	pem.Encode(keyOut, &pem.Block{Type: "RSA PRIVATE KEY", Bytes: x509.MarshalPKCS1PrivateKey(priv)})

	return &template, priv, nil
}

func generateCert(cn, org, dns string, sans []string, caCert *x509.Certificate, caKey *rsa.PrivateKey, outDir, name string) error {
	priv, err := rsa.GenerateKey(rand.Reader, 4096)
	if err != nil {
		return err
	}

	// Generate a random serial number
	serialNumberLimit := new(big.Int).Lsh(big.NewInt(1), 128)
	serialNumber, _ := rand.Int(rand.Reader, serialNumberLimit)

	template := x509.Certificate{
		SerialNumber: serialNumber,
		Subject: pkix.Name{
			Organization: []string{"XXX " + org},
			CommonName:   cn,
		},
		NotBefore:   time.Now(),
		NotAfter:    time.Now().AddDate(1, 0, 0),
		KeyUsage:    x509.KeyUsageDigitalSignature | x509.KeyUsageKeyEncipherment,
		ExtKeyUsage: []x509.ExtKeyUsage{x509.ExtKeyUsageServerAuth, x509.ExtKeyUsageClientAuth},
	}

	if sans != nil {
		template.DNSNames = sans
	}

	derBytes, err := x509.CreateCertificate(rand.Reader, &template, caCert, &priv.PublicKey, caKey)
	if err != nil {
		return err
	}

	certOut, _ := os.Create(filepath.Join(outDir, name+".crt"))
	defer certOut.Close()
	pem.Encode(certOut, &pem.Block{Type: "CERTIFICATE", Bytes: derBytes})

	keyOut, _ := os.Create(filepath.Join(outDir, name+".key"))
	defer keyOut.Close()
	pem.Encode(keyOut, &pem.Block{Type: "RSA PRIVATE KEY", Bytes: x509.MarshalPKCS1PrivateKey(priv)})

	return nil
}
