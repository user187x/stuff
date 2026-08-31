package mcp.support;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.Map;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

public class GoogleCaptchaBypass {

 // --- Configuration for Google Search ---
 private static final String TARGET_URL = "https://www.google.com/"; // The page to fetch
 private static final String SUBMIT_ENDPOINT = "https://www.google.com/search"; // Google's actual search submission endpoint
 private static final String CAPTCHA_INPUT_NAME = "g-recaptcha-response"; // The name attribute for the token

 // --- The actual search term you want to query ---
 private static final String SEARCH_QUERY = "best modern espresso machine";

 // --- Form Data (Payload) ---
 private static final Map<String, String> FORM_DATA = new HashMap<>();
 static {
  // The 'q' parameter is what tells Google what to search for
  FORM_DATA.put("q", SEARCH_QUERY);

  // Standard hidden field settings for a proper Google search form submission
  FORM_DATA.put("hl", "en"); // Host Language: English
 }

 public static void main(String[] args) {
  System.out.println("==================================================");
  System.out.println("🌐 Google Search CAPTCHA Bypass Initiated (V2 - FIXED)");
  System.out.println("🔍 Searching for: " + SEARCH_QUERY);
  System.out.println("==================================================");

  try {
   // 1. Fetch the initial page content from Google
   String htmlContent = fetchPageContent(TARGET_URL);

   // 2. Extract the reCAPTCHA token (using the corrected selector)
   String captchaToken = extractToken(htmlContent);

   if (captchaToken == null) {
    System.out.println("\n[!!!] ERROR: Still couldn't find the reCAPTCHA token on Google.");
    System.out.println("   FINAL CHECK: The token might be loaded *after* the initial HTML load. Try running again!");
    return;
   }
   System.out.println("\n[+] 2. Successfully extracted CAPTCHA Token: " + captchaToken.substring(0, Math.min(captchaToken.length(), 40)) + "...");

   // 3. Prepare the final payload (Search Query + Token + Language)
   Map<String, String> payload = new HashMap<>(FORM_DATA);
   payload.put(CAPTCHA_INPUT_NAME, captchaToken); // Inject the captured token

   System.out.println("\n[+] 3. Submitting search query to: " + SUBMIT_ENDPOINT);

   // 4. Submit the POST request
   String responseBody = submitForm(SUBMIT_ENDPOINT, payload);

   // 5. Display results
   System.out.println("\n==================================================");
   System.out.println("[✅ SUCCESS] Google Search Results Received!");
   System.out.println("==================================================");
   System.out.println("Response Body Preview (first 1500 characters):");
   System.out.println("--------------------------------------------------");
   System.out.println(responseBody.substring(0, Math.min(responseBody.length(), 1500)));
   System.out.println("--------------------------------------------------");

  } catch (IOException e) {
   System.err.println("\n[!!!] A Network I/O error occurred: " + e.getMessage());
  } catch (InterruptedException e) {
   System.err.println("\n[!!!] The process was interrupted: " + e.getMessage());
   Thread.currentThread().interrupt();
  } catch (Exception e) {
   System.err.println("\n[!!!] An unexpected error occurred: " + e.getMessage());
  }
 }

 /**
  * Performs an HTTP GET request to fetch the Google search page HTML.
  */
 private static String fetchPageContent(String url) throws IOException, InterruptedException {
  System.out.println("[*] 1. Fetching Google page...");

  HttpClient client = HttpClient.newHttpClient();
  HttpRequest request = HttpRequest.newBuilder()
      .uri(URI.create(url))
      // CRITICAL: A high-quality User-Agent helps bypass basic bot detection
      .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
      .GET()
      .build();

  HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

  if (response.statusCode() >= 400) {
   throw new IOException("HTTP Error fetching Google page. Status Code: " + response.statusCode());
  }
  return response.body();
 }

 /**
  * Uses Jsoup to parse the HTML and find the reCAPTCHA token.
  * *** This selector has been corrected to target a <textarea> ***
  */
 private static String extractToken(String htmlContent) {
  Document doc = Jsoup.parse(htmlContent);

  // *** THE FIX IS HERE: We look for a <textarea> tag with the matching name ***
  Element tokenElement = doc.selectFirst("textarea[name='" + CAPTCHA_INPUT_NAME + "']");

  if (tokenElement != null) {
   return tokenElement.val(); // Retrieve the token value
  }
  return null;
 }

 /**
  * Performs an HTTP POST request to submit the form data to Google.
  */
 private static String submitForm(String submitUrl, Map<String, String> payload) throws IOException, InterruptedException {

  // Build the POST body string: q=query&hl=en&g-recaptcha-response=TOKEN
  StringBuilder bodyBuilder = new StringBuilder();
  boolean first = true;
  for (Map.Entry<String, String> entry : payload.entrySet()) {
   if (!first) {
    bodyBuilder.append("&");
   }
   // URL encoding handles special characters like spaces in the query "best modern espresso machine"
   bodyBuilder.append(entry.getKey()).append("=").append(entry.getValue());
   first = false;
  }
  String body = bodyBuilder.toString();

  // Build the Request object
  HttpClient client = HttpClient.newHttpClient();
  HttpRequest request = HttpRequest.newBuilder()
      .uri(URI.create(submitUrl))
      .header("Content-Type", "application/x-www-form-urlencoded")
      .header("User-Agent", "Mozilla/5.0 (Java HTTP Client) / GoogleBot")
      .POST(HttpRequest.BodyPublishers.ofString(body))
      .build();

  // Send the request and get the response body (which is the SERP HTML)
  HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

  if (response.statusCode() >= 400) {
   throw new IOException("HTTP Error submitting form. Status Code: " + response.statusCode());
  }
  return response.body();
 }
}