import math
import time
import tkinter as tk
import board
import adafruit_qmc5883p

# 1. Initialize the Hardware
i2c = board.I2C()
compass = adafruit_qmc5883p.QMC5883P(i2c)

# 2. Setup the GUI Window
root = tk.Tk()
root.title('QMC5883P Compass')
root.geometry('400x550')
root.configure(bg='#1e1e1e')

# 3. Create the Drawing Canvas
canvas = tk.Canvas(root, width=400, height=550, bg='#1e1e1e', highlightthickness=0)
canvas.pack()

# --- Draw Static UI Elements ---
cx, cy = 200, 220  # Center coordinates for the compass face
radius = 150

# Compass Ring
canvas.create_oval(
 cx - radius, cy - radius, cx + radius, cy + radius, outline='#555555', width=4
)
canvas.create_text(
 cx, cy - radius - 20, text='N', fill='#ff4444', font=('Helvetica', 18, 'bold')
)
canvas.create_text(
 cx, cy + radius + 20, text='S', fill='white', font=('Helvetica', 18, 'bold')
)
canvas.create_text(
 cx + radius + 20, cy, text='E', fill='white', font=('Helvetica', 18, 'bold')
)
canvas.create_text(
 cx - radius - 20, cy, text='W', fill='white', font=('Helvetica', 18, 'bold')
)

# --- Create Dynamic UI Elements ---
# The needle line
needle = canvas.create_line(
 cx, cy, cx, cy - radius, fill='#ff4444', width=6, arrow=tk.LAST
)
heading_label = canvas.create_text(
 cx, 420, text='Heading: 0.0°', fill='white', font=('Helvetica', 16)
)

# The Z-Axis Dip Bubble
canvas.create_text(
 cx, 470, text='Magnetic Dip (Z-Axis)', fill='#aaaaaa', font=('Helvetica', 12)
)
canvas.create_rectangle(100, 490, 300, 510, outline='#555555', fill='#333333')
dip_bubble = canvas.create_oval(190, 488, 210, 512, fill='#44aaff')
dip_label = canvas.create_text(
 cx, 530, text='Dip: 0.0°', fill='#aaaaaa', font=('Helvetica', 12)
)


def update_gui():
 try:
  # Read the sensor
  mag_x, mag_y, mag_z = compass.magnetic

  # --- Calculate Heading ---
  heading_rad = math.atan2(mag_y, mag_x)
  heading_deg = math.degrees(heading_rad)
  if heading_deg < 0:
   heading_deg += 360

  # Update the Needle GUI
  # Tkinter angles start with 0 at the right (East), so we offset by 90 degrees
  # to map our calculated heading to the visual compass dial.
  draw_angle = math.radians(heading_deg - 90)
  end_x = cx + radius * math.cos(draw_angle)
  end_y = cy + radius * math.sin(draw_angle)

  canvas.coords(needle, cx, cy, end_x, end_y)
  canvas.itemconfig(heading_label, text=f'Heading: {heading_deg:.1f}°')

  # --- Calculate Magnetic Dip (Z-Axis Tilt) ---
  xy_magnitude = math.sqrt(mag_x**2 + mag_y**2)
  dip_rad = math.atan2(mag_z, xy_magnitude)
  dip_deg = math.degrees(dip_rad)

  # Update the Bubble GUI
  # Map the -90 to +90 degree dip to the 200-pixel wide box
  bubble_x = cx + (dip_deg / 90.0) * 100
  bubble_x = max(110, min(290, bubble_x))  # Constrain inside the box

  canvas.coords(dip_bubble, bubble_x - 10, 488, bubble_x + 10, 512)
  canvas.itemconfig(dip_label, text=f'Dip Angle: {dip_deg:.1f}°')

 except Exception as e:
  print(f'Read error: {e}')

 # Schedule the GUI to update again in 100 milliseconds
 root.after(100, update_gui)


# Start the application loop
update_gui()
root.mainloop()
