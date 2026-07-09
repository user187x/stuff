package xxx.com.thermal.camera;

import nu.pattern.OpenCV;
import org.opencv.core.*;
import org.opencv.core.Point;
import org.opencv.imgproc.Imgproc;
import org.opencv.videoio.VideoCapture;
import org.opencv.videoio.Videoio;

import javax.swing.*;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.KeyboardFocusManager;
import java.awt.RenderingHints;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class ThermalCamera {

 static {
  OpenCV.loadLocally();
 }

 // ---- JetBrains Darcula-inspired palette (OpenCV Scalars are BGR) ----
 private static final Scalar BG       = new Scalar(34, 31, 30);    // #1E1F22 editor bg
 private static final Scalar PANEL    = new Scalar(48, 45, 43);    // #2B2D30 panel
 private static final Scalar PANEL_HI = new Scalar(65, 63, 60);    // #3C3F41 hover
 private static final Scalar BORDER   = new Scalar(74, 69, 67);    // #43454A separators
 private static final Scalar ACCENT   = new Scalar(240, 116, 53);  // #3574F0 IntelliJ blue
 private static final Scalar ACCENT2  = new Scalar(109, 198, 255); // #FFC66D Darcula amber
 private static final Scalar TEXT     = new Scalar(229, 225, 223); // #DFE1E5
 private static final Scalar TEXT_DIM = new Scalar(122, 115, 111); // #6F737A
 private static final int FONT = Imgproc.FONT_HERSHEY_SIMPLEX;
 private static final int CORNER = 10; // flatter, modern radius

 private static final int CANVAS_W = 1920;
 private static final int CANVAS_H = 1080;
 private static final int RAIL_W = 380;
 private static final int VIEW_W = CANVAS_W - RAIL_W;
 private static final int VIEW_H = CANVAS_H;

 private int srcW = 256;
 private int srcH = 192;

 private static final String[] LAYOUT_NAMES = {"STACKED", "SIDE-BY-SIDE", "LINE-INT"};
 private int layoutMode = 0;

 private static final int[] STRIDES = {0, 160, 192, 240, 256, 320, 384, 480, 512, 640, 768, 960, 1280};
 private int strideIdx = 0;

 // Known combined image+thermal modes for the TC001 / P2 Pro / Mileseey
 // family. WIDTHxHEIGHT of the full double frame. Probed in this order.
 private static final int[][] CANDIDATE_MODES = {
     {256, 384},   // 256x192 sensor (TC001, P2 Pro, TR256)
     {256, 392},   // same + 8 metadata rows (some InfiRay firmware)
     {640, 1024},  // 640x512 sensor combined
     {320, 480},   // 320x240 sensor combined
     {160, 240},   // 160x120 sensor combined (TR160)
     {384, 576},   // 384x288 sensor combined
 };

 private static Object[][] COLORMAPS = {
     {"JET", Imgproc.COLORMAP_JET, false},
     {"HOT", Imgproc.COLORMAP_HOT, false},
     {"MAGMA", Imgproc.COLORMAP_MAGMA, false},
     {"INFERNO", Imgproc.COLORMAP_INFERNO, false},
     {"PLASMA", Imgproc.COLORMAP_PLASMA, false},
     {"BONE", Imgproc.COLORMAP_BONE, false},
     {"SPRING", Imgproc.COLORMAP_SPRING, false},
     {"AUTUMN", Imgproc.COLORMAP_AUTUMN, false},
     {"VIRIDIS", Imgproc.COLORMAP_VIRIDIS, false},
     {"PARULA", Imgproc.COLORMAP_PARULA, false},
     {"INV RAINBOW", Imgproc.COLORMAP_RAINBOW, true}
 };

 private static Object[][] ROTATIONS = {
     {"0", null},
     {"90", Core.ROTATE_90_CLOCKWISE},
     {"180", Core.ROTATE_180},
     {"270", Core.ROTATE_90_COUNTERCLOCKWISE}
 };

 private int cmapIdx = 3;
 private int rotIdx = 0;
 private int blurRad = 0;
 private int sharpenVal = 0;
 private double alpha = 1.0;
 private int threshold = 2;
 private String unit = "C";
 private boolean hud = true;
 private double snapFlashUntil = 0;
 private double fps = 0.0;
 private long lastTime = System.nanoTime();
 private boolean wantSnapshot = false;
 private volatile boolean running = true;
 private boolean thermalVerified = false;

 private Button btnRotL, btnRotY, btnPal, btnUnit, btnConD, btnConU, btnSnap, btnLayout, btnHud, btnQuit;
 private Slider sldBlur, sldSharp;
 private final List<Object> controls = new ArrayList<>();

 public static void main(String[] args) {
  int deviceIndex = (args.length > 0) ? Integer.parseInt(args[0]) : 0;
  new ThermalCamera().run(deviceIndex);
 }

 public void run(int deviceIndex) {
  initUI();

  MatOfInt openParams = new MatOfInt(Videoio.CAP_PROP_CONVERT_RGB, 0);
  VideoCapture cap = new VideoCapture(deviceIndex, Videoio.CAP_V4L2, openParams);

  if (!cap.isOpened()) {
   System.err.println("Fatal Error: Unable to open camera at index " + deviceIndex);
   return;
  }
  cap.set(Videoio.CAP_PROP_CONVERT_RGB, 0.0);

  printModeInfo(cap, "Default mode");

  // MODE PROBE: the default 640x512@50 mode is a preview/upscaled stream
  // with NO thermal plane - that's why every layout decoded garbage.
  // Ask the hardware for each known combined mode and verify the bottom
  // half actually contains temperature data before accepting it.
  thermalVerified = probeThermalMode(cap);
  if (thermalVerified) {
   printModeInfo(cap, "Selected thermal mode");
   layoutMode = 0; // combined modes are stacked: image top, thermal bottom
  } else {
   System.err.println("WARNING: no combined image+thermal mode found on this");
   System.err.println("video node. Temps will be garbage. This usually means the");
   System.err.println("thermal stream is on the device's OTHER /dev/video node -");
   System.err.println("run: v4l2-ctl --list-devices  and try the other index.");
  }

  JFrame frame = new JFrame("Thermal Viewer");
  frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
  CanvasPanel panel = new CanvasPanel();
  panel.setPreferredSize(new Dimension(1600, 900));
  frame.setContentPane(panel);
  frame.pack();
  frame.setMinimumSize(new Dimension(800, 450));
  frame.setLocationRelativeTo(null);
  frame.setVisible(true);

  setupInputListeners(panel);

  Mat rawFrame = new Mat();
  while (running) {
   if (!cap.read(rawFrame) || rawFrame.empty()) break;

   if (rawFrame.channels() != 2) {
    System.err.println("Frame is not raw 2-channel YUYV (" + rawFrame.size()
        + " ch=" + rawFrame.channels() + "). Raw mode not active - aborting.");
    break;
   }

   Mat buf = rawFrame;
   int stride = STRIDES[strideIdx];
   if (stride > 0 && stride != rawFrame.cols()) {
    long totalPx = (long) rawFrame.cols() * rawFrame.rows();
    if (totalPx % stride == 0) {
     buf = rawFrame.reshape(2, (int) (totalPx / stride));
    }
   }

   Mat imdata, thdata;
   switch (layoutMode) {
    case 1: {
     srcW = buf.cols() / 2;
     srcH = buf.rows();
     imdata = buf.submat(new Rect(0, 0, srcW, srcH));
     thdata = buf.submat(new Rect(srcW, 0, srcW, srcH));
     break;
    }
    case 2: {
     srcW = buf.cols();
     srcH = buf.rows() / 2;
     imdata = new Mat(srcH, srcW, buf.type());
     thdata = new Mat(srcH, srcW, buf.type());
     for (int r = 0; r < srcH; r++) {
      buf.row(2 * r).copyTo(imdata.row(r));
      buf.row(2 * r + 1).copyTo(thdata.row(r));
     }
     break;
    }
    default: {
     srcW = buf.cols();
     srcH = buf.rows() / 2;
     imdata = buf.submat(new Rect(0, 0, srcW, srcH));
     thdata = buf.submat(new Rect(0, srcH, srcW, srcH));
     break;
    }
   }

   calculateFPS();
   Mat canvas = composeCanvas(imdata, thdata);

   if (wantSnapshot) {
    wantSnapshot = false;
    String name = "Thermal-" + System.currentTimeMillis() + ".png";
    org.opencv.imgcodecs.Imgcodecs.imwrite(name, canvas);
    snapFlashUntil = (System.nanoTime() / 1e9) + 0.25;
    btnSnap.sub = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
   }

   BufferedImage img = matToBufferedImage(canvas);
   panel.setImage(img);

   imdata.release();
   thdata.release();
   canvas.release();
  }

  cap.release();
  frame.dispose();
  System.exit(0);
 }

 private static void printModeInfo(VideoCapture cap, String label) {
  int fourcc = (int) cap.get(Videoio.CAP_PROP_FOURCC);
  System.out.println(label + ": "
      + (char) (fourcc & 0xFF) + (char) ((fourcc >> 8) & 0xFF)
      + (char) ((fourcc >> 16) & 0xFF) + (char) ((fourcc >> 24) & 0xFF)
      + "  " + (int) cap.get(Videoio.CAP_PROP_FRAME_WIDTH) + "x"
      + (int) cap.get(Videoio.CAP_PROP_FRAME_HEIGHT)
      + " @ " + cap.get(Videoio.CAP_PROP_FPS) + " fps");
 }

 /**
  * Try each candidate combined mode; accept the first one where the camera
  * honors the resolution AND the bottom half decodes as believable
  * temperature data (Kelvin*64 -> mostly -20..150 C for a normal scene).
  */
 private boolean probeThermalMode(VideoCapture cap) {
  Mat test = new Mat();
  for (int[] mode : CANDIDATE_MODES) {
   cap.set(Videoio.CAP_PROP_FRAME_WIDTH, mode[0]);
   cap.set(Videoio.CAP_PROP_FRAME_HEIGHT, mode[1]);
   // flush one frame; the first after a mode switch can be stale
   cap.read(test);
   if (!cap.read(test) || test.empty()) continue;

   boolean sizeOk = test.cols() == mode[0] && test.rows() == mode[1];
   boolean thermal = sizeOk && bottomHalfLooksThermal(test);
   System.out.println("Probe " + mode[0] + "x" + mode[1] + ": "
       + (sizeOk ? "accepted" : "rejected (got " + test.cols() + "x" + test.rows() + ")")
       + (sizeOk ? (thermal ? ", thermal data VERIFIED" : ", bottom half is not thermal") : ""));
   if (thermal) return true;
  }
  return false;
 }

 private boolean bottomHalfLooksThermal(Mat frame) {
  if (frame.channels() != 2 || frame.rows() % 2 != 0) return false;
  int w = frame.cols(), h = frame.rows() / 2;
  Mat th = frame.submat(new Rect(0, h, w, h));
  Mat cont = th.isContinuous() ? th : th.clone();
  byte[] buf = new byte[w * h * 2];
  cont.get(0, 0, buf);
  if (cont != th) cont.release();

  int samples = 0, plausible = 0;
  for (int r = h / 8; r < h; r += Math.max(1, h / 8)) {
   for (int c = w / 8; c < w; c += Math.max(1, w / 8)) {
    int i = (r * w + c) * 2;
    int raw = (buf[i] & 0xFF) | ((buf[i + 1] & 0xFF) << 8);
    double cel = (raw / 64.0) - 273.15;
    samples++;
    if (cel > -20 && cel < 150) plausible++;
   }
  }
  return samples > 0 && plausible >= samples * 0.9;
 }

 private static class CanvasPanel extends JPanel {
  private volatile BufferedImage image;
  private volatile double scale = 1.0;
  private volatile int offX = 0, offY = 0;

  CanvasPanel() { setBackground(new Color(30, 31, 34)); setFocusable(true); } // #1E1F22

  void setImage(BufferedImage img) {
   this.image = img;
   repaint();
  }

  Point toCanvas(int x, int y) {
   if (scale <= 0) return new Point(-1, -1);
   return new Point((x - offX) / scale, (y - offY) / scale);
  }

  @Override
  protected void paintComponent(Graphics g) {
   super.paintComponent(g);
   BufferedImage img = image;
   if (img == null) return;
   double s = Math.min((double) getWidth() / img.getWidth(),
       (double) getHeight() / img.getHeight());
   int dw = (int) (img.getWidth() * s);
   int dh = (int) (img.getHeight() * s);
   offX = (getWidth() - dw) / 2;
   offY = (getHeight() - dh) / 2;
   scale = s;
   Graphics2D g2 = (Graphics2D) g;
   g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
       RenderingHints.VALUE_INTERPOLATION_BILINEAR);
   g2.drawImage(img, offX, offY, dw, dh, null);
  }
 }

 private void setupInputListeners(CanvasPanel panel) {
  panel.addMouseListener(new MouseAdapter() {
   @Override
   public void mousePressed(MouseEvent e) {
    Point p = panel.toCanvas(e.getX(), e.getY());
    int x = (int) p.x, y = (int) p.y;
    for (Object ctrl : controls) {
     if (ctrl instanceof Slider s && s.hit(x, y)) { s.dragging = true; s.setFromX(x); return; }
     if (ctrl instanceof Button b && b.hit(x, y)) {
      b.flashUntil = (System.nanoTime() / 1e9) + 0.15;
      b.action.run(); return;
     }
    }
    if (x >= 0 && x < VIEW_W) actHud();
   }

   @Override
   public void mouseReleased(MouseEvent e) {
    sldBlur.dragging = false; sldSharp.dragging = false;
   }
  });

  panel.addMouseMotionListener(new MouseMotionAdapter() {
   @Override
   public void mouseDragged(MouseEvent e) {
    Point p = panel.toCanvas(e.getX(), e.getY());
    if (sldBlur.dragging) sldBlur.setFromX((int) p.x);
    if (sldSharp.dragging) sldSharp.setFromX((int) p.x);
   }
  });

  KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(e -> {
   if (e.getID() != KeyEvent.KEY_PRESSED) return false;
   switch (Character.toLowerCase(e.getKeyChar())) {
    case 'q' -> running = false;
    case 'r' -> actRotCW();
    case 'e' -> actRotCCW();
    case 'm' -> actCmap();
    case 'l' -> actLayout();
    case '[' -> actStride(-1);
    case ']' -> actStride(+1);
    case 'u' -> actUnit();
    case 'a' -> setBlur(blurRad + 1);
    case 'z' -> setBlur(blurRad - 1);
    case 'd' -> setSharpen(sharpenVal + 1);
    case 'c' -> setSharpen(sharpenVal - 1);
    case 'f' -> actConUp();
    case 'v' -> actConDn();
    case 's' -> threshold++;
    case 'x' -> threshold = Math.max(0, threshold - 1);
    case 'p' -> wantSnapshot = true;
    case 'h' -> actHud();
   }
   return false;
  });
 }

 private BufferedImage matToBufferedImage(Mat mat) {
  int type = (mat.channels() > 1) ? BufferedImage.TYPE_3BYTE_BGR : BufferedImage.TYPE_BYTE_GRAY;
  int bufferSize = mat.channels() * mat.cols() * mat.rows();
  byte[] b = new byte[bufferSize];
  mat.get(0, 0, b);
  BufferedImage image = new BufferedImage(mat.cols(), mat.rows(), type);
  final byte[] targetPixels = ((DataBufferByte) image.getRaster().getDataBuffer()).getData();
  System.arraycopy(b, 0, targetPixels, 0, b.length);
  return image;
 }

 private void initUI() {
  btnRotL = new Button("< ROT", this::actRotCCW, "0 deg", false);
  btnRotY = new Button("ROT >", this::actRotCW, "0 deg", false);
  btnPal = new Button("PALETTE", this::actCmap, (String) COLORMAPS[cmapIdx][0], false);
  btnUnit = new Button("UNITS", this::actUnit, "deg C", false);
  btnConD = new Button("CON -", this::actConDn, "", false);
  btnConU = new Button("CON +", this::actConUp, "1.0", false);
  sldBlur = new Slider("BLUR", () -> blurRad, this::setBlur, 0, 20);
  sldSharp = new Slider("SHARPEN", () -> sharpenVal, this::setSharpen, 0, 10);
  btnSnap = new Button("SNAPSHOT", () -> wantSnapshot = true, "save PNG", false);
  btnLayout = new Button("LAYOUT", this::actLayout, LAYOUT_NAMES[layoutMode], false);
  btnHud = new Button("HUD", this::actHud, "on", true);
  btnQuit = new Button("QUIT", () -> running = false, "", false);

  btnHud.active = true;

  List<List<Object>> rows = List.of(
      List.of(btnRotL, btnRotY),
      List.of(btnPal, btnUnit),
      List.of(btnConD, btnConU),
      List.of(sldBlur),
      List.of(sldSharp),
      List.of(btnSnap, btnLayout),
      List.of(btnHud, btnQuit)
  );

  int pad = 20;
  int bh = (CANVAS_H - pad * (rows.size() + 1)) / rows.size();
  int y = pad;

  for (List<Object> row : rows) {
   int bw = (RAIL_W - pad * (row.size() + 1)) / row.size();
   int x = CANVAS_W - RAIL_W + pad;
   for (Object control : row) {
    if (control instanceof Button b) b.rect = new Rect(x, y, bw, bh);
    if (control instanceof Slider s) s.rect = new Rect(x, y, bw, bh);
    controls.add(control);
    x += bw + pad;
   }
   y += bh + pad;
  }
 }

 private Mat composeCanvas(Mat imdata, Mat thdata) {
  Mat canvas = new Mat(new Size(CANVAS_W, CANVAS_H), CvType.CV_8UC3, BG);
  ThermalMetrics metrics = evaluateThermalData(thdata);

  ProcessedFrame processed = renderHeatmap(imdata);
  Mat heat = processed.mat;

  Point centerCanvasPt = mapThermalPoint(srcW / 2, srcH / 2, processed.tw, processed.th);
  Imgproc.circle(heat, centerCanvasPt, 18, ACCENT, 2, Imgproc.LINE_AA);
  Imgproc.line(heat, new Point(centerCanvasPt.x - 30, centerCanvasPt.y), new Point(centerCanvasPt.x - 14, centerCanvasPt.y), ACCENT, 2, Imgproc.LINE_AA);
  Imgproc.line(heat, new Point(centerCanvasPt.x + 14, centerCanvasPt.y), new Point(centerCanvasPt.x + 30, centerCanvasPt.y), ACCENT, 2, Imgproc.LINE_AA);
  Imgproc.line(heat, new Point(centerCanvasPt.x, centerCanvasPt.y - 30), new Point(centerCanvasPt.x, centerCanvasPt.y - 14), ACCENT, 2, Imgproc.LINE_AA);
  Imgproc.line(heat, new Point(centerCanvasPt.x, centerCanvasPt.y + 14), new Point(centerCanvasPt.x, centerCanvasPt.y + 30), ACCENT, 2, Imgproc.LINE_AA);
  drawOutlinedText(heat, formatTemp(metrics.centerTemp) + " " + unit, new Point(centerCanvasPt.x + 25, centerCanvasPt.y - 25), 0.85, ACCENT, 2);

  if (metrics.maxTemp > metrics.avgTemp + threshold) {
   Point maxPt = mapThermalPoint(metrics.maxCol, metrics.maxRow, processed.tw, processed.th);
   Imgproc.circle(heat, maxPt, 10, new Scalar(0, 0, 0), 3);
   Imgproc.circle(heat, maxPt, 10, new Scalar(70, 82, 244), -1); // #F45246 red
   drawOutlinedText(heat, formatTemp(metrics.maxTemp) + " " + unit, new Point(maxPt.x + 18, maxPt.y + 10), 0.85, TEXT, 2);
  }

  if (metrics.minTemp < metrics.avgTemp - threshold) {
   Point minPt = mapThermalPoint(metrics.minCol, metrics.minRow, processed.tw, processed.th);
   Imgproc.circle(heat, minPt, 10, new Scalar(0, 0, 0), 3);
   Imgproc.circle(heat, minPt, 10, new Scalar(250, 168, 87), -1); // #57A8FA light blue
   drawOutlinedText(heat, formatTemp(metrics.minTemp) + " " + unit, new Point(minPt.x + 18, minPt.y + 10), 0.85, ACCENT2, 2);
  }

  int ox = (VIEW_W - 60 - heat.cols()) / 2 + 60;
  int oy = (VIEW_H - heat.rows()) / 2;
  Rect roi = new Rect(ox, oy, heat.cols(), heat.rows());
  heat.copyTo(canvas.submat(roi));

  drawRoundedRect(canvas, new Point(ox - 2, oy - 2), new Point(ox + heat.cols() + 2, oy + heat.rows() + 2), BORDER, 8, false, 2);

  if (hud) {
   drawScaleBar(canvas, metrics.minTemp, metrics.maxTemp, oy, heat.rows());
   drawStatusStrip(canvas, metrics, ox, oy);
   drawHistogram(canvas, metrics.allTemps, metrics.minTemp, metrics.maxTemp);
  }

  for (Object ctrl : controls) {
   if (ctrl instanceof Button b) b.draw(canvas);
   if (ctrl instanceof Slider s) s.draw(canvas);
  }

  if ((System.nanoTime() / 1e9) < snapFlashUntil) {
   Imgproc.rectangle(canvas, new Point(0, 0), new Point(VIEW_W, CANVAS_H), new Scalar(255, 255, 255), -1);
  }

  heat.release();
  return canvas;
 }

 private ProcessedFrame renderHeatmap(Mat imdata) {
  Mat bgr = new Mat();
  Imgproc.cvtColor(imdata, bgr, Imgproc.COLOR_YUV2BGR_YUYV);
  bgr.convertTo(bgr, -1, alpha, 0);

  Integer rotFlag = (Integer) ROTATIONS[rotIdx][1];
  if (rotFlag != null) Core.rotate(bgr, bgr, rotFlag);

  int rawH = bgr.rows();
  int rawW = bgr.cols();
  double k = Math.min((double) (VIEW_W - 40 - 60) / rawW, (double) (VIEW_H - 40) / rawH);
  int tw = Math.max(2, (int) (rawW * k));
  int th = Math.max(2, (int) (rawH * k));

  Imgproc.resize(bgr, bgr, new Size(tw, th), 0, 0, Imgproc.INTER_LANCZOS4);

  if (sharpenVal > 0) {
   double amount = sharpenVal / 2.0;
   Mat blurred = new Mat();
   Imgproc.GaussianBlur(bgr, blurred, new Size(0, 0), 2.0);
   Core.addWeighted(bgr, 1.0 + amount, blurred, -amount, 0, bgr);
   blurred.release();
  }

  if (blurRad > 0) {
   Imgproc.blur(bgr, bgr, new Size(blurRad, blurRad));
  }

  int cmap = (int) COLORMAPS[cmapIdx][1];
  boolean invert = (boolean) COLORMAPS[cmapIdx][2];
  Imgproc.applyColorMap(bgr, bgr, cmap);

  if (invert) Core.bitwise_not(bgr, bgr);

  return new ProcessedFrame(bgr, tw, th);
 }

 private Point mapThermalPoint(int col, int row, int tw, int th) {
  int rx = col, ry = row, rw = srcW, rh = srcH;
  if (rotIdx == 1) {
   rx = srcH - 1 - row; ry = col; rw = srcH; rh = srcW;
  } else if (rotIdx == 2) {
   rx = srcW - 1 - col; ry = srcH - 1 - row;
  } else if (rotIdx == 3) {
   rx = row; ry = srcW - 1 - col; rw = srcH; rh = srcW;
  }
  return new Point((int) (rx * ((double) tw / rw)), (int) (ry * ((double) th / rh)));
 }

 private ThermalMetrics evaluateThermalData(Mat thdata) {
  int w = thdata.cols(), h = thdata.rows();
  ThermalMetrics m = new ThermalMetrics(w * h);

  Mat cont = thdata.isContinuous() ? thdata : thdata.clone();
  byte[] buf = new byte[w * h * 2];
  cont.get(0, 0, buf);
  if (cont != thdata) cont.release();

  double maxRaw = -9999, minRaw = 99999, total = 0;
  int ci = h / 2, cj = w / 2;

  for (int r = 0; r < h; r++) {
   int rowOff = r * w * 2;
   for (int c = 0; c < w; c++) {
    int i = rowOff + c * 2;
    int raw = (buf[i] & 0xFF) | ((buf[i + 1] & 0xFF) << 8);
    double cel = (raw / 64.0) - 273.15;
    m.allTemps[r * w + c] = cel;
    total += cel;

    if (cel > maxRaw) { maxRaw = cel; m.maxRow = r; m.maxCol = c; }
    if (cel < minRaw) { minRaw = cel; m.minRow = r; m.minCol = c; }
    if (r == ci && c == cj) m.centerTemp = cel;
   }
  }
  m.maxTemp = maxRaw; m.minTemp = minRaw; m.avgTemp = total / (w * h);
  return m;
 }

 private void drawScaleBar(Mat canvas, double lo, double hi, int oy, int th) {
  int bx = 30, bw = 30, by = oy + 45, bh = th - 90;
  if (bh <= 0) return;
  Mat ramp = new Mat(bh, 1, CvType.CV_8UC1);
  for (int i = 0; i < bh; i++) {
   ramp.put(i, 0, (int) (255 - ((double) i / bh * 255)));
  }
  Imgproc.applyColorMap(ramp, ramp, (int) COLORMAPS[cmapIdx][1]);
  if ((boolean) COLORMAPS[cmapIdx][2]) Core.bitwise_not(ramp, ramp);

  Mat barRoi = canvas.submat(new Rect(bx, by, bw, bh));
  Imgproc.resize(ramp, barRoi, new Size(bw, bh));

  drawRoundedRect(canvas, new Point(bx - 2, by - 2), new Point(bx + bw + 2, by + bh + 2), BORDER, 4, false, 2);
  Imgproc.putText(canvas, formatTemp(hi, 0) + unit, new Point(bx - 10, by - 15), FONT, 0.75, TEXT, 2, Imgproc.LINE_AA);
  Imgproc.putText(canvas, formatTemp(lo, 0) + unit, new Point(bx - 10, by + bh + 30), FONT, 0.75, ACCENT2, 2, Imgproc.LINE_AA);
  ramp.release();
 }

 private void drawStatusStrip(Mat canvas, ThermalMetrics m, int ox, int oy) {
  int sy2 = Math.min(oy + 60, CANVAS_H);
  int sx2 = Math.min(ox + 1200, CANVAS_W);
  Mat strip = canvas.submat(new Rect(ox + 15, oy + 12, sx2 - (ox + 15), sy2 - (oy + 12)));
  strip.convertTo(strip, -1, 0.35, 0);

  int stride = STRIDES[strideIdx];
  String mode = LAYOUT_NAMES[layoutMode] + (stride > 0 ? "/" + stride : "")
      + (thermalVerified ? "" : " !NO-THERMAL");
  Imgproc.putText(canvas, String.format("CTR %s%s", formatTemp(m.centerTemp), unit), new Point(ox + 30, oy + 45), FONT, 0.9, ACCENT, 2, Imgproc.LINE_AA);
  Imgproc.putText(canvas, String.format("MAX %s", formatTemp(m.maxTemp)), new Point(ox + 270, oy + 45), FONT, 0.85, TEXT, 2, Imgproc.LINE_AA);
  Imgproc.putText(canvas, String.format("MIN %s", formatTemp(m.minTemp)), new Point(ox + 470, oy + 45), FONT, 0.85, ACCENT2, 2, Imgproc.LINE_AA);
  Imgproc.putText(canvas, String.format("AVG %s", formatTemp(m.avgTemp)), new Point(ox + 670, oy + 45), FONT, 0.85, TEXT_DIM, 2, Imgproc.LINE_AA);
  Imgproc.putText(canvas, String.format("THR %dC", threshold), new Point(ox + 870, oy + 45), FONT, 0.85, TEXT_DIM, 2, Imgproc.LINE_AA);
  Imgproc.putText(canvas, String.format("%.1f FPS [%s]", fps, mode), new Point(ox + 1020, oy + 45), FONT, 0.85, TEXT_DIM, 2, Imgproc.LINE_AA);
 }

 private void drawHistogram(Mat canvas, double[] data, double lo, double hi) {
  int w = 450, h = 220, x0 = VIEW_W - w - 40, y0 = CANVAS_H - h - 40;
  Mat panel = canvas.submat(new Rect(x0, y0, w, h));
  panel.convertTo(panel, -1, 0.25, 0);
  Core.add(panel, PANEL.mul(new Scalar(0.75)), panel);

  drawRoundedRect(canvas, new Point(x0, y0), new Point(x0 + w, y0 + h), BORDER, CORNER, false, 2);
  Imgproc.putText(canvas, "THERMAL DISTRIBUTION", new Point(x0 + 20, y0 + 35), FONT, 0.7, TEXT_DIM, 2, Imgproc.LINE_AA);

  int bins = 48; int[] hist = new int[bins]; int peak = 1;
  double range = hi - lo;
  if (range <= 0) range = 1.0;

  for (double t : data) {
   int bin = (int) (((t - lo) / range) * bins);
   if (bin >= 0 && bin < bins) {
    hist[bin]++;
    if (hist[bin] > peak) peak = hist[bin];
   }
  }

  Mat ramp = new Mat(1, bins, CvType.CV_8UC1);
  for (int i = 0; i < bins; i++) ramp.put(0, i, (int) ((double) i / bins * 255));
  Imgproc.applyColorMap(ramp, ramp, (int) COLORMAPS[cmapIdx][1]);
  if ((boolean) COLORMAPS[cmapIdx][2]) Core.bitwise_not(ramp, ramp);

  int baseY = y0 + h - 45; double bw = (w - 40) / (double) bins;
  for (int i = 0; i < bins; i++) {
   int bh = (int) (((double) hist[i] / peak) * (h - 100));
   int bx = (int) (x0 + 20 + i * bw);
   double[] c = ramp.get(0, i);
   Imgproc.rectangle(canvas, new Point(bx, baseY - bh), new Point(bx + Math.max((int) bw - 1, 1), baseY), new Scalar(c[0], c[1], c[2]), -1);
  }
  Imgproc.putText(canvas, formatTemp(lo, 0) + unit, new Point(x0 + 20, y0 + h - 15), FONT, 0.7, TEXT_DIM, 2, Imgproc.LINE_AA);
  Imgproc.putText(canvas, formatTemp(hi, 0) + unit, new Point(x0 + w - 90, y0 + h - 15), FONT, 0.7, TEXT_DIM, 2, Imgproc.LINE_AA);
  ramp.release();
 }

 private void actRotCW() { rotIdx = (rotIdx + 1) % 4; syncRotLabels(); }
 private void actRotCCW() { rotIdx = (rotIdx - 1 + 4) % 4; syncRotLabels(); }
 private void syncRotLabels() { String label = ROTATIONS[rotIdx][0] + " deg"; btnRotL.sub = label; btnRotY.sub = label; }
 private void actCmap() { cmapIdx = (cmapIdx + 1) % COLORMAPS.length; btnPal.sub = (String) COLORMAPS[cmapIdx][0]; }
 private void actLayout() { layoutMode = (layoutMode + 1) % LAYOUT_NAMES.length; syncLayoutLabel(); }
 private void actStride(int dir) {
  strideIdx = (strideIdx + dir + STRIDES.length) % STRIDES.length;
  syncLayoutLabel();
 }
 private void syncLayoutLabel() {
  int stride = STRIDES[strideIdx];
  btnLayout.sub = LAYOUT_NAMES[layoutMode] + (stride > 0 ? " " + stride : "");
 }
 private void actUnit() { unit = unit.equals("C") ? "F" : "C"; btnUnit.sub = "deg " + unit; }
 private void actConUp() { alpha = Math.min(3.0, alpha + 0.1); btnConU.sub = String.format("%.1f", alpha); }
 private void actConDn() { alpha = Math.max(0.0, alpha - 0.1); btnConU.sub = String.format("%.1f", alpha); }
 private void setBlur(int v) { blurRad = Math.clamp(v, 0, 20); }
 private void setSharpen(int v) { sharpenVal = Math.clamp(v, 0, 10); }
 private void actHud() { hud = !hud; btnHud.active = hud; btnHud.sub = hud ? "on" : "off"; }
 private void calculateFPS() {
  long now = System.nanoTime(); double dt = (now - lastTime) / 1e9; lastTime = now;
  if (dt > 0) fps = (fps == 0) ? (1.0 / dt) : (fps * 0.9 + (1.0 / dt) * 0.1);
 }
 private String formatTemp(double c) { return formatTemp(c, 1); }
 private String formatTemp(double c, int dec) {
  double v = unit.equals("F") ? (c * 9.0 / 5.0 + 32.0) : c;
  return String.format("%." + dec + "f", v);
 }

 private static void drawOutlinedText(Mat img, String s, Point org, double scale, Scalar color, int thick) {
  Imgproc.putText(img, s, org, FONT, scale, new Scalar(0, 0, 0), thick + 1, Imgproc.LINE_AA);
  Imgproc.putText(img, s, org, FONT, scale, color, thick, Imgproc.LINE_AA);
 }

 private static void drawRoundedRect(Mat img, Point p1, Point p2, Scalar color, int r, boolean fill, int thick) {
  int x1 = (int) p1.x, y1 = (int) p1.y, x2 = (int) p2.x, y2 = (int) p2.y;
  if (fill) {
   Imgproc.rectangle(img, new Point(x1 + r, y1), new Point(x2 - r, y2), color, -1);
   Imgproc.rectangle(img, new Point(x1, y1 + r), new Point(x2, y2 - r), color, -1);
   for (Point c : List.of(new Point(x1 + r, y1 + r), new Point(x2 - r, y1 + r), new Point(x1 + r, y2 - r), new Point(x2 - r, y2 - r)))
    Imgproc.circle(img, c, r, color, -1, Imgproc.LINE_AA);
  } else {
   Imgproc.line(img, new Point(x1 + r, y1), new Point(x2 - r, y1), color, thick, Imgproc.LINE_AA);
   Imgproc.line(img, new Point(x1 + r, y2), new Point(x2 - r, y2), color, thick, Imgproc.LINE_AA);
   Imgproc.line(img, new Point(x1, y1 + r), new Point(x1, y2 - r), color, thick, Imgproc.LINE_AA);
   Imgproc.line(img, new Point(x2, y1 + r), new Point(x2, y2 - r), color, thick, Imgproc.LINE_AA);
   Imgproc.ellipse(img, new Point(x1 + r, y1 + r), new Size(r, r), 180, 0, 90, color, thick, Imgproc.LINE_AA);
   Imgproc.ellipse(img, new Point(x2 - r, y1 + r), new Size(r, r), 270, 0, 90, color, thick, Imgproc.LINE_AA);
   Imgproc.ellipse(img, new Point(x2 - r, y2 - r), new Size(r, r), 0, 0, 90, color, thick, Imgproc.LINE_AA);
   Imgproc.ellipse(img, new Point(x1 + r, y2 - r), new Size(r, r), 90, 0, 90, color, thick, Imgproc.LINE_AA);
  }
 }

 private static class Button {
  String label, sub; Runnable action; boolean toggle, active = false; Rect rect; double flashUntil = 0;
  Button(String l, Runnable a, String s, boolean t) { label = l; action = a; sub = s; toggle = t; }
  boolean hit(int mx, int my) { return rect != null && rect.contains(new Point(mx, my)); }
  void draw(Mat img) {
   boolean hot = (System.nanoTime() / 1e9) < flashUntil;
   Scalar base = (hot || (toggle && active)) ? PANEL_HI : PANEL;
   drawRoundedRect(img, rect.tl(), rect.br(), base, CORNER, true, 1);
   Scalar edge = (toggle && active) ? ACCENT : BORDER;
   drawRoundedRect(img, rect.tl(), rect.br(), edge, CORNER, false, 2);
   Imgproc.putText(img, label, new Point(rect.x + 25, rect.y + 55), FONT, 1.0, (toggle && active) ? ACCENT : TEXT, 2, Imgproc.LINE_AA);
   if (!sub.isEmpty()) Imgproc.putText(img, sub, new Point(rect.x + 25, rect.y + rect.height - 20), FONT, 0.75, TEXT_DIM, 2, Imgproc.LINE_AA);
  }
 }

 private static class Slider {
  String label; java.util.function.Supplier<Integer> getter; java.util.function.Consumer<Integer> setter;
  int vmin, vmax; Rect rect; boolean dragging = false;
  Slider(String l, java.util.function.Supplier<Integer> g, java.util.function.Consumer<Integer> s, int mn, int mx) {
   label = l; getter = g; setter = s; vmin = mn; vmax = mx;
  }
  boolean hit(int mx, int my) { return rect != null && rect.contains(new Point(mx, my)); }
  void setFromX(int mx) {
   int tx = rect.x + 32, tw = rect.width - 64;
   double frac = Math.clamp((double) (mx - tx) / Math.max(tw, 1), 0.0, 1.0);
   setter.accept((int) Math.round(vmin + frac * (vmax - vmin)));
  }
  void draw(Mat img) {
   drawRoundedRect(img, rect.tl(), rect.br(), dragging ? PANEL_HI : PANEL, CORNER, true, 1);
   drawRoundedRect(img, rect.tl(), rect.br(), BORDER, CORNER, false, 2);
   int val = getter.get();
   Imgproc.putText(img, label, new Point(rect.x + 25, rect.y + 45), FONT, 0.9, TEXT, 2, Imgproc.LINE_AA);
   String vstr = val == 0 ? "off" : String.valueOf(val);
   int[] baseLine = new int[1];
   Size tSize = Imgproc.getTextSize(vstr, FONT, 0.9, 2, baseLine);
   Imgproc.putText(img, vstr, new Point(rect.x + rect.width - 25 - tSize.width, rect.y + 45), FONT, 0.9, val > 0 ? ACCENT : TEXT_DIM, 2, Imgproc.LINE_AA);

   int tx = rect.x + 32, ty = rect.y + rect.height - 40, tw = rect.width - 64, span = vmax - vmin;
   Imgproc.line(img, new Point(tx, ty), new Point(tx + tw, ty), BORDER, 6, Imgproc.LINE_AA);

   int step = Math.max(1, span / 10);
   for (int i = vmin; i <= vmax; i += step) {
    int fx = (int) (tx + ((double) (i - vmin) / span) * tw);
    Imgproc.line(img, new Point(fx, ty - 8), new Point(fx, ty + 8), TEXT_DIM, 2, Imgproc.LINE_AA);
   }
   int kx = (int) (tx + ((double) (val - vmin) / span) * tw);
   Imgproc.line(img, new Point(tx, ty), new Point(kx, ty), ACCENT, 6, Imgproc.LINE_AA);
   Imgproc.circle(img, new Point(kx, ty), 16, ACCENT, -1, Imgproc.LINE_AA);
   Imgproc.circle(img, new Point(kx, ty), 16, BG, 2, Imgproc.LINE_AA);
  }
 }

 private static class ThermalMetrics {
  double centerTemp, maxTemp, minTemp, avgTemp;
  int maxRow, maxCol, minRow, minCol;
  final double[] allTemps;
  ThermalMetrics(int n) { allTemps = new double[n]; }
 }

 private record ProcessedFrame(Mat mat, int tw, int th) {}
}