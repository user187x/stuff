package xxx;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.ItemEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

public class Preferences {
 private JTextField TextFieldUrl;
 private JCheckBox checkBoxSaveLog;
 private JCheckBox checkBoxUpload;
 private JCheckBox checkBoxDashboard;
 private JCheckBox checkBoxMQTT;
 private JButton ButtonLogFileName;
 private JFrame mainFrame;
 private Transceiver mLo;

 public Preferences(Transceiver iifroglablora) {
  this.mLo = iifroglablora;

  this.mainFrame = new JFrame("Preferences");
  this.mainFrame.addWindowListener(new WindowAdapter() {
   public void windowClosing(WindowEvent windowEvent) {
    Preferences.this.mainFrame.dispose();
   }
  });

  this.mainFrame.setLayout(new BorderLayout());

  JPanel mainContainer = new JPanel();
  mainContainer.setLayout(new BoxLayout(mainContainer, BoxLayout.Y_AXIS));
  mainContainer.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

  // ui_Step2() removed because its contents are now directly inside Transceiver.java
  mainContainer.add(ui_Step3());

  this.mainFrame.add(mainContainer, BorderLayout.CENTER);
  this.mainFrame.pack();
  this.mainFrame.setMinimumSize(new Dimension(550, 400));
  this.mainFrame.setLocationRelativeTo(null);
  this.mainFrame.setVisible(true);
 }

 public JPanel ui_Step3() {
  JPanel panel = new JPanel();
  panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
  panel.setBorder(BorderFactory.createTitledBorder("When Receive Data:"));

  JPanel row1 = new JPanel(new FlowLayout(FlowLayout.LEFT));
  this.checkBoxDashboard = new JCheckBox("Upload to Dashboard");
  this.checkBoxDashboard.setSelected(Boolean.parseBoolean(this.mLo.loadPreferences("checkBoxDashboard", "true")));
  this.checkBoxDashboard.addItemListener(e -> {
   String tcheck = Boolean.toString(e.getStateChange() == ItemEvent.SELECTED);
   Preferences.this.mLo.savePreference("checkBoxDashboard", tcheck);
   Preferences.this.mLo.appendLog("Preference updated: Upload to Dashboard set to " + tcheck);
  });
  row1.add(this.checkBoxDashboard);

  JButton ButtonIoT = new JButton("Open Dashboard");
  ButtonIoT.addActionListener(e -> {
   if (Desktop.isDesktopSupported()) {
    try {
     Desktop.getDesktop().browse(new URI(Preferences.this.mLo.mDomainName + "page_IoT_index.php?device=LoRa&IoT=1&share=1&projectid=" + Preferences.this.mLo.ProjectId));
     Preferences.this.mLo.appendLog("Browser opened successfully for Dashboard view.");
    } catch (IOException | URISyntaxException e1) {
     Preferences.this.mLo.appendLog("Error opening Dashboard in browser: " + e1.getMessage());
    }
   } else {
    Preferences.this.mLo.appendLog("Desktop browsing is not supported on this device/OS.");
   }
  });
  row1.add(ButtonIoT);
  panel.add(row1);

  JPanel row2 = new JPanel(new FlowLayout(FlowLayout.LEFT));
  this.checkBoxUpload = new JCheckBox("Upload to HTTP (URL+data)");
  this.checkBoxUpload.setSelected(Boolean.parseBoolean(this.mLo.loadPreferences("checkBoxUpload", "false")));
  this.checkBoxUpload.addItemListener(e -> {
   String check = Boolean.toString(e.getStateChange() == ItemEvent.SELECTED);
   Preferences.this.mLo.savePreference("checkBoxUpload", check);
   Preferences.this.mLo.appendLog("Preference updated: Upload to HTTP set to " + check);
  });
  row2.add(this.checkBoxUpload);

  this.TextFieldUrl = new JTextField("http://127.0.0.1/index.php?data=", 25);
  row2.add(this.TextFieldUrl);
  panel.add(row2);

  JPanel row3 = new JPanel(new FlowLayout(FlowLayout.LEFT));
  this.checkBoxSaveLog = new JCheckBox("save data to");
  this.checkBoxSaveLog.setSelected(Boolean.parseBoolean(this.mLo.loadPreferences("checkBoxSaveLog", "false")));
  this.checkBoxSaveLog.addItemListener(e -> {
   String tcheck = Boolean.toString(e.getStateChange() == ItemEvent.SELECTED);
   Preferences.this.mLo.savePreference("checkBoxSaveLog", tcheck);
   Preferences.this.mLo.appendLog("Preference updated: Save Log set to " + tcheck);
  });
  row3.add(this.checkBoxSaveLog);

  File f = new File(this.mLo.StringLogFileName);
  this.ButtonLogFileName = new JButton(f.getName());
  this.ButtonLogFileName.addActionListener(e -> {
   JFileChooser fileChooser = new JFileChooser();
   fileChooser.setCurrentDirectory(new File(System.getProperty("user.home")));
   int result = fileChooser.showSaveDialog((Component)null);
   if (result == JFileChooser.APPROVE_OPTION) {
    File selectedFile = fileChooser.getSelectedFile();
    Preferences.this.mLo.StringLogFileName = selectedFile.getAbsolutePath();
    Preferences.this.mLo.savePreference("StringLogFileName", Preferences.this.mLo.StringLogFileName);
    Preferences.this.ButtonLogFileName.setText(selectedFile.getName());
    Preferences.this.mLo.appendLog("Log file target changed to: " + selectedFile.getAbsolutePath());
   }
  });
  row3.add(this.ButtonLogFileName);
  panel.add(row3);

  JPanel row4 = new JPanel(new FlowLayout(FlowLayout.LEFT));
  this.checkBoxMQTT = new JCheckBox("Upload to MQTT");
  this.checkBoxMQTT.setSelected(Boolean.parseBoolean(this.mLo.loadPreferences("checkBoxMQTT", "false")));
  this.checkBoxMQTT.addItemListener(e -> {
   String tcheck = Boolean.toString(e.getStateChange() == ItemEvent.SELECTED);
   Preferences.this.mLo.savePreference("checkBoxMQTT", tcheck);
   Preferences.this.mLo.appendLog("Preference updated: Upload to MQTT set to " + tcheck);
  });
  row4.add(this.checkBoxMQTT);
  panel.add(row4);

  JPanel row5 = new JPanel(new FlowLayout(FlowLayout.LEFT));
  JCheckBox mDirect2APP = new JCheckBox("Remember to choose, open and execute immediately next time");
  mDirect2APP.setSelected(Boolean.parseBoolean(this.mLo.loadPreferences("mDirect2APP", "true")));
  mDirect2APP.addItemListener(e -> {
   String tcheck = Boolean.toString(e.getStateChange() == ItemEvent.SELECTED);
   Preferences.this.mLo.savePreference("mDirect2APP", tcheck);
   Preferences.this.mLo.appendLog("Preference updated: Auto-execute set to " + tcheck);
  });
  row5.add(mDirect2APP);
  panel.add(row5);

  JPanel row6 = new JPanel(new FlowLayout(FlowLayout.LEFT));
  row6.add(new JLabel("Need to re-start application for the new settings."));
  panel.add(row6);

  return panel;
 }
}