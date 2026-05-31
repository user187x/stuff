package xxx.com.mosquito;

import ai.djl.Model;
import ai.djl.ndarray.NDArray;
import ai.djl.ndarray.NDList;
import ai.djl.ndarray.NDManager;
import ai.djl.ndarray.types.Shape;
import ai.djl.nn.SequentialBlock;
import ai.djl.nn.core.Linear;
import ai.djl.training.DefaultTrainingConfig;
import ai.djl.training.EasyTrain;
import ai.djl.training.Trainer;
import ai.djl.training.dataset.ArrayDataset;
import ai.djl.training.loss.Loss;
import ai.djl.training.optimizer.Adam;
import com.formdev.flatlaf.FlatDarkLaf;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.GeneralPath;
import java.awt.geom.Point2D;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.prefs.Preferences;
import java.util.stream.Collectors;

import ai.djl.inference.Predictor;
import ai.djl.translate.Translator;
import ai.djl.translate.TranslatorContext;
import ai.djl.translate.Batchifier;

public class MosquitoSimulator {

  public static void main(String[] args) {
    FlatDarkLaf.setup();
    SwingUtilities.invokeLater(MosquitoSimulator::createAndShowGui);
  }

  private static void createAndShowGui() {
    // --- Main Frame Setup ---
    JFrame frame = new JFrame("Mosquito Simulator");
    frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    frame.setPreferredSize(new Dimension(1200, 800));

    // --- Create Main Panels ---
    MosquitoPanel mosquitoPanel = new MosquitoPanel();
    ZapCounter zapCounter = new ZapCounter();
    MissCounter missCounter = new MissCounter();
    TrainingZapCounter trainingZapCounter = new TrainingZapCounter();
    TrainingMissCounter trainingMissCounter = new TrainingMissCounter();
    TrainingLogPanel trainingLogPanel = new TrainingLogPanel();
    LiveProficiencyChartPanel chartPanel = new LiveProficiencyChartPanel();
    JLabel chaperoneStatusLabel = new JLabel();
    JLabel accuracyLabel = new JLabel("Current Accuracy: 0.0%"); // Declare the accuracy label here

    // Main control panel at the top
    JPanel mainControlPanel = new JPanel(new GridBagLayout());
    mainControlPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
    GridBagConstraints gbc = new GridBagConstraints();
    gbc.insets = new Insets(5, 5, 5, 5);
    gbc.fill = GridBagConstraints.HORIZONTAL;

    // --- Create Control Panels ---
    JPanel mosquitoControls = createMosquitoControlsPanel(mosquitoPanel);
    JPanel counterPanel = createCounterPanel(zapCounter, missCounter, accuracyLabel); // Pass the accuracy label
    final Tracker externalTracker = new Tracker(
        mosquitoPanel,
        zapCounter,
        missCounter,
        trainingZapCounter,
        trainingMissCounter,
        trainingLogPanel,
        accuracyLabel,
        chartPanel,
        chaperoneStatusLabel
    );
    JPanel trackerControls = createTrackerControlsPanel(externalTracker);
    JPanel proficiencyPanel = createProficiencyPanel(chartPanel);

    // --- Add Control Panels to Main Control Panel ---
    gbc.gridx = 0;
    gbc.gridy = 0;
    gbc.weightx = 0.5;
    mainControlPanel.add(mosquitoControls, gbc);

    gbc.gridx = 1;
    gbc.gridy = 0;
    gbc.weightx = 0.5;
    mainControlPanel.add(trackerControls, gbc);

    gbc.gridx = 0;
    gbc.gridy = 1;
    gbc.weightx = 0.5;
    mainControlPanel.add(counterPanel, gbc);

    gbc.gridx = 1;
    gbc.gridy = 1;
    gbc.weightx = 0.5;
    mainControlPanel.add(proficiencyPanel, gbc);

    // --- Training Panel on the right ---
    JPanel trainingPanel = createTrainingPanel(
        trainingZapCounter,
        trainingMissCounter,
        externalTracker,
        trainingLogPanel,
        chaperoneStatusLabel
    );

    // --- Split pane for the main content and the new graph panel ---
    JSplitPane mainSplitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, mosquitoPanel, chartPanel);
    mainSplitPane.setResizeWeight(0.7);
    mainSplitPane.setOneTouchExpandable(true);

    // --- Add components to frame ---
    frame.add(mainControlPanel, BorderLayout.NORTH);
    frame.add(mainSplitPane, BorderLayout.CENTER);
    frame.add(trainingPanel, BorderLayout.EAST);

    frame.pack();
    frame.setLocationRelativeTo(null);
    frame.setVisible(true);

    // Start the simulation and tracker
    frame.addWindowListener(new WindowAdapter() {
      @Override
      public void windowClosing(WindowEvent e) {
        externalTracker.saveLearningData();
      }
    });

    mosquitoPanel.setMosquitoCount(50); // Initial count
    externalTracker.startTracking();
    externalTracker.loadLearningData(); // Load data on startup
  }

  private static JPanel createMosquitoControlsPanel(MosquitoPanel mosquitoPanel) {
    JPanel panel = new JPanel(new GridBagLayout());
    panel.setBorder(BorderFactory.createTitledBorder("Mosquito Controls"));
    GridBagConstraints gbc = new GridBagConstraints();
    gbc.fill = GridBagConstraints.HORIZONTAL;
    gbc.insets = new Insets(5, 5, 5, 5);

    // Slider for Mosquito Count
    JLabel countLabel = new JLabel("Mosquitoes: 50");
    JSlider mosquitoCountSlider = new JSlider(JSlider.HORIZONTAL, 0, 200, 50);
    mosquitoCountSlider.setMajorTickSpacing(50);
    mosquitoCountSlider.setMinorTickSpacing(10);
    mosquitoCountSlider.setPaintTicks(true);
    mosquitoCountSlider.setPaintLabels(true);
    mosquitoCountSlider.addChangeListener(e -> {
      int count = mosquitoCountSlider.getValue();
      countLabel.setText("Mosquitoes: " + count);
      mosquitoPanel.setMosquitoCount(count);
    });
    gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0.2; panel.add(countLabel, gbc);
    gbc.gridx = 1; gbc.gridy = 0; gbc.weightx = 0.8; panel.add(mosquitoCountSlider, gbc);

    // Slider for Mosquito Speed
    JLabel speedLabel = new JLabel("Speed: 50%");
    JSlider mosquitoSpeedSlider = new JSlider(JSlider.HORIZONTAL, 1, 100, 50);
    mosquitoSpeedSlider.addChangeListener(e -> {
      int speedPercentage = mosquitoSpeedSlider.getValue();
      speedLabel.setText("Speed: " + speedPercentage + "%");
      mosquitoPanel.setMosquitoSpeed((double) speedPercentage / 100.0);
    });
    gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0.2; panel.add(speedLabel, gbc);
    gbc.gridx = 1; gbc.gridy = 1; gbc.weightx = 0.8; panel.add(mosquitoSpeedSlider, gbc);

    return panel;
  }

  private static JPanel createCounterPanel(ZapCounter zapCounter, MissCounter missCounter, JLabel accuracyLabel) {
    JPanel panel = new JPanel(new GridBagLayout());
    panel.setBorder(BorderFactory.createTitledBorder("Session Stats"));
    GridBagConstraints gbc = new GridBagConstraints();
    gbc.fill = GridBagConstraints.HORIZONTAL;
    gbc.insets = new Insets(5, 5, 5, 5);

    JLabel zappedCountLabel = new JLabel("Mosquitoes Zapped: 0");
    zapCounter.setLabel(zappedCountLabel);
    gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 1.0; panel.add(zappedCountLabel, gbc);

    JLabel missedCountLabel = new JLabel("Missed Zaps: 0");
    missCounter.setLabel(missedCountLabel);
    gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 1.0; panel.add(missedCountLabel, gbc);

    // Use the accuracyLabel passed in as a parameter
    gbc.gridx = 0; gbc.gridy = 2; gbc.weightx = 1.0; panel.add(accuracyLabel, gbc);

    return panel;
  }

  private static JPanel createTrackerControlsPanel(Tracker externalTracker) {
    JPanel panel = new JPanel(new GridBagLayout());
    panel.setBorder(BorderFactory.createTitledBorder("Tracker Controls"));
    GridBagConstraints gbc = new GridBagConstraints();
    gbc.fill = GridBagConstraints.HORIZONTAL;
    gbc.insets = new Insets(5, 5, 5, 5);

    JLabel trackerCountLabel = new JLabel("Track: 5");
    JSlider trackerCountSlider = new JSlider(JSlider.HORIZONTAL, 0, 20, 5);
    trackerCountSlider.addChangeListener(e -> {
      int count = trackerCountSlider.getValue();
      trackerCountLabel.setText("Track: " + count);
      externalTracker.setMosquitosToTrack(count);
    });
    gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0.2; panel.add(trackerCountLabel, gbc);
    gbc.gridx = 1; gbc.gridy = 0; gbc.weightx = 0.8; panel.add(trackerCountSlider, gbc);

    JLabel scanFreqLabel = new JLabel("Scan Freq: 500ms");
    JSlider scanFreqSlider = new JSlider(JSlider.HORIZONTAL, 50, 1000, 500);
    scanFreqSlider.setMajorTickSpacing(250);
    scanFreqSlider.setPaintTicks(true);
    scanFreqSlider.addChangeListener(e -> {
      int freq = scanFreqSlider.getValue();
      scanFreqLabel.setText("Scan Freq: " + freq + "ms");
      externalTracker.setScanFrequency(freq);
    });
    gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0.2; panel.add(scanFreqLabel, gbc);
    gbc.gridx = 1; gbc.gridy = 1; gbc.weightx = 0.8; panel.add(scanFreqSlider, gbc);

    return panel;
  }

  private static JPanel createProficiencyPanel(LiveProficiencyChartPanel chartPanel) {
    JPanel panel = new JPanel(new GridBagLayout());
    panel.setBorder(BorderFactory.createTitledBorder("Proficiency Goal"));
    GridBagConstraints gbc = new GridBagConstraints();
    gbc.fill = GridBagConstraints.HORIZONTAL;
    gbc.insets = new Insets(5, 5, 5, 5);

    JLabel desiredProficiencyLabel = new JLabel("Desired Proficiency: 75%");
    JSlider desiredProficiencySlider = new JSlider(JSlider.HORIZONTAL, 0, 100, 75);
    desiredProficiencySlider.setMajorTickSpacing(25);
    desiredProficiencySlider.setPaintTicks(true);
    desiredProficiencySlider.setPaintLabels(true);
    desiredProficiencySlider.addChangeListener(e -> {
      int proficiency = desiredProficiencySlider.getValue();
      desiredProficiencyLabel.setText("Desired Proficiency: " + proficiency + "%");
      chartPanel.setDesiredProficiency(proficiency);
    });
    gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0.2; panel.add(desiredProficiencyLabel, gbc);
    gbc.gridx = 1; gbc.gridy = 0; gbc.weightx = 0.8; panel.add(desiredProficiencySlider, gbc);

    return panel;
  }

  private static JPanel createTrainingPanel(
      TrainingZapCounter trainingZapCounter,
      TrainingMissCounter trainingMissCounter,
      Tracker externalTracker,
      TrainingLogPanel trainingLogPanel,
      JLabel chaperoneStatusLabel
  ) {
    JPanel panel = new JPanel(new GridBagLayout());
    panel.setBorder(BorderFactory.createTitledBorder("Training"));
    GridBagConstraints gbc = new GridBagConstraints();
    gbc.gridwidth = GridBagConstraints.REMAINDER;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    gbc.insets = new Insets(2, 5, 2, 5);

    // --- Model Selection ---
    JLabel modelLabel = new JLabel("AI Model:");
    modelLabel.setHorizontalAlignment(SwingConstants.CENTER);
    panel.add(modelLabel, gbc);

    String[] models = { "Adaptive Learning", "Neural Network" };
    JComboBox<String> modelSelector = new JComboBox<>(models);
    modelSelector.addActionListener(e -> {
      String selectedModel = (String) modelSelector.getSelectedItem();
      externalTracker.setModel(selectedModel);
    });
    panel.add(modelSelector, gbc);


    // --- Top Controls ---
    JLabel trainingZapLabel = new JLabel("Current Kills: 0");
    trainingZapLabel.setHorizontalAlignment(SwingConstants.CENTER);
    trainingZapCounter.setLabel(trainingZapLabel);
    panel.add(trainingZapLabel, gbc);

    JLabel trainingMissLabel = new JLabel("Current Misses: 0");
    trainingMissLabel.setHorizontalAlignment(SwingConstants.CENTER);
    trainingMissCounter.setLabel(trainingMissLabel);
    panel.add(trainingMissLabel, gbc);

    JButton autoTrainButton = new JButton("Auto Train AI");
    autoTrainButton.addActionListener(e -> {
      externalTracker.toggleAutoTraining();
    });
    externalTracker.getChaperoneAI().setAutoTrainButton(autoTrainButton); // Link button to AI
    panel.add(autoTrainButton, gbc);

    // --- Training Data Panel ---
    gbc.weighty = 0.1; // Give some weight to this to push other components
    gbc.fill = GridBagConstraints.BOTH;
    JPanel trainingDataPanel = new JPanel(new GridBagLayout());
    trainingDataPanel.setBorder(BorderFactory.createTitledBorder("Training Data"));
    panel.add(trainingDataPanel, gbc);

    GridBagConstraints gbcData = new GridBagConstraints();
    gbcData.gridwidth = GridBagConstraints.REMAINDER;
    gbcData.fill = GridBagConstraints.HORIZONTAL;
    gbcData.insets = new Insets(2, 5, 2, 5);

    final ZapperModel zapperModel = externalTracker.getZapperModel();
    final JLabel savedDataLabel = new JLabel(zapperModel.getSavedDataDetails());
    savedDataLabel.setHorizontalAlignment(SwingConstants.CENTER);
    trainingDataPanel.add(savedDataLabel, gbcData);

    JButton trashDataButton = new JButton("Trash Saved Data");
    trashDataButton.addActionListener(e -> {
      int choice = JOptionPane.showConfirmDialog(
          panel,
          "Are you sure you want to delete all saved training data?",
          "Confirm Deletion",
          JOptionPane.YES_NO_OPTION,
          JOptionPane.WARNING_MESSAGE
      );
      if (choice == JOptionPane.YES_OPTION) {
        zapperModel.trashSavedData();
        savedDataLabel.setText(zapperModel.getSavedDataDetails());
        JOptionPane.showMessageDialog(panel, "Saved training data has been deleted.");
      }
    });
    trainingDataPanel.add(trashDataButton, gbcData);

    gbcData.weighty = 1.0;
    gbcData.anchor = GridBagConstraints.SOUTH;
    JButton endGenerationButton = new JButton("End Generation & Log");
    endGenerationButton.addActionListener(e -> {
      externalTracker.endGeneration();
    });
    trainingDataPanel.add(endGenerationButton, gbcData);

    // --- Chaperone AI Status Section ---
    gbc.weighty = 0.1;
    gbc.fill = GridBagConstraints.BOTH;
    JPanel chaperonePanel = new JPanel(new BorderLayout());
    chaperonePanel.setBorder(BorderFactory.createTitledBorder("Chaperone AI Status"));
    panel.add(chaperonePanel, gbc);

    chaperoneStatusLabel.setHorizontalAlignment(SwingConstants.CENTER);
    chaperonePanel.add(chaperoneStatusLabel, BorderLayout.CENTER);

    JButton interruptButton = new JButton("Interrupt");
    interruptButton.addActionListener(e -> {
      externalTracker.getChaperoneAI().forceIntervention(
          externalTracker.getZapperModel(),
          trainingLogPanel,
          externalTracker.getAccuracy(),
          (externalTracker.getHighestZaps() > 0) ? (double) externalTracker.getHighestZaps() / (externalTracker.getHighestZaps()) * 100 : 0.0,
          externalTracker.getHighestZaps(),
          externalTracker.getHighestZaps()
      );
    });
    chaperonePanel.add(interruptButton, BorderLayout.SOUTH);

    // --- Training Log ---
    gbc.weighty = 1.0; // Give most weight to the log
    gbc.fill = GridBagConstraints.BOTH;
    panel.add(trainingLogPanel, gbc);

    return panel;
  }
}

// --- Mosquito and CO2Zone Classes ---

class Mosquito {
  private final UUID id;
  private double x, y;
  private double vx, vy;
  private static double MAX_SPEED = 2.0;
  private static final double ATTRACTION_FORCE = 0.1;
  private static double RANDOM_FORCE = 0.2;
  private final Color color;
  private final int size = 4;
  private static final Random random = new Random();

  // Static setters for dynamic difficulty changes
  public static void setMaxSpeed(double speed) {
    MAX_SPEED = speed;
  }

  public static void setRandomForce(double force) {
    RANDOM_FORCE = force;
  }

  // Constructor for random spawning
  public Mosquito(int panelWidth, int panelHeight) {
    this(
        random.nextDouble() * panelWidth * 1.5 - panelWidth * 0.25,
        random.nextDouble() * panelHeight * 1.5 - panelHeight * 0.25
    );
  }

  // Constructor for spawning at specific coordinates
  public Mosquito(double x, double y) {
    this.id = UUID.randomUUID();
    this.x = x;
    this.y = y;
    this.vx = (random.nextDouble() - 0.5) * 2 * MAX_SPEED;
    this.vy = (random.nextDouble() - 0.5) * 2 * MAX_SPEED;
    this.color = new Color(random.nextInt(256), random.nextInt(256), random.nextInt(256));
  }

  public UUID getId() { return id; }
  public double getX() { return x; }
  public double getY() { return y; }
  public double getVx() { return vx; }
  public double getVy() { return vy; }

  public void destroy() {
    // No-op for now, but a place for potential destruction logic
  }

  public void update(List<CO2Zone> zones, double speedMultiplier) {
    // Apply more erratic, random movement to simulate real flight paths
    this.vx += (random.nextDouble() - 0.5) * RANDOM_FORCE * speedMultiplier;
    this.vy += (random.nextDouble() - 0.5) * RANDOM_FORCE * speedMultiplier;

    // Apply attraction force from CO2 zones
    for (CO2Zone zone : zones) {
      double dx = zone.getX() - this.x;
      double dy = zone.getY() - this.y;
      double distance = Math.sqrt(dx * dx + dy * dy);

      // The closer the mosquito, the stronger the attraction
      if (distance > 0) {
        double attractionMagnitude = ATTRACTION_FORCE * 100 / (distance + 1);
        this.vx += dx / distance * attractionMagnitude;
        this.vy += dy / distance * attractionMagnitude;
      }
    }

    // Clamp speed to prevent it from getting too high
    double currentSpeed = Math.sqrt(vx * vx + vy * vy);
    if (currentSpeed > MAX_SPEED) {
      double ratio = MAX_SPEED / currentSpeed;
      vx *= ratio;
      vy *= ratio;
    }

    // Update position based on velocity and speed multiplier
    x += vx * speedMultiplier;
    y += vy * speedMultiplier;
  }

  public void draw(Graphics g) {
    g.setColor(color);
    g.fillOval((int) x, (int) y, size, size);
  }
}

class CO2Zone {
  private final int x, y;
  private static final int MAX_RADIUS = 50;
  private static final int BLIP_PERIOD = 20; // Number of frames for a full blip cycle
  private int animationFrame = 0;

  public CO2Zone(int x, int y) {
    this.x = x;
    this.y = y;
  }

  public int getX() { return x; }
  public int getY() { return y; }

  public void update() {
    animationFrame = (animationFrame + 1) % BLIP_PERIOD;
  }

  public void draw(Graphics g) {
    Graphics2D g2d = (Graphics2D) g;
    // Draw multiple concentric circles with decreasing opacity
    for (int i = 0; i < 4; i++) {
      // Calculate radius and opacity for each circle
      double radius = (double) MAX_RADIUS * ((animationFrame + (i * (BLIP_PERIOD / 4))) % BLIP_PERIOD) / BLIP_PERIOD;
      double alpha = 1.0 - (radius / MAX_RADIUS);

      // Set the color with the calculated alpha value
      g2d.setColor(new Color(255, 0, 0, (int) (alpha * 200)));

      // Draw the circle
      int diameter = (int) (radius * 2);
      int drawX = x - (int) radius;
      int drawY = y - (int) radius;
      g2d.fillOval(drawX, drawY, diameter, diameter);
    }
  }

  public boolean contains(int clickX, int clickY) {
    int dx = clickX - x;
    int dy = clickY - y;
    return dx * dx + dy * dy <= MAX_RADIUS * MAX_RADIUS;
  }
}

class MosquitoPanel extends JPanel implements ActionListener {
  private final List<Mosquito> mosquitoes = new ArrayList<>();
  private final List<CO2Zone> co2Zones = new ArrayList<>();
  private final List<TrackMark> trackedMarks = new ArrayList<>();
  private final List<PredictedPath> predictedPaths = new ArrayList<>();
  private final List<ZapAnimation> zapAnimations = new ArrayList<>();
  private final List<ZapMarker> zapMarkers = new ArrayList<>();
  private final Timer timer;
  private final Random random = new Random();
  private double speedMultiplier = 0.5;
  private int desiredMosquitoCount = 50;

  public MosquitoPanel() {
    this.setBackground(new Color(240, 255, 240));
    this.setPreferredSize(new Dimension(800, 480));

    this.timer = new Timer(16, this);
    this.timer.start();

    this.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        if (e.getButton() == MouseEvent.BUTTON1) {
          // Left click adds/removes CO2 zones
          boolean zoneClicked = false;
          for (int i = 0; i < co2Zones.size(); i++) {
            if (co2Zones.get(i).contains(e.getX(), e.getY())) {
              co2Zones.remove(i);
              zoneClicked = true;
              break;
            }
          }
          if (!zoneClicked) {
            co2Zones.add(new CO2Zone(e.getX(), e.getY()));
          }
        } else if (e.getButton() == MouseEvent.BUTTON3) {
          // Right click adds a new mosquito
          addMosquitoAt(e.getX(), e.getY());
        }
      }
    });
  }

  public List<Mosquito> getMosquitoes() {
    return mosquitoes;
  }

  // New method to get only mosquitoes currently visible on the panel
  public List<Mosquito> getVisibleMosquitoes() {
    return mosquitoes.stream()
        .filter(m -> m.getX() >= 0 && m.getX() <= getWidth() && m.getY() >= 0 && m.getY() <= getHeight())
        .collect(Collectors.toList());
  }

  public void setTrackedData(List<TrackMark> newMarks, List<PredictedPath> newPaths) {
    this.trackedMarks.addAll(newMarks);
    this.predictedPaths.addAll(newPaths);
  }

  public void addZapAnimation(ZapAnimation animation) {
    this.zapAnimations.add(animation);
  }

  public void addZapMarker(ZapMarker marker) {
    this.zapMarkers.add(marker);
  }

  public void removeMosquito(Mosquito mosquito) {
    mosquitoes.remove(mosquito);
  }

  public void setMosquitoCount(int count) {
    this.desiredMosquitoCount = count;
    int currentSize = mosquitoes.size();
    if (count > currentSize) {
      for (int i = 0; i < (count - currentSize); i++) {
        mosquitoes.add(new Mosquito(getWidth(), getHeight()));
      }
    } else if (count < currentSize) {
      while (mosquitoes.size() > count) {
        mosquitoes.remove(random.nextInt(mosquitoes.size()));
      }
    }
  }

  public int getDesiredMosquitoCount() {
    return desiredMosquitoCount;
  }

  public void addMosquitoAt(int x, int y) {
    mosquitoes.add(new Mosquito(x, y));
  }

  public void addMultipleMosquitoes(int count) {
    for (int i = 0; i < count; i++) {
      mosquitoes.add(new Mosquito(getWidth(), getHeight()));
    }
  }

  public void setMosquitoSpeed(double speed) {
    this.speedMultiplier = speed;
  }

  @Override
  protected void paintComponent(Graphics g) {
    super.paintComponent(g);
    for (Mosquito mosquito : mosquitoes) {
      mosquito.draw(g);
    }
    for (CO2Zone zone : co2Zones) {
      zone.draw(g);
    }
    for (TrackMark mark : trackedMarks) {
      mark.draw(g);
    }
    for (PredictedPath path : predictedPaths) {
      path.draw(g);
    }
    for (ZapAnimation zap : zapAnimations) {
      zap.draw(g);
    }
    for (ZapMarker marker : zapMarkers) {
      marker.draw(g);
    }
  }

  @Override
  public void actionPerformed(ActionEvent e) {
    for (Mosquito mosquito : mosquitoes) {
      mosquito.update(co2Zones, speedMultiplier);
    }
    for (CO2Zone zone : co2Zones) {
      zone.update();
    }
    trackedMarks.removeIf(TrackMark::isExpired);
    predictedPaths.removeIf(PredictedPath::isExpired);
    zapAnimations.removeIf(ZapAnimation::isExpired);
    zapMarkers.removeIf(ZapMarker::isExpired);
    repaint();
  }
}

// --- External Tracker Classes ---

/**
 * An autonomous external tracker that periodically scans the environment
 * to track a specified number of mosquitos, placing fading brackets at
 * their locations. This version now includes a more robust training AI.
 */
class Tracker implements ActionListener {
  private final MosquitoPanel panel;
  private final ZapCounter zapCounter;
  private final MissCounter missCounter;
  private final TrainingZapCounter trainingZapCounter;
  private final TrainingMissCounter trainingMissCounter;
  private final TrainingLogPanel trainingLogPanel;
  private final JLabel accuracyLabel;
  private final LiveProficiencyChartPanel chartPanel;
  private ZapperModel zapperModel;
  private final Timer scanTimer;
  private int mosquitosToTrack = 5;
  private final Random random = new Random();
  private static final int BRACKET_DURATION = 1500;
  private final Map<UUID, TrackedMosquitoData> trackedMosquitoes = new HashMap<>();
  private final RandomColorGenerator colorGenerator = new RandomColorGenerator();
  private final double ZAP_TOLERANCE = 15.0;
  private final ChaperoneAI chaperoneAI;

  // Auto-training variables
  private boolean isAutoTraining = false;
  private final Timer autoTrainTimer;
  private int autoTrainLevel = 0;
  private static final double AUTO_TRAIN_SPEED_INCREMENT = 0.05;
  private static final double AUTO_TRAIN_FORCE_INCREMENT = 0.05;
  private static final int AUTO_TRAIN_TIMER_DELAY = 10000; // Check and increase difficulty every 10 seconds
  private static final double PROFICIENCY_THRESHOLD = 75.0; // 75% accuracy

  private int highestZaps = 0;
  private int consecutiveDownGens = 0;
  private int lastGenZaps = -1;
  private long startTime;

  public Tracker(MosquitoPanel panel,
      ZapCounter zapCounter,
      MissCounter missCounter,
      TrainingZapCounter trainingZapCounter,
      TrainingMissCounter trainingMissCounter,
      TrainingLogPanel trainingLogPanel,
      JLabel accuracyLabel,
      LiveProficiencyChartPanel chartPanel,
      JLabel chaperoneStatusLabel) {
    this.panel = panel;
    this.zapCounter = zapCounter;
    this.missCounter = missCounter;
    this.trainingZapCounter = trainingZapCounter;
    this.trainingMissCounter = trainingMissCounter;
    this.trainingLogPanel = trainingLogPanel;
    this.accuracyLabel = accuracyLabel;
    this.chartPanel = chartPanel;
    this.zapperModel = new AdaptiveLearningZapper();
    this.scanTimer = new Timer(500, this);
    this.startTime = System.currentTimeMillis();
    this.chaperoneAI = new ChaperoneAI(chaperoneStatusLabel);

    // Timer for auto-training logic
    this.autoTrainTimer = new Timer(AUTO_TRAIN_TIMER_DELAY, e -> {
      if (isAutoTraining) {
        // Increase difficulty
        autoTrainLevel++;
        double newSpeed = 0.5 + autoTrainLevel * AUTO_TRAIN_SPEED_INCREMENT;
        double newForce = 0.2 + autoTrainLevel * AUTO_TRAIN_FORCE_INCREMENT;
        Mosquito.setMaxSpeed(2.0 + autoTrainLevel * AUTO_TRAIN_SPEED_INCREMENT);
        Mosquito.setRandomForce(0.2 + autoTrainLevel * AUTO_TRAIN_FORCE_INCREMENT);
        panel.setMosquitoSpeed(newSpeed);

        // Check for proficiency
        if (getAccuracy() >= PROFICIENCY_THRESHOLD) {
          toggleAutoTraining();
          JOptionPane.showMessageDialog(panel, "AI has reached 75% accuracy and is now proficient!", "Training Complete", JOptionPane.INFORMATION_MESSAGE);
        }
      }
    });
  }

  public ZapperModel getZapperModel() {
    return zapperModel;
  }

  public void setModel(String modelName) {
    if ("Neural Network".equals(modelName)) {
      this.zapperModel = new NeuralNetworkZapper();
    } else {
      this.zapperModel = new AdaptiveLearningZapper();
    }
    this.zapperModel.loadData();
  }

  public ChaperoneAI getChaperoneAI() {
    return chaperoneAI;
  }

  public int getHighestZaps() {
    return highestZaps;
  }

  public void setMosquitosToTrack(int count) {
    this.mosquitosToTrack = count;
  }

  public void setScanFrequency(int frequency) {
    this.scanTimer.setDelay(frequency);
  }

  public void startTracking() {
    this.scanTimer.start();
    chaperoneAI.setIdleState();
  }

  public void toggleAutoTraining() {
    this.isAutoTraining = !this.isAutoTraining;
    if (isAutoTraining) {
      autoTrainTimer.start();
      autoTrainLevel = 0; // Reset difficulty
      Mosquito.setMaxSpeed(2.0);
      Mosquito.setRandomForce(0.2);
      panel.setMosquitoSpeed(0.5);
      lastGenZaps = -1;
      consecutiveDownGens = 0;
      startTime = System.currentTimeMillis(); // Reset start time for the graph
      chartPanel.clearData();
      chaperoneAI.setMonitoringState();
    } else {
      autoTrainTimer.stop();
      chaperoneAI.setIdleState();
    }
  }

  public boolean isAutoTraining() {
    return isAutoTraining;
  }

  public void endGeneration() {
    int currentZaps = trainingZapCounter.getCount();
    int currentMisses = trainingMissCounter.getCount();
    double accuracy = (currentZaps + currentMisses > 0) ? (double) currentZaps / (currentZaps + currentMisses) * 100 : 0.0;

    // Chaperone AI logic
    chaperoneAI.recordGeneration(currentZaps);
    if (chaperoneAI.isPerformanceDeclining()) {
      double bestAccuracy = (highestZaps > 0) ? (double) highestZaps / (highestZaps) * 100 : 0.0;
      chaperoneAI.takeCorrectiveAction(zapperModel, trainingLogPanel, accuracy, bestAccuracy, highestZaps, highestZaps);
    }

    // Logic for tracking consecutive declining generations
    if (lastGenZaps != -1) {
      if (currentZaps <= lastGenZaps) {
        consecutiveDownGens++;
      } else {
        consecutiveDownGens = 0;
      }
    }

    trainingLogPanel.addGenerationEntry(currentZaps, highestZaps, lastGenZaps, accuracy); // Pass the accuracy
    lastGenZaps = currentZaps; // Update score for the next comparison

    // Compare with the highest score achieved so far
    if (currentZaps > highestZaps) {
      highestZaps = currentZaps;
      zapperModel.saveSnapshot(); // Save the current state
    }

    trainingZapCounter.reset();
    trainingMissCounter.reset();
  }

  public double getAccuracy() {
    int currentZaps = trainingZapCounter.getCount();
    int currentMisses = trainingMissCounter.getCount();
    int totalAttempts = currentZaps + currentMisses;
    return (totalAttempts > 0) ? (double) currentZaps / totalAttempts * 100 : 0.0;
  }

  public void saveLearningData() {
    zapperModel.saveData();
  }

  public void loadLearningData() {
    zapperModel.loadData();
  }

  @Override
  public void actionPerformed(ActionEvent e) {
    // Continuous spawning logic for auto-training
    if (isAutoTraining) {
      int currentCount = panel.getVisibleMosquitoes().size();
      int desiredCount = panel.getDesiredMosquitoCount();
      if (currentCount < desiredCount) {
        panel.addMultipleMosquitoes(desiredCount - currentCount);
      }

      // Update the live proficiency graph
      int overallZaps = zapCounter.getCount();
      int overallMisses = missCounter.getCount();
      int currentGenZaps = trainingZapCounter.getCount();
      int currentGenMisses = trainingMissCounter.getCount();

      double overallAccuracy = (overallZaps + overallMisses) > 0 ? (double) overallZaps / (overallZaps + overallMisses) * 100 : 0.0;
      double currentGenAccuracy = (currentGenZaps + currentGenMisses) > 0 ? (double) currentGenZaps / (currentGenZaps + currentGenMisses) * 100 : 0.0;

      long elapsedTime = System.currentTimeMillis();
      chartPanel.addDataPoint(elapsedTime, overallAccuracy, currentGenAccuracy);
    }

    List<Mosquito> visibleMosquitoes = new ArrayList<>(panel.getVisibleMosquitoes());

    trackedMosquitoes.keySet().retainAll(visibleMosquitoes.stream()
        .map(Mosquito::getId)
        .collect(Collectors.toList()));

    if (visibleMosquitoes.isEmpty() || mosquitosToTrack == 0) {
      return;
    }

    List<Mosquito> untrackedMosquitoes = visibleMosquitoes.stream()
        .filter(m -> !trackedMosquitoes.containsKey(m.getId()))
        .collect(Collectors.toList());

    java.util.Collections.shuffle(untrackedMosquitoes);
    int newTrackCount = Math.min(mosquitosToTrack - trackedMosquitoes.size(), untrackedMosquitoes.size());

    for (int i = 0; i < newTrackCount; i++) {
      Mosquito newMosquito = untrackedMosquitoes.get(i);
      Color uniqueColor = colorGenerator.nextColor();
      trackedMosquitoes.put(newMosquito.getId(), new TrackedMosquitoData(uniqueColor));
    }

    List<TrackMark> newMarks = new ArrayList<>();
    List<PredictedPath> newPaths = new ArrayList<>();

    for (Mosquito trackedMosquito : visibleMosquitoes) {
      TrackedMosquitoData data = trackedMosquitoes.get(trackedMosquito.getId());

      if (data == null) {
        continue;
      }

      double currentX = trackedMosquito.getX();
      double currentY = trackedMosquito.getY();

      data.addPosition(new Point.Double(currentX, currentY));
      data.incrementTrackCount();

      Point.Double predictedPosition = zapperModel.predictPosition(
          trackedMosquito.getId(),
          new Point.Double(currentX, currentY),
          trackedMosquito.getVx(),
          trackedMosquito.getVy(),
          scanTimer.getDelay()
      );

      panel.addZapMarker(new ZapMarker(predictedPosition.x, predictedPosition.y, 500));

      double dx = trackedMosquito.getX() - predictedPosition.x;
      double dy = trackedMosquito.getY() - predictedPosition.y;
      double distance = Math.sqrt(dx * dx + dy * dy);

      if (distance < ZAP_TOLERANCE) {
        ZapAnimation zap = new ZapAnimation(trackedMosquito.getX(), trackedMosquito.getY(), data.getColor(), 500);
        panel.addZapAnimation(zap);
        panel.removeMosquito(trackedMosquito);
        trackedMosquitoes.remove(trackedMosquito.getId());
        zapCounter.increment();
        trainingZapCounter.increment();
      } else {
        missCounter.increment();
        trainingMissCounter.increment();
        zapperModel.learnFromMiss(trackedMosquito.getId(), predictedPosition, new Point.Double(trackedMosquito.getX(), trackedMosquito.getY()));

        TrackMark newMark = new TrackMark(currentX, currentY, data.getColor(), BRACKET_DURATION, data.getTrackCount(), data.getHexColor());
        newMarks.add(newMark);
        PredictedPath newPath = new PredictedPath(currentX, currentY, predictedPosition.x, predictedPosition.y, data.getColor(), BRACKET_DURATION);
        newPaths.add(newPath);
      }
    }

    panel.setTrackedData(newMarks, newPaths);

    int currentZaps = trainingZapCounter.getCount();
    int currentMisses = trainingMissCounter.getCount();
    int totalAttempts = currentZaps + currentMisses;

    double currentAccuracy = (totalAttempts > 0) ? (double) currentZaps / totalAttempts * 100 : 0.0;
    accuracyLabel.setText(String.format("Current Accuracy: %.1f%%", currentAccuracy));

    if (totalAttempts >= 20 && isAutoTraining) {
      endGeneration();
    }
  }
}

interface ZapperModel {
  Point2D.Double predictPosition(UUID mosquitoId, Point2D.Double currentPos, double vx, double vy, int timeDeltaMs);
  void learnFromMiss(UUID mosquitoId, Point2D.Double predictedPos, Point2D.Double actualPos);
  void saveData();
  void loadData();
  void saveSnapshot();
  void revertToSnapshot();
  String getSavedDataDetails();
  void trashSavedData();
}

/**
 * A simple AI that learns from missed zaps to correct its future predictions.
 * This version now includes checkpointing for "best" generations.
 */
class AdaptiveLearningZapper implements ZapperModel {
  private static final String PREF_KEY = "mosquito_tracker_adaptive_learning_data";
  private final Preferences prefs = Preferences.userNodeForPackage(MosquitoSimulator.class);
  private final Map<UUID, List<Point2D.Double>> predictionCorrections = new HashMap<>();
  private final Map<UUID, List<Point2D.Double>> snapshotCorrections = new HashMap<>();
  private final int maxCorrectionsHistory = 5;

  public AdaptiveLearningZapper() {
    // Initialize with default values if needed
  }

  @Override
  public String getSavedDataDetails() {
    String savedData = prefs.get(PREF_KEY, "");
    if (savedData.isEmpty()) {
      return "No training data saved.";
    }
    String[] entries = savedData.split("\\|");
    return entries.length + " saved records.";
  }

  @Override
  public void trashSavedData() {
    prefs.remove(PREF_KEY);
    resetAllCorrections();
  }

  @Override
  public Point2D.Double predictPosition(UUID mosquitoId, Point2D.Double currentPos, double vx, double vy, int timeDeltaMs) {
    double predictedX = currentPos.x + vx * timeDeltaMs / 16.0;
    double predictedY = currentPos.y + vy * timeDeltaMs / 16.0;

    List<Point2D.Double> corrections = predictionCorrections.get(mosquitoId);
    if (corrections != null && !corrections.isEmpty()) {
      double avgCorrectionX = corrections.stream().mapToDouble(c -> c.x).average().orElse(0.0);
      double avgCorrectionY = corrections.stream().mapToDouble(c -> c.y).average().orElse(0.0);
      predictedX += avgCorrectionX;
      predictedY += avgCorrectionY;
    }

    return new Point2D.Double(predictedX, predictedY);
  }

  @Override
  public void learnFromMiss(UUID mosquitoId, Point2D.Double predictedPos, Point2D.Double actualPos) {
    double errorX = actualPos.x - predictedPos.x;
    double errorY = actualPos.y - predictedPos.y;

    List<Point2D.Double> corrections = predictionCorrections.computeIfAbsent(mosquitoId, k -> new ArrayList<>());

    corrections.add(new Point2D.Double(errorX, errorY));
    if (corrections.size() > maxCorrectionsHistory) {
      corrections.remove(0);
    }
  }

  // Creates a deep copy of the current learning data
  @Override
  public void saveSnapshot() {
    snapshotCorrections.clear();
    for (Map.Entry<UUID, List<Point2D.Double>> entry : predictionCorrections.entrySet()) {
      snapshotCorrections.put(entry.getKey(), new ArrayList<>(entry.getValue()));
    }
  }

  // Reverts the current learning data to the last saved snapshot
  @Override
  public void revertToSnapshot() {
    predictionCorrections.clear();
    for (Map.Entry<UUID, List<Point2D.Double>> entry : snapshotCorrections.entrySet()) {
      predictionCorrections.put(entry.getKey(), new ArrayList<>(entry.getValue()));
    }
  }

  public void resetAllCorrections() {
    predictionCorrections.clear();
    snapshotCorrections.clear();
  }

  // Saves the current learning data to Java Preferences
  @Override
  public void saveData() {
    StringBuilder sb = new StringBuilder();
    for (Map.Entry<UUID, List<Point2D.Double>> entry : predictionCorrections.entrySet()) {
      sb.append(entry.getKey().toString());
      for (Point2D.Double point : entry.getValue()) {
        sb.append(",");
        sb.append(point.x);
        sb.append(",");
        sb.append(point.y);
      }
      sb.append("|");
    }
    prefs.put(PREF_KEY, sb.toString());
  }

  // Loads the learning data from Java Preferences on startup
  @Override
  public void loadData() {
    String savedData = prefs.get(PREF_KEY, "");
    if (savedData.isEmpty()) {
      return;
    }

    predictionCorrections.clear();
    String[] entries = savedData.split("\\|");
    for (String entry : entries) {
      if (entry.isEmpty()) continue;
      String[] parts = entry.split(",");
      if (parts.length < 1) continue;

      try {
        UUID id = UUID.fromString(parts[0]);
        List<Point2D.Double> corrections = new ArrayList<>();
        for (int i = 1; i < parts.length; i += 2) {
          if (i + 1 < parts.length) {
            double x = Double.parseDouble(parts[i]);
            double y = Double.parseDouble(parts[i+1]);
            corrections.add(new Point2D.Double(x, y));
          }
        }
        if (!corrections.isEmpty()) {
          predictionCorrections.put(id, corrections);
        }
      } catch (IllegalArgumentException e) {
        // Malformed data, skip this entry
        System.err.println("Failed to parse UUID or double from saved data.");
      }
    }
    // Also save a snapshot of the loaded data to serve as the initial checkpoint
    saveSnapshot();
  }
}

class NeuralNetworkZapper implements ZapperModel {

  private final Model model;
  private final Path modelPath = Paths.get("build/model");
  private final List<float[]> trainingData = new ArrayList<>();
  private final List<float[]> trainingLabels = new ArrayList<>();

  private static final Translator<NDList, NDList> PASSTHROUGH = new Translator<NDList, NDList>() {
    @Override public NDList processInput(TranslatorContext ctx, NDList input) { return input; }
    @Override public NDList processOutput(TranslatorContext ctx, NDList output) { return output; }
    @Override public Batchifier getBatchifier() { return Batchifier.STACK; }
  };

  public NeuralNetworkZapper() {
    SequentialBlock block = new SequentialBlock();
    block.add(Linear.builder().setUnits(128).build());
    block.add(ai.djl.nn.Activation::relu);
    block.add(Linear.builder().setUnits(64).build());
    block.add(ai.djl.nn.Activation::relu);
    block.add(Linear.builder().setUnits(2).build());

    model = Model.newInstance("mosquito-zapper");
    model.setBlock(block);

    DefaultTrainingConfig dummyCfg = new DefaultTrainingConfig(Loss.l2Loss());

    try (Trainer trainer = model.newTrainer(dummyCfg)) {
      trainer.initialize(new Shape(1, 4)); // your input is [x, y, vx, vy]
    }
  }

  @Override
  public Point2D.Double predictPosition(UUID mosquitoId, Point2D.Double currentPos,
      double vx, double vy, int timeDeltaMs) {

    try (NDManager manager = NDManager.newBaseManager()) {
      float[] input = {(float) currentPos.x, (float) currentPos.y, (float) vx, (float) vy};
      NDArray inputArray = manager.create(input).reshape(1, 4);
      NDList a = new NDList(inputArray);

      try (Predictor<NDList, NDList> predictor = model.newPredictor(PASSTHROUGH)) {
        NDList result = predictor.predict(a);
        float[] output = result.get(0).toFloatArray();
        return new Point2D.Double(output[0], output[1]);
      }
    }
    catch (Exception e) {
      e.printStackTrace();
      return new Point2D.Double(currentPos.x, currentPos.y);
    }
  }

  @Override
  public void learnFromMiss(UUID mosquitoId, Point2D.Double predictedPos, Point2D.Double actualPos) {
    float[] input = {(float) predictedPos.x, (float) predictedPos.y, 0, 0}; // Simplified input for training
    float[] label = {(float) actualPos.x, (float) actualPos.y};

    trainingData.add(input);
    trainingLabels.add(label);

    if (trainingData.size() >= 10) { // Train every 10 misses
      try (NDManager manager = NDManager.newBaseManager()) {
        // Flatten the lists into single float arrays
        float[] flatData = new float[trainingData.size() * 4];
        float[] flatLabels = new float[trainingLabels.size() * 2];
        for (int i = 0; i < trainingData.size(); i++) {
          System.arraycopy(trainingData.get(i), 0, flatData, i * 4, 4);
          System.arraycopy(trainingLabels.get(i), 0, flatLabels, i * 2, 2);
        }

        NDArray data = manager.create(flatData, new Shape(trainingData.size(), 4));
        NDArray labels = manager.create(flatLabels, new Shape(trainingLabels.size(), 2));

        ArrayDataset dataset = new ArrayDataset.Builder()
            .setData(data)
            .optLabels(labels)
            .setSampling(32, true)
            .build();

        DefaultTrainingConfig config = new DefaultTrainingConfig(Loss.l2Loss())
            .optOptimizer(Adam.builder().optLearningRateTracker(ai.djl.training.tracker.Tracker.fixed(0.001f)).build());

        try (Trainer trainer = model.newTrainer(config)) {

          trainer.initialize(new Shape(1, 4));
          EasyTrain.fit(trainer, 1, dataset, null);
        }

        // Clear buffers after training
        trainingData.clear();
        trainingLabels.clear();
      } catch (Exception e) {
        e.printStackTrace();
      }
    }
  }
  @Override
  public void saveData() {
    try {
      Files.createDirectories(modelPath);
      model.save(modelPath, "mosquito-zapper");
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  @Override
  public void loadData() {
    if (Files.exists(modelPath)) {
      try {
        model.load(modelPath, "mosquito-zapper");
      } catch (Exception e) {
        e.printStackTrace();
      }
    }
  }

  @Override
  public void saveSnapshot() {
    try {
      model.save(modelPath, "snapshot");
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  @Override
  public void revertToSnapshot() {
    try {
      model.load(modelPath, "snapshot");
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  @Override
  public String getSavedDataDetails() {
    return Files.exists(modelPath.resolve("mosquito-zapper.params")) ? "Model data found." : "No model data found.";
  }

  @Override
  public void trashSavedData() {
    try {
      if (Files.exists(modelPath)) {
        Files.walk(modelPath)
            .map(Path::toFile)
            .forEach(File::delete);
      }
    } catch (IOException e) {
      e.printStackTrace();
    }
  }
}


/**
 * A class representing the data for a single tracked mosquito,
 * including its unique color, history of track marks, and predicted path.
 */
class TrackedMosquitoData {
  private final Color uniqueColor;
  private final String hexColor;
  private final List<Point2D.Double> positionHistory;
  private final int maxHistory = 5;
  private int trackCount = 0;

  public TrackedMosquitoData(Color color) {
    this.uniqueColor = color;
    this.hexColor = String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
    this.positionHistory = new ArrayList<>();
  }

  public Color getColor() {
    return uniqueColor;
  }

  public String getHexColor() {
    return hexColor;
  }

  public void addPosition(Point2D.Double position) {
    positionHistory.add(position);
    if (positionHistory.size() > maxHistory) {
      positionHistory.remove(0);
    }
  }

  public void incrementTrackCount() {
    trackCount++;
  }

  public int getTrackCount() {
    return trackCount;
  }

  public Point2D.Double getLastPosition() {
    if (positionHistory.isEmpty()) {
      return null;
    }
    return positionHistory.get(positionHistory.size() - 1);
  }
}

/**
 * A class representing a single fading bracket on the canvas.
 */
class TrackMark {
  private final int x, y;
  private final Color color;
  private final String hexColor;
  private final long creationTime;
  private final int duration;
  private final int trackCount;
  private static final int BRACKET_SIZE = 15;
  private static final int BRACKET_THICKNESS = 2;

  public TrackMark(double x, double y, Color color, int duration, int trackCount, String hexColor) {
    this.x = (int) x;
    this.y = (int) y;
    this.color = color;
    this.hexColor = hexColor;
    this.creationTime = System.currentTimeMillis();
    this.duration = duration;
    this.trackCount = trackCount;
  }

  public boolean isExpired() {
    return System.currentTimeMillis() - creationTime > duration;
  }

  public void draw(Graphics g) {
    Graphics2D g2d = (Graphics2D) g;

    float opacity = 1.0f - (float) (System.currentTimeMillis() - creationTime) / duration;
    if (opacity < 0) opacity = 0;

    Color trackColor = new Color(color.getRed(), color.getGreen(), color.getBlue(), (int) (255 * opacity));
    g2d.setColor(trackColor);
    g2d.setStroke(new BasicStroke(BRACKET_THICKNESS));

    // Draw the four corner brackets
    int halfSize = BRACKET_SIZE / 2;
    int cornerLength = 5;

    // Top-left
    g2d.drawLine(x - halfSize, y - halfSize + cornerLength, x - halfSize, y - halfSize);
    g2d.drawLine(x - halfSize + cornerLength, y - halfSize, x - halfSize, y - halfSize);

    // Top-right
    g2d.drawLine(x + halfSize, y - halfSize + cornerLength, x + halfSize, y - halfSize);
    g2d.drawLine(x + halfSize - cornerLength, y - halfSize, x + halfSize, y - halfSize);

    // Bottom-left
    g2d.drawLine(x - halfSize, y + halfSize - cornerLength, x - halfSize, y + halfSize);
    g2d.drawLine(x - halfSize + cornerLength, y + halfSize, x - halfSize, y + halfSize);

    // Bottom-right
    g2d.drawLine(x + halfSize, y + halfSize - cornerLength, x + halfSize, y + halfSize);
    g2d.drawLine(x + halfSize - cornerLength, y + halfSize, x + halfSize, y + halfSize);

    // Draw the track count number
    if (trackCount > 0) {
      g2d.setFont(new Font("Arial", Font.BOLD, 10));
      g2d.drawString(String.valueOf(trackCount), x - 4, y + 4);
    }

    // Draw the hex color value
    g2d.setFont(new Font("Arial", Font.PLAIN, 8));
    g2d.drawString(hexColor, x - halfSize, y - halfSize - 2);
  }
}

class PredictedPath {
  private final int startX, startY, endX, endY;
  private final Color color;
  private final long creationTime;
  private final int duration;

  public PredictedPath(double startX, double startY, double endX, double endY, Color color, int duration) {
    this.startX = (int) startX;
    this.startY = (int) startY;
    this.endX = (int) endX;
    this.endY = (int) endY;
    this.color = color;
    this.creationTime = System.currentTimeMillis();
    this.duration = duration;
  }

  public boolean isExpired() {
    return System.currentTimeMillis() - creationTime > duration;
  }

  public void draw(Graphics g) {
    Graphics2D g2d = (Graphics2D) g;

    float opacity = 1.0f - (float) (System.currentTimeMillis() - creationTime) / duration;
    if (opacity < 0) opacity = 0;

    g2d.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), (int) (255 * opacity)));
    g2d.setStroke(new BasicStroke(1));

    g2d.drawLine(startX, startY, endX, endY);
  }
}

class ZapAnimation {
  private final int x, y;
  private final Color color;
  private final long creationTime;
  private final int duration;
  private static final int MAX_RADIUS = 30;

  public ZapAnimation(double x, double y, Color color, int duration) {
    this.x = (int) x;
    this.y = (int) y;
    this.color = color;
    this.creationTime = System.currentTimeMillis();
    this.duration = duration;
  }

  public boolean isExpired() {
    return System.currentTimeMillis() - creationTime > duration;
  }

  public void draw(Graphics g) {
    Graphics2D g2d = (Graphics2D) g;

    float progress = (float) (System.currentTimeMillis() - creationTime) / duration;
    float opacity = 1.0f - progress;
    int radius = (int) (MAX_RADIUS * progress);

    if (opacity < 0) opacity = 0;

    Color zapColor = new Color(color.getRed(), color.getGreen(), color.getBlue(), (int) (255 * opacity));
    g2d.setColor(zapColor);
    g2d.setStroke(new BasicStroke(2));

    int diameter = radius * 2;
    int drawX = x - radius;
    int drawY = y - radius;

    g2d.drawOval(drawX, drawY, diameter, diameter);
  }
}

class ZapMarker {
  private final int x, y;
  private final long creationTime;
  private final int duration;

  public ZapMarker(double x, double y, int duration) {
    this.x = (int) x;
    this.y = (int) y;
    this.creationTime = System.currentTimeMillis();
    this.duration = duration;
  }

  public boolean isExpired() {
    return System.currentTimeMillis() - creationTime > duration;
  }

  public void draw(Graphics g) {
    Graphics2D g2d = (Graphics2D) g;

    float opacity = 1.0f - (float) (System.currentTimeMillis() - creationTime) / duration;
    if (opacity < 0) opacity = 0;

    Color markerColor = new Color(255, 0, 0, (int) (255 * opacity));
    g2d.setColor(markerColor);
    g2d.setStroke(new BasicStroke(1));

    g2d.drawLine(x - 5, y, x + 5, y);
    g2d.drawLine(x, y - 5, x, y + 5);
  }
}

class RandomColorGenerator {
  private final Random random = new Random();

  public Color nextColor() {
    return new Color(random.nextFloat(), random.nextFloat(), random.nextFloat());
  }
}

class ZapCounter {
  private int count = 0;
  private JLabel label;

  public void setLabel(JLabel label) {
    this.label = label;
    updateLabel();
  }

  public void increment() {
    count++;
    updateLabel();
  }

  public int getCount() {
    return count;
  }

  public void reset() {
    count = 0;
    updateLabel();
  }

  private void updateLabel() {
    if (label != null) {
      label.setText("Mosquitoes Zapped: " + count);
    }
  }
}

class MissCounter {
  private int count = 0;
  private JLabel label;

  public void setLabel(JLabel label) {
    this.label = label;
    updateLabel();
  }

  public void increment() {
    count++;
    updateLabel();
  }

  public int getCount() {
    return count;
  }

  public void reset() {
    count = 0;
    updateLabel();
  }

  private void updateLabel() {
    if (label != null) {
      label.setText("Missed Zaps: " + count);
    }
  }
}

class TrainingZapCounter {
  private int count = 0;
  private JLabel label;

  public void setLabel(JLabel label) {
    this.label = label;
    updateLabel();
  }

  public void increment() {
    count++;
    updateLabel();
  }

  public int getCount() {
    return count;
  }

  public void reset() {
    count = 0;
    updateLabel();
  }

  private void updateLabel() {
    if (label != null) {
      label.setText("Current Kills: " + count);
    }
  }
}

// New class for tracking misses in a single training generation
class TrainingMissCounter {
  private int count = 0;
  private JLabel label;

  public void setLabel(JLabel label) {
    this.label = label;
    updateLabel();
  }

  public void increment() {
    count++;
    updateLabel();
  }

  public int getCount() {
    return count;
  }

  public void reset() {
    count = 0;
    updateLabel();
  }

  private void updateLabel() {
    if (label != null) {
      label.setText("Current Misses: " + count);
    }
  }
}

// Panel to display the training log with heatmap-like colors and performance indicators
class TrainingLogPanel extends JPanel {
  private final JPanel logPanel;
  private int generationCount = 0;

  public TrainingLogPanel() {
    this.setLayout(new BorderLayout());
    this.setPreferredSize(new Dimension(220, 100));

    // Add a header for the log columns
    JPanel headerPanel = new JPanel(new GridLayout(1, 4));
    headerPanel.add(new JLabel("Gen", SwingConstants.CENTER));
    headerPanel.add(new JLabel("Perf.", SwingConstants.CENTER));
    headerPanel.add(new JLabel("Accuracy", SwingConstants.CENTER));
    headerPanel.add(new JLabel("Zaps", SwingConstants.CENTER));
    this.add(headerPanel, BorderLayout.NORTH);

    this.logPanel = new JPanel();
    this.logPanel.setLayout(new BoxLayout(logPanel, BoxLayout.Y_AXIS));
    this.logPanel.setBackground(new Color(250, 250, 250));

    JScrollPane scrollPane = new JScrollPane(logPanel);
    scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
    scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
    this.add(scrollPane, BorderLayout.CENTER);
  }

  public void addGenerationEntry(int zapCount, int highestZaps, int previousZapCount, double accuracy) {
    generationCount++;

    String performanceIndicator = "—";
    if (previousZapCount != -1) {
      if (zapCount > previousZapCount) {
        performanceIndicator = "▲";
      } else if (zapCount < previousZapCount) {
        performanceIndicator = "▼";
      }
    }

    float scoreNormalized = (highestZaps > 0) ? (float) zapCount / highestZaps : 0;
    float hue = scoreNormalized * 0.33f;
    Color bgColor = Color.getHSBColor(hue, 0.8f, 0.9f);

    // Create a new panel for each log entry to ensure correct alignment
    JPanel entryPanel = new JPanel(new GridLayout(1, 4));
    entryPanel.setBackground(bgColor);
    entryPanel.setMaximumSize(new Dimension(Short.MAX_VALUE, 20));

    JLabel genLabel = new JLabel("Gen " + generationCount, SwingConstants.CENTER);
    JLabel indicatorLabel = new JLabel(performanceIndicator, SwingConstants.CENTER);
    JLabel accLabel = new JLabel(String.format("%.1f%%", accuracy), SwingConstants.CENTER);
    JLabel zapsLabel = new JLabel(String.valueOf(zapCount), SwingConstants.CENTER);

    entryPanel.add(genLabel);
    entryPanel.add(indicatorLabel);
    entryPanel.add(accLabel);
    entryPanel.add(zapsLabel);

    this.logPanel.add(entryPanel);
    this.logPanel.revalidate();
    this.logPanel.repaint();

    JScrollBar vertical = ((JScrollPane) this.getComponent(1)).getVerticalScrollBar();
    vertical.setValue(vertical.getMaximum());
  }

  public void addRevertEntry(double currentAccuracy, double bestAccuracy, int bestZaps, int highestZaps) {
    generationCount++;
    float scoreNormalized = (highestZaps > 0) ? (float) bestZaps / highestZaps : 0;
    float hue = scoreNormalized * 0.33f; // Green for high scores
    Color bgColor = Color.getHSBColor(hue, 0.9f, 1.0f);

    JPanel entryPanel = new JPanel(new GridLayout(1, 1));
    entryPanel.setBackground(bgColor);
    entryPanel.setBorder(BorderFactory.createLineBorder(Color.YELLOW, 2));
    entryPanel.setMaximumSize(new Dimension(Short.MAX_VALUE, 40));

    String revertText = String.format("<html><center>Reverting! Gen %d (%s)<br>Back to Best: %s</center></html>",
        generationCount,
        String.format("%.1f%%", currentAccuracy),
        String.format("%.1f%%", bestAccuracy));
    JLabel revertLabel = new JLabel(revertText, SwingConstants.CENTER);
    revertLabel.setFont(new Font("Arial", Font.BOLD, 10));

    entryPanel.add(revertLabel);

    this.logPanel.add(entryPanel);
    this.logPanel.revalidate();
    this.logPanel.repaint();

    JScrollBar vertical = ((JScrollPane) this.getComponent(1)).getVerticalScrollBar();
    vertical.setValue(vertical.getMaximum());
  }
}

class LiveProficiencyChartPanel extends JPanel {
  private final List<Point2D.Double> overallProficiencyData = new ArrayList<>();
  private final List<Point2D.Double> currentGenProficiencyData = new ArrayList<>();
  private double desiredProficiency = 75.0;
  private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss");
  private static final Color COLOR_BLACK = Color.BLACK;
  private static final Color COLOR_WHITE = Color.WHITE;
  private static final Color COLOR_GRID = new Color(50, 50, 50);
  private static final Color COLOR_WATERMARK = new Color(255, 255, 255, 100);
  private static final Color COLOR_PROFICIENCY_OVERALL = new Color(239, 68, 68);
  private static final Color COLOR_PROFICIENCY_CURRENT = new Color(246, 213, 94);
  private static final Font FONT_AXIS = new Font("Arial", Font.PLAIN, 10);
  private static final Font FONT_LEGEND = new Font("Arial", Font.PLAIN, 12);
  private static final Font FONT_MESSAGE = new Font("Arial", Font.PLAIN, 14);
  private static final Stroke STROKE_WATERMARK = new BasicStroke(1, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10.0f, new float[]{5.0f}, 0.0f);
  private static final Stroke STROKE_GRAPH = new BasicStroke(2);
  private static final Stroke STROKE_DEFAULT = new BasicStroke(1);


  public LiveProficiencyChartPanel() {
    this.setPreferredSize(new Dimension(800, 200));
    this.setBackground(COLOR_BLACK);
  }

  public void addDataPoint(long time, double overallAccuracy, double currentGenAccuracy) {
    overallProficiencyData.add(new Point2D.Double(time, overallAccuracy));
    currentGenProficiencyData.add(new Point2D.Double(time, currentGenAccuracy));

    this.repaint();
  }

  public void clearData() {
    overallProficiencyData.clear();
    currentGenProficiencyData.clear();
    this.repaint();
  }

  public void setDesiredProficiency(double proficiency) {
    this.desiredProficiency = proficiency;
    this.repaint();
  }

  @Override
  protected void paintComponent(Graphics g) {
    super.paintComponent(g);
    Graphics2D g2d = (Graphics2D) g;
    g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

    int width = getWidth();
    int height = getHeight();
    int leftPadding = 40;
    int rightPadding = 20;
    int topPadding = 10;
    int bottomPadding = 40;
    int legendWidth = 130;
    int graphWidth = width - leftPadding - rightPadding - legendWidth;
    int graphHeight = height - topPadding - bottomPadding;

    // Draw grid lines and Y-axis labels
    g2d.setColor(COLOR_WHITE);
    g2d.setFont(FONT_AXIS);
    for (int i = 0; i <= 10; i++) {
      int y = height - bottomPadding - (i * graphHeight / 10);
      g2d.setColor(COLOR_GRID);
      g2d.drawLine(leftPadding, y, leftPadding + graphWidth, y);
      if (i % 2 == 0) {
        g2d.setColor(COLOR_WHITE);
        g2d.drawString(i * 10 + "%", 5, y + 4);
      }
    }

    if (overallProficiencyData.isEmpty()) {
      g2d.setColor(Color.LIGHT_GRAY);
      g2d.setFont(FONT_MESSAGE);
      String message = "Start auto-training to view live graph";
      FontMetrics fm = g2d.getFontMetrics();
      int x = (width - fm.stringWidth(message)) / 2;
      int y = (height - fm.getHeight()) / 2 + fm.getAscent();
      g2d.drawString(message, x, y);
      return;
    }

    // Draw the watermark for desired proficiency
    int watermarkY = height - bottomPadding - (int) (desiredProficiency / 100.0 * graphHeight);
    g2d.setColor(COLOR_WATERMARK);
    g2d.setStroke(STROKE_WATERMARK);
    g2d.drawLine(leftPadding, watermarkY, leftPadding + graphWidth, watermarkY);
    g2d.drawString("Desired Proficiency", leftPadding + 5, watermarkY - 5);
    g2d.setStroke(STROKE_DEFAULT);

    // Draw X-axis labels (Time)
    long startTime = (long) overallProficiencyData.get(0).getX();
    long endTime = (long) overallProficiencyData.get(overallProficiencyData.size() - 1).getX();
    long timeSpan = Math.max(1, endTime - startTime);
    int numTicks = Math.max(2, graphWidth / 80); // A tick roughly every 80 pixels
    long tickInterval = (long) Math.ceil((double) timeSpan / numTicks / 1000.0) * 1000; // in whole seconds

    for (long t = startTime - (startTime % 1000); t <= endTime; t += tickInterval) {
      if(t < startTime) continue;
      int x = leftPadding + (int) (((double) (t - startTime) / timeSpan) * graphWidth);
      g2d.setColor(COLOR_GRID);
      g2d.drawLine(x, height - bottomPadding, x, topPadding);
      g2d.setColor(COLOR_WHITE);
      g2d.drawString(timeFormat.format(new Date(t)), x - 15, height - bottomPadding + 15);
    }

    // Draw the line graphs
    g2d.setStroke(STROKE_GRAPH);
    drawPath(g2d, overallProficiencyData, COLOR_PROFICIENCY_OVERALL, startTime, timeSpan, graphWidth, graphHeight, leftPadding, bottomPadding);
    drawPath(g2d, currentGenProficiencyData, COLOR_PROFICIENCY_CURRENT, startTime, timeSpan, graphWidth, graphHeight, leftPadding, bottomPadding);

    // Draw legend
    int legendX = width - rightPadding - legendWidth + 20;
    g2d.setColor(COLOR_WHITE);
    g2d.setFont(FONT_LEGEND);
    g2d.drawString("Overall Proficiency", legendX, topPadding + 20);
    g2d.drawString("Current Generation", legendX, topPadding + 40);
    g2d.setColor(COLOR_PROFICIENCY_OVERALL);
    g2d.fillRect(legendX - 20, topPadding + 10, 10, 10);
    g2d.setColor(COLOR_PROFICIENCY_CURRENT);
    g2d.fillRect(legendX - 20, topPadding + 30, 10, 10);
  }

  private void drawPath(Graphics2D g2d, List<Point2D.Double> data, Color color, long startTime, long timeSpan, int graphWidth, int graphHeight, int leftPadding, int bottomPadding) {
    if (data.size() < 2) return;
    GeneralPath path = new GeneralPath();
    for (int i = 0; i < data.size(); i++) {
      long time = (long) data.get(i).getX();
      double value = data.get(i).getY();
      int x = leftPadding + (int) (((double) (time - startTime) / timeSpan) * graphWidth);
      int y = getHeight() - bottomPadding - (int) (value / 100.0 * graphHeight);
      if (i == 0) {
        path.moveTo(x, y);
      } else {
        path.lineTo(x, y);
      }
    }
    g2d.setColor(color);
    g2d.draw(path);
  }
}

class ChaperoneAI {
  private final List<Integer> recentGenerationZaps = new ArrayList<>();
  private final int observationWindow = 5; // Number of generations to observe
  private final double declineThreshold = -2.0; // Average decline to trigger action
  private final JLabel statusLabel;
  private final ImageIcon spinnerIcon = new ImageIcon(getClass().getResource("/gif/spinner-1.gif"));
  private final ImageIcon gearsIcon = new ImageIcon(getClass().getResource("/gif/small-gears.gif"));
  private JButton autoTrainButton;

  public ChaperoneAI(JLabel statusLabel) {
    this.statusLabel = statusLabel;
  }

  public void setAutoTrainButton(JButton button) {
    this.autoTrainButton = button;
  }

  public void setIdleState() {
    statusLabel.setIcon(spinnerIcon);
    statusLabel.setToolTipText("Chaperone AI is idle. Start auto-training to activate.");
    if (autoTrainButton != null) {
      autoTrainButton.setText("Auto Train AI");
    }
  }

  public void setMonitoringState() {
    statusLabel.setIcon(gearsIcon);
    statusLabel.setToolTipText("Chaperone AI is actively monitoring performance.");
    if (autoTrainButton != null) {
      autoTrainButton.setText("Stop Auto-Training");
    }
  }

  private void setInterventionState(String reason) {
    statusLabel.setIcon(gearsIcon);
    statusLabel.setToolTipText(reason);
  }

  public void recordGeneration(int zaps) {
    recentGenerationZaps.add(zaps);
    if (recentGenerationZaps.size() > observationWindow) {
      recentGenerationZaps.remove(0);
    }
  }

  public boolean isPerformanceDeclining() {
    if (recentGenerationZaps.size() < observationWindow) {
      return false;
    }

    double totalChange = 0;
    for (int i = 1; i < recentGenerationZaps.size(); i++) {
      totalChange += recentGenerationZaps.get(i) - recentGenerationZaps.get(i-1);
    }
    double averageChange = totalChange / (recentGenerationZaps.size() - 1);

    return averageChange < declineThreshold;
  }

  public void takeCorrectiveAction(ZapperModel zapperModel, TrainingLogPanel trainingLogPanel, double currentAccuracy, double bestAccuracy, int bestZaps, int highestZaps) {
    zapperModel.revertToSnapshot();
    setInterventionState("Performance declining. Reverting to best model.");
    trainingLogPanel.addRevertEntry(currentAccuracy, bestAccuracy, bestZaps, highestZaps);
    recentGenerationZaps.clear(); // Reset after intervention
  }

  public void forceIntervention(ZapperModel zapperModel, TrainingLogPanel trainingLogPanel, double currentAccuracy, double bestAccuracy, int bestZaps, int highestZaps) {
    zapperModel.revertToSnapshot();
    setInterventionState("Forced intervention. Reverting to best model.");
    trainingLogPanel.addRevertEntry(currentAccuracy, bestAccuracy, bestZaps, highestZaps);
    recentGenerationZaps.clear();
  }
}
