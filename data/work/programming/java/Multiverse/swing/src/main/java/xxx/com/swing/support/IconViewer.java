package xxx.com.swing.support;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.formdev.flatlaf.FlatLightLaf;
import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatIntelliJLaf;
import com.formdev.flatlaf.FlatDarculaLaf;
import com.formdev.flatlaf.themes.FlatMacLightLaf;
import com.formdev.flatlaf.themes.FlatMacDarkLaf;

/**
 * A Swing application that displays icons from the UIManager's defaults for selected Look and Feel.
 * Includes a dropdown to select the Look and Feel and dynamically update the icon list without changing the app's visual L&F.
 * Icons are pre-collected and converted to safe ImageIcons to prevent painting errors.
 * Now includes FlatLaf themes.
 * Uses context-specific dummy components for painting icons to handle type-specific requirements.
 */
public class IconViewer extends JFrame {

  private final JPanel mainPanel; // The panel that holds the icon list
  private Map<String, List<IconEntry>> allIcons; // Stores icons for all L&Fs

  public IconViewer() {
    super("Swing UI Icon Viewer");

    // 1. Set up the main frame with a BorderLayout
    setLayout(new BorderLayout(0, 5));

    // 2. Collect all icons for each L&F without visually changing the app
    allIcons = new HashMap<>();
    collectAllIcons();

    // 3. Create the top panel for the Look and Feel selector
    JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
    topPanel.add(new JLabel("Look and Feel:"));

    List<String> lafNames = new ArrayList<>(allIcons.keySet());
    lafNames.sort(String::compareToIgnoreCase);
    JComboBox<String> lafComboBox = new JComboBox<>(lafNames.toArray(new String[0]));
    topPanel.add(lafComboBox);
    add(topPanel, BorderLayout.NORTH);

    // 4. Create the main panel for the icons and place it in a scroll pane
    mainPanel = new JPanel();
    mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
    mainPanel.setBackground(Color.WHITE);

    JPanel wrapperPanel = new JPanel(new BorderLayout());
    wrapperPanel.setBackground(Color.WHITE);
    wrapperPanel.add(mainPanel, BorderLayout.NORTH);

    JScrollPane scrollPane = new JScrollPane(wrapperPanel);
    scrollPane.getVerticalScrollBar().setUnitIncrement(16);
    add(scrollPane, BorderLayout.CENTER);

    // 5. Add an ActionListener to the JComboBox to handle selection changes
    lafComboBox.addActionListener(e -> {
      String selectedLaf = (String) lafComboBox.getSelectedItem();
      if (selectedLaf == null) return;
      displayIconsForTheme(selectedLaf);
    });

    // 6. Final frame setup and initial selection
    setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    setSize(450, 700);
    setLocationRelativeTo(null);

    // Select an initial L&F (Nimbus preferably, or System default)
    selectInitialLookAndFeel(lafComboBox);
  }

  /**
   * Collects icons for all installed Look and Feels without visually changing the application's L&F.
   * Temporarily sets each L&F to retrieve its defaults, converts icons to safe ImageIcons, then restores the original L&F.
   */
  private void collectAllIcons() {
    List<UIManager.LookAndFeelInfo> lafsList = new ArrayList<>(List.of(UIManager.getInstalledLookAndFeels()));

    // Add FlatLaf themes
    lafsList.add(new UIManager.LookAndFeelInfo("Flat Light", FlatLightLaf.class.getName()));
    lafsList.add(new UIManager.LookAndFeelInfo("Flat Dark", FlatDarkLaf.class.getName()));
    lafsList.add(new UIManager.LookAndFeelInfo("Flat IntelliJ", FlatIntelliJLaf.class.getName()));
    lafsList.add(new UIManager.LookAndFeelInfo("Flat Darcula", FlatDarculaLaf.class.getName()));
    lafsList.add(new UIManager.LookAndFeelInfo("Flat macOS Light", FlatMacLightLaf.class.getName()));
    lafsList.add(new UIManager.LookAndFeelInfo("Flat macOS Dark", FlatMacDarkLaf.class.getName()));

    UIManager.LookAndFeelInfo[] lafs = lafsList.toArray(new UIManager.LookAndFeelInfo[0]);

    String originalLafClassName = UIManager.getLookAndFeel().getClass().getName();

    for (UIManager.LookAndFeelInfo lafInfo : lafs) {
      try {
        UIManager.setLookAndFeel(lafInfo.getClassName());
        // No updateComponentTreeUI, so visuals don't change

        UIDefaults defaults = UIManager.getDefaults();
        List<Map.Entry<Object, Object>> iconEntries = new ArrayList<>();

        for (Map.Entry<Object, Object> entry : defaults.entrySet()) {
          if (entry.getValue() instanceof Icon) {
            iconEntries.add(entry);
          }
        }
        iconEntries.sort(Comparator.comparing(e -> e.getKey().toString()));

        List<IconEntry> icons = new ArrayList<>();
        for (Map.Entry<Object, Object> entry : iconEntries) {
          String name = entry.getKey().toString();
          Icon icon = (Icon) entry.getValue();
          Icon safeIcon = createSafeIcon(name, icon);
          if (safeIcon != null) {
            icons.add(new IconEntry(name, safeIcon));
          }
        }
        allIcons.put(lafInfo.getName(), icons);
      } catch (Exception ex) {
        // Skip L&Fs that can't be loaded
        ex.printStackTrace();
      }
    }

    // Restore original L&F without updating UI (since it wasn't changed visually)
    try {
      UIManager.setLookAndFeel(originalLafClassName);
    } catch (Exception ex) {
      ex.printStackTrace();
    }
  }

  /**
   * Displays the icons for the selected theme in the main panel.
   * @param theme The name of the selected Look and Feel theme.
   */
  private void displayIconsForTheme(String theme) {
    mainPanel.removeAll();

    List<IconEntry> icons = allIcons.get(theme);
    if (icons == null) return;

    for (IconEntry entry : icons) {
      JLabel itemLabel = new JLabel(entry.name, entry.icon, SwingConstants.LEFT);
      Border lineBorder = BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(220, 220, 220));
      Border padding = BorderFactory.createEmptyBorder(8, 10, 8, 10);
      itemLabel.setBorder(BorderFactory.createCompoundBorder(lineBorder, padding));
      itemLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
      itemLabel.setToolTipText(entry.name);

      mainPanel.add(itemLabel);
    }

    setTitle("Swing UI Icon Viewer (" + icons.size() + " icons for " + theme + ")");
    mainPanel.revalidate();
    mainPanel.repaint();
  }

  /**
   * Converts any Icon into a static, "safe" ImageIcon by painting it to a BufferedImage.
   * Uses a context-specific dummy component to avoid issues with component-specific icons.
   * @param name The UIManager key for the icon, used to determine dummy type.
   * @param icon The source icon to convert.
   * @return A new ImageIcon, or null if the source icon has no renderable size or painting fails.
   */
  private Icon createSafeIcon(String name, Icon icon) {
    if (icon == null || icon.getIconWidth() <= 0 || icon.getIconHeight() <= 0) {
      return null;
    }
    // Create a new image and get its graphics context
    BufferedImage image = new BufferedImage(icon.getIconWidth(), icon.getIconHeight(), BufferedImage.TYPE_INT_ARGB);
    Graphics2D g2d = image.createGraphics();
    // Get a suitable dummy component based on the icon name
    JComponent dummy = getDummyComponent(name);
    try {
      icon.paintIcon(dummy, g2d, 0, 0);
    } catch (Exception e) {
      // If painting fails (e.g., due to incompatible component type), skip this icon
      g2d.dispose();
      return null;
    }
    // Clean up resources
    g2d.dispose();
    // Return the new image wrapped in an ImageIcon
    return new ImageIcon(image);
  }

  /**
   * Returns a suitable dummy component for painting the icon based on the UIManager key.
   * @param key The UIManager key for the icon.
   * @return A JComponent instance of the appropriate type.
   */
  private JComponent getDummyComponent(String key) {
    String lowerKey = key.toLowerCase();
    if (lowerKey.contains("button") || lowerKey.contains("frame") || lowerKey.contains("closeicon") ||
        lowerKey.contains("maximizeicon") || lowerKey.contains("minimizeicon") || lowerKey.contains("iconifyicon")) {
      return new JButton();
    }
    if (lowerKey.contains("checkbox")) {
      return new JCheckBox();
    }
    if (lowerKey.contains("radio")) {
      return new JRadioButton();
    }
    if (lowerKey.contains("toggle")) {
      return new JToggleButton();
    }
    if (lowerKey.contains("combobox")) {
      return new JComboBox();
    }
    if (lowerKey.contains("spinner")) {
      return new JSpinner();
    }
    if (lowerKey.contains("slider")) {
      return new JSlider();
    }
    if (lowerKey.contains("progressbar")) {
      return new JProgressBar();
    }
    if (lowerKey.contains("tree")) {
      return new JTree();
    }
    if (lowerKey.contains("table")) {
      return new JTable();
    }
    if (lowerKey.contains("list")) {
      return new JList();
    }
    if (lowerKey.contains("scrollbar")) {
      return new JScrollBar();
    }
    if (lowerKey.contains("scrollpane")) {
      return new JScrollPane();
    }
    if (lowerKey.contains("tabbedpane")) {
      return new JTabbedPane();
    }
    if (lowerKey.contains("menuitem")) {
      return new JMenuItem();
    }
    if (lowerKey.contains("menu")) {
      return new JMenu();
    }
    if (lowerKey.contains("checkboxmenu")) {
      return new JCheckBoxMenuItem();
    }
    if (lowerKey.contains("radiomenu")) {
      return new JRadioButtonMenuItem();
    }
    if (lowerKey.contains("popupmenu")) {
      return new JPopupMenu();
    }
    if (lowerKey.contains("toolbar")) {
      return new JToolBar();
    }
    if (lowerKey.contains("tooltip")) {
      return new JToolTip();
    }
    if (lowerKey.contains("text") || lowerKey.contains("editor") || lowerKey.contains("password") || lowerKey.contains("formattedtext")) {
      return new JTextField();
    }
    if (lowerKey.contains("textarea")) {
      return new JTextArea();
    }
    if (lowerKey.contains("optionpane") || lowerKey.contains("audiosystem") || lowerKey.contains("desktop") || lowerKey.contains("splitpane")) {
      return new JLabel();
    }
    if (lowerKey.contains("filechooser")) {
      return new JFileChooser();
    }
    if (lowerKey.contains("colorchooser")) {
      return new JColorChooser();
    }
    // Default fallback
    return new JPanel();
  }

  private void selectInitialLookAndFeel(JComboBox<String> lafComboBox) {
    String preferredLaf = "Nimbus";
    String systemLafClassName = UIManager.getSystemLookAndFeelClassName();
    String systemLafName = null;

    // Find the system L&F name
    UIManager.LookAndFeelInfo[] lafs = UIManager.getInstalledLookAndFeels();
    for (UIManager.LookAndFeelInfo laf : lafs) {
      if (laf.getClassName().equals(systemLafClassName)) {
        systemLafName = laf.getName();
        break;
      }
    }

    // Prefer Nimbus if available, else system, else first
    if (allIcons.containsKey(preferredLaf)) {
      lafComboBox.setSelectedItem(preferredLaf);
    } else if (systemLafName != null && allIcons.containsKey(systemLafName)) {
      lafComboBox.setSelectedItem(systemLafName);
    } else if (lafComboBox.getItemCount() > 0) {
      lafComboBox.setSelectedIndex(0);
    }
  }

  // Helper class to hold icon name and safe icon
  private static class IconEntry {
    String name;
    Icon icon;

    IconEntry(String name, Icon icon) {
      this.name = name;
      this.icon = icon;
    }
  }

  public static void main(String[] args) {
    SwingUtilities.invokeLater(() -> new IconViewer().setVisible(true));
  }
}
