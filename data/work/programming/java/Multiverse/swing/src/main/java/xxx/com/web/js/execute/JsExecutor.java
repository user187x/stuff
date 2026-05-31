package xxx.com.web.js.execute;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.stream.Collectors;

public class JsExecutor {

  public static void main(String[] args) {
    // Paths to the scripts in the resources folder.
    String beautifyScriptPath = "/lib/js/beautify.js";
    String uglyScriptPath = "/lib/js/ugly.js";

    // 1. Create a GraalVM Polyglot Context directly. This is the modern entry point.
    try (Context context = Context.newBuilder("js").allowAllAccess(true).build()) {

      // 2. Create a simple object to act as the 'exports' target for the library.
      context.eval("js", "var exports = {};");

      // 3. Load and evaluate the js-beautify library using a Source object.
      try (InputStream is = JsExecutor.class.getResourceAsStream(beautifyScriptPath);
          BufferedReader reader = new BufferedReader(new InputStreamReader(Objects.requireNonNull(is)))) {
        Source beautifySource = Source.newBuilder("js", reader, "beautify.js").build();
        context.eval(beautifySource);
      }

      // 4. Read the ugly JavaScript file into a single string.
      String uglyJsContent;
      try (InputStream is = JsExecutor.class.getResourceAsStream(uglyScriptPath);
          BufferedReader reader = new BufferedReader(new InputStreamReader(Objects.requireNonNull(is), StandardCharsets.UTF_8))) {
        uglyJsContent = reader.lines().collect(Collectors.joining("\n"));
      }

      // 5. Retrieve the 'js_beautify' function from the 'exports' object.
      Value exports = context.getBindings("js").getMember("exports");
      Value jsBeautifyFunction = exports.getMember("js_beautify");

      if (jsBeautifyFunction == null || !jsBeautifyFunction.canExecute()) {
        throw new NoSuchMethodException("js_beautify function not found in the library's exports.");
      }

      // 6. Create the options object.
      Value options = context.eval("js", "({ indent_size: 2, space_in_empty_paren: true })");

      // 7. Execute the function with the ugly code and options.
      System.out.println("--- Formatting JavaScript code... ---");

      Value result = jsBeautifyFunction.execute(uglyJsContent, options);
      String formattedJs = result.asString();

      System.out.println("--- Formatted Code ---");
      System.out.println(formattedJs);

    } catch (Exception e) {
      System.err.println("An error occurred during script execution." + e.getMessage());
    }
  }
}
