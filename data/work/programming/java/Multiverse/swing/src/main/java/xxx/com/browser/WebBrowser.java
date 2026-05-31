package xxx.com.browser;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.ui.FlatTabbedPaneUI;
import java.awt.AlphaComposite;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.UnrecoverableKeyException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.prefs.Preferences;
import javafx.application.Platform;
import javafx.concurrent.Worker;
import javafx.embed.swing.JFXPanel;
import javafx.scene.Scene;
import javafx.scene.web.WebView;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JLayeredPane;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JPopupMenu;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.plaf.TabbedPaneUI;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableColumn;
import org.apache.batik.swing.JSVGCanvas;
import org.apache.commons.lang3.StringUtils;

public class WebBrowser extends JFrame {

  private static final Color KINDA_GRAY = new Color(135, 135, 135);
  private static final Color DARK_GRAY = new Color(39, 46, 48, 255);
  private static final char[] KEYSTORE_PASSWORD = "persistent-password".toCharArray();
  private static final String PREF_SPINNER_URI = "spinnerUri";
  private String homePageUrl = "https://www.google.com";

  private List<URI> svgFiles;
  private KeyStore clientKeyStore;
  private String customUserAgent = null;
  private Preferences prefs;
  private Map<String, String> bookmarks;
  private URI currentSpinnerUri;
  private boolean isFullScreen = false;

  private JButton backButton, forwardButton, homeButton, refreshButton, menuButton;
  private JTextField urlTextField;
  private JProgressBar progressBar;
  private JLabel statusLabel;
  private JPopupMenu menuPopup, tabContextMenu, homeButtonMenu;
  private int rightClickedTabIndex = -1;

  private JMenuItem newContextTabItem,
      closeOthersItem,
      closeLeftItem,
      closeRightItem,
      importP12Item,
      setUserAgentItem,
      downloadsItem,
      addBookmarkItem,
      manageBookmarksItem,
      manageCertsItem,
      fullScreenItem,
      hideTabsMenuItem,
      hideTabsContextMenuItem;
  private JMenu bookmarksMenu, setSpinnerMenu;
  private ImageIcon appIcon, forwardIcon, backIcon, homeIcon, refreshIcon, menuIcon, showIcon, hideIcon;
  private JSVGCanvas svgCanvas;
  private JTabbedPane tabbedPane;
  private SlidePanel slidePanel;
  private JPanel mainPanel, navigationPanel;
  private boolean tabsPermanentlyHidden = false;
  private TabbedPaneUI originalTabbedPaneUI;
  private TabbedPaneUI hiddenTabbedPaneUI;

  public WebBrowser() {
    initComponents();
    initListeners();
    addNewTab(homePageUrl);
    setVisible(true);
  }

  private void initComponents() {
    initResources();
    prefs = Preferences.userNodeForPackage(WebBrowser.class);

    String savedSpinner = prefs.get(PREF_SPINNER_URI, null);
    if (savedSpinner != null) {
      try {
        this.currentSpinnerUri = new URI(savedSpinner);
      } catch (URISyntaxException e) {
        this.currentSpinnerUri = getRandomSpinner();
      }
    } else {
      this.currentSpinnerUri = getRandomSpinner();
    }

    bookmarks = new HashMap<>();
    loadClientKeyStoreFromPrefs();

    setSize(900, 600);
    setIconImage(appIcon.getImage());
    setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

    backButton = new JButton(backIcon);
    forwardButton = new JButton(forwardIcon);
    homeButton = new JButton(homeIcon);
    refreshButton = new JButton(refreshIcon);
    menuButton = new JButton(menuIcon);

    urlTextField = new JTextField();
    urlTextField.putClientProperty("JComponent.roundRect", true);

    svgCanvas = new JSVGCanvas();
    svgCanvas.setURI(
        Objects.requireNonNull(getClass().getResource("/svg/wind-toy.svg")).toString());
    svgCanvas.setPreferredSize(new Dimension(24, 24));
    svgCanvas.setSize(new Dimension(24, 24));
    svgCanvas.setBackground(new Color(0, 0, 0, 0));
    svgCanvas.setVisible(false);

    navigationPanel = new JPanel(new BorderLayout(5, 0));
    progressBar = new JProgressBar();
    statusLabel = new JLabel("Ready");
    menuPopup = new JPopupMenu();
    tabbedPane = new JTabbedPane();

    // Store original UI and create a "hidden" version
    this.originalTabbedPaneUI = tabbedPane.getUI();
    this.hiddenTabbedPaneUI =
        new FlatTabbedPaneUI() {
          @Override
          public int calculateTabAreaHeight(int tabPlacement, int horizRunCount, int maxTabHeight) {
            // Return 0 to effectively hide the tab area
            return 0;
          }
        };

    backButton.setEnabled(false);
    forwardButton.setEnabled(false);
    homeButton.setToolTipText("Home (Right-click to set homepage)");
    refreshButton.setToolTipText("Refresh");
    menuButton.setToolTipText("Menu");

    // --- START: Button Re-ordering and Persistence Logic ---
    backButton.putClientProperty("id", "back");
    forwardButton.putClientProperty("id", "forward");
    homeButton.putClientProperty("id", "home");
    refreshButton.putClientProperty("id", "refresh");

    Map<String, JButton> buttonMap = new HashMap<>();
    buttonMap.put("back", backButton);
    buttonMap.put("forward", forwardButton);
    buttonMap.put("home", homeButton);
    buttonMap.put("refresh", refreshButton);

    JPanel leftButtonsPanel = new JPanel();

    String defaultOrder = "back,forward,home,refresh";
    String savedOrder = prefs.get("navButtonOrder", defaultOrder);
    List<String> orderedIds = new ArrayList<>(Arrays.asList(savedOrder.split(",")));

    buttonMap
        .keySet()
        .forEach(
            id -> {
              if (!orderedIds.contains(id)) {
                orderedIds.add(id);
              }
            });

    for (String id : orderedIds) {
      if (buttonMap.containsKey(id)) {
        leftButtonsPanel.add(buttonMap.get(id));
      }
    }
    leftButtonsPanel.add(svgCanvas);

    ButtonReorderListener reorderListener = new ButtonReorderListener(leftButtonsPanel, prefs);
    for (Component comp : leftButtonsPanel.getComponents()) {
      if (comp instanceof JButton) {
        comp.addMouseListener(reorderListener);
        comp.addMouseMotionListener(reorderListener);
      }
    }
    // --- END: Button Re-ordering and Persistence Logic ---

    JPanel rightButtonsPanel = new JPanel();
    rightButtonsPanel.add(menuButton);

    JPanel textFieldWrapper = new JPanel(new java.awt.GridBagLayout());
    java.awt.GridBagConstraints gbc = new java.awt.GridBagConstraints();
    gbc.weightx = 1.0;
    gbc.fill = java.awt.GridBagConstraints.HORIZONTAL;
    textFieldWrapper.add(urlTextField, gbc);

    navigationPanel.add(leftButtonsPanel, BorderLayout.WEST);
    navigationPanel.add(textFieldWrapper, BorderLayout.CENTER);
    navigationPanel.add(rightButtonsPanel, BorderLayout.EAST);

    urlTextField.setPreferredSize(new Dimension(0, 25));
    progressBar.setStringPainted(true);
    progressBar.setForeground(new Color(0, 120, 215));
    progressBar.setPreferredSize(new Dimension(getWidth(), 20));
    progressBar.setVisible(false);

    statusLabel.setFont(new Font("Arial", Font.ITALIC, 12));
    statusLabel.setForeground(Color.BLACK);

    JMenuItem newTabItem = new JMenuItem("New Tab");
    newTabItem.setIcon(createMenuIcon("/png/add.png", 16));
    addBookmarkItem = new JMenuItem("Add Bookmark");
    addBookmarkItem.setIcon(recolorIcon(createMenuIcon("/png/browser/add.png", 16), KINDA_GRAY));
    bookmarksMenu = new JMenu("Bookmarks");
    bookmarksMenu.setIcon(recolorIcon(createMenuIcon("/png/directory.png", 16), KINDA_GRAY));
    manageBookmarksItem = new JMenuItem("Manage Bookmarks...");
    manageBookmarksItem.setIcon(recolorIcon(createMenuIcon("/png/equalizer.png", 16), KINDA_GRAY));
    JMenuItem clearHistoryItem = new JMenuItem("Clear History");
    clearHistoryItem.setIcon(recolorIcon(createMenuIcon("/png/clear.png", 16), KINDA_GRAY));
    downloadsItem = new JMenuItem("Downloads");
    downloadsItem.setIcon(recolorIcon(createMenuIcon("/png/download.png", 16), KINDA_GRAY));
    importP12Item = new JMenuItem("Import Certificate...");
    importP12Item.setIcon(createMenuIcon("/png/pki/ca-bundle.png", 16));
    manageCertsItem = new JMenuItem("Manage Certificates...");
    manageCertsItem.setIcon(createMenuIcon("/png/pki/key.png", 16));
    setUserAgentItem = new JMenuItem("Set User-Agent...");
    setUserAgentItem.setIcon(recolorIcon(createMenuIcon("/png/browser/zombie.png", 16), KINDA_GRAY));
    setSpinnerMenu = new JMenu("Set Spinner");
    setSpinnerMenu.setIcon(createMenuIcon("/png/system2.png", 16));
    fullScreenItem = new JMenuItem("Full Screen");
    fullScreenItem.setIcon(createMenuIcon("/png/fullScreen.png", 16));
    hideTabsMenuItem = new JMenuItem("Hide Tabs");
    hideTabsMenuItem.setIcon(recolorIcon(createMenuIcon("/png/password/eye-shut.png", 16), KINDA_GRAY));

    menuPopup.add(newTabItem);
    menuPopup.add(clearHistoryItem);
    menuPopup.addSeparator();
    menuPopup.add(addBookmarkItem);
    menuPopup.add(bookmarksMenu);
    menuPopup.add(manageBookmarksItem);
    menuPopup.addSeparator();
    menuPopup.add(downloadsItem);
    menuPopup.addSeparator();
    menuPopup.add(importP12Item);
    menuPopup.add(manageCertsItem);
    menuPopup.add(setUserAgentItem);
    menuPopup.add(fullScreenItem);
    menuPopup.add(hideTabsMenuItem);
    menuPopup.addSeparator();
    menuPopup.add(setSpinnerMenu);

    loadBookmarks();
    rebuildSpinnerMenu();

    tabContextMenu = new JPopupMenu();
    newContextTabItem = new JMenuItem("New Tab");
    closeOthersItem = new JMenuItem("Close Other Tabs");
    closeLeftItem = new JMenuItem("Close Tabs to the Left");
    closeRightItem = new JMenuItem("Close Tabs to the Right");
    hideTabsContextMenuItem = new JMenuItem("Hide Tabs");
    tabContextMenu.add(newContextTabItem);
    tabContextMenu.addSeparator();
    tabContextMenu.add(closeOthersItem);
    tabContextMenu.add(closeLeftItem);
    tabContextMenu.add(closeRightItem);
    tabContextMenu.addSeparator();
    tabContextMenu.add(hideTabsContextMenuItem);

    homeButtonMenu = new JPopupMenu();
    JMenuItem setHomepageItem = new JMenuItem("Set current page as homepage");
    homeButtonMenu.add(setHomepageItem);

    JPanel southPanel = new JPanel(new java.awt.GridBagLayout());
    java.awt.GridBagConstraints southgbc = new java.awt.GridBagConstraints();
    southgbc.gridx = 0;
    southgbc.gridy = 0;
    southgbc.anchor = java.awt.GridBagConstraints.WEST;
    southgbc.insets = new java.awt.Insets(0, 5, 0, 0);
    southPanel.add(statusLabel, southgbc);
    southgbc.weightx = 1.0;
    southgbc.fill = java.awt.GridBagConstraints.HORIZONTAL;
    southgbc.anchor = java.awt.GridBagConstraints.CENTER;
    southgbc.insets = new java.awt.Insets(0, 0, 0, 0);
    southPanel.add(progressBar, southgbc);
    southPanel.setComponentZOrder(statusLabel, 0);
    southPanel.setComponentZOrder(progressBar, 1);
    southPanel.setBackground(KINDA_GRAY);
    southPanel.setOpaque(true);

    mainPanel = new JPanel(new BorderLayout());
    mainPanel.add(navigationPanel, BorderLayout.NORTH);
    mainPanel.add(tabbedPane, BorderLayout.CENTER);
    mainPanel.add(southPanel, BorderLayout.SOUTH);

    // --- START: Side Panel Integration ---
    JLayeredPane layeredPane = new JLayeredPane();
    setContentPane(layeredPane);

    layeredPane.addComponentListener(
        new java.awt.event.ComponentAdapter() {
          @Override
          public void componentResized(java.awt.event.ComponentEvent e) {
            Component c = e.getComponent();
            // Set the bounds for the main panel
            mainPanel.setBounds(0, 0, c.getWidth(), c.getHeight());
            // Tell the main panel to re-layout its own components (THE FIX)
            mainPanel.revalidate();

            if (slidePanel != null) {
              slidePanel.updatePosition();
            }
          }
        });

    layeredPane.add(mainPanel, JLayeredPane.DEFAULT_LAYER);
    slidePanel = new SlidePanel(this);
    layeredPane.add(slidePanel, JLayeredPane.PALETTE_LAYER);
    // --- END: Side Panel Integration ---

    ImageIcon addIcon =
        recolorIcon(
            new ImageIcon(
                new ImageIcon(
                    Objects.requireNonNull(getClass().getResource("/png/browser/add.png")))
                    .getImage()
                    .getScaledInstance(18, 18, Image.SCALE_SMOOTH)),
            KINDA_GRAY);
    JButton newTabButton = new JButton(addIcon);
    newTabButton.setToolTipText("New Tab");
    newTabButton.putClientProperty("JButton.buttonType", "toolBarButton");
    newTabButton.setPreferredSize(new Dimension(34, 34));
    newTabButton.addActionListener(e -> addNewTab(homePageUrl));
    tabbedPane.addTab(null, new JPanel());
    int plusButtonIndex = tabbedPane.getTabCount() - 1;
    tabbedPane.setTabComponentAt(plusButtonIndex, newTabButton);
    tabbedPane.setEnabledAt(plusButtonIndex, false);
  }

  private ImageIcon createMenuIcon(String path, int size) {
    try {
      return new ImageIcon(
          new ImageIcon(Objects.requireNonNull(getClass().getResource(path)))
              .getImage()
              .getScaledInstance(size, size, Image.SCALE_SMOOTH));

    } catch (Exception e) {

      System.err.println("Warning: Could not load menu icon: " + path);

      return new ImageIcon(new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB));
    }
  }

  private void initResources() {
    initSpinners();

    appIcon =
        recolorIcon(
            new ImageIcon(
                new ImageIcon(Objects.requireNonNull(getClass().getResource("/png/skull3.png")))
                    .getImage()
                    .getScaledInstance(45, 45, Image.SCALE_SMOOTH)),
            KINDA_GRAY);

    backIcon =
        recolorIcon(
            new ImageIcon(
                new ImageIcon(Objects.requireNonNull(getClass().getResource("/png/back.png")))
                    .getImage()
                    .getScaledInstance(20, 20, Image.SCALE_SMOOTH)),
            KINDA_GRAY);
    forwardIcon =
        recolorIcon(
            new ImageIcon(
                new ImageIcon(Objects.requireNonNull(getClass().getResource("/png/forward.png")))
                    .getImage()
                    .getScaledInstance(20, 20, Image.SCALE_SMOOTH)),
            KINDA_GRAY);
    homeIcon =
        recolorIcon(
            new ImageIcon(
                new ImageIcon(
                    Objects.requireNonNull(getClass().getResource("/png/browser/home.png")))
                    .getImage()
                    .getScaledInstance(20, 20, Image.SCALE_SMOOTH)),
            KINDA_GRAY);
    refreshIcon =
        recolorIcon(
            new ImageIcon(
                new ImageIcon(
                    Objects.requireNonNull(getClass().getResource("/png/browser/refresh.png")))
                    .getImage()
                    .getScaledInstance(20, 20, Image.SCALE_SMOOTH)),
            KINDA_GRAY);
    menuIcon =
        recolorIcon(
            new ImageIcon(
                new ImageIcon(
                    Objects.requireNonNull(
                        getClass().getResource("/png/browser/hamburger.png")))
                    .getImage()
                    .getScaledInstance(20, 20, Image.SCALE_SMOOTH)),
            KINDA_GRAY);

    showIcon =
        recolorIcon(
            new ImageIcon(
                new ImageIcon(
                    Objects.requireNonNull(
                        getClass().getResource("/png/password/eye-open.png")))
                    .getImage()
                    .getScaledInstance(20, 20, Image.SCALE_SMOOTH)),
            KINDA_GRAY);

    hideIcon =
        recolorIcon(
            new ImageIcon(
                new ImageIcon(
                    Objects.requireNonNull(
                        getClass().getResource("/png/password/eye-shut.png")))
                    .getImage()
                    .getScaledInstance(20, 20, Image.SCALE_SMOOTH)),
            KINDA_GRAY);
  }

  private void initListeners() {
    backButton.addActionListener(ev -> getActiveTab().ifPresent(BrowserTab::goBack));
    forwardButton.addActionListener(ev -> getActiveTab().ifPresent(BrowserTab::goForward));
    homeButton.addActionListener(ev -> getActiveTab().ifPresent(tab -> tab.loadURL(homePageUrl)));
    refreshButton.addActionListener(ev -> getActiveTab().ifPresent(BrowserTab::reload));

    homeButton.addMouseListener(
        new MouseAdapter() {
          public void mousePressed(MouseEvent e) {
            if (e.isPopupTrigger()) {
              homeButtonMenu.show(e.getComponent(), e.getX(), e.getY());
            }
          }

          public void mouseReleased(MouseEvent e) {
            if (e.isPopupTrigger()) {
              homeButtonMenu.show(e.getComponent(), e.getX(), e.getY());
            }
          }
        });

    ((JMenuItem) homeButtonMenu.getComponent(0))
        .addActionListener(
            e ->
                getActiveTab()
                    .ifPresent(
                        tab ->
                            Platform.runLater(
                                () -> {
                                  String currentUrl = tab.webView.getEngine().getLocation();
                                  if (currentUrl != null
                                      && !currentUrl.isEmpty()
                                      && !currentUrl.equals("about:blank")) {
                                    this.homePageUrl = currentUrl;
                                    SwingUtilities.invokeLater(
                                        () -> statusLabel.setText("Homepage set successfully"));
                                  }
                                })));

    urlTextField.addKeyListener(
        new KeyAdapter() {
          public void keyReleased(KeyEvent ev) {
            if (ev.getKeyCode() == KeyEvent.VK_ENTER) {
              getActiveTab().ifPresent(tab -> tab.loadURL(urlTextField.getText()));
            }
          }
        });

    menuButton.addMouseListener(
        new MouseAdapter() {
          public void mouseClicked(MouseEvent e) {
            menuPopup.show(menuButton, e.getX(), e.getY());
          }
        });

    ((JMenuItem) menuPopup.getComponent(0)).addActionListener(e -> addNewTab(homePageUrl));
    ((JMenuItem) menuPopup.getComponent(1))
        .addActionListener(
            e ->
                getActiveTab()
                    .ifPresent(
                        tab -> {
                          tab.clearHistory();
                          updateUiForActiveTab();
                          statusLabel.setText("History cleared");
                        }));

    addBookmarkItem.addActionListener(
        e ->
            getActiveTab()
                .ifPresent(
                    tab ->
                        Platform.runLater(
                            () -> {
                              String url = tab.webView.getEngine().getLocation();
                              String title = tab.webView.getEngine().getTitle();
                              SwingUtilities.invokeLater(() -> addBookmark(url, title));
                            })));

    manageBookmarksItem.addActionListener(e -> slidePanel.showPanel(1));
    downloadsItem.addActionListener(e -> slidePanel.showPanel(0));
    importP12Item.addActionListener(e -> importP12Certificate());
    manageCertsItem.addActionListener(e -> showManageCertificatesDialog());
    setUserAgentItem.addActionListener(e -> showSetUserAgentDialog());
    fullScreenItem.addActionListener(e -> toggleFullScreen());
    hideTabsMenuItem.addActionListener(e -> toggleTabsVisibility());
    hideTabsContextMenuItem.addActionListener(e -> toggleTabsVisibility());
    newContextTabItem.addActionListener(e -> addNewTab(homePageUrl));
    closeOthersItem.addActionListener(e -> closeOtherTabs());
    closeLeftItem.addActionListener(e -> closeLeftTabs());
    closeRightItem.addActionListener(e -> closeRightTabs());
    tabbedPane.addChangeListener(e -> updateUiForActiveTab());

    TabDragAndDropListener tabListener = new TabDragAndDropListener();
    tabbedPane.addMouseListener(tabListener);
    tabbedPane.addMouseMotionListener(tabListener);

//    MouseAdapter hoverListener =
//        new MouseAdapter() {
//          @Override
//          public void mouseEntered(MouseEvent e) {
//            if (tabsPermanentlyHidden) {
//              showTabs();
//            }
//          }

//          @Override
//          public void mouseExited(MouseEvent e) {
//            if (tabsPermanentlyHidden) {
//              if (!navigationPanel.getBounds().contains(e.getPoint())) {
//                hideTabs();
//              }
//            }
//          }
//        };

//    navigationPanel.addMouseListener(hoverListener);
//    navigationPanel.addMouseMotionListener(
//        new MouseMotionAdapter() {
//          @Override
//          public void mouseMoved(MouseEvent e) {
//            if (tabsPermanentlyHidden && tabbedPane.getUI() != originalTabbedPaneUI) {
//              showTabs();
//            }
//          }
//        });

    statusLabel.addPropertyChangeListener(
        "text",
        evt -> {
          Timer timer = new Timer(3500, tmr -> statusLabel.setText(StringUtils.EMPTY));
          timer.setRepeats(false);
          timer.start();
        });
  }

  private void hideTabs() {
    if (tabbedPane.getUI() != hiddenTabbedPaneUI) {
      tabbedPane.setUI(hiddenTabbedPaneUI);
      hideTabsMenuItem.setIcon(showIcon);
    }
  }

  private void showTabs() {
    if (tabbedPane.getUI() != originalTabbedPaneUI) {
      tabbedPane.setUI(originalTabbedPaneUI);
      hideTabsMenuItem.setIcon(hideIcon);
    }
  }

  private void toggleTabsVisibility() {
    tabsPermanentlyHidden = !tabsPermanentlyHidden;
    if (tabsPermanentlyHidden) {
      hideTabs();
      hideTabsMenuItem.setText("Show Tabs");
      hideTabsContextMenuItem.setText("Show Tabs");
    } else {
      showTabs();
      hideTabsMenuItem.setText("Hide Tabs");
      hideTabsContextMenuItem.setText("Hide Tabs");
    }
  }

  public static ImageIcon changeBrightness(ImageIcon icon, float factor) {
    BufferedImage sourceImage =
        new BufferedImage(icon.getIconWidth(), icon.getIconHeight(), BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = sourceImage.createGraphics();
    icon.paintIcon(null, g, 0, 0);
    g.dispose();

    BufferedImage destImage =
        new BufferedImage(
            sourceImage.getWidth(), sourceImage.getHeight(), BufferedImage.TYPE_INT_ARGB);

    for (int y = 0; y < sourceImage.getHeight(); y++) {
      for (int x = 0; x < sourceImage.getWidth(); x++) {
        int sourceRgb = sourceImage.getRGB(x, y);
        int alpha = (sourceRgb >> 24) & 0xff;

        if (alpha == 0) {
          destImage.setRGB(x, y, sourceRgb);
          continue;
        }

        Color sourcePixelColor = new Color(sourceRgb, true);
        float[] sourceHsb =
            Color.RGBtoHSB(
                sourcePixelColor.getRed(),
                sourcePixelColor.getGreen(),
                sourcePixelColor.getBlue(),
                null);

        float newBrightness = sourceHsb[2] * factor;
        newBrightness = Math.max(0.0f, Math.min(1.0f, newBrightness));

        Color newPixelColor = Color.getHSBColor(sourceHsb[0], sourceHsb[1], newBrightness);

        int finalRgb = (alpha << 24) | (newPixelColor.getRGB() & 0x00ffffff);
        destImage.setRGB(x, y, finalRgb);
      }
    }
    return new ImageIcon(destImage);
  }

  private void importP12Certificate() {
    JFileChooser fileChooser = new JFileChooser();
    fileChooser.setDialogTitle("Select a P12 Certificate File");
    fileChooser.setFileFilter(
        new FileNameExtensionFilter("P12 Files (*.p12, *.pfx)", "p12", "pfx"));
    if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
      File p12File = fileChooser.getSelectedFile();
      JPasswordField passwordField = new JPasswordField(20);
      int option =
          JOptionPane.showConfirmDialog(
              this,
              passwordField,
              "Enter Password for " + p12File.getName(),
              JOptionPane.OK_CANCEL_OPTION,
              JOptionPane.PLAIN_MESSAGE);

      if (option == JOptionPane.OK_OPTION) {
        char[] password = passwordField.getPassword();
        try {
          KeyStore tempP12Store = KeyStore.getInstance("PKCS12");
          try (FileInputStream fis = new FileInputStream(p12File)) {
            tempP12Store.load(fis, password);
          }

          int importedCount = 0;
          Enumeration<String> aliases = tempP12Store.aliases();
          while (aliases.hasMoreElements()) {
            String alias = aliases.nextElement();
            if (tempP12Store.isKeyEntry(alias)) {
              KeyStore.ProtectionParameter protectionParam =
                  new KeyStore.PasswordProtection(password);
              KeyStore.Entry entry = tempP12Store.getEntry(alias, protectionParam);
              clientKeyStore.setEntry(alias, entry, protectionParam);
              importedCount++;
            }
          }

          if (importedCount > 0) {
            saveClientKeyStoreToPrefs();
            updateDefaultSslContext();
            statusLabel.setText("Successfully imported " + importedCount + " certificate(s).");
          } else {
            statusLabel.setText("No certificates found in the selected file.");
          }
        } catch (UnrecoverableKeyException | IOException e) {
          statusLabel.setText("Error: Incorrect password or corrupted P12 file.");
        } catch (Exception e) {
          statusLabel.setText("Error importing certificate: " + e.getMessage());
          e.printStackTrace();
        } finally {
          Arrays.fill(password, ' ');
        }
      }
    }
  }

  private void updateDefaultSslContext() {
    try {
      KeyManagerFactory kmf =
          KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
      kmf.init(clientKeyStore, null);
      SSLContext sslContext = SSLContext.getInstance("TLS");
      sslContext.init(kmf.getKeyManagers(), null, null);
      HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.getSocketFactory());
    } catch (Exception e) {
      statusLabel.setText("Failed to apply new certificate context.");
      e.printStackTrace();
    }
  }

  private void loadClientKeyStoreFromPrefs() {
    try {
      clientKeyStore = KeyStore.getInstance("PKCS12");
      String encodedKeystore = prefs.get("clientKeyStore", null);
      if (encodedKeystore != null && !encodedKeystore.isEmpty()) {
        byte[] storeData = Base64.getDecoder().decode(encodedKeystore);
        clientKeyStore.load(new ByteArrayInputStream(storeData), KEYSTORE_PASSWORD);
      } else {
        clientKeyStore.load(null, null);
      }
    } catch (Exception e) {
      JOptionPane.showMessageDialog(
          this,
          "Could not load client certificate store from preferences: \n" + e.getMessage(),
          "Error",
          JOptionPane.ERROR_MESSAGE);
      try {
        clientKeyStore.load(null, null);
      } catch (Exception ex) {
        // Should not happen
      }
    }
    updateDefaultSslContext();
  }

  private void saveClientKeyStoreToPrefs() {
    try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
      clientKeyStore.store(bos, KEYSTORE_PASSWORD);
      String encoded = Base64.getEncoder().encodeToString(bos.toByteArray());
      prefs.put("clientKeyStore", encoded);
    } catch (Exception e) {
      statusLabel.setText("Error saving certificate store.");
      e.printStackTrace();
    }
  }

  private void addBookmark(String url, String title) {
    if (url == null || url.isEmpty() || url.equals("about:blank")) {
      statusLabel.setText("Cannot bookmark an empty page.");
      return;
    }
    String bookmarkTitle = (title == null || title.isEmpty()) ? url : title;
    bookmarks.put(url, bookmarkTitle);
    saveBookmarksToPrefs();
    statusLabel.setText("Bookmark added.");
  }

  private void saveBookmarksToPrefs() {
    StringBuilder sb = new StringBuilder();
    for (Map.Entry<String, String> entry : bookmarks.entrySet()) {
      sb.append(Base64.getEncoder().encodeToString(entry.getKey().getBytes()))
          .append("|")
          .append(Base64.getEncoder().encodeToString(entry.getValue().getBytes()))
          .append(";;");
    }
    prefs.put("bookmarks", sb.toString());
    rebuildBookmarkMenu();
    if (slidePanel != null) {
      slidePanel.updateBookmarksTable();
    }
  }

  private void loadBookmarks() {
    bookmarks.clear();
    String savedBookmarks = prefs.get("bookmarks", "");
    if (!savedBookmarks.isEmpty()) {
      String[] pairs = savedBookmarks.split(";;");
      for (String pair : pairs) {
        String[] parts = pair.split("\\|");
        if (parts.length == 2) {
          String url = new String(Base64.getDecoder().decode(parts[0]));
          String title = new String(Base64.getDecoder().decode(parts[1]));
          bookmarks.put(url, title);
        }
      }
    }
    rebuildBookmarkMenu();
  }

  private void rebuildBookmarkMenu() {
    bookmarksMenu.removeAll();
    if (bookmarks.isEmpty()) {
      JMenuItem emptyItem = new JMenuItem("(No bookmarks yet)");
      emptyItem.setEnabled(false);
      bookmarksMenu.add(emptyItem);
    } else {
      for (Map.Entry<String, String> entry : bookmarks.entrySet()) {
        JMenuItem bookmarkItem = new JMenuItem(entry.getValue());
        bookmarkItem.setToolTipText(entry.getKey());
        bookmarkItem.addActionListener(
            e -> getActiveTab().ifPresent(tab -> tab.loadURL(entry.getKey())));
        bookmarksMenu.add(bookmarkItem);
      }
    }
  }

  private void rebuildSpinnerMenu() {
    setSpinnerMenu.removeAll();
    if (svgFiles == null || svgFiles.isEmpty()) {
      JMenuItem noSpinnersItem = new JMenuItem("(No spinners found)");
      noSpinnersItem.setEnabled(false);
      setSpinnerMenu.add(noSpinnersItem);
    } else {
      for (URI spinnerUri : svgFiles) {
        String fileName = new File(spinnerUri).getName();
        JMenuItem spinnerItem = new JMenuItem(fileName);
        spinnerItem.addActionListener(
            e -> {
              this.currentSpinnerUri = spinnerUri;
              prefs.put(PREF_SPINNER_URI, spinnerUri.toString());
              svgCanvas.setURI(currentSpinnerUri.toString());
              statusLabel.setText("Spinner set to " + fileName);
            });
        setSpinnerMenu.add(spinnerItem);
      }
    }
  }

  private void showManageCertificatesDialog() {
    JDialog dialog = new JDialog(this, "Manage Client Certificates", true);
    dialog.setSize(500, 400);
    dialog.setLocationRelativeTo(this);
    updateManageCertsDialogContent(dialog);
    dialog.setVisible(true);
  }

  private void updateManageCertsDialogContent(JDialog dialog) {
    dialog.getContentPane().removeAll();
    JPanel listPanel = new JPanel();
    listPanel.setLayout(new BoxLayout(listPanel, BoxLayout.Y_AXIS));
    try {
      Enumeration<String> aliases = clientKeyStore.aliases();
      if (!aliases.hasMoreElements()) {
        listPanel.add(new JLabel("No client certificates have been imported."));
      } else {
        while (aliases.hasMoreElements()) {
          String alias = aliases.nextElement();
          JPanel itemPanel = new JPanel(new BorderLayout(10, 0));
          itemPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
          JLabel aliasLabel = new JLabel(alias);
          JButton removeButton = new JButton("Remove");
          removeButton.addActionListener(
              e -> {
                try {
                  clientKeyStore.deleteEntry(alias);
                  saveClientKeyStoreToPrefs();
                  updateDefaultSslContext();
                  updateManageCertsDialogContent(dialog);
                  statusLabel.setText("Certificate removed.");
                } catch (Exception ex) {
                  statusLabel.setText("Error removing certificate.");
                }
              });
          itemPanel.add(aliasLabel, BorderLayout.CENTER);
          itemPanel.add(removeButton, BorderLayout.EAST);
          listPanel.add(itemPanel);
        }
      }
    } catch (Exception e) {
      listPanel.add(new JLabel("Error reading certificate store."));
    }
    dialog.add(new JScrollPane(listPanel));
    dialog.revalidate();
    dialog.repaint();
  }

  private void showSetUserAgentDialog() {
    getActiveTab()
        .ifPresent(
            tab ->
                Platform.runLater(
                    () -> {
                      String currentUserAgent = tab.webView.getEngine().getUserAgent();
                      SwingUtilities.invokeLater(
                          () -> {
                            String newAgent =
                                (String)
                                    JOptionPane.showInputDialog(
                                        this,
                                        "Enter the new User-Agent string:",
                                        "Set User-Agent",
                                        JOptionPane.PLAIN_MESSAGE,
                                        null,
                                        null,
                                        currentUserAgent);
                            if (newAgent != null && !newAgent.trim().isEmpty()) {
                              setGlobalUserAgent(newAgent.trim());
                            }
                          });
                    }));
  }

  private void setGlobalUserAgent(String userAgent) {
    this.customUserAgent = userAgent;
    for (int i = 0; i < tabbedPane.getTabCount(); i++) {
      Component comp = tabbedPane.getComponentAt(i);
      if (comp instanceof BrowserTab tab) {
        Platform.runLater(() -> tab.webView.getEngine().setUserAgent(userAgent));
      }
    }
    statusLabel.setText("User-Agent has been updated.");
  }

  private void toggleFullScreen() {
    isFullScreen = !isFullScreen;
    GraphicsDevice device =
        GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
    dispose();
    if (isFullScreen) {
      setUndecorated(true);
      device.setFullScreenWindow(this);
      fullScreenItem.setText("Exit Full Screen");
    } else {
      setUndecorated(false);
      device.setFullScreenWindow(null);
      fullScreenItem.setText("Full Screen");
    }
    setVisible(true);
  }

  private void addNewTab(String url) {
    BrowserTab newTab = new BrowserTab(this, url);
    int newTabIndex = tabbedPane.getTabCount() - 1;
    tabbedPane.insertTab(null, null, newTab, null, newTabIndex);
    TabHeader tabHeader = new TabHeader("New Tab");
    tabbedPane.setTabComponentAt(newTabIndex, tabHeader);
    tabbedPane.setSelectedIndex(newTabIndex);
    newTab.loadURL(url);
  }

  private void closeOtherTabs() {
    if (rightClickedTabIndex == -1) return;
    for (int i = tabbedPane.getTabCount() - 2; i >= 0; i--) {
      if (i != rightClickedTabIndex) {
        tabbedPane.remove(i);
      }
    }
  }

  private void closeLeftTabs() {
    if (rightClickedTabIndex <= 0) return;
    for (int i = rightClickedTabIndex - 1; i >= 0; i--) {
      tabbedPane.remove(i);
    }
  }

  private void closeRightTabs() {
    if (rightClickedTabIndex == -1) return;
    for (int i = tabbedPane.getTabCount() - 2; i > rightClickedTabIndex; i--) {
      tabbedPane.remove(i);
    }
  }

  private Optional<BrowserTab> getActiveTab() {
    int selectedIndex = tabbedPane.getSelectedIndex();
    if (selectedIndex == -1 || selectedIndex == tabbedPane.getTabCount() - 1) {
      return Optional.empty();
    }
    return Optional.of((BrowserTab) tabbedPane.getSelectedComponent());
  }

  private void updateUiForActiveTab() {
    getActiveTab()
        .ifPresent(
            tab ->
                Platform.runLater(
                    () -> {
                      String location = tab.webView.getEngine().getLocation();
                      int currentIndex = tab.webView.getEngine().getHistory().getCurrentIndex();
                      int historySize = tab.webView.getEngine().getHistory().getEntries().size();
                      SwingUtilities.invokeLater(
                          () -> {
                            urlTextField.setText(location);
                            backButton.setEnabled(currentIndex > 0);
                            forwardButton.setEnabled(currentIndex < historySize - 1);
                          });
                    }));
  }

  private String verifyUrl(String url) throws IllegalArgumentException {

    if (url == null || url.trim().isEmpty()) {
      throw new IllegalArgumentException("URL cannot be empty");
    }

    String formattedUrl = url.trim();

    if (!formattedUrl.startsWith("http://") && !formattedUrl.startsWith("https://")) {
      formattedUrl = "https://" + formattedUrl;
    }

    try {
      URI uri = new URI(formattedUrl);
      if (uri.getHost() == null) {
        throw new URISyntaxException(formattedUrl, "Host is missing");
      }
      return formattedUrl;
    } catch (Exception e) {
      throw new IllegalArgumentException("Invalid URL format: " + e.getMessage(), e);
    }
  }

  public static ImageIcon colorGrade(ImageIcon icon, Color color) {
    BufferedImage sourceImage =
        new BufferedImage(icon.getIconWidth(), icon.getIconHeight(), BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = sourceImage.createGraphics();
    icon.paintIcon(null, g, 0, 0);
    g.dispose();

    BufferedImage destImage =
        new BufferedImage(
            sourceImage.getWidth(), sourceImage.getHeight(), BufferedImage.TYPE_INT_ARGB);

    float[] targetHsb = Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), null);
    float targetHue = targetHsb[0];
    float targetSaturation = targetHsb[1];

    for (int y = 0; y < sourceImage.getHeight(); y++) {
      for (int x = 0; x < sourceImage.getWidth(); x++) {
        int sourceRgb = sourceImage.getRGB(x, y);
        int alpha = (sourceRgb >> 24) & 0xff;

        if (alpha == 0) {
          destImage.setRGB(x, y, sourceRgb);
          continue;
        }

        Color sourcePixelColor = new Color(sourceRgb, true);
        float[] sourceHsb =
            Color.RGBtoHSB(
                sourcePixelColor.getRed(),
                sourcePixelColor.getGreen(),
                sourcePixelColor.getBlue(),
                null);
        float sourceBrightness = sourceHsb[2];

        Color newPixelColor = Color.getHSBColor(targetHue, targetSaturation, sourceBrightness);

        int finalRgb = (alpha << 24) | (newPixelColor.getRGB() & 0x00ffffff);
        destImage.setRGB(x, y, finalRgb);
      }
    }

    return new ImageIcon(destImage);
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

  public URI getRandomSpinner() {
    return svgFiles.get(new Random().nextInt(svgFiles.size()));
  }

  public void initSpinners() {
    try {
      Path path = Path.of(ClassLoader.getSystemResource("svg/").toURI());
      File directory = path.toFile();
      this.svgFiles =
          Arrays.stream(directory.listFiles((dir, name) -> name.toLowerCase().endsWith(".svg")))
              .map(File::toURI)
              .toList();
    } catch (Exception e) {
      System.out.println("Error loading SVG files: " + e.getMessage());
      System.exit(-1);
    }
  }

  public void startDownload(URL url, File file) {
    slidePanel.startDownload(url, file);
  }

  private class BrowserTab extends JPanel {
    private final JFXPanel jfxPanel;
    private WebView webView;
    private final WebBrowser parentBrowser;
    private double contextMenuX, contextMenuY;

    public BrowserTab(WebBrowser parent, String initialUrl) {
      super(new BorderLayout());
      this.parentBrowser = parent;
      jfxPanel = new JFXPanel();
      add(jfxPanel, BorderLayout.CENTER);

      Platform.runLater(
          () -> {
            webView = new WebView();
            if (customUserAgent != null) {
              webView.getEngine().setUserAgent(customUserAgent);
            }

            webView.setContextMenuEnabled(false);
            setupContextMenu();

            jfxPanel.setScene(new Scene(webView));
            try {
              URL scrollbarCssUrl = getClass().getResource("/javafx/scrollbar.css");
              if (scrollbarCssUrl != null) {
                webView.getEngine().setUserStyleSheetLocation(scrollbarCssUrl.toExternalForm());
              }
            } catch (Exception e) {
              System.out.println("Issue with webview engine : " + e.getMessage());
            }
            setupWebViewListeners();
            loadURL(initialUrl);
          });
    }

    private void setupContextMenu() {
      javafx.scene.control.ContextMenu contextMenu = new javafx.scene.control.ContextMenu();
      javafx.scene.control.MenuItem saveAsItem = new javafx.scene.control.MenuItem("Save as...");

      saveAsItem.setOnAction(
          event -> {
            String script =
                "var elem = document.elementFromPoint(%f, %f);"
                    + "var link = elem.closest('a');"
                    + "if (link) { link.href; }"
                    + "else if (elem.tagName === 'IMG') { elem.src; }"
                    + "else { null; }";

            Object urlToDownload =
                webView
                    .getEngine()
                    .executeScript(String.format(script, contextMenuX, contextMenuY));

            if (urlToDownload instanceof String) {
              SwingUtilities.invokeLater(() -> promptAndStartDownload((String) urlToDownload));
            }
          });
      contextMenu.getItems().add(saveAsItem);

      webView.setOnContextMenuRequested(
          event -> {
            contextMenuX = event.getX();
            contextMenuY = event.getY();

            saveAsItem.setDisable(true);
            String findUrlScript =
                "var elem = document.elementFromPoint(%f, %f);"
                    + "var link = elem.closest('a');"
                    + "(link != null && link.href != '') || (elem.tagName === 'IMG' && elem.src != '')";
            Object result =
                webView
                    .getEngine()
                    .executeScript(String.format(findUrlScript, event.getX(), event.getY()));
            if (result instanceof Boolean && (Boolean) result) {
              saveAsItem.setDisable(false);
            }
            contextMenu.show(webView, event.getScreenX(), event.getScreenY());
          });
    }

    private void promptAndStartDownload(String urlString) {
      try {
        URI uri = new URI(urlString);
        String suggestedFileName = new File(uri).getName();
        if (suggestedFileName.isEmpty()) {
          suggestedFileName = "download";
        }

        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Save As");
        fileChooser.setSelectedFile(new File(suggestedFileName));

        if (fileChooser.showSaveDialog(parentBrowser) == JFileChooser.APPROVE_OPTION) {
          File selectedFile = fileChooser.getSelectedFile();
          parentBrowser.startDownload(uri.toURL(), selectedFile);
        }
      } catch (Exception e) {
        statusLabel.setText("Invalid download URL: " + e.getMessage());
      }
    }

    private boolean isDownloadable(String url) {
      if (url == null || url.isEmpty()) {
        return false;
      }
      String lowerCaseUrl = url.toLowerCase();
      return lowerCaseUrl.endsWith(".mp3")
          || lowerCaseUrl.endsWith(".wav")
          || lowerCaseUrl.endsWith(".ogg")
          || lowerCaseUrl.endsWith(".zip")
          || lowerCaseUrl.endsWith(".rar")
          || lowerCaseUrl.endsWith(".7z")
          || lowerCaseUrl.endsWith(".pdf")
          || lowerCaseUrl.endsWith(".doc")
          || lowerCaseUrl.endsWith(".docx")
          || lowerCaseUrl.endsWith(".xls")
          || lowerCaseUrl.endsWith(".xlsx")
          || lowerCaseUrl.endsWith(".ppt")
          || lowerCaseUrl.endsWith(".pptx")
          || lowerCaseUrl.endsWith(".exe")
          || lowerCaseUrl.endsWith(".msi")
          || lowerCaseUrl.endsWith(".dmg");
    }

    private void setupWebViewListeners() {
      Worker<Void> loadWorker = webView.getEngine().getLoadWorker();

      webView
          .getEngine()
          .locationProperty()
          .addListener(
              (obs, oldLocation, newLocation) -> {
                if (isDownloadable(newLocation)) {
                  Platform.runLater(
                      () -> {
                        try {
                          if (webView.getEngine().getHistory().getEntries().size() > 1) {
                            webView.getEngine().getHistory().go(-1);
                          }
                        } catch (Exception e) {
                          // History might have been cleared
                        }
                      });

                  SwingUtilities.invokeLater(() -> promptAndStartDownload(newLocation));
                }
              });

      loadWorker
          .progressProperty()
          .addListener(
              (obs, old, val) -> {
                if (isActiveTab()) {
                  SwingUtilities.invokeLater(
                      () -> progressBar.setValue((int) (val.doubleValue() * 100)));
                }
              });
      loadWorker
          .stateProperty()
          .addListener(
              (obs, old, state) -> {
                if (isActiveTab()) {
                  SwingUtilities.invokeLater(
                      () -> {
                        switch (state) {
                          case RUNNING:
                            statusLabel.setText("Loading...");
                            progressBar.setVisible(true);
                            svgCanvas.setURI(currentSpinnerUri.toString());
                            svgCanvas.setVisible(true);
                            break;
                          case SUCCEEDED:
                            statusLabel.setText("Page loaded");
                            progressBar.setVisible(false);
                            svgCanvas.setVisible(false);
                            updateUiForActiveTab();
                            break;
                          case FAILED:
                            statusLabel.setText("Failed to load page");
                            progressBar.setVisible(false);
                            svgCanvas.setVisible(false);
                            break;
                        }
                      });
                }
              });
      webView
          .getEngine()
          .getHistory()
          .currentIndexProperty()
          .addListener(
              (obs, old, val) -> {
                if (isActiveTab()) {
                  updateUiForActiveTab();
                }
              });
      webView
          .getEngine()
          .titleProperty()
          .addListener(
              (obs, old, title) ->
                  SwingUtilities.invokeLater(
                      () -> {
                        int index = tabbedPane.indexOfComponent(this);
                        if (index != -1) {
                          Component tabComponent = tabbedPane.getTabComponentAt(index);
                          if (tabComponent instanceof TabHeader) {
                            ((TabHeader) tabComponent).setTitle(title);
                          }
                        }
                      }));
    }

    public void loadURL(String url) {
      try {
        String verifiedUrl = verifyUrl(url);
        Platform.runLater(() -> webView.getEngine().load(verifiedUrl));
      } catch (Exception e) {
        statusLabel.setText("Invalid URL: " + e.getMessage());
      }
    }

    public void goBack() {
      Platform.runLater(() -> webView.getEngine().getHistory().go(-1));
    }

    public void goForward() {
      Platform.runLater(() -> webView.getEngine().getHistory().go(1));
    }

    public void reload() {
      Platform.runLater(() -> webView.getEngine().reload());
    }

    public void clearHistory() {
      Platform.runLater(() -> webView.getEngine().getHistory().getEntries().clear());
    }

    private boolean isActiveTab() {
      return tabbedPane.getSelectedComponent() == this;
    }
  }

  private class TabHeader extends JPanel {

    private final JLabel titleLabel;

    public TabHeader(String title) {
      super(new FlowLayout(FlowLayout.LEFT, 0, 0));
      setOpaque(false);
      titleLabel = new JLabel(title);
      titleLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 5));

      ImageIcon baseCloseIcon =
          new ImageIcon(Objects.requireNonNull(getClass().getResource("/png/close4.png")));

      ImageIcon normalIcon =
          recolorIcon(
              new ImageIcon(baseCloseIcon.getImage().getScaledInstance(12, 12, Image.SCALE_SMOOTH)),
              new Color(230, 76, 76));

      ImageIcon hoverIcon = changeBrightness(normalIcon, 1.5f);
      ImageIcon pressedIcon = changeBrightness(normalIcon, 0.7f);

      JButton closeButton = new JButton();
      closeButton.setIcon(normalIcon);
      closeButton.setRolloverIcon(hoverIcon);
      closeButton.setPressedIcon(pressedIcon);

      closeButton.setFocusPainted(false);
      closeButton.setBorderPainted(false);
      closeButton.setContentAreaFilled(false);
      closeButton.setOpaque(false);
      closeButton.setToolTipText("Close Tab");

      closeButton.addActionListener(
          e -> {
            int i = tabbedPane.indexOfTabComponent(TabHeader.this);
            if (i != -1) {
              tabbedPane.remove(i);
              if (tabbedPane.getTabCount() == 1) {
                System.exit(0);
              }
            }
          });
      add(titleLabel);
      add(closeButton);
    }

    public void setTitle(String title) {
      titleLabel.setText(title);
    }
  }

  private class TabDragAndDropListener extends MouseAdapter {
    private int draggedTabIndex = -1;

    public void mousePressed(MouseEvent e) {
      if (e.isPopupTrigger()) {
        showContextMenu(e);
      } else {
        draggedTabIndex = tabbedPane.indexAtLocation(e.getX(), e.getY());
      }
    }

    public void mouseReleased(MouseEvent e) {
      if (e.isPopupTrigger()) {
        showContextMenu(e);
      }
      draggedTabIndex = -1;
    }

    public void mouseDragged(MouseEvent e) {
      if (draggedTabIndex != -1) {
        int targetIndex = tabbedPane.indexAtLocation(e.getX(), e.getY());
        if (targetIndex != -1
            && targetIndex != draggedTabIndex
            && targetIndex < tabbedPane.getTabCount() - 1
            && draggedTabIndex != tabbedPane.getTabCount() - 1) {
          Component draggedComp = tabbedPane.getComponentAt(draggedTabIndex);
          Component draggedHeader = tabbedPane.getTabComponentAt(draggedTabIndex);
          tabbedPane.remove(draggedTabIndex);
          tabbedPane.insertTab(null, null, draggedComp, null, targetIndex);
          tabbedPane.setTabComponentAt(targetIndex, draggedHeader);
          draggedTabIndex = targetIndex;
          tabbedPane.setSelectedIndex(draggedTabIndex);
        }
      }
    }

    private void showContextMenu(MouseEvent e) {
      rightClickedTabIndex = tabbedPane.indexAtLocation(e.getX(), e.getY());
      if (rightClickedTabIndex != -1 && rightClickedTabIndex != tabbedPane.getTabCount() - 1) {
        closeOthersItem.setEnabled(tabbedPane.getTabCount() > 2);
        closeLeftItem.setEnabled(rightClickedTabIndex > 0);
        closeRightItem.setEnabled(rightClickedTabIndex < tabbedPane.getTabCount() - 2);
        tabContextMenu.show(tabbedPane, e.getX(), e.getY());
      }
    }
  }

  private enum DownloadStatus {
    DOWNLOADING,
    COMPLETED,
    CANCELED,
    FAILED
  }

  private static class DownloadTask extends SwingWorker<Void, Void> {
    private final URL url;
    private final File outputFile;
    private long totalSize = -1;
    private long downloadedSize = 0;
    private DownloadStatus status;

    public DownloadTask(URL url, File outputFile) {
      this.url = url;
      this.outputFile = outputFile;
      this.status = DownloadStatus.DOWNLOADING;
    }

    protected Void doInBackground() throws Exception {
      HttpURLConnection connection = (HttpURLConnection) url.openConnection();
      totalSize = connection.getContentLengthLong();
      try (InputStream in = connection.getInputStream();
          FileOutputStream out = new FileOutputStream(outputFile)) {
        byte[] buffer = new byte[4096];
        int bytesRead;
        while ((bytesRead = in.read(buffer)) != -1) {
          if (isCancelled()) {
            status = DownloadStatus.CANCELED;
            return null;
          }
          out.write(buffer, 0, bytesRead);
          downloadedSize += bytesRead;
          if (totalSize > 0) {
            setProgress((int) (downloadedSize * 100 / totalSize));
          }
        }
      }
      return null;
    }

    protected void done() {
      if (isCancelled()) {
        status = DownloadStatus.CANCELED;
        outputFile.delete();
      } else {
        try {
          get();
          status = DownloadStatus.COMPLETED;
          setProgress(100);
        } catch (Exception e) {
          status = DownloadStatus.FAILED;
          e.printStackTrace();
          outputFile.delete();
        }
      }
      firePropertyChange("status", null, status);
    }

    public String getFileName() {
      return outputFile.getName();
    }

    public DownloadStatus getStatus() {
      return status;
    }

    public String getSizeInfo() {
      if (totalSize <= 0) {
        return String.format("%.2f MB", downloadedSize / 1024.0 / 1024.0);
      }
      return String.format(
          "%.2f / %.2f MB", downloadedSize / 1024.0 / 1024.0, totalSize / 1024.0 / 1024.0);
    }

    public File getOutputFile() {
      return outputFile;
    }
  }

  private class SlidePanel extends JPanel {
    private static final int DEFAULT_PANEL_WIDTH = 400;
    private static final int MIN_PANEL_WIDTH = 200;
    private static final int HANDLE_VISIBLE_WIDTH = 12;
    private static final int HIDDEN_WIDTH = 0;
    private static final int ANIMATION_SPEED = 20;

    private final JTabbedPane tabbedPane;
    private final DownloadsPanel downloadsPanel;
    private final BookmarksPanel bookmarksPanel;
    private Timer animationTimer;
    private boolean isPanelVisible = false;
    private int currentPanelWidth = DEFAULT_PANEL_WIDTH;
    private final java.awt.event.AWTEventListener clickAwayListener;

    public SlidePanel(WebBrowser parent) {
      setLayout(new BorderLayout());
      setOpaque(false); // This panel itself is transparent, allowing contentPanel to show through

      // This panel holds the actual visible content and border
      JPanel contentPanel = new JPanel(new BorderLayout());
      contentPanel.setOpaque(true); // Make contentPanel opaque to cover what's behind
      contentPanel.setBackground(DARK_GRAY); // Set to your desired dark gray background
      // Removed border for a cleaner look as per image

      tabbedPane = new JTabbedPane();
      // Apply FlatLaf-like styling to the tabbed pane for a clean look
      tabbedPane.setOpaque(true);
      tabbedPane.setBackground(DARK_GRAY);
      tabbedPane.setForeground(KINDA_GRAY); // Tab text color
      tabbedPane.setTabPlacement(JTabbedPane.TOP);

      // Custom UIManager properties for a cleaner tabbed pane appearance
      UIManager.put("TabbedPane.tabInsets", new Insets(0, 10, 0, 10)); // Adjust as needed
      UIManager.put(
          "TabbedPane.contentBorderInsets", new Insets(0, 0, 0, 0)); // Remove content border
      UIManager.put(
          "TabbedPane.tabAreaInsets", new Insets(0, 0, 0, 0)); // Remove extra space around tabs
      UIManager.put(
          "TabbedPane.selectedBackground",
          new Color(48, 51, 53)); // Slightly lighter than background for selected tab

      downloadsPanel = new DownloadsPanel();
      bookmarksPanel = new BookmarksPanel();

      tabbedPane.addTab("Downloads", downloadsPanel);
      tabbedPane.addTab("Bookmarks", bookmarksPanel);

      contentPanel.add(tabbedPane, BorderLayout.CENTER);

      // --- Create the drag handle ---
      JPanel handle = new JPanel();
      handle.setPreferredSize(new Dimension(HANDLE_VISIBLE_WIDTH, 0));
      handle.setCursor(Cursor.getPredefinedCursor(Cursor.W_RESIZE_CURSOR));
      handle.setBackground(new Color(60, 63, 65)); // Slightly different dark gray for handle
      // Removed border for a flat, clean look as per image

      ResizeListener resizeListener = new ResizeListener();
      handle.addMouseListener(resizeListener);
      handle.addMouseMotionListener(resizeListener);

      add(handle, BorderLayout.WEST);
      add(contentPanel, BorderLayout.CENTER);

      // --- Listener to hide the panel when clicking elsewhere ---
      this.clickAwayListener =
          event -> {
            if (event instanceof MouseEvent && event.getID() == MouseEvent.MOUSE_PRESSED) {
              if (isPanelVisible) {
                Component source = (Component) ((MouseEvent) event).getSource();
                // If the click did not originate from this panel or its children, hide it.
                if (SwingUtilities.getWindowAncestor(source)
                    == SwingUtilities.getWindowAncestor(this)
                    && !SwingUtilities.isDescendingFrom(source, this)) {
                  hidePanel();
                }
              }
            }
          };
    }

    // This is called by the main frame's resize listener
    public void updatePosition() {
      if (getParent() == null) return;
      int parentWidth = getParent().getWidth();
      int parentHeight = getParent().getHeight();
      int width = isPanelVisible ? currentPanelWidth : HIDDEN_WIDTH;
      int x = parentWidth - width;
      setBounds(x, 0, width, parentHeight);
      revalidate();
    }

    // Called from menu items
    public void showPanel(int tabIndex) {
      tabbedPane.setSelectedIndex(tabIndex);
      if (isPanelVisible) return;
      isPanelVisible = true;
      animatePanelToWidth(currentPanelWidth);
    }

    public void hidePanel() {
      if (!isPanelVisible) return;
      // Save the current width before hiding, but only if it's a reasonable size
      if (getWidth() > MIN_PANEL_WIDTH) {
        currentPanelWidth = getWidth();
      }
      isPanelVisible = false;
      animatePanelToWidth(HIDDEN_WIDTH);
    }

    private void animatePanelToWidth(int targetWidth) {
      if (animationTimer != null && animationTimer.isRunning()) {
        animationTimer.stop();
      }
      final int startWidth = getWidth();

      // If we are already at the target, perform listener logic and exit
      if (startWidth == targetWidth) {
        if (isPanelVisible) {
          Toolkit.getDefaultToolkit()
              .addAWTEventListener(clickAwayListener, java.awt.AWTEvent.MOUSE_EVENT_MASK);
        } else {
          Toolkit.getDefaultToolkit().removeAWTEventListener(clickAwayListener);
        }
        return;
      }

      animationTimer =
          new Timer(
              5,
              e -> {
                int currentWidth = getWidth();
                int step = (targetWidth > startWidth) ? ANIMATION_SPEED : -ANIMATION_SPEED;
                int newWidth = currentWidth + step;

                // Check if animation is finished
                if ((step > 0 && newWidth >= targetWidth)
                    || (step < 0 && newWidth <= targetWidth)) {
                  newWidth = targetWidth;
                  ((Timer) e.getSource()).stop();

                  if (isPanelVisible) {
                    Toolkit.getDefaultToolkit()
                        .addAWTEventListener(clickAwayListener, java.awt.AWTEvent.MOUSE_EVENT_MASK);
                  } else {
                    Toolkit.getDefaultToolkit().removeAWTEventListener(clickAwayListener);
                  }
                }

                int newX = getParent().getWidth() - newWidth;
                setBounds(newX, 0, newWidth, getParent().getHeight());
                revalidate();
              });
      animationTimer.start();
    }

    public void startDownload(URL url, File file) {
      showPanel(0);
      downloadsPanel.startDownload(url, file);
    }

    public void updateBookmarksTable() {
      bookmarksPanel.updateTable();
    }

    // --- Inner class to handle dragging and resizing ---
    private class ResizeListener extends MouseAdapter {
      private int initialX;
      private int initialWidth;

      @Override
      public void mousePressed(MouseEvent e) {
        initialX = e.getXOnScreen();
        initialWidth = getWidth();

        // Stop any ongoing animation
        if (animationTimer != null && animationTimer.isRunning()) {
          animationTimer.stop();
        }
      }

      @Override
      public void mouseDragged(MouseEvent e) {
        int deltaX = e.getXOnScreen() - initialX;
        int newWidth = initialWidth - deltaX;

        // Enforce min/max size
        if (newWidth < MIN_PANEL_WIDTH) {
          newWidth = MIN_PANEL_WIDTH;
        }
        if (newWidth
            > getParent().getWidth() - 200) { // Max width, leave some space for main content
          newWidth = getParent().getWidth() - 200;
        }

        int newX = getParent().getWidth() - newWidth;
        setBounds(newX, 0, newWidth, getParent().getHeight());
        revalidate();
      }
    }
  }

  private class DownloadsPanel extends JPanel {
    private final DownloadsTableModel tableModel;
    private final JTable table;
    private final ExecutorService executorService;

    public DownloadsPanel() {
      setLayout(new BorderLayout());
      executorService = Executors.newFixedThreadPool(4);

      tableModel = new DownloadsTableModel();
      table = new JTable(tableModel);
      setupTable();

      JScrollPane scrollPane = new JScrollPane(table);
      add(scrollPane, BorderLayout.CENTER);

      JButton clearAllButton = new JButton("Clear All Finished");
      clearAllButton.addActionListener(e -> tableModel.clearFinished());
      JPanel southPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
      southPanel.add(clearAllButton);
      add(southPanel, BorderLayout.SOUTH);
    }

    private void setupTable() {
      table.setRowHeight(25);
      table.setShowGrid(false);
      table.setIntercellSpacing(new Dimension(0, 0));
      table.getColumnModel().getColumn(3).setCellRenderer(new ProgressBarRenderer());
      table.getColumnModel().getColumn(0).setPreferredWidth(150);
      table.addMouseListener(
          new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
              if (e.getClickCount() == 2) {
                int row = table.getSelectedRow();
                if (row != -1) {
                  DownloadTask task = tableModel.getTaskAt(row);
                  if (task.getStatus() == DownloadStatus.COMPLETED) {
                    try {
                      Desktop.getDesktop().open(task.getOutputFile());
                    } catch (IOException ex) {
                      statusLabel.setText("Error opening file.");
                    }
                  }
                }
              }
            }
          });

      // In setupTable method for DownloadsPanel and BookmarksPanel
      table.setDefaultRenderer(
          Object.class,
          new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(
                JTable table,
                Object value,
                boolean isSelected,
                boolean hasFocus,
                int row,
                int column) {
              Component c =
                  super.getTableCellRendererComponent(
                      table, value, isSelected, hasFocus, row, column);
              if (!isSelected) {
                c.setBackground(
                    row % 2 == 0
                        ? new Color(39, 46, 48)
                        : new Color(48, 51, 53)); // Alternating rows
              }
              c.setForeground(Color.WHITE); // Text color
              setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5)); // Add padding
              return c;
            }
          });
      table.setSelectionBackground(new Color(0, 120, 215)); // Selection color
      table.setSelectionForeground(Color.WHITE);
      table.getTableHeader().setOpaque(false);
      table.getTableHeader().setBackground(DARK_GRAY);
      table.getTableHeader().setForeground(KINDA_GRAY);
    }

    public void startDownload(URL url, File file) {
      DownloadTask task = new DownloadTask(url, file);
      task.addPropertyChangeListener(tableModel);
      tableModel.addTask(task);
      executorService.submit(task);
    }
  }

  private static class DownloadsTableModel extends AbstractTableModel
      implements PropertyChangeListener {
    private final List<DownloadTask> tasks = new CopyOnWriteArrayList<>();
    private final String[] columnNames = {"File", "Size", "Status", "Progress"};

    public void addTask(DownloadTask task) {
      tasks.addFirst(task);
      fireTableRowsInserted(0, 0);
    }

    public DownloadTask getTaskAt(int row) {
      return tasks.get(row);
    }

    public void clearFinished() {
      tasks.removeIf(
          task ->
              task.getStatus() == DownloadStatus.COMPLETED
                  || task.getStatus() == DownloadStatus.CANCELED
                  || task.getStatus() == DownloadStatus.FAILED);
      fireTableDataChanged();
    }

    @Override
    public int getRowCount() {
      return tasks.size();
    }

    @Override
    public int getColumnCount() {
      return columnNames.length;
    }

    @Override
    public String getColumnName(int column) {
      return columnNames[column];
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
      DownloadTask task = tasks.get(rowIndex);
      switch (columnIndex) {
        case 0:
          return task.getFileName();
        case 1:
          return task.getSizeInfo();
        case 2:
          return task.getStatus();
        case 3:
          return task.getProgress();
        default:
          return null;
      }
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
      DownloadTask sourceTask = (DownloadTask) evt.getSource();
      final int row = tasks.indexOf(sourceTask);
      if (row != -1) {
        SwingUtilities.invokeLater(() -> fireTableRowsUpdated(row, row));
      }
    }
  }

  private static class ProgressBarRenderer extends JProgressBar
      implements javax.swing.table.TableCellRenderer {
    public ProgressBarRenderer() {
      super(0, 100);
      setStringPainted(true);
    }

    @Override
    public Component getTableCellRendererComponent(
        JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
      setValue((Integer) value);
      return this;
    }
  }

  private class BookmarksPanel extends JPanel {
    private final BookmarksTableModel tableModel;

    public BookmarksPanel() {
      setLayout(new BorderLayout());
      tableModel = new BookmarksTableModel();
      JTable table = new JTable(tableModel);
      setupTable(table);

      JScrollPane scrollPane = new JScrollPane(table);
      add(scrollPane, BorderLayout.CENTER);

      JButton removeButton = new JButton("Remove Selected");
      removeButton.addActionListener(
          e -> {
            int[] selectedRows = table.getSelectedRows();
            if (selectedRows.length > 0) {
              List<String> urlsToRemove = new ArrayList<>();
              for (int row : selectedRows) {
                urlsToRemove.add(tableModel.getBookmarkAt(row).url);
              }
              urlsToRemove.forEach(bookmarks::remove);
              saveBookmarksToPrefs();
            }
          });
      JPanel southPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
      southPanel.add(removeButton);
      add(southPanel, BorderLayout.SOUTH);

      updateTable();
    }

    private void setupTable(JTable table) {
      table.setRowHeight(25);
      table.setShowGrid(false);
      table.setIntercellSpacing(new Dimension(0, 0));
      TableColumn titleColumn = table.getColumnModel().getColumn(0);
      titleColumn.setPreferredWidth(150);
      TableColumn urlColumn = table.getColumnModel().getColumn(1);
      urlColumn.setPreferredWidth(250);
    }

    public void updateTable() {
      tableModel.updateData();
    }
  }

  private class BookmarkEntry {
    String title;
    String url;

    BookmarkEntry(String title, String url) {
      this.title = title;
      this.url = url;
    }
  }

  private class BookmarksTableModel extends AbstractTableModel {
    private final List<BookmarkEntry> bookmarkEntries = new ArrayList<>();
    private final String[] columnNames = {"Title", "URL"};

    public void updateData() {
      bookmarkEntries.clear();
      bookmarkEntries.addAll(
          bookmarks.entrySet().stream()
              .map(entry -> new BookmarkEntry(entry.getValue(), entry.getKey()))
              .toList());
      fireTableDataChanged();
    }

    public BookmarkEntry getBookmarkAt(int row) {
      return bookmarkEntries.get(row);
    }

    @Override
    public int getRowCount() {
      return bookmarkEntries.size();
    }

    @Override
    public int getColumnCount() {
      return columnNames.length;
    }

    @Override
    public String getColumnName(int column) {
      return columnNames[column];
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
      BookmarkEntry entry = bookmarkEntries.get(rowIndex);
      return columnIndex == 0 ? entry.title : entry.url;
    }
  }

  private class ButtonReorderListener extends MouseAdapter {

    private final JPanel panel;
    private final Preferences prefs;
    private Timer holdTimer;
    private JButton sourceButton;
    private JButton draggedButton = null;

    public ButtonReorderListener(JPanel panel, Preferences prefs) {
      this.panel = panel;
      this.prefs = prefs;
    }

    @Override
    public void mousePressed(MouseEvent e) {
      if (!SwingUtilities.isLeftMouseButton(e)) {
        return;
      }

      sourceButton = (JButton) e.getSource();

      holdTimer =
          new Timer(
              5000,
              (actionEvent) -> {
                draggedButton = sourceButton;
                panel.setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
                draggedButton.setBorder(BorderFactory.createLineBorder(Color.YELLOW, 2));
              });
      holdTimer.setRepeats(false);
      holdTimer.start();
    }

    @Override
    public void mouseDragged(MouseEvent e) {
      if (draggedButton == null) {
        return;
      }

      Point dragPoint = SwingUtilities.convertPoint(sourceButton, e.getPoint(), panel);
      Component targetComponent = panel.getComponentAt(dragPoint);

      if (targetComponent == null
          || targetComponent == draggedButton
          || !(targetComponent instanceof JButton)) {
        return;
      }

      Component[] components = panel.getComponents();
      List<Component> componentList = Arrays.asList(components);
      int targetIndex = componentList.indexOf(targetComponent);

      if (targetIndex != -1) {
        panel.remove(draggedButton);
        panel.add(draggedButton, targetIndex);
        panel.revalidate();
        panel.repaint();
      }
    }

    @Override
    public void mouseReleased(MouseEvent e) {
      if (holdTimer != null) {
        holdTimer.stop();
      }

      if (draggedButton != null) {
        panel.setCursor(Cursor.getDefaultCursor());
        draggedButton.setBorder(UIManager.getBorder("Button.border"));
        saveButtonOrder();
      }

      draggedButton = null;
      sourceButton = null;
    }

    private void saveButtonOrder() {
      StringBuilder order = new StringBuilder();
      for (Component comp : panel.getComponents()) {
        if (comp instanceof JButton) {
          Object id = ((JButton) comp).getClientProperty("id");
          if (id instanceof String) {
            if (order.length() > 0) {
              order.append(",");
            }
            order.append(id);
          }
        }
      }
      prefs.put("navButtonOrder", order.toString());
    }
  }

  public static void main(String[] args) throws UnsupportedLookAndFeelException {

    FlatDarkLaf.setup();

    UIManager.put("TabbedPane.tabType", "card");
    UIManager.put("TabbedPane.tabArc", 999);
    UIManager.put("TabbedPane.tabInsets", new Insets(0, 10, 0, 10));
    UIManager.put("TabbedPane.showTabSeparators", false);
    UIManager.put("TabbedPane.selectedBackground", new Color(80, 80, 80));

    UIManager.put("TabbedPane.tabHeight", 30); // Adjust tab height
    UIManager.put("TabbedPane.selectedBackground", new Color(48, 51, 53)); // Darker selected tab
    UIManager.put("TabbedPane.background", new Color(39, 46, 48)); // Background of the tab area
    UIManager.put(
        "TabbedPane.contentBackground",
        new Color(39, 46, 48)); // Background of the content area below tabs
    UIManager.put("TabbedPane.shadow", new Color(0, 0, 0, 0)); // Remove shadow
    UIManager.put("TabbedPane.darkShadow", new Color(0, 0, 0, 0)); // Remove dark shadow
    UIManager.put("TabbedPane.light", new Color(0, 0, 0, 0)); // Remove light
    UIManager.put("TabbedPane.highlight", new Color(0, 0, 0, 0)); // Remove highlight
    UIManager.put("TabbedPane.focus", new Color(0, 0, 0, 0)); // Remove focus border
    UIManager.put("TabbedPane.border", BorderFactory.createEmptyBorder()); // Remove default border

    SwingUtilities.invokeLater(WebBrowser::new);
  }
}