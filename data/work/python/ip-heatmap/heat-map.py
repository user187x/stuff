# /// script
# dependencies = [
#   "folium",
#   "requests"
# ]
# ///

import folium
from folium.plugins import HeatMap
import requests

# 1. Prepare your raw IP data
ip_addresses = [
    "8.8.8.8",      # Google DNS
    "1.1.1.1",      # Cloudflare
    "140.211.11.105", # Oregon, US
    "185.228.168.10", # CleanBrowsing
    "77.88.8.8"     # Yandex (Russia)
]

heat_data = []

# 2. Convert IP addresses to Latitude and Longitude coordinates
for ip in ip_addresses:
    try:
        # Utilizing free tier endpoints from IPinfo api
        response = requests.get(f"https://ipinfo.io/{ip}/json", timeout=5)
        if response.status_code == 200:
            data = response.json()
            # Coordinates are returned as a comma-separated string: "lat,lng"
            if "loc" in data:
                lat, lng = map(float, data["loc"].split(","))
                heat_data.append([lat, lng])
    except Exception as e:
        print(f"Could not resolve IP {ip}: {e}")

# 3. Initialize the interactive map object centered globally
base_map = folium.Map(location=[20, 0], zoom_start=2)

# 4. Generate and attach the HeatMap cluster layer
if heat_data:
    HeatMap(heat_data, radius=15, blur=10).add_to(base_map)
    
    # 5. Export the interactive rendering to an HTML webpage
    output_file = "ip_heatmap.html"
    base_map.save(output_file)
    print(f"Success! Map successfully created and saved to '{output_file}'")
else:
    print("No valid coordinates retrieved.")

