# ESP32-S3 Custom Sensor & Display Development Guide

This guide outlines the complete workflow for setting up an ESP32 development environment on a Linux system, from a fresh Arduino IDE installation to deploying code that interacts with physical sensors and a built-in RGB display.

---

## Step 1: Download and Install Arduino IDE 2.x

To ensure compatibility with modern ESP32 board definitions and libraries, use the latest 2.x version of the Arduino IDE.

1. Navigate to the official [Arduino Software page](https://www.arduino.cc/en/software).
2. Download the **Linux AppImage** or install via **Flatpak** (common for sandboxed Linux environments).
3. If using the AppImage, make it executable and run it:
   ```bash
   chmod +x arduino-ide_*.AppImage
   ./arduino-ide_*.AppImage
Step 2: Linux USB Permissions & Packages
By default, Linux distributions restrict read/write access to serial ports. You must grant your user account permissions to communicate with the ESP32 over USB (/dev/ttyUSB0 or /dev/ttyACM0).

Open your terminal.

Add your user to the dialout and tty groups:

Bash
sudo usermod -a -G dialout $USER
sudo usermod -a -G tty $USER
Ensure basic serial communication packages are installed (Debian/Ubuntu):

Bash
sudo apt update
sudo apt install python3-serial
Log out and log back in (or reboot) for the group changes to take effect.

Step 3: Install the ESP32 Board Definitions (JSON)
The Arduino IDE needs to know how to compile code specifically for Espressif chips.

Open Arduino IDE 2.x.

Go to File > Preferences.

Locate the Additional boards manager URLs field.

Paste the following official Espressif JSON URL:
https://dl.espressif.com/espressif/package_esp32_index.json

Click OK.

Open the Boards Manager (the board icon on the left sidebar).

Search for esp32 by Espressif Systems and click Install.

Step 4: Select Board and Configure Critical Settings
Connecting to a high-performance ESP32-S3 with a large display requires specific memory configurations to prevent immediate boot crashes.

Connect your ESP32 device to your computer via USB.

In the top dropdown menu, select Select other board and port...

Search for and select ESP32S3 Dev Module.

Select the corresponding port (typically /dev/ttyUSB0).

Navigate to the Tools menu and configure the following critical settings:

Board: ESP32S3 Dev Module

Port: /dev/ttyUSB0

PSRAM: OPI PSRAM (Critical: Failing to enable this on displays like the 480x480 RGB ST7701S will result in an immediate esp_core_dump_flash crash loop).

USB CDC On Boot: Enabled (Helps with serial monitor debugging on some S3 variants).

Step 5: Install Required Libraries
Complex projects require external libraries. You will use two installation methods: the built-in Library Manager and manual directory inclusion.

Method A: Arduino Library Manager
Open the Library Manager (book stack icon on the left).

Search for and Install the following:

GFX Library for Arduino (by Moon On Our Nation)

ESP_I2S

Adafruit Unified Sensor

Adafruit HMC5883 Unified (If using a magnetometer)

Method B: Manual Installation (For Flatpak/Sandboxed IDEs)
When using the Flatpak version of the Arduino IDE, the standard "Add .ZIP Library" feature may silently fail due to sandbox restrictions.

Download the specific library ZIP (e.g., a custom bmi160-arduino GitHub repository).

Go to Sketch > Show Sketch Folder.

Create a directory named src inside your sketch folder.

Extract the .h and .cpp files from the downloaded ZIP directly into the src folder.

In your code, include the library using relative paths:

C++
#include "src/bmi160.h"
Step 6: The Development Workflow
With the environment configured, follow this iterative process to write, flash, and test code on the device.

1. Write & Verify Code
Write your code using standard Arduino C/C++.

Click the Verify (checkmark) button. This compiles the code and checks for syntax errors or missing dependencies without attempting to push it to the board.

2. Upload to Device
Ensure your board and port are selected.

Click the Upload (right arrow) button.

Watch the output console. You should see esptool.py writing to the flash memory partitions, ending with a "Hard resetting via RTS pin..." message.

3. Monitor Serial Output
Open the Serial Monitor (magnifying glass icon).

Crucial: Ensure the baud rate dropdown in the bottom right perfectly matches the rate defined in your setup() function (e.g., Serial.begin(115200);). A mismatch will result in corrupted, garbled text (like xxxxxxxxxxx8xxxx).

Use Serial.println() statements in your code to debug sensor initialization and live data reads.

4. Physical Testing & Noise Mitigation
I2C Conflicts: When wiring sensors (like I2C magnetometers or accelerometers) to generic GPIO headers, ensure SDA and SCL are mapped correctly in your Wire.begin(SDA_PIN, SCL_PIN); function.

Electrical Noise: Large parallel RGB displays generate massive electrical interference. If sensor data freezes or corrupts, increase the polling delay in your loop() (e.g., from 50ms to 200ms) and ensure explicit delay() yields are placed after I2C transactions to allow signals to settle.
