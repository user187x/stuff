package xxx.beta;

import com.fazecast.jSerialComm.SerialPort;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Scanner;

public class App {

  static void main(String[] args) {
    System.out.println("=== KV4P HT V2.0 Java Manager ===");

    // 1. Find available serial ports
    SerialPort[] availablePorts = SerialPort.getCommPorts();
    if (availablePorts.length == 0) {
      System.out.println("No serial ports found. Is the KV4P plugged in?");
      return;
    }

    System.out.println("Available Ports:");
    for (int i = 0; i < availablePorts.length; i++) {
      System.out.println(
          "["
              + i
              + "] "
              + availablePorts[i].getSystemPortName()
              + " - "
              + availablePorts[i].getDescriptivePortName());
    }

    // 2. Select the port (For simplicity, asking the user)
    Scanner scanner = new Scanner(System.in);
    System.out.print("Select the port number for the KV4P (e.g., ttyUSB0): ");
    int portIndex = scanner.nextInt();

    SerialPort radioPort = availablePorts[portIndex];

    // 3. Configure connection settings (SA818 standard is 9600 baud)
    radioPort.setComPortParameters(9600, 8, 1, 0);
    radioPort.setComPortTimeouts(SerialPort.TIMEOUT_READ_BLOCKING, 1000, 0);

    if (radioPort.openPort()) {
      System.out.println("Successfully opened port: " + radioPort.getSystemPortName());
    } else {
      System.out.println("Failed to open port. Check your Linux dialout/uucp group permissions.");
      return;
    }

    try {
      OutputStream out = radioPort.getOutputStream();
      InputStream in = radioPort.getInputStream();

      // 4. Construct the SA818 Frequency Command
      // Format: AT+DMOSETGROUP=GBW,TFV,RFV,Tx_CTCSS,SQ,Rx_CTCSS
      // 0 = 12.5kHz bandwidth, 4 = Squelch level
      String frequencyCommand = "AT+DMOSETGROUP=0,146.5200,146.5200,0000,4,0000\r\n";

      System.out.println("Sending command: " + frequencyCommand.trim());
      out.write(frequencyCommand.getBytes());
      out.flush();

      // 5. Read the response from the radio
      Thread.sleep(500); // Give the ESP32/SA818 time to process
      byte[] readBuffer = new byte[1024];
      int numRead = in.read(readBuffer);

      if (numRead > 0) {
        String response = new String(readBuffer, 0, numRead);
        System.out.println("Radio Response: " + response.trim());
      } else {
        System.out.println("No response received from the radio.");
      }

    } catch (Exception e) {
      e.printStackTrace();
    } finally {
      // 6. Clean up
      radioPort.closePort();
      System.out.println("Port closed.");
      scanner.close();
    }
  }
}
