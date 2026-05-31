package xxx.com.swing.cursor;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class CustomMouseCursor {
  public static void main(String[] args) {
    JFrame frame = new JFrame("Custom Cursor Demo");
    frame.setSize(300, 200);
    frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

    JPanel panel = new JPanel();

    // Load your custom cursor image (replace "path/to/your/image.png" with the actual path)
    Toolkit toolkit = Toolkit.getDefaultToolkit();
    Image customCursorImage = toolkit.getImage("png/slug.gif");

    // Define the hotspot (the point within the image that acts as the cursor's tip)
    Point hotSpot = new Point(0, 0); // Top-left corner of the image

    // Create the custom cursor
    Cursor customCursor = toolkit.createCustomCursor(customCursorImage, hotSpot, "MyCustomCursor");

    // Set the custom cursor for the panel
    panel.setCursor(customCursor);

    frame.add(panel);
    frame.setVisible(true);
  }

  public static void animatedGifCursor() {

    JFrame frame = new JFrame("Custom Animated Cursor Demo");
    frame.setSize(400, 300);
    frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

    JPanel panel = new JPanel();
    frame.add(panel);

    // --- Animated Cursor Logic ---

    // 1. Load the images for each frame of your animation.
    //    Replace these with the actual paths to your image files.
    String[] imagePaths = {
        "png/slug1.gif",
        "png/slug2.gif",
        "png/slug3.gif",
        "png/slug4.gif"
        // Add more frames if needed
    };

    Toolkit toolkit = Toolkit.getDefaultToolkit();
    final Cursor[] cursors = new Cursor[imagePaths.length];
    Point hotSpot = new Point(0, 0); // Define the cursor hotspot

    for (int i = 0; i < imagePaths.length; i++) {
      Image image = toolkit.getImage(imagePaths[i]);
      cursors[i] = toolkit.createCustomCursor(image, hotSpot, "animatedCursor" + i);
    }

    // 2. Create a Timer to cycle through the cursors.
    Timer timer = new Timer(100, new ActionListener() { // 100ms delay between frames
      private int currentFrame = 0;

      @Override
      public void actionPerformed(ActionEvent e) {
        panel.setCursor(cursors[currentFrame]);
        currentFrame = (currentFrame + 1) % cursors.length;
      }
    });

    // 3. Start the animation.
    timer.start();


    frame.setVisible(true);
  }
}
