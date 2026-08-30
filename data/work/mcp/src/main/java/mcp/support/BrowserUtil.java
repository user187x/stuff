package mcp.support;

import org.jsoup.Jsoup;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;

import java.time.Duration;

public class BrowserUtil {

 private final WebDriver driver;

 public BrowserUtil() {
  FirefoxOptions options = new FirefoxOptions();
  options.addArguments("-headless");
  options.addArguments("--width=1920");
  options.addArguments("--height=1080");

  // Selenium Manager will automatically download GeckoDriver and Firefox if missing
  this.driver = new FirefoxDriver(options);

  // Set a standard timeout for page loads
  this.driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(15));

  // Cleanly close the browser when the Java app exits
  Runtime.getRuntime().addShutdownHook(new Thread(() -> {
   if (driver != null) {
    driver.quit();
   }
  }));
 }

 /**
  * Fetches a URL using Selenium Firefox WebDriver.
  */
 public String fetchWithJavascript(String url) {
  driver.get(url);
  String rawHtml = driver.getPageSource();
  return Jsoup.parse(rawHtml).text();
 }
}