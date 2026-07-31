package main

import (
	"log"

	"github.com/ncruces/zenity"
)

// WindowGreeter acts as our "class"
type WindowGreeter struct {
	Title   string
	Message string
}

// Pop is a method attached to WindowGreeter.
// This is how Go handles object-oriented behavior.
func (w *WindowGreeter) Pop() {
	// zenity.Info opens a native OS dialog box
	err := zenity.Info(w.Message, zenity.Title(w.Title))
	if err != nil {
		log.Fatalf("Failed to open window: %v", err)
	}
}
