package main

import (
	"bytes"
	"flag"
	"fmt"
	"go/parser"
	"go/printer"
	"go/token"
	"os"
)

func main() {
	// Add a -w flag for in-place writing, defaulting to false
	writeFlag := flag.Bool("w", false, "write result to (source) file instead of stdout")
	flag.Parse()

	// Ensure a filename was provided after any flags
	if flag.NArg() < 1 {
		fmt.Println("Usage: prettygo [-w] <filename.go>")
		os.Exit(1)
	}

	targetFile := flag.Arg(0)

	// 1. Read the target Go file
	src, err := os.ReadFile(targetFile)
	if err != nil {
		fmt.Printf("Error reading file: %v\n", err)
		os.Exit(1)
	}

	// 2. Parse the AST
	fset := token.NewFileSet()
	astNode, err := parser.ParseFile(fset, targetFile, src, parser.ParseComments)
	if err != nil {
		fmt.Printf("Syntax error in Go code: %v\n", err)
		os.Exit(1)
	}

	// 3. Configure the 1-space printer
	cfg := printer.Config{
		Mode:     printer.UseSpaces,
		Tabwidth: 1,
		Indent:   0,
	}

	// 4. Print the AST to a buffer
	var buf bytes.Buffer
	err = cfg.Fprint(&buf, fset, astNode)
	if err != nil {
		fmt.Printf("Error formatting code: %v\n", err)
		os.Exit(1)
	}

	// 5. Output to file if -w is passed, otherwise print to terminal
	if *writeFlag {
		err = os.WriteFile(targetFile, buf.Bytes(), 0644)
		if err != nil {
			fmt.Printf("Error writing to file: %v\n", err)
			os.Exit(1)
		}
		fmt.Printf("Formatted: %s\n", targetFile)
	} else {
		fmt.Println(buf.String())
	}
}
