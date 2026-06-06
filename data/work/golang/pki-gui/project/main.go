package main

import (
	"fmt"
	"os"
	"strings"

	"dev.environment/pki-provisioner/internal/pki"
	"fyne.io/fyne/v2"
	"fyne.io/fyne/v2/app"
	"fyne.io/fyne/v2/container"
	"fyne.io/fyne/v2/dialog"
	"fyne.io/fyne/v2/theme"
	"fyne.io/fyne/v2/widget"
)

func main() {
	// Initialize the Fyne application and create a window
	myApp := app.New()
	myWindow := myApp.NewWindow("Local PKI & Host Provisioner")
	myWindow.Resize(fyne.NewSize(500, 400))

	// Go doesn't have classes; we build UI by composing structs.
	domainEntry := widget.NewEntry()
	domainEntry.SetText("test.xxx")

	ipEntry := widget.NewEntry()
	ipEntry.SetText("127.0.0.1")

	logBox := widget.NewMultiLineEntry()
	logBox.Disable() // Read-only log output
	logBox.SetText("Ready...\n")

	// Helper function for logging to the UI
	logMsg := func(msg string) {
		logBox.SetText(logBox.Text + msg + "\n")
	}

	// Form container for inputs
	form := &widget.Form{
		Items: []*widget.FormItem{
			{Text: "Domain", Widget: domainEntry},
			{Text: "Target IP", Widget: ipEntry},
		},
	}

	// Action button with an anonymous callback function (closure)
	runBtn := widget.NewButtonWithIcon("Provision Environment", theme.ConfirmIcon(), func() {
		domain := domainEntry.Text
		ip := ipEntry.Text
		outDir := "./certs"

		logMsg(fmt.Sprintf("[-] Generating PKI for %s...", domain))

		// Call our exported package function
		err := pki.GeneratePKI(domain, outDir)
		if err != nil {
			dialog.ShowError(err, myWindow)
			return
		}
		logMsg("[✓] Certificates generated in ./certs")

		// Hosts file logic
		logMsg("[-] Checking /etc/hosts...")
		err = updateHostsFile(domain, ip)
		if err != nil {
			// In a real OS environment, writing to /etc/hosts requires elevated privileges (sudo).
			// Instead of failing silently or requiring the whole GUI to run as root, we handle the error gracefully.
			logMsg("[!] /etc/hosts modification failed: " + err.Error())
			logMsg(fmt.Sprintf("    -> Please manually add: %s %s", ip, domain))
		} else {
			logMsg(fmt.Sprintf("[✓] Updated /etc/hosts with %s -> %s", domain, ip))
		}

		logMsg("\n✅ Provisioning Complete! Traffic is ready.")
	})

	runBtn.Importance = widget.HighImportance

	// VBox (Vertical Box) layout
	content := container.NewVBox(
		widget.NewLabelWithStyle("🔒 Environment Provisioner", fyne.TextAlignCenter, fyne.TextStyle{Bold: true}),
		widget.NewSeparator(),
		form,
		runBtn,
		widget.NewSeparator(),
		widget.NewLabel("Logs:"),
	)

	// We use a Border layout to let the log box take up all remaining vertical space
	mainLayout := container.NewBorder(content, nil, nil, nil, logBox)
	myWindow.SetContent(mainLayout)
	
	// Start the event loop (blocking call)
	myWindow.ShowAndRun()
}

func updateHostsFile(domain, targetIP string) error {
	hostsFile := "/etc/hosts"
	content, err := os.ReadFile(hostsFile)
	if err != nil {
		return err
	}

	if strings.Contains(string(content), domain) {
		return fmt.Errorf("domain already exists in hosts file")
	}

	entry := fmt.Sprintf("\n%s %s # Added by Local PKI Toolkit\n", targetIP, domain)
	
	// Open file in Append mode. This will throw a permission error unless the GUI is run via sudo/pkexec.
	f, err := os.OpenFile(hostsFile, os.O_APPEND|os.O_WRONLY, 0644)
	if err != nil {
		return err
	}
	defer f.Close()

	_, err = f.WriteString(entry)
	return err
}
