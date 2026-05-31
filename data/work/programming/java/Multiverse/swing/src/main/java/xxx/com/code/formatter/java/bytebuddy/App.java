package xxx.com.code.formatter.java.bytebuddy;

import com.google.googlejavaformat.java.Formatter;
import com.google.googlejavaformat.java.FormatterException;
import java.lang.reflect.Method;

/**
 * This application demonstrates how to use the google-java-format library as an API to format a
 * string of Java code.
 *
 * <p>--- IMPORTANT RUNTIME INSTRUCTIONS for JDK 9+ ---
 *
 * <p>This code calls internal APIs of the Java compiler, which are protected by the Java Platform
 * Module System (JPMS) in JDK 9 and newer. Failure to grant access will result in an
 * `IllegalAccessError`.
 *
 * <p>There are two ways to solve this:
 *
 * <p>1. Programmatic (Attempted in this class): The `tryAddRequiredExports()` method attempts to
 * grant access at runtime using reflection. This may fail in certain environments or on newer JDKs.
 *
 * <p>2. JVM Arguments (Recommended & Most Reliable): You must add the following VM options to your
 * IDE's Run Configuration to guarantee the code will run.
 *
 * <p>In IntelliJ IDEA: a. Go to "Run" -> "Edit Configurations..." b. Select your application's
 * configuration. c. Click "Modify options" -> "Add VM options". d. Paste the following lines into
 * the "VM options" text box:
 *
 * <p>--add-exports=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED
 * --add-exports=jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED
 * --add-exports=jdk.compiler/com.sun.tools.javac.parser=ALL-UNNAMED
 * --add-exports=jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED
 * --add-exports=jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED
 */
public class App {

  private static final String[][] REQUIRED_EXPORTS = {
    {"jdk.compiler", "com.sun.tools.javac.api"},
    {"jdk.compiler", "com.sun.tools.javac.file"},
    {"jdk.compiler", "com.sun.tools.javac.parser"},
    {"jdk.compiler", "com.sun.tools.javac.tree"},
    {"jdk.compiler", "com.sun.tools.javac.code"},
    {"jdk.compiler", "com.sun.tools.javac.util"}
  };

  public static void main(String[] args) {
    // Attempt to programmatically add the required exports for modern JDKs
    if (!tryAddRequiredExports()) {
      System.out.println(
          "Warning: Could not programmatically add required JDK exports. Formatting may fail on modular JDKs.");
    }

    // The messy Java code you want to format, as a string
    String uglyCode =
        "public class MyClass { public static void main(String[] args) { System.out.println(\"Hello\"); int x=5;}}";

    System.out.println("--- Original Code ---");
    System.out.println(uglyCode);

    try {
      // Create a new Formatter instance
      Formatter formatter = new Formatter();

      // Call the formatSource() method
      String formattedCode = formatter.formatSource(uglyCode);

      System.out.println("\n--- Formatted Code ---");
      System.out.println(formattedCode);

    } catch (FormatterException e) {
      System.err.println("Failed to format code: " + e.getMessage());
    } catch (Throwable t) {
      System.err.println(
          "An unexpected error occurred. This can happen on JDK 9+ if the required --add-exports are missing.");
    }
  }

  private static boolean tryAddRequiredExports() {
    try {
      // Use reflection to get the Module class, which only exists in Java 9+
      Class<?> moduleClass = Class.forName("java.lang.Module");

      // Get the current module for this App class
      Method getModuleMethod = Class.class.getMethod("getModule");
      Object thisModule = getModuleMethod.invoke(App.class);

      // Get the boot layer
      Method getLayerMethod = Class.class.getMethod("getLayer");
      Object bootLayer = getLayerMethod.invoke(thisModule);

      // On Java 8, getLayer() returns null, and we don't need to do anything.
      if (bootLayer == null) {
        return true;
      }

      // Get the method to find a module by name: Optional<Module> findModule(String name)
      Method findModuleMethod = bootLayer.getClass().getMethod("findModule", String.class);

      // Get the method to add exports: addExports(String pkg, Module target)
      Method addExportsMethod = moduleClass.getMethod("addExports", String.class, moduleClass);

      for (String[] export : REQUIRED_EXPORTS) {
        String moduleName = export[0];
        String packageName = export[1];

        // Find the source module (e.g., jdk.compiler)
        java.util.Optional<?> sourceModuleOpt =
            (java.util.Optional<?>) findModuleMethod.invoke(bootLayer, moduleName);

        if (sourceModuleOpt.isPresent()) {
          Object sourceModule = sourceModuleOpt.get();
          System.out.println(
              "Programmatically exporting " + packageName + " from " + moduleName + "...");
          // Execute: sourceModule.addExports(packageName, thisModule);
          addExportsMethod.invoke(sourceModule, packageName, thisModule);
        }
      }
      return true;
    } catch (Exception e) {
      // This will likely fail on Java 8, which is expected and fine.
      // It could also fail on newer JDKs if a SecurityManager is in place.
      return false;
    }
  }
}
