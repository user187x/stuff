# list devices
ls /dev/ttyACM*
sudo dmesg | grep -i tty

# View logs
sudo dmesg --follow

# Configure Minicom
sudo minicom -s

Serial Device​ /dev/ttyUSB0
Bps/Par/Bits​ 115200 8N1
Hardware Flow Ctrl​ Yes

-> Save as dfl & exit

# Configure Device
screen /dev/ttyACME1 115200


stty -F /dev/ttyACM0 speed 3000000 baud line = 0 min = 0 time = 0 -brkint -icrnl -imaxbel -opost /
-isig -icanon -iexten -echo -echoe -echok -echoctl -echoke


# Configure baudrate
stty -F /dev/ttyACM0 115200 raw -clocal -echo


# Device or resource busy fix
sudo fuser -k /dev/ttyACM0

# Configure your settings
AT+ADDRESS=1          // Set the module address (0-65535)
AT+CHANNEL=18         // Set frequency (e.g., 433M or 868M/915M based on HF)
AT+MODE=1             // Set to transparent transmit/receive mode
AT+EXIT

# Install Python
pip install pyserial

# Python Script
#=================

import serial
import time

# Adjust the port to match your dongle
ser = serial.Serial('/dev/ttyUSB0', 115200, timeout=1)

time.sleep(2) # Wait for connection

# Send "hello world"
ser.write(b'hello world\r\n')
print("Message sent!")

ser.close()

