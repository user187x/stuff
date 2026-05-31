package xxx.com.code.formatter.java;

import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.ToolFactory;
import org.eclipse.jdt.core.formatter.CodeFormatter;
import org.eclipse.jface.text.Document;
import org.eclipse.text.edits.TextEdit;
import java.util.Map;

/**
 * Simple Java formatter using Eclipse JDT - NO JVM FLAGS NEEDED!
 *
 * <p>Add this dependency to your pom.xml: <dependency> <groupId>org.eclipse.jdt</groupId>
 * <artifactId>org.eclipse.jdt.core</artifactId> <version>3.39.0</version> </dependency>
 */
public class EclipseJavaFormatter {

  public static void main(String[] args) {
    String unformattedCode =
        """
            package xxx.com.test;

            public class TestClass {
            public static void main(String[] args) {
            int x=5;
            int y=10;
            if(x+y>10){
            System.out.println("Sum is greater than 10");
            }else{
            System.out.println("Sum is 10 or less");
            }
            }
            }
            """;

    String formatted = formatJava(unformattedCode);
    System.out.println("=== FORMATTED CODE ===");
    System.out.println(formatted);
  }

  public static String formatJava(String source) {
    try {
      // Configure formatter options
      Map<String, String> options = JavaCore.getOptions();
      options.put(JavaCore.COMPILER_SOURCE, "21");
      options.put(JavaCore.COMPILER_COMPLIANCE, "21");
      options.put(JavaCore.COMPILER_CODEGEN_TARGET_PLATFORM, "21");

      // Create the formatter
      CodeFormatter formatter = ToolFactory.createCodeFormatter(options);

      // Format the source
      TextEdit edit =
          formatter.format(
              CodeFormatter.K_COMPILATION_UNIT,
              source,
              0,
              source.length(),
              0,
              System.lineSeparator());

      if (edit == null) {
        // Formatting failed - return original
        return source;
      }

      // Apply the edit
      Document document = new Document(source);
      edit.apply(document);
      return document.get();

    } catch (Exception e) {
        System.out.println("Failed to format java: " + e.getMessage());
      return source;
    }
  }
}
