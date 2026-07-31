package main

import (
	"encoding/json"
	"fmt"
	"os"
	"os/exec"
	"sync"
	"time"
)

// --- Minimal Structs for JSON Parsing ---

type NodeList struct {
	Items []struct {
		Metadata struct{ Name string `json:"name"` } `json:"metadata"`
		Status   struct {
			Conditions []struct {
				Type   string `json:"type"`
				Status string `json:"status"`
			} `json:"conditions"`
		} `json:"status"`
	} `json:"items"`
}

type DeploymentList struct {
	Items []struct {
		Metadata struct {
			Name      string `json:"name"`
			Namespace string `json:"namespace"`
		} `json:"metadata"`
		Status struct {
			Replicas          int `json:"replicas"`
			AvailableReplicas int `json:"availableReplicas"`
		} `json:"status"`
	} `json:"items"`
}

type PodList struct {
	Items []struct {
		Metadata struct {
			Name      string `json:"name"`
			Namespace string `json:"namespace"`
		} `json:"metadata"`
		Status struct {
			Phase string `json:"phase"`
		} `json:"status"`
	} `json:"items"`
}

// --- Report Structure ---

type HealthReport struct {
	mu                   sync.Mutex // Protects the slices during concurrent writes
	NotReadyNodes        []string
	DegradedDeployments  []string
	FailingPods          []string
}

func main() {
	fmt.Println("Starting Cluster Health Audit...")
	startTime := time.Now()

	var wg sync.WaitGroup
	report := &HealthReport{}

	// Task 1: Check Node Readiness
	wg.Add(1)
	go func() {
		defer wg.Done()
		out, err := exec.Command("kubectl", "get", "nodes", "-o", "json").Output()
		if err != nil {
			return
		}

		var nodes NodeList
		json.Unmarshal(out, &nodes)

		for _, node := range nodes.Items {
			isReady := false
			for _, cond := range node.Status.Conditions {
				if cond.Type == "Ready" && cond.Status == "True" {
					isReady = true
					break
				}
			}
			if !isReady {
				report.mu.Lock()
				report.NotReadyNodes = append(report.NotReadyNodes, node.Metadata.Name)
				report.mu.Unlock()
			}
		}
	}()

	// Task 2: Check for Deployments missing available replicas
	wg.Add(1)
	go func() {
		defer wg.Done()
		out, err := exec.Command("kubectl", "get", "deployments", "-A", "-o", "json").Output()
		if err != nil {
			return
		}

		var deps DeploymentList
		json.Unmarshal(out, &deps)

		for _, dep := range deps.Items {
			// If it expects replicas but none are available, flag it
			if dep.Status.Replicas > 0 && dep.Status.AvailableReplicas == 0 {
				issue := fmt.Sprintf("%s/%s", dep.Metadata.Namespace, dep.Metadata.Name)
				report.mu.Lock()
				report.DegradedDeployments = append(report.DegradedDeployments, issue)
				report.mu.Unlock()
			}
		}
	}()

	// Task 3: Check for Pods stuck in non-running states
	wg.Add(1)
	go func() {
		defer wg.Done()
		out, err := exec.Command("kubectl", "get", "pods", "-A", "-o", "json").Output()
		if err != nil {
			return
		}

		var pods PodList
		json.Unmarshal(out, &pods)

		for _, pod := range pods.Items {
			// Ignore pods that are running or have successfully completed
			if pod.Status.Phase != "Running" && pod.Status.Phase != "Succeeded" {
				issue := fmt.Sprintf("%s/%s (State: %s)", pod.Metadata.Namespace, pod.Metadata.Name, pod.Status.Phase)
				report.mu.Lock()
				report.FailingPods = append(report.FailingPods, issue)
				report.mu.Unlock()
			}
		}
	}()

	// Wait for all three kubectl commands to finish
	wg.Wait()

	// Print the final report
	fmt.Printf("\n--- Audit Completed in %v ---\n\n", time.Since(startTime))

	fmt.Println("### NODE HEALTH ###")
	if len(report.NotReadyNodes) == 0 {
		fmt.Println("✅ All nodes are Ready.")
	} else {
		for _, n := range report.NotReadyNodes {
			fmt.Printf("❌ Node Not Ready: %s\n", n)
		}
	}

	fmt.Println("\n### DEPLOYMENT HEALTH ###")
	if len(report.DegradedDeployments) == 0 {
		fmt.Println("✅ All deployments have available replicas.")
	} else {
		for _, d := range report.DegradedDeployments {
			fmt.Printf("⚠️  Degraded Deployment: %s\n", d)
		}
	}

	fmt.Println("\n### POD HEALTH ###")
	if len(report.FailingPods) == 0 {
		fmt.Println("✅ All pods are Running or Succeeded.")
	} else {
		for _, p := range report.FailingPods {
			fmt.Printf("❌ Failing Pod: %s\n", p)
		}
	}
}
