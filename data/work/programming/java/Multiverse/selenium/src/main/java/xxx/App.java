package xxx;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.firefox.FirefoxDriver;

public class BrowserViewer {

    public static void main(String[] args) {
        // Uncomment the one you want to test:
        
        openChrome();
        // openBrave();
        // openFirefox();
    }

    public static void openChrome() {
        // 1. Point to your downloaded chromedriver
        System.setProperty("webdriver.chrome.driver", "/path/to/chromedriver");

        // 2. Launch Chrome
        WebDriver driver = new ChromeDriver();

        try {
            // 3. View the webpage
            driver.get("https://en.wikipedia.org/wiki/Linux");
            
            // Keep it open for 5 seconds to see it
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            // 4. Close the browser
            driver.quit();
        }
    }

    public static void openBrave() {
        // 1. Point to your downloaded chromedriver (Brave uses ChromeDriver)
        System.setProperty("webdriver.chrome.driver", "/path/to/chromedriver");

        // 2. Set the path to the Brave browser executable
        ChromeOptions options = new ChromeOptions();
        options.setBinary("/usr/bin/brave-browser"); // Default Linux path

        // 3. Launch Brave using ChromeOptions
        WebDriver driver = new ChromeDriver(options);

        try {
            // 4. View the webpage
            driver.get("https://en.wikipedia.org/wiki/Linux");
            
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            // 5. Close the browser
            driver.quit();
        }
    }

    public static void openFirefox() {
        // 1. Point to your downloaded geckodriver
        System.setProperty("webdriver.gecko.driver", "/path/to/geckodriver");

        // 2. Launch Firefox
        WebDriver driver = new FirefoxDriver();

        try {
            // 3. View the webpage
            driver.get("https://en.wikipedia.org/wiki/Linux");
            
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            // 4. Close the browser
            driver.quit();
        }
    }
}
