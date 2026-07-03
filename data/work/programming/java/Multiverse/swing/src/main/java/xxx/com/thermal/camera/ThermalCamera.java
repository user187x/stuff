package xxx.com.thermal.camera;

import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.Rect;
import org.opencv.core.Size;
import org.opencv.videoio.VideoCapture;
import org.opencv.videoio.Videoio;
import org.opencv.imgproc.Imgproc;
import org.opencv.highgui.HighGui;
import java.util.ArrayList;
import java.util.List;

public class ThermalCamera {

 // Load the OpenCV native library
 static {
  System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
 }

 public static void main(String[] args) {
  System.out.println("Starting Topdon TC001 Java App...");

  // 1. Initialize Device (Assuming /dev/video0 or auto-detected index)
  int deviceIndex = 0;
  VideoCapture cap = new VideoCapture(deviceIndex, Videoio.CAP_V4L2);

  if (!cap.isOpened()) {
   System.err.println("Error: Cannot open camera.");
   return;
  }

  // 2. Set Raw Data Property (Equivalent to cap.set(cv2.CAP_PROP_CONVERT_RGB, 0.0))
  cap.set(Videoio.CAP_PROP_CONVERT_RGB, 0.0);

  Mat frame = new Mat();

  // Window settings
  int width = 256;
  int height = 192;
  int scale = 3;

  while (true) {
   // Grab and read the frame to clear buffers
   cap.grab();
   boolean ret = cap.read(frame);

   if (!ret || frame.empty()) {
    System.err.println("Camera disconnected or buffer error.");
    break;
   }

   // 3. Split the frame into Image Data and Thermal Data
   // The TC001 outputs a 256x384 frame. Top half is image, bottom half is data.
   Rect imgRect = new Rect(0, 0, width, height);
   Rect thermalRect = new Rect(0, height, width, height);

   Mat imdata = new Mat(frame, imgRect);
   Mat thdata = new Mat(frame, thermalRect);

   // 4. Extract Temperature from Center Pixel (128, 96)
   // In Java OpenCV, retrieving pixel data returns a double array.
   double[] centerPixel = thdata.get(96, 128);
   if (centerPixel != null && centerPixel.length >= 2) {
    // centerPixel[0] is 'hi', centerPixel[1] is 'lo'
    double hi = centerPixel[0];

    // Address the uint8 overflow explicitly by casting to int
    int lo = (int) centerPixel[1] * 256;

    double rawTemp = hi + lo;
    double temp = (rawTemp / 64) - 273.15;

    // Format temperature (equivalent to round(temp, 2))
    temp = Math.round(temp * 100.0) / 100.0;
    System.out.println("Center Temp: " + temp + " C");
   }

   // 5. Image Processing (YUV to BGR, Scale, and Colormap)
   Mat bgr = new Mat();
   Imgproc.cvtColor(imdata, bgr, Imgproc.COLOR_YUV2BGR_YUYV);

   // Resize (Scale up)
   Mat resized = new Mat();
   Imgproc.resize(bgr, resized, new Size(width * scale, height * scale), 0, 0, Imgproc.INTER_CUBIC);

   // Apply Colormap (Using JET as default)
   Mat heatmap = new Mat();
   Imgproc.applyColorMap(resized, heatmap, Imgproc.COLORMAP_JET);

   // 6. Display the Image
   HighGui.imshow("Thermal", heatmap);

   // 7. Key Handling (Wait 1ms for key press)
   int key = HighGui.waitKey(1);
   if (key == 113 || key == 81) { // 'q' or 'Q' to quit
    break;
   }
  }

  // Cleanup
  cap.release();
  HighGui.destroyAllWindows();
  System.exit(0);
 }
}
