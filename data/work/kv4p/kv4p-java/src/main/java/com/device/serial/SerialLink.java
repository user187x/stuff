package com.device.serial;

import static com.device.protocol.Protocol.SERIAL_BAUD;

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
    // Re-resolve a fresh handle by device path: reusing a SerialPort object from an old
    // scan (or from a previous connect in the same session) can fail to reopen after the
    // device re-enumerates or a prior close is still settling in the kernel.
    SerialPort port = SerialPort.getCommPort(selected.getSystemPortPath());
    port.setComPortParameters(SERIAL_BAUD, 8, SerialPort.ONE_STOP_BIT, SerialPort.NO_PARITY);
    port.setComPortTimeouts(SerialPort.TIMEOUT_READ_BLOCKING, 100, 0);

    // A busy port is often transient: ModemManager probes new USB serial adapters for a
    // few seconds after plug-in, and a just-closed fd can take a moment to release.
    int attempts = 5;
    int lastError = 0;
    for (int attempt = 1; attempt <= attempts; attempt++) {
      if (port.openPort()) {
        return new SerialLink(port);
      }
      lastError = port.getLastErrorCode();
      try {
        Thread.sleep(250);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        break;
      }
    }
    throw new IOException(
        "Could not open " + port.getSystemPortPath()
            + " after " + attempts + " attempts (OS error " + lastError + ")."
            + diagnose(lastError));
  }

  private static String diagnose(int errno) {
    return switch (errno) {
      case 13 -> " Permission denied: add your user to the serial group"
          + " (usually 'dialout' or 'uucp') and log out/in, e.g."
          + " sudo usermod -aG dialout $USER";
      case 16, 11 -> " Port is busy: another program has it open. Check for a previous"
          + " instance of this app still running, ModemManager probing the device"
          + " (sudo systemctl stop ModemManager), or brltty claiming the USB adapter"
          + " (sudo systemctl stop brltty brltty-udev; common with CH340/CP210x).";
      case 2 -> " Device node no longer exists: the adapter re-enumerated or was"
          + " unplugged. Click 'Refresh Devices' and reconnect.";
      default -> " If this persists, check that no other program holds the port and"
          + " that your user has permission to access serial devices.";
    };
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

      // Leave DTR and RTS asserted for the rest of the session, mirroring the reference
      // Android app ("needed for better data transfer"). Boards with a native USB-CDC
      // ESP32 (e.g. QT Py ESP32-S2 builds) discard their serial output while the host
      // holds DTR deasserted, which silently kills HELLO/audio delivery. On classic
      // UART-bridge boards the two auto-reset transistors cancel when both lines are
      // asserted, so this is harmless there.
      // Order matters: assert DTR first — raising RTS alone (RTS=1, DTR=0) would pull
      // EN low and reset the chip again.
      port.setDTR();
      port.setRTS();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  /** Start a background reader delivering raw byte chunks to {@code sink}. */
  public void startReader(Consumer<byte[]> sink, Consumer<Exception> onError) {
    running = true;
    // Platform thread, not virtual: this loop blocks in native readBytes() calls
    // back-to-back and never reaches a Java blocking point, so as a virtual thread it
    // would pin a carrier permanently and starve every other virtual thread in the
    // process (timers, retries) — observed as a hard stall on single/low-core machines.
    reader =
        Thread.ofPlatform()
            .daemon()
            .name("serial-rx")
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
    // Stop the reader FIRST and let its current (<=100 ms timeout) native read finish,
    // then close the port. Closing the fd while a native read is in flight can leave
    // the device node busy on some platforms, making the next openPort() fail with
    // "Could not open ..." until the JVM exits.
    running = false;
    if (reader != null) {
      try {
        reader.join(600);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }
    if (!port.closePort()) {
      // One retry: a straggling native call can briefly hold the handle.
      try {
        Thread.sleep(100);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
      port.closePort();
    }
    if (reader != null) {
      try {
        reader.join(500);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
      reader = null;
    }
  }
}