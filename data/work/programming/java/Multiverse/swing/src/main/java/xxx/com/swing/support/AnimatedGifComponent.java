package xxx.com.swing.support;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * A custom JComponent that properly handles animated GIF playback in Swing.
 * This component manually manages frame timing and animation to avoid Swing's
 * built-in GIF animation issues.
 */
public class AnimatedGifComponent extends JComponent {

    private List<BufferedImage> frames;
    private List<Integer> delays;
    private int currentFrame = 0;
    private Timer animationTimer;
    private boolean isAnimating = false;
    private byte[] gifData;

    /**
     * Creates an AnimatedGifComponent from GIF byte data
     *
     * @param gifData The GIF file as byte array
     */
    public AnimatedGifComponent(byte[] gifData) {
        this.gifData = gifData;
        this.frames = new ArrayList<>();
        this.delays = new ArrayList<>();

        loadGifFrames();
        setupAnimation();

        // Set preferred size based on first frame
        if (!frames.isEmpty()) {
            BufferedImage firstFrame = frames.get(0);
            setPreferredSize(new Dimension(firstFrame.getWidth(), firstFrame.getHeight()));
        }
    }

    /**
     * Creates an AnimatedGifComponent from an ImageIcon containing a GIF
     *
     * @param gifIcon ImageIcon containing animated GIF data
     */
    public AnimatedGifComponent(ImageIcon gifIcon) {
        // Try to extract byte data from ImageIcon
        // This is a workaround since ImageIcon doesn't expose the original bytes
        this.frames = new ArrayList<>();
        this.delays = new ArrayList<>();

        // For ImageIcon, we'll use a simpler approach with a fixed delay
        Image image = gifIcon.getImage();
        if (image instanceof BufferedImage) {
            frames.add((BufferedImage) image);
            delays.add(100); // 100ms default delay
        } else {
            // Convert to BufferedImage
            BufferedImage buffered = new BufferedImage(
                    gifIcon.getIconWidth(),
                    gifIcon.getIconHeight(),
                    BufferedImage.TYPE_INT_ARGB
            );
            Graphics2D g2d = buffered.createGraphics();
            g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            gifIcon.paintIcon(null, g2d, 0, 0);
            g2d.dispose();

            frames.add(buffered);
            delays.add(100);
        }

        setupAnimation();
        setPreferredSize(new Dimension(gifIcon.getIconWidth(), gifIcon.getIconHeight()));
    }

    /**
     * Load all frames and delays from the GIF data
     */
    private void loadGifFrames() {
        if (gifData == null) return;

        try (ImageInputStream stream = ImageIO.createImageInputStream(new ByteArrayInputStream(gifData))) {
            ImageReader reader = ImageIO.getImageReadersByFormatName("gif").next();
            reader.setInput(stream);

            int numFrames = reader.getNumImages(true);
            System.out.println("DEBUG: Loading " + numFrames + " frames from GIF");

            for (int i = 0; i < numFrames; i++) {
                BufferedImage frame = reader.read(i);
                frames.add(frame);

                // Get frame delay (in centiseconds, convert to milliseconds)
                javax.imageio.metadata.IIOMetadata metadata = reader.getImageMetadata(i);
                int delay = extractDelay(metadata);
                delays.add(delay);

                System.out.println("DEBUG: Frame " + i + " delay: " + delay + "ms");
            }

            reader.dispose();

        } catch (IOException e) {
            System.err.println("Error loading GIF frames: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Extract frame delay from GIF metadata
     */
    private int extractDelay(javax.imageio.metadata.IIOMetadata metadata) {
        String metaFormatName = metadata.getNativeMetadataFormatName();
        org.w3c.dom.Node root = metadata.getAsTree(metaFormatName);

        org.w3c.dom.Node child = root.getFirstChild();
        while (child != null) {
            if ("GraphicControlExtension".equals(child.getNodeName())) {
                org.w3c.dom.NamedNodeMap map = child.getAttributes();
                org.w3c.dom.Node delayTime = map.getNamedItem("delayTime");
                if (delayTime != null) {
                    // GIF delay is in centiseconds (1/100th of a second)
                    int centiseconds = Integer.parseInt(delayTime.getNodeValue());
                    return Math.max(centiseconds * 10, 20); // Minimum 20ms delay
                }
            }
            child = child.getNextSibling();
        }

        return 100; // Default 100ms if no delay found
    }

    /**
     * Setup the animation timer
     */
    private void setupAnimation() {
        if (delays.isEmpty()) {
            delays.add(100); // Default delay
        }

        animationTimer = new Timer(delays.get(0), new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (frames.size() > 1) {
                    currentFrame = (currentFrame + 1) % frames.size();

                    // Update timer delay for next frame
                    if (currentFrame < delays.size()) {
                        animationTimer.setDelay(delays.get(currentFrame));
                    }

                    repaint();
                }
            }
        });
    }

    /**
     * Start the animation
     */
    public void startAnimation() {
        if (!isAnimating && !frames.isEmpty()) {
            isAnimating = true;
            animationTimer.start();
            System.out.println("DEBUG: Animation started");
        }
    }

    /**
     * Stop the animation
     */
    public void stopAnimation() {
        if (isAnimating) {
            isAnimating = false;
            animationTimer.stop();
            System.out.println("DEBUG: Animation stopped");
        }
    }

    /**
     * Check if animation is currently running
     */
    public boolean isAnimating() {
        return isAnimating;
    }

    /**
     * Reset animation to first frame
     */
    public void resetAnimation() {
        currentFrame = 0;
        if (!delays.isEmpty()) {
            animationTimer.setDelay(delays.get(0));
        }
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        if (frames.isEmpty()) return;

        Graphics2D g2d = (Graphics2D) g.create();
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

        BufferedImage currentImage = frames.get(currentFrame);

        // Center the image in the component
        int x = (getWidth() - currentImage.getWidth()) / 2;
        int y = (getHeight() - currentImage.getHeight()) / 2;

        g2d.drawImage(currentImage, x, y, null);
        g2d.dispose();
    }

    @Override
    public Dimension getPreferredSize() {
        if (!frames.isEmpty()) {
            BufferedImage firstFrame = frames.get(0);
            return new Dimension(firstFrame.getWidth(), firstFrame.getHeight());
        }
        return super.getPreferredSize();
    }

    /**
     * Cleanup resources when component is no longer needed
     */
    public void dispose() {
        stopAnimation();
        if (animationTimer != null) {
            animationTimer = null;
        }
        frames.clear();
        delays.clear();
    }
}
