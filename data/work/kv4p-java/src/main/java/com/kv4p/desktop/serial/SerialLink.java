package com.kv4p.desktop.serial;

import static com.kv4p.desktop.protocol.Kv4pProtocol.SERIAL_BAUD;

import com.fazecast.jSerialComm.SerialPort;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

/**
 * USB-CDC transport to the kv4p HT, replacing the Android usb-serial-for-android stack. Uses
 * jSerialComm so the same jar runs on Windows, macOS and Linux.
 */
public final class SerialLink implements AutoCloseable {

  private final SerialPort port;
  private Thread reader;
  private volatile boolean running;

  private SerialLink(SerialPort port) {
    this.port = port;
  }

  public static List<SerialPort> availablePorts() {
    return Arrays.asList(SerialPort.getCommPorts());
  }

  public static SerialLink open(SerialPort selected) throws IOException {
    selected.setComPortParameters(SERIAL_BAUD, 8, SerialPort.ONE_STOP_BIT, SerialPort.NO_PARITY);
    selected.setComPortTimeouts(SerialPort.TIMEOUT_READ_BLOCKING, 100, 0);
    if (!selected.openPort()) {
      throw new IOException("Could not open " + selected.getSystemPortName());
    }
    return new SerialLink(selected);
  }

  public void resetDevice() {
    try {
      // Assert RTS and clear DTR to pull EN (Enable/Reset) low
      port.clearDTR();
      port.setRTS();
      Thread.sleep(100);

      // Clear both to allow the chip to boot normally (IO0 high)
      port.clearDTR();
      port.clearRTS();

      // Give the microcontroller a moment to boot and transmit HELLO
      Thread.sleep(100);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  /** Start a background reader delivering raw byte chunks to {@code sink}. */
  public void startReader(Consumer<byte[]> sink, Consumer<Exception> onError) {
    running = true;
    reader =
        Thread.ofVirtual()
            .name("kv4p-serial-rx")
            .start(
                () -> {
                  byte[] buf = new byte[4096];
                  while (running && port.isOpen()) {
                    int n = port.readBytes(buf, buf.length);
                    if (n > 0) {
                      sink.accept(Arrays.copyOf(buf, n));
                    } else if (n < 0) {
                      if (running) {
                        onError.accept(new IOException("Serial read failed (device unplugged?)"));
                      }
                      return;
                    }
                  }
                });
  }

  public synchronized void write(byte[] frame) throws IOException {
    int written = port.writeBytes(frame, frame.length);
    if (written != frame.length) {
      throw new IOException("Short serial write: " + written + "/" + frame.length);
    }
  }

  public String portName() {
    return port.getSystemPortName();
  }

  @Override
  public void close() {
    running = false;
    port.closePort();
    if (reader != null) {
      try {
        reader.join(500);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }
  }
}
