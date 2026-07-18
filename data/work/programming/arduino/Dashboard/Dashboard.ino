#include <Arduino_GFX_Library.h>
#include <ESP_I2S.h>
#include <Wire.h> // Essential for BMI160 communication
#include <BMI160_Arduino.h> // Essential BMI160 library
#include <math.h> // For compass math functions

// ----------------------------------------------------
// GPIO DEFINITIONS (Verified Mapping for ESP32-4848S040)
// ----------------------------------------------------
// External hardware (H1 Red wire): Diagnostic LED Pulse
#define EXT_LED_PIN 19 
// Physical BOOT button (Used to simulate screen press)
#define PHYSICAL_BOOT_BUTTON_PIN 0 

// I2C pins explicitly mapped to repurposed H1 header right column
#define I2C_SDA_PIN 44 // Replaces H1 RXD
#define I2C_SCL_PIN 43 // Replaces H1 TXD

BMI160 Arduino_BMI160;

// ----------------------------------------------------
// DISPLAY CONFIGURATION (Stable/Verified config from Turn 9)
// ----------------------------------------------------
extern const uint8_t st7701_type1_init_operations[];

Arduino_DataBus *cmdBus = new Arduino_SWSPI(
  GFX_NOT_DEFINED /* DC */, 39 /* CS */, 48 /* SCK */, 47 /* MOSI */, GFX_NOT_DEFINED /* MISO */);

// RGB parallel interface configuration (ST7701S RGB parallel 480x480)
Arduino_ESP32RGBPanel *rgbpanel = new Arduino_ESP32RGBPanel(
  18, 17, 16, 21,
  4, 5, 6, 7, 15,           // R0-R4
  8, 20, 3, 46, 9, 10,      // G0-G5
  11, 12, 13, 14, 0,        // B0-B4
  1, 10, 8, 50,             // hsync
  1, 10, 8, 20,             // vsync
  0, 16000000, false, 0, 0, 0);

Arduino_GFX *gfx = new Arduino_RGB_Display(
  480, 480, rgbpanel, 0, true,
  cmdBus, GFX_NOT_DEFINED, st7701_type1_init_operations, sizeof(st7701_type1_init_operations));

// ----------------------------------------------------
// AUDIO CONFIGURATION (I2S NS4168 Amp)
// ----------------------------------------------------
const uint8_t I2S_BCLK = 1;  
const uint8_t I2S_LRCK = 2;  
const uint8_t I2S_DOUT = 40; 

I2SClass i2s;
const int sampleRate = 16000;
const int frequency = 880;  // A5 tone
const int amplitude = 4000; // Volume

// Play efficient beep (sound)
void playBeep(int duration_ms) {
  int totalSamples = (sampleRate * duration_ms) / 1000;
  int halfPeriod = sampleRate / frequency / 2;
  int16_t sample = amplitude;
  
  for (int i = 0; i < totalSamples; i++) {
    if (i % halfPeriod == 0) sample = -sample; // Toggle wave polarity
    i2s.write((uint8_t*)&sample, sizeof(sample));
  }
  
  sample = 0; // Send zero sample to stop buzzing
  i2s.write((uint8_t*)&sample, sizeof(sample));
}

// ----------------------------------------------------
// COMPASS (SENSING-BASED NEEDLE) & UI SETUP
// ----------------------------------------------------
int centerX = 240; 
int centerY = 240;
int radius = 150;
float DEG_TO_RAD_M = 3.14159265 / 180.0;

// Virtual Button definition (Bottom Right)
int btnX = 350;
int btnY = 380;
int btnW = 100;
int btnH = 60;

// Store previous needle position to erase efficiently
int oldNeedleX = centerX;
int oldNeedleY = centerY - radius + 10;

void drawStaticUI() {
  gfx->fillScreen(0x0000); // Start Black

  // Draw Compass Ring
  gfx->drawCircle(centerX, centerY, radius, 0xFFFF); // WHITE ring
  
  // Set Text properties for Compass Labels
  gfx->setTextSize(3);
  gfx->setTextColor(0xFFFF); // WHITE text

  // Compass Labels N, S, E, W
  gfx->setCursor(centerX - 10, centerY - radius - 35); gfx->print("N");
  gfx->setCursor(centerX - 10, centerY + radius + 10); gfx->print("S");
  gfx->setCursor(centerX + radius + 15, centerY - 10); gfx->print("E");
  gfx->setCursor(centerX - radius - 35, centerY - 10); gfx->print("W");
}

void drawVirtualButton(bool pressed) {
  // Use hex codes directly (standard names are undefined in scope)
  uint16_t bgColor = pressed ? 0xF800 : 0x001F; // RED : DarkBlue
  uint16_t textColor = pressed ? 0xFFFF : 0xBDF7; // WHITE : LightGray

  // Draw rounded rectangle
  gfx->fillRoundRect(btnX, btnY, btnW, btnH, 10, bgColor);
  gfx->drawRoundRect(btnX, btnY, btnW, btnH, 10, 0xFFFF); // WHITE outline

  // Draw "LIGHT" Text centered on the button
  gfx->setTextSize(3);
  gfx->setTextColor(textColor);
  gfx->setCursor(btnX + 10, btnY + 20);
  gfx->print("LIGHT");
}

// Update the red simulated needle
void updateCompassNeedle(int heading) {
  // 1. Erase old needle in black
  gfx->drawLine(centerX, centerY, oldNeedleX, oldNeedleY, 0x0000);

  // 2. Calculate new needle position based on heading
  // Adjust math so 0 degrees is true North (up)
  float angle = (float)(heading - 90) * DEG_TO_RAD_M;
  int needleLen = radius - 15;
  int newNeedleX = centerX + cos(angle) * needleLen;
  int newNeedleY = centerY + sin(angle) * needleLen;

  // 3. Draw new needle in RED (0xF800)
  gfx->drawLine(centerX, centerY, newNeedleX, newNeedleY, 0xF800);

  // 4. Update coordinates for the next loop
  oldNeedleX = newNeedleX;
  oldNeedleY = newNeedleY;
}

// ----------------------------------------------------
// MAIN SETUP & LOOP
// ----------------------------------------------------
void setup() {
  // Setup standard Serial at matching baud rate
  Serial.begin(115200);

  // Setup PSRAM (Mandatory configuration, not code-driven, but crucial for large frames)
  // Ensure "Tools > PSRAM > OPI PSRAM" is set in IDE.

  // Setup External Hardware
  pinMode(PHYSICAL_BOOT_BUTTON_PIN, INPUT_PULLUP);
  pinMode(EXT_LED_PIN, OUTPUT);
  // Default LED state: Reverted requirement, Turn the external light ON automatically
  digitalWrite(EXT_LED_PIN, HIGH); 

  // Setup I2C & BMI160 Sensor
  Wire.begin(I2C_SDA_PIN, I2C_SCL_PIN);
  if (!Arduino_BMI160.begin(BMI160_I2C_MODE, Wire)) {
    Serial.println("Ooops, no BMI160 detected ... Check your wiring!");
    // If sensor is missing, we stop here to force a wiring check.
    while (1); 
  }

  // Setup Audio
  i2s.setPins(I2S_BCLK, I2S_LRCK, I2S_DOUT, -1, -1);
  i2s.begin(I2S_MODE_STD, sampleRate, I2S_DATA_BIT_WIDTH_16BIT, I2S_SLOT_MODE_MONO, I2S_STD_SLOT_LEFT);

  // Setup Display & Backlight
  pinMode(38, OUTPUT); 
  digitalWrite(38, HIGH); // Enable backlight GPIO 38
  
  gfx->begin();
  cmdBus->sendCommand(0x3A); // Fix standard ST7701S pixel format error (RGB565)
  cmdBus->sendData(0x55); // Pixel format fix
  delay(10);
  
  // Initialize UI (Paint screen BLACK)
  drawStaticUI();
  drawVirtualButton(false); // Draw button in 'up' state
}

unsigned long lastUpdate = 0;

void loop() {
  unsigned long currentMillis = millis();

  // Update "Sensed" Orientation Reading every 50ms (Stable I2C transaction frequency)
  if (currentMillis - lastUpdate > 50) {
    int ax_raw, ay_raw, az_raw;
    int gx_raw, gy_raw, gz_raw;

    // Read raw data from the accelerometer and gyroscope
    Arduino_BMI160.readAccelerometer(ax_raw, ay_raw, az_raw);
    Arduino_BMI160.readGyro(gx_raw, gy_raw, gz_raw);

    // Map Y-axis Acceleration (Gravitational vector/Tilt) to Rotation.
    // The accelerometer range is +/-2g default (+/-32768).
    // Mapping gravity vector ay_raw from generic range to 0-360 degrees.
    // When tilted fully, the needle will point to 90 or 270 (E or W).
    int sensedRotation = map(ay_raw, -16384, 16384, 0, 360); 

    // Smoothly update the visual on-screen needle with sensed rotation data
    updateCompassNeedle(sensedRotation);

    // Heartbeat Blink (Visual confirmation of successful sensor read)
    digitalWrite(EXT_LED_PIN, LOW); // External LED pulse OFF
    delay(10);
    digitalWrite(EXT_LED_PIN, HIGH); // External LED pulse back ON

    lastUpdate = currentMillis;
  }

  // Monitor Physical BOOT Button (Simulating standard touchscreen press)
  // Pressed = LOW
  if (digitalRead(PHYSICAL_BOOT_BUTTON_PIN) == LOW) {
    // Simulated button press
    drawVirtualButton(true); // Visually press virtual button (Turn it red)

    // Play Audio (150ms beep)
    playBeep(150);

    // Reverting logic: Turn the external hardware LED OFF only while the button is active
    digitalWrite(EXT_LED_PIN, LOW); 
    
    // Wait for physical button release (keeps virtual button red, beep silent, external light off)
    while (digitalRead(PHYSICAL_BOOT_BUTTON_PIN) == LOW) {
      delay(10);
    }

    // Visually release virtual button (Turn it blue)
    drawVirtualButton(false); 

    // Reverting requirements: Turn the external hardware LED back ON automatically
    digitalWrite(EXT_LED_PIN, HIGH);
  }
  delay(1); // Yield for stability
}