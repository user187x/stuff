package com.device.manager;

import static com.device.protocol.Kv4pProtocol.*;

import com.device.protocol.KissDecoder;
import com.device.protocol.KissEncoder;
import com.device.protocol.Structs.DeviceState;
import com.device.protocol.Structs.Hello;
import com.device.protocol.Structs.HostDesiredState;
import com.device.protocol.Structs.WindowUpdate;
import com.device.serial.SerialLink;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Desktop equivalent of the Android app's radio service. Implements protocol v2.2: waits for
 * COMMAND_HELLO, sends COMMAND_HOST_DESIRED_STATE snapshots with a monotonically increasing
 * sequence, treats DeviceState.appliedSequence as the ACK, retries unacknowledged snapshots (same
 * sequence, bounded), honors window-based flow control for TX audio, and routes RX audio / AX.25 /
 * debug frames to listeners.
 */
public final class DeviceManager implements AutoCloseable, KissDecoder.Listener {

  public interface Listener {
    default void onHello(Hello hello) {}

    default void onDeviceState(DeviceState state) {}

    default void onRxAudio(byte[] adpcmPayload) {}

    default void onAx25Received(byte[] ax25) {}

    default void onDebugMessage(int level, String message) {}

    default void onDisconnected(String reason) {}
  }

  private static final int MAX_STATE_RETRIES = 3;
  private static final long STATE_RETRY_MS = 1000;

  private final SerialLink link;
  private final KissDecoder decoder = new KissDecoder(this);
  private final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();
  private final ScheduledExecutorService scheduler =
      Executors.newSingleThreadScheduledExecutor(Thread.ofVirtual().factory());

  private final Object stateLock = new Object();
  private HostDesiredState desired =
      new HostDesiredState(
          0,
          -1,
          HOST_STATE_ENABLE_STATUS_REPORTS | HOST_STATE_HIGH_POWER | HOST_STATE_RSSI_ENABLED,
          BW_25K,
          0f,
          0f,
          0,
          0,
          0);
  private long nextSequence = 1;
  private long lastAckedSequence = 0;
  private int retriesLeft = MAX_STATE_RETRIES;
  private ScheduledFuture<?> retryTask;

  private volatile Hello hello;
  private volatile DeviceState lastDeviceState;

  // Flow-control window (bytes we may still put on the wire before the next WINDOW_UPDATE).
  private final Object windowLock = new Object();
  private long window = Long.MAX_VALUE; // unlimited until HELLO declares one

  private DeviceManager(SerialLink link) {
    this.link = link;
  }

  /**
   * Creates a client bound to {@code link} without starting any I/O. Register listeners with
   * {@link #addListener}, then call {@link #start()}. Splitting construction from startup closes
   * a race where COMMAND_HELLO (a one-shot event) arrived and was dispatched while the listener
   * list was still empty — e.g. when HELLO bytes were already buffered by the OS, or the device
   * booted faster than the caller could register its listener. A lost HELLO meant the UI never
   * enabled itself and never sent HOST_STATE_RX_AUDIO_OPEN, so the firmware stayed in
   * MODE_STOPPED and no RX audio was ever streamed.
   */
  public static DeviceManager connect(SerialLink link) {
    return new DeviceManager(link);
  }

  /** Starts the serial reader and reboots the ESP32 so it emits COMMAND_HELLO. Call after all
   * listeners have been registered. */
  public void start() {
    link.startReader(decoder::feed, e -> notifyAll(l -> l.onDisconnected(e.getMessage())));
    link.resetDevice(); // reboot ESP32 so it emits COMMAND_HELLO for this session
  }

  /**
   * Registers a listener. If the HELLO handshake has already completed, it is replayed to the
   * new listener immediately so late registration can never miss the one-shot handshake event.
   */
  public void addListener(Listener l) {
    listeners.add(l);
    Hello h = hello;
    if (h != null) {
      try {
        l.onHello(h);
      } catch (RuntimeException ignored) {
        // A misbehaving listener must not break registration.
      }
      DeviceState d = lastDeviceState;
      if (d != null) {
        try {
          l.onDeviceState(d);
        } catch (RuntimeException ignored) {
          // Ignored for the same reason.
        }
      }
    }
  }

  public Hello hello() {
    return hello;
  }

  public DeviceState lastDeviceState() {
    return lastDeviceState;
  }

  public HostDesiredState desiredState() {
    synchronized (stateLock) {
      return desired;
    }
  }

  // ---------------- Host desired state ----------------

  /** Apply a mutation to the desired state and push a new snapshot (new sequence). */
  public void updateDesiredState(java.util.function.UnaryOperator<HostDesiredState> mutation) {
    synchronized (stateLock) {
      HostDesiredState next = mutation.apply(desired);
      // Session + global flags travel together in one snapshot on the wire.
      desired = next.withSequence(nextSequence++);
      retriesLeft = MAX_STATE_RETRIES;
      sendDesiredStateLocked();
    }
  }

  public void setRadioConfig(
      float freqTx, float freqRx, int bw, int ctcssTx, int ctcssRx, int squelch, int memoryId) {
    updateDesiredState(
        s ->
            new HostDesiredState(
                s.sequence(),
                memoryId,
                s.flags() | HOST_STATE_RADIO_CONFIG_VALID,
                bw,
                freqTx,
                freqRx,
                ctcssTx,
                squelch,
                ctcssRx));
  }

  public void setFlag(int mask, boolean on) {
    updateDesiredState(s -> s.withFlags(on ? s.flags() | mask : s.flags() & ~mask));
  }

  public void setPtt(boolean down) {
    setFlag(HOST_STATE_PTT_REQUESTED, down);
  }

  public void setRxAudioOpen(boolean open) {
    setFlag(HOST_STATE_RX_AUDIO_OPEN, open);
  }

  public void setTxAllowed(boolean allowed) {
    setFlag(HOST_STATE_TX_ALLOWED, allowed);
  }

  private void sendDesiredStateLocked() {
    try {
      writeFlowControlled(KissEncoder.vendorFrame(COMMAND_HOST_DESIRED_STATE, desired.toBytes()));
    } catch (IOException e) {
      notifyAll(l -> l.onDisconnected("Serial write failed: " + e.getMessage()));
      return;
    }
    scheduleRetryLocked();
  }

  private void scheduleRetryLocked() {
    if (retryTask != null) {
      retryTask.cancel(false);
    }
    long expectedSeq = desired.sequence();
    retryTask =
        scheduler.schedule(
            () -> {
              synchronized (stateLock) {
                if (lastAckedSequence >= expectedSeq || retriesLeft <= 0) {
                  return;
                }
                retriesLeft--;
                // Retry the exact same snapshot with the same sequence (protocol rule).
                sendDesiredStateLocked();
              }
            },
            STATE_RETRY_MS,
            TimeUnit.MILLISECONDS);
  }

  // ---------------- TX paths ----------------

  /** Stream one 128-byte ADPCM voice frame while PTT is requested. */
  public void sendTxAudioFrame(byte[] adpcmBlock) {
    try {
      writeFlowControlled(KissEncoder.vendorFrame(COMMAND_HOST_TX_AUDIO, adpcmBlock));
    } catch (IOException e) {
      notifyAll(l -> l.onDisconnected("Serial write failed: " + e.getMessage()));
    }
  }

  /** Transmit a raw AX.25 packet (firmware runs the AFSK modulator on-chip). */
  public void sendAx25(byte[] ax25) {
    try {
      writeFlowControlled(KissEncoder.dataFrame(ax25));
    } catch (IOException e) {
      notifyAll(l -> l.onDisconnected("Serial write failed: " + e.getMessage()));
    }
  }

  private void writeFlowControlled(byte[] frame) throws IOException {
    synchronized (windowLock) {
      long deadline = System.currentTimeMillis() + 2000;
      while (window < frame.length) {
        long wait = deadline - System.currentTimeMillis();
        if (wait <= 0) break; // don't wedge the UI forever on a stalled window
        try {
          windowLock.wait(wait);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          break;
        }
      }
      if (window != Long.MAX_VALUE) {
        window -= frame.length;
      }
    }
    link.write(frame);
  }

  // ---------------- KISS decoder callbacks (serial reader thread) ----------------

  @Override
  public void onAx25(byte[] ax25) {
    notifyAll(l -> l.onAx25Received(ax25));
  }

  @Override
  public void onVendorCommand(int command, byte[] payload) {
    switch (command) {
      case COMMAND_HELLO -> {
        Hello h = Hello.fromBytes(payload);
        hello = h;
        lastDeviceState = h.deviceState();
        synchronized (windowLock) {
          window = h.version().windowSize();
          windowLock.notifyAll();
        }
        synchronized (stateLock) {
          // Sequence is global across transports; resync from the device.
          lastAckedSequence = h.deviceState().appliedSequence();
          nextSequence = lastAckedSequence + 1;
          // Seed our desired snapshot from the firmware's restored NVS state,
          // keeping our session flags (status reports / audio open).
          int sessionFlags =
              desired.flags()
                  & (HOST_STATE_ENABLE_STATUS_REPORTS
                  | HOST_STATE_RX_AUDIO_OPEN
                  | HOST_STATE_PTT_REQUESTED);
          DeviceState d = h.deviceState();
          desired =
              new HostDesiredState(
                  nextSequence++,
                  d.memoryId(),
                  (d.flags() & 0x08FF) | sessionFlags, // keep global flags reported by device
                  d.bw(),
                  d.freqTx(),
                  d.freqRx(),
                  d.ctcssTx(),
                  d.squelch(),
                  d.ctcssRx());
          retriesLeft = MAX_STATE_RETRIES;
          sendDesiredStateLocked(); // enables status reports for this session
        }
        notifyAll(l -> l.onHello(h));
      }
      case COMMAND_DEVICE_STATE -> {
        if (payload.length >= DeviceState.SIZE) {
          DeviceState state = DeviceState.fromBytes(payload, 0);
          lastDeviceState = state;
          synchronized (stateLock) {
            if (state.appliedSequence() > lastAckedSequence) {
              lastAckedSequence = state.appliedSequence();
            }
            if (lastAckedSequence >= desired.sequence() && retryTask != null) {
              retryTask.cancel(false);
            }
          }
          notifyAll(l -> l.onDeviceState(state));
        }
      }
      case COMMAND_WINDOW_UPDATE -> {
        if (payload.length >= WindowUpdate.SIZE) {
          long credit = WindowUpdate.fromBytes(payload).size();
          synchronized (windowLock) {
            if (window != Long.MAX_VALUE) {
              window += credit;
              windowLock.notifyAll();
            }
          }
        }
      }
      case COMMAND_RX_AUDIO -> notifyAll(l -> l.onRxAudio(payload));
      case COMMAND_DEBUG_INFO,
           COMMAND_DEBUG_ERROR,
           COMMAND_DEBUG_WARN,
           COMMAND_DEBUG_DEBUG,
           COMMAND_DEBUG_TRACE -> {
        String msg = new String(payload, StandardCharsets.UTF_8);
        notifyAll(l -> l.onDebugMessage(command, msg));
      }
      default -> {
        /* unknown vendor command: ignore, forward-compatible */
      }
    }
  }

  private void notifyAll(java.util.function.Consumer<Listener> event) {
    for (Listener l : listeners) {
      try {
        event.accept(l);
      } catch (RuntimeException ignored) {
        // A misbehaving listener must not kill the serial reader.
      }
    }
  }

  @Override
  public void close() {
    scheduler.shutdownNow();
    link.close();
  }
}