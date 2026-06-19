import serial
import time

# Adjust to your specific port and baudrate
SERIAL_PORT = '/dev/ttyUSB0'
BAUD_RATE = 115200

try:
    ser = serial.Serial(SERIAL_PORT, BAUD_RATE, timeout=1)
    print("Serial port opened successfully.")
    
    while True:
        # Send data
        ser.write(b"Hello LoRa\r\n")
        print("Sent: Hello LoRa")
        
        # Read data
        if ser.in_waiting > 0:
            data = ser.readline().decode('utf-8').strip()
            print(f"Received: {data}")
            
        time.sleep(2)

except Exception as e:
    print(f"Error: {e}")
finally:
    ser.close()

