#!/bin/bash
# Exit immediately if a command exits with a non-zero status
set -e

echo "========================================"
echo " Starting Shared Memory IPC Demo"
echo "========================================"

# 1. Clean up any previous runs
if [ -f "shared_data.bin" ]; then
    rm shared_data.bin
fi

# 2. Run the Python application (The Writer)
python3 python_app/writer.py
echo ""

# 3. Build and Run the Java application (The Reader) using Maven
echo "[Maven] Compiling and executing Java app..."
mvn -q clean compile exec:java -Dexec.mainClass="com.demo.SharedMemoryReader"
echo ""

echo "========================================"
echo " Demo Complete"
echo "========================================"
