package xxx.com.json;

import com.formdev.flatlaf.FlatDarkLaf;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import org.apache.commons.lang3.StringUtils;

public class JsonFormatterGson extends JFrame {

  private static final long serialVersionUID = 1L;
  private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
  private static final Color SUCCESS_COLOR = new Color(0, 176, 0); // A pleasant green
  private static final Color ERROR_COLOR = new Color(220, 53, 69); // A clear red

  // --- UI Components ---
  private JTextArea inputArea;
  private JTextArea outputArea;
  private JButton formatButton;
  private JButton clearButton;
  private JLabel statusLabel;

  public static void main(String[] args) {
    // It's best practice to set the Look and Feel before creating any Swing components.
    FlatDarkLaf.setup();
    SwingUtilities.invokeLater(JsonFormatterGson::new);
  }

  public JsonFormatterGson() {
    super("JSON Formatter"); // Set title via super() constructor

    // --- Initialize and configure the UI ---
    initComponents();
    layoutComponents();
    registerListeners();

    // --- Final Frame Setup ---
    setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    setMinimumSize(new Dimension(600, 400));
    setSize(800, 600);
    setLocationRelativeTo(null); // Center the frame on screen
    setVisible(true);
  }

  /**
   * Initializes all UI components with their properties.
   */
  private void initComponents() {
    // Input Area
    inputArea = new JTextArea();
    inputArea.setLineWrap(true);
    inputArea.setWrapStyleWord(true);
    // Add a modern placeholder text hint
    inputArea.putClientProperty("JTextField.placeholderText", "Paste your JSON here...");

    // Output Area (read-only)
    outputArea = new JTextArea();
    outputArea.setEditable(false);
    outputArea.setLineWrap(true);
    outputArea.setWrapStyleWord(true);

    // Buttons with icons for a sharper look
    formatButton = new JButton("Format", UIManager.getIcon("Actions.execute"));
    clearButton = new JButton("Clear", UIManager.getIcon("Actions.cancel"));

    // Status Label for user feedback
    statusLabel = new JLabel("Ready");
    statusLabel.setBorder(BorderFactory.createEmptyBorder(2, 5, 2, 5)); // Add some padding
    statusLabel.setFont(statusLabel.getFont().deriveFont(Font.ITALIC));
  }

  /**
   * Lays out the initialized components in the frame.
   */
  private void layoutComponents() {
    // --- Input Panel ---
    JPanel inputPanel = new JPanel(new BorderLayout(0, 5)); // 5px vertical gap
    inputPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 5)); // Padding
    inputPanel.add(new JLabel("Input JSON:"), BorderLayout.NORTH);
    inputPanel.add(new JScrollPane(inputArea), BorderLayout.CENTER);

    // --- Output Panel ---
    JPanel outputPanel = new JPanel(new BorderLayout(0, 5));
    outputPanel.setBorder(BorderFactory.createEmptyBorder(10, 5, 10, 10)); // Padding
    outputPanel.add(new JLabel("Formatted JSON:"), BorderLayout.NORTH);
    outputPanel.add(new JScrollPane(outputArea), BorderLayout.CENTER);

    // --- Split Pane for resizable text areas ---
    JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, inputPanel, outputPanel);
    splitPane.setResizeWeight(0.5); // Distribute space evenly
    splitPane.setContinuousLayout(true);
    splitPane.setBorder(null); // The parent panel will have the border

    // --- Button Panel ---
    JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0)); // 10px horizontal gap
    buttonPanel.add(formatButton);
    buttonPanel.add(clearButton);

    // --- South Panel (Buttons + Status Bar) ---
    JPanel southPanel = new JPanel(new BorderLayout());
    southPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 5, 0));
    southPanel.add(buttonPanel, BorderLayout.CENTER);
    southPanel.add(statusLabel, BorderLayout.SOUTH);

    // --- Add main panels to the frame ---
    setLayout(new BorderLayout());
    add(splitPane, BorderLayout.CENTER);
    add(southPanel, BorderLayout.SOUTH);
  }

  /**
   * Registers all event listeners for interactive components.
   */
  private void registerListeners() {
    // Format Button Action
    formatButton.addActionListener(e -> formatJson());

    // Clear Button Action
    clearButton.addActionListener(e -> {
      inputArea.setText(StringUtils.EMPTY);
      outputArea.setText(StringUtils.EMPTY);
      updateStatus("Cleared.", UIManager.getColor("Label.foreground"));
    });
  }

  /**
   * Handles the JSON formatting logic.
   */
  private void formatJson() {
    String inputText = inputArea.getText();
    if (StringUtils.isBlank(inputText)) {
      updateStatus("Error: Input is empty.", ERROR_COLOR);
      outputArea.setText(StringUtils.EMPTY);
      return;
    }

    try {
      JsonElement jsonElement = JsonParser.parseString(inputText);
      String formattedJson = GSON.toJson(jsonElement);
      outputArea.setText(formattedJson);
      updateStatus("Formatting successful.", SUCCESS_COLOR);
    } catch (JsonSyntaxException ex) {
      outputArea.setText(StringUtils.EMPTY);
      // Provide a more user-friendly error message
      updateStatus("Error: " + ex.getMessage(), ERROR_COLOR);
    }
  }

  /**
   * Updates the status bar with a message and color.
   *
   * @param message The text to display.
   * @param color The color of the text.
   */
  private void updateStatus(String message, Color color) {
    statusLabel.setText(message);
    statusLabel.setForeground(color);
  }
}
