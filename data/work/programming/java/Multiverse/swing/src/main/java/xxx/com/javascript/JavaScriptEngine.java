package xxx.com.javascript;

import java.util.HashSet;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import xxx.com.jvmargs.JvmUtil;

// <dependency>
//   <groupId>org.graalvm.js</groupId>
//   <artifactId>js</artifactId>
//   <version>...</version> <!-- Use the appropriate version -->
//  </dependency>
//  <dependency>
//   <groupId>org.graalvm.js</groupId>
//   <artifactId>js-scriptengine</artifactId>
//   <version>...</version> <!-- Use the appropriate version -->
// </dependency>

public class JavaScriptEngine {

  public static void main(String[] args) throws Exception {
    JvmUtil.enforceJvmArgs(JavaScriptEngine.class, JavaScriptEngine::runScript, args, HashSet::new);
  }

  public static void runScript() {

    // Call the helper to check for the arguments and run your code.
    ScriptEngineManager manager = new ScriptEngineManager();
    ScriptEngine engine = manager.getEngineByName("Graal.js");

    try {
      String javascriptCode = "function greet(name) { return 'Hello, ' + name; }";
      engine.eval(javascriptCode);
    } catch (Exception e) {
      System.out.println(e.getMessage());
    }
  }
}
