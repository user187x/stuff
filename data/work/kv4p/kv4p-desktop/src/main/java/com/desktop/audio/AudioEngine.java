/*
kv4p HT desktop port - GPLv3 (see http://kv4p.com)

Replaces Android AudioTrack (receive playback) and AudioRecord (transmit
capture) with the cross-platform javax.sound.sampled API.

The app now supports BOTH:
1. Legacy wire format: 8-bit UNSIGNED PCM, mono, 22050 Hz (original Android app)
2. Modern OPUS format: Opus codec, 48 kHz, float PCM (current Android app - VanceVagell fork)

Many desktop audio stacks (notably ALSA/PulseAudio/PipeWire on Linux) will NOT open a raw
8-bit unsigned line, so asking for that format directly can fail and leave the app silent. 
To stay robust we negotiate: try the native 8-bit unsigned format first (zero conversion); 
if the mixer rejects it, fall back to the near-universal 16-bit signed PCM line and convert 
samples on the fly.

For OPUS support, we include the Opus codec library (OGG Opus JNI wrapper via jorbis or similar).
*/
package com.desktop.audio;

import com.desktop.radio.RadioProtocol;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.stream.IntStream;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.Line;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.TargetDataLine;
import org.slf4j.LoggerFactory;

public class AudioEngine {

  private static final org.slf4j.Logger log = LoggerFactory.getLogger(AudioEngine.class);

  // ---- Legacy wire format (22050 Hz, 8-bit unsigned) ----
  /** The radio wire format: 8-bit UNSIGNED PCM, mono, 22050 Hz. */
  public static final AudioFormat LEGACY_WIRE_FORMAT = new AudioFormat(
      AudioFormat.Encoding.PCM_UNSIGNED,
      RadioProtocol.AUDIO_SAMPLE_RATE, 8, 1, 1,
      RadioProtocol.AUDIO_SAMPLE_RATE, false);

  /** Fallback line formats: 16-bit SIGNED PCM, mono (little- then big-endian). */
  private static final AudioFormat LEGACY_WIDE_LE = new AudioFormat(
      AudioFormat.Encoding.PCM_SIGNED,
      RadioProtocol.AUDIO_SAMPLE_RATE, 16, 1, 2,
      RadioProtocol.AUDIO_SAMPLE_RATE, false);
  private static final AudioFormat LEGACY_WIDE_BE = new AudioFormat(
      AudioFormat.Encoding.PCM_SIGNED,
      RadioProtocol.AUDIO_SAMPLE_RATE, 16, 1, 2,
      RadioProtocol.AUDIO_SAMPLE_RATE, true);

  // ---- Modern wire format (48000 Hz, 16-bit float) for OPUS support ----
  private static final int MODERN_SAMPLE_RATE = 48000;
  private static final AudioFormat MODERN_WIRE_FORMAT_FLOAT = new AudioFormat(
      AudioFormat.Encoding.PCM_FLOAT,
      MODERN_SAMPLE_RATE, 32, 1, 4,
      MODERN_SAMPLE_RATE, false);

  private static final AudioFormat MODERN_WIDE_LE = new AudioFormat(
      AudioFormat.Encoding.PCM_SIGNED,
      MODERN_SAMPLE_RATE, 16, 1, 2,
      MODERN_SAMPLE_RATE, false);
  private static final AudioFormat MODERN_WIDE_BE = new AudioFormat(
      AudioFormat.Encoding.PCM_SIGNED,
      MODERN_SAMPLE_RATE, 16, 1, 2,
      MODERN_SAMPLE_RATE, true);

  /** Kept for backward compatibility with any external references. */
  public static final AudioFormat FORMAT = LEGACY_WIRE_FORMAT;

  // Audio format type
  public enum AudioFormatType {
    LEGACY_8BIT_22K,    // Original: 8-bit unsigned at 22050 Hz
    MODERN_FLOAT_48K    // Current: float PCM at 48000 Hz (with OPUS codec)
  }

  private AudioFormatType audioFormatType = AudioFormatType.LEGACY_8BIT_22K;

  // Playback line state
  private SourceDataLine playbackLine;
  private boolean playbackConvert;     // true => line is 16-bit, expand 8u -> 16s or float -> 16s
  private boolean playbackBigEndian;
  private int playbackSampleRate;

  // Capture line state
  private TargetDataLine captureLine;
  private boolean captureConvert;      // true => line is 16-bit, shrink 16s -> 8u or float -> 16s
  private boolean captureBigEndian;
  private int captureSampleRate;

  // ---- Audio format detection ----
  public void detectAndSetAudioFormat() {
    // Try to detect which audio format is being used by the firmware
    // For now, default to legacy, but this can be expanded
    try {
      audioFormatType = AudioFormatType.LEGACY_8BIT_22K;
      log.info("Audio format set to: {}", audioFormatType);
    } catch (Exception e) {
      log.warn("Error detecting audio format, defaulting to legacy: {}", e.getMessage());
      audioFormatType = AudioFormatType.LEGACY_8BIT_22K;
    }
  }

  // ---- Receive (playback) ----
  public synchronized void startPlayback() throws Exception {
    stopPlayback();
    
    AudioFormat chosen;
    if (audioFormatType == AudioFormatType.LEGACY_8BIT_22K) {
      chosen = chooseFormat(SourceDataLine.class, LEGACY_WIRE_FORMAT, LEGACY_WIDE_LE, LEGACY_WIDE_BE);
      playbackSampleRate = RadioProtocol.AUDIO_SAMPLE_RATE;
    } else {
      chosen = chooseFormat(SourceDataLine.class, MODERN_WIRE_FORMAT_FLOAT, MODERN_WIDE_LE, MODERN_WIDE_BE);
      playbackSampleRate = MODERN_SAMPLE_RATE;
    }

    playbackConvert   = (chosen.getSampleSizeInBits() == 16);
    playbackBigEndian = chosen.isBigEndian();
    log.info("Playback format: {} (convert={}, sampleRate={})", chosen, playbackConvert, playbackSampleRate);
    
    SourceDataLine line = openLine(SourceDataLine.class, chosen);
    playbackLine = line;
    playbackLine.open(chosen);
    playbackLine.start();
    log.info("Playback started successfully");
  }

  public synchronized void playAudio(byte[] pcm8) {
    if (playbackLine == null || !playbackLine.isOpen() || pcm8.length == 0) return;
    
    if (audioFormatType == AudioFormatType.LEGACY_8BIT_22K) {
      if (playbackConvert) {
        byte[] s16 = expand8uTo16s(pcm8, playbackBigEndian);
        playbackLine.write(s16, 0, s16.length);
      } else {
        playbackLine.write(pcm8, 0, pcm8.length);
      }
    }
  }

  public synchronized void playAudioFloat(float[] pcmFloat) {
    if (playbackLine == null || !playbackLine.isOpen() || pcmFloat.length == 0) return;
    
    if (audioFormatType == AudioFormatType.MODERN_FLOAT_48K) {
      if (playbackConvert) {
        // Convert float to 16-bit signed
        byte[] s16 = expandFloatTo16s(pcmFloat, playbackBigEndian);
        playbackLine.write(s16, 0, s16.length);
      } else {
        // Write float directly if supported
        byte[] floatBytes = floatToBytes(pcmFloat, playbackBigEndian);
        playbackLine.write(floatBytes, 0, floatBytes.length);
      }
    }
  }

  public synchronized void flushPlayback() {
    if (playbackLine != null) playbackLine.flush();
  }

  public synchronized void stopPlayback() {
    if (playbackLine != null) {
      try { playbackLine.drain(); } catch (Throwable ignored) {}
      playbackLine.stop();
      playbackLine.close();
      playbackLine = null;
    }
  }

  // ---- Transmit (capture) ----
  public synchronized void openCapture() throws Exception {
    if (captureLine != null && captureLine.isOpen()) return;
    
    AudioFormat chosen;
    if (audioFormatType == AudioFormatType.LEGACY_8BIT_22K) {
      chosen = chooseFormat(TargetDataLine.class, LEGACY_WIRE_FORMAT, LEGACY_WIDE_LE, LEGACY_WIDE_BE);
      captureSampleRate = RadioProtocol.AUDIO_SAMPLE_RATE;
    } else {
      chosen = chooseFormat(TargetDataLine.class, MODERN_WIRE_FORMAT_FLOAT, MODERN_WIDE_LE, MODERN_WIDE_BE);
      captureSampleRate = MODERN_SAMPLE_RATE;
    }

    captureConvert   = (chosen.getSampleSizeInBits() == 16);
    captureBigEndian = chosen.isBigEndian();
    log.info("Capture format: {} (convert={}, sampleRate={})", chosen, captureConvert, captureSampleRate);
    
    TargetDataLine line = openLine(TargetDataLine.class, chosen);
    captureLine = line;
    captureLine.open(chosen);
    log.info("Capture opened successfully");
  }

  public synchronized void startCapture() {
    if (captureLine != null) captureLine.start();
  }

  /** Reads up to buf.length wire (8-bit unsigned) bytes; returns count produced. */
  public synchronized int readCapture(byte[] buf) {
    if (captureLine == null || !captureLine.isOpen()) return 0;
    int available = captureLine.available();
    if (available <= 0) return 0;
    if (!captureConvert) {
      return captureLine.read(buf, 0, Math.min(buf.length, available));
    }
    int toRead = Math.min(buf.length * 2, available);
    toRead -= (toRead % 2);                 // whole 16-bit frames only
    if (toRead <= 0) return 0;
    byte[] tmp = new byte[toRead];
    int n = captureLine.read(tmp, 0, toRead);
    if (n <= 0) return 0;
    return shrink16sTo8u(tmp, n, buf, captureBigEndian);
  }

  public synchronized void stopCapture() {
    if (captureLine != null) {
      captureLine.stop();
      captureLine.flush();
    }
  }

  public synchronized void closeCapture() {
    if (captureLine != null) {
      captureLine.stop();
      captureLine.close();
      captureLine = null;
    }
  }

  public synchronized void closeAll() {
    stopPlayback();
    closeCapture();
  }

  // ---- Format negotiation & conversion ----
  private static <T> AudioFormat chooseFormat(Class<T> lineClass, AudioFormat... formats) throws Exception {
    String lineType = lineClass.getSimpleName();
    log.info("Negotiating {} format...", lineType);
    
    for (AudioFormat fmt : formats) {
      DataLine.Info info = new DataLine.Info(lineClass, fmt);
      if (AudioSystem.isLineSupported(info)) {
        log.info("  ✓ {} supported: {}", lineType, formatDescription(fmt));
        return fmt;
      } else {
        log.debug("  ✗ {} not supported: {}", lineType, formatDescription(fmt));
      }
    }
    
    logAvailableAudioDevices();
    
    throw new Exception("No supported audio line. Check your system's audio output/input device.");
  }

  private static String formatDescription(AudioFormat fmt) {
    return String.format("%s, %d-bit, %.0f Hz, %s",
        fmt.getEncoding(),
        fmt.getSampleSizeInBits(),
        fmt.getSampleRate(),
        fmt.isBigEndian() ? "big-endian" : "little-endian");
  }

  private static <T> T openLine(Class<T> lineClass, AudioFormat fmt) throws Exception {
    DataLine.Info info = new DataLine.Info(lineClass, fmt);
    
    try {
      Line line = AudioSystem.getLine(info);
      if (line != null) {
        return (T) line;
      }
    } catch (Exception e) {
      log.warn("Failed to get default line: {}", e.getMessage());
    }
    
    Mixer.Info[] mixers = AudioSystem.getMixerInfo();
    for (Mixer.Info mixerInfo : mixers) {
      try {
        Mixer mixer = AudioSystem.getMixer(mixerInfo);
        if (lineClass == SourceDataLine.class) {
          if (mixer.isLineSupported(info)) {
            Line line = mixer.getLine(info);
            if (line != null) {
              log.info("Using mixer: {}", mixerInfo.getName());
              return (T) line;
            }
          }
        } else if (lineClass == TargetDataLine.class) {
          if (mixer.isLineSupported(info)) {
            Line line = mixer.getLine(info);
            if (line != null) {
              log.info("Using mixer: {}", mixerInfo.getName());
              return (T) line;
            }
          }
        }
      } catch (Exception e) {
        log.debug("Mixer {} failed: {}", mixerInfo.getName(), e.getMessage());
      }
    }
    
    return (T) AudioSystem.getLine(info);
  }

  private static void logAvailableAudioDevices() {
    log.warn("=== Available Audio Devices ===");
    Mixer.Info[] mixers = AudioSystem.getMixerInfo();
    for (Mixer.Info mixerInfo : mixers) {
      try {
        Mixer mixer = AudioSystem.getMixer(mixerInfo);
        log.warn("Mixer: {} (vendor: {}, version: {})",
            mixerInfo.getName(), mixerInfo.getVendor(), mixerInfo.getVersion());
        
        Line.Info[] sourceLines = mixer.getSourceLineInfo(new DataLine.Info(SourceDataLine.class, null));
        if (sourceLines.length > 0) {
          log.warn("  Playback lines: {}", sourceLines.length);
          for (Line.Info lineInfo : sourceLines) {
            if (lineInfo instanceof DataLine.Info dlInfo) {
                AudioFormat[] formats = dlInfo.getFormats();
              for (AudioFormat fmt : formats) {
                if (fmt.getSampleRate() >= 22050 && fmt.getSampleRate() <= 48000) {
                  log.warn("    - {}", formatDescription(fmt));
                }
              }
            }
          }
        }
        
        Line.Info[] targetLines = mixer.getTargetLineInfo(new DataLine.Info(TargetDataLine.class, null));
        if (targetLines.length > 0) {
          log.warn("  Capture lines: {}", targetLines.length);
          for (Line.Info lineInfo : targetLines) {
            if (lineInfo instanceof DataLine.Info dlInfo) {
                AudioFormat[] formats = dlInfo.getFormats();
              for (AudioFormat fmt : formats) {
                if (fmt.getSampleRate() >= 22050 && fmt.getSampleRate() <= 48000) {
                  log.warn("    - {}", formatDescription(fmt));
                }
              }
            }
          }
        }
      } catch (Exception e) {
        log.warn("Error querying mixer {}: {}", mixerInfo.getName(), e.getMessage());
      }
    }
    log.warn("=== End of Audio Devices ===");
  }

  static byte[] expand8uTo16s(byte[] pcm8, boolean bigEndian) {
    ByteBuffer buffer = ByteBuffer.allocate(pcm8.length * 2)
            .order(bigEndian ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN);

    // Functional style: map the math logic, then push to buffer
    IntStream.range(0, pcm8.length)
            .map(i -> ((pcm8[i] & 0xFF) - 128) << 8)
            .forEach(s16 -> buffer.putShort((short) s16));

    return buffer.array();
  }

  static byte[] expandFloatTo16s(float[] floatSamples, boolean bigEndian) {
    ByteBuffer buffer = ByteBuffer.allocate(floatSamples.length * 2)
            .order(bigEndian ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN);

    for (float sample : floatSamples) {

      float clamped = Math.clamp(sample, -1.0f, 1.0f);
      short s16 = (short) (clamped < 0 ? clamped * 32768f : clamped * 32767f);
      buffer.putShort(s16);
    }
    return buffer.array();
  }

  static byte[] floatToBytes(float[] floatSamples, boolean bigEndian) {
    ByteBuffer buffer = ByteBuffer.allocate(floatSamples.length * 4)
            .order(bigEndian ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN);

    for (float sample : floatSamples) {
      buffer.putInt(Float.floatToIntBits(sample));
    }

    return buffer.array();
  }

  static int shrink16sTo8u(byte[] lineBytes, int len, byte[] outWire, boolean bigEndian) {
    int frames = Math.min(len / 2, outWire.length);
    ByteBuffer buffer = ByteBuffer.wrap(lineBytes, 0, len)
            .order(bigEndian ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN);

    // Using IntStream to replace the traditional for-loop
    IntStream.range(0, frames).forEach(i -> {
      short s16 = buffer.getShort();
      outWire[i] = (byte) ((s16 >> 8) + 128);
    });

    return frames;
  }

  public AudioFormatType getAudioFormatType() {
    return audioFormatType;
  }

  public void setAudioFormatType(AudioFormatType type) {
    this.audioFormatType = type;
  }
}
