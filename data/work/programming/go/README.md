```markdown
# 🚀 Go Hello World: Starter Project

A zero-to-hero starter repository for building and executing Go applications. This project is designed to be a self-contained starting point that fits cleanly into terminal-based workflows and containerized environments.

## 🛠️ Prerequisites

Ensure you have the Go toolchain installed.
Verify your installation:
```bash
go version

```

## 🏗️ Core Workflow Commands

When developing in Go, especially within a terminal multiplexer like `tmux` or an editor like `vim` or `neovim`, you'll rely on a few core commands to compile and test your code rapidly.

### 1. Quick Execution (No binary output)

For rapid iteration, you can compile and run your code on the fly without saving a persistent binary to your directory:

```bash
go run main.go

```

### 2. Building the Executable

To compile your code into a standalone, statically linked binary:

```bash
go build -o my-app

```

*This creates an executable named `my-app` optimized for your host OS and architecture.*

### 3. Running the Binary

```bash
./my-app

```

### 4. Managing Dependencies

As you add external libraries to your project (e.g., TUI libraries, CLI framework tools), Go will track them in your `go.mod` file. To clean up unused dependencies and download missing ones, regularly run:

```bash
go mod tidy

```

---

## ⚙️ Adding More Functionality

Ready to expand beyond "Hello World"? Here is how to add new features and organize your application.

### Step 1: Create a New File or Package

You can keep things simple by adding a new file in the same directory (the same `main` package), for example, `banner.go`:

```go
package main

import "fmt"

// PrintBanner displays a TUI-style header
func PrintBanner() {
    fmt.Println("=====================================")
    fmt.Println("     Welcome to the Go Workspace     ")
    fmt.Println("=====================================")
}

```

### Step 2: Update `main.go`

Modify your entry point to call the newly created function. Because they share the `main` package, you do not need to import `banner.go`.

```go
package main

import "fmt"

func main() {
    // Call the function from banner.go
    PrintBanner()
    fmt.Println("🚀 Hello, World! Core system online.")
}

```

### Step 3: Run the Multi-file Project

When working with multiple files in the `main` package, you can no longer just run `go run main.go`. Instead, point Go to the entire directory:

```bash
go run .
# OR
go build -o my-app .
```

---

## 🐳 Containerization (Docker)

Because Go compiles to a static binary, it is perfect for minimal, highly secure container images—ideal for eventual deployment to production Kubernetes clusters.

Here is a zero-to-hero multi-stage `Dockerfile` you can drop into this directory to containerize your application:

```dockerfile
# Build stage
FROM golang:1.22-alpine AS builder
WORKDIR /app

# Copy dependency manifests
COPY go.mod ./
# COPY go.sum ./ # Uncomment if you have external dependencies

# Copy source code and compile
COPY *.go ./
RUN CGO_ENABLED=0 GOOS=linux go build -o my-app .

# Final minimal execution image
FROM alpine:latest
WORKDIR /root/

# Pull the binary from the builder stage
COPY --from=builder /app/my-app .

# Run the binary
CMD ["./my-app"]
```

Build and run your container:

```bash
docker build -t go-hello-world .
docker run --rm go-hello-world
```