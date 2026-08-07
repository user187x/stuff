package xxx.waveshare;

import com.fazecast.jSerialComm.SerialPort;
import com.fazecast.jSerialComm.SerialPortDataListener;
import com.fazecast.jSerialComm.SerialPortEvent;

public class WaveshareLoRaReader {

    // Change this to your specific COM port or /dev/ttyUSB0 (Linux)
    private static final String PORT_NAME = "COM3";
    private static SerialPort serialPort;

    public static void main(String[] args) {
        // 1. Initialize the port via factory method
        serialPort = SerialPort.getCommPort(PORT_NAME);

        // 2. Set port parameters (Waveshare default: 115200, 8N1)
        serialPort.setBaudRate(115200);
        serialPort.setNumDataBits(8);
        serialPort.setNumStopBits(SerialPort.ONE_STOP_BIT);
        serialPort.setParity(SerialPort.NO_PARITY);

        // 3. Set flow control to None
        serialPort.setFlowControl(SerialPort.FLOW_CONTROL_DISABLED);

        // 4. Open the serial port and verify success
        if (serialPort.openPort()) {
            System.out.println("Waveshare LoRa Connected. Listening for data...");

            // 5. Add event listener to handle received LoRa messages
            serialPort.addDataListener(new SerialPortReader());

            // Example: Send a transmission packet
            sendMessage("Hello from Java LoRa!");
        } else {
            System.err.println("Error setting up serial port: Failed to open " + PORT_NAME);
        }
    }

    // Method to send text/data via LoRa
    public static void sendMessage(String message) {
        if (serialPort != null && serialPort.isOpen()) {
            byte[] buffer = message.getBytes();
            int bytesWritten = serialPort.writeBytes(buffer, buffer.length);
            if (bytesWritten > 0) {
                System.out.println("Sent: " + message);
            } else {
                System.err.println("Error writing to serial port: No bytes written.");
            }
        } else {
            System.err.println("Error writing to serial port: Port is closed.");
        }
    }

    // Class to handle incoming serial events using jSerialComm interfaces
    private static class SerialPortReader implements SerialPortDataListener {

        // Define the events we want to listen for (Data Available)
        @Override
        public int getListeningEvents() {
            return SerialPort.LISTENING_EVENT_DATA_AVAILABLE;
        }

        @Override
        public void serialEvent(SerialPortEvent event) {
            // Verify event type matches our configuration
            if (event.getEventType() != SerialPort.LISTENING_EVENT_DATA_AVAILABLE) {
                return;
            }

            SerialPort port = event.getSerialPort();
            int bytesAvailable = port.bytesAvailable();

            if (bytesAvailable > 0) {
                byte[] readBuffer = new byte[bytesAvailable];
                int numRead = port.readBytes(readBuffer, readBuffer.length);

                if (numRead > 0) {
                    String receivedData = new String(readBuffer, 0, numRead);
                    System.out.print("Received: " + receivedData);
                }
            }
        }
    }
}
