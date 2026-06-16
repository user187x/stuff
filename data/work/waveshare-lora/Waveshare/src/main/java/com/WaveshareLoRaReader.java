package com;

import com.fazecast.jSerialComm.SerialPort;
import com.fazecast.jSerialComm.SerialPortDataListener;
import com.fazecast.jSerialComm.SerialPortEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class WaveshareLoRaReader {

    private static String portName = "";
    private static SerialPort serialPort;

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        // 1. Determine which port to use
        if (args.length > 0) {
            portName = args[0];
            serialPort = SerialPort.getCommPort(portName);
        } else {
            serialPort = promptForAvailablePort(scanner);
            if (serialPort == null) {
                System.out.println("Exiting application.");
                scanner.close();
                return;
            }
            portName = serialPort.getSystemPortName();
        }

        // 2. Set port parameters
        serialPort.setBaudRate(115200);
        serialPort.setNumDataBits(8);
        serialPort.setNumStopBits(SerialPort.ONE_STOP_BIT);
        serialPort.setParity(SerialPort.NO_PARITY);
        serialPort.setFlowControl(SerialPort.FLOW_CONTROL_DISABLED);

        // 3. Open the serial port for active chat session
        if (serialPort.openPort()) {
            System.out.println("\n=================================================");
            System.out.println("Waveshare LoRa Connected on " + portName);
            System.out.println("Auto-Echo Mode: Active");
            System.out.println("Type your message and press ENTER to send.");
            System.out.println("Type 'exit' to quit.");
            System.out.println("=================================================");

            serialPort.addDataListener(new SerialPortReader());

            try { Thread.sleep(1500); } catch (Exception e) {}

            // 4. Interactive Input Loop
            while (true) {
                String input = scanner.nextLine();

                if ("exit".equalsIgnoreCase(input.trim())) {
                    System.out.println("Closing connection and shutting down...");
                    break;
                }

                sendMessage(input);
            }

        } else {
            System.err.println("Error: Failed to open " + portName + ". It may have just been grabbed by another process.");
        }

        scanner.close();
        if (serialPort != null && serialPort.isOpen()) {
            serialPort.closePort();
        }
    }

    /**
     * Scans all system ports, filters out bound/busy devices, and prompts the user.
     */
    private static SerialPort promptForAvailablePort(Scanner scanner) {
        SerialPort[] allPorts = SerialPort.getCommPorts();
        if (allPorts.length == 0) {
            System.err.println("No serial ports found on this system!");
            return null;
        }

        List<SerialPort> freePortsList = new ArrayList<>();
        System.out.println("\n--- Scanning & Verifying Serial Ports ---");

        for (SerialPort port : allPorts) {
            // Test if the port is currently bound/busy by trying to open it
            if (port.openPort()) {
                // If it opens successfully, it's free! Close it immediately so we can list it.
                port.closePort();
                freePortsList.add(port);
                System.out.println("  [AVAILABLE] " + port.getSystemPortName() + " (" + port.getDescriptivePortName() + ")");
            } else {
                // If openPort() returns false, another process or terminal instance has locked it
                System.out.println("  [LOCKED / IN USE] " + port.getSystemPortName() + " (" + port.getDescriptivePortName() + ")");
            }
        }

        if (freePortsList.isEmpty()) {
            System.err.println("\nAll connected serial devices are currently busy or locked by other apps!");
            return null;
        }

        // Display only the selectable, free ports to the user
        System.out.println("\n--- Select an Available Device ---");
        for (int i = 0; i < freePortsList.size(); i++) {
            SerialPort freePort = freePortsList.get(i);
            System.out.println((i + 1) + ". " + freePort.getSystemPortName());
        }

        System.out.print("\nEnter the number of the device to bind: ");
        int choice = -1;
        while (true) {
            try {
                String input = scanner.nextLine();
                choice = Integer.parseInt(input.trim());
                if (choice >= 1 && choice <= freePortsList.size()) {
                    break;
                }
                System.out.print("Invalid choice. Enter a number between 1 and " + freePortsList.size() + ": ");
            } catch (NumberFormatException e) {
                System.out.print("Please enter a valid number: ");
            }
        }

        return freePortsList.get(choice - 1);
    }

    public static void sendMessage(String message) {
        if (serialPort != null && serialPort.isOpen()) {
            String msgToSend = message + "\r\n";
            byte[] buffer = msgToSend.getBytes();
            serialPort.writeBytes(buffer, buffer.length);

            if (!message.startsWith("[Auto-Ack]")) {
                System.out.println("[Me]: " + message);
            }
        }
    }

    private static class SerialPortReader implements SerialPortDataListener {
        @Override
        public int getListeningEvents() { return SerialPort.LISTENING_EVENT_DATA_AVAILABLE; }

        @Override
        public void serialEvent(SerialPortEvent event) {
            if (event.getEventType() != SerialPort.LISTENING_EVENT_DATA_AVAILABLE) return;

            SerialPort port = event.getSerialPort();
            int bytesAvailable = port.bytesAvailable();

            if (bytesAvailable > 0) {
                byte[] readBuffer = new byte[bytesAvailable];
                int numRead = port.readBytes(readBuffer, readBuffer.length);

                if (numRead > 0) {
                    String receivedData = new String(readBuffer, 0, numRead).trim();
                    if (receivedData.isEmpty()) return;

                    System.out.print("\n[Received]: " + receivedData + "\n");

                    if (receivedData.startsWith("[Auto-Ack]")) {
                        return;
                    }

                    try {
                        Thread.sleep(200);
                        String ackPayload = "[Auto-Ack] Message delivered successfully to room hardware (" + portName + ")";
                        sendMessage(ackPayload);
                    } catch (Exception e) {}
                }
            }
        }
    }
}