package xxx.com.concat;

import com.formdev.flatlaf.FlatDarkLaf;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.*;
import java.io.*;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.prefs.Preferences;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.TableModelEvent;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.plaf.basic.BasicSplitPaneDivider;
import javax.swing.plaf.basic.BasicSplitPaneUI;
import javax.swing.table.*;
import javazoom.jl.player.Player;

public class JavaFileConcatenator extends JFrame {

  @Serial
  private static final long serialVersionUID = 26L;
  private static final Border noFocusBorder = BorderFactory.createEmptyBorder(1, 1, 1, 1);

  private static final String RACOON_SFX = "audio/racoon.mp3";
  private static final String WHISTLE_SFX = "audio/whistle.mp3";

  private JTextArea displayArea;
  private JButton saveButton;
  private JButton clearFab;
  private final JFileChooser fileChooser;
  private JTabbedPane tabbedPane;
  private JProgressBar progressBar;
  private JLayeredPane layeredPane;

  // --- FAB Positioning & Animation ---
  private int fabVerticalOffset = 10;
  private Timer fabAnimationTimer;

  // --- "Concatenate by File" Tab Components ---
  private JSplitPane fileTabSplitPane;
  private DefaultListModel<FileContentBlock> fileContentModel;
  private JList<FileContentBlock> fileContentList;
  private final List<FileContentBlock> fileContentBlocks = new ArrayList<>();
  private ImageIcon emptyStateIcon;
  private ImageIcon addIcon;
  private ImageIcon trashIcon;
  private ImageIcon selectDirIcon;
  private ImageIcon clearIcon;
  private ImageIcon processIcon;
  private ImageIcon fileIcon;
  private ImageIcon directoryIcon;
  private ImageIcon extensionIcon;
  private ImageIcon refreshIcon;

  private int lastDividerLocationBeforeCollapse;

  // --- "Concatenate by Directory" Tab Components ---
  private JTextField dirPathField;
  private JLabel dirErrorLabel;
  private Timer messageClearTimer;
  private MarqueeLabel marqueeLabel;
  @SuppressWarnings("FieldCanBeLocal")
  private JPanel scanConfigPanel;
  private JPanel messagePanel;
  private CardLayout messageCardLayout;
  private String activeMarqueeMessage = null;
  private List<FilterItem> filterData;
  private FilterTableModel filterTableModel;
  private DefaultListModel<FileListItem> foundFilesModel;
  private JList<FileListItem> foundFilesList;
  private DefaultListModel<DirectoryListItem> directoryFilterModel;
  private JList<DirectoryListItem> directoryFilterList;
  private Path scanRootPath;
  private JLabel directoryCountLabel;
  private JLabel fileCountLabel;
  private JCheckBox rememberDirCheckbox;

  // --- Data stores for dynamic filtering ---
  private List<File> allFilesFromScan = new ArrayList<>();
  private Set<File> allDirsFromScan = new HashSet<>();

  // --- Preferences for persistent settings ---
  private final Preferences prefs;
  private static final String LAST_DIR_KEY = "lastDirectory";
  private static final String REMEMBER_DIR_KEY = "rememberDirectory";
  private static final String SAVED_FILTERS_KEY = "savedFilters";
  private static final String SELECTED_FILES_KEY = "selectedFiles";
  private static final String SELECTED_DIRS_KEY = "selectedDirectories";

  // --- Enum for message types ---
  public enum MessageType {
    ALERT, MARQUEE
  }


  public JavaFileConcatenator() {
    setTitle("File Concatenator");
    setSize(900, 700);
    setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    setLocationRelativeTo(null);

    java.net.URL iconURL = getClass().getResource("/png/process.png");
    if (iconURL != null) {
      setIconImage(new ImageIcon(iconURL).getImage());
    }
    else {
      System.err.println("Warning: Could not find app icon resource 'app_icon.png'.");
    }

    // Initialize preferences for this application node
    prefs = Preferences.userNodeForPackage(JavaFileConcatenator.class);

    fileChooser = new JFileChooser();
    loadResources();

    initComponents();
    addComponentsToFrame();
    loadPreferences(); // Load saved settings after UI is initialized
    addWindowListener(new WindowAdapter() {
      @Override
      public void windowClosing(WindowEvent e) {
        saveSelections();
        super.windowClosing(e);
      }
    });
    playSound(RACOON_SFX);
  }

  private void loadPreferences() {
    // Load "Remember Directory" setting
    boolean remember = prefs.getBoolean(REMEMBER_DIR_KEY, false);
    rememberDirCheckbox.setSelected(remember);
    if (remember) {
      String lastDir = prefs.get(LAST_DIR_KEY, null);
      if (lastDir != null && !lastDir.isEmpty()) {
        dirPathField.setText(lastDir);
      }
    }

    // Load saved filters
    String savedFiltersJson = prefs.get(SAVED_FILTERS_KEY, "[]");
    List<FilterItem> loadedFilters = FilterItem.fromJson(savedFiltersJson);
    if (!loadedFilters.isEmpty()) {
      filterData.clear();
      filterData.addAll(loadedFilters);
      filterTableModel.fireTableDataChanged();
    }
  }

  private void saveFilters() {
    String filtersJson = FilterItem.toJson(filterData);
    prefs.put(SAVED_FILTERS_KEY, filtersJson);
  }

  private void saveSelections() {
    if (scanRootPath == null || !rememberDirCheckbox.isSelected()) {
      prefs.remove(SELECTED_FILES_KEY);
      prefs.remove(SELECTED_DIRS_KEY);
      return;
    }

    Set<String> selectedFilePaths = new HashSet<>();
    for (int i = 0; i < foundFilesModel.getSize(); i++) {
      FileListItem item = foundFilesModel.getElementAt(i);
      if (item.isSelected()) {
        selectedFilePaths.add(item.getFile().getAbsolutePath());
      }
    }

    Set<String> selectedDirPaths = new HashSet<>();
    for (int i = 0; i < directoryFilterModel.getSize(); i++) {
      DirectoryListItem item = directoryFilterModel.getElementAt(i);
      if (item.isSelected()) {
        selectedDirPaths.add(item.getDirectory().getAbsolutePath());
      }
    }

    prefs.put(SELECTED_FILES_KEY, String.join(";", selectedFilePaths));
    prefs.put(SELECTED_DIRS_KEY, String.join(";", selectedDirPaths));
  }

  private void restoreSelections() {
    if (scanRootPath == null || !rememberDirCheckbox.isSelected()) {
      return;
    }

    String selectedFilesStr = prefs.get(SELECTED_FILES_KEY, "");
    String selectedDirsStr = prefs.get(SELECTED_DIRS_KEY, "");

    Set<String> selectedFilePaths = new HashSet<>(Arrays.asList(selectedFilesStr.split(";")));
    Set<String> selectedDirPaths = new HashSet<>(Arrays.asList(selectedDirsStr.split(";")));

    boolean selectionsChanged = false;

    // Restore file selections
    for (int i = 0; i < foundFilesModel.getSize(); i++) {
      FileListItem item = foundFilesModel.getElementAt(i);
      String path = item.getFile().getAbsolutePath();
      if (selectedFilePaths.contains(path)) {
        if (item.getFile().exists()) {
          item.setSelected(true);
        }
        else {
          selectedFilePaths.remove(path); // Remove non-existent file path
          selectionsChanged = true;
        }
      }
      else {
        item.setSelected(false);
      }
    }

    // Restore directory selections
    for (int i = 0; i < directoryFilterModel.getSize(); i++) {
      DirectoryListItem item = directoryFilterModel.getElementAt(i);
      String path = item.getDirectory().getAbsolutePath();
      if (selectedDirPaths.contains(path)) {
        if (item.getDirectory().exists()) {
          item.setSelected(true);
        }
        else {
          selectedDirPaths.remove(path); // Remove non-existent dir path
          selectionsChanged = true;
        }
      }
      else {
        item.setSelected(false);
      }
    }

    // If any paths were invalid, re-save the cleaned-up preferences
    if (selectionsChanged) {
      prefs.put(SELECTED_FILES_KEY, String.join(";", selectedFilePaths));
      prefs.put(SELECTED_DIRS_KEY, String.join(";", selectedDirPaths));
    }

    foundFilesList.repaint();
    directoryFilterList.repaint();
    updateCounters();
  }

  private void loadResources() {
    try {

      // Load and scale main empty state icon
      java.net.URL emptyStateUrl = getClass().getResource("/add_file.png");
      if (emptyStateUrl != null) {
        emptyStateIcon = new ImageIcon(emptyStateUrl);
      }
      else {
        System.err.println("Warning: Could not find empty state image resource 'add_file.png'.");
      }

      // Load and scale the filter type icons
      directoryIcon = scaleIcon(new ImageIcon(Objects.requireNonNull(getClass().getResource("/png/directory.png"))), 16, 16);
      fileIcon = scaleIcon(new ImageIcon(Objects.requireNonNull(getClass().getResource("/png/file.png"))), 16, 16);
      extensionIcon = scaleIcon(new ImageIcon(Objects.requireNonNull(getClass().getResource("/png/extension.png"))), 16, 16);

      // Load and scale the select icon
      java.net.URL selectDirUrl = getClass().getResource("/png/select.png");
      if (selectDirUrl != null) {
        ImageIcon originalSelectDirIcon = new ImageIcon(selectDirUrl);
        Image scaledSelectDirImage = originalSelectDirIcon.getImage().getScaledInstance(16, 16, Image.SCALE_SMOOTH);
        selectDirIcon = new ImageIcon(scaledSelectDirImage);
      }
      else {
        System.err.println("Warning: Could not select directory icon resource 'select.png'.");
      }

      // Load and scale the refresh icon
      java.net.URL refreshUrl = getClass().getResource("/png/refresh.png");
      if (refreshUrl != null) {
        refreshIcon = scaleIcon(new ImageIcon(refreshUrl), 16, 16);
      } else {
        System.err.println("Warning: Could not find refresh icon resource 'refresh.png'.");
      }

      java.net.URL clearUrl = getClass().getResource("/png/clear.png");
      if (clearUrl != null) {
        ImageIcon originalClearIcon = new ImageIcon(clearUrl);
        Image scaledClearImage = originalClearIcon.getImage().getScaledInstance(28, 28, Image.SCALE_SMOOTH);
        clearIcon = new ImageIcon(scaledClearImage);
      }
      else {
        System.err.println("Warning: Could not find clear icon resource 'clear.png'.");
      }

      // Load and scale the process icon
      java.net.URL processUrl = getClass().getResource("/png/process.png");
      if (processUrl != null) {
        ImageIcon originalProcessIcon = new ImageIcon(processUrl);
        Image scaledProcessImage = originalProcessIcon.getImage().getScaledInstance(32, 32, Image.SCALE_SMOOTH);
        processIcon = new ImageIcon(scaledProcessImage);
      }
      else {
        System.err.println("Warning: Could not find process icon resource 'process.png'.");
      }

      // Load and scale the new add icon
      java.net.URL addIconUrl = getClass().getResource("/png/add.png");
      if (addIconUrl != null) {
        ImageIcon originalAddIcon = new ImageIcon(addIconUrl);
        Image scaledAddImage = originalAddIcon.getImage().getScaledInstance(16, 16, Image.SCALE_SMOOTH);
        addIcon = new ImageIcon(scaledAddImage);
      }
      else {
        System.err.println("Warning: Could not find add icon resource 'add.png'.");
      }

      // Load and scale the new trash icon
      java.net.URL trashIconUrl = getClass().getResource("/png/trash.png");
      if (trashIconUrl != null) {
        ImageIcon originalTrashIcon = new ImageIcon(trashIconUrl);
        Image scaledTrashImage = originalTrashIcon.getImage().getScaledInstance(16, 16, Image.SCALE_SMOOTH);
        trashIcon = new ImageIcon(scaledTrashImage);
      }
      else {
        System.err.println("Warning: Could not find trash icon resource 'trash.png'.");
      }
    }
    catch (Exception e) {
      System.err.println("Error loading resources: " + e.getMessage());
    }
  }

  private ImageIcon scaleIcon(ImageIcon icon, int width, int height) {

    if (icon == null || icon.getImage() == null) {
      System.err.println("Cannot scale a null icon.");
      return new ImageIcon(); // Return an empty icon to avoid NullPointerException
    }
    Image img = icon.getImage();
    Image scaledImg = img.getScaledInstance(width, height, Image.SCALE_SMOOTH);
    return new ImageIcon(scaledImg);
  }

  private void setSaveButtonActive(boolean isActive) {

    saveButton.setEnabled(isActive);

    if (isActive) {
      saveButton.setBackground(new Color(30, 130, 76)); // Active (green)
    }
    else {
      saveButton.setBackground(UIManager.getColor("Component.borderColor")); // Inactive (gray)
    }
  }

  private void initComponents() {

    tabbedPane = new JTabbedPane();

    JPanel filePanel = createFilePanel();
    tabbedPane.addTab("Concatenate by File", filePanel);

    JPanel directoryPanel = createDirectoryPanel();
    tabbedPane.addTab("Concatenate by Directory", directoryPanel);

    // Add a listener to animate FAB positions and trigger auto-scan
    tabbedPane.addChangeListener(e -> {
      int selectedIndex = tabbedPane.getSelectedIndex();
      if (selectedIndex == 0) {
        // "File" tab: animate to default position
        animateFabVerticalOffset(10);
      }
      else if (selectedIndex == 1) {
        // "Directory" tab: animate buttons up
        animateFabVerticalOffset(30);

        // --- AUTO-SCAN LOGIC ---
        // Check if remember is toggled, path is valid, and we haven't scanned yet
        if (rememberDirCheckbox.isSelected() && !dirPathField.getText().equals("Select Directory") && scanRootPath == null) {
          File dir = new File(dirPathField.getText());
          if (dir.isDirectory()) {
            scanDirectoryAction();
          }
          else {
            displayMessage("Remembered path is not a valid directory.", MessageType.ALERT);
            // Invalidate the preference since the path is bad
            rememberDirCheckbox.setSelected(false);
            prefs.putBoolean(REMEMBER_DIR_KEY, false);
            prefs.remove(LAST_DIR_KEY);
          }
        }
      }
    });

    saveButton = new FloatingActionButton(processIcon);
    saveButton.setToolTipText("Process");
    saveButton.setEnabled(false);
    saveButton.addActionListener(e -> saveConcatenatedFile());
    saveButton.setBackground(UIManager.getColor("Component.borderColor"));

    clearFab = new FloatingActionButton(clearIcon);
    clearFab.setToolTipText("Clear");
    clearFab.setEnabled(false);
    clearFab.setBackground(UIManager.getColor("Actions.Blue"));

    clearFab.addActionListener(e -> {

      int selectedTab = tabbedPane.getSelectedIndex();
      if (selectedTab == 0) {
        clearFileContent();
      }
      else if (selectedTab == 1) {
        clearDirectoryContent();
      }
    });


    progressBar = new JProgressBar(0, 100);
    progressBar.setStringPainted(true);
    progressBar.setVisible(false);
  }

  private JPanel createFilePanel() {

    JPanel filePanel = new JPanel(new BorderLayout(10, 10));
    filePanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

    fileContentModel = new DefaultListModel<>();
    fileContentList = new JList<>(fileContentModel);
    fileContentList.setCellRenderer(new FileContentListCellRenderer());

    fileContentList.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        int index = fileContentList.locationToIndex(e.getPoint());
        if (index != -1) {
          FileContentBlock block = fileContentModel.getElementAt(index);
          block.setSelected(!block.isSelected());
          regenerateDisplayArea();
          fileContentList.repaint(fileContentList.getCellBounds(index, index));
        }
      }
    });

    JScrollPane fileListScrollPane = new JScrollPane(fileContentList);
    fileListScrollPane.setBorder(BorderFactory.createTitledBorder("Source Files"));

    displayArea = new ImageOverlayTextArea(emptyStateIcon != null ? emptyStateIcon.getImage() : null);
    displayArea.setEditable(false);
    displayArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
    displayArea.setOpaque(false);

    displayArea.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        selectFiles();
      }
    });

    setupDragAndDrop(displayArea);


    JScrollPane displayScrollPane = new JScrollPane(displayArea);
    displayScrollPane.getViewport().setOpaque(false);
    displayScrollPane.setOpaque(false);
    fileTabSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, fileListScrollPane, displayScrollPane);
    fileTabSplitPane.setDividerLocation(0);
    fileTabSplitPane.setDividerSize(8);

    if (fileTabSplitPane.getUI() instanceof BasicSplitPaneUI) {
      BasicSplitPaneDivider divider = ((BasicSplitPaneUI) fileTabSplitPane.getUI()).getDivider();
      divider.addMouseListener(new MouseAdapter() {
        @Override
        public void mouseClicked(MouseEvent e) {
          if (e.getClickCount() == 2) {
            int currentLocation = fileTabSplitPane.getDividerLocation();
            if (currentLocation < fileTabSplitPane.getDividerSize()) {
              if (fileContentModel.isEmpty()) {
                fileTabSplitPane.setDividerLocation(lastDividerLocationBeforeCollapse > 0 ? lastDividerLocationBeforeCollapse : 150);
              }
              else {
                String shortestName = fileContentModel.getElementAt(0).toString();
                for (int i = 1; i < fileContentModel.getSize(); i++) {
                  String currentName = fileContentModel.getElementAt(i).toString();
                  if (currentName.length() < shortestName.length()) {
                    shortestName = currentName;
                  }
                }
                FontMetrics fm = fileContentList.getFontMetrics(fileContentList.getFont());
                int newLocation = fm.stringWidth(shortestName) + 50;
                fileTabSplitPane.setDividerLocation(newLocation);
              }
            }
            else {
              lastDividerLocationBeforeCollapse = currentLocation;
              fileTabSplitPane.setDividerLocation(0);
            }
          }
        }
      });
    }

    filePanel.add(fileTabSplitPane, BorderLayout.CENTER);
    return filePanel;
  }

  /**
   * Adds a double-click listener to a JSplitPane's divider to toggle collapse/expand state. Collapses
   * the top or left component by moving the divider to position 0.
   *
   * @param splitPane The JSplitPane to make collapsible.
   */
  private void addCollapsibility(JSplitPane splitPane) {
    if (!(splitPane.getUI() instanceof BasicSplitPaneUI)) {
      return;
    }
    BasicSplitPaneDivider divider = ((BasicSplitPaneUI) splitPane.getUI()).getDivider();
    divider.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        if (e.getClickCount() == 2) {
          int currentLocation = splitPane.getDividerLocation();
          // A divider location near 0 means the top/left pane is collapsed.
          if (currentLocation < splitPane.getDividerSize()) {
            // It's collapsed, so restore it.
            Object lastLocationObj = splitPane.getClientProperty("lastLocation");
            int restoreLocation = 150; // A generic default
            if (lastLocationObj instanceof Integer && (Integer) lastLocationObj > 0) {
              restoreLocation = (Integer) lastLocationObj;
            }
            else {
              // If no last location, calculate a sensible default.
              if (splitPane.getOrientation() == JSplitPane.VERTICAL_SPLIT) {
                restoreLocation = Math.max(150, splitPane.getHeight() / 4);
              }
              else {
                restoreLocation = Math.max(150, splitPane.getWidth() / 4);
              }
            }
            splitPane.setDividerLocation(restoreLocation);
          }
          else {
            // It's open, so collapse it.
            splitPane.putClientProperty("lastLocation", currentLocation);
            splitPane.setDividerLocation(0);
          }
        }
      }
    });
  }

  private JPanel createDirectoryPanel() {
    JPanel directoryPanel = new JPanel(new BorderLayout(10, 10));
    directoryPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

    // --- Panel 1: Scan Configuration (Top, Not Collapsible) ---
    scanConfigPanel = new JPanel(new GridBagLayout());
    scanConfigPanel.setBorder(BorderFactory.createTitledBorder("Scan Configuration"));
    GridBagConstraints gbc = new GridBagConstraints();
    gbc.insets = new Insets(2, 5, 2, 5);
    gbc.fill = GridBagConstraints.HORIZONTAL;

    gbc.gridx = 0;
    gbc.gridy = 0;
    gbc.weightx = 0;
    JButton selectDirButton = new JButton(selectDirIcon);
    selectDirButton.setToolTipText("Select directory");
    selectDirButton.setBorderPainted(true);
    selectDirButton.setContentAreaFilled(true);
    selectDirButton.setFocusPainted(false);
    selectDirButton.setOpaque(false);
    selectDirButton.addActionListener(e -> selectDirectory());
    scanConfigPanel.add(selectDirButton, gbc);

    gbc.gridx = 1;
    gbc.gridy = 0;
    gbc.weightx = 1.0;
    dirPathField = new JTextField("Select Directory");
    dirPathField.setEditable(true);
    dirPathField.addActionListener(e -> scanDirectoryAction());
    dirPathField.setTransferHandler(new DirectoryDropHandler());
    new BashStyleTabCompleter(dirPathField, this);
    scanConfigPanel.add(dirPathField, gbc);

    gbc.gridx = 2;
    gbc.weightx = 0;
    gbc.fill = GridBagConstraints.NONE;
    gbc.anchor = GridBagConstraints.CENTER;
    JButton refreshButton = new JButton(refreshIcon);
    refreshButton.setToolTipText("Rescan current directory");
    refreshButton.addActionListener(e -> scanDirectoryAction());
    refreshButton.setBorderPainted(true);
    refreshButton.setContentAreaFilled(true);
    refreshButton.setFocusPainted(false);
    refreshButton.setOpaque(false);
    scanConfigPanel.add(refreshButton, gbc);

    gbc.gridx = 3;
    gbc.gridy = 0;
    gbc.weightx = 0;
    gbc.fill = GridBagConstraints.NONE;
    gbc.anchor = GridBagConstraints.CENTER;
    rememberDirCheckbox = new JCheckBox();
    rememberDirCheckbox.setToolTipText("Remember Directory Path");
    rememberDirCheckbox.addActionListener(e -> {
      boolean isSelected = rememberDirCheckbox.isSelected();
      prefs.putBoolean(REMEMBER_DIR_KEY, isSelected);
      if (isSelected) {
        File dir = new File(dirPathField.getText());
        if (dir.isDirectory()) {
          prefs.put(LAST_DIR_KEY, dir.getAbsolutePath());
        }
      }
      else {
        prefs.remove(LAST_DIR_KEY);
      }
    });
    scanConfigPanel.add(rememberDirCheckbox, gbc);

    // --- Message Area ---
    messageCardLayout = new CardLayout();
    messagePanel = new JPanel(messageCardLayout);
    dirErrorLabel = new JLabel(" ");
    dirErrorLabel.setFont(dirErrorLabel.getFont().deriveFont(10f));
    dirErrorLabel.setForeground(Color.RED);
    messagePanel.add(new JPanel(), "EMPTY"); // Blank panel
    messagePanel.add(dirErrorLabel, "ALERT");

    gbc.gridy = 1;
    gbc.gridx = 0;
    gbc.gridwidth = 4; // Span all columns
    gbc.insets = new Insets(0, 5, 0, 5);
    scanConfigPanel.add(messagePanel, gbc);


    directoryPanel.add(scanConfigPanel, BorderLayout.NORTH);

    // --- Panel 2: Filters (Will be in a collapsible pane) ---
    JPanel filterPanel = createFilterPanel();

    // --- Panel 3: Directory Filter (Will be in a collapsible pane) ---
    directoryFilterModel = new DefaultListModel<>();
    directoryFilterList = new JList<>(directoryFilterModel);
    directoryFilterList.setCellRenderer(new DirectoryListCellRenderer());
    directoryFilterList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    directoryFilterList.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        int index = directoryFilterList.locationToIndex(e.getPoint());
        if (index == -1) {
          return;
        }
        DirectoryListItem clickedDirItem = directoryFilterModel.getElementAt(index);
        boolean shouldBeSelected = !clickedDirItem.isSelected();
        String clickedPath = clickedDirItem.getDirectory().getAbsolutePath();
        for (int i = 0; i < directoryFilterModel.getSize(); i++) {
          DirectoryListItem currentDirItem = directoryFilterModel.getElementAt(i);
          String currentPath = currentDirItem.getDirectory().getAbsolutePath();
          if (currentPath.equals(clickedPath) || currentPath.startsWith(clickedPath + File.separator)) {
            currentDirItem.setSelected(shouldBeSelected);
          }
        }
        for (int i = 0; i < foundFilesModel.getSize(); i++) {
          FileListItem currentFileItem = foundFilesModel.getElementAt(i);
          String parentPath = currentFileItem.getFile().getParent();
          if (parentPath != null && (parentPath.equals(clickedPath) || parentPath.startsWith(clickedPath + File.separator))) {
            currentFileItem.setSelected(shouldBeSelected);
          }
        }
        directoryFilterList.repaint();
        foundFilesList.repaint();
        updateCounters();
      }
    });
    JScrollPane directoryFilterScrollPane = new JScrollPane(directoryFilterList);
    directoryFilterScrollPane.setBorder(BorderFactory.createTitledBorder("Directory Filter"));
    directoryCountLabel = new JLabel();
    directoryCountLabel.setHorizontalAlignment(SwingConstants.CENTER);
    JPanel directoryFilterPanel = new JPanel(new BorderLayout(0, 3));
    directoryFilterPanel.add(directoryFilterScrollPane, BorderLayout.CENTER);
    directoryFilterPanel.add(directoryCountLabel, BorderLayout.SOUTH);

    // --- Panel 4: Found Files ---
    foundFilesModel = new DefaultListModel<>();
    foundFilesList = new JList<>(foundFilesModel);
    foundFilesList.setCellRenderer(new FileListCellRenderer());
    foundFilesList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    foundFilesList.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        int index = foundFilesList.locationToIndex(e.getPoint());
        if (index != -1) {
          FileListItem item = foundFilesModel.getElementAt(index);
          item.setSelected(!item.isSelected());
          foundFilesList.repaint(foundFilesList.getCellBounds(index, index));
          updateCounters();
        }
      }
    });
    JScrollPane foundFilesScrollPane = new JScrollPane(foundFilesList);
    foundFilesScrollPane.setBorder(BorderFactory.createTitledBorder("Found Files"));
    fileCountLabel = new JLabel();
    fileCountLabel.setHorizontalAlignment(SwingConstants.CENTER);
    JPanel foundFilesPanel = new JPanel(new BorderLayout(0, 3));
    foundFilesPanel.add(foundFilesScrollPane, BorderLayout.CENTER);
    foundFilesPanel.add(fileCountLabel, BorderLayout.SOUTH);

    // --- Assemble collapsible sections using nested Split Panes ---
    JSplitPane horizontalSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, directoryFilterPanel, foundFilesPanel);
    horizontalSplit.setDividerLocation(250);
    horizontalSplit.setDividerSize(8);
    addCollapsibility(horizontalSplit);

    JSplitPane verticalSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, filterPanel, horizontalSplit);
    verticalSplit.setDividerLocation(220); // Initial height for the filter panel
    verticalSplit.setDividerSize(8);
    addCollapsibility(verticalSplit);

    directoryPanel.add(verticalSplit, BorderLayout.CENTER);

    updateCounters();
    return directoryPanel;
  }

  private JPanel createFilterPanel() {

    JPanel panel = new JPanel(new BorderLayout(0, 5));
    panel.setBorder(BorderFactory.createTitledBorder("Filters"));

    filterData = new ArrayList<>();
    filterTableModel = new FilterTableModel(filterData);

    JTable filterTable = new JTable(filterTableModel) {
      // --- FIX for Tooltip ---
      // This override ensures that tooltips from the cell renderer are displayed.
      @Override
      public String getToolTipText(MouseEvent e) {
        int row = rowAtPoint(e.getPoint());
        int col = columnAtPoint(e.getPoint());
        if (row != -1 && col != -1) {
          TableCellRenderer renderer = getCellRenderer(row, col);
          Component component = prepareRenderer(renderer, row, col);
          if (component instanceof JComponent) {
            return ((JComponent) component).getToolTipText();
          }
        }
        return super.getToolTipText(e);
      }
    };

    // Enable sorting support
    filterTable.setAutoCreateRowSorter(true);
    filterTable.setRowSorter(new TableRowSorter<>(filterTableModel));

    filterTable.setFillsViewportHeight(true);
    filterTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    filterTable.setShowGrid(false);
    filterTable.setIntercellSpacing(new Dimension(0, 0));
    filterTable.setRowHeight(24); // Add padding between rows

    // --- Column configuration ---
    TableColumnModel columnModel = filterTable.getColumnModel();
    PaddedCellRenderer paddedRenderer = new PaddedCellRenderer();
    columnModel.getColumn(0).setCellRenderer(paddedRenderer);
    columnModel.getColumn(0).setPreferredWidth(150);

    columnModel.getColumn(1).setCellRenderer(paddedRenderer);
    columnModel.getColumn(1).setPreferredWidth(75);
    columnModel.getColumn(1).setCellRenderer(new DefaultTableCellRenderer() {
      {
        setHorizontalAlignment(SwingConstants.CENTER);
      }
    });

    // Use the new TypeIconRenderer for the "Type" column
    TableColumn typeColumn = columnModel.getColumn(2);
    typeColumn.setCellRenderer(new TypeIconRenderer());
    typeColumn.setPreferredWidth(75);


    columnModel.getColumn(3).setCellRenderer(new ButtonRenderer(trashIcon));
    columnModel.getColumn(3).setPreferredWidth(30);
    columnModel.getColumn(3).setMinWidth(30);
    columnModel.getColumn(3).setMaxWidth(30);

    // --- Cell Editors for Dropdowns ---
    JComboBox<FilterItem.Decision> decisionComboBox = new JComboBox<>(FilterItem.Decision.values());
    columnModel.getColumn(1).setCellEditor(new DefaultCellEditor(decisionComboBox));

    JComboBox<FilterItem.Type> typeComboBox = new JComboBox<>(FilterItem.Type.values());
    typeComboBox.setRenderer(new TypeIconListCellRenderer());
    columnModel.getColumn(2).setCellEditor(new DefaultCellEditor(typeComboBox));

    filterTable.addMouseListener(new MouseAdapter() {
      private void handlePopup(MouseEvent e) {
        int row = filterTable.rowAtPoint(e.getPoint());
        if (row >= 0) {
          filterTable.setRowSelectionInterval(row, row);
          JPopupMenu contextMenu = createFilterContextMenu(row);
          contextMenu.show(e.getComponent(), e.getX(), e.getY());
        }
      }

      @Override
      public void mousePressed(MouseEvent e) {
        if (e.isPopupTrigger()) {
          handlePopup(e);
        }
      }

      @Override
      public void mouseReleased(MouseEvent e) {
        if (e.isPopupTrigger()) {
          handlePopup(e);
        }
      }

      @Override
      public void mouseClicked(MouseEvent e) {
        int column = filterTable.columnAtPoint(e.getPoint());
        int row = filterTable.rowAtPoint(e.getPoint());
        if (row >= 0 && column == 3) {
          SwingUtilities.invokeLater(() -> {
            filterData.remove(row);
            filterTableModel.fireTableRowsDeleted(row, row);
            saveFilters(); // Persist change
            applyFiltersAndUpdateUI(); // Re-filter after removal
          });
        }
      }
    });

    filterTableModel.addTableModelListener(e -> {
      if (e.getType() == TableModelEvent.UPDATE) {
        saveFilters(); // Persist change
        SwingUtilities.invokeLater(this::applyFiltersAndUpdateUI);
      }
    });

    JScrollPane scrollPane = new JScrollPane(filterTable);
    // Set a preferred size that is not excessively large but prevents collapsing.
    int headerHeight = filterTable.getTableHeader().getPreferredSize().height;
    int preferredHeight = headerHeight + (filterTable.getRowHeight() * 4); // Header + 4 rows
    scrollPane.setPreferredSize(new Dimension(scrollPane.getPreferredSize().width, preferredHeight));
    panel.add(scrollPane, BorderLayout.CENTER);


    // --- Input panel for adding new filters ---
    JPanel addPanel = new JPanel(new BorderLayout(10, 2));
    JTextField newFilterEntryField = new JTextField();
    final JPopupMenu autoCompletePopup = new JPopupMenu();
    autoCompletePopup.setFocusable(false);

    JComboBox<FilterItem.Type> typeSelector = new JComboBox<>(FilterItem.Type.values());
    typeSelector.setRenderer(new TypeIconListCellRenderer());

    newFilterEntryField.getDocument().addDocumentListener(new DocumentListener() {
      private Timer timer;

      private void triggerUpdate() {
        if (timer != null && timer.isRunning()) {
          timer.restart();
        }
        else {
          timer = new Timer(200, e -> updateAutoComplete());
          timer.setRepeats(false);
          timer.start();
        }
      }

      @Override
      public void insertUpdate(DocumentEvent e) {
        triggerUpdate();
      }

      @Override
      public void removeUpdate(DocumentEvent e) {
        triggerUpdate();
      }

      @Override
      public void changedUpdate(DocumentEvent e) {
        // Not used for plain text fields
      }

      private void updateAutoComplete() {
        String text = newFilterEntryField.getText();
        if (text.isEmpty() || scanRootPath == null) {
          autoCompletePopup.setVisible(false);
          return;
        }

        Set<Suggestion> suggestions = new HashSet<>();

        // Add directory suggestions
        allDirsFromScan.stream()
            .map(dir -> scanRootPath.relativize(dir.toPath()).toString())
            .filter(name -> !name.isEmpty() && name.toLowerCase().startsWith(text.toLowerCase()))
            .forEach(name -> suggestions.add(new Suggestion(name, FilterItem.Type.DIRECTORY)));

        // Add file and extension suggestions
        allFilesFromScan.stream()
            .map(File::getName)
            .forEach(name -> {
              if (name.toLowerCase().startsWith(text.toLowerCase())) {
                suggestions.add(new Suggestion(name, FilterItem.Type.FILE));
              }
              int dotIndex = name.lastIndexOf('.');
              if (dotIndex >= 0) { // Allow files like ".bashrc"
                String ext = name.substring(dotIndex);
                if (ext.toLowerCase().startsWith(text.toLowerCase())) {
                  suggestions.add(new Suggestion(ext, FilterItem.Type.EXTENSION));
                }
              }
            });

        autoCompletePopup.removeAll();

        if (suggestions.isEmpty()) {
          autoCompletePopup.setVisible(false);
          return;
        }

        List<Suggestion> sortedSuggestions = new ArrayList<>(suggestions);
        sortedSuggestions.sort(Comparator.comparing(s -> s.name, String.CASE_INSENSITIVE_ORDER));

        sortedSuggestions.stream()
            .limit(5)
            .forEach(suggestion -> {
              String html = String.format("<html>%s <font color='gray'>(%s)</font></html>",
                  suggestion.name,
                  suggestion.type.toString().toLowerCase());
              JMenuItem item = new JMenuItem(html);
              item.addActionListener(actionEvent -> {
                newFilterEntryField.setText(suggestion.name);
                typeSelector.setSelectedItem(suggestion.type);
                autoCompletePopup.setVisible(false);
              });
              autoCompletePopup.add(item);
            });

        if (autoCompletePopup.getComponentCount() > 0 && newFilterEntryField.isShowing()) {
          autoCompletePopup.show(newFilterEntryField, 0, newFilterEntryField.getHeight());
        }
        else {
          autoCompletePopup.setVisible(false);
        }
      }
    });

    newFilterEntryField.addFocusListener(new FocusAdapter() {
      @Override
      public void focusLost(FocusEvent e) {
        // Use a timer to allow a click on the popup to register before it disappears
        Timer hideTimer = new Timer(200, ae -> {
          Component opposite = e.getOppositeComponent();
          if (opposite == null || !SwingUtilities.isDescendingFrom(opposite, autoCompletePopup)) {
            autoCompletePopup.setVisible(false);
          }
        });
        hideTimer.setRepeats(false);
        hideTimer.start();
      }
    });


    JRadioButton keepRadio = new JRadioButton("keep", true);
    JRadioButton tossRadio = new JRadioButton("toss");
    ButtonGroup decisionGroup = new ButtonGroup();
    decisionGroup.add(keepRadio);
    decisionGroup.add(tossRadio);

    JButton addButton = new JButton(addIcon);
    addButton.setToolTipText("Add filter");
    addButton.setBorderPainted(false);
    addButton.setContentAreaFilled(false);
    addButton.setFocusPainted(false);
    addButton.setOpaque(false);
    addButton.setCursor(new Cursor(Cursor.HAND_CURSOR));

    JPanel controlsPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
    controlsPanel.add(typeSelector);
    controlsPanel.add(keepRadio);
    controlsPanel.add(tossRadio);
    controlsPanel.add(addButton);

    addPanel.add(newFilterEntryField, BorderLayout.CENTER);
    addPanel.add(controlsPanel, BorderLayout.EAST);

    Runnable addAction = () -> {
      String entryText = newFilterEntryField.getText().trim();
      if (entryText.isEmpty())
        return;

      FilterItem.Type selectedType = (FilterItem.Type) typeSelector.getSelectedItem();
      FilterItem.Decision selectedDecision = keepRadio.isSelected() ? FilterItem.Decision.KEEP : FilterItem.Decision.TOSS;

      FilterItem newItem = new FilterItem(entryText, selectedDecision, selectedType);

      if (!filterData.contains(newItem)) {
        int newIndex = filterData.size();
        filterData.add(newItem);
        filterTableModel.fireTableRowsInserted(newIndex, newIndex);
        newFilterEntryField.setText("");
        saveFilters(); // Persist change
        applyFiltersAndUpdateUI(); // Re-filter after addition
      }
      newFilterEntryField.requestFocusInWindow();
    };

    addButton.addActionListener(e -> addAction.run());
    newFilterEntryField.addActionListener(e -> addAction.run());

    panel.add(addPanel, BorderLayout.SOUTH);

    return panel;
  }

  private JPopupMenu createFilterContextMenu(final int row) {
    JPopupMenu menu = new JPopupMenu();
    FilterItem item = filterData.get(row);

    if (item.getType() == FilterItem.Type.FILE || item.getType() == FilterItem.Type.DIRECTORY) {
      JMenuItem openItem = new JMenuItem("Open Location");
      openItem.addActionListener(e -> {
        if (scanRootPath != null) {
          File fileToShow = scanRootPath.resolve(item.getEntry()).toFile();
          try {
            if (Desktop.isDesktopSupported()) {
              Desktop desktop = Desktop.getDesktop();
              if (fileToShow.exists()) {
                if (fileToShow.isDirectory()) {
                  desktop.open(fileToShow);
                }
                else {
                  // To highlight the file, we need to open its parent directory.
                  desktop.open(fileToShow.getParentFile());
                }
              }
              else {
                JOptionPane.showMessageDialog(this, "File or directory does not exist:\n" + fileToShow.getAbsolutePath(), "Error",
                    JOptionPane.ERROR_MESSAGE);
              }
            }
          }
          catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Could not open file location:\n" + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
          }
        }
      });
      menu.add(openItem);
    }

    JMenuItem deleteItem = new JMenuItem("Delete");
    deleteItem.addActionListener(e -> {
      SwingUtilities.invokeLater(() -> {
        filterData.remove(row);
        filterTableModel.fireTableRowsDeleted(row, row);
        saveFilters(); // Persist change
        applyFiltersAndUpdateUI();
      });
    });

    menu.add(deleteItem);

    return menu;
  }

  private void addComponentsToFrame() {
    JPanel contentPanel = new JPanel(new BorderLayout());
    contentPanel.add(tabbedPane, BorderLayout.CENTER);

    JPanel progressPanel = new JPanel(new BorderLayout());
    progressPanel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
    progressPanel.add(progressBar, BorderLayout.CENTER);
    contentPanel.add(progressPanel, BorderLayout.SOUTH);

    this.layeredPane = new JLayeredPane();
    layeredPane.add(contentPanel, JLayeredPane.DEFAULT_LAYER);

    layeredPane.add(saveButton, JLayeredPane.PALETTE_LAYER);
    layeredPane.add(clearFab, JLayeredPane.PALETTE_LAYER);

    layeredPane.addComponentListener(new ComponentAdapter() {
      @Override
      public void componentResized(ComponentEvent e) {
        contentPanel.setBounds(0, 0, layeredPane.getWidth(), layeredPane.getHeight());
        positionFabs(layeredPane);
      }
    });

    setContentPane(layeredPane);
  }

  private void positionFabs(JLayeredPane pane) {

    int fabSize = 56;
    int spacing = 15;

    int sideMargin = 25;
    int bottomMargin = sideMargin + fabVerticalOffset;

    if (saveButton != null) {

      int x = pane.getWidth() - fabSize - sideMargin;
      int y = pane.getHeight() - fabSize - bottomMargin;

      saveButton.setBounds(x, y, fabSize, fabSize);
    }

    if (clearFab != null) {

      int x = pane.getWidth() - fabSize - sideMargin;
      int y = pane.getHeight() - (fabSize * 2) - bottomMargin - spacing;

      clearFab.setBounds(x, y, fabSize, fabSize);
    }
  }

  private void setFabVerticalOffset(int offset) {
    this.fabVerticalOffset = offset;
    if (layeredPane != null) {
      positionFabs(layeredPane);
      layeredPane.repaint();
    }
  }

  /**
   * Animates the vertical position of the floating action buttons.
   *
   * @param targetOffset The final offset from the bottom to animate to.
   */
  private void animateFabVerticalOffset(int targetOffset) {
    if (fabAnimationTimer != null && fabAnimationTimer.isRunning()) {
      fabAnimationTimer.stop();
    }

    final int startOffset = this.fabVerticalOffset;
    if (startOffset == targetOffset) {
      return; // No animation needed
    }

    final int totalSteps = 20; // Animation duration control
    final int delay = 15; // Milliseconds between steps
    final AtomicInteger step = new AtomicInteger(0);

    fabAnimationTimer = new Timer(delay, e -> {
      int currentStep = step.incrementAndGet();
      if (currentStep >= totalSteps) {
        ((Timer) e.getSource()).stop();
        setFabVerticalOffset(targetOffset); // Snap to the final position
      }
      else {
        // Linear interpolation for smooth movement
        int newOffset = startOffset + ((targetOffset - startOffset) * currentStep) / totalSteps;
        setFabVerticalOffset(newOffset);
      }
    });
    fabAnimationTimer.start();
  }


  private void processAndAppendFiles(File[] files) {
    saveButton.setEnabled(false);
    clearFab.setEnabled(false);
    tabbedPane.setEnabled(false);
    progressBar.setValue(0);
    progressBar.setVisible(true);
    new AppendWorker(files).execute();
  }

  private void animateSourcePanel(int targetPosition) {
    final int startPosition = fileTabSplitPane.getDividerLocation();
    final int totalSteps = 20;
    final int delay = 15;
    final AtomicInteger step = new AtomicInteger(0);
    Timer timer = new Timer(delay, e -> {
      int currentStep = step.incrementAndGet();
      if (currentStep > totalSteps) {
        ((Timer) e.getSource()).stop();
        fileTabSplitPane.setDividerLocation(targetPosition);
      }
      else {
        int newPosition = startPosition + ((targetPosition - startPosition) * currentStep) / totalSteps;
        fileTabSplitPane.setDividerLocation(newPosition);
      }
    });
    timer.start();
  }

  private void regenerateDisplayArea() {
    StringBuilder visibleContent = new StringBuilder();
    for (FileContentBlock block : fileContentBlocks) {
      if (block.isSelected()) {
        visibleContent.append(block.getContent());
      }
    }
    displayArea.setText(visibleContent.toString());
    SwingUtilities.invokeLater(() -> displayArea.setCaretPosition(0));
    boolean hasContent = !fileContentBlocks.isEmpty();
    setSaveButtonActive(hasContent);

    clearFab.setEnabled(hasContent);
    if (hasContent && fileTabSplitPane.getDividerLocation() <= 1) {
      animateSourcePanel(80);
    }
    else if (!hasContent) {
      fileTabSplitPane.setDividerLocation(0);
    }
  }

  private void clearFileContent() {

    fileContentBlocks.clear();
    fileContentModel.clear();
    regenerateDisplayArea();
  }

  private void clearDirectoryContent() {

    dirPathField.setText("Select Directory");
    dirErrorLabel.setText("");
    scanRootPath = null;

    allFilesFromScan.clear();
    allDirsFromScan.clear();
    foundFilesModel.clear();
    directoryFilterModel.clear();

    updateCounters();
    saveButton.setEnabled(false);
    setSaveButtonActive(false);
    clearFab.setEnabled(false);
  }


  // --- LOGIC FOR "CONCATENATE BY DIRECTORY" TAB ---

  private void updateCounters() {
    long selectedDirs = 0;
    for (int i = 0; i < directoryFilterModel.getSize(); i++) {
      if (directoryFilterModel.getElementAt(i).isSelected()) {
        selectedDirs++;
      }
    }
    directoryCountLabel.setText(String.format("%d (selected) : %d (total directories)", selectedDirs, directoryFilterModel.getSize()));
    long selectedFiles = 0;
    for (int i = 0; i < foundFilesModel.getSize(); i++) {
      if (foundFilesModel.getElementAt(i).isSelected()) {
        selectedFiles++;
      }
    }
    fileCountLabel.setText(String.format("%d (selected) : %d (total files)", selectedFiles, foundFilesModel.getSize()));
  }

  private void selectDirectory() {
    fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
    fileChooser.setAcceptAllFileFilterUsed(false);
    if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
      dirPathField.setText(fileChooser.getSelectedFile().getAbsolutePath());
      scanDirectoryAction();
    }
  }

  /**
   * Displays a temporary message, either as a standard alert or a scrolling marquee.
   * Handles restoring the marquee after an alert is shown.
   *
   * @param message The message to display.
   * @param type    The type of message (ALERT or MARQUEE).
   */
  public void displayMessage(String message, MessageType type) {
    if (messageClearTimer != null && messageClearTimer.isRunning()) {
      messageClearTimer.stop();
    }

    if (type == MessageType.ALERT) {
      if (marqueeLabel != null) {
        marqueeLabel.stop();
      }
      dirErrorLabel.setText(message);
      messageCardLayout.show(messagePanel, "ALERT");

      messageClearTimer = new Timer(3000, e -> {
        if (activeMarqueeMessage != null) {
          displayMessage(activeMarqueeMessage, MessageType.MARQUEE);
        } else {
          messageCardLayout.show(messagePanel, "EMPTY");
        }
      });
      messageClearTimer.setRepeats(false);
      messageClearTimer.start();

    } else if (type == MessageType.MARQUEE) {
      activeMarqueeMessage = message;

      if (marqueeLabel == null) {
        marqueeLabel = new MarqueeLabel("");
        messagePanel.add(marqueeLabel, "MARQUEE");
      }
      marqueeLabel.setText("Scan complete: " + message);
      messageCardLayout.show(messagePanel, "MARQUEE");
      marqueeLabel.start();

      // This timer is now just to clear the activeMarqueeMessage state,
      // not to stop the animation itself unless another alert comes in.
      messageClearTimer = new Timer(15000, e -> {
        activeMarqueeMessage = null;
        if (marqueeLabel != null) {
          marqueeLabel.stop();
        }
        messageCardLayout.show(messagePanel, "EMPTY");
      });
      messageClearTimer.setRepeats(false);
      messageClearTimer.start();
    }
  }


  /**
   * Performs a shake animation on the main window.
   */
  public void shake() {
    final Point originalLocation = getLocation();
    final int shakeDistance = 5;
    final int shakeCount = 6;
    final int delay = 30;

    Timer shakeTimer = new Timer(delay, null);
    shakeTimer.addActionListener(new ActionListener() {
      private int count = 0;
      @Override
      public void actionPerformed(ActionEvent e) {
        if (count >= shakeCount) {
          setLocation(originalLocation);
          ((Timer) e.getSource()).stop();
          return;
        }
        int dx = (count % 2 == 0) ? shakeDistance : -shakeDistance;
        setLocation(originalLocation.x + dx, originalLocation.y);
        count++;
      }
    });
    shakeTimer.start();
  }


  private void scanDirectoryAction() {

    String dirPathStr = dirPathField.getText();
    File dir = new File(dirPathStr);

    if (!dir.isDirectory()) {
      displayMessage("Invalid directory path.", MessageType.ALERT);
      return;
    }

    if (rememberDirCheckbox.isSelected()) {
      prefs.put(LAST_DIR_KEY, dirPathStr);
    }
    else {
      prefs.remove(LAST_DIR_KEY);
    }

    dirErrorLabel.setText(" ");
    this.scanRootPath = dir.toPath();

    allFilesFromScan.clear();
    allDirsFromScan.clear();
    foundFilesModel.clear();
    directoryFilterModel.clear();
    updateCounters();

    tabbedPane.setEnabled(false);
    saveButton.setEnabled(false);
    clearFab.setEnabled(false);

    progressBar.setIndeterminate(true);
    progressBar.setString("Scanning directory...");
    progressBar.setVisible(true);

    new ScanWorker().execute();
  }

  private void applyFiltersAndUpdateUI() {
    if (scanRootPath == null) {
      return;
    }

    foundFilesModel.clear();
    directoryFilterModel.clear();

    List<FilterItem> currentFilters = new ArrayList<>(this.filterData);
    Set<File> visibleDirs = new HashSet<>();

    for (File file : allFilesFromScan) {
      if (filePassesFilters(file.toPath(), currentFilters)) {
        foundFilesModel.addElement(new FileListItem(file));
        if (file.getParentFile() != null) {
          visibleDirs.add(file.getParentFile());
        }
      }
    }

    Set<File> allVisibleAncestorDirs = new HashSet<>();
    for (File dir : visibleDirs) {
      File current = dir;
      while (current != null && !current.equals(scanRootPath.toFile().getParentFile())) {
        if (allDirsFromScan.contains(current)) {
          allVisibleAncestorDirs.add(current);
        }
        current = current.getParentFile();
      }
    }

    List<DirectoryListItem> visibleDirItems = allVisibleAncestorDirs.stream()
        .map(dir -> new DirectoryListItem(dir, scanRootPath))
        .sorted(Comparator.comparing(DirectoryListItem::toString))
        .toList();

    visibleDirItems.forEach(directoryFilterModel::addElement);

    restoreSelections(); // Restore selections after lists are populated

    updateCounters();

    boolean hasResults = scanRootPath != null;
    setSaveButtonActive(!foundFilesModel.isEmpty());
    clearFab.setEnabled(hasResults);
  }

  /**
   * FIX: This method contains the corrected filter logic. It now uses switch statements to ensure
   * each filter is evaluated only against its specific type, and the overall logic flow is clarified.
   */
  private boolean filePassesFilters(Path path, List<FilterItem> filters) {
    String lowerCaseFileName = path.getFileName().toString().toLowerCase();
    Path parentDir = path.getParent();

    // TOSS filters take precedence. If any TOSS filter matches, the file is rejected.
    for (FilterItem filter : filters) {
      if (filter.getDecision() == FilterItem.Decision.TOSS) {
        String pattern = filter.getEntry().toLowerCase();
        switch (filter.getType()) {
          case DIRECTORY:
            if (parentDir != null && parentDir.startsWith(scanRootPath.resolve(pattern).normalize())) {
              return false; // Tossed
            }
            break;
          case FILE:
            if (lowerCaseFileName.equals(pattern)) {
              return false; // Tossed
            }
            break;
          case EXTENSION:
            if (lowerCaseFileName.endsWith(pattern)) {
              return false; // Tossed
            }
            break;
        }
      }
    }

    // Check KEEP filters only if the file was not tossed.
    List<FilterItem> keepFilters = filters.stream()
        .filter(f -> f.getDecision() == FilterItem.Decision.KEEP)
        .toList();

    // If there are no KEEP filters, the file is accepted by default (since it wasn't tossed).
    if (keepFilters.isEmpty()) {
      return true;
    }

    // If there ARE KEEP filters, at least one must match for the file to be accepted.
    for (FilterItem filter : keepFilters) {
      String pattern = filter.getEntry().toLowerCase();
      switch (filter.getType()) {
        case DIRECTORY:
          if (parentDir != null && parentDir.startsWith(scanRootPath.resolve(pattern).normalize())) {
            return true; // Kept
          }
          break;
        case FILE:
          if (lowerCaseFileName.equals(pattern)) {
            return true; // Kept
          }
          break;
        case EXTENSION:
          if (lowerCaseFileName.endsWith(pattern)) {
            return true; // Kept
          }
          break;
      }
    }

    // The file was not tossed, but it failed to match any of the required KEEP filters.
    return false;
  }


  // --- GENERAL/SHARED LOGIC ---

  private void setupDragAndDrop(JComponent component) {
    component.setTransferHandler(new FileDragDropHandler(this));
  }

  private void selectFiles() {
    fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
    fileChooser.setMultiSelectionEnabled(true);
    fileChooser.setFileFilter(new FileNameExtensionFilter("Java, XML, Gradle", "java", "xml", "gradle"));
    fileChooser.setAcceptAllFileFilterUsed(true);
    if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
      File[] selectedFiles = fileChooser.getSelectedFiles();
      if (selectedFiles != null && selectedFiles.length > 0) {
        processAndAppendFiles(selectedFiles);
      }
    }
  }

  private void saveConcatenatedFile() {

    int selectedTab = tabbedPane.getSelectedIndex();
    if (selectedTab == 0) {

      if (displayArea.getText().isEmpty() || fileContentBlocks.stream().noneMatch(FileContentBlock::isSelected)) {
        JOptionPane.showMessageDialog(this, "No content has been selected to save.", "Save Error", JOptionPane.WARNING_MESSAGE);
        return;
      }

      fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
      if (fileChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {

        File fileToSave = fileChooser.getSelectedFile();
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(fileToSave))) {

          writer.write(displayArea.getText());
          JOptionPane.showMessageDialog(this, "File saved successfully!", "Save Successful", JOptionPane.INFORMATION_MESSAGE);

        }
        catch (IOException ex) {
          JOptionPane.showMessageDialog(this, "Error saving file: \n" + ex.getMessage(), "Save Error", JOptionPane.ERROR_MESSAGE);
        }
      }
    }
    else if (selectedTab == 1) {
      List<File> filesToProcess = new ArrayList<>();
      for (int i = 0; i < foundFilesModel.getSize(); i++) {
        FileListItem item = foundFilesModel.getElementAt(i);
        if (item.isSelected())
          filesToProcess.add(item.getFile());
      }
      if (filesToProcess.isEmpty()) {
        JOptionPane.showMessageDialog(this, "No files are selected in the list to concatenate.", "Information",
            JOptionPane.INFORMATION_MESSAGE);
        return;
      }
      fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
      String userHome = System.getProperty("user.home");
      File desktopDir = new File(userHome, "//Desktop");

      fileChooser.setCurrentDirectory(desktopDir);

      if (fileChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
        File fileToSave = forceTxtExtension(fileChooser.getSelectedFile());
        saveButton.setEnabled(false);
        tabbedPane.setEnabled(false);
        progressBar.setValue(0);
        progressBar.setVisible(true);
        new SaveWorker(filesToProcess.toArray(new File[0]), fileToSave).execute();
      }
    }
  }
  public static File forceTxtExtension(File originalFile) {

    if (originalFile == null) return null;

    String name = originalFile.getName();
    int dotIndex = name.lastIndexOf('.');
    String baseName = dotIndex > 0 ? name.substring(0, dotIndex) : name;

    return new File(originalFile.getParent(), baseName + ".txt");
  }

  /**
   * Plays a sound from a resource file in a new thread to avoid blocking the UI.
   *
   * @param resourcePath The path to the sound file within the resources.
   */
  private void playSound(final String resourcePath) {
    new Thread(() -> {

      ClassLoader classLoader = getClass().getClassLoader();

      try (InputStream inputStream = classLoader.getResourceAsStream(resourcePath)) {
        if (inputStream != null) {
          new Player(new BufferedInputStream(inputStream)).play();
        } else {
          System.err.println("Could not find resource: " + resourcePath);
        }
      }
      catch (Exception e) {
        System.err.println("Error loading MP3: " + e.getMessage());
      }
    }).start();
  }

  private String buildConcatenatedString(File[] files, java.util.function.Consumer<Integer> progressPublisher) throws IOException {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < files.length; i++) {
      File file = files[i];
      if (file.isFile()) {
        sb.append(buildFileContentBlock(file));
      }
      progressPublisher.accept((int) (((i + 1.0) / files.length) * 100));
    }
    return sb.toString();
  }

  private String buildFileContentBlock(File file) throws IOException {
    StringBuilder sb = new StringBuilder();
    String filePath = file.getAbsolutePath();
    int borderWidth = Math.max(80, filePath.length() + 6);
    char[] borderChars = new char[borderWidth];
    java.util.Arrays.fill(borderChars, '#');
    String borderLine = new String(borderChars);
    sb.append(borderLine).append("\n");
    sb.append(String.format("#  %-" + (borderWidth - 4) + "s #", filePath)).append("\n");
    sb.append(borderLine).append("\n\n");
    try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
      String line;
      while ((line = reader.readLine()) != null) {
        sb.append(line).append("\n");
      }
    }
    sb.append("\n\n");
    return sb.toString();
  }

  private record ScanResult(List<File> allFiles, Set<File> allDirs, String scanPath) {
  }

  private class ScanWorker extends SwingWorker<ScanResult, Void> {
    @Override
    protected ScanResult doInBackground() throws Exception {
      List<File> files = new ArrayList<>();
      Set<File> dirs = new HashSet<>();
      try (Stream<Path> stream = Files.walk(scanRootPath)) {
        stream.forEach(path -> {
          File file = path.toFile();
          if (file.isDirectory()) {
            dirs.add(file);
          }
          else {
            files.add(file);
          }
        });
      }
      return new ScanResult(files, dirs, scanRootPath.toString());
    }

    @Override
    protected void done() {
      try {
        ScanResult result = get();
        allFilesFromScan = result.allFiles;
        allDirsFromScan = result.allDirs;
        applyFiltersAndUpdateUI(); // Apply initial filters
        displayMessage(result.scanPath, MessageType.MARQUEE);

        if (foundFilesModel.isEmpty()) {
          JOptionPane.showMessageDialog(JavaFileConcatenator.this, "No matching files found after applying filters.", "Scan Complete",
              JOptionPane.INFORMATION_MESSAGE);
        }
      }
      catch (InterruptedException | ExecutionException e) {
        JOptionPane.showMessageDialog(JavaFileConcatenator.this, "An error occurred during scan:\n" + e.getCause().getMessage(), "Error",
            JOptionPane.ERROR_MESSAGE);
      }
      finally {
        progressBar.setVisible(false);
        progressBar.setIndeterminate(false);
        progressBar.setString("");
        tabbedPane.setEnabled(true);
      }
    }
  }


  private class AppendWorker extends SwingWorker<List<FileContentBlock>, Integer> {
    private final File[] files;

    public AppendWorker(File[] files) {
      this.files = files;
    }

    @Override
    protected List<FileContentBlock> doInBackground() throws Exception {
      List<FileContentBlock> newBlocks = new ArrayList<>();
      for (int i = 0; i < files.length; i++) {
        File file = files[i];
        String content = buildFileContentBlock(file);
        newBlocks.add(new FileContentBlock(file, content));
        publish((int) (((i + 1.0) / files.length) * 100));
      }
      return newBlocks;
    }

    @Override
    protected void process(List<Integer> chunks) {
      progressBar.setValue(chunks.get(chunks.size() - 1));
    }

    @Override
    protected void done() {
      try {
        List<FileContentBlock> newBlocks = get();
        fileContentBlocks.addAll(newBlocks);
        newBlocks.forEach(fileContentModel::addElement);
        regenerateDisplayArea();
      }
      catch (InterruptedException | ExecutionException e) {
        JOptionPane.showMessageDialog(JavaFileConcatenator.this, "An error occurred while processing files:\n" + e.getCause().getMessage(),
            "Error", JOptionPane.ERROR_MESSAGE);
      }
      finally {
        progressBar.setVisible(false);
        tabbedPane.setEnabled(true);
      }
    }
  }

  private class SaveWorker extends SwingWorker<Void, Integer> {
    private final File[] files;
    private final File destination;

    public SaveWorker(File[] files, File destination) {
      this.files = files;
      this.destination = destination;
    }

    @Override
    protected Void doInBackground() throws Exception {
      String result = buildConcatenatedString(files, this::publish);
      try (BufferedWriter writer = new BufferedWriter(new FileWriter(destination))) {
        writer.write(result);
      }
      return null;
    }

    @Override
    protected void process(List<Integer> chunks) {
      progressBar.setValue(chunks.get(chunks.size() - 1));
    }

    @Override
    protected void done() {
      try {
        get();
        playSound(WHISTLE_SFX);

        JOptionPane.showMessageDialog(JavaFileConcatenator.this, "File saved successfully!", "Save Successful",
            JOptionPane.INFORMATION_MESSAGE);

      }
      catch (InterruptedException | ExecutionException e) {
        JOptionPane.showMessageDialog(JavaFileConcatenator.this, "An error occurred while saving:\n" + e.getCause().getMessage(), "Error",
            JOptionPane.ERROR_MESSAGE);
      }
      finally {
        progressBar.setVisible(false);
        tabbedPane.setEnabled(true);

        setSaveButtonActive(true);
      }
    }
  }

  // --- Helper and Inner Classes ---


  private record Suggestion(String name, FilterItem.Type type) {

    @Override
      public boolean equals(Object o) {
        if (this == o)
          return true;
        if (o == null || getClass() != o.getClass())
          return false;
        Suggestion that = (Suggestion) o;
        return name.equals(that.name) && type == that.type;
      }

  }

  private static class ImageOverlayTextArea extends JTextArea {
    @Serial
    private static final long serialVersionUID = 630747744194087440L;
    private final Image image;

    public ImageOverlayTextArea(Image image) {
      this.image = image;
    }

    @Override
    protected void paintComponent(Graphics g) {
      super.paintComponent(g);
      if (image != null && this.getDocument().getLength() == 0) {
        int x = (this.getWidth() - image.getWidth(null)) / 2;
        int y = (this.getHeight() - image.getHeight(null)) / 2;
        g.drawImage(image, x, y, this);
      }
    }
  }

  private static class FloatingActionButton extends JButton {
    @Serial
    private static final long serialVersionUID = -2311031788954001002L;

    public FloatingActionButton(Icon icon) {
      super(icon);
      setBackground(UIManager.getColor("Actions.Blue"));
      setOpaque(false);
      setBorder(null);
      setContentAreaFilled(false);
      setFocusPainted(false);
      setPreferredSize(new Dimension(56, 56));
      setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    @Override
    protected void paintComponent(Graphics g) {
      Graphics2D g2 = (Graphics2D) g.create();
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

      g2.setColor(new Color(0, 0, 0, 70));
      g2.fillOval(4, 6, getWidth() - 8, getHeight() - 8);

      if (getModel().isArmed()) {
        g2.setColor(getBackground().darker());
      }
      else if (getModel().isRollover()) {
        g2.setColor(getBackground().brighter());
      }
      else {
        g2.setColor(getBackground());
      }
      g2.fillOval(2, 2, getWidth() - 4, getHeight() - 4);

      g2.dispose();

      super.paintComponent(g);
    }
  }


  private class DirectoryDropHandler extends TransferHandler {
    @Serial
    private static final long serialVersionUID = 1L;

    @Override
    public boolean canImport(TransferSupport support) {
      if (!support.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
        return false;
      }
      try {

        @SuppressWarnings("unchecked")
        List<File> files = (List<File>) support.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);

        if (files.isEmpty()) {
          throw new IOException("Directory was empty -> Nothing to do");
        }
        else if (files.iterator().next().isDirectory()) {

          Path directory = files.iterator().next().toPath();
          try (DirectoryStream<Path> dirStream = Files.newDirectoryStream(directory)) {

            return dirStream.iterator().hasNext();
          }
        }

        return files.size() == 1 && files.get(0).isDirectory();
      }
      catch (UnsupportedFlavorException | IOException e) {
        return false;
      }
    }

    @Override
    public boolean importData(TransferSupport support) {
      if (!canImport(support)) {
        return false;
      }
      try {
        @SuppressWarnings("unchecked")
        List<File> files = (List<File>) support.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
        File dir = files.get(0);
        SwingUtilities.invokeLater(() -> {
          dirPathField.setText(dir.getAbsolutePath());
          scanDirectoryAction();
        });
        return true;
      }
      catch (UnsupportedFlavorException | IOException e) {
        return false;
      }
    }
  }

  private static class FileDragDropHandler extends TransferHandler {
    @Serial
    private static final long serialVersionUID = -8670182305116497884L;
    private final JavaFileConcatenator parentFrame;

    public FileDragDropHandler(JavaFileConcatenator frame) {
      this.parentFrame = frame;
    }

    @Override
    public boolean canImport(TransferSupport support) {
      return support.isDataFlavorSupported(DataFlavor.javaFileListFlavor);
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean importData(TransferSupport support) {
      if (!canImport(support))
        return false;
      try {
        List<File> files = (List<File>) support.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
        parentFrame.processAndAppendFiles(files.toArray(new File[0]));
        return true;
      }
      catch (UnsupportedFlavorException | IOException e) {
        JOptionPane.showMessageDialog(parentFrame, "Error processing dropped files: \n" + e.getMessage(), "Drop Error",
            JOptionPane.ERROR_MESSAGE);
        return false;
      }
    }
  }

  private static class FileContentBlock {
    private final File sourceFile;
    private final String content;
    private boolean isSelected = true;

    public FileContentBlock(File sourceFile, String content) {
      this.sourceFile = sourceFile;
      this.content = content;
    }

    public String getContent() {
      return content;
    }

    public boolean isSelected() {
      return isSelected;
    }

    public void setSelected(boolean selected) {
      isSelected = selected;
    }

    @Override
    public String toString() {
      return sourceFile.getName();
    }
  }

  private static class FileContentListCellRenderer extends JCheckBox implements ListCellRenderer<FileContentBlock> {
    @Serial
    private static final long serialVersionUID = 8100187514613678607L;

    @Override
    public Component getListCellRendererComponent(JList<? extends FileContentBlock> list, FileContentBlock value, int index, boolean isSelected, boolean cellHasFocus) {
      setEnabled(list.isEnabled());
      setSelected(value.isSelected());
      setFont(list.getFont());
      setText(value.toString());
      setToolTipText(value.sourceFile.getAbsolutePath());
      setBackground(list.getBackground());
      setForeground(list.getForeground());
      setBorder(cellHasFocus ? UIManager.getBorder("List.focusCellHighlightBorder") : noFocusBorder);
      return this;
    }
  }

  private static class FileListItem {
    private final File file;
    private boolean isSelected = true;

    public FileListItem(File file) {
      this.file = file;
    }

    public File getFile() {
      return file;
    }

    public boolean isSelected() {
      return isSelected;
    }

    public void setSelected(boolean selected) {
      isSelected = selected;
    }

    @Override
    public String toString() {
      return file.getAbsolutePath();
    }
  }

  private static class FileListCellRenderer extends JCheckBox implements ListCellRenderer<FileListItem> {
    @Serial
    private static final long serialVersionUID = -3641025085261111633L;

    @Override
    public Component getListCellRendererComponent(JList<? extends FileListItem> list, FileListItem value, int index, boolean isSelected, boolean cellHasFocus) {
      setEnabled(list.isEnabled());
      setSelected(value.isSelected());
      setFont(list.getFont());
      setText(value.toString());
      setBackground(list.getBackground());
      setForeground(list.getForeground());
      setBorder(cellHasFocus ? UIManager.getBorder("List.focusCellHighlightBorder") : noFocusBorder);
      return this;
    }
  }

  private static class DirectoryListItem {
    private final File directory;
    private final Path rootPath;
    private boolean isSelected = true;

    public DirectoryListItem(File directory, Path rootPath) {
      this.directory = directory;
      this.rootPath = rootPath;
    }

    public File getDirectory() {
      return directory;
    }

    public boolean isSelected() {
      return isSelected;
    }

    public void setSelected(boolean selected) {
      isSelected = selected;
    }

    @Override
    public String toString() {
      Path relativePath = rootPath.relativize(directory.toPath());
      String pathString = relativePath.toString();
      return pathString.isEmpty() ? "." : pathString;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o)
        return true;
      if (o == null || getClass() != o.getClass())
        return false;
      return directory.equals(((DirectoryListItem) o).directory);
    }

    @Override
    public int hashCode() {
      return directory.hashCode();
    }
  }

  private static class DirectoryListCellRenderer extends JCheckBox implements ListCellRenderer<DirectoryListItem> {
    @Serial
    private static final long serialVersionUID = -8395205255644223240L;

    @Override
    public Component getListCellRendererComponent(JList<? extends DirectoryListItem> list, DirectoryListItem value, int index, boolean isSelected, boolean cellHasFocus) {
      setEnabled(list.isEnabled());
      setSelected(value.isSelected());
      setFont(list.getFont());
      setText(value.toString());
      setToolTipText(value.getDirectory().getAbsolutePath());
      setBackground(list.getBackground());
      setForeground(list.getForeground());
      setBorder(cellHasFocus ? UIManager.getBorder("List.focusCellHighlightBorder") : noFocusBorder);
      return this;
    }
  }

  private static class FilterItem {
    enum Decision {
      KEEP, TOSS;

      @Override
      public String toString() {
        return this.name().toLowerCase();
      }
    }

    enum Type {
      FILE, DIRECTORY, EXTENSION;

      @Override
      public String toString() {
        // Capitalize first letter for display
        return this.name().substring(0, 1).toUpperCase() + this.name().substring(1).toLowerCase();
      }
    }

    String entry;
    Decision decision;
    Type type;

    public FilterItem(String entry, Decision decision, Type type) {
      this.entry = entry;
      this.decision = decision;
      this.type = type;
    }

    public String getEntry() {
      return entry;
    }

    public Decision getDecision() {
      return decision;
    }

    public Type getType() {
      return type;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o)
        return true;
      if (o == null || getClass() != o.getClass())
        return false;
      FilterItem that = (FilterItem) o;
      return entry.equals(that.entry) && decision == that.decision && type == that.type;
    }

    @Override
    public int hashCode() {
      return Objects.hash(entry, decision, type);
    }

    // --- JSON Serialization for Persistence ---
    public static String toJson(List<FilterItem> items) {
      return items.stream()
          .map(item -> String.format("{\"entry\":\"%s\",\"decision\":\"%s\",\"type\":\"%s\"}",
              item.entry.replace("\\", "\\\\").replace("\"", "\\\""),
              item.decision.name(),
              item.type.name()))
          .collect(Collectors.joining(",", "[", "]"));
    }

    // --- JSON Deserialization for Persistence ---
    public static List<FilterItem> fromJson(String json) {
      List<FilterItem> items = new ArrayList<>();
      if (json == null || json.length() <= 2) {
        return items;
      }
      // Simple parsing, not a robust JSON parser
      String content = json.substring(1, json.length() - 1).trim();
      if (content.isEmpty()) {
        return items;
      }
      String[] objects = content.replace("},{", "}\u0000{").split("\u0000");
      for (String objStr : objects) {
        try {
          String entry = objStr.split("\"entry\":\"")[1].split("\"")[0]
              .replace("\\\"", "\"").replace("\\\\", "\\");
          String decisionStr = objStr.split("\"decision\":\"")[1].split("\"")[0];
          String typeStr = objStr.split("\"type\":\"")[1].split("\"")[0];
          items.add(new FilterItem(entry, Decision.valueOf(decisionStr), Type.valueOf(typeStr)));
        }
        catch (Exception e) {
          System.err.println("Failed to parse filter item from JSON: " + objStr);
        }
      }
      return items;
    }
  }

  private static class FilterTableModel extends AbstractTableModel {
    private final String[] columnNames = {"Entry", "Decision", "Type", ""};
    private final List<FilterItem> filterList;

    public FilterTableModel(List<FilterItem> filterList) {
      this.filterList = filterList;
    }

    @Override
    public boolean isCellEditable(int row, int col) {
      // "Decision" and "Type" columns are editable.
      return col == 1 || col == 2;
    }

    @Override
    public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
      FilterItem item = filterList.get(rowIndex);
      try {
        switch (columnIndex) {
          case 1: // Decision column
            item.decision = (FilterItem.Decision) aValue;
            break;
          case 2: // Type column
            item.type = (FilterItem.Type) aValue;
            break;
        }
        // Notify listeners that the cell has been updated.
        fireTableCellUpdated(rowIndex, columnIndex);
      }
      catch (Exception e) {
        System.err.println("Invalid value set in table: " + aValue);
      }
    }

    @Override
    public int getColumnCount() {
      return columnNames.length;
    }

    @Override
    public int getRowCount() {
      return filterList.size();
    }

    @Override
    public String getColumnName(int col) {
      return columnNames[col];
    }

    @Override
    public Object getValueAt(int row, int col) {
      FilterItem item = filterList.get(row);
      return switch (col) {
        case 0 -> item.getEntry();
        case 1 -> item.getDecision();
        case 2 -> item.getType();
        case 3 -> ""; // Button column doesn't need text value
        default -> null;
      };
    }
  }

  private static class ButtonRenderer extends JButton implements TableCellRenderer {

    @Serial
    private static final long serialVersionUID = -938340426203202030L;

    public ButtonRenderer(ImageIcon icon) {
      super(icon);
      setOpaque(true);
      setBorderPainted(false);
      setContentAreaFilled(false);
      setBorder(BorderFactory.createEmptyBorder(2, 5, 2, 5));
    }

    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
      setBackground(isSelected ? table.getSelectionBackground() : table.getBackground());
      return this;
    }
  }

  private static class PaddedCellRenderer extends DefaultTableCellRenderer {

    @Serial
    private static final long serialVersionUID = 2247471368110537489L;

    public PaddedCellRenderer() {
      super();
      setBorder(BorderFactory.createEmptyBorder(2, 5, 2, 5));
    }
  }

  /**
   * Renders an icon in the "Type" column of the filter table instead of text.
   */
  private class TypeIconRenderer extends DefaultTableCellRenderer {
    @Serial
    private static final long serialVersionUID = 3508618871574018573L;

    public TypeIconRenderer() {
      super();
      setHorizontalAlignment(SwingConstants.CENTER);
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
      // Let the superclass handle selection colors etc.
      super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

      setText(""); // Always clear text
      if (value instanceof FilterItem.Type type) {
        switch (type) {
          case FILE -> {
            setIcon(fileIcon);
            setToolTipText("File");
          }
          case DIRECTORY -> {
            setIcon(directoryIcon);
            setToolTipText("Directory");
          }
          case EXTENSION -> {
            setIcon(extensionIcon);
            setToolTipText("Extension");
          }
          default -> {
            setIcon(null);
            setToolTipText(null);
          }
        }
      }
      else {
        setIcon(null);
        setToolTipText(null);
      }
      return this;
    }
  }

  /**
   * Renders an icon and text in the JComboBox editor for the "Type" column.
   */
  private class TypeIconListCellRenderer extends DefaultListCellRenderer {
    @Serial
    private static final long serialVersionUID = -7127305752493985821L;

    @Override
    public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
      // Let the superclass handle text, selection colors etc.
      JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);

      if (value instanceof FilterItem.Type type) {
        switch (type) {
          case FILE -> label.setIcon(fileIcon);
          case DIRECTORY -> label.setIcon(directoryIcon);
          case EXTENSION -> label.setIcon(extensionIcon);
        }
      }
      return label;
    }
  }

  /**
   * Provides bash-style tab completion and up/down arrow history for a JTextField for file paths.
   */
  private static class BashStyleTabCompleter {
    private final JTextField textField;
    private final JavaFileConcatenator mainFrame;
    private List<String> history;
    private int historyIndex;
    private String basePathForHistory;
    private boolean isCyclingHistory = false; // Flag to prevent listener recursion

    public BashStyleTabCompleter(JTextField textField, JavaFileConcatenator mainFrame) {
      this.textField = textField;
      this.mainFrame = mainFrame;
      this.history = new ArrayList<>();
      this.historyIndex = -1;
      this.basePathForHistory = "";

      // --- Key Bindings ---
      InputMap inputMap = textField.getInputMap(JComponent.WHEN_FOCUSED);
      ActionMap actionMap = textField.getActionMap();

      // Tab for completion
      inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_TAB, 0), "doTabCompletion");
      actionMap.put("doTabCompletion", new AbstractAction() {
        @Override
        public void actionPerformed(ActionEvent e) {
          complete();
        }
      });

      // Up arrow for previous history
      inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0), "historyUp");
      actionMap.put("historyUp", new AbstractAction() {
        @Override
        public void actionPerformed(ActionEvent e) {
          cycleHistory(-1);
        }
      });

      // Down arrow for next history
      inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0), "historyDown");
      actionMap.put("historyDown", new AbstractAction() {
        @Override
        public void actionPerformed(ActionEvent e) {
          cycleHistory(1);
        }
      });

      // --- Prevent Tab from changing focus ---
      Set<AWTKeyStroke> forwardKeys = new HashSet<>(textField.getFocusTraversalKeys(KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS));
      forwardKeys.remove(KeyStroke.getKeyStroke(KeyEvent.VK_TAB, 0));
      textField.setFocusTraversalKeys(KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS, forwardKeys);

      // --- Reset history on manual typing ---
      textField.getDocument().addDocumentListener(new DocumentListener() {
        @Override public void insertUpdate(DocumentEvent e) { if (!isCyclingHistory) resetHistory(); }
        @Override public void removeUpdate(DocumentEvent e) { if (!isCyclingHistory) resetHistory(); }
        @Override public void changedUpdate(DocumentEvent e) { if (!isCyclingHistory) resetHistory(); }
      });
    }

    private void resetHistory() {
      history.clear();
      historyIndex = -1;
    }

    private String getBasePath(String text) {
      int lastSeparatorIndex = text.lastIndexOf(File.separator);
      lastSeparatorIndex = Math.max(lastSeparatorIndex, text.lastIndexOf('/'));
      return (lastSeparatorIndex >= 0) ? text.substring(0, lastSeparatorIndex + 1) : "";
    }

    private void cycleHistory(int direction) {
      String currentText = textField.getText();
      String currentBasePath = getBasePath(currentText);

      // If the base path has changed or history is empty, populate it
      if (history.isEmpty() || !currentBasePath.equals(basePathForHistory)) {
        basePathForHistory = currentBasePath;
        File dir = new File(basePathForHistory.isEmpty() ? "." : basePathForHistory);
        if (!dir.isAbsolute()) {
          dir = new File(dir.getAbsolutePath());
        }
        if (dir.isDirectory()) {
          String[] contents = dir.list();
          if (contents != null) {
            history = new ArrayList<>(Arrays.asList(contents));
            history.sort(String.CASE_INSENSITIVE_ORDER);
          } else {
            history.clear();
          }
        } else {
          history.clear();
        }
      }

      if (history.isEmpty()) {
        return;
      }

      historyIndex += direction;

      // Wrap around the index
      if (historyIndex < 0) {
        historyIndex = history.size() - 1;
      } else if (historyIndex >= history.size()) {
        historyIndex = 0;
      }

      String newName = history.get(historyIndex);

      isCyclingHistory = true;
      textField.setText(new File(basePathForHistory, newName).getPath());
      isCyclingHistory = false;
    }

    private void complete() {
      resetHistory(); // Reset history on tab completion
      String text = textField.getText();
      String basePath = getBasePath(text);
      String toComplete = text.substring(basePath.length());

      File dir = new File(basePath.isEmpty() ? "." : basePath);

      if (!dir.isAbsolute()) {
        dir = new File(dir.getAbsolutePath());
      }

      if (!dir.isDirectory()) {
        mainFrame.shake();
        return;
      }

      String[] allNames = dir.list();
      if (allNames == null) {
        mainFrame.shake();
        return;
      }

      List<String> matches = new ArrayList<>();
      for (String name : allNames) {
        if (name.toLowerCase().startsWith(toComplete.toLowerCase())) {
          matches.add(name);
        }
      }

      if (matches.isEmpty()) {
        mainFrame.shake();
        return;
      }

      if (matches.size() == 1) {
        String match = matches.get(0);
        File completedFile = new File(dir, match);
        String newText = new File(basePath, match).getPath();
        if (completedFile.isDirectory()) {
          newText += File.separator;
        }
        textField.setText(newText);
      } else {
        String lcp = findLongestCommonPrefix(matches);
        if (lcp.length() > toComplete.length()) {
          textField.setText(new File(basePath, lcp).getPath());
        }
      }
    }

    private String findLongestCommonPrefix(List<String> strings) {
      if (strings == null || strings.isEmpty()) {
        return "";
      }
      String first = strings.get(0);
      int commonPrefixLength = first.length();
      for (int i = 1; i < strings.size(); i++) {
        String current = strings.get(i);
        int j = 0;
        while (j < commonPrefixLength && j < current.length() &&
            Character.toLowerCase(first.charAt(j)) == Character.toLowerCase(current.charAt(j))) {
          j++;
        }
        commonPrefixLength = j;
      }
      return first.substring(0, commonPrefixLength);
    }
  }

  /**
   * A label that animates its text in a continuous loop like a marquee.
   */
  private static class MarqueeLabel extends JLabel {
    private int x;
    private final Timer timer;
    private int textWidth;
    private static final int GAP = 50; // Gap between repeated text

    public MarqueeLabel(String text) {
      super(text);
      this.x = 0;
      this.timer = new Timer(40, e -> {
        x--;
        // If the first copy of the text has completely scrolled off screen
        if (x <= -textWidth - GAP) {
          x += textWidth + GAP; // Reset position to create the loop
        }
        repaint();
      });
    }

    @Override
    public void setText(String text) {
      super.setText(text);
      updateTextWidth();
      x = 0;
    }

    @Override
    public void setFont(Font font) {
      super.setFont(font);
      updateTextWidth();
    }

    private void updateTextWidth() {
      if (getFont() != null) {
        this.textWidth = getFontMetrics(getFont()).stringWidth(getText());
      }
    }

    @Override
    protected void paintComponent(Graphics g) {
      // Set clipping to prevent text from drawing outside the label's bounds
      g.setClip(0, 0, getWidth(), getHeight());

      if (textWidth == 0) {
        updateTextWidth();
      }

      // Ensure text is not drawn if it's too small to be seen or not set
      if (textWidth == 0 || getWidth() == 0) {
        return;
      }

      int y = getBaseline(getWidth(), getHeight());

      // Draw the text twice to create the seamless loop effect
      g.drawString(getText(), x, y);
      g.drawString(getText(), x + textWidth + GAP, y);
    }

    // Helper to vertically center the text
    @Override
    public int getBaseline(int width, int height) {
      FontMetrics fm = getFontMetrics(getFont());
      return ((height - fm.getHeight()) / 2) + fm.getAscent();
    }

    public void start() {
      x = 0;
      timer.start();
    }

    public void stop() {
      timer.stop();
    }
  }

  public static void main(String[] args) {

    SwingUtilities.invokeLater(() -> {
      FlatDarkLaf.setup();
      JavaFileConcatenator frame = new JavaFileConcatenator();
      frame.setVisible(true);
    });
  }
}
