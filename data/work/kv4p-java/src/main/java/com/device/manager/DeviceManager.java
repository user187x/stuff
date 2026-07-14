package com.device.manager;

import static com.device.protocol.Protocol.*;

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
  // HELLO arrives only after the firmware finishes booting and initializing the radio
  // module, which can take many seconds — keep this timeout generous.
  private static final long HELLO_TIMEOUT_MS = 15_000;
  private static final int HELLO_MAX_ATTEMPTS = 3;

  private final SerialLink link;
  private final KissDecoder decoder = new KissDecoder(this);
  private final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();
  private final ScheduledExecutorService scheduler =
      Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().daemon().factory());

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

  private final Object windowLock = new Object();
  private long window = Long.MAX_VALUE;

  // Serializes desired-state encode+write so snapshots reach the wire in sequence order.
  // Writing outside stateLock (to avoid stalling the reader) is kept, but without this
  // lock two threads could put seq N+1 on the wire before seq N; the firmware applies
  // session flags from EVERY frame, so a stale trailing frame could silently clear
  // RX_AUDIO_OPEN and stop audio. Lock order is always txLock -> stateLock -> windowLock.
  private final Object txLock = new Object();

  private DeviceManager(SerialLink link) {
    this.link = link;
  }

  public static DeviceManager connect(SerialLink link) {
    return new DeviceManager(link);
  }

  public void start() {
    link.startReader(decoder::feed, e -> notifyAll(l -> l.onDisconnected(e.getMessage())));
    link.resetDevice();
    scheduleHelloWatchdog(1);
  }

  /**
   * The device sends COMMAND_HELLO once per boot, after its radio-module init (which can
   * take several seconds). If it never arrives — chip was mid-boot during our reset, the
   * frame was lost, or the reset didn't take on this adapter — reset again a bounded
   * number of times instead of waiting forever.
   */
  private void scheduleHelloWatchdog(int attempt) {
    scheduler.schedule(
        () -> {
          if (hello != null) {
            return; // handshake completed
          }
          if (attempt < HELLO_MAX_ATTEMPTS) {
            notifyAll(
                l ->
                    l.onDebugMessage(
                        COMMAND_DEBUG_WARN,
                        "No HELLO from device after "
                            + (HELLO_TIMEOUT_MS / 1000)
                            + "s; resetting again (attempt "
                            + (attempt + 1)
                            + "/"
                            + HELLO_MAX_ATTEMPTS
                            + ")"));
            link.resetDevice();
            scheduleHelloWatchdog(attempt + 1);
          } else {
            notifyAll(
                l ->
                    l.onDebugMessage(
                        COMMAND_DEBUG_ERROR,
                        "Device never sent HELLO after "
                            + HELLO_MAX_ATTEMPTS
                            + " resets. Check the cable, firmware, and that this is the"
                            + " right serial port, then Disconnect and Connect again."));
          }
        },
        HELLO_TIMEOUT_MS,
        TimeUnit.MILLISECONDS);
  }

  public void addListener(Listener l) {
    listeners.add(l);
    Hello h = hello;
    if (h != null) {
      try {
        l.onHello(h);
      } catch (RuntimeException ignored) {}
      DeviceState d = lastDeviceState;
      if (d != null) {
        try {
          l.onDeviceState(d);
        } catch (RuntimeException ignored) {}
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

  public void updateDesiredState(java.util.function.UnaryOperator<HostDesiredState> mutation) {
    synchronized (txLock) {
      byte[] frame;
      synchronized (stateLock) {
        HostDesiredState next = mutation.apply(desired);
        desired = next.withSequence(nextSequence++);
        retriesLeft = MAX_STATE_RETRIES;
        frame = KissEncoder.vendorFrame(COMMAND_HOST_DESIRED_STATE, desired.toBytes());
        scheduleRetryLocked();
      }
      // Write outside stateLock (so the reader thread is never stalled behind a
      // flow-control wait) but inside txLock (so frames hit the wire in sequence order).
      try {
        writeFlowControlled(frame);
      } catch (IOException e) {
        notifyAll(l -> l.onDisconnected("Serial write failed: " + e.getMessage()));
      }
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

  private void scheduleRetryLocked() {
    if (retryTask != null) {
      retryTask.cancel(false);
    }
    long expectedSeq = desired.sequence();
    retryTask =
        scheduler.schedule(
            () -> {
              synchronized (txLock) {
                byte[] frame;
                synchronized (stateLock) {
                  if (lastAckedSequence >= expectedSeq || retriesLeft <= 0) {
                    return;
                  }
                  retriesLeft--;
                  // Retry the exact same snapshot with the same sequence (protocol rule).
                  frame = KissEncoder.vendorFrame(COMMAND_HOST_DESIRED_STATE, desired.toBytes());
                  scheduleRetryLocked();
                }
                try {
                  writeFlowControlled(frame);
                } catch (IOException e) {
                  notifyAll(l -> l.onDisconnected("Serial write failed: " + e.getMessage()));
                }
              }
            },
            STATE_RETRY_MS,
            TimeUnit.MILLISECONDS);
  }

  public void sendTxAudioFrame(byte[] adpcmBlock) {
    try {
      writeFlowControlled(KissEncoder.vendorFrame(COMMAND_HOST_TX_AUDIO, adpcmBlock));
    } catch (IOException e) {
      notifyAll(l -> l.onDisconnected("Serial write failed: " + e.getMessage()));
    }
  }

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
        if (wait <= 0) break;
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

        synchronized (txLock) {
          byte[] frame;
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
                    (d.flags() & 0x08FF) | sessionFlags,
                    d.bw(),
                    d.freqTx(),
                    d.freqRx(),
                    d.ctcssTx(),
                    d.squelch(),
                    d.ctcssRx());
            retriesLeft = MAX_STATE_RETRIES;
            frame = KissEncoder.vendorFrame(COMMAND_HOST_DESIRED_STATE, desired.toBytes());
            scheduleRetryLocked();
          }

          try {
            writeFlowControlled(frame);
          } catch (IOException e) {
            notifyAll(l -> l.onDisconnected("Serial write failed: " + e.getMessage()));
          }
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
    }
  }

  private void notifyAll(java.util.function.Consumer<Listener> event) {
    for (Listener l : listeners) {
      try {
        event.accept(l);
      } catch (RuntimeException ignored) {}
    }
  }

  @Override
  public void close() {
    scheduler.shutdownNow();
    link.close();
  }
}