package xxx.com.os;

import com.formdev.flatlaf.FlatDarculaLaf;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.event.WindowStateListener;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.Serial;
import java.text.DecimalFormat;
import java.time.Duration;
import java.util.*;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.Icon;
import javax.swing.ImageIcon; // Added import for default icon
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.RowSorter; // Added import for sorting
import javax.swing.SortOrder; // Added import for sorting
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.filechooser.FileSystemView;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import jcuda.driver.CUcontext;
import jcuda.driver.CUdevice;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.DateAxis;
import org.jfree.chart.axis.ValueAxis;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYSplineRenderer;
import org.jfree.chart.title.TextTitle;
import org.jfree.data.time.Second;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;
import oshi.SystemInfo;
import oshi.hardware.*;
import oshi.software.os.OSFileStore;
import oshi.software.os.OSProcess;
import oshi.software.os.OperatingSystem;
import xxx.com.gpu.GpuInfo;
import xxx.com.image.transformer.GifTransformer;

import static jcuda.driver.JCudaDriver.*;

public class SystemMonitor extends JFrame {

  private static final String APP_NAME  = "System Monitor";
  private static final String APP_VERSION = "1.0.0";
  private static Image appIcon;

  @Serial
  private static final long serialVersionUID = -6759096393637548071L;
  private static final int UPDATE_INTERVAL_MS = 550;
  private static final DecimalFormat GIGA_FORMAT = new DecimalFormat("0.0");

  private static final Color KINDA_GRAY = new Color(135, 135, 135);

  // --- UI Components ---
  private JTabbedPane tabbedPane;
  private JProgressBar overallCpuBar, memoryBar, gpuBar;
  private JLabel overallCpuLabel, memoryLabel, gpuLabel;
  private TimeSeries cpuSeries, gpuSeries;
  private List<TimeSeries> coreSeriesList;

  // --- Process Table ---
  private JTable processTable;
  private DefaultTableModel processTableModel;
  private final Map<String, Icon> iconCache = new HashMap<>();
  // --- ADDED: Field for the default process icon ---
  private Icon processIcon;
  private Icon archTypeIcon;
  private Icon gpuIcon;
  private Icon speedIcon;
  private Icon storageIcon;
  private Icon osIcon;
  private Icon memoryIcon;
  private Icon buildIcon;
  private Icon processTabIcon;
  private Icon networkTabIcon;
  private Icon userIcon;
  private Icon systemIcon;
  private Icon serialNumberIcon;
  private Icon gearsIcon;

  // --- GPU Monitoring ---
  private boolean isGpuMonitoringAvailable = false;
  private CUcontext gpuContext;
  private CUdevice cUdevice;

  // --- OSHI Hardware Monitoring ---
  private SystemInfo systemInfo;
  private CentralProcessor oshiProcessor;
  private GlobalMemory oshiMemory;
  private OperatingSystem oshiOS;
  private long[][] prevProcTicks;

  // --- Process CPU Calculation ---
  private List<OSProcess> oldProcessList;
  private long oldCpuTotalTicks;

  // --- Network Monitoring ---
  private final Map<String, TimeSeries> netSentSeriesMap = new HashMap<>();
  private final Map<String, TimeSeries> netRecvSeriesMap = new HashMap<>();
  private final Map<String, NetworkStats> prevNetStatsMap = new HashMap<>();
  private final Map<String, ChartPanel> netChartMap = new HashMap<>();
  private final Map<String, Double> lastNetSentMap = new HashMap<>();
  private final Map<String, Double> lastNetRecvMap = new HashMap<>();
  private TimeSeries totalNetSentSeries;
  private TimeSeries totalNetRecvSeries;
  private ChartPanel totalNetChartPanel;
  private double lastTotalSentSpeed;
  private double lastTotalRecvSpeed;
  private int lastCpuPercent;

  // --- Disk Monitoring ---
  private JTable diskTable;
  private DefaultTableModel diskTableModel;
  private final Map<String, TimeSeries> diskReadSeriesMap = new HashMap<>();
  private final Map<String, TimeSeries> diskWriteSeriesMap = new HashMap<>();
  private final Map<String, DiskStats> prevDiskStatsMap = new HashMap<>();
  private final Map<String, ChartPanel> diskChartMap = new HashMap<>();

  // --- System Monitor Charts ---
  private ChartPanel cpuChartPanel, gpuChartPanel;
  private final List<ChartPanel> coreChartPanels = new ArrayList<>();

  // --- System Tray ---
  private SystemTray tray;
  private TrayIcon trayIcon;

  public static class CustomChartPanel extends ChartPanel {

    private String customToolTipText = null;

    public CustomChartPanel(JFreeChart chart) {
      super(chart);
    }

    @Override
    public String getToolTipText(MouseEvent event) {
      String entityTip = super.getToolTipText(event);
      if (entityTip != null) {
        return entityTip;
      }
      return customToolTipText;
    }

    public void setCustomToolTipText(String text) {
      this.customToolTipText = text;
    }
  }

  public static ImageIcon scaleIcon(ImageIcon icon, int width, int height) {
    if (icon == null) return null;
    Image img = icon.getImage();
    Image scaledImg = img.getScaledInstance(width, height, Image.SCALE_SMOOTH);
    return new ImageIcon(scaledImg);
  }

  public static ImageIcon recolorIcon(ImageIcon icon, Color color) {

    BufferedImage bufferedImage = new BufferedImage(icon.getIconWidth(), icon.getIconHeight(), BufferedImage.TYPE_INT_ARGB);
    Graphics2D g2d = bufferedImage.createGraphics();

    g2d.drawImage(icon.getImage(), 0, 0, null);
    g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_IN, 1.0f));
    g2d.setColor(color);
    g2d.fillRect(0, 0, icon.getIconWidth(), icon.getIconHeight());
    g2d.dispose();

    return new ImageIcon(bufferedImage);
  }

  public static void main(String[] args) {
    FlatDarculaLaf.setup();
    ChartFactory.setChartTheme(org.jfree.chart.StandardChartTheme.createDarknessTheme());
    SwingUtilities.invokeLater(SystemMonitor::new);
  }

  public SystemMonitor() {
    super(APP_NAME);

    try {

      Image image = Toolkit.getDefaultToolkit().getImage(getClass().getResource("/png/appIcon.png"));
      appIcon = image.getScaledInstance(16, 16, Image.SCALE_SMOOTH);

      processIcon = recolorIcon(scaleIcon(new ImageIcon(Objects.requireNonNull(getClass().getResource("/png/exec-process.png"))), 21, 21), KINDA_GRAY);
      archTypeIcon = recolorIcon(scaleIcon(new ImageIcon(Objects.requireNonNull(getClass().getResource("/png/process.png"))), 21, 21), KINDA_GRAY);
      buildIcon = recolorIcon(scaleIcon(new ImageIcon(Objects.requireNonNull(getClass().getResource("/png/build.png"))), 21, 21), KINDA_GRAY);
      speedIcon = recolorIcon(scaleIcon(new ImageIcon(Objects.requireNonNull(getClass().getResource("/png/speed.png"))), 21, 21), KINDA_GRAY);
      storageIcon = recolorIcon(scaleIcon(new ImageIcon(Objects.requireNonNull(getClass().getResource("/png/storage.png"))), 21, 21), KINDA_GRAY);
      gpuIcon = recolorIcon(scaleIcon(new ImageIcon(Objects.requireNonNull(getClass().getResource("/png/gpu.png"))), 32, 21), KINDA_GRAY);
      osIcon = recolorIcon(scaleIcon(new ImageIcon(Objects.requireNonNull(getClass().getResource("/png/os.png"))), 21, 21), KINDA_GRAY);
      memoryIcon = recolorIcon(scaleIcon(new ImageIcon(Objects.requireNonNull(getClass().getResource("/png/memory.png"))), 21, 21), KINDA_GRAY);
      networkTabIcon = recolorIcon(scaleIcon(new ImageIcon(Objects.requireNonNull(getClass().getResource("/png/network_tab.png"))), 16, 16), KINDA_GRAY);
      processTabIcon = recolorIcon(scaleIcon(new ImageIcon(Objects.requireNonNull(getClass().getResource("/png/process.png"))), 16, 16), KINDA_GRAY);
      userIcon = recolorIcon(scaleIcon(new ImageIcon(Objects.requireNonNull(getClass().getResource("/png/user.png"))), 21, 21), KINDA_GRAY);
      systemIcon = scaleIcon(new ImageIcon(Objects.requireNonNull(getClass().getResource("/png/windows.png"))), 16, 16);
      serialNumberIcon = recolorIcon(scaleIcon(new ImageIcon(Objects.requireNonNull(getClass().getResource("/png/serial.png"))), 16, 16), KINDA_GRAY);

      // Loading Gif without modification
      gearsIcon = new ImageIcon(Objects.requireNonNull(getClass().getResource("/gif/small-gears.gif")));

      // Loading Gif with modification
      GifTransformer gifTransformer = GifTransformer.load(getClass().getResource("/gif/typing-indicator.gif"));
      gifTransformer.resize(18, 18);
      gifTransformer.applyTint(KINDA_GRAY);
      gifTransformer.changeSpeed(.40);
      ImageIcon indicator = gifTransformer.toImageIcon();

    } catch (Exception e) {

      System.err.println("Could not load resource " + e.getMessage());
      System.exit(-1);
    }

    setIconImage(appIcon);

    initializeGpuDriver();
    initializeOshi();
    initComponents();
    layoutComponents();
    startUpdateTimer();

    addWindowListener(new WindowAdapter() {
      @Override
      public void windowClosing(WindowEvent e) {
        if (isGpuMonitoringAvailable) {
          cuCtxDestroy(gpuContext);
        }
        if (tray != null && trayIcon != null) {
          tray.remove(trayIcon);
        }
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
      }
    });

    addWindowStateListener(new WindowStateListener() {
      @Override
      public void windowStateChanged(WindowEvent e) {
        if (e.getNewState() == ICONIFIED) {
          setVisible(false);
        }
      }
    });

    setMinimumSize(new Dimension(800, 750));
    setSize(1200, 900);
    setLocationRelativeTo(null);
    setVisible(true);

    setupSystemTray();
  }

  private void setupSystemTray() {
    if (SystemTray.isSupported()) {

      tray = SystemTray.getSystemTray();
      trayIcon = new TrayIcon(appIcon, APP_NAME);
      trayIcon.setImageAutoSize(true);

      PopupMenu popup = new PopupMenu();
      MenuItem openItem = new MenuItem("Open");

      openItem.addActionListener(e -> {
        setVisible(true);
        setExtendedState(JFrame.NORMAL);
      });
      MenuItem exitItem = new MenuItem("Exit");
      exitItem.addActionListener(e -> System.exit(0));
      popup.add(openItem);
      popup.add(exitItem);

      trayIcon.setPopupMenu(popup);
      trayIcon.addActionListener(e -> {
        setVisible(true);
        setExtendedState(JFrame.NORMAL);
      });

      try {
        tray.add(trayIcon);
      } catch (AWTException e) {
        System.err.println("TrayIcon could not be added.");
      }
    }
  }

  private void updateTrayIcon(int cpuLoad, double netSent, double netRecv) {

    if (trayIcon == null) return;

    int width = 16;
    int height = 16;
    BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = image.createGraphics();
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

    // Background
    g.setColor(new Color(30, 30, 30));
    g.fillRect(0, 0, width, height);

    // CPU Bar (Left)
    int cpuBarHeight = (int) (cpuLoad / 100.0 * height);
    g.setColor(new Color(0, 120, 255));
    g.fillRect(1, height - cpuBarHeight, 6, cpuBarHeight);

    // Network Bar (Right)
    // Use a logarithmic scale for network to show small values more clearly
    double totalNet = netSent + netRecv;
    double logNet = totalNet > 0 ? Math.log1p(totalNet) : 0;
    double maxLogNet = Math.log1p(10240); // Corresponds to 10 MB/s
    int netBarHeight = (int) ((logNet / maxLogNet) * height);
    g.setColor(new Color(0, 200, 120));
    g.fillRect(9, height - netBarHeight, 6, netBarHeight);

    g.dispose();
    trayIcon.setImage(image);
    trayIcon.setToolTip(String.format("CPU: %d%%\nSent: %.2f KB/s\nRecv: %.2f KB/s", cpuLoad, netSent, netRecv));

    setIconImage(image);
  }


  // --- INITIALIZATION ---
  private void initializeGpuDriver() {
    try {
      setExceptionsEnabled(true);
      cuInit(0);
      cUdevice = new CUdevice();
      cuDeviceGet(cUdevice, 0);
      gpuContext = new CUcontext();
      cuCtxCreate(gpuContext, 0, cUdevice);
      isGpuMonitoringAvailable = true;
    } catch (Exception e) {
      System.err.println("Could not initialize JCuda Driver: " + e.getMessage());
      isGpuMonitoringAvailable = false;
    }
  }

  private void initializeOshi() {
    systemInfo = new SystemInfo();
    oshiOS = systemInfo.getOperatingSystem();
    oshiProcessor = systemInfo.getHardware().getProcessor();
    oshiMemory = systemInfo.getHardware().getMemory();
    prevProcTicks = oshiProcessor.getProcessorCpuLoadTicks();
    oldCpuTotalTicks = Arrays.stream(prevProcTicks).flatMapToLong(Arrays::stream).sum();
    oldProcessList = oshiOS.getProcesses();

    for (NetworkIF net : systemInfo.getHardware().getNetworkIFs()) {
      prevNetStatsMap.put(net.getName(), new NetworkStats(net.getBytesSent(), net.getBytesRecv(), net.getTimeStamp()));
    }
    for (HWDiskStore disk : systemInfo.getHardware().getDiskStores()) {
      prevDiskStatsMap.put(disk.getName(), new DiskStats(disk.getReadBytes(), disk.getWriteBytes(), disk.getTimeStamp()));
    }
  }

  private void initComponents() {
    tabbedPane = new JTabbedPane();
    tabbedPane.setFont(new Font("Inter", Font.PLAIN, 14));
    tabbedPane.setBackground(new Color(30, 30, 30));
    tabbedPane.setForeground(Color.WHITE);

    overallCpuBar = new ModernProgressBar(0, 100);
    memoryBar = new ModernProgressBar(0, 100);
    gpuBar = new ModernProgressBar(0, 100);
    overallCpuLabel = new JLabel("0.00 / 0.00 GHz", SwingConstants.CENTER);
    overallCpuLabel.setFont(new Font("Inter", Font.PLAIN, 12));
    overallCpuLabel.setForeground(Color.LIGHT_GRAY);
    memoryLabel = new JLabel("0.0 / 0.0 GB", SwingConstants.CENTER);
    memoryLabel.setFont(new Font("Inter", Font.PLAIN, 12));
    memoryLabel.setForeground(Color.LIGHT_GRAY);
    gpuLabel = new JLabel("0.0 / 0.0 GB", SwingConstants.CENTER);
    gpuLabel.setFont(new Font("Inter", Font.PLAIN, 12));
    gpuLabel.setForeground(Color.LIGHT_GRAY);

    cpuSeries = new TimeSeries("CPU Usage");
    cpuSeries.setMaximumItemCount(100);
    gpuSeries = new TimeSeries("GPU Memory Usage");
    gpuSeries.setMaximumItemCount(100);
    coreSeriesList = new ArrayList<>();
    for (int i = 0; i < oshiProcessor.getLogicalProcessorCount(); i++) {
      TimeSeries coreSeries = new TimeSeries("Core " + i);
      coreSeries.setMaximumItemCount(100);
      coreSeriesList.add(coreSeries);
    }

    totalNetSentSeries = new TimeSeries("Total Sent ⬆");
    totalNetSentSeries.setMaximumItemCount(100);
    totalNetRecvSeries = new TimeSeries("Total Received ⬇");
    totalNetRecvSeries.setMaximumItemCount(100);

    // --- MODIFIED: Renamed "Background" column to "Type" ---
    String[] processColumnNames = {"", "Process Name", "PID", "CPU %", "Memory (MB)", "File Path", "Type"};

    processTableModel = new DefaultTableModel(processColumnNames, 0) {
      @Override
      public boolean isCellEditable(int row, int column) { return false; }

      @Override
      public Class<?> getColumnClass(int columnIndex) {
        return switch (columnIndex) { // Icon
          case 0, 6 -> // Type
              Icon.class; // Process Name
          case 1, 5 -> // File Path
              String.class;
          case 2 -> // PID
              Integer.class; // CPU %
          case 3, 4 -> // Memory (MB)
              Double.class;
          default -> Object.class;
        };
      }
    };
    processTable = new JTable(processTableModel);
    processTable.setFont(new Font("Inter", Font.PLAIN, 12));
    processTable.setRowHeight(30);
    processTable.setGridColor(new Color(50, 50, 50));
    processTable.setShowGrid(true);
    processTable.setAutoCreateRowSorter(true);

    ModernTableCellRenderer centerRenderer = new ModernTableCellRenderer();
    processTable.setDefaultRenderer(String.class, centerRenderer);
    processTable.setDefaultRenderer(Integer.class, centerRenderer);
    processTable.setDefaultRenderer(Double.class, centerRenderer);

    processTable.getColumnModel().getColumn(0).setMinWidth(32);
    processTable.getColumnModel().getColumn(0).setMaxWidth(32); // Icon
    processTable.getColumnModel().getColumn(2).setMaxWidth(80); // PID
    processTable.getColumnModel().getColumn(3).setMaxWidth(80); // CPU %
    processTable.getColumnModel().getColumn(4).setMaxWidth(120); // Memory
    processTable.getColumnModel().getColumn(6).setMaxWidth(32); // Type


    String[] diskColumnNames = {"Disk", "Partition", "Mount", "Type", "Total", "Usable"};
    diskTableModel = new DefaultTableModel(diskColumnNames, 0) {
      @Override
      public boolean isCellEditable(int row, int column) { return false; }
    };
    diskTable = new JTable(diskTableModel);
    diskTable.setFont(new Font("Inter", Font.PLAIN, 12));
    diskTable.setRowHeight(30);
    diskTable.setGridColor(new Color(50, 50, 50));
    diskTable.setShowGrid(true);
    diskTable.setDefaultRenderer(Object.class, new ModernTableCellRenderer());
  }

  // --- LAYOUT ---
  private void layoutComponents() {
    tabbedPane.addTab("System Overview", UIManager.getIcon("FileChooser.homeFolderIcon"), createSystemOverviewTab());
    tabbedPane.addTab("System Monitor", UIManager.getIcon("FileView.computerIcon"), createMonitorTab());
    tabbedPane.addTab("Processes", processTabIcon, createProcessesTab());
    tabbedPane.addTab("Network", networkTabIcon, createNetworkTab());
    tabbedPane.addTab("Disk", UIManager.getIcon("FileView.hardDriveIcon"), createDiskTab());
    setLayout(new BorderLayout());
    add(tabbedPane, BorderLayout.CENTER);
  }

  private JPanel createSystemOverviewTab() {
    JPanel panel = new JPanel(new BorderLayout());
    panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
    panel.setBackground(new Color(30, 30, 30));
    Box contentBox = Box.createVerticalBox();

    String hostName = oshiOS.getNetworkParams().getHostName();
    contentBox.add(createOverviewRow("Device Name", hostName, UIManager.getIcon("FileView.computerIcon")));

    Duration uptime = Duration.ofSeconds(oshiOS.getSystemUptime());
    String uptimeStr = String.format("%d days, %02d:%02d:%02d", uptime.toDays(), uptime.toHoursPart(), uptime.toMinutesPart(), uptime.toSecondsPart());
    contentBox.add(createOverviewRow("Uptime", uptimeStr, gearsIcon));

    String systemType = oshiProcessor.getProcessorIdentifier().getMicroarchitecture() + (oshiProcessor.getProcessorIdentifier().isCpu64bit() ? " (64-bit)" : " (32-bit)");
    contentBox.add(createOverviewRow("System Type", systemType, archTypeIcon));

    contentBox.add(createOverviewRow("System Serial #", systemInfo.getHardware().getComputerSystem().getSerialNumber(), serialNumberIcon));

    contentBox.add(Box.createVerticalStrut(20));

    contentBox.add(createOverviewRow("Operating System", oshiOS.toString(), osIcon));
    contentBox.add(createOverviewRow("OS Build", oshiOS.getVersionInfo().getBuildNumber(), buildIcon));
    contentBox.add(Box.createVerticalStrut(20));

    contentBox.add(createOverviewRow("Processor", oshiProcessor.getProcessorIdentifier().getName(), processTabIcon));
    contentBox.add(createOverviewRow("Processor Max Speed", String.format("%.2f GHz", oshiProcessor.getMaxFreq() / 1_000_000_000.0), speedIcon));

    long totalStorage = systemInfo.getHardware().getDiskStores().stream().mapToLong(HWDiskStore::getSize).sum();
    contentBox.add(createOverviewRow("Total Storage", formatSize(totalStorage), storageIcon));

    if (!systemInfo.getHardware().getGraphicsCards().isEmpty()) {
      GraphicsCard mainGpu = systemInfo.getHardware().getGraphicsCards().getFirst();
      contentBox.add(createOverviewRow("Graphics Card", mainGpu.getName(), gpuIcon));
      contentBox.add(createOverviewRow("Graphics Memory", formatSize(mainGpu.getVRam()), memoryIcon));
    }

    panel.add(contentBox, BorderLayout.NORTH);

    return panel;
  }

  private JPanel createOverviewRow(String key, String value, Icon icon) {
    JPanel panel = new JPanel(new BorderLayout(10, 0));
    panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
    panel.setBackground(new Color(30, 30, 30));
    JLabel keyLabel = new JLabel(key);
    keyLabel.setFont(new Font("Inter", Font.BOLD, 14));
    keyLabel.setForeground(Color.WHITE);
    keyLabel.setPreferredSize(new Dimension(200, 30));
    JLabel valueLabel = new JLabel(value);
    valueLabel.setFont(new Font("Inter", Font.PLAIN, 14));
    valueLabel.setForeground(Color.LIGHT_GRAY);
    JLabel iconLabel = new JLabel(icon);
    iconLabel.setPreferredSize(new Dimension(30, 30));
    JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 5));
    leftPanel.setBackground(new Color(30, 30, 30));
    leftPanel.add(iconLabel);
    leftPanel.add(keyLabel);
    panel.add(leftPanel, BorderLayout.WEST);
    panel.add(valueLabel, BorderLayout.CENTER);
    panel.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(50, 50, 50)));
    return panel;
  }

  private JPanel createMonitorTab() {
    JPanel panel = new JPanel(new BorderLayout(10, 15));
    panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
    panel.setBackground(new Color(30, 30, 30));

    JPanel topPanel = new JPanel(new GridLayout(1, 3, 20, 0));
    topPanel.setBackground(new Color(30, 30, 30));
    topPanel.add(createMeterPanel("CPU", overallCpuBar, overallCpuLabel, new Color(0, 120, 255)));
    topPanel.add(createMeterPanel("Memory", memoryBar, memoryLabel, new Color(255, 85, 85)));
    topPanel.add(createMeterPanel("GPU Memory", gpuBar, gpuLabel, new Color(0, 200, 120)));
    panel.add(topPanel, BorderLayout.NORTH);

    JPanel centerPanel = new JPanel(new BorderLayout(0, 10));
    centerPanel.setBackground(new Color(30, 30, 30));
    JPanel combinedGraphPanel = new JPanel(new GridLayout(1, 2, 10, 0));
    combinedGraphPanel.setBackground(new Color(30, 30, 30));
    combinedGraphPanel.add(cpuChartPanel = createChartPanel(oshiProcessor.getProcessorIdentifier().getName(), cpuSeries, new Color(0, 120, 255)));
    combinedGraphPanel.add(gpuChartPanel = createChartPanel(getGpuName(cUdevice), gpuSeries, new Color(0, 200, 120)));
    centerPanel.add(combinedGraphPanel, BorderLayout.NORTH);

    JPanel coreGraphsPanel = new JPanel(new GridLayout(0, 4, 10, 10));
    coreGraphsPanel.setBackground(new Color(30, 30, 30));
    for (int i = 0; i < coreSeriesList.size(); i++) {
      ChartPanel miniChart = createMiniChartPanel(coreSeriesList.get(i), "Core " + i, new Color(100, 200, 100));
      coreGraphsPanel.add(miniChart);
      coreChartPanels.add(miniChart);
    }
    JScrollPane scrollPane = new JScrollPane(coreGraphsPanel);
    scrollPane.setBorder(BorderFactory.createEmptyBorder());
    centerPanel.add(scrollPane, BorderLayout.CENTER);
    panel.add(centerPanel, BorderLayout.CENTER);
    return panel;
  }

  private ChartPanel createChartPanel(String title, TimeSeries series, Color lineColor) {
    TimeSeriesCollection dataset = new TimeSeriesCollection(series);
    JFreeChart chart = ChartFactory.createTimeSeriesChart(null, "Time", "Usage (%)", dataset);
    customizeChart(chart, lineColor, 2.5f);

    TextTitle chartTitle = new TextTitle(title, new Font("Inter", Font.BOLD, 16));
    chartTitle.setPaint(Color.WHITE);
    chart.setTitle(chartTitle);
    chart.setBackgroundPaint(new Color(35, 35, 35));

    chart.getXYPlot().getRangeAxis().setRange(0.0, 100.0);
    CustomChartPanel chartPanel = new CustomChartPanel(chart);
    chartPanel.setPreferredSize(new Dimension(getWidth() / 2, 250));
    chartPanel.setBackground(new Color(35, 35, 35));
    return chartPanel;
  }

  private ChartPanel createMiniChartPanel(TimeSeries series, String title, Color lineColor) {
    TimeSeriesCollection dataset = new TimeSeriesCollection(series);
    JFreeChart chart = ChartFactory.createTimeSeriesChart(null, null, null, dataset);
    customizeChart(chart, lineColor, 1.8f);

    TextTitle chartTitle = new TextTitle(title, new Font("Inter", Font.PLAIN, 12));
    chartTitle.setPaint(Color.WHITE);
    chart.setTitle(chartTitle);
    chart.setBackgroundPaint(new Color(35, 35, 35));

    XYPlot plot = chart.getXYPlot();
    plot.getRangeAxis().setRange(0.0, 100.0);
    plot.getRangeAxis().setVisible(false);
    plot.getDomainAxis().setVisible(false);
    CustomChartPanel chartPanel = new CustomChartPanel(chart);
    chartPanel.setPreferredSize(new Dimension(180, 100));
    chartPanel.setBackground(new Color(35, 35, 35));
    return chartPanel;
  }

  private JPanel createProcessesTab() {
    JPanel panel = new JPanel(new BorderLayout(0, 5));
    panel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
    panel.setBackground(new Color(30, 30, 30));
    JLabel title = new JLabel("Running Processes");
    title.setFont(new Font("Inter", Font.BOLD, 16));
    title.setForeground(Color.WHITE);
    panel.add(title, BorderLayout.NORTH);
    JScrollPane scrollPane = new JScrollPane(processTable);
    scrollPane.setBorder(BorderFactory.createEmptyBorder());
    panel.add(scrollPane, BorderLayout.CENTER);

    JPopupMenu popupMenu = new JPopupMenu();
    JMenuItem openLocationItem = new JMenuItem("Open File Location");
    JMenuItem killProcessItem = new JMenuItem("Kill Process");
    popupMenu.add(openLocationItem);
    popupMenu.add(killProcessItem);

    openLocationItem.addActionListener(e -> {
      int selectedRow = processTable.getSelectedRow();
      if (selectedRow != -1) {
        int modelRow = processTable.convertRowIndexToModel(selectedRow);
        String path = (String) processTableModel.getValueAt(modelRow, 5);
        if (path != null && !path.isEmpty() && Desktop.isDesktopSupported()) {
          try {
            File file = new File(path);
            if (file.exists()) {
              Desktop.getDesktop().open(file.getParentFile());
            } else {
              JOptionPane.showMessageDialog(SystemMonitor.this, "File path does not exist: " + path, "Error", JOptionPane.ERROR_MESSAGE);
            }
          } catch (IOException | NullPointerException ex) {
            JOptionPane.showMessageDialog(SystemMonitor.this, "Could not open file location.", "Error", JOptionPane.ERROR_MESSAGE);
          }
        } else {
          JOptionPane.showMessageDialog(SystemMonitor.this, "File location not available for this process.", "Info", JOptionPane.INFORMATION_MESSAGE);
        }
      }
    });

    killProcessItem.addActionListener(e -> {
      int selectedRow = processTable.getSelectedRow();
      if (selectedRow != -1) {
        int modelRow = processTable.convertRowIndexToModel(selectedRow);
        int pid = (Integer) processTableModel.getValueAt(modelRow, 2);
        String processName = (String) processTableModel.getValueAt(modelRow, 1);

        int confirm = JOptionPane.showConfirmDialog(
            SystemMonitor.this,
            "Are you sure you want to kill process '" + processName + "' (PID: " + pid + ")?",
            "Confirm Kill Process",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE
        );

        if (confirm == JOptionPane.YES_OPTION) {
          try {
            Optional<ProcessHandle> processHandle = ProcessHandle.of(pid);
            if (processHandle.isPresent()) {
              processHandle.get().destroyForcibly();
              new Timer(500, event -> {
                updateProcessList();
                ((Timer)event.getSource()).stop();
              }).start();
            } else {
              JOptionPane.showMessageDialog(SystemMonitor.this, "Process with PID " + pid + " not found (may have already ended).", "Error", JOptionPane.ERROR_MESSAGE);
            }
          } catch (Exception ex) {
            JOptionPane.showMessageDialog(SystemMonitor.this, "Failed to kill process: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
          }
        }
      }
    });

    processTable.addMouseListener(new MouseAdapter() {
      @Override
      public void mousePressed(MouseEvent e) {
        if (SwingUtilities.isRightMouseButton(e)) {
          JTable source = (JTable) e.getSource();
          int row = source.rowAtPoint(e.getPoint());
          if (row >= 0 && row < source.getRowCount()) {
            if (!source.isRowSelected(row)) {
              source.setRowSelectionInterval(row, row);
            }
          }
        }
      }

      @Override
      public void mouseReleased(MouseEvent e) {
        if (e.isPopupTrigger() && processTable.getSelectedRow() != -1) {
          popupMenu.show(e.getComponent(), e.getX(), e.getY());
        }
      }
    });

    return panel;
  }

  private String getGroupKey(NetworkIF net) {
    String lowerName = net.getName().toLowerCase();
    if (lowerName.contains("vmnet") || lowerName.contains("vmware")) return "VMWare";
    if (lowerName.contains("veth") || lowerName.contains("hyper-v") || lowerName.contains("hyperv")) return "HyperV";
    if (lowerName.startsWith("wlan") || lowerName.contains("wi-fi")) return "WiFi";
    if (lowerName.startsWith("eth") || lowerName.startsWith("en") || lowerName.contains("ethernet")) return "Ethernet";
    if (lowerName.startsWith("lo")) return "Loopback";
    return "Other";
  }

  private JPanel createNetworkTab() {
    JPanel container = new JPanel(new BorderLayout());
    container.setBackground(new Color(30, 30, 30));

    TimeSeriesCollection totalDataset = new TimeSeriesCollection();
    totalDataset.addSeries(totalNetSentSeries);
    totalDataset.addSeries(totalNetRecvSeries);
    totalNetChartPanel = createDualLineChartPanel("Total Network Activity", "Speed (KB/s)", totalDataset);
    totalNetChartPanel.setPreferredSize(new Dimension(0, 200));
    container.add(totalNetChartPanel, BorderLayout.NORTH);

    JTabbedPane groupTabs = new JTabbedPane();
    groupTabs.setTabPlacement(JTabbedPane.BOTTOM);
    groupTabs.setFont(new Font("Inter", Font.PLAIN, 14));
    groupTabs.setBackground(new Color(30, 30, 30));
    groupTabs.setForeground(Color.WHITE);

    Map<String, List<NetworkIF>> groupMap = systemInfo.getHardware().getNetworkIFs().stream()
        .filter(net -> net.getBytesRecv() > 0 || net.getBytesSent() > 0)
        .collect(Collectors.groupingBy(this::getGroupKey));

    for (Map.Entry<String, List<NetworkIF>> entry : groupMap.entrySet()) {
      String key = entry.getKey();
      List<NetworkIF> nets = entry.getValue();
      String groupName = key + " Interfaces (" + nets.size() + ")";
      JPanel groupContent = new JPanel(new GridLayout(0, 2, 10, 10));
      groupContent.setBackground(new Color(30, 30, 30));
      for (NetworkIF net : nets) {
        String name = net.getName();
        TimeSeries sentSeries = new TimeSeries("Sent ⬆");
        sentSeries.setMaximumItemCount(100);
        TimeSeries recvSeries = new TimeSeries("Received ⬇");
        recvSeries.setMaximumItemCount(100);
        netSentSeriesMap.put(name, sentSeries);
        netRecvSeriesMap.put(name, recvSeries);
        TimeSeriesCollection dataset = new TimeSeriesCollection();
        dataset.addSeries(sentSeries);
        dataset.addSeries(recvSeries);
        ChartPanel chartPanel = createDualLineChartPanel(net.getDisplayName(), "Speed (KB/s)", dataset);
        chartPanel.setPreferredSize(new Dimension(400, 200));
        netChartMap.put(name, chartPanel);
        groupContent.add(chartPanel);
      }
      groupTabs.addTab(groupName, groupContent);
    }

    container.add(groupTabs, BorderLayout.CENTER);
    return container;
  }

  private JPanel createDiskTab() {
    updateDiskInfoTable();
    JScrollPane tableScrollPane = new JScrollPane(diskTable);
    tableScrollPane.setMinimumSize(new Dimension(0, 150));
    tableScrollPane.setBorder(BorderFactory.createEmptyBorder());
    JPanel graphsPanel = new JPanel(new GridLayout(0, 2, 10, 10));
    graphsPanel.setBackground(new Color(30, 30, 30));
    graphsPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
    for (HWDiskStore disk : systemInfo.getHardware().getDiskStores()) {
      TimeSeries readSeries = new TimeSeries("Read");
      readSeries.setMaximumItemCount(100);
      TimeSeries writeSeries = new TimeSeries("Write");
      writeSeries.setMaximumItemCount(100);
      diskReadSeriesMap.put(disk.getName(), readSeries);
      diskWriteSeriesMap.put(disk.getName(), writeSeries);
      TimeSeriesCollection dataset = new TimeSeriesCollection();
      dataset.addSeries(readSeries);
      dataset.addSeries(writeSeries);
      ChartPanel chartPanel = createDualLineChartPanel(disk.getModel(), "Speed (MB/s)", dataset);
      chartPanel.setPreferredSize(new Dimension(500, 200));
      diskChartMap.put(disk.getName(), chartPanel);
      graphsPanel.add(chartPanel);
    }
    JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, tableScrollPane, graphsPanel);
    splitPane.setResizeWeight(0.3);
    splitPane.setBorder(BorderFactory.createEmptyBorder());
    SwingUtilities.invokeLater(() -> splitPane.setDividerLocation(0.3));
    JPanel containerPanel = new JPanel(new BorderLayout());
    containerPanel.setBackground(new Color(30, 30, 30));
    containerPanel.add(splitPane, BorderLayout.CENTER);
    return containerPanel;
  }

  // --- CHARTING UTILITIES ---
  private ChartPanel createDualLineChartPanel(String title, String yAxisLabel, TimeSeriesCollection dataset) {
    JFreeChart chart = ChartFactory.createTimeSeriesChart(null, "Time", yAxisLabel, dataset);
    customizeDualChart(chart, new Color(255, 85, 85), new Color(0, 120, 255));

    TextTitle chartTitle = new TextTitle(title, new Font("Inter", Font.BOLD, 16));
    chartTitle.setPaint(Color.WHITE);
    chart.setTitle(chartTitle);
    chart.setBackgroundPaint(new Color(35, 35, 35));

    XYPlot plot = chart.getXYPlot();
    ValueAxis domainAxis = plot.getDomainAxis();
    if (domainAxis instanceof DateAxis) {
      domainAxis.setTickLabelPaint(Color.LIGHT_GRAY);
    }
    plot.getRangeAxis().setTickLabelPaint(Color.LIGHT_GRAY);
    CustomChartPanel chartPanel = new CustomChartPanel(chart);
    chartPanel.setBackground(new Color(35, 35, 35));
    return chartPanel;
  }

  private void customizeChart(JFreeChart chart, Color lineColor, float strokeWidth) {
    XYPlot plot = chart.getXYPlot();
    plot.setBackgroundPaint(new Color(35, 35, 35));
    plot.setDomainGridlinesVisible(true);
    plot.setRangeGridlinesVisible(true);
    plot.setDomainGridlinePaint(new Color(50, 50, 50));
    plot.setRangeGridlinePaint(new Color(50, 50, 50));
    XYSplineRenderer renderer = new XYSplineRenderer();
    renderer.setSeriesPaint(0, lineColor);
    renderer.setSeriesStroke(0, new BasicStroke(strokeWidth));
    renderer.setDefaultShapesVisible(false);
    plot.setRenderer(renderer);
    chart.removeLegend();
  }

  private void customizeDualChart(JFreeChart chart, Color color1, Color color2) {
    XYPlot plot = chart.getXYPlot();
    plot.setBackgroundPaint(new Color(35, 35, 35));
    plot.setDomainGridlinesVisible(true);
    plot.setRangeGridlinesVisible(true);
    plot.setDomainGridlinePaint(new Color(50, 50, 50));
    plot.setRangeGridlinePaint(new Color(50, 50, 50));
    XYSplineRenderer renderer = new XYSplineRenderer();
    renderer.setSeriesPaint(0, color1);
    renderer.setSeriesPaint(1, color2);
    renderer.setSeriesStroke(0, new BasicStroke((float) 2.5));
    renderer.setSeriesStroke(1, new BasicStroke((float) 2.5));
    renderer.setDefaultShapesVisible(false);
    plot.setRenderer(renderer);
    chart.removeLegend();
  }

  private JPanel createMeterPanel(String title, JProgressBar bar, JLabel valueLabel, Color color) {
    JPanel panel = new JPanel(new BorderLayout(5, 5));
    panel.setBackground(new Color(30, 30, 30));
    JLabel titleLabel = new JLabel(title, SwingConstants.CENTER);
    titleLabel.setFont(new Font("Inter", Font.BOLD, 14));
    titleLabel.setForeground(Color.WHITE);
    bar.setPreferredSize(new Dimension(80, 120));
    bar.setForeground(color);
    bar.setBorder(BorderFactory.createLineBorder(new Color(50, 50, 50), 1));
    panel.add(titleLabel, BorderLayout.NORTH);
    panel.add(bar, BorderLayout.CENTER);
    panel.add(valueLabel, BorderLayout.SOUTH);
    return panel;
  }

  // --- UPDATE LOGIC ---
  private void startUpdateTimer() {
    new Timer(UPDATE_INTERVAL_MS, e -> {
      updateSystemMetrics();
      switch (tabbedPane.getSelectedIndex()) {
        case 2: updateProcessList(); break;
        case 3: updateNetworkMetrics(); break;
        case 4: updateDiskMetrics(); break;
      }
    }).start();
  }

  private void updateSystemMetrics() {
    // CPU
    long[][] currentProcTicks = oshiProcessor.getProcessorCpuLoadTicks();
    double[] coreLoads = oshiProcessor.getProcessorCpuLoadBetweenTicks(prevProcTicks);
    this.prevProcTicks = currentProcTicks;
    double systemCpuLoad = 0.0;
    int cpuPercent = 0;
    double usagePercent = 0.0;
    if (coreLoads != null) {
      systemCpuLoad = Arrays.stream(coreLoads).average().orElse(0.0);
      cpuPercent = (int) (systemCpuLoad * 100);
      long maxFreq = oshiProcessor.getMaxFreq();
      long[] currentFreqs = oshiProcessor.getCurrentFreq();
      double avgCurrentFreq = Arrays.stream(currentFreqs).average().orElse(0.0);
      overallCpuLabel.setText(String.format("%.2f / %.2f GHz", avgCurrentFreq / 1_000_000_000.0, maxFreq / 1_000_000_000.0));
      overallCpuBar.setValue(cpuPercent);
      this.cpuSeries.addOrUpdate(new Second(), cpuPercent);
      if (coreLoads.length == coreSeriesList.size()) {
        for (int i = 0; i < coreSeriesList.size(); i++) {
          coreSeriesList.get(i).addOrUpdate(new Second(), coreLoads[i] * 100);
        }
      }
    }
    lastCpuPercent = cpuPercent;
    // Memory
    long totalMem = oshiMemory.getTotal();
    long usedMem = totalMem - oshiMemory.getAvailable();
    memoryBar.setValue((int) ((usedMem * 100) / totalMem));
    memoryLabel.setText(String.format("%.1f / %.1f GB", usedMem / (1024.0 * 1024.0 * 1024.0), totalMem / (1024.0 * 1024.0 * 1024.0)));
    // GPU
    if (isGpuMonitoringAvailable) {
      long[] free = new long[1];
      long[] total = new long[1];
      cuMemGetInfo(free, total);
      if (total[0] > 0) {
        long usedGpuMemory = total[0] - free[0];
        usagePercent = (double) usedGpuMemory * 100.0 / total[0];
        gpuBar.setValue((int) usagePercent);
        gpuLabel.setText(String.format("%.1f / %.1f GB", usedGpuMemory / (1024.0 * 1024.0 * 1024.0), total[0] / (1024.0 * 1024.0 * 1024.0)));
        this.gpuSeries.addOrUpdate(new Second(), usagePercent);
      }
    }

    // Update tooltips
    final int finalCpuPercent = cpuPercent;
    final double finalUsagePercent = usagePercent;
    final double[] finalCoreLoads = coreLoads;
    SwingUtilities.invokeLater(() -> {
      if (cpuChartPanel != null) {
        ((CustomChartPanel) cpuChartPanel).setCustomToolTipText(String.format("CPU Usage: %d%%", finalCpuPercent));
      }
      if (gpuChartPanel != null) {
        ((CustomChartPanel) gpuChartPanel).setCustomToolTipText(String.format("GPU Memory Usage: %d%%", (int) finalUsagePercent));
      }
      for (int i = 0; i < coreChartPanels.size(); i++) {
        if (finalCoreLoads != null && i < finalCoreLoads.length) {
          ((CustomChartPanel) coreChartPanels.get(i)).setCustomToolTipText(String.format("Core %d Usage: %.0f%%", i, finalCoreLoads[i] * 100));
        }
      }
      updateTrayIcon(lastCpuPercent, lastTotalSentSpeed, lastTotalRecvSpeed);
    });
  }

  private void updateProcessList() {
    new Thread(() -> {
      final String currentUser = System.getProperty("user.name");
      final FileSystemView fsv = FileSystemView.getFileSystemView();
      List<OSProcess> newProcessList = oshiOS.getProcesses();
      long[][] currentTotalTicks = oshiProcessor.getProcessorCpuLoadTicks();
      long newCpuTotalTicks = Arrays.stream(currentTotalTicks).flatMapToLong(Arrays::stream).sum();
      Map<Integer, OSProcess> oldProcessMap = oldProcessList.stream().collect(Collectors.toMap(OSProcess::getProcessID, Function.identity()));
      long elapsedCpuTicks = newCpuTotalTicks - oldCpuTotalTicks;
      List<Object[]> rowData = new ArrayList<>();

      for (OSProcess p : newProcessList) {
        OSProcess oldProcess = oldProcessMap.get(p.getProcessID());
        double cpuUsage = 0.0;
        if (oldProcess != null && elapsedCpuTicks > 0) {
          long kernelDiff = p.getKernelTime() - oldProcess.getKernelTime();
          long userDiff = p.getUserTime() - oldProcess.getUserTime();
          cpuUsage = Math.min(100.0, 100.0 * (kernelDiff + userDiff) / (double) elapsedCpuTicks);
        }

        String path = p.getPath();
        Icon icon = null;
        if (path != null && !path.isEmpty()) {
          icon = iconCache.get(path);
          if (icon == null) {
            File file = new File(path);
            if (file.exists()) {
              icon = fsv.getSystemIcon(file);
              iconCache.put(path, icon);
            }
          }
        }
        // --- MODIFIED: Use default icon if no specific icon was found ---
        if (icon == null) {
          icon = processIcon;
        }

        // --- MODIFIED: Logic for "Type" column (User vs. System) ---
//        String processType = "User";
//        if (!p.getUser().equalsIgnoreCase(currentUser)) {
//          processType = "System";
//        }

        Icon processTypeIcon = userIcon;
        if (!p.getUser().equalsIgnoreCase(currentUser)) {
          processTypeIcon = systemIcon;
        }

        rowData.add(new Object[]{
            icon,
            p.getName(),
            p.getProcessID(),
            Double.parseDouble(String.format("%.2f", cpuUsage)),
            Double.parseDouble(String.format("%.2f", p.getResidentSetSize() / (1024.0 * 1024.0))),
            path,
            processTypeIcon
        });
      }
      this.oldProcessList = newProcessList;
      this.oldCpuTotalTicks = newCpuTotalTicks;
      SwingUtilities.invokeLater(() -> {
        // --- MODIFIED: Force sort by CPU usage instead of preserving user sort ---
        int selectedRow = processTable.getSelectedRow();
        processTableModel.setRowCount(0);
        for (Object[] row : rowData) processTableModel.addRow(row);

        TableRowSorter<?> sorter = (TableRowSorter<?>) processTable.getRowSorter();
        List<RowSorter.SortKey> sortKeys = new ArrayList<>();
        // Column 3 is CPU %
        sortKeys.add(new RowSorter.SortKey(3, SortOrder.DESCENDING));
        sorter.setSortKeys(sortKeys);
        sorter.sort();

        if (selectedRow != -1 && selectedRow < processTable.getRowCount()) {
          int modelRow = processTable.convertRowIndexToModel(selectedRow);
          processTable.setRowSelectionInterval(modelRow, modelRow);
        }
      });
    }).start();
  }

  private void updateNetworkMetrics() {
    new Thread(() -> {
      double totalSentSpeed = 0.0;
      double totalRecvSpeed = 0.0;
      Second now = new Second();
      for (NetworkIF net : systemInfo.getHardware().getNetworkIFs()) {
        net.updateAttributes();
        String name = net.getName();
        NetworkStats prevStats = prevNetStatsMap.get(name);
        long currentTime = net.getTimeStamp();
        long elapsedTime = currentTime - prevStats.timeStamp;
        double sentSpeed = 0.0;
        double recvSpeed = 0.0;
        if (elapsedTime > 0) {
          sentSpeed = (double) (net.getBytesSent() - prevStats.bytesSent) / elapsedTime * 1000.0 / 1024.0; // KB/s
          recvSpeed = (double) (net.getBytesRecv() - prevStats.bytesRecv) / elapsedTime * 1000.0 / 1024.0; // KB/s
        }
        lastNetSentMap.put(name, sentSpeed);
        lastNetRecvMap.put(name, recvSpeed);
        totalSentSpeed += sentSpeed;
        totalRecvSpeed += recvSpeed;
        prevNetStatsMap.put(name, new NetworkStats(net.getBytesSent(), net.getBytesRecv(), currentTime));
      }
      lastTotalSentSpeed = totalSentSpeed;
      lastTotalRecvSpeed = totalRecvSpeed;
      double finalTotalSent = totalSentSpeed;
      double finalTotalRecv = totalRecvSpeed;
      SwingUtilities.invokeLater(() -> {
        for (NetworkIF net : systemInfo.getHardware().getNetworkIFs()) {
          String name = net.getName();
          if (netSentSeriesMap.containsKey(name)) {
            netSentSeriesMap.get(name).addOrUpdate(now, lastNetSentMap.getOrDefault(name, 0.0));
            netRecvSeriesMap.get(name).addOrUpdate(now, lastNetRecvMap.getOrDefault(name, 0.0));
          }
          ChartPanel cp = netChartMap.get(name);
          if (cp != null) {
            ((CustomChartPanel) cp).setCustomToolTipText(String.format("Sent: %.2f KB/s\nRecv: %.2f KB/s", lastNetSentMap.getOrDefault(name, 0.0), lastNetRecvMap.getOrDefault(name, 0.0)));
          }
        }
        totalNetSentSeries.addOrUpdate(now, finalTotalSent);
        totalNetRecvSeries.addOrUpdate(now, finalTotalRecv);
        if (totalNetChartPanel != null) {
          ((CustomChartPanel) totalNetChartPanel).setCustomToolTipText(String.format("Total Sent: %.2f KB/s\nTotal Recv: %.2f KB/s", finalTotalSent, finalTotalRecv));
        }
        updateTrayIcon(lastCpuPercent, lastTotalSentSpeed, lastTotalRecvSpeed);
      });
    }).start();
  }

  private void updateDiskInfoTable() {
    diskTableModel.setRowCount(0);
    List<OSFileStore> fileStores = oshiOS.getFileSystem().getFileStores(true);
    for (HWDiskStore disk : systemInfo.getHardware().getDiskStores()) {
      boolean firstPartition = true;
      for (HWPartition part : disk.getPartitions()) {
        String mount = fileStores.stream().filter(fs -> fs.getUUID() != null && fs.getUUID().equalsIgnoreCase(part.getUuid())).map(OSFileStore::getMount).findFirst().orElse("");
        OSFileStore store = fileStores.stream().filter(fs -> fs.getUUID() != null && fs.getUUID().equalsIgnoreCase(part.getUuid())).findFirst().orElse(null);
        diskTableModel.addRow(new Object[]{firstPartition ? disk.getModel() : "", part.getIdentification(), mount, store != null ? store.getType() : "N/A", store != null ? formatSize(store.getTotalSpace()) : "N/A", store != null ? formatSize(store.getUsableSpace()) : "N/A"});
        firstPartition = false;
      }
    }
  }

  private void updateDiskMetrics() {
    new Thread(() -> {
      for (HWDiskStore disk : systemInfo.getHardware().getDiskStores()) {
        disk.updateAttributes();
        DiskStats prevStats = prevDiskStatsMap.get(disk.getName());
        long currentTime = disk.getTimeStamp();
        long elapsedTime = currentTime - prevStats.timeStamp;
        double readSpeed = 0.0;
        double writeSpeed = 0.0;
        if (elapsedTime > 0) {
          readSpeed = (double) (disk.getReadBytes() - prevStats.bytesRead) / elapsedTime * 1000.0 / (1024 * 1024); // MB/s
          writeSpeed = (double) (disk.getWriteBytes() - prevStats.bytesWritten) / elapsedTime * 1000.0 / (1024 * 1024); // MB/s
        }
        final double rs = readSpeed;
        final double ws = writeSpeed;
        final String dn = disk.getName();
        SwingUtilities.invokeLater(() -> {
          Second now = new Second();
          if (diskReadSeriesMap.containsKey(dn)) {
            diskReadSeriesMap.get(dn).addOrUpdate(now, rs);
            diskWriteSeriesMap.get(dn).addOrUpdate(now, ws);
          }
          ChartPanel dp = diskChartMap.get(dn);
          if (dp != null) {
            ((CustomChartPanel) dp).setCustomToolTipText(String.format("Read: %.2f MB/s\nWrite: %.2f MB/s", rs, ws));
          }
        });
        prevDiskStatsMap.put(disk.getName(), new DiskStats(disk.getReadBytes(), disk.getWriteBytes(), currentTime));
      }
    }).start();
  }

  // --- HELPER CLASSES AND METHODS ---
  private static String formatSize(long size) {
    if (size <= 0) return "0 B";
    final String[] units = new String[]{"B", "KB", "MB", "GB", "TB"};
    int digitGroups = (int) (Math.log10(size) / Math.log10(1024));
    return new DecimalFormat("#,##0.#").format(size / Math.pow(1024, digitGroups)) + " " + units[digitGroups];
  }

  private static class NetworkStats {
    long bytesSent, bytesRecv, timeStamp;
    NetworkStats(long bytesSent, long bytesRecv, long timeStamp) {
      this.bytesSent = bytesSent; this.bytesRecv = bytesRecv; this.timeStamp = timeStamp;
    }
  }

  private static class DiskStats {
    long bytesRead, bytesWritten, timeStamp;
    DiskStats(long bytesRead, long bytesWritten, long timeStamp) {
      this.bytesRead = bytesRead; this.bytesWritten = bytesWritten; this.timeStamp = timeStamp;
    }
  }

  private static class ModernProgressBar extends JProgressBar {
    public ModernProgressBar(int min, int max) {
      super(min, max);
      setStringPainted(true);
      setFont(new Font("Inter", Font.PLAIN, 12));
      setForeground(Color.WHITE);
    }

    @Override
    protected void paintComponent(Graphics g) {
      Graphics2D g2d = (Graphics2D) g;
      g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      int width = getWidth();
      int height = getHeight();
      GradientPaint gradient = new GradientPaint(0, 0, new Color(50, 50, 50), 0, height, new Color(70, 70, 70));
      g2d.setPaint(gradient);
      g2d.fillRect(0, 0, width, height);
      super.paintComponent(g);
    }
  }

  private static String getGpuName(CUdevice device) {
    byte[] deviceNameBytes = new byte[1024]; // A buffer large enough for any device name
    cuDeviceGetName(deviceNameBytes, deviceNameBytes.length, device);

    // C-style strings are null-terminated. We find the first null byte (0)
    // to determine the actual length of the name.
    int len = 0;
    while (len < deviceNameBytes.length && deviceNameBytes[len] != 0) {
      len++;
    }

    // Create a String using only the bytes that are part of the name.
    return new String(deviceNameBytes, 0, len);
  }

  private static class ModernTableCellRenderer extends DefaultTableCellRenderer {
    public ModernTableCellRenderer() {
      setHorizontalAlignment(SwingConstants.CENTER);
      setFont(new Font("Inter", Font.PLAIN, 12));
      setForeground(Color.LIGHT_GRAY);
      setBackground(new Color(35, 35, 35));
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
      Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
      if (isSelected) {
        c.setBackground(new Color(50, 50, 50));
        c.setForeground(Color.WHITE);
      } else {
        c.setBackground(row % 2 == 0 ? new Color(35, 35, 35) : new Color(40, 40, 40));
        c.setForeground(Color.LIGHT_GRAY);
      }
      return c;
    }
  }

  public static void getIntegratedGpu() {
    GpuInfo.getIntegratedGpu();
  }
}
