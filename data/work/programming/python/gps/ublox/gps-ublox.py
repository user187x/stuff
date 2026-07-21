import time
import tkinter as tk
import serial
import pynmea2
import tkintermapview

# --- 1. Initialize the Hardware ---
# Change this to match your operating system's port for the U-Blox 7
# Linux: '/dev/ttyACM0' or '/dev/ttyUSB0' | Windows: 'COM3', 'COM4' etc.
SERIAL_PORT = '/dev/ttyACM0'
BAUD_RATE = 9600

try:
 gps_serial = serial.Serial(SERIAL_PORT, BAUD_RATE, timeout=1)
 print(f'Connected to U-Blox 7 on {SERIAL_PORT}')
except serial.SerialException:
 print(f'Error: Could not open {SERIAL_PORT}. Check connection and port name.')
 gps_serial = None

# --- 2. Setup the GUI Window ---
root = tk.Tk()
root.title('U-Blox 7 GPS Map')
root.geometry('600x750')
root.configure(bg='#1e1e1e')

# --- 3. Create the UI Elements ---
# Header Label
header_label = tk.Label(
 root,
 text='U-Blox 7 Live GPS Tracker',
 fg='white',
 bg='#1e1e1e',
 font=('Helvetica', 18, 'bold'),
)
header_label.pack(pady=10)

# Map Widget
map_widget = tkintermapview.TkinterMapView(
 root, width=560, height=500, corner_radius=10
)
map_widget.pack(pady=10)
map_widget.set_tile_server('https://a.tile.openstreetmap.org/{z}/{x}/{y}.png')
map_widget.set_zoom(2)  # Default zoom (zoomed out until we get a fix)

# Keep track of our map marker so we can move it
current_marker = None

# Status Display Frame
status_frame = tk.Frame(root, bg='#333333', bd=2, relief=tk.SUNKEN)
status_frame.pack(fill=tk.X, padx=20, pady=10)

lat_label = tk.Label(
 status_frame,
 text='Latitude: Waiting for fix...',
 fg='#44aaff',
 bg='#333333',
 font=('Helvetica', 14),
)
lat_label.pack(anchor='w', padx=10, pady=2)

lon_label = tk.Label(
 status_frame,
 text='Longitude: Waiting for fix...',
 fg='#44aaff',
 bg='#333333',
 font=('Helvetica', 14),
)
lon_label.pack(anchor='w', padx=10, pady=2)

sat_label = tk.Label(
 status_frame, text='Satellites: 0', fg='#aaaaaa', bg='#333333', font=('Helvetica', 12)
)
sat_label.pack(anchor='w', padx=10, pady=2)


# --- 4. Main Update Loop ---
def update_gui():
 global current_marker

 if gps_serial and gps_serial.in_waiting > 0:
  try:
   # Read a line of text from the GPS module
   line = gps_serial.readline().decode('ascii', errors='replace').strip()

   # $GPGGA contains essential fix data (3D location and accuracy data)
   # $GNRMC or $GPRMC also contain location data
   if line.startswith('$GPGGA') or line.startswith('$GNGGA'):
    msg = pynmea2.parse(line)

    # Check if we have a valid GPS fix (gps_qual > 0)
    if msg.gps_qual > 0:
     lat = msg.latitude
     lon = msg.longitude
     sats = msg.num_sats

     # Update Text Labels
     lat_label.config(text=f'Latitude: {lat:.6f}°')
     lon_label.config(text=f'Longitude: {lon:.6f}°')
     sat_label.config(text=f'Satellites: {sats}')

     # Update the Map
     if current_marker is None:
      # First time getting a fix, zoom in
      map_widget.set_zoom(15)
      map_widget.set_position(lat, lon)
      current_marker = map_widget.set_marker(lat, lon, text='U-Blox 7')
     else:
      # Update existing marker position
      current_marker.set_position(lat, lon)
      map_widget.set_position(lat, lon)

  except pynmea2.ParseError as e:
   pass  # Ignore malformed NMEA sentences, which happen occasionally
  except Exception as e:
   print(f'Error reading GPS: {e}')

 # Schedule the GUI to read the serial buffer again in 100 milliseconds
 root.after(100, update_gui)


# Start the application loop
update_gui()
root.mainloop()
