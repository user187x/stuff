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

