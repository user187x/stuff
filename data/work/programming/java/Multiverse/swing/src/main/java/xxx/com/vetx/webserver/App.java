package xxx.com.vetx.webserver;

import io.vertx.core.Vertx;

public class App {

  public static void main(String[] args) {

    Vertx.vertx()
        .createHttpServer()
        .requestHandler(
            req ->
                req.response()
                    .putHeader("content-type", "text/plain")
                    .end("Hello from Vert.x Future!"))
        .listen(8080) // This returns a Future<HttpServer>
        .onSuccess(
            server -> { // Use onSuccess for the success case
              System.out.println("HTTP server started on port " + server.actualPort());
            })
        .onFailure(
            error -> { // Use onFailure for the error case
              System.err.println("HTTP server failed to start " + error.getMessage());
              System.exit(1);
            });
  }
}
