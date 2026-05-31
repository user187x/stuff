package xxx.com.audio;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

/**
 * A simple Java application to convert text to Morse code and play it as audio.
 * This is a self-contained class with all necessary logic.
 */
public class MorseCodePlayer {

  // A map to store the conversion from alphanumeric characters to Morse code.
  private static final Map<Character, String> MORSE_CODE_MAP = new HashMap<>();

  // Static initializer block to populate the Morse code map.
  static {
    MORSE_CODE_MAP.put('A', ".-");
    MORSE_CODE_MAP.put('B', "-...");
    MORSE_CODE_MAP.put('C', "-.-.");
    MORSE_CODE_MAP.put('D', "-..");
    MORSE_CODE_MAP.put('E', ".");
    MORSE_CODE_MAP.put('F', "..-.");
    MORSE_CODE_MAP.put('G', "--.");
    MORSE_CODE_MAP.put('H', "....");
    MORSE_CODE_MAP.put('I', "..");
    MORSE_CODE_MAP.put('J', ".---");
    MORSE_CODE_MAP.put('K', "-.-");
    MORSE_CODE_MAP.put('L', ".-..");
    MORSE_CODE_MAP.put('M', "--");
    MORSE_CODE_MAP.put('N', "-.");
    MORSE_CODE_MAP.put('O', "---");
    MORSE_CODE_MAP.put('P', ".--.");
    MORSE_CODE_MAP.put('Q', "--.-");
    MORSE_CODE_MAP.put('R', ".-.");
    MORSE_CODE_MAP.put('S', "...");
    MORSE_CODE_MAP.put('T', "-");
    MORSE_CODE_MAP.put('U', "..-");
    MORSE_CODE_MAP.put('V', "...-");
    MORSE_CODE_MAP.put('W', ".--");
    MORSE_CODE_MAP.put('X', "-..-");
    MORSE_CODE_MAP.put('Y', "-.--");
    MORSE_CODE_MAP.put('Z', "--..");
    MORSE_CODE_MAP.put('1', ".----");
    MORSE_CODE_MAP.put('2', "..---");
    MORSE_CODE_MAP.put('3', "...--");
    MORSE_CODE_MAP.put('4', "....-");
    MORSE_CODE_MAP.put('5', ".....");
    MORSE_CODE_MAP.put('6', "-....");
    MORSE_CODE_MAP.put('7', "--...");
    MORSE_CODE_MAP.put('8', "---..");
    MORSE_CODE_MAP.put('9', "----.");
    MORSE_CODE_MAP.put('0', "-----");
    MORSE_CODE_MAP.put('.', ".-.-.-");
    MORSE_CODE_MAP.put(',', "--..--");
    MORSE_CODE_MAP.put('?', "..--..");
    MORSE_CODE_MAP.put(' ', "/"); // Use '/' to represent a space between words
  }

  // --- Audio Generation Constants ---
  private static final int DOT_DURATION_MS = 100; // Base time unit
  private static final int DASH_DURATION_MS = DOT_DURATION_MS * 3;
  private static final int SYMBOL_GAP_MS = DOT_DURATION_MS; // Gap between dots and dashes in a letter
  private static final int LETTER_GAP_MS = DOT_DURATION_MS * 3;
  private static final int WORD_GAP_MS = DOT_DURATION_MS * 7;
  private static final float SAMPLE_RATE = 8000;
  private static final int TONE_HZ = 800;

  /**
   * The main entry point for the application.
   *
   * @param args Command line arguments (not used).
   */
  public static void main(String[] args) {
    Scanner scanner = new Scanner(System.in);

    System.out.println("--- Morse Code Audio Player ---");
    System.out.print("Enter a sentence to convert and play: ");

    // Read user input and convert to uppercase for map compatibility.
    String input = scanner.nextLine().toUpperCase();
    scanner.close();

    // Convert the input string to its Morse code representation.
    String morseCode = textToMorse(input);

    System.out.println("Morse Code: " + morseCode);
    System.out.println("Playing audio...");

    try {
      playMorseCode(morseCode);
      System.out.println("Playback finished.");
    } catch (LineUnavailableException e) {
      System.err.println("Error: Audio line is unavailable.");
      e.printStackTrace();
    } catch (InterruptedException e) {
      System.err.println("Error: Playback was interrupted.");
      Thread.currentThread().interrupt(); // Restore the interrupted status
      e.printStackTrace();
    }
  }

  /**
   * Converts a given string of text into its Morse code equivalent.
   *
   * @param text The text to convert.
   * @return A string representing the Morse code.
   */
  public static String textToMorse(String text) {
    StringBuilder morseBuilder = new StringBuilder();
    for (char character : text.toCharArray()) {
      String morseSymbol = MORSE_CODE_MAP.get(character);
      if (morseSymbol != null) {
        morseBuilder.append(morseSymbol).append(" ");
      }
    }
    return morseBuilder.toString().trim();
  }

  /**
   * Plays the audio representation of a Morse code string.
   *
   * @param morseCode The Morse code to play.
   * @throws LineUnavailableException If the audio line cannot be opened.
   * @throws InterruptedException If the thread is interrupted while sleeping.
   */
  public static void playMorseCode(String morseCode) throws LineUnavailableException, InterruptedException {
    // Set up the audio format
    AudioFormat af = new AudioFormat(SAMPLE_RATE, 8, 1, true, false);
    try (SourceDataLine line = AudioSystem.getSourceDataLine(af)) {
      line.open(af);
      line.start();

      for (char symbol : morseCode.toCharArray()) {
        switch (symbol) {
          case '.':
            playTone(line, DOT_DURATION_MS);
            Thread.sleep(SYMBOL_GAP_MS);
            break;
          case '-':
            playTone(line, DASH_DURATION_MS);
            Thread.sleep(SYMBOL_GAP_MS);
            break;
          case ' ':
            // This is a gap between letters
            Thread.sleep(LETTER_GAP_MS - SYMBOL_GAP_MS); // Subtract one symbol gap already waited
            break;
          case '/':
            // This is a gap between words
            Thread.sleep(WORD_GAP_MS - LETTER_GAP_MS); // Subtract one letter gap already waited
            break;
        }
      }
      line.drain();
    }
  }

  /**
   * Generates and plays a sine wave tone for a specified duration.
   *
   * @param line The SourceDataLine to write the audio data to.
   * @param durationMs The duration of the tone in milliseconds.
   */
  private static void playTone(SourceDataLine line, int durationMs) {
    int numSamples = (int) ((durationMs / 1000.0) * SAMPLE_RATE);
    byte[] soundData = new byte[numSamples];

    // Generate the sine wave data
    for (int i = 0; i < numSamples; i++) {
      double angle = (2.0 * Math.PI * i) / (SAMPLE_RATE / TONE_HZ);
      soundData[i] = (byte) (Math.sin(angle) * 127.0);
    }

    // Write the data to the audio line to play it
    line.write(soundData, 0, numSamples);
  }
}

