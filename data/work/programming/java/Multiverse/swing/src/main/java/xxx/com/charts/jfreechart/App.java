package xxx.com.charts.jfreechart;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.data.category.DefaultCategoryDataset;

import javax.swing.*;

public class App {

  public static void main(String[] args) {

    DefaultCategoryDataset dataset = new DefaultCategoryDataset();
    dataset.addValue(1, "Category 1", "January");
    dataset.addValue(4, "Category 1", "February");
    dataset.addValue(3, "Category 1", "March");

    JFreeChart chart = ChartFactory.createBarChart("Monthly Sales", "Month", "Sales", dataset);

    JFrame frame = new JFrame("JFreeChart");
    frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    frame.add(new ChartPanel(chart));
    frame.pack();
    frame.setVisible(true);
  }
}
