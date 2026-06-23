package xxx.com.usb;

import com.fazecast.jSerialComm.SerialPort;
import java.util.Arrays;
import java.util.List;

public class UsbDeviceFinder {

  public static void main(String[] args) {

    List<SerialPort> availableCommPorts = Arrays.asList(SerialPort.getCommPorts());
    availableCommPorts.forEach(
        s -> System.out.println("Device : " + s.getDescriptivePortName().toUpperCase()));
  }
}
