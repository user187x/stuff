package xxx.com.web.html;

import com.helger.css.decl.*;
import com.helger.css.writer.CSSWriter;
import com.helger.css.writer.CSSWriterSettings;
import htmlflow.HtmlFlow;
import htmlflow.HtmlView;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;

/**
 * Generates a dynamic HTML page using htmlflow and ph-css.
 * The generated page is a boilerplate for a single-page React application.
 */
public class HtmlResponseWriter {

  /**
   * Generates a string containing dynamic CSS rules using ph-css.
   * @return A string representation of the CSS stylesheet.
   */
  private String generateCss() {
    // Create a sheet for CSS 3.0
    final CascadingStyleSheet aCSS = new CascadingStyleSheet();

    // Style for the body
    final CSSStyleRule bodyRule = new CSSStyleRule();
    final CSSSelector bodySelector = new CSSSelector();
    bodySelector.addMember(new CSSSelectorSimpleMember("body"));
    bodyRule.addSelector(bodySelector);
    bodyRule.addDeclaration(new CSSDeclaration("margin", CSSExpression.createSimple("0")));
    bodyRule.addDeclaration(new CSSDeclaration("font-family", CSSExpression.createSimple("-apple-system, BlinkMacSystemFont, 'Segoe UI', 'Roboto', 'Oxygen', 'Ubuntu', 'Cantarell', 'Fira Sans', 'Droid Sans', 'Helvetica Neue', sans-serif")));
    bodyRule.addDeclaration(new CSSDeclaration("background-color", CSSExpression.createSimple("#f0f2f5")));
    aCSS.addRule(bodyRule);

    // Style for the main container
    final CSSStyleRule containerRule = new CSSStyleRule();
    final CSSSelector containerSelector = new CSSSelector();
    containerSelector.addMember(new CSSSelectorSimpleMember(".container"));
    containerRule.addSelector(containerSelector);
    containerRule.addDeclaration(new CSSDeclaration("display", CSSExpression.createSimple("flex")));
    containerRule.addDeclaration(new CSSDeclaration("justify-content", CSSExpression.createSimple("center")));
    containerRule.addDeclaration(new CSSDeclaration("align-items", CSSExpression.createSimple("center")));
    containerRule.addDeclaration(new CSSDeclaration("height", CSSExpression.createSimple("100vh")));
    containerRule.addDeclaration(new CSSDeclaration("text-align", CSSExpression.createSimple("center")));
    aCSS.addRule(containerRule);

    // Style for the content card
    final CSSStyleRule cardRule = new CSSStyleRule();
    final CSSSelector cardSelector = new CSSSelector();
    cardSelector.addMember(new CSSSelectorSimpleMember(".card"));
    cardRule.addSelector(cardSelector);
    cardRule.addDeclaration(new CSSDeclaration("background-color", CSSExpression.createSimple("white")));
    cardRule.addDeclaration(new CSSDeclaration("padding", CSSExpression.createSimple("40px")));
    cardRule.addDeclaration(new CSSDeclaration("border-radius", CSSExpression.createSimple("8px")));
    cardRule.addDeclaration(new CSSDeclaration("box-shadow", CSSExpression.createSimple("0 4px 8px rgba(0,0,0,0.1)")));
    aCSS.addRule(cardRule);

    // Convert the CSS object model to a string
    // Use constructor that defaults to CSS 3.0 to avoid version resolution issues.
    final CSSWriterSettings settings = new CSSWriterSettings(false); // false = not optimized
    try {
      return new CSSWriter(settings).getCSSAsString(aCSS);
    } catch (final Exception e) {
      throw new RuntimeException("Failed to generate CSS string", e);
    }
  }

  /**
   * Creates the HtmlView object representing the dynamic HTML page.
   * @return An HtmlView instance.
   */
  private HtmlView<Void> createHtmlView() {

    final String dynamicCss = generateCss();

    // Use the HtmlFlow factory with a lambda to define the template
    // @formatter:off
    return HtmlFlow.view(view -> view
        .html().attrLang("en")
          .head()
            .meta().attrCharset("UTF-8").__()
            .meta().attrName("viewport").attrContent("width=device-width, initial-scale=1.0").__()
            .title().text("React App Shell").__()
            .style().raw(dynamicCss).__() // Inject the CSS string here
          .__() // head
        .body()
          // The main div where the React app will be mounted
          .div().attrId("root")
            // You can add placeholder content here that React will replace
            .div().attrClass("container")
              .div().attrClass("card")
                .h1().text("Loading Java-Generated Shell...").__()
                .p().text("Waiting for React to initialize.").__()
              .__() // card
            .__() // container
          .__() // root
          // Link to the React bundle. In a real app, this path would be managed by a build tool.
          .script().attrSrc("/static/js/main.js").__()
        .__() // body
      .__() // html
    );
    // @formatter:on
  }

  /**
   * Writes the generated HTML to a standard PrintStream (e.g., System.out).
   *
   * @param out The PrintStream to write to.
   */
  public void write(PrintStream out) {
    HtmlView<Void> view = createHtmlView();
    out.print(view.render());
  }

  /**
   * Writes the generated HTML to an HttpServletResponse, setting cache headers.
   * This version is for use in a web application context.
   *
   * @param response The HttpServletResponse object from a servlet or controller.
   */
  public void writeWithCaching(HttpServletResponse response) {
    try {
      // Set Cache-Control header to allow caching for 1 hour (3600 seconds)
      response.setHeader("Cache-Control", "public, max-age=3600");
      response.setContentType("text/html; charset=UTF-8");

      HtmlView<Void> view = createHtmlView();
      // Get the writer from the response object and print the rendered HTML
      response.getWriter().print(view.render());

    } catch (IOException e) {
      throw new UncheckedIOException("Failed to write HTML to HttpServletResponse", e);
    }
  }
}


