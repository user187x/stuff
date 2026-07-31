package main

import (
	"crypto/rand"
	"crypto/rsa"
	"crypto/tls"
	"crypto/x509"
	"crypto/x509/pkix"
	"encoding/json"
	"encoding/pem"
	"fmt"
	"io"
	"math/big"
	"net"
	"net/http"
	"os"
	"time"
)

// ResponseData is the JSON structure our server will send back
type ResponseData struct {
	Message   string `json:"message"`
	Status    string `json:"status"`
	Timestamp string `json:"timestamp"`
}

func main() {
	// 1. Generate temporary TLS certificates for the HTTPS server
	generateSelfSignedCert("cert.pem", "key.pem")
	defer os.Remove("cert.pem") // Clean up after the script ends
	defer os.Remove("key.pem")

	// 2. Define the request handler for both servers
	mux := http.NewServeMux()
	mux.HandleFunc("/api/process", func(w http.ResponseWriter, r *http.Request) {
		// Log the incoming request
		fmt.Printf("[SERVER] Received %s request on %s\n", r.Method, r.URL.Path)

		// Process the request and build a response
		response := ResponseData{
			Message:   "Request processed successfully over " + r.TLS.ServerName, // Will panic if HTTP, handled carefully below
			Status:    "success",
			Timestamp: time.Now().Format(time.RFC3339),
		}

		if r.TLS == nil {
			response.Message = "Request processed successfully over HTTP"
		} else {
			response.Message = "Request processed successfully over HTTPS"
		}

		// Send the response
		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(http.StatusOK)
		json.NewEncoder(w).Encode(response)
	})

	// 3. Start the HTTP server in the background
	go func() {
		fmt.Println("[HTTP] Starting server on :8080...")
		if err := http.ListenAndServe(":8080", mux); err != nil {
			fmt.Printf("[HTTP] Error: %v\n", err)
		}
	}()

	// 4. Start the HTTPS server in the background
	go func() {
		fmt.Println("[HTTPS] Starting secure server on :8443...")
		if err := http.ListenAndServeTLS(":8443", "cert.pem", "key.pem", mux); err != nil {
			fmt.Printf("[HTTPS] Error: %v\n", err)
		}
	}()

	// Give the servers a second to spin up
	time.Sleep(1 * time.Second)
	fmt.Println("\n--------------------------------------------------")

	// 5. Create an HTTP Client to send a request to our HTTPS server
	// We must configure it to skip certificate verification since we are using a self-signed cert
	customTransport := http.DefaultTransport.(*http.Transport).Clone()
	customTransport.TLSClientConfig = &tls.Config{InsecureSkipVerify: true}

	client := &http.Client{
		Transport: customTransport,
		Timeout:   5 * time.Second,
	}

	// 6. Send the request
	fmt.Println("[CLIENT] Sending GET request to https://127.0.0.1:8443/api/process ...")
	resp, err := client.Get("https://127.0.0.1:8443/api/process")
	if err != nil {
		fmt.Printf("[CLIENT] Request failed: %v\n", err)
		return
	}
	defer resp.Body.Close()

	// 7. Read and process the response
	bodyBytes, err := io.ReadAll(resp.Body)
	if err != nil {
		fmt.Printf("[CLIENT] Failed to read response body: %v\n", err)
		return
	}

	fmt.Printf("[CLIENT] Received Response (Status: %s):\n", resp.Status)

	// Pretty print the JSON we got back
	var parsedResponse ResponseData
	json.Unmarshal(bodyBytes, &parsedResponse)
	prettyJSON, _ := json.MarshalIndent(parsedResponse, "", "  ")
	fmt.Println(string(prettyJSON))

	fmt.Println("--------------------------------------------------")
	fmt.Println("Exiting application...")
}

// generateSelfSignedCert is a DevOps helper function to dynamically generate TLS certs
func generateSelfSignedCert(certFile, keyFile string) {
	priv, _ := rsa.GenerateKey(rand.Reader, 2048)

	template := x509.Certificate{
		SerialNumber: big.NewInt(1),
		Subject: pkix.Name{
			Organization: []string{"Localhost Testing"},
		},
		NotBefore:             time.Now(),
		NotAfter:              time.Now().Add(1 * time.Hour),
		KeyUsage:              x509.KeyUsageKeyEncipherment | x509.KeyUsageDigitalSignature,
		ExtKeyUsage:           []x509.ExtKeyUsage{x509.ExtKeyUsageServerAuth},
		BasicConstraintsValid: true,
		IPAddresses:           []net.IP{net.ParseIP("127.0.0.1")},
	}

	certBytes, _ := x509.CreateCertificate(rand.Reader, &template, &template, &priv.PublicKey, priv)

	// Write Certificate
	certOut, _ := os.Create(certFile)
	pem.Encode(certOut, &pem.Block{Type: "CERTIFICATE", Bytes: certBytes})
	certOut.Close()

	// Write Key
	keyOut, _ := os.Create(keyFile)
	pem.Encode(keyOut, &pem.Block{Type: "RSA PRIVATE KEY", Bytes: x509.MarshalPKCS1PrivateKey(priv)})
	keyOut.Close()
}
