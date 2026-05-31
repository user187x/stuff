package xxx.com.web.networkscan;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class PortScanner {
  private static final int TIMEOUT_MS = 1000;
  private static final int THREAD_POOL_SIZE = 50;

  public static void main(String[] args) {
    String host = "localhost"; // Default host
    int startPort = 1;
    int endPort = 1024;

    // Check for command-line arguments
    if (args.length >= 3) {
      host = args[0];
      try {
        startPort = Integer.parseInt(args[1]);
        endPort = Integer.parseInt(args[2]);
      } catch (NumberFormatException e) {
        System.err.println("Invalid port numbers. Using default range 1-1024.");
      }
    }

    System.out.println("Scanning ports on " + host + " from " + startPort + " to " + endPort + "...");
    List<Integer> openPorts = scanPorts(host, startPort, endPort);

    if (openPorts.isEmpty()) {
      System.out.println("No open ports found.");
    } else {
      System.out.println("Open ports found: " + openPorts);
    }
  }

  public static List<Integer> scanPorts(String host, int startPort, int endPort) {
    List<Integer> openPorts = new ArrayList<>();
    ExecutorService executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);

    for (int port = startPort; port <= endPort; port++) {
      final int currentPort = port;
      executor.submit(() -> {
        try (Socket socket = new Socket()) {
          socket.connect(new InetSocketAddress(host, currentPort), TIMEOUT_MS);
          synchronized (openPorts) {
            openPorts.add(currentPort);
          }
          System.out.println("Port " + currentPort + " is open");
        } catch (Exception e) {
          // Port is closed or unreachable
        }
      });
    }

    executor.shutdown();
    try {
      executor.awaitTermination(60, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      System.err.println("Scan interrupted: " + e.getMessage());
    }

    return openPorts;
  }
}
