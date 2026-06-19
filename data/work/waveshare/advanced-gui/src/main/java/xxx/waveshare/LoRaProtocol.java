package xxx.waveshare;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

public class LoRaProtocol {
 private final String myNodeId;
 private final Sender sender;
 private final LoRaHardwareController hardwareController;
 private final Timer beaconTimer;

 // Buffers for incoming file transfers
 private final Map<String, StringBuilder> incomingFiles = new HashMap<>();

 public LoRaProtocol(String myNodeId, Sender sender, LoRaHardwareController hardwareController) {
  this.myNodeId = myNodeId;
  this.sender = sender;
  this.hardwareController = hardwareController;
  this.beaconTimer = new Timer(true);
 }

 // --- 3. Send without self-listening ---
 public void sendTextMessage(String text) {
  // Format: MSG|NODE_ID|Text
  sender.send("MSG|" + myNodeId + "|" + text + "\r\n");
 }

 // --- 4. Beacon for discovery ---
 public void startBeacon(long intervalMs) {
  beaconTimer.scheduleAtFixedRate(new TimerTask() {
   @Override
   public void run() {
    sender.send("BCN|" + myNodeId + "|DISCOVERY_PING\r\n");
   }
  }, 0, intervalMs);
 }

 public void stopBeacon() {
  beaconTimer.cancel();
 }

 // --- 5. Protocol for File Transfer ---
 public void sendFile(String filename, byte[] fileData) {
  sender.send("FILE_START|" + myNodeId + "|" + filename + "\r\n");

  // Chunk size kept small for LoRa reliability (e.g., 64 bytes converted to Base64)
  int chunkSize = 64;
  for (int i = 0; i < fileData.length; i += chunkSize) {
   int end = Math.min(fileData.length, i + chunkSize);
   byte[] chunk = new byte[end - i];
   System.arraycopy(fileData, i, chunk, 0, chunk.length);
   String b64 = Base64.getEncoder().encodeToString(chunk);

   sender.send("FILE_DATA|" + myNodeId + "|" + filename + "|" + b64 + "\r\n");
   try { Thread.sleep(100); } catch (InterruptedException e) {} // Prevent serial buffer overflow
  }
  sender.send("FILE_END|" + myNodeId + "|" + filename + "\r\n");
 }

 // Called by Listener when a full \n terminated line is received
 public void processIncomingLine(String line) {
  // Notify hardware controller that we heard something (stops scanning)
  hardwareController.signalDetected();

  String[] parts = line.split("\\|");
  if (parts.length < 3) return; // Not our protocol, ignore or log

  String type = parts[0];
  String senderId = parts[1];

  // 3. Ignore self-messages
  if (senderId.equals(myNodeId)) {
   return;
  }

  switch (type) {
   case "MSG":
    System.out.println("\n[TEXT from " + senderId + "]: " + parts[2]);
    break;
   case "BCN":
    System.out.println("\n[BEACON detected from " + senderId + "]");
    break;
   case "FILE_START":
    System.out.println("\n[FILE INCOMING from " + senderId + "]: " + parts[2]);
    incomingFiles.put(parts[2], new StringBuilder());
    break;
   case "FILE_DATA":
    if (incomingFiles.containsKey(parts[2])) {
     incomingFiles.get(parts[2]).append(parts[3]); // Append Base64 chunk
    }
    break;
   case "FILE_END":
    System.out.println("\n[FILE COMPLETE]: " + parts[2]);
    byte[] decoded = Base64.getDecoder().decode(incomingFiles.get(parts[2]).toString());
    incomingFiles.remove(parts[2]);
    // Save 'decoded' to disk here...
    break;
  }
 }
}