package xxx.com.os.task;

import org.htmlunit.BrowserVersion;
import org.htmlunit.WebClient;
import org.htmlunit.html.HtmlPage;
import org.htmlunit.html.HtmlSubmitInput;
import org.htmlunit.html.HtmlTextArea;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;

public class GoogleSearchHtmlUnit {

    // IMPORTANT: This code requires the Jsoup and HtmlUnit libraries.
    // Jsoup: https://jsoup.org/download
    // HtmlUnit: https://htmlunit.sourceforge.io/download.html

    public static void main(String[] args) {
        // The search query you want to look up on Google.
        final String searchQuery = "How to parse HTML in Java";
        System.out.println("Searching Google for: " + searchQuery + "\n");

        try (WebClient webClient = new WebClient(BrowserVersion.CHROME)) {
            // Configure HtmlUnit to mimic a real browser more closely.
            webClient.getOptions().setJavaScriptEnabled(true); // Enable JS for better compatibility with Google's page.
            webClient.getOptions().setCssEnabled(false); // No need for CSS.
            webClient.getOptions().setThrowExceptionOnScriptError(false);
            webClient.getOptions().setRedirectEnabled(true);

            // 1. Load the Google homepage.
            HtmlPage homePage = webClient.getPage("https://www.google.com");

            // 2. Find the search input (now a textarea), set the query, and submit the form.
            HtmlTextArea searchInput = (HtmlTextArea) homePage.getElementByName("q");
            searchInput.setText(searchQuery);
            HtmlSubmitInput submitButton = (HtmlSubmitInput) homePage.getElementByName("btnK"); // Use "btnK" for homepage.
            HtmlPage resultsPage = submitButton.click();

            // Wait briefly for any JS to process (if enabled).
            webClient.waitForBackgroundJavaScript(2000);

            // 3. Parse the results HTML with Jsoup.
            Document doc = Jsoup.parse(resultsPage.asXml());

            // For debugging, save the HTML:
            // java.nio.file.Files.writeString(java.nio.file.Paths.get("google_results.html"), doc.html());

            // 4. Select the search result blocks (updated for current Google structure).
            Elements searchResults = doc.select("div.g");

            if (searchResults.isEmpty()) {
                System.out.println("No search results found. Google's page structure may have changed, or the request was blocked.");
                return;
            }

            System.out.println("Found " + searchResults.size() + " potential results. Displaying top results:\n");

            // 5. Iterate and extract title, link, and description.
            int resultCount = 0;
            for (Element result : searchResults) {
                Element titleElement = result.select("h3").first();
                Element linkElement = result.select(".yuRUbf > a").first(); // More specific selector for the main link.
                Element descriptionElement = result.select(".VwiC3b").first(); // Updated for current description class.

                if (titleElement != null && linkElement != null) {
                    String title = titleElement.text();
                    String url = linkElement.attr("href");
                    String description = (descriptionElement != null) ? descriptionElement.text() : "No description available.";

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

        } catch (IOException e) {
            System.err.println("An error occurred while trying to fetch the search results:");
            e.printStackTrace();
        } catch (ClassCastException e) {
            System.err.println("Element type mismatch—Google's structure may have changed again. Check the saved HTML for details.");
            e.printStackTrace();
        }
    }
}