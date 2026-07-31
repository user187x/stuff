package main

import (
	"fmt"
	"os"
	"path/filepath"
)

func main() {
	// 1. Resolve the equivalent of "~"
	homeDir, err := os.UserHomeDir()
	if err != nil {
		fmt.Printf("Error finding home directory: %v\n", err)
		os.Exit(1)
	}

	// Construct the target directory path: ~/.local/bin
	targetDir := filepath.Join(homeDir, ".local", "bin")

	// 2. Equivalent to: mkdir -p ~/.local/bin/
	// os.MkdirAll creates the directory and any necessary parents.
	// 0755 gives the owner read/write/execute permissions, and others read/execute.
	fmt.Printf("Creating directory: %s\n", targetDir)
	err = os.MkdirAll(targetDir, 0755)
	if err != nil {
		fmt.Printf("Error creating directory: %v\n", err)
		os.Exit(1)
	}

	// 3. Equivalent to: mv prettygo ~/.local/bin/
	sourceFile := "prettygo"
	destinationFile := filepath.Join(targetDir, sourceFile)

	fmt.Printf("Moving %s to %s\n", sourceFile, destinationFile)
	err = os.Rename(sourceFile, destinationFile)
	if err != nil {
		fmt.Printf("Error moving file: %v\n", err)
		os.Exit(1)
	}

	fmt.Println("Install complete!")
}
