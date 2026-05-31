package xxx.com.web.js.lint;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pure Java JavaScript linter using JSweet-style transpilation No embedded JavaScript strings - all
 * logic implemented in Java
 */
public class JavaScriptLinter {

  private static final Pattern SEMICOLON_PATTERN =
      Pattern.compile("^(return|break|continue|throw)\\s");
  private static final Pattern VAR_ASSIGNMENT_PATTERN =
      Pattern.compile("^(const|let|var)\\s+\\w+\\s*=");
  private static final Pattern FUNCTION_CALL_PATTERN = Pattern.compile("^\\w+\\s*\\(.*\\)\\s*$");
  private static final Pattern OBJECT_METHOD_PATTERN = Pattern.compile("^\\w+\\.\\w+.*$");
  private static final Pattern CONTROL_FLOW_PATTERN =
      Pattern.compile("^(if|for|while|function|class|else)\\s*[\\(\\{]");
  private static final Pattern IMPORT_EXPORT_PATTERN = Pattern.compile("^(import|export)\\s");
  private static final Pattern VAR_DECLARATION_PATTERN = Pattern.compile("^var\\s+\\w+");
  private static final Pattern UNDECLARED_VAR_PATTERN = Pattern.compile("^\\s*(\\w+)\\s*=");
  private static final Pattern FUNCTION_DECLARATION_PATTERN =
      Pattern.compile("^function\\s+\\w+\\s*\\([^)]*\\)\\s*$");

  /** Main linting method that processes JavaScript code */
  public LintResult lintCode(String jsCode, String filename) {
    if (jsCode == null || jsCode.trim().isEmpty()) {
      return new LintResult(
          filename != null ? filename : "unknown",
          List.of(
              new LintIssue(
                  0, "Invalid input: code must be a non-empty string", "input-validation")),
          List.of(),
          false);
    }

    List<LintIssue> errors = new ArrayList<>();
    List<LintIssue> warnings = new ArrayList<>();

    String[] lines = jsCode.split("\\n");

    for (int i = 0; i < lines.length; i++) {
      int lineNum = i + 1;
      String line = lines[i];

      // Apply all linting rules
      checkSemicolons(line, lineNum, errors);
      checkConsoleLog(line, lineNum, warnings);
      checkVarDeclarations(line, lineNum, warnings);
      checkEquality(line, lineNum, warnings);
      checkTrailingSpaces(line, lineNum, warnings);
      checkUndeclaredVariables(line, lineNum, warnings);
      checkFunctionDeclarations(line, lineNum, warnings);
    }

    boolean success = errors.isEmpty();
    return new LintResult(filename != null ? filename : "input.js", errors, warnings, success);
  }

  /** Checks for missing semicolons */
  private void checkSemicolons(String line, int lineNum, List<LintIssue> errors) {
    String trimmed = line.trim();

    if (trimmed.isEmpty()
        || trimmed.endsWith(";")
        || trimmed.endsWith("{")
        || trimmed.endsWith("}")
        || trimmed.startsWith("//")
        || trimmed.startsWith("*")
        || CONTROL_FLOW_PATTERN.matcher(trimmed).find()
        || IMPORT_EXPORT_PATTERN.matcher(trimmed).find()) {
      return;
    }

    if (SEMICOLON_PATTERN.matcher(trimmed).find()
        || VAR_ASSIGNMENT_PATTERN.matcher(trimmed).find()
        || FUNCTION_CALL_PATTERN.matcher(trimmed).matches()
        || (OBJECT_METHOD_PATTERN.matcher(trimmed).matches() && !trimmed.contains("function"))) {

      errors.add(new LintIssue(lineNum, "Missing semicolon", "semicolon"));
    }
  }

  /** Checks for console statements */
  private void checkConsoleLog(String line, int lineNum, List<LintIssue> warnings) {
    if (line.contains("console.log")
        || line.contains("console.warn")
        || line.contains("console.error")) {
      warnings.add(
          new LintIssue(
              lineNum, "Console statements should be removed in production", "no-console"));
    }
  }

  /** Checks for var declarations */
  private void checkVarDeclarations(String line, int lineNum, List<LintIssue> warnings) {
    String trimmed = line.trim();
    if (VAR_DECLARATION_PATTERN.matcher(trimmed).find()) {
      warnings.add(new LintIssue(lineNum, "Use let or const instead of var", "no-var"));
    }
  }

  /** Checks for loose equality operators */
  private void checkEquality(String line, int lineNum, List<LintIssue> warnings) {
    if ((line.contains("==") && !line.contains("==="))
        || (line.contains("!=") && !line.contains("!=="))) {
      warnings.add(
          new LintIssue(
              lineNum,
              "Use strict equality (=== or !==) instead of loose equality",
              "strict-equality"));
    }
  }

  /** Checks for trailing whitespace */
  private void checkTrailingSpaces(String line, int lineNum, List<LintIssue> warnings) {
    if (line.endsWith(" ") || line.endsWith("\t")) {
      warnings.add(new LintIssue(lineNum, "Trailing whitespace detected", "no-trailing-spaces"));
    }
  }

  /** Checks for potentially undeclared variables */
  private void checkUndeclaredVariables(String line, int lineNum, List<LintIssue> warnings) {
    Matcher matcher = UNDECLARED_VAR_PATTERN.matcher(line);
    if (matcher.find()
        && !line.contains("var ")
        && !line.contains("let ")
        && !line.contains("const ")
        && !line.contains("function ")
        && !line.contains(".")) {

      String varName = matcher.group(1);
      warnings.add(
          new LintIssue(lineNum, "Possible undeclared variable: " + varName, "undeclared-var"));
    }
  }

  /** Checks for function declarations without bodies */
  private void checkFunctionDeclarations(String line, int lineNum, List<LintIssue> warnings) {
    String trimmed = line.trim();
    if (FUNCTION_DECLARATION_PATTERN.matcher(trimmed).matches()) {
      warnings.add(
          new LintIssue(
              lineNum,
              "Function declaration without body - missing opening brace?",
              "function-body"));
    }
  }

  /** Lints JavaScript code from a file */
  public LintResult lintFile(String filePath) throws IOException {
    Path path = Paths.get(filePath);
    if (!Files.exists(path)) {
      throw new IllegalArgumentException("File not found: " + filePath);
    }

    String code = Files.readString(path);
    return lintCode(code, path.getFileName().toString());
  }

  /** Lints multiple JavaScript files */
  public List<LintResult> lintFiles(List<String> filePaths) {
    List<LintResult> results = new ArrayList<>();

    for (String filePath : filePaths) {
      try {
        results.add(lintFile(filePath));
      } catch (Exception e) {
        results.add(
            new LintResult(
                filePath,
                List.of(new LintIssue(0, "Error reading file: " + e.getMessage(), "file-error")),
                List.of(),
                false));
      }
    }

    return results;
  }

  /** Applies custom linting rules implemented in Java */
  public LintResult lintWithCustomRules(
      String jsCode, CustomLintRules customRules, String filename) {
    // Get base linting result
    LintResult baseResult = lintCode(jsCode, filename);

    if (customRules == null) {
      return baseResult;
    }

    // Apply custom rules
    CustomLintResult customResult = customRules.applyCustomRules(jsCode, filename);

    // Combine results
    List<LintIssue> combinedErrors = new ArrayList<>(baseResult.getErrors());
    List<LintIssue> combinedWarnings = new ArrayList<>(baseResult.getWarnings());

    if (customResult != null) {
      combinedErrors.addAll(customResult.getErrors());
      combinedWarnings.addAll(customResult.getWarnings());
    }

    boolean success = combinedErrors.isEmpty();
    return new LintResult(filename, combinedErrors, combinedWarnings, success);
  }

  /** Interface for implementing custom linting rules in Java */
  public interface CustomLintRules {
    CustomLintResult applyCustomRules(String code, String filename);
  }

  /** Result of custom linting rules */
  public static class CustomLintResult {
    private final List<LintIssue> errors;
    private final List<LintIssue> warnings;

    public CustomLintResult(List<LintIssue> errors, List<LintIssue> warnings) {
      this.errors = errors != null ? errors : new ArrayList<>();
      this.warnings = warnings != null ? warnings : new ArrayList<>();
    }

    public List<LintIssue> getErrors() {
      return errors;
    }

    public List<LintIssue> getWarnings() {
      return warnings;
    }
  }

  /** Example implementation of custom rules */
  public static class ExampleCustomRules implements CustomLintRules {
    @Override
    public CustomLintResult applyCustomRules(String code, String filename) {
      List<LintIssue> errors = new ArrayList<>();
      List<LintIssue> warnings = new ArrayList<>();

      String[] lines = code.split("\\n");

      for (int i = 0; i < lines.length; i++) {
        int lineNum = i + 1;
        String line = lines[i];

        // Custom rule: detect TODO comments
        if (line.contains("TODO") || line.contains("FIXME")) {
          warnings.add(new LintIssue(lineNum, "TODO/FIXME comment found", "no-todos"));
        }

        // Custom rule: detect long lines
        if (line.length() > 100) {
          warnings.add(
              new LintIssue(
                  lineNum, "Line too long (" + line.length() + " characters)", "max-line-length"));
        }

        // Custom rule: detect magic numbers
        if (line.matches(".*\\b\\d{2,}\\b.*") && !line.contains("//")) {
          warnings.add(
              new LintIssue(
                  lineNum,
                  "Magic number detected - consider using a constant",
                  "no-magic-numbers"));
        }

        // Custom rule: detect empty catch blocks
        if (line.trim().equals("} catch (") || line.trim().equals("} catch(")) {
          errors.add(new LintIssue(lineNum, "Empty catch block detected", "no-empty-catch"));
        }
      }

      return new CustomLintResult(errors, warnings);
    }
  }

  /** Represents a single linting issue (error or warning) */
  public static class LintIssue {
    private final int line;
    private final String message;
    private final String rule;

    public LintIssue(int line, String message, String rule) {
      this.line = line;
      this.message = message;
      this.rule = rule;
    }

    public int getLine() {
      return line;
    }

    public String getMessage() {
      return message;
    }

    public String getRule() {
      return rule;
    }

    @Override
    public String toString() {
      return String.format("Line %d: %s (%s)", line, message, rule);
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) return true;
      if (obj == null || getClass() != obj.getClass()) return false;
      LintIssue lintIssue = (LintIssue) obj;
      return line == lintIssue.line
          && Objects.equals(message, lintIssue.message)
          && Objects.equals(rule, lintIssue.rule);
    }

    @Override
    public int hashCode() {
      return Objects.hash(line, message, rule);
    }
  }

  /** Result of a linting operation */
  public static class LintResult {
    private final String filename;
    private final List<LintIssue> errors;
    private final List<LintIssue> warnings;
    private final boolean success;

    public LintResult(
        String filename, List<LintIssue> errors, List<LintIssue> warnings, boolean success) {
      this.filename = filename;
      this.errors = errors != null ? errors : new ArrayList<>();
      this.warnings = warnings != null ? warnings : new ArrayList<>();
      this.success = success;
    }

    public String getFilename() {
      return filename;
    }

    public List<LintIssue> getErrors() {
      return errors;
    }

    public List<LintIssue> getWarnings() {
      return warnings;
    }

    public boolean isSuccess() {
      return success;
    }

    public boolean hasErrors() {
      return !errors.isEmpty();
    }

    public boolean hasWarnings() {
      return !warnings.isEmpty();
    }

    public int getErrorCount() {
      return errors.size();
    }

    public int getWarningCount() {
      return warnings.size();
    }

    public String getFormattedOutput() {
      StringBuilder sb = new StringBuilder();
      sb.append("--- Linting ").append(filename).append(" ---").append(System.lineSeparator());

      for (LintIssue error : errors) {
        sb.append("ERROR: ").append(error).append(System.lineSeparator());
      }

      for (LintIssue warning : warnings) {
        sb.append("WARNING: ").append(warning).append(System.lineSeparator());
      }

      sb.append(
          String.format(
              "Found %d errors and %d warnings%s",
              getErrorCount(), getWarningCount(), System.lineSeparator()));

      return sb.toString();
    }

    @Override
    public String toString() {
      return getFormattedOutput();
    }
  }

  /** Configuration class for linting options */
  public static class LintConfig {
    private boolean checkSemicolons = true;
    private boolean checkConsole = true;
    private boolean checkVarDeclarations = true;
    private boolean checkEquality = true;
    private boolean checkTrailingSpaces = true;
    private boolean checkUndeclaredVariables = true;
    private boolean checkFunctionDeclarations = true;

    // Getters and setters
    public boolean isCheckSemicolons() {
      return checkSemicolons;
    }

    public void setCheckSemicolons(boolean checkSemicolons) {
      this.checkSemicolons = checkSemicolons;
    }

    public boolean isCheckConsole() {
      return checkConsole;
    }

    public void setCheckConsole(boolean checkConsole) {
      this.checkConsole = checkConsole;
    }

    public boolean isCheckVarDeclarations() {
      return checkVarDeclarations;
    }

    public void setCheckVarDeclarations(boolean checkVarDeclarations) {
      this.checkVarDeclarations = checkVarDeclarations;
    }

    public boolean isCheckEquality() {
      return checkEquality;
    }

    public void setCheckEquality(boolean checkEquality) {
      this.checkEquality = checkEquality;
    }

    public boolean isCheckTrailingSpaces() {
      return checkTrailingSpaces;
    }

    public void setCheckTrailingSpaces(boolean checkTrailingSpaces) {
      this.checkTrailingSpaces = checkTrailingSpaces;
    }

    public boolean isCheckUndeclaredVariables() {
      return checkUndeclaredVariables;
    }

    public void setCheckUndeclaredVariables(boolean checkUndeclaredVariables) {
      this.checkUndeclaredVariables = checkUndeclaredVariables;
    }

    public boolean isCheckFunctionDeclarations() {
      return checkFunctionDeclarations;
    }

    public void setCheckFunctionDeclarations(boolean checkFunctionDeclarations) {
      this.checkFunctionDeclarations = checkFunctionDeclarations;
    }
  }

  /** Enhanced linter with configuration support */
  public LintResult lintCodeWithConfig(String jsCode, String filename, LintConfig config) {
    if (jsCode == null || jsCode.trim().isEmpty()) {
      return new LintResult(
          filename != null ? filename : "unknown",
          List.of(
              new LintIssue(
                  0, "Invalid input: code must be a non-empty string", "input-validation")),
          List.of(),
          false);
    }

    List<LintIssue> errors = new ArrayList<>();
    List<LintIssue> warnings = new ArrayList<>();

    String[] lines = jsCode.split("\\n");

    for (int i = 0; i < lines.length; i++) {
      int lineNum = i + 1;
      String line = lines[i];

      // Apply configurable linting rules
      if (config.isCheckSemicolons()) {
        checkSemicolons(line, lineNum, errors);
      }
      if (config.isCheckConsole()) {
        checkConsoleLog(line, lineNum, warnings);
      }
      if (config.isCheckVarDeclarations()) {
        checkVarDeclarations(line, lineNum, warnings);
      }
      if (config.isCheckEquality()) {
        checkEquality(line, lineNum, warnings);
      }
      if (config.isCheckTrailingSpaces()) {
        checkTrailingSpaces(line, lineNum, warnings);
      }
      if (config.isCheckUndeclaredVariables()) {
        checkUndeclaredVariables(line, lineNum, warnings);
      }
      if (config.isCheckFunctionDeclarations()) {
        checkFunctionDeclarations(line, lineNum, warnings);
      }
    }

    boolean success = errors.isEmpty();
    return new LintResult(filename != null ? filename : "input.js", errors, warnings, success);
  }

  /** Main method demonstrating the pure Java linter */
  public static void main(String[] args) {
    JavaScriptLinter linter = new JavaScriptLinter();

    try {
      // Example 1: Basic linting
      String exampleCode =
          """
                var x = 5
                console.log("Hello World")
                if (x == 5) {
                    return x
                }
                const y = 10;
                undeclaredVar = "test"
                """;

      System.out.println("=== Basic Linting ===");
      LintResult result1 = linter.lintCode(exampleCode, "example.js");
      System.out.print(result1.getFormattedOutput());
      System.out.println("Success: " + result1.isSuccess());

      // Example 2: Custom rules
      System.out.println("\n=== Custom Rules ===");
      String codeWithTodos =
          """
                const x = 5; // TODO: make this configurable
                const veryLongLineOfCodeThatExceedsTheMaximumLengthAllowedByOurCodingStandardsAndShouldBeRefactored = "test";
                const magicNumber = 42;
                """;

      LintResult result2 =
          linter.lintWithCustomRules(codeWithTodos, new ExampleCustomRules(), "custom.js");
      System.out.print(result2.getFormattedOutput());

      // Example 3: Configurable linting
      System.out.println("\n=== Configurable Linting (Console checks disabled) ===");
      LintConfig config = new LintConfig();
      config.setCheckConsole(false); // Disable console warnings

      LintResult result3 = linter.lintCodeWithConfig(exampleCode, "configured.js", config);
      System.out.print(result3.getFormattedOutput());

    } catch (Exception e) {
      System.err.println("Error: " + e.getMessage());
      e.printStackTrace();
    }
  }
}
