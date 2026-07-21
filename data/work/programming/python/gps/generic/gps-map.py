import tkinter as tk
from tkinter import scrolledtext
import tkintermapview
import serial
import pynmea2
import threading
import queue
import time


class GPSApp:
 def __init__(self, root):
  self.root = root
  self.root.title('U-Blox 7 GPS Tracker')
  self.root.geometry('800x700')

  # --- Layout Setup ---

  # Top Frame for Map
  self.map_frame = tk.Frame(self.root)
  self.map_frame.pack(fill=tk.BOTH, expand=True, padx=10, pady=(10, 5))

  # Bottom Frame for Console
  self.console_frame = tk.Frame(self.root)
  self.console_frame.pack(fill=tk.X, expand=False, padx=10, pady=(5, 10))

  # Initialize Map Widget
  self.map_widget = tkintermapview.TkinterMapView(
   self.map_frame, width=800, height=450, corner_radius=0
  )
  self.map_widget.pack(fill=tk.BOTH, expand=True)
  # Default starting location (Baltimore, MD)
  self.map_widget.set_position(39.2904, -76.6122)
  self.map_widget.set_zoom(10)
  self.marker = None

  # Initialize Console Text Area
  self.console = scrolledtext.ScrolledText(
   self.console_frame, height=12, bg='black', fg='#00FF00', font=('Consolas', 10)
  )
  self.console.pack(fill=tk.X, expand=False)
  self.log_to_console('System initialized. Starting GPS thread...\n')

  # --- Threading & Data Setup ---

  self.data_queue = queue.Queue()
  self.running = True
  self.port = '/dev/ttyACM0'
  self.baudrate = 9600

  # Start background thread for reading serial data
  self.serial_thread = threading.Thread(target=self.read_serial_data, daemon=True)
  self.serial_thread.start()

  # Start the Tkinter loop that checks for new data
  self.root.after(100, self.process_queue)

  # Handle window close gracefully
  self.root.protocol('WM_DELETE_WINDOW', self.on_closing)

 def log_to_console(self, text):
  """Inserts text into the console and scrolls to the bottom."""
  self.console.insert(tk.END, text + '\n')
  self.console.see(tk.END)

 def read_serial_data(self):
  """Runs in a background thread to prevent UI freezing."""
  while self.running:
   try:
    with serial.Serial(self.port, self.baudrate, timeout=1) as ser:
     self.data_queue.put(
      ('LOG', f'Successfully connected to {self.port} at {self.baudrate} baud.')
     )

     while self.running:
      line = ser.readline().decode('ascii', errors='replace').strip()
      if line:
       # Send raw sentence to UI
       self.data_queue.put(('RAW', line))

       # Parse for location
       if line.startswith('$GPGGA'):
        try:
         msg = pynmea2.parse(line)
         if msg.gps_qual > 0:
          self.data_queue.put(('FIX', (msg.latitude, msg.longitude)))
        except pynmea2.ParseError:
         pass  # Ignore incomplete NMEA sentences
   except serial.SerialException as e:
    self.data_queue.put(('LOG', f'Serial Error: {e}'))
    self.data_queue.put(('LOG', 'Retrying in 5 seconds...'))
    time.sleep(5)

 def process_queue(self):
  """Runs on the main UI thread, processes data from the background thread."""
  try:
   while True:
    msg_type, data = self.data_queue.get_nowait()

    if msg_type == 'LOG':
     self.log_to_console(f'[*] {data}')

    elif msg_type == 'RAW':
     self.log_to_console(data)

    elif msg_type == 'FIX':
     lat, lon = data
     self.update_map(lat, lon)

  except queue.Empty:
   pass
  finally:
   # Re-schedule this method to run again in 100ms
   if self.running:
    self.root.after(100, self.process_queue)

 def update_map(self, lat, lon):
  """Updates the map position and marker."""
  if not self.marker:
   self.log_to_console(f'[*] GPS FIX ACQUIRED: {lat:.6f}, {lon:.6f}')
   self.map_widget.set_zoom(15)
   self.marker = self.map_widget.set_marker(lat, lon, text='Current Location')
   self.map_widget.set_position(lat, lon)
  else:
   # Update existing marker to save memory/processing
   self.marker.set_position(lat, lon)
   self.map_widget.set_position(lat, lon)

 def on_closing(self):
  """Clean up the thread before destroying the window."""
  self.running = False
  self.root.destroy()


if __name__ == '__main__':
 root = tk.Tk()
 app = GPSApp(root)
 root.mainloop()
