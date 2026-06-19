package xxx.waveshare;

import com.fazecast.jSerialComm.SerialPort;

public class Sender {

 private final SerialPort serialPort;

 public Sender(SerialPort serialPort) {
  this.serialPort = serialPort;
 }

 public void send(String message) {
  serialPort.writeBytes(message.getBytes(), message.getBytes().length);
 }
}
