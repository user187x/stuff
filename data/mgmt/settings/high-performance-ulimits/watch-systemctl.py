#!/usr/bin/env python3
import time
import subprocess
from datetime import datetime

# Add any kernel parameters you want to watch live
PARAMS = [
 'vm.swappiness',
 'net.core.somaxconn',
 'fs.file-max',
 'kernel.pid_max',
 'net.ipv4.ip_forward',
]


def fetch_values():
 vals = {}
 for p in PARAMS:
  try:
   res = subprocess.run(['sysctl', '-n', p], capture_output=True, text=True, check=True)
   vals[p] = res.stdout.strip()
  except Exception:
   vals[p] = 'unavailable'
 return vals


print(
 f'[{datetime.now().strftime("%H:%M:%S")}] Streaming live sysctl parameter changes... (Ctrl+C to quit)'
)
current_state = fetch_values()

for p, v in current_state.items():
 print(f'  init > {p} = {v}')

try:
 while True:
  time.sleep(0.5)  # Check twice a second
  new_state = fetch_values()
  for p in PARAMS:
   if new_state[p] != current_state[p]:
    timestamp = datetime.now().strftime('%Y-%m-%d %H:%M:%S')
    print(
     f"[{timestamp}] [UPDATE DETECTED] {p}: '{current_state[p]}' ---> '{new_state[p]}'"
    )
    current_state[p] = new_state[p]

  # Also dynamically check if new parameters were introduced to sysctl globally
  # (Optional layer if parameters are being created/destroyed dynamically)

except KeyboardInterrupt:
 print('\nLive stream stopped.')
