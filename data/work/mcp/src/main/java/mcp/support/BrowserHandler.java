package mcp.support;

import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitUntilState;
import org.jsoup.Jsoup;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class BrowserHandler {

 /** Thrown when the target serves a bot-check / CAPTCHA page instead of real content. */
 public static class CaptchaBlockedException extends RuntimeException {
  public CaptchaBlockedException(String message) { super(message); }
 }

 private final Playwright playwright;
 private final BrowserContext context;

 // Only used when we cannot find a real Chrome install and must fall back to bundled Chromium.
 private static final String CHROME_UA =
     "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
         + "(KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36";

 public BrowserHandler() {
  this.playwright = Playwright.create();
  this.context = launchStealthContext();

  Runtime.getRuntime().addShutdownHook(new Thread(() -> {
   try { if (context != null) context.close(); } catch (Exception ignored) {}
   try { if (playwright != null) playwright.close(); } catch (Exception ignored) {}
  }));
 }

 private BrowserContext launchStealthContext() {
  // Persistent profile: stores cookies (including Google's consent cookie) so that after the
  // first run the browser looks like a returning human rather than a fresh incognito session.
  Path profileDir = Paths.get(System.getProperty("user.home"), ".mcp-browser-profile");

  List<String> args = Arrays.asList(
      "--disable-blink-features=AutomationControlled",
      "--disable-infobars",
      "--no-sandbox",
      "--disable-dev-shm-usage",
      "--disable-extensions",
      "--no-first-run",
      "--no-default-browser-check"
  );

  // Prefer REAL Google Chrome (channel=chrome). Its fingerprint is far cleaner than bundled
  // Chromium, and headless=true triggers the modern --headless=new mode, which is much harder
  // to detect. Fall back to bundled Chromium (with a spoofed UA) if Chrome isn't installed.
  BrowserContext ctx;
  try {
   BrowserType.LaunchPersistentContextOptions o = new BrowserType.LaunchPersistentContextOptions()
       .setHeadless(true)
       .setChannel("chrome")
       .setViewportSize(1920, 1080)
       .setLocale("en-US")
       .setTimezoneId("America/New_York")
       .setArgs(args);
   ctx = playwright.chromium().launchPersistentContext(profileDir, o);
  } catch (PlaywrightException chromeMissing) {
   BrowserType.LaunchPersistentContextOptions o = new BrowserType.LaunchPersistentContextOptions()
       .setHeadless(true)
       .setViewportSize(1920, 1080)
       .setLocale("en-US")
       .setTimezoneId("America/New_York")
       .setUserAgent(CHROME_UA)   // spoof UA only when we're NOT using real Chrome
       .setArgs(args);
   ctx = playwright.chromium().launchPersistentContext(profileDir, o);
  }

  // Fingerprint patches applied to every page in this context (run before page scripts).
  ctx.addInitScript("Object.defineProperty(navigator,'webdriver',{get:()=>undefined});");
  ctx.addInitScript("window.chrome={runtime:{}};");
  ctx.addInitScript("Object.defineProperty(navigator,'plugins',{get:()=>[1,2,3,4,5]});");
  ctx.addInitScript("Object.defineProperty(navigator,'languages',{get:()=>['en-US','en']});");
  ctx.addInitScript(
      "const q=window.navigator.permissions.query;"
          + "window.navigator.permissions.query=(p)=>p&&p.name==='notifications'"
          + "?Promise.resolve({state:Notification.permission}):q(p);");

  return ctx;
 }

 /**
  * Fetches a URL with a full Chrome render. Throws {@link CaptchaBlockedException} if the site
  * returns a bot-check page so the caller can fall back to another provider.
  */
 public String fetchWithJavascript(String url) {
  try (Page page = context.newPage()) {
   page.navigate(url, new Page.NavigateOptions()
       .setWaitUntil(WaitUntilState.DOMCONTENTLOADED)
       .setTimeout(20000));

   dismissConsentIfPresent(page);

   // Wait for the results container rather than NETWORKIDLE — Google holds connections open,
   // so NETWORKIDLE usually just times out even on a perfectly good page.
   try {
    page.waitForSelector("#search, #rso, #res, div[role='main']",
        new Page.WaitForSelectorOptions().setTimeout(8000));
   } catch (PlaywrightException noResults) {
    page.waitForLoadState(LoadState.LOAD);
   }

   // Small human-like pause before reading the DOM.
   page.waitForTimeout(ThreadLocalRandom.current().nextInt(400, 1100));

   String rawHtml = page.content();

   // Google redirects blocked traffic to /sorry/ ; also scan the body for the usual phrases.
   if (page.url().contains("/sorry/")
       || rawHtml.contains("unusual traffic")
       || rawHtml.contains("detected unusual")
       || rawHtml.contains("check if you're a real person")
       || rawHtml.contains("not a robot")) {
    throw new CaptchaBlockedException("Target returned a bot-check page for: " + url);
   }

   return Jsoup.parse(rawHtml).text();
  }
 }

 /** Clicks through Google's cookie/consent interstitial the first time it appears. */
 private void dismissConsentIfPresent(Page page) {
  String[] consentButtons = {
      "#L2AGLb",                          // Google's "Accept all" button id
      "button:has-text('Accept all')",
      "button:has-text('Reject all')",
      "button:has-text('I agree')"
  };
  for (String sel : consentButtons) {
   try {
    var btn = page.querySelector(sel);
    if (btn != null && btn.isVisible()) {
     btn.click();
     page.waitForTimeout(500);
     return;
    }
   } catch (PlaywrightException ignored) { }
  }
 }
}