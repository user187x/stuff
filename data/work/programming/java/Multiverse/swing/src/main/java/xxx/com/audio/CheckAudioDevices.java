import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import javax.sound.sampled.*;

public class CheckAudioDevices {

  // Audio format settings (Standard CD Quality)
  private static final float SAMPLE_RATE = 44100f;
  private static final int SAMPLE_SIZE_BITS = 16;
  private static final int CHANNELS = 1; // Mono
  private static final boolean SIGNED = true;
  private static final boolean BIG_ENDIAN = false;

  public static void main(String[] args) {
    System.out.println("=== Java Audio Device Smoke Test ===");

    // 1. Scan and filter only valid playback mixers
    Mixer.Info[] allMixers = AudioSystem.getMixerInfo();
    List<Mixer.Info> playbackMixers = new ArrayList<>();

    for (Mixer.Info info : allMixers) {
      Mixer mixer = AudioSystem.getMixer(info);
      // SourceLineInfo represents lines feeding into the mixer (Playback)
      if (mixer.getSourceLineInfo().length > 0) {
        playbackMixers.add(info);
      }
    }

    if (playbackMixers.isEmpty()) {
      System.err.println("No audio output devices found on this system.");
      return;
    }

    // 2. Display the filtered list to the user
    System.out.println("\nAvailable Playback Devices:");
    for (int i = 0; i < playbackMixers.size(); i++) {
      System.out.printf("[%d] %s\n", i, playbackMixers.get(i).getName());
    }

    // 3. Get user choice via console input
    Scanner scanner = new Scanner(System.in);
    int selection = -1;
    while (selection < 0 || selection >= playbackMixers.size()) {
      System.out.printf("\nSelect a device index (0-%d): ", playbackMixers.size() - 1);
      if (scanner.hasNextInt()) {
        selection = scanner.nextInt();
      } else {
        scanner.next(); // Clear invalid input
      }
    }

    Mixer.Info chosenMixerInfo = playbackMixers.get(selection);
    System.out.println("\nTesting device: " + chosenMixerInfo.getName());

    // 4. Run the audible test on the selected device
    boolean success = playTestTone(chosenMixerInfo);

    if (success) {
      System.out.println("\nSmoke test finished. Did you hear the 1-second beep?");
    } else {
      System.err.println("\nSmoke test failed to initialize or write to the device.");
    }
    scanner.close();
  }

  /** Generates a 1-second sine wave tone and streams it to the targeted mixer. */
  private static boolean playTestTone(Mixer.Info mixerInfo) {
    AudioFormat format =
        new AudioFormat(SAMPLE_RATE, SAMPLE_SIZE_BITS, CHANNELS, SIGNED, BIG_ENDIAN);
    DataLine.Info lineInfo = new DataLine.Info(SourceDataLine.class, format);

    Mixer mixer = AudioSystem.getMixer(mixerInfo);

    // Check if this specific mixer can handle our target audio format
    if (!mixer.isLineSupported(lineInfo)) {
      System.err.println(
          "Error: Selected device does not support standard 44.1kHz 16-bit Mono audio.");
      return false;
    }

    try (SourceDataLine line = (SourceDataLine) mixer.getLine(lineInfo)) {
      line.open(format);
      line.start();

      // Generate a 1-second pure tone (440Hz = A4 note)
      int durationSeconds = 1;
      int totalSamples = (int) (SAMPLE_RATE * durationSeconds);
      byte[] audioBuffer = new byte[totalSamples * 2]; // 16-bit needs 2 bytes per sample

      double frequency = 440.0;

      for (int i = 0; i < totalSamples; i++) {
        // Calculate sine wave value matching the sample rate
        short sample =
            (short) (Math.sin(2.0 * Math.PI * i * frequency / SAMPLE_RATE) * Short.MAX_VALUE);

        // Pack the 16-bit short value into 2 bytes (Little Endian)
        ByteBuffer.wrap(audioBuffer, i * 2, 2)
            .order(java.nio.ByteOrder.LITTLE_ENDIAN)
            .putShort(sample);
      }

      System.out.println("Playing tone now...");
      // Write to the audio line buffer (blocks until the data is accepted)
      line.write(audioBuffer, 0, audioBuffer.length);

      // Wait for the hardware buffer to finish playing entirely before closing
      line.drain();
      return true;

    } catch (LineUnavailableException e) {
      System.err.println("Line unavailable: The device might be in use by another application.");
      e.printStackTrace();
      return false;
    }
  }
}
