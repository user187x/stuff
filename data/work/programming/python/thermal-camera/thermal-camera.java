 

import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;
import org.opencv.videoio.VideoCapture;
import org.opencv.videoio.VideoWriter;
import org.opencv.videoio.Videoio;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class ThermalApp {

    // ----------------------------------------------------------------------
    // Theme & Constants (Colors are in BGR format for OpenCV)
    // ----------------------------------------------------------------------
    private static final Scalar BG = new Scalar(14, 18, 24);
    private static final Scalar PANEL = new Scalar(26, 32, 40);
    private static final Scalar PANEL_HI = new Scalar(36, 46, 58);
    private static final Scalar ACCENT = new Scalar(0, 214, 255);
    private static final Scalar ACCENT2 = new Scalar(255, 160, 60);
    private static final Scalar REC_RED = new Scalar(235, 70, 70);
    private static final Scalar TEXT = new Scalar(220, 230, 235);
    private static final Scalar TEXT_DIM = new Scalar(128, 140, 150);
    private static final int FONT = Imgproc.FONT_HERSHEY_SIMPLEX;

    private static final int CANVAS_W = 1024;
    private static final int CANVAS_H = 600;
    private static final int RAIL_W = 216;
    private static final int VIEW_W = CANVAS_W - RAIL_W;
    private static final int VIEW_H = CANVAS_H;

    // 256x192 General settings
    private static final int WIDTH = 256;
    private static final int HEIGHT = 192;
    private static final int SCALE = 3;
    private static final int NEW_WIDTH = WIDTH * SCALE;
    private static final int NEW_HEIGHT = HEIGHT * SCALE;
    private static final String WIN = "Thermal";

    // ----------------------------------------------------------------------
    // State Variables
    // ----------------------------------------------------------------------
    private int cmapI = 3; // INFERNO default
    private int rotI = 0;
    private int rad = 0; // blur radius
    private double alpha = 1.0; // Contrast control
    private String unit = "C";
    private boolean hud = true;
    private boolean recording = false;
    private VideoWriter writer = null;
    private boolean running = true;
    private String saveDir = System.getProperty("user.dir");

    // UI Components
    private List<Button> buttons = new ArrayList<>();
    private List<Slider> sliders = new ArrayList<>();

    // Static block to load OpenCV native library
    static {
        System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
    }

    public static void main(String[] args) {
        ThermalApp app = new ThermalApp();
        // app.run(); // Entry point for loop (requires windowing setup)
    }

    public ThermalApp() {
        setupUI();
    }

    // ----------------------------------------------------------------------
    // UI Classes
    // ----------------------------------------------------------------------
    abstract class Action {
        abstract void execute();
    }

    class Button {
        String label, sub;
        Action action;
        boolean toggle, active;
        Rect rect = new Rect(0, 0, 0, 0);
        long flashUntil = 0;

        Button(String label, String sub, boolean toggle, Action action) {
            this.label = label;
            this.sub = sub;
            this.toggle = toggle;
            this.action = action;
        }

        boolean hit(int x, int y) {
            return rect.contains(new Point(x, y));
        }

        void draw(Mat img) {
            boolean hot = System.currentTimeMillis() < flashUntil;
            Scalar base = (hot || (toggle && active)) ? PANEL_HI : PANEL;
            Imgproc.rectangle(img, rect, base, -1);
            // Add rounded corner logic and text drawing here (simplified for brevity)
            Imgproc.putText(img, label, new Point(rect.x + 14, rect.y + 27), FONT, 0.55, (toggle && active) ? ACCENT : TEXT, 1);
        }
    }

    class Slider {
        String label;
        int vmin, vmax;
        Rect rect = new Rect(0, 0, 0, 0);
        boolean dragging = false;

        Slider(String label, int vmin, int vmax) {
            this.label = label;
            this.vmin = vmin;
            this.vmax = vmax;
        }

        boolean hit(int px, int py) {
            return rect.contains(new Point(px, py));
        }
    }

    private void setupUI() {
        // UI Initialization logic here (mapping to rows and setting Rects)
        Button btnUnit = new Button("UNITS", "deg C", false, new Action() {
            @Override void execute() {
                unit = unit.equals("C") ? "F" : "C";
            }
        });
        buttons.add(btnUnit);
        // Add other buttons and sliders...
    }

    // ----------------------------------------------------------------------
    // Camera & Data Helpers
    // ----------------------------------------------------------------------
    private VideoCapture openCapture(int dev) {
        VideoCapture cap = new VideoCapture("/dev/video" + dev, Videoio.CAP_V4L);
        cap.set(Videoio.CAP_PROP_CONVERT_RGB, 0.0); // Keep raw
        return cap;
    }

    private boolean looksLikeTC001(Mat frame) {
        return frame != null && frame.dims() > 0 && frame.rows() == HEIGHT * 2 && frame.cols() == WIDTH;
    }

    // ----------------------------------------------------------------------
    // Math & Thermal Parsing (Verbatim translation of LeoDJ's math)
    // ----------------------------------------------------------------------
    private double toUnit(double c) {
        return unit.equals("F") ? c * 9.0 / 5.0 + 32.0 : c;
    }

    public class TempData {
        double temp, maxTemp, minTemp, avgTemp;
        int mRow, mCol, lRow, lCol;
    }

    private TempData measure(Mat thdata) {
        TempData data = new TempData();

        // Center pixel
        double[] centerPixel = thdata.get(96, 128);
        double hi = centerPixel[0];
        double lo = centerPixel[1] * 256.0;
        data.temp = Math.round(((hi + lo) / 64.0) - 273.15 * 100.0) / 100.0;

        // Split channels to find min/max
        List<Mat> channels = new ArrayList<>();
        Core.split(thdata, channels);
        Mat channel0 = channels.get(0); // High bytes
        Mat channel1 = channels.get(1); // Low bytes

        // Find max
        Core.MinMaxLocResult minMax = Core.minMaxLoc(channel1);
        data.mCol = (int) minMax.maxLoc.x;
        data.mRow = (int) minMax.maxLoc.y;
        
        double hiMax = channel0.get(data.mRow, data.mCol)[0];
        double loMax = minMax.maxVal * 256.0;
        data.maxTemp = Math.round(((hiMax + loMax) / 64.0) - 273.15 * 100.0) / 100.0;

        // Find min
        data.lCol = (int) minMax.minLoc.x;
        data.lRow = (int) minMax.minLoc.y;
        
        double hiMin = channel0.get(data.lRow, data.lCol)[0];
        double loMin = minMax.minVal * 256.0;
        data.minTemp = Math.round(((hiMin + loMin) / 64.0) - 273.15 * 100.0) / 100.0;

        // Averages
        Scalar mean0 = Core.mean(channel0);
        Scalar mean1 = Core.mean(channel1);
        double avgHi = mean0.val[0];
        double avgLo = mean1.val[0] * 256.0;
        data.avgTemp = Math.round(((avgHi + avgLo) / 64.0) - 273.15 * 100.0) / 100.0;

        return data;
    }

    // ----------------------------------------------------------------------
    // Imaging Pipeline (Completed from the cut-off prompt)
    // ----------------------------------------------------------------------
    private Mat renderHeatmap(Mat imdata) {
        Mat bgr = new Mat();
        
        // Convert the real image to RGB
        Imgproc.cvtColor(imdata, bgr, Imgproc.COLOR_YUV2BGR_YUYV);
        
        // Contrast
        bgr.convertTo(bgr, -1, alpha, 0); // alpha is scale/contrast
        
        // Bicubic interpolate, upscale and blur (Completing your cutoff code)
        Mat resized = new Mat();
        Imgproc.resize(bgr, resized, new Size(NEW_WIDTH, NEW_HEIGHT), 0, 0, Imgproc.INTER_CUBIC);
        
        if (rad > 0) {
            // Ensure radius is odd for GaussianBlur
            int ksize = (rad % 2 == 0) ? rad + 1 : rad;
            Imgproc.GaussianBlur(resized, resized, new Size(ksize, ksize), 0);
        }
        
        // Apply Colormap (Assuming standard map application here)
        Mat mapped = new Mat();
        int colorMapType = Imgproc.COLORMAP_INFERNO; // Replace with array map logic
        Imgproc.applyColorMap(resized, mapped, colorMapType);
        
        return mapped;
    }
}