package com.kv4p.desktop.audio;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.SourceDataLine;

/**
 * Plays decoded RX audio, upsampled to 48 kHz mono 16-bit LE PCM, routed through the system sound
 * server with forced maximum gain.
 */
public final class RxAudioPlayer implements AutoCloseable {

  // 48 kHz format to bypass native ALSA hardware limitations
  private static final AudioFormat FORMAT = new AudioFormat(48000, 16, 1, true, false);

  private SourceDataLine line;
  private volatile boolean open;

  public synchronized void start() throws LineUnavailableException {
    if (open) return;

    DataLine.Info info = new DataLine.Info(SourceDataLine.class, FORMAT);
    line = null;

    // Explicitly target PipeWire/PulseAudio to navigate multi-device desktop routing
    for (Mixer.Info mixerInfo : AudioSystem.getMixerInfo()) {
      if (mixerInfo.getName().contains("PulseAudio") || mixerInfo.getName().contains("PipeWire")) {
        Mixer mixer = AudioSystem.getMixer(mixerInfo);
        if (mixer.isLineSupported(info)) {
          line = (SourceDataLine) mixer.getLine(info);
          break;
        }
      }
    }

    if (line == null) {
      line = (SourceDataLine) AudioSystem.getLine(info);
    }

    // Standard 0.25 second buffer
    line.open(FORMAT, 48000 / 4);

    // CRITICAL: Force the volume to maximum to prevent silent ALSA initialization
    if (line.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
      FloatControl gain = (FloatControl) line.getControl(FloatControl.Type.MASTER_GAIN);
      gain.setValue(gain.getMaximum());
    }

    line.start();
    open = true;
  }

  /** Decode one COMMAND_RX_AUDIO ADPCM payload, upsample to 48 kHz, and queue for playback. */
  public void playAdpcm(byte[] adpcmPayload) {
    if (!open || adpcmPayload.length < ImaAdpcm.BLOCK_BYTES) return;
    short[] pcm = ImaAdpcm.decodePayload(adpcmPayload);

    // 3x Linear Upsampling: 16 kHz -> 48 kHz
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
  }

  @Override
  public synchronized void close() {
    open = false;
    if (line != null) {
      line.stop();
      line.flush();
      line.close();
      line = null;
    }
  }
}
