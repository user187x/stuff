package xxx;

import com.fazecast.jSerialComm.SerialPort;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class LoraUtil {
 public int m_BaudRate;
 public SerialPort comPort;
 public SerialPort[] mSerialPort;
 private final ArrayList<SerialPort> mLoRaSerialPort = new ArrayList<>();
 public ArrayList<String> mLoRaSerialPortString = new ArrayList<>();
 public static String OS = System.getProperty("os.name");
 private String mSerialPortDescriptivePortName;
 private final Consumer<String> logger;

 private static final char[] hexArray = "0123456789ABCDEF".toCharArray();

 public LoraUtil(int i_BaudRate, Consumer<String> logger) {
  this.m_BaudRate = i_BaudRate;
  this.logger = logger;
 }

 private void log(String msg) {
  if (this.logger != null) {
   this.logger.accept(msg);
  } else {
   System.out.println(msg);
  }
 }

 // FIXED: Allow Linux/Mac to properly populate the COM ports
 public boolean CheckPIDVID(SerialPort iSerialPort) {
  if (OS.startsWith("Linux") || OS.startsWith("Mac")) {
   return true;
  }
  return OS.startsWith("Windows");
 }

 public ArrayList<String> serial_allPorts() {
  this.mLoRaSerialPort.clear();
  this.mLoRaSerialPortString.clear();
  this.mSerialPort = SerialPort.getCommPorts();

  for(int length = this.mSerialPort.length; length > 0; --length) {
   SerialPort port = this.mSerialPort[length - 1];
   this.log("SerialPort Detected: " + port.getDescriptivePortName() + ", PATH: " + port.getSystemPortName());
   if (this.CheckPIDVID(port)) {
    this.mLoRaSerialPort.addFirst(port);
    this.mLoRaSerialPortString.addFirst(port.getSystemPortName());
   }
  }
  return this.mLoRaSerialPortString;
 }

 public void SerialPort_setSerialPort(String iSerialPortDescriptivePortName) {
  this.mSerialPortDescriptivePortName = iSerialPortDescriptivePortName;
 }

 public boolean openConnection() {
  try {
   if (this.comPort != null) this.comPort.closePort();

   this.comPort = SerialPort.getCommPort(this.mSerialPortDescriptivePortName);
   this.comPort.setBaudRate(this.m_BaudRate);
   this.comPort.setComPortTimeouts(SerialPort.TIMEOUT_READ_BLOCKING, 100, 0);

   if (this.comPort.openPort()) {
    log("Port Opened Successfully: " + this.mSerialPortDescriptivePortName);
    return true;
   }
  } catch (Exception e) {
   log("Failed to open COM port: " + e.getMessage());
  }
  return false;
 }

 public void FunLora_close() {
  if (this.comPort != null && this.comPort.isOpen()) {
   this.comPort.closePort();
   log("Port Closed.");
  }
  this.comPort = null;
 }

 // --- AT COMMAND PROTOCOL CONTROLLERS ---

 public String sendATCommand(String command) {
  if (comPort == null || !comPort.isOpen()) return "";

  try {
   byte[] cmdBytes = (command + "\r\n").getBytes(StandardCharsets.UTF_8);
   comPort.writeBytes(cmdBytes, cmdBytes.length);
   log("--> " + command.trim());

   // Allow the device time to process and respond
   Thread.sleep(150);

   if (comPort.bytesAvailable() > 0) {
    byte[] readBuffer = new byte[comPort.bytesAvailable()];
    comPort.readBytes(readBuffer, readBuffer.length);
    String response = new String(readBuffer, StandardCharsets.UTF_8).trim();
    log("<-- " + response.replace("\r\n", " [CRLF] "));
    return response;
   }
  } catch (Exception e) {
   log("AT Command Error: " + e.getMessage());
  }
  return "";
 }

 public boolean enterATMode() {
  return sendATCommand("+++").contains("OK") || sendATCommand("").contains("OK");
 }

 public void exitATMode() {
  sendATCommand("AT+EXIT");
 }

 public String getFirmwareVersion() {
  String res = sendATCommand("AT+VER");
  return res.isEmpty() ? "Unknown" : res;
 }

 public String getDeviceAddress() {
  String res = sendATCommand("AT+ADDR=?");
  // Fallback to a placeholder if the device doesn't respond properly to the address query
  return res.isEmpty() ? "LORA_NODE" : res.replaceAll("[^0-9a-zA-Z]", "");
 }

 public void configureDevice(int channel, int powerDBm) {
  enterATMode();
  sendATCommand("AT+MODE=1"); // 1: Stream mode (Transparent transmission)
  sendATCommand("AT+TXCH=" + channel);
  sendATCommand("AT+RXCH=" + channel);
  sendATCommand("AT+PWR=" + powerDBm);
  exitATMode();
 }

 // --- TRANSPARENT DATA TRANSMISSION ---

 public void sendData(byte[] data) {
  if (comPort != null && comPort.isOpen()) {
   comPort.writeBytes(data, data.length);
  }
 }

 public byte[] readAvailableData() {
  if (comPort != null && comPort.isOpen() && comPort.bytesAvailable() > 0) {
   byte[] readBuffer = new byte[comPort.bytesAvailable()];
   comPort.readBytes(readBuffer, readBuffer.length);
   return readBuffer;
  }
  return null;
 }

 // --- UTILITIES ---

 public static String FunBytesToHex(byte[] bytes, char iSplit) {
  int hasSplit = (iSplit != '$') ? 3 : 2;
  char[] hexChars = new char[bytes.length * hasSplit];
  for(int j = 0; j < bytes.length; ++j) {
   int v = bytes[j] & 255;
   hexChars[j * hasSplit] = hexArray[v >>> 4];
   hexChars[j * hasSplit + 1] = hexArray[v & 15];
   if (hasSplit == 3) hexChars[j * 3 + 2] = iSplit;
  }
  return new String(hexChars);
 }

 public static String FunBytesToHexString(byte[] bytes, char iSplit) {
  StringBuilder d1 = new StringBuilder();
  for (byte aByte : bytes) {
   if (d1.length() > 0) d1.append(iSplit);
   d1.append(Integer.toHexString(aByte & 255));
  }
  return d1.toString();
 }

 public static String FunBytesToString(byte[] bytes, char iSplit) {
  StringBuilder d1 = new StringBuilder();
  for (byte aByte : bytes) {
   if (d1.length() > 0) d1.append(iSplit);
   d1.append(aByte & 255);
  }
  return d1.toString();
 }
}