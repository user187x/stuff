package xxx.com.charts.xchart;

import org.knowm.xchart.XYChart;
import org.knowm.xchart.XYChartBuilder;
import org.knowm.xchart.SwingWrapper;

public class App {

  public static void main(String[] args) {

    double[] xData = new double[] {0.0, 1.0, 2.0, 3.0};
    double[] yData = new double[] {2.0, 1.0, 0.0, 3.0};

    XYChart chart =
        new XYChartBuilder()
            .width(800)
            .height(600)
            .title("Simple Line Chart")
            .xAxisTitle("X")
            .yAxisTitle("Y")
            .build();

    chart.addSeries("Line", xData, yData);

    new SwingWrapper<>(chart).displayChart();
  }
}
