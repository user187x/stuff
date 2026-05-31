package xxx.com.swing.overlay;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.TimerTask;

import static xxx.com.swing.overlay.FloatingActionButton.MAIN_BUTTON_SIZE;

/**
 * A utility to add a Draggable Floating Action Button (FAB) that floats independently over any screen.
 */
public class FloatingButton {

  /**
   * Sets up the Floating Action Button as an independent overlay that can be dragged across any monitor.
   */
  public static void setup() {
    SwingUtilities.invokeLater(() -> {
      // Create the Floating Action Button
      FloatingActionButton fab = new FloatingActionButton();

      // Add action items
      fab.addActionItem("Add Item", createIcon(IconType.ADD, fab.getScaleFactor()), e -> JOptionPane.showMessageDialog(null, "Add Action Triggered!"));
      fab.addActionItem("Edit Item", createIcon(IconType.EDIT, fab.getScaleFactor()), e -> JOptionPane.showMessageDialog(null, "Edit Action Triggered!"));
      fab.addActionItem("Delete Item", createIcon(IconType.DELETE, fab.getScaleFactor()), e -> JOptionPane.showMessageDialog(null, "Delete Action Triggered!"));

      // Create a transparent, always-on-top window
      JWindow window = new JWindow();
      // Check for per-pixel translucency support
      if (!GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().isWindowTranslucencySupported(GraphicsDevice.WindowTranslucency.PERPIXEL_TRANSLUCENT)) {
        System.err.println("Per-pixel translucency not supported; using opaque background as fallback.");
        window.setBackground(new Color(255, 0, 0, 50)); // Fallback to semi-transparent red
      } else {
        window.setBackground(new Color(0, 0, 0, 0)); // Fully transparent
      }
      window.setContentPane(fab);
      window.setAlwaysOnTop(true);
      window.setFocusable(true);
      window.addWindowFocusListener(new WindowFocusListener() {
        @Override
        public void windowGainedFocus(WindowEvent e) {}
        @Override
        public void windowLostFocus(WindowEvent e) {
          if (fab.isExpanded()) {
            fab.toggleMenu();
          }
        }
      });
      fab.setWindow(window);

      // Find the monitor with the highest resolution
      GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
      GraphicsDevice[] devices = ge.getScreenDevices();
      GraphicsDevice primaryDevice = ge.getDefaultScreenDevice();
      GraphicsDevice maxResDevice = primaryDevice;
      int maxResolution = 0;
      for (GraphicsDevice device : devices) {
        GraphicsConfiguration gc = device.getDefaultConfiguration();
        Rectangle bounds = gc.getBounds();
        int resolution = bounds.width * bounds.height;
        if (resolution > maxResolution) {
          maxResolution = resolution;
          maxResDevice = device;
        }
      }

      // Position on the highest-resolution monitor
      GraphicsConfiguration maxResGc = maxResDevice.getDefaultConfiguration();
      Rectangle maxResBounds = maxResGc.getBounds();
      int initX = maxResBounds.x + 100;
      int initY = maxResBounds.y + 100;
      window.setLocation(initX, initY);
      window.setSize((int)(MAIN_BUTTON_SIZE * fab.getScaleFactor()), (int)(MAIN_BUTTON_SIZE * fab.getScaleFactor()));

      // Pre-render without making visible
      int width = window.getWidth();
      int height = window.getHeight();
      BufferedImage bi = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
      Graphics2D g = bi.createGraphics();
      window.paint(g);
      g.dispose();

      window.setVisible(true);
      window.requestFocus();
      window.repaint();

      // Move to primary screen after a short delay
      java.util.Timer timer = new java.util.Timer();
      timer.schedule(new TimerTask() {
        @Override
        public void run() {
          SwingUtilities.invokeLater(() -> {
            GraphicsConfiguration primaryGc = primaryDevice.getDefaultConfiguration();
            Rectangle primaryBounds = primaryGc.getBounds();
            int primaryX = primaryBounds.x + 100;
            int primaryY = primaryBounds.y + 100;
            window.setLocation(primaryX, primaryY);
            fab.updateScaleFactor(primaryGc); // Update scale factor for primary screen
            fab.setWindow(window); // Update window position in FAB
            window.revalidate();
            window.repaint();
            System.out.println("Moved FAB to primary monitor at: (" + primaryX + ", " + primaryY + ")");
          });
        }
      }, 500); // Delay of 500ms to ensure visibility

      // Debug monitor information
      System.out.println("Highest-resolution monitor bounds: " + maxResBounds);
      System.out.println("FAB initially displayed at: (" + initX + ", " + initY + ")");
      System.out.println("Primary monitor bounds: " + primaryDevice.getDefaultConfiguration().getBounds());
      System.out.println("Scale factor: " + fab.getScaleFactor());
      for (int i = 0; i < devices.length; i++) {
        System.out.println("Monitor " + i + " bounds: " + devices[i].getDefaultConfiguration().getBounds());
      }
    });
  }

  public static void main(String[] args) {
    setup();
  }

  enum IconType { ADD, EDIT, DELETE }

  static Icon createIcon(IconType type, double scaleFactor) {
    return new Icon() {
      @Override
      public void paintIcon(Component c, Graphics g, int x, int y) {
        Graphics2D g2d = (Graphics2D) g.create();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2d.setStroke(new BasicStroke((float)(2f * scaleFactor)));
        g2d.setColor(Color.WHITE);
        int midX = x + getIconWidth() / 2;
        int midY = y + getIconHeight() / 2;
        switch (type) {
          case ADD:
            g2d.drawLine(midX - (int)(4 * scaleFactor), midY, midX + (int)(4 * scaleFactor), midY);
            g2d.drawLine(midX, midY - (int)(4 * scaleFactor), midX, midY + (int)(4 * scaleFactor));
            break;
          case EDIT:
            g2d.drawLine(x + (int)(4 * scaleFactor), y + (int)(14 * scaleFactor), x + (int)(8 * scaleFactor), y + (int)(18 * scaleFactor));
            g2d.drawLine(x + (int)(8 * scaleFactor), y + (int)(18 * scaleFactor), x + (int)(14 * scaleFactor), y + (int)(12 * scaleFactor));
            g2d.draw(new Line2D.Double(x + (4.5 * scaleFactor), y + (13.5 * scaleFactor), x + (6 * scaleFactor), y + (12 * scaleFactor)));
            break;
          case DELETE:
            g2d.drawLine(midX - (int)(4 * scaleFactor), midY - (int)(4 * scaleFactor), midX + (int)(4 * scaleFactor), midY + (int)(4 * scaleFactor));
            g2d.drawLine(midX - (int)(4 * scaleFactor), midY + (int)(4 * scaleFactor), midX + (int)(4 * scaleFactor), midY - (int)(4 * scaleFactor));
            break;
        }
        g2d.dispose();
      }
      @Override
      public int getIconWidth() { return (int)(24 * scaleFactor); }
      @Override
      public int getIconHeight() { return (int)(24 * scaleFactor); }
    };
  }
}

interface Alphable {
  void setAlpha(float alpha);
}

 class FloatingActionButton extends JComponent {
  static final int MAIN_BUTTON_SIZE = 60;
  static final int ACTION_ITEM_SIZE = 45;
  static final int GAP = 15;
  static final int MARGIN = 20;
  static final int ANIMATION_DURATION = 200;
  static final int ANIMATION_STEP = 10;

  private final CircularButton mainButton;
  private final List<ActionItem> actionItems = new ArrayList<>();
  private final Timer animationTimer;
  private final Timer tuckAnimationTimer;
  private boolean expanded = false;
  private double animationProgress = 0.0;
  private boolean isTucked = false;
  private int preTuckX;
  private double tuckAnimationProgress = 0.0;
  private int tuckStartX, tuckTargetX;
  private Point startDrag;
  private JWindow window;
  private int mainButtonScreenX;
  private int mainButtonScreenY;
  private int yDirection;
  private int finalMinX;
  private int finalMinY;
  private int finalMaxX;
  private int finalMaxY;
  private double scaleFactor = 1.0;
  private GraphicsConfiguration lastGraphicsConfig;
  private boolean wasDragged = false;
  private boolean potentialMenu = false;

  public FloatingActionButton() {
    setLayout(null);
    setOpaque(false);
    // Determine initial scale factor and graphics configuration
    GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
    GraphicsDevice device = ge.getDefaultScreenDevice();
    GraphicsConfiguration gc = device.getDefaultConfiguration();
    AffineTransform transform = gc.getDefaultTransform();
    scaleFactor = Math.max(transform.getScaleX(), transform.getScaleY());
    lastGraphicsConfig = gc;

    mainButton = new CircularButton((int)(MAIN_BUTTON_SIZE * scaleFactor), new Color(0, 102, 204), scaleFactor);
    mainButton.addActionListener(e -> toggleMenu());
    add(mainButton);

    MouseAdapter dragHandler = new MouseAdapter() {
      @Override
      public void mousePressed(MouseEvent e) {
        if (SwingUtilities.isRightMouseButton(e)) {
          potentialMenu = true;
          if (e.getClickCount() >= 2) {
            toggleTuckAway();
            potentialMenu = false;
          } else if (!isTucked) {
            startDrag = e.getLocationOnScreen();
            wasDragged = false;
          }
        }
      }
      @Override
      public void mouseReleased(MouseEvent e) {
        if (SwingUtilities.isRightMouseButton(e) && potentialMenu && !wasDragged) {
          potentialMenu = false;
          RoundedPopup popup = new RoundedPopup();
          Point p = MouseInfo.getPointerInfo().getLocation();
          popup.setLocation(p.x, p.y);
          popup.setVisible(true);
        }
        startDrag = null;
      }
      @Override
      public void mouseDragged(MouseEvent e) {
        if (startDrag != null && SwingUtilities.isRightMouseButton(e)) {
          wasDragged = true;
          potentialMenu = false;
          int deltaX = e.getXOnScreen() - startDrag.x;
          int deltaY = e.getYOnScreen() - startDrag.y;
          window.setLocation(window.getX() + deltaX, window.getY() + deltaY);
          mainButtonScreenX = window.getX();
          mainButtonScreenY = window.getY();
          startDrag = e.getLocationOnScreen();
        }
      }
    };
    mainButton.addMouseListener(dragHandler);
    mainButton.addMouseMotionListener(dragHandler);

    animationTimer = new Timer(ANIMATION_STEP, e -> animateStep());
    tuckAnimationTimer = new Timer(ANIMATION_STEP, e -> animateTuckStep());

    mainButton.setBounds(0, 0, (int)(MAIN_BUTTON_SIZE * scaleFactor), (int)(MAIN_BUTTON_SIZE * scaleFactor));
  }

  public double getScaleFactor() {
    return scaleFactor;
  }

  public void setWindow(JWindow window) {
    this.window = window;
    mainButtonScreenX = window.getX();
    mainButtonScreenY = window.getY();
    lastGraphicsConfig = window.getGraphicsConfiguration();
    updateScaleFactor(lastGraphicsConfig); // Ensure scale factor matches the new window's monitor
  }

  void updateScaleFactor(GraphicsConfiguration gc) {
    AffineTransform transform = gc.getDefaultTransform();
    double newScaleFactor = Math.max(transform.getScaleX(), transform.getScaleY());
    if (newScaleFactor != scaleFactor) {
      scaleFactor = newScaleFactor;
      System.out.println("Monitor changed, new scale factor: " + scaleFactor);

      // Update main button
      remove(mainButton);
      mainButton.setPreferredSize(new Dimension((int)(MAIN_BUTTON_SIZE * scaleFactor), (int)(MAIN_BUTTON_SIZE * scaleFactor)));
      mainButton.updateScaleFactor(scaleFactor);
      add(mainButton);

      // Update action items
      for (ActionItem item : actionItems) {
        JComponent buttonWrapper = item.getButtonWrapper();
        TransparentLabel label = item.getLabel();
        remove(buttonWrapper);
        remove(label);
        if (buttonWrapper instanceof CircularButton) {
          ((CircularButton) buttonWrapper).setPreferredSize(new Dimension((int)(ACTION_ITEM_SIZE * scaleFactor), (int)(ACTION_ITEM_SIZE * scaleFactor)));
          ((CircularButton) buttonWrapper).updateScaleFactor(scaleFactor);
          // Update icon with new scale factor
          FloatingButton.IconType iconType = switch (label.getText()) {
            case "Add Item" -> FloatingButton.IconType.ADD;
            case "Edit Item" -> FloatingButton.IconType.EDIT;
            case "Delete Item" -> FloatingButton.IconType.DELETE;
            default -> null;
          };
          if (iconType != null) {
            ((CircularButton) buttonWrapper).setIcon(FloatingButton.createIcon(iconType, scaleFactor));
          }
        } else if (buttonWrapper instanceof JLayeredPane) {
          // Handle Delete Item (no FireAnimationOverlay)
          for (Component comp : ((JLayeredPane) buttonWrapper).getComponents()) {
            if (comp instanceof CircularButton) {
              ((CircularButton) comp).setPreferredSize(new Dimension((int)(ACTION_ITEM_SIZE * scaleFactor), (int)(ACTION_ITEM_SIZE * scaleFactor)));
              ((CircularButton) comp).updateScaleFactor(scaleFactor);
              FloatingButton.IconType iconType = FloatingButton.IconType.DELETE;
              ((CircularButton) comp).setIcon(FloatingButton.createIcon(iconType, scaleFactor));
            }
          }
          buttonWrapper.setPreferredSize(new Dimension((int)(ACTION_ITEM_SIZE * scaleFactor), (int)(ACTION_ITEM_SIZE * scaleFactor)));
        }
        label.setFont(new Font("SansSerif", Font.BOLD, (int)(12 * scaleFactor)));
        label.setBorder(BorderFactory.createEmptyBorder((int)(5 * scaleFactor), (int)(8 * scaleFactor), (int)(5 * scaleFactor), (int)(8 * scaleFactor)));
        add(buttonWrapper);
        add(label);
      }

      // Update window size and trigger layout
      if (!expanded) {
        window.setSize((int)(MAIN_BUTTON_SIZE * scaleFactor), (int)(MAIN_BUTTON_SIZE * scaleFactor));
      }
      layoutComponents();
      revalidate();
      window.repaint();
    }
  }

  public boolean isExpanded() {
    return expanded;
  }

  public void addActionItem(String tooltip, Icon icon, ActionListener action) {
    CircularButton button = new CircularButton((int)(ACTION_ITEM_SIZE * scaleFactor), new Color(51, 153, 255), scaleFactor);
    button.setIcon(icon);
    button.addActionListener(e -> {
      action.actionPerformed(e);
      toggleMenu();
    });
    TransparentLabel label = new TransparentLabel(tooltip);
    label.setFont(new Font("SansSerif", Font.BOLD, (int)(12 * scaleFactor)));
    label.setForeground(Color.WHITE);
    label.setBackground(new Color(100, 100, 100));
    label.setOpaque(true);
    label.setBorder(BorderFactory.createEmptyBorder((int)(5 * scaleFactor), (int)(8 * scaleFactor), (int)(5 * scaleFactor), (int)(8 * scaleFactor)));
    JComponent buttonComponent = button;
    if ("Delete Item".equals(tooltip)) {
      // Removed FireAnimationOverlay
      buttonComponent = button;
    }
    buttonComponent.setVisible(false);
    label.setVisible(false);
    ActionItem item = new ActionItem(buttonComponent, label);
    actionItems.add(item);
    add(buttonComponent);
    add(label);
  }

  void toggleMenu() {
    if (isTucked) return;
    expanded = !expanded;
    if (expanded) {
      mainButtonScreenX = window.getX();
      mainButtonScreenY = window.getY();
      Rectangle screenBounds = window.getGraphicsConfiguration().getBounds();
      int requiredHeight = (int)((actionItems.size() * (ACTION_ITEM_SIZE + GAP) * scaleFactor) + (MARGIN * scaleFactor));
      // Correct logic to determine menu expansion direction
      int distanceToBottom = screenBounds.y + screenBounds.height - (mainButtonScreenY + (int)(MAIN_BUTTON_SIZE * scaleFactor));
      yDirection = (distanceToBottom < requiredHeight) ? -1 : 1; // Prefer below unless not enough space
      for (ActionItem item : actionItems) {
        item.getButtonWrapper().setVisible(true);
        item.getLabel().setVisible(true);
      }
      finalMinX = 0;
      finalMinY = 0;
      finalMaxX = (int)(MAIN_BUTTON_SIZE * scaleFactor);
      finalMaxY = (int)(MAIN_BUTTON_SIZE * scaleFactor);
      for (int i = 0; i < actionItems.size(); i++) {
        ActionItem item = actionItems.get(i);
        int itemX = (int)((MAIN_BUTTON_SIZE - ACTION_ITEM_SIZE) / 2 * scaleFactor);
        int targetY = (yDirection == -1) ? (int)(-(i + 1) * (ACTION_ITEM_SIZE + GAP) * scaleFactor) : (int)((MAIN_BUTTON_SIZE + GAP + i * (ACTION_ITEM_SIZE + GAP)) * scaleFactor);
        int labelWidth = item.getLabel().getPreferredSize().width;
        boolean displayOnRight = (mainButtonScreenX < labelWidth + (int)(GAP * scaleFactor));
        int labelX = displayOnRight ? (int)(itemX + (ACTION_ITEM_SIZE * scaleFactor) + (10 * scaleFactor)) : (int)(itemX - labelWidth - (10 * scaleFactor));
        finalMinX = Math.min(finalMinX, itemX);
        finalMinX = Math.min(finalMinX, labelX);
        finalMinY = Math.min(finalMinY, targetY);
        finalMaxX = Math.max(finalMaxX, (int)(itemX + (ACTION_ITEM_SIZE * scaleFactor)));
        finalMaxX = Math.max(finalMaxX, labelX + labelWidth);
        finalMaxY = Math.max(finalMaxY, (int)(targetY + (ACTION_ITEM_SIZE * scaleFactor)));
      }
      int f_windowWidth = finalMaxX - finalMinX;
      int f_windowHeight = finalMaxY - finalMinY;
      int f_windowX = mainButtonScreenX - (-finalMinX);
      int f_windowY = mainButtonScreenY - (-finalMinY);
      window.setBounds(f_windowX, f_windowY, f_windowWidth, f_windowHeight);
      window.requestFocus();
    }
    animationTimer.start();
  }

  private void toggleTuckAway() {
    if (expanded) return;
    isTucked = !isTucked;
    tuckAnimationProgress = 0.0;
    tuckStartX = window.getX();
    Rectangle screenBounds = window.getGraphicsConfiguration().getBounds();
    int midPoint = screenBounds.x + screenBounds.width / 2;
    if (isTucked) {
      preTuckX = tuckStartX;
      tuckTargetX = (tuckStartX < midPoint) ? (int)(screenBounds.x - (MAIN_BUTTON_SIZE * scaleFactor) / 2) : (int)(screenBounds.x + screenBounds.width - (MAIN_BUTTON_SIZE * scaleFactor) / 2);
    } else {
      tuckTargetX = preTuckX;
    }
    tuckAnimationTimer.start();
  }

  private void animateStep() {
    double step = (double) ANIMATION_STEP / ANIMATION_DURATION;
    animationProgress += expanded ? step : -step;
    animationProgress = Math.max(0.0, Math.min(1.0, animationProgress));
    mainButton.setRotation(easeInOut(animationProgress) * Math.PI / 4.0);
    layoutComponents();
    if (animationProgress <= 0.0 || animationProgress >= 1.0) {
      animationTimer.stop();
      if (!expanded) {
        for (ActionItem item : actionItems) {
          item.getButtonWrapper().setVisible(false);
          item.getLabel().setVisible(false);
        }
        window.setBounds(mainButtonScreenX, mainButtonScreenY, (int)(MAIN_BUTTON_SIZE * scaleFactor), (int)(MAIN_BUTTON_SIZE * scaleFactor));
        mainButton.setBounds(0, 0, (int)(MAIN_BUTTON_SIZE * scaleFactor), (int)(MAIN_BUTTON_SIZE * scaleFactor));
      }
    }
    window.repaint();
  }

  private void animateTuckStep() {
    double step = (double) ANIMATION_STEP / ANIMATION_DURATION;
    tuckAnimationProgress += step;
    tuckAnimationProgress = Math.min(1.0, tuckAnimationProgress);
    int currentX = (int) (tuckStartX + (tuckTargetX - tuckStartX) * easeInOut(tuckAnimationProgress));
    window.setLocation(currentX, window.getY());
    mainButtonScreenX = currentX;
    if (tuckAnimationProgress >= 1.0) tuckAnimationTimer.stop();
  }

  private double easeInOut(double t) {
    return t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2;
  }

  private void layoutComponents() {
    if (!animationTimer.isRunning() && !expanded) {
      window.setSize((int)(MAIN_BUTTON_SIZE * scaleFactor), (int)(MAIN_BUTTON_SIZE * scaleFactor));
      window.setLocation(mainButtonScreenX, mainButtonScreenY);
      mainButton.setBounds(0, 0, (int)(MAIN_BUTTON_SIZE * scaleFactor), (int)(MAIN_BUTTON_SIZE * scaleFactor));
      return;
    }
    double easedProgress = easeInOut(animationProgress);
    int baseButtonX = 0;
    int baseButtonY = 0;
    mainButton.setBounds(baseButtonX - finalMinX, baseButtonY - finalMinY, (int)(MAIN_BUTTON_SIZE * scaleFactor), (int)(MAIN_BUTTON_SIZE * scaleFactor));
    for (int i = 0; i < actionItems.size(); i++) {
      ActionItem item = actionItems.get(i);
      int itemX = (int)((MAIN_BUTTON_SIZE - ACTION_ITEM_SIZE) / 2 * scaleFactor);
      int targetY = (yDirection == -1)
          ? (int)(-(i + 1) * (ACTION_ITEM_SIZE + GAP) * scaleFactor)
          : (int)((MAIN_BUTTON_SIZE + GAP + i * (ACTION_ITEM_SIZE + GAP)) * scaleFactor);
      int currentY = (int) (baseButtonY + (targetY - baseButtonY) * easedProgress);
      int labelWidth = item.getLabel().getPreferredSize().width;
      int labelHeight = item.getLabel().getPreferredSize().height;
      boolean displayOnRight = (mainButtonScreenX + baseButtonX < labelWidth + (int)(GAP * scaleFactor));
      int labelX = displayOnRight ? (int)(itemX + (ACTION_ITEM_SIZE * scaleFactor) + (10 * scaleFactor)) : (int)(itemX - labelWidth - (10 * scaleFactor));
      item.getButtonWrapper().setBounds(itemX - finalMinX, currentY - finalMinY, (int)(ACTION_ITEM_SIZE * scaleFactor), (int)(ACTION_ITEM_SIZE * scaleFactor));
      item.getLabel().setBounds(labelX - finalMinX, currentY + ((int)(ACTION_ITEM_SIZE * scaleFactor) - labelHeight) / 2 - finalMinY, labelWidth, labelHeight);
      item.setAlpha((float) easedProgress);
    }
  }

  @Override
  public boolean isOpaque() {
    return false;
  }

  private static class ActionItem {
    private final JComponent buttonWrapper;
    private final TransparentLabel label;
    private final List<Alphable> transparentComponents = new ArrayList<>();

    public ActionItem(JComponent b, TransparentLabel l) {
      this.buttonWrapper = b;
      this.label = l;
      synchronized (transparentComponents) {
        this.transparentComponents.add(l);
        if (b instanceof Alphable) transparentComponents.add((Alphable) b);
        if (b != null) {
          for (Component c : ((Container) b).getComponents()) {
            if (c instanceof Alphable) transparentComponents.add((Alphable) c);
          }
        }
      }
    }

    public void setAlpha(float alpha) {
      synchronized (transparentComponents) {
        for (Alphable comp : transparentComponents) comp.setAlpha(alpha);
      }
    }

    public JComponent getButtonWrapper() {
      return buttonWrapper;
    }

    public TransparentLabel getLabel() {
      return label;
    }
  }
}

class CircularButton extends JButton implements Alphable {
  private final Color backgroundColor;
  private double rotation = 0.0;
  private float alpha = 1.0f;
  private double scaleFactor;
  private Image fabImage;

  public CircularButton(int size, Color color, double scaleFactor) {
    this.backgroundColor = color;
    this.scaleFactor = scaleFactor;
    this.fabImage = new ImageIcon(getClass().getResource("/png/fab1.png")).getImage();
    setIcon(new ImageIcon(getClass().getResource("/gif/activity.png")));
    setPreferredSize(new Dimension((int)(size * scaleFactor), (int)(size * scaleFactor)));
    setContentAreaFilled(false);
    setBorderPainted(false);
    setFocusPainted(false);
    setOpaque(false);
  }

  public void updateScaleFactor(double newScaleFactor) {
    this.scaleFactor = newScaleFactor;
    setPreferredSize(new Dimension((int)(MAIN_BUTTON_SIZE * scaleFactor), (int)(MAIN_BUTTON_SIZE * scaleFactor)));
    repaint();
  }

  public void setRotation(double r) {
    this.rotation = r;
    repaint();
  }

  @Override
  public void setAlpha(float a) {
    this.alpha = Math.max(0.0f, Math.min(1.0f, a));
    repaint();
  }

  @Override
  protected void paintComponent(Graphics g) {
    Graphics2D g2d = (Graphics2D) g.create();
    g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
    g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
    g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
    int size = Math.min(getWidth(), getHeight());
    if (getModel().isArmed()) g2d.setColor(backgroundColor.darker());
    else if (getModel().isRollover()) g2d.setColor(backgroundColor.brighter());
    else g2d.setColor(backgroundColor);
    g2d.fill(new Ellipse2D.Double(0, 0, size - 1, size - 1));
    if (getIcon() != null) {
      getIcon().paintIcon(this, g2d, (getWidth() - getIcon().getIconWidth()) / 2, (getHeight() - getIcon().getIconHeight()) / 2);
    }
    int fabSize = (int)(24 * scaleFactor); // assuming base size 24 for fab1.png
    g2d.drawImage(fabImage, (getWidth() - fabSize)/2, (getHeight() - fabSize)/2, fabSize, fabSize, null);
    g2d.dispose();
  }

  @Override
  protected void paintBorder(Graphics g) {}

  @Override
  public boolean contains(int x, int y) {
    return new Ellipse2D.Float(0, 0, getWidth(), getHeight()).contains(x, y);
  }
}

class TransparentLabel extends JLabel implements Alphable {
  private float alpha = 1.0f;

  public TransparentLabel(String text) {
    super(text);
  }

  @Override
  public void setAlpha(float a) {
    this.alpha = Math.max(0.0f, Math.min(1.0f, a));
    repaint();
  }

  @Override
  public void paintComponent(Graphics g) {
    Graphics2D g2d = (Graphics2D) g.create();
    g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
    g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
    g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
    super.paintComponent(g2d);
    g2d.dispose();
  }
}

class RoundedPopup extends JWindow {
  public RoundedPopup() {
    setBackground(new Color(0, 0, 0, 0));
    JPanel panel = new JPanel() {
      @Override
      protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(new Color(100, 100, 100, 180));
        g2.fillRoundRect(0, 0, getWidth(), getHeight(), 20, 20);
        g2.dispose();
      }
    };
    panel.setOpaque(false);
    panel.setLayout(new FlowLayout());
    JLabel exit = new JLabel("Exit");
    exit.setForeground(Color.WHITE);
    exit.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        System.exit(0);
      }
    });
    panel.add(exit);
    setContentPane(panel);
    pack();
    addWindowFocusListener(new WindowFocusListener() {
      @Override
      public void windowGainedFocus(WindowEvent e) {}
      @Override
      public void windowLostFocus(WindowEvent e) {
        dispose();
      }
    });
  }
}
