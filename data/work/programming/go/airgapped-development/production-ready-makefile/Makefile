# Variables
APP_NAME := cookbook
VERSION := 1.0.0
ARCHIVE_NAME := $(APP_NAME)-$(VERSION)-airgap.tar.gz

# Mark targets that don't represent physical files as .PHONY
.PHONY: help vendor bundle clean build-airgap

help:
	@echo "=========================================================="
	@echo " Go Airgap Deployment Builder"
	@echo "=========================================================="
	@echo "Usage:"
	@echo "  make vendor       - Download and lock dependencies locally"
	@echo "  make bundle       - Create a tarball ready for airgapped transfer"
	@echo "  make build-airgap - (Airgap Side) Build using only local vendor files"
	@echo "  make clean        - Remove vendor folder, binaries, and archives"
	@echo ""

vendor:
	@echo "==> Tidying go.mod and vendoring dependencies..."
	go mod tidy
	go mod vendor

bundle: vendor
	@echo "==> Creating airgap bundle: $(ARCHIVE_NAME)..."
	# Tar the current directory, excluding git history, previous archives, and binaries
	tar -czvf $(ARCHIVE_NAME) \
		--exclude=.git \
		--exclude=$(ARCHIVE_NAME) \
		--exclude=$(APP_NAME) \
		.
	@echo "==> Success! You can now transfer $(ARCHIVE_NAME) across the airgap."

build-airgap:
	@echo "==> Building $(APP_NAME) using offline vendor cache..."
	# -mod=vendor forces Go to look ONLY in the local vendor folder
	go build -mod=vendor -o $(APP_NAME) main.go
	@echo "==> Build complete! Run with ./$(APP_NAME)"

clean:
	@echo "==> Cleaning workspace..."
	rm -rf vendor/
	rm -f *.tar.gz
	rm -f $(APP_NAME)
