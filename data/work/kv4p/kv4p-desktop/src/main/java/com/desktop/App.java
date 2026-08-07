/*
kv4p HT desktop port - GPLv3 (see http://kv4p.com)
*/
package com.desktop;

import com.formdev.flatlaf.FlatDarkLaf;
import com.desktop.data.AppDatabase;
import com.desktop.ui.MainWindow;
import javax.swing.SwingUtilities;

public final class App {
  public static void main(String[] args) {
    try {
      FlatDarkLaf.setup();
    } catch (Exception ignored) {
    }

    final AppDatabase db = AppDatabase.open();
    SwingUtilities.invokeLater(
        () -> {
          MainWindow w = new MainWindow(db);
          w.setLocationByPlatform(true);
          w.setVisible(true);
        });
  }
}
