package xxx.com.cli;

import com.github.lalyos.jfiglet.FigletFont;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Stylizer {

  public static final String ITALIC_CODE = "\\033[3m";
  public static final String TERM_CODE = "\\033[0m";

  public static String asItalic(String value) {
    return  ITALIC_CODE + value + TERM_CODE;
  }

  public static void printItalic(String value) {
    System.out.println(asItalic(value));
  }

  public static void printBanner(String value) throws Exception {

    Path fontPath = Paths.get("/font/roboto.ttf");
    String banner =  FigletFont.convertOneLine(fontPath.toString(), value);

    System.out.println(banner);
  }
}
