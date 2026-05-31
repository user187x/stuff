package xxx.com.calendar.touchpad;

import com.formdev.flatlaf.FlatDarkLaf;
import org.rocksdb.Options;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.rocksdb.RocksIterator;

import javax.swing.*;
import javax.swing.Timer;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.awt.image.RescaleOp;
import java.awt.image.VolatileImage;
import java.io.*;
import java.lang.ref.WeakReference;
import java.net.URL;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.List;
import java.util.*;
import java.util.function.Consumer;

public class TouchScreenCalendar extends JFrame {

  private static final String CARD_MONTH = "MonthView";
  private static final String CARD_DAY = "DayView";
  private static final String CARD_EDITOR = "EditorView";

  private final CardLayout cardLayout = new CardLayout();
  private final JPanel mainPanel = new JPanel(cardLayout);
  private final MonthViewPanel monthView;
  private final DayViewPanel dayView;
  private final FullScreenEventEditor editorView;
  private final EventStore eventStore;

  private YearMonth currentYearMonth;
  private int navClickCount = 0;
  private long firstNavClickTime = 0;
  private boolean isFullScreen = false;
  private final javax.swing.Timer animationTimer;

  private static ImageIcon backButtonIcon;
  private static ImageIcon forwardButtonIcon;
  private static ImageIcon slugButtonIcon;
  private static ImageIcon spinnerIcon;

  private static final Color KINDA_GRAY = new Color(135, 135, 135);
  private static Font robotoFont;

  private static final Color BACKGROUND_COLOR = UIManager.getColor("Panel.background");

  private final String databasePath = "calendar_db";
  private final DimmingPane dimmingPane;

  public TouchScreenCalendar() {

    initializeResources();
    setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

    // The DimmingPane is now the intelligent container for popups.
    dimmingPane = new DimmingPane();
    setGlassPane(dimmingPane);

    eventStore = new EventStore(databasePath);
    currentYearMonth = YearMonth.now();

    Consumer<LocalDate> onDayViewRequested = this::showDayView;

    Consumer<Event> onEventUpdated =
        (event) -> {
          eventStore.saveEvent(event);
          refreshViews();
          showDayView(event.dateTime.toLocalDate());
        };

    Consumer<Event> onEventDeleted =
        (event) -> {
          eventStore.deleteEvent(event);
          refreshViews();
        };

    Runnable onMonthViewRequested = this::showMonthView;
    Consumer<EditorRequest> onEditorRequested = this::showEventEditor;

    monthView = new MonthViewPanel(onDayViewRequested, this::handleSecretClick);
    dayView = new DayViewPanel(onMonthViewRequested, onEditorRequested, onEventDeleted);
    editorView = new FullScreenEventEditor(onEventUpdated, this::showDayViewFromEditor);

    mainPanel.add(monthView, CARD_MONTH);
    mainPanel.add(dayView, CARD_DAY);
    mainPanel.add(editorView, CARD_EDITOR);
    add(mainPanel);

    Runtime.getRuntime().addShutdownHook(new Thread(eventStore::close));

    setFocusable(true);

    animationTimer = new javax.swing.Timer(50, e -> monthView.repaint());
    animationTimer.start();

    updateMonthView();
    cardLayout.show(mainPanel, CARD_MONTH);

    toggleFullScreen();
  }

  // This method allows other panels to access the single DimmingPane instance.
  private DimmingPane getDimmingPane() {
    return this.dimmingPane;
  }

  public void initializeResources() {
    URL backImageUrl = getClass().getResource("/png/back.png");
    URL forwardImageUrl = getClass().getResource("/png/forward.png");
    URL todayButtonUrl = getClass().getResource("/png/today.png");
    URL slugButtonUrl = getClass().getResource("/png/slug.png");
    URL spinnerButtonUrl = getClass().getResource("/gif/spinner-2.gif");

    List<URL> images = new ArrayList<>();
    images.add(backImageUrl);
    images.add(forwardImageUrl);
    images.add(todayButtonUrl);

    for (URL url : images) {
      if (url == null) {
        System.out.println("Image not found : " + url);
        System.exit(1);
      }
    }

    backButtonIcon =
        recolorIcon(
            scaleIcon(new ImageIcon(Objects.requireNonNull(backImageUrl)), 125, 95), KINDA_GRAY);
    forwardButtonIcon =
        recolorIcon(
            scaleIcon(new ImageIcon(Objects.requireNonNull(forwardImageUrl)), 125, 95), KINDA_GRAY);

    spinnerIcon = new ImageIcon(Objects.requireNonNull(spinnerButtonUrl));

    slugButtonIcon = scaleIcon(new ImageIcon(Objects.requireNonNull(slugButtonUrl)), 125, 95);
    // slugButtonIcon = recolorIcon(slugButtonIcon, UIManager.getColor("Panel.background"));

    String fontName = "Roboto.ttf";

    try (InputStream inputStream = getClass().getResourceAsStream("/font/" + fontName)) {

      if (inputStream == null) throw new IOException("Font resource not found: " + fontName);

      robotoFont = Font.createFont(Font.TRUETYPE_FONT, inputStream);
      GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();

      List<String> availableFonts = Arrays.asList(ge.getAvailableFontFamilyNames());

      if (!availableFonts.contains(robotoFont.getFontName())) {
        ge.registerFont(robotoFont);
      }

      System.out.println("Font loaded : " + robotoFont.getFontName());
    } catch (Exception e) {

      System.out.println("Font not found : " + e.getMessage());
      System.exit(1);
    }
  }

  public static class EditorRequest {
    final Event event;
    final LocalDateTime defaultDateTime;

    EditorRequest(Event event, LocalDateTime defaultDateTime) {
      this.event = event;
      this.defaultDateTime = defaultDateTime;
    }
  }

  private void showEventEditor(EditorRequest request) {
    editorView.prepareEditor(request.event, request.defaultDateTime);
    cardLayout.show(mainPanel, CARD_EDITOR);
  }

  private void showDayViewFromEditor(LocalDate date) {
    showDayView(date);
  }

  private void handleSecretClick() {
    final int CLICK_COUNT_TARGET = 7;
    final long MAX_DELAY_MS = 2000;
    long currentTime = System.currentTimeMillis();
    if (navClickCount == 0 || (currentTime - firstNavClickTime > MAX_DELAY_MS)) {
      firstNavClickTime = currentTime;
      navClickCount = 1;
    } else {
      navClickCount++;
    }
    if (navClickCount == CLICK_COUNT_TARGET) {
      toggleFullScreen();
      navClickCount = 0;
    }
  }

  private void toggleFullScreen() {
    GraphicsDevice device = getGraphicsConfiguration().getDevice();
    isFullScreen = !isFullScreen;

    dispose();
    setUndecorated(isFullScreen);

    if (isFullScreen) {
      device.setFullScreenWindow(this);
      setFocusableWindowState(false);
    } else {
      device.setFullScreenWindow(null);
      setSize(1280, 800);
      setLocationRelativeTo(null);
      setFocusableWindowState(true);
    }
    setVisible(true);
  }

  private void refreshViews() {
    updateMonthView();
    dayView.refreshEvents();
  }

  private void showDayView(LocalDate date) {
    dayView.displayFor(date);
    cardLayout.show(mainPanel, CARD_DAY);
  }

  private void showMonthView() {
    updateMonthView();
    cardLayout.show(mainPanel, CARD_MONTH);
  }

  private void updateMonthView() {
    Map<Integer, List<Event>> monthEvents =
        eventStore.getEventsForMonth(currentYearMonth.getYear(), currentYearMonth.getMonthValue());
    monthView.updateCalendar(currentYearMonth, monthEvents);
  }

  private static class Event implements Serializable {
    @Serial private static final long serialVersionUID = 3528710796136728291L;

    final String id;
    String title;
    LocalDateTime dateTime;
    boolean isAllDay;
    String location;
    String note;
    Color color;

    Event(
        String title,
        LocalDateTime dateTime,
        boolean isAllDay,
        String location,
        String note,
        Color color) {
      this.id = UUID.randomUUID().toString();
      this.title = title;
      this.dateTime = dateTime;
      this.isAllDay = isAllDay;
      this.location = location;
      this.note = note;
      this.color = color;
    }

    public byte[] getDbKey() {
      return (dateTime.toLocalDate().format(DateTimeFormatter.ISO_LOCAL_DATE) + ":" + id)
          .getBytes();
    }
  }

  private static class EventStore {
    private RocksDB db;

    private EventStore(String databaseName) {
      try {
        RocksDB.loadLibrary();
        Options options = new Options().setCreateIfMissing(true);
        db = RocksDB.open(options, databaseName);
      } catch (RocksDBException e) {
        System.out.println("Error initializing RocksDB : " + e.getMessage());
        System.exit(1);
      }
    }

    public void saveEvent(Event event) {
      try {
        db.put(event.getDbKey(), serialize(event));
      } catch (RocksDBException | IOException e) {
        System.out.println(e.getMessage());
        System.exit(1);
      }
    }

    public void deleteEvent(Event event) {
      try {
        db.delete(event.getDbKey());
      } catch (RocksDBException e) {
        System.out.println(e.getMessage());
        System.exit(1);
      }
    }

    public Map<Integer, List<Event>> getEventsForMonth(int year, int month) {
      Map<Integer, List<Event>> eventsByDay = new HashMap<>();
      String monthPrefix = String.format("%d-%02d", year, month);
      try (RocksIterator iterator = db.newIterator()) {
        for (iterator.seek(monthPrefix.getBytes());
            iterator.isValid() && new String(iterator.key()).startsWith(monthPrefix);
            iterator.next()) {
          try {
            Event event = (Event) deserialize(iterator.value());
            eventsByDay
                .computeIfAbsent(event.dateTime.getDayOfMonth(), k -> new ArrayList<>())
                .add(event);
          } catch (IOException | ClassNotFoundException e) {
            System.out.println(e.getMessage());
            System.exit(1);
          }
        }
      }
      return eventsByDay;
    }

    public List<Event> getEventsForDay(LocalDate date) {
      List<Event> events = new ArrayList<>();
      String dayPrefix = date.format(DateTimeFormatter.ISO_LOCAL_DATE);
      try (RocksIterator iterator = db.newIterator()) {
        for (iterator.seek(dayPrefix.getBytes());
            iterator.isValid() && new String(iterator.key()).startsWith(dayPrefix);
            iterator.next()) {
          try {
            events.add((Event) deserialize(iterator.value()));
          } catch (IOException | ClassNotFoundException e) {
            System.out.println(e.getMessage());
            System.exit(1);
          }
        }
      }
      events.sort(Comparator.comparing(e -> e.isAllDay ? LocalDateTime.MIN : e.dateTime));
      return events;
    }

    public void close() {
      if (db != null) db.close();
    }

    private byte[] serialize(Object obj) throws IOException {
      try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
          ObjectOutputStream oos = new ObjectOutputStream(bos)) {
        oos.writeObject(obj);
        return bos.toByteArray();
      }
    }

    private Object deserialize(byte[] bytes) throws IOException, ClassNotFoundException {
      try (ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
          ObjectInputStream ois = new ObjectInputStream(bis)) {
        return ois.readObject();
      }
    }
  }

  public static ImageIcon createFilteredIcon(ImageIcon original, float scaleFactor) {
    BufferedImage img =
        new BufferedImage(
            original.getIconWidth(), original.getIconHeight(), BufferedImage.TYPE_INT_ARGB);
    img.getGraphics().drawImage(original.getImage(), 0, 0, null);
    RescaleOp op = new RescaleOp(scaleFactor, 0, null);
    BufferedImage filteredImage = op.filter(img, null);
    return new ImageIcon(filteredImage);
  }

  public enum ButtonColors {
    NORMAL,
    CLICKED,
    HOOVER
  }

  public static Map<ButtonColors, ImageIcon> getButtonIcons(ImageIcon icon) {
    Map<ButtonColors, ImageIcon> buttonIcons = new HashMap<>();
    ImageIcon brighterIcon = createFilteredIcon(icon, 1.2f);
    ImageIcon darkerIcon = createFilteredIcon(icon, 0.8f);
    buttonIcons.put(ButtonColors.NORMAL, icon);
    buttonIcons.put(ButtonColors.CLICKED, brighterIcon);
    buttonIcons.put(ButtonColors.HOOVER, darkerIcon);
    return buttonIcons;
  }

  public static void addButtonInteractionEffects(JButton button) {
    if (button.getIcon() == null) {
      return;
    }
    button.setContentAreaFilled(false);
    button.setBorderPainted(false);
    button.setFocusPainted(false);
    button.setOpaque(false);
    button.setRolloverEnabled(true);
    Map<ButtonColors, ImageIcon> buttonIcons = getButtonIcons((ImageIcon) button.getIcon());
    button.addMouseListener(
        new java.awt.event.MouseAdapter() {
          public void mouseEntered(java.awt.event.MouseEvent evt) {
            button.setIcon(buttonIcons.get(ButtonColors.HOOVER));
          }

          public void mouseExited(java.awt.event.MouseEvent evt) {
            button.setIcon(buttonIcons.get(ButtonColors.NORMAL));
          }

          public void mousePressed(java.awt.event.MouseEvent evt) {
            button.setIcon(buttonIcons.get(ButtonColors.CLICKED));
          }

          public void mouseReleased(java.awt.event.MouseEvent evt) {
            if (button.contains(evt.getPoint())) {
              button.setIcon(buttonIcons.get(ButtonColors.HOOVER));
            } else {
              button.setIcon(buttonIcons.get(ButtonColors.NORMAL));
            }
          }
        });
  }

  public static ImageIcon scaleIcon(ImageIcon icon, int width, int height) {
    if (icon == null) return null;
    Image img = icon.getImage();
    Image scaledImg = img.getScaledInstance(width, height, Image.SCALE_SMOOTH);
    return new ImageIcon(scaledImg);
  }

  public static class PillBorder extends AbstractBorder {

    private final Color color;
    private final int thickness;
    private final int padding;

    private volatile boolean isGlowing;

    // Animation state
    private float travelProgress = 0.0f;
    private int direction = 1;
    private final Random random = new Random();
    private long lastDirectionChange = System.currentTimeMillis();

    // Shape jitter
    private Shape currentShape;
    private long lastShapeChangeTime = 0L;
    private long shapeSeed = 0L;

    // Stroke dash animation
    private float dashPhase = 0f;

    // Particles
    private final List<Particle> particles = new ArrayList<>();
    private long lastParticleSpawn = 0L;
    private static final int MAX_PARTICLES = 60;

    // Repaint driver per component (no leaks)
    private final WeakHashMap<JComponent, Timer> timers = new WeakHashMap<>();

    public PillBorder(Color color, int thickness, int padding) {
      this.color = color;
      this.thickness = thickness;
      this.padding = padding;
    }

    public void setGlowing(boolean isGlowing) {
      this.isGlowing = isGlowing;
      // Timer creation is deferred to paintBorder, when we have the component.
      // If you disable glowing, timers will auto-stop on next paint.
    }

    @Override
    public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
      final long now = System.currentTimeMillis();

      // Start/stop a repaint timer tied to this component
      maybeManageTimer(c, now);

      Graphics2D g2d = (Graphics2D) g.create();
      try {
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Geometry
        double bx = x + (double) thickness / 2.0;
        double by = y + (double) thickness / 2.0;
        double bw = width - thickness;
        double bh = height - thickness;
        double arc = Math.max(2, bh - thickness);
        RoundRectangle2D borderShape = new RoundRectangle2D.Double(bx, by, bw, bh, arc, arc);

        // Lazy-create a jittered "glow band" shape (changes every 500ms)
        if (currentShape == null || now - lastShapeChangeTime > 500) {
          shapeSeed = random.nextLong();
          currentShape = createDistortedPerimeterBand(borderShape, 72, 2.5, shapeSeed);
          lastShapeChangeTime = now;
        }

        // Dash stroke animation for the outline
        dashPhase = (now % 4000L) / 4000f * 48f; // cycles dash every 4s

        // Draw nice base outline first
        g2d.setStroke(new BasicStroke(thickness, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2d.setColor(color);
        g2d.draw(borderShape);

        // Add animated dashed highlight on top
        float[] dash = {12f, 10f, 3f, 10f};
        Stroke dashed =
            new BasicStroke(
                Math.max(1f, thickness * 0.65f),
                BasicStroke.CAP_ROUND,
                BasicStroke.JOIN_ROUND,
                10f,
                dash,
                dashPhase);
        g2d.setStroke(dashed);
        g2d.setComposite(AlphaComposite.SrcOver.derive(0.45f));
        g2d.setColor(color.brighter());
        g2d.draw(borderShape);

        if (isGlowing) {
          // Update progress and possibly reverse direction
          if (now - lastDirectionChange > 2000 + random.nextInt(3000)) {
            direction *= -1;
            lastDirectionChange = now;
          }
          travelProgress += (0.0035f * direction); // slightly faster
          if (travelProgress > 1f) travelProgress -= 1f;
          if (travelProgress < 0f) travelProgress += 1f;

          // Pulse from 0..1
          double pulse = 0.5 * (1 + Math.sin(now * Math.PI * 2 / 2200.0));

          // "Comet" head point on the perimeter
          Point2D.Double head = getPointOnPillPerimeter(bx, by, bw, bh, travelProgress);

          // Clip to a stroked border band so glows stay near the outline
          Shape glowBand =
              new BasicStroke(
                      Math.max(2f, thickness * 1.6f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
                  .createStrokedShape(borderShape);

          Shape prevClip = g2d.getClip();
          g2d.clip(glowBand);

          // Comet trail (several faint orbs behind the head)
          int trailCount = 9;
          for (int i = 0; i < trailCount; i++) {
            double step = i / (double) trailCount;
            double p = wrap01(travelProgress - step * 0.035 - 0.01 * pulse);
            Point2D.Double pt = getPointOnPillPerimeter(bx, by, bw, bh, p);

            float radius = (float) (14 + 8 * (1 - step) + 4 * pulse);
            float alpha = (float) (0.26 * (1 - step) * (0.7 + 0.3 * pulse));
            drawRadialOrb(g2d, pt, radius, color, alpha);
          }

          // Comet head + tiny orbiter circling around it
          float headRadius = (float) (18 + 10 * pulse);
          drawRadialOrb(g2d, head, headRadius, color.brighter(), 0.65f);

          double orbitT = (now % 1200L) / 1200.0 * Math.PI * 2;
          Point2D.Double orb =
              new Point2D.Double(head.x + Math.cos(orbitT) * 10, head.y + Math.sin(orbitT) * 10);
          drawRadialOrb(g2d, orb, 6f + (float) (3 * pulse), Color.WHITE, 0.55f);

          // Sparkles: short-lived particles ejected from the head
          spawnParticlesIfNeeded(now, head);
          updateAndRenderParticles(g2d, now);

          // Soft outer glow along the band
          g2d.setClip(prevClip);
          g2d.setComposite(AlphaComposite.SrcOver.derive(0.18f));
          Stroke haloStroke =
              new BasicStroke(
                  Math.max(2f, thickness * 2.2f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
          Shape halo = haloStroke.createStrokedShape(borderShape);
          g2d.setColor(color);
          g2d.draw(halo);
        }

      } finally {
        g2d.dispose();
      }
    }

    @Override
    public Insets getBorderInsets(Component c) {
      int total = thickness + padding;
      return new Insets(total, total, total, total);
    }

    @Override
    public Insets getBorderInsets(Component c, Insets insets) {
      int total = thickness + padding;
      insets.left = insets.right = insets.top = insets.bottom = total;
      return insets;
    }

    // --- Internals ---------------------------------------------------------

    private void maybeManageTimer(Component c, long now) {
      if (!(c instanceof JComponent jc)) return;

      Timer t = timers.get(jc);
      if (isGlowing) {
        if (t == null) {
          WeakReference<JComponent> ref = new WeakReference<>(jc);
          t =
              new Timer(
                  16,
                  e -> {
                    JComponent comp = ref.get();
                    if (comp == null || !comp.isDisplayable()) {
                      ((Timer) e.getSource()).stop();
                    } else {
                      comp.repaint();
                    }
                  });
          t.setCoalesce(true);
          t.start();
          timers.put(jc, t);
        } else if (!t.isRunning()) {
          t.start();
        }
      } else if (t != null && t.isRunning()) {
        t.stop();
      }
    }

    private static double wrap01(double v) {
      v %= 1.0;
      if (v < 0) v += 1.0;
      return v;
    }

    /** Returns a point traveling clockwise along a rounded-rectangle "pill" perimeter. */
    private Point2D.Double getPointOnPillPerimeter(
        double x, double y, double w, double h, double progress) {
      double r = h / 2.0;
      double top = Math.max(0, w - h); // straight parts
      double arcLen = Math.PI * r; // each semicircle length
      double peri = (top + arcLen) * 2.0;
      double d = progress * peri;

      // Top edge (left -> right)
      if (d < top) return new Point2D.Double(x + r + d, y);
      d -= top;

      // Right arc (top -> bottom)
      if (d < arcLen) {
        double ang = (d / arcLen) * Math.PI - Math.PI / 2.0;
        return new Point2D.Double(x + w - r + r * Math.cos(ang), y + r + r * Math.sin(ang));
      }
      d -= arcLen;

      // Bottom edge (right -> left)
      if (d < top) return new Point2D.Double(x + w - r - d, y + h);
      d -= top;

      // Left arc (bottom -> top)
      double ang = Math.PI / 2.0 + (d / arcLen) * Math.PI;
      return new Point2D.Double(x + r + r * Math.cos(ang), y + r + r * Math.sin(ang));
    }

    /**
     * Creates a wiggly band following the rounded rectangle, by sampling perimeter points and
     * offsetting them slightly along the local normal. Stable for a short duration via seed.
     */
    private Shape createDistortedPerimeterBand(
        RoundRectangle2D base, int samples, double jitterAmp, long seed) {
      Path2D path = new Path2D.Double();
      Random r = new Random(seed);

      // Sample the core curve
      List<Point2D.Double> pts = new ArrayList<>(samples);
      for (int i = 0; i < samples; i++) {
        double t = i / (double) samples;
        pts.add(
            getPointOnPillPerimeter(
                base.getX(), base.getY(), base.getWidth(), base.getHeight(), t));
      }

      // Build a band by offsetting outward then inward
      List<Point2D.Double> outer = new ArrayList<>(samples);
      List<Point2D.Double> inner = new ArrayList<>(samples);
      for (int i = 0; i < samples; i++) {
        Point2D.Double p = pts.get(i);
        Point2D.Double pPrev = pts.get((i - 1 + samples) % samples);
        Point2D.Double pNext = pts.get((i + 1) % samples);

        double dx = pNext.x - pPrev.x;
        double dy = pNext.y - pPrev.y;
        double len = Math.hypot(dx, dy);
        if (len == 0) len = 1;
        // Normal (outward-ish)
        double nx = -dy / len;
        double ny = dx / len;

        double jitter = (r.nextDouble() * 2 - 1) * jitterAmp;
        double thicknessBand = Math.max(2, this.thickness * 0.8);

        outer.add(
            new Point2D.Double(
                p.x + nx * (thicknessBand + jitter), p.y + ny * (thicknessBand + jitter)));
        inner.add(
            new Point2D.Double(
                p.x - nx * (thicknessBand + jitter), p.y - ny * (thicknessBand + jitter)));
      }

      // Create a closed ring (outer forward, inner backward)
      boolean first = true;
      for (Point2D.Double p : outer) {
        if (first) {
          path.moveTo(p.x, p.y);
          first = false;
        } else path.lineTo(p.x, p.y);
      }
      for (int i = inner.size() - 1; i >= 0; i--) {
        Point2D.Double p = inner.get(i);
        path.lineTo(p.x, p.y);
      }
      path.closePath();
      return path;
    }

    private void drawRadialOrb(
        Graphics2D g2d, Point2D.Double center, float radius, Color base, float alpha) {
      float a = Math.max(0f, Math.min(1f, alpha));
      Color inner = new Color(base.getRed(), base.getGreen(), base.getBlue(), Math.round(255 * a));
      Color outer = new Color(base.getRed(), base.getGreen(), base.getBlue(), 0);

      RadialGradientPaint paint =
          new RadialGradientPaint(
              new Point2D.Float((float) center.x, (float) center.y),
              radius,
              new float[] {0f, 1f},
              new Color[] {inner, outer});
      Paint old = g2d.getPaint();
      g2d.setPaint(paint);
      // Fill a small circle area to apply the radial gradient
      g2d.fill(new Ellipse2D.Double(center.x - radius, center.y - radius, radius * 2, radius * 2));
      g2d.setPaint(old);
    }

    private void spawnParticlesIfNeeded(long now, Point2D.Double origin) {
      if (now - lastParticleSpawn < 35) return;
      lastParticleSpawn = now;

      int spawn = 1 + random.nextInt(3); // 1–3 particles
      for (int i = 0; i < spawn; i++) {
        if (particles.size() >= MAX_PARTICLES) break;
        double ang = random.nextDouble() * Math.PI * 2;
        double speed = 0.4 + random.nextDouble() * 0.9;
        double vx = Math.cos(ang) * speed;
        double vy = Math.sin(ang) * speed;
        float lifeMs = 450 + random.nextInt(400);
        particles.add(new Particle(origin.x, origin.y, vx, vy, now, lifeMs));
      }
    }

    private void updateAndRenderParticles(Graphics2D g2d, long now) {
      g2d.setComposite(AlphaComposite.SrcOver);
      Iterator<Particle> it = particles.iterator();
      while (it.hasNext()) {
        Particle p = it.next();
        float t = (now - p.birth) / p.lifeMs;
        if (t >= 1f) {
          it.remove();
          continue;
        }

        // Ease out + slight upward drift
        double ease = 1 - (1 - t) * (1 - t);
        double x = p.x + p.vx * 60 * ease;
        double y = p.y + p.vy * 60 * ease - 6 * ease;

        float radius = 3f + 3f * (1 - t);
        float alpha = 0.6f * (1 - t);

        drawRadialOrb(g2d, new Point2D.Double(x, y), radius, Color.WHITE, alpha);
      }
    }

    private record Particle(double x,double y,double vx,double vy,long birth,float lifeMs) {
    }
  }

  public static class RoundedBorder extends AbstractBorder {
    private final Color borderColor;
    private final Color titleColor;
    private final int padding;
    private final int thickness;
    private final String title;
    private final Font titleFont;
    private final Set<TitlePosition> titlePositions;

    public enum TitlePosition {
      TOP_LEFT,
      TOP_CENTER,
      TOP_RIGHT,
      BOTTOM_LEFT,
      BOTTOM_CENTER,
      BOTTOM_RIGHT
    }

    public RoundedBorder(Color borderColor, int padding, int thickness) {
      this(
          borderColor,
          KINDA_GRAY,
          padding,
          thickness,
          null,
          new Font("SansSerif", Font.BOLD, 20),
          (TitlePosition) null);
    }

    public RoundedBorder(
        Color borderColor,
        Color titleColor,
        int padding,
        int thickness,
        String title,
        TitlePosition... positions) {
      this(
          borderColor,
          titleColor,
          padding,
          thickness,
          title,
          new Font("SansSerif", Font.BOLD, 20),
          positions);
    }

    public RoundedBorder(
        Color borderColor,
        Color titleColor,
        int padding,
        int thickness,
        String title,
        Font titleFont,
        TitlePosition... positions) {
      this.borderColor = borderColor;
      this.titleColor = titleColor;
      this.padding = padding;
      this.thickness = thickness;
      this.title = title;
      this.titleFont = titleFont;
      this.titlePositions = EnumSet.noneOf(TitlePosition.class);
      if (positions != null && positions.length > 0 && positions[0] != null) {
        this.titlePositions.addAll(Arrays.asList(positions));
      } else {
        if (hasTitle()) {
          this.titlePositions.add(TitlePosition.TOP_CENTER);
        }
      }
    }

    private boolean hasTitle() {
      return title != null && !title.trim().isEmpty();
    }

    @Override
    public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
      Graphics2D g2d = (Graphics2D) g.create();
      g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

      FontMetrics fm = g2d.getFontMetrics(titleFont);
      int titleHeight = fm.getHeight();
      int topOverflow = getTitleOverflow(true, titleHeight);
      int bottomOverflow = getTitleOverflow(false, titleHeight);
      int inset = thickness / 2;

      Shape borderPath =
          new RoundRectangle2D.Double(
              x + inset,
              y + inset + topOverflow,
              width - thickness,
              height - thickness - topOverflow - bottomOverflow,
              padding,
              padding);

      Stroke stroke = new BasicStroke(thickness);
      Area borderArea = new Area(stroke.createStrokedShape(borderPath));

      if (hasTitle()) {
        for (TitlePosition pos : titlePositions) {
          Rectangle2D titleRect = getTitleRect(pos, fm, x, y, width, height);
          Area titleArea = new Area(titleRect);
          borderArea.subtract(titleArea);
        }
      }

      g2d.setColor(borderColor);
      g2d.fill(borderArea);

      if (hasTitle()) {
        g2d.setFont(titleFont);
        g2d.setColor(titleColor);
        for (TitlePosition pos : titlePositions) {
          drawTitleText(g2d, fm, pos, x, y, width, height);
        }
      }

      g2d.dispose();
    }

    private Rectangle2D getTitleRect(
        TitlePosition position, FontMetrics fm, int x, int y, int width, int height) {
      int titleWidth = fm.stringWidth(title);
      int titleHeight = fm.getHeight();
      int topOverflow = getTitleOverflow(true, titleHeight);
      int titleX = 0;
      int rectY = 0;

      switch (position) {
        case TOP_LEFT:
          titleX = x + padding;
          rectY = y + topOverflow;
          break;
        case TOP_CENTER:
          titleX = x + (width - titleWidth) / 2;
          rectY = y + topOverflow;
          break;
        case TOP_RIGHT:
          titleX = x + width - titleWidth - padding;
          rectY = y + topOverflow;
          break;
        case BOTTOM_LEFT:
        case BOTTOM_CENTER:
        case BOTTOM_RIGHT:
          int bottomOverflow = getTitleOverflow(false, titleHeight);
          rectY = y + height - thickness - bottomOverflow;
          if (position == TitlePosition.BOTTOM_LEFT) titleX = x + padding;
          if (position == TitlePosition.BOTTOM_CENTER) titleX = x + (width - titleWidth) / 2;
          if (position == TitlePosition.BOTTOM_RIGHT) titleX = x + width - titleWidth - padding;
          break;
      }
      return new Rectangle2D.Double(titleX - 2, rectY, titleWidth + 4, thickness);
    }

    private void drawTitleText(
        Graphics2D g2d,
        FontMetrics fm,
        TitlePosition position,
        int x,
        int y,
        int width,
        int height) {
      Rectangle2D titleRect = getTitleRect(position, fm, x, y, width, height);
      int titleY = (int) titleRect.getY() + ((thickness - fm.getHeight()) / 2) + fm.getAscent();
      g2d.drawString(title, (int) titleRect.getX() + 2, titleY);
    }

    @Override
    public Insets getBorderInsets(Component c) {
      FontMetrics fm = c.getFontMetrics(titleFont);
      int titleHeight = fm.getHeight();
      int topOverflow = getTitleOverflow(true, titleHeight);
      int bottomOverflow = getTitleOverflow(false, titleHeight);
      int top = padding + thickness + topOverflow;
      int bottom = padding + thickness + bottomOverflow;
      int left = padding + thickness;
      int right = padding + thickness;
      return new Insets(top, left, bottom, right);
    }

    @Override
    public Insets getBorderInsets(Component c, Insets insets) {
      Insets newInsets = getBorderInsets(c);
      insets.left = newInsets.left;
      insets.top = newInsets.top;
      insets.right = newInsets.right;
      insets.bottom = newInsets.bottom;
      return insets;
    }

    private int getTitleOverflow(boolean isTop, int titleHeight) {
      if (!hasTitle()) return 0;
      boolean hasRelevantTitle =
          titlePositions.stream()
              .anyMatch(
                  p ->
                      isTop
                          ? (p == TitlePosition.TOP_LEFT
                              || p == TitlePosition.TOP_CENTER
                              || p == TitlePosition.TOP_RIGHT)
                          : (p == TitlePosition.BOTTOM_LEFT
                              || p == TitlePosition.BOTTOM_CENTER
                              || p == TitlePosition.BOTTOM_RIGHT));
      if (hasRelevantTitle) {
        return Math.max(0, (titleHeight - thickness) / 2);
      }
      return 0;
    }
  }

  public static class CalendarHeaderPanel extends JPanel {
    public final JButton prevButton;
    public final JButton nextButton;
    public final JButton todayButton;
    public final JLabel mainTextLabel;
    public final JLabel clockLabel;
    public final PillButton yearButton;

    private final int navButtonHorizontalPadding = 50;
    private final int navButtonVerticalPadding = 35;

    public CalendarHeaderPanel() {
      super(new BorderLayout());
      setBorder(new EmptyBorder(7, 0, 5, 0));
      setOpaque(false);

      prevButton = new JButton(backButtonIcon);
      addButtonInteractionEffects(prevButton);
      JPanel prevButtonPanel = new JPanel(new BorderLayout());
      prevButtonPanel.setOpaque(false);
      prevButtonPanel.add(prevButton, BorderLayout.CENTER);
      prevButtonPanel.setBorder(
          new EmptyBorder(
              navButtonVerticalPadding, navButtonHorizontalPadding, navButtonVerticalPadding, 0));
      add(prevButtonPanel, BorderLayout.WEST);

      nextButton = new JButton(forwardButtonIcon);
      addButtonInteractionEffects(nextButton);
      JPanel nextButtonPanel = new JPanel(new BorderLayout());
      nextButtonPanel.setOpaque(false);
      nextButtonPanel.add(nextButton, BorderLayout.CENTER);
      nextButtonPanel.setBorder(
          new EmptyBorder(
              navButtonVerticalPadding, 0, navButtonVerticalPadding, navButtonHorizontalPadding));
      add(nextButtonPanel, BorderLayout.EAST);

      JPanel headerCenter = new JPanel(new GridBagLayout());
      headerCenter.setOpaque(false);
      headerCenter.setBorder(new EmptyBorder(0, 450, 0, 450));

      GridBagConstraints gbc = new GridBagConstraints();
      gbc.gridy = 0;
      gbc.fill = GridBagConstraints.NONE;

      URL todayButtonUrl = getClass().getResource("/png/today.png");
      ImageIcon origTodayIcon =
          TouchScreenCalendar.scaleIcon(
              new ImageIcon(Objects.requireNonNull(todayButtonUrl)), 105, 105);
      ImageIcon baseTodayIcon = recolorIcon(origTodayIcon, KINDA_GRAY);
      int dayOfMonth = LocalDate.now().getDayOfMonth();

      AnimatedGlowIcon todayIconWithAnimation = new AnimatedGlowIcon(baseTodayIcon, dayOfMonth);
      todayButton = new JButton(todayIconWithAnimation);
      todayButton.setContentAreaFilled(false);
      todayButton.setBorderPainted(false);
      todayButton.setFocusPainted(false);
      todayButton.addMouseListener(
          new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
              todayIconWithAnimation.setHovered(true);
              todayButton.repaint();
            }

            @Override
            public void mouseExited(MouseEvent e) {
              todayIconWithAnimation.setHovered(false);
              todayIconWithAnimation.setPressed(false);
              todayButton.repaint();
            }

            @Override
            public void mousePressed(MouseEvent e) {
              todayIconWithAnimation.setPressed(true);
              todayButton.repaint();
            }

            @Override
            public void mouseReleased(MouseEvent e) {
              todayIconWithAnimation.setPressed(false);
              todayButton.repaint();
            }
          });

      mainTextLabel = new JLabel("", SwingConstants.CENTER);
      mainTextLabel.setFont(new Font("SansSerif", Font.BOLD, 55));
      mainTextLabel.setBorder(null);

      // Ensure the month label has a constant width (max month) and a stable height,
      // so side buttons don't shift when month names change.
      try {
        java.awt.FontMetrics fm = getFontMetrics(mainTextLabel.getFont());
        int maxMonthWidth = 0;
        for (java.time.Month mon : java.time.Month.values()) {
          String name =
              mon.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.getDefault())
                  .toUpperCase();
          maxMonthWidth = Math.max(maxMonthWidth, fm.stringWidth(name));
        }
        maxMonthWidth += 20; // padding
        int h = Math.max(mainTextLabel.getPreferredSize().height, fm.getHeight());
        java.awt.Dimension fixed = new java.awt.Dimension(maxMonthWidth, h);
        mainTextLabel.setMinimumSize(fixed);
        mainTextLabel.setPreferredSize(fixed);
        mainTextLabel.setMaximumSize(
            new java.awt.Dimension(
                Integer.MAX_VALUE, h)); // allow layout stretch horizontally if needed
      } catch (Throwable ignore) {
        // If fonts/metrics not ready, we simply skip; label will size normally.
      }
      clockLabel = new JLabel();
      clockLabel.setFont(new Font("SansSerif", Font.PLAIN, 18));
      clockLabel.setHorizontalAlignment(SwingConstants.CENTER);

      final PillBorder clockBorder = new PillBorder(KINDA_GRAY, 1, 6);
      clockBorder.setGlowing(true);
      clockLabel.setBorder(new CompoundBorder(clockBorder, new EmptyBorder(0, 45, 0, 45)));

      Timer clockTimer =
          new Timer(
              500,
              e -> {
                LocalTime now = LocalTime.now();
                boolean isColonVisible = (System.currentTimeMillis() / 500) % 2 == 0;
                String format = isColonVisible ? "h:mm a" : "h mm a";
                clockLabel.setText(now.format(DateTimeFormatter.ofPattern(format)));
              });
      clockTimer.start();

      Timer glowTimer = new Timer(20, e -> clockLabel.repaint());
      glowTimer.start();

      JPanel clockContainer = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
      clockContainer.setOpaque(false);
      clockContainer.add(clockLabel);
      JPanel clockBundlePanel = new JPanel(new BorderLayout());
      clockBundlePanel.setOpaque(false);
      clockBundlePanel.add(mainTextLabel, BorderLayout.CENTER);
      clockBundlePanel.add(clockContainer, BorderLayout.SOUTH);

      // TODO
      yearButton = new PillButton("");
      yearButton.setFont(new Font("SansSerif", Font.BOLD, 55));
      yearButton.setBorder(new EmptyBorder(6, 28, 6, 28));

      final int HEADER_GAP = 80;
      gbc.gridx = 0;
      gbc.weightx = 0;
      gbc.fill = GridBagConstraints.NONE;
      headerCenter.add(todayButton, gbc);
      gbc.gridx = 1;
      gbc.weightx = 0;
      gbc.fill = GridBagConstraints.NONE;
      headerCenter.add(Box.createHorizontalStrut(HEADER_GAP), gbc);
      gbc.gridx = 2;
      gbc.weightx = 1.0;
      gbc.fill = GridBagConstraints.HORIZONTAL;
      headerCenter.add(Box.createHorizontalGlue(), gbc);
      gbc.gridx = 3;
      gbc.weightx = 0;
      gbc.fill = GridBagConstraints.NONE;
      headerCenter.add(clockBundlePanel, gbc);
      gbc.gridx = 4;
      gbc.weightx = 1.0;
      gbc.fill = GridBagConstraints.HORIZONTAL;
      headerCenter.add(Box.createHorizontalGlue(), gbc);
      gbc.gridx = 5;
      gbc.weightx = 0;
      gbc.fill = GridBagConstraints.NONE;
      headerCenter.add(Box.createHorizontalStrut(HEADER_GAP), gbc);
      gbc.gridx = 6;
      gbc.weightx = 0;
      gbc.fill = GridBagConstraints.NONE;
      headerCenter.add(yearButton, gbc);

      add(headerCenter, BorderLayout.CENTER);
    }
  }

  public abstract static class BaseViewPanel extends JPanel {
    protected final CalendarHeaderPanel headerPanel;
    protected final JPanel workArea;

    public BaseViewPanel() {
      super(new BorderLayout(5, 5));
      setBorder(new RoundedBorder(Color.white, 30, 5));
      headerPanel = new CalendarHeaderPanel();
      workArea = new JPanel();
      workArea.setOpaque(false);
      add(headerPanel, BorderLayout.NORTH);
      add(workArea, BorderLayout.CENTER);
    }
  }

  private class MonthViewPanel extends BaseViewPanel {

    private final JPanel calendarGrid = new JPanel(new GridLayout(0, 7, 5, 5));
    private final Consumer<LocalDate> onDayDoubleClick;
    private Point swipeStartPoint;
    private boolean isAnimating = false;

    // TODO
    private final Font yearPrefixFont = new Font("Roboto Light", Font.PLAIN, 55);
    private final Color yearPrefixColor = KINDA_GRAY;
    private final Font yearSuffixFont = new Font("SansSerif", Font.BOLD, 65);
    private final Color yearSuffixColor = Color.WHITE;

    private MonthViewPanel(Consumer<LocalDate> onDayDoubleClick, Runnable onSecretClick) {
      super();
      this.onDayDoubleClick = onDayDoubleClick;
      workArea.setLayout(new BorderLayout());
      workArea.add(calendarGrid, BorderLayout.CENTER);
      calendarGrid.setBorder(new RoundedBorder(Color.WHITE, 40, 5));
      headerPanel.prevButton.addActionListener(e -> advanceMonth(-1));
      headerPanel.nextButton.addActionListener(e -> advanceMonth(1));
      headerPanel.todayButton.addActionListener(
          e -> {
            currentYearMonth = YearMonth.now();
            updateMonthView();
          });
      headerPanel.yearButton.addActionListener(e -> showYearPicker());
      headerPanel.mainTextLabel.addMouseListener(
          new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
              if (onSecretClick != null) onSecretClick.run();
            }
          });
      MouseAdapter swipeListener =
          new MouseAdapter() {
            public void mousePressed(MouseEvent e) {
              if (!isAnimating) swipeStartPoint = e.getPoint();
            }

            public void mouseReleased(MouseEvent e) {
              if (swipeStartPoint == null || isAnimating) return;
              int dx = e.getX() - swipeStartPoint.x;
              if (Math.abs(dx) > 50) advanceMonth(dx > 0 ? -1 : 1);
              swipeStartPoint = null;
            }
          };
      addMouseListener(swipeListener);
      calendarGrid.addMouseListener(swipeListener);
    }

    private void showYearPicker() {
      final TouchScreenCalendar mainFrame =
          (TouchScreenCalendar) SwingUtilities.getWindowAncestor(this);
      final DimmingPane dimmer = mainFrame.getDimmingPane();
      YearPickerPanel yearPickerPanel =
          new YearPickerPanel(
              currentYearMonth.getYear(),
              year -> {
                currentYearMonth = currentYearMonth.withYear(year);
                updateMonthView();
                dimmer.hidePanel();
              });
      dimmer.showPanel(yearPickerPanel);
    }

    private void advanceMonth(int direction) {
      if (isAnimating) return;
      isAnimating = true;
      BufferedImage oldImage =
          new BufferedImage(
              calendarGrid.getWidth(), calendarGrid.getHeight(), BufferedImage.TYPE_INT_ARGB);
      calendarGrid.paint(oldImage.createGraphics());
      currentYearMonth = currentYearMonth.plusMonths(direction);
      updateCalendar(
          currentYearMonth,
          eventStore.getEventsForMonth(
              currentYearMonth.getYear(), currentYearMonth.getMonthValue()));
      calendarGrid.validate();
      BufferedImage newImage =
          new BufferedImage(
              calendarGrid.getWidth(), calendarGrid.getHeight(), BufferedImage.TYPE_INT_ARGB);
      calendarGrid.paint(newImage.createGraphics());
      AnimationPane animationPane = new AnimationPane(oldImage, newImage, direction);
      animationPane.setPreferredSize(calendarGrid.getSize());
      workArea.remove(calendarGrid);
      workArea.add(animationPane, BorderLayout.CENTER);
      revalidate();
      animationPane.startAnimation(
          () -> {
            workArea.remove(animationPane);
            workArea.add(calendarGrid, BorderLayout.CENTER);
            revalidate();
            repaint();
            isAnimating = false;
          });
    }

    public void updateCalendar(YearMonth yearMonth, Map<Integer, List<Event>> monthEvents) {
      headerPanel.mainTextLabel.setText(
          yearMonth.format(DateTimeFormatter.ofPattern("MMMM")).toUpperCase());
      headerPanel.yearButton.setYearText(
          String.valueOf(yearMonth.getYear()),
          yearPrefixFont,
          yearPrefixColor,
          yearSuffixFont,
          yearSuffixColor);
      calendarGrid.removeAll();
      String[] daysOfWeek = {
        "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"
      };
      for (String day : daysOfWeek) {
        JLabel weekLabel = new JLabel(day.toUpperCase(), SwingConstants.CENTER);
        weekLabel.setFont(new Font("SansSerif", Font.BOLD, 32));
        calendarGrid.add(weekLabel);
      }
      LocalDate firstOfMonth = yearMonth.atDay(1);
      int dayOfWeekOffset = firstOfMonth.getDayOfWeek().getValue() - DayOfWeek.MONDAY.getValue();
      if (dayOfWeekOffset < 0) dayOfWeekOffset += 7;
      for (int i = 0; i < dayOfWeekOffset; i++) {
        JPanel placeholder = new JPanel();
        placeholder.setOpaque(false);
        calendarGrid.add(placeholder);
      }
      for (int day = 1; day <= yearMonth.lengthOfMonth(); day++) {
        calendarGrid.add(
            new DayCellPanel(
                yearMonth.atDay(day),
                monthEvents.getOrDefault(day, Collections.emptyList()),
                onDayDoubleClick));
      }
      revalidate();
      repaint();
    }
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

  private static class AnimationPane extends JComponent {

    private final BufferedImage oldImage, newImage;
    private final int direction;
    private float progress = 0.0f;

    AnimationPane(BufferedImage old, BufferedImage newImg, int dir) {
      this.oldImage = old;
      this.newImage = newImg;
      this.direction = dir;
      setOpaque(false);
    }

    public void startAnimation(Runnable onFinished) {
      setVisible(true);
      progress = 0f; // reset just in case

      final int FRAME_DELAY_MS = 8;
      final int DURATION_MS = 100;
      final float STEP = FRAME_DELAY_MS / (float) DURATION_MS;

      javax.swing.Timer timer =
          new javax.swing.Timer(
              FRAME_DELAY_MS,
              e -> {
                progress += STEP;
                if (progress >= 1.0f) {
                  progress = 1.0f; // clamp
                  ((javax.swing.Timer) e.getSource()).stop();
                  setVisible(false);
                  if (onFinished != null) onFinished.run();
                }
                repaint();
              });
      timer.start();
    }

    protected void paintComponent(Graphics g) {
      super.paintComponent(g);
      int oldX = (int) (-progress * getWidth() * direction);
      int newX =
          direction > 0
              ? getWidth() - (int) (progress * getWidth())
              : -getWidth() + (int) (progress * getWidth());
      g.drawImage(newImage, newX, 0, this);
      g.drawImage(oldImage, oldX, 0, this);
    }
  }

  private static class DayCellPanel extends JPanel {

    private final LocalDate date;
    private final JLabel dayLabel;
    private long lastClickTime = 0;

    private DayCellPanel(LocalDate date, List<Event> events, Consumer<LocalDate> onDayDoubleClick) {
      this.date = date;
      setLayout(new BorderLayout());
      if (date.isBefore(LocalDate.now())) setBackground(new Color(60, 60, 60));
      else setBackground(new Color(45, 45, 45));
      setBorder(new EmptyBorder(2, 2, 2, 2));
      dayLabel = new JLabel(String.valueOf(date.getDayOfMonth()), SwingConstants.LEFT);
      dayLabel.setBorder(new EmptyBorder(5, 8, 0, 0));
      dayLabel.setFont(new Font("SansSerif", Font.BOLD, 16));
      add(dayLabel, BorderLayout.NORTH);
      JPanel eventsPanel = new JPanel();
      eventsPanel.setLayout(new BoxLayout(eventsPanel, BoxLayout.Y_AXIS));
      eventsPanel.setOpaque(false);
      for (Event event : events) {
        JLabel eventLabel = new JLabel(" " + event.title);
        eventLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
        eventLabel.setOpaque(true);
        eventLabel.setBackground(event.color.darker());
        eventLabel.setForeground(Color.WHITE);
        eventsPanel.add(eventLabel);
      }
      add(eventsPanel, BorderLayout.CENTER);
      addMouseListener(
          new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
              long currentTime = System.currentTimeMillis();
              if (e.getButton() == MouseEvent.BUTTON1) {
                if (currentTime - lastClickTime < 500) {
                  onDayDoubleClick.accept(date);
                  lastClickTime = 0;
                } else {
                  lastClickTime = currentTime;
                }
              }
            }
          });
    }

    @Override
    protected void paintComponent(Graphics g) {
      super.paintComponent(g);
      boolean isToday = date.equals(LocalDate.now());
      if (isToday) dayLabel.setForeground(new Color(135, 206, 250));
      else dayLabel.setForeground(UIManager.getColor("Label.foreground"));
      Graphics2D g2d = (Graphics2D) g.create();
      g2d.setColor(Color.DARK_GRAY);
      g2d.drawRect(0, 0, getWidth() - 1, getHeight() - 1);
      if (isToday) {
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        long time = System.currentTimeMillis();
        double period = 2000.0;
        double glow = 0.5 * (1 + Math.sin(time * 2 * Math.PI / period));
        for (int i = 0; i < 3; i++) {
          Color baseColor = new Color(135, 206, 250);
          float rawAlpha = (float) (glow * (0.4 * (3 - i)));
          float clampedAlpha = Math.max(0.0f, Math.min(1.0f, rawAlpha));
          g2d.setColor(
              new Color(
                  baseColor.getRed() / 255f,
                  baseColor.getGreen() / 255f,
                  baseColor.getBlue() / 255f,
                  clampedAlpha));
          g2d.setStroke(new BasicStroke(2 + i));
          g2d.drawRoundRect(1 + i, 1 + i, getWidth() - 3 - 2 * i, getHeight() - 3 - 2 * i, 8, 8);
        }
      }
      g2d.dispose();
    }
  }

  private static class SchedulePanel extends JPanel implements Scrollable {

    final int HOUR_HEIGHT = 80, TIME_GUTTER_WIDTH = 80;
    private VolatileImage buffer;

    private SchedulePanel() {
      setOpaque(true);
    }

    private void createBuffer() {
      if (getWidth() > 0 && getHeight() > 0) buffer = createVolatileImage(getWidth(), getHeight());
    }

    public void paint(Graphics g) {
      if (buffer == null || getWidth() != buffer.getWidth() || getHeight() != buffer.getHeight())
        createBuffer();
      if (buffer != null) {
        do {
          int code = buffer.validate(getGraphicsConfiguration());
          if (code == VolatileImage.IMAGE_INCOMPATIBLE) createBuffer();
          Graphics2D g2d = buffer.createGraphics();
          super.paint(g2d);
          g2d.dispose();
        } while (buffer.contentsLost());
        g.drawImage(buffer, 0, 0, this);
      } else {
        super.paint(g);
      }
    }

    protected void paintComponent(Graphics g) {
      super.paintComponent(g);
      for (int h = 0; h < 24; h++) {
        int y = h * HOUR_HEIGHT;
        g.setColor(Color.GRAY);
        g.drawLine(TIME_GUTTER_WIDTH, y, getWidth() - TIME_GUTTER_WIDTH, y);
        g.setColor(Color.LIGHT_GRAY);
        g.drawString(
            String.format("%d:00 %s", (h % 12 == 0) ? 12 : h % 12, h < 12 ? "AM" : "PM"),
            10,
            y + 15);
      }
      LocalTime now = LocalTime.now();
      int timeInMinutes = now.getHour() * 60 + now.getMinute();
      int y = (int) (((double) timeInMinutes / (24 * 60)) * (24 * HOUR_HEIGHT));
      g.setColor(Color.RED);
      g.drawLine(TIME_GUTTER_WIDTH, y, getWidth() - TIME_GUTTER_WIDTH, y);
    }

    public Dimension getPreferredScrollableViewportSize() {
      return getPreferredSize();
    }

    public int getScrollableUnitIncrement(Rectangle r, int o, int d) {
      return 16;
    }

    public int getScrollableBlockIncrement(Rectangle r, int o, int d) {
      return r.height;
    }

    public boolean getScrollableTracksViewportWidth() {
      return true;
    }

    public boolean getScrollableTracksViewportHeight() {
      return false;
    }
  }

  public static void setButtonPermanentlyPressed(JButton button) {
    if (button.getIcon() == null) return;
    for (MouseListener listener : button.getMouseListeners()) button.removeMouseListener(listener);
    Map<ButtonColors, ImageIcon> buttonIcons = getButtonIcons((ImageIcon) button.getIcon());
    button.setIcon(buttonIcons.get(ButtonColors.CLICKED));
  }

  private class DayViewPanel extends BaseViewPanel {

    private final Runnable onBack;
    private final Consumer<EditorRequest> onEditorRequest;
    private final Consumer<Event> onEventDelete;
    private LocalDate currentDate;

    private final SchedulePanel schedulePanel = new SchedulePanel();
    private final JScrollPane scrollPane;
    private final javax.swing.Timer flickTimer;
    private Point swipeStartPoint, startViewPosition, lastDragPoint;
    private long lastDragTime;
    private double velocityY;
    private long lastClickTime = 0;

    private final Font yearPrefixFont = new Font("Roboto Light", Font.PLAIN, 45);
    private final Color yearPrefixColor = KINDA_GRAY;
    private final Font yearSuffixFont = new Font("SansSerif", Font.BOLD, 50);
    private final Color yearSuffixColor = Color.WHITE;

    private DayViewPanel(
        Runnable onBack, Consumer<EditorRequest> onEditorRequest, Consumer<Event> onEventDelete) {
      super();
      this.onBack = onBack;
      this.onEditorRequest = onEditorRequest;
      this.onEventDelete = onEventDelete;
      scrollPane = new JScrollPane(schedulePanel);
      scrollPane.setBorder(new RoundedBorder(Color.WHITE, KINDA_GRAY, 40, 5, ""));
      scrollPane.getViewport().setScrollMode(JViewport.SIMPLE_SCROLL_MODE);
      workArea.setLayout(new BorderLayout());
      workArea.add(scrollPane, BorderLayout.CENTER);
      headerPanel.prevButton.addActionListener(e -> onBack.run());
      headerPanel.nextButton.setVisible(true);
      headerPanel.nextButton.setIcon(slugButtonIcon);
      headerPanel.nextButton.setEnabled(false);
      headerPanel.nextButton.setFocusable(false);
      setButtonPermanentlyPressed(headerPanel.nextButton);
      headerPanel.yearButton.setVisible(true);
      headerPanel.yearButton.setEnabled(false);
      headerPanel.yearButton.setFocusable(false);
      headerPanel.mainTextLabel.setFont(new Font("SansSerif", Font.BOLD, 55));
      Icon icon = headerPanel.todayButton.getIcon();
      if (icon instanceof AnimatedGlowIcon) ((AnimatedGlowIcon) icon).setInteractionEnabled(false);
      schedulePanel.addMouseListener(
          new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
              long currentTime = System.currentTimeMillis();
              if (e.getButton() == MouseEvent.BUTTON1) {
                if (currentTime - lastClickTime < 500) {
                  onEditorRequest.accept(
                      new EditorRequest(
                          null, currentDate.atTime(e.getY() / schedulePanel.HOUR_HEIGHT, 0)));
                  lastClickTime = 0;
                } else {
                  lastClickTime = currentTime;
                }
              }
            }
          });
      flickTimer =
          new javax.swing.Timer(
              15,
              e -> {
                JViewport vp = scrollPane.getViewport();
                Point pos = vp.getViewPosition();
                pos.y -= (int) (velocityY * 15);
                velocityY *= 0.95;
                if (pos.y < 0) {
                  pos.y = 0;
                  velocityY = 0;
                }
                if (pos.y > schedulePanel.getHeight() - vp.getHeight()) {
                  pos.y = schedulePanel.getHeight() - vp.getHeight();
                  velocityY = 0;
                }
                vp.setViewPosition(pos);
                if (Math.abs(velocityY) < 0.1) ((javax.swing.Timer) e.getSource()).stop();
              });
      new javax.swing.Timer(60000, e -> schedulePanel.repaint()).start();
      MouseAdapter swipe =
          new MouseAdapter() {
            public void mousePressed(MouseEvent e) {
              flickTimer.stop();
              swipeStartPoint = e.getPoint();
              startViewPosition = scrollPane.getViewport().getViewPosition();
              lastDragPoint = e.getPoint();
              lastDragTime = System.currentTimeMillis();
              velocityY = 0;
            }

            public void mouseDragged(MouseEvent e) {
              if (swipeStartPoint == null) return;
              int dy = e.getY() - swipeStartPoint.y;
              Point p = new Point(startViewPosition);
              p.y -= dy;
              JViewport vp = scrollPane.getViewport();
              if (p.y < 0) p.y = 0;
              if (p.y > schedulePanel.getHeight() - vp.getHeight())
                p.y = schedulePanel.getHeight() - vp.getHeight();
              vp.setViewPosition(p);
              long now = System.currentTimeMillis();
              long dt = now - lastDragTime;
              if (dt > 10) {
                velocityY = (double) (e.getY() - lastDragPoint.y) / dt;
                lastDragTime = now;
                lastDragPoint = e.getPoint();
              }
            }

            public void mouseReleased(MouseEvent e) {
              if (Math.abs(velocityY) > 0.2) flickTimer.start();
              swipeStartPoint = null;
            }
          };
      schedulePanel.addMouseListener(swipe);
      schedulePanel.addMouseMotionListener(swipe);
    }

    public void refreshEvents() {
      if (currentDate != null) displayFor(currentDate);
    }

    public void displayFor(LocalDate date) {
      currentDate = date;
      String weekday = date.format(DateTimeFormatter.ofPattern("EEEE")).toUpperCase();
      scrollPane.setBorder(
          new RoundedBorder(
              Color.WHITE,
              Color.WHITE,
              40,
              5,
              weekday,
              new Font("SansSerif", Font.BOLD, 40),
              RoundedBorder.TitlePosition.TOP_CENTER));
      headerPanel.mainTextLabel.setText(
          date.format(DateTimeFormatter.ofPattern("MMMM")).toUpperCase());
      headerPanel.yearButton.setYearText(
          String.valueOf(date.getYear()),
          yearPrefixFont,
          yearPrefixColor,
          yearSuffixFont,
          yearSuffixColor);
      Icon icon = headerPanel.todayButton.getIcon();
      if (icon instanceof AnimatedGlowIcon) {
        ((AnimatedGlowIcon) icon).setText(String.valueOf(date.getDayOfMonth()));
        headerPanel.todayButton.repaint();
      }
      schedulePanel.removeAll();
      List<Event> events = eventStore.getEventsForDay(date);
      for (Event event : events) {
        EventPanel p = new EventPanel(event, onEditorRequest, onEventDelete);
        int y = event.isAllDay ? 0 : event.dateTime.getHour() * schedulePanel.HOUR_HEIGHT;
        p.setBounds(
            schedulePanel.TIME_GUTTER_WIDTH,
            y,
            getWidth() - (schedulePanel.TIME_GUTTER_WIDTH * 2),
            event.isAllDay ? 30 : 60);
        schedulePanel.add(p);
      }
      schedulePanel.setPreferredSize(new Dimension(0, 24 * schedulePanel.HOUR_HEIGHT));
      schedulePanel.revalidate();
      schedulePanel.repaint();
    }
  }

  private static class EventPanel extends JPanel {
    private long lastClickTime = 0;

    private EventPanel(
        Event event, Consumer<EditorRequest> onEditorRequest, Consumer<Event> onEventDelete) {
      setBackground(event.color);
      setBorder(new LineBorder(event.color.brighter()));
      setLayout(new BorderLayout());
      add(
          new JLabel(
              "<html><b>"
                  + event.title
                  + "</b><br>"
                  + (event.isAllDay
                      ? "All Day"
                      : event.dateTime.format(DateTimeFormatter.ofPattern("h:mm a")))),
          BorderLayout.CENTER);
      addMouseListener(
          new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
              long currentTime = System.currentTimeMillis();
              if (e.getButton() == MouseEvent.BUTTON1) {
                if (currentTime - lastClickTime < 500) {
                  onEditorRequest.accept(new EditorRequest(event, null));
                  lastClickTime = 0;
                } else {
                  lastClickTime = currentTime;
                }
              }
            }
          });
      JPopupMenu menu = new JPopupMenu();
      JMenuItem edit = new JMenuItem("Edit");
      edit.addActionListener(e -> onEditorRequest.accept(new EditorRequest(event, null)));
      JMenuItem delete = new JMenuItem("Delete");
      delete.addActionListener(
          e -> {
            if (JOptionPane.showConfirmDialog(
                    null, "Delete this event?", "Confirm Deletion", JOptionPane.YES_NO_OPTION)
                == JOptionPane.YES_OPTION) onEventDelete.accept(event);
          });
      menu.add(edit);
      menu.add(delete);
      setComponentPopupMenu(menu);
    }
  }

  private static class FullScreenEventEditor extends JPanel {

    private final Consumer<Event> onSave;
    private final Consumer<LocalDate> onCancel;
    private Event currentEvent;
    private LocalDate originalDate, selectedEventDate;
    private LocalTime selectedEventTime;
    private final JTextField titleField = new JTextField(), locationField = new JTextField();
    private final TimePickerPanel timePicker;
    private final JCheckBox allDayCheckBox = new JCheckBox("Entire Day");
    private final JTextArea noteArea = new JTextArea();
    private final JButton dateButton = new JButton();
    private final JPanel formPanel;
    private DatePickerPanel datePickerPanel;
    private final JPanel datePickerContainer;
    private boolean isDatePickerVisible = false;
    private javax.swing.Timer slideTimer;

    public FullScreenEventEditor(Consumer<Event> onSave, Consumer<LocalDate> onCancel) {
      this.onSave = onSave;
      this.onCancel = onCancel;
      setLayout(new BorderLayout(20, 20));
      setBorder(new EmptyBorder(40, 40, 40, 40));
      CalendarHeaderPanel header = new CalendarHeaderPanel();
      header.prevButton.addActionListener(
          e -> {
            if (originalDate != null) onCancel.accept(originalDate);
          });
      header.nextButton.setEnabled(false);
      header.mainTextLabel.setText("Event Details");
      header.mainTextLabel.setFont(new Font("SansSerif", Font.BOLD, 48));
      header.todayButton.setVisible(false);
      header.clockLabel.setVisible(false);
      header.yearButton.setVisible(false);
      add(header, BorderLayout.NORTH);
      formPanel = new JPanel(new GridBagLayout());
      Font fieldFont = new Font("SansSerif", Font.PLAIN, 24);
      Font labelFont = new Font("SansSerif", Font.BOLD, 24);
      GridBagConstraints gbc = new GridBagConstraints();
      gbc.insets = new Insets(10, 10, 10, 10);
      gbc.fill = GridBagConstraints.HORIZONTAL;
      titleField.setFont(fieldFont);
      titleField.setHorizontalAlignment(SwingConstants.CENTER);
      locationField.setFont(fieldFont);
      locationField.setHorizontalAlignment(SwingConstants.CENTER);
      noteArea.setFont(fieldFont);
      noteArea.setLineWrap(true);
      noteArea.setWrapStyleWord(true);
      allDayCheckBox.setFont(labelFont);
      allDayCheckBox.setHorizontalAlignment(SwingConstants.RIGHT);
      dateButton.setFont(fieldFont);
      timePicker = new TimePickerPanel(LocalTime.now());
      allDayCheckBox.addActionListener(e -> timePicker.setVisible(!allDayCheckBox.isSelected()));
      dateButton.addActionListener(e -> toggleDatePicker());
      gbc.anchor = GridBagConstraints.EAST;
      gbc.weightx = 0;
      gbc.gridx = 0;
      gbc.gridy = 0;
      formPanel.add(new JLabel("Title:"), gbc);
      gbc.gridy = 1;
      formPanel.add(new JLabel("Date:"), gbc);
      gbc.gridy = 2;
      formPanel.add(new JLabel("Time:"), gbc);
      gbc.gridy = 3;
      formPanel.add(new JLabel("Location:"), gbc);
      gbc.gridy = 4;
      formPanel.add(new JLabel("Notes:"), gbc);
      gbc.anchor = GridBagConstraints.WEST;
      gbc.weightx = 1;
      gbc.gridx = 1;
      gbc.gridy = 0;
      formPanel.add(titleField, gbc);
      gbc.gridy = 1;
      JPanel dateWidgetContainer = new JPanel(new BorderLayout());
      dateButton.setHorizontalAlignment(SwingConstants.CENTER);
      dateWidgetContainer.add(dateButton, BorderLayout.NORTH);
      datePickerContainer = new JPanel(new BorderLayout());
      datePickerContainer.setOpaque(false);
      dateWidgetContainer.add(datePickerContainer, BorderLayout.CENTER);
      formPanel.add(dateWidgetContainer, gbc);
      gbc.gridy = 2;
      JPanel timePanel = new JPanel(new BorderLayout(20, 0));
      timePanel.add(timePicker, BorderLayout.CENTER);
      timePanel.add(allDayCheckBox, BorderLayout.EAST);
      formPanel.add(timePanel, gbc);
      gbc.gridy = 3;
      formPanel.add(locationField, gbc);
      gbc.gridy = 4;
      gbc.fill = GridBagConstraints.BOTH;
      gbc.weighty = 1;
      formPanel.add(new JScrollPane(noteArea), gbc);
      add(formPanel, BorderLayout.CENTER);
      JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 10));
      Font buttonFont = new Font("SansSerif", Font.BOLD, 24);
      JButton saveButton = new JButton("Save");
      saveButton.setFont(buttonFont);
      saveButton.setMargin(new Insets(10, 30, 10, 30));
      saveButton.addActionListener(e -> saveEvent());
      buttonPanel.add(saveButton);
      add(buttonPanel, BorderLayout.SOUTH);
    }

    private void toggleDatePicker() {
      if (slideTimer != null && slideTimer.isRunning()) return;
      isDatePickerVisible = !isDatePickerVisible;
      if (isDatePickerVisible) {
        Color eventColor =
            (currentEvent != null && currentEvent.color != null)
                ? currentEvent.color
                : new Color(2, 119, 189);
        datePickerPanel =
            new DatePickerPanel(
                selectedEventDate,
                eventColor,
                newDate -> {
                  this.selectedEventDate = newDate;
                  dateButton.setText(
                      newDate.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG)));
                  toggleDatePicker();
                });
        datePickerContainer.add(datePickerPanel, BorderLayout.CENTER);
      }
      animatePanel(isDatePickerVisible);
    }

    private void animatePanel(boolean show) {
      int startHeight = datePickerContainer.getHeight();
      int endHeight = show ? datePickerPanel.getPreferredSize().height : 0;
      if (startHeight == endHeight) return;
      int duration = 200;
      long startTime = System.currentTimeMillis();
      slideTimer =
          new javax.swing.Timer(
              10,
              e -> {
                long elapsed = System.currentTimeMillis() - startTime;
                float progress = Math.min(1f, (float) elapsed / duration);
                int newHeight = startHeight + (int) (progress * (endHeight - startHeight));
                datePickerContainer.setPreferredSize(
                    new Dimension(datePickerContainer.getWidth(), newHeight));
                formPanel.revalidate();
                if (progress >= 1f) {
                  ((javax.swing.Timer) e.getSource()).stop();
                  if (!show) {
                    datePickerContainer.removeAll();
                    datePickerContainer.revalidate();
                    datePickerContainer.repaint();
                  }
                }
              });
      slideTimer.start();
    }

    public void prepareEditor(Event event, LocalDateTime defaultTime) {
      currentEvent = event;
      if (event != null) {
        originalDate = event.dateTime.toLocalDate();
        selectedEventDate = event.dateTime.toLocalDate();
        selectedEventTime = event.dateTime.toLocalTime();
        titleField.setText(event.title);
        allDayCheckBox.setSelected(event.isAllDay);
        locationField.setText(event.location);
        noteArea.setText(event.note);
        timePicker.setTime(selectedEventTime);
        timePicker.setEnabled(!event.isAllDay);
      } else {
        originalDate = defaultTime.toLocalDate();
        selectedEventDate = defaultTime.toLocalDate();
        selectedEventTime = defaultTime.toLocalTime();
        titleField.setText("");
        allDayCheckBox.setSelected(false);
        locationField.setText("");
        noteArea.setText("");
        timePicker.setTime(selectedEventTime);
        timePicker.setEnabled(true);
      }
      dateButton.setText(
          selectedEventDate.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG)));
    }

    private void saveEvent() {
      selectedEventTime = timePicker.getTime();
      LocalDateTime combinedDateTime = LocalDateTime.of(selectedEventDate, selectedEventTime);
      if (currentEvent == null) {
        currentEvent =
            new Event(
                titleField.getText(),
                combinedDateTime,
                allDayCheckBox.isSelected(),
                locationField.getText(),
                noteArea.getText(),
                new Color(2, 119, 189));
      } else {
        currentEvent.title = titleField.getText();
        currentEvent.dateTime = combinedDateTime;
        currentEvent.isAllDay = allDayCheckBox.isSelected();
        currentEvent.location = locationField.getText();
        currentEvent.note = noteArea.getText();
      }
      onSave.accept(currentEvent);
    }
  }

  private static class TimePickerPanel extends JPanel {

    private LocalTime time;
    private final JButton hourLabel, minuteLabel, ampmLabel;
    private final Font pickerFont = new Font("SansSerif", Font.BOLD, 36);

    TimePickerPanel(LocalTime initialTime) {
      this.time = initialTime;
      setLayout(new GridBagLayout());
      GridBagConstraints gbc = new GridBagConstraints();
      hourLabel = new JButton(formatHour(time.getHour()));
      minuteLabel = new JButton(String.format("%02d", time.getMinute()));
      ampmLabel = new JButton(time.getHour() < 12 ? "AM" : "PM");
      setupButton(hourLabel);
      setupButton(minuteLabel);
      setupButton(ampmLabel);
      hourLabel.addActionListener(e -> showTimeDialog(true));
      minuteLabel.addActionListener(e -> showTimeDialog(false));
      ampmLabel.addActionListener(
          e -> {
            time = time.plusHours(12);
            updateLabels();
          });
      gbc.insets = new Insets(0, 5, 0, 5);
      gbc.fill = GridBagConstraints.BOTH;
      add(hourLabel, gbc);
      add(new JLabel(":"), gbc);
      add(minuteLabel, gbc);
      add(ampmLabel, gbc);
    }

    private void setupButton(JButton button) {
      button.setFont(pickerFont);
      button.setFocusPainted(false);
      button.setBorder(new EmptyBorder(5, 10, 5, 10));
      button.setContentAreaFilled(false);
    }

    private String formatHour(int hour) {
      int displayHour = hour % 12;
      return String.valueOf(displayHour == 0 ? 12 : displayHour);
    }

    private void updateLabels() {
      hourLabel.setText(formatHour(time.getHour()));
      minuteLabel.setText(String.format("%02d", time.getMinute()));
      ampmLabel.setText(time.getHour() < 12 ? "AM" : "PM");
    }

    public LocalTime getTime() {
      return time;
    }

    public void setTime(LocalTime time) {
      this.time = time;
      updateLabels();
    }

    @Override
    public void setEnabled(boolean enabled) {
      super.setEnabled(enabled);
      hourLabel.setEnabled(enabled);
      minuteLabel.setEnabled(enabled);
      ampmLabel.setEnabled(enabled);
    }

    private void showTimeDialog(boolean isHour) {
      JDialog dialog =
          new JDialog(
              (Frame) SwingUtilities.getWindowAncestor(this),
              "Select " + (isHour ? "Hour" : "Minute"),
              true);
      dialog.setLayout(new GridLayout(isHour ? 4 : 6, isHour ? 3 : 10, 5, 5));
      dialog.setUndecorated(true);
      dialog.getRootPane().setBorder(new LineBorder(Color.WHITE, 2));
      int max = isHour ? 12 : 59;
      for (int i = isHour ? 1 : 0; i <= max; i++) {
        final int val = i;
        JButton btn = new JButton(String.format(isHour ? "%d" : "%02d", i));
        btn.setFont(new Font("SansSerif", Font.BOLD, 20));
        btn.addActionListener(
            e -> {
              if (isHour) {
                int currentAmPm = time.getHour() < 12 ? 0 : 12;
                time = time.withHour(val % 12 + currentAmPm);
              } else {
                time = time.withMinute(val);
              }
              updateLabels();
              dialog.dispose();
            });
        dialog.add(btn);
      }
      dialog.pack();
      dialog.setLocationRelativeTo(isHour ? hourLabel : minuteLabel);
      dialog.setVisible(true);
    }
  }

  private static class DatePickerPanel extends JPanel {
    private final LocalDate selectedDate;
    private final JLabel monthYearLabel;
    private final JPanel daysGridPanel;
    private YearMonth currentYearMonth;
    private final Color accentColor;
    private final Consumer<LocalDate> onDateSelect;

    private DatePickerPanel(
        LocalDate initialDate, Color accentColor, Consumer<LocalDate> onDateSelect) {
      this.selectedDate = initialDate;
      this.currentYearMonth = YearMonth.from(initialDate);
      this.accentColor = accentColor != null ? accentColor : new Color(2, 119, 189);
      this.onDateSelect = onDateSelect;
      setLayout(new BorderLayout(5, 5));
      monthYearLabel = new JLabel("", SwingConstants.CENTER);
      monthYearLabel.setFont(new Font("SansSerif", Font.BOLD, 16));
      Dimension fixed = computeMaxMonthLabelSize(monthYearLabel, true);
      monthYearLabel.setPreferredSize(fixed);
      monthYearLabel.setMinimumSize(fixed);
      monthYearLabel.setMaximumSize(fixed);
      monthYearLabel.setBorder(new EmptyBorder(0, 0, 0, 25));
      daysGridPanel = new JPanel(new GridLayout(0, 7, 5, 5));
      JPanel monthNavPanel = new JPanel(new BorderLayout());
      JButton prev = new JButton("<");
      prev.addActionListener(e -> changeMonth(-1));
      JButton next = new JButton(">");
      next.addActionListener(e -> changeMonth(1));
      monthNavPanel.add(prev, BorderLayout.WEST);
      monthNavPanel.add(monthYearLabel, BorderLayout.CENTER);
      monthNavPanel.add(next, BorderLayout.EAST);
      add(monthNavPanel, BorderLayout.NORTH);
      add(daysGridPanel, BorderLayout.CENTER);
      updateCalendar();
    }

    private static Dimension computeMaxMonthLabelSize(JLabel label, boolean includeYear) {
      FontMetrics fm = label.getFontMetrics(label.getFont());
      int maxWidth = 0;
      for (java.time.Month m : java.time.Month.values()) {
        String name =
            m.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.getDefault())
                .toUpperCase(java.util.Locale.getDefault());
        if (includeYear) name += " 2099";
        maxWidth = Math.max(maxWidth, fm.stringWidth(name));
      }
      Insets insets =
          label.getBorder() != null
              ? label.getBorder().getBorderInsets(label)
              : new Insets(0, 0, 0, 0);
      return new Dimension(
          maxWidth + insets.left + insets.right + 20,
          fm.getHeight() + insets.top + insets.bottom + 10);
    }

    private void changeMonth(int amount) {
      currentYearMonth = currentYearMonth.plusMonths(amount);
      updateCalendar();
    }

    private void updateCalendar() {
      monthYearLabel.setText(currentYearMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy")));
      daysGridPanel.removeAll();
      String[] headers = {"S", "M", "T", "W", "T", "F", "S"};
      for (String h : headers) {
        JLabel headerLabel = new JLabel(h, SwingConstants.CENTER);
        headerLabel.setFont(new Font("SansSerif", Font.BOLD, 14));
        daysGridPanel.add(headerLabel);
      }
      LocalDate first = currentYearMonth.atDay(1);
      int firstDayOfWeek = first.getDayOfWeek().getValue() % 7;
      for (int i = 0; i < firstDayOfWeek; i++) daysGridPanel.add(new JLabel(""));
      for (int day = 1; day <= currentYearMonth.lengthOfMonth(); day++) {
        LocalDate date = currentYearMonth.atDay(day);
        DayButton btn = new DayButton(String.valueOf(day));
        btn.setSelected(date.equals(selectedDate));
        btn.setToday(date.equals(LocalDate.now()));
        btn.addActionListener(e -> onDateSelect.accept(date));
        daysGridPanel.add(btn);
      }
      revalidate();
      repaint();
    }

    private class DayButton extends JButton {
      private boolean isToday;
      private boolean isButtonSelected;

      DayButton(String text) {
        super(text);
        setContentAreaFilled(false);
        setBorder(null);
        setFont(new Font("SansSerif", Font.PLAIN, 14));
        setPreferredSize(new Dimension(40, 40));
      }

      public void setToday(boolean today) {
        this.isToday = today;
      }

      public void setSelected(boolean selected) {
        isButtonSelected = selected;
      }

      @Override
      protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int diameter = Math.min(getWidth(), getHeight()) - 8;
        int x = (getWidth() - diameter) / 2;
        int y = (getHeight() - diameter) / 2;
        if (isButtonSelected) {
          g2.setColor(accentColor);
          g2.fillOval(x, y, diameter, diameter);
          g2.setColor(Color.WHITE);
        } else if (isToday) {
          g2.setColor(accentColor);
          g2.setStroke(new BasicStroke(2));
          g2.drawOval(x, y, diameter, diameter);
          g2.setColor(UIManager.getColor("Button.foreground"));
        } else {
          g2.setColor(UIManager.getColor("Button.foreground"));
        }
        FontMetrics fm = g2.getFontMetrics();
        int stringX = (getWidth() - fm.stringWidth(getText())) / 2;
        int stringY = (getHeight() - fm.getHeight()) / 2 + fm.getAscent();
        g2.drawString(getText(), stringX, stringY);
        g2.dispose();
      }
    }
  }

  private static class YearPickerPanel extends JPanel {
    private int currentDecadeStart;
    private final int selectedYear;
    private final JLabel decadeLabel;
    private final JPanel yearsGridPanel;
    private final Consumer<Integer> onYearSelect;

    private YearPickerPanel(int initialYear, Consumer<Integer> onYearSelect) {
      this.selectedYear = initialYear;
      this.currentDecadeStart = (initialYear / 10) * 10;
      this.onYearSelect = onYearSelect;
      setLayout(new BorderLayout(20, 20));
      setBorder(new EmptyBorder(30, 30, 30, 30));
      setBackground(new Color(45, 45, 45));
      decadeLabel = new JLabel("", SwingConstants.CENTER);
      decadeLabel.setFont(new Font("SansSerif", Font.BOLD, 36));
      yearsGridPanel = new JPanel(new GridLayout(4, 3, 20, 20));
      yearsGridPanel.setOpaque(false);
      BorderLayout borderLayout = new BorderLayout();
      borderLayout.setHgap(15);
      JPanel navPanel = new JPanel(borderLayout);
      navPanel.setOpaque(false);
      JButton prevDecadeButton = new JButton(backButtonIcon);
      prevDecadeButton.setFont(new Font("SansSerif", Font.BOLD, 30));
      prevDecadeButton.addActionListener(e -> changeDecade(-10));
      addButtonInteractionEffects(prevDecadeButton);
      JButton nextDecadeButton = new JButton(forwardButtonIcon);
      nextDecadeButton.setFont(new Font("SansSerif", Font.BOLD, 30));
      nextDecadeButton.addActionListener(e -> changeDecade(10));
      addButtonInteractionEffects(nextDecadeButton);
      navPanel.add(prevDecadeButton, BorderLayout.WEST);
      navPanel.add(decadeLabel, BorderLayout.CENTER);
      navPanel.add(nextDecadeButton, BorderLayout.EAST);
      add(navPanel, BorderLayout.NORTH);
      add(yearsGridPanel, BorderLayout.CENTER);
      updateYearGrid();
    }

    private void changeDecade(int amount) {
      currentDecadeStart += amount;
      updateYearGrid();
    }

    private void updateYearGrid() {
      yearsGridPanel.removeAll();
      decadeLabel.setText(currentDecadeStart + " ⇆ " + (currentDecadeStart + 9));
      for (int i = 0; i < 12; i++) {
        int year = currentDecadeStart + i;
        PillButton yearButton = new PillButton(String.valueOf(year));
        yearButton.setFont(new Font("SansSerif", Font.BOLD, 28));
        yearButton.setPreferredSize(new Dimension(100, 60));
        if (year == selectedYear) yearButton.setSelected(true);
        yearButton.addActionListener(e -> onYearSelect.accept(year));
        yearsGridPanel.add(yearButton);
      }
      revalidate();
      repaint();
    }
  }

  private static class PillButton extends JButton {
    private boolean isHovered = false;
    private boolean isButtonSelected = false;
    private final Color selectedColor = new Color(2, 119, 189);
    private final Color hoverColor = new Color(85, 85, 85);
    private String yearPrefix, yearSuffix;
    private Font prefixFont, suffixFont;
    private Color prefixColor, suffixColor;

    public PillButton(String text) {
      super(text);
      setFont(new Font("SansSerif", Font.BOLD, 18));
      setFocusPainted(false);
      setBorderPainted(false);
      setContentAreaFilled(false);
      addMouseListener(
          new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
              isHovered = true;
              repaint();
            }

            @Override
            public void mouseExited(MouseEvent e) {
              isHovered = false;
              repaint();
            }
          });
    }

    public void setYearText(
        String year, Font prefixFont, Color prefixColor, Font suffixFont, Color suffixColor) {
      if (year != null && year.length() == 4) {
        this.yearPrefix = year.substring(0, 2);
        this.yearSuffix = year.substring(2);
        this.prefixFont = prefixFont;
        this.prefixColor = prefixColor;
        this.suffixFont = suffixFont;
        this.suffixColor = suffixColor;
        setText(year);
      } else {
        this.yearPrefix = null;
        this.yearSuffix = null;
        setText(year);
      }
      repaint();
    }

    public void setSelected(boolean selected) {
      this.isButtonSelected = selected;
      repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
      Graphics2D g2 = (Graphics2D) g.create();
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      int arc = getHeight();
      int inset = 3;
      if (isButtonSelected) {
        g2.setColor(selectedColor);
        g2.fill(
            new RoundRectangle2D.Double(
                inset, inset, getWidth() - (inset * 2), getHeight() - (inset * 2), arc, arc));
      } else if (isHovered) {
        g2.setColor(hoverColor);
        g2.fill(
            new RoundRectangle2D.Double(
                inset, inset, getWidth() - (inset * 2), getHeight() - (inset * 2), arc, arc));
      }
      if (yearPrefix != null && yearSuffix != null) {
        g2.setRenderingHint(
            RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        FontMetrics prefixFm = g2.getFontMetrics(prefixFont);
        FontMetrics suffixFm = g2.getFontMetrics(suffixFont);
        int prefixWidth = prefixFm.stringWidth(yearPrefix);
        int suffixWidth = suffixFm.stringWidth(yearSuffix);
        int totalWidth = prefixWidth + suffixWidth;
        int x = (getWidth() - totalWidth) / 2;
        int y = (getHeight() - prefixFm.getHeight()) / 2 + prefixFm.getAscent();
        g2.setFont(prefixFont);
        g2.setColor(isButtonSelected ? Color.WHITE : prefixColor);
        g2.drawString(yearPrefix, x, y);
        g2.setFont(suffixFont);
        g2.setColor(isButtonSelected ? Color.WHITE : suffixColor);
        g2.drawString(yearSuffix, x + prefixWidth, y);
      } else {
        g2.setColor(isButtonSelected ? Color.WHITE : UIManager.getColor("Button.foreground"));
        FontMetrics fm = g2.getFontMetrics(getFont());
        int stringX = (getWidth() - fm.stringWidth(getText())) / 2;
        int stringY = (getHeight() - fm.getHeight()) / 2 + fm.getAscent();
        g2.drawString(getText(), stringX, stringY);
      }
      g2.dispose();
    }
  }

  private static class AnimatedGlowIcon implements Icon {
    private final ImageIcon baseIcon;
    private String text;
    private boolean interactionEnabled = true;
    private boolean isHovered = false;
    private boolean isPressed = false;

    public AnimatedGlowIcon(ImageIcon baseIcon, int dayOfMonth) {
      this.baseIcon = baseIcon;
      this.text = String.valueOf(dayOfMonth);
    }

    public void setText(String text) {
      this.text = text;
    }

    public void setInteractionEnabled(boolean enabled) {
      this.interactionEnabled = enabled;
    }

    public void setHovered(boolean hovered) {
      this.isHovered = hovered;
    }

    public void setPressed(boolean pressed) {
      this.isPressed = pressed;
    }

    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {
      Graphics2D g2d = (Graphics2D) g.create();
      if (interactionEnabled) {
        double scale = 1.0;
        if (isPressed) scale = 0.9;
        else if (isHovered) scale = 1.1;
        int centerX = x + getIconWidth() / 2;
        int centerY = y + getIconHeight() / 2;
        g2d.translate(centerX, centerY);
        g2d.scale(scale, scale);
        g2d.translate(-centerX, -centerY);
      }
      g2d.setRenderingHint(
          RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
      baseIcon.paintIcon(c, g2d, x, y);
      g2d.setFont(new Font("SansSerif", Font.BOLD, 45));
      FontMetrics fm = g2d.getFontMetrics();
      int textWidth = fm.stringWidth(text);
      int textX = x + (getIconWidth() - textWidth) / 2;
      int textY = y + (getIconHeight() + fm.getAscent()) / 2 + 2;
      long time = System.currentTimeMillis();
      double glow = 0.5 * (1 + Math.sin(time * 2 * Math.PI / 2000.0));
      Color white = new Color(1.0f, 1.0f, 1.0f, Math.max(0, (float) glow));
      g2d.setColor(white);
      g2d.drawString(text, textX + 1, textY);
      g2d.drawString(text, textX - 1, textY);
      g2d.drawString(text, textX, textY + 1);
      g2d.drawString(text, textX, textY - 1);
      g2d.setColor(Color.WHITE);
      g2d.drawString(text, textX, textY);
      g2d.dispose();
    }

    @Override
    public int getIconWidth() {
      return baseIcon.getIconWidth();
    }

    @Override
    public int getIconHeight() {
      return baseIcon.getIconHeight();
    }
  }

  public static ImageIcon overlayNumberOnIcon(ImageIcon baseIcon, int dayOfMonth) {
    Image baseImage = baseIcon.getImage();

    BufferedImage newImage =
        new BufferedImage(
            baseIcon.getIconWidth(), baseIcon.getIconHeight(), BufferedImage.TYPE_INT_ARGB);
    Graphics2D g2d = newImage.createGraphics();
    g2d.drawImage(baseImage, 0, 0, null);
    g2d.setFont(new Font("SansSerif", Font.BOLD, 25));
    g2d.setColor(Color.WHITE);
    g2d.setRenderingHint(
        RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    String text = String.valueOf(dayOfMonth);
    FontMetrics fm = g2d.getFontMetrics();
    int textWidth = fm.stringWidth(text);
    int textHeight = fm.getAscent();
    int x = (baseIcon.getIconWidth() - textWidth) / 2;
    int y = (baseIcon.getIconHeight() + textHeight) / 2 + 5;
    g2d.drawString(text, x, y);
    g2d.dispose();

    return new ImageIcon(newImage);
  }

  private static class DimmingPane extends JComponent {

    private final Color dimColor = new Color(0, 0, 0, 0.5f);
    private JComponent childPanel;

    public DimmingPane() {
      setLayout(new GridBagLayout());
      setOpaque(false);
      addMouseListener(
          new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
              if (childPanel != null) {
                Point childPoint =
                    SwingUtilities.convertPoint(DimmingPane.this, e.getPoint(), childPanel);
                if (!childPanel.contains(childPoint)) hidePanel();
              }
            }
          });
    }

    public void showPanel(JComponent panel) {
      this.childPanel = panel;
      add(panel, new GridBagConstraints());
      setVisible(true);
      revalidate();
      repaint();
    }

    public void hidePanel() {
      if (childPanel != null) {
        remove(childPanel);
        childPanel = null;
        setVisible(false);
        revalidate();
        repaint();
      }
    }

    @Override
    protected void paintComponent(Graphics g) {
      g.setColor(dimColor);
      g.fillRect(0, 0, getWidth(), getHeight());
      super.paintComponent(g);
    }
  }

  public static void main(String[] args) {
    FlatDarkLaf.setup();
    SwingUtilities.invokeLater(TouchScreenCalendar::new);
  }
}
