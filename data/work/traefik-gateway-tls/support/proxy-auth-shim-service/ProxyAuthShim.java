import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class ProxyAuthShim {
  // Initialize a reusable HTTP Client
  private static final HttpClient httpClient =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

  public static void main(String[] args) throws IOException {
    int port = 8080;

    // Pull the URLs from the Deployment environment variables
    String keycloakUrl =
        System.getenv()
            .getOrDefault(
                "KEYCLOAK_AUTH_URL",
                "http://keycloak.default.svc.cluster.local:8080/realms/master/protocol/openid-connect/userinfo");
    String externalApiUrl =
        System.getenv()
            .getOrDefault("EXTERNAL_API_URL", "https://api.external-provider.com/validate");

    HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

    server.createContext(
        "/verify",
        new HttpHandler() {
          @Override
          public void handle(HttpExchange exchange) throws IOException {
            // Extract the Authorization header
            String authHeader = exchange.getRequestHeaders().getFirst("Authorization");

            if (authHeader == null || authHeader.isEmpty()) {
              sendResponse(exchange, 401, "Missing Authorization Header");
              return;
            }

            try {
              // ==========================================
              // 1. Check Internal Keycloak Service
              // ==========================================
              HttpRequest keycloakReq =
                  HttpRequest.newBuilder()
                      .uri(URI.create(keycloakUrl))
                      .header("Authorization", authHeader)
                      .GET()
                      .build();

              HttpResponse<String> keycloakRes =
                  httpClient.send(keycloakReq, HttpResponse.BodyHandlers.ofString());

              if (keycloakRes.statusCode() != 200) {
                sendResponse(exchange, 401, "Unauthorized by Internal Keycloak");
                System.out.println(
                    "Validation failed at Keycloak for: " + exchange.getRemoteAddress());
                return;
              }

              // ==========================================
              // 2. Check External 3rd Party API
              // ==========================================
              HttpRequest externalReq =
                  HttpRequest.newBuilder()
                      .uri(URI.create(externalApiUrl))
                      .header("Authorization", authHeader)
                      .GET()
                      .build();

              HttpResponse<String> externalRes =
                  httpClient.send(externalReq, HttpResponse.BodyHandlers.ofString());

              if (externalRes.statusCode() != 200) {
                sendResponse(exchange, 401, "Unauthorized by External API");
                System.out.println(
                    "Validation failed at External API for: " + exchange.getRemoteAddress());
                return;
              }

              // ==========================================
              // 3. Success - Both services approved
              // ==========================================
              // Note: You can parse keycloakRes.body() here using a JSON library
              // to extract real usernames/roles to inject dynamically.
              exchange.getResponseHeaders().add("X-Forwarded-User", "verified-user");

              sendResponse(exchange, 200, "Authorized");
              System.out.println("Validation successful for: " + exchange.getRemoteAddress());

            } catch (InterruptedException e) {
              sendResponse(exchange, 500, "Internal Server Error");
            }
          }
        });

    server.setExecutor(null);
    server.start();
    System.out.println("Proxy Auth Shim listening on port " + port);
    System.out.println(" -> Keycloak URL: " + keycloakUrl);
    System.out.println(" -> External API URL: " + externalApiUrl);
  }

  private static void sendResponse(HttpExchange exchange, int statusCode, String response)
      throws IOException {
    exchange.sendResponseHeaders(statusCode, response.getBytes().length);
    OutputStream os = exchange.getResponseBody();
    os.write(response.getBytes());
    os.close();
  }
}
