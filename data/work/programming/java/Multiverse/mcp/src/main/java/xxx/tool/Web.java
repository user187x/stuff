package xxx.tool;

import io.github.bonigarcia.wdm.WebDriverManager;
import java.net.URL;
import java.time.Duration;
import java.util.List;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;

/**
 * This class demonstrates how to query Google by automating a real web browser using Selenium. This
 * method is more likely to bypass bot detection but is heavier, slower, and can break if Google
 * changes its page structure.
 *
 * <p>To run this, you need to: 1. Add Selenium and Jsoup libraries to your project. Maven
 * Dependencies: <dependency> <groupId>org.seleniumhq.selenium</groupId>
 * <artifactId>selenium-java</artifactId> <version>4.21.0</version> </dependency> <dependency>
 * <groupId>org.jsoup</groupId> <artifactId>jsoup</artifactId> <version>1.17.2</version>
 * </dependency>
 *
 * <p>2. Download the correct ChromeDriver for your version of Chrome:
 * <a href="https://googlechromelabs.github.io/chrome-for-testing/">...</a>
 *
 * <p>3. Set the path to the chromedriver executable.
 */
public class Web {

  public static String search(String query) {
    // --- IMPORTANT: Update this path to where you downloaded chromedriver ---

    URL resourceUrl = Web.class.getResource("/chrome-driver.exe");
    String path = resourceUrl.getPath();

    System.setProperty(
        "webdriver.chrome.driver",
        "D:\\Projects\\Java\\mcp-server\\src\\main\\resources\\chrome-driver.exe");
    // --------------------------------------------------------------------

    // Configure Chrome to run in headless mode (without a UI)
    ChromeOptions options = new ChromeOptions();
    options.addArguments("--headless");
    // options.addArguments("--disable-gpu");
    options.addArguments(
        "user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.7339.82 Safari/537.36");
    options.addArguments("--window-size=1920,1200");

    StringBuilder payload = new StringBuilder();
    WebDriver driver = null;

    try {
      driver = new ChromeDriver(options);

      // Navigate to Google's search page
      driver.get("https://www.google.com" + query);

      WebElement searchBox = driver.findElement(By.name("q"));
      searchBox.sendKeys(query);
      searchBox.submit(); // Submit the search

      List<WebElement> searchResults = driver.findElements(By.xpath("//div[@class='g']//a"));

      System.out.println("Search Result URLs:");
      for (WebElement result : searchResults) {
        String url = result.getAttribute("href");
        if (url != null && !url.isEmpty()) {
          System.out.println(url);
        }
      }

    } catch (Exception e) {

      System.out.println("Failure scrapping : " + e.getMessage());
      return "Failure find results for: " + query;
    }
    finally {
      // Important: Quit the driver to close the browser instance
      if (driver != null) {
        driver.quit();
      }
    }

    return payload.toString();
  }

  public static String test(String query) {

    System.setProperty(
        "webdriver.chrome.driver",
        "D:\\Projects\\Java\\mcp-server\\src\\main\\resources\\chrome-driver.exe");

    WebDriverManager.chromedriver().clearDriverCache().setup();
    WebDriver driver = new ChromeDriver();
    String url = "https://www.google.com/";
    driver.get(url);

    driver.manage().window().maximize();
    driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(20));

    driver.findElement(By.name("q")).sendKeys(query);

    driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(20));

    List<WebElement> searchitems =
        driver.findElements(By.xpath("//ul[@role='listbox']/li/descendant::div[@class='eIPGRd']"));

    for (WebElement searchitem : searchitems) {

      String listitem = searchitem.getText();
      System.out.println(listitem);

      if (listitem.contains(query)) {
        searchitem.click();
        break;
      }
    }

    return "";
  }
}
