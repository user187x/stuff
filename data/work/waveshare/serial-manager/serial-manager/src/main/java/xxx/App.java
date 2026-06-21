package xxx;

import com.formdev.flatlaf.FlatDarculaLaf;
import com.formdev.flatlaf.FlatDarkLaf;

import javax.swing.*;

public class App {

 static void main(String[] args) {

  FlatDarculaLaf.setup();

  SwingUtilities.invokeLater(() -> {
   new Transceiver().init();
  });
 }
}