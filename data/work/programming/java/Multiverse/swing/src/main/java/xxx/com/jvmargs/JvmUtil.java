// File: JvmArgSetter.java
package xxx.com.jvmargs;

import java.io.File;
import java.io.PrintWriter;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.CharUtils;
import org.apache.commons.lang3.StringUtils;

/**
 * A helper class to ensure the JVM is running with a required set of arguments. If the arguments
 * are not present, it automatically relaunches the application with them.
 */
public final class JvmUtil {

  public static Set<String> defaultRequiredArgs = new HashSet<>();

  //  A simple main method to demonstrate how to use the helper.
  //  In a real project, you would call 'ensureJvmArgs' from your own application's main method.
  public static void main(String[] args) {

    // Call the helper to check for the arguments and run your code.
    enforceJvmArgs(
        JvmUtil.class,
        args,
        new HashSet<>(),
        () -> {
          // --- Your application's original code goes here ---
          System.out.println("Hello from MyApplication!");
        });
  }

  /**
   * Checks if all required JVM arguments are set and relaunches the application if they are
   * missing. If the arguments are present, it executes the provided application logic.
   *
   * @param args The main method arguments from your application.
   * @param enforcedArgs A set of JVM arguments that must be present.
   * @param applicationLogic A Runnable containing the actual code for your application.
   */
  public static void enforceJvmArgs(
      Class<?> mainClass, Runnable applicationLogic, String[] args, String... enforcedArgs)
      throws Exception {
    enforceJvmArgs(mainClass, args, new HashSet<>(Arrays.asList(enforcedArgs)), applicationLogic);
  }

  public static void enforceJvmArgs(
      Class<?> mainClass, Runnable applicationLogic, String[] args, Supplier<HashSet<String>> supplier)
      throws Exception {
    enforceJvmArgs(mainClass, args, supplier.get(), applicationLogic);
  }

  /**
   * Checks if all required JVM arguments are set and relaunches the application if they are
   * missing. If the arguments are present, it executes the provided application logic.
   *
   * @param args The main method arguments from your application.
   * @param enforcedArgs A set of JVM arguments that must be present.
   * @param applicationLogic A Runnable containing the actual code for your application.
   */
  public static void enforceJvmArgs(
      Class<?> mainClass, String[] args, Set<String> enforcedArgs, Runnable applicationLogic) {

    // Define the set of JVM arguments you want to enforce.
    defaultRequiredArgs.add("-XX:+UnlockExperimentalVMOptions");
    defaultRequiredArgs.add("-XX:+EnableJVMCI");
    defaultRequiredArgs.add("-Dpolyglot.engine.WarnInterpreterOnly=false");

    RuntimeMXBean runtimeMxBean = ManagementFactory.getRuntimeMXBean();
    Set<String> currentJvmArgs = new HashSet<>(runtimeMxBean.getInputArguments());

    if (CollectionUtils.isEmpty(enforcedArgs)) {
      enforcedArgs = defaultRequiredArgs;
    }

    // check if the current args contain all enforced args
    if (currentJvmArgs.containsAll(enforcedArgs)) {
      System.out.println("✅ Required JVM args are set. Running application...");
      applicationLogic.run();
    } else {
      System.out.println("⚠️ Required JVM args not set. Relaunching with necessary flags...");
      relaunchWithJvmArgs(mainClass, args, enforcedArgs, currentJvmArgs);
    }
  }

  private static void relaunchWithJvmArgs(
      Class<?> mainClass, String[] args, Set<String> enforcedArgs, Set<String> currentJvmArgs) {

    File argFile = null;

    try {

      argFile = File.createTempFile("jvmargs", ".txt");
      argFile.deleteOnExit();

      Set<String> allJvmArgs = new HashSet<>(currentJvmArgs);
      allJvmArgs.addAll(enforcedArgs);

      try (PrintWriter writer = new PrintWriter(argFile, StandardCharsets.UTF_8)) {

        // Write all unique JVM arguments.
        allJvmArgs.forEach(arg -> writer.println(formatArgumentForFile(arg)));

        // Write classpath, main class, and application arguments.
        writer.println("-cp");
        writer.println(formatArgumentForFile(System.getProperty("java.class.path")));
        writer.println(mainClass.getName());

        // Write the application's arguments to the file
        Arrays.asList(args).forEach(arg -> writer.println(formatArgumentForFile(arg)));
      }

      String javaExecutable = ProcessHandle.current().info().command().orElseThrow();
      List<String> command = List.of(javaExecutable, "@" + argFile.getAbsolutePath());

      System.out.println("Executing command: " + String.join(" ", command));
      new ProcessBuilder(command).inheritIO().start().waitFor();

    } catch (Exception e) {
      System.out.println(e.getMessage());
    }
    finally {
      if (argFile != null) {
        argFile.delete();
      }
    }
  }

  /**
   * Formats an argument to be safely written to a Java @-file by escaping backslashes and quoting
   * arguments that contain spaces.
   */
  private static String formatArgumentForFile(String arg) {

    String apostrophe = CharUtils.toString('"');
    String backspace = CharUtils.toString('\\');
    String doubleBackspace = "\\\\";

    String escapedArg = arg.replace(backspace, doubleBackspace);

    if (escapedArg.contains(StringUtils.SPACE) && !escapedArg.startsWith(apostrophe)) {
      return apostrophe + escapedArg + apostrophe;
    }

    return escapedArg;
  }
}
