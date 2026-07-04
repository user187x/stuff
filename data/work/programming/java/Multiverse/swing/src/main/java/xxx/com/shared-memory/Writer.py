import mmap
import os

# For pure RAM-based shared memory on Linux, you could use /dev/shm/shared_data.bin
# We use a local file here for cross-platform simplicity.
FILE_NAME = "shared_data.bin"
FILE_SIZE = 1024

print("[Python] Initializing shared memory space...")

# Create a file filled with null bytes to establish the memory footprint
with open(FILE_NAME, "wb") as f:
    f.write(b'\x00' * FILE_SIZE)

# Open the file for reading and writing
with open(FILE_NAME, "r+b") as f:
    # Memory-map the file. size=0 means map the whole file.
    with mmap.mmap(f.fileno(), 0) as mm:
        message = b"Hello from Python! The shared memory link is active."
        print(f"[Python] Writing to shared memory: '{message.decode()}'")
        
        # Move cursor to beginning and write bytes
        mm.seek(0)
        mm.write(message)
        
        # Flush changes to ensure Java can read them immediately
        mm.flush()

print("[Python] Process complete. Yielding to Java...")
