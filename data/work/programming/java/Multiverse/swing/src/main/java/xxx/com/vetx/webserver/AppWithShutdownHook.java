package xxx.com.vetx.webserver;

import xxx.com.vetx.support.VertxApp;

public class AppWithShutdownHook {

  public static void main(String[] args) {

    VertxApp.create()
      .withShutdownHook()
      .start(8080, (vertx, port) -> // Lambda now accepts vertx and port
        vertx.createHttpServer()
          .requestHandler(req -> req.response()
              .end("Hello from a Vert.x!"))
          .listen(port) // Use the port variable here
          .onSuccess(server -> {
            System.out.println("HTTP server started on port " + server.actualPort());
            System.out.println("Press Ctrl+C to shut down.");
          })
          .onFailure(Throwable::printStackTrace)
      );
    }
  }
