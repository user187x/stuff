import time
import math
import board
import adafruit_qmc5883p

# Initialize the I2C connection
i2c = board.I2C()
compass = adafruit_qmc5883p.QMC5883P(i2c)

print('Compass is live. Press Ctrl+C to quit.')

while True:
 mag_x, mag_y, mag_z = compass.magnetic

 # Calculate the heading in radians
 heading_rad = math.atan2(mag_y, mag_x)

 # Convert radians to degrees
 heading_deg = math.degrees(heading_rad)

 # Correct for negative angles so it falls between 0 and 360 degrees
 if heading_deg < 0:
  heading_deg += 360

 print(
  f'Heading: {heading_deg:.1f}° (Raw - X: {mag_x:.2f}G, Y: {mag_y:.2f}G, Z: {mag_z:.2f}G)'
 )
 time.sleep(0.5)
