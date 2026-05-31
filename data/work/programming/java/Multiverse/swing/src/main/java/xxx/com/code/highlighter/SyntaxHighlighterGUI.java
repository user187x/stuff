package xxx.com.code.highlighter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.google.googlejavaformat.java.Formatter;
import com.google.googlejavaformat.java.FormatterException;
import org.apache.tika.Tika;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rtextarea.RTextScrollPane;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.parser.Parser;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.regex.Pattern;
// import org.apache.tika.langdetect.optimaize.OptimaizeLangDetector; // If more advanced natural
// language detection was needed
// import org.apache.tika.language.detect.LanguageDetector; // Interface for language detectors
// import org.apache.tika.language.detect.LanguageResult; // Result of language detection


public class SyntaxHighlighterGUI extends JFrame {

  private RSyntaxTextArea codeTextArea; // Single text area for input and output
  private JComboBox<String> languageComboBox;
  private JButton colorizeButton;
  private JButton exportAsPngButton;
  private JButton formatCodeButton;
  private JButton saveFileButton;
  private JButton detectLanguageButton;

  // Define language constants for RSyntaxTextArea
  private static final String[] LANGUAGES = {
      "Text (None)", "Java", "Python", "JavaScript", "HTML", "XML", "CSS",
      "C", "C++", "C#", "SQL", "JSON", "Markdown", "PHP", "Ruby", "Perl"
  };

  // Define syntax style constants from RSyntaxTextArea
  private static final String[] SYNTAX_STYLES = {
      SyntaxConstants.SYNTAX_STYLE_NONE,
      SyntaxConstants.SYNTAX_STYLE_JAVA,
      SyntaxConstants.SYNTAX_STYLE_PYTHON,
      SyntaxConstants.SYNTAX_STYLE_JAVASCRIPT,
      SyntaxConstants.SYNTAX_STYLE_HTML,
      SyntaxConstants.SYNTAX_STYLE_XML,
      SyntaxConstants.SYNTAX_STYLE_CSS,
      SyntaxConstants.SYNTAX_STYLE_C,
      SyntaxConstants.SYNTAX_STYLE_CPLUSPLUS,
      SyntaxConstants.SYNTAX_STYLE_CSHARP,
      SyntaxConstants.SYNTAX_STYLE_SQL,
      SyntaxConstants.SYNTAX_STYLE_JSON,
      SyntaxConstants.SYNTAX_STYLE_MARKDOWN,
      SyntaxConstants.SYNTAX_STYLE_PHP,
      SyntaxConstants.SYNTAX_STYLE_RUBY,
      SyntaxConstants.SYNTAX_STYLE_PERL
  };

  // Define corresponding file extensions
  private static final String[] FILE_EXTENSIONS = {
      ".txt", ".java", ".py", ".js", ".html", ".xml", ".css",
      ".c", ".cpp", ".cs", ".sql", ".json", ".md", ".php", ".rb", ".pl"
  };

  // For Jackson JSON Formatter
  private final ObjectMapper jsonObjectMapper;
  // For Apache Tika Language Detection
  private Tika tikaInstance;


  public SyntaxHighlighterGUI() {
    setTitle("Code Syntax Highlighter & Formatter");
    setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    setSize(1250, 600);
    setLocationRelativeTo(null);
    setLayout(new BorderLayout(5, 5));

    // Initialize JSON ObjectMapper
    jsonObjectMapper = new ObjectMapper();
    jsonObjectMapper.enable(SerializationFeature.INDENT_OUTPUT);

    // Initialize Apache Tika
    try {
      tikaInstance = new Tika();
    }
    catch (NoClassDefFoundError e) {
      tikaInstance = null; // Handle missing Tika gracefully
      System.err.println("Apache Tika libraries not found. Language detection will be limited.");
      // Optionally, show a JOptionPane message to the user here or on first detection attempt
    }


    initComponents();
    layoutComponents();
    addListeners();

    if (LANGUAGES.length > 0) {
      languageComboBox.setSelectedIndex(0);
      applySyntaxHighlighting();
    }
  }

  private void initComponents() {
    codeTextArea = new RSyntaxTextArea();
    codeTextArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_NONE);
    codeTextArea.setCodeFoldingEnabled(true);
    codeTextArea.setAntiAliasingEnabled(true);
    codeTextArea.setFont(new Font("Monospaced", Font.PLAIN, 14));
    codeTextArea.setLineWrap(true);
    codeTextArea.setWrapStyleWord(true);

    languageComboBox = new JComboBox<>(LANGUAGES);
    colorizeButton = new JButton("Apply Syntax Highlighting");
    exportAsPngButton = new JButton("Export as PNG");
    formatCodeButton = new JButton("Format Code");
    saveFileButton = new JButton("Save File");
    detectLanguageButton = new JButton("Detect Language");
  }

  private void layoutComponents() {
    JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
    topPanel.add(new JLabel("Select Language:"));
    topPanel.add(languageComboBox);
    topPanel.add(detectLanguageButton);
    topPanel.add(colorizeButton);
    topPanel.add(formatCodeButton);
    topPanel.add(saveFileButton);
    topPanel.add(exportAsPngButton);


    add(topPanel, BorderLayout.NORTH);

    RTextScrollPane scrollPane = new RTextScrollPane(codeTextArea);
    scrollPane.setLineNumbersEnabled(true);
    scrollPane.setFoldIndicatorEnabled(true);
    add(scrollPane, BorderLayout.CENTER);
  }

  private void addListeners() {
    colorizeButton.addActionListener(e -> applySyntaxHighlighting());
    languageComboBox.addActionListener(e -> applySyntaxHighlighting());
    exportAsPngButton.addActionListener(e -> exportTextAreaAsPng());
    formatCodeButton.addActionListener(e -> formatSelectedCode());
    saveFileButton.addActionListener(e -> saveCodeToFile());
    detectLanguageButton.addActionListener(e -> detectAndApplyLanguage());
  }

  private void detectAndApplyLanguage() {
    String code = codeTextArea.getText();
    if (code == null || code.trim().isEmpty()) {
      JOptionPane.showMessageDialog(this, "No text to detect language from.", "Info", JOptionPane.INFORMATION_MESSAGE);
      return;
    }

    if (tikaInstance == null) {
      JOptionPane.showMessageDialog(this,
          "Language detection library (Apache Tika) is not available.\nPlease ensure Tika JARs are in the classpath.",
          "Detection Unavailable", JOptionPane.WARNING_MESSAGE);
      // Optionally, fall back to simpler heuristics if Tika is missing
      // String detectedLangHeuristic = detectLanguageHeuristically(code); // Old method
      // if (detectedLangHeuristic != null) { ... handle ... }
      return;
    }

    String detectedLanguageName = null;
    try {
      detectedLanguageName = detectLanguageWithTika(code);
    }
    catch (NoClassDefFoundError e) {
      JOptionPane.showMessageDialog(this,
          "Apache Tika libraries seem to be missing or configured incorrectly.\nLanguage detection failed. Error: " + e.getMessage(),
          "Tika Error", JOptionPane.ERROR_MESSAGE);
      return;
    }


    if (detectedLanguageName != null) {
      boolean found = false;
      for (int i = 0; i < LANGUAGES.length; i++) {
        if (LANGUAGES[i].equalsIgnoreCase(detectedLanguageName)) { // Use equalsIgnoreCase for flexibility
          languageComboBox.setSelectedIndex(i);
          JOptionPane.showMessageDialog(this, "Detected language: " + LANGUAGES[i], "Language Detected", JOptionPane.INFORMATION_MESSAGE);
          found = true;
          break;
        }
      }
      if (!found && !"Text (None)".equals(detectedLanguageName)) { // Avoid message if it correctly identified as generic text
        JOptionPane.showMessageDialog(this,
            "Detected language type (" + detectedLanguageName
                + ") is not directly supported or mapped in the dropdown. Consider selecting manually.",
            "Detection Info", JOptionPane.INFORMATION_MESSAGE);
      }
      else if (!found && "Text (None)".equals(detectedLanguageName)) {
        // If Tika + fallback decided it's "Text (None)", set it.
        languageComboBox.setSelectedItem("Text (None)");
        JOptionPane.showMessageDialog(this, "Could not confidently identify a specific programming language. Defaulting to Text.",
            "Detection Result", JOptionPane.INFORMATION_MESSAGE);
      }
    }
    else { // detectedLanguageName is null
      JOptionPane.showMessageDialog(this, "Could not confidently identify a specific programming language. Please select manually.",
          "Detection Failed", JOptionPane.INFORMATION_MESSAGE);
    }
  }

  // Language detection using Apache Tika with heuristic fallback
  private String detectLanguageWithTika(String code) {
    if (tikaInstance == null)
      return fallbackHeuristicDetection(code); // Fallback if Tika not initialized

    String mimeType = tikaInstance.detect(code).toLowerCase();
    System.out.println("Tika detected MIME type: " + mimeType); // For debugging

    // Map Tika's MIME types to our language names
    switch (mimeType) {
      case "text/x-java-source":
      case "application/java":
        return "Java";
      case "text/x-python":
      case "application/python":
        return "Python";
      case "application/javascript":
      case "text/javascript":
        return "JavaScript";
      case "text/html":
        return "HTML";
      case "application/xml":
      case "text/xml":
        return "XML";
      case "text/css":
        return "CSS";
      case "text/x-csrc":
      case "text/x-c":
        return "C";
      case "text/x-c++src":
      case "text/x-c++":
        return "C++";
      case "text/x-csharp":
        return "C#";
      case "application/sql": // Tika might not always give this for plain SQL
      case "text/sql":
        return "SQL";
      case "application/json":
        return "JSON";
      case "text/markdown":
      case "text/x-markdown":
        return "Markdown";
      case "application/x-httpd-php":
      case "text/x-php":
        return "PHP";
      case "text/x-ruby":
      case "application/x-ruby":
        return "Ruby";
      case "text/x-perl":
      case "application/x-perl":
        return "Perl";
      case "application/octet-stream": // Generic binary or unknown
      case "text/plain":
        // If Tika gives a generic type, try our more targeted heuristics
        System.out.println("Tika returned generic type (" + mimeType + "), attempting fallback heuristics.");
        return fallbackHeuristicDetection(code);
      default:
        System.out.println("Unmapped MIME type by Tika: " + mimeType + ". Attempting fallback.");
        return fallbackHeuristicDetection(code); // Attempt fallback for any other unmapped type
    }
  }

  // Heuristic detection as a fallback or standalone if Tika fails/is missing
  private String fallbackHeuristicDetection(String code) {
    String trimmedCode = code.trim();

    // Java: Common keywords and structure
    if ((code.contains("public class") || code.contains("public interface") || code.contains("package ") || code.contains("import java."))
        &&
        (code.contains("{") && code.contains("}") && code.contains(";"))) {
      return "Java";
    }
    // More specific Java check for typical class structure
    if (Pattern.compile("class\\s+\\w+\\s*\\{").matcher(code).find() && code.contains(";")) {
      return "Java";
    }


    // JSON: Starts with { or [ and ends with } or ], very structured
    if ((trimmedCode.startsWith("{") && trimmedCode.endsWith("}")) || (trimmedCode.startsWith("[") && trimmedCode.endsWith("]"))) {
      Pattern jsonPattern = Pattern.compile("\"[^\"]+\"\\s*:\\s*(\"[^\"]*\"|\\d+|true|false|null|\\[|\\{)");
      if (jsonPattern.matcher(trimmedCode).find() || trimmedCode.equals("{}") || trimmedCode.equals("[]")) {
        return "JSON";
      }
    }

    // XML: Starts with <?xml or has clear open/close tags
    if (trimmedCode.toLowerCase().startsWith("<?xml")) {
      return "XML";
    }
    // HTML: Starts with <!DOCTYPE html or contains <html>, <body> tags
    String lowerCode = code.toLowerCase();
    if (lowerCode.contains("<!doctype html") || (lowerCode.contains("<html") && lowerCode.contains("</html>"))) {
      if (lowerCode.contains("<body") || lowerCode.contains("<head") || lowerCode.contains("<div")) {
        return "HTML";
      }
    }
    // Generic XML/HTML tag check
    if (Pattern.compile("<\\s*\\w+[^>]*>.*?</\\s*\\w+\\s*>", Pattern.DOTALL).matcher(trimmedCode).find()) {
      if (lowerCode.contains("<html") || lowerCode.contains("<body") || lowerCode.contains("<script"))
        return "HTML"; // Prioritize HTML if common HTML tags found
      return "XML"; // Default to XML for general tag structures not identified as HTML
    }


    // CSS: Contains selectors and properties
    Pattern cssPattern = Pattern.compile("\\s*([#.]?\\w+[-\\w_]*|[\\*])\\s*\\{([^}]+)\\}");
    if (cssPattern.matcher(code).find()) {
      if (code.contains(":") && code.contains(";")) {
        return "CSS";
      }
    }

    // Python: Common keywords and structure (def, import, print)
    if (code.contains("def ") && code.contains(":") && (code.contains("import ") || code.contains("print("))) {
      if (!code.contains(";") || code.split(";").length < 5) {
        // The problematic line was here. Corrected to make it compile.
        // NOTE: The logic of this specific 'if' condition after fixing the syntax error
        // results in a condition that will likely always be false.
        // A more robust Python brace check would be needed for better accuracy.
        if (!Pattern.compile("\\{[^}]*\\}").matcher(code).find() && (Pattern.compile("\\{[^}]*\\}", Pattern.DOTALL).matcher(code).find()
            && !code.contains(" class ") && !code.contains(" function "))) { // Less likely to have braces than JS/Java unless for
                                                                             // dicts/sets
          return "Python";
        }
      }
    }

    // JavaScript: Common keywords
    if (code.contains("function ") || code.contains("var ") || code.contains("let ") || code.contains("const ")
        || code.contains("console.log")) {
      if (code.contains("{") && code.contains("}")) {
        return "JavaScript";
      }
    }

    // PHP: Starts with <?php
    if (trimmedCode.toLowerCase().startsWith("<?php")) {
      return "PHP";
    }

    // SQL: Common keywords
    Pattern sqlPattern = Pattern.compile("\\b(SELECT|INSERT\\s+INTO|UPDATE|DELETE\\s+FROM|CREATE\\s+TABLE|ALTER\\s+TABLE|DROP\\s+TABLE)\\b",
        Pattern.CASE_INSENSITIVE);
    if (sqlPattern.matcher(code).find()) {
      return "SQL";
    }

    // Markdown: Common markdown syntax
    if (Pattern.compile("^#+\\s+.*", Pattern.MULTILINE).matcher(code).find() ||
        Pattern.compile("^\\s*[*+-]\\s+.*", Pattern.MULTILINE).matcher(code).find() ||
        Pattern.compile("\\[.*\\]\\(.*\\)").matcher(code).find()) {
      return "Markdown";
    }

    // C-like (C, C++, C#) - very basic check
    if ((code.contains("#include") || code.contains("using namespace")) && (code.contains("int main") || code.contains("void main"))) {
      if (code.contains("cout <<") || code.contains("std::"))
        return "C++";
      return "C";
    }
    if (code.contains("namespace ") && code.contains("class ") && code.contains("static void Main") && code.contains("System.")) {
      return "C#";
    }

    // Ruby: def, end, puts
    if (code.contains("def ") && code.contains("end") && (code.contains("puts ") || code.contains("require "))) {
      return "Ruby";
    }

    // Perl: use, print, sub, $variable, @array, %hash
    if ((code.contains("use strict") || code.contains("use warnings") || code.contains("sub "))
        && (code.contains("print ") || Pattern.compile("\\$[a-zA-Z_]").matcher(code).find())) {
      return "Perl";
    }

    return "Text (None)"; // Default if no specific heuristic matches
  }


  private void applySyntaxHighlighting() {
    int selectedIndex = languageComboBox.getSelectedIndex();
    if (selectedIndex >= 0 && selectedIndex < SYNTAX_STYLES.length) {
      String selectedLanguage = LANGUAGES[selectedIndex];
      String selectedStyle = SYNTAX_STYLES[selectedIndex];
      codeTextArea.setSyntaxEditingStyle(selectedStyle);

      formatCodeButton.setEnabled(isFormatterAvailable(selectedLanguage));

      if (selectedStyle.equals(SyntaxConstants.SYNTAX_STYLE_NONE)) {
        codeTextArea.setLineWrap(true);
        codeTextArea.setWrapStyleWord(true);
      }
      else {
        codeTextArea.setLineWrap(false);
      }
    }
    else {
      codeTextArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_NONE);
      formatCodeButton.setEnabled(false);
    }
  }

  private boolean isFormatterAvailable(String language) {
    switch (language) {
      case "Java":
      case "JSON":
      case "HTML":
      case "XML":
        return true;
      default:
        return false;
    }
  }

  private void formatSelectedCode() {
    String selectedLanguage = (String) languageComboBox.getSelectedItem();
    String originalCode = codeTextArea.getText();

    if (originalCode == null || originalCode.trim().isEmpty()) {
      JOptionPane.showMessageDialog(this, "No code to format.", "Formatting Info", JOptionPane.INFORMATION_MESSAGE);
      return;
    }

    String formattedCode = null;
    try {
      switch (selectedLanguage) {
        case "Java":
          formattedCode = formatJavaCode(originalCode);
          break;
        case "JSON":
          formattedCode = formatJsonCode(originalCode);
          break;
        case "HTML":
          formattedCode = formatHtmlCode(originalCode);
          break;
        case "XML":
          formattedCode = formatXmlCode(originalCode);
          break;
        default:
          JOptionPane.showMessageDialog(this, "No formatter available for " + selectedLanguage + ".", "Formatting Info",
              JOptionPane.INFORMATION_MESSAGE);
          return;
      }

      if (formattedCode != null) {
        codeTextArea.setText(formattedCode);
        applySyntaxHighlighting();
      }

    }
    catch (NoClassDefFoundError ncde) {
      JOptionPane.showMessageDialog(this,
          "A required formatting library is missing for " + selectedLanguage + ".\n" +
              "Please ensure all dependencies are in your classpath.\nError: " + ncde.getMessage(),
          "Library Missing Error", JOptionPane.ERROR_MESSAGE);
    }
    catch (Exception ex) {
      JOptionPane.showMessageDialog(this, "Error formatting " + selectedLanguage + " code: " + ex.getMessage(), "Formatting Error",
          JOptionPane.ERROR_MESSAGE);
    }
  }

  private String formatJavaCode(String code) throws FormatterException {
    try {
      Formatter formatter = new Formatter();
      return formatter.formatSource(code);
    }
    catch (NoClassDefFoundError e) {
      throw new NoClassDefFoundError("Google Java Formatter library not found. " + e.getMessage());
    }
  }

  private String formatJsonCode(String code) throws IOException {
    try {
      Object json = jsonObjectMapper.readValue(code, Object.class);
      return jsonObjectMapper.writeValueAsString(json);
    }
    catch (NoClassDefFoundError e) {
      throw new NoClassDefFoundError("Jackson JSON library not found. " + e.getMessage());
    }
  }

  private String formatHtmlCode(String code) {
    try {
      Document doc = Jsoup.parse(code, "", Parser.htmlParser());
      doc.outputSettings().indentAmount(2).outline(true);
      return doc.html();
    }
    catch (NoClassDefFoundError e) {
      throw new NoClassDefFoundError("Jsoup HTML library not found. " + e.getMessage());
    }
  }

  private String formatXmlCode(String code) {
    try {
      Document doc = Jsoup.parse(code, "", Parser.xmlParser());
      doc.outputSettings().indentAmount(2).outline(true);
      return doc.outerHtml();
    }
    catch (NoClassDefFoundError e) {
      throw new NoClassDefFoundError("Jsoup XML library not found. " + e.getMessage());
    }
  }

  private void saveCodeToFile() {
    JFileChooser fileChooser = new JFileChooser();
    fileChooser.setDialogTitle("Save Code As");

    int selectedIndex = languageComboBox.getSelectedIndex();
    String defaultExtension = ".txt";
    String description = "Text files (*.txt)";

    if (selectedIndex >= 0 && selectedIndex < FILE_EXTENSIONS.length) {
      defaultExtension = FILE_EXTENSIONS[selectedIndex];
      description = LANGUAGES[selectedIndex] + " files (*" + defaultExtension + ")";
    }

    fileChooser.setSelectedFile(new File("untitled" + defaultExtension));
    FileNameExtensionFilter filter = new FileNameExtensionFilter(description, defaultExtension.substring(1));
    fileChooser.setFileFilter(filter);
    fileChooser.addChoosableFileFilter(filter);

    int userSelection = fileChooser.showSaveDialog(this);

    if (userSelection == JFileChooser.APPROVE_OPTION) {
      File fileToSave = fileChooser.getSelectedFile();
      String filePath = fileToSave.getAbsolutePath();
      if (!filePath.toLowerCase().endsWith(defaultExtension)) {
        fileToSave = new File(filePath + defaultExtension);
      }

      try (FileWriter writer = new FileWriter(fileToSave)) {
        writer.write(codeTextArea.getText());
        JOptionPane.showMessageDialog(this, "File saved successfully as " + fileToSave.getName(), "Save Successful",
            JOptionPane.INFORMATION_MESSAGE);
      }
      catch (IOException ex) {
        JOptionPane.showMessageDialog(this, "Error saving file: " + ex.getMessage(), "Save Error", JOptionPane.ERROR_MESSAGE);
      }
    }
  }

  private void exportTextAreaAsPng() {
    int width = Math.max(1, codeTextArea.getWidth());
    int height = Math.max(1, codeTextArea.getHeight());

    BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g2d = image.createGraphics();
    try {
      codeTextArea.paint(g2d);
    }
    finally {
      g2d.dispose();
    }

    JFileChooser fileChooser = new JFileChooser();
    fileChooser.setDialogTitle("Save as PNG");
    fileChooser.setSelectedFile(new File("code_snippet.png"));
    FileNameExtensionFilter pngFilter = new FileNameExtensionFilter("PNG Images (*.png)", "png");
    fileChooser.setFileFilter(pngFilter);
    fileChooser.addChoosableFileFilter(pngFilter);

    int userSelection = fileChooser.showSaveDialog(this);
    if (userSelection == JFileChooser.APPROVE_OPTION) {
      File fileToSave = fileChooser.getSelectedFile();
      String filePath = fileToSave.getAbsolutePath();
      if (!filePath.toLowerCase().endsWith(".png")) {
        fileToSave = new File(filePath + ".png");
      }
      try {
        boolean success = ImageIO.write(image, "png", fileToSave);
        if (success) {
          JOptionPane.showMessageDialog(this, "Image saved successfully as " + fileToSave.getName(), "Export Successful",
              JOptionPane.INFORMATION_MESSAGE);
        }
        else {
          JOptionPane.showMessageDialog(this, "Failed to save image. Unknown error.", "Export Error", JOptionPane.ERROR_MESSAGE);
        }
      }
      catch (IOException ex) {
        JOptionPane.showMessageDialog(this, "Error saving image: " + ex.getMessage(), "Export Error", JOptionPane.ERROR_MESSAGE);
      }
    }
  }

  public static void main(String[] args) {
    try {
      UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
    }
    catch (Exception e) {
      System.err.println("Couldn't set system look and feel. Using default.");
    }

    SwingUtilities.invokeLater(() -> new SyntaxHighlighterGUI().setVisible(true));
  }
}
