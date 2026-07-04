# Compiler to use
CC = gcc

# Compiler flags: -O2 for optimization, -Wall for compiler warnings
CFLAGS = -O2 -Wall

# Libraries to link:
#   -lbluetooth  BlueZ (L2CAP, SDP, HCI)
#   -lsystemd    sd-bus (the auto-pairing D-Bus agent)
#   -lpthread    background agent + spinner threads
LIBS = -lbluetooth -lsystemd -lpthread

# The name of the final executable
TARGET = btmouse

# Install location
PREFIX = /usr/local
BINDIR = $(PREFIX)/bin

# Source files: main program + setup helper + pairing agent + spinner
SRC = main.c bt_setup.c bt_agent.c spinner.c

# Header dependencies
HDR = bt_setup.h bt_agent.h spinner.h

# Default target
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

# Build the executable
$(TARGET): $(SRC) $(HDR)
	$(CC) $(CFLAGS) -o $(TARGET) $(SRC) $(LIBS)

# Optional: install the binary system-wide (so you can run `btmouse` anywhere)
install: $(TARGET)
	install -Dm755 $(TARGET) $(BINDIR)/$(TARGET)
	@echo "Installed to $(BINDIR)/$(TARGET)"

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
	$(MAKE)

# Remove local build artifact
clean:
	rm -f $(TARGET)

# Make sure to include all commands here
.PHONY: all check_deps install stop uninstall clean rebuild
