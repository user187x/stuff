package com.device.audio;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.BooleanControl;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.SourceDataLine;

/** Plays decoded RX audio, upsampled to 48 kHz mono 16-bit LE PCM. */
public final class RxAudioPlayer implements AutoCloseable {
  public static final AudioFormat FORMAT = new AudioFormat(48000, 16, 1, true, false);
  private SourceDataLine line;
  private volatile boolean open;
  private Thread worker;
  private final BlockingQueue<byte[]> audioQueue = new LinkedBlockingQueue<>(500);

  public synchronized void start(Mixer.Info selectedMixerInfo) throws LineUnavailableException {
    if (open) return;
    DataLine.Info info = new DataLine.Info(SourceDataLine.class, FORMAT);
    line = null;

    // Use the user-selected device if provided and supported
    if (selectedMixerInfo != null) {
      Mixer mixer = AudioSystem.getMixer(selectedMixerInfo);
      if (mixer.isLineSupported(info)) {
        line = (SourceDataLine) mixer.getLine(info);
      }
    }

    // Fallback to the default system audio line
    if (line == null) {
      line = (SourceDataLine) AudioSystem.getLine(info);
    }

    line.open(FORMAT, 48000 / 2);

    if (line.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
      FloatControl gain = (FloatControl) line.getControl(FloatControl.Type.MASTER_GAIN);
      gain.setValue(gain.getMaximum());
    }
    if (line.isControlSupported(BooleanControl.Type.MUTE)) {
      BooleanControl mute = (BooleanControl) line.getControl(BooleanControl.Type.MUTE);
      mute.setValue(false);
    }

    line.start();
    open = true;
    worker = Thread.ofVirtual().name("kv4p-rx-audio").start(this::playbackLoop);
  }

  public void playAdpcm(byte[] adpcmPayload) {
    if (!open || adpcmPayload.length < ImaAdpcm.BLOCK_BYTES) return;
    audioQueue.offer(adpcmPayload);
  }

  private void playbackLoop() {
    while (open) {
      try {
        byte[] adpcmPayload = audioQueue.take();
        short[] pcm = ImaAdpcm.decodePayload(adpcmPayload);
        byte[] bytes = new byte[pcm.length * 3 * 2];
        int outIdx = 0;
        for (int i = 0; i < pcm.length; i++) {
          int current = pcm[i];
          int next = (i + 1 < pcm.length) ? pcm[i + 1] : current;
          for (int phase = 0; phase < 3; phase++) {
            short interpolated = (short) (current + (((next - current) * phase) / 3));
            bytes[outIdx++] = (byte) (interpolated & 0xFF);
            bytes[outIdx++] = (byte) ((interpolated >> 8) & 0xFF);
          }
        }
        line.write(bytes, 0, bytes.length);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        break;
      } catch (Exception e) {
        // Ignore playback loop exceptions to keep worker alive
      }
    }
  }

  @Override
  public synchronized void close() {
    open = false;
    if (worker != null) {
      worker.interrupt();
      worker = null;
    }
    if (line != null) {
      line.stop();
      line.flush();
      line.close();
      line = null;
    }
    audioQueue.clear();
  }
}
