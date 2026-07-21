import serial
import pynmea2


def read_gps_data():
 # Your u-blox 7 port based on your terminal output
 port = '/dev/ttyACM0'
 # 9600 is the standard default baud rate for u-blox 7 receivers
 baudrate = 9600

 try:
  # Open the serial port
  ser = serial.Serial(port, baudrate, timeout=1)
  print(f'Connected to {port}. Listening for GPS data... (Press Ctrl+C to stop)')

  while True:
   # Read a line, decode it, and strip whitespace
   line = ser.readline().decode('ascii', errors='replace').strip()

   # We only want to parse lines that actually contain data
   if not line:
    continue

   # Print the raw line (optional, you can comment this out)
   print(f'Raw: {line}')

   # Parse GGA (Global Positioning System Fix Data) for location
   if line.startswith('$GPGGA'):
    try:
     msg = pynmea2.parse(line)

     # gps_qual == 0 means no fix, > 0 means we have a fix
     if msg.gps_qual > 0:
      print(
       f'--> [FIX] Lat: {msg.latitude:.6f}, Lon: {msg.longitude:.6f}, Sats: {msg.num_sats}, Alt: {msg.altitude}m'
      )
     else:
      print('--> [NO FIX] Waiting for satellites... (Move closer to a window/outside)')

    except pynmea2.ParseError as e:
     print(f'Parse error: {e}')

 except serial.SerialException as e:
  print(f'\nError opening serial port: {e}')
  print(
   "Tip: If you get a 'Permission denied' error, run this command in your terminal:"
  )
  print('sudo usermod -a -G dialout $USER')
  print('Then log out and log back in.')
 except KeyboardInterrupt:
  print('\nExiting script...')
 finally:
  if 'ser' in locals() and ser.is_open:
   ser.close()


if __name__ == '__main__':
 read_gps_data()
