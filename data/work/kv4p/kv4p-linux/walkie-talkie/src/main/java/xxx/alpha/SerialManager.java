package xxx.alpha;

import com.fazecast.jSerialComm.SerialPort;
import com.fazecast.jSerialComm.SerialPortDataListener;
import com.fazecast.jSerialComm.SerialPortEvent;

public class SerialManager {
    private SerialPort serialPort;

    public boolean connect(String portName) {
        serialPort = SerialPort.getCommPort(portName);
        serialPort.setBaudRate(115200); // Standard for ESP32 serial bridging
        serialPort.setNumDataBits(8);
        serialPort.setNumStopBits(1);
        serialPort.setParity(SerialPort.NO_PARITY);

        if (serialPort.openPort()) {
            serialPort.addDataListener(new SerialPortDataListener() {
                @Override
                public int getListeningEvents() {
                    return SerialPort.LISTENING_EVENT_DATA_AVAILABLE;
                }

                @Override
                public void serialEvent(SerialPortEvent event) {
                    if (event.getEventType() != SerialPort.LISTENING_EVENT_DATA_AVAILABLE) return;

                    byte[] newData = new byte[serialPort.bytesAvailable()];
                    int numRead = serialPort.readBytes(newData, newData.length);
                    // Route to audio decoder / APRS packet parser
                    processIncomingData(newData, numRead);
                }
            });
            return true;
        }
        return false;
    }

    private void processIncomingData(byte[] data, int length) {
        // Demux the KISS framing / Audio packets here
    }

    public void transmit(byte[] payload) {
        if (serialPort != null && serialPort.isOpen()) {
            serialPort.writeBytes(payload, payload.length);
        }
    }

    public void close() {
        if (serialPort != null) {
            serialPort.closePort();
        }
    }
}