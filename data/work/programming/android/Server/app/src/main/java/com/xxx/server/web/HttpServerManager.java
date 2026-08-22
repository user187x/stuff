package com.xxx.server.web;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;
import android.webkit.MimeTypeMap;

import androidx.documentfile.provider.DocumentFile;

import com.xxx.server.web.tls.TLSCertificateManager;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
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
    private java.util.function.Consumer<Void> connectionListener;
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

    public int getPort() {
        return port;
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
        
        // Body handler for uploads
        router.route().handler(BodyHandler.create());
        router.post("/upload").handler(this::handleFileUpload);

        restRouteManager.applyRoutesToRouter(router);
        
        // Handle all requests including root /
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
            long bytesSent = context.response().bytesWritten();
            statsManager.onRequest(0, bytesSent); // Log bytes sent
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
            if (onSuccess != null) onSuccess.handle(null);
            return;
        }

        try {
            setupRouter();
            HttpServerOptions options = new HttpServerOptions()
                    .setPort(port)
                    .setHost("0.0.0.0")
                    .setLogActivity(true);

            if (tlsEnabled) {
                TLSCertificateManager certManager = new TLSCertificateManager(context);
                if (certManager.certificateExists()) {
                    File keystoreFile = new File(context.getFilesDir(), "server_keystore.p12");
                    options.setSsl(true)
                            .setKeyCertOptions(new PfxOptions()
                                    .setPath(keystoreFile.getAbsolutePath())
                                    .setPassword(TLSCertificateManager.KEYSTORE_PASSWORD))
                            .addEnabledSecureTransportProtocol("TLSv1.3")
                            .addEnabledSecureTransportProtocol("TLSv1.2");
                }
            }

            httpServer = vertx.createHttpServer(options);
            httpServer.connectionHandler(conn -> {
                statsManager.onConnection();
                if (connectionListener != null) {
                    connectionListener.accept(null);
                }
            });
            httpServer.requestHandler(router);
            httpServer.webSocketHandler(this::handleWebSocketConnection);

            httpServer.listen()
                    .onSuccess(s -> {
                        isHttpServerRunning.set(true);
                        String addr = getServerAddress();
                        logger.accept("SYSTEM: Server started on " + addr + ":" + s.actualPort());
                        if (onSuccess != null) onSuccess.handle(null);
                    })
                    .onFailure(err -> {
                        logger.accept("ERROR: Failed to start: " + err.getMessage());
                        Log.e(TAG, "Failed to start server", err);
                    });
        } catch (Exception e) {
            logger.accept("CRITICAL: " + e.getMessage());
            Log.e(TAG, "Critical start error", e);
        }
    }

    public String getServerAddress() {
        try {
            java.util.Enumeration<java.net.NetworkInterface> en = java.net.NetworkInterface.getNetworkInterfaces();
            while (en.hasMoreElements()) {
                java.net.NetworkInterface intf = en.nextElement();
                if (intf.getName().contains("wlan") || intf.getName().contains("eth")) {
                    java.util.Enumeration<java.net.InetAddress> enumIpAddr = intf.getInetAddresses();
                    while (enumIpAddr.hasMoreElements()) {
                        java.net.InetAddress inetAddress = enumIpAddr.nextElement();
                        if (!inetAddress.isLoopbackAddress() && inetAddress instanceof java.net.Inet4Address) {
                            return inetAddress.getHostAddress();
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting IP", e);
        }
        return "127.0.0.1";
    }

    private final Map<ServerWebSocket, String> clientNames = new ConcurrentHashMap<>();

    private String generateRandomName() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder sb = new StringBuilder();
        java.util.Random rnd = new java.util.Random();
        while (sb.length() < 5) {
            int index = (int) (rnd.nextFloat() * chars.length());
            sb.append(chars.charAt(index));
        }
        return sb.toString();
    }

    private void handleWebSocketConnection(final ServerWebSocket ws) {
        if (!isWebSocketServerRunning.get()) {
            ws.close();
            return;
        }

        if (!ws.path().equals(WEBSOCKET_PATH)) {
            ws.close();
            return;
        }

        String clientName = "USER_" + generateRandomName();
        connectedClients.add(ws);
        clientNames.put(ws, clientName);
        
        logger.accept("CHAT: " + clientName + " connected from " + ws.remoteAddress());
        broadcastChatMessage("SYSTEM", clientName + " HAS JOINED THE SESSION.");

        ws.textMessageHandler(msg -> {
            String name = clientNames.get(ws);
            if (msg.startsWith("SIGNAL_TYPING:")) {
                boolean isTyping = msg.endsWith("START");
                broadcastChatEvent("TYPING", name, isTyping ? "START" : "STOP");
            } else {
                logger.accept("CHAT: [" + name + "] " + msg);
                broadcastChatMessage(name, msg);
            }
        });

        ws.closeHandler(v -> {
            String name = clientNames.remove(ws);
            connectedClients.remove(ws);
            if (name != null) {
                logger.accept("CHAT: " + name + " disconnected.");
                broadcastChatMessage("SYSTEM", name + " HAS LEFT THE SESSION.");
            }
        });

        ws.exceptionHandler(err -> logger.accept("CHAT ERROR: " + err.getMessage()));
    }

    public void broadcastChatMessage(String sender, String message) {
        String formatted = "[" + sender + "]: " + message;
        for (ServerWebSocket client : connectedClients) {
            client.writeTextMessage(formatted);
        }
        // Also broadcast to the Android UI if listening
        Intent intent = new Intent("com.xxx.server.CHAT_MESSAGE");
        intent.putExtra("sender", sender);
        intent.putExtra("message", message);
        androidx.localbroadcastmanager.content.LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
    }

    public void broadcastChatEvent(String type, String sender, String data) {
        String payload = "EVENT:" + type + ":" + sender + ":" + data;
        for (ServerWebSocket client : connectedClients) {
            client.writeTextMessage(payload);
        }
        Intent intent = new Intent("com.xxx.server.CHAT_EVENT");
        intent.putExtra("type", type);
        intent.putExtra("sender", sender);
        intent.putExtra("data", data);
        androidx.localbroadcastmanager.content.LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
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
        logger.accept("REQ: " + path + " (Root: " + (rootFolder.isEmpty() ? "NOT_SET" : "ACTIVE") + ")");
        
        if (rootFolder == null || rootFolder.isEmpty()) {
            context.response().setStatusCode(404).end("ERROR: Root directory not configured in app settings.");
            return;
        }

        try {
            // Handle root folder based on type (URI or file path)
            if (rootFolder.startsWith("content://")) {
                handleDocumentTreeRequest(context, path);
            } else {
                handleFileSystemRequest(context, path);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error handling request", e);
            logger.accept("ERROR: " + e.getMessage());
            context.response().setStatusCode(500).end("Internal server error: " + e.getMessage());
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

    private String getHtmlHeader(String title) {
        return "<!DOCTYPE html><html><head><title>" + title + "</title>" +
                "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">" +
                "<style>" +
                "body { font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; margin: 0; background: #0A0A0A; color: #E0E0E0; }" +
                ".container { max-width: 900px; margin: 0 auto; padding: 20px; padding-bottom: 400px; }" +
                "h1 { color: #00F3FF; border-bottom: 1px solid #333; padding-bottom: 10px; font-size: 24px; }" +
                ".path-nav { margin-bottom: 20px; color: #888; font-family: monospace; }" +
                "ul { list-style: none; padding: 0; border: 1px solid #222; border-radius: 8px; overflow: hidden; }" +
                "li { padding: 12px 15px; background: #121212; border-bottom: 1px solid #222; display: flex; align-items: center; transition: background 0.2s; }" +
                "li:last-child { border-bottom: none; }" +
                "li:hover { background: #1A1A1A; }" +
                "li a { color: #E0E0E0; text-decoration: none; flex-grow: 1; display: flex; align-items: center; }" +
                "li a:hover { color: #00F3FF; }" +
                ".icon { margin-right: 15px; font-size: 20px; min-width: 25px; text-align: center; }" +
                ".actions { display: flex; gap: 10px; }" +
                ".btn { padding: 6px 12px; border-radius: 4px; font-size: 13px; text-decoration: none; cursor: pointer; border: none; font-weight: bold; }" +
                ".btn-download { background: #00F3FF; color: #000; }" +
                ".btn-download:hover { background: #00D1FF; }" +
                ".upload-sect { margin-top: 30px; padding: 20px; background: #121212; border-radius: 8px; border: 1px dashed #333; }" +
                ".upload-sect h2 { margin-top: 0; font-size: 18px; color: #00FF41; }" +
                "input[type=file] { margin-bottom: 10px; display: block; background: #1A1A1A; color: #888; padding: 10px; width: 100%; box-sizing: border-box; border-radius: 4px; border: 1px solid #333; }" +
                "input[type=submit] { background: #00FF41; color: #000; padding: 10px 20px; border: none; border-radius: 4px; font-weight: bold; cursor: pointer; width: 100%; }" +
                "input[type=submit]:hover { background: #00E63A; }" +
                "#chat-panel { position: fixed; bottom: 0; left: 0; right: 0; background: #121212; border-top: 2px solid #00FF41; padding: 15px; z-index: 1000; }" +
                "#chat-log { height: 200px; overflow-y: auto; background: #050505; color: #00FF41; font-family: monospace; padding: 10px; border: 1px solid #222; margin-bottom: 10px; font-size: 13px; }" +
                ".chat-input-wrap { display: flex; gap: 10px; }" +
                "#chat-input { flex-grow: 1; background: #1A1A1A; border: 1px solid #333; color: white; padding: 10px; font-family: monospace; border-radius: 4px; }" +
                "#chat-send { background: #00FF41; border: none; padding: 10px 20px; cursor: pointer; font-weight: bold; border-radius: 4px; }" +
                "#typing-indicator { color: #555; font-family: monospace; font-size: 11px; height: 15px; margin-bottom: 5px; }" +
                "</style></head><body><div class=\"container\">";
    }

    private String getHtmlFooter() {
        if (!isWebSocketServerRunning.get()) {
            return "</div></body></html>";
        }

        return "</div>" +
                "<div id=\"chat-panel\">" +
                "<div style=\"color:#00FF41; font-family:monospace; margin-bottom:5px; font-size:12px; font-weight:bold;\">SECURE_CHAT_TERMINAL_v1.0</div>" +
                "<div id=\"typing-indicator\"></div>" +
                "<div id=\"chat-log\"></div>" +
                "<div class=\"chat-input-wrap\">" +
                "<input type=\"text\" id=\"chat-input\" placeholder=\"ENTER_MESSAGE...\" onkeypress=\"if(event.keyCode==13) sendChat()\">" +
                "<button id=\"chat-send\" onclick=\"sendChat()\">SEND</button>" +
                "</div>" +
                "</div>" +
                "<script>" +
                "const log = document.getElementById('chat-log');" +
                "const input = document.getElementById('chat-input');" +
                "const typing = document.getElementById('typing-indicator');" +
                "const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';" +
                "const wsUrl = protocol + '//' + window.location.host + '" + WEBSOCKET_PATH + "';" +
                "const socket = new WebSocket(wsUrl);" +
                "let typingTimeout;" +
                "let isTypingSent = false;" +
                "socket.onmessage = function(e) {" +
                "  if(e.data.startsWith('EVENT:TYPING:')) {" +
                "    const parts = e.data.split(':');" +
                "    const user = parts[2];" +
                "    const status = parts[3];" +
                "    if(status === 'START') {" +
                "      typing.textContent = '(' + user + ' is typing...)';" +
                "    } else {" +
                "      typing.textContent = '';" +
                "    }" +
                "    return;" +
                "  }" +
                "  const div = document.createElement('div');" +
                "  div.textContent = e.data;" +
                "  log.appendChild(div);" +
                "  log.scrollTop = log.scrollHeight;" +
                "};" +
                "input.addEventListener('input', () => {" +
                "  if(!isTypingSent) {" +
                "    socket.send('SIGNAL_TYPING:START');" +
                "    isTypingSent = true;" +
                "  }" +
                "  clearTimeout(typingTimeout);" +
                "  typingTimeout = setTimeout(() => {" +
                "    socket.send('SIGNAL_TYPING:STOP');" +
                "    isTypingSent = false;" +
                "  }, 2000);" +
                "});" +
                "socket.onopen = () => { appendLog('SYSTEM: CONNECTION_ESTABLISHED'); };" +
                "socket.onclose = () => { appendLog('SYSTEM: CONNECTION_LOST'); };" +
                "function appendLog(msg) {" +
                "  const div = document.createElement('div');" +
                "  div.textContent = msg;" +
                "  log.appendChild(div);" +
                "  log.scrollTop = log.scrollHeight;" +
                "}" +
                "function sendChat() {" +
                "  const msg = input.value.trim();" +
                "  if(msg && socket.readyState === WebSocket.OPEN) {" +
                "    socket.send(msg);" +
                "    input.value = '';" +
                "    socket.send('SIGNAL_TYPING:STOP');" +
                "    isTypingSent = false;" +
                "  }" +
                "}" +
                "</script></body></html>";
    }

    private void generateDirectoryListingHtml(io.vertx.ext.web.RoutingContext context, DocumentFile directory, String path) {
        StringBuilder html = new StringBuilder();
        html.append(getHtmlHeader("File Explorer - " + path));
        html.append("<h1>FILE_EXPLORER_v1.0</h1>");
        html.append("<div class=\"path-nav\">ROOT" + path.replace("/", " > ") + "</div>");
        
        html.append("<ul>");
        if (!path.equals("/")) {
            String parentPath = path.substring(0, path.lastIndexOf('/'));
            if (parentPath.isEmpty()) parentPath = "/";
            html.append("<li><a href=\"").append(parentPath).append("\"><span class=\"icon\">&#11013;</span> .. (Parent Directory)</a></li>");
        }

        DocumentFile[] files = directory.listFiles();
        if (files == null || files.length == 0) {
            html.append("<li style=\"color:#888;justify-content:center;\">NO_FILES_FOUND_IN_DIRECTORY</li>");
        } else {
            for (DocumentFile file : files) {
                String fileName = file.getName();
                if (fileName == null) continue;
                String filePath = path.endsWith("/") ? path + fileName : path + "/" + fileName;
                boolean isDir = file.isDirectory();
                String icon = isDir ? "&#128193;" : "&#128196;";
                
                html.append("<li>");
                html.append("<a href=\"").append(filePath).append("\"><span class=\"icon\">").append(icon).append("</span> ").append(fileName).append("</a>");
                if (!isDir) {
                    html.append("<div class=\"actions\"><a href=\"").append(filePath).append("\" download class=\"btn btn-download\">DOWNLOAD</a></div>");
                }
                html.append("</li>");
            }
        }
        html.append("</ul>");

        if (allowUploads) {
            html.append("<div class=\"upload-sect\">");
            html.append("<h2>UPLOAD_NEW_FILE</h2>");
            html.append("<form action=\"/upload?dir=").append(path).append("\" method=\"post\" enctype=\"multipart/form-data\">");
            html.append("<input type=\"file\" name=\"file\" required>");
            html.append("<input type=\"submit\" value=\"EXECUTE_UPLOAD\">");
            html.append("</form></div>");
        }

        html.append(getHtmlFooter());
        context.response().putHeader("Content-Type", "text/html; charset=utf-8").end(html.toString());
    }

    private void generateDirectoryListingHtmlFile(io.vertx.ext.web.RoutingContext context, java.io.File directory, String path) {
        StringBuilder html = new StringBuilder();
        html.append(getHtmlHeader("File Explorer - " + path));
        html.append("<h1>FILE_EXPLORER_v1.0</h1>");
        html.append("<div class=\"path-nav\">ROOT" + path.replace("/", " > ") + "</div>");
        
        html.append("<ul>");
        if (!path.equals("/")) {
            String parentPath = path.substring(0, path.lastIndexOf('/'));
            if (parentPath.isEmpty()) parentPath = "/";
            html.append("<li><a href=\"").append(parentPath).append("\"><span class=\"icon\">&#11013;</span> .. (Parent Directory)</a></li>");
        }

        java.io.File[] files = directory.listFiles();
        if (files == null || files.length == 0) {
            html.append("<li style=\"color:#888;justify-content:center;\">NO_FILES_FOUND_IN_DIRECTORY</li>");
        } else {
            for (java.io.File file : files) {
                String fileName = file.getName();
                String filePath = path.endsWith("/") ? path + fileName : path + "/" + fileName;
                boolean isDir = file.isDirectory();
                String icon = isDir ? "&#128193;" : "&#128196;";
                
                html.append("<li>");
                html.append("<a href=\"").append(filePath).append("\"><span class=\"icon\">").append(icon).append("</span> ").append(fileName).append("</a>");
                if (!isDir) {
                    html.append("<div class=\"actions\"><a href=\"").append(filePath).append("\" download class=\"btn btn-download\">DOWNLOAD</a></div>");
                }
                html.append("</li>");
            }
        }
        html.append("</ul>");

        if (allowUploads) {
            html.append("<div class=\"upload-sect\">");
            html.append("<h2>UPLOAD_NEW_FILE</h2>");
            html.append("<form action=\"/upload?dir=").append(path).append("\" method=\"post\" enctype=\"multipart/form-data\">");
            html.append("<input type=\"file\" name=\"file\" required>");
            html.append("<input type=\"submit\" value=\"EXECUTE_UPLOAD\">");
            html.append("</form></div>");
        }

        html.append(getHtmlFooter());
        context.response().putHeader("Content-Type", "text/html; charset=utf-8").end(html.toString());
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
            
            logger.accept("FILE: Accessing " + file.getName() + " [" + getClientIp(context.request()) + "]");

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
            logger.accept("FILE: Accessing " + file.getName() + " [" + getClientIp(context.request()) + "]");
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
        String dirParam = context.request().getParam("dir");
        if (dirParam == null) dirParam = "/";
        final String targetDir = dirParam;

        if (context.fileUploads().isEmpty()) {
            context.response().setStatusCode(400).end("No file uploaded");
            return;
        }

        io.vertx.ext.web.FileUpload fileUpload = context.fileUploads().iterator().next();
        String fileName = fileUpload.fileName();
        String tempFile = fileUpload.uploadedFileName();

        logger.accept("FILE: Uploading " + fileName + " [" + getClientIp(context.request()) + "]");

        try {
            if (rootFolder.startsWith("content://")) {
                saveToDocumentTree(context, targetDir, fileName, tempFile);
            } else {
                saveToFileSystem(context, targetDir, fileName, tempFile);
            }
        } catch (Exception e) {
            Log.e(TAG, "Upload failed", e);
            context.response().setStatusCode(500).end("Upload failed: " + e.getMessage());
        }
    }

    private void saveToDocumentTree(io.vertx.ext.web.RoutingContext context, String path, String fileName, String tempFile) throws IOException {
        DocumentFile rootDoc = DocumentFile.fromTreeUri(this.context, Uri.parse(rootFolder));
        DocumentFile targetFolder = navigateToPath(rootDoc, path);
        
        if (targetFolder == null || !targetFolder.isDirectory()) {
            context.response().setStatusCode(500).end("Target directory invalid");
            return;
        }

        DocumentFile newFile = targetFolder.createFile(getMimeType(fileName), fileName);
        if (newFile == null) {
            context.response().setStatusCode(500).end("Could not create file in SAF");
            return;
        }

        try (InputStream is = new java.io.FileInputStream(tempFile);
             java.io.OutputStream os = this.context.getContentResolver().openOutputStream(newFile.getUri())) {
            byte[] buffer = new byte[8192];
            int len;
            while ((len = is.read(buffer)) != -1) {
                os.write(buffer, 0, len);
            }
        }
        
        // Redirect back to the directory listing
        context.response().setStatusCode(302).putHeader("Location", path).end();
    }

    private void saveToFileSystem(io.vertx.ext.web.RoutingContext context, String path, String fileName, String tempFile) throws IOException {
        java.io.File rootDir = new java.io.File(rootFolder);
        java.io.File targetDir = new java.io.File(rootDir, path);
        java.io.File destFile = new java.io.File(targetDir, fileName);

        java.nio.file.Files.move(new java.io.File(tempFile).toPath(), destFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        
        context.response().setStatusCode(302).putHeader("Location", path).end();
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

    public ServerStatsManager getStatsManager() {
        return statsManager;
    }

    public void setConnectionListener(java.util.function.Consumer<Void> listener) {
        this.connectionListener = listener;
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