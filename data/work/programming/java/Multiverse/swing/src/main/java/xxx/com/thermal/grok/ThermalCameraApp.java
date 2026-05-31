package xxx.com.thermal.grok;

import nu.pattern.OpenCV;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.videoio.VideoCapture;
import org.opencv.videoio.Videoio;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;

public class ThermalCameraApp extends JFrame {

  static {
    try {
      OpenCV.loadLocally();
      System.out.println("OpenCV library loaded: " + Core.NATIVE_LIBRARY_NAME);
      System.out.println("OpenCV version: " + Core.getVersionString());
    } catch (Exception e) {
      System.err.println("Failed to load OpenCV: " + e.getMessage());
      System.exit(1);
    }
  }

  private final JLabel videoLabel;
  private VideoCapture capture;
  private boolean running = false;
  private final int cameraIndex;

  public ThermalCameraApp(int cameraIndex) {
    this.cameraIndex = cameraIndex;

    // Debug: Print OpenCV version and library path
    System.out.println("OpenCV Version: " + Core.getVersionString());
    System.out.println("java.library.path: " + System.getProperty("java.library.path"));

    // Load OpenCV native library
    try {
      System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
    } catch (UnsatisfiedLinkError e) {
      System.err.println("Failed to load OpenCV native library: " + e.getMessage());
      JOptionPane.showMessageDialog(this, "Failed to load OpenCV native library. Ensure the OpenCV DLL/SO is in the system PATH or specify the full path.");
      System.exit(1);
    }

    setTitle("XH09 Thermal Camera Display");
    setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    setSize(640, 480);
    setLayout(new BorderLayout());

    videoLabel = new JLabel();
    add(videoLabel, BorderLayout.CENTER);

    // Start capture thread
    startCapture();

    setVisible(true);
  }

  private void startCapture() {
    // Use CAP_DSHOW for Windows; adjust for other platforms if needed
    capture = new VideoCapture();
    capture.open(cameraIndex, Videoio.CAP_DSHOW);

    if (!capture.isOpened()) {
      JOptionPane.showMessageDialog(this, "Failed to open camera at index " + cameraIndex + ". Try a different index or backend.");
      System.exit(1);
    }

    // Set properties (XH09-specific)
    capture.set(Videoio.CAP_PROP_FRAME_WIDTH, 256);
    capture.set(Videoio.CAP_PROP_FRAME_HEIGHT, 192);
    capture.set(Videoio.CAP_PROP_FPS, 50);

    // Debug: Print actual resolution
    System.out.println("Actual Width: " + capture.get(Videoio.CAP_PROP_FRAME_WIDTH));
    System.out.println("Actual Height: " + capture.get(Videoio.CAP_PROP_FRAME_HEIGHT));

    running = true;

    new Thread(() -> {
      Mat frame = new Mat();
      while (running) {
        if (capture.read(frame)) {
          BufferedImage image = matToBufferedImage(frame);
          videoLabel.setIcon(new ImageIcon(image));
        }
        try {
          Thread.sleep(20); // ~50 FPS
        } catch (InterruptedException e) {
          e.printStackTrace();
        }
      }
    }).start();

    // Cleanup on close
    addWindowListener(new java.awt.event.WindowAdapter() {
      @Override
      public void windowClosing(java.awt.event.WindowEvent windowEvent) {
        running = false;
        if (capture != null) {
          capture.release();
        }
      }
    });
  }

  private BufferedImage matToBufferedImage(Mat mat) {
    int type = BufferedImage.TYPE_BYTE_GRAY; // Grayscale for thermal imagery
    if (mat.channels() > 1) {
      type = BufferedImage.TYPE_3BYTE_BGR;
    }
    BufferedImage image = new BufferedImage(mat.cols(), mat.rows(), type);
    byte[] targetPixels = ((DataBufferByte) image.getRaster().getDataBuffer()).getData();
    mat.get(0, 0, targetPixels);
    return image;
  }

  public static void main(String[] args) {

    int cameraIndex = 0; // Default camera

    VideoCapture capture = new VideoCapture();
    boolean opened = capture.open(cameraIndex, Videoio.CAP_DSHOW);

    if (!opened || !capture.isOpened()) {
      System.err.println("Failed to open camera with index " + cameraIndex);
      return;
    }

    // Example: Read a frame from the camera
    Mat frame = new Mat();
    if (capture.read(frame)) {
      System.out.println("Frame captured: " + frame.size());
    } else {
      System.err.println("Failed to capture frame");
    }

    // Release the camera
    capture.release();
  }
}
