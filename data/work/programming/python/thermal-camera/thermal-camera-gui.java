import com.formdev.flatlaf.FlatDarkLaf;
import org.opencv.core.*;
import org.opencv.core.Point;
import org.opencv.imgproc.Imgproc;
import org.opencv.videoio.VideoCapture;
import org.opencv.videoio.VideoWriter;
import org.opencv.videoio.Videoio;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class ThermalApp extends JFrame {

    // ----------------------------------------------------------------------
    // Constants & Configurations
    // ----------------------------------------------------------------------
    private static final int WIDTH = 256;
    private static final int HEIGHT = 192;
    private static final int SCALE = 3;
    private static final int NEW_WIDTH = WIDTH * SCALE;
    private static final int NEW_HEIGHT = HEIGHT * SCALE;
    
    // Touch-friendly sizes
    private static final Dimension TOUCH_BUTTON_SIZE = new Dimension(180, 50);

    // ----------------------------------------------------------------------
    // State Variables
    // ----------------------------------------------------------------------
    private int cmapI = Imgproc.COLORMAP_INFERNO; 
    private int rad = 0; // blur radius
    private double alpha = 1.0; // Contrast control
    private String unit = "C";
    private boolean hud = true;
    private boolean recording = false;
    private volatile boolean running = true;

    // OpenCV 
    private VideoCapture capture;
    private Mat currentFrame = new Mat();
    
    // Swing Components
    private JPanel videoPanel;
    private BufferedImage currentImage;

    static {
        System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
    }

    public static void main(String[] args) {
        // Apply the FlatDarkLaf before creating any Swing components
        try {
            UIManager.setLookAndFeel(new FlatDarkLaf());
        } catch (Exception ex) {
            System.err.println("Failed to initialize LaF");
        }

        SwingUtilities.invokeLater(() -> {
            ThermalApp app = new ThermalApp();
            app.setVisible(true);
        });
    }

    public ThermalApp() {
        super("Thermal Camera Controller");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1024, 600);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        setupUI();
        startCameraThread();
    }

    // ----------------------------------------------------------------------
    // Modern Swing UI Setup
    // ----------------------------------------------------------------------
    private void setupUI() {
        // 1. Video Panel (Custom painting for OpenCV frames)
        videoPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                if (currentImage != null) {
                    // Center the image in the panel
                    int x = (getWidth() - currentImage.getWidth()) / 2;
                    int y = (getHeight() - currentImage.getHeight()) / 2;
                    g.drawImage(currentImage, x, y, this);
                }
            }
        };
        videoPanel.setBackground(new Color(14, 18, 24)); 
        add(videoPanel, BorderLayout.CENTER);

        // 2. Control Rail (Right Side)
        JPanel controlRail = new JPanel();
        controlRail.setLayout(new BoxLayout(controlRail, BoxLayout.Y_AXIS));
        controlRail.setPreferredSize(new Dimension(250, 0));
        controlRail.setBorder(new EmptyBorder(15, 15, 15, 15));
        
        // Touch-friendly Unit Toggle
        JToggleButton btnUnit = new JToggleButton("Units: °C");
        btnUnit.setPreferredSize(TOUCH_BUTTON_SIZE);
        btnUnit.setMaximumSize(TOUCH_BUTTON_SIZE);
        btnUnit.setFont(btnUnit.getFont().deriveFont(16f));
        btnUnit.addActionListener(e -> {
            unit = btnUnit.isSelected() ? "F" : "C";
            btnUnit.setText("Units: °" + unit);
        });
        btnUnit.setAlignmentX(Component.CENTER_ALIGNMENT);

        // Touch-friendly HUD Toggle
        JToggleButton btnHud = new JToggleButton("Toggle HUD", true);
        btnHud.setPreferredSize(TOUCH_BUTTON_SIZE);
        btnHud.setMaximumSize(TOUCH_BUTTON_SIZE);
        btnHud.setFont(btnHud.getFont().deriveFont(16f));
        btnHud.addActionListener(e -> hud = btnHud.isSelected());
        btnHud.setAlignmentX(Component.CENTER_ALIGNMENT);

        // Blur Slider
        JSlider sliderBlur = new JSlider(0, 10, 0);
        sliderBlur.setBorder(new TitledBorder("Blur Radius"));
        sliderBlur.setMajorTickSpacing(2);
        sliderBlur.setPaintTicks(true);
        sliderBlur.setPaintLabels(true);
        sliderBlur.addChangeListener(e -> rad = sliderBlur.getValue());

        // Contrast Slider
        JSlider sliderContrast = new JSlider(50, 200, 100);
        sliderContrast.setBorder(new TitledBorder("Contrast (Alpha)"));
        sliderContrast.addChangeListener(e -> alpha = sliderContrast.getValue() / 100.0);

        // Assembly
        controlRail.add(Box.createVerticalStrut(20));
        controlRail.add(btnUnit);
        controlRail.add(Box.createVerticalStrut(15));
        controlRail.add(btnHud);
        controlRail.add(Box.createVerticalStrut(30));
        controlRail.add(sliderBlur);
        controlRail.add(Box.createVerticalStrut(15));
        controlRail.add(sliderContrast);
        controlRail.add(Box.createVerticalGlue()); // Pushes everything up

        add(controlRail, BorderLayout.EAST);
    }

    // ----------------------------------------------------------------------
    // Background Camera Thread
    // ----------------------------------------------------------------------
    private void startCameraThread() {
        Thread cameraThread = new Thread(() -> {
            capture = new VideoCapture("/dev/video0", Videoio.CAP_V4L);
            capture.set(Videoio.CAP_PROP_CONVERT_RGB, 0.0); // Keep raw

            if (!capture.isOpened()) {
                System.err.println("Cannot open camera /dev/video0");
                return;
            }

            while (running) {
                if (capture.read(currentFrame) && !currentFrame.empty()) {
                    // Render the frame using your logic
                    Mat processed = renderHeatmap(currentFrame);
                    
                    if (hud) {
                        TempData td = measure(currentFrame);
                        drawHUD(processed, td);
                    }

                    // Convert to standard Java image for UI rendering
                    currentImage = matToBufferedImage(processed);
                    
                    // Tell Swing to update the UI
                    videoPanel.repaint();
                }
            }
            capture.release();
        });
        cameraThread.setDaemon(true); // Ensures thread dies when app closes
        cameraThread.start();
    }

    // ----------------------------------------------------------------------
    // Math & Thermal Parsing 
    // ----------------------------------------------------------------------
    private double toUnit(double c) {
        return unit.equals("F") ? c * 9.0 / 5.0 + 32.0 : c;
    }

    public class TempData {
        double temp, maxTemp, minTemp, avgTemp;
        int mRow, mCol, lRow, lCol;
    }

    private TempData measure(Mat thdata) {
        // (Preserved from your original code)
        TempData data = new TempData();
        if(thdata.rows() < 192) return data; // Guard against bad frames

        double[] centerPixel = thdata.get(96, 128);
        if (centerPixel != null && centerPixel.length >= 2) {
            double hi = centerPixel[0];
            double lo = centerPixel[1] * 256.0;
            data.temp = Math.round(((hi + lo) / 64.0) - 273.15 * 100.0) / 100.0;
        }
        return data;
    }

    private void drawHUD(Mat processed, TempData td) {
        String tempStr = String.format("Center: %.1f %s", toUnit(td.temp), unit);
        Imgproc.putText(processed, tempStr, new Point(20, 40), 
                        Imgproc.FONT_HERSHEY_SIMPLEX, 1.0, new Scalar(255, 255, 255), 2);
    }

    // ----------------------------------------------------------------------
    // Imaging Pipeline
    // ----------------------------------------------------------------------
    private Mat renderHeatmap(Mat imdata) {
        Mat bgr = new Mat();
        
        // Guard against parsing the wrong matrix type
        if (imdata.channels() == 2) {
            Imgproc.cvtColor(imdata, bgr, Imgproc.COLOR_YUV2BGR_YUYV);
        } else {
            imdata.copyTo(bgr);
        }
        
        bgr.convertTo(bgr, -1, alpha, 0); 
        
        Mat resized = new Mat();
        Imgproc.resize(bgr, resized, new Size(NEW_WIDTH, NEW_HEIGHT), 0, 0, Imgproc.INTER_CUBIC);
        
        if (rad > 0) {
            int ksize = (rad % 2 == 0) ? rad + 1 : rad;
            Imgproc.GaussianBlur(resized, resized, new Size(ksize, ksize), 0);
        }
        
        Mat mapped = new Mat();
        Imgproc.applyColorMap(resized, mapped, cmapI);
        
        return mapped;
    }

    // ----------------------------------------------------------------------
    // Utility: OpenCV Mat to Java BufferedImage
    // ----------------------------------------------------------------------
    private BufferedImage matToBufferedImage(Mat mat) {
        int type = BufferedImage.TYPE_BYTE_GRAY;
        if (mat.channels() > 1) {
            type = BufferedImage.TYPE_3BYTE_BGR;
        }
        int bufferSize = mat.channels() * mat.cols() * mat.rows();
        byte[] b = new byte[bufferSize];
        mat.get(0, 0, b);
        
        BufferedImage image = new BufferedImage(mat.cols(), mat.rows(), type);
        final byte[] targetPixels = ((DataBufferByte) image.getRaster().getDataBuffer()).getData();
        System.arraycopy(b, 0, targetPixels, 0, b.length);
        
        return image;
    }
}