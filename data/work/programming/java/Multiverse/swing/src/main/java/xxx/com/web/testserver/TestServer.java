package xxx.com.web.testserver;

import com.google.gson.*;
import io.javalin.Javalin;

import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class TestServer {

  private static final List<JsonObject> MOCK_DATA = new ArrayList<>();
  private static final int ITEMS_PER_PAGE = 10;
  private static final int TOTAL_ITEMS = 100;

  public static void main(String[] args) {
    // Generate some mock data for the pagination endpoint
    for (int i = 1; i <= TOTAL_ITEMS; i++) {
      JsonObject item = new JsonObject();
      item.addProperty("id", i);
      item.addProperty("name", "Item #" + i);
      item.addProperty("description", "This is the description for item " + i + ".");
      MOCK_DATA.add(item);
    }

    Javalin app = Javalin.create().start(4567);
    System.out.println("Javalin test server running on http://localhost:4567");

    // === Endpoint 1: A large binary file ===
    app.get(
        "/largefile",
        ctx -> {
          System.out.println("Request received for /largefile");
          int fileSizeMB = 20;
          int fileSize = fileSizeMB * 1024 * 1024;

          ctx.header("Content-Disposition", "attachment; filename=\"large_file.bin\"");
          ctx.contentType("application/octet-stream");
          ctx.header("Content-Length", String.valueOf(fileSize));

          try (OutputStream os = ctx.res().getOutputStream()) {
            byte[] buffer = new byte[4096];
            Random random = new Random();
            int written = 0;
            while (written < fileSize) {
              random.nextBytes(buffer);
              int toWrite = Math.min(buffer.length, fileSize - written);
              os.write(buffer, 0, toWrite);
              written += toWrite;
            }
            os.flush();
          } catch (Exception e) {
            System.err.println("Error streaming large file: " + e.getMessage());
          }
        });

    // === Endpoint 2: A paginated JSON API ===
    app.get(
        "/items",
        ctx -> {
          System.out.println("Request received for /items");
          int page = ctx.queryParamAsClass("page", Integer.class).getOrDefault(1);

          int start = (page - 1) * ITEMS_PER_PAGE;
          int end = Math.min(start + ITEMS_PER_PAGE, MOCK_DATA.size());

          if (start >= MOCK_DATA.size()) {
            ctx.status(404).result("Page not found");
            return;
          }

          Gson gson = new GsonBuilder().setPrettyPrinting().create();

          List<JsonObject> pageData = MOCK_DATA.subList(start, end);
          JsonElement jsonElement = gson.toJsonTree(pageData);

          JsonArray jsonResponse = jsonElement.getAsJsonArray();

          // --- Build the Link Header ---
          String baseUrl = "http://localhost:4567/items";
          int lastPage = (int) Math.ceil((double) TOTAL_ITEMS / ITEMS_PER_PAGE);
          List<String> links = new ArrayList<>();

          if (page < lastPage) {
            links.add(String.format("<%s?page=%d>; rel=\"next\"", baseUrl, page + 1));
            links.add(String.format("<%s?page=%d>; rel=\"last\"", baseUrl, lastPage));
          }
          if (page > 1) {
            links.add(String.format("<%s?page=%d>; rel=\"prev\"", baseUrl, page - 1));
            links.add(String.format("<%s?page=%d>; rel=\"first\"", baseUrl, 1));
          }

          ctx.header("Link", String.join(", ", links));
          ctx.contentType("application/json");
          ctx.result(jsonResponse.toString()); // Pretty print JSON
        });
  }
}
