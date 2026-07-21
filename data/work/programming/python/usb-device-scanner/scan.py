import board

# Initialize I2C
i2c = board.I2C()

# Lock the bus to perform a scan
while not i2c.try_lock():
 pass

try:
 print('Scanning I2C bus...')
 addresses = i2c.scan()

 if not addresses:
  print('No I2C devices found! Check wiring and the FT232H I2C switch.')
 else:
  print('Success! I2C devices found at:')
  for address in addresses:
   print(f'- 0x{address:02X}')
finally:
 # Always unlock the bus when done
 i2c.unlock()
