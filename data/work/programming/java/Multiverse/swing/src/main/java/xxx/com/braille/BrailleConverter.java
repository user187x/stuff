package xxx.com.braille;

import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

/**
 * A simple Java application to convert text to Braille characters.
 * This is a self-contained class with all necessary logic.
 */
public class BrailleConverter {

  // A map to store the conversion from alphanumeric characters to Braille Unicode characters.
  // The value is now a String to handle multi-character sequences if needed, fixing the original error.
  private static final Map<Character, String> BRAILLE_MAP = new HashMap<>();

  // Static initializer block to populate the Braille map.
  static {
    BRAILLE_MAP.put('A', "⠁");
    BRAILLE_MAP.put('B', "⠃");
    BRAILLE_MAP.put('C', "⠉");
    BRAILLE_MAP.put('D', "⠙");
    BRAILLE_MAP.put('E', "⠑");
    BRAILLE_MAP.put('F', "⠋");
    BRAILLE_MAP.put('G', "⠛");
    BRAILLE_MAP.put('H', "⠓");
    BRAILLE_MAP.put('I', "⠊");
    BRAILLE_MAP.put('J', "⠚");
    BRAILLE_MAP.put('K', "⠅");
    BRAILLE_MAP.put('L', "⠇");
    BRAILLE_MAP.put('M', "⠍");
    BRAILLE_MAP.put('N', "⠝");
    BRAILLE_MAP.put('O', "⠕");
    BRAILLE_MAP.put('P', "⠏");
    BRAILLE_MAP.put('Q', "⠟");
    BRAILLE_MAP.put('R', "⠗");
    BRAILLE_MAP.put('S', "⠎");
    BRAILLE_MAP.put('T', "⠞");
    BRAILLE_MAP.put('U', "⠥");
    BRAILLE_MAP.put('V', "⠧");
    BRAILLE_MAP.put('W', "⠺");
    BRAILLE_MAP.put('X', "⠭");
    BRAILLE_MAP.put('Y', "⠽");
    BRAILLE_MAP.put('Z', "⠵");
    // For numbers, we map them to their corresponding letter-form Braille.
    // The textToBraille method will add the number indicator prefix.
    BRAILLE_MAP.put('1', "⠁");
    BRAILLE_MAP.put('2', "⠃");
    BRAILLE_MAP.put('3', "⠉");
    BRAILLE_MAP.put('4', "⠙");
    BRAILLE_MAP.put('5', "⠑");
    BRAILLE_MAP.put('6', "⠋");
    BRAILLE_MAP.put('7', "⠛");
    BRAILLE_MAP.put('8', "⠓");
    BRAILLE_MAP.put('9', "⠊");
    BRAILLE_MAP.put('0', "⠚");
    BRAILLE_MAP.put(' ', " "); // A space remains a space
    BRAILLE_MAP.put('.', "⠲");
    BRAILLE_MAP.put(',', "⠂");
    BRAILLE_MAP.put('?', "⠦");
    BRAILLE_MAP.put('!', "⠖");
    BRAILLE_MAP.put(';', "⠆");
    BRAILLE_MAP.put(':', "⠒");
    BRAILLE_MAP.put('-', "⠤");
  }

  // Special character for indicating the following character is a number.
  private static final char NUMBER_INDICATOR = '⠼';
  // Special character for indicating the following character is an uppercase letter.
  private static final char CAPITAL_INDICATOR = '⠠';


  /**
   * The main entry point for the application.
   *
   * @param args Command line arguments (not used).
   */
  public static void main(String[] args) {
    Scanner scanner = new Scanner(System.in);

    System.out.println("--- Text to Braille Converter ---");
    System.out.print("Enter a sentence to convert: ");

    String input = scanner.nextLine();
    scanner.close();

    String brailleText = textToBraille(input);

    System.out.println("Braille: " + brailleText);
  }

  /**
   * Converts a given string of text into its Braille Unicode equivalent.
   * Handles capital letters and numbers according to Braille conventions.
   *
   * @param text The text to convert.
   * @return A string of Braille Unicode characters.
   */
  public static String textToBraille(String text) {
    StringBuilder brailleBuilder = new StringBuilder();
    boolean numberMode = false;

    for (char character : text.toCharArray()) {
      // Check if the character is a digit
      if (Character.isDigit(character)) {
        // If we are not already in number mode, add the number indicator
        if (!numberMode) {
          brailleBuilder.append(NUMBER_INDICATOR);
          numberMode = true;
        }
      }
      // If the character is a space, it turns off number mode
      else if (character == ' ') {
        numberMode = false;
      }
      // For any other non-digit, turn off number mode
      else {
        numberMode = false;
      }

      // Check for uppercase letters
      if (Character.isUpperCase(character)) {
        brailleBuilder.append(CAPITAL_INDICATOR);
      }

      // Convert character to uppercase to find it in the map
      String brailleString = BRAILLE_MAP.get(Character.toUpperCase(character));

      if (brailleString != null) {
        brailleBuilder.append(brailleString);
      } else {
        // If character is not in the map, append a placeholder or ignore
        brailleBuilder.append(' ');
      }
    }
    return brailleBuilder.toString();
  }
}

