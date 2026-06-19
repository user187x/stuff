package xxx.waveshare;

public class LoRaHardwareController {
 private final Sender sender;
 private volatile boolean isScanning = false;
 private volatile boolean signalFound = false;

 public LoRaHardwareController(Sender sender) {
  this.sender = sender;
 }

 // --- 1. Ability to change frequencies ---
 public void setChannel(int channel) {
  if (channel < 0 || channel > 80) {
   System.out.println("Invalid channel. Must be 0-80 for 850-930MHz range.");
   return;
  }
  try {
   // Enter AT mode
   sender.send("+++");
   Thread.sleep(500);

   // Set TX and RX channels to match
   sender.send("AT+TXCH=" + channel + "\r\n");
   Thread.sleep(200);
   sender.send("AT+RXCH=" + channel + "\r\n");
   Thread.sleep(200);

   // Exit and save
   sender.send("AT+EXIT\r\n");
   Thread.sleep(200);

   System.out.println("Hardware tuned to Channel " + channel);
  } catch (InterruptedException e) {
   Thread.currentThread().interrupt();
  }
 }

 // --- 2. Ability to scan for signals ---
 public void startScan(int startChannel, int endChannel) {
  isScanning = true;
  signalFound = false;

  new Thread(() -> {
   System.out.println("Starting spectrum scan from CH" + startChannel + " to CH" + endChannel);
   for (int ch = startChannel; ch <= endChannel; ch++) {
    if (!isScanning || signalFound) {
     System.out.println("Scan halted on CH" + (ch - 1));
     break;
    }
    System.out.println("Scanning CH" + ch + "...");
    setChannel(ch);

    try {
     // Dwell time on each channel to listen for beacons/activity
     Thread.sleep(3500);
    } catch (InterruptedException e) {}
   }
   isScanning = false;
  }).start();
 }

 public void stopScan() {
  isScanning = false;
 }

 public void signalDetected() {
  if (isScanning) {
   this.signalFound = true;
   this.isScanning = false;
   System.out.println("Signal detected! Scan stopped automatically.");
  }
 }
}