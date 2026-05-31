package xxx.com.mp3;

import com.formdev.flatlaf.FlatDarkLaf;
import com.mpatric.mp3agic.*;

import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.*;
import java.awt.geom.GeneralPath;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import javax.imageio.ImageIO;
import javax.sound.sampled.*;
import javax.swing.*;
import javax.swing.border.BevelBorder;
import javax.swing.border.TitledBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.plaf.basic.BasicSplitPaneDivider;
import javax.swing.plaf.basic.BasicSplitPaneUI;

/**
 * A Swing-based GUI for the WaveformGenerator class with audio playback, scrubbing, and a live
 * visualizer.
 */
public class Mp3Visualizer extends JFrame {

  public static final String ADD_FILE_ICON_FILE = "png/addFile.png";
  public static final String SCRUBBER_ICON_FILE = "png/scrubber.png";
  public static final String FULLSCREEN_ICON_FILE = "png/fullscreen.png";
  public static final String SCREENSHOT_ICON_FILE = "png/screenshot.png";
  public static final String VOLUME_ICON_FILE = "png/volume.png";
  public static final String PLAY_ICON_FILE = "png/play.png";
  public static final String PAUSE_ICON_FILE = "png/pause.png";
  public static final String EJECT_ICON_FILE = "png/eject.png";
  public static final String EQUALIZER_ICON_FILE = "png/equalizer.png";
  public static final String ACTIVITY_GIF_FILE = "gif/activity.gif";
  private static final String EDIT_EXIF_ICON_FILE = "png/editexif.png";
  public static final String FREQUENCY_ICON_FILE = "png/frequency.png";
  public static final String VISUAL_ADJUSTMENTS_ICON_FILE = "png/settings.png";
  public static final String SAVE_ICON_FILE = "png/save.png";
  public static final String NOTES_ICON_FILE = "png/notes.png";
  public static final String WAVEFORM_ICON_FILE = "png/waveform.png";
  public static final String TAB_ICON_FILE = "png/tab.png";
  public static final String INFO_ICON_FILE = "png/info.png";
  private static final String APP_ICON_FILE = "png/appIcon.png";

  // --- GUI Components ---
  private JButton playButton, ejectButton;
  private JLabel statusLabel;
  private JPanel bgColorPreview;
  private WaveformPanel waveformPanel;
  private LiveWaveformPanel liveWaveformPanel;
  private JSlider colorSlider;
  private JSlider volumeSlider;
  private JSlider panSlider;
  private JLabel activityGifLabel;
  private JLabel clockLabel;
  //private VerticalGauge dbGauge; // ADD THIS LINE
  //private VerticalGauge hzGauge; // ADD THIS LINE
  private HorizontalGauge dbGauge; // ADD THIS LINE
  private HorizontalGauge hzGauge; // ADD THIS LINE

  // --- EXIF/ID3 Editor UI Components ---
  private JButton exifButton, saveExifButton;
  private JPanel exifPanel;
  private final JTextField titleField = new JTextField();
  private final JTextField artistField = new JTextField();
  private final JTextField albumField = new JTextField();
  private final JTextField yearField = new JTextField();
  private final JTextField genreField = new JTextField();
  private final JTextArea commentArea = new JTextArea(3, 10);
  private final JLabel albumArtLabel = new JLabel();

  // --- Equalizer UI Components ---
  private JButton equalizerButton;
  private JPanel equalizerPanel;
  private final JSlider[] eqSliders = new JSlider[7];

  // --- Frequency Analyzer UI Components ---
  private JButton frequencyButton;
  private JPanel frequencyPanel;
  private SpectrumAnalyzerPanel spectrumAnalyzerPanel;
  private boolean isFrequencyPanelVisible = false;
  private Timer frequencyPanelAnimator;

  // --- Visual Adjustments UI Components ---
  private JButton visualAdjustmentsButton;
  private JPanel visualAdjustmentsPanel;
  private boolean isVisualAdjustmentsPanelVisible = false;
  private Timer visualAdjustmentsPanelAnimator;
  private JRadioButton bgRadio,
      highAmpRadio,
      lowAmpRadio,
      liveOutRadio,
      clockRadio,
      freqRadio,
      scrubberRadio;
  private ButtonGroup colorSelectionGroup;
  private JComboBox<LiveWaveformPanel.WaveformStyle> liveWaveformStyleChooser;
  private JToggleButton rememberColorsButton;
  private boolean isUpdatingColorControls = false; // Flag to prevent listener loops

  // --- Volume Slider Panel ---
  private JButton volumeButton;
  private JPanel volumeSlidingPanel;
  private boolean isVolumeSlidingPanelVisible = false;
  private Timer volumeSlidingPanelAnimator;

  // --- Notes Panel ---
  private JButton notesButton;
  private JPanel notesPanel;
  private JTextArea notesArea;
  private boolean isNotesPanelVisible = false;
  private Timer notesPanelAnimator;

  // --- Info Panel ---
  private JButton infoButton;
  private JPanel infoPanel;
  private JLabel sampleRateLabel,
      bitRateLabel,
      channelsLabel,
      trackLengthLabel,
      encodingTypeLabel,
      fileSizeLabel;
  private boolean isInfoPanelVisible = false;
  private Timer infoPanelAnimator;

  // --- EXIF/ID3 Data ---
  private Mp3File mp3file;
  private byte[] albumImageBytes;
  private String albumImageMimeType;
  private boolean isExifPanelVisible = false;
  private Timer exifPanelAnimator;
  private boolean isEqualizerPanelVisible = false;
  private Timer equalizerPanelAnimator;

  // --- Data Fields ---
  private File inputFile;
  private Color backgroundColor;
  private volatile boolean isPlaying = false;
  private Thread playbackThread;
  private long currentMicros = 0;
  private long audioDurationMicros = -1;
  private boolean wasPlayingBeforeScrub = false;

  // In Mp3Visualizer class, with the other volatile fields
  private volatile double currentDb = -Double.MAX_VALUE;
  private volatile double currentPeakHz = 0.0;
  private volatile double currentRms = 0.0; // Add this

  // Colors of the waveform visualizer and live output
  private Color lowAmpColor = Color.GREEN;
  private Color highAmpColor = new Color(43, 96, 221);
  private Color liveOutputColor = Color.RED;
  private Color spectrumColor = new Color(123, 0, 156);
  private Color clockColor = Color.GREEN;
  private Color scrubberColor = new Color(54, 54, 54);

  // --- In-Memory Audio Data ---
  private byte[] pcmData;
  private AudioFormat pcmFormat;
  private SourceDataLine dataLine;
  private Timer uiUpdateTimer;
  private long playbackStartOffsetMicros = 0;
  private Timer resizeTimer;
  private byte[] originalPcmData;
  private long originalAudioDurationMicros = -1;


  private final java.util.List<byte[]> pcmDataHistory = new java.util.ArrayList<>();
  private final java.util.List<Long> durationHistory = new java.util.ArrayList<>();

  // --- Fullscreen State ---
  private boolean isFullScreen = false;
  private Rectangle normalBounds;

  // --- Overlay Icons ---
  private ImageIcon dragDropOverlayIcon,
      scaledFullScreenIcon,
      scaledScreenshotIcon,
      volumeIcon,
      scrubberIcon,
      saveIcon,
      appIcon,
      notesIcon,
      infoIcon,
      tabIcon;

  private static ImageIcon waveformIcon;

  private ImageIcon playIcon,
      pauseIcon,
      ejectIcon,
      exifIcon,
      equalizerIcon,
      frequencyIcon,
      visualAdjustmentsIcon,
      activityGifIcon;

  private ImageIcon menuPlayIcon,
      menuPauseIcon,
      menuEjectIcon,
      menuFullscreenIcon,
      menuZoomInIcon,
      menuZoomOutIcon,
      menuZoomResetIcon,
      menuOpenIcon,
      menuExitIcon;

  // --- Fields for Collapsible Panels ---
  private JSplitPane mainSplitPane;
  private Timer animationTimer;

  // --- Equalizer DSP Fields ---
  private final BiquadFilter[][] eqFilters = new BiquadFilter[2][7];
  private static final float[] EQ_FREQUENCIES = {
      150.0f, 60.0f, 250.0f, 1000.0f, 4000.0f, 16000.0f, 6000.0f
  };
  private static final String[] EQ_LABELS = {"Bass", "60", "250", "1k", "4k", "16k", "Treble"};

  // --- Marquee Title Fields ---
  private Timer titleMarqueeTimer;
  private String baseMarqueeText;
  private String scrollingCanvas;
  private int marqueePosition;
  private static final String DEFAULT_TITLE = "MP3 Waveform Visualizer";

  // --- Persistence ---
  private final Preferences prefs = Preferences.userNodeForPackage(Mp3Visualizer.class);

  private enum CollapseDirection {
    TO_START,
    TO_END
  }

  public Mp3Visualizer() {
    super(DEFAULT_TITLE);
    for (int channel = 0; channel < 2; channel++) {
      for (int band = 0; band < 7; band++) {
        eqFilters[channel][band] = new BiquadFilter();
      }
    }

    initUI();
  }

  private ImageIcon scaleIcon(ImageIcon icon, int width, int height) {

    if (icon == null) return null;

    Image img = icon.getImage();
    Image scaledImg = img.getScaledInstance(width, height, Image.SCALE_SMOOTH);

    return new ImageIcon(scaledImg);
  }

  private void loadExternalResources() {

    try {

      URL appIconUrl = getClass().getResource("/" + APP_ICON_FILE);
      if (appIconUrl != null) appIcon = new ImageIcon(appIconUrl);

      URL addFileUrl = getClass().getResource("/" + ADD_FILE_ICON_FILE);
      if (addFileUrl != null) dragDropOverlayIcon = new ImageIcon(addFileUrl);

      URL scrubberUrl = getClass().getResource("/" + SCRUBBER_ICON_FILE);
      if (scrubberUrl != null) scrubberIcon = new ImageIcon(scrubberUrl);

      URL fullscreenUrl = getClass().getResource("/" + FULLSCREEN_ICON_FILE);
      if (fullscreenUrl != null)
        scaledFullScreenIcon = scaleIcon(new ImageIcon(fullscreenUrl), 25, 25);

      URL screenshotUrl = getClass().getResource("/" + SCREENSHOT_ICON_FILE);
      if (screenshotUrl != null)
        scaledScreenshotIcon = scaleIcon(new ImageIcon(screenshotUrl), 25, 25);

      URL volumeUrl = getClass().getResource("/" + VOLUME_ICON_FILE);
      if (volumeUrl != null) volumeIcon = scaleIcon(new ImageIcon(volumeUrl), 24, 24);

      URL playUrl = getClass().getResource("/" + PLAY_ICON_FILE);
      if (playUrl != null) playIcon = scaleIcon(new ImageIcon(playUrl), 24, 24);

      URL pauseUrl = getClass().getResource("/" + PAUSE_ICON_FILE);
      if (pauseUrl != null) pauseIcon = scaleIcon(new ImageIcon(pauseUrl), 24, 24);

      URL ejectUrl = getClass().getResource("/" + EJECT_ICON_FILE);
      if (ejectUrl != null) ejectIcon = scaleIcon(new ImageIcon(ejectUrl), 24, 24);

      URL exifUrl = getClass().getResource("/" + EDIT_EXIF_ICON_FILE);
      if (exifUrl != null) exifIcon = scaleIcon(new ImageIcon(exifUrl), 24, 24);

      URL equalizerUrl = getClass().getResource("/" + EQUALIZER_ICON_FILE);
      if (equalizerUrl != null) equalizerIcon = scaleIcon(new ImageIcon(equalizerUrl), 24, 24);

      URL frequencyUrl = getClass().getResource("/" + FREQUENCY_ICON_FILE);
      if (frequencyUrl != null) frequencyIcon = scaleIcon(new ImageIcon(frequencyUrl), 24, 24);

      URL visualAdjustmentsUrl = getClass().getResource("/" + VISUAL_ADJUSTMENTS_ICON_FILE);
      if (visualAdjustmentsUrl != null)
        visualAdjustmentsIcon = scaleIcon(new ImageIcon(visualAdjustmentsUrl), 24, 24);

      URL activityUrl = getClass().getResource("/" + ACTIVITY_GIF_FILE);
      if (activityUrl != null) activityGifIcon = scaleIcon(new ImageIcon(activityUrl), 24, 24);

      URL saveUrl = getClass().getResource("/" + SAVE_ICON_FILE);
      if (saveUrl != null) saveIcon = scaleIcon(new ImageIcon(saveUrl), 20, 20);

      URL notesUrl = getClass().getResource("/" + NOTES_ICON_FILE);
      if (notesUrl != null) notesIcon = scaleIcon(new ImageIcon(notesUrl), 24, 24);

      URL waveformIconUrl = getClass().getResource("/" + WAVEFORM_ICON_FILE);
      if (waveformIconUrl != null) waveformIcon = scaleIcon(new ImageIcon(waveformIconUrl), 24, 24);

      URL tabUrl = getClass().getResource("/" + TAB_ICON_FILE);
      if (tabUrl != null) tabIcon = new ImageIcon(tabUrl);

      URL infoUrl = getClass().getResource("/" + INFO_ICON_FILE);
      if (infoUrl != null) infoIcon = scaleIcon(new ImageIcon(infoUrl), 24, 24);

      URL menuPlayUrl = getClass().getResource("/" + PLAY_ICON_FILE);
      if (menuPlayUrl != null) menuPlayIcon = scaleIcon(new ImageIcon(menuPlayUrl), 16, 16);

      URL menuPauseUrl = getClass().getResource("/" + PAUSE_ICON_FILE);
      if (menuPauseUrl != null) menuPauseIcon = scaleIcon(new ImageIcon(menuPauseUrl), 16, 16);

      URL menuEjectUrl = getClass().getResource("/" + EJECT_ICON_FILE);
      if (menuEjectUrl != null) menuEjectIcon = scaleIcon(new ImageIcon(menuEjectUrl), 16, 16);

      URL menuFullscreenUrl = getClass().getResource("/" + FULLSCREEN_ICON_FILE);
      if (menuFullscreenUrl != null) menuFullscreenIcon = scaleIcon(new ImageIcon(menuFullscreenUrl), 16, 16);

      URL menuZoomInUrl = getClass().getResource("/png/zoomIn.png");
      if (menuZoomInUrl != null) menuZoomInIcon = scaleIcon(new ImageIcon(menuZoomInUrl), 16, 16);

      URL menuZoomOutUrl = getClass().getResource("/png/zoomOut.png");
      if (menuZoomOutUrl != null) menuZoomOutIcon = scaleIcon(new ImageIcon(menuZoomOutUrl), 16, 16);

      URL menuZoomResetUrl = getClass().getResource("/png/zoomReset.png");
      if (menuZoomResetUrl != null) menuZoomResetIcon = scaleIcon(new ImageIcon(menuZoomResetUrl), 16, 16);

      URL menuOpenUrl = getClass().getResource("/png/open.png");
      if (menuOpenUrl != null) menuOpenIcon = scaleIcon(new ImageIcon(menuOpenUrl), 16, 16);

      URL menuExitUrl = getClass().getResource("/png/exit.png");
      if (menuExitUrl != null) menuExitIcon = scaleIcon(new ImageIcon(menuExitUrl), 16, 16);

    } catch (Exception e) {

      System.err.println("Error loading icon resources: " + e.getMessage());
      // Don't exit, the app can still run without icons.
    }
  }

  private void initUI() {

    loadExternalResources();

    setIconImage(appIcon.getImage());

    this.backgroundColor = UIManager.getColor("Panel.background");
    if (this.backgroundColor == null) this.backgroundColor = Color.BLACK;

    // Set default background color. This might be overwritten by loaded preferences.
    getContentPane().setBackground(this.backgroundColor);

    setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    addWindowListener(
        new java.awt.event.WindowAdapter() {
          @Override
          public void windowClosing(java.awt.event.WindowEvent windowEvent) {
            stopPlayback();
          }
        });

    setLayout(new BorderLayout());

    // Panel creations need to happen before listeners are added.
    liveWaveformPanel = new LiveWaveformPanel(activityGifIcon, tabIcon);

    JPanel leftControlsPanel = createLeftControlsPanel();

    waveformPanel = new WaveformPanel(this, scrubberIcon, dragDropOverlayIcon);
    waveformPanel.setOpaque(false);
    waveformPanel.setTransferHandler(new FileDragDropHandler());

    activityGifLabel = new JLabel();
    activityGifLabel.setIcon(scaleIcon(activityGifIcon, 25, 25));
    activityGifLabel.setVisible(false);

    JPanel gifContainerPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
    gifContainerPanel.setOpaque(false);
    gifContainerPanel.add(activityGifLabel);

    JPanel waveformWrapperPanel = new JPanel(new BorderLayout());
    waveformWrapperPanel.setOpaque(false); // See through to the content pane
    waveformWrapperPanel.add(waveformPanel, BorderLayout.CENTER);
    waveformWrapperPanel.add(gifContainerPanel, BorderLayout.SOUTH);

    liveWaveformPanel.setWaveformColor(liveOutputColor);
    liveWaveformPanel.setPreferredSize(new Dimension(0, 100));
    liveWaveformPanel.setBorder(BorderFactory.createTitledBorder("Live Output"));

    JSplitPane verticalSplitPane =
        new JSplitPane(JSplitPane.VERTICAL_SPLIT, waveformWrapperPanel, liveWaveformPanel);
    verticalSplitPane.setResizeWeight(0.8);
    verticalSplitPane.setBorder(BorderFactory.createEmptyBorder());
    addAnimatedCollapsibility(verticalSplitPane, CollapseDirection.TO_END);

    mainSplitPane =
        new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftControlsPanel, verticalSplitPane);
    mainSplitPane.setBorder(BorderFactory.createEmptyBorder());
    addAnimatedCollapsibility(mainSplitPane, CollapseDirection.TO_START);

    statusLabel = new JLabel("Ready. Drag and drop an audio file to begin.", SwingConstants.CENTER);
    JPanel statusPanel = new JPanel(new BorderLayout());
    statusPanel.setBorder(BorderFactory.createEmptyBorder(2, 10, 5, 10));
    statusPanel.add(statusLabel, BorderLayout.CENTER);

    add(mainSplitPane, BorderLayout.CENTER);
    add(statusPanel, BorderLayout.SOUTH);

    addListeners();
    updateEjectButtonState();

    resizeTimer = new Timer(250, e -> regenerateWaveformImage());
    resizeTimer.setRepeats(false);
    waveformPanel.addComponentListener(
        new ComponentAdapter() {
          @Override
          public void componentResized(ComponentEvent e) {
            if (pcmData != null) resizeTimer.restart();
          }
        });

    addComponentListener(
        new ComponentAdapter() {
          @Override
          public void componentResized(ComponentEvent e) {
            updateMarqueeCanvas();
          }
        });

    loadColorPreferences(); // Load saved colors after UI is initialized
    loadNotes(); // Load saved notes

    pack();
    setSize(1000, 700);
    setLocationRelativeTo(null);

    SwingUtilities.invokeLater(() -> mainSplitPane.setDividerLocation(0));
  }

  // Replace the entire createLeftControlsPanel method with this one
  private JPanel createLeftControlsPanel() {

    JPanel leftPanel = new JPanel();

    leftPanel.setLayout(new BoxLayout(leftPanel, BoxLayout.Y_AXIS));
    leftPanel.setBorder(BorderFactory.createEmptyBorder(10, 5, 10, 5));
    leftPanel.setMinimumSize(new Dimension(250, 0));

    // --- Main Playback Panel ---
    JPanel playbackPanel = new JPanel(new BorderLayout(15, 0));
    playbackPanel.setBorder(BorderFactory.createTitledBorder("Playback"));

    // --- Clock Display (Top) ---
    clockLabel = new JLabel("00:00.000", SwingConstants.CENTER);
    clockLabel.setFont(new Font("Monospaced", Font.BOLD, 24));
    clockLabel.setForeground(new Color(50, 255, 50));
    clockLabel.setBackground(Color.BLACK);
    clockLabel.setOpaque(true);
    clockLabel.setBorder(
        BorderFactory.createCompoundBorder(
            BorderFactory.createBevelBorder(BevelBorder.LOWERED),
            BorderFactory.createEmptyBorder(5, 10, 5, 10)));

    playbackPanel.add(clockLabel, BorderLayout.NORTH);

    // --- Gauges ---
    dbGauge = new HorizontalGauge("dB", -60, 0, -60);
    dbGauge.setToolTipText("Decibels (Full Scale)");
    hzGauge = new HorizontalGauge("Hz", 20, 22050, 0, true);
    hzGauge.setToolTipText("Dominant Frequency (Pitch)");

    JPanel meterPanel = new JPanel(new GridLayout(2, 1, 0, 0)); // Stacks gauges
    meterPanel.add(dbGauge);
    meterPanel.add(hzGauge);

    // --- Buttons ---
    playButton = new JButton();
    playButton.setIcon(playIcon);
    playButton.setToolTipText("Play");

    ejectButton = new JButton();
    ejectButton.setIcon(ejectIcon);
    ejectButton.setToolTipText("Eject File");

    JPanel buttonPanel = new JPanel(new GridLayout(1, 2, 2, 2));
    buttonPanel.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0)); // Add top padding
    buttonPanel.add(playButton);
    buttonPanel.add(ejectButton);

    // --- Container for Gauges and Buttons ---
    JPanel gaugeAndButtonContainer = new JPanel(new BorderLayout());
    gaugeAndButtonContainer.add(meterPanel, BorderLayout.CENTER);
    gaugeAndButtonContainer.add(buttonPanel, BorderLayout.SOUTH); // Buttons below gauges

    playbackPanel.add(gaugeAndButtonContainer, BorderLayout.CENTER); // Add the combined panel

    // --- Final Assembly ---
    playbackPanel.setMaximumSize(
        new Dimension(Short.MAX_VALUE, playbackPanel.getPreferredSize().height));

    JPanel detailsPanel = new JPanel(new BorderLayout());
    detailsPanel.setBorder(BorderFactory.createTitledBorder("Configurations"));
    JPanel buttonContainer = new JPanel(new GridLayout(7, 1, 0, 5));

    exifButton = new JButton(exifIcon);
    exifButton.setToolTipText("EXIF");

    equalizerButton = new JButton(equalizerIcon);
    equalizerButton.setToolTipText("Equalizer");

    frequencyButton = new JButton(frequencyIcon);
    frequencyButton.setToolTipText("Frequency Spectrum");

    visualAdjustmentsButton = new JButton(visualAdjustmentsIcon);
    visualAdjustmentsButton.setToolTipText("Visual Adjustments");

    volumeButton = new JButton(volumeIcon);

    notesButton = new JButton(notesIcon);
    notesButton.setToolTipText("Notes");

    infoButton = new JButton(infoIcon);
    infoButton.setToolTipText("File Information");

    buttonContainer.add(exifButton);
    buttonContainer.add(equalizerButton);
    buttonContainer.add(frequencyButton);
    buttonContainer.add(visualAdjustmentsButton);
    buttonContainer.add(volumeButton);
    buttonContainer.add(notesButton);
    buttonContainer.add(infoButton);
    detailsPanel.add(buttonContainer, BorderLayout.NORTH);

    JPanel slidingContainer = new JPanel();
    slidingContainer.setLayout(new BoxLayout(slidingContainer, BoxLayout.Y_AXIS));
    exifPanel = createExifPanel();
    equalizerPanel = createEqualizerPanel();
    frequencyPanel = createFrequencyPanel();
    visualAdjustmentsPanel = createVisualAdjustmentsPanel();
    volumeSlidingPanel = createVolumeSlidingPanel();
    notesPanel = createNotesPanel();
    infoPanel = createInfoPanel();

    exifPanel.setVisible(false);
    equalizerPanel.setVisible(false);
    frequencyPanel.setVisible(false);
    visualAdjustmentsPanel.setVisible(false);
    volumeSlidingPanel.setVisible(false);
    notesPanel.setVisible(false);
    infoPanel.setVisible(false);

    slidingContainer.add(Box.createVerticalGlue());
    slidingContainer.add(exifPanel);
    slidingContainer.add(equalizerPanel);
    slidingContainer.add(frequencyPanel);
    slidingContainer.add(visualAdjustmentsPanel);
    slidingContainer.add(volumeSlidingPanel);
    slidingContainer.add(notesPanel);
    slidingContainer.add(infoPanel);

    detailsPanel.add(slidingContainer, BorderLayout.CENTER);

    detailsPanel.addComponentListener(
        new ComponentAdapter() {
          @Override
          public void componentResized(ComponentEvent e) {
            updateFrequencyPanelHeight();
          }
        });

    leftPanel.add(playbackPanel);
    leftPanel.add(Box.createRigidArea(new Dimension(0, 5)));
    leftPanel.add(detailsPanel);

    updateVolumeTooltip();
    updatePanTooltip();

    return leftPanel;
  }

  private JPanel createVolumeSlidingPanel() {
    JPanel containerPanel = new JPanel();
    containerPanel.setLayout(new BoxLayout(containerPanel, BoxLayout.Y_AXIS));

    // --- Volume Panel ---
    JPanel volumePanel = new JPanel(new BorderLayout(5, 0));
    volumePanel.setBorder(new TitledBorder("Volume"));

    volumeSlider = new AudioMeterSlider(0, 100, 100);
    volumeSlider.setMajorTickSpacing(25);
    volumeSlider.setMinorTickSpacing(5);
    volumeSlider.setPaintTicks(true);

    if (volumeIcon != null) {
      Image img = volumeIcon.getImage();
      ImageIcon panelIcon = new ImageIcon(img.getScaledInstance(20, 20, Image.SCALE_SMOOTH));
      volumePanel.add(new JLabel(panelIcon), BorderLayout.WEST);
    }
    volumePanel.add(volumeSlider, BorderLayout.CENTER);
    containerPanel.add(volumePanel);

    // --- Pan/Balance Panel ---
    JPanel panPanel = new JPanel(new BorderLayout(5, 0));
    panPanel.setBorder(new TitledBorder("Balance"));

    panSlider = new JSlider(-100, 100, 0);
    panSlider.setMajorTickSpacing(50);
    panSlider.setMinorTickSpacing(10);
    panSlider.setPaintTicks(true);

    JLabel leftLabel = new JLabel("L");
    leftLabel.setFont(leftLabel.getFont().deriveFont(Font.BOLD));
    panPanel.add(leftLabel, BorderLayout.WEST);

    panPanel.add(panSlider, BorderLayout.CENTER);

    JLabel rightLabel = new JLabel("R");
    rightLabel.setFont(rightLabel.getFont().deriveFont(Font.BOLD));
    panPanel.add(rightLabel, BorderLayout.EAST);

    containerPanel.add(panPanel);

    containerPanel.setPreferredSize(new Dimension(200, 140));

    return containerPanel;
  }

  private JPanel createNotesPanel() {
    JLayeredPane layeredPane = new JLayeredPane();
    layeredPane.setPreferredSize(new Dimension(240, 150));

    notesArea = new JTextArea();
    notesArea.setLineWrap(true);
    notesArea.setWrapStyleWord(true);
    JScrollPane scrollPane = new JScrollPane(notesArea);

    JButton saveNotesButton = new FloatingActionButton(saveIcon);
    saveNotesButton.setToolTipText("Save Notes");
    saveNotesButton.addActionListener(e -> saveNotes());

    layeredPane.add(scrollPane, JLayeredPane.DEFAULT_LAYER);
    layeredPane.add(saveNotesButton, JLayeredPane.PALETTE_LAYER);

    // Add a component listener to keep the FAB in the corner on resize
    layeredPane.addComponentListener(
        new ComponentAdapter() {
          @Override
          public void componentResized(ComponentEvent e) {
            int fabSize = 40;
            int margin = 10;
            int width = layeredPane.getWidth();
            int height = layeredPane.getHeight();

            scrollPane.setBounds(0, 0, width, height);
            saveNotesButton.setBounds(
                width - fabSize - margin, height - fabSize - margin, fabSize, fabSize);
            layeredPane.revalidate();
            layeredPane.repaint();
          }
        });

    JPanel wrapper = new JPanel(new BorderLayout());
    wrapper.setBorder(BorderFactory.createTitledBorder("Notes"));
    wrapper.add(layeredPane, BorderLayout.CENTER);
    return wrapper;
  }

  private JPanel createInfoPanel() {
    JPanel panel = new JPanel(new GridBagLayout());
    panel.setBorder(BorderFactory.createTitledBorder("File Information"));
    GridBagConstraints gbc = new GridBagConstraints();
    gbc.insets = new Insets(4, 8, 4, 8);
    gbc.anchor = GridBagConstraints.WEST;

    // Helper consumer to add a row with a title label and a data label
    BiConsumer<Integer, String> addInfoRow =
        (row, title) -> {
          // Title Label (e.g., "Sample Rate:")
          JLabel titleLabel = new JLabel(title);
          titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD));
          gbc.gridx = 0;
          gbc.gridy = row;
          gbc.weightx = 0;
          gbc.fill = GridBagConstraints.NONE;
          panel.add(titleLabel, gbc);

          // Data Label (placeholder) is created and assigned to a class field later
        };

    // --- Create and add all rows ---
    addInfoRow.accept(0, "Sample Rate:");
    sampleRateLabel = new JLabel("---");
    gbc.gridx = 1;
    gbc.gridy = 0;
    gbc.weightx = 1.0;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    panel.add(sampleRateLabel, gbc);

    addInfoRow.accept(1, "Bit Rate:");
    bitRateLabel = new JLabel("---");
    gbc.gridx = 1;
    gbc.gridy = 1;
    panel.add(bitRateLabel, gbc);

    addInfoRow.accept(2, "Channels:");
    channelsLabel = new JLabel("---");
    gbc.gridx = 1;
    gbc.gridy = 2;
    panel.add(channelsLabel, gbc);

    addInfoRow.accept(3, "Track Length:");
    trackLengthLabel = new JLabel("---");
    gbc.gridx = 1;
    gbc.gridy = 3;
    panel.add(trackLengthLabel, gbc);

    addInfoRow.accept(4, "Encoding:");
    encodingTypeLabel = new JLabel("---");
    gbc.gridx = 1;
    gbc.gridy = 4;
    panel.add(encodingTypeLabel, gbc);

    addInfoRow.accept(5, "File Size:");
    fileSizeLabel = new JLabel("---");
    gbc.gridx = 1;
    gbc.gridy = 5;
    panel.add(fileSizeLabel, gbc);

    // Add a vertical glue to push content to the top and fill remaining space
    gbc.gridy = 6;
    gbc.weighty = 1.0;
    panel.add(Box.createVerticalGlue(), gbc);

    panel.setPreferredSize(new Dimension(240, 220));
    return panel;
  }

  private void updateVolumeTooltip() {
    if (volumeButton != null && volumeSlider != null) {
      int volumeLevel = volumeSlider.getValue();
      volumeButton.setToolTipText("Volume " + volumeLevel + "%");
      volumeSlider.setToolTipText("Volume " + volumeLevel + "%");
    }
  }

  private void updatePanTooltip() {
    if (panSlider == null) return;
    int panValue = panSlider.getValue();
    String tooltip;
    if (panValue == 0) {
      tooltip = "Center Balance";
    } else if (panValue < 0) {
      tooltip = Math.abs(panValue) + "% Left";
    } else {
      tooltip = panValue + "% Right";
    }
    panSlider.setToolTipText(tooltip);
  }

  // --- Visual Adjustment Panel Helper Methods ---

  /** Gets the appropriate color variable based on the current radio button selection. */
  private Color getColorForSelection() {
    if (bgRadio.isSelected()) return backgroundColor;
    if (highAmpRadio.isSelected()) return highAmpColor;
    if (lowAmpRadio.isSelected()) return lowAmpColor;
    if (liveOutRadio.isSelected()) return liveOutputColor;
    if (freqRadio.isSelected()) return spectrumColor;
    if (clockRadio.isSelected()) return clockColor;
    if (scrubberRadio.isSelected()) return scrubberColor;
    return Color.BLACK; // Fallback
  }

  /**
   * Sets the appropriate color variable and updates all dependent UI components.
   *
   * @param newColor The new color to apply.
   * @param source A string ("slider" or "chooser") to indicate the origin of the change and prevent
   * listener feedback loops.
   */
  private void setColorForSelection(Color newColor, String source) {
    if (isUpdatingColorControls) return;

    // Update the correct color variable
    if (bgRadio.isSelected()) {
      backgroundColor = newColor;
      getContentPane().setBackground(backgroundColor); // Update main background
    } else if (highAmpRadio.isSelected()) {
      highAmpColor = newColor;
    } else if (lowAmpRadio.isSelected()) {
      lowAmpColor = newColor;
    } else if (clockRadio.isSelected()) {
      clockColor = newColor;
    } else if (liveOutRadio.isSelected()) {
      liveOutputColor = newColor;
    } else if (freqRadio.isSelected()) {
      spectrumColor = newColor;
      if (spectrumAnalyzerPanel != null) {
        spectrumAnalyzerPanel.setBarColor(spectrumColor);
      }
    } else if (scrubberRadio.isSelected()) {
      scrubberColor = newColor;
    }

    // --- Update controls to reflect the change ---
    isUpdatingColorControls = true;
    bgColorPreview.setBackground(newColor);

    // If the change came from the color chooser, update the slider's position
    if (!"slider".equals(source)) {
      float[] hsb =
          Color.RGBtoHSB(newColor.getRed(), newColor.getGreen(), newColor.getBlue(), null);
      colorSlider.setValue((int) (hsb[0] * 360));
    }

    // Update the clock digits colors
    clockLabel.setForeground(clockColor);

    isUpdatingColorControls = false;

    // --- Repaint all affected components ---
    liveWaveformPanel.setBackground(backgroundColor);
    liveWaveformPanel.setWaveformColor(liveOutputColor); // This also calls repaint
    liveWaveformPanel.setHighAmpColor(highAmpColor); // Update color for BLOCKS style

    if (spectrumAnalyzerPanel != null) {
      spectrumAnalyzerPanel.repaint();
    }
    if (pcmData != null) {
      regenerateWaveformImage();
    }
    // Always repaint the main waveform panel to reflect scrubber color changes
    waveformPanel.repaint();

    if (rememberColorsButton != null && rememberColorsButton.isSelected()) {
      saveColorPreferences();
    }
  }

  /** Updates the color slider and preview panel to reflect the selected radio button's color. */
  private void updateColorControls() {
    if (isUpdatingColorControls) return;
    isUpdatingColorControls = true;

    Color selectedColor = getColorForSelection();
    bgColorPreview.setBackground(selectedColor);

    float[] hsb =
        Color.RGBtoHSB(
            selectedColor.getRed(), selectedColor.getGreen(), selectedColor.getBlue(), null);
    colorSlider.setValue((int) (hsb[0] * 360));

    isUpdatingColorControls = false;
  }

  private JPanel createVisualAdjustmentsPanel() {

    JPanel panel = new JPanel(new GridBagLayout());
    panel.setBorder(BorderFactory.createTitledBorder("Visual Adjustments"));
    GridBagConstraints gbc = new GridBagConstraints();
    gbc.fill = GridBagConstraints.HORIZONTAL;
    gbc.weightx = 1.0;

    // --- Radio Button Panel for selecting what to color ---
    JPanel radioPanel = new JPanel();
    radioPanel.setLayout(new BoxLayout(radioPanel, BoxLayout.Y_AXIS));
    radioPanel.setBorder(BorderFactory.createTitledBorder("Color Target"));
    colorSelectionGroup = new ButtonGroup();

    bgRadio = new JRadioButton("Background");
    highAmpRadio = new JRadioButton("High Amplitude");
    lowAmpRadio = new JRadioButton("Low Amplitude");
    liveOutRadio = new JRadioButton("Live Output");
    clockRadio = new JRadioButton("Audio Clock");
    freqRadio = new JRadioButton("Frequency Spectrum");
    scrubberRadio = new JRadioButton("Scrubber");

    JRadioButton[] radios = {
        bgRadio, highAmpRadio, lowAmpRadio, liveOutRadio, clockRadio, freqRadio, scrubberRadio
    };
    for (JRadioButton radio : radios) {
      colorSelectionGroup.add(radio);
      radioPanel.add(radio);
      radio.addActionListener(e -> updateColorControls());
    }
    bgRadio.setSelected(true); // Set a default selection

    gbc.gridx = 0;
    gbc.gridy = 0;
    gbc.gridwidth = 2;
    gbc.anchor = GridBagConstraints.WEST;
    gbc.insets = new Insets(0, 5, 10, 5);
    panel.add(radioPanel, gbc);

    // --- Color Control Panel (Slider and Preview) ---
    colorSlider = new JSlider(0, 360, 0); // Represents Hue value
    rememberColorsButton = new JToggleButton(saveIcon);
    rememberColorsButton.setToolTipText("Remember color settings");

    bgColorPreview = new JPanel();
    bgColorPreview.setBorder(BorderFactory.createLineBorder(Color.GRAY));
    bgColorPreview.setPreferredSize(new Dimension(24, 24));
    bgColorPreview.setToolTipText("Click to open color picker for precise selection");

    JPanel controlPanel = new JPanel(new BorderLayout(10, 0));
    controlPanel.add(rememberColorsButton, BorderLayout.WEST);
    controlPanel.add(colorSlider, BorderLayout.CENTER);
    controlPanel.add(bgColorPreview, BorderLayout.EAST);

    gbc.gridy = 1;
    gbc.insets = new Insets(0, 5, 5, 5);
    panel.add(controlPanel, gbc);

    // --- Separator and Live Waveform Style Chooser ---
    gbc.gridy = 2;
    gbc.insets = new Insets(10, 0, 0, 0);
    panel.add(new JSeparator(), gbc);

    JPanel liveStylePanel = new JPanel(new BorderLayout(5, 0));
    liveStylePanel.setBorder(BorderFactory.createTitledBorder("Live Waveform Style"));
    liveWaveformStyleChooser = new JComboBox<>(LiveWaveformPanel.WaveformStyle.values());
    liveStylePanel.add(liveWaveformStyleChooser, BorderLayout.CENTER);

    gbc.gridy = 3;
    gbc.insets = new Insets(5, 5, 5, 5);
    panel.add(liveStylePanel, gbc);

    // --- Link the JComboBox to the live waveform panel for the context menu ---
    if (liveWaveformPanel != null) {
      liveWaveformPanel.setStyleChooser(liveWaveformStyleChooser);
    }

    // --- Initialize controls to match the default selection ---
    updateColorControls();

    // --- Add Listeners ---
    rememberColorsButton.addItemListener(
        e -> {
          if (e.getStateChange() == ItemEvent.SELECTED) {
            saveColorPreferences();
          } else {
            clearColorPreferences();
          }
        });

    liveWaveformStyleChooser.addActionListener(
        e -> {
          if (liveWaveformPanel != null && liveWaveformStyleChooser.getSelectedItem() != null) {
            liveWaveformPanel.setStyle(
                (LiveWaveformPanel.WaveformStyle) liveWaveformStyleChooser.getSelectedItem());
          }
        });

    bgColorPreview.addMouseListener(
        new MouseAdapter() {
          @Override
          public void mouseClicked(MouseEvent e) {

            Color currentColor = getColorForSelection();
            Color chosenColor =
                JColorChooser.showDialog(Mp3Visualizer.this, "Choose Color", currentColor);

            if (chosenColor != null) {
              setColorForSelection(chosenColor, "chooser");
            }
          }
        });

    colorSlider.addChangeListener(
        e -> {

          // Check flags to prevent loops and only update when user interaction is finished
          if (isUpdatingColorControls || ((JSlider) e.getSource()).getValueIsAdjusting()) {
            return;
          }

          float hue = colorSlider.getValue() / 360f;
          Color currentColor = getColorForSelection();
          float[] hsb =
              Color.RGBtoHSB(
                  currentColor.getRed(), currentColor.getGreen(), currentColor.getBlue(), null);

          // Create new color from slider's hue but preserve existing saturation & brightness
          Color newColor = Color.getHSBColor(hue, hsb[1], hsb[2]);

          setColorForSelection(newColor, "slider");
        });

    return panel;
  }

  private void saveColorPreferences() {
    try {
      prefs.putBoolean("rememberColors", true);
      prefs.putInt("backgroundColor", backgroundColor.getRGB());
      prefs.putInt("highAmpColor", highAmpColor.getRGB());
      prefs.putInt("lowAmpColor", lowAmpColor.getRGB());
      prefs.putInt("liveOutputColor", liveOutputColor.getRGB());
      prefs.putInt("spectrumColor", spectrumColor.getRGB());
      prefs.putInt("clockColor", clockColor.getRGB());
      prefs.putInt("scrubberColor", scrubberColor.getRGB());
      prefs.flush(); // Ensure preferences are written immediately
    } catch (BackingStoreException e) {
      System.err.println("Error saving color preferences: " + e.getMessage());
    }
  }

  private void clearColorPreferences() {
    try {
      prefs.putBoolean("rememberColors", false);
      prefs.remove("backgroundColor");
      prefs.remove("highAmpColor");
      prefs.remove("lowAmpColor");
      prefs.remove("liveOutputColor");
      prefs.remove("spectrumColor");
      prefs.remove("clockColor");
      prefs.remove("scrubberColor");
      prefs.flush(); // Ensure preferences are written immediately
    } catch (BackingStoreException e) {
      System.err.println("Error clearing color preferences: " + e.getMessage());
    }
  }

  private void loadColorPreferences() {
    boolean remember = prefs.getBoolean("rememberColors", false);
    if (rememberColorsButton != null) {
      rememberColorsButton.setSelected(remember);
    }

    if (remember) {
      isUpdatingColorControls = true; // Prevent listeners from firing while we load

      backgroundColor = new Color(prefs.getInt("backgroundColor", Color.BLACK.getRGB()));
      highAmpColor = new Color(prefs.getInt("highAmpColor", Color.RED.getRGB()));
      lowAmpColor = new Color(prefs.getInt("lowAmpColor", Color.GREEN.getRGB()));
      liveOutputColor = new Color(prefs.getInt("liveOutputColor", Color.RED.getRGB()));
      spectrumColor = new Color(prefs.getInt("spectrumColor", Color.RED.getRGB()));
      clockColor = new Color(prefs.getInt("clockColor", Color.GREEN.getRGB()));
      scrubberColor = new Color(prefs.getInt("scrubberColor", Color.CYAN.getRGB()));

      // Apply loaded colors to all relevant UI components
      getContentPane().setBackground(backgroundColor); // Update main window background
      updateColorControls(); // Updates the slider/preview based on current radio selection

      liveWaveformPanel.setBackground(backgroundColor);
      liveWaveformPanel.setWaveformColor(liveOutputColor);
      liveWaveformPanel.setHighAmpColor(highAmpColor);

      if (spectrumAnalyzerPanel != null) {
        spectrumAnalyzerPanel.setBarColor(spectrumColor);
        spectrumAnalyzerPanel.repaint();
      }
      if (clockLabel != null) {
        clockLabel.setForeground(clockColor);
      }
      if (waveformPanel != null && pcmData != null) {
        regenerateWaveformImage();
      } else if (waveformPanel != null) {
        waveformPanel.repaint();
      }

      isUpdatingColorControls = false;
      repaint(); // Repaint the entire frame to ensure all visual changes are applied
    }
  }

  private void saveNotes() {
    if (notesArea != null) {
      prefs.put("userNotes", notesArea.getText());
      try {
        prefs.flush();
        statusLabel.setText("✅ Notes saved.");
      } catch (BackingStoreException e) {
        statusLabel.setText("❌ Error saving notes.");
      }
    }
  }

  private void loadNotes() {
    if (notesArea != null) {
      notesArea.setText(prefs.get("userNotes", ""));
    }
  }

  private void populateInfoPanel() {
    if (inputFile == null || audioDurationMicros <= 0) {
      clearInfoPanel();
      return;
    }

    try {
      // --- Generic Information (from universal sources) ---
      // This works for any audio type (MP3, WAV, etc.)

      // File Size
      long fileSize = inputFile.length();
      String sizeStr;
      if (fileSize < 1024) {
        sizeStr = fileSize + " B";
      } else if (fileSize < 1024 * 1024) {
        sizeStr = String.format("%.2f KB", fileSize / 1024.0);
      } else {
        sizeStr = String.format("%.2f MB", fileSize / (1024.0 * 1024.0));
      }
      fileSizeLabel.setText(sizeStr);

      // Track Length
      long totalSeconds = audioDurationMicros / 1_000_000L;
      long minutes = totalSeconds / 60;
      long seconds = totalSeconds % 60;
      trackLengthLabel.setText(String.format("%d min %02d sec", minutes, seconds));

      // Sample Rate & Channels (from pcmFormat, the most reliable source)
      if (pcmFormat != null) {
        sampleRateLabel.setText(String.format("%,d Hz", (int) pcmFormat.getSampleRate()));

        int numChannels = pcmFormat.getChannels();
        String channelStr;
        switch (numChannels) {
          case 1:
            channelStr = "Mono";
            break;
          case 2:
            channelStr = "Stereo";
            break;
          default:
            channelStr = numChannels + " channels";
            break;
        }
        channelsLabel.setText(channelStr);
      } else {
        sampleRateLabel.setText("---");
        channelsLabel.setText("---");
      }

      // --- MP3-Specific Information ---
      // This will only be populated if the file was a valid MP3.
      if (mp3file != null) {
        bitRateLabel.setText(
            mp3file.getBitrate() + " kbps" + (mp3file.isVbr() ? " (VBR)" : " (CBR)"));
        encodingTypeLabel.setText(mp3file.getVersion() + ", " + mp3file.getLayer());
      } else {
        bitRateLabel.setText("N/A");
        encodingTypeLabel.setText("N/A");
      }

    } catch (Exception e) {
      // In case any call fails, clear the panel to be safe
      System.err.println("Error populating info panel: " + e.getMessage());
      clearInfoPanel();
    }
  }

  private void clearInfoPanel() {
    if (sampleRateLabel != null) sampleRateLabel.setText("---");
    if (bitRateLabel != null) bitRateLabel.setText("---");
    if (channelsLabel != null) channelsLabel.setText("---");
    if (trackLengthLabel != null) trackLengthLabel.setText("---");
    if (encodingTypeLabel != null) encodingTypeLabel.setText("---");
    if (fileSizeLabel != null) fileSizeLabel.setText("---");
  }

  private JPanel createExifPanel() {

    JPanel panel = new JPanel(new BorderLayout(5, 10));

    albumArtLabel.setHorizontalAlignment(SwingConstants.CENTER);
    albumArtLabel.setPreferredSize(new Dimension(150, 150));
    albumArtLabel.setBorder(BorderFactory.createEtchedBorder());

    displayAlbumArt();

    panel.add(albumArtLabel, BorderLayout.NORTH);

    albumArtLabel.setTransferHandler(new ImageTransferHandler());
    albumArtLabel.addMouseListener(
        new MouseAdapter() {

          public void mousePressed(MouseEvent e) {

            if (albumImageBytes == null) return;

            JComponent c = (JComponent) e.getSource();
            c.getTransferHandler().exportAsDrag(c, e, TransferHandler.MOVE);
          }
        });

    JPanel fieldsPanel = new JPanel();
    fieldsPanel.setLayout(new GridBagLayout());

    GridBagConstraints gbc = new GridBagConstraints();
    gbc.insets = new Insets(2, 2, 2, 2);
    gbc.fill = GridBagConstraints.HORIZONTAL;
    gbc.weightx = 1.0;

    BiConsumer<Integer, JComponent> addLabeledField =
        (row, component) -> {
          gbc.gridy = row;
          fieldsPanel.add(component, gbc);
        };

    addLabeledField.accept(0, createTitledComponent("Title", titleField));
    addLabeledField.accept(1, createTitledComponent("Artist", artistField));
    addLabeledField.accept(2, createTitledComponent("Album", albumField));
    addLabeledField.accept(3, createTitledComponent("Year", yearField));
    addLabeledField.accept(4, createTitledComponent("Genre", genreField));

    commentArea.setLineWrap(true);
    commentArea.setWrapStyleWord(true);

    addLabeledField.accept(5, createTitledComponent("Comment", new JScrollPane(commentArea)));

    panel.add(new JScrollPane(fieldsPanel), BorderLayout.CENTER);

    JPanel savePanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
    saveExifButton = new JButton("Save Changes");
    saveExifButton.setToolTipText("Save Changes");

    savePanel.add(saveExifButton);

    panel.add(savePanel, BorderLayout.SOUTH);

    setExifFieldsEnabled(false);

    return panel;
  }

  private JPanel createEqualizerPanel() {
    JPanel panel = new JPanel(new GridLayout(1, 7, 5, 0));
    panel.setBorder(BorderFactory.createEmptyBorder(10, 5, 10, 5));

    for (int i = 0; i < eqSliders.length; i++) {
      JPanel bandPanel = new JPanel(new BorderLayout());
      eqSliders[i] = new JSlider(JSlider.VERTICAL, 0, 24, 12);

      int gain = eqSliders[i].getValue() - 12;
      String tooltip;
      if (i == 0) { // Bass
        tooltip = String.format("Bass Shelf Gain: %+d dB", gain);
      } else if (i == eqSliders.length - 1) { // Treble
        tooltip = String.format("Treble Shelf Gain: %+d dB", gain);
      } else {
        tooltip = String.format("%s Hz Peak Gain: %+d dB", EQ_LABELS[i], gain);
      }
      eqSliders[i].setToolTipText(tooltip);

      final int bandIndex = i;
      eqSliders[i].addChangeListener(
          e -> {
            int currentGain = eqSliders[bandIndex].getValue() - 12;
            String newTooltip;
            if (bandIndex == 0) {
              newTooltip = String.format("Bass Shelf Gain: %+d dB", currentGain);
            } else if (bandIndex == eqSliders.length - 1) {
              newTooltip = String.format("Treble Shelf Gain: %+d dB", currentGain);
            } else {
              newTooltip =
                  String.format("%s Hz Peak Gain: %+d dB", EQ_LABELS[bandIndex], currentGain);
            }
            eqSliders[bandIndex].setToolTipText(newTooltip);
          });

      bandPanel.add(eqSliders[i], BorderLayout.CENTER);

      JLabel freqLabel = new JLabel(EQ_LABELS[i], SwingConstants.CENTER);
      freqLabel.setFont(freqLabel.getFont().deriveFont(freqLabel.getFont().getSize() - 2f));
      bandPanel.add(freqLabel, BorderLayout.SOUTH);

      panel.add(bandPanel);
    }
    return panel;
  }

  private JPanel createFrequencyPanel() {
    JPanel panel = new JPanel();
    // Use BoxLayout to control vertical distribution
    panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
    spectrumAnalyzerPanel = new SpectrumAnalyzerPanel();

    // The height is now dynamic and will be set by a component listener.
    // We remove the fixed size definitions that were here.

    // 1. Add a "glue" component first. It will expand to take up all
    //    available vertical space, pushing everything else down.
    panel.add(Box.createVerticalGlue());

    // 2. Add the spectrum analyzer. It will now be "stuck" to the bottom.
    panel.add(spectrumAnalyzerPanel);

    // Set a default preferred size on the container panel to guide the animation.
    // This will be updated dynamically by the listener.
    panel.setPreferredSize(new Dimension(240, 140));
    return panel;
  }

  /**
   * Calculates and applies a dynamic height to the frequency spectrum visualizer panel based on the
   * available space in its parent container. This method is called by a ComponentListener when the
   * UI is resized.
   */
  private void updateFrequencyPanelHeight() {
    if (frequencyPanel == null || spectrumAnalyzerPanel == null) {
      return;
    }

    // The component that resizes is detailsPanel.
    JPanel slidingContainer = (JPanel) frequencyPanel.getParent();
    if (slidingContainer == null) return;

    JPanel detailsPanel = (JPanel) slidingContainer.getParent();
    if (detailsPanel == null || !(detailsPanel.getLayout() instanceof BorderLayout)) return;

    // Get the component in the NORTH region (the vertical button bar).
    Component northComponent =
        ((BorderLayout) detailsPanel.getLayout()).getLayoutComponent(BorderLayout.NORTH);
    int northHeight =
        (northComponent != null && northComponent.isVisible()) ? northComponent.getHeight() : 0;

    // The total height available for the sliding panel area.
    int availableHeight = detailsPanel.getHeight() - northHeight;

    // Don't do anything if there's no real space to work with.
    if (availableHeight <= 20) {
      return;
    }

    // The desired height of the analyzer is a proportion of the available space.
    // You can adjust this value to change the default size of the visualizer.
    final double proportion = 0.85; // Use 50% of the available vertical space.
    int desiredAnalyzerHeight = (int) (availableHeight * proportion);

    // Enforce a minimum height for usability.
    final int minHeight = 80;
    desiredAnalyzerHeight = Math.max(minHeight, desiredAnalyzerHeight);

    // The analyzer panel itself shouldn't be taller than the space available for it.
    desiredAnalyzerHeight = Math.min(desiredAnalyzerHeight, availableHeight);

    // Update the SpectrumAnalyzerPanel's size constraints. The BoxLayout of its
    // parent (frequencyPanel) will respect this maximum size.
    Dimension analyzerSize = new Dimension(Short.MAX_VALUE, desiredAnalyzerHeight);
    spectrumAnalyzerPanel.setPreferredSize(analyzerSize);
    spectrumAnalyzerPanel.setMaximumSize(analyzerSize);

    // The 'frequencyPanel' as a whole should animate to fill the entire available space.
    // We set its preferred height to guide the animation.
    int desiredPanelHeight = availableHeight;
    frequencyPanel.setPreferredSize(
        new Dimension(frequencyPanel.getPreferredSize().width, desiredPanelHeight));

    // If the frequency panel is already visible, we need to resize it in place.
    if (isFrequencyPanelVisible) {
      // Stop any conflicting animation.
      if (frequencyPanelAnimator != null && frequencyPanelAnimator.isRunning()) {
        frequencyPanelAnimator.stop();
      }

      // Update its maximum size and re-layout the container.
      frequencyPanel.setMaximumSize(new Dimension(Short.MAX_VALUE, desiredPanelHeight));
      frequencyPanel.revalidate();
    }
  }

  private String formatFrequency(float freq) {
    if (freq >= 1000) {
      return String.format("%.0fk", freq / 1000.0f);
    }
    return String.format("%.0f", freq);
  }

  private JPanel createTitledComponent(String title, JComponent component) {
    JPanel panel = new JPanel(new BorderLayout());
    JLabel label = new JLabel(title);
    label.setFont(label.getFont().deriveFont(Font.BOLD, label.getFont().getSize() - 2f));
    panel.add(label, BorderLayout.NORTH);
    panel.add(component, BorderLayout.CENTER);
    panel.setBorder(BorderFactory.createEmptyBorder(3, 0, 3, 0));
    return panel;
  }

  private void hideAllSlidingPanels() {
    if (isExifPanelVisible) {
      animateSlidingPanel(exifPanel, true);
      isExifPanelVisible = false;
    }
    if (isEqualizerPanelVisible) {
      animateSlidingPanel(equalizerPanel, true);
      isEqualizerPanelVisible = false;
    }
    if (isFrequencyPanelVisible) {
      animateSlidingPanel(frequencyPanel, true);
      isFrequencyPanelVisible = false;
    }
    if (isVisualAdjustmentsPanelVisible) {
      animateSlidingPanel(visualAdjustmentsPanel, true);
      isVisualAdjustmentsPanelVisible = false;
    }
    if (isVolumeSlidingPanelVisible) {
      animateSlidingPanel(volumeSlidingPanel, true);
      isVolumeSlidingPanelVisible = false;
    }
    if (isNotesPanelVisible) {
      animateSlidingPanel(notesPanel, true);
      isNotesPanelVisible = false;
    }
    if (isInfoPanelVisible) {
      animateSlidingPanel(infoPanel, true);
      isInfoPanelVisible = false;
    }
  }

  private void toggleExifPanel() {
    boolean wasVisible = isExifPanelVisible;
    hideAllSlidingPanels();
    if (!wasVisible) {
      animateSlidingPanel(exifPanel, false);
      isExifPanelVisible = true;
    }
  }

  private void toggleEqualizerPanel() {
    boolean wasVisible = isEqualizerPanelVisible;
    hideAllSlidingPanels();
    if (!wasVisible) {
      animateSlidingPanel(equalizerPanel, false);
      isEqualizerPanelVisible = true;
    }
  }

  private void toggleFrequencyPanel() {
    boolean wasVisible = isFrequencyPanelVisible;
    hideAllSlidingPanels();
    if (!wasVisible) {
      animateSlidingPanel(frequencyPanel, false);
      isFrequencyPanelVisible = true;
    }
  }

  private void toggleVisualAdjustmentsPanel() {
    boolean wasVisible = isVisualAdjustmentsPanelVisible;
    hideAllSlidingPanels();
    if (!wasVisible) {
      animateSlidingPanel(visualAdjustmentsPanel, false);
      isVisualAdjustmentsPanelVisible = true;
    }
  }

  private void toggleVolumeSlidingPanel() {
    boolean wasVisible = isVolumeSlidingPanelVisible;
    hideAllSlidingPanels();
    if (!wasVisible) {
      animateSlidingPanel(volumeSlidingPanel, false);
      isVolumeSlidingPanelVisible = true;
    }
  }

  private void toggleNotesPanel() {
    boolean wasVisible = isNotesPanelVisible;
    hideAllSlidingPanels();
    if (!wasVisible) {
      animateSlidingPanel(notesPanel, false);
      isNotesPanelVisible = true;
    }
  }

  private void toggleInfoPanel() {
    boolean wasVisible = isInfoPanelVisible;
    hideAllSlidingPanels();
    if (!wasVisible) {
      animateSlidingPanel(infoPanel, false);
      isInfoPanelVisible = true;
    }
  }

  private void animateSlidingPanel(JPanel panel, boolean isVisible) {
    Timer animator;
    if (panel == exifPanel) animator = exifPanelAnimator;
    else if (panel == equalizerPanel) animator = equalizerPanelAnimator;
    else if (panel == frequencyPanel) animator = frequencyPanelAnimator;
    else if (panel == visualAdjustmentsPanel) animator = visualAdjustmentsPanelAnimator;
    else if (panel == notesPanel) animator = notesPanelAnimator;
    else if (panel == infoPanel) animator = infoPanelAnimator;
    else animator = volumeSlidingPanelAnimator;

    if (animator != null && animator.isRunning()) animator.stop();

    panel.setVisible(true);
    final int startHeight = panel.getHeight();
    final int targetHeight = !isVisible ? panel.getPreferredSize().height : 0;
    if (startHeight == targetHeight) {
      if (isVisible) panel.setVisible(false);
      return;
    }

    final int totalSteps = 20;
    final int delay = 10;
    final AtomicInteger step = new AtomicInteger(0);

    Timer newAnimator =
        new Timer(
            delay,
            e -> {
              int currentStep = step.incrementAndGet();
              if (currentStep >= totalSteps) {
                ((Timer) e.getSource()).stop();
                panel.setMaximumSize(new Dimension(Short.MAX_VALUE, targetHeight));
                if (isVisible) panel.setVisible(false);
              } else {
                int newHeight =
                    startHeight + ((targetHeight - startHeight) * currentStep) / totalSteps;
                panel.setMaximumSize(new Dimension(Short.MAX_VALUE, newHeight));
              }
              panel.revalidate();
              panel.repaint();
            });

    if (panel == exifPanel) exifPanelAnimator = newAnimator;
    else if (panel == equalizerPanel) equalizerPanelAnimator = newAnimator;
    else if (panel == frequencyPanel) frequencyPanelAnimator = newAnimator;
    else if (panel == visualAdjustmentsPanel) visualAdjustmentsPanelAnimator = newAnimator;
    else if (panel == notesPanel) notesPanelAnimator = newAnimator;
    else if (panel == infoPanel) infoPanelAnimator = newAnimator;
    else volumeSlidingPanelAnimator = newAnimator;

    newAnimator.start();
  }

  private void animateDivider(JSplitPane splitPane, int targetLocation) {

    if (animationTimer != null && animationTimer.isRunning()) {
      animationTimer.stop();
    }

    final int startLocation = splitPane.getDividerLocation();

    if (startLocation == targetLocation) return;

    final int totalSteps = 20;
    final int delay = 10;
    final AtomicInteger step = new AtomicInteger(0);

    animationTimer =
        new Timer(
            delay,
            e -> {
              int currentStep = step.incrementAndGet();

              if (currentStep >= totalSteps) {

                ((Timer) e.getSource()).stop();
                splitPane.setDividerLocation(targetLocation);
              } else {
                int newLocation =
                    startLocation + ((targetLocation - startLocation) * currentStep) / totalSteps;
                splitPane.setDividerLocation(newLocation);
              }
            });

    animationTimer.start();
  }

  private void addAnimatedCollapsibility(JSplitPane splitPane, CollapseDirection direction) {

    SwingUtilities.invokeLater(
        () -> {
          if (!(splitPane.getUI() instanceof BasicSplitPaneUI)) return;

          BasicSplitPaneDivider divider = ((BasicSplitPaneUI) splitPane.getUI()).getDivider();

          if (divider == null) return;

          divider.addMouseListener(
              new MouseAdapter() {

                @Override
                public void mouseClicked(MouseEvent e) {

                  if (e.getClickCount() != 2) return;

                  int currentLocation = splitPane.getDividerLocation();
                  int maxLocation = splitPane.getMaximumDividerLocation();
                  int collapsedPosition =
                      (direction == CollapseDirection.TO_START) ? 0 : maxLocation;
                  boolean isCollapsed = currentLocation == collapsedPosition;

                  if (isCollapsed) {

                    int defaultExpandedPosition =
                        (direction == CollapseDirection.TO_START)
                            ? 260
                            : splitPane.getHeight()
                                - 150
                                - splitPane.getInsets().bottom
                                - splitPane.getDividerSize();

                    Object lastPosObj = splitPane.getClientProperty("lastLocation");

                    int restorePosition =
                        (lastPosObj instanceof Integer)
                            ? (Integer) lastPosObj
                            : defaultExpandedPosition;

                    animateDivider(splitPane, restorePosition);
                  } else {

                    splitPane.putClientProperty("lastLocation", currentLocation);
                    animateDivider(splitPane, collapsedPosition);
                  }
                }
              });
        });
  }

  private void updateVolume() {
    if (dataLine == null || !dataLine.isOpen()) return;

    if (dataLine.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
      FloatControl gainControl = (FloatControl) dataLine.getControl(FloatControl.Type.MASTER_GAIN);
      float dbRange = gainControl.getMaximum() - gainControl.getMinimum();
      float targetDb = gainControl.getMinimum() + (dbRange * (volumeSlider.getValue() / 100.0f));

      gainControl.setValue(targetDb);
    }
  }

  private void updatePan() {
    if (dataLine == null || !dataLine.isOpen()) return;

    if (dataLine.isControlSupported(FloatControl.Type.PAN)) {
      FloatControl panControl = (FloatControl) dataLine.getControl(FloatControl.Type.PAN);
      float panValue = panSlider.getValue() / 100.0f;
      panControl.setValue(panValue);
    }
  }

  private void updateEqFilter(int band) {
    if (pcmFormat == null || band < 0 || band >= eqSliders.length) return;

    float gainDb = eqSliders[band].getValue() - 12.0f;
    float freq = EQ_FREQUENCIES[band];

    for (int channel = 0; channel < pcmFormat.getChannels(); channel++) {
      if (channel < 2) {
        if (band == 0) { // Bass
          eqFilters[channel][band].setLowShelf(pcmFormat.getSampleRate(), freq, gainDb);
        } else if (band == eqSliders.length - 1) { // Treble
          eqFilters[channel][band].setHighShelf(pcmFormat.getSampleRate(), freq, gainDb);
        } else { // Peaking
          eqFilters[channel][band].setPeakingEq(pcmFormat.getSampleRate(), freq, 1.0f, gainDb);
        }
      }
    }
  }

  private void addListeners() {

    volumeSlider.addChangeListener(
        e -> {
          updateVolume();
          updateVolumeTooltip();
        });

    panSlider.addChangeListener(
        e -> {
          updatePan();
          updatePanTooltip();
        });

    playButton.addActionListener(e -> togglePlayback());
    exifButton.addActionListener(e -> toggleExifPanel());
    equalizerButton.addActionListener(e -> toggleEqualizerPanel());
    frequencyButton.addActionListener(e -> toggleFrequencyPanel());
    visualAdjustmentsButton.addActionListener(e -> toggleVisualAdjustmentsPanel());
    volumeButton.addActionListener(e -> toggleVolumeSlidingPanel());
    notesButton.addActionListener(e -> toggleNotesPanel());
    infoButton.addActionListener(e -> toggleInfoPanel());

    for (int i = 0; i < eqSliders.length; i++) {
      final int band = i;
      eqSliders[i].addChangeListener(e -> updateEqFilter(band));
    }

    saveExifButton.addActionListener(e -> saveExifFile());
  }

  private void loadTagData() {

    if (inputFile == null) return;

    clearExifFields();

    try {

      mp3file = new Mp3File(inputFile);
      ID3v2 id3v2Tag;

      if (mp3file.hasId3v2Tag()) {

        id3v2Tag = mp3file.getId3v2Tag();
        albumImageBytes = id3v2Tag.getAlbumImage();
        albumImageMimeType = id3v2Tag.getAlbumImageMimeType();
        displayAlbumArt();
      } else if (mp3file.hasId3v1Tag()) {

        ID3v1 id3v1Tag = mp3file.getId3v1Tag();
        titleField.setText(id3v1Tag.getTitle());
        artistField.setText(id3v1Tag.getArtist());
        albumField.setText(id3v1Tag.getAlbum());
        yearField.setText(id3v1Tag.getYear());
        genreField.setText(id3v1Tag.getGenreDescription());
        commentArea.setText(id3v1Tag.getComment());
        setExifFieldsEnabled(true);

        return;
      } else {

        id3v2Tag = new ID3v24Tag();
        mp3file.setId3v2Tag(id3v2Tag);
      }

      titleField.setText(id3v2Tag.getTitle());
      artistField.setText(id3v2Tag.getArtist());
      albumField.setText(id3v2Tag.getAlbum());
      yearField.setText(id3v2Tag.getYear());
      genreField.setText(id3v2Tag.getGenreDescription());
      commentArea.setText(id3v2Tag.getComment());
      setExifFieldsEnabled(true);
    } catch (IOException | UnsupportedTagException | InvalidDataException ex) {
      // This is expected for non-MP3 files, so we don't show an error.
      // mp3file will be null.
      setExifFieldsEnabled(false);
    }
  }

  private void saveExifFile() {

    if (mp3file == null) {
      JOptionPane.showMessageDialog(
          this,
          "No MP3 file is loaded or file is not an MP3.",
          "Save Error",
          JOptionPane.ERROR_MESSAGE);
      return;
    }

    ID3v2 id3v2Tag = mp3file.hasId3v2Tag() ? mp3file.getId3v2Tag() : new ID3v24Tag();

    if (!mp3file.hasId3v2Tag()) mp3file.setId3v2Tag(id3v2Tag);

    id3v2Tag.setTitle(titleField.getText());
    id3v2Tag.setArtist(artistField.getText());
    id3v2Tag.setAlbum(albumField.getText());
    id3v2Tag.setYear(yearField.getText());
    id3v2Tag.setComment(commentArea.getText());
    id3v2Tag.setAlbumImage(albumImageBytes, albumImageMimeType);
    id3v2Tag.setGenreDescription(genreField.getText());
    File tempFile = new File(inputFile.getParent(), inputFile.getName() + ".tmp");

    try {
      mp3file.save(tempFile.getAbsolutePath());
      Files.move(tempFile.toPath(), inputFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
      statusLabel.setText("✅ EXIF tags saved successfully!");
      String title = titleField.getText();
      if (title == null || title.trim().isEmpty()) {
        title = inputFile.getName();
      }
      startTitleMarquee(title);
    } catch (IOException | NotSupportedException ex) {
      statusLabel.setText("❌ Error saving EXIF tags: " + ex.getMessage());
      if (tempFile.exists()) tempFile.delete();
    }
  }

  private void addAlbumArt(File imageFile) {
    try {

      albumImageBytes = Files.readAllBytes(imageFile.toPath());
      albumImageMimeType = Files.probeContentType(imageFile.toPath());

      if (albumImageMimeType == null) {
        String name = imageFile.getName().toLowerCase();
        albumImageMimeType = name.endsWith("png") ? "image/png" : "image/jpeg";
      }

      displayAlbumArt();
    } catch (IOException ex) {
      JOptionPane.showMessageDialog(
          this,
          "Error reading image file: " + ex.getMessage(),
          "Image Error",
          JOptionPane.ERROR_MESSAGE);
    }
  }

  private void removeAlbumArt() {
    albumImageBytes = null;
    albumImageMimeType = null;
    displayAlbumArt();
  }

  private void displayAlbumArt() {
    if (albumImageBytes != null) {
      try {
        BufferedImage img = ImageIO.read(new ByteArrayInputStream(albumImageBytes));
        Image scaledImg = img.getScaledInstance(150, 150, Image.SCALE_SMOOTH);
        albumArtLabel.setIcon(new ImageIcon(scaledImg));
        albumArtLabel.setText(null);
      } catch (Exception e) {
        albumArtLabel.setIcon(null);
        albumArtLabel.setText("Preview N/A");
      }
    } else {
      albumArtLabel.setIcon(null);
      albumArtLabel.setText("No Album Art");
    }
  }

  private void setExifFieldsEnabled(boolean enabled) {
    titleField.setEnabled(enabled);
    artistField.setEnabled(enabled);
    albumField.setEnabled(enabled);
    yearField.setEnabled(enabled);
    genreField.setEnabled(enabled);
    commentArea.setEnabled(enabled);
    saveExifButton.setEnabled(enabled);
  }

  private void clearExifFields() {
    titleField.setText("");
    artistField.setText("");
    albumField.setText("");
    yearField.setText("");
    genreField.setText("");
    commentArea.setText("");
    albumImageBytes = null;
    albumImageMimeType = null;
    displayAlbumArt();
  }

  private void resetPlayback() {
    stopPlayback();
    playButton.setEnabled(false);
    panSlider.setValue(0);
    panSlider.setEnabled(false);
    audioDurationMicros = -1;
    pcmData = null;
    pcmFormat = null;
    waveformPanel.setNeedlePosition(-1);
    currentDb = -Double.MAX_VALUE;
    currentPeakHz = 0.0;
    currentRms = 0.0; // Reset RMS
    clearInfoPanel();

    if (clockLabel != null) {
      clockLabel.setText("00:00.000");
    }
    if (dbGauge != null) dbGauge.setValue(-60); // ADD THIS
    if (hzGauge != null) hzGauge.setValue(0);   // ADD THIS

    currentDb = -Double.MAX_VALUE;
    currentPeakHz = 0.0;

    if (volumeSlider instanceof AudioMeterSlider) {
      ((AudioMeterSlider) volumeSlider).setMeterLevel(0.0);
    }
  }

  private void ejectAndResetUI() {

    liveWaveformPanel.startFadingOut();

    stopPlayback();
    resetPlayback();
    inputFile = null;
    mp3file = null;
    waveformPanel.setWaveform(null);
    ejectButton.setEnabled(false);
    statusLabel.setText("Ready. Drag and drop an audio file to begin.");
    clearExifFields();
    setExifFieldsEnabled(false);
    updateEjectButtonState();
    stopTitleMarquee();
    originalPcmData = null;
    originalAudioDurationMicros = -1;
  }

  public void openFilePicker() {
    JFileChooser fileChooser = new JFileChooser();
    fileChooser.setDialogTitle("Select an Audio File");
    fileChooser.setFileFilter(
        new FileNameExtensionFilter("Audio Files (MP3, WAV, AIF)", "mp3", "wav", "aif", "aiff"));
    if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
      loadAudioFile(fileChooser.getSelectedFile());
    }
  }

  public void openFileLocation() {
    if (inputFile != null && inputFile.getParentFile() != null) {
      if (Desktop.isDesktopSupported()) {
        try {
          Desktop.getDesktop().open(inputFile.getParentFile());
        } catch (IOException ex) {
          statusLabel.setText("❌ Could not open file location.");
          JOptionPane.showMessageDialog(
              this,
              "Error opening file location: " + ex.getMessage(),
              "Error",
              JOptionPane.ERROR_MESSAGE);
        }
      } else {
        statusLabel.setText("❌ Desktop operations not supported on this system.");
      }
    }
  }

  private void updateEjectButtonState() {
    for (java.awt.event.ActionListener al : ejectButton.getActionListeners()) {
      ejectButton.removeActionListener(al);
    }
    if (inputFile == null) {
      ejectButton.setIcon(ejectIcon);
      ejectButton.setToolTipText("Load File");
      ejectButton.addActionListener(e -> openFilePicker());
    } else {
      ejectButton.setIcon(ejectIcon);
      ejectButton.setToolTipText("Eject File");
      ejectButton.addActionListener(e -> ejectAndResetUI());
    }
  }

  private void loadAudioFile(File file) {
    inputFile = file;
    statusLabel.setText("Selected input: " + inputFile.getName());
    resetPlayback();
    liveWaveformPanel.setLoading(true);
    waveformPanel.setLoading(true, "Decoding...");
    new LoadAudioWorker(inputFile).execute();
    updateEjectButtonState();
  }

  private void exportWaveformImage() {
    if (waveformPanel.getWaveformImage() == null) {
      statusLabel.setText("No waveform to export.");
      return;
    }
    JFileChooser chooser = new JFileChooser();
    chooser.setDialogTitle("Save Waveform As...");
    chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("PNG Images", "png"));

    if (inputFile != null) {
      String outputName = inputFile.getName().replaceAll("\\..*$", "") + ".png";
      chooser.setSelectedFile(new File(inputFile.getParent(), outputName));
    } else {
      chooser.setSelectedFile(new File("png/waveform.png"));
    }

    if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
      File fileToSave = chooser.getSelectedFile();
      if (!fileToSave.getName().toLowerCase().endsWith(".png")) {
        fileToSave = new File(fileToSave.getParentFile(), fileToSave.getName() + ".png");
      }
      try {
        BufferedImage originalImage = waveformPanel.getWaveformImage();
        int tickAreaHeight = 30;
        int newWidth = originalImage.getWidth();
        int newHeight = originalImage.getHeight() + tickAreaHeight;
        BufferedImage compositeImage =
            new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = compositeImage.createGraphics();
        g2d.setColor(this.backgroundColor);
        g2d.fillRect(0, originalImage.getHeight(), newWidth, tickAreaHeight);
        g2d.drawImage(originalImage, 0, 0, null);

        // The time ticks are now drawn directly in WaveformPanel's paintComponent, so we can use
        // that logic.
        // For export, we need a static method that can be called on any graphics context.
        WaveformPanel.drawTimeTicks(
            g2d, 0, newWidth, originalImage.getHeight(), audioDurationMicros, tickAreaHeight);
        g2d.dispose();
        ImageIO.write(compositeImage, "png", fileToSave);
        statusLabel.setText("✅ Waveform saved to " + fileToSave.getName());
      } catch (IOException e) {
        statusLabel.setText("❌ Error saving file: " + e.getMessage());
        JOptionPane.showMessageDialog(
            this, "Error saving file: " + e.getMessage(), "Save Error", JOptionPane.ERROR_MESSAGE);
      }
    }
  }

  private void regenerateWaveformImage() {

    if (pcmData == null) return;

    int width = waveformPanel.getWaveformDrawableWidth();
    int height = waveformPanel.getHeight();

    if (width > 0 && height > 0) {
      waveformPanel.setProgressBarMode(false, "Generating Waveform...");
      new GenerateImageWorker(width, height, waveformPanel, liveWaveformPanel).execute();
    }
  }

  private void togglePlayback() {
    if (isPlaying) stopPlayback();
    else startPlayback();
  }

  private void calculateAndStoreDb(float[] samples) {
    if (samples == null || samples.length == 0) {
      currentDb = -Double.MAX_VALUE;
      currentRms = 0.0; // Reset RMS
      return;
    }
    double sumOfSquares = 0.0;
    for (float sample : samples) {
      sumOfSquares += sample * sample;
    }
    // Calculate Root Mean Square
    double rms = Math.sqrt(sumOfSquares / samples.length);

    // --- MODIFICATION START ---
    // Store the calculated RMS value for the meter
    this.currentRms = rms;
    // --- MODIFICATION END ---

    if (rms == 0.0) {
      currentDb = -96.0;
    } else {
      currentDb = 20 * Math.log10(rms);
    }
  }

  // Replace the entire calculateAndStorePeakHz method with this final version
  private void calculateAndStorePeakHz(double[] magnitudes, float sampleRate, int fftSize) {
    if (magnitudes == null || magnitudes.length < 3) {
      currentPeakHz = 0.0;
      return;
    }

    // --- 1. Harmonic Product Spectrum (HPS) ---
    final int HPS_HARMONICS = 5;
    int spectrumLength = magnitudes.length / HPS_HARMONICS;
    double[] hpsProduct = new double[spectrumLength];
    System.arraycopy(magnitudes, 0, hpsProduct, 0, spectrumLength);
    for (int harmonic = 2; harmonic <= HPS_HARMONICS; harmonic++) {
      for (int i = 0; i < spectrumLength; i++) {
        hpsProduct[i] *= magnitudes[i * harmonic];
      }
    }

    // --- 2. Find the Peak in the HPS Result ---
    int peakIndex = -1;
    double maxMagnitude = -1.0;
    int minIndex = (int)(20.0 / (sampleRate / fftSize));

    for (int i = minIndex; i < spectrumLength - 1; i++) {
      if (hpsProduct[i] > maxMagnitude) {
        maxMagnitude = hpsProduct[i];
        peakIndex = i;
      }
    }

    // --- Final Check and Interpolation ---
    // A threshold helps to reject noise when the signal is quiet.
    final double HPS_THRESHOLD = 1E-12;

    if (peakIndex > 0 && peakIndex < spectrumLength - 1 && maxMagnitude > HPS_THRESHOLD) {
      // Quadratic Interpolation for high precision
      double y1 = hpsProduct[peakIndex - 1];
      double y2 = hpsProduct[peakIndex];
      double y3 = hpsProduct[peakIndex + 1];
      double p = 0.5 * (y1 - y3) / (y1 - 2 * y2 + y3);
      double preciseIndex = peakIndex + p;

      currentPeakHz = preciseIndex * (double) sampleRate / fftSize;
    } else {
      // If no significant peak is found, gradually decay to zero
      currentPeakHz *= 0.8;
    }

    if (currentPeakHz < 20.0) {
      currentPeakHz = 0.0;
    }
  }

  private void startPlayback() {
    if (isPlaying || pcmData == null) return;

    isPlaying = true;

    liveWaveformPanel.startFadingIn();

    if (activityGifLabel != null) activityGifLabel.setVisible(true);

    playButton.setIcon(pauseIcon);
    playButton.setToolTipText("Pause");
    playbackStartOffsetMicros = currentMicros;

    for (int i = 0; i < eqSliders.length; i++) {
      updateEqFilter(i);
    }

    // Replace the entire playbackThread lambda in the startPlayback() method
    playbackThread =
        new Thread(
            () -> {
              try {
                dataLine = AudioSystem.getSourceDataLine(pcmFormat);
                dataLine.open(pcmFormat, 8192);
                dataLine.start();
                updateVolume();
                updatePan();

                long startByte =
                    (long)
                        (playbackStartOffsetMicros / (double) audioDurationMicros * pcmData.length);
                startByte = (startByte / pcmFormat.getFrameSize()) * pcmFormat.getFrameSize();

                byte[] buffer = new byte[4096];
                try (ByteArrayInputStream bais = new ByteArrayInputStream(pcmData)) {
                  bais.skip(startByte);
                  int bytesRead;
                  while (isPlaying && (bytesRead = bais.read(buffer, 0, buffer.length)) != -1) {
                    float[] samples = bytesToFloats(buffer, bytesRead, pcmFormat);

                    // --- CORRECTED LOGIC ---
                    // Perform analysis on the ORIGINAL audio data FIRST
                    calculateAndStoreDb(samples);

                    if (pcmFormat.getChannels() > 0) {
                      int fftSize = 4096; // Increased for better frequency resolution
                      float[] fftInput = new float[fftSize];
                      int samplesToCopy =
                          Math.min(fftSize, samples.length / pcmFormat.getChannels());

                      // Create mono signal for FFT
                      for (int i = 0; i < samplesToCopy; i++) {
                        float monoSample = 0;
                        for (int ch = 0; ch < pcmFormat.getChannels(); ch++) {
                          monoSample += samples[i * pcmFormat.getChannels() + ch];
                        }
                        fftInput[i] = monoSample / pcmFormat.getChannels();
                      }

                      // Apply windowing function
                      java.util.stream.IntStream.range(0, fftSize)
                          .parallel()
                          .forEach(
                              i ->
                                  fftInput[i] *=
                                      (float) (0.5 * (1 - Math.cos(2 * Math.PI * i / (fftSize - 1)))));

                      double[] magnitudes = FFT.transform(fftInput);
                      if (spectrumAnalyzerPanel != null) {
                        spectrumAnalyzerPanel.updateMagnitudes(magnitudes);
                      }
                      // This now analyzes the clean signal
                      calculateAndStorePeakHz(magnitudes, pcmFormat.getSampleRate(), fftSize);
                    }


                    // --- Apply EQ filters AFTER analysis ---
                    for (int i = 0; i < samples.length; i += pcmFormat.getChannels()) {
                      for (int ch = 0; ch < pcmFormat.getChannels(); ch++) {
                        if (i + ch < samples.length && ch < 2) {
                          float sample = samples[i + ch];
                          for (int band = 0; band < eqSliders.length; band++) {
                            sample = eqFilters[ch][band].process(sample);
                          }
                          samples[i + ch] = sample;
                        }
                      }
                    }

                    // Write processed audio and update live visualizer
                    byte[] processedBytes = floatsToBytes(samples, pcmFormat);
                    dataLine.write(processedBytes, 0, processedBytes.length);
                    updateLiveVisualizer(processedBytes, processedBytes.length, pcmFormat);
                  }
                }
                dataLine.drain();
              } catch (Exception e) {
                if (isPlaying) e.printStackTrace();
              } finally {
                if (dataLine != null) {
                  dataLine.stop();
                  dataLine.close();
                }
                if (isPlaying) {
                  SwingUtilities.invokeLater(
                      () -> {
                        stopPlayback();
                        currentMicros = 0;
                        waveformPanel.setNeedlePosition(-1);
                      });
                }
              }
            });

    if (uiUpdateTimer == null) {
      uiUpdateTimer = new Timer(33, e -> updatePlaybackUI());
    }
    uiUpdateTimer.start();
    playbackThread.start();
  }

  private void stopPlayback() {

    liveWaveformPanel.startFadingOut();

    if (activityGifLabel != null) {
      activityGifLabel.setVisible(false);
    }

    isPlaying = false;

    if (uiUpdateTimer != null) {
      updatePlaybackUI();
      uiUpdateTimer.stop();
    }

    if (playbackThread != null) playbackThread.interrupt();

    if (dataLine != null) dataLine.close();

    playButton.setIcon(playIcon);
    playButton.setToolTipText("Play");
  }

  private void updatePlaybackUI() {

    if (dataLine != null && dataLine.isOpen() && audioDurationMicros > 0) {

      long elapsedMicros = dataLine.getMicrosecondPosition();

      currentMicros = playbackStartOffsetMicros + elapsedMicros;
      double progress = (double) currentMicros / audioDurationMicros;

      waveformPanel.setNeedlePosition(progress);

      long totalSeconds = currentMicros / 1_000_000L;
      long minutes = totalSeconds / 60;
      long seconds = totalSeconds % 60;
      long millis = (currentMicros / 1000) % 1000;

      clockLabel.setText(String.format("%02d:%02d.%03d", minutes, seconds, millis));
    }

    // Update the dB and Hz meters
    if (dbGauge != null) dbGauge.setValue(currentDb);
    if (hzGauge != null) hzGauge.setValue(currentPeakHz);

    if (volumeSlider instanceof AudioMeterSlider) {
      ((AudioMeterSlider) volumeSlider).setMeterLevel(currentRms);
    }
  }

  private void updateLiveVisualizer(byte[] audioBytes, int bytesRead, AudioFormat format) {

    int frameSize = format.getFrameSize();
    int numChannels = format.getChannels();
    int sampleSizeInBytes = format.getSampleSizeInBits() / 8;
    int numFrames = bytesRead / frameSize;
    float[] samples = new float[numFrames];

    for (int frame = 0; frame < numFrames; frame++) {

      int frameStartByte = frame * frameSize;
      double channelSum = 0;

      for (int channel = 0; channel < numChannels; channel++) {

        int sampleStartByte = frameStartByte + channel * sampleSizeInBytes;
        int low = audioBytes[sampleStartByte] & 0xFF;
        int high = audioBytes[sampleStartByte + 1];
        short sampleValue = (short) ((high << 8) | low);
        channelSum += sampleValue;
      }

      samples[frame] = (float) (channelSum / numChannels) / 32768.0f;
    }
    liveWaveformPanel.updateSamples(samples);
  }

  public void startScrubbing() {
    if (!playButton.isEnabled()) return;
    wasPlayingBeforeScrub = isPlaying;
    if (isPlaying) stopPlayback();
  }

  public void seekTo(double progress) {
    if (audioDurationMicros <= 0) return;
    currentMicros = (long) (audioDurationMicros * progress);
    waveformPanel.setNeedlePosition(progress);
  }

  public void endScrubbing() {
    if (!playButton.isEnabled()) return;
    if (wasPlayingBeforeScrub) startPlayback();
  }

  private void toggleFullScreen() {
    GraphicsDevice device = getGraphicsConfiguration().getDevice();
    if (isFullScreen) {
      device.setFullScreenWindow(null);
      dispose();
      setUndecorated(false);
      setBounds(normalBounds);
      setVisible(true);
      isFullScreen = false;

      waveformPanel.revalidate();
      waveformPanel.repaint();

    } else {
      normalBounds = getBounds();
      dispose();
      setUndecorated(true);
      device.setFullScreenWindow(this);
      setVisible(true);
      isFullScreen = true;
    }
  }

  private void updateMarqueeCanvas() {
    if (baseMarqueeText == null || baseMarqueeText.trim().isEmpty() || !isDisplayable()) {
      return;
    }
    int charWidthHeuristic = 8;
    int paddingChars = getWidth() / charWidthHeuristic;
    String padding = " ".repeat(Math.max(10, paddingChars));
    this.scrollingCanvas = padding + baseMarqueeText;
  }

  private void startTitleMarquee(String text) {
    stopTitleMarquee();

    if (text == null || text.trim().isEmpty()) {
      setTitle(DEFAULT_TITLE);
      return;
    }

    this.baseMarqueeText = text;
    updateMarqueeCanvas();
    marqueePosition = 0;

    titleMarqueeTimer =
        new Timer(
            150,
            e -> {
              if (scrollingCanvas == null) return;

              marqueePosition++;

              if (marqueePosition >= scrollingCanvas.length()) {
                marqueePosition = 0;
              }

              setTitle(scrollingCanvas.substring(marqueePosition));
            });

    titleMarqueeTimer.start();
  }

  private void stopTitleMarquee() {
    if (titleMarqueeTimer != null && titleMarqueeTimer.isRunning()) {
      titleMarqueeTimer.stop();
    }
    titleMarqueeTimer = null;
    baseMarqueeText = null;
    scrollingCanvas = null;
    setTitle(DEFAULT_TITLE);
  }

  private float[] bytesToFloats(byte[] buffer, int bytesRead, AudioFormat format) {
    int bytesPerSample = format.getSampleSizeInBits() / 8;
    int numSamples = bytesRead / bytesPerSample;
    float[] samples = new float[numSamples];
    for (int i = 0; i < numSamples; i++) {
      int sampleIndex = i * bytesPerSample;
      int low = buffer[sampleIndex] & 0xFF;
      int high = buffer[sampleIndex + 1];
      short sample = (short) ((high << 8) | low);
      samples[i] = sample / 32768.0f;
    }
    return samples;
  }

  private byte[] floatsToBytes(float[] samples, AudioFormat format) {
    int bytesPerSample = format.getSampleSizeInBits() / 8;
    byte[] buffer = new byte[samples.length * bytesPerSample];
    for (int i = 0; i < samples.length; i++) {
      float sample = Math.max(-1.0f, Math.min(1.0f, samples[i]));
      short val = (short) (sample * 32767.0);
      int sampleIndex = i * bytesPerSample;
      buffer[sampleIndex] = (byte) (val & 0xFF);
      buffer[sampleIndex + 1] = (byte) ((val >> 8) & 0xFF);
    }
    return buffer;
  }

  // Replace your existing zoomTo and resetZoom methods, and add zoomIn/zoomOut
  public void zoomTo(Rectangle zoomRect) {
    if (pcmData == null || zoomRect == null || zoomRect.width <= 0 || zoomRect.height <= 0) {
      return;
    }

    if (isPlaying) {
      stopPlayback();
    }

    // Store current state in history before zooming
    pcmDataHistory.add(pcmData);
    durationHistory.add(audioDurationMicros);

    double drawableWidth = waveformPanel.getWaveformDrawableWidth();
    double startProgress = (zoomRect.x - WaveformPanel.Y_AXIS_PADDING) / drawableWidth;
    double endProgress = (zoomRect.x + zoomRect.width - WaveformPanel.Y_AXIS_PADDING) / drawableWidth;

    startProgress = Math.max(0, Math.min(1, startProgress));
    endProgress = Math.max(0, Math.min(1, endProgress));

    if (endProgress <= startProgress) {
      // Invalid selection, so remove the state we just added
      pcmDataHistory.remove(pcmDataHistory.size() - 1);
      durationHistory.remove(durationHistory.size() - 1);
      return;
    }

    int frameSize = pcmFormat.getFrameSize();
    int totalFrames = pcmData.length / frameSize;
    int startFrame = (int) (totalFrames * startProgress);
    int endFrame = (int) (totalFrames * endProgress);
    int startByte = startFrame * frameSize;
    int endByte = endFrame * frameSize;
    int lengthInBytes = endByte - startByte;

    if (lengthInBytes <= 0) {
      // Invalid selection, so remove the state we just added
      pcmDataHistory.remove(pcmDataHistory.size() - 1);
      durationHistory.remove(durationHistory.size() - 1);
      return;
    }

    byte[] zoomedPcmData = new byte[lengthInBytes];
    System.arraycopy(pcmData, startByte, zoomedPcmData, 0, lengthInBytes);

    pcmData = zoomedPcmData;
    audioDurationMicros = (long) (1_000_000L * (endFrame - startFrame) / pcmFormat.getFrameRate());

    currentMicros = 0;
    waveformPanel.setNeedlePosition(0);
    regenerateWaveformImage();
  }

  public void zoomIn() {
    if (pcmData == null) return;
    if (isPlaying) stopPlayback();

    // Store current state
    pcmDataHistory.add(pcmData);
    durationHistory.add(audioDurationMicros);

    int frameSize = pcmFormat.getFrameSize();
    int totalFrames = pcmData.length / frameSize;
    int centerFrame = totalFrames / 2;
    int newHalfWidth = totalFrames / 4; // Zoom in by factor of 2

    int startFrame = Math.max(0, centerFrame - newHalfWidth);
    int endFrame = Math.min(totalFrames, centerFrame + newHalfWidth);

    int startByte = startFrame * frameSize;
    int endByte = endFrame * frameSize;
    int lengthInBytes = endByte - startByte;

    if (lengthInBytes <= 0) {
      pcmDataHistory.remove(pcmDataHistory.size() - 1);
      durationHistory.remove(durationHistory.size() - 1);
      return;
    }

    byte[] zoomedPcmData = new byte[lengthInBytes];
    System.arraycopy(pcmData, startByte, zoomedPcmData, 0, lengthInBytes);

    pcmData = zoomedPcmData;
    audioDurationMicros = (long) (1_000_000L * (endFrame - startFrame) / pcmFormat.getFrameRate());

    currentMicros = 0;
    waveformPanel.setNeedlePosition(0);
    regenerateWaveformImage();
  }

  public void zoomOut() {
    if (pcmDataHistory.size() <= 1) return; // Can't zoom out from the base level
    if (isPlaying) stopPlayback();

    // Restore the previous state from history
    pcmData = pcmDataHistory.remove(pcmDataHistory.size() - 1);
    audioDurationMicros = durationHistory.remove(durationHistory.size() - 1);

    currentMicros = 0;
    waveformPanel.setNeedlePosition(0);
    regenerateWaveformImage();
  }

  public void resetZoom() {
    if (pcmDataHistory.size() <= 1) return; // Not zoomed
    if (isPlaying) stopPlayback();

    // Restore the original data from the first entry in history
    pcmData = pcmDataHistory.get(0);
    audioDurationMicros = durationHistory.get(0);

    // Clear the history, leaving only the original state
    byte[] originalData = pcmDataHistory.get(0);
    long originalDuration = durationHistory.get(0);
    pcmDataHistory.clear();
    durationHistory.clear();
    pcmDataHistory.add(originalData);
    durationHistory.add(originalDuration);


    currentMicros = 0;
    waveformPanel.setNeedlePosition(0);
    regenerateWaveformImage();
  }

  /** A simple panel to display a real-time frequency spectrum analyzer. */
  private class SpectrumAnalyzerPanel extends JPanel {
    private double[] magnitudes;
    private final Object magLock = new Object();
    private static final int NUM_BARS = 12; // Display 32 frequency bands
    private Color barColor = Mp3Visualizer.this.spectrumColor;

    public void setBarColor(Color color) {
      this.barColor = color;
    }

    public void updateMagnitudes(double[] newMagnitudes) {
      if (newMagnitudes == null || newMagnitudes.length == 0) return;

      double[] barMagnitudes = new double[NUM_BARS];
      double maxLog = Math.log(newMagnitudes.length);
      int currentBin = 0;

      for (int i = 0; i < NUM_BARS; i++) {
        // Logarithmically determine the range of FFT bins for this bar
        double endBinD = Math.exp(maxLog * (i + 1) / NUM_BARS);
        int endBin = Math.min((int) endBinD, newMagnitudes.length);
        if (endBin <= currentBin) endBin = currentBin + 1;
        if (endBin > newMagnitudes.length) endBin = newMagnitudes.length;

        double peakMag = 0;
        for (int j = currentBin; j < endBin; j++) {
          if (newMagnitudes[j] > peakMag) {
            peakMag = newMagnitudes[j];
          }
        }
        // Normalize logarithmically to a 0-1 range for drawing.
        // The scale (e.g., max of 100) is for tuning visualization.
        double scaledMag = Math.log1p(peakMag) / Math.log1p(100);
        barMagnitudes[i] = Math.min(scaledMag, 1.0);

        currentBin = endBin;
        if (currentBin >= newMagnitudes.length) break;
      }

      synchronized (magLock) {
        this.magnitudes = barMagnitudes;
      }
      repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
      super.paintComponent(g);
      Graphics2D g2d = (Graphics2D) g;

      // Use the parent's background color
      g2d.setColor(Mp3Visualizer.this.backgroundColor);
      g2d.fillRect(0, 0, getWidth(), getHeight());
      g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

      synchronized (magLock) {
        if (magnitudes == null || NUM_BARS == 0) {
          return;
        }

        int width = getWidth();
        int height = getHeight();
        int barWidth = width / NUM_BARS;
        int gap = Math.max(1, barWidth / 5);

        // Create a single vertical gradient for the entire panel.
        // This makes the color dependent on the amplitude (y-position).
        GradientPaint gp =
            new GradientPaint(
                0,
                height,
                this.barColor, // The base color at the bottom (low amplitude)
                0,
                0,
                Mp3Visualizer.this.highAmpColor); // The "hot" color at the top (high amplitude)

        // Set the gradient as the paint for all subsequent drawing
        g2d.setPaint(gp);

        for (int i = 0; i < NUM_BARS; i++) {
          int barHeight = (int) (magnitudes[i] * height);
          int x = i * barWidth;
          int y = height - barHeight;

          // Each bar is now drawn with a slice of the gradient
          g2d.fillRect(x + (gap / 2), y, barWidth - gap, barHeight);
        }
      }
    }
  }

  /** A utility class to perform a Fast Fourier Transform. */
  private static class FFT {
    public static double[] transform(float[] buffer) {
      final int n = buffer.length;
      if ((n & (n - 1)) != 0) {
        // Input must be a power of 2.
        return new double[n / 2];
      }
      double[] real = new double[n];
      double[] imag = new double[n];
      for (int i = 0; i < n; i++) {
        real[i] = buffer[i];
      }
      // Bit-reversal permutation
      int j = 0;
      for (int i = 0; i < n - 1; i++) {
        if (i < j) {
          double tempReal = real[i];
          real[i] = real[j];
          real[j] = tempReal;
        }
        int k = n / 2;
        while (k <= j) {
          j -= k;
          k /= 2;
        }
        j += k;
      }
      // Cooley-Tukey FFT
      for (int len = 2; len <= n; len <<= 1) {
        double angle = -2 * Math.PI / len;
        double w_real = Math.cos(angle);
        double w_imag = Math.sin(angle);
        for (int i = 0; i < n; i += len) {
          double t_real = 1;
          double t_imag = 0;
          for (j = 0; j < len / 2; j++) {
            int a = i + j;
            int b = a + len / 2;
            double v_real = real[b] * t_real - imag[b] * t_imag;
            double v_imag = real[b] * t_imag + imag[b] * t_real;
            real[b] = real[a] - v_real;
            imag[b] = imag[a] - v_imag;
            real[a] += v_real;
            imag[a] += v_imag;
            double next_t_real = t_real * w_real - t_imag * w_imag;
            t_imag = t_real * w_imag + t_imag * w_real;
            t_real = next_t_real;
          }
        }
      }
      // Calculate magnitudes
      double[] magnitudes = new double[n / 2];
      for (int i = 0; i < n / 2; i++) {
        magnitudes[i] = Math.sqrt(real[i] * real[i] + imag[i] * imag[i]);
      }
      return magnitudes;
    }
  }

  /**
   * A custom component that displays a value as a vertical LED-style gauge,
   * inspired by classic hardware meters.
   */
  /**
   * A custom component that displays a value as a vertical LED-style gauge,
   * inspired by classic hardware meters.
   */
  private static class VerticalGauge extends JPanel {
    private final String unitLabel;
    private final double minValue, maxValue;
    private volatile double value;
    private final boolean isLogarithmic; // Field to hold the scale type

    // --- CONSTRUCTORS (Corrected) ---
    /**
     * Creates a standard, linear gauge.
     */
    public VerticalGauge(String unitLabel, double minValue, double maxValue, double initialValue) {
      this(unitLabel, minValue, maxValue, initialValue, false); // Defaults to linear scale
    }

    /**
     * Creates a gauge with a specified scale type (linear or logarithmic).
     */
    public VerticalGauge(String unitLabel, double minValue, double maxValue, double initialValue, boolean isLogarithmic) {
      this.unitLabel = unitLabel;
      this.minValue = minValue;
      this.maxValue = maxValue;
      this.value = initialValue;
      this.isLogarithmic = isLogarithmic; // This line is now correct

      int totalGaugeHeight = (SEGMENT_HEIGHT + SEGMENT_GAP) * SEGMENT_COUNT;
      int digitalDisplayHeight = 45;
      setPreferredSize(new Dimension(GAUGE_WIDTH + SCALE_WIDTH + PADDING * 2, totalGaugeHeight + digitalDisplayHeight + PADDING * 2));
    }


    // Visual configuration
    private static final int SEGMENT_COUNT = 24;
    private static final int SEGMENT_HEIGHT = 4;
    private static final int SEGMENT_GAP = 2;
    private static final int GAUGE_WIDTH = 20;
    private static final int SCALE_WIDTH = 30;
    private static final int PADDING = 10;
    private static final Color COLOR_GREEN = new Color(34, 177, 76);
    private static final Color COLOR_YELLOW = new Color(255, 242, 0);
    private static final Color COLOR_RED = new Color(237, 28, 36);
    private static final Color COLOR_OFF = new Color(40, 40, 40);
    private static final Color COLOR_BACKGROUND = new Color(25, 25, 25);
    private static final Font FONT_DIGITAL = new Font("Monospaced", Font.BOLD, 22);
    private static final Font FONT_LABEL = new Font("SansSerif", Font.BOLD, 10);
    private static final Font FONT_SCALE = new Font("SansSerif", Font.PLAIN, 9);

    public void setValue(double value) {
      this.value = Math.max(minValue, Math.min(maxValue, value));
      repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
      super.paintComponent(g);
      Graphics2D g2d = (Graphics2D) g.create();
      g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

      g2d.setColor(COLOR_BACKGROUND);
      g2d.fillRect(0, 0, getWidth(), getHeight());

      int barAreaX = PADDING;
      int barAreaY = PADDING;
      int totalBarHeight = (SEGMENT_HEIGHT + SEGMENT_GAP) * SEGMENT_COUNT - SEGMENT_GAP;

      drawScale(g2d, barAreaX + GAUGE_WIDTH + 5, barAreaY, totalBarHeight);

      double valueRatio;
      if (isLogarithmic) {
        if (value < minValue || minValue <= 0) {
          valueRatio = 0;
        } else {
          valueRatio = (Math.log(value) - Math.log(minValue)) / (Math.log(maxValue) - Math.log(minValue));
        }
      } else {
        valueRatio = (value - minValue) / (maxValue - minValue);
      }
      int segmentsToLight = (int) (valueRatio * SEGMENT_COUNT);

      for (int i = 0; i < SEGMENT_COUNT; i++) {
        int segmentY = barAreaY + (SEGMENT_COUNT - 1 - i) * (SEGMENT_HEIGHT + SEGMENT_GAP);
        double segmentRatio = (double) i / (SEGMENT_COUNT - 1);

        if (i < segmentsToLight) {
          if (segmentRatio > 0.85) g2d.setColor(COLOR_RED);
          else if (segmentRatio > 0.6) g2d.setColor(COLOR_YELLOW);
          else g2d.setColor(COLOR_GREEN);
        } else {
          g2d.setColor(COLOR_OFF);
        }
        g2d.fillRect(barAreaX, segmentY, GAUGE_WIDTH, SEGMENT_HEIGHT);
      }

      drawDigitalDisplay(g2d, PADDING, barAreaY + totalBarHeight + 10, getWidth() - PADDING * 2);

      g2d.dispose();
    }

    private void drawScale(Graphics2D g2d, int x, int y, int height) {
      g2d.setColor(Color.LIGHT_GRAY);
      g2d.setFont(FONT_SCALE);
      FontMetrics fm = g2d.getFontMetrics();

      if (isLogarithmic) {
        double[] logTicks = {20, 200, 2000, 20000};
        for (double tickValue : logTicks) {
          if (tickValue >= minValue && tickValue <= maxValue) {
            double logRatio = (Math.log(tickValue) - Math.log(minValue)) / (Math.log(maxValue) - Math.log(minValue));
            int tickY = y + (int)(height * (1.0 - logRatio));

            String label;
            if (tickValue < 1000) label = String.valueOf((int)tickValue);
            else label = (int)(tickValue / 1000) + "k";

            g2d.drawLine(x, tickY, x + 5, tickY);
            g2d.drawString(label, x + 8, tickY + fm.getAscent() / 2);
          }
        }
      } else {
        for(int i = 0; i <= 2; i++) {
          double ratio = i / 2.0;
          int tickY = y + (int)(height * (1.0 - ratio));
          int tickLabelValue = (int)(minValue + ratio * (maxValue - minValue));
          String label = String.valueOf(tickLabelValue);

          g2d.drawLine(x, tickY, x + 5, tickY);
          g2d.drawString(label, x + 8, tickY + fm.getAscent() / 2);
        }
      }
    }

    private void drawDigitalDisplay(Graphics2D g2d, int x, int y, int width) {
      g2d.setColor(Color.BLACK);
      g2d.fillRect(x, y, width, 40);

      g2d.setColor(COLOR_RED);
      g2d.setFont(FONT_DIGITAL);
      FontMetrics fm = g2d.getFontMetrics();

      String valueStr;
      if (value <= minValue) {
        valueStr = "---";
      } else if (unitLabel.equals("dB")) {
        valueStr = String.format("%4.1f", value);
      } else {
        valueStr = String.format("%,.0f", value);
      }

      int strWidth = fm.stringWidth(valueStr);
      g2d.drawString(valueStr, x + (width - strWidth) / 2, y + 27);

      g2d.setColor(Color.GRAY);
      g2d.setFont(FONT_LABEL);
      FontMetrics fmLabel = g2d.getFontMetrics();
      int labelWidth = fmLabel.stringWidth(unitLabel);
      g2d.drawString(unitLabel, x + (width - labelWidth) / 2, y + 38);
    }
  }

  /**
   * A custom component that displays a value as a horizontal LED-style gauge.
   */
  private static class HorizontalGauge extends JPanel {
    private final String unitLabel;
    private final double minValue, maxValue;
    private volatile double value;
    private final boolean isLogarithmic;

    // --- CONSTRUCTORS ---
    public HorizontalGauge(String unitLabel, double minValue, double maxValue, double initialValue) {
      this(unitLabel, minValue, maxValue, initialValue, false);
    }

    public HorizontalGauge(String unitLabel, double minValue, double maxValue, double initialValue, boolean isLogarithmic) {
      this.unitLabel = unitLabel;
      this.minValue = minValue;
      this.maxValue = maxValue;
      this.value = initialValue;
      this.isLogarithmic = isLogarithmic;

      int gaugeHeight = 20;
      int scaleHeight = 15;
      int digitalDisplayWidth = 70;
      setPreferredSize(new Dimension(240, gaugeHeight + scaleHeight + PADDING));
    }

    // Visual configuration
    private static final int SEGMENT_COUNT = 24;
    private static final int SEGMENT_WIDTH = 5;
    private static final int SEGMENT_GAP = 2;
    private static final int GAUGE_HEIGHT = 18;
    private static final int PADDING = 5;
    private static final Color COLOR_GREEN = new Color(34, 177, 76);
    private static final Color COLOR_YELLOW = new Color(255, 242, 0);
    private static final Color COLOR_RED = new Color(237, 28, 36);
    private static final Color COLOR_OFF = new Color(40, 40, 40);
    private static final Color COLOR_BACKGROUND = new Color(25, 25, 25);
    private static final Font FONT_DIGITAL = new Font("Monospaced", Font.BOLD, 18);
    private static final Font FONT_LABEL = new Font("SansSerif", Font.BOLD, 9);
    private static final Font FONT_SCALE = new Font("SansSerif", Font.PLAIN, 9);

    public void setValue(double value) {
      this.value = Math.max(minValue, Math.min(maxValue, value));
      repaint();
    }

    // Replace the paintComponent method in HorizontalGauge
    @Override
    protected void paintComponent(Graphics g) {
      super.paintComponent(g);
      Graphics2D g2d = (Graphics2D) g.create();
      g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

      g2d.setColor(COLOR_BACKGROUND);
      g2d.fillRect(0, 0, getWidth(), getHeight());

      int digitalDisplayWidth = 65;

      // --- LAYOUT LOGIC (Flipped) ---
      // 1. Draw Digital Readout first on the LEFT
      drawDigitalDisplay(g2d, PADDING, PADDING, digitalDisplayWidth);

      // 2. Define the gauge area to the RIGHT of the digital display
      int gaugeAreaX = PADDING + digitalDisplayWidth + PADDING;
      int gaugeAreaY = PADDING;
      int gaugeAreaWidth = getWidth() - gaugeAreaX - PADDING;

      // 3. Draw Scale and Segments in the new gauge area
      drawScale(g2d, gaugeAreaX, gaugeAreaY + GAUGE_HEIGHT + 2, gaugeAreaWidth);

      double valueRatio;
      if (isLogarithmic) {
        if (value < minValue || minValue <= 0) valueRatio = 0;
        else valueRatio = (Math.log(value) - Math.log(minValue)) / (Math.log(maxValue) - Math.log(minValue));
      } else {
        valueRatio = (value - minValue) / (maxValue - minValue);
      }
      int segmentsToLight = (int) (valueRatio * SEGMENT_COUNT);

      for (int i = 0; i < SEGMENT_COUNT; i++) {
        int segmentX = gaugeAreaX + i * (SEGMENT_WIDTH + SEGMENT_GAP);
        double segmentRatio = (double) i / (SEGMENT_COUNT - 1);

        if (i < segmentsToLight) {
          if (segmentRatio > 0.85) g2d.setColor(COLOR_RED);
          else if (segmentRatio > 0.6) g2d.setColor(COLOR_YELLOW);
          else g2d.setColor(COLOR_GREEN);
        } else {
          g2d.setColor(COLOR_OFF);
        }
        g2d.fillRect(segmentX, gaugeAreaY, SEGMENT_WIDTH, GAUGE_HEIGHT);
      }

      g2d.dispose();
    }

    private void drawScale(Graphics2D g2d, int x, int y, int width) {
      g2d.setColor(Color.LIGHT_GRAY);
      g2d.setFont(FONT_SCALE);
      FontMetrics fm = g2d.getFontMetrics();

      if (isLogarithmic) {
        double[] logTicks = {20, 200, 2000, 20000};
        for (double tickValue : logTicks) {
          if (tickValue >= minValue && tickValue <= maxValue) {
            double logRatio = (Math.log(tickValue) - Math.log(minValue)) / (Math.log(maxValue) - Math.log(minValue));
            int tickX = x + (int)(width * logRatio);

            String label;
            if (tickValue < 1000) label = String.valueOf((int)tickValue);
            else label = (int)(tickValue / 1000) + "k";

            g2d.drawLine(tickX, y, tickX, y + 4);
            int labelWidth = fm.stringWidth(label);
            g2d.drawString(label, tickX - labelWidth / 2, y + 15);
          }
        }
      } else {
        for(int i = 0; i <= 2; i++) {
          double ratio = i / 2.0;
          int tickX = x + (int)(width * ratio);
          int tickLabelValue = (int)(minValue + ratio * (maxValue - minValue));
          String label = String.valueOf(tickLabelValue);

          g2d.drawLine(tickX, y, tickX, y + 4);
          int labelWidth = fm.stringWidth(label);
          g2d.drawString(label, tickX - labelWidth / 2, y + 15);
        }
      }
    }

    // Replace the drawDigitalDisplay method in the HorizontalGauge class
    private void drawDigitalDisplay(Graphics2D g2d, int x, int y, int width) {
      // Background for digital display
      g2d.setColor(Color.BLACK);
      g2d.fillRect(x, y, width, getHeight() - PADDING * 2);

      // Value Text
      g2d.setColor(COLOR_RED);
      g2d.setFont(FONT_DIGITAL);
      FontMetrics fm = g2d.getFontMetrics();

      String valueStr;
      if (value <= minValue) {
        valueStr = "---";
      } else if (unitLabel.equals("dB")) {
        valueStr = String.format("%4.1f", value);
      } else {
        valueStr = String.format("%,.0f", value);
      }

      // --- CENTERED TEXT LOGIC ---
      // Calculate the width of the string to center it horizontally
      int strWidth = fm.stringWidth(valueStr);
      g2d.drawString(valueStr, x + (width - strWidth) / 2, y + 18);

      // Unit Label (already centered)
      g2d.setColor(Color.GRAY);
      g2d.setFont(FONT_LABEL);
      FontMetrics fmLabel = g2d.getFontMetrics();
      int labelWidth = fmLabel.stringWidth(unitLabel);
      g2d.drawString(unitLabel, x + (width - labelWidth) / 2, y + 30);
    }
  }

  private static class WaveformPanel extends JPanel {

    private final Mp3Visualizer parent;
    private BufferedImage waveformImage;
    private double needleProgress = -1.0;

    public static final int Y_AXIS_PADDING = 40;

    private final Color needleColor = new Color(255, 0, 0, 180);
    private Rectangle fullscreenButtonBounds, screenshotButtonBounds;
    private boolean isHoveringOnFullscreen,
        isPressingOnFullscreen,
        isHoveringOnScreenshot,
        isPressingOnScreenshot;
    private boolean isScrubbingForTooltip = false;
    private Point scrubPoint = null;
    private static final int SCRUBBER_IMAGE_WIDTH = 20;
    private static final int SCRUBBER_Y_OFFSET = 0;
    private final Image scrubberImage;
    private final Image dragDropImage;

    // --- Context Menu ---
    private JPopupMenu popupMenu;
    // In WaveformPanel, with the other JMenuItem declarations
    private JMenuItem playPauseItem, ejectItem, openLocationItem, fullscreenItem, exitItem, zoomInItem, zoomOutItem, resetZoomItem;
    private Rectangle zoomRect;

    // --- Loading UI Components ---
    private boolean isLoading = false;
    private final JPanel progressPanel;
    private final JProgressBar progressBar;
    private final JLabel progressLabel;
    private final Timer progressAnimator;
    private static final int PROGRESS_PANEL_HEIGHT = 40;

    public WaveformPanel(Mp3Visualizer parent, ImageIcon scrubberIcon, ImageIcon dragDropIcon) {
      this.parent = parent;
      this.scrubberImage = (scrubberIcon != null) ? scrubberIcon.getImage() : null;
      this.dragDropImage = (dragDropIcon != null) ? dragDropIcon.getImage() : null;

      setLayout(null);

      progressPanel = new JPanel(new BorderLayout(10, 0));
      progressPanel.setBounds(0, getHeight(), getWidth(), PROGRESS_PANEL_HEIGHT);
      progressPanel.setBackground(new Color(0, 0, 0, 150));
      progressPanel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
      progressPanel.setVisible(false);

      progressBar = new JProgressBar();
      progressLabel = new JLabel(" ", SwingConstants.CENTER);
      progressLabel.setForeground(Color.WHITE);

      progressPanel.add(progressLabel, BorderLayout.NORTH);
      progressPanel.add(progressBar, BorderLayout.CENTER);
      add(progressPanel);

      progressAnimator = new Timer(15, e -> animateProgressPanel());
      progressAnimator.setRepeats(true);

      createContextMenu();
      addMouseListeners();
      addComponentListener(
          new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
              positionOverlays();
            }
          });
    }

    private void createContextMenu() {

      popupMenu = new JPopupMenu();

      playPauseItem = new JMenuItem("Play", parent.menuPlayIcon);
      ejectItem = new JMenuItem("Eject", parent.menuEjectIcon);
      openLocationItem = new JMenuItem("Open File Location", parent.menuOpenIcon);
      fullscreenItem = new JMenuItem("Full Screen", parent.menuFullscreenIcon);
      zoomInItem = new JMenuItem("Zoom In", parent.menuZoomInIcon);
      zoomOutItem = new JMenuItem("Zoom Out", parent.menuZoomOutIcon);
      resetZoomItem = new JMenuItem("Reset Zoom", parent.menuZoomResetIcon);
      exitItem = new JMenuItem("Exit", parent.menuExitIcon);

      playPauseItem.addActionListener(e -> parent.togglePlayback());
      ejectItem.addActionListener(e -> parent.ejectAndResetUI());
      openLocationItem.addActionListener(e -> parent.openFileLocation());
      fullscreenItem.addActionListener(e -> parent.toggleFullScreen());
      zoomInItem.addActionListener(e -> parent.zoomIn());
      zoomOutItem.addActionListener(e -> parent.zoomOut());
      resetZoomItem.addActionListener(e -> parent.resetZoom()); // Action for reset
      exitItem.addActionListener(e -> System.exit(0));

      popupMenu.add(playPauseItem);
      popupMenu.add(ejectItem);
      popupMenu.add(openLocationItem);
      popupMenu.add(fullscreenItem);
      popupMenu.addSeparator();
      popupMenu.add(zoomInItem);
      popupMenu.add(zoomOutItem);
      popupMenu.add(resetZoomItem); // Add to menu
      popupMenu.addSeparator();
      popupMenu.add(exitItem);
    }

    private void showPopupMenu(MouseEvent e) {
      boolean fileLoaded = (parent.inputFile != null);
      playPauseItem.setText(parent.isPlaying ? "Pause" : "Play");
      playPauseItem.setEnabled(fileLoaded);
      ejectItem.setEnabled(fileLoaded);
      openLocationItem.setEnabled(fileLoaded);

      // A selection has been drawn with the right mouse button
      if (zoomRect != null && zoomRect.width > 0 && zoomRect.height > 0) {
        zoomInItem.setText("Zoom to Selection");
        zoomInItem.setEnabled(true);
        zoomInItem.addActionListener(event -> {
          parent.zoomTo(zoomRect);
          zoomRect = null;
          repaint();
        });
      } else {
        zoomInItem.setText("Zoom In");
        zoomInItem.setEnabled(fileLoaded);
        // Remove specific listener to avoid conflict
        for(ActionListener al : zoomInItem.getActionListeners()) {
          zoomInItem.removeActionListener(al);
        }
        zoomInItem.addActionListener(event -> parent.zoomIn());
      }

      // Update zoom menu items based on zoom history
      boolean isZoomed = parent.pcmDataHistory.size() > 1;
      zoomOutItem.setEnabled(isZoomed);
      resetZoomItem.setEnabled(isZoomed);

      popupMenu.show(e.getComponent(), e.getX(), e.getY());
    }


    private void positionOverlays() {
      int width = getWidth();
      int height = getHeight();

      if (!progressAnimator.isRunning()) {
        int targetY = isLoading ? height - PROGRESS_PANEL_HEIGHT : height;
        progressPanel.setBounds(0, targetY, width, PROGRESS_PANEL_HEIGHT);
      } else {
        progressPanel.setSize(width, PROGRESS_PANEL_HEIGHT);
      }
    }

    private void animateProgressPanel() {
      int currentY = progressPanel.getY();
      int targetY = isLoading ? getHeight() - PROGRESS_PANEL_HEIGHT : getHeight();

      if (currentY == targetY) {
        progressAnimator.stop();
        if (!isLoading) {
          progressPanel.setVisible(false);
        }
        return;
      }

      int step = (targetY > currentY) ? 2 : -2;
      int newY = currentY + step;

      if ((step > 0 && newY > targetY) || (step < 0 && newY < targetY)) {
        newY = targetY;
      }
      progressPanel.setLocation(0, newY);
    }

    public int getWaveformDrawableWidth() {
      return getWidth() - (2 * Y_AXIS_PADDING);
    }

    private void addMouseListeners() {
      setToolTipText("");
      MouseAdapter mouseAdapter =
          new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
              if (SwingUtilities.isLeftMouseButton(e)
                  && !isLoading
                  && getWaveformImage() == null
                  && e.getClickCount() == 2) {
                parent.openFilePicker();
              }
            }

            @Override
            public void mousePressed(MouseEvent e) {
              if (e.isPopupTrigger()) {
                showPopupMenu(e);
                return;
              }
              if (SwingUtilities.isLeftMouseButton(e)) {
                if (isLoading || getWaveformImage() == null) return;

                int drawableX = Y_AXIS_PADDING;
                int drawableWidth = getWaveformDrawableWidth();

                if (fullscreenButtonBounds != null
                    && fullscreenButtonBounds.contains(e.getPoint())) {
                  isPressingOnFullscreen = true;
                } else if (screenshotButtonBounds != null
                    && screenshotButtonBounds.contains(e.getPoint())) {
                  isPressingOnScreenshot = true;
                } else if (e.getX() >= drawableX && e.getX() <= drawableX + drawableWidth) {
                  parent.startScrubbing();
                  updateSeekPosition(e);
                  isScrubbingForTooltip = true;
                  scrubPoint = e.getPoint();
                }
                repaint();
              } else if (SwingUtilities.isRightMouseButton(e)) {
                if (getWaveformImage() != null) {
                  zoomRect = new Rectangle(e.getPoint());
                  repaint();
                }
              }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
              if (e.isPopupTrigger()) {
                showPopupMenu(e);
                return;
              }
              if (SwingUtilities.isLeftMouseButton(e)) {
                if (isLoading || getWaveformImage() == null) return;
                boolean wasButtonAction = false;
                if (isPressingOnFullscreen) {
                  isPressingOnFullscreen = false;
                  wasButtonAction = true;
                  if (fullscreenButtonBounds != null
                      && fullscreenButtonBounds.contains(e.getPoint())) parent.toggleFullScreen();
                } else if (isPressingOnScreenshot) {
                  isPressingOnScreenshot = false;
                  wasButtonAction = true;
                  if (screenshotButtonBounds != null
                      && screenshotButtonBounds.contains(e.getPoint()))
                    parent.exportWaveformImage();
                }
                if (!wasButtonAction) parent.endScrubbing();

                isScrubbingForTooltip = false;
                scrubPoint = null;
                repaint();
              } else if (SwingUtilities.isRightMouseButton(e)) {
                if (zoomRect != null) {
                  parent.zoomTo(zoomRect);
                  zoomRect = null;
                  repaint();
                }
              }
            }

            @Override
            public void mouseDragged(MouseEvent e) {
              if (SwingUtilities.isLeftMouseButton(e)) {
                if (isLoading || getWaveformImage() == null) return;
                if (!isPressingOnFullscreen && !isPressingOnScreenshot) {
                  updateSeekPosition(e);
                  scrubPoint = e.getPoint();
                }
              } else if (SwingUtilities.isRightMouseButton(e)) {
                if (zoomRect != null) {
                  zoomRect.setSize(e.getX() - zoomRect.x, e.getY() - zoomRect.y);
                  repaint();
                }
              }
            }

            @Override
            public void mouseMoved(MouseEvent e) {
              if (isLoading || getWaveformImage() == null) return;
              boolean needsRepaint = false;
              boolean oldHoverFullscreen = isHoveringOnFullscreen;
              isHoveringOnFullscreen =
                  fullscreenButtonBounds != null && fullscreenButtonBounds.contains(e.getPoint());
              if (oldHoverFullscreen != isHoveringOnFullscreen) needsRepaint = true;

              boolean oldHoverScreenshot = isHoveringOnScreenshot;
              isHoveringOnScreenshot =
                  screenshotButtonBounds != null && screenshotButtonBounds.contains(e.getPoint());
              if (oldHoverScreenshot != isHoveringOnScreenshot) needsRepaint = true;

              if (needsRepaint) repaint();
            }
          };
      addMouseListener(mouseAdapter);
      addMouseMotionListener(mouseAdapter);
    }

    public void setLoading(boolean loading, String message) {
      this.isLoading = loading;
      setWaveform(null);

      if (loading) {
        progressLabel.setText(message);
        progressBar.setIndeterminate(true);
        progressBar.setValue(0);
        progressPanel.setVisible(true);
      }
      progressAnimator.start();
      repaint();
    }

    public void setProgressBarMode(boolean indeterminate, String message) {
      progressBar.setIndeterminate(indeterminate);
      progressLabel.setText(message);
      if (!indeterminate) {
        progressBar.setValue(0);
      }
    }

    public void updateProgress(int value) {
      progressBar.setValue(value);
    }

    @Override
    public String getToolTipText(MouseEvent event) {
      if (isLoading) return null;
      if (fullscreenButtonBounds != null && fullscreenButtonBounds.contains(event.getPoint())) {
        return "Toggle Fullscreen";
      }
      if (screenshotButtonBounds != null && screenshotButtonBounds.contains(event.getPoint())) {
        return "Save Image";
      }
      return super.getToolTipText(event);
    }

    private void updateSeekPosition(MouseEvent e) {
      if (!parent.playButton.isEnabled()) return;

      int drawableX = Y_AXIS_PADDING;
      int drawableWidth = getWaveformDrawableWidth();
      if (drawableWidth <= 0) return;

      double progress = (double) (e.getX() - drawableX) / drawableWidth;
      parent.seekTo(Math.max(0, Math.min(1, progress)));
    }

    public void setWaveform(BufferedImage image) {
      this.waveformImage = image;
      repaint();
    }

    public BufferedImage getWaveformImage() {
      return this.waveformImage;
    }

    public void setNeedlePosition(double progress) {
      this.needleProgress = progress;
      repaint();
    }

    // In the WaveformPanel class...

    public static void drawTimeTicks(
        Graphics g, int xOffset, int width, int yOffset, long durationMicros, int tickAreaHeight) {
      if (durationMicros <= 0 || width <= 0) return;

      Graphics2D g2d = (Graphics2D) g.create();
      g2d.setRenderingHint(
          RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
      g2d.setColor(Color.LIGHT_GRAY);
      g2d.setFont(new Font("SansSerif", Font.PLAIN, 10));
      FontMetrics fm = g2d.getFontMetrics();

      long durationSeconds = durationMicros / 1_000_000L;
      final int MIN_PIXELS_PER_TICK_LABEL = 60; // Adjusted for potentially smaller labels
      int maxTicks = Math.max(1, width / MIN_PIXELS_PER_TICK_LABEL);

      // Determine the primary tick interval (seconds or milliseconds)
      boolean useMillisecondScale = durationMicros < 5_000_000L;

      int yBottom = yOffset + tickAreaHeight - 1;
      int yMinorTickTop = yBottom - 5;
      int yMajorTickTop = yBottom - 10;
      int yText = yMajorTickTop - 2;
      int lastLabelEndX = -20;

      if (useMillisecondScale) {
        // --- Millisecond Scale ---
        long[] msIntervals = {10, 20, 50, 100, 200, 500};
        long majorTickIntervalMs = 500; // Default
        double roughIntervalMs = (double) durationMicros / 1000.0 / maxTicks;

        for (long interval : msIntervals) {
          majorTickIntervalMs = interval;
          if (interval > roughIntervalMs) {
            break;
          }
        }
        long majorTickIntervalMicro = majorTickIntervalMs * 1000L;

        for (long tMicro = 0; tMicro <= durationMicros; tMicro += majorTickIntervalMicro) {
          int x = xOffset + (int) ((tMicro / (double) durationMicros) * width);
          g2d.drawLine(x, yMajorTickTop, x, yBottom);

          long currentSec = tMicro / 1_000_000L;
          long currentMs = (tMicro / 1000) % 1000;

          String timeStr = String.format("%d.%03d", currentSec, currentMs);
          int strWidth = fm.stringWidth(timeStr);
          int textX = Math.max(xOffset, x - strWidth / 2);
          textX = Math.min(xOffset + width - strWidth, textX);

          if (textX > lastLabelEndX) {
            g2d.drawString(timeStr, textX, yText);
            lastLabelEndX = textX + strWidth + 10; // Add padding
          }
        }

      } else {
        // --- Second/Minute Scale ---
        double roughIntervalSec = (double) durationSeconds / maxTicks;
        long[] niceIntervals = {1, 2, 5, 10, 15, 30, 60, 120, 300, 600, 900, 1800, 3600};
        long majorTickIntervalSec = niceIntervals[0];
        for (long interval : niceIntervals) {
          majorTickIntervalSec = interval;
          if (interval > roughIntervalSec) {
            break;
          }
        }

        long majorTickIntervalMicro = majorTickIntervalSec * 1_000_000L;
        int minorTicksPerMajor = (majorTickIntervalSec >= 30) ? 5 : 4;
        if (majorTickIntervalSec < 5) minorTicksPerMajor = (int) majorTickIntervalSec;
        if (minorTicksPerMajor == 0) minorTicksPerMajor = 1;
        long minorTickIntervalMicro = majorTickIntervalMicro / minorTicksPerMajor;

        boolean drawMinorTicks = (minorTickIntervalMicro * width / (double) durationMicros) > 5;
        if (drawMinorTicks) {
          for (long tMicro = 0; tMicro <= durationMicros; tMicro += minorTickIntervalMicro) {
            if (tMicro % majorTickIntervalMicro != 0) {
              int x = xOffset + (int) ((tMicro / (double) durationMicros) * width);
              g2d.drawLine(x, yMinorTickTop, x, yBottom);
            }
          }
        }

        for (long tMicro = 0; tMicro <= durationMicros; tMicro += majorTickIntervalMicro) {
          int x = xOffset + (int) ((tMicro / (double) durationMicros) * width);
          g2d.drawLine(x, yMajorTickTop, x, yBottom);

          long currentTotalSeconds = tMicro / 1_000_000L;
          long hours = currentTotalSeconds / 3600;
          long minutes = (currentTotalSeconds % 3600) / 60;
          long seconds = currentTotalSeconds % 60;

          String timeStr =
              (durationSeconds > 3600)
                  ? String.format("%d:%02d:%02d", hours, minutes, seconds)
                  : String.format("%d:%02d", minutes, seconds);

          int strWidth = fm.stringWidth(timeStr);
          int textX = Math.max(xOffset, x - strWidth / 2);
          textX = Math.min(xOffset + width - strWidth, textX);

          if (textX > lastLabelEndX) {
            g2d.drawString(timeStr, textX, yText);
            lastLabelEndX = textX + strWidth + 10; // Add padding
          }
        }
      }
      g2d.dispose();
    }

    // Replace the drawDbScale method in the WaveformPanel class
    private void drawDbScale(Graphics2D g2d) {
      g2d.setRenderingHint(
          RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
      g2d.setColor(Color.GRAY);
      g2d.setFont(new Font("SansSerif", Font.PLAIN, 9));
      FontMetrics fm = g2d.getFontMetrics();

      int panelWidth = getWidth();
      int panelHeight = getHeight();
      int drawableHeight = panelHeight - (2 * WaveformGenerator.VERTICAL_PADDING);
      if (drawableHeight <= 0) return;

      int centerY = WaveformGenerator.VERTICAL_PADDING + (drawableHeight / 2);
      double halfHeight = drawableHeight / 2.0;

      final double minDb = -60.0;
      final double maxDb = 0.0;
      final double dbRange = maxDb - minDb;

      // --- Major Ticks ---
      int[] majorTicksDb = {0, -12, -24, -36, -48, -60};
      for (int db : majorTicksDb) {
        double dbRatio = (db - minDb) / dbRange;
        int pixelOffset = (int) (dbRatio * halfHeight);
        int y = centerY - pixelOffset;
        int y_neg = centerY + pixelOffset;

        g2d.drawLine(Y_AXIS_PADDING - 5, y, Y_AXIS_PADDING, y);
        g2d.drawLine(panelWidth - Y_AXIS_PADDING, y, panelWidth - Y_AXIS_PADDING + 5, y);
        // Also draw the counterpart tick for the bottom half
        g2d.drawLine(Y_AXIS_PADDING - 5, y_neg, Y_AXIS_PADDING, y_neg);
        g2d.drawLine(panelWidth - Y_AXIS_PADDING, y_neg, panelWidth - Y_AXIS_PADDING + 5, y_neg);

        // --- Intelligent Label Drawing ---
        String label = String.valueOf(db);
        int labelWidth = fm.stringWidth(label);
        int topLabelY = y + fm.getAscent();
        int bottomLabelY = y_neg;

        // Draw the top label
        g2d.drawString(label, Y_AXIS_PADDING - 5 - labelWidth - 2, topLabelY);
        g2d.drawString(label, panelWidth - Y_AXIS_PADDING + 5 + 2, topLabelY);

        // --- CORRECTED LOGIC ---
        // The "if (db != 0)" condition has been removed to allow the bottom "0" to draw.
        // An overlap check is still performed to handle smaller panel sizes.
        if ((bottomLabelY - fm.getAscent()) > topLabelY) {
          g2d.drawString(label, Y_AXIS_PADDING - 5 - labelWidth - 2, bottomLabelY);
          g2d.drawString(label, panelWidth - Y_AXIS_PADDING + 5 + 2, bottomLabelY);
        }
      }

      // --- Minor Ticks ---
      g2d.setColor(Color.GRAY.darker());
      for (int db = -3; db > minDb; db -= 3) {
        boolean isMajor = false;
        for (int majorDb : majorTicksDb) {
          if (db == majorDb) {
            isMajor = true;
            break;
          }
        }
        if (isMajor) continue;

        double dbRatio = (db - minDb) / dbRange;
        int pixelOffset = (int) (dbRatio * halfHeight);
        int y = centerY - pixelOffset;
        int y_neg = centerY + pixelOffset;

        g2d.drawLine(Y_AXIS_PADDING - 3, y, Y_AXIS_PADDING, y);
        g2d.drawLine(Y_AXIS_PADDING - 3, y_neg, Y_AXIS_PADDING, y_neg);
        g2d.drawLine(panelWidth - Y_AXIS_PADDING, y, panelWidth - Y_AXIS_PADDING + 3, y);
        g2d.drawLine(panelWidth - Y_AXIS_PADDING, y_neg, panelWidth - Y_AXIS_PADDING + 3, y_neg);
      }

      // --- Center Line Label (-Infinity) ---
      String infinity = "-∞";
      int labelWidth = fm.stringWidth(infinity);
      int labelY = centerY + (fm.getAscent() / 2) - 2;
      g2d.setColor(Color.GRAY);
      g2d.drawString(infinity, Y_AXIS_PADDING - 5 - labelWidth - 2, labelY);
      g2d.drawString(infinity, panelWidth - Y_AXIS_PADDING + 5 + 2, labelY);
    }

    @Override
    protected void paintComponent(Graphics g) {
      super.paintComponent(g);
      Graphics2D g2d = (Graphics2D) g;

      // The background is now handled by the frame's content pane.
      int drawableX = Y_AXIS_PADDING;
      int drawableWidth = getWaveformDrawableWidth();

      if (waveformImage != null) {
        g.drawImage(waveformImage, drawableX, 0, drawableWidth, getHeight(), this);
        drawDbScale(g2d);
        drawTimeTicks(
            g,
            drawableX,
            drawableWidth,
            getHeight() - WaveformGenerator.VERTICAL_PADDING,
            parent.audioDurationMicros,
            WaveformGenerator.VERTICAL_PADDING);

        if (needleProgress >= 0) {
          final int startY = WaveformGenerator.VERTICAL_PADDING;
          final int drawHeight = getHeight() - startY;
          final int needleX = drawableX + (int) (needleProgress * drawableWidth);

          // Always draw the styled, animated gradient line.
          Graphics2D g2dNeedle = (Graphics2D) g.create();

          // Ensure antialiasing is on for smooth rounded corners
          g2dNeedle.setRenderingHint(
              RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

          g2dNeedle.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.5f));

          // This is the current BPM of the song (TODO)
          double bpm = 100;

          // This is the speed of the scrubber animation
          double cycle = getCycle(bpm);

          float yOffset = (float) (cycle * drawHeight);

          Point2D startPoint = new Point2D.Float(needleX, startY + yOffset);
          Point2D endPoint = new Point2D.Float(needleX, startY + drawHeight + yOffset);

          // Expanded color array for a rainbow gradient effect.
          Color[] colors = {
              Color.DARK_GRAY,
              Color.GRAY,
              Color.WHITE,
              parent.scrubberColor,
              Color.LIGHT_GRAY,
              Color.BLACK,
              Color.DARK_GRAY // Looping back to Red
          };

          // Corresponding fractions for the expanded color array.
          float[] fractions = {0.0f, 0.17f, 0.34f, 0.5f, 0.67f, 0.84f, 1.0f};

          LinearGradientPaint lgp =
              new LinearGradientPaint(
                  startPoint,
                  endPoint,
                  fractions,
                  colors,
                  MultipleGradientPaint.CycleMethod.REPEAT);
          g2dNeedle.setPaint(lgp);

          int scrubberWidth = 7;
          // Use fillRoundRect to create the pill shape.
          g2dNeedle.fillRoundRect(
              needleX - scrubberWidth / 2,
              startY,
              scrubberWidth,
              drawHeight,
              scrubberWidth,
              scrubberWidth);
          g2dNeedle.dispose();
        }

        if (isScrubbingForTooltip && scrubPoint != null && parent.audioDurationMicros > 0) {
          Graphics2D g2dTooltip = (Graphics2D) g.create();
          g2dTooltip.setRenderingHint(
              RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

          double progress = (double) (scrubPoint.x - drawableX) / drawableWidth;
          progress = Math.max(0, Math.min(1, progress));

          long currentMicros = (long) (progress * parent.audioDurationMicros);
          long totalSeconds = currentMicros / 1_000_000L;
          long minutes = totalSeconds / 60;
          long seconds = totalSeconds % 60;
          long millis = (currentMicros / 1000) % 1000;
          String timeStr = String.format("%d:%02d.%03d", minutes, seconds, millis);

          FontMetrics fm = g2dTooltip.getFontMetrics();
          int strWidth = fm.stringWidth(timeStr);
          int boxPadding = 5;
          int boxWidth = strWidth + 2 * boxPadding;
          int boxHeight = fm.getHeight() + boxPadding;

          int boxY = getHeight() - 20 - boxHeight - 5;
          int boxX = scrubPoint.x - boxWidth / 2;
          boxX = Math.max(2, Math.min(getWidth() - boxWidth - 2, boxX));

          g2dTooltip.setColor(new Color(0, 0, 0, 190));
          g2dTooltip.fillRoundRect(boxX, boxY, boxWidth, boxHeight, 10, 10);
          g2dTooltip.setColor(Color.WHITE);
          g2dTooltip.drawString(
              timeStr, boxX + boxPadding, boxY + fm.getAscent() + (boxPadding / 2));

          g2dTooltip.dispose();
        }

        // --- Button Drawing Logic (Corrected Positions) ---
        Graphics2D g2dButtons = (Graphics2D) g.create();
        int rightEdgeOfWaveform = getWidth() - Y_AXIS_PADDING;
        int buttonMargin = 5;

        if (parent.scaledFullScreenIcon != null) {
          int iconWidth = parent.scaledFullScreenIcon.getIconWidth();
          int iconHeight = parent.scaledFullScreenIcon.getIconHeight();
          int x = rightEdgeOfWaveform - iconWidth - buttonMargin;
          int y = 10;
          if (isHoveringOnFullscreen) {
            iconWidth += 4;
            iconHeight += 4;
            x -= 2;
            y -= 2;
          }
          if (isPressingOnFullscreen) {
            x += 1;
            y += 1;
          }
          fullscreenButtonBounds = new Rectangle(x, y, iconWidth, iconHeight);
          float alpha = isHoveringOnFullscreen ? 1.0f : 0.7f;
          g2dButtons.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
          g2dButtons.drawImage(
              parent.scaledFullScreenIcon.getImage(), x, y, iconWidth, iconHeight, this);
        }

        if (parent.scaledScreenshotIcon != null) {
          int iconWidth = parent.scaledScreenshotIcon.getIconWidth();
          int iconHeight = parent.scaledScreenshotIcon.getIconHeight();
          int x = rightEdgeOfWaveform - iconWidth - buttonMargin;
          int y = getHeight() - iconHeight - 40;
          if (isHoveringOnScreenshot) {
            iconWidth += 4;
            iconHeight += 4;
            x -= 2;
            y -= 2;
          }
          if (isPressingOnScreenshot) {
            x += 1;
            y += 1;
          }
          screenshotButtonBounds = new Rectangle(x, y, iconWidth, iconHeight);
          float alpha = isHoveringOnScreenshot ? 1.0f : 0.7f;
          g2dButtons.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
          g2dButtons.drawImage(
              parent.scaledScreenshotIcon.getImage(), x, y, iconWidth, iconHeight, this);
        }
        g2dButtons.dispose();

      } else if (!isLoading) {
        if (dragDropImage != null) {
          int x = (getWidth() - dragDropImage.getWidth(null)) / 2;
          int y = (getHeight() - dragDropImage.getHeight(null)) / 2;
          g.drawImage(dragDropImage, x, y, this);
        } else {
          g.setColor(getForeground());
          g.drawString("Drag & Drop or Double-Click to Open File", 10, 20);
        }
      }
      if (zoomRect != null) {
        g2d.setColor(new Color(0, 150, 255, 100));
        g2d.fill(zoomRect);
        g2d.setColor(new Color(0, 150, 255));
        g2d.draw(zoomRect);
      }
    }

    private static double getCycle(double bpm) {

      double period;

      if (bpm > 0) {
        // Define the BPM and period ranges
        double minBpm = 60.0;
        double maxBpm = 180.0;

        double minPeriod = 12.0e8; // Slower animation for lower BPM
        double maxPeriod = 1.0e8; // Faster animation for higher BPM

        // Clamp the current BPM to the defined range for stable mapping
        double clampedBpm = Math.max(minBpm, Math.min(maxBpm, bpm));

        // Normalize the BPM value (0.0 to 1.0)
        double normalizedBpm = (clampedBpm - minBpm) / (maxBpm - minBpm);

        // Map the normalized BPM to the period range
        period = minPeriod + (maxPeriod - minPeriod) * normalizedBpm;
      } else {
        // Use a default period if no BPM is detected
        period = 6.0e8;
      }

      return (System.nanoTime() / period) % 1.0;
    }
  }

  private static class LiveWaveformPanel extends JPanel {
    /** Enum to define the different available waveform visualizations. */
    private static final int TAB_ICON_MARGIN_X = 50;
    private static final int TAB_ICON_MARGIN_Y = 8;

    private BufferedImage auroraBackground;
    private int[] auroraPixels;

    private final Timer fadeAnimator;
    private volatile float masterAlpha = 0.0f; // Controls the master fade in/out

    public enum WaveformStyle {
      BARS("Bars"),
      BLOCKS("Blocks"),
      SYMMETRIC_BLOCKS("Symmetric Blocks"),
      LINE("Line"),
      FILLED_AREA("Filled Area"),
      SYMMETRIC_LINE("Symmetric Line"),
      SYMMETRIC_FILLED("Symmetric Filled"),
      RIBBON_WAVE("Ribbon Wave"),
      RADIAL_WAVES("Radial Waves"),
      SUPERNOVA("Supernova"),
      HYPNO_WHEEL("Hypno Wheel"),
      AURORA("Aurora");// Add this line

      private final String displayName;

      WaveformStyle(String displayName) {
        this.displayName = displayName;
      }

      @Override
      public String toString() {
        return displayName;
      }
    }

    private float[] samples = new float[0];
    private final Object sampleLock = new Object();
    private Color waveformColor = Color.GREEN;
    private Color highAmpColor = Color.RED;
    private WaveformStyle currentStyle = WaveformStyle.BARS;
    private static final int H_PADDING = 35;
    private static final int V_PADDING = 5;
    private static final int TICK_LENGTH = 5;
    private boolean isLoading = false;
    private final JLabel loadingLabel;

    private float gradientOffset = 0.0f;
    private double rotationAngle = 0;

    private RadialWaveformWorker activeRadialWorker;
    private volatile BufferedImage radialImage;

    // --- Floating Tab Fields ---
    private final ImageIcon scaledTabIcon;
    private JComboBox<WaveformStyle> styleChooser;
    private JPopupMenu styleMenu;
    private Timer tabAnimator;
    private float tabAlpha = 0.0f;

    // Add these two new methods to the LiveWaveformPanel class
    /**
     * Starts a timer to animate the masterAlpha value, fading the visualization in or out.
     * @param fadeIn True to fade in (alpha to 1.0), false to fade out (alpha to 0.0).
     */
    private void animateMasterFade(boolean fadeIn) {
      if (fadeAnimator != null && fadeAnimator.isRunning()) {
        fadeAnimator.stop();
      }

      final float targetAlpha = fadeIn ? 1.0f : 0.0f;
      final float step = 0.04f; // Controls fade speed

      // If already at the target, no need to animate.
      if (masterAlpha == targetAlpha) return;

      // Define the animation logic in a listener
      ActionListener listener = e -> {
        if (fadeIn) {
          masterAlpha += step;
          if (masterAlpha >= targetAlpha) {
            masterAlpha = targetAlpha;
            ((Timer) e.getSource()).stop();
          }
        } else { // Fading out
          masterAlpha -= step;
          if (masterAlpha <= targetAlpha) {
            masterAlpha = targetAlpha;
            ((Timer) e.getSource()).stop();
          }
        }
        repaint(); // Repaint the panel at each step of the animation
      };

      // Replace old listeners and start the timer
      for(ActionListener al : fadeAnimator.getActionListeners()) {
        fadeAnimator.removeActionListener(al);
      }
      fadeAnimator.addActionListener(listener);
      fadeAnimator.start();
    }


    public void startFadingIn() {
      animateMasterFade(true);
    }

    public void startFadingOut() {
      animateMasterFade(false);
    }

    /**
     * Sets the visualization style for the live waveform.
     *
     * @param style The WaveformStyle to use for drawing.
     */
    public void setStyle(WaveformStyle style) {
      this.currentStyle = style;
      // If we switch to the radial style, kick off a calculation immediately
      // with the current sample buffer to avoid a blank screen.
      if (style == WaveformStyle.RADIAL_WAVES) {
        updateSamples(this.samples);
      }
      repaint();
    }

    public void setHighAmpColor(Color color) {
      this.highAmpColor = color;
    }

    public void setStyleChooser(JComboBox<WaveformStyle> chooser) {
      this.styleChooser = chooser;
    }

    // Replace the existing LiveWaveformPanel constructor with this one
    // Replace the LiveWaveformPanel constructor with this one
    public LiveWaveformPanel(ImageIcon loadingIcon, ImageIcon tabIcon) {
      setLayout(null); // Use null layout to position the loading label
      setOpaque(false); // Make transparent to see parent's background color

      // --- FADE ANIMATION SETUP ---
      // This timer controls the master fade-in and fade-out effect.
      fadeAnimator = new Timer(20, e -> {
        // This is a placeholder; the listener's action is defined
        // dynamically in startFadingIn/startFadingOut.
      });


      // Scale the incoming icon to the desired size
      if (tabIcon != null) {
        Image scaledImg = tabIcon.getImage().getScaledInstance(30, 30, Image.SCALE_SMOOTH);
        this.scaledTabIcon = new ImageIcon(scaledImg);
      } else {
        this.scaledTabIcon = null;
      }

      loadingLabel = new JLabel();
      if (loadingIcon != null) {
        int newWidth = (int) (loadingIcon.getIconWidth() * 1.5);
        int newHeight = (int) (loadingIcon.getIconHeight() * 1.5);
        loadingLabel.setIcon(
            new ImageIcon(
                loadingIcon
                    .getImage()
                    .getScaledInstance(newWidth, newHeight, Image.SCALE_DEFAULT)));
      }
      loadingLabel.setVisible(false);
      add(loadingLabel);

      addComponentListener(
          new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
              positionOverlays();
            }
          });

      createStyleMenu();
      addTabMouseListeners();
    }


    /** Creates the right-click context menu for changing waveform styles. */
    private void createStyleMenu() {
      if (scaledTabIcon == null) return;

      styleMenu = new JPopupMenu();

      for (WaveformStyle style : WaveformStyle.values()) {

        JMenuItem menuItem = new JMenuItem(style.toString());
        menuItem.setIcon(waveformIcon);

        menuItem.addActionListener(
            e -> {
              // Set the combo box, which will trigger the update via its own listener
              if (styleChooser != null) {
                styleChooser.setSelectedItem(style);
              } else {
                // Fallback if chooser isn't linked, just call the method directly
                setStyle(style);
              }
            });
        styleMenu.add(menuItem);
      }
    }

    /** Adds mouse listeners to handle the floating tab animation and click events. */
    private void addTabMouseListeners() {
      if (scaledTabIcon == null) return;
      addMouseListener(
          new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
              animateTab(true); // Animate in
            }

            @Override
            public void mouseExited(MouseEvent e) {
              animateTab(false); // Animate out
            }

            @Override
            public void mouseClicked(MouseEvent e) {
              // Only allow clicking if the tab is fully or mostly visible
              if (tabAlpha > 0.5f) {
                int iconWidth = scaledTabIcon.getIconWidth();
                int iconHeight = scaledTabIcon.getIconHeight();
                int x = getWidth() - iconWidth - TAB_ICON_MARGIN_X;
                int y = TAB_ICON_MARGIN_Y;
                Rectangle tabBounds = new Rectangle(x, y, iconWidth, iconHeight);
                if (tabBounds.contains(e.getPoint()) && styleMenu != null) {
                  styleMenu.show(e.getComponent(), x, y + iconHeight);
                }
              }
            }
          });
    }

    /**
     * Animates the tab by fading it in or out.
     *
     * @param show True to fade in, false to fade out.
     */
    private void animateTab(boolean show) {
      if (tabAnimator != null && tabAnimator.isRunning()) {
        tabAnimator.stop();
      }

      final float targetAlpha = show ? 1.0f : 0.0f;
      // No need to animate if it's already at the target alpha
      if (tabAlpha == targetAlpha) return;

      tabAnimator =
          new Timer(
              20, // Milliseconds between steps
              e -> {
                float step = 0.05f; // Controls the speed of the fade
                if (show) { // Fading in
                  tabAlpha += step;
                  if (tabAlpha >= targetAlpha) {
                    tabAlpha = targetAlpha;
                    ((Timer) e.getSource()).stop();
                  }
                } else { // Fading out
                  tabAlpha -= step;
                  if (tabAlpha <= targetAlpha) {
                    tabAlpha = targetAlpha;
                    ((Timer) e.getSource()).stop();
                  }
                }
                repaint();
              });
      tabAnimator.start();
    }

    private void positionOverlays() {
      int width = getWidth();
      int height = getHeight();
      if (loadingLabel.getIcon() != null) {
        int iconWidth = loadingLabel.getIcon().getIconWidth();
        int iconHeight = loadingLabel.getIcon().getIconHeight();
        loadingLabel.setBounds(
            (width - iconWidth) / 2, (height - iconHeight) / 2, iconWidth, iconHeight);
      }
    }

    public void setLoading(boolean loading) {
      this.isLoading = loading;
      loadingLabel.setVisible(loading);
      repaint();
    }

    public void setWaveformColor(Color color) {
      this.waveformColor = color;
      repaint();
    }

    @Override
    public void setBackground(Color bg) {
      // This panel is transparent, so we don't call super.setBackground().
      // The parent container's background will show through.
    }

    public void updateSamples(float[] newSamples) {
      synchronized (sampleLock) {
        this.samples = newSamples;

        // If the radial style is active, offload the heavy calculation
        if (currentStyle == WaveformStyle.RADIAL_WAVES) {
          // If a worker is already running, cancel it
          if (activeRadialWorker != null && !activeRadialWorker.isDone()) {
            activeRadialWorker.cancel(true);
          }
          // Create and execute a new worker, passing the panel instance to it.
          activeRadialWorker =
              new RadialWaveformWorker(this, this.samples, getWidth(), getHeight());
          activeRadialWorker.execute();
        } else {
          // For all other lightweight styles, just repaint directly on the EDT
          repaint();
        }
      }
    }

    @Override
    protected void paintComponent(Graphics g) {
      super.paintComponent(g);
      if (isLoading) {
        return; // Don't draw anything else if loading
      }

      if (auroraBackground == null || auroraBackground.getWidth() != getWidth() || auroraBackground.getHeight() != getHeight()) {
        int w = getWidth();
        int h = getHeight();
        if (w > 0 && h > 0) {
          auroraBackground = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
          auroraPixels = new int[w * h];
        } else {
          return; // Can't create a zero-sized image
        }
      }

      gradientOffset = (gradientOffset + 0.005f) % 1.0f;
      rotationAngle = (rotationAngle + 0.002) % (2 * Math.PI); // For Supernova style

      Graphics2D g2d = (Graphics2D) g.create();
      g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g2d.setRenderingHint(
          RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

      g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, masterAlpha));

      int width = getWidth();
      int height = getHeight();

      synchronized (sampleLock) {
        if (samples != null && samples.length != 0 && width > 0) {
          Insets insets = getInsets();
          int drawableWidth = width - insets.left - insets.right - 2 * H_PADDING;
          int drawableHeight = height - insets.top - insets.bottom - 2 * V_PADDING;
          int drawStartX = insets.left + H_PADDING;
          int drawEndX = width - insets.right - H_PADDING;

          if (currentStyle == WaveformStyle.BLOCKS) {
            // Special handling for Blocks style: bottom-up ticks and drawing
            int drawBottomY = height - insets.bottom - V_PADDING;
            drawBottomUpTicksAndLabels(g2d, drawableHeight, drawStartX, drawEndX, drawBottomY);
            drawWaveformBlocks(g2d, drawableWidth, drawableHeight, drawStartX, drawBottomY);
          } else {
            // Existing logic for all other centered styles
            int centerY = insets.top + V_PADDING + drawableHeight / 2;
            drawTicksAndLabels(g2d, drawableHeight, drawStartX, drawEndX, centerY);
            switch (currentStyle) {
              case LINE:
                drawWaveformLine(g2d, drawableWidth, drawableHeight, drawStartX, centerY, false);
                break;
              case FILLED_AREA:
                drawWaveformFilled(g2d, drawableWidth, drawableHeight, drawStartX, centerY, false);
                break;
              case SYMMETRIC_LINE:
                drawWaveformLine(g2d, drawableWidth, drawableHeight, drawStartX, centerY, true);
                break;
              case SYMMETRIC_FILLED:
                drawWaveformFilled(g2d, drawableWidth, drawableHeight, drawStartX, centerY, true);
                break;
              case SYMMETRIC_BLOCKS:
                drawWaveformSymmetricBlocks(
                    g2d, drawableWidth, drawableHeight, drawStartX, centerY);
                break;
              case RIBBON_WAVE:
                drawRibbonWave(g2d, drawableWidth, drawableHeight, drawStartX, centerY);
                break;
              case RADIAL_WAVES:
                drawRadialWaves(g2d, drawableWidth, drawableHeight);
                break;
              case SUPERNOVA: // New style
                drawSupernova(g2d, width, height);
                break;
              case AURORA: // Add this case
                drawAurora(g2d, width, height);
                break;
              case HYPNO_WHEEL: // Add this case
                drawHypnoWheel(g2d, width, height);
                break;
              case BARS:
              default:
                drawWaveformBars(g2d, drawableWidth, drawableHeight, drawStartX, centerY);
                break;
            }
          }
        }
      }

      // Draw the floating tab with alpha transparency
      if (scaledTabIcon != null && tabAlpha > 0.0f) {
        Graphics2D g2dTab = (Graphics2D) g2d.create(); // Create a copy for composite
        g2dTab.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, tabAlpha));
        int x = getWidth() - scaledTabIcon.getIconWidth() - TAB_ICON_MARGIN_X;
        int y = TAB_ICON_MARGIN_Y;
        scaledTabIcon.paintIcon(this, g2dTab, x, y);
        g2dTab.dispose();
      }
      g2d.dispose();
    }

    private void drawBottomUpTicksAndLabels(
        Graphics2D g2d, int h, int x_start, int x_end, int y_bottom) {
      g2d.setColor(Color.GRAY);
      g2d.setFont(new Font("SansSerif", Font.PLAIN, 10));
      FontMetrics fm = g2d.getFontMetrics();

      // --- Draw Major Ticks ---
      String[] labels = {"1.0", "0.5", "0.0"};
      float[] values = {1.0f, 0.5f, 0.0f};

      for (int i = 0; i < labels.length; i++) {
        int y = y_bottom - (int) (values[i] * h);

        g2d.drawLine(x_start - TICK_LENGTH, y, x_start, y);
        int labelWidth = fm.stringWidth(labels[i]);
        g2d.drawString(
            labels[i], x_start - TICK_LENGTH - labelWidth - 2, y + (fm.getAscent() / 2) - 2);

        g2d.drawLine(x_end, y, x_end + TICK_LENGTH, y);
        g2d.drawString(labels[i], x_end + TICK_LENGTH + 2, y + (fm.getAscent() / 2) - 2);
      }

      // --- Draw Minor Ticks ---
      g2d.setColor(Color.GRAY.darker());
      int MINOR_TICK_LENGTH = 3;
      // We want ticks at 0.1, 0.2, 0.3, 0.4, 0.6, 0.7, 0.8, 0.9
      int[] minorTickNumerators = {1, 2, 3, 4, 6, 7, 8, 9};

      for (int numerator : minorTickNumerators) {
        float val = numerator / 10.0f;
        // Calculate y-position from the bottom up for the 0.0 to 1.0 scale
        int y = y_bottom - (int) (val * h);
        g2d.drawLine(x_start - MINOR_TICK_LENGTH, y, x_start, y);
        g2d.drawLine(x_end, y, x_end + MINOR_TICK_LENGTH, y);
      }
    }

    private void drawTicksAndLabels(Graphics2D g2d, int h, int x_start, int x_end, int y_mid) {
      g2d.setColor(Color.GRAY);
      g2d.setFont(new Font("SansSerif", Font.PLAIN, 10));
      FontMetrics fm = g2d.getFontMetrics();

      String[] labels = {"1.0", "0.5", "0.0", "-0.5", "-1.0"};
      float[] values = {1.0f, 0.5f, 0.0f, -0.5f, -1.0f};

      for (int i = 0; i < labels.length; i++) {
        int y = y_mid - (int) (values[i] * (h / 2.0));

        g2d.drawLine(x_start - TICK_LENGTH, y, x_start, y);
        int labelWidth = fm.stringWidth(labels[i]);
        g2d.drawString(
            labels[i], x_start - TICK_LENGTH - labelWidth - 2, y + (fm.getAscent() / 2) - 2);

        g2d.drawLine(x_end, y, x_end + TICK_LENGTH, y);
        g2d.drawString(labels[i], x_end + TICK_LENGTH + 2, y + (fm.getAscent() / 2) - 2);
      }

      g2d.setColor(Color.GRAY.darker());
      int MINOR_TICK_LENGTH = 3;
      int[] minorTickNumerators = {1, 2, 3, 4, 6, 7, 8, 9};

      for (int numerator : minorTickNumerators) {
        float val = numerator / 10.0f;
        int yPos = y_mid - (int) (val * (h / 2.0));
        g2d.drawLine(x_start - MINOR_TICK_LENGTH, yPos, x_start, yPos);
        g2d.drawLine(x_end, yPos, x_end + MINOR_TICK_LENGTH, yPos);

        int yNeg = y_mid + (int) (val * (h / 2.0));
        g2d.drawLine(x_start - MINOR_TICK_LENGTH, yNeg, x_start, yNeg);
        g2d.drawLine(x_end, yNeg, x_end + MINOR_TICK_LENGTH, yNeg);
      }
    }

    private void drawWaveformBars(Graphics2D g2d, int w, int h, int x_off, int y_mid) {
      if (samples.length == 0 || w <= 0) return;

      int gap = 2;
      double colWidth = (double) w / samples.length;
      int barWidth = Math.max(1, (int) colWidth - gap);

      GradientPaint gp =
          new GradientPaint(0, y_mid + (h / 2.0f), this.waveformColor, 0, 0, this.highAmpColor);
      g2d.setPaint(gp);

      // --- Data for Special Effects ---
      GeneralPath topEnvelope = new GeneralPath();
      GeneralPath bottomEnvelope = new GeneralPath();

      // FIX: Correctly initialize the starting point of both envelopes.
      // They now trace the center line (y_mid) when the amplitude is on the opposite side.
      int initialBarHeight = (int) (Math.abs(samples[0]) * h / 2.0);
      int initial_y_top = (samples[0] >= 0) ? (y_mid - initialBarHeight) : y_mid;
      int initial_y_bottom = (samples[0] < 0) ? (y_mid + initialBarHeight) : y_mid;
      topEnvelope.moveTo(x_off, initial_y_top);
      bottomEnvelope.moveTo(x_off, initial_y_bottom);

      // --- Main Drawing Loop (Bars) ---
      for (int i = 0; i < samples.length; i++) {
        int x = x_off + (int) (i * colWidth);
        int barHeight = (int) (Math.abs(samples[i]) * h / 2.0);

        // Draw the main body of the bar (positive or negative)
        if (samples[i] >= 0) {
          g2d.fillRect(x, y_mid - barHeight, barWidth, barHeight);
        } else {
          g2d.fillRect(x, y_mid, barWidth, barHeight);
        }

        // FIX: Update both envelope paths on every sample.
        int x_envelope = x + barWidth / 2;
        topEnvelope.lineTo(x_envelope, (samples[i] >= 0) ? (y_mid - barHeight) : y_mid);
        bottomEnvelope.lineTo(x_envelope, (samples[i] < 0) ? (y_mid + barHeight) : y_mid);
      }

      // --- Special Effect 1: Peak Indicators ---
      g2d.setColor(this.highAmpColor);
      int peakIndicatorHeight = 3;
      for (int i = 0; i < samples.length; i++) {
        int x = x_off + (int) (i * colWidth);
        int barHeight = (int) (Math.abs(samples[i]) * h / 2.0);

        if (samples[i] >= 0) {
          g2d.fillRect(x, y_mid - barHeight, barWidth, peakIndicatorHeight);
        } else {
          g2d.fillRect(x, y_mid + barHeight - peakIndicatorHeight, barWidth, peakIndicatorHeight);
        }
      }

      // --- Special Effect 2: Envelope Line ---
      g2d.setPaint(new Color(255, 255, 255, 100));
      g2d.setStroke(new BasicStroke(1f));
      g2d.draw(topEnvelope);
      g2d.draw(bottomEnvelope);
    }

    // Add this new method to the LiveWaveformPanel class
    // Replace the existing drawHypnoWheel method
    private void drawHypnoWheel(Graphics2D g2d, int w, int h) {
      if (samples == null || samples.length == 0) return;

      int centerX = w / 2;
      int centerY = h / 2;

      // --- Calculate Audio Metrics ---
      double sumOfSquares = 0.0;
      float peak = 0.0f;
      for (float sample : samples) {
        sumOfSquares += sample * sample;
        if (Math.abs(sample) > peak) {
          peak = Math.abs(sample);
        }
      }
      double rms = Math.sqrt(sumOfSquares / samples.length);

      // --- Update Animation State ---
      rotationAngle += 0.01 + (rms * 0.04);

      // --- DYNAMIC SIZING ---
      final int numSpirals = 2;
      final int numTurns = 5;
      final int pointsPerTurn = 180;
      // The max radius of the spiral is based on the SHORTEST dimension.
      final float maxRadius = Math.min(w, h) * 0.5f;

      // Use the master alpha for the blending effect
      g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.8f * masterAlpha));

      for (int i = 0; i < numSpirals; i++) {
        GeneralPath spiralPath = new GeneralPath();
        double spiralRotation = rotationAngle * ((i % 2 == 0) ? 1 : -1.2);
        float strokeWidth = 2.0f + (peak * 15.0f);
        g2d.setStroke(new BasicStroke(strokeWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        spiralPath.moveTo(centerX, centerY);

        for (int j = 1; j <= numTurns * pointsPerTurn; j++) {
          double angle = spiralRotation + ((double) j / pointsPerTurn) * 2.0 * Math.PI;
          double progress = (double) j / (numTurns * pointsPerTurn);
          int sampleIndex = (int) (progress * (samples.length - 1));
          float amp = samples[sampleIndex] * 10f;
          float radius = (float) (progress * maxRadius + amp); // Use maxRadius here
          float x = (float) (centerX + radius * Math.cos(angle));
          float y = (float) (centerY + radius * Math.sin(angle));
          spiralPath.lineTo(x, y);
        }

        float hue1 = (gradientOffset + (i * 0.5f)) % 1.0f;
        float hue2 = (hue1 + 0.1f) % 1.0f;
        Color color1 = Color.getHSBColor(hue1, 1.0f, 1.0f);
        Color color2 = Color.getHSBColor(hue2, 1.0f, 1.0f);
        g2d.setPaint(new GradientPaint(centerX, centerY, color1, w, h, color2, true));
        g2d.draw(spiralPath);
      }
    }

    // Replace the entire drawAurora method with this new, final version
    private void drawAurora(Graphics2D g2d, int w, int h) {
      if (samples == null || samples.length < 2 || auroraPixels == null) return;

      // --- Audio Analysis ---
      double sumOfSquares = 0.0;
      float peak = 0.0f;
      for (float sample : samples) {
        sumOfSquares += sample * sample;
        if (Math.abs(sample) > peak) {
          peak = Math.abs(sample);
        }
      }
      double rms = Math.sqrt(sumOfSquares / samples.length);

      // --- 1. Generate Flowing Plasma Background ---
      double time = gradientOffset * 10.0; // Time component for animation
      float rmsFactor = (float) (0.5 + rms * 2.0); // Music intensity affects the pattern

      // Loop through each pixel of the background buffer
      for (int y = 0; y < h; y++) {
        for (int x = 0; x < w; x++) {
          // A classic plasma effect using multiple sine waves
          double v1 = Math.sin((x * 0.01 * rmsFactor) + time);
          double v2 = Math.sin((y * 0.02 * rmsFactor + x * 0.01) + time);
          double v3 = Math.sin(Math.sqrt((x - w/2.0)*(x - w/2.0) + (y - h/2.0)*(y - h/2.0)) * 0.03 - time);

          // Combine the waves
          double totalValue = v1 + v2 + v3;

          // Map the result to a color gradient
          float hue = (float) (0.6 + (totalValue * 0.1)); // Blue-purple-pink range
          float brightness = 0.2f + (peak * 0.4f); // Flashes with the beat
          float saturation = 0.9f;

          auroraPixels[y * w + x] = Color.HSBtoRGB(hue, saturation, brightness);
        }
      }
      // Apply the generated pixels to the image and draw it
      auroraBackground.setRGB(0, 0, w, h, auroraPixels, 0, w);
      g2d.drawImage(auroraBackground, 0, 0, null);


      // --- Layer 2 & 3: Main Waveform and Core (Drawn on top of the background) ---
      // This logic is unchanged but is included for completeness.
      int centerY = h / 2;
      double step = (double) w / (samples.length - 1);

      GeneralPath topPath = new GeneralPath();
      GeneralPath bottomPath = new GeneralPath();
      topPath.moveTo(0, centerY);
      bottomPath.moveTo(0, centerY);

      for (int i = 0; i < samples.length; i++) {
        float xPos = (float) (i * step);
        float sampleHeight = samples[i] * h * 0.4f;
        topPath.lineTo(xPos, centerY - sampleHeight);
        bottomPath.lineTo(xPos, centerY + sampleHeight);
      }
      topPath.lineTo(w, centerY);
      bottomPath.lineTo(w, centerY);
      topPath.closePath();
      bottomPath.closePath();

      GradientPaint mainWaveGradient = new GradientPaint(
          w/2f, centerY - h * 0.25f, new Color(255, 0, 128, 200),
          w/2f, centerY + h * 0.25f, new Color(75, 0, 130, 220)
      );
      g2d.setPaint(mainWaveGradient);
      g2d.fill(topPath);
      g2d.fill(bottomPath);

      GeneralPath corePath = new GeneralPath();
      corePath.moveTo(0, centerY + (samples[0] * h * 0.1f));
      for (int i = 1; i < samples.length; i++) {
        float xPos = (float) (i * step);
        float sampleHeight = samples[i] * h * 0.1f;
        corePath.lineTo(xPos, centerY + sampleHeight);
      }

      for (int i = 5; i >= 1; i--) {
        float fraction = (float) i / 5;
        g2d.setStroke(new BasicStroke(1.0f + (fraction * 6.0f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        Color coreColor = new Color(1.0f, 1.0f, 0.5f, 0.15f * (1.0f - fraction) * (0.5f + peak));
        g2d.setColor(coreColor);
        g2d.draw(corePath);
      }
      g2d.setStroke(new BasicStroke(1.5f));
      g2d.setColor(Color.WHITE);
      g2d.draw(corePath);
    }

    private void drawRibbonWave(Graphics2D g2d, int w, int h, int x_off, int y_mid) {
      if (samples.length < 2 || w <= 0) return;

      final int numRibbons = 15; // Number of parallel lines to create the ribbon effect
      final Color startColor = new Color(75, 0, 130); // Purple
      final Color endColor = new Color(0, 255, 255); // Cyan

      // A gradient from the center to the edges will color the ribbons
      GradientPaint topGradient =
          new GradientPaint(x_off, y_mid, startColor, x_off, y_mid - (h / 2f), endColor);
      GradientPaint bottomGradient =
          new GradientPaint(x_off, y_mid, startColor, x_off, y_mid + (h / 2f), endColor);
      double step = (double) w / (samples.length - 1);

      // Draw from back to front (largest ribbon to smallest)
      for (int i = numRibbons; i >= 1; i--) {
        float fraction = (float) i / numRibbons;

        GeneralPath topPath = new GeneralPath();
        GeneralPath bottomPath = new GeneralPath();

        float firstScaledHeight = samples[0] * (h / 2.0f) * fraction;
        topPath.moveTo(x_off, y_mid - firstScaledHeight);
        bottomPath.moveTo(x_off, y_mid + firstScaledHeight);

        for (int j = 1; j < samples.length; j++) {
          float scaledHeight = samples[j] * (h / 2.0f) * fraction;
          topPath.lineTo(x_off + j * step, y_mid - scaledHeight);
          bottomPath.lineTo(x_off + j * step, y_mid + scaledHeight);
        }

        g2d.setPaint(topGradient);
        g2d.draw(topPath);

        g2d.setPaint(bottomGradient);
        g2d.draw(bottomPath);
      }
    }

    private void drawRadialWaves(Graphics2D g2d, int w, int h) {
      // This method is now extremely lightweight. It just draws the
      // pre-rendered image created by the background worker.
      if (radialImage != null) {
        g2d.drawImage(radialImage, 0, 0, null);
      }
    }

    // Replace the existing drawSupernova method with this new implementation
    // Replace the existing drawSupernova method
    private void drawSupernova(Graphics2D g2d, int w, int h) {
      if (samples == null || samples.length == 0) return;

      int centerX = w / 2;
      int centerY = h / 2;

      // --- Calculate Audio Metrics ---
      double sumOfSquares = 0.0;
      float peak = 0.0f;
      for (float sample : samples) {
        sumOfSquares += sample * sample;
        if (Math.abs(sample) > peak) {
          peak = Math.abs(sample);
        }
      }
      double rms = Math.sqrt(sumOfSquares / samples.length);

      // --- Update Animation State ---
      rotationAngle += 0.005 + (peak * 0.03);

      // --- DYNAMIC SIZING ---
      // The maximum radius is now based on the SHORTEST dimension of the container.
      final int numBlades = 7;
      final int numTeeth = 32;
      final float maxRadius = Math.min(w, h) * 0.45f; // Ensures it always fits

      // Set a composite for a nice blending effect
      g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.75f * masterAlpha));
      g2d.setStroke(new BasicStroke(2.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

      for (int i = numBlades - 1; i >= 0; i--) {
        GeneralPath sawBladePath = new GeneralPath();
        float layerFraction = (float) i / (numBlades - 1);
        double layerRotation = rotationAngle * (1 + layerFraction) * ((i % 2 == 0) ? 1 : -1);
        float baseRadius = maxRadius * layerFraction * 0.9f;
        float toothHeight = maxRadius * 0.15f * (float) rms * (2.0f - layerFraction);

        for (int j = 0; j <= numTeeth * 2; j++) {
          double angle = layerRotation + (double) j * Math.PI / numTeeth;
          float radius = (j % 2 == 0) ? (baseRadius + toothHeight) : baseRadius;
          float x = (float) (centerX + radius * Math.cos(angle));
          float y = (float) (centerY + radius * Math.sin(angle));
          if (j == 0) sawBladePath.moveTo(x, y);
          else sawBladePath.lineTo(x, y);
        }
        sawBladePath.closePath();

        float hue = (gradientOffset + (peak * 0.3f) + (layerFraction * 0.2f)) % 1.0f;
        Color bladeColor = Color.getHSBColor(hue, 1.0f, 1.0f);
        RadialGradientPaint rgp = new RadialGradientPaint(
            centerX, centerY,
            baseRadius + toothHeight,
            new float[]{0.0f, 0.8f, 1.0f},
            new Color[]{bladeColor.brighter(), bladeColor, bladeColor.darker()}
        );
        g2d.setPaint(rgp);
        g2d.fill(sawBladePath);
      }
    }

    private void drawWaveformSymmetricBlocks(Graphics2D g2d, int w, int h, int x_off, int y_mid) {
      if (samples.length == 0 || w <= 0) return;

      // --- Configuration ---
      final int blockWidth = 4;
      final int blockHeight = 4;
      final int blockGap = 1; // Gap between blocks in a column
      final int columnGap = 2; // Gap between columns

      final int totalBlockHeight = blockHeight + blockGap;
      final int totalColumnWidth = blockWidth + columnGap;
      if (totalColumnWidth <= 0) return;

      // --- Data Aggregation ---
      int numCols = w / totalColumnWidth;
      if (numCols <= 0) return;

      float[] peakMagnitudes = new float[numCols];
      double samplesPerCol = (double) samples.length / numCols;

      for (int i = 0; i < numCols; i++) {
        int startSample = (int) (i * samplesPerCol);
        int endSample = (int) ((i + 1) * samplesPerCol);
        if (endSample > samples.length) endSample = samples.length;

        float peak = 0f;
        for (int j = startSample; j < endSample; j++) {
          float currentAbs = Math.abs(samples[j]);
          if (currentAbs > peak) {
            peak = currentAbs;
          }
        }
        peakMagnitudes[i] = peak;
      }

      // --- Drawing ---
      // Gradient paint for the block colors, changing horizontally like the example
      Color[] colors = {
          new Color(148, 0, 211), // Violet
          new Color(0, 70, 255), // Blue
          Color.CYAN,
          Color.GREEN,
          Color.YELLOW,
          Color.ORANGE
      };
      float[] fractions = {0.0f, 0.2f, 0.4f, 0.6f, 0.8f, 1.0f};
      LinearGradientPaint gradient =
          new LinearGradientPaint(x_off, y_mid, x_off + w, y_mid, fractions, colors);
      g2d.setPaint(gradient);

      // Paths for the outline effect, tracing the outer edges
      GeneralPath topOutline = new GeneralPath();
      GeneralPath bottomOutline = new GeneralPath();
      boolean firstPoint = true;

      for (int i = 0; i < numCols; i++) {
        int x = x_off + i * totalColumnWidth;
        int barPixelHeight = (int) (peakMagnitudes[i] * h / 2.0);
        int numBlocks = (barPixelHeight > 0) ? barPixelHeight / totalBlockHeight : 0;

        // Draw the blocks for the current column
        for (int b = 0; b < numBlocks; b++) {
          int yTop = y_mid - (b * totalBlockHeight) - totalBlockHeight;
          int yBottom = y_mid + (b * totalBlockHeight);
          g2d.fillRect(x, yTop, blockWidth, blockHeight);
          g2d.fillRect(x, yBottom, blockWidth, blockHeight);
        }

        // Determine the outer Y coordinates for the outline path
        int topEdgeY = y_mid - (numBlocks * totalBlockHeight);
        int bottomEdgeY = y_mid + (numBlocks > 0 ? (numBlocks * totalBlockHeight) - blockGap : 0);

        if (firstPoint) {
          topOutline.moveTo(x, topEdgeY);
          bottomOutline.moveTo(x, bottomEdgeY);
          firstPoint = false;
        } else {
          topOutline.lineTo(x, topEdgeY);
          bottomOutline.lineTo(x, bottomEdgeY);
        }
      }

      // Draw the purple outline over the blocks
      g2d.setPaint(new Color(75, 0, 130, 180)); // Indigo/Purple with transparency
      g2d.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
      g2d.draw(topOutline);
      g2d.draw(bottomOutline);
    }

    private void drawWaveformBlocks(Graphics2D g2d, int w, int h, int x_off, int y_bottom) {
      if (samples.length == 0 || w <= 0) return;

      final int blockHeight = 6;
      final int blockWidth = 6;
      final int blockGap = 2;
      final int colGap = 2;
      final int totalBlockSize = blockHeight + blockGap;
      final int totalColWidth = blockWidth + (2 * colGap);

      int numCols = w / totalColWidth;
      if (numCols == 0) return;

      float[] displayMagnitudes = new float[numCols];
      double samplesPerCol = (double) samples.length / numCols;

      for (int i = 0; i < numCols; i++) {
        int startSample = (int) (i * samplesPerCol);
        int endSample = (int) ((i + 1) * samplesPerCol);
        if (endSample > samples.length) {
          endSample = samples.length;
        }

        float peak = 0f;
        for (int j = startSample; j < endSample; j++) {
          float currentAbs = Math.abs(samples[j]);
          if (currentAbs > peak) {
            peak = currentAbs;
          }
        }
        displayMagnitudes[i] = peak;
      }

      GradientPaint gp =
          new GradientPaint(0, y_bottom, this.waveformColor, 0, y_bottom - h, this.highAmpColor);
      g2d.setPaint(gp);

      for (int i = 0; i < numCols; i++) {
        int x = x_off + i * totalColWidth;
        int barPixelHeight = (int) (displayMagnitudes[i] * h);
        int numBlocks = barPixelHeight / totalBlockSize;

        for (int b = 0; b < numBlocks; b++) {
          int blockY = y_bottom - (b * totalBlockSize) - blockHeight - blockGap;
          g2d.fillRect(x + colGap, blockY, blockWidth, blockHeight);
        }
      }
    }

    private void drawWaveformLine(
        Graphics2D g2d, int w, int h, int x_off, int y_mid, boolean symmetric) {
      if (samples.length < 2 || w <= 0) return;
      g2d.setStroke(new BasicStroke(1.5f));

      float patternWidth = w * 2;
      float startX = x_off - (w * gradientOffset);
      float endX = startX + patternWidth;
      Color[] colors = {
          this.waveformColor,
          this.highAmpColor,
          this.waveformColor,
          this.highAmpColor,
          this.waveformColor
      };
      float[] fractions = {0.0f, 0.25f, 0.5f, 0.75f, 1.0f};
      LinearGradientPaint lgp =
          new LinearGradientPaint(startX, y_mid, endX, y_mid, fractions, colors);
      g2d.setPaint(lgp);

      GeneralPath path = new GeneralPath();
      double step = (double) w / (samples.length - 1);
      path.moveTo(x_off, y_mid - (samples[0] * h / 2.0f));
      for (int i = 1; i < samples.length; i++) {
        path.lineTo(x_off + i * step, y_mid - (samples[i] * h / 2.0f));
      }
      g2d.draw(path);

      if (symmetric) {
        GeneralPath symmetricPath = new GeneralPath();
        symmetricPath.moveTo(x_off, y_mid + (samples[0] * h / 2.0f));
        for (int i = 1; i < samples.length; i++) {
          symmetricPath.lineTo(x_off + i * step, y_mid + (samples[i] * h / 2.0f));
        }
        g2d.draw(symmetricPath);
      }
    }

    private void drawWaveformFilled(
        Graphics2D g2d, int w, int h, int x_off, int y_mid, boolean symmetric) {
      if (samples.length < 2) return;

      double step = (double) w / (samples.length - 1);

      GradientPaint topGradient =
          new GradientPaint(
              x_off, y_mid, this.waveformColor, x_off, y_mid - (h / 2.0f), this.highAmpColor);

      GeneralPath path = new GeneralPath();
      path.moveTo(x_off, y_mid);
      for (int i = 0; i < samples.length; i++) {
        path.lineTo(x_off + i * step, y_mid - (samples[i] * h / 2.0f));
      }
      path.lineTo(x_off + (samples.length - 1) * step, y_mid);
      path.closePath();

      g2d.setPaint(topGradient);
      g2d.fill(path);

      if (symmetric) {
        GradientPaint bottomGradient =
            new GradientPaint(
                x_off, y_mid, this.waveformColor, x_off, y_mid + (h / 2.0f), this.highAmpColor);

        GeneralPath symmetricPath = new GeneralPath();
        symmetricPath.moveTo(x_off, y_mid);
        for (int i = 0; i < samples.length; i++) {
          symmetricPath.lineTo(x_off + i * step, y_mid + (samples[i] * h / 2.0f));
        }
        symmetricPath.lineTo(x_off + (samples.length - 1) * step, y_mid);
        symmetricPath.closePath();

        g2d.setPaint(bottomGradient);
        g2d.fill(symmetricPath);
      }
    }
  }

  /**
   * A SwingWorker to calculate the complex paths for the radial waveform on a background thread to
   * prevent UI stuttering.
   */
  /**
   * A SwingWorker to calculate the complex paths for the radial waveform on a background thread to
   * prevent UI stuttering.
   */
  /**
   * A SwingWorker to calculate the complex paths for the radial waveform on a background thread to
   * prevent UI stuttering.
   */
  /**
   * A SwingWorker to render the entire radial waveform visualization onto an off-screen image. This
   * prevents all heavy computation (path calculation and rendering) from touching the main UI
   * thread.
   */
  private static class RadialWaveformWorker extends SwingWorker<BufferedImage, Void> {

    private final float[] samplesToProcess;
    private final int width;
    private final int height;
    private final LiveWaveformPanel panel; // Reference to the panel to update

    public RadialWaveformWorker(LiveWaveformPanel panel, float[] samples, int width, int height) {
      this.panel = panel;
      this.samplesToProcess = samples;
      this.width = width;
      this.height = height;
    }

    @Override
    protected BufferedImage doInBackground() throws Exception {
      if (samplesToProcess == null || samplesToProcess.length == 0 || width <= 0 || height <= 0) {
        return null;
      }

      // Create a new, transparent image to draw on
      BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
      Graphics2D g2d = image.createGraphics();
      g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

      // --- All drawing logic is now performed here, in the background ---
      final int centerX = width / 2;
      final int centerY = height / 2;
      final int numLoops = 8;
      final float baseRadius = width / 16f;
      final float maxAmplitudeOffset = height / 4f;

      Color[] colors = {
          Color.YELLOW, Color.ORANGE, Color.RED, new Color(148, 0, 211), Color.MAGENTA, Color.YELLOW
      };
      float[] fractions = {0.0f, 0.2f, 0.4f, 0.6f, 0.8f, 1.0f};
      g2d.setPaint(
          new LinearGradientPaint(
              0,
              centerY,
              width,
              centerY,
              fractions,
              colors,
              MultipleGradientPaint.CycleMethod.REPEAT));

      for (int i = 0; i < numLoops; i++) {
        if (isCancelled()) {
          g2d.dispose();
          return null;
        }

        float scale = 1.0f - ((float) i / numLoops);
        float alpha = 0.9f * scale;
        g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
        g2d.setStroke(new BasicStroke(1.5f * scale + 0.5f));

        GeneralPath path = new GeneralPath();
        boolean firstPoint = true;

        for (int angle = 0; angle <= 360; angle++) {
          int sampleIndex = (int) (((double) angle / 360.0) * (samplesToProcess.length - 1));
          float amplitude = samplesToProcess[sampleIndex];

          double radius = (baseRadius * scale) + (amplitude * maxAmplitudeOffset * scale);
          double radAngle = Math.toRadians(angle);
          float x = (float) (centerX + radius * Math.cos(radAngle));
          float y = (float) (centerY + radius * Math.sin(radAngle));

          if (firstPoint) {
            path.moveTo(x, y);
            firstPoint = false;
          } else {
            path.lineTo(x, y);
          }
        }
        path.closePath();
        g2d.draw(path);
      }

      g2d.dispose(); // Clean up graphics resources
      return image;
    }

    @Override
    protected void done() {
      try {
        BufferedImage result = get();
        if (result != null) { // Only update if the task wasn't cancelled or didn't fail
          panel.radialImage = result;
          panel.repaint();
        }
      } catch (java.util.concurrent.CancellationException e) {
        // Expected if a new task is started. Ignore.
      } catch (InterruptedException | java.util.concurrent.ExecutionException e) {
        // Unexpected error during background rendering.
        e.printStackTrace();
      }
    }
  }

  private class FileDragDropHandler extends TransferHandler {
    @Override
    public boolean canImport(TransferSupport support) {
      return support.isDataFlavorSupported(DataFlavor.javaFileListFlavor);
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean importData(TransferSupport support) {
      if (!canImport(support)) return false;
      try {
        List<File> files =
            (List<File>) support.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
        if (files != null && !files.isEmpty()) {
          for (File file : files) {
            String name = file.getName().toLowerCase();
            if (file.isFile()
                && (name.endsWith(".mp3")
                || name.endsWith(".wav")
                || name.endsWith(".aif")
                || name.endsWith(".aiff"))) {
              loadAudioFile(file);
              return true;
            }
          }
          statusLabel.setText("❌ No supported audio files dropped (MP3, WAV, AIF).");
          return false;
        }
      } catch (UnsupportedFlavorException | IOException e) {
        JOptionPane.showMessageDialog(
            Mp3Visualizer.this,
            "Error processing dropped files: \n" + e.getMessage(),
            "Drop Error",
            JOptionPane.ERROR_MESSAGE);
        return false;
      }
      return false;
    }
  }

  private class LoadAudioWorker extends SwingWorker<Void, Void> {
    private final File audioFile;
    private Exception error = null;

    LoadAudioWorker(File audioFile) {
      this.audioFile = audioFile;
    }

    @Override
    protected Void doInBackground() throws Exception {
      try {
        statusLabel.setText("Decoding audio file...");
        try (AudioInputStream rawStream = AudioSystem.getAudioInputStream(audioFile)) {
          AudioFormat baseFormat = rawStream.getFormat();
          pcmFormat =
              new AudioFormat(
                  AudioFormat.Encoding.PCM_SIGNED,
                  baseFormat.getSampleRate(),
                  16,
                  baseFormat.getChannels(),
                  baseFormat.getChannels() * 2,
                  baseFormat.getSampleRate(),
                  false);
          try (AudioInputStream decodedStream =
              AudioSystem.getAudioInputStream(pcmFormat, rawStream)) {
            pcmData = decodedStream.readAllBytes();
            originalPcmData = pcmData;
          }
        }
      } catch (Exception e) {
        this.error = e;
      }
      return null;
    }

    @Override
    protected void done() {
      try {
        if (error != null) throw error;
        if (pcmData != null && pcmFormat != null) {
          int frameSize = pcmFormat.getFrameSize();
          long totalFrames = pcmData.length / frameSize;
          audioDurationMicros = (long) (1_000_000 * totalFrames / pcmFormat.getFrameRate());

          // Clear previous zoom history and set the base state
          pcmDataHistory.clear();
          durationHistory.clear();
          pcmDataHistory.add(pcmData);
          durationHistory.add(audioDurationMicros);


          if (pcmFormat.getChannels() == 2) {
            panSlider.setEnabled(true);
          }

          regenerateWaveformImage();
          loadTagData();
          populateInfoPanel();
          String title = titleField.getText();
          if (title == null || title.trim().isEmpty()) {
            title = inputFile.getName();
          }
          startTitleMarquee(title);
        } else {
          audioDurationMicros = -1;
          setExifFieldsEnabled(false);
          waveformPanel.setLoading(false, "");
          liveWaveformPanel.setLoading(false);
        }
        if (audioDurationMicros > 0) {
          playButton.setEnabled(true);
          ejectButton.setEnabled(true);
        } else {
          statusLabel.setText("❌ Error: Could not load audio data.");
          playButton.setEnabled(false);
          ejectButton.setEnabled(false);
        }
      } catch (Exception e) {
        waveformPanel.setLoading(false, "");
        liveWaveformPanel.setLoading(false);
        String message =
            (e instanceof ExecutionException) ? e.getCause().getMessage() : e.getMessage();
        statusLabel.setText("❌ Error: " + message);
        JOptionPane.showMessageDialog(
            Mp3Visualizer.this, message, "Error", JOptionPane.ERROR_MESSAGE);
        e.printStackTrace();
      }
    }
  }

  private class GenerateImageWorker extends SwingWorker<BufferedImage, Integer> {
    private final int imgWidth, imgHeight;
    private Exception error = null;
    private final WaveformPanel progressTarget;
    private final LiveWaveformPanel gifTarget;

    GenerateImageWorker(
        int width, int height, WaveformPanel progressTarget, LiveWaveformPanel gifTarget) {
      this.imgWidth = width;
      this.imgHeight = height;
      this.progressTarget = progressTarget;
      this.gifTarget = gifTarget;
    }

    @Override
    protected BufferedImage doInBackground() throws Exception {
      try {
        statusLabel.setText("Generating waveform image...");
        WaveformGenerator generator =
            new WaveformGenerator(imgWidth, imgHeight, backgroundColor, lowAmpColor, highAmpColor);
        return generator.createWaveformImageFromPcm(pcmData, pcmFormat, this::publish);
      } catch (Exception e) {
        this.error = e;
        return null;
      }
    }

    @Override
    protected void process(List<Integer> chunks) {
      if (progressTarget != null && !chunks.isEmpty()) {
        progressTarget.updateProgress(chunks.get(chunks.size() - 1));
      }
    }

    @Override
    protected void done() {
      progressTarget.setLoading(false, "");
      gifTarget.setLoading(false);
      try {
        if (error != null) throw error;
        BufferedImage image = get();
        progressTarget.setWaveform(image);
        statusLabel.setText("✅ Ready to play.");
      } catch (Exception e) {
        String message =
            (e instanceof ExecutionException) ? e.getCause().getMessage() : e.getMessage();
        statusLabel.setText("❌ Error generating image: " + message);
        e.printStackTrace();
      }
    }
  }

  private class ImageTransferHandler extends TransferHandler {
    @Override
    public int getSourceActions(JComponent c) {
      return MOVE;
    }

    @Override
    protected Transferable createTransferable(JComponent c) {
      return new StringSelection("");
    }

    @Override
    protected void exportDone(JComponent source, Transferable data, int action) {
      if (action == MOVE) {
        removeAlbumArt();
      }
    }

    @Override
    public boolean canImport(TransferSupport support) {
      return support.isDataFlavorSupported(DataFlavor.javaFileListFlavor);
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean importData(TransferSupport support) {
      if (!canImport(support)) return false;
      try {
        List<File> files =
            (List<File>) support.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
        if (files != null && !files.isEmpty()) {
          for (File file : files) {
            String name = file.getName().toLowerCase();
            if (name.endsWith(".jpg")
                || name.endsWith(".jpeg")
                || name.endsWith(".png")
                || name.endsWith(".gif")) {
              addAlbumArt(file);
              return true;
            }
          }
        }
      } catch (UnsupportedFlavorException | IOException e) {
        e.printStackTrace();
      }
      return false;
    }
  }

  private class BiquadFilter {
    private double a0, a1, a2, b1, b2;
    private double z1, z2;

    public void setPeakingEq(float sampleRate, float centerFreq, float bandwidth, float gainDb) {
      double gain = Math.pow(10, gainDb / 20.0);
      double w0 = 2 * Math.PI * centerFreq / sampleRate;
      double alpha = Math.sin(w0) * Math.sinh(Math.log(2.0) / 2.0 * bandwidth * w0 / Math.sin(w0));
      double cos_w0 = Math.cos(w0);
      double b0_inv;
      if (gain >= 1.0) { // Boost
        b0_inv = 1.0 / (1 + alpha);
        a0 = (1 + alpha * gain) * b0_inv;
        a1 = (-2 * cos_w0) * b0_inv;
        a2 = (1 - alpha * gain) * b0_inv;
        b1 = (-2 * cos_w0) * b0_inv;
        b2 = (1 - alpha) * b0_inv;
      } else { // Cut
        b0_inv = 1.0 / (1 + alpha / gain);
        a0 = (1 + alpha) * b0_inv;
        a1 = (-2 * cos_w0) * b0_inv;
        a2 = (1 - alpha) * b0_inv;
        b1 = (-2 * cos_w0) * b0_inv;
        b2 = (1 - alpha / gain) * b0_inv;
      }
    }

    public void setLowShelf(float sampleRate, float cutoffFreq, float gainDb) {
      double A = Math.pow(10, gainDb / 40.0);
      double w0 = 2 * Math.PI * cutoffFreq / sampleRate;
      double cos_w0 = Math.cos(w0);
      double sin_w0 = Math.sin(w0);
      double alpha = sin_w0 / 2.0 * Math.sqrt((A + 1.0 / A) * (1.0 - 1.0) + 2.0);

      double common_term1 = (A + 1);
      double common_term2 = (A - 1) * cos_w0;
      double common_term3 = 2 * Math.sqrt(A) * alpha;

      double a0_denom = common_term1 + common_term2 + common_term3;

      // My 'a' variables are numerator coeffs (b in cookbook)
      this.a0 = A * (common_term1 - common_term2 + common_term3) / a0_denom;
      this.a1 = 2 * A * ((A - 1) - (A + 1) * cos_w0) / a0_denom;
      this.a2 = A * (common_term1 - common_term2 - common_term3) / a0_denom;

      // My 'b' variables are denominator coeffs (a in cookbook, minus the leading a0)
      this.b1 = -2 * ((A - 1) + (A + 1) * cos_w0) / a0_denom;
      this.b2 = (common_term1 + common_term2 - common_term3) / a0_denom;
    }

    public void setHighShelf(float sampleRate, float cutoffFreq, float gainDb) {
      double A = Math.pow(10, gainDb / 40.0);
      double w0 = 2 * Math.PI * cutoffFreq / sampleRate;
      double cos_w0 = Math.cos(w0);
      double sin_w0 = Math.sin(w0);
      double alpha = sin_w0 / 2.0 * Math.sqrt((A + 1.0 / A) * (1.0 - 1.0) + 2.0);

      double common_term1 = (A + 1);
      double common_term2 = (A - 1) * cos_w0;
      double common_term3 = 2 * Math.sqrt(A) * alpha;

      double a0_denom = common_term1 - common_term2 + common_term3;

      // My 'a' variables are numerator coeffs (b in cookbook)
      this.a0 = A * (common_term1 + common_term2 + common_term3) / a0_denom;
      this.a1 = -2 * A * ((A - 1) + (A + 1) * cos_w0) / a0_denom;
      this.a2 = A * (common_term1 + common_term2 - common_term3) / a0_denom;

      // My 'b' variables are denominator coeffs (a in cookbook, minus the leading a0)
      this.b1 = 2 * ((A - 1) - (A + 1) * cos_w0) / a0_denom;
      this.b2 = (common_term1 - common_term2 - common_term3) / a0_denom;
    }

    public float process(float in) {
      double out = in * a0 + z1;
      z1 = in * a1 + z2 - b1 * out;
      z2 = in * a2 - b2 * out;
      return (float) out;
    }
  }


  private record WaveformGenerator(int width,int height, Color backgroundColor, Color lowAmpColor, Color highAmpColor) {
      public static final int VERTICAL_PADDING = 20;

    public BufferedImage createWaveformImageFromPcm(
          byte[] pcmData, AudioFormat pcmFormat, Consumer<Integer> progressConsumer) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = image.createGraphics();
        // The background is drawn by the parent component, so we make this transparent
        g2d.setComposite(AlphaComposite.Clear);
        g2d.fillRect(0, 0, width, height);
        g2d.setComposite(AlphaComposite.SrcOver);

        int bytesPerSample = pcmFormat.getSampleSizeInBits() / 8;
        int numChannels = pcmFormat.getChannels();
        int frameSize = bytesPerSample * numChannels;
        int numFrames = pcmData.length / frameSize;

        // --- Find the global maximum amplitude for normalization ---
        // This is still needed to scale all values consistently.
        float globalMaxAbsAmp = 0f;
        for (int frame = 0; frame < numFrames; frame++) {
          float averageAmp = 0;
          for (int channel = 0; channel < numChannels; channel++) {
            int byteOffset = frame * frameSize + channel * bytesPerSample;
            short sampleValue = 0;
            if (bytesPerSample == 2) {
              int low = pcmData[byteOffset] & 0xFF;
              int high = pcmData[byteOffset + 1];
              sampleValue = (short) ((high << 8) | low);
            }
            averageAmp += sampleValue;
          }
          averageAmp /= numChannels;
          if (Math.abs(averageAmp) > globalMaxAbsAmp) {
            globalMaxAbsAmp = Math.abs(averageAmp);
          }
        }
        if (globalMaxAbsAmp == 0) {
          globalMaxAbsAmp = 32768.0f; // Prevent division by zero for silent files
        }

        int samplesPerPixel = numFrames / width;
        if (samplesPerPixel == 0) samplesPerPixel = 1;

        int drawableHeight = height - (2 * VERTICAL_PADDING);
        if (drawableHeight < 1) {
          drawableHeight = 1;
        }
        int centerY = VERTICAL_PADDING + (drawableHeight / 2);
        int lastProgress = -1;

        // --- REVISED DRAWING LOGIC: Use RMS for height and Peak for color ---
        for (int x = 0; x < width; x++) {
          int startSample = x * samplesPerPixel;
          int endSample = startSample + samplesPerPixel;
          if (endSample > numFrames) endSample = numFrames;

          double sumOfSquares = 0.0;
          float peakAmpInPixel = 0f;

          for (int sample = startSample; sample < endSample; sample++) {
            float currentAmp = 0;
            for (int channel = 0; channel < numChannels; channel++) {
              int byteOffset = sample * frameSize + channel * bytesPerSample;
              short sampleValue = 0;
              if (bytesPerSample == 2) {
                int low = pcmData[byteOffset] & 0xFF;
                int high = pcmData[byteOffset + 1];
                sampleValue = (short) ((high << 8) | low);
              }
              currentAmp += sampleValue;
            }
            currentAmp /= numChannels;

            sumOfSquares += currentAmp * currentAmp;
            if (Math.abs(currentAmp) > peakAmpInPixel) {
              peakAmpInPixel = Math.abs(currentAmp);
            }
          }

          int numSamplesInPixel = endSample - startSample;
          if (numSamplesInPixel == 0) continue; // Skip if no samples

          // Calculate RMS (Root Mean Square) for perceived loudness -> This determines line height
          float rms = (float) Math.sqrt(sumOfSquares / numSamplesInPixel);
          float rmsNormalized = rms / globalMaxAbsAmp;

          // Use the peak amplitude within this pixel to determine the color
          float peakNormalized = peakAmpInPixel / globalMaxAbsAmp;

          // The line height is based on the RMS value.
          int lineHeight = (int) (rmsNormalized * (drawableHeight / 2.0f));

          // The color is interpolated from green to red based on the peak value.
          float peakRatio = Math.min(1.0f, peakNormalized); // Clamp ratio to 1.0
          int r =
              (int)
                  (lowAmpColor.getRed() + peakRatio * (highAmpColor.getRed() - lowAmpColor.getRed()));
          int g =
              (int)
                  (lowAmpColor.getGreen()
                      + peakRatio * (highAmpColor.getGreen() - lowAmpColor.getGreen()));
          int b =
              (int)
                  (lowAmpColor.getBlue()
                      + peakRatio * (highAmpColor.getBlue() - lowAmpColor.getBlue()));
          g2d.setColor(new Color(r, g, b));

          // Draw a symmetric line from -RMS to +RMS.
          // The line's height is based on RMS, and its color is based on the peak for this slice.
          g2d.drawLine(x, centerY - lineHeight, x, centerY + lineHeight);

          int progress = (x * 100) / width;
          if (progress > lastProgress) {
            progressConsumer.accept(progress);
            lastProgress = progress;
          }
        }

        if (lastProgress < 100) {
          progressConsumer.accept(100);
        }

        g2d.dispose();
        return image;
      }
    }

  /**
   * A custom JSlider that paints a real-time audio meter in its track, similar to the Windows 11
   * volume slider.
   */
  private static class AudioMeterSlider extends JSlider {
    private volatile double meterLevel = 0.0;
    private final Color meterColor = new Color(50, 150, 255, 200); // A pleasant blue

    public AudioMeterSlider(int min, int max, int value) {
      super(min, max, value);
    }

    /**
     * Sets the current audio level to be displayed.
     *
     * @param level A normalized value between 0.0 (silence) and 1.0 (full scale).
     */
    public void setMeterLevel(double level) {
      // Clamp the value between 0.0 and 1.0
      this.meterLevel = Math.max(0.0, Math.min(1.0, level));
      repaint(); // Trigger a redraw to show the new level
    }

    @Override
    protected void paintComponent(Graphics g) {
      // Create a copy of the graphics object to not interfere with other painting
      Graphics2D g2d = (Graphics2D) g.create();

      // --- 1. Draw our custom audio meter bar FIRST ---

      // Calculate the dimensions of the track area to draw in.
      // This is a robust approximation that works well across Look & Feels.
      int trackX = getInsets().left;
      int trackWidth = getWidth() - getInsets().left - getInsets().right;
      int trackHeight = 8; // A fixed height for the meter looks clean
      int trackY = (getHeight() / 2) - (trackHeight / 2);

      // Calculate the width of the meter bar based on the current audio level
      int meterWidth = (int) (trackWidth * meterLevel);

      // Draw the background of the meter (the empty part) in a darker color
      g2d.setColor(meterColor.darker().darker());
      g2d.fillRect(trackX, trackY, trackWidth, trackHeight);

      // Draw the active meter bar on top
      g2d.setColor(meterColor);
      g2d.fillRect(trackX, trackY, meterWidth, trackHeight);

      // --- 2. Let the original JSlider paint itself on TOP of our bar ---
      // This is crucial for drawing the thumb, ticks, and focus indicators correctly.
      super.paintComponent(g);

      // Dispose of the graphics copy
      g2d.dispose();
    }
  }

  /** A custom JButton class to create a circular, floating action button (FAB) look. */
  private static class FloatingActionButton extends JButton {
    public FloatingActionButton(Icon icon) {
      super(icon);
      setContentAreaFilled(false);
      setFocusPainted(false);
      setBorderPainted(false);
      setCursor(new Cursor(Cursor.HAND_CURSOR));
    }

    @Override
    protected void paintComponent(Graphics g) {
      Graphics2D g2 = (Graphics2D) g.create();
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

      if (getModel().isArmed()) {
        g2.setColor(getBackground().darker());
      } else {
        g2.setColor(getBackground());
      }

      g2.fillOval(0, 0, getWidth(), getHeight());

      // Let the superclass paint the icon
      super.paintComponent(g);
      g2.dispose();
    }

    @Override
    protected void paintBorder(Graphics g) {
      // No border
    }
  }

  public static void main(String[] args) {
    FlatDarkLaf.setup();
    SwingUtilities.invokeLater(() -> new Mp3Visualizer().setVisible(true));
  }
}
