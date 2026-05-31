// thermal/ThermalCameraViewer.java

package xxx.com.thermal.gemini;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;

public class ThermalCameraViewer {

  // --- Camera Configuration ---
  private static final int IMAGE_WIDTH = 384;
  private static final int IMAGE_HEIGHT = 288;
  private static final int FRAME_BUFFER_SIZE = IMAGE_WIDTH * IMAGE_HEIGHT * 2;

  private static JFrame frame;
  private static ImagePanel imagePanel;
  private static volatile boolean stopCapture = false;

  /**
   * JNA Interface to map functions from XthermDll.dll.
   */
  public interface XthermDll extends Library {
    // This now simply loads the library. The path is set in the main method.
    XthermDll INSTANCE = Native.load("XthermDll", XthermDll.class);

    // Maps the functions from the DLL's header file.
    int InitDevice(Pointer hWnd, Pointer errmsg);
    int StartDevice();
    int CloseDevice();
    int GetRawData(ByteBuffer pdata, IntByReference len);
  }

  public static void main(String[] args) {
    // ---
    // UPDATED CODE: Set the path for JNA to find the DLL.
    // IMPORTANT: Change this path to the actual location of your XthermDll.dll file.
    // Example: "D:\\Projects\\Java\\Multiverse\\tactic"
    // ---
    String dllPath = "D:\\Projects\\Java\\Multiverse\\tactic"; // <-- ❗ CHANGE THIS LINE ❗
    System.setProperty("jna.library.path", dllPath);


    // --- Step 1: Initialize the Device using the DLL ---
    System.out.println("Initializing device via XthermDll...");
    try {
      int initResult = XthermDll.INSTANCE.InitDevice(null, null);
      if (initResult != 0) {
        JOptionPane.showMessageDialog(null, "Failed to initialize device. Error code: " + initResult +
                "\n\n- Is the camera plugged in?\n- Is XthermDll.dll in the correct path?\n- Did you use Zadig to install the WinUSB driver?",
            "Initialization Error", JOptionPane.ERROR_MESSAGE);
        return;
      }
    } catch (UnsatisfiedLinkError e) {
      JOptionPane.showMessageDialog(null, "Could not load XthermDll.dll.\n\n" +
          "Please ensure:\n" +
          "1. The path in the code is correct (currently: " + dllPath + ").\n" +
          "2. The DLL is 64-bit to match your 64-bit Java.\n" +
          "3. You have installed any required MSVC Redistributables.\n\n" +
          "Error details: " + e.getMessage(), "DLL Load Error", JOptionPane.ERROR_MESSAGE);
      return;
    }
    System.out.println("Device initialized successfully.");

    // --- Step 2: Start the data stream ---
    int startResult = XthermDll.INSTANCE.StartDevice();
    if (startResult != 1) {
      JOptionPane.showMessageDialog(null, "Failed to start device stream. Error code: " + startResult,
          "Start Error", JOptionPane.ERROR_MESSAGE);
      XthermDll.INSTANCE.CloseDevice();
      return;
    }
    System.out.println("Device stream started.");

    // --- Step 3: Setup GUI ---
    setupGui();

    // --- Step 4: Start Data Capture Thread ---
    Thread captureThread = createCaptureThread();
    captureThread.start();
  }

  /**
   * A custom JPanel to render the thermal image.
   */
  static class ImagePanel extends JPanel {
    private final BufferedImage image;

    public ImagePanel(int width, int height) {
      this.image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
      setPreferredSize(new Dimension(width * 2, height * 2)); // Scale up for visibility
    }

    public void updateImage(byte[] yuy2Data) {
      if (yuy2Data.length < FRAME_BUFFER_SIZE) {
        return; // Not a full frame
      }
      convertYUY2toRGB(yuy2Data, IMAGE_WIDTH, IMAGE_HEIGHT, this.image);
      repaint();
    }

    private void convertYUY2toRGB(byte[] yuy2, int width, int height, BufferedImage image) {
      int i = 0;
      for (int y = 0; y < height; y++) {
        for (int x = 0; x < width; x += 2) {
          int y1 = yuy2[i++] & 0xFF;
          int u = yuy2[i++] & 0xFF;
          int y2 = yuy2[i++] & 0xFF;
          int v = yuy2[i++] & 0xFF;

          int r1 = (int) (y1 + 1.402 * (v - 128));
          int g1 = (int) (y1 - 0.344136 * (u - 128) - 0.714136 * (v - 128));
          int b1 = (int) (y1 + 1.772 * (u - 128));

          int r2 = (int) (y2 + 1.402 * (v - 128));
          int g2 = (int) (y2 - 0.344136 * (u - 128) - 0.714136 * (v - 128));
          int b2 = (int) (y2 + 1.772 * (u - 128));

          image.setRGB(x, y, toRgb(r1, g1, b1));
          image.setRGB(x + 1, y, toRgb(r2, g2, b2));
        }
      }
    }

    private int toRgb(int r, int g, int b) {
      r = Math.max(0, Math.min(255, r));
      g = Math.max(0, Math.min(255, g));
      b = Math.max(0, Math.min(255, b));
      return (r << 16) | (g << 8) | b;
    }

    @Override
    protected void paintComponent(Graphics g) {
      super.paintComponent(g);
      if (image != null) {
        g.drawImage(image, 0, 0, getWidth(), getHeight(), null);
      }
    }
  }

  private static Thread createCaptureThread() {
    return new Thread(() -> {
      ByteBuffer dataBuffer = ByteBuffer.allocate(FRAME_BUFFER_SIZE);
      IntByReference dataLen = new IntByReference(FRAME_BUFFER_SIZE);

      while (!stopCapture) {
        dataBuffer.clear();
        dataLen.setValue(FRAME_BUFFER_SIZE);

        int result = XthermDll.INSTANCE.GetRawData(dataBuffer, dataLen);

        if (result == 1 && dataLen.getValue() > 0) {
          byte[] frameData = new byte[dataLen.getValue()];
          dataBuffer.get(frameData);
          SwingUtilities.invokeLater(() -> imagePanel.updateImage(frameData));
        }

        try {
          Thread.sleep(30);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          break;
        }
      }

      // --- Step 5: Cleanup ---
      System.out.println("Stopping capture and closing device...");
      XthermDll.INSTANCE.CloseDevice();
      System.out.println("Cleanup complete.");
      System.exit(0);
    });
  }

  private static void setupGui() {
    SwingUtilities.invokeLater(() -> {
      frame = new JFrame("Thermal Camera Feed");
      imagePanel = new ImagePanel(IMAGE_WIDTH, IMAGE_HEIGHT);
      frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
      frame.getContentPane().add(imagePanel);
      frame.pack();
      frame.setLocationRelativeTo(null);
      frame.setVisible(true);
      frame.addWindowListener(new java.awt.event.WindowAdapter() {
        @Override
        public void windowClosing(java.awt.event.WindowEvent windowEvent) {
          stopCapture = true;
        }
      });
    });
  }
}
