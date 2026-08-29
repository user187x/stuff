package mcp;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.WaitUntilState;
import org.jsoup.Jsoup;

import java.util.Arrays;
import java.util.Map;

public class HeadlessBrowserHelper {

    private final Playwright playwright;
    private final Browser browser;

    public HeadlessBrowserHelper() {
        this.playwright = Playwright.create();
        
        // ULTIMATE STEALTH: Launch Chromium with arguments that disable automation flags
        BrowserType.LaunchOptions options = new BrowserType.LaunchOptions()
                .setHeadless(true)
                .setArgs(Arrays.asList(
                        "--disable-blink-features=AutomationControlled", // Strips webdriver flags at engine level
                        "--disable-infobars",
                        "--no-sandbox",
                        "--disable-dev-shm-usage",
                        "--disable-extensions",
                        "--window-size=1920,1080"
                ));
                
        this.browser = playwright.chromium().launch(options);
        
        // Cleanly close the browser when the Java app exits
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (browser != null) browser.close();
            if (playwright != null) playwright.close();
        }));
    }

    /**
     * Fetches a URL using Playwright with Stealth Javascript injection to bypass CAPTCHAs.
     */
    public String fetchWithJavascript(String url) {
        try (BrowserContext context = browser.newContext()) {
            
            // 1. Mask Headers to match a completely normal desktop Chrome user
            context.setExtraHTTPHeaders(Map.of(
                "User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36",
                "Accept-Language", "en-US,en;q=0.9",
                "Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8",
                "Sec-Ch-Ua", "\"Chromium\";v=\"122\", \"Not(A:Brand\";v=\"24\", \"Google Chrome\";v=\"122\"",
                "Sec-Ch-Ua-Mobile", "?0",
                "Sec-Ch-Ua-Platform", "\"Windows\""
            ));

            // 2. JS INJECTION: Override any lingering webdriver fingerprints
            context.addInitScript("Object.defineProperty(navigator, 'webdriver', {get: () => undefined});");
            context.addInitScript("window.navigator.chrome = { runtime: {} };");
            context.addInitScript("Object.defineProperty(navigator, 'plugins', {get: () => [1, 2, 3, 4, 5]});");

            try (Page page = context.newPage()) {
                // Navigate and wait for network to settle
                page.navigate(url, new Page.NavigateOptions()
                        .setWaitUntil(WaitUntilState.NETWORKIDLE)
                        .setTimeout(15000));

                String rawHtml = page.content();
                return Jsoup.parse(rawHtml).text();
            }
        }
    }
}