package xxx.com.vetx.websockets;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.WebSocket;
import io.vertx.core.http.WebSocketConnectOptions;
import io.vertx.core.net.ProxyOptions;
import io.vertx.core.net.ProxyType;

import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class WebSocketClient {

  public static class Config {
    private String host = "localhost";
    private int port = 8989;
    private boolean ssl = false;
    private String path = "/";
    private long pingIntervalMillis = TimeUnit.SECONDS.toMillis(20);
    private boolean autoReconnect = true;
    private long reconnectBaseDelayMillis = 500;   // exponential backoff base
    private long reconnectMaxDelayMillis = 10_000; // cap
    private ProxyOptions proxyOptions;             // optional

    public String getHost() { return host; }
    public int getPort() { return port; }
    public boolean isSsl() { return ssl; }
    public String getPath() { return path; }
    public long getPingIntervalMillis() { return pingIntervalMillis; }
    public boolean isAutoReconnect() { return autoReconnect; }
    public long getReconnectBaseDelayMillis() { return reconnectBaseDelayMillis; }
    public long getReconnectMaxDelayMillis() { return reconnectMaxDelayMillis; }
    public ProxyOptions getProxyOptions() { return proxyOptions; }

    public Config setHost(String host) { this.host = Objects.requireNonNull(host); return this; }
    public Config setPort(int port) { this.port = port; return this; }
    public Config setSsl(boolean ssl) { this.ssl = ssl; return this; }
    public Config setPath(String path) { this.path = Objects.requireNonNull(path); return this; }
    public Config setPingIntervalMillis(long pingIntervalMillis) { this.pingIntervalMillis = pingIntervalMillis; return this; }
    public Config setAutoReconnect(boolean autoReconnect) { this.autoReconnect = autoReconnect; return this; }
    public Config setReconnectBaseDelayMillis(long v) { this.reconnectBaseDelayMillis = v; return this; }
    public Config setReconnectMaxDelayMillis(long v) { this.reconnectMaxDelayMillis = v; return this; }
    public Config setHttpProxy(String host, int port) {
      this.proxyOptions = new ProxyOptions().setType(ProxyType.HTTP).setHost(host).setPort(port);
      return this;
    }
  }

  private final Vertx vertx;
  private final io.vertx.core.http.WebSocketClient wsClient;
  private final Config cfg;

  private WebSocket ws;
  private long pingTimerId = -1;
  private int reconnectAttempts = 0;

  private Consumer<String> onText;
  private Consumer<Buffer> onBinary;
  private Consumer<Throwable> onError;
  private Runnable onOpen;
  private Runnable onClose;

  public WebSocketClient(Vertx vertx, Config config) {
    this.vertx = Objects.requireNonNull(vertx);
    this.cfg = Objects.requireNonNull(config);

    HttpClientOptions opts = new HttpClientOptions()
        .setDefaultHost(cfg.getHost())
        .setDefaultPort(cfg.getPort())
        .setSsl(cfg.isSsl())
        .setVerifyHost(false)
        .setTrustAll(true)
        .setKeepAlive(true)
        .setConnectTimeout(10_000);

    if (cfg.getProxyOptions() != null) {
      opts.setProxyOptions(cfg.getProxyOptions());
    }

    // Vert.x 5: create a WebSocketClient, NOT an HttpClient
    this.wsClient = vertx.createWebSocketClient();
  }

  /** Connect (or reconnect) to the WebSocket endpoint. */
  public Future<Void> connect() {
    Promise<Void> promise = Promise.promise();

    WebSocketConnectOptions wsOpts = new WebSocketConnectOptions()
        .setHost(cfg.getHost())
        .setPort(cfg.getPort())
        .setSsl(cfg.isSsl())
        .setURI(cfg.getPath());

    wsClient.connect(wsOpts)
        .onSuccess(socket -> {
          this.ws = socket;
          reconnectAttempts = 0; // reset backoff

          if (onOpen != null) onOpen.run();

          // Message handlers
          ws.textMessageHandler(msg -> { if (onText != null) onText.accept(msg); });
          ws.binaryMessageHandler(buf -> { if (onBinary != null) onBinary.accept(buf); });

          // Error & lifecycle
          ws.exceptionHandler(err -> { if (onError != null) onError.accept(err); });
          ws.closeHandler(v -> handleClosed());

          // Optional keepalive ping
          schedulePing();

          promise.complete();
        })
        .onFailure(err -> {
          if (onError != null) onError.accept(err);
          if (cfg.isAutoReconnect()) scheduleReconnect();
          promise.fail(err);
        });

    return promise.future();
  }

  /** Send a UTF-8 text message. */
  public void sendText(String msg) {
    if (ws == null) throw new IllegalStateException("Not connected");
    ws.writeTextMessage(msg);
  }

  /** Send a binary message. */
  public void sendBinary(Buffer buffer) {
    if (ws == null) throw new IllegalStateException("Not connected");
    ws.writeBinaryMessage(buffer);
  }

  /** Close the current WebSocket (and stop auto-reconnect). */
  public void close() {
    cancelPing();
    if (ws != null) {
      cfg.setAutoReconnect(false); // prevent reconnect on intentional close
      ws.close();
      ws = null;
    }
  }

  private void handleClosed() {
    cancelPing();
    if (onClose != null) onClose.run();
    if (cfg.isAutoReconnect()) scheduleReconnect();
  }

  private void schedulePing() {
    cancelPing();
    if (cfg.getPingIntervalMillis() <= 0) return;
    pingTimerId = vertx.setPeriodic(cfg.getPingIntervalMillis(), id -> {
      if (ws != null && !ws.isClosed()) {
        ws.writePing(Buffer.buffer());
      }
    });
  }

  private void cancelPing() {
    if (pingTimerId != -1) {
      vertx.cancelTimer(pingTimerId);
      pingTimerId = -1;
    }
  }

  private void scheduleReconnect() {
    long delay = Math.min(cfg.getReconnectMaxDelayMillis(),
        (long) (cfg.getReconnectBaseDelayMillis() * Math.pow(2, reconnectAttempts++)));
    vertx.setTimer(delay, t -> connect());
  }

  // ---- Hooks ----
  public WebSocketClient onText(Consumer<String> handler) { this.onText = handler; return this; }
  public WebSocketClient onBinary(Consumer<Buffer> handler) { this.onBinary = handler; return this; }
  public WebSocketClient onError(Consumer<Throwable> handler) { this.onError = handler; return this; }
  public WebSocketClient onOpen(Runnable handler) { this.onOpen = handler; return this; }
  public WebSocketClient onClose(Runnable handler) { this.onClose = handler; return this; }

  // ---- Convenience: quick demo ----
  public static void main(String[] args) {

    Vertx vertx = Vertx.vertx();

    WebSocketClient client = new WebSocketClient(vertx, new Config()
        .setHost(WebSocketServer.SERVER_IP)
        .setPort(WebSocketServer.WEBSOCKET_PORT)
        .setPath(WebSocketServer.WEBSOCKET_PATH)
        .setPingIntervalMillis(20_000)
        .setAutoReconnect(true)
    );

    client
    .onOpen(() -> System.out.println("WebSocket Client Connected"))
    .onText(msg -> System.out.println("WebSocket Client : " + msg))
    .onError(err -> System.err.println("WebSocket Client Error : " + err))
    .onClose(() -> System.out.println("WebSocket Client Closed"));

    client.connect()
    .onSuccess(v -> client.sendText("Hello From -> Bob"))
    .onFailure(Throwable::printStackTrace);

    // Graceful shutdown on Ctrl+C / SIGTERM
    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      System.out.println("Shutdown signal received. Closing client...");
      try {

        client.sendText("Test Complete");

        // closes WebSocket & stops auto-reconnect
        client.close();

        vertx.close()
            .onSuccess(v -> System.out.println("Shutdown complete"))
            .onFailure(err -> System.err.println("Shutdown error: " + err))
            .toCompletionStage().toCompletableFuture().join(); // block until done
      }
      catch (Exception e) {
        e.printStackTrace();
      }
    }));
  }
}
