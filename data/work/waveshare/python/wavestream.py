import serial

# Open the port at 115200 baud rate
ser = serial.Serial('/dev/ttyACM0', 115200, timeout=1)
ser.flush()

try:
    while True:
        if ser.in_waiting > 0:
            # Read a line of text from the device
            line = ser.readline().decode('utf-8').rstrip()
            print(f"Data: {line}")
except KeyboardInterrupt:
    print("Closing connection.")
    ser.close()
