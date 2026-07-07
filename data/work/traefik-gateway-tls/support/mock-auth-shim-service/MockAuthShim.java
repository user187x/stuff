import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;

public class MockAuthShim {
    public static void main(String[] args) throws IOException {
        int port = 8080;
        // Create an HTTP server listening on port 8080
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

        // Listen on the /verify path configured in the Traefik middleware
        server.createContext("/verify", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                // 1. Inject the headers that Traefik will copy and pass to the backend
                exchange.getResponseHeaders().add("X-Forwarded-User", "mock-admin-user");
                exchange.getResponseHeaders().add("X-Forwarded-Groups", "platform-admins,developers");

                // 2. Return HTTP 200 OK to allow the request through
                String response = "Authentication successful (Mock)";
                exchange.sendResponseHeaders(200, response.getBytes().length);
                
                OutputStream os = exchange.getResponseBody();
                os.write(response.getBytes());
                os.close();
                
                System.out.println("Processed auth validation for: " + exchange.getRemoteAddress());
            }
        });

        server.setExecutor(null); // creates a default executor
        server.start();
        System.out.println("Mock Pre-Auth Shim listening on port " + port);
    }
}
