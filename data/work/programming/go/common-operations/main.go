package main

import (
 "bufio"
 "fmt"
 "os"
 "strconv"
 "strings"
 "syscall"

 "golang.org/x/term"
)

func main() {
 // ==========================================
 // 1. STRING MANIPULATIONS
 // ==========================================
 fmt.Println("--- 1. String Manipulations ---")
 rawString := "   hello,world,go,is,great   "

 // Trim (removes whitespace from beginning and end)
 trimmed := strings.TrimSpace(rawString)
 fmt.Printf("Trimmed: '%s'\n", trimmed)

 // Prepend, Append, and Concat
 prefix := "Message: "
 suffix := "!!!"
 concatenated := prefix + trimmed + suffix
 fmt.Printf("Concatenated: '%s'\n", concatenated)

 // Split on characters (creates a slice of strings)
 csvData := "server1,server2,server3"
 splitStrings := strings.Split(csvData, ",")
 fmt.Printf("Split into array: %q\n", splitStrings)

 // ==========================================
 // 2. TYPE CONVERSIONS
 // ==========================================
 fmt.Println("\n--- 2. Type Conversions ---")

 // String to Number (Integer)
 portStr := "8080"
 portInt, err := strconv.Atoi(portStr)
 if err != nil {
  fmt.Println("Error converting string to int")
 }
 fmt.Printf("String to Int: %d (Math works: %d)\n", portInt, portInt+1)

 // Number to String
 statusCode := 404
 statusStr := strconv.Itoa(statusCode)
 fmt.Printf("Int to String: 'Error code %s'\n", statusStr)

 // ==========================================
 // 3. LOOPS & COUNTERS
 // ==========================================
 fmt.Println("\n--- 3. Loops ---")

 // Basic counter loop
 fmt.Print("Counter: ")
 for i := 1; i <= 5; i++ {
  fmt.Printf("%d ", i)
 }
 fmt.Println()

 // ==========================================
 // 4. ARRAYS (SLICES in Go)
 // ==========================================
 fmt.Println("\n--- 4. Arrays (Slices) ---")

 // Create an array (slice)
 servers := []string{"web-01", "db-01"}
 fmt.Printf("Initial array: %v\n", servers)

 // Insert (Append) values
 servers = append(servers, "cache-01", "web-02")
 fmt.Printf("After insert: %v\n", servers)

 // Delete a value (Remove "db-01" at index 1)
 // Go doesn't have a built-in "remove" function, so we slice the array together
 indexToRemove := 1
 servers = append(servers[:indexToRemove], servers[indexToRemove+1:]...)
 fmt.Printf("After deleting index 1: %v\n", servers)

 // ==========================================
 // 5. MAPS (Dictionaries / Hashmaps)
 // ==========================================
 fmt.Println("\n--- 5. Maps ---")

 // Create a map
 configs := make(map[string]string)

 // Insert map values
 configs["environment"] = "production"
 configs["region"] = "us-east-1"
 configs["debug"] = "true"
 fmt.Printf("Initial map: %v\n", configs)

 // Delete map values
 delete(configs, "debug")
 fmt.Printf("After deleting 'debug': %v\n", configs)

 // ==========================================
 // 6. FILE OPERATIONS
 // ==========================================
 fmt.Println("\n--- 6. File Operations ---")
 fileName := "test_config.txt"

 // Write text to a new file (overwrites if exists, permissions 0644)
 err = os.WriteFile(fileName, []byte("INIT=true\n"), 0644)
 if err != nil {
  fmt.Println("Error writing file:", err)
 }
 fmt.Println("Created and wrote to file:", fileName)

 // Append text to an existing file
 // We have to open the file in APPEND mode
 f, err := os.OpenFile(fileName, os.O_APPEND|os.O_WRONLY, 0644)
 if err != nil {
  fmt.Println("Error opening file:", err)
 } else {
  f.WriteString("APPENDED_KEY=secret_value\n")
  f.Close()
  fmt.Println("Appended text to file:", fileName)
 }

 // ==========================================
 // 7. USER INPUT (Terminal I/O)
 // ==========================================
 fmt.Println("\n--- 7. Terminal I/O ---")

 // Read standard user input
 reader := bufio.NewReader(os.Stdin)
 fmt.Print("Enter your username: ")
 username, _ := reader.ReadString('\n')
 username = strings.TrimSpace(username) // Clean up the newline character

 // Read sensitive input (hides typing)
 fmt.Print("Enter your password: ")
 bytePassword, err := term.ReadPassword(int(syscall.Stdin))
 fmt.Println() // Add a newline after password entry because the terminal won't

 if err == nil {
  password := string(bytePassword)
  fmt.Printf("\nWelcome, %s! Your password is %d characters long.\n", username, len(password))
 }
}
