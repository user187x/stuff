# Compiler to use
CC = gcc

# Compiler flags: -O2 for optimization, -Wall for compiler warnings
CFLAGS = -O2 -Wall

# Libraries to link the daemon:
#   -lbluetooth  BlueZ (L2CAP, SDP, HCI)
#   -lsystemd    sd-bus (the auto-pairing D-Bus agent)
#   -lpthread    background agent + spinner threads
LIBS = -lbluetooth -lsystemd -lpthread

# The name of the final daemon executable
TARGET = btmouse

# The GTK4 remote-control GUI (built with `make gui`).
# NOTE: pkg-config is queried inside the build recipe (not here with :=), so
# that a first-time `make gui` which installs GTK still picks up the flags.
GUI_TARGET = btmouse-gui
GUI_SRC    = gui.c

# Install location
PREFIX = /usr/local
BINDIR = $(PREFIX)/bin

# Source files: main program + setup helper + pairing agent + audio + spinner
SRC = main.c bt_setup.c bt_agent.c bt_audio.c spinner.c

# Header dependencies
HDR = bt_setup.h bt_agent.h bt_audio.h spinner.h

# Default target (daemon only, so headless boxes need no GTK)
all: check_deps $(TARGET)

# Ensure the required development headers exist; install them if missing.
# Uses sudo only when available (some environments run as root already).
check_deps:
	@need=""; \
	[ -f /usr/include/bluetooth/bluetooth.h ] || need="$$need libbluetooth-dev"; \
	[ -f /usr/include/systemd/sd-bus.h ]      || need="$$need libsystemd-dev"; \
	if [ -n "$$need" ]; then \
		echo "Installing missing dev packages:$$need"; \
		if command -v sudo >/dev/null 2>&1; then \
			sudo apt-get update && sudo apt-get install -y $$need; \
		else \
			apt-get update && apt-get install -y $$need; \
		fi; \
	fi

# Ensure the GTK4 dev packages needed by the GUI are present.
check_gui_deps:
	@if ! pkg-config --exists gtk4 2>/dev/null; then \
		echo "Installing missing dev packages: libgtk-4-dev pkg-config"; \
		if command -v sudo >/dev/null 2>&1; then \
			sudo apt-get update && sudo apt-get install -y libgtk-4-dev pkg-config; \
		else \
			apt-get update && apt-get install -y libgtk-4-dev pkg-config; \
		fi; \
	fi
	@pkg-config --exists gtk4 2>/dev/null || { \
		echo "ERROR: GTK4 development files not found (need gtk4.pc)."; \
		echo "       Install them manually, e.g.:"; \
		echo "       sudo apt-get install libgtk-4-dev pkg-config"; \
		exit 1; }

# Build the daemon
$(TARGET): $(SRC) $(HDR)
	$(CC) $(CFLAGS) -o $(TARGET) $(SRC) $(LIBS)

# Build the GUI (opt-in: `make gui`)
gui: $(GUI_TARGET)

# check_gui_deps is an order-only prerequisite (the | separator): it runs
# first and installs GTK if needed, but never itself forces a rebuild. We ask
# pkg-config for the flags here, inside the recipe, so they are resolved AFTER
# any install has happened -- fixing the first-run "gtk/gtk.h not found" case.
$(GUI_TARGET): $(GUI_SRC) | check_gui_deps
	$(CC) $(CFLAGS) -o $(GUI_TARGET) $(GUI_SRC) \
		$(shell pkg-config --cflags --libs gtk4) -lm

# Build everything (daemon + GUI)
both: all gui

# Optional: install the daemon system-wide (so you can run `btmouse` anywhere).
# Also installs the GUI if it has been built.
install: $(TARGET)
	install -Dm755 $(TARGET) $(BINDIR)/$(TARGET)
	@echo "Installed to $(BINDIR)/$(TARGET)"
	@if [ -f $(GUI_TARGET) ]; then \
		install -Dm755 $(GUI_TARGET) $(BINDIR)/$(GUI_TARGET); \
		echo "Installed to $(BINDIR)/$(GUI_TARGET)"; \
	fi

# Easy stop: signal a running instance to shut down cleanly
stop:
	@if pkill -INT -x $(TARGET); then \
		echo "Sent stop signal to btmouse."; \
	else \
		echo "btmouse does not appear to be running."; \
	fi

# Full uninstall: revert system changes and remove binaries
uninstall:
	./uninstall.sh

# Rebuild from scratch by cleaning first, then running make
rebuild:
	$(MAKE) clean
	$(MAKE) both

# Remove local build artifacts
clean:
	rm -f $(TARGET) $(GUI_TARGET)

# Make sure to include all commands here
.PHONY: all both gui check_deps check_gui_deps install stop uninstall clean rebuild
