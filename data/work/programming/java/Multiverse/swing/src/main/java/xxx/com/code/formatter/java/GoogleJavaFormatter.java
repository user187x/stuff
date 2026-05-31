package xxx.com.code.formatter.java;

import com.google.googlejavaformat.java.Formatter;
import com.google.googlejavaformat.java.FormatterException;

public class GoogleJavaFormatter {

    public static void main(String[] args) {
        try {
            // Example unformatted Java code
            String sourceCode = """
          package xxx.com.code.formatter;
          
          import com.google.googlejavaformat.java.Formatter;
          import com.google.googlejavaformat.java.FormatterException;
          
          public class ExampleClass {
              public static void main(String[] args) {
                  System.out.println("Hello, World!");
                  int x=5;
                  int y=10;
                  int sum=x+y;
                  if(sum>10){
                      System.out.println("Sum is greater than 10");
                  }else{
                      System.out.println("Sum is 10 or less");
                  }
              }
              
              private void someMethod(){
                  String text="test";
                  System.out.println(text);
              }
          }
          """;

            // Format the source code
            String formattedSource = new Formatter().formatSource(sourceCode);

            System.out.println("=== FORMATTED CODE ===");
            System.out.println(formattedSource);

        } catch (FormatterException e) {
            System.err.println("Failed to format source code: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Utility method to format Java source code
     *
     * @param sourceCode The unformatted Java source code
     * @return The formatted Java source code
     * @throws FormatterException if the source code cannot be formatted
     */
    public static String format(String sourceCode) throws FormatterException {
        return new Formatter().formatSource(sourceCode);
    }
}