package xxx.com.video.converter;

import ws.schild.jave.Encoder;
import ws.schild.jave.MultimediaObject;
import ws.schild.jave.encode.AudioAttributes;
import ws.schild.jave.encode.EncodingAttributes;
import ws.schild.jave.encode.VideoAttributes;

import java.io.File;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Scanner;
import java.util.Set;

public class VideoConverter {
  private static final Set<String> SUPPORTED_FORMATS = new HashSet<>(Arrays.asList(
      "mp4", "3gp", "avi", "flv", "mkv", "mov", "webm", "wmv", "asf", "avchd",
      "divx", "hevc", "m2ts", "mpeg", "mpg", "mts", "rm", "rmvb", "ts", "vob",
      "wtv", "xvid"
  ));

  public static void main(String[] args) {
    Scanner scanner = new Scanner(System.in);

    // Get input file path
    System.out.print("Enter the path to the input video file: ");
    String inputPath = scanner.nextLine();
    File inputFile = new File(inputPath);

    // Validate input file
    if (!inputFile.exists() || !inputFile.isFile()) {
      System.out.println("Error: Input file does not exist or is not a valid file.");
      scanner.close();
      return;
    }

    // Get target format
    System.out.print("Enter the target format (" + String.join(", ", SUPPORTED_FORMATS) + "): ");
    String targetFormat = scanner.nextLine().toLowerCase();

    // Validate target format
    if (!SUPPORTED_FORMATS.contains(targetFormat)) {
      System.out.println("Error: Unsupported target format. Supported formats are: " + String.join(", ", SUPPORTED_FORMATS));
      scanner.close();
      return;
    }

    // Set output file path
    String outputPath = inputFile.getAbsolutePath().substring(0, inputFile.getAbsolutePath().lastIndexOf(".")) + "." + targetFormat;
    File outputFile = new File(outputPath);

    try {
      // Configure audio attributes
      AudioAttributes audio = new AudioAttributes();
      audio.setCodec("aac");
      audio.setBitRate(128000); // 128 kbps
      audio.setChannels(2);
      audio.setSamplingRate(44100);

      // Configure video attributes
      VideoAttributes video = new VideoAttributes();
      video.setCodec(getVideoCodec(targetFormat));
      video.setBitRate(1000000); // 1 Mbps
      video.setFrameRate(30);

      // Set encoding attributes
      EncodingAttributes attrs = new EncodingAttributes();
      attrs.setOutputFormat(targetFormat);
      attrs.setAudioAttributes(audio);
      attrs.setVideoAttributes(video);

      // Perform conversion
      Encoder encoder = new Encoder();
      encoder.encode(new MultimediaObject(inputFile), outputFile, attrs);

      System.out.println("Conversion successful! Output saved to: " + outputPath);
    } catch (Exception e) {
      System.err.println("Error during conversion: " + e.getMessage());
      e.printStackTrace();
    } finally {
      scanner.close();
    }
  }

  // Map target format to appropriate video codec
  private static String getVideoCodec(String format) {
    switch (format) {
      case "mp4":
      case "mov":
      case "avchd":
      case "hevc":
        return "h264";
      case "webm":
        return "vp8";
      case "wmv":
      case "asf":
        return "wmv2";
      case "flv":
        return "flv1";
      case "mkv":
      case "m2ts":
      case "mts":
      case "ts":
        return "mpeg2video";
      case "avi":
      case "divx":
      case "xvid":
        return "mpeg4";
      case "3gp":
        return "h263";
      case "rm":
      case "rmvb":
        return "rv40";
      case "vob":
        return "mpeg2video";
      case "wtv":
        return "mpeg2video";
      case "mpeg":
      case "mpg":
        return "mpeg1video";
      default:
        return "h264"; // Default to H.264 for unknown formats
    }
  }
}
