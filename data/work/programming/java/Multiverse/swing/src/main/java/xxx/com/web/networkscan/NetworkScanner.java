package xxx.com.web.networkscan;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * An advanced Java application to scan the local network for active devices.
 * It identifies devices by attempting to connect to common ports (a more reliable
 * method than pinging), then finds their MAC address to determine the manufacturer,
 * and scans other ports to identify running services.
 */
public class NetworkScanner {

  // A map of common ports and the services they typically represent.
  private static final Map<Integer, String> COMMON_PORTS = new ConcurrentHashMap<>();
  // A list of ports to check to determine if a host is alive.
  private static final int[] DISCOVERY_PORTS = {22, 80, 443, 445, 8080};
  // A map of MAC address prefixes (OUIs) to known manufacturers.
  private static final Map<String, String> MAC_VENDOR_MAP = new ConcurrentHashMap<>();

  static {
    // Populate common ports for detailed scanning
    COMMON_PORTS.put(21, "FTP");
    COMMON_PORTS.put(22, "SSH");
    COMMON_PORTS.put(23, "Telnet");
    COMMON_PORTS.put(80, "HTTP (Web Server)");
    COMMON_PORTS.put(443, "HTTPS (Secure Web)");
    COMMON_PORTS.put(445, "SMB (Windows File Sharing)");
    COMMON_PORTS.put(548, "AFP (Apple File Sharing)");
    COMMON_PORTS.put(3389, "RDP (Remote Desktop)");
    COMMON_PORTS.put(5353, "mDNS (Bonjour/AirPlay)");
    COMMON_PORTS.put(62078, "Apple AirPlay 2");
    COMMON_PORTS.put(8008, "Google Cast");
    COMMON_PORTS.put(8009, "Google Cast");
    COMMON_PORTS.put(8080, "HTTP-alt (Web Server)");

    // Expanded list of common MAC address vendors
    MAC_VENDOR_MAP.put("DC:A6:32", "Google, Inc.");
    MAC_VENDOR_MAP.put("00:1A:A0", "Google, Inc."); // Chromecast
    MAC_VENDOR_MAP.put("B4:E6:2D", "Amazon Technologies");
    MAC_VENDOR_MAP.put("A8:DB:03", "Apple");
    MAC_VENDOR_MAP.put("00:03:93", "Apple");
    MAC_VENDOR_MAP.put("B8:27:EB", "Raspberry Pi Foundation");
    MAC_VENDOR_MAP.put("E0:B9:A5", "Samsung Electronics");
    MAC_VENDOR_MAP.put("40:A3:CC", "Samsung Electronics");
    MAC_VENDOR_MAP.put("F8:E0:79", "Sony");
    MAC_VENDOR_MAP.put("00:14:51", "Netgear");
    MAC_VENDOR_MAP.put("CC:46:D6", "Cisco");
    MAC_VENDOR_MAP.put("00:1F:90", "TP-LINK");
    MAC_VENDOR_MAP.put("00:0F:B5", "Dell");
    MAC_VENDOR_MAP.put("00:15:C5", "Intel Corporate");
    MAC_VENDOR_MAP.put("3C:D9:2B", "Hewlett Packard");
    MAC_VENDOR_MAP.put("00:E0:4C", "Realtek");
  }

  public static void main(String[] args) throws InterruptedException {
    System.out.println("Starting advanced network scan (using TCP connection method)...");

    String localIp = getLocalIpAddress();
    if (localIp == null) {
      System.err.println("Could not find a local IP address. Exiting.");
      return;
    }

    String subnet = localIp.substring(0, localIp.lastIndexOf('.'));
    System.out.println("Scanning network: " + subnet + ".0/24");

    final ExecutorService executorService = Executors.newFixedThreadPool(100);

    for (int i = 1; i <= 254; i++) {
      String host = subnet + "." + i;
      if (host.equals(localIp)) continue; // Skip scanning our own machine

      executorService.submit(() -> {
        // The new, more reliable method to check if a host is online.
        if (isHostAlive(host)) {
          try {
            InetAddress inetAddress = InetAddress.getByName(host);
            String hostname = inetAddress.getHostName();
            System.out.println("\nFound device: " + hostname + " (" + host + ")");

            String macAddress = getMacAddress(host);
            if (macAddress != null && !macAddress.isEmpty()) {
              String vendor = getVendorFromMac(macAddress);
              System.out.println("  MAC Address: " + macAddress + " (" + vendor + ")");
            }

            List<String> openPorts = scanPorts(host);
            if (!openPorts.isEmpty()) {
              System.out.println("  Open Ports: " + String.join(", ", openPorts));
            }
          } catch (IOException e) {
            // This might happen if hostname resolution fails, but we know the IP is active.
            System.out.println("\nFound device: " + host);
          }
        }
      });
    }

    executorService.shutdown();
    boolean finished = executorService.awaitTermination(15, TimeUnit.MINUTES);

    if (finished) {
      System.out.println("\n\nScan completed successfully.");
    } else {
      System.out.println("\n\nScan timed out. Not all devices may have been found.");
    }
  }

  /**
   * Checks if a host is reachable by attempting to establish a TCP connection
   * to a list of common ports. This is more reliable than ICMP pings.
   * @param host The IP address of the target host.
   * @return true if the host is alive, false otherwise.
   */
  private static boolean isHostAlive(String host) {
    for (int port : DISCOVERY_PORTS) {
      try (Socket socket = new Socket()) {
        socket.connect(new InetSocketAddress(host, port), 200); // 200ms timeout
        // Connection successful, host is alive.
        return true;
      } catch (IOException e) {
        // Connection failed, either timed out or was refused. Try next port.
      }
    }
    // None of the discovery ports responded.
    return false;
  }

  /**
   * Scans a list of common ports on a given host to see which are open.
   * @param host The IP address of the target host.
   * @return A list of strings describing the open ports and their services.
   */
  private static List<String> scanPorts(String host) {
    // Use a thread-safe list because it will be accessed by multiple threads.
    List<String> openPorts = Collections.synchronizedList(new ArrayList<>());
    ExecutorService portScannerExecutor = Executors.newFixedThreadPool(20);

    for (Integer port : COMMON_PORTS.keySet()) {
      portScannerExecutor.submit(() -> {
        try (Socket socket = new Socket()) {
          socket.connect(new InetSocketAddress(host, port), 200);
          openPorts.add(port + " [" + COMMON_PORTS.get(port) + "]");
        } catch (IOException e) {
          // Port is likely closed.
        }
      });
    }

    portScannerExecutor.shutdown();
    try {
      // Wait a bit for port scans to complete.
      portScannerExecutor.awaitTermination(10, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
    return openPorts;
  }

  /**
   * Attempts to retrieve the MAC address for a given IP by calling the system's ARP command.
   * @param ip The IP address of the device.
   * @return The MAC address as a string, or null if not found.
   */
  private static String getMacAddress(String ip) {
    Pattern macPattern = Pattern.compile("([0-9A-Fa-f]{2}[:-]){5}([0-9A-Fa-f]{2})");
    try {
      ProcessBuilder pb = new ProcessBuilder("arp", "-a", ip);
      Process process = pb.start();
      try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
        String line;
        while ((line = reader.readLine()) != null) {
          Matcher matcher = macPattern.matcher(line);
          if (matcher.find()) {
            return matcher.group(0).toUpperCase().replace("-", ":");
          }
        }
      }
    } catch (IOException e) {
      return null;
    }
    return null;
  }

  /**
   * Looks up the vendor from our internal map based on the MAC address.
   * @param macAddress The MAC address of the device.
   * @return The vendor name, or "Unknown Vendor" if not found.
   */
  private static String getVendorFromMac(String macAddress) {
    if (macAddress == null || macAddress.length() < 8) {
      return "Unknown Vendor";
    }
    String oui = macAddress.substring(0, 8);
    return MAC_VENDOR_MAP.getOrDefault(oui, "Unknown Vendor");
  }

  /**
   * Finds the primary local IP address of the machine.
   * @return The local IP address as a String, or null if not found.
   */
  private static String getLocalIpAddress() {
    try {
      Enumeration<NetworkInterface> nets = NetworkInterface.getNetworkInterfaces();
      for (NetworkInterface netint : Collections.list(nets)) {
        if (netint.isUp() && !netint.isLoopback() && !netint.isVirtual()) {
          Enumeration<InetAddress> addresses = netint.getInetAddresses();
          for (InetAddress inetAddress : Collections.list(addresses)) {
            if (inetAddress instanceof java.net.Inet4Address) {
              return inetAddress.getHostAddress();
            }
          }
        }
      }
    } catch (SocketException e) {
      System.err.println("SocketException while getting local IP: " + e.getMessage());
    }
    return null;
  }
}

