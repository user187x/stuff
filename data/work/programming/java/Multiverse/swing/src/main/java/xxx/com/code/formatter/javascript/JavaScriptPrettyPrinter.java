package xxx.com.code.formatter.javascript;

import org.mozilla.javascript.CompilerEnvirons;
import org.mozilla.javascript.Parser;
import org.mozilla.javascript.ast.AstRoot;

/**
 * A Java application to format (pretty print) JavaScript code. It uses the Mozilla Rhino library to
 * parse the code into an Abstract Syntax Tree (AST) and then converts it back to a well-formatted
 * string.
 */
public class JavaScriptPrettyPrinter {

  /**
   * Pretty prints a given JavaScript source code string.
   *
   * @param jsCode The JavaScript code to format.
   * @return A pretty-printed version of the code, or an error message if parsing fails.
   */
  public static String prettyPrint(String jsCode) {
    try {
      // 1. Configure the parser environment
      CompilerEnvirons env = new CompilerEnvirons();
      // We want to try and parse even if there are some minor errors
      env.setRecoverFromErrors(true);
      // We want to record comments and attach them to the AST
      env.setRecordingComments(true);
      env.setRecordingLocalJsDocComments(true);
      // Set language version
      env.setLanguageVersion(170);

      // 2. Create the parser
      Parser parser = new Parser(env);

      // 3. Parse the code into an Abstract Syntax Tree (AST)
      // The "source.js" is a dummy filename used for error reporting.
      // The '1' is the starting line number.
      AstRoot ast = parser.parse(jsCode, "source.js", 1);

      // 4. Convert the AST back to a formatted source string.
      // The toSource() method on the root node handles the pretty-printing.
      return ast.toSource();

    } catch (Exception e) {
      // In case of a fatal syntax error, return a message.
      System.err.println("Error parsing JavaScript code: " + e.getMessage());
      return "// Could not format the code due to a syntax error.\n" + jsCode;
    }
  }

  /**
   * Main method to demonstrate the pretty printer.
   *
   * @param args Command line arguments (not used).
   */
  public static void main(String[] args) {

    // Example of poorly formatted JavaScript code (syntax corrected)
    String uglyJavaScript =
        "function foo(a,b){if(a>b){console.log('a is greater');}else{ for(var i=0;i<b;i++){console.log('b');}} var x= {a:1, b:2};}";

    System.out.println("--- Original JavaScript ---");
    System.out.println(uglyJavaScript);
    System.out.println("\n---------------------------\n");

    String prettyJavaScript = prettyPrint(uglyJavaScript);

    System.out.println("--- Pretty-Printed JavaScript ---");
    System.out.println(prettyJavaScript);
  }
}
