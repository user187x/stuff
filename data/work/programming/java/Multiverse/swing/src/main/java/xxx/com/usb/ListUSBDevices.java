package xxx.com.usb;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ListUSBDevices {
  private static final String REMOTE_USB_IDS_URL = "https://raw.githubusercontent.com/usbids/usbids/master/usb.ids";
  private static final Path USB_IDS_CACHE = Paths.get("usb.ids");
  private static final String LOCAL_USB_IDS_RESOURCE = "data/usb.ids";
  private static final Map<Integer, String> VENDOR_MAP = new HashMap<>();
  private static final Map<Integer, Map<Integer, String>> PRODUCT_MAP = new HashMap<>();
  private static final Map<String, Map<String, String>> SYSTEM_DEVICE_INFO = new HashMap<>();
  private static volatile boolean isPrinting = false;
  private static volatile boolean running = true;
  private static Set<String> currentDevices = new HashSet<>();

  static {
    // Load system command device info
    loadSystemDeviceInfo();
    // Load usb.ids for Vendor/Product ID mappings
    loadUsbIds();
  }

  public static void main(String[] args) {
    // Add shutdown hook to stop spinner cleanly
    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      running = false;
      System.out.print("\r");
      System.out.println("Exiting USB listener...");
    }));

    // Initial enumeration
    System.out.println("Connected USB Devices (via PowerShell):");
    listDevicesUsingSystemCommand();

    // Start active listening mode with spinner
    startListening();
  }

  private static void startListening() {
    System.out.println("Entering active listening mode... Press Ctrl+C to exit.");

    // Start spinner thread
    Thread spinnerThread = new Thread(() -> {
      char[] spinnerChars = {'\\', '|', '/'};
      int spinnerIndex = 0;
      while (running) {
        if (!isPrinting) {
          System.out.print("\rScanning USB ports " + spinnerChars[spinnerIndex]);
          spinnerIndex = (spinnerIndex + 1) % spinnerChars.length;
        }
        try {
          Thread.sleep(200);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          break;
        }
      }
      System.out.print("\r");
    });
    spinnerThread.setDaemon(true);
    spinnerThread.start();

    // Initial scan
    scanAndUpdateDevices();

    // Polling loop
    while (running) {
      try {
        Thread.sleep(2000);
        scanAndUpdateDevices();
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        running = false;
        break;
      }
    }
  }

  private static void scanAndUpdateDevices() {
    synchronized (ListUSBDevices.class) {
      isPrinting = true;
      System.out.print("\r");
      Set<String> newDevices = getCurrentUsbDevices();

      // Detect disconnected devices
      Set<String> disconnected = new HashSet<>(currentDevices);
      disconnected.removeAll(newDevices);
      for (String device : disconnected) {
        System.out.println("Disconnected: " + device);
      }

      // Detect new devices
      Set<String> added = new HashSet<>(newDevices);
      added.removeAll(currentDevices);
      for (String device : added) {
        System.out.println("Plugged in: " + device);
        String[] parts = device.split(" ");
        if (parts.length >= 4) {
          try {
            int vendorId = Integer.parseInt(parts[0].split(":")[1].substring(2), 16);
            int productId = Integer.parseInt(parts[1].split(":")[1].substring(2), 16);
            if (vendorId >= 0 && vendorId <= 0xFFFF && productId >= 0 && productId <= 0xFFFF) {
              printDeviceDetails(vendorId, productId, "");
            } else {
              System.out.println("Skipping device with invalid IDs: Vendor:0x" + String.format("%04x", vendorId) + ", Product:0x" + String.format("%04x", productId));
            }
          } catch (NumberFormatException e) {
            System.err.println("Error parsing device ID: " + e.getMessage());
          }
        }
      }

      // Update current devices
      currentDevices = newDevices;
      isPrinting = false;
    }
  }

  private static Set<String> getCurrentUsbDevices() {
    Set<String> devices = new HashSet<>();
    try {
      ProcessBuilder pb = new ProcessBuilder("powershell", "Get-PnpDevice -Class USB | Select-Object DeviceID, Description, Manufacturer, SerialNumber, Status, InstanceId | Format-List");
      Process process = pb.start();
      try (BufferedReader reader = process.inputReader()) {
        String vendorId = null, productId = null, description = null, manufacturer = null, serial = null, instanceId = null;
        Pattern pattern = Pattern.compile("(DeviceID|Description|Manufacturer|SerialNumber|Status|InstanceId)\\s*:\\s*(.*)");
        while (true) {
          String line = reader.readLine();
          if (line == null) break;
          line = line.trim();
          Matcher matcher = pattern.matcher(line);
          if (matcher.matches()) {
            String key = matcher.group(1);
            String value = matcher.group(2).trim();
            switch (key) {
              case "DeviceID":
                if (value.contains("VID_") && value.contains("PID_")) {
                  Pattern idPattern = Pattern.compile("VID_(\\w{4})&PID_(\\w{4})");
                  Matcher idMatcher = idPattern.matcher(value);
                  if (idMatcher.find()) {
                    vendorId = idMatcher.group(1);
                    productId = idMatcher.group(2);
                  }
                }
                break;
              case "Description":
                description = value;
                break;
              case "Manufacturer":
                manufacturer = value;
                break;
              case "SerialNumber":
                serial = value;
                break;
              case "InstanceId":
                instanceId = value;
                if (vendorId != null && productId != null) {
                  try {
                    int vId = Integer.parseInt(vendorId, 16);
                    int pId = Integer.parseInt(productId, 16);
                    if (vId >= 0 && vId <= 0xFFFF && pId >= 0 && pId <= 0xFFFF) {
                      String id = "Vendor:0x" + vendorId +
                          " Product:0x" + productId +
                          " Manufacturer:" + (manufacturer != null && !manufacturer.isEmpty() ? manufacturer : "Unknown") +
                          " Product:" + (description != null && !description.isEmpty() ? description : "Unknown") +
                          " Serial:" + (serial != null && !serial.isEmpty() ? serial : "Unknown");
                      devices.add(id);
                    }
                  } catch (NumberFormatException e) {
                    // Skip invalid IDs
                  }
                  vendorId = productId = description = manufacturer = serial = instanceId = null;
                }
                break;
            }
          }
        }
      }
      if (!process.waitFor(3, TimeUnit.SECONDS)) {
        System.err.println("PowerShell command timed out");
        process.destroy();
      }
    } catch (Exception e) {
      System.err.println("Error running PowerShell command: " + e.getMessage());
    }
    return devices;
  }

  private static void loadUsbIds() {
    // Check for local usb.ids in resources/data/usb.ids
    try (InputStream resourceStream = ListUSBDevices.class.getClassLoader().getResourceAsStream(LOCAL_USB_IDS_RESOURCE)) {
      if (resourceStream != null) {
        System.out.println("Using local usb.ids from resources/data/usb.ids");
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(resourceStream, StandardCharsets.UTF_8))) {
          parseUsbIds(reader);
          return;
        }
      } else {
        System.out.println("Local usb.ids not found in resources/data/usb.ids");
      }
    } catch (IOException e) {
      System.err.println("Error accessing local usb.ids from resources/data/usb.ids: " + e.getMessage());
    }

    // Check cached usb.ids in project directory
    try {
      if (Files.exists(USB_IDS_CACHE)) {
        long fileSize = Files.size(USB_IDS_CACHE);
        if (fileSize < 1000) {
          System.err.println("Cached usb.ids is too small (" + fileSize + " bytes), deleting...");
          Files.deleteIfExists(USB_IDS_CACHE);
        } else if (System.currentTimeMillis() - Files.getLastModifiedTime(USB_IDS_CACHE).toMillis() < 24 * 60 * 60 * 1000) {
          System.out.println("Using cached usb.ids from " + USB_IDS_CACHE);
          try (BufferedReader reader = Files.newBufferedReader(USB_IDS_CACHE, StandardCharsets.UTF_8)) {
            String firstLine = reader.readLine();
            if (firstLine == null || !firstLine.contains("USB")) {
              System.err.println("Cached usb.ids appears invalid (missing USB identifier), deleting...");
              Files.deleteIfExists(USB_IDS_CACHE);
            } else {
              reader.reset();
              parseUsbIds(reader);
              return;
            }
          }
        }
      }
    } catch (IOException e) {
      System.err.println("Error accessing cached usb.ids: " + e.getMessage());
      try {
        Files.deleteIfExists(USB_IDS_CACHE);
      } catch (IOException ex) {
        System.err.println("Failed to delete cached usb.ids: " + ex.getMessage());
      }
    }

    // Try downloading usb.ids
    for (int attempt = 1; attempt <= 3; attempt++) {
      try {
        System.out.println("Downloading usb.ids from " + REMOTE_USB_IDS_URL + " (attempt " + attempt + ")...");
        HttpURLConnection conn = (HttpURLConnection) new URL(REMOTE_USB_IDS_URL).openConnection();
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(10000);
        conn.setRequestProperty("Accept", "text/plain");
        int responseCode = conn.getResponseCode();
        if (responseCode != HttpURLConnection.HTTP_OK) {
          throw new IOException("HTTP error code: " + responseCode);
        }
        try (InputStream in = conn.getInputStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
          StringBuilder content = new StringBuilder();
          String line;
          while ((line = reader.readLine()) != null) {
            content.append(line).append("\n");
          }
          if (content.length() < 1000 || !content.toString().contains("USB")) {
            throw new IOException("Downloaded usb.ids is invalid (length=" + content.length() + ")");
          }
          Files.write(USB_IDS_CACHE, content.toString().getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
          parseUsbIds(new BufferedReader(new StringReader(content.toString())));
          return;
        }
      } catch (IOException e) {
        System.err.println("Failed to download usb.ids from " + REMOTE_USB_IDS_URL + ": " + e.getMessage());
        if (attempt < 3) {
          try {
            Thread.sleep(1000 * attempt);
          } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
          }
        }
      }
    }

    // Fallback to minimal hardcoded map
    System.err.println("Using fallback Vendor ID mappings due to failure in loading usb.ids");
    VENDOR_MAP.put(0x0b05, "ASUS");
    VENDOR_MAP.put(0x8087, "Intel");
    VENDOR_MAP.put(0x1a86, "CH340/CH341 USB-Serial");
    VENDOR_MAP.put(0x05e3, "Genesys Logic");
    VENDOR_MAP.put(0x045e, "Microsoft");
    VENDOR_MAP.put(0x3434, "Keychron");
    VENDOR_MAP.put(0x1b3f, "Generalplus Technology");
  }

  private static void parseUsbIds(BufferedReader reader) throws IOException {
    String line;
    Integer currentVendorId = null;
    Map<Integer, String> currentProductMap = null;

    while ((line = reader.readLine()) != null) {
      line = line.trim();
      if (line.isEmpty() || line.startsWith("#")) continue;
      if (!line.startsWith("\t")) {
        try {
          String[] parts = line.split("\\s+", 2);
          if (parts.length == 2) {
            currentVendorId = Integer.parseInt(parts[0], 16);
            if (currentVendorId >= 0 && currentVendorId <= 0xFFFF) {
              VENDOR_MAP.put(currentVendorId, parts[1]);
              currentProductMap = new HashMap<>();
              PRODUCT_MAP.put(currentVendorId, currentProductMap);
            }
          }
        } catch (NumberFormatException e) {
          // Skip invalid vendor lines
        }
      } else if (currentVendorId != null && line.startsWith("\t")) {
        try {
          String[] parts = line.split("\\s+", 2);
          if (parts.length == 2) {
            int productId = Integer.parseInt(parts[0], 16);
            if (productId >= 0 && productId <= 0xFFFF) {
              currentProductMap.put(productId, parts[1]);
            }
          }
        } catch (NumberFormatException e) {
          // Skip invalid product lines
        }
      }
    }
  }

  private static String lookupRemoteUsbId(int vendorId, int productId) {
    if (vendorId < 0 || vendorId > 0xFFFF || productId < 0 || productId > 0xFFFF) {
      return null;
    }
    String vendorIdHex = String.format("%04x", vendorId);
    String productIdHex = String.format("%04x", productId);
    for (int attempt = 1; attempt <= 3; attempt++) {
      try {
        System.out.println("Querying remote usb.ids for VID: 0x" + vendorIdHex + ", PID: 0x" + productIdHex + " (attempt " + attempt + ")...");
        HttpURLConnection conn = (HttpURLConnection) new URL(REMOTE_USB_IDS_URL).openConnection();
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(10000);
        conn.setRequestProperty("Accept", "text/plain");
        int responseCode = conn.getResponseCode();
        if (responseCode != HttpURLConnection.HTTP_OK) {
          throw new IOException("HTTP error code: " + responseCode);
        }
        try (InputStream in = conn.getInputStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
          String line;
          Integer currentVendorId = null;
          String vendorName = null;
          String productName = null;
          while ((line = reader.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            if (!line.startsWith("\t")) {
              try {
                String[] parts = line.split("\\s+", 2);
                if (parts.length == 2) {
                  currentVendorId = Integer.parseInt(parts[0], 16);
                  vendorName = parts[1];
                  if (currentVendorId == vendorId) {
                    VENDOR_MAP.putIfAbsent(vendorId, vendorName);
                  }
                }
              } catch (NumberFormatException e) {
                // Skip invalid vendor lines
              }
            } else if (currentVendorId != null && currentVendorId == vendorId && line.startsWith("\t")) {
              try {
                String[] parts = line.split("\\s+", 2);
                if (parts.length == 2 && Integer.parseInt(parts[0], 16) == productId) {
                  productName = parts[1];
                  PRODUCT_MAP.computeIfAbsent(vendorId, k -> new HashMap<>()).put(productId, productName);
                  break;
                }
              } catch (NumberFormatException e) {
                // Skip invalid product lines
              }
            }
          }
          return vendorName != null ? vendorName + ", " + (productName != null ? productName : "Unknown") : null;
        }
      } catch (IOException e) {
        System.err.println("Failed to query remote usb.ids for VID: 0x" + vendorIdHex + ", PID: 0x" + productIdHex + ": " + e.getMessage());
        if (attempt < 3) {
          try {
            Thread.sleep(1000 * attempt);
          } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
          }
        }
      }
    }
    return null;
  }

  private static void listDevicesUsingSystemCommand() {
    try {
      ProcessBuilder pb = new ProcessBuilder("powershell", "Get-PnpDevice -Class USB | Select-Object DeviceID, Description, Manufacturer, SerialNumber, Status, InstanceId | Format-List");
      Process process = pb.start();
      try (BufferedReader reader = process.inputReader()) {
        String vendorId = null, productId = null, description = null, manufacturer = null, serial = null, instanceId = null;
        Pattern pattern = Pattern.compile("(DeviceID|Description|Manufacturer|SerialNumber|Status|InstanceId)\\s*:\\s*(.*)");
        while (true) {
          String line = reader.readLine();
          if (line == null) break;
          line = line.trim();
          Matcher matcher = pattern.matcher(line);
          if (matcher.matches()) {
            String key = matcher.group(1);
            String value = matcher.group(2).trim();
            switch (key) {
              case "DeviceID":
                if (value.contains("VID_") && value.contains("PID_")) {
                  Pattern idPattern = Pattern.compile("VID_(\\w{4})&PID_(\\w{4})");
                  Matcher idMatcher = idPattern.matcher(value);
                  if (idMatcher.find()) {
                    vendorId = idMatcher.group(1);
                    productId = idMatcher.group(2);
                  }
                }
                break;
              case "Description":
                description = value;
                break;
              case "Manufacturer":
                manufacturer = value;
                break;
              case "SerialNumber":
                serial = value;
                break;
              case "InstanceId":
                instanceId = value;
                if (vendorId != null && productId != null) {
                  try {
                    int vId = Integer.parseInt(vendorId, 16);
                    int pId = Integer.parseInt(productId, 16);
                    if (vId >= 0 && vId <= 0xFFFF && pId >= 0 && pId <= 0xFFFF) {
                      printDeviceDetails(vId, pId, "");
                    }
                  } catch (NumberFormatException e) {
                    System.out.println("Skipping device with invalid IDs: Vendor:0x" + vendorId + ", Product:0x" + productId);
                  }
                  vendorId = productId = description = manufacturer = serial = instanceId = null;
                }
                break;
            }
          }
        }
      }
      if (!process.waitFor(3, TimeUnit.SECONDS)) {
        System.err.println("PowerShell command timed out");
        process.destroy();
      }
    } catch (Exception e) {
      System.err.println("Error running PowerShell command: " + e.getMessage());
    }
  }

  private static void printDeviceDetails(int vendorId, int productId, String indent) {
    try {
      String vendorIdHex = String.format("%04x", vendorId);
      String productIdHex = String.format("%04x", productId);
      String manufacturer = VENDOR_MAP.getOrDefault(vendorId, "Unknown");
      String product = PRODUCT_MAP.getOrDefault(vendorId, new HashMap<>()).getOrDefault(productId, "Unknown");
      String serialNumber = "Unknown";
      String deviceClass = "Unknown";
      String speed = "Unknown";
      String power = "Unknown";
      String busPort = "Unknown";
      String instanceId = "Unknown";

      // If vendor or product not found in local usb.ids, try remote lookup
      boolean vendorFound = VENDOR_MAP.containsKey(vendorId);
      boolean productFound = PRODUCT_MAP.getOrDefault(vendorId, new HashMap<>()).containsKey(productId);
      if (!vendorFound || !productFound) {
        String remoteInfo = lookupRemoteUsbId(vendorId, productId);
        if (remoteInfo != null) {
          String[] parts = remoteInfo.split(", ", 2);
          if (!vendorFound) {
            manufacturer = parts[0];
            VENDOR_MAP.putIfAbsent(vendorId, manufacturer);
          }
          if (!productFound && parts.length > 1) {
            product = parts[1];
            PRODUCT_MAP.computeIfAbsent(vendorId, k -> new HashMap<>()).put(productId, product);
          }
        }
      }

      // Use system command data as fallback
      Map<String, String> productInfo = SYSTEM_DEVICE_INFO.get(vendorIdHex);
      if (productInfo != null) {
        String sysDescription = productInfo.get(productIdHex);
        if (sysDescription != null) {
          String[] parts = sysDescription.split(", ");
          if ("Unknown".equals(manufacturer) && parts.length > 0) {
            manufacturer = parts[0].trim();
          }
          if ("Unknown".equals(product) && parts.length > 1 && !parts[1].startsWith("Serial:") && !parts[1].startsWith("Bus:") && !parts[1].startsWith("InstanceId:")) {
            product = parts[1].trim();
          }
          if ("Unknown".equals(serialNumber)) {
            for (String part : parts) {
              if (part.startsWith("Serial:")) {
                serialNumber = part.substring(7).trim();
                break;
              }
            }
          }
          if ("Unknown".equals(busPort)) {
            for (int i = 0; i < parts.length - 1; i++) {
              if (parts[i].startsWith("Bus:")) {
                busPort = parts[i].substring(5) + "/" + parts[i + 1].substring(5);
                break;
              }
            }
          }
          for (String part : parts) {
            if (part.startsWith("InstanceId:")) {
              instanceId = part.substring(11).trim();
              break;
            }
          }
        }
      }

      System.out.printf("%sDevice:%n" +
              "%s  Vendor ID: 0x%04x (%s)%n" +
              "%s  Product ID: 0x%04x (%s)%n" +
              "%s  Manufacturer: %s%n" +
              "%s  Product: %s%n" +
              "%s  Serial Number: %s%n" +
              "%s  Device Class: %s%n" +
              "%s  Speed: %s%n" +
              "%s  Power: %s%n" +
              "%s  Bus/Port: %s%n" +
              "%s  Instance ID: %s%n",
          indent, indent, vendorId, manufacturer,
          indent, productId, product,
          indent, manufacturer,
          indent, product,
          indent, serialNumber != null && !serialNumber.isEmpty() ? serialNumber : "Unknown",
          indent, deviceClass,
          indent, speed,
          indent, power,
          indent, busPort,
          indent, instanceId);
    } catch (Exception e) {
      System.err.println(indent + "Error retrieving device details: " + e.getMessage());
    }
  }

  private static void loadSystemDeviceInfo() {
    try {
      ProcessBuilder pb = new ProcessBuilder("powershell", "Get-PnpDevice -Class USB | Select-Object DeviceID, Description, Manufacturer, SerialNumber, Status, InstanceId | Format-List");
      Process process = pb.start();
      try (BufferedReader reader = process.inputReader()) {
        String vendorId = null, productId = null, description = null, manufacturer = null, serial = null, bus = null, port = null, instanceId = null;
        Pattern pattern = Pattern.compile("(DeviceID|Description|Manufacturer|SerialNumber|Status|InstanceId)\\s*:\\s*(.*)");
        while (true) {
          String line = reader.readLine();
          if (line == null) break;
          line = line.trim();
          Matcher matcher = pattern.matcher(line);
          if (matcher.matches()) {
            String key = matcher.group(1);
            String value = matcher.group(2).trim();
            switch (key) {
              case "DeviceID":
                if (value.contains("VID_") && value.contains("PID_")) {
                  Pattern idPattern = Pattern.compile("VID_(\\w{4})&PID_(\\w{4})");
                  Matcher idMatcher = idPattern.matcher(value);
                  if (idMatcher.find()) {
                    vendorId = idMatcher.group(1);
                    productId = idMatcher.group(2);
                  }
                }
                break;
              case "Description":
                description = value;
                break;
              case "Manufacturer":
                manufacturer = value;
                break;
              case "SerialNumber":
                serial = value;
                break;
              case "InstanceId":
                instanceId = value;
                if (vendorId != null && productId != null) {
                  try {
                    int vId = Integer.parseInt(vendorId, 16);
                    int pId = Integer.parseInt(productId, 16);
                    if (vId >= 0 && vId <= 0xFFFF && pId >= 0 && pId <= 0xFFFF) {
                      String fullDescription = (manufacturer != null && !manufacturer.isEmpty() ? manufacturer : "Unknown") +
                          ", " + (description != null && !description.isEmpty() ? description : "Unknown") +
                          (serial != null && !serial.isEmpty() ? ", Serial: " + serial : "") +
                          (instanceId != null && !instanceId.isEmpty() ? ", InstanceId: " + instanceId : "");
                      SYSTEM_DEVICE_INFO.computeIfAbsent(vendorId, k -> new HashMap<>()).put(productId, fullDescription);
                    }
                  } catch (NumberFormatException e) {
                    // Skip invalid IDs
                  }
                  vendorId = productId = description = manufacturer = serial = instanceId = null;
                }
                break;
            }
          }
        }
      }
      if (!process.waitFor(3, TimeUnit.SECONDS)) {
        System.err.println("PowerShell command timed out");
        process.destroy();
      }
    } catch (Exception e) {
      System.err.println("Error running PowerShell command: " + e.getMessage());
    }
  }
}
