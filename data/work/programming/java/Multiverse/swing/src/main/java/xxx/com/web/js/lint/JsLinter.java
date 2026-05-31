package xxx.com.web.js.lint;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import xxx.com.web.js.execute.JsExecutor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Java application that uses GraalVM Polyglot to lint JavaScript code
 * Uses embedded JavaScript execution without requiring Node.js
 */
public class JsLinter {

    private final Context polyglotContext;
    private final JsExecutor jsExecutor;
    private final Value linterFunction;

    public JsLinter() {
        // Initialize GraalVM Polyglot context for JavaScript execution
        this.polyglotContext = Context.newBuilder("js")
                .allowAllAccess(true)
                .build();

        this.jsExecutor = new JsExecutor();
        this.linterFunction = initializeLinter();
    }

    /**
     * Initializes the JavaScript linting function in the GraalVM context
     */
    private Value initializeLinter() {
        String linterScript = """
            function createLinter() {
                return {
                    lint: function(code, filename) {
                        const errors = [];
                        const warnings = [];
                        
                        if (!code || typeof code !== 'string') {
                            errors.push({
                                line: 0,
                                message: 'Invalid input: code must be a non-empty string',
                                rule: 'input-validation'
                            });
                            return {
                                filename: filename || 'unknown',
                                errors: errors,
                                warnings: warnings,
                                errorCount: errors.length,
                                warningCount: warnings.length
                            };
                        }
                        
                        const lines = code.split('\\n');
                        
                        lines.forEach(function(line, index) {
                            const lineNum = index + 1;
                            
                            // Check for various linting issues
                            checkSemicolons(line, lineNum, errors);
                            checkConsoleLog(line, lineNum, warnings);
                            checkVarDeclarations(line, lineNum, warnings);
                            checkEquality(line, lineNum, warnings);
                            checkTrailingSpaces(line, lineNum, warnings);
                            checkUndeclaredVariables(line, lineNum, warnings);
                            checkFunctionDeclarations(line, lineNum, warnings);
                        });
                        
                        return {
                            filename: filename || 'input.js',
                            errors: errors,
                            warnings: warnings,
                            errorCount: errors.length,
                            warningCount: warnings.length
                        };
                    }
                };
                
                function checkSemicolons(line, lineNum, errors) {
                    const trimmed = line.trim();
                    if (trimmed.length > 0 && 
                        !trimmed.endsWith(';') && 
                        !trimmed.endsWith('{') && 
                        !trimmed.endsWith('}') && 
                        !trimmed.startsWith('//') && 
                        !trimmed.startsWith('*') &&
                        !trimmed.match(/^(if|for|while|function|class|else)\\s*[\\(\\{]/) &&
                        !trimmed.match(/^(import|export)\\s/)) {
                        
                        if (trimmed.match(/^(return|break|continue|throw)\\s/) ||
                            trimmed.match(/^(const|let|var)\\s+\\w+\\s*=/) ||
                            trimmed.match(/^\\w+\\s*\\(.*\\)\\s*$/) ||
                            trimmed.match(/^\\w+\\.\\w+.*$/) && !trimmed.includes('function')) {
                            errors.push({
                                line: lineNum,
                                message: 'Missing semicolon',
                                rule: 'semicolon'
                            });
                        }
                    }
                }
                
                function checkConsoleLog(line, lineNum, warnings) {
                    if (line.includes('console.log') || 
                        line.includes('console.warn') || 
                        line.includes('console.error')) {
                        warnings.push({
                            line: lineNum,
                            message: 'Console statements should be removed in production',
                            rule: 'no-console'
                        });
                    }
                }
                
                function checkVarDeclarations(line, lineNum, warnings) {
                    if (line.trim().match(/^var\\s+\\w+/)) {
                        warnings.push({
                            line: lineNum,
                            message: 'Use let or const instead of var',
                            rule: 'no-var'
                        });
                    }
                }
                
                function checkEquality(line, lineNum, warnings) {
                    if ((line.includes('==') && !line.includes('===')) ||
                        (line.includes('!=') && !line.includes('!=='))) {
                        warnings.push({
                            line: lineNum,
                            message: 'Use strict equality (=== or !==) instead of loose equality',
                            rule: 'strict-equality'
                        });
                    }
                }
                
                function checkTrailingSpaces(line, lineNum, warnings) {
                    if (line.endsWith(' ') || line.endsWith('\\t')) {
                        warnings.push({
                            line: lineNum,
                            message: 'Trailing whitespace detected',
                            rule: 'no-trailing-spaces'
                        });
                    }
                }
                
                function checkUndeclaredVariables(line, lineNum, warnings) {
                    // Simple check for potential undeclared variables
                    const assignmentMatch = line.match(/^\\s*(\\w+)\\s*=/);
                    if (assignmentMatch && 
                        !line.includes('var ') && 
                        !line.includes('let ') && 
                        !line.includes('const ') &&
                        !line.includes('function ') &&
                        !line.includes('.')) {
                        warnings.push({
                            line: lineNum,
                            message: 'Possible undeclared variable: ' + assignmentMatch[1],
                            rule: 'undeclared-var'
                        });
                    }
                }
                
                function checkFunctionDeclarations(line, lineNum, warnings) {
                    if (line.trim().match(/^function\\s+\\w+\\s*\\([^)]*\\)\\s*$/)) {
                        warnings.push({
                            line: lineNum,
                            message: 'Function declaration without body - missing opening brace?',
                            rule: 'function-body'
                        });
                    }
                }
            }
            
            // Return the linter factory function
            createLinter;
            """;

        try {
            // Execute the script and get the linter factory
            Value linterFactory = polyglotContext.eval("js", linterScript);
            return linterFactory.execute();
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize JavaScript linter", e);
        }
    }

    /**
     * Lints JavaScript code from a string
     */
    public LintResult lintCode(String jsCode, String filename) {
        try {
            Value result = linterFunction.getMember("lint").execute(jsCode, filename);
            return convertToLintResult(result);
        } catch (Exception e) {
            return new LintResult(filename, List.of(new LintIssue(0, "Linting error: " + e.getMessage(), "internal-error")),
                    List.of(), false);
        }
    }

    /**
     * Lints JavaScript code from a file
     */
    public LintResult lintFile(String filePath) throws IOException {
        Path path = Paths.get(filePath);
        if (!Files.exists(path)) {
            throw new IllegalArgumentException("File not found: " + filePath);
        }

        String code = Files.readString(path);
        return lintCode(code, path.getFileName().toString());
    }

    /**
     * Lints multiple JavaScript files
     */
    public List<LintResult> lintFiles(List<String> filePaths) {
        List<LintResult> results = new ArrayList<>();

        for (String filePath : filePaths) {
            try {
                results.add(lintFile(filePath));
            } catch (Exception e) {
                results.add(new LintResult(filePath,
                        List.of(new LintIssue(0, "Error reading file: " + e.getMessage(), "file-error")),
                        List.of(), false));
            }
        }

        return results;
    }

    /**
     * Executes custom JavaScript linting rules
     */
    public LintResult lintWithCustomRules(String jsCode, String customRulesScript, String filename) {
        try {
            // Combine the base linter script with custom rules
            String combinedScript = """
                function createLinter() {
                    return {
                        lint: function(code, filename) {
                            const errors = [];
                            const warnings = [];
                            
                            if (!code || typeof code !== 'string') {
                                errors.push({
                                    line: 0,
                                    message: 'Invalid input: code must be a non-empty string',
                                    rule: 'input-validation'
                                });
                                return {
                                    filename: filename || 'unknown',
                                    errors: errors,
                                    warnings: warnings,
                                    errorCount: errors.length,
                                    warningCount: warnings.length
                                };
                            }
                            
                            const lines = code.split('\\n');
                            
                            lines.forEach(function(line, index) {
                                const lineNum = index + 1;
                                
                                // Check for various linting issues
                                checkSemicolons(line, lineNum, errors);
                                checkConsoleLog(line, lineNum, warnings);
                                checkVarDeclarations(line, lineNum, warnings);
                                checkEquality(line, lineNum, warnings);
                                checkTrailingSpaces(line, lineNum, warnings);
                                checkUndeclaredVariables(line, lineNum, warnings);
                                checkFunctionDeclarations(line, lineNum, warnings);
                            });
                            
                            return {
                                filename: filename || 'input.js',
                                errors: errors,
                                warnings: warnings,
                                errorCount: errors.length,
                                warningCount: warnings.length
                            };
                        }
                    };
                    
                    // Helper functions (same as before)
                    function checkSemicolons(line, lineNum, errors) {
                        const trimmed = line.trim();
                        if (trimmed.length > 0 && 
                            !trimmed.endsWith(';') && 
                            !trimmed.endsWith('{') && 
                            !trimmed.endsWith('}') && 
                            !trimmed.startsWith('//') && 
                            !trimmed.startsWith('*') &&
                            !trimmed.match(/^(if|for|while|function|class|else)\\s*[\\(\\{]/) &&
                            !trimmed.match(/^(import|export)\\s/)) {
                            
                            if (trimmed.match(/^(return|break|continue|throw)\\s/) ||
                                trimmed.match(/^(const|let|var)\\s+\\w+\\s*=/) ||
                                trimmed.match(/^\\w+\\s*\\(.*\\)\\s*$/) ||
                                trimmed.match(/^\\w+\\.\\w+.*$/) && !trimmed.includes('function')) {
                                errors.push({
                                    line: lineNum,
                                    message: 'Missing semicolon',
                                    rule: 'semicolon'
                                });
                            }
                        }
                    }
                    
                    function checkConsoleLog(line, lineNum, warnings) {
                        if (line.includes('console.log') || 
                            line.includes('console.warn') || 
                            line.includes('console.error')) {
                            warnings.push({
                                line: lineNum,
                                message: 'Console statements should be removed in production',
                                rule: 'no-console'
                            });
                        }
                    }
                    
                    function checkVarDeclarations(line, lineNum, warnings) {
                        if (line.trim().match(/^var\\s+\\w+/)) {
                            warnings.push({
                                line: lineNum,
                                message: 'Use let or const instead of var',
                                rule: 'no-var'
                            });
                        }
                    }
                    
                    function checkEquality(line, lineNum, warnings) {
                        if ((line.includes('==') && !line.includes('===')) ||
                            (line.includes('!=') && !line.includes('!=='))) {
                            warnings.push({
                                line: lineNum,
                                message: 'Use strict equality (=== or !==) instead of loose equality',
                                rule: 'strict-equality'
                            });
                        }
                    }
                    
                    function checkTrailingSpaces(line, lineNum, warnings) {
                        if (line.endsWith(' ') || line.endsWith('\\t')) {
                            warnings.push({
                                line: lineNum,
                                message: 'Trailing whitespace detected',
                                rule: 'no-trailing-spaces'
                            });
                        }
                    }
                    
                    function checkUndeclaredVariables(line, lineNum, warnings) {
                        const assignmentMatch = line.match(/^\\s*(\\w+)\\s*=/);
                        if (assignmentMatch && 
                            !line.includes('var ') && 
                            !line.includes('let ') && 
                            !line.includes('const ') &&
                            !line.includes('function ') &&
                            !line.includes('.')) {
                            warnings.push({
                                line: lineNum,
                                message: 'Possible undeclared variable: ' + assignmentMatch[1],
                                rule: 'undeclared-var'
                            });
                        }
                    }
                    
                    function checkFunctionDeclarations(line, lineNum, warnings) {
                        if (line.trim().match(/^function\\s+\\w+\\s*\\([^)]*\\)\\s*$/)) {
                            warnings.push({
                                line: lineNum,
                                message: 'Function declaration without body - missing opening brace?',
                                rule: 'function-body'
                            });
                        }
                    }
                }
                
                """ + customRulesScript + """
                
                // Execute custom linting
                function lintWithCustom(code, filename) {
                    // Use the base linter
                    const baseLinter = createLinter();
                    const baseResult = baseLinter.lint(code, filename);
                    
                    // Apply custom rules if defined
                    if (typeof applyCustomRules === 'function') {
                        const customResult = applyCustomRules(code, filename);
                        return {
                            filename: filename,
                            errors: baseResult.errors.concat(customResult.errors || []),
                            warnings: baseResult.warnings.concat(customResult.warnings || []),
                            errorCount: baseResult.errorCount + (customResult.errorCount || 0),
                            warningCount: baseResult.warningCount + (customResult.warningCount || 0)
                        };
                    }
                    
                    return baseResult;
                }
                
                lintWithCustom;
                """;

            // Execute the combined script using GraalVM Polyglot directly
            Value customLinter = polyglotContext.eval("js", combinedScript);
            Value result = customLinter.execute(jsCode, filename);
            return convertToLintResult(result);

        } catch (Exception e) {
            return new LintResult(filename,
                    List.of(new LintIssue(0, "Custom linting error: " + e.getMessage(), "custom-error")),
                    List.of(), false);
        }
    }

    /**
     * Converts GraalVM Value result to LintResult object
     */
    private LintResult convertToLintResult(Value jsResult) {
        try {
            String filename = jsResult.getMember("filename").asString();
            Value errorsArray = jsResult.getMember("errors");
            Value warningsArray = jsResult.getMember("warnings");

            List<LintIssue> errors = convertToLintIssues(errorsArray);
            List<LintIssue> warnings = convertToLintIssues(warningsArray);

            boolean success = errors.isEmpty();

            return new LintResult(filename, errors, warnings, success);

        } catch (Exception e) {
            return new LintResult("unknown",
                    List.of(new LintIssue(0, "Error processing lint result: " + e.getMessage(), "conversion-error")),
                    List.of(), false);
        }
    }

    /**
     * Converts JavaScript array of issues to Java List
     */
    private List<LintIssue> convertToLintIssues(Value jsArray) {
        List<LintIssue> issues = new ArrayList<>();

        if (jsArray != null && jsArray.hasArrayElements()) {
            long arraySize = jsArray.getArraySize();
            for (long i = 0; i < arraySize; i++) {
                Value issue = jsArray.getArrayElement(i);
                int line = issue.getMember("line").asInt();
                String message = issue.getMember("message").asString();
                String rule = issue.getMember("rule").asString();
                issues.add(new LintIssue(line, message, rule));
            }
        }

        return issues;
    }

    /**
     * Closes the GraalVM context and releases resources
     */
    public void close() {
        if (polyglotContext != null) {
            polyglotContext.close();
        }
    }

    /**
     * Represents a single linting issue (error or warning)
     */
    public static class LintIssue {
        private final int line;
        private final String message;
        private final String rule;

        public LintIssue(int line, String message, String rule) {
            this.line = line;
            this.message = message;
            this.rule = rule;
        }

        public int getLine() { return line; }
        public String getMessage() { return message; }
        public String getRule() { return rule; }

        @Override
        public String toString() {
            return String.format("Line %d: %s (%s)", line, message, rule);
        }
    }

    /**
     * Result of a linting operation
     */
    public static class LintResult {
        private final String filename;
        private final List<LintIssue> errors;
        private final List<LintIssue> warnings;
        private final boolean success;

        public LintResult(String filename, List<LintIssue> errors, List<LintIssue> warnings, boolean success) {
            this.filename = filename;
            this.errors = errors;
            this.warnings = warnings;
            this.success = success;
        }

        public String getFilename() { return filename; }
        public List<LintIssue> getErrors() { return errors; }
        public List<LintIssue> getWarnings() { return warnings; }
        public boolean isSuccess() { return success; }
        public boolean hasErrors() { return !errors.isEmpty(); }
        public boolean hasWarnings() { return !warnings.isEmpty(); }

        public int getErrorCount() { return errors.size(); }
        public int getWarningCount() { return warnings.size(); }

        public String getFormattedOutput() {
            StringBuilder sb = new StringBuilder();
            sb.append("--- Linting ").append(filename).append(" ---\\n");

            for (LintIssue error : errors) {
                sb.append("ERROR: ").append(error).append("\\n");
            }

            for (LintIssue warning : warnings) {
                sb.append("WARNING: ").append(warning).append("\\n");
            }

            sb.append(String.format("Found %d errors and %d warnings\\n",
                    getErrorCount(), getWarningCount()));

            return sb.toString();
        }

        @Override
        public String toString() {
            return getFormattedOutput();
        }
    }

    /**
     * Main method demonstrating the linter usage
     */
    public static void main(String[] args) {
        JsLinter linter = new JsLinter();

        try {
            // Example 1: Lint code from string
            String exampleCode = """
                var x = 5
                console.log("Hello World")
                if (x == 5) {
                    return x
                }
                const y = 10;
                undeclaredVar = "test"
                """;

            System.out.println("=== Linting code from string ===");
            LintResult result1 = linter.lintCode(exampleCode, "example.js");
            System.out.println(result1.getFormattedOutput());
            System.out.println("Success: " + result1.isSuccess());

            // Example 2: Create and lint a sample file
            Path sampleFile = Paths.get("sample.js");
            String sampleCode = """
                // Sample JavaScript file with various issues
                var name = "John"
                const age = 25;
                
                function greet() {
                    console.log("Hello, " + name)
                    if (age == 25) {
                        return "Adult"
                    }
                }
                
                let message = greet();
                console.log(message);
                """;

            Files.write(sampleFile, sampleCode.getBytes());

            System.out.println("\\n=== Linting file ===");
            LintResult result2 = linter.lintFile(sampleFile.toString());
            System.out.println(result2.getFormattedOutput());

            // Example 3: Custom linting rules
            String customRules = """
                function applyCustomRules(code, filename) {
                    const errors = [];
                    const warnings = [];
                    const lines = code.split('\\n');
                    
                    lines.forEach(function(line, index) {
                        const lineNum = index + 1;
                        
                        // Custom rule: detect TODO comments
                        if (line.includes('TODO') || line.includes('FIXME')) {
                            warnings.push({
                                line: lineNum,
                                message: 'TODO/FIXME comment found',
                                rule: 'no-todos'
                            });
                        }
                        
                        // Custom rule: detect long lines
                        if (line.length > 100) {
                            warnings.push({
                                line: lineNum,
                                message: 'Line too long (' + line.length + ' characters)',
                                rule: 'max-line-length'
                            });
                        }
                    });
                    
                    return {
                        errors: errors,
                        warnings: warnings,
                        errorCount: errors.length,
                        warningCount: warnings.length
                    };
                }
                """;

            String codeWithTodos = """
                const x = 5; // TODO: make this configurable
                const veryLongLineOfCodeThatExceedsTheMaximumLengthAllowedByOurCodingStandardsAndShouldBeRefactored = "test";
                """;

            System.out.println("\\n=== Linting with custom rules ===");
            LintResult result3 = linter.lintWithCustomRules(codeWithTodos, customRules, "custom.js");
            System.out.println(result3.getFormattedOutput());

            // Cleanup
            Files.deleteIfExists(sampleFile);

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        } finally {
            linter.close();
        }
    }
}