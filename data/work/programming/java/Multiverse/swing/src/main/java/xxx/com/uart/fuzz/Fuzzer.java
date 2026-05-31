package xxx.com.uart.fuzz;

import com.fazecast.jSerialComm.SerialPort;
import java.util.ArrayList;
import java.util.List;

public class Fuzzer {

  /**
   * Discovers and returns all available USB/serial port names
   * @return List of port names (e.g., "/dev/ttyUSB0", "COM3", etc.)
   */
  public static List<String> usbDiscoveryPort() {
    List<String> usbPorts = new ArrayList<>();
    List<String> allPorts = new ArrayList<>();

    // Get all available serial ports
    SerialPort[] availablePorts = SerialPort.getCommPorts();

    System.out.println("=== Serial Port Discovery ===");
    System.out.println("Total ports found: " + availablePorts.length);
    System.out.println();

    if (availablePorts.length == 0) {
      System.out.println("No serial ports detected at all!");
      System.out.println("This could mean:");
      System.out.println("1. No USB-to-serial devices are connected");
      System.out.println("2. Drivers are not installed");
      System.out.println("3. Devices are not recognized by the system");
      System.out.println("4. Permission issues (try running with sudo on Linux/macOS)");
      return usbPorts;
    }

    for (int i = 0; i < availablePorts.length; i++) {
      SerialPort port = availablePorts[i];
      String portName = port.getSystemPortName();
      String description = port.getPortDescription();
      String location = port.getPortLocation();
      int vendorId = port.getVendorID();
      int productId = port.getProductID();
      String descriptivePortName = port.getDescriptivePortName();

      allPorts.add(portName);

      System.out.println("Port " + (i + 1) + ":");
      System.out.println("  Name: " + portName);
      System.out.println("  Description: " + (description != null ? description : "N/A"));
      System.out.println("  Descriptive Name: " + (descriptivePortName != null ? descriptivePortName : "N/A"));
      System.out.println("  Location: " + (location != null ? location : "N/A"));
      System.out.println("  Vendor ID: 0x" + Integer.toHexString(vendorId).toUpperCase());
      System.out.println("  Product ID: 0x" + Integer.toHexString(productId).toUpperCase());

      // More lenient USB detection - include ALL ports for now
      boolean isUsb = isUsbPort(portName, description, location, vendorId, productId, descriptivePortName);

      if (isUsb) {
        usbPorts.add(portName);
        System.out.println("  ✓ Identified as USB port");
      } else {
        System.out.println("  ✗ Not identified as USB port");
      }
      System.out.println();
    }

    System.out.println("=== Summary ===");
    System.out.println("All ports: " + allPorts);
    System.out.println("USB ports: " + usbPorts);
    System.out.println("Total USB ports found: " + usbPorts.size());

    // If no USB ports found but we have ports, suggest using all ports
    if (usbPorts.isEmpty() && !allPorts.isEmpty()) {
      System.out.println();
      System.out.println("WARNING: No ports identified as USB, but " + allPorts.size() + " port(s) available.");
      System.out.println("You may want to try using one of these ports manually:");
      for (String port : allPorts) {
        System.out.println("  - " + port);
      }
    }

    return usbPorts;
  }

  /**
   * Returns ALL available serial ports (not just USB)
   */
  public static List<String> getAllSerialPorts() {
    List<String> allPorts = new ArrayList<>();
    SerialPort[] availablePorts = SerialPort.getCommPorts();

    for (SerialPort port : availablePorts) {
      allPorts.add(port.getSystemPortName());
    }

    return allPorts;
  }

  /**
   * Helper method to determine if a port is likely a USB serial port
   */
  private static boolean isUsbPort(String portName, String description, String location,
      int vendorId, int productId, String descriptivePortName) {
    if (portName == null) return false;

    // Convert to lowercase for case-insensitive comparison
    String lowerPortName = portName.toLowerCase();
    String lowerDescription = description != null ? description.toLowerCase() : "";
    String lowerLocation = location != null ? location.toLowerCase() : "";
    String lowerDescriptiveName = descriptivePortName != null ? descriptivePortName.toLowerCase() : "";

    // Check vendor/product IDs (non-zero usually indicates USB device)
    boolean hasUsbIds = vendorId != 0 || productId != 0;

    // Common USB serial port patterns
    boolean isUsbByName = lowerPortName.contains("ttyusb") ||    // Linux USB serial
        lowerPortName.contains("ttyacm") ||     // Linux USB CDC-ACM
        lowerPortName.contains("cu.usb") ||     // macOS USB serial
        lowerPortName.contains("cu.wchusbserial") || // macOS CH340/CH341
        lowerPortName.contains("cu.usbmodem") || // macOS USB modem
        lowerPortName.contains("cu.usbserial") || // macOS USB serial
        lowerPortName.startsWith("com");        // Windows COM ports

    boolean isUsbByDescription = lowerDescription.contains("usb") ||
        lowerDescription.contains("serial") ||
        lowerDescription.contains("ftdi") ||
        lowerDescription.contains("ch340") ||
        lowerDescription.contains("ch341") ||
        lowerDescription.contains("ch34") ||
        lowerDescription.contains("cp210") ||
        lowerDescription.contains("cp21") ||
        lowerDescription.contains("prolific") ||
        lowerDescription.contains("pl2303") ||
        lowerDescription.contains("arduino") ||
        lowerDescription.contains("esp32") ||
        lowerDescription.contains("esp8266");

    boolean isUsbByLocation = lowerLocation.contains("usb") ||
        lowerLocation.contains("/dev/bus/usb");

    boolean isUsbByDescriptiveName = lowerDescriptiveName.contains("usb") ||
        lowerDescriptiveName.contains("serial") ||
        lowerDescriptiveName.contains("com");

    // More lenient detection - if ANY indicator suggests USB, include it
    return hasUsbIds || isUsbByName || isUsbByDescription || isUsbByLocation || isUsbByDescriptiveName;
  }

  public static void main(String[] args) {
    // Discover available USB ports
    List<String> availableUsbPorts = usbDiscoveryPort();

    String portName = null;

    if (availableUsbPorts.isEmpty()) {
      System.out.println("No USB serial ports automatically detected.");

      // Try to get all serial ports as fallback
      List<String> allPorts = getAllSerialPorts();
      if (!allPorts.isEmpty()) {
        System.out.println("However, found " + allPorts.size() + " serial port(s) total.");
        System.out.println("You can try using one manually by uncommenting and editing the line below:");
        System.out.println("// String portName = \"" + allPorts.get(0) + "\";");
        System.out.println();

        // Uncomment the next line to use the first available port regardless of type:
        // portName = allPorts.get(0);

        if (portName == null) {
          System.out.println("No port selected. Exiting.");
          System.out.println("To use a specific port, modify the code to set portName manually.");
          return;
        }
      } else {
        System.out.println("No serial ports available at all. Exiting.");
        System.out.println("Make sure:");
        System.out.println("1. Your USB device is connected");
        System.out.println("2. Drivers are installed");
        System.out.println("3. You have permission to access serial ports");
        System.out.println("4. On Linux/macOS, try running with sudo");
        return;
      }
    } else {
      // Use the first available USB port
      portName = availableUsbPorts.get(0);
    }

    System.out.println("Using port: " + portName);

    try {
      // Get the serial port by name
      SerialPort serialPort = SerialPort.getCommPort(portName);

      // Open the serial port
      if (!serialPort.openPort()) {
        System.out.println("Failed to open port");
        return;
      }

      // Set the baud rate and other parameters
      serialPort.setComPortParameters(9600, 8, SerialPort.ONE_STOP_BIT, SerialPort.NO_PARITY);

      // Set timeouts (optional but recommended)
      serialPort.setComPortTimeouts(SerialPort.TIMEOUT_WRITE_BLOCKING, 0, 0);

      // Flush any existing data in the buffer
      serialPort.flushIOBuffers();

      System.out.println("Starting fuzzer on port: " + portName);

      while (true) {
        byte[] data = new byte[10]; // You can adjust this size as needed

        // Fill the data array with random bytes
        for (int i = 0; i < data.length; i++) {
          data[i] = (byte) (Math.random() * 256);
        }

        // Send the data
        int bytesWritten = serialPort.writeBytes(data, data.length);
        if (bytesWritten != data.length) {
          System.out.println("Failed to send all data. Sent: " + bytesWritten + "/" + data.length + " bytes");
        }

        // Optional: Add a small delay to prevent overwhelming the port
        Thread.sleep(10);
      }
    } catch (Exception e) {
      System.out.println("Error: " + e.getMessage());
      e.printStackTrace();
    }
  }
}