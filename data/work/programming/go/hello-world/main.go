package main

import "fmt"

func main() {
	fmt.Println("🚀 Initializing application...")

	// Instantiate our struct (like creating a new class object)
	helloWindow := WindowGreeter{
		Title:   "System Notification",
		Message: "Hi! This is a native OS window triggered by Go.",
	}

	// Call the method to pop the window
	fmt.Println("[*] Waiting for user to close the window...")
	helloWindow.Pop()

	fmt.Println("✅ Window closed. Application exiting.")
}
