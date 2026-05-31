package xxx.com.usb;

import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Scanner;

import com.fazecast.jSerialComm.SerialPort;
import xxx.com.gps.nmea.NmeaSentence;
import xxx.com.gps.nmea.NmeaSentenceFactory;

public class UsbGpsReader {

  public static void main(String[] args) throws Exception {
    UsbGpsReader.run();
  }

  public static void run() throws Exception {
    SerialPort.autoCleanupAtShutdown();
    Optional<SerialPort> optional = searchCommPort();

    if (optional.isPresent()) {
      SerialPort serialPort = optional.get();
      serialPort.openPort(3000);
      if (!serialPort.isOpen()) {
        throw new Exception("GPS Device Not Working Properly");
      }
      listenGPS(serialPort);
    } else {
      System.out.println("GPS Device Not found");
    }
  }

  private static void listenGPS(SerialPort comPort) {
    try (InputStream inputStream = comPort.getInputStream()) {
      try (Scanner scanner = new Scanner(inputStream)) {
        while (scanner.hasNextLine()) {
          processData(scanner.nextLine());
        }
      } catch (Exception ex) {
        throw ex;
      }
    } catch (Exception e) {
      System.out.println("Failure Reading Serial Port : " + e.getMessage());
    }
  }

  private static Optional<SerialPort> searchCommPort() {
    List<SerialPort> availableCommPorts = Arrays.asList(SerialPort.getCommPorts());
    return availableCommPorts.stream()
        .peek(s -> System.out.println("Searching Serial Ports..."))
        .filter(s -> s.getDescriptivePortName().contains("GPS"))
        .peek(s -> System.out.println("GPS Receiver Found : " + s.getSystemPortName()))
        .peek(s -> System.out.println("Device : " + s.getDescriptivePortName().toUpperCase()))
        .findAny();
  }

  private static void processData(String line) {
    NmeaSentenceFactory.createSentence(line).ifPresent(NmeaSentence::print);
  }
}
