package xxx.com.swing.fab;

import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * A utility to add a Draggable Floating Action Button (FAB) to a Java Swing application.
 */
public class FloatingButton {

  /**
   * Installs a Floating Action Button as a true overlay on the given JFrame
   * using the glass pane. This method will not affect the layout of any other
   * components in the frame.
   *
   * @param frame The JFrame to which the FAB will be added.
   */
  public static void setup(JFrame frame) {
    // Create the Floating Action Button
    FloatingActionButton fab = new FloatingActionButton();

    // Add action items that will appear when the FAB is expanded.
    // You can customize these for your application.
    fab.addActionItem("Add Item", createIcon(IconType.ADD), e -> JOptionPane.showMessageDialog(frame, "Add Action Triggered!"));
    fab.addActionItem("Edit Item", createIcon(IconType.EDIT), e -> JOptionPane.showMessageDialog(frame, "Edit Action Triggered!"));
    fab.addActionItem("Delete Item", createIcon(IconType.DELETE), e -> JOptionPane.showMessageDialog(frame, "Delete Action Triggered!"));

    // Set the FAB to be the frame's glass pane.
    // The FAB is a JComponent, so this is a valid operation.
    frame.setGlassPane(fab);

    // Make the glass pane (our FAB) visible.
    fab.setVisible(true);
  }

  // Example of how to use the setup method in a standalone demo
  public static void main(String[] args) {
    // Run the Swing application on the Event Dispatch Thread for thread safety.
    SwingUtilities.invokeLater(() -> {
      JFrame frame = new JFrame("Floating Action Button Demo");
      frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
      frame.setSize(500, 600);
      frame.setLocationRelativeTo(null); // Center the frame

      // Add some background content. The FAB will not interfere with this layout.
      JTextArea backgroundContent = getJTextArea();
      frame.add(new JScrollPane(backgroundContent));

      // Now, setup the FAB on the frame. It's a single, non-intrusive call.
      setup(frame);

      frame.setVisible(true);
    });
  }

  private static @NotNull JTextArea getJTextArea() {
    JTextArea backgroundContent = new JTextArea();
    backgroundContent.setText("This is the main content area.\n\n" +
        "The FAB is now on the glass pane, so it doesn't affect the layout of this text area or any other component.\n\n" +
        "- Right-click and drag to move the button.\n" +
        "- Menu expands down if too high.\n" +
        "- Menu labels display on the right if too far left.\n" +
        "- Double-right-click to tuck the button away.");
    backgroundContent.setFont(new Font("Inter", Font.PLAIN, 16));
    backgroundContent.setEditable(false);
    backgroundContent.setLineWrap(true);
    backgroundContent.setWrapStyleWord(true);
    backgroundContent.setMargin(new Insets(20, 20, 20, 20));
    return backgroundContent;
  }

  // Enum to define different icon types for sub-actions
  private enum IconType { ADD, EDIT, DELETE }

  /**
   * A helper method to create simple icons programmatically.
   * @param type The type of icon to create.
   * @return An Icon object.
   */
  private static Icon createIcon(IconType type) {
    return new Icon() {
      @Override
      public void paintIcon(Component c, Graphics g, int x, int y) {
        Graphics2D g2d = (Graphics2D) g.create();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setStroke(new BasicStroke(2f));
        g2d.setColor(Color.WHITE);
        int midX = x + getIconWidth() / 2;
        int midY = y + getIconHeight() / 2;
        switch (type) {
          case ADD: g2d.drawLine(midX - 4, midY, midX + 4, midY); g2d.drawLine(midX, midY - 4, midX, midY + 4); break;
          case EDIT: g2d.drawLine(x + 4, y + 14, x + 8, y + 18); g2d.drawLine(x + 8, y + 18, x + 14, y + 12); g2d.draw(new Line2D.Double(x + 4.5, y + 13.5, x + 6, y + 12)); break;
          case DELETE: g2d.drawLine(midX - 4, midY - 4, midX + 4, midY + 4); g2d.drawLine(midX - 4, midY + 4, midX + 4, midY - 4); break;
        }
        g2d.dispose();
      }
      @Override public int getIconWidth() { return 24; }
      @Override public int getIconHeight() { return 24; }
    };
  }
}

/** An interface for components that can have their transparency adjusted. */
interface Alphable {
  void setAlpha(float alpha);
}

/**
 * The main Floating Action Button component. It is draggable, has smart expansion,
 * and can be tucked away with a double-right-click. This component is designed
 * to be used as a glass pane.
 */
class FloatingActionButton extends JComponent {
  private static final int MAIN_BUTTON_SIZE = 60;
  private static final int ACTION_ITEM_SIZE = 45;
  private static final int GAP = 15;
  private static final int MARGIN = 20;
  private static final int ANIMATION_DURATION = 200;
  private static final int ANIMATION_STEP = 10;

  private final CircularButton mainButton;
  private final List<ActionItem> actionItems = new ArrayList<>();
  private final JPanel overlay;
  private final Timer animationTimer;
  private final Timer tuckAnimationTimer;
  private boolean expanded = false;
  private double animationProgress = 0.0;
  private int mainButtonX, mainButtonY;
  private Point dragOffset;
  private boolean hasBeenPlaced = false;
  private boolean isTucked = false;
  private int preTuckX;
  private double tuckAnimationProgress = 0.0;
  private int tuckStartX, tuckTargetX;

  public FloatingActionButton() {
    setLayout(null);
    setOpaque(false);
    overlay = new JPanel();
    overlay.setBackground(new Color(0, 0, 0, 0));
    overlay.setOpaque(false);
    overlay.setVisible(false);
    overlay.addMouseListener(new MouseAdapter() { @Override public void mouseClicked(MouseEvent e) { toggleMenu(); } });
    add(overlay);
    mainButton = new CircularButton(MAIN_BUTTON_SIZE, new Color(0, 102, 204));
    mainButton.addActionListener(e -> toggleMenu());
    add(mainButton);
    MouseAdapter dragHandler = new MouseAdapter() {
      @Override public void mousePressed(MouseEvent e) {
        if (SwingUtilities.isRightMouseButton(e)) {
          if (e.getClickCount() >= 2) { toggleTuckAway(); }
          else if (!isTucked) { dragOffset = e.getPoint(); }
        }
      }
      @Override public void mouseReleased(MouseEvent e) { dragOffset = null; }
      @Override public void mouseDragged(MouseEvent e) {
        if (dragOffset != null && !isTucked && SwingUtilities.isRightMouseButton(e)) {
          Point newPoint = SwingUtilities.convertPoint(mainButton, e.getPoint(), FloatingActionButton.this);
          mainButtonX = newPoint.x - dragOffset.x;
          mainButtonY = newPoint.y - dragOffset.y;
          mainButtonX = Math.max(MARGIN, Math.min(getWidth() - MAIN_BUTTON_SIZE - MARGIN, mainButtonX));
          mainButtonY = Math.max(MARGIN, Math.min(getHeight() - MAIN_BUTTON_SIZE - MARGIN, mainButtonY));
          layoutComponents();
        }
      }
    };
    mainButton.addMouseListener(dragHandler);
    mainButton.addMouseMotionListener(dragHandler);
    addComponentListener(new ComponentAdapter() {
      @Override public void componentResized(ComponentEvent e) {
        if (!hasBeenPlaced) {
          mainButtonX = getWidth() - MAIN_BUTTON_SIZE - MARGIN;
          mainButtonY = getHeight() - MAIN_BUTTON_SIZE - MARGIN;
          hasBeenPlaced = true;
        } else {
          mainButtonX = Math.max(MARGIN, Math.min(getWidth() - MAIN_BUTTON_SIZE - MARGIN, mainButtonX));
          mainButtonY = Math.max(MARGIN, Math.min(getHeight() - MAIN_BUTTON_SIZE - MARGIN, mainButtonY));
        }
        if (isTucked) {
          int midPoint = getWidth() / 2;
          mainButtonX = (preTuckX < midPoint) ? -MAIN_BUTTON_SIZE / 2 : getWidth() - MAIN_BUTTON_SIZE / 2;
        }
        layoutComponents();
      }
    });
    animationTimer = new Timer(ANIMATION_STEP, e -> animateStep());
    tuckAnimationTimer = new Timer(ANIMATION_STEP, e -> animateTuckStep());
  }

  public void addActionItem(String tooltip, Icon icon, ActionListener action) {
    CircularButton button = new CircularButton(ACTION_ITEM_SIZE, new Color(51, 153, 255));
    button.setIcon(icon);
    button.addActionListener(e -> { action.actionPerformed(e); toggleMenu(); });
    TransparentLabel label = new TransparentLabel(tooltip);
    label.setFont(new Font("Inter", Font.BOLD, 12));
    label.setForeground(Color.WHITE);
    label.setBackground(new Color(100, 100, 100));
    label.setOpaque(true);
    label.setBorder(BorderFactory.createEmptyBorder(5, 8, 5, 8));
    JComponent buttonComponent = button;
    if ("Delete Item".equals(tooltip)) {
      FireAnimationOverlay fireEffect = new FireAnimationOverlay();
      JLayeredPane layeredButton = new JLayeredPane();
      layeredButton.setPreferredSize(new Dimension(ACTION_ITEM_SIZE, ACTION_ITEM_SIZE));
      button.setBounds(0, 0, ACTION_ITEM_SIZE, ACTION_ITEM_SIZE);
      fireEffect.setBounds(0, 0, ACTION_ITEM_SIZE, ACTION_ITEM_SIZE);
      layeredButton.add(button, JLayeredPane.DEFAULT_LAYER);
      layeredButton.add(fireEffect, JLayeredPane.PALETTE_LAYER);
      buttonComponent = layeredButton;
    }
    buttonComponent.setVisible(false);
    label.setVisible(false);
    ActionItem item = new ActionItem(buttonComponent, label);
    actionItems.add(item);
    add(buttonComponent);
    add(label);
  }

  private void toggleMenu() {
    if (isTucked) return;
    expanded = !expanded;
    if (expanded) {
      overlay.setVisible(true);
      for (ActionItem item : actionItems) { item.getButtonWrapper().setVisible(true); item.getLabel().setVisible(true); }
    }
    animationTimer.start();
  }

  private void toggleTuckAway() {
    if (expanded) return;
    isTucked = !isTucked;
    tuckAnimationProgress = 0.0;
    tuckStartX = mainButtonX;
    if (isTucked) {
      preTuckX = mainButtonX;
      int midPoint = getWidth() / 2;
      tuckTargetX = (mainButtonX < midPoint) ? -MAIN_BUTTON_SIZE / 2 : getWidth() - MAIN_BUTTON_SIZE / 2;
    } else { tuckTargetX = preTuckX; }
    tuckAnimationTimer.start();
  }

  private void animateStep() {
    double step = (double) ANIMATION_STEP / ANIMATION_DURATION;
    animationProgress += expanded ? step : -step;
    animationProgress = Math.max(0.0, Math.min(1.0, animationProgress));
    mainButton.setRotation(easeInOut(animationProgress) * Math.PI / 4.0);
    overlay.setBackground(new Color(0, 0, 0, (int) (100 * easeInOut(animationProgress))));
    layoutComponents();
    if (animationProgress <= 0.0 || animationProgress >= 1.0) {
      animationTimer.stop();
      if (!expanded) {
        overlay.setVisible(false);
        for (ActionItem item : actionItems) { item.getButtonWrapper().setVisible(false); item.getLabel().setVisible(false); }
      }
    }
  }

  private void animateTuckStep() {
    double step = (double) ANIMATION_STEP / ANIMATION_DURATION;
    tuckAnimationProgress += step;
    tuckAnimationProgress = Math.min(1.0, tuckAnimationProgress);
    mainButtonX = (int) (tuckStartX + (tuckTargetX - tuckStartX) * easeInOut(tuckAnimationProgress));
    layoutComponents();
    if (tuckAnimationProgress >= 1.0) tuckAnimationTimer.stop();
  }

  private double easeInOut(double t) { return t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2; }

  private void layoutComponents() {
    overlay.setBounds(0, 0, getWidth(), getHeight());
    mainButton.setBounds(mainButtonX, mainButtonY, MAIN_BUTTON_SIZE, MAIN_BUTTON_SIZE);
    if (!expanded && !animationTimer.isRunning()) return;
    double easedProgress = easeInOut(animationProgress);
    int requiredHeight = (actionItems.size() * (ACTION_ITEM_SIZE + GAP)) + MARGIN;
    int yDirection = (mainButtonY < requiredHeight) ? 1 : -1;
    for (int i = 0; i < actionItems.size(); i++) {
      ActionItem item = actionItems.get(i);
      int itemX = mainButtonX + (MAIN_BUTTON_SIZE - ACTION_ITEM_SIZE) / 2;
      int targetY = (yDirection == -1) ? mainButtonY - (i + 1) * (ACTION_ITEM_SIZE + GAP) : mainButtonY + MAIN_BUTTON_SIZE + GAP + (i * (ACTION_ITEM_SIZE + GAP));
      int currentY = (int) (mainButtonY - (mainButtonY - targetY) * easedProgress);
      item.getButtonWrapper().setBounds(itemX, currentY, ACTION_ITEM_SIZE, ACTION_ITEM_SIZE);
      int labelWidth = item.getLabel().getPreferredSize().width;
      int labelHeight = item.getLabel().getPreferredSize().height;
      boolean displayMenuOnRight = (mainButtonX < labelWidth + GAP);
      int labelX = displayMenuOnRight ? itemX + ACTION_ITEM_SIZE + 10 : itemX - labelWidth - 10;
      item.getLabel().setBounds(labelX, currentY + (ACTION_ITEM_SIZE - labelHeight) / 2, labelWidth, labelHeight);
      item.setAlpha((float)easedProgress);
    }
  }

  @Override public boolean isOpaque() { return false; }

  private static class ActionItem {
    private final JComponent buttonWrapper;
    private final TransparentLabel label;
    private final List<Alphable> transparentComponents = new ArrayList<>();
    public ActionItem(JComponent b, TransparentLabel l) {
      this.buttonWrapper = b; this.label = l;
      this.transparentComponents.add(l);
      if (b instanceof Alphable) transparentComponents.add((Alphable) b);
      if (b != null) {
        for (Component c : ((Container) b).getComponents()) { if (c instanceof Alphable) transparentComponents.add((Alphable) c); }
      }
    }
    public void setAlpha(float alpha) { for (Alphable comp : transparentComponents) comp.setAlpha(alpha); }
    public JComponent getButtonWrapper() { return buttonWrapper; }
    public TransparentLabel getLabel() { return label; }
  }
}

class CircularButton extends JButton implements Alphable {
  private final Color backgroundColor;
  private double rotation = 0.0;
  private float alpha = 1.0f;
  public CircularButton(int size, Color color) {
    this.backgroundColor = color;
    setPreferredSize(new Dimension(size, size));
    setContentAreaFilled(false); setBorderPainted(false); setFocusPainted(false); setOpaque(false);
  }
  public void setRotation(double r) { this.rotation = r; repaint(); }
  @Override public void setAlpha(float a) { this.alpha = Math.max(0.0f, Math.min(1.0f, a)); repaint(); }
  @Override protected void paintComponent(Graphics g) {
    Graphics2D g2d = (Graphics2D) g.create();
    g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
    int size = Math.min(getWidth(), getHeight());
    if (getModel().isArmed()) g2d.setColor(backgroundColor.darker());
    else if (getModel().isRollover()) g2d.setColor(backgroundColor.brighter());
    else g2d.setColor(backgroundColor);
    g2d.fill(new Ellipse2D.Double(0, 0, size - 1, size - 1));
    g2d.setColor(new Color(0, 0, 0, 60));
    g2d.fill(new Ellipse2D.Double(2, 2, size - 1, size - 1));
    if (getIcon() != null) {
      getIcon().paintIcon(this, g2d, (getWidth() - getIcon().getIconWidth()) / 2, (getHeight() - getIcon().getIconHeight()) / 2);
    } else {
      g2d.setColor(Color.WHITE);
      g2d.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
      int centerX = getWidth() / 2, centerY = getHeight() / 2, lineLength = getWidth() / 4;
      AffineTransform originalTransform = g2d.getTransform();
      g2d.rotate(rotation, centerX, centerY);
      g2d.drawLine(centerX - lineLength, centerY, centerX + lineLength, centerY);
      g2d.drawLine(centerX, centerY - lineLength, centerX, centerY + lineLength);
      g2d.setTransform(originalTransform);
    }
    g2d.dispose();
  }
  @Override protected void paintBorder(Graphics g) {}
  @Override public boolean contains(int x, int y) { return new Ellipse2D.Float(0, 0, getWidth(), getHeight()).contains(x, y); }
}

class TransparentLabel extends JLabel implements Alphable {
  private float alpha = 1.0f;
  public TransparentLabel(String text) { super(text); }
  @Override public void setAlpha(float a) { this.alpha = Math.max(0.0f, Math.min(1.0f, a)); repaint(); }
  @Override public void paintComponent(Graphics g) {
    Graphics2D g2d = (Graphics2D) g.create();
    g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
    super.paintComponent(g2d);
    g2d.dispose();
  }
}

class FireAnimationOverlay extends JComponent implements Alphable {
  private final List<Particle> particles = new ArrayList<>();
  private final Timer animationTimer;
  private float alpha = 1.0f;
  public FireAnimationOverlay() {
    setOpaque(false);
    animationTimer = new Timer(50, e -> updateParticles());
    animationTimer.start();
  }
  private void updateParticles() {
    for (int i = 0; i < 3; i++) { particles.add(new Particle(getWidth())); }
    particles.removeIf(Particle::update);
    repaint();
  }
  @Override public void setAlpha(float a) { this.alpha = Math.max(0.0f, Math.min(1.0f, a)); }
  @Override protected void paintComponent(Graphics g) {
    super.paintComponent(g);
    Graphics2D g2d = (Graphics2D) g.create();
    g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
    for (Particle p : particles) { p.draw(g2d); }
    g2d.dispose();
  }
  private static class Particle {
    float x, y, ySpeed; int size, lifespan, initialLifespan; Color color;
    Particle(int width) {
      Random rand = new Random();
      this.x = rand.nextFloat() * (width * 0.6f) + (width * 0.2f);
      this.y = rand.nextFloat() * 20 + 40;
      this.ySpeed = rand.nextFloat() * 1.5f + 0.5f;
      this.size = rand.nextInt(6) + 4;
      this.lifespan = rand.nextInt(40) + 20;
      this.initialLifespan = lifespan;
      this.color = new Color(255, rand.nextInt(150) + 50, 0);
    }
    boolean update() { y -= ySpeed; lifespan--; return lifespan <= 0; }
    void draw(Graphics2D g2d) {
      float opacity = (float) lifespan / initialLifespan;
      g2d.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), (int) (opacity * 255)));
      g2d.fill(new Ellipse2D.Float(x - size / 2f, y - size / 2f, size, size));
    }
  }
}
