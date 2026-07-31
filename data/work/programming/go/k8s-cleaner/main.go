package main

import (
	"encoding/json"
	"fmt"
	"os"
	"os/exec"
)

// Define struct representations of the specific JSON data we care about.
type PodList struct {
	Items []Pod `json:"items"`
}

type Pod struct {
	Metadata struct {
		Name      string `json:"name"`
		Namespace string `json:"namespace"`
	} `json:"metadata"`
	Status struct {
		ContainerStatuses []struct {
			State struct {
				Waiting struct {
					Reason string `json:"reason"`
				} `json:"waiting"`
			} `json:"state"`
		} `json:"containerStatuses"`
	} `json:"status"`
}

func main() {
	fmt.Println("Fetching all pods as JSON...")

	// Execute 'kubectl get pods -A -o json'
	cmd := exec.Command("kubectl", "get", "pods", "-A", "-o", "json")
	output, err := cmd.CombinedOutput()
	if err != nil {
		fmt.Printf("Failed to execute kubectl: %s\n", string(output))
		os.Exit(1)
	}

	// Parse the JSON directly into our Go struct
	var pods PodList
	if err := json.Unmarshal(output, &pods); err != nil {
		fmt.Printf("Failed to parse JSON: %v\n", err)
		os.Exit(1)
	}

	// Loop through pods and delete ones in CrashLoopBackOff
	deletedCount := 0
	for _, pod := range pods.Items {
		for _, container := range pod.Status.ContainerStatuses {
			if container.State.Waiting.Reason == "CrashLoopBackOff" {
				fmt.Printf("Deleting Pod: %s in Namespace: %s\n", pod.Metadata.Name, pod.Metadata.Namespace)

				delCmd := exec.Command("kubectl", "delete", "pod", pod.Metadata.Name, "-n", pod.Metadata.Namespace)
				if delErr := delCmd.Run(); delErr != nil {
					fmt.Printf("  -> Error deleting pod: %v\n", delErr)
				} else {
					deletedCount++
				}
				break // Move to the next pod once we've deleted this one
			}
		}
	}

	fmt.Printf("Cleanup complete. Deleted %d CrashLoopBackOff pods.\n", deletedCount)
}
