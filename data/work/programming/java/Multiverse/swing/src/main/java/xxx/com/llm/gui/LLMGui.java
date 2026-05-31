package xxx.com.llm.gui;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.formdev.flatlaf.FlatDarkLaf;
import de.kherud.llama.InferenceParameters;
import de.kherud.llama.LlamaModel;
import de.kherud.llama.LlamaOutput;
import de.kherud.llama.ModelParameters;
import de.kherud.llama.args.MiroStat;
import de.kherud.llama.args.NumaStrategy;
import org.apache.commons.io.FilenameUtils;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.ToolFactory;
import org.eclipse.jdt.core.formatter.CodeFormatter;
import org.eclipse.jface.text.Document;
import org.eclipse.text.edits.TextEdit;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rsyntaxtextarea.Theme;
import org.fife.ui.rtextarea.RTextScrollPane;
import org.htmlunit.util.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.parser.Parser;
import xxx.com.console.Spinner;
import xxx.com.image.converter.svg.SvgToGifUtil;

import javax.swing.*;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.text.BadLocationException;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.prefs.Preferences;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LLMGui extends JFrame {

  // --- Model Configuration and Constants ---
  private static String modelPath = "D:/LLMs/";
  private static final String MODEL_NAME = "llama-3-instruct-neurona-8b-v2-q4_k_m.gguf";
  private String currentModelFile;

  // --- Speaker Customization ---
  // Internal constant IDs
  public static final String HUMAN = "☺";
  public static final String COMPUTER = "Computer";
  private static final String NEWLINE = System.lineSeparator();

  // Display names, avatars, and paths that can be customized by the user
  private String humanName;
  private String computerName;
  private Icon humanAvatar;
  private Icon computerAvatar;
  private String humanAvatarPath;
  private String computerAvatarPath;

  // Dynamic prompts based on customized names
  private String humanPrompt;
  private String computerPrompt;
  private String systemPrompt;

  private LlamaModel model;
  private StringBuilder conversation;

  // --- GUI Components ---
  private JTable conversationTable;
  private DefaultTableModel tableModel;
  private final SpellCheckedTextField inputField;
  private final JButton sendButton;
  private final JProgressBar progressBar;
  private final JSplitPane splitPane;
  private int lastDividerLocation = 600;
  private boolean isSettingsPanelVisible = false;
  private MessageCellRenderer renderer;

  // --- Settings Panel Components ---
  private JSpinner gpuLayersSpinner;
  private JSpinner ctxSizeSpinner;
  private JSpinner batchSizeSpinner;
  private JSpinner threadsSpinner;
  private JSpinner threadsBatchSpinner;
  private JCheckBox flashAttnCheckbox;
  private JCheckBox disableLogCheckbox;
  private JComboBox<NumaStrategy> numaStrategyComboBox;
  private JCheckBox enableMlockCheckbox;
  private JSpinner temperatureSpinner;
  private JCheckBox penalizeNlCheckbox;
  private JComboBox<MiroStat> miroStatComboBox;
  private JSpinner repeatPenaltySpinner;
  private JTextField stopStringsField;
  private SpellCheckedTextArea initialPromptTextArea;
  private final JPanel settingsPanel;
  private final Preferences prefs;
  private final SpellChecker spellChecker;

  // Collapsible panel components
  private CollapsiblePanel modelParamsPanel;
  private CollapsiblePanel inferenceParamsPanel;
  private CollapsiblePanel initialPromptPanel;
  private String lastExpandedPanel = "Model Parameters"; // Default

  public LLMGui() {

    setTitle("LLM");

    Image image = Toolkit.getDefaultToolkit().getImage(getClass().getResource("/png/system4.png"));
    Image appIcon = image.getScaledInstance(25, 25, Image.SCALE_SMOOTH);

    setIconImage(appIcon);

    setSize(850, 600);
    setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
    setLocationRelativeTo(null);
    setLayout(new BorderLayout(5, 5));

    // Initialize preferences
    prefs = Preferences.userNodeForPackage(LLMGui.class);

    // Initialize spell checker
    spellChecker = new SpellChecker();

    // Initialize dynamic names and prompts with defaults before creating UI
    updateSpeakerData(HUMAN, "☺", null);
    updateSpeakerData(COMPUTER, "Computer", null);

    // --- Create Main UI components ---
    createConversationTable();
    // Add the new renderer for the speaker column (avatars and names)
    conversationTable.getColumnModel().getColumn(0).setCellRenderer(new SpeakerCellRenderer());
    JScrollPane scrollPane = new JScrollPane(conversationTable);

    settingsPanel = createSettingsPanel();
    splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, scrollPane, settingsPanel);
    splitPane.setResizeWeight(1.0);
    splitPane.setBorder(null);

    // Disable continuous layout to prevent flickering during resize
    splitPane.setContinuousLayout(false);

    JPanel bottomPanel = new JPanel(new BorderLayout());
    progressBar = new JProgressBar();
    progressBar.setIndeterminate(true);
    progressBar.setStringPainted(true);
    progressBar.setVisible(false);

    JPanel inputPanel = new JPanel(new BorderLayout(5, 0));
    inputField = new SpellCheckedTextField();
    inputField.setSpellChecker(spellChecker);
    inputField.setFont(new Font("SansSerif", Font.PLAIN, 14));

    JPanel buttonPanel = new JPanel(new GridLayout(1, 3, 5, 0));
    sendButton = new JButton("Send");
    JButton loadButton = new JButton("Load ⏏");
    loadButton.setToolTipText("Load New Model...");
    JButton settingsButton = new JButton("Settings ⚙");
    settingsButton.setToolTipText("Toggle Model Settings");

    buttonPanel.add(sendButton);
    buttonPanel.add(loadButton);
    buttonPanel.add(settingsButton);

    inputPanel.add(inputField, BorderLayout.CENTER);
    inputPanel.add(buttonPanel, BorderLayout.EAST);
    inputPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

    bottomPanel.add(progressBar, BorderLayout.NORTH);
    bottomPanel.add(inputPanel, BorderLayout.CENTER);

    add(splitPane, BorderLayout.CENTER);
    add(bottomPanel, BorderLayout.SOUTH);

    Action sendMessageAction =
        new AbstractAction() {
          public void actionPerformed(java.awt.event.ActionEvent e) {
            handleUserInput();
          }
        };
    sendButton.addActionListener(sendMessageAction);
    inputField.addActionListener(sendMessageAction);
    loadButton.addActionListener(e -> handleLoadModel());
    settingsButton.addActionListener(e -> toggleSettingsPanel());

    addWindowListener(
        new WindowAdapter() {
          public void windowClosing(WindowEvent e) {
            closeModel();
            System.exit(0);
          }
        });

    // Fixed component listener - only adjust divider when actually needed
    splitPane.addPropertyChangeListener(
        JSplitPane.DIVIDER_LOCATION_PROPERTY,
        evt -> {
          // Track the divider location when panel is visible
          if (isSettingsPanelVisible
              && splitPane.getDividerLocation()
                  < splitPane.getWidth() - splitPane.getDividerSize()) {
            lastDividerLocation = splitPane.getDividerLocation();
          }
        });

    loadSettings();
    String lastModel = prefs.get("model.last_loaded", modelPath + MODEL_NAME);
    loadNewModel(new File(lastModel));

    // Initial setup - hide settings panel
    SwingUtilities.invokeLater(
        () -> {
          splitPane.setDividerLocation(splitPane.getWidth() - splitPane.getDividerSize());
          settingsPanel.setMinimumSize(new Dimension(0, 0));
        });
  }

  private void createConversationTable() {
    tableModel =
        new DefaultTableModel(new Object[] {"Speaker", "Message"}, 0) {
          @Override
          public boolean isCellEditable(int row, int column) {
            // We can make it editable if we want to allow users to edit responses,
            // but for now, we'll keep it false. Formatting is handled by the renderer button.
            return false;
          }
        };
    conversationTable = new JTable(tableModel);

    renderer = new MessageCellRenderer();
    // The renderer for column 0 (Speaker) is set in the constructor
    conversationTable.getColumnModel().getColumn(1).setCellRenderer(renderer);

    TableColumn speakerColumn = conversationTable.getColumnModel().getColumn(0);
    speakerColumn.setMaxWidth(120);
    speakerColumn.setMinWidth(80);

    conversationTable.setTableHeader(null);
    conversationTable.setShowGrid(false);
    conversationTable.setIntercellSpacing(new Dimension(0, 0));

    conversationTable.addMouseListener(
        new MouseAdapter() {
          public void mousePressed(MouseEvent e) {
            if (e.isPopupTrigger()) showPopup(e);
          }

          public void mouseReleased(MouseEvent e) {
            if (e.isPopupTrigger()) showPopup(e);
          }

          private void showPopup(MouseEvent e) {
            int row = conversationTable.rowAtPoint(e.getPoint());
            if (row >= 0) {
              conversationTable.setRowSelectionInterval(row, row);
              // Pass the internal speaker ID to the context menu
              String speaker = (String) tableModel.getValueAt(row, 0);
              createContextMenu(speaker).show(e.getComponent(), e.getX(), e.getY());
            }
          }
        });
  }

  private JPopupMenu createContextMenu(String speaker) {
    JPopupMenu popup = new JPopupMenu();
    // Check against the internal constant ID
    boolean isHuman = HUMAN.equals(speaker);

    JMenuItem bgColorItem = new JMenuItem("Change Background Color...");
    bgColorItem.addActionListener(
        e -> {
          Color newColor =
              JColorChooser.showDialog(
                  this,
                  "Select Background Color",
                  isHuman ? renderer.getHumanBg() : renderer.getComputerBg());
          if (newColor != null) {
            renderer.setStyle(isHuman, newColor, null, null);
            conversationTable.repaint();
            saveSettings(); // Auto-save UI changes
          }
        });

    JMenuItem fgColorItem = new JMenuItem("Change Text Color...");
    fgColorItem.addActionListener(
        e -> {
          Color newColor =
              JColorChooser.showDialog(
                  this,
                  "Select Text Color",
                  isHuman ? renderer.getHumanFg() : renderer.getComputerFg());
          if (newColor != null) {
            renderer.setStyle(isHuman, null, newColor, null);
            conversationTable.repaint();
            saveSettings(); // Auto-save UI changes
          }
        });

    JMenuItem fontSizeItem = new JMenuItem("Change Font Size...");
    fontSizeItem.addActionListener(
        e -> {
          try {
            String sizeStr =
                JOptionPane.showInputDialog(
                    this, "Enter new font size:", renderer.getFontSize(isHuman));
            if (sizeStr != null) {
              int newSize = Integer.parseInt(sizeStr.trim());
              renderer.setStyle(isHuman, null, null, newSize);
              conversationTable.repaint();
              saveSettings(); // Auto-save UI changes
            }
          } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(
                this, "Invalid number.", "Error", JOptionPane.ERROR_MESSAGE);
          }
        });

    popup.add(bgColorItem);
    popup.add(fgColorItem);
    popup.add(fontSizeItem);
    popup.add(new JPopupMenu.Separator());

    // --- New "Edit Name" menu item ---
    JMenuItem editNameItem = new JMenuItem("Edit Name...");
    editNameItem.addActionListener(
        e -> {
          String currentName = isHuman ? humanName : computerName;
          String newName = JOptionPane.showInputDialog(this, "Enter new name:", currentName);
          if (newName != null && !newName.trim().isEmpty()) {
            if (isHuman) {
              updateSpeakerData(HUMAN, newName.trim(), humanAvatarPath);
            } else {
              updateSpeakerData(COMPUTER, newName.trim(), computerAvatarPath);
            }
            conversationTable.repaint(); // Renderer will pick up the change
            saveSettings();
          }
        });

    // --- New "Edit Avatar" menu item ---
    JMenuItem editAvatarItem = new JMenuItem("Edit Avatar...");
    editAvatarItem.addActionListener(
        e -> {
          JFileChooser fileChooser = new JFileChooser();
          fileChooser.setDialogTitle("Select Avatar Image");
          fileChooser.setFileFilter(
              new FileNameExtensionFilter("Images (png, gif, svg)", "png", "gif", "svg"));
          if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            if (isHuman) {
              updateSpeakerData(HUMAN, humanName, file.getAbsolutePath());
            } else {
              updateSpeakerData(COMPUTER, computerName, file.getAbsolutePath());
            }
            conversationTable.repaint();
            saveSettings();
          }
        });

    // --- New "Clear Avatar" menu item ---
    JMenuItem clearAvatarItem = new JMenuItem("Clear Avatar");
    clearAvatarItem.addActionListener(
        e -> {
          if (isHuman) {
            updateSpeakerData(HUMAN, humanName, null);
          } else {
            updateSpeakerData(COMPUTER, computerName, null);
          }
          conversationTable.repaint();
          saveSettings();
        });
    // Only enable if an avatar is currently set
    clearAvatarItem.setEnabled(isHuman ? humanAvatar != null : computerAvatar != null);

    popup.add(editNameItem);
    popup.add(editAvatarItem);
    popup.add(clearAvatarItem);

    return popup;
  }

  private JPanel createSettingsPanel() {
    JPanel mainPanel = new JPanel();
    mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
    mainPanel.setPreferredSize(new Dimension(250, 0)); // Set preferred width

    // Create collapsible panels
    modelParamsPanel = new CollapsiblePanel("Model Parameters", createModelParamsContent());
    inferenceParamsPanel =
        new CollapsiblePanel("Inference Parameters", createInferenceParamsContent());
    initialPromptPanel = new CollapsiblePanel("Initial Prompt", createInitialPromptContent());

    // Set up mutual exclusivity
    setupMutualExclusivity();

    // Add panels to main panel
    mainPanel.add(modelParamsPanel);
    mainPanel.add(Box.createRigidArea(new Dimension(0, 5)));
    mainPanel.add(inferenceParamsPanel);
    mainPanel.add(Box.createRigidArea(new Dimension(0, 5)));
    mainPanel.add(initialPromptPanel);
    mainPanel.add(Box.createVerticalGlue());

    // Create global Apply and Save buttons at the bottom
    JPanel globalButtonPanel = createGlobalButtonPanel();
    mainPanel.add(globalButtonPanel);

    return mainPanel;
  }

  private JPanel createModelParamsContent() {
    JPanel panel = new JPanel(new GridBagLayout());
    panel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
    addModelParamsComponents(panel);
    return panel;
  }

  private JPanel createInferenceParamsContent() {
    JPanel panel = new JPanel(new GridBagLayout());
    panel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
    addInferenceParamsComponents(panel);
    return panel;
  }

  private JPanel createInitialPromptContent() {
    JPanel panel = new JPanel(new BorderLayout(5, 5));
    panel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

    // Create text area for initial prompt
    initialPromptTextArea = new SpellCheckedTextArea(8, 20);
    initialPromptTextArea.setSpellChecker(spellChecker);
    initialPromptTextArea.setLineWrap(true);
    initialPromptTextArea.setWrapStyleWord(true);
    initialPromptTextArea.setFont(new Font("Monospaced", Font.PLAIN, 12));

    JScrollPane scrollPane = new JScrollPane(initialPromptTextArea);
    scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
    scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);

    // Create clear button
    JButton clearButton = new JButton("Clear");
    clearButton.addActionListener(
        e -> {
          initialPromptTextArea.setText("");
          saveSettings(); // Auto-save when cleared
        });

    JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
    buttonPanel.add(clearButton);

    panel.add(scrollPane, BorderLayout.CENTER);
    panel.add(buttonPanel, BorderLayout.SOUTH);

    return panel;
  }

  private JPanel createGlobalButtonPanel() {
    JPanel buttonPanel = new JPanel(new GridLayout(1, 2, 5, 0));
    buttonPanel.setBorder(BorderFactory.createEmptyBorder(10, 5, 5, 5));

    JButton applyButton = new JButton("Apply");
    applyButton.addActionListener(
        e -> {
          if (currentModelFile != null && !currentModelFile.isEmpty()) {
            loadNewModel(new File(currentModelFile));
          } else {
            JOptionPane.showMessageDialog(
                this, "No model loaded.", "Error", JOptionPane.ERROR_MESSAGE);
          }
        });

    JButton saveButton = new JButton("Save");
    saveButton.setToolTipText("Remember Settings");
    saveButton.addActionListener(
        e -> {
          saveSettings();
          JOptionPane.showMessageDialog(
              this, "Settings have been saved.", "Success", JOptionPane.INFORMATION_MESSAGE);
        });

    buttonPanel.add(applyButton);
    buttonPanel.add(saveButton);

    return buttonPanel;
  }

  private void setupMutualExclusivity() {
    CollapsiblePanel[] panels = {modelParamsPanel, inferenceParamsPanel, initialPromptPanel};

    for (CollapsiblePanel panel : panels) {
      panel.addToggleListener(
          expanded -> {
            if (expanded) {
              // Collapse all other panels
              for (CollapsiblePanel otherPanel : panels) {
                if (otherPanel != panel && otherPanel.isExpanded()) {
                  otherPanel.setExpanded(false);
                }
              }
              // Remember which panel was last expanded
              lastExpandedPanel = panel.getTitle();
              saveSettings();
            }
          });
    }
  }

  private void addModelParamsComponents(JPanel panel) {
    GridBagConstraints gbc = new GridBagConstraints();
    gbc.insets = new Insets(4, 4, 4, 4);
    gbc.fill = GridBagConstraints.HORIZONTAL;

    final int[] y = {0};

    BiConsumer<String, JComponent> addRow =
        (label, component) -> {
          gbc.gridy = y[0];
          gbc.gridx = 0;
          gbc.weightx = 0.4;
          gbc.anchor = GridBagConstraints.EAST;
          panel.add(new JLabel(label), gbc);
          gbc.gridx = 1;
          gbc.weightx = 0.6;
          gbc.anchor = GridBagConstraints.WEST;
          panel.add(component, gbc);
        };

    gpuLayersSpinner = new JSpinner(new SpinnerNumberModel(40, 0, 1000, 1));
    ctxSizeSpinner = new JSpinner(new SpinnerNumberModel(8192, 1, 65536, 1024));
    batchSizeSpinner = new JSpinner(new SpinnerNumberModel(2048, 1, 65536, 512));
    threadsSpinner = new JSpinner(new SpinnerNumberModel(16, 1, 128, 1));
    threadsBatchSpinner = new JSpinner(new SpinnerNumberModel(16, 1, 128, 1));
    flashAttnCheckbox = new JCheckBox("Enabled", true);
    disableLogCheckbox = new JCheckBox("Enabled", true);
    numaStrategyComboBox = new JComboBox<>(NumaStrategy.values());
    enableMlockCheckbox = new JCheckBox("Enabled", true);

    addRow.accept("GPU Layers:", gpuLayersSpinner);
    y[0]++;
    addRow.accept("Context Size:", ctxSizeSpinner);
    y[0]++;
    addRow.accept("Batch Size:", batchSizeSpinner);
    y[0]++;
    addRow.accept("Threads:", threadsSpinner);
    y[0]++;
    addRow.accept("Threads (Batch):", threadsBatchSpinner);
    y[0]++;
    addRow.accept("Flash Attention:", flashAttnCheckbox);
    y[0]++;
    addRow.accept("Disable Log:", disableLogCheckbox);
    y[0]++;
    addRow.accept("NUMA Strategy:", numaStrategyComboBox);
    y[0]++;
    addRow.accept("Enable Mlock:", enableMlockCheckbox);
  }

  private void addInferenceParamsComponents(JPanel panel) {
    GridBagConstraints gbc = new GridBagConstraints();
    gbc.insets = new Insets(4, 4, 4, 4);
    gbc.fill = GridBagConstraints.HORIZONTAL;

    final int[] y = {0};
    BiConsumer<String, JComponent> addRow =
        (label, component) -> {
          gbc.gridy = y[0];
          gbc.gridx = 0;
          gbc.weightx = 0.4;
          gbc.anchor = GridBagConstraints.EAST;
          panel.add(new JLabel(label), gbc);
          gbc.gridx = 1;
          gbc.weightx = 0.6;
          gbc.anchor = GridBagConstraints.WEST;
          panel.add(component, gbc);
        };

    temperatureSpinner = new JSpinner(new SpinnerNumberModel(0.7, 0.0, 2.0, 0.1));
    penalizeNlCheckbox = new JCheckBox("Enabled", true);
    miroStatComboBox = new JComboBox<>(MiroStat.values());
    repeatPenaltySpinner = new JSpinner(new SpinnerNumberModel(1.1, 0.0, 5.0, 0.1));
    stopStringsField = new JTextField(humanPrompt.trim(), 15);

    addRow.accept("Temperature:", temperatureSpinner);
    y[0]++;
    addRow.accept("Penalize NL:", penalizeNlCheckbox);
    y[0]++;
    addRow.accept("MiroStat:", miroStatComboBox);
    y[0]++;
    addRow.accept("Repeat Penalty:", repeatPenaltySpinner);
    y[0]++;
    addRow.accept("Stop Strings:", stopStringsField);
  }

  private void toggleSettingsPanel() {
    isSettingsPanelVisible = !isSettingsPanelVisible;

    if (isSettingsPanelVisible) {
      // Show settings panel
      settingsPanel.setMinimumSize(new Dimension(250, 0));
      settingsPanel.setPreferredSize(new Dimension(250, 0));
      splitPane.setDividerLocation(lastDividerLocation);
      splitPane.setDividerSize(5);
    } else {
      // Hide settings panel
      lastDividerLocation = splitPane.getDividerLocation();
      settingsPanel.setMinimumSize(new Dimension(0, 0));
      settingsPanel.setPreferredSize(new Dimension(0, 0));
      splitPane.setDividerLocation(splitPane.getWidth() - splitPane.getDividerSize());
      splitPane.setDividerSize(0);
    }

    // Force layout update
    splitPane.revalidate();
  }

  private void initializeModel(String modelFile) {
    this.currentModelFile = modelFile;
    prefs.put("model.last_loaded", modelFile);

    ModelParameters modelParams =
        new ModelParameters()
            .setModel(modelFile)
            .setGpuLayers((Integer) gpuLayersSpinner.getValue())
            .setCtxSize((Integer) ctxSizeSpinner.getValue())
            .setBatchSize((Integer) batchSizeSpinner.getValue())
            .setThreads((Integer) threadsSpinner.getValue())
            .setThreadsBatch((Integer) threadsBatchSpinner.getValue())
            .setNuma((NumaStrategy) Objects.requireNonNull(numaStrategyComboBox.getSelectedItem()));

    if (flashAttnCheckbox.isSelected()) modelParams.enableFlashAttn();
    if (disableLogCheckbox.isSelected()) modelParams.disableLog();
    if (enableMlockCheckbox.isSelected()) modelParams.enableMlock();

    this.model = new LlamaModel(modelParams);

    // This logic now ensures the UI always reflects the prompt used by the model.
    String customPrompt = initialPromptTextArea.getText().trim();

    if (customPrompt.isEmpty()) {
      // The prompt is empty. Notify the user, update the UI, and use the default prompt.
      final String message = "The initial prompt is empty. Using the default system prompt.";
      // This method can be called from a background thread, so UI updates must be on the EDT.
      SwingUtilities.invokeLater(
          () -> {
            JOptionPane.showMessageDialog(
                LLMGui.this, message, "Prompt Warning", JOptionPane.INFORMATION_MESSAGE);
            initialPromptTextArea.setText(systemPrompt);
          });
      // Use the system prompt for the conversation history.
      this.conversation = new StringBuilder(systemPrompt);
    } else {
      // Use the prompt from the UI, preserving any intentional whitespace.
      this.conversation = new StringBuilder(initialPromptTextArea.getText());
    }
  }

  private void closeModel() {
    if (model != null) {
      model.close();
    }
  }

  private void handleUserInput() {
    final String userInput = inputField.getText().trim();
    if (userInput.isEmpty()) {
      return;
    }
    appendToConversation(HUMAN, userInput);
    inputField.setText("");
    setInteractionEnabled(false, "Waiting for response...");

    appendToConversation(COMPUTER, "");
    final int responseRow = tableModel.getRowCount() - 1;

    SwingWorker<Void, String> worker =
        new SwingWorker<>() {
          @Override
          protected Void doInBackground() {
            if (model == null) {
              throw new IllegalStateException("Model is not initialized.");
            }
            conversation
                .append(NEWLINE)
                .append(humanPrompt)
                .append(userInput)
                .append(NEWLINE)
                .append(computerPrompt);

            InferenceParameters inferParams =
                new InferenceParameters(conversation.toString())
                    .setTemperature(((Double) temperatureSpinner.getValue()).floatValue())
                    .setPenalizeNl(penalizeNlCheckbox.isSelected())
                    .setMiroStat(
                        (MiroStat) Objects.requireNonNull(miroStatComboBox.getSelectedItem()))
                    .setRepeatPenalty(((Double) repeatPenaltySpinner.getValue()).floatValue());

            String stopString = stopStringsField.getText();
            // Failsafe: If the stop string field is empty, use the default human prompt.
            // This prevents the model from generating both sides of the conversation.
            if (stopString == null || stopString.trim().isEmpty()) {
              stopString = humanPrompt.trim();
              // Also, update the UI to show the user which stop string is being used.
              final String finalStopString = stopString;
              SwingUtilities.invokeLater(() -> stopStringsField.setText(finalStopString));
            }

            if (stopString != null && !stopString.trim().isEmpty()) {
              inferParams.setStopStrings(stopString.split(","));
            }

            for (LlamaOutput output : model.generate(inferParams)) {
              String token = output.toString();
              publish(token);
              conversation.append(token);
            }
            return null;
          }

          @Override
          protected void process(List<String> chunks) {
            String currentText = (String) tableModel.getValueAt(responseRow, 1);
            StringBuilder newText = new StringBuilder(currentText);
            for (String chunk : chunks) {
              newText.append(chunk);
            }
            tableModel.setValueAt(newText.toString(), responseRow, 1);
            scrollToBottom();
          }

          @Override
          protected void done() {
            try {
              get();
            } catch (Exception e) {
              String currentText = (String) tableModel.getValueAt(responseRow, 1);
              tableModel.setValueAt(
                  currentText + "\n\nError: Could not get response from model.", responseRow, 1);
            } finally {
              setInteractionEnabled(true, "");
              inputField.requestFocusInWindow();
              scrollToBottom();
            }
          }
        };
    worker.execute();
  }

  private void handleLoadModel() {
    JFileChooser fileChooser = new JFileChooser(modelPath);
    fileChooser.setFileFilter(new FileNameExtensionFilter("GGUF Model Files", "gguf"));
    if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
      File selectedFile = fileChooser.getSelectedFile();
      modelPath = selectedFile.getParent();
      loadNewModel(selectedFile);
    }
  }

  private void loadNewModel(File modelFile) {
    setInteractionEnabled(false, "Loading model: " + modelFile.getName());
    tableModel.setRowCount(0);

    new SwingWorker<Void, Void>() {
      @Override
      protected Void doInBackground() throws Exception {
        closeModel();
        initializeModel(modelFile.getAbsolutePath());
        return null;
      }

      @Override
      protected void done() {

        try {
          get();

          tableModel.setRowCount(0);

          conversation
              .append(humanPrompt)
              .append("Hello ")
              .append(computerName)
              .append(NEWLINE)
              .append(computerPrompt)
              .append("Hello. How may I help you today?");

          // Also add the greeting to the UI table for the user to see.
          appendToConversation(HUMAN, "Hello " + computerName);
          appendToConversation(COMPUTER, "Hello. How may I help you today?");

          // TODO Put this in the title bar
          Spinner spinner = new Spinner();
          spinner.startMarquee("Active LLM :", modelFile.getName(), 40, 100, true);

          setTitle("Active Model ( " + modelFile.getName() + " )");
        } catch (Exception e) {

          tableModel.setRowCount(0);
          appendToConversation(
              COMPUTER, "Error loading model: " + modelFile.getName() + "\n\n" + e.getMessage());
        } finally {
          setInteractionEnabled(true, "");
        }
      }
    }.execute();
  }

  private void saveSettings() {
    // Model Parameters
    prefs.putInt("model.gpuLayers", (Integer) gpuLayersSpinner.getValue());
    prefs.putInt("model.ctxSize", (Integer) ctxSizeSpinner.getValue());
    prefs.putInt("model.batchSize", (Integer) batchSizeSpinner.getValue());
    prefs.putInt("model.threads", (Integer) threadsSpinner.getValue());
    prefs.putInt("model.threadsBatch", (Integer) threadsBatchSpinner.getValue());
    prefs.putBoolean("model.flashAttn", flashAttnCheckbox.isSelected());
    prefs.putBoolean("model.disableLog", disableLogCheckbox.isSelected());
    prefs.put(
        "model.numaStrategy",
        ((NumaStrategy) Objects.requireNonNull(numaStrategyComboBox.getSelectedItem())).name());
    prefs.putBoolean("model.enableMlock", enableMlockCheckbox.isSelected());

    // Inference Parameters
    prefs.putDouble("inference.temperature", (Double) temperatureSpinner.getValue());
    prefs.putBoolean("inference.penalizeNl", penalizeNlCheckbox.isSelected());
    prefs.put(
        "inference.miroStat",
        ((MiroStat) Objects.requireNonNull(miroStatComboBox.getSelectedItem())).name());
    prefs.putDouble("inference.repeatPenalty", (Double) repeatPenaltySpinner.getValue());
    prefs.put("inference.stopStrings", stopStringsField.getText());

    // Initial Prompt
    prefs.put("ui.initialPrompt", initialPromptTextArea.getText());

    // UI Settings from Renderer
    prefs.putInt("ui.human.bg", renderer.getHumanBg().getRGB());
    prefs.putInt("ui.human.fg", renderer.getHumanFg().getRGB());
    prefs.putInt("ui.human.fontSize", renderer.getFontSize(true));
    prefs.putInt("ui.computer.bg", renderer.getComputerBg().getRGB());
    prefs.putInt("ui.computer.fg", renderer.getComputerFg().getRGB());
    prefs.putInt("ui.computer.fontSize", renderer.getFontSize(false));

    // New speaker customization settings
    prefs.put("ui.human.name", humanName);
    prefs.put("ui.computer.name", computerName);
    prefs.put("ui.human.avatar", humanAvatarPath != null ? humanAvatarPath : "");
    prefs.put("ui.computer.avatar", computerAvatarPath != null ? computerAvatarPath : "");

    // Save last expanded panel
    prefs.put("ui.lastExpandedPanel", lastExpandedPanel);
  }

  private void loadSettings() {
    // UI Speaker Settings (load first due to dependencies)
    String loadedHumanName = prefs.get("ui.human.name", HUMAN);
    String loadedHumanAvatar = prefs.get("ui.human.avatar", "");
    updateSpeakerData(HUMAN, loadedHumanName, loadedHumanAvatar);

    String loadedComputerName = prefs.get("ui.computer.name", COMPUTER);
    String loadedComputerAvatar = prefs.get("ui.computer.avatar", "");
    updateSpeakerData(COMPUTER, loadedComputerName, loadedComputerAvatar);

    // Model Parameters
    gpuLayersSpinner.setValue(prefs.getInt("model.gpuLayers", 40));
    ctxSizeSpinner.setValue(prefs.getInt("model.ctxSize", 8192));
    batchSizeSpinner.setValue(prefs.getInt("model.batchSize", 2048));
    threadsSpinner.setValue(prefs.getInt("model.threads", 16));
    threadsBatchSpinner.setValue(prefs.getInt("model.threadsBatch", 16));
    flashAttnCheckbox.setSelected(prefs.getBoolean("model.flashAttn", true));
    disableLogCheckbox.setSelected(prefs.getBoolean("model.disableLog", true));
    numaStrategyComboBox.setSelectedItem(
        NumaStrategy.valueOf(prefs.get("model.numaStrategy", NumaStrategy.DISTRIBUTE.name())));
    enableMlockCheckbox.setSelected(prefs.getBoolean("model.enableMlock", true));

    // Inference Parameters
    temperatureSpinner.setValue(prefs.getDouble("inference.temperature", 0.7));
    penalizeNlCheckbox.setSelected(prefs.getBoolean("inference.penalizeNl", true));
    miroStatComboBox.setSelectedItem(
        MiroStat.valueOf(prefs.get("inference.miroStat", MiroStat.V2.name())));
    repeatPenaltySpinner.setValue(prefs.getDouble("inference.repeatPenalty", 1.1));
    stopStringsField.setText(prefs.get("inference.stopStrings", humanPrompt.trim()));

    // Initial Prompt: Use the current systemPrompt as the default if no custom prompt is saved.
    initialPromptTextArea.setText(prefs.get("ui.initialPrompt", systemPrompt));

    // UI Style Settings for Renderer
    Color humanBg = new Color(prefs.getInt("ui.human.bg", new Color(0, 68, 128).getRGB()));
    Color humanFg = new Color(prefs.getInt("ui.human.fg", Color.WHITE.getRGB()));
    int humanFontSize = prefs.getInt("ui.human.fontSize", 14);
    renderer.setStyle(true, humanBg, humanFg, humanFontSize);

    Color computerBg = new Color(prefs.getInt("ui.computer.bg", new Color(60, 63, 65).getRGB()));
    Color computerFg = new Color(prefs.getInt("ui.computer.fg", Color.WHITE.getRGB()));
    int computerFontSize = prefs.getInt("ui.computer.fontSize", 14);
    renderer.setStyle(false, computerBg, computerFg, computerFontSize);

    // Load last expanded panel and set it
    SwingUtilities.invokeLater(
        () -> {
          // Collapse all panels first
          modelParamsPanel.setExpanded(false);
          inferenceParamsPanel.setExpanded(false);
          initialPromptPanel.setExpanded(false);

          // Expand the last opened panel
          switch (lastExpandedPanel) {
            case "Model Parameters":
              modelParamsPanel.setExpanded(true);
              break;
            case "Inference Parameters":
              inferenceParamsPanel.setExpanded(true);
              break;
            case "Initial Prompt":
              initialPromptPanel.setExpanded(true);
              break;
            default:
              modelParamsPanel.setExpanded(true); // Default fallback
              break;
          }
        });
  }

  private void appendToConversation(String speaker, String message) {
    // The first column always stores the internal constant ID
    tableModel.addRow(new Object[] {speaker, message});
    scrollToBottom();
  }

  private void scrollToBottom() {
    SwingUtilities.invokeLater(
        () -> {
          JScrollBar vertical =
              ((JScrollPane) conversationTable.getParent().getParent()).getVerticalScrollBar();
          vertical.setValue(vertical.getMaximum());
        });
  }

  private void setContainerEnabled(Container container, boolean enabled) {
    for (Component c : container.getComponents()) {
      c.setEnabled(enabled);
      if (c instanceof Container) {
        setContainerEnabled((Container) c, enabled);
      }
    }
  }

  private void setInteractionEnabled(boolean enabled, String progressBarText) {
    inputField.setEnabled(enabled);
    sendButton.setEnabled(enabled);
    setContainerEnabled(settingsPanel, enabled);

    progressBar.setVisible(!enabled);
    if (!enabled) {
      progressBar.setString(progressBarText);
    }
  }

  /** Updates the speaker's display data and refreshes the dynamic prompts. */
  private void updateSpeakerData(String speakerId, String name, String filePath) {
    Icon avatar = null;
    String avatarPath = null;

    // Check if the file path is provided and the file actually exists
    if (StringUtils.isNotBlank(filePath) && new File(filePath).exists()) {

      ImageIcon originalIcon = null;

      if (FilenameUtils.getExtension(filePath).equals("svg")) {
        originalIcon = SvgToGifUtil.convertSvgToGif(new File(filePath));
      } else {
        originalIcon = new ImageIcon(filePath);
      }

      // Explicitly set the table as the observer to receive animation updates.
      Objects.requireNonNull(originalIcon).setImageObserver(conversationTable);

      // Use the new ScalableIcon to handle resizing while preserving animation
      avatar = new ScalableIcon(originalIcon, 32, 32);
      avatarPath = filePath;
    }

    if (HUMAN.equals(speakerId)) {
      this.humanName = name;
      this.humanAvatar = avatar;
      this.humanAvatarPath = avatarPath;
    } else {
      this.computerName = name;
      this.computerAvatar = avatar;
      this.computerAvatarPath = avatarPath;
    }
    updatePrompts();
  }

  public static ImageIcon recolorIcon(ImageIcon icon, Color color) {

    BufferedImage bufferedImage =
        new BufferedImage(icon.getIconWidth(), icon.getIconHeight(), BufferedImage.TYPE_INT_ARGB);
    Graphics2D g2d = bufferedImage.createGraphics();

    g2d.drawImage(icon.getImage(), 0, 0, null);
    g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_IN, 1.0f));
    g2d.setColor(color);
    g2d.fillRect(0, 0, icon.getIconWidth(), icon.getIconHeight());
    g2d.dispose();

    return new ImageIcon(bufferedImage);
  }

  /** Regenerates the prompts based on the current speaker names. */
  private void updatePrompts() {
    humanPrompt = humanName + " : ";
    computerPrompt = computerName + " : ";
    systemPrompt =
        "You are "
            + computerName
            + ", an incredibly knowledgeable AI assistant. You are having a conversation with "
            + humanName
            + ". You are helpful, an expert in coding and writing, and you must always provide accurate, truthful information."
            + NEWLINE;
  }

  public static void main(String[] args) {
    try {
      UIManager.setLookAndFeel(new FlatDarkLaf());
    } catch (Exception ex) {
      System.err.println("Failed to initialize LaF");
    }
    SwingUtilities.invokeLater(() -> new LLMGui().setVisible(true));
  }

  /**
   * Custom Cell Renderer for the speaker column. Displays the speaker's avatar and current display
   * name.
   */
  class SpeakerCellRenderer extends DefaultTableCellRenderer {

    public SpeakerCellRenderer() {
      setOpaque(true);
      setBorder(BorderFactory.createEmptyBorder(2, 5, 2, 5));
      setHorizontalAlignment(CENTER);
      setVerticalAlignment(CENTER);
      setVerticalTextPosition(BOTTOM);
      setHorizontalTextPosition(CENTER);
    }

    @Override
    public Component getTableCellRendererComponent(
        JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
      // The `value` is the internal speaker ID (HUMAN or COMPUTER)
      String speakerId = (String) value;

      if (HUMAN.equals(speakerId)) {
        setText(humanName);
        setIcon(humanAvatar);
        setBackground(renderer.getHumanBg());
        setForeground(renderer.getHumanFg());
      } else {
        setText(computerName);
        setIcon(computerAvatar);
        setBackground(renderer.getComputerBg());
        setForeground(renderer.getComputerFg());
      }

      if (isSelected) {
        setBackground(table.getSelectionBackground());
        setForeground(table.getSelectionForeground());
      } else if (row % 2 != 0) {
        setBackground(getSlightlyDarker(getBackground()));
      }

      // Ensure row height is sufficient for the avatar
      int currentHeight = table.getRowHeight(row);
      int preferredHeight = getPreferredSize().height;
      if (currentHeight < preferredHeight) {
        table.setRowHeight(row, preferredHeight);
      }

      return this;
    }

    private Color getSlightlyDarker(Color c) {
      return new Color(
          Math.max((int) (c.getRed() * 0.95), 0),
          Math.max((int) (c.getGreen() * 0.95), 0),
          Math.max((int) (c.getBlue() * 0.95), 0));
    }
  }

  /**
   * An Icon implementation that scales a source ImageIcon. Crucially, it passes the ImageObserver
   * from the source icon to the drawImage call, which is necessary for animated GIFs to repaint
   * correctly.
   */
  record ScalableIcon(ImageIcon source, int width, int height) implements Icon {

    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {
      if (source != null) {
        // Pass the icon's own observer to ensure animation frames trigger repaints.
        // This is the key to making animated GIFs work in a renderer.
        g.drawImage(source.getImage(), x, y, width, height, source.getImageObserver());
      }
    }

    @Override
    public int getIconWidth() {
      return width;
    }

    @Override
    public int getIconHeight() {
      return height;
    }
  }

  /**
   * A collapsible panel that can expand and collapse its content. Supports mutual exclusivity
   * through toggle listeners.
   */
  static class CollapsiblePanel extends JPanel {
    private final String title;
    private final JPanel contentPanel;
    private final JButton toggleButton;
    private boolean expanded = false;
    private final java.util.List<java.util.function.Consumer<Boolean>> toggleListeners =
        new java.util.ArrayList<>();

    public CollapsiblePanel(String title, JPanel content) {
      this.title = title;
      this.contentPanel = content;

      setLayout(new BorderLayout());
      setBorder(BorderFactory.createEtchedBorder());

      // Create header with toggle button
      toggleButton = new JButton();
      updateToggleButton();
      toggleButton.setHorizontalAlignment(SwingConstants.LEFT);
      toggleButton.setFocusPainted(false);
      toggleButton.setBorderPainted(false);
      toggleButton.setContentAreaFilled(false);
      toggleButton.addActionListener(e -> toggle());

      // Add components
      add(toggleButton, BorderLayout.NORTH);

      // Initially hide content
      contentPanel.setVisible(false);
      add(contentPanel, BorderLayout.CENTER);
    }

    private void updateToggleButton() {
      String arrow = expanded ? "v " : "> ";
      toggleButton.setText(arrow + title);
    }

    public void toggle() {
      setExpanded(!expanded);
    }

    public void setExpanded(boolean expanded) {
      if (this.expanded != expanded) {
        this.expanded = expanded;
        contentPanel.setVisible(expanded);
        updateToggleButton();

        // Notify listeners
        for (java.util.function.Consumer<Boolean> listener : toggleListeners) {
          listener.accept(expanded);
        }

        // Revalidate parent to update layout
        Container parent = getParent();
        if (parent != null) {
          parent.revalidate();
          parent.repaint();
        }
      }
    }

    public boolean isExpanded() {
      return expanded;
    }

    public String getTitle() {
      return title;
    }

    public void addToggleListener(java.util.function.Consumer<Boolean> listener) {
      toggleListeners.add(listener);
    }
  }
}

// Custom Cell Renderer for the conversation table
class MessageCellRenderer extends JPanel implements TableCellRenderer {
  private Color humanBg = new Color(0, 68, 128);
  private Color humanFg = Color.WHITE;
  private Color computerBg = new Color(60, 63, 65);
  private Color computerFg = Color.WHITE;
  private Font humanFont = new Font("Monospaced", Font.PLAIN, 14);
  private Font computerFont = new Font("Monospaced", Font.PLAIN, 14);

  private final SpellCheckedTextArea.CodeFormatterUtil codeFormatterUtil =
      new SpellCheckedTextArea.CodeFormatterUtil();
  private final Pattern codeBlockPattern =
      Pattern.compile("```(\\w*)\\n?(.*?)\\n?```", Pattern.DOTALL);

  public MessageCellRenderer() {
    setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
  }

  @Override
  public Component getTableCellRendererComponent(
      JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {

    // --- 1. Clear and Style the Main Panel ---
    removeAll(); // Clear components from previous render
    String speaker = (String) table.getValueAt(row, 0);
    String message = (String) value;
    boolean isHuman = LLMGui.HUMAN.equals(speaker);

    Color bgColor = isHuman ? humanBg : computerBg;
    if (isSelected) {
      bgColor = table.getSelectionBackground();
    } else if (row % 2 != 0) {
      bgColor = getSlightlyDarker(bgColor);
    }
    setBackground(bgColor);

    // --- 2. Parse Message and Add Segments ---
    if (message == null || message.isEmpty()) {
      // Handle empty messages to avoid parser errors
      addTextSegment("...", isHuman, isSelected, bgColor);
    } else {
      Matcher matcher = codeBlockPattern.matcher(message);
      int lastEnd = 0;

      while (matcher.find()) {
        // Part A: Add any plain text found before the code block
        if (matcher.start() > lastEnd) {
          String plainText = message.substring(lastEnd, matcher.start());
          addTextSegment(plainText, isHuman, isSelected, bgColor);
        }

        // Part B: Add the code block itself
        String languageHint = matcher.group(1);
        String code = matcher.group(2);
        addCodeSegment(code, languageHint, table, row, isHuman, isSelected, bgColor);

        // Update the position of the last match
        lastEnd = matcher.end();
      }

      // Part C: Add any remaining text after the last code block.
      // If no code blocks were found, this will render the entire message as plain text.
      if (lastEnd < message.length()) {
        String remainingText = message.substring(lastEnd);
        addTextSegment(remainingText, isHuman, isSelected, bgColor);
      }
    }

    // --- 3. Adjust Row Height ---
    // Let the table layout manager determine the size, then set the row height.
    int preferredHeight = getPreferredSize().height;
    if (table.getRowHeight(row) != preferredHeight) {
      table.setRowHeight(row, preferredHeight);
    }

    return this;
  }

  private void addTextSegment(
      String text, boolean isHuman, boolean isSelected, Color panelBgColor) {
    JTextArea textArea = new JTextArea(text);
    textArea.setLineWrap(true);
    textArea.setWrapStyleWord(true);
    textArea.setEditable(false);
    textArea.setOpaque(true);
    textArea.setFont(isHuman ? humanFont : computerFont);
    textArea.setMargin(new Insets(5, 5, 5, 5));
    textArea.setComponentOrientation(getComponentOrientation());

    if (isSelected) {
      textArea.setBackground(panelBgColor);
      textArea.setForeground(Color.WHITE);
    } else {
      textArea.setBackground(panelBgColor);
      textArea.setForeground(isHuman ? humanFg : computerFg);
    }

    // To make the text area width match the panel width
    textArea.setAlignmentX(Component.LEFT_ALIGNMENT);
    add(textArea);
  }

  private void addCodeSegment(
      String code,
      String languageHint,
      JTable table,
      int row,
      boolean isHuman,
      boolean isSelected,
      Color panelBgColor) {
    // --- Detect Language ---
    String detectedLanguage = codeFormatterUtil.detectLanguage(languageHint, code);

    // --- Main Panel for Code Block ---
    JPanel codePanel = new JPanel(new BorderLayout());
    codePanel.setBorder(BorderFactory.createEtchedBorder());
    codePanel.setAlignmentX(Component.LEFT_ALIGNMENT);
    codePanel.setBackground(new Color(45, 45, 45));

    // --- Header Panel ---
    JPanel headerPanel = new JPanel(new BorderLayout());
    headerPanel.setBackground(new Color(60, 63, 65));
    headerPanel.setBorder(BorderFactory.createEmptyBorder(2, 5, 2, 5));

    JLabel langLabel = new JLabel(detectedLanguage);
    langLabel.setForeground(Color.LIGHT_GRAY);
    langLabel.setFont(new Font("SansSerif", Font.BOLD, 12));
    headerPanel.add(langLabel, BorderLayout.WEST);

    // --- Format Button ---
    JButton formatButton = new JButton("Format Code");
    formatButton.setMargin(new Insets(1, 4, 1, 4));
    formatButton.setFocusPainted(false);
    formatButton.setEnabled(codeFormatterUtil.isFormatterAvailable(detectedLanguage));
    formatButton.addActionListener(
        e -> {
          String formattedCode = codeFormatterUtil.format(code, detectedLanguage);
          if (!formattedCode.equals(code)) {
            // To update the model, we must replace the original code segment in the full message
            // string
            String originalMessage = (String) table.getModel().getValueAt(row, 1);
            Matcher matcher = codeBlockPattern.matcher(originalMessage);
            if (matcher.find()) { // Find the first code block to replace (simple approach)
              StringBuilder newMessage = new StringBuilder(originalMessage);
              // Replace only the code part (group 2)
              newMessage.replace(matcher.start(2), matcher.end(2), formattedCode);
              table.getModel().setValueAt(newMessage.toString(), row, 1);
            }
          }
        });
    headerPanel.add(formatButton, BorderLayout.EAST);

    // --- Code Area ---
    RSyntaxTextArea codeArea = new RSyntaxTextArea(code);
    codeArea.setSyntaxEditingStyle(codeFormatterUtil.getSyntaxStyle(detectedLanguage));
    codeArea.setEditable(false);
    codeArea.setAntiAliasingEnabled(true);
    codeArea.setCodeFoldingEnabled(true);
    codeArea.setFont(isHuman ? humanFont : computerFont);
    codeArea.setMargin(new Insets(5, 5, 5, 5));

    // Apply a dark theme to the code area
    try {
      Theme theme =
          Theme.load(
              getClass().getResourceAsStream("/org/fife/ui/rsyntaxtextarea/themes/dark.xml"));
      theme.apply(codeArea);
    } catch (IOException ioe) {
      // Fallback to default dark colors if theme fails
      codeArea.setBackground(new Color(43, 43, 43));
      codeArea.setForeground(Color.WHITE);
      codeArea.setCurrentLineHighlightColor(new Color(50, 50, 50));
      codeArea.setCaretColor(Color.WHITE);
    }

    RTextScrollPane scrollPane = new RTextScrollPane(codeArea);
    scrollPane.setLineNumbersEnabled(true);
    scrollPane.setFoldIndicatorEnabled(true);
    scrollPane.setBorder(null);

    // --- Assemble Panel ---
    codePanel.add(headerPanel, BorderLayout.NORTH);
    codePanel.add(scrollPane, BorderLayout.CENTER);
    add(codePanel);
  }

  private Color getSlightlyDarker(Color c) {
    return new Color(
        Math.max((int) (c.getRed() * 0.95), 0),
        Math.max((int) (c.getGreen() * 0.95), 0),
        Math.max((int) (c.getBlue() * 0.95), 0));
  }

  public void setStyle(boolean isHuman, Color bg, Color fg, Integer fontSize) {
    if (isHuman) {
      if (bg != null) humanBg = bg;
      if (fg != null) humanFg = fg;
      if (fontSize != null) humanFont = humanFont.deriveFont((float) fontSize);
    } else {
      if (bg != null) computerBg = bg;
      if (fg != null) computerFg = fg;
      if (fontSize != null) computerFont = computerFont.deriveFont((float) fontSize);
    }
  }

  public Color getHumanBg() {
    return humanBg;
  }

  public Color getHumanFg() {
    return humanFg;
  }

  public Color getComputerBg() {
    return computerBg;
  }

  public Color getComputerFg() {
    return computerFg;
  }

  public int getFontSize(boolean isHuman) {
    return isHuman ? humanFont.getSize() : computerFont.getSize();
  }
}

/** Simple spell checker that loads a dictionary and provides spell checking functionality */
class SpellChecker {

  private Set<String> dictionary;
  private Map<String, List<String>> suggestionCache;

  public SpellChecker() {
    dictionary = new HashSet<>();
    suggestionCache = new HashMap<>();
    loadDictionary();
  }

  private void loadDictionary() {
    try {
      // Try to load from resources first
      try (BufferedReader reader =
          new BufferedReader(
              new InputStreamReader(
                  Objects.requireNonNull(getClass().getResourceAsStream("/data/wordlist.txt"))))) {
        String line;
        while ((line = reader.readLine()) != null) {
          String word = line.trim().toLowerCase();
          if (!word.isEmpty()) {
            dictionary.add(word);
          }
        }
        System.out.println("Loaded " + dictionary.size() + " words from wordlist.txt");
      }
    } catch (IOException | NullPointerException e) {
      System.err.println("Could not load wordlist.txt from resources: " + e.getMessage());
    }
  }

  public boolean isCorrect(String word) {
    if (word == null || word.trim().isEmpty()) return true;
    String cleanWord = word.toLowerCase().replaceAll("[^a-zA-Z]", "");
    return cleanWord.isEmpty() || dictionary.contains(cleanWord);
  }

  public List<String> getSuggestions(String word) {
    if (word == null || word.trim().isEmpty()) return new ArrayList<>();

    String cleanWord = word.toLowerCase().replaceAll("[^a-zA-Z]", "");
    if (cleanWord.isEmpty()) return new ArrayList<>();

    if (suggestionCache.containsKey(cleanWord)) {
      return suggestionCache.get(cleanWord);
    }

    List<String> suggestions = new ArrayList<>();

    // Simple suggestion algorithm - find words with edit distance of 1 or 2
    for (String dictWord : dictionary) {
      int distance = editDistance(cleanWord, dictWord);
      if (distance <= 2) {
        suggestions.add(dictWord);
      }
      // Limit search for performance with large dictionaries
      if (suggestions.size() > 20) break;
    }

    // Sort by edit distance and word frequency (shorter words first)
    suggestions.sort(
        (a, b) -> {
          int distA = editDistance(cleanWord, a);
          int distB = editDistance(cleanWord, b);
          if (distA != distB) return Integer.compare(distA, distB);
          return Integer.compare(a.length(), b.length());
        });

    // Limit to top 5 suggestions
    if (suggestions.size() > 5) {
      suggestions = suggestions.subList(0, 5);
    }

    suggestionCache.put(cleanWord, suggestions);
    return suggestions;
  }

  private int editDistance(String s1, String s2) {
    // Optimize for performance - if length difference is too large, return max
    if (Math.abs(s1.length() - s2.length()) > 3) {
      return Integer.MAX_VALUE;
    }

    int[][] dp = new int[s1.length() + 1][s2.length() + 1];

    for (int i = 0; i <= s1.length(); i++) {
      dp[i][0] = i;
    }
    for (int j = 0; j <= s2.length(); j++) {
      dp[0][j] = j;
    }

    for (int i = 1; i <= s1.length(); i++) {
      for (int j = 1; j <= s2.length(); j++) {
        if (s1.charAt(i - 1) == s2.charAt(j - 1)) {
          dp[i][j] = dp[i - 1][j - 1];
        } else {
          dp[i][j] = 1 + Math.min(dp[i - 1][j], Math.min(dp[i][j - 1], dp[i - 1][j - 1]));
        }
      }
    }

    return dp[s1.length()][s2.length()];
  }
}

/** Custom text field with spell checking functionality */
class SpellCheckedTextField extends JTextField {
  private SpellChecker spellChecker;
  private JPopupMenu suggestionPopup;
  private Timer suggestionTimer;
  private Timer hoverTimer;
  private String currentHoveredWord;
  private int currentWordStart;
  private int currentWordEnd;

  public SpellCheckedTextField() {
    super();
    initSpellChecking();
  }

  public SpellCheckedTextField(int columns) {
    super(columns);
    initSpellChecking();
  }

  private void initSpellChecking() {
    // Add document listener to check spelling as user types
    getDocument()
        .addDocumentListener(
            new DocumentListener() {
              @Override
              public void insertUpdate(DocumentEvent e) {
                scheduleSpellCheck();
              }

              @Override
              public void removeUpdate(DocumentEvent e) {
                scheduleSpellCheck();
              }

              @Override
              public void changedUpdate(DocumentEvent e) {
                scheduleSpellCheck();
              }
            });

    // Add key listener for showing suggestions
    addKeyListener(
        new KeyAdapter() {
          @Override
          public void keyPressed(KeyEvent e) {
            if (e.getKeyCode() == KeyEvent.VK_SPACE || e.getKeyCode() == KeyEvent.VK_ENTER) {
              hideSuggestions();
            }
          }
        });

    // Timer to delay spell checking
    suggestionTimer = new Timer(500, e -> checkCurrentWord());
    suggestionTimer.setRepeats(false);

    // Hover timer
    hoverTimer =
        new Timer(
            300,
            e -> {
              if (currentHoveredWord != null) {
                showHoverSuggestions(currentHoveredWord, currentWordStart, currentWordEnd);
              }
            });
    hoverTimer.setRepeats(false);

    // Mouse motion for hover
    addMouseMotionListener(
        new MouseMotionAdapter() {
          @Override
          public void mouseMoved(MouseEvent e) {
            handleMouseMoved(e);
          }
        });

    // Mouse exit
    addMouseListener(
        new MouseAdapter() {
          @Override
          public void mouseExited(MouseEvent e) {
            hideSuggestions();
            hoverTimer.stop();
            currentHoveredWord = null;
          }
        });
  }

  private void handleMouseMoved(MouseEvent e) {
    int pos;
    try {
      pos = viewToModel(e.getPoint());
    } catch (Exception ex) {
      pos = -1;
    }
    String text = getText();

    if (pos < 0 || text.isEmpty()) {
      hideSuggestions();
      hoverTimer.stop();
      currentHoveredWord = null;
      return;
    }

    int start = findWordStart(text, pos);
    int end = findWordEnd(text, pos);

    if (start >= end || start >= text.length() || !Character.isLetter(text.charAt(start))) {
      hideSuggestions();
      hoverTimer.stop();
      currentHoveredWord = null;
      return;
    }

    String word = text.substring(start, end);
    if (spellChecker.isCorrect(word)) {
      hideSuggestions();
      hoverTimer.stop();
      currentHoveredWord = null;
      return;
    }

    // Over misspelled word
    if (!word.equals(currentHoveredWord)) {
      hideSuggestions();
      hoverTimer.stop();
      currentHoveredWord = word;
      currentWordStart = start;
      currentWordEnd = end;
      hoverTimer.restart();
    }
  }

  private void scheduleSpellCheck() {
    suggestionTimer.restart();
  }

  private void checkCurrentWord() {
    if (spellChecker == null) return;

    String text = getText();
    int caretPos = getCaretPosition();

    if (text.isEmpty() || caretPos == 0) return;

    // Find the current word
    int wordStart = findWordStart(text, caretPos);
    int wordEnd = findWordEnd(text, caretPos);

    if (wordStart < wordEnd) {
      String currentWord = text.substring(wordStart, wordEnd);
      if (!spellChecker.isCorrect(currentWord)) {
        showSuggestions(currentWord, wordStart, wordEnd);
      } else {
        hideSuggestions();
      }
    }
  }

  private void showSuggestions(String word, int wordStart, int wordEnd) {
    List<String> suggestions = spellChecker.getSuggestions(word);

    if (suggestions.isEmpty()) return;

    hideSuggestions();

    suggestionPopup = new JPopupMenu();

    JPanel strip = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 5));
    strip.setBackground(Color.WHITE);

    for (String suggestion : suggestions) {
      JButton btn = new JButton(suggestion);
      btn.setMargin(new Insets(2, 4, 2, 4));
      btn.addActionListener(
          ae -> {
            replaceMisspelledWord(suggestion, wordStart, wordEnd);
            hideSuggestions();
          });
      strip.add(btn);
    }

    suggestionPopup.add(strip);

    try {
      Rectangle caretRect = modelToView(getCaretPosition());
      suggestionPopup.show(this, caretRect.x, caretRect.y + caretRect.height + 2);
    } catch (BadLocationException ex) {
      // Handle silently
    }
  }

  private void showHoverSuggestions(String word, int wordStart, int wordEnd) {
    List<String> suggestions = spellChecker.getSuggestions(word);

    if (suggestions.isEmpty()) return;

    hideSuggestions();

    suggestionPopup = new JPopupMenu();

    JPanel strip = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 5));
    strip.setBackground(Color.WHITE);

    for (String suggestion : suggestions) {
      JButton btn = new JButton(suggestion);
      btn.setMargin(new Insets(2, 4, 2, 4));
      btn.addActionListener(
          ae -> {
            replaceMisspelledWord(suggestion, wordStart, wordEnd);
            hideSuggestions();
          });
      strip.add(btn);
    }

    suggestionPopup.add(strip);

    try {
      Rectangle rect = modelToView(wordStart);
      suggestionPopup.show(this, rect.x, rect.y + rect.height + 2);
    } catch (BadLocationException ex) {
      // Handle silently
    }
  }

  private void hideSuggestions() {
    if (suggestionPopup != null) {
      suggestionPopup.setVisible(false);
      suggestionPopup = null;
    }
  }

  private void replaceMisspelledWord(String replacement, int start, int end) {
    try {
      getDocument().remove(start, end - start);
      getDocument().insertString(start, replacement, null);
      hideSuggestions();
    } catch (BadLocationException ex) {
      // Handle exception silently
    }
  }

  private int findWordStart(String text, int pos) {
    int start = pos - 1;
    while (start >= 0 && Character.isLetter(text.charAt(start))) {
      start--;
    }
    return start + 1;
  }

  private int findWordEnd(String text, int pos) {
    int end = pos;
    while (end < text.length() && Character.isLetter(text.charAt(end))) {
      end++;
    }
    return end;
  }

  public void setSpellChecker(SpellChecker spellChecker) {
    this.spellChecker = spellChecker;
  }

  @Override
  protected void paintComponent(Graphics g) {
    super.paintComponent(g);

    if (spellChecker != null) {
      paintSpellCheckUnderlines(g);
    }
  }

  private void paintSpellCheckUnderlines(Graphics g) {
    String text = getText();
    if (text.isEmpty()) return;

    Graphics2D g2 = (Graphics2D) g.create();
    g2.setColor(Color.RED);
    g2.setStroke(
        new BasicStroke(
            1.0f,
            BasicStroke.CAP_BUTT,
            BasicStroke.JOIN_MITER,
            10.0f,
            new float[] {2.0f, 2.0f},
            0.0f));

    FontMetrics fm = g2.getFontMetrics();
    int y = getHeight() - getInsets().bottom - 2;

    int wordStart = 0;
    for (int i = 0; i <= text.length(); i++) {
      if (i == text.length() || !Character.isLetter(text.charAt(i))) {
        if (wordStart < i) {
          String word = text.substring(wordStart, i);
          if (!spellChecker.isCorrect(word)) {
            int x1 = getInsets().left + fm.stringWidth(text.substring(0, wordStart));
            int x2 = getInsets().left + fm.stringWidth(text.substring(0, i));
            g2.drawLine(x1, y, x2, y);
          }
        }
        wordStart = i + 1;
      }
    }

    g2.dispose();
  }
}

/** Custom text area with spell checking functionality */
class SpellCheckedTextArea extends JTextArea {
  private SpellChecker spellChecker;
  private JPopupMenu suggestionPopup;
  private Timer suggestionTimer;
  private Timer hoverTimer;
  private String currentHoveredWord;
  private int currentWordStart;
  private int currentWordEnd;

  public SpellCheckedTextArea() {
    super();
    initSpellChecking();
  }

  public SpellCheckedTextArea(int rows, int columns) {
    super(rows, columns);
    initSpellChecking();
  }

  private void initSpellChecking() {
    // Add document listener to check spelling as user types
    getDocument()
        .addDocumentListener(
            new DocumentListener() {
              @Override
              public void insertUpdate(DocumentEvent e) {
                scheduleSpellCheck();
              }

              @Override
              public void removeUpdate(DocumentEvent e) {
                scheduleSpellCheck();
              }

              @Override
              public void changedUpdate(DocumentEvent e) {
                scheduleSpellCheck();
              }
            });

    // Add mouse listener for right-click suggestions
    addMouseListener(
        new MouseAdapter() {
          @Override
          public void mousePressed(MouseEvent e) {
            if (e.isPopupTrigger()) {
              showContextMenu(e);
            }
          }

          @Override
          public void mouseReleased(MouseEvent e) {
            if (e.isPopupTrigger()) {
              showContextMenu(e);
            }
          }
        });

    // Timer to delay spell checking
    suggestionTimer = new Timer(1000, e -> repaint());
    suggestionTimer.setRepeats(false);

    // Hover timer
    hoverTimer =
        new Timer(
            300,
            e -> {
              if (currentHoveredWord != null) {
                showHoverSuggestions(currentHoveredWord, currentWordStart, currentWordEnd);
              }
            });
    hoverTimer.setRepeats(false);

    // Mouse motion for hover
    addMouseMotionListener(
        new MouseMotionAdapter() {
          @Override
          public void mouseMoved(MouseEvent e) {
            handleMouseMoved(e);
          }
        });

    // Mouse exit
    addMouseListener(
        new MouseAdapter() {
          @Override
          public void mouseExited(MouseEvent e) {
            hideSuggestions();
            hoverTimer.stop();
            currentHoveredWord = null;
          }
        });
  }

  private void handleMouseMoved(MouseEvent e) {
    int pos = viewToModel(e.getPoint());
    String text = getText();

    if (pos < 0 || text.isEmpty()) {
      hideSuggestions();
      hoverTimer.stop();
      currentHoveredWord = null;
      return;
    }

    int start = findWordStart(text, pos);
    int end = findWordEnd(text, pos);

    if (start >= end || start >= text.length() || !Character.isLetter(text.charAt(start))) {
      hideSuggestions();
      hoverTimer.stop();
      currentHoveredWord = null;
      return;
    }

    String word = text.substring(start, end);
    if (spellChecker.isCorrect(word)) {
      hideSuggestions();
      hoverTimer.stop();
      currentHoveredWord = null;
      return;
    }

    // Over misspelled word
    if (!word.equals(currentHoveredWord)) {
      hideSuggestions();
      hoverTimer.stop();
      currentHoveredWord = word;
      currentWordStart = start;
      currentWordEnd = end;
      hoverTimer.restart();
    }
  }

  private void scheduleSpellCheck() {
    suggestionTimer.restart();
  }

  private void showContextMenu(MouseEvent e) {
    if (spellChecker == null) return;

    int pos = viewToModel(e.getPoint());
    String text = getText();

    int wordStart = findWordStart(text, pos);
    int wordEnd = findWordEnd(text, pos);

    if (wordStart < wordEnd) {
      String word = text.substring(wordStart, wordEnd);
      if (!spellChecker.isCorrect(word)) {
        List<String> suggestions = spellChecker.getSuggestions(word);
        if (!suggestions.isEmpty()) {
          showSuggestionMenu(suggestions, wordStart, wordEnd, e.getX(), e.getY());
          return;
        }
      }
    }

    // Show default context menu if no suggestions
    JPopupMenu defaultMenu = new JPopupMenu();
    defaultMenu.add(new JMenuItem("Cut")).addActionListener(ev -> cut());
    defaultMenu.add(new JMenuItem("Copy")).addActionListener(ev -> copy());
    defaultMenu.add(new JMenuItem("Paste")).addActionListener(ev -> paste());
    defaultMenu.show(this, e.getX(), e.getY());
  }

  private void showSuggestionMenu(
      List<String> suggestions, int wordStart, int wordEnd, int x, int y) {
    JPopupMenu menu = new JPopupMenu();

    for (String suggestion : suggestions) {
      JMenuItem item = new JMenuItem(suggestion);
      item.addActionListener(e -> replaceMisspelledWord(suggestion, wordStart, wordEnd));
      menu.add(item);
    }

    menu.addSeparator();
    menu.add(new JMenuItem("Cut")).addActionListener(e -> cut());
    menu.add(new JMenuItem("Copy")).addActionListener(e -> copy());
    menu.add(new JMenuItem("Paste")).addActionListener(e -> paste());

    menu.show(this, x, y);
  }

  private void showHoverSuggestions(String word, int wordStart, int wordEnd) {
    List<String> suggestions = spellChecker.getSuggestions(word);

    if (suggestions.isEmpty()) return;

    hideSuggestions();

    suggestionPopup = new JPopupMenu();

    JPanel strip = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 5));
    strip.setBackground(Color.WHITE);

    for (String suggestion : suggestions) {
      JButton btn = new JButton(suggestion);
      btn.setMargin(new Insets(2, 4, 2, 4));
      btn.addActionListener(
          ae -> {
            replaceMisspelledWord(suggestion, wordStart, wordEnd);
            hideSuggestions();
          });
      strip.add(btn);
    }

    suggestionPopup.add(strip);

    try {
      Rectangle rect = modelToView(wordStart);
      suggestionPopup.show(this, rect.x, rect.y + rect.height + 2);
    } catch (BadLocationException ex) {
      // Handle silently
    }
  }

  private void hideSuggestions() {
    if (suggestionPopup != null) {
      suggestionPopup.setVisible(false);
      suggestionPopup = null;
    }
  }

  private void replaceMisspelledWord(String replacement, int start, int end) {
    try {
      getDocument().remove(start, end - start);
      getDocument().insertString(start, replacement, null);
      hideSuggestions();
    } catch (BadLocationException ex) {
      // Handle exception silently
    }
  }

  private int findWordStart(String text, int pos) {
    int start = pos - 1;
    // Handle case where cursor is at the very beginning or out of bounds
    if (start >= text.length()) {
      start = text.length() - 1;
    }
    while (start >= 0 && Character.isLetter(text.charAt(start))) {
      start--;
    }
    return start + 1;
  }

  private int findWordEnd(String text, int pos) {
    int end = pos;
    // Handle case where cursor is out of bounds
    if (end >= text.length()) {
      return text.length();
    }
    while (end < text.length() && Character.isLetter(text.charAt(end))) {
      end++;
    }
    return end;
  }

  public void setSpellChecker(SpellChecker spellChecker) {
    this.spellChecker = spellChecker;
  }

  @Override
  protected void paintComponent(Graphics g) {
    super.paintComponent(g);

    if (spellChecker != null) {
      paintSpellCheckUnderlines(g);
    }
  }

  private void paintSpellCheckUnderlines(Graphics g) {
    String text = getText();
    if (text.isEmpty()) return;

    Graphics2D g2 = (Graphics2D) g.create();
    g2.setColor(Color.RED);
    g2.setStroke(
        new BasicStroke(
            1.0f,
            BasicStroke.CAP_BUTT,
            BasicStroke.JOIN_MITER,
            10.0f,
            new float[] {2.0f, 2.0f},
            0.0f));

    try {
      FontMetrics fm = g2.getFontMetrics();
      int lineHeight = fm.getHeight();

      String[] lines = text.split("\n");
      int currentPos = 0;

      for (int lineNum = 0; lineNum < lines.length; lineNum++) {
        String line = lines[lineNum];
        Rectangle lineRect = modelToView(currentPos);

        if (lineRect != null) {
          int y = lineRect.y + lineHeight - 2;
          checkLineForMisspellings(g2, line, lineRect.x, y, fm);
        }

        currentPos += line.length() + 1; // +1 for newline
      }
    } catch (BadLocationException ex) {
      // Handle exception silently
    }

    g2.dispose();
  }

  private void checkLineForMisspellings(
      Graphics2D g2, String line, int lineX, int lineY, FontMetrics fm) {
    int wordStart = 0;
    for (int i = 0; i <= line.length(); i++) {
      if (i == line.length() || !Character.isLetter(line.charAt(i))) {
        if (wordStart < i) {
          String word = line.substring(wordStart, i);
          if (!spellChecker.isCorrect(word)) {
            int x1 = lineX + fm.stringWidth(line.substring(0, wordStart));
            int x2 = lineX + fm.stringWidth(line.substring(0, i));
            g2.drawLine(x1, lineY, x2, lineY);
          }
        }
        wordStart = i + 1;
      }
    }
  }

  /**
   * A utility class for detecting programming languages and formatting code snippets. Combines
   * logic from EclipseJavaFormatter and SyntaxHighlighterGUI.
   */
  public static class CodeFormatterUtil {

    private final ObjectMapper jsonObjectMapper;
    private static final Map<String, String> languageToSyntaxStyle = new HashMap<>();

    static {
      languageToSyntaxStyle.put("Java", SyntaxConstants.SYNTAX_STYLE_JAVA);
      languageToSyntaxStyle.put("Python", SyntaxConstants.SYNTAX_STYLE_PYTHON);
      languageToSyntaxStyle.put("JavaScript", SyntaxConstants.SYNTAX_STYLE_JAVASCRIPT);
      languageToSyntaxStyle.put("HTML", SyntaxConstants.SYNTAX_STYLE_HTML);
      languageToSyntaxStyle.put("XML", SyntaxConstants.SYNTAX_STYLE_XML);
      languageToSyntaxStyle.put("CSS", SyntaxConstants.SYNTAX_STYLE_CSS);
      languageToSyntaxStyle.put("C", SyntaxConstants.SYNTAX_STYLE_C);
      languageToSyntaxStyle.put("C++", SyntaxConstants.SYNTAX_STYLE_CPLUSPLUS);
      languageToSyntaxStyle.put("C#", SyntaxConstants.SYNTAX_STYLE_CSHARP);
      languageToSyntaxStyle.put("SQL", SyntaxConstants.SYNTAX_STYLE_SQL);
      languageToSyntaxStyle.put("JSON", SyntaxConstants.SYNTAX_STYLE_JSON);
      languageToSyntaxStyle.put("Markdown", SyntaxConstants.SYNTAX_STYLE_MARKDOWN);
      languageToSyntaxStyle.put("PHP", SyntaxConstants.SYNTAX_STYLE_PHP);
      languageToSyntaxStyle.put("Ruby", SyntaxConstants.SYNTAX_STYLE_RUBY);
      languageToSyntaxStyle.put("Perl", SyntaxConstants.SYNTAX_STYLE_PERL);
      languageToSyntaxStyle.put("Text (None)", SyntaxConstants.SYNTAX_STYLE_NONE);
    }

    public CodeFormatterUtil() {
      jsonObjectMapper = new ObjectMapper();
      jsonObjectMapper.enable(SerializationFeature.INDENT_OUTPUT);
    }

    /**
     * Formats a code string based on the given language.
     *
     * @param code The source code to format.
     * @param language The programming language of the code.
     * @return The formatted code string.
     */
    public String format(String code, String language) {
      if (code == null || language == null) {
        return code;
      }
      try {
        switch (language) {
          case "Java":
            return formatJava(code);
          case "JSON":
            return formatJson(code);
          case "HTML":
            return formatHtml(code);
          case "XML":
            return formatXml(code);
          default:
            return code; // No formatter available
        }
      } catch (Exception e) {
        System.err.println("Failed to format " + language + " code: " + e.getMessage());
        return code; // Return original on error
      }
    }

    /** Checks if a formatter is available for the specified language. */
    public boolean isFormatterAvailable(String language) {
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

    /**
     * Detects the programming language of a code snippet.
     *
     * @param languageHint An optional hint from markdown (e.g., "java").
     * @param code The code snippet to analyze.
     * @return The name of the detected language (e.g., "Java", "Python").
     */
    public String detectLanguage(String languageHint, String code) {
      // 1. Use the hint if it's valid and mapped
      if (languageHint != null && !languageHint.isEmpty()) {
        for (String key : languageToSyntaxStyle.keySet()) {
          if (key.equalsIgnoreCase(languageHint)) {
            return key;
          }
        }
      }
      // 2. If no valid hint, use heuristic detection
      return fallbackHeuristicDetection(code);
    }

    /** Gets the RSyntaxTextArea syntax style constant for a given language name. */
    public String getSyntaxStyle(String language) {
      return languageToSyntaxStyle.getOrDefault(language, SyntaxConstants.SYNTAX_STYLE_NONE);
    }

    // --- Private Formatting Methods ---

    private String formatJava(String source) {
      try {
        Map<String, String> options = JavaCore.getOptions();
        options.put(JavaCore.COMPILER_SOURCE, "21");
        options.put(JavaCore.COMPILER_COMPLIANCE, "21");
        options.put(JavaCore.COMPILER_CODEGEN_TARGET_PLATFORM, "21");

        CodeFormatter formatter = ToolFactory.createCodeFormatter(options);

        TextEdit edit =
            formatter.format(
                CodeFormatter.K_COMPILATION_UNIT | CodeFormatter.K_STATEMENTS,
                source,
                0,
                source.length(),
                0,
                System.lineSeparator());

        if (edit == null) return source;

        Document document = new Document(source);
        edit.apply(document);
        return document.get();
      } catch (Exception e) {
        System.err.println("Failed to format java: " + e.getMessage());
        return source;
      }
    }

    private String formatJson(String code) throws IOException {
      Object json = jsonObjectMapper.readValue(code, Object.class);
      return jsonObjectMapper.writeValueAsString(json);
    }

    private String formatHtml(String code) {
      org.jsoup.nodes.Document doc = Jsoup.parse(code, "", Parser.htmlParser());
      doc.outputSettings().indentAmount(2).outline(true);
      return doc.html();
    }

    private String formatXml(String code) {
      org.jsoup.nodes.Document doc = Jsoup.parse(code, "", Parser.xmlParser());
      doc.outputSettings().indentAmount(2).outline(true);
      return doc.outerHtml();
    }

    // --- Private Language Detection Logic (from SyntaxHighlighterGUI) ---

    private String fallbackHeuristicDetection(String code) {
      String trimmedCode = code.trim();
      if (trimmedCode.isEmpty()) return "Text (None)";

      // Java: Common keywords and structure
      if (Pattern.compile("class\\s+\\w+\\s*\\{").matcher(code).find() && code.contains(";")) {
        return "Java";
      }
      if ((code.contains("public class") || code.contains("import java.")) && code.contains("{")) {
        return "Java";
      }

      // JSON: Must start with { or [
      if ((trimmedCode.startsWith("{") && trimmedCode.endsWith("}"))
          || (trimmedCode.startsWith("[") && trimmedCode.endsWith("]"))) {
        return "JSON";
      }

      // HTML/XML
      String lowerCode = trimmedCode.toLowerCase();
      if (lowerCode.startsWith("<!doctype html") || lowerCode.contains("<html")) {
        return "HTML";
      }
      if (lowerCode.startsWith("<?xml")) {
        return "XML";
      }
      if (Pattern.compile("<\\s*\\w+[^>]*>.*?</\\s*\\w+\\s*>", Pattern.DOTALL)
          .matcher(trimmedCode)
          .find()) {
        return "XML";
      }

      // CSS
      if (Pattern.compile("\\s*([#.]?\\w+)\\s*\\{([^}]+)\\}").matcher(code).find()) {
        return "CSS";
      }

      // Python
      if (code.contains("def ")
          && code.contains(":")
          && !code.contains("{")
          && !code.contains(";")) {
        return "Python";
      }

      // JavaScript
      if (code.contains("function ")
          || code.contains("const ")
          || code.contains("let ")
          || code.contains("console.log")) {
        return "JavaScript";
      }

      // SQL
      if (Pattern.compile(
              "\\b(SELECT|INSERT\\s+INTO|UPDATE|DELETE\\s+FROM|CREATE\\s+TABLE)\\b",
              Pattern.CASE_INSENSITIVE)
          .matcher(code)
          .find()) {
        return "SQL";
      }

      // C#
      if (code.contains("namespace ")
          && code.contains("class ")
          && code.contains("static void Main")) {
        return "C#";
      }

      // C++
      if (code.contains("#include <iostream>")
          || (code.contains("std::cout") && code.contains("int main"))) {
        return "C++";
      }

      // C
      if (code.contains("#include <stdio.h>")
          && code.contains("int main")
          && code.contains("printf(")) {
        return "C";
      }

      return "Text (None)";
    }
  }
}
