### Build/Install
```bash
 make
```
### Clean Existing
```bash
 make clean && make
```
### Uninstall
```bash
 make uninstall
```
### Remove Old Pairing
```bash
 # Listing Devices
 ➤  bluetoothctl -- devices
 # Removing Device (by MAC)
 ➤  bluetoothctl -- remove B0:D5:FB:9A:51:AE
```

### Instructions
make              # daemon (unchanged; headless boxes still need no GTK)
make gui          # builds btmouse-gui (auto-installs libgtk-4-dev if missing)
sudo ./btmouse    # start the daemon, pair from the host
./btmouse-gui     # launch the panel as your normal user
