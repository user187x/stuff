package com.xxx.server.web;

import android.annotation.SuppressLint;
import android.content.Context;
import android.net.Uri;
import android.util.Log;
import android.webkit.MimeTypeMap;

import androidx.documentfile.provider.DocumentFile;

import com.xxx.server.web.tls.TLSCertificateManager;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Base64;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.http.ServerWebSocket;
import io.vertx.core.http.WebSocketFrame;
import io.vertx.core.net.PfxOptions;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;

public class HttpServerManager {
    private static final String TAG = "HttpServerManager";
    private final Context context;
    private final Vertx vertx;
    private HttpServer httpServer;
    private Router router;
    // Configuration
    private int port = 8080;
    private String rootFolder = "";
    private boolean redirectToIndex = true;
    private boolean renderFolderContent = true;
    private boolean allowUploads = false;
    // Security settings
    private boolean restrictNetwork = false;
    private boolean ipWhitelist = false;
    private boolean basicAuth = false;
    private boolean tlsEnabled = false;
    private Set<String> allowedIps = new HashSet<>();
    private String authUsername = "";
    private String authPassword = "";
    // Miscellaneous
    private Map<String, String> customHeaders = new HashMap<>();
    private final AtomicBoolean isHttpServerRunning = new AtomicBoolean(false);
    private final AtomicBoolean isWebSocketServerRunning = new AtomicBoolean(true);
    private final Consumer<String> logger;
    private final Consumer<HttpServerRequest> requestNotifier;
    private final ServerStatsManager statsManager;
    private final InactivityManager inactivityManager;
    private final RestRouteManager restRouteManager;
    public static final String SERVER_IP = "0.0.0.0";
    public static final int SERVER_PORT = 8080;
    public static final int SECURE_SERVER_PORT = 8443;
    public static final int WEBSOCKET_PORT = 9999;
    public static final String WEBSOCKET_PATH = "/websocket/v1";
    private final Set<ServerWebSocket> connectedClients = Collections.newSetFromMap(new ConcurrentHashMap<>());


    public HttpServerManager(Context context, Consumer<String> logger, Consumer<HttpServerRequest> requestNotifier,
                             ServerStatsManager statsManager, InactivityManager inactivityManager,
                             RestRouteManager restRouteManager) {
        this.context = context;
        this.vertx = Vertx.vertx();
        this.logger = logger;
        this.requestNotifier = requestNotifier;
        this.statsManager = statsManager;
        this.inactivityManager = inactivityManager;
        this.restRouteManager = restRouteManager;
    }

    public synchronized void stopServer(Handler<Void> onStopped) {
        if (!isHttpServerRunning.get() || httpServer == null) {
            Log.w(TAG, "Server is not running");
            if (onStopped != null) {
                onStopped.handle(null);
            }
            return;
        }

        httpServer.close()
                .onSuccess(v -> {
                    isHttpServerRunning.set(false);
                    isWebSocketServerRunning.set(false);
                    Log.i(TAG, "Server stopped");
                    if (onStopped != null) {
                        onStopped.handle(null);
                    }
                })
                .onFailure(err -> Log.e(TAG, "Error stopping server: " + err.getMessage()));
    }

    private void setupRouter() {
        router = Router.router(vertx);
        // Add security middleware
        router.route().handler(this::handleSecurityChecks);
        // Add custom headers
        router.route().handler(this::addCustomHeaders);
        // Route for the root path to display server status
        router.get("/").handler(this::handleStatusRequest);
        // Handle uploads if enabled
        if (allowUploads) {
            router.route().handler(BodyHandler.create());
            router.post("/upload").handler(this::handleFileUpload);
        }

        restRouteManager.applyRoutesToRouter(router);
        // Handle all requests
        router.route("/*").handler(this::handleRequest);
    }


    private void handleSecurityChecks(io.vertx.ext.web.RoutingContext context) {
        long startTime = System.currentTimeMillis();
        long bytesReceived = context.request().bytesRead();
        statsManager.onRequest(bytesReceived, 0); // We don't know bytes sent yet
        inactivityManager.updateLastActivity();

        HttpServerRequest request = context.request();
        String clientIp = getClientIp(request);

        // IP whitelist check
        if (ipWhitelist && !allowedIps.isEmpty() && !allowedIps.contains(clientIp)) {
            long responseTime = System.currentTimeMillis() - startTime;
            logRequest(request, clientIp, 403, responseTime);
            context.response()
                    .setStatusCode(403)
                    .end("Access denied");
            return;
        }

        // Basic authentication check
        if (basicAuth && !checkBasicAuth(request)) {
            long responseTime = System.currentTimeMillis() - startTime;
            logRequest(request, clientIp, 401, responseTime);
            context.response()
                    .setStatusCode(401)
                    .putHeader("WWW-Authenticate", "Basic realm=\"HTTP Server\"")
                    .end("Authentication required");
            return;
        }

        // Add response end handler to log successful requests
        context.response().endHandler(v -> {
            long responseTime = System.currentTimeMillis() - startTime;
            int statusCode = context.response().getStatusCode();
            logRequest(request, clientIp, statusCode, responseTime);
        });

        // Notify request listener
        requestNotifier.accept(context.request());
        context.next();
    }

    private void logRequest(HttpServerRequest request, String clientIp, int statusCode, long responseTime) {
        String method = request.method().name();
        String path = request.path();

        // Format the log message to match the Live Log Activity expected format
        @SuppressLint("DefaultLocale") String logMessage = String.format("[%s] %s [%d] %s, %d ms",
                clientIp, method, statusCode, path, responseTime);

        logger.accept(logMessage);
    }

    public synchronized void startServer(Handler<Void> onSuccess) {
        if (isHttpServerRunning.get()) {
            Log.w(TAG, "Server is already running");
            if (onSuccess != null) {
                onSuccess.handle(null);
            }
            return;
        }

        try {
            setupRouter();
            HttpServerOptions options = new HttpServerOptions()
                    .setPort(SERVER_PORT)
                    .setHost(SERVER_IP)
                    .setIdleTimeout(0)
                    .setIdleTimeoutUnit(TimeUnit.SECONDS)
                    .setTcpKeepAlive(true);

            if (tlsEnabled) {
                TLSCertificateManager certManager = new TLSCertificateManager(context);
                if (certManager.certificateExists()) {
                    // Correctly get the path to the keystore in the app's private files directory
                    File keystoreFile = new File(context.getFilesDir(), "server_keystore.p12");

                    options
                            .setSsl(true)
                            .setKeyCertOptions(new PfxOptions()
                                    .setPath(keystoreFile.getAbsolutePath()) // Path to your PKCS12 keystore
                                    .setPassword(TLSCertificateManager.KEYSTORE_PASSWORD) // Password for your keystore
                            )
                            // Optional: Specify enabled TLS protocols
                            .addEnabledSecureTransportProtocol("TLSv1.3")
                            .addEnabledSecureTransportProtocol("TLSv1.2");

                    Log.i(TAG, "TLS is enabled. Server will use HTTPS.");

                } else {
                    Log.w(TAG, "TLS is enabled in settings, but no certificate was found. Starting without encryption.");
                    logger.accept("Warning: TLS is enabled, but no certificate found. Server is NOT secure.");
                }
            }


            // Create the server with options
            httpServer = vertx.createHttpServer(options);

            httpServer.connectionHandler(conn -> {
                statsManager.onConnection();
                conn.closeHandler(v -> {
                });
            });

            // Set the request handler to the router for HTTP requests
            httpServer.requestHandler(router);

            // Set the WebSocket handler. It will be active based on isWebSocketServerRunning flag.
            httpServer.webSocketHandler(this::handleWebSocketConnection);

            httpServer.listen(port)
                    .onSuccess(s -> {
                        isHttpServerRunning.set(true);
                        @SuppressLint("DefaultLocale") String startMessage = String.format("Server was started at %s://%s:%d in '%s'",
                                (tlsEnabled ? "https" : "http"), getServerAddress(), port, rootFolder);
                        logger.accept(startMessage);
                        Log.i(TAG, "Server started on port " + port);
                        if (onSuccess != null) {
                            onSuccess.handle(null);
                        }
                    })
                    .onFailure(err -> Log.e(TAG, "Failed to start server: " + err.getMessage()));

        } catch (Exception e) {
            Log.e(TAG, "Error starting server", e);
        }
    }


    private String getServerAddress() {
        // Try to get the actual server address
        try {
            java.net.InetAddress localHost = java.net.InetAddress.getLocalHost();
            return localHost.getHostAddress();
        } catch (Exception e) {
            return "0.0.0.0";
        }
    }

    private void handleWebSocketConnection(final ServerWebSocket ws) {
        if (!isWebSocketServerRunning.get()) {
            ws.close();
            return;
        }

        if (!ws.path().equals(WEBSOCKET_PATH)) {
            ws.writeFrame(WebSocketFrame.textFrame("WebSocket Server Path : " + WEBSOCKET_PATH, true));
            ws.close();
            return;
        }
        connectedClients.add(ws);
        logger.accept("WebSocket Client Connected: " + ws.remoteAddress() + " Total clients: " + connectedClients.size());


        final long PING_INTERVAL_MS = 20_000;
        final long ZOMBIE_THRESHOLD_MS = 90_000;

        final Map<String, Long> wsState = new HashMap<>();
        wsState.put("lastPong", System.currentTimeMillis());

        long pingId = vertx.setPeriodic(PING_INTERVAL_MS, t -> {
            if (ws.isClosed()) {
                Long id = wsState.get("pingId");
                if (id != null)
                    vertx.cancelTimer(id);
                return;
            }

            long last = Optional.ofNullable(wsState.get("lastPong")).orElse(0L);
            long now = System.currentTimeMillis();

            if (now - last > ZOMBIE_THRESHOLD_MS) {
                ws.close((short) 1002, "No pong");
                return;
            }

            ws.writeFrame(WebSocketFrame.pingFrame(Buffer.buffer("keepalive")));
        });

        wsState.put("pingId", pingId);

        ws.pongHandler(buf -> {
            wsState.put("lastPong", System.currentTimeMillis());
            logger.accept("Pong <- " + ws.remoteAddress());
        });

        ws.textMessageHandler(msg -> {
            logger.accept("WebSocket Server <- " + msg);
            // Broadcast the message to all connected clients
            for (ServerWebSocket client : connectedClients) {
                client.writeTextMessage(msg);
            }
        });

        ws.closeHandler(v -> {
            connectedClients.remove(ws);
            logger.accept("WebSocket Client closed: " + ws.remoteAddress() + " Total clients: " + connectedClients.size());
            Long id = wsState.remove("pingId");
            if (id != null)
                vertx.cancelTimer(id);
        });

        ws.exceptionHandler(err -> logger.accept("WebSocket Server Error : " + err.getMessage()));
    }

    private void handleStatusRequest(io.vertx.ext.web.RoutingContext context) {
        String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());
        String html = "<html><head><title>Server Status</title>" +
                "<style>" +
                "body { font-family: Arial, sans-serif; margin: 20px; background: #121212; color: #ffffff; }" +
                "h1 { color: #bb86fc; }" +
                "</style></head>" +
                "<body><h1>Server is Running</h1><p>Current time: " + timestamp + "</p></body></html>";
        context.response()
                .putHeader("Content-Type", "text/html")
                .end(html);
    }


    private void addCustomHeaders(io.vertx.ext.web.RoutingContext context) {
        HttpServerResponse response = context.response();
        for (Map.Entry<String, String> header : customHeaders.entrySet()) {
            response.putHeader(header.getKey(), header.getValue());
        }
        context.next();
    }

    private void handleRequest(io.vertx.ext.web.RoutingContext context) {
        String path = context.request().path();
        try {
            // Handle root folder based on type (URI or file path)
            if (rootFolder.startsWith("content://")) {
                handleDocumentTreeRequest(context, path);
            } else {
                handleFileSystemRequest(context, path);
            }

        } catch (Exception e) {
            Log.e(TAG, "Error handling request", e);
            context.response().setStatusCode(500).end("Internal server error");
        }
    }

    private void handleDocumentTreeRequest(io.vertx.ext.web.RoutingContext context, String path) {
        try {
            DocumentFile rootDoc = DocumentFile.fromTreeUri(this.context, Uri.parse(rootFolder));
            if (rootDoc == null || !rootDoc.exists()) {
                context.response().setStatusCode(500).end("Invalid root folder");
                return;
            }

            DocumentFile requestedFile = navigateToPath(rootDoc, path);
            if (requestedFile == null || !requestedFile.exists()) {
                context.response().setStatusCode(404).end("File not found");
                return;
            }

            if (requestedFile.isDirectory()) {
                handleDirectoryListing(context, requestedFile, path);
            } else {
                serveDocumentFile(context, requestedFile);
            }

        } catch (Exception e) {
            Log.e(TAG, "Error handling document tree request", e);
            context.response().setStatusCode(500).end("Internal server error");
        }
    }

    private void handleFileSystemRequest(io.vertx.ext.web.RoutingContext context, String path) {
        try {
            java.io.File rootDir = new java.io.File(rootFolder);
            if (!rootDir.exists() || !rootDir.isDirectory()) {
                context.response().setStatusCode(500).end("Invalid root folder");
                return;
            }

            java.io.File requestedFile = new java.io.File(rootDir, path);
            // Security check - ensure the requested file is within the root directory
            if (!requestedFile.getCanonicalPath().startsWith(rootDir.getCanonicalPath())) {
                context.response().setStatusCode(403).end("Access denied");
                return;
            }

            if (!requestedFile.exists()) {
                context.response().setStatusCode(404).end("File not found");
                return;
            }

            if (requestedFile.isDirectory()) {
                handleDirectoryListingFile(context, requestedFile, path);
            } else {
                serveFile(context, requestedFile);
            }

        } catch (Exception e) {
            Log.e(TAG, "Error handling file system request", e);
            context.response().setStatusCode(500).end("Internal server error");
        }
    }

    private void handleDirectoryListing(io.vertx.ext.web.RoutingContext context, DocumentFile directory, String path) {
        if (!renderFolderContent) {
            context.response().setStatusCode(403).end("Directory listing disabled");
            return;
        }

        // Check for index.html
        if (redirectToIndex) {
            DocumentFile indexFile = directory.findFile("index.html");
            if (indexFile != null && indexFile.exists()) {
                serveDocumentFile(context, indexFile);
                return;
            }
        }

        generateDirectoryListingHtml(context, directory, path);
    }

    private void handleDirectoryListingFile(io.vertx.ext.web.RoutingContext context, java.io.File directory, String path) {
        if (!renderFolderContent) {
            context.response().setStatusCode(403).end("Directory listing disabled");
            return;
        }

        // Check for index.html
        if (redirectToIndex) {
            java.io.File indexFile = new java.io.File(directory, "index.html");
            if (indexFile.exists() && indexFile.isFile()) {
                serveFile(context, indexFile);
                return;
            }
        }

        generateDirectoryListingHtmlFile(context, directory, path);
    }

    private void generateDirectoryListingHtml(io.vertx.ext.web.RoutingContext context, DocumentFile directory, String path) {
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>\n");
        html.append("<html><head><title>Directory listing for ").append(path).append("</title>");
        html.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">");
        html.append("<style>");
        html.append("body { font-family: Arial, sans-serif; margin: 20px; background: #121212; color: #ffffff; }");
        html.append("h1 { color: #bb86fc; }");
        html.append("a { color: #03dac6; text-decoration: none; }");
        html.append("a:hover { text-decoration: underline; }");
        html.append("ul { list-style-type: none; padding: 0; }");
        html.append("li { margin: 5px 0; padding: 10px; background: #1e1e1e; border-radius: 5px; }");
        html.append("</style></head><body>");
        html.append("<h1>Directory listing for ").append(path).append("</h1>");
        html.append("<ul>");
        // Add parent directory link if not root
        if (!path.equals("/")) {
            String parentPath = path.substring(0, path.lastIndexOf('/'));
            if (parentPath.isEmpty()) parentPath = "/";
            html.append("<li><a href=\"").append(parentPath).append("\">.. (parent directory)</a></li>");
        }

        // List directory contents
        DocumentFile[] files = directory.listFiles();
        for (DocumentFile file : files) {
            String fileName = file.getName();
            if (fileName != null) {
                String filePath = path.endsWith("/") ?
                        path + fileName : path + "/" + fileName;
                String icon = file.isDirectory() ? "&#128193;" : "&#128196;";
                html.append("<li><a href=\"").append(filePath).append("\">")
                        .append(icon).append(" ").append(fileName).append("</a></li>");
            }
        }

        html.append("</ul></body></html>");
        context.response()
                .putHeader("Content-Type", "text/html; charset=utf-8")
                .end(html.toString());
    }

    private void generateDirectoryListingHtmlFile(io.vertx.ext.web.RoutingContext context, java.io.File directory, String path) {
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>\n");
        html.append("<html><head><title>Directory listing for ").append(path).append("</title>");
        html.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">");
        html.append("<style>");
        html.append("body { font-family: Arial, sans-serif; margin: 20px; background: #121212; color: #ffffff; }");
        html.append("h1 { color: #bb86fc; }");
        html.append("a { color: #03dac6; text-decoration: none; }");
        html.append("a:hover { text-decoration: underline; }");
        html.append("ul { list-style-type: none; padding: 0; }");
        html.append("li { margin: 5px 0; padding: 10px; background: #1e1e1e; border-radius: 5px; }");
        html.append("</style></head><body>");
        html.append("<h1>Directory listing for ").append(path).append("</h1>");
        html.append("<ul>");
        // Add parent directory link if not root
        if (!path.equals("/")) {
            String parentPath = path.substring(0, path.lastIndexOf('/'));
            if (parentPath.isEmpty()) parentPath = "/";
            html.append("<li><a href=\"").append(parentPath).append("\">.. (parent directory)</a></li>");
        }

        // List directory contents
        java.io.File[] files = directory.listFiles();
        if (files != null) {
            for (java.io.File file : files) {
                String fileName = file.getName();
                String filePath = path.endsWith("/") ? path + fileName : path + "/" + fileName;
                String icon = file.isDirectory() ?
                        "&#128193;" : "&#128196;";
                html.append("<li><a href=\"").append(filePath).append("\">")
                        .append(icon).append(" ").append(fileName).append("</a></li>");
            }
        }

        html.append("</ul></body></html>");
        context.response()
                .putHeader("Content-Type", "text/html; charset=utf-8")
                .end(html.toString());
    }

    private void serveDocumentFile(io.vertx.ext.web.RoutingContext context, DocumentFile file) {
        try {
            if (this.context == null) {
                context.response().setStatusCode(500).end("Internal server error: context is null");
                return;
            }
            InputStream inputStream = this.context.getContentResolver().openInputStream(file.getUri());
            if (inputStream == null) {
                context.response().setStatusCode(500).end("Cannot read file");
                return;
            }

            String mimeType = file.getType();
            if (mimeType == null || mimeType.isEmpty()) {
                mimeType = getMimeType(file.getName());
            }

            context.response()
                    .putHeader("Content-Type", mimeType)
                    .putHeader("Content-Length", String.valueOf(file.length()));
            // Stream the file
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                context.response().write(io.vertx.core.buffer.Buffer.buffer(java.util.Arrays.copyOf(buffer, bytesRead)));
            }

            inputStream.close();
            context.response().end();
        } catch (IOException e) {
            Log.e(TAG, "Error serving document file", e);
            context.response().setStatusCode(500).end("Error reading file");
        }
    }

    private void serveFile(io.vertx.ext.web.RoutingContext context, java.io.File file) {
        try {
            String mimeType = getMimeType(file.getName());
            context.response()
                    .putHeader("Content-Type", mimeType)
                    .putHeader("Content-Length", String.valueOf(file.length()))
                    .sendFile(file.getAbsolutePath());
        } catch (Exception e) {
            Log.e(TAG, "Error serving file", e);
            context.response().setStatusCode(500).end("Error reading file");
        }
    }

    private String getMimeType(String fileName) {
        if (fileName == null) return "application/octet-stream";
        String extension = "";
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot > 0) {
            extension = fileName.substring(lastDot + 1).toLowerCase();
        }

        String mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
        return mimeType != null ? mimeType : "application/octet-stream";
    }

    private void handleFileUpload(io.vertx.ext.web.RoutingContext context) {
        // File upload implementation would go here
        context.response().setStatusCode(501).end("File upload not yet implemented");
    }

    private DocumentFile navigateToPath(DocumentFile root, String path) {
        if (path.equals("/") || path.isEmpty()) {
            return root;
        }

        // Remove leading slash and split path
        if (path.startsWith("/")) {
            path = path.substring(1);
        }

        if (path.isEmpty()) {
            return root;
        }

        String[] parts = path.split("/");
        DocumentFile current = root;
        for (String part : parts) {
            if (part.isEmpty()) continue;
            DocumentFile next = current.findFile(part);
            if (next == null) {
                return null;
            }
            current = next;
        }

        return current;
    }

    private String getClientIp(HttpServerRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }

        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }

        return request.connection().remoteAddress().host();
    }

    private boolean checkBasicAuth(HttpServerRequest request) {
        if (authUsername.isEmpty() && authPassword.isEmpty()) {
            return true;
            // No auth configured
        }

        String auth = request.getHeader("Authorization");
        if (auth == null || !auth.startsWith("Basic ")) {
            return false;
        }

        try {
            String credentials = auth.substring("Basic ".length());
            String decoded = new String(Base64.getDecoder().decode(credentials));
            String[] parts = decoded.split(":", 2);
            if (parts.length == 2) {
                return authUsername.equals(parts[0]) && authPassword.equals(parts[1]);
            }
        } catch (Exception e) {
            Log.w(TAG, "Error parsing basic auth", e);
        }

        return false;
    }

    public void cleanup() {
        stopServer(null);
        if (vertx != null) {
            vertx.close();
        }
    }

    // Getters for stats
    public boolean isHttpServerRunning() {
        return isHttpServerRunning.get();
    }

    public boolean isWebSocketServerRunning() {
        return isWebSocketServerRunning.get();
    }

    public void stopWebSocketServer() {
        isWebSocketServerRunning.set(false);
        logger.accept("WebSocket Server disabled");
    }

    public void startWebSocketServer() {
        isWebSocketServerRunning.set(true);
        logger.accept("WebSocket Server enabled");
    }

    // Getters and setters
    public void setPort(int port) {
        this.port = port;
    }

    public void setRootFolder(String rootFolder) {
        this.rootFolder = rootFolder;
    }

    public void setRedirectToIndex(boolean redirectToIndex) {
        this.redirectToIndex = redirectToIndex;
    }

    public void setRenderFolderContent(boolean renderFolderContent) {
        this.renderFolderContent = renderFolderContent;
    }

    public void setAllowUploads(boolean allowUploads) {
        this.allowUploads = allowUploads;
    }

    public void setRestrictNetwork(boolean restrictNetwork) {
        this.restrictNetwork = restrictNetwork;
    }

    public void setIpWhitelist(boolean ipWhitelist) {
        this.ipWhitelist = ipWhitelist;
    }

    public void setBasicAuth(boolean basicAuth) {
        this.basicAuth = basicAuth;
    }

    public void setTlsEnabled(boolean tlsEnabled) {
        this.tlsEnabled = tlsEnabled;
    }

    public void setAllowedIps(Set<String> allowedIps) {
        this.allowedIps = allowedIps;
    }

    public void setAuthUsername(String username) {
        this.authUsername = username;
    }

    public void setAuthPassword(String password) {
        this.authPassword = password;
    }

    public void setCustomHeaders(Map<String, String> customHeaders) {
        this.customHeaders = customHeaders;
    }
}