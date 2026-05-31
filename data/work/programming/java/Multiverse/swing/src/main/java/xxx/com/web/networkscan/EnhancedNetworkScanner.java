package xxx.com.web.networkscan;

import org.apache.commons.net.util.SubnetUtils;

import java.net.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class EnhancedNetworkScanner {

  private static final int TIMEOUT_MS = 500;
  private static final int THREAD_POOL_SIZE = 100;
  private static final int[] PROBE_PORTS = {22, 80, 139, 443, 445, 3389, 5900};

  public static void main(String[] args) {
    String subnet;
    try {
      subnet = getSubnet();
    } catch (UnknownHostException e) {
      System.err.println("Error determining subnet: " + e.getMessage());
      return;
    }

    SubnetUtils utils = new SubnetUtils(subnet + ".0/24");
    String[] allIPs = utils.getInfo().getAllAddresses();
    System.out.println("Scanning " + allIPs.length + " possible addresses in subnet " + subnet + ".0/24...");

    List<String> connectedDevices = scanNetwork(Arrays.asList(allIPs));

    if (connectedDevices.isEmpty()) {
      System.out.println("No connected devices found.");
    } else {
      System.out.println("Connected devices found (" + connectedDevices.size() + "):");
      for (String ip : connectedDevices) {
        System.out.println(ip);
      }
    }
  }

  private static String getSubnet() throws UnknownHostException {
    InetAddress localHost = InetAddress.getLocalHost();
    byte[] ipAddr = localHost.getAddress();
    return String.format("%d.%d.%d", (ipAddr[0] & 0xFF), (ipAddr[1] & 0xFF), (ipAddr[2] & 0xFF));
  }

  public static List<String> scanNetwork(List<String> ips) {
    List<String> connectedDevices = new ArrayList<>();
    ExecutorService executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);

    for (String ip : ips) {
      executor.submit(() -> {
        boolean isUp = false;
        try {
          // Try ICMP reachability first
          InetAddress address = InetAddress.getByName(ip);
          if (address.isReachable(TIMEOUT_MS)) {
            isUp = true;
            System.out.println("Device found at " + ip + " (ICMP reachable)");
          } else {
            // Fall back to TCP port probing
            for (int port : PROBE_PORTS) {
              try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(ip, port), TIMEOUT_MS);
                isUp = true;
                System.out.println("Device found at " + ip + " (port " + port + " open)");
                break;
              } catch (ConnectException e) {
                isUp = true; // Device responded, but port is closed
                System.out.println("Device found at " + ip + " (port " + port + " closed but responsive)");
                break;
              } catch (Exception e) {
                // Continue to next port
              }
            }
          }
        } catch (Exception e) {
          // Host not reachable
        }
        if (isUp) {
          synchronized (connectedDevices) {
            connectedDevices.add(ip);
          }
        }
      });
    }

    executor.shutdown();
    try {
      executor.awaitTermination(300, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      System.err.println("Scan interrupted: " + e.getMessage());
    }

    return connectedDevices;
  }
}
