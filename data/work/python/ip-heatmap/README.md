##### Instructions 

#### install dependencies

```bash
pipx install folium requests
```
*You can run this script either as a standalone executable using `pipx` (recommended) or via standard Python execution.*

#### Run Globally with `pipx`

Using `pipx run` allows you to execute the script from anywhere on your system without manually managing virtual environments or installing dependencies.

**1. Add this metadata header to the top of your script:**

```python
# /// script
# dependencies = [
#   "folium",
#   "requests"
# ]
# ///

import folium
from folium.plugins import HeatMap
import requests

# ... (the rest of your code) ...

```
**2. Run Command:**

```bash
pipx run my/path/heat-map.py
```


