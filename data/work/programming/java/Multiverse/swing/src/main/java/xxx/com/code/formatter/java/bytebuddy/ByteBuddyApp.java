package xxx.com.code.formatter.java.bytebuddy;

import com.google.googlejavaformat.java.Formatter;
import com.google.googlejavaformat.java.FormatterException;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.matcher.ElementMatchers;

import java.lang.reflect.Method;

/**
 * This application demonstrates how to use the google-java-format library
 * and how to use Byte Buddy to solve the Java Platform Module System (JPMS)
 * IllegalAccessError that occurs on JDK 9+.
 *
 * --- How it Works ---
 * The error originates in a static initializer block in the google-java-format
 * library. This code uses Byte Buddy to intercept that static initializer
 * and run our own code first. Our injected code uses reflection to grant the
 * necessary module access permissions before the original code runs,
 * thus preventing the error.
 */
public class ByteBuddyApp {

  public static void main(String[] args) {
    // Use Byte Buddy to patch the problematic class before we use it.
    try {
      System.out.println("Attempting to patch google-java-format using Byte Buddy...");

      // Find the problematic class by name
      Class<?> classToPatch = Class.forName("com.google.googlejavaformat.java.JavaInput");

      new ByteBuddy()
          .redefine(classToPatch)
          // We want to add code to the static initializer block
          .visit(Advice.to(ModuleExportingAdvice.class)
              .on(ElementMatchers.isTypeInitializer()))
          .make();
          // Inject our modified class into the class loader
          //.load(classToPatch.getClassLoader(), ClassLoadingStrategy.Default.INJECTION);

      System.out.println("Byte Buddy patch applied successfully.");

    } catch (Exception e) {
      System.err.println("Failed to apply Byte Buddy patch. Formatting will likely fail.");
      e.printStackTrace();
    }


    // The messy Java code you want to format, as a string
    String uglyCode = "public class MyClass { public static void main(String[] args) { System.out.println(\"Hello\"); int x=5;}}";

    System.out.println("\n--- Original Code ---");
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
      e.printStackTrace();
    } catch (Throwable t) {
      // Catch other potential errors, like the IllegalAccessError if exports failed
      System.err.println("An unexpected error occurred. This can happen on JDK 9+ if the required --add-exports are missing.");
      t.printStackTrace();
    }
  }

  /**
   * This is the "Advice" class that Byte Buddy will inject into the target class.
   * It contains the logic to programmatically add the required module exports.
   */
  public static class ModuleExportingAdvice {

    private static final String[][] REQUIRED_EXPORTS = {
        {"jdk.compiler", "com.sun.tools.javac.api"},
        {"jdk.compiler", "com.sun.tools.javac.file"},
        {"jdk.compiler", "com.sun.tools.javac.parser"},
        {"jdk.compiler", "com.sun.tools.javac.tree"},
        {"jdk.compiler", "com.sun.tools.javac.util"}
    };

    /**
     * This method's code will be injected at the beginning of the target method
     * (in this case, the static initializer of JavaInput).
     */
    @Advice.OnMethodEnter
    public static void addExports() {
      try {
        // Use reflection to get the Module class, which only exists in Java 9+
        Class<?> moduleClass = Class.forName("java.lang.Module");

        // Get the current module for our code
        Method getModuleMethod = Class.class.getMethod("getModule");
        Object thisModule = getModuleMethod.invoke(App.class);

        // Get the boot layer
        Method getLayerMethod = Class.class.getMethod("getLayer");
        Object bootLayer = getLayerMethod.invoke(thisModule);

        // On Java 8, getLayer() returns null, and we don't need to do anything.
        if (bootLayer == null) {
          return;
        }

        // Get the method to find a module by name: Optional<Module> findModule(String name)
        Method findModuleMethod = bootLayer.getClass().getMethod("findModule", String.class);

        // Get the method to add exports: addExports(String pkg, Module target)
        Method addExportsMethod = moduleClass.getMethod("addExports", String.class, moduleClass);

        for (String[] export : REQUIRED_EXPORTS) {
          String moduleName = export[0];
          String packageName = export[1];

          // Find the source module (e.g., jdk.compiler)
          java.util.Optional<?> sourceModuleOpt = (java.util.Optional<?>) findModuleMethod.invoke(bootLayer, moduleName);

          if (sourceModuleOpt.isPresent()) {
            Object sourceModule = sourceModuleOpt.get();
            System.out.println("Injecting export: " + packageName + " from " + moduleName);
            // Execute: sourceModule.addExports(packageName, thisModule);
            addExportsMethod.invoke(sourceModule, packageName, thisModule);
          }
        }
      } catch (Exception e) {
        // This will likely fail on Java 8, which is expected and fine.
        // It could also fail on newer JDKs if a SecurityManager is in place.
        System.err.println("Warning: Failed to programmatically add required JDK exports via Byte Buddy advice.");
      }
    }
  }
}
