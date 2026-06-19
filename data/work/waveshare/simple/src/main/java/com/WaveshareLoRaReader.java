package com;

import com.fazecast.jSerialComm.SerialPort;
import com.fazecast.jSerialComm.SerialPortDataListener;
import com.fazecast.jSerialComm.SerialPortEvent;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.UUID;

public class WaveshareLoRaReader {

    // ---------------------------------------------------------------------
    //  MESSAGE PROTOCOL
    //
    //  Every frame put on the air looks like:
    //
    //        <NODE_ID>|<TYPE>|<PAYLOAD>\r\n
    //
    //  e.g.  7F3A9C|MSG|Hello from the lab
    //        7F3A9C|ACK|Delivered to room hardware (COM5)
    //
    //  NODE_ID is a random id generated once per run. Because the id is
    //  stamped onto everything THIS node transmits, a receiver can compare
    //  the incoming sender id against its own NODE_ID. If they match, the
    //  frame is simply this node hearing its own transmission echoed back
    //  over the radio, so it is dropped instead of being treated as a new
    //  message (which previously caused the node to "talk to itself" and
    //  fire off acknowledgments for its own traffic).
    // ---------------------------------------------------------------------
    private static final String NODE_ID = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
    private static final String DELIM    = "|";
    private static final String TYPE_MSG = "MSG"; // a normal chat message (gets acknowledged)
    private static final String TYPE_ACK = "ACK"; // an acknowledgment (never acknowledged back)

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

            // Show everything about the device the user just bound to,
            // BEFORE we let them start sending messages.
            printDeviceInfo(serialPort);

            System.out.println("\n=================================================");
            System.out.println("Waveshare LoRa Connected on " + portName);
            System.out.println("Auto-Echo Mode: Active   |   This Node ID: " + NODE_ID);
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

    /**
     * Prints a full readout of the device the user just bound to. Called once,
     * after the port is opened and configured but before the chat loop starts.
     */
    private static void printDeviceInfo(SerialPort port) {
        System.out.println("\n========== CONNECTED DEVICE INFORMATION ==========");
        System.out.println("  This Node ID         : " + NODE_ID + "   (stamped on every outgoing message)");
        System.out.println();
        System.out.println("  -- Identity --");
        System.out.println("  System Port Name     : " + port.getSystemPortName());
        System.out.println("  Full System Path     : " + port.getSystemPortPath());
        System.out.println("  Descriptive Name     : " + port.getDescriptivePortName());
        System.out.println("  Port Description     : " + port.getPortDescription());
        System.out.println("  Physical Location    : " + port.getPortLocation());
        //System.out.println("  Device Driver        : " + port.getDeviceDriver());
        System.out.println("  Manufacturer         : " + port.getManufacturer());
        System.out.println("  Serial Number        : " + port.getSerialNumber());
        System.out.println("  Vendor ID  (VID)     : " + formatUsbId(port.getVendorID()));
        System.out.println("  Product ID (PID)     : " + formatUsbId(port.getProductID()));
        System.out.println();
        System.out.println("  -- Serial Parameters --");
        System.out.println("  Baud Rate            : " + port.getBaudRate());
        System.out.println("  Data Bits            : " + port.getNumDataBits());
        System.out.println("  Stop Bits            : " + describeStopBits(port.getNumStopBits()));
        System.out.println("  Parity               : " + describeParity(port.getParity()));
        System.out.println("  Flow Control         : " + describeFlowControl(port.getFlowControlSettings()));
        System.out.println("  Read Timeout (ms)    : " + port.getReadTimeout());
        System.out.println("  Write Timeout (ms)   : " + port.getWriteTimeout());
        System.out.println();
        System.out.println("  -- Status --");
        System.out.println("  Port Open            : " + port.isOpen());
        System.out.println("  jSerialComm Version  : " + SerialPort.getVersion());
        System.out.println("==================================================");
    }

    /** USB VID/PID come back as -1 for non-USB ports; otherwise show them as 0xXXXX. */
    private static String formatUsbId(int id) {
        return (id == -1) ? "N/A (not USB-based)" : String.format("0x%04X", id);
    }

    private static String describeStopBits(int v) {
        switch (v) {
            case SerialPort.ONE_STOP_BIT:              return "1";
            case SerialPort.ONE_POINT_FIVE_STOP_BITS:  return "1.5";
            case SerialPort.TWO_STOP_BITS:             return "2";
            default:                                   return "Unknown (" + v + ")";
        }
    }

    private static String describeParity(int v) {
        switch (v) {
            case SerialPort.NO_PARITY:    return "None";
            case SerialPort.ODD_PARITY:   return "Odd";
            case SerialPort.EVEN_PARITY:  return "Even";
            case SerialPort.MARK_PARITY:  return "Mark";
            case SerialPort.SPACE_PARITY: return "Space";
            default:                      return "Unknown (" + v + ")";
        }
    }

    private static String describeFlowControl(int flags) {
        if (flags == SerialPort.FLOW_CONTROL_DISABLED) return "Disabled";
        List<String> on = new ArrayList<>();
        if ((flags & SerialPort.FLOW_CONTROL_RTS_ENABLED)         != 0) on.add("RTS");
        if ((flags & SerialPort.FLOW_CONTROL_CTS_ENABLED)         != 0) on.add("CTS");
        if ((flags & SerialPort.FLOW_CONTROL_DSR_ENABLED)         != 0) on.add("DSR");
        if ((flags & SerialPort.FLOW_CONTROL_DTR_ENABLED)         != 0) on.add("DTR");
        if ((flags & SerialPort.FLOW_CONTROL_XONXOFF_IN_ENABLED)  != 0) on.add("XON/XOFF In");
        if ((flags & SerialPort.FLOW_CONTROL_XONXOFF_OUT_ENABLED) != 0) on.add("XON/XOFF Out");
        return on.isEmpty() ? "Disabled" : String.join(", ", on);
    }

    // ------------------------------------------------------------------
    //  SENDING
    // ------------------------------------------------------------------

    /** Low-level: stamp our NODE_ID + type onto a payload and push it out the port. */
    private static void sendFrame(String type, String payload) {
        if (serialPort != null && serialPort.isOpen()) {
            String frame = NODE_ID + DELIM + type + DELIM + payload + "\r\n";
            byte[] buffer = frame.getBytes(StandardCharsets.UTF_8);
            serialPort.writeBytes(buffer, buffer.length);
        }
    }

    /** Public: send a user-typed chat message. */
    public static void sendMessage(String text) {
        sendFrame(TYPE_MSG, text);
        System.out.println("[Me]: " + text);
    }

    /** Send an automatic acknowledgment back to whoever sent us a message. */
    private static void sendAck(String toNodeId) {
        sendFrame(TYPE_ACK, "Message delivered successfully to room hardware (" + portName + ") for node " + toNodeId);
    }

    // ------------------------------------------------------------------
    //  RECEIVING
    // ------------------------------------------------------------------

    private static class SerialPortReader implements SerialPortDataListener {
        // Buffer to hold fragmented incoming data
        private final StringBuilder messageBuffer = new StringBuilder();

        @Override
        public int getListeningEvents() {
            return SerialPort.LISTENING_EVENT_DATA_AVAILABLE;
        }

        @Override
        public void serialEvent(SerialPortEvent event) {
            if (event.getEventType() != SerialPort.LISTENING_EVENT_DATA_AVAILABLE) return;

            SerialPort port = event.getSerialPort();
            int bytesAvailable = port.bytesAvailable();

            if (bytesAvailable > 0) {
                byte[] readBuffer = new byte[bytesAvailable];
                int numRead = port.readBytes(readBuffer, readBuffer.length);

                if (numRead > 0) {
                    // Append the new chunk to our buffer
                    String chunk = new String(readBuffer, 0, numRead, StandardCharsets.UTF_8);
                    messageBuffer.append(chunk);

                    // Process every complete line (denoted by a newline) in the buffer
                    int newlineIndex;
                    while ((newlineIndex = messageBuffer.indexOf("\n")) != -1) {
                        // Extract the full line up to the newline, then drop it from the buffer
                        String line = messageBuffer.substring(0, newlineIndex).trim();
                        messageBuffer.delete(0, newlineIndex + 1);

                        if (line.isEmpty()) continue;

                        handleFrame(line);
                    }
                }
            }
        }

        /** Parse one complete line of the form NODE_ID|TYPE|PAYLOAD and act on it. */
        private void handleFrame(String line) {
            // Split into at most 3 parts so the payload itself may contain '|'
            String[] parts = line.split("\\|", 3);

            if (parts.length < 3) {
                // Not one of our frames (e.g. raw terminal output). Show it, but
                // never acknowledge something we can't identify the sender of.
                System.out.print("\n[Received - unrecognized format]: " + line + "\n");
                return;
            }

            String senderId = parts[0];
            String type     = parts[1];
            String payload  = parts[2];

            // (1) THE KEY FIX: if the sender id is our own, this is just our
            //     transmission coming back over the radio. Ignore it entirely.
            if (NODE_ID.equals(senderId)) {
                return;
            }

            // An acknowledgment from the other side: display it, but do NOT ack
            // an ack (that would bounce acks back and forth forever).
            if (TYPE_ACK.equals(type)) {
                System.out.print("\n[Ack from " + senderId + "]: " + payload + "\n");
                return;
            }

            // Otherwise treat it as a real, deliverable message from another node.
            System.out.print("\n[" + senderId + "]: " + payload + "\n");

            try {
                Thread.sleep(200);
                sendAck(senderId);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}