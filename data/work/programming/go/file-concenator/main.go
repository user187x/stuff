package main

import (
	"fmt"
	"os"
	"path/filepath"
	"strings"
)

func main() {
	directories := []string{"./dir1", "./dir2"}
	outputFileName := "giant_file.txt"

	// Create or truncate the output file
	outFile, err := os.OpenFile(outputFileName, os.O_CREATE|os.O_WRONLY|os.O_TRUNC, 0644)
	if err != nil {
		fmt.Printf("Error creating output file: %v\n", err)
		os.Exit(1)
	}
	// Ensure the file is closed when the function finishes
	defer outFile.Close()

	// Imperatively loop over each directory
	for _, dir := range directories {
		fmt.Printf("Scanning directory: %s\n", dir)

		// Read all entries in the directory
		entries, err := os.ReadDir(dir)
		if err != nil {
			fmt.Printf("Warning: Could not read directory %s: %v\n", dir, err)
			continue // Skip to the next directory
		}

		// Loop over the files in the current directory
		for _, entry := range entries {
			// Skip subdirectories
			if entry.IsDir() {
				continue
			}

			// Check if the file ends in .txt
			if strings.HasSuffix(entry.Name(), ".txt") {
				fullPath := filepath.Join(dir, entry.Name())
				fmt.Printf("  -> Found %s, appending to master file...\n", fullPath)

				// Read the entire file into memory
				content, err := os.ReadFile(fullPath)
				if err != nil {
					fmt.Printf("Error reading file %s: %v\n", fullPath, err)
					continue
				}

				// Write the content to our giant file
				_, err = outFile.Write(content)
				if err != nil {
					fmt.Printf("Error writing to output file: %v\n", err)
				}

				// Add a newline after each file's content for readability
				outFile.WriteString("\n")
			}
		}
	}

	fmt.Println("Concatenation complete!")
}
