package xxx.com.console;

public class ColoredConsole {

  public static final String RESET = "\u001B[0m";

  // Text Styles
  public static final String BOLD = "\u001B[1m";
  public static final String ITALIC = "\u001B[3m";
  public static final String BLINK = "\u001B[5m";

  // Declare ANSI escape codes as constants for readability
  public static final String ANSI_RESET = "\u001B[0m";
  public static final String ANSI_RED = "\u001B[31m";
  public static final String ANSI_GREEN = "\u001B[32m";
  public static final String ANSI_YELLOW = "\u001B[33m";
  public static final String ANSI_BLUE = "\u001B[34m";
  public static final String ANSI_BLUE_BOLD = "\u001B[1;34m";

  // Foreground Colors
  public static final String RED_TEXT = "\u001B[31m";
  public static final String WHITE_TEXT = "\u001B[37m";

  // Background Colors
  public static final String GREEN_BG = "\u001B[42m";
  public static final String BLUE_BG = "\u001B[44m";

  public static void main(String[] args) {

    System.out.println("This is the default color.");
    System.out.println(ANSI_RED + "This text is red." + ANSI_RESET);
    System.out.println(ANSI_GREEN + "This text is green." + ANSI_RESET);
    System.out.println(ANSI_YELLOW + "This text is yellow." + ANSI_RESET);
    System.out.println(ANSI_BLUE + "This text is blue." + ANSI_RESET);
    System.out.println(ANSI_BLUE_BOLD + "This is bold blue." + ANSI_RESET);

    System.out.println("--- Basic Styles ---");
    System.out.println(BOLD + "This is bold text." + RESET);
    System.out.println(ITALIC + "This is italic text (if supported)." + RESET);
    System.out.println(BLINK + "This is blinking text (if supported)." + RESET);

    System.out.println("\n--- Background Colors ---");
    System.out.println(GREEN_BG + "Text with a green background." + RESET);
    System.out.println(BLUE_BG + WHITE_TEXT + "White text on a blue background." + RESET);

    System.out.println("\n--- Combined Styles ---");
    // Combining bold, red text, and a blue background
    System.out.println(BOLD + RED_TEXT + BLUE_BG + "Bold red text on a blue background." + RESET);
  }
}
