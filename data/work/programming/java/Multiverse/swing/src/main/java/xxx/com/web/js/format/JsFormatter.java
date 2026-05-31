package xxx.com.web.js.format;

import com.google.javascript.jscomp.*;
import org.apache.commons.lang3.tuple.Pair;

import javax.swing.filechooser.FileSystemView;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class JsFormatter {

  public enum Mode {UGLY, PRETTY}

  public static void main(String[] args) throws Exception {

    // Original file
    URL resourceUrl = JsFormatter.class.getClassLoader().getResource("lib/js/beautify.js");
    File file = Paths.get(Objects.requireNonNull(resourceUrl).toURI()).toFile();
    String inputFile = file.getAbsolutePath();

    // Uglified file
    FileSystemView fsv = FileSystemView.getFileSystemView();
    File desktopDirectory = fsv.getHomeDirectory();
    String desktopPath = desktopDirectory.getAbsolutePath();

    // Uglified file
    String uglyOutput = desktopPath + "\\ungly.js";
    process(inputFile, uglyOutput, getCompOptions(Mode.UGLY));

    // Prettified file
    String prettyOutput = desktopPath + "\\ungly.js";
    process(uglyOutput, uglyOutput, getCompOptions(Mode.PRETTY));
  }

  public static Pair<Compiler, CompilerOptions> getCompOptions(Mode mode) {

    Compiler compiler = new Compiler();
    CompilerOptions options = new CompilerOptions();

    if(mode == Mode.UGLY) {
      CompilationLevel.SIMPLE_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
      options.setPrettyPrint(false);
    }
    else {
      CompilationLevel.WHITESPACE_ONLY.setOptionsForCompilationLevel(options);
      options.setPrettyPrint(true);
    }

    return Pair.of(compiler, options);
  }

  public static void process(String inputFile, String outputFile, Pair<Compiler, CompilerOptions> compOptions) throws Exception {

    Compiler compiler = compOptions.getLeft();
    CompilerOptions options = compOptions.getRight();

    try {

      SourceFile input = SourceFile.fromFile(inputFile);
      Result result = compiler.compile(Collections.<SourceFile>emptyList(), List.of(input), options);

      if (result.success) {
        try (FileWriter writer = new FileWriter(outputFile)) {
          writer.write(compiler.toSource());
        }
        System.out.println("Formatter successful. Output written to " + outputFile);
      } else {

        System.err.println("Formatter failed:");

        for (var warning : result.warnings) {
          System.err.println(warning.toString());
        }
        for (var error : result.errors) {
          System.err.println(error.toString());
        }
      }
    } catch (IOException e) {
      System.err.println("Error reading input file: " + e.getMessage());
    }
  }
}
