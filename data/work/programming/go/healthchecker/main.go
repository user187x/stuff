package main

import (
	"fmt"
	"net/http"
	"sync"
	"time"
)

func main() {
	endpoints := []string{
		"https://google.com",
		"https://github.com",
		"https://kubernetes.io",
		"https://this-site-does-not-exist.org",
		"https://aws.amazon.com",
	}

	// WaitGroup keeps track of how many concurrent tasks are running
	var wg sync.WaitGroup

	// Configure an HTTP client with a strict timeout
	client := http.Client{
		Timeout: 5 * time.Second,
	}

	fmt.Println("Starting concurrent health checks...")
	startTime := time.Now()

	for _, url := range endpoints {
		// Increment the counter for the WaitGroup
		wg.Add(1)

		// The 'go' keyword spins this anonymous function off into its own lightweight thread
		go func(targetUrl string) {
			// Decrement the counter when the function finishes (whether it succeeded or failed)
			defer wg.Done()

			resp, err := client.Get(targetUrl)
			if err != nil {
				fmt.Printf("[FAIL] %s - Error: %v\n", targetUrl, err)
				return
			}
			defer resp.Body.Close()

			if resp.StatusCode >= 200 && resp.StatusCode <= 299 {
				fmt.Printf("[ OK ] %s - Status: %d\n", targetUrl, resp.StatusCode)
			} else {
				fmt.Printf("[WARN] %s - Status: %d\n", targetUrl, resp.StatusCode)
			}
		}(url) // Pass the url into the closure to prevent variable shadowing
	}

	// Block the main thread until the WaitGroup counter hits zero
	wg.Wait()

	fmt.Printf("All checks completed in %v\n", time.Since(startTime))
}
