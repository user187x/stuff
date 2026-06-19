package xxx.waveshare;

import com.fazecast.jSerialComm.SerialPort;
import com.fazecast.jSerialComm.SerialPortDataListener;
import com.fazecast.jSerialComm.SerialPortEvent;
import java.util.function.Consumer;

public class Listener {

 private static final StringBuilder buffer = new StringBuilder();

 public static void listen(SerialPort serialPort, Consumer<String> onLineReceived) {
  serialPort.addDataListener(new SerialPortDataListener() {

   @Override
   public int getListeningEvents() { return SerialPort.LISTENING_EVENT_DATA_AVAILABLE; }

   @Override
   public void serialEvent(SerialPortEvent event) {
    if (event.getEventType() != SerialPort.LISTENING_EVENT_DATA_AVAILABLE)
     return;

    byte[] newData = new byte[serialPort.bytesAvailable()];
    int numRead = serialPort.readBytes(newData, newData.length);

    String incoming = new String(newData, 0, numRead);
    buffer.append(incoming);

    // Check if we have a complete line (terminated by \n)
    int newlineIdx;
    while ((newlineIdx = buffer.indexOf("\n")) != -1) {
     String line = buffer.substring(0, newlineIdx).trim();
     buffer.delete(0, newlineIdx + 1); // remove processed line

     if (!line.isEmpty() && onLineReceived != null) {
      onLineReceived.accept(line);
     }
    }
   }
  });
 }
}