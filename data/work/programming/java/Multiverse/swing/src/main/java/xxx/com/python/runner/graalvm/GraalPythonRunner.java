package xxx.com.python.runner.graalvm;

import java.util.HashSet;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import xxx.com.jvmargs.JvmUtil;

public class GraalPythonRunner {

  private static final String PYTHON_CODE =
    """
    # Python program to find the largest number among the three input numbers
    num1 = 10
    num2 = 14
    num3 = 12
    
    if (num1 >= num2) and (num1 >= num3):
       largest = num1
    elif (num2 >= num1) and (num2 >= num3):
       largest = num2
    else:
       largest = num3
    
    print("The largest number is", largest)
    """;

  public static void main(String[] args) throws Exception {

    // Example 1
    JvmUtil.enforceJvmArgs(
        GraalPythonRunner.class, GraalPythonRunner::runFunction, args, HashSet::new);

    // Example 2
    JvmUtil.enforceJvmArgs(
        GraalPythonRunner.class, () -> runFunction(PYTHON_CODE), args, HashSet::new);
  }

  /**
   * Pure String Python code execution
   * @param pythonCode actual python code
   */
  public static void runFunction(String pythonCode) {

    try (Context context = Context.create("python")) {
      context.eval("python", pythonCode);
    }
  }

  /**
   * Running various Python methods
   */
  public static void runFunction() {

    try (Context context = Context.create("python")) {
      // Execute a simple Python script
      context.eval("python", "print('Hello from Python!')");

      // Evaluate a Python expression and get the result
      Value result = context.eval("python", "2 + 3");
      System.out.println("Python result: " + result.asInt());

      // Call a Python function defined in a string
      StringBuilder stringBuilder = new StringBuilder();
      stringBuilder.append("def greet(name):");
      stringBuilder.append(System.lineSeparator());
      stringBuilder.append("  return 'Hello, ' + name");

      context.eval("python", stringBuilder.toString());
      Value greetFunction = context.getBindings("python").getMember("greet");
      Value greeting = greetFunction.execute("World");

      System.out.println("Python function call result: " + greeting.asString());
    }
  }
}
