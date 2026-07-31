package main

import (
	"crypto/x509"
	"crypto/x509/pkix"
	"encoding/json"
	"encoding/pem"
	"fmt"
	"net/http"
	"net/url"
	"strings"
)

type UserIdentity struct {
	CommonName string   `json:"common_name"`
	DN         string   `json:"distinguished_name"`
	Emails     []string `json:"emails"`
	Issuer     string   `json:"issuer"`
}

// buildCleanDN maps X.509 OIDs to human-readable labels
func buildCleanDN(name pkix.Name) string {
	var parts []string
	// Iterate through the raw names to build a clean string
	for _, n := range name.Names {
		val := fmt.Sprintf("%v", n.Value)
		switch n.Type.String() {
		case "2.5.4.3":
			parts = append(parts, "CN="+val)
		case "2.5.4.11":
			parts = append(parts, "OU="+val)
		case "2.5.4.10":
			parts = append(parts, "O="+val)
		case "2.5.4.7":
			parts = append(parts, "L="+val)
		case "2.5.4.8":
			parts = append(parts, "ST="+val)
		case "2.5.4.6":
			parts = append(parts, "C="+val)
		case "1.2.840.113549.1.9.1":
			parts = append(parts, "EMAIL="+val)
		default:
			parts = append(parts, n.Type.String()+"="+val)
		}
	}
	return strings.Join(parts, ", ")
}

func main() {
	http.HandleFunc("/whoami", identityHandler)
	fmt.Println("Backend service listening on :8080...")
	if err := http.ListenAndServe(":8080", nil); err != nil {
		fmt.Printf("Server failed: %v\n", err)
	}
}

func identityHandler(w http.ResponseWriter, r *http.Request) {
	certHeader := r.Header.Get("X-Forwarded-Tls-Client-Cert")
	if certHeader == "" {
		http.Error(w, "Missing header", http.StatusUnauthorized)
		return
	}

	rawPEM, err := url.QueryUnescape(certHeader)
	if err != nil {
		http.Error(w, "Failed to URL-decode", http.StatusBadRequest)
		return
	}

	block, _ := pem.Decode([]byte(rawPEM))
	if block == nil {
		http.Error(w, "Failed to parse PEM", http.StatusBadRequest)
		return
	}

	cert, err := x509.ParseCertificate(block.Bytes)
	if err != nil {
		http.Error(w, "Failed to parse x509", http.StatusBadRequest)
		return
	}

	identity := UserIdentity{
		CommonName: cert.Subject.CommonName,
		DN:         buildCleanDN(cert.Subject), // Use our new clean formatter
		Emails:     cert.EmailAddresses,
		Issuer:     buildCleanDN(cert.Issuer),  // Use it for the issuer too
	}

	// Legacy Email Fallback
	if len(identity.Emails) == 0 {
		for _, name := range cert.Subject.Names {
			if name.Type.String() == "1.2.840.113549.1.9.1" {
				if emailStr, ok := name.Value.(string); ok {
					identity.Emails = append(identity.Emails, emailStr)
				}
			}
		}
	}
	if identity.Emails == nil {
		identity.Emails = []string{}
	}

	prettyJSON, _ := json.MarshalIndent(identity, "", "  ")

	w.Header().Set("Content-Type", "text/plain")
	fmt.Fprintf(w, "common name: %s\n", identity.CommonName)
	fmt.Fprintf(w, "distinguished name: %s\n", identity.DN)
	fmt.Fprintf(w, "email: %s\n", strings.Join(identity.Emails, ", "))
	fmt.Fprintf(w, "Issuer: %s\n\n", identity.Issuer)
	fmt.Fprintf(w, "%s\n", string(prettyJSON))
}
