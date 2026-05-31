package xxx.com.charts.echarts;

import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import javafx.scene.Scene;
import javafx.scene.web.WebView;
import org.icepear.echarts.Bar;
import org.icepear.echarts.render.Engine;

import javax.swing.*;
import java.io.File;
import java.net.MalformedURLException;

public class App {

  private static void initAndShowGUI() {
    // --- 1. Create the EChart Option Object ---
    Bar bar =
        new Bar()
            .setLegend()
            .setTooltip("item")
            .addXAxis(new String[] {"Matcha Latte", "Milk Tea", "Cheese Cocoa", "Walnut Brownie"})
            .addYAxis()
            .addSeries("2023", new Number[] {43.3, 83.1, 86.4, 72.4})
            .addSeries("2024", new Number[] {85.8, 73.4, 65.2, 53.9})
            .addSeries("2035", new Number[] {93.7, 55.1, 82.5, 39.1});

    // --- 2. Render the Chart to an HTML file ---
    Engine engine = new Engine();
    String chartHtmlPath = "bar_chart.html";
    try {
      engine.render(chartHtmlPath, bar.getOption());
    } catch (Exception e) {
      System.out.println("Error rendering chart:");
      return;
    }

    // --- 3. Create the Swing JFrame ---
    JFrame frame = new JFrame("ECharts in Java Swing");
    frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    frame.setSize(700, 800);

    // --- 4. Embed a JavaFX Panel ---
    // JFXPanel is the bridge between Swing and JavaFX.
    final JFXPanel jfxPanel = new JFXPanel();
    frame.add(jfxPanel);

    // --- 5. Initialize the JavaFX components on the JavaFX Application Thread ---
    // This is crucial to prevent threading issues.
    Platform.runLater(() -> initFX(jfxPanel, chartHtmlPath));

    // Make the frame visible
    frame.setVisible(true);
  }

  private static void initFX(JFXPanel jfxPanel, String htmlPath) {
    // Create a WebView, which is a mini-browser
    WebView webView = new WebView();

    // Create a JavaFX Scene and set the WebView as its root
    jfxPanel.setScene(new Scene(webView));

    // Load the generated HTML file.
    // We must convert the file path to a URL format.
    try {
      File f = new File(htmlPath);
      String url = f.toURI().toURL().toString();
      System.out.println("Loading URL: " + url);
      webView.getEngine().load(url);
    }
    catch (MalformedURLException e) {

        System.out.println("Error creating URL for the html file:");
    }
  }

  public static void main(String[] args) {
    SwingUtilities.invokeLater(App::initAndShowGUI);
  }
}
