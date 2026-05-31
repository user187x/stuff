package xxx.com.ocr.cli;

import java.io.File;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import net.sourceforge.tess4j.Tesseract;

public class App {

  public static void main(String[] args) {

    try {

      System.setProperty("jna.library.path", "");

      URL resourceUrl = App.class.getResource("/ocr/tessdata");
      Path resourcePath = Paths.get(resourceUrl.toURI());
      String trainedData = resourcePath.toAbsolutePath().toString();

      System.out.println("TessData directory : " +trainedData);

      resourceUrl = App.class.getResource("/ocr/image/smile-image-text.jpg");
      resourcePath = Paths.get(resourceUrl.toURI());
      File image = resourcePath.toFile();

      System.out.println("Test image directory : " + image.getAbsolutePath());

      Tesseract tesseract = new Tesseract();
      tesseract.setVariable("user_defined_dpi", "96");
      tesseract.setDatapath(trainedData);

      System.out.print("OCR Detected Text : " + tesseract.doOCR(image));

    } catch (Exception e) {

      System.out.println(e.getMessage());
    }
  }
}
