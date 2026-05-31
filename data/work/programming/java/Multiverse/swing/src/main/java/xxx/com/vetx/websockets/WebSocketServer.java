package xxx.com.vetx.websockets;

import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.WebSocketFrame;
import io.vertx.ext.web.Router;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class WebSocketServer {

  public static final boolean ENABLE_WEB_SERVER = true;
  public static final boolean ENABLE_WEBSOCKET_SERVER = true;
  public static final String SERVER_IP = "127.0.0.1";
  public static final int SERVER_PORT = 8999;
  public static final int WEBSOCKET_PORT = 9999;
  public static final String WEBSOCKET_PATH = "/chat/v1";

  public static void main(String[] args) {

    Vertx vertx = Vertx.vertx();

    // 0 = no idle timeout
    HttpServerOptions opts = new HttpServerOptions()
        .setIdleTimeout(0)
        .setIdleTimeoutUnit(TimeUnit.SECONDS)
        .setTcpKeepAlive(true);

    HttpServer server = vertx.createHttpServer(opts);

    if (ENABLE_WEB_SERVER) {
      setupWebServer(vertx, server);
    } else {
      System.out.println("WebServer Routes : DISABLED.");
      // If web server is off, all non-WS requests get a 404
      server.requestHandler(req -> req.response().setStatusCode(404).end("Not Found"));
    }

    // Conditionally set up the WebSocket handler
    if (ENABLE_WEBSOCKET_SERVER) {
      setupWebSocketServer(vertx, server);
    } else {
      System.out.println("WebSocket Server : DISABLED.");
    }

    // --- Start the server ---
    server.listen(WEBSOCKET_PORT)
        .onSuccess(s -> System.out.println("WebServer Running Port : " + s.actualPort()))
        .onFailure(err -> System.err.println("WebServer Failure : " + err.getMessage()));

    // The separate TCP server remains unaffected
    setupTcpServer(vertx);

    // Graceful shutdown on Ctrl+C / SIGTERM
    Runtime.getRuntime().addShutdownHook(new Thread(() ->
        server.close().compose(v -> vertx.close())
            .onSuccess(v -> System.out.println("Shutdown complete"))
            .onFailure(err -> System.err.println("Shutdown error: " + err))
    ));
  }

  /**
   * Configures and attaches all web server routes (e.g., /hello).
   * @param vertx The Vert.x instance.
   * @param server The HttpServer to attach the router to.
   */
  private static void setupWebServer(Vertx vertx, HttpServer server) {

    System.out.println("Web server routes are ENABLED.");
    Router router = Router.router(vertx);

    router.route().handler(ctx -> {
      LocalDateTime now = LocalDateTime.now();
      DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
      String formattedDate = now.format(formatter);
      String method = ctx.request().method().name();
      String path = ctx.request().path();
      String remoteAddress = ctx.request().remoteAddress().toString();
      System.out.printf("[%s] %s %s from %s%n", formattedDate, method, path, remoteAddress);
      ctx.next();
    });

    router.get("/").handler(routingContext -> {
      LocalDateTime now = LocalDateTime.now();
      DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
      String formattedDate = now.format(formatter);
      routingContext.response()
          .putHeader("content-type", "text/plain")
          .setStatusCode(200)
          .end("Welcome " + formattedDate);
    });

    server.requestHandler(router);
  }

  /**
   * Configures and attaches the WebSocket handler.
   * @param server The HttpServer to attach the WebSocket handler to.
   */
  private static void setupWebSocketServer(Vertx vertx, HttpServer server) {

    System.out.println("WebSocket server : ENABLED.");
    server.webSocketHandler(ws -> {

      if (!ws.path().equals(WEBSOCKET_PATH)) {

        ws.writeFrame(WebSocketFrame.textFrame("WebSocket Server Path : " + WEBSOCKET_PATH, true));
        ws.close();
        return;
      }

      System.out.println("WebSocket Client Connected: " + ws.remoteAddress());

      // Auto-respond to TEXT messages
      ws.textMessageHandler(msg -> {
        // respond automatically
        ws.writeTextMessage("WebSocket Server Received : " + msg);
      });

      // TODO Might want to add accept message first
      // respond automatically
      //ws.binaryMessageHandler(ws::writeBinaryMessage);

      final long PING_INTERVAL_MS = 20_000;

      // if no pong for 90s, close
      final long ZOMBIE_THRESHOLD_MS = 90_000;

      // Per-connection zombie checks
      final Map<String, Long> wsState = new HashMap<>();
      wsState.put("lastPong", System.currentTimeMillis());

      // Start periodic keepalive pings
      long pingId = vertx.setPeriodic(PING_INTERVAL_MS, t -> {

        if (ws.isClosed()) {
          // Already closed; stop the timer
          Long id = wsState.get("pingId");
          if (id != null)
            vertx.cancelTimer(id);

          return;
        }

        long last = wsState.getOrDefault("lastPong", 0L);
        long now  = System.currentTimeMillis();

        // If we've missed pongs for too long, close the socket (watchdog)
        if (now - last > ZOMBIE_THRESHOLD_MS) {
          ws.close((short) 1002, "No pong");
          // closeHandler below will cancel the timer
          return;
        }

        // Send a ping frame to keep intermediaries happy and check liveness
        ws.writeFrame(WebSocketFrame.pingFrame(Buffer.buffer("keepalive")));
      });

      wsState.put("pingId", pingId);

      // Update lastPong whenever we receive a pong
      ws.pongHandler(buf -> {
        wsState.put("lastPong", System.currentTimeMillis());
        System.out.println("Pong <- " + ws.remoteAddress());
      });

      // Ignores to Ping/Pongs/Close Messages
      ws.textMessageHandler(msg -> {

        System.out.println("WebSocket Server <- " + msg);
        ws.writeTextMessage("echo : " + msg);
      });

      // Listens to Ping/Pongs/Close Messages
      ws.frameHandler(frame -> {

        if (frame.isText()) {
          ws.writeFrame(WebSocketFrame.textFrame("echo : " + frame.textData(), true));
        }
      });

      ws.closeHandler(v -> {

        System.out.println("WebSocket Client closed: " + ws.remoteAddress());
        Long id = wsState.remove("pingId");

        if (id != null)
          vertx.cancelTimer(id);
      });

      ws.exceptionHandler(err -> System.err.println("WebSocket Server Error : " + err.getMessage()));

    });
  }

  /**
   * Configures and starts the separate TCP echo server.
   * @param vertx The Vert.x instance.
   */
  private static void setupTcpServer(Vertx vertx) {

    vertx.createNetServer()
      .connectHandler(socket -> socket.handler(socket::write))
      .listen(SERVER_PORT)
      .onSuccess(ns -> System.out.println("TCP Server Port :" + ns.actualPort()));
  }
}
