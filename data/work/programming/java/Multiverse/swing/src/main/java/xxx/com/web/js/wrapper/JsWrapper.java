package xxx.com.web.js.wrapper;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.*;
import java.util.stream.Collectors;

/**
 * A utility class for invoking JavaScript functions from a .js file and discovering function
 * signatures.
 */
public class JsWrapper {

  /** Represents a function signature with name, parameters, and their inferred types. */
  public static class FunctionSignature {
    private final String name;
    private final List<String> parameterNames;
    private final List<String> parameterTypes;

    public FunctionSignature(
        String name, List<String> parameterNames, List<String> parameterTypes) {
      this.name = name;
      this.parameterNames = parameterNames;
      this.parameterTypes = parameterTypes;
    }

    public String getName() {
      return name;
    }

    public List<String> getParameterNames() {
      return parameterNames;
    }

    public List<String> getParameterTypes() {
      return parameterTypes;
    }

    @Override
    public String toString() {
      StringBuilder sb = new StringBuilder(name).append("(");
      for (int i = 0; i < parameterNames.size(); i++) {
        sb.append(parameterTypes.get(i)).append(" ").append(parameterNames.get(i));
        if (i < parameterNames.size() - 1) {
          sb.append(", ");
        }
      }
      sb.append(")");
      return sb.toString();
    }
  }

  /**
   * Invokes a JavaScript function from the specified file with the given arguments.
   *
   * @param jsFilePath The path to the JavaScript file in resources (e.g., "/scripts/myscript.js").
   * @param functionName The name of the function to invoke.
   * @param args The arguments to pass to the function.
   * @return The result of the function execution, or null if the function is not found or fails.
   * @throws RuntimeException If the JavaScript file cannot be loaded or evaluated.
   */
  public static Object invokeFunction(String jsFilePath, String functionName, Object... args) {
    try (Context context = Context.newBuilder("js").allowAllAccess(true).build()) {
      // Load and evaluate the JavaScript file
      try (InputStream is = JsWrapper.class.getResourceAsStream(jsFilePath);
          BufferedReader reader =
              new BufferedReader(new InputStreamReader(Objects.requireNonNull(is)))) {
        String jsContent = reader.lines().collect(Collectors.joining("\n"));
        Source jsSource = Source.newBuilder("js", jsContent, jsFilePath).build();
        context.eval(jsSource);
        System.out.println("JavaScript file evaluated: " + jsFilePath);

        // Find the function
        Value bindings = context.getBindings("js");
        Value function = bindings.getMember(functionName);
        if (function == null || !function.canExecute()) {
          System.err.println("Function '" + functionName + "' not found or not executable.");
          return null;
        }

        // Convert arguments to polyglot values and execute
        Value[] polyglotArgs = new Value[args.length];
        for (int i = 0; i < args.length; i++) {
          polyglotArgs[i] = context.asValue(args[i]);
        }
        Value result = function.execute((Object) polyglotArgs);
        return result.as(Object.class);
      }
    } catch (Exception e) {
      throw new RuntimeException(
          "Failed to invoke function '"
              + functionName
              + "' from "
              + jsFilePath
              + ": "
              + e.getMessage(),
          e);
    }
  }

  /**
   * Discovers all top-level functions in a JavaScript file and returns their signatures.
   *
   * @param jsFilePath The path to the JavaScript file in resources (e.g., "/scripts/myscript.js").
   * @return A Map where keys are function names and values are FunctionSignature objects containing
   *     the function name, parameter names, and inferred parameter types.
   * @throws RuntimeException If the JavaScript file cannot be loaded or evaluated.
   */
  public static Map<String, FunctionSignature> discoverFunctions(String jsFilePath) {
    Map<String, FunctionSignature> signatures = new LinkedHashMap<>();
    try (Context context = Context.newBuilder("js").allowAllAccess(true).build()) {
      // Load and evaluate the JavaScript file
      try (InputStream is = JsWrapper.class.getResourceAsStream(jsFilePath);
          BufferedReader reader =
              new BufferedReader(new InputStreamReader(Objects.requireNonNull(is)))) {
        String jsContent = reader.lines().collect(Collectors.joining("\n"));
        Source jsSource = Source.newBuilder("js", jsContent, jsFilePath).build();
        context.eval(jsSource);

        // Get all bindings and filter for functions
        Value bindings = context.getBindings("js");
        for (String key : bindings.getMemberKeys()) {
          Value member = bindings.getMember(key);
          if (member.canExecute()) {
            List<String> paramNames = new ArrayList<>();
            List<String> paramTypes = new ArrayList<>();

            // Extract parameter names
            String funcStr = member.toString();
            paramNames = extractParameterNames(funcStr);

            // Infer parameter types (basic heuristic based on names or sample execution)
            paramTypes = inferParameterTypes(context, member, paramNames);

            signatures.put(key, new FunctionSignature(key, paramNames, paramTypes));
          }
        }
      }
    } catch (Exception e) {
      throw new RuntimeException(
          "Failed to discover functions in " + jsFilePath + ": " + e.getMessage(), e);
    }
    return signatures;
  }

  /**
   * Extracts parameter names from a function's string representation.
   *
   * @param funcStr The string representation of the function.
   * @return A list of parameter names, or an empty list if parsing fails.
   */
  private static List<String> extractParameterNames(String funcStr) {
    List<String> paramNames = new ArrayList<>();
    try {
      int start = funcStr.indexOf('(');
      int end = funcStr.indexOf(')');
      if (start == -1 || end == -1 || end < start) {
        return paramNames;
      }
      String paramList = funcStr.substring(start + 1, end).trim();
      if (paramList.isEmpty()) {
        return paramNames;
      }
      // Split by commas, trim whitespace, and handle simple parameter lists
      for (String param : paramList.split("\\s*,\\s*")) {
        // Remove default values or other syntax (e.g., "= default")
        param = param.split("=")[0].trim();
        if (!param.isEmpty()) {
          paramNames.add(param);
        }
      }
    } catch (Exception e) {
      // Return empty list on failure
    }
    return paramNames;
  }

  /**
   * Infers parameter types using a combination of name heuristics and sample execution.
   *
   * @param context The GraalVM context.
   * @param function The function value.
   * @param paramNames The list of parameter names.
   * @return A list of inferred parameter types.
   */
  private static List<String> inferParameterTypes(
      Context context, Value function, List<String> paramNames) {
    List<String> paramTypes = new ArrayList<>();
    for (String paramName : paramNames) {
      // Heuristic based on parameter name
      String inferredType = inferTypeFromName(paramName);

      // Try sample execution for better type inference (safe inputs only)
      if ("unknown".equals(inferredType) && function.canExecute()) {
        try {
          Value[] sampleArgs = new Value[paramNames.size()];
          for (int i = 0; i < paramNames.size(); i++) {
            // Use safe sample values based on index
            sampleArgs[i] =
                context.asValue(i == paramNames.indexOf(paramName) ? getSampleValue(i) : null);
          }
          Value result = function.execute((Object) sampleArgs);
          if (result != null) {
            inferredType = getTypeFromValue(result);
          }
        } catch (Exception e) {
          // Fallback to heuristic if execution fails
        }
      }
      paramTypes.add(inferredType);
    }
    return paramTypes;
  }

  /**
   * Infers a type based on parameter name (basic heuristic).
   *
   * @param paramName The parameter name.
   * @return The inferred type, or "unknown" if no match.
   */
  private static String inferTypeFromName(String paramName) {
    paramName = paramName.toLowerCase();
    if (paramName.contains("num") || paramName.contains("count") || paramName.contains("index")) {
      return "number";
    } else if (paramName.contains("str")
        || paramName.contains("name")
        || paramName.contains("text")) {
      return "string";
    } else if (paramName.contains("bool")
        || paramName.contains("flag")
        || paramName.contains("is")) {
      return "boolean";
    } else if (paramName.contains("arr")
        || paramName.contains("list")
        || paramName.contains("items")) {
      return "array";
    } else if (paramName.contains("obj")
        || paramName.contains("data")
        || paramName.contains("config")) {
      return "object";
    } else if (paramName.contains("func") || paramName.contains("callback")) {
      return "function";
    } else {
      return "unknown";
    }
  }

  /**
   * Provides a sample value for type inference based on index.
   *
   * @param index The parameter index.
   * @return A safe sample value.
   */
  private static Object getSampleValue(int index) {
    return switch (index % 3) {
      case 0 -> 42; // Number
      case 1 -> "test"; // String
      case 2 -> true; // Boolean
      default -> null;
    };
  }

  /**
   * Determines the type of a Value object.
   *
   * @param value The GraalVM Value.
   * @return The inferred type.
   */
  private static String getTypeFromValue(Value value) {
    if (value.isNumber()) {
      return "number";
    } else if (value.isString()) {
      return "string";
    } else if (value.isBoolean()) {
      return "boolean";
    } else if (value.hasArrayElements()) {
      return "array";
    } else if (value.hasMembers()) {
      return "object";
    } else if (value.canExecute()) {
      return "function";
    } else {
      return "unknown";
    }
  }

  /**
   * Generates sample arguments based on parameter types.
   *
   * @param paramTypes The list of parameter types.
   * @return An array of sample arguments.
   */
  private static Object[] generateSampleArguments(List<String> paramTypes) {
    Object[] args = new Object[paramTypes.size()];
    for (int i = 0; i < paramTypes.size(); i++) {
      switch (paramTypes.get(i)) {
        case "number":
          args[i] = 42;
          break;
        case "string":
          args[i] = "test";
          break;
        case "boolean":
          args[i] = true;
          break;
        case "array":
          args[i] = new Object[] {1, 2, 3};
          break;
        case "object":
          args[i] = new LinkedHashMap<String, Object>();
          break;
        case "function":
          args[i] = (Runnable) () -> System.out.println("Callback invoked");
          break;
        default:
          args[i] = null;
          break;
      }
    }
    return args;
  }

  // Example usage
  public static void main(String[] args) {

    // Example JavaScript file path in resources
    URL resourceUrl = JsWrapper.class.getClassLoader().getResource("/lib/js/jslint.mjs");
    File jsFile = new File(Objects.requireNonNull(resourceUrl).getFile());

    // Discover functions
    System.out.println("Discovered functions:");
    Map<String, FunctionSignature> functions = discoverFunctions(jsFile.getAbsolutePath());
    functions.forEach((name, sig) -> System.out.println(sig));

    // Invoke each discovered function with sample arguments
    System.out.println("\nInvoking functions with sample arguments:");
    for (Map.Entry<String, FunctionSignature> entry : functions.entrySet()) {
      String functionName = entry.getKey();
      FunctionSignature sig = entry.getValue();
      Object[] sampleArgs = generateSampleArguments(sig.getParameterTypes());
      try {
        Object result = invokeFunction(jsFile.getAbsolutePath(), functionName, sampleArgs);
        System.out.printf("Function '%s' result: %s%n", functionName, result);
      } catch (Exception e) {
        System.err.printf("Failed to invoke '%s': %s%n", functionName, e.getMessage());
      }
    }
  }
}
