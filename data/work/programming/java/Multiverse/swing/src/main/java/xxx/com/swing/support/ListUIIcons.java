package xxx.com.swing.support;

import javax.swing.*;

public class ListUIIcons {

  public static void main(String[] args) {

    // Optionally set a specific Look and Feel to inspect its icons
    try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); } catch (Exception e) {}

    UIDefaults defaults = UIManager.getDefaults();
    for (Object key : defaults.keySet()) {
      if (defaults.get(key) instanceof Icon) {
        System.out.println(key);
      }
    }
  }
}
