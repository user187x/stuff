package xxx.com.swing;

import javax.swing.*;
import java.awt.*;

public class TestWindow {
  public static void main(String[] args) {
    SwingUtilities.invokeLater(() -> {
      JWindow window = new JWindow();
      window.setBackground(new Color(255, 0, 0, 50));
      window.setSize(100, 100);
      window.setLocation(100, 100);
      window.setAlwaysOnTop(true);
      window.setVisible(true);
      System.out.println("Test window at (100, 100)");
    });
  }
}
