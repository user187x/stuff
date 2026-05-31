package xxx.com.calendar.advanced;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.JPanel;
import javax.swing.Timer;

class LoadingAnimationPanel extends JPanel implements ActionListener {
  private static final long serialVersionUID = 1L;
  private final Timer timer;
  private int angle = 0;
  private final int DELAY = 50;

  public LoadingAnimationPanel() {
    setBackground(Color.WHITE);
    setOpaque(true);
    setPreferredSize(new Dimension(100, 100));
    timer = new Timer(DELAY, this);
  }

  public void startAnimation() {
    if (!timer.isRunning())
      timer.start();
  }

  public void stopAnimation() {
    if (timer.isRunning())
      timer.stop();
  }

  @Override
  public void actionPerformed(ActionEvent e) {
    angle = (angle + 10) % 360;
    repaint();
  }

  @Override
  protected void paintComponent(Graphics g) {
    super.paintComponent(g);
    Graphics2D g2d = (Graphics2D) g.create();
    g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    int width = getWidth();
    int height = getHeight();
    if (width > 0 && height > 0) {
      int centerX = width / 2;
      int centerY = height / 2;
      int radius = Math.min(Math.min(width, height) / 3, 30);
      g2d.setColor(Color.BLUE);
      g2d.setStroke(new BasicStroke(3));
      g2d.drawArc(centerX - radius, centerY - radius, radius * 2, radius * 2, angle, 90);
      g2d.drawArc(centerX - radius, centerY - radius, radius * 2, radius * 2, angle + 180, 90);
    }
    g2d.dispose();
  }
}
