package xxx.simple;

import com.fazecast.jSerialComm.SerialPort;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

/**
 * Owns the serial-port lifecycle (discovery, configuration, open/close). This
 * is the transport layer only: it knows nothing about radio commands, framing
 * or messaging.
 *
 * The standard UART settings for the Waveshare USB-TO-LoRa dongle are
 * 115200 baud, 8 data bits, 1 stop bit, no parity.
 */
public class DeviceManager {

    public static final int BAUD = 115200;

    private SerialPort serialPort;

    /** Names of all serial ports currently present. */
    public static List<String> listPorts() {
        List<String> names = new ArrayList<>();
        for (SerialPort p : SerialPort.getCommPorts()) names.add(p.getSystemPortName());
        return names;
    }

    /**
     * Interactive selection + open, preserved from the original CLI flow.
     * Exits the JVM on a missing port or open failure (CLI behaviour).
     */
    public void initialize() {
        System.out.println("Available Ports:");
        System.out.println("-----------------");

        Map<String, SerialPort> portMap = new HashMap<>();
        Arrays.stream(SerialPort.getCommPorts()).forEach(p -> {
            System.out.println(p.getSystemPortName() + " - " + p.getDescriptivePortName());
            portMap.put(p.getSystemPortName(), p);
        });

        System.out.print("Selected Port: ");
        Scanner scanner = new Scanner(System.in);
        String devicePort = scanner.nextLine().trim();

        if (!portMap.containsKey(devicePort)) {
            serialPort = null;
            System.out.println("Port Not Found");
            System.exit(0);
        }

        serialPort = SerialPort.getCommPort(devicePort);
        configure(serialPort);

        if (serialPort.openPort()) {
            System.out.println("Port opened successfully!");
            System.out.println("Waiting for data...");
        } else {
            System.out.println("Failed to open port.");
            System.exit(1);
        }
    }

    /**
     * Programmatic open used by the GUI and tests. Returns false instead of
     * exiting so callers can handle errors gracefully.
     */
    public boolean open(String portName) {
        if (portName == null || portName.isBlank()) return false;
        serialPort = SerialPort.getCommPort(portName);
        configure(serialPort);
        return serialPort.openPort();
    }

    public void close() {
        if (serialPort != null && serialPort.isOpen()) {
            serialPort.removeDataListener();
            serialPort.closePort();
        }
        serialPort = null;
    }

    public SerialPort getSerialPort() {
        return serialPort;
    }

    private static void configure(SerialPort port) {
        port.setBaudRate(BAUD);
        port.setNumDataBits(8);
        port.setNumStopBits(SerialPort.ONE_STOP_BIT);
        port.setParity(SerialPort.NO_PARITY);
    }
}
