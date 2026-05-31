package xxx.com.code.formatter.java;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;

/**
 * Simplest possible JavaParser formatter
 *
 * Add this dependency to your pom.xml:
 * <dependency>
 *     <groupId>com.github.javaparser</groupId>
 *     <artifactId>javaparser-core</artifactId>
 *     <version>3.26.2</version>
 * </dependency>
 */
public class SimpleJavaFormatter {

    public static void main(String[] args) {
        String sourceCode = """
            package xxx.com.test;
            
            public class TestClass {
            public static void main(String[] args) {
            int x=5;int y=10;
            if(x+y>10){System.out.println("Greater than 10");}
            else{System.out.println("10 or less");}
            }
            }
            """;

        String formatted = formatJava(sourceCode);
        System.out.println("=== FORMATTED CODE ===");
        System.out.println(formatted);
    }

    public static String formatJava(String source) {
        try {
            // Parse the Java code
            JavaParser parser = new JavaParser();
            ParseResult<CompilationUnit> result = parser.parse(source);

            // Check if parsing was successful
            if (result.getResult().isPresent()) {
                CompilationUnit cu = result.getResult().get();

                // Simply call toString() - JavaParser formats it nicely by default
                return cu.toString();
            } else {
                System.err.println("Failed to parse Java code");
                return source;
            }

        } catch (Exception e) {
            System.err.println("Error formatting code: " + e.getMessage());
            e.printStackTrace();
            return source;
        }
    }
}