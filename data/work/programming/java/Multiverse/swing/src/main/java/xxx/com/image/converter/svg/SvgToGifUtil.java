package xxx.com.image.converter.svg;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import xxx.com.swing.support.AnimatedGifComponent;

import javax.swing.*;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Utility class providing a simple static method to convert SVG to animated GIF */
public class SvgToGifUtil {

    /**
     * Represents SVG animation parameters extracted from the file
     */
    private static class SvgAnimationParams {
        double duration = 3.0;  // Default 3 seconds
        int fps = 30;           // Default 30 FPS
        boolean hasAnimation = false;

        @Override
        public String toString() {
            return String.format("SvgAnimationParams{duration=%.2fs, fps=%d, hasAnimation=%s}",
                    duration, fps, hasAnimation);
        }
    }

    /**
     * Converts an SVG file to a high-quality animated GIF and returns it as an ImageIcon.
     * Automatically detects animation parameters from the SVG file.
     *
     * <p>This method analyzes the SVG for animation elements and adjusts:
     * - Duration based on 'dur' attribute in animations
     * - Frame rate based on animation complexity
     * - Infinite loop, transparent background preservation
     * - "none" disposal method for best quality
     *
     * @param inputSvg The input SVG file to convert
     * @return ImageIcon containing the animated GIF, or null if conversion fails
     * @throws IllegalArgumentException if inputSvg is null or doesn't exist
     */
    public static ImageIcon convertSvgToGif(File inputSvg) {
        if (inputSvg == null) {
            throw new IllegalArgumentException("Input SVG file cannot be null");
        }
        if (!inputSvg.exists() || !inputSvg.isFile()) {
            throw new IllegalArgumentException(
                    "Input SVG file does not exist or is not a file: " + inputSvg.getAbsolutePath());
        }
        if (!inputSvg.getName().toLowerCase().endsWith(".svg")) {
            throw new IllegalArgumentException(
                    "Input file must be an SVG file: " + inputSvg.getAbsolutePath());
        }

        // Parse SVG to extract animation parameters
        SvgAnimationParams params = parseSvgAnimationParams(inputSvg);
        System.out.println("DEBUG: Detected animation parameters: " + params);

        // DEBUG: Change temp file to a fixed location for inspection
        File outputGif = new File("debug_output.gif");
        System.out.println("DEBUG: Attempting to save GIF to: " + outputGif.getAbsolutePath());

        try {
            // Configuration for high-quality animated GIF using detected parameters
            Integer targetWidth = null;
            Integer targetHeight = null;
            int loopCount = 0;
            String disposal = "none";

            // Convert SVG to GIF using the detected parameters
            SvgToGif.convert(
                    inputSvg, outputGif, params.fps, params.duration,
                    targetWidth, targetHeight, loopCount, disposal);

            // Check if the file was actually created and has content
            if (!outputGif.exists() || outputGif.length() == 0) {
                System.err.println("DEBUG: Conversion failed. The output GIF is missing or empty.");
                return null;
            }
            System.out.println("DEBUG: GIF file size: " + outputGif.length() + " bytes.");

            // Read the GIF file into a byte array
            byte[] gifBytes = Files.readAllBytes(outputGif.toPath());

            // Create ImageIcon from byte array
            ImageIcon gifIcon = new ImageIcon(gifBytes);

            // Check if the image loaded successfully by checking dimensions
            // MediaTracker status check is unreliable for GIFs
            if (gifIcon.getIconWidth() > 0 && gifIcon.getIconHeight() > 0) {
                System.out.println("DEBUG: Successfully loaded GIF as ImageIcon. Dimensions: "
                        + gifIcon.getIconWidth() + "x" + gifIcon.getIconHeight());
                return gifIcon;
            } else {
                System.err.println("DEBUG: Failed to load GIF - invalid dimensions: "
                        + gifIcon.getIconWidth() + "x" + gifIcon.getIconHeight());

                // Try alternative loading method
                try {
                    Toolkit toolkit = Toolkit.getDefaultToolkit();
                    Image image = toolkit.createImage(gifBytes);

                    // Wait for image to load
                    MediaTracker tracker = new MediaTracker(new JLabel());
                    tracker.addImage(image, 0);
                    tracker.waitForAll();

                    if (tracker.isErrorAny()) {
                        System.err.println("DEBUG: MediaTracker reported an error loading the image");
                        return null;
                    }

                    ImageIcon alternativeIcon = new ImageIcon(image);
                    if (alternativeIcon.getIconWidth() > 0 && alternativeIcon.getIconHeight() > 0) {
                        System.out.println("DEBUG: Successfully loaded GIF using alternative method");
                        return alternativeIcon;
                    }
                } catch (InterruptedException e) {
                    System.err.println("DEBUG: Interrupted while waiting for image to load: " + e.getMessage());
                    Thread.currentThread().interrupt();
                }

                return null;
            }

        } catch (Exception e) {
            System.err.println("Error converting SVG to GIF: " + e.getMessage());
            e.printStackTrace(); // Added for better debugging
            return null;
        }
        // NOTE: The 'finally' block for deletion is removed for this test.
    }

    /**
     * Overloaded method with custom duration for the animation.
     * This method overrides any duration detected from the SVG file.
     *
     * @param inputSvg The input SVG file to convert
     * @param durationSeconds Duration of the animation in seconds (overrides SVG duration)
     * @return ImageIcon containing the animated GIF, or null if conversion fails
     */
    public static ImageIcon convertSvgToGif(File inputSvg, double durationSeconds) {
        if (inputSvg == null) {
            throw new IllegalArgumentException("Input SVG file cannot be null");
        }

        if (!inputSvg.exists() || !inputSvg.isFile()) {
            throw new IllegalArgumentException(
                    "Input SVG file does not exist or is not a file: " + inputSvg.getAbsolutePath());
        }

        if (durationSeconds <= 0) {
            throw new IllegalArgumentException("Duration must be greater than 0");
        }

        // Parse SVG to get FPS recommendations, but use custom duration
        SvgAnimationParams params = parseSvgAnimationParams(inputSvg);
        params.duration = durationSeconds; // Override with custom duration
        System.out.println("DEBUG: Using custom duration. Animation parameters: " + params);

        Path tempGifPath = null;
        try {
            // Create a temporary file for the output GIF
            tempGifPath = Files.createTempFile("svg_to_gif_", ".gif");
            File outputGif = tempGifPath.toFile();

            // Configuration for high-quality animated GIF with custom duration
            Integer targetWidth = null; // Use SVG's original width
            Integer targetHeight = null; // Use SVG's original height
            int loopCount = 0; // Infinite loop
            String disposal = "none"; // Best quality disposal method

            // Convert SVG to GIF using the detected FPS and custom duration
            SvgToGif.convert(
                    inputSvg,
                    outputGif,
                    params.fps,
                    params.duration,
                    targetWidth,
                    targetHeight,
                    loopCount,
                    disposal);

            // Read the temporary GIF file into a byte array first.
            byte[] gifBytes = Files.readAllBytes(tempGifPath);

            // Create the ImageIcon from the byte array in memory.
            ImageIcon gifIcon = new ImageIcon(gifBytes);

            // Check if the image loaded successfully by checking dimensions instead of MediaTracker
            if (gifIcon.getIconWidth() > 0 && gifIcon.getIconHeight() > 0) {
                return gifIcon;
            } else {
                // Try alternative loading method
                try {
                    Toolkit toolkit = Toolkit.getDefaultToolkit();
                    Image image = toolkit.createImage(gifBytes);

                    // Wait for image to load
                    MediaTracker tracker = new MediaTracker(new JLabel());
                    tracker.addImage(image, 0);
                    tracker.waitForAll();

                    if (tracker.isErrorAny()) {
                        System.err.println("MediaTracker reported an error loading the image");
                        return null;
                    }

                    ImageIcon alternativeIcon = new ImageIcon(image);
                    if (alternativeIcon.getIconWidth() > 0 && alternativeIcon.getIconHeight() > 0) {
                        return alternativeIcon;
                    }
                } catch (InterruptedException e) {
                    System.err.println("Interrupted while waiting for image to load: " + e.getMessage());
                    Thread.currentThread().interrupt();
                }

                System.err.println("Failed to load generated GIF as ImageIcon");
                return null;
            }

        } catch (Exception e) {
            System.err.println("Error converting SVG to GIF: " + e.getMessage());
            e.printStackTrace(); // Added for better debugging
            return null;
        } finally {
            // Clean up temporary file
            if (tempGifPath != null) {
                try {
                    Files.deleteIfExists(tempGifPath);
                } catch (IOException e) {
                    System.err.println("Warning: Could not delete temporary file: " + tempGifPath);
                }
            }
        }
    }

    /**
     * Overloaded method with custom duration and FPS.
     * This method overrides any parameters detected from the SVG file.
     *
     * @param inputSvg The input SVG file to convert
     * @param durationSeconds Duration of the animation in seconds
     * @param fps Frame rate for the animation
     * @return ImageIcon containing the animated GIF, or null if conversion fails
     */
    public static ImageIcon convertSvgToGif(File inputSvg, double durationSeconds, int fps) {
        if (inputSvg == null) {
            throw new IllegalArgumentException("Input SVG file cannot be null");
        }

        if (!inputSvg.exists() || !inputSvg.isFile()) {
            throw new IllegalArgumentException(
                    "Input SVG file does not exist or is not a file: " + inputSvg.getAbsolutePath());
        }

        if (durationSeconds <= 0) {
            throw new IllegalArgumentException("Duration must be greater than 0");
        }

        if (fps <= 0 || fps > 120) {
            throw new IllegalArgumentException("FPS must be between 1 and 120");
        }

        System.out.println("DEBUG: Using custom parameters. Duration: " + durationSeconds + "s, FPS: " + fps);

        Path tempGifPath = null;
        try {
            // Create a temporary file for the output GIF
            tempGifPath = Files.createTempFile("svg_to_gif_", ".gif");
            File outputGif = tempGifPath.toFile();

            // Configuration for high-quality animated GIF with custom parameters
            Integer targetWidth = null; // Use SVG's original width
            Integer targetHeight = null; // Use SVG's original height
            int loopCount = 0; // Infinite loop
            String disposal = "none"; // Best quality disposal method

            // Convert SVG to GIF using custom parameters
            SvgToGif.convert(
                    inputSvg,
                    outputGif,
                    fps,
                    durationSeconds,
                    targetWidth,
                    targetHeight,
                    loopCount,
                    disposal);

            // Read the temporary GIF file into a byte array first.
            byte[] gifBytes = Files.readAllBytes(tempGifPath);

            // Create the ImageIcon from the byte array in memory.
            ImageIcon gifIcon = new ImageIcon(gifBytes);

            // Check if the image loaded successfully by checking dimensions instead of MediaTracker
            if (gifIcon.getIconWidth() > 0 && gifIcon.getIconHeight() > 0) {
                return gifIcon;
            } else {
                // Try alternative loading method
                try {
                    Toolkit toolkit = Toolkit.getDefaultToolkit();
                    Image image = toolkit.createImage(gifBytes);

                    // Wait for image to load
                    MediaTracker tracker = new MediaTracker(new JLabel());
                    tracker.addImage(image, 0);
                    tracker.waitForAll();

                    if (tracker.isErrorAny()) {
                        System.err.println("MediaTracker reported an error loading the image");
                        return null;
                    }

                    ImageIcon alternativeIcon = new ImageIcon(image);
                    if (alternativeIcon.getIconWidth() > 0 && alternativeIcon.getIconHeight() > 0) {
                        return alternativeIcon;
                    }
                } catch (InterruptedException e) {
                    System.err.println("Interrupted while waiting for image to load: " + e.getMessage());
                    Thread.currentThread().interrupt();
                }

                System.err.println("Failed to load generated GIF as ImageIcon");
                return null;
            }

        } catch (Exception e) {
            System.err.println("Error converting SVG to GIF: " + e.getMessage());
            e.printStackTrace();
            return null;
        } finally {
            // Clean up temporary file
            if (tempGifPath != null) {
                try {
                    Files.deleteIfExists(tempGifPath);
                } catch (IOException e) {
                    System.err.println("Warning: Could not delete temporary file: " + tempGifPath);
                }
            }
        }
    }

    /**
     * Parses an SVG file to extract animation parameters such as duration and complexity.
     *
     * @param svgFile The SVG file to parse
     * @return SvgAnimationParams containing the detected parameters
     */
    private static SvgAnimationParams parseSvgAnimationParams(File svgFile) {
        SvgAnimationParams params = new SvgAnimationParams();

        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(svgFile);

            // Look for animation elements
            NodeList animateElements = document.getElementsByTagName("animate");
            NodeList animateTransformElements = document.getElementsByTagName("animateTransform");
            NodeList animateMotionElements = document.getElementsByTagName("animateMotion");

            int totalAnimations = animateElements.getLength() +
                    animateTransformElements.getLength() +
                    animateMotionElements.getLength();

            if (totalAnimations > 0) {
                params.hasAnimation = true;

                // Parse duration from any animation element
                double shortestDuration = Double.MAX_VALUE;
                boolean foundDuration = false;

                // Check animate elements
                for (int i = 0; i < animateElements.getLength(); i++) {
                    Element element = (Element) animateElements.item(i);
                    double duration = parseDuration(element.getAttribute("dur"));
                    if (duration > 0) {
                        shortestDuration = Math.min(shortestDuration, duration);
                        foundDuration = true;
                    }
                }

                // Check animateTransform elements
                for (int i = 0; i < animateTransformElements.getLength(); i++) {
                    Element element = (Element) animateTransformElements.item(i);
                    double duration = parseDuration(element.getAttribute("dur"));
                    if (duration > 0) {
                        shortestDuration = Math.min(shortestDuration, duration);
                        foundDuration = true;
                    }
                }

                // Check animateMotion elements
                for (int i = 0; i < animateMotionElements.getLength(); i++) {
                    Element element = (Element) animateMotionElements.item(i);
                    double duration = parseDuration(element.getAttribute("dur"));
                    if (duration > 0) {
                        shortestDuration = Math.min(shortestDuration, duration);
                        foundDuration = true;
                    }
                }

                if (foundDuration) {
                    params.duration = shortestDuration;
                }

                // Adjust FPS based on animation complexity and duration
                if (params.duration < 1.0) {
                    // Fast animations need higher FPS for smoothness
                    params.fps = 60;
                } else if (params.duration < 2.0) {
                    // Medium speed animations
                    params.fps = 45;
                } else {
                    // Slower animations can use standard FPS
                    params.fps = 30;
                }

                // Increase FPS for complex animations (multiple elements)
                if (totalAnimations > 2) {
                    params.fps = Math.min(params.fps + 15, 60);
                }

                System.out.println("DEBUG: Found " + totalAnimations + " animation element(s)");
                if (foundDuration) {
                    System.out.println("DEBUG: Shortest duration found: " + shortestDuration + "s");
                }
            } else {
                System.out.println("DEBUG: No animation elements found, using defaults");
            }

        } catch (Exception e) {
            System.err.println("Warning: Could not parse SVG for animation parameters: " + e.getMessage());
            // Keep default values
        }

        return params;
    }

    /**
     * Parses a duration string (e.g., "0.75s", "750ms", "2") into seconds.
     *
     * @param durationStr The duration string to parse
     * @return Duration in seconds, or -1 if parsing fails
     */
    private static double parseDuration(String durationStr) {
        if (durationStr == null || durationStr.trim().isEmpty()) {
            return -1;
        }

        durationStr = durationStr.trim().toLowerCase();

        try {
            // Pattern for duration with units: "0.75s", "750ms", "1.5"
            Pattern pattern = Pattern.compile("^([0-9]*\\.?[0-9]+)(s|ms)?$");
            Matcher matcher = pattern.matcher(durationStr);

            if (matcher.matches()) {
                double value = Double.parseDouble(matcher.group(1));
                String unit = matcher.group(2);

                if ("ms".equals(unit)) {
                    return value / 1000.0; // Convert milliseconds to seconds
                } else {
                    // "s" or no unit (assume seconds)
                    return value;
                }
            }
        } catch (NumberFormatException e) {
            System.err.println("Warning: Could not parse duration: " + durationStr);
        }

        return -1;
    }

    /**
     * Create an AnimatedGifComponent from an SVG file with auto-detected parameters.
     * This is the recommended way to use animated GIFs in Swing applications.
     *
     * @param inputSvg The input SVG file to convert
     * @return AnimatedGifComponent ready to be added to Swing containers, or null if conversion fails
     */
    public static AnimatedGifComponent createAnimatedComponent(File inputSvg) {
        // First create the temporary GIF file with detected parameters
        SvgAnimationParams params = parseSvgAnimationParams(inputSvg);
        System.out.println("DEBUG: Creating animated component with parameters: " + params);

        Path tempGifPath = null;
        try {
            tempGifPath = Files.createTempFile("svg_to_gif_component_", ".gif");
            File outputGif = tempGifPath.toFile();

            // Convert SVG to GIF
            SvgToGif.convert(
                    inputSvg, outputGif, params.fps, params.duration,
                    null, null, 0, "none");

            if (!outputGif.exists() || outputGif.length() == 0) {
                System.err.println("Failed to create GIF for animated component");
                return null;
            }

            // Read GIF data and create component
            byte[] gifBytes = Files.readAllBytes(tempGifPath);
            return new AnimatedGifComponent(gifBytes);

        } catch (Exception e) {
            System.err.println("Error creating animated component: " + e.getMessage());
            e.printStackTrace();
            return null;
        } finally {
            // Clean up temporary file
            if (tempGifPath != null) {
                try {
                    Files.deleteIfExists(tempGifPath);
                } catch (IOException e) {
                    System.err.println("Warning: Could not delete temporary file: " + tempGifPath);
                }
            }
        }
    }

    /**
     * Create an AnimatedGifComponent from an SVG file with custom parameters.
     *
     * @param inputSvg The input SVG file to convert
     * @param durationSeconds Duration of the animation in seconds
     * @param fps Frame rate for the animation
     * @return AnimatedGifComponent ready to be added to Swing containers, or null if conversion fails
     */
    public static AnimatedGifComponent createAnimatedComponent(File inputSvg, double durationSeconds, int fps) {
        System.out.println("DEBUG: Creating animated component with custom parameters. Duration: " + durationSeconds + "s, FPS: " + fps);

        Path tempGifPath = null;
        try {
            tempGifPath = Files.createTempFile("svg_to_gif_component_", ".gif");
            File outputGif = tempGifPath.toFile();

            // Convert SVG to GIF
            SvgToGif.convert(
                    inputSvg, outputGif, fps, durationSeconds,
                    null, null, 0, "none");

            if (!outputGif.exists() || outputGif.length() == 0) {
                System.err.println("Failed to create GIF for animated component");
                return null;
            }

            // Read GIF data and create component
            byte[] gifBytes = Files.readAllBytes(tempGifPath);
            return new AnimatedGifComponent(gifBytes);

        } catch (Exception e) {
            System.err.println("Error creating animated component: " + e.getMessage());
            e.printStackTrace();
            return null;
        } finally {
            // Clean up temporary file
            if (tempGifPath != null) {
                try {
                    Files.deleteIfExists(tempGifPath);
                } catch (IOException e) {
                    System.err.println("Warning: Could not delete temporary file: " + tempGifPath);
                }
            }
        }
    }

    /** Example usage demonstrating both ImageIcon and AnimatedGifComponent approaches */
    public static void main(String[] args) {
        // Example usage
        // Note: Ensure you have an SVG file at the specified resource path for this to work.
        URL url = SvgToGifUtil.class.getResource("/svg/blocks-wave.svg");
        if (url == null) {
            System.err.println("Error: SVG resource not found. Please check the path '/svg/blocks-wave.svg'");
            return;
        }
        File svgFile = new File(url.getFile());

        // Method 1: Traditional ImageIcon approach (may have animation issues)
        System.out.println("=== ImageIcon Approach ===");
        ImageIcon animatedGif = convertSvgToGif(svgFile);
        if (animatedGif != null) {
            System.out.println("Successfully converted SVG to animated GIF!");
            System.out.println("GIF dimensions: " + animatedGif.getIconWidth() + "x" + animatedGif.getIconHeight());
        } else {
            System.out.println("Failed to convert SVG to GIF");
        }

        // Method 2: AnimatedGifComponent approach (RECOMMENDED)
        System.out.println("\n=== AnimatedGifComponent Approach (RECOMMENDED) ===");
        AnimatedGifComponent animatedComponent = createAnimatedComponent(svgFile);
        if (animatedComponent != null) {
            System.out.println("Successfully created animated component!");

            // Example: Create a simple test window
            SwingUtilities.invokeLater(() -> {
                JFrame frame = new JFrame("Animated SVG Test");
                frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
                frame.setLayout(new BorderLayout());

                // Add the animated component
                JPanel centerPanel = new JPanel(new FlowLayout());
                centerPanel.add(new JLabel("Animated Component: "));
                centerPanel.add(animatedComponent);
                frame.add(centerPanel, BorderLayout.CENTER);

                // Add controls
                JPanel controlPanel = new JPanel(new FlowLayout());
                JButton startBtn = new JButton("Start");
                JButton stopBtn = new JButton("Stop");
                JButton resetBtn = new JButton("Reset");

                startBtn.addActionListener(e -> animatedComponent.startAnimation());
                stopBtn.addActionListener(e -> animatedComponent.stopAnimation());
                resetBtn.addActionListener(e -> animatedComponent.resetAnimation());

                controlPanel.add(startBtn);
                controlPanel.add(stopBtn);
                controlPanel.add(resetBtn);
                frame.add(controlPanel, BorderLayout.SOUTH);

                // For comparison, add ImageIcon version (if it worked)
                if (animatedGif != null) {
                    JLabel imageIconLabel = new JLabel("ImageIcon: ", animatedGif, JLabel.LEFT);
                    frame.add(imageIconLabel, BorderLayout.NORTH);
                }

                frame.pack();
                frame.setLocationRelativeTo(null);
                frame.setVisible(true);

                // Start animation automatically
                animatedComponent.startAnimation();
            });

        } else {
            System.out.println("Failed to create animated component");
        }
    }
}