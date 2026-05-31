package xxx.com.pdf.html;

import com.itextpdf.html2pdf.HtmlConverter;
import com.itextpdf.html2pdf.resolver.font.DefaultFontProvider;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.font.FontProvider;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.Path;

public class HtmlToPdfConverter {
  /**
   * Downloads an HTML page from a URL using Selenium, saves it to a file, and converts it to a PDF saved in the OS temp directory
   * @param urlString URL of the HTML page to convert (e.g., https://www.cnn.com)
   * @param htmlFileName Name of the temporary HTML file (without path)
   * @param pdfFileName Name of the output PDF file (without path)
   * @return Path to the generated PDF file
   */
  public static String convertToPdf(String urlString, String htmlFileName, String pdfFileName) {
    WebDriver driver = null;
    String tempDir = System.getProperty("java.io.tmpdir");
    String htmlPath = Paths.get(tempDir, htmlFileName).toString();
    String pdfPath = Paths.get(tempDir, pdfFileName).toString();

    try {
      // Ensure URL uses HTTPS
      if (!urlString.startsWith("http://") && !urlString.startsWith("https://")) {
        urlString = "https://" + urlString;
      }

      // Set up ChromeDriver with headless mode
      ChromeOptions options = new ChromeOptions();
      options.addArguments("--headless", "--disable-gpu", "--no-sandbox", "--disable-dev-shm-usage");
      driver = new ChromeDriver(options);

      // Navigate to the URL and get fully rendered HTML
      driver.get(urlString);
      // Wait for page to load
      Thread.sleep(7000); // 7 seconds for complex pages
      String htmlContent = driver.getPageSource();

      // Clean HTML with Jsoup
      Document doc = Jsoup.parse(htmlContent);
      // Remove unsupported tags
      doc.select("picture, source, script, iframe").remove();
      // Remove elements with zero or negative dimensions
      doc.select("[style*='width: 0'], [style*='height: 0'], [style*='margin: -'], [style*='padding: -']").remove();
      // Clean CSS in <style> tags
      Elements styleElements = doc.select("style");
      for (org.jsoup.nodes.Element style : styleElements) {
        String css = style.html();
        // Remove problematic CSS properties
        css = css.replaceAll("color\\s*:\\s*unset\\s*;", "") // Remove color: unset
            .replaceAll("justify-content\\s*:[^;]+;", "") // Remove unsupported flex properties
            .replaceAll("--[\\w-]+\\s*:[^;]+;", "") // Remove custom CSS variables
            .replaceAll("margin\\s*:\\s*-[^;]+;", "") // Remove negative margins
            .replaceAll("padding\\s*:\\s*-[^;]+;", "") // Remove negative padding
            .replaceAll("width\\s*:\\s*0[^;]*;", "") // Remove zero width
            .replaceAll("height\\s*:\\s*0[^;]*;", ""); // Remove zero height
        style.html(css);
      }
      // Remove inline styles with problematic properties
      doc.select("[style*='margin: -'], [style*='padding: -'], [style*='width: 0'], [style*='height: 0']").removeAttr("style");

      // Save cleaned HTML to file
      Files.write(Paths.get(htmlPath), doc.outerHtml().getBytes());

      // Convert HTML file to PDF
      try {
        FontProvider fontProvider = new DefaultFontProvider(true, true, false);
        com.itextpdf.html2pdf.ConverterProperties props = new com.itextpdf.html2pdf.ConverterProperties()
            .setFontProvider(fontProvider)
            .setBaseUri(urlString);
        HtmlConverter.convertToPdf(new File(htmlPath), new File(pdfPath), props);
      } catch (Exception e) {
        System.err.println("PDF conversion failed: " + e.getMessage());
        e.printStackTrace();
        return null;
      }

      System.out.println("HTML saved at: " + htmlPath);
      System.out.println("PDF created successfully at: " + pdfPath);
      return pdfPath;
    } catch (IOException | InterruptedException e) {
      System.err.println("Error processing HTML from URL or saving file: " + e.getMessage());
      e.printStackTrace();
      return null;
    } finally {
      if (driver != null) {
        try {
          driver.quit();
        } catch (Exception e) {
          System.err.println("Error closing WebDriver: " + e.getMessage());
        }
      }
    }
  }

  /**
   * Main method for testing the HTML to PDF conversion with CNN example
   */
  public static void main(String[] args) {
    // Example: Convert https://www.cnn.com to PDF
    String url = "https://www.cnn.com";
    String htmlFileName = "cnn_page.html";
    String pdfFileName = "cnn_page.pdf";
    String generatedPdfPath = convertToPdf(url, htmlFileName, pdfFileName);
    if (generatedPdfPath != null) {
      System.out.println("CNN PDF is located at: " + generatedPdfPath);
    } else {
      System.out.println("Failed to create PDF from CNN website.");
    }
    System.exit(0);
  }
}
