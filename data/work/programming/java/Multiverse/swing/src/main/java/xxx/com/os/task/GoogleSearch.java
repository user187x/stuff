package xxx.com.os.task;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeDriverService;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;
import java.util.Objects;

public class GoogleSearch {

  public static void main(String[] args) {

    String searchQuery = "How to parse HTML in Java";
    System.out.println("Searching Google for: " + searchQuery);

    ChromeOptions options = new ChromeOptions();
    options.setAcceptInsecureCerts(true);
    options.addArguments("--headless=new"); // Headless mode (no UI).
    options.addArguments(
        "--user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Safari/537.36"); // Mimic browser.
    options.addArguments("--disable-blink-features=AutomationControlled"); // Reduce detection.

    ChromeDriverService service =
        new ChromeDriverService.Builder().withLogOutput(System.out).build();
    WebDriver driver = new ChromeDriver(service, options);

    try {
      // 1. Load the Google homepage.
      driver.get("https://www.google.com");

      // 2. Find the search input (textarea), set the query, and submit.
      WebElement searchInput = driver.findElement(By.name("q"));
      searchInput.sendKeys(searchQuery);
      WebElement submitButton = driver.findElement(By.name("btnK"));
      submitButton.submit();

      // 3. Wait for results to load (up to 10 seconds).
      WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
      wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("div.g")));

      // 4. Parse the results page source with Jsoup.
      Document doc = Jsoup.parse(Objects.requireNonNull(driver.getPageSource()));

      // 5. Select the search result blocks (updated for current Google structure).
      Elements searchResults = doc.select("div.g");

      if (searchResults.isEmpty()) {
        System.out.println(
            "No search results found. Google's page structure may have changed, or the request was blocked.");
        // For debugging, save the HTML:
        // java.nio.file.Files.writeString(java.nio.file.Paths.get("google_results.html"),
        // doc.html());
        return;
      }

      System.out.println(
          "Found " + searchResults.size() + " potential results. Displaying top results:\n");

      // 6. Iterate and extract title, link, and description.
      int resultCount = 0;
      for (Element result : searchResults) {
        Element titleElement = result.select("h3").first();
        Element linkElement =
            result.select(".yuRUbf > a").first(); // More specific selector for the main link.
        Element descriptionElement =
            result.select(".VwiC3b").first(); // Updated for current description class.

        if (titleElement != null && linkElement != null) {
          String title = titleElement.text();
          String url = linkElement.attr("href");
          String description =
              (descriptionElement != null)
                  ? descriptionElement.text()
                  : "No description available.";

          // Skip non-result elements (e.g., ads or features).
          if (title.isEmpty() || !url.startsWith("http")) {
            continue;
          }

          System.out.println("Title: " + title);
          System.out.println("URL: " + url);
          System.out.println("Description: " + description);
          System.out.println("--------------------------------------------------\n");

          resultCount++;
          if (resultCount >= 5) { // Limit to top 5.
            break;
          }
        }
      }

    } catch (Exception e) {
      System.err.println("An error occurred while trying to fetch the search results:");
      e.printStackTrace();
    } finally {
      driver.quit(); // Clean up the driver.
    }
  }
}
