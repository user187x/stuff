package xxx.waveshare;

import com.fazecast.jSerialComm.SerialPort;
import java.util.Scanner;

public class App {

 public static void main(String[] args) {
  // Node ID used to filter self-messages and identify origin
  String myNodeId = "NODE_" + (int)(Math.random() * 1000);
  System.out.println("My Node ID: " + myNodeId);

  DeviceManager deviceManager = new DeviceManager();
  deviceManager.initialize();

  SerialPort serialPort = deviceManager.getSerialPort();
  Sender sender = new Sender(serialPort);

  // Wire up hardware controller and protocol handler
  LoRaHardwareController hwController = new LoRaHardwareController(sender);
  LoRaProtocol protocol = new LoRaProtocol(myNodeId, sender, hwController);

  // Start listening, route parsed strings to our Protocol object
  Listener.listen(serialPort, protocol::processIncomingLine);

  // Start broadcasting discovery pings every 15 seconds
  protocol.startBeacon(15000);

  System.out.println("Commands:");
  System.out.println(" /channel <0-80>  - Change Frequency");
  System.out.println(" /scan            - Auto-scan channels");
  System.out.println(" /sendfile <name> - Test file transfer protocol");
  System.out.println(" <text>           - Send general message");

  Scanner scanner = new Scanner(System.in);

  while (true) {

   String input = scanner.nextLine();

   if (input.startsWith("/channel ")) {
    int ch = Integer.parseInt(input.split(" ")[1]);
    hwController.setChannel(ch);
   }
   else if (input.equals("/scan")) {
    hwController.startScan(0, 80);
   }
   else if (input.startsWith("/sendfile ")) {
    String filename = input.split(" ")[1];
    byte[] mockData = "Hello, this is a mock file content over LoRa".getBytes();
    protocol.sendFile(filename, mockData);
    System.out.println("Mock file dispatched.");
   }
   else {
    // Regular chat message
    protocol.sendTextMessage(input);
   }
  }
 }
}