package xxx.waveshare;

import com.fazecast.jSerialComm.SerialPort;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

public class DeviceManager {

  private SerialPort serialPort;

  public void initialize() {

    System.out.println("Available Ports:");
    System.out.println("-----------------");

    Map<String, SerialPort> portMap = new HashMap<>();

    Arrays.stream(SerialPort.getCommPorts()).forEach(p -> {
      System.out.println(p.getSystemPortName() + " - " + p.getDescriptivePortName());
      portMap.put(p.getSystemPortName(), p);
    });

    System.out.println("Selected Port: ");
    Scanner scanner = new Scanner(System.in);
    String devicePort = scanner.nextLine();

    if (portMap.containsKey(devicePort)) {
      serialPort = SerialPort.getCommPort(devicePort);
    } else {
      serialPort = null;
      System.out.println("Port Not Found");
      System.exit(0);
    }

    // 3. Configure parameters
    serialPort.setBaudRate(115200);
    serialPort.setNumDataBits(8);
    serialPort.setNumStopBits(SerialPort.ONE_STOP_BIT);
    serialPort.setParity(SerialPort.NO_PARITY);

    // 4. Open the port
    if (serialPort.openPort()) {
      System.out.println("Port opened successfully!");
      System.out.println("Waiting for data...");
    } else {
      System.out.println("Failed to open port.");
      System.exit(1);
    }
  }

  public SerialPort getSerialPort() {
    return serialPort;
  }
}
