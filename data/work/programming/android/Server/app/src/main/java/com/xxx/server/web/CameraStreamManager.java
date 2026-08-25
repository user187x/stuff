package com.xxx.server.web;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServerResponse;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class CameraStreamManager {
  private static final CameraStreamManager INSTANCE = new CameraStreamManager();
  private final Set<HttpServerResponse> clients = Collections.newSetFromMap(new ConcurrentHashMap<>());
  private static final String BOUNDARY = "mjpegframe";

  public static CameraStreamManager getInstance() {
    return INSTANCE;
  }

  public void addClient(HttpServerResponse response) {
    response.setChunked(true)
        .putHeader("Content-Type", "multipart/x-mixed-replace; boundary=--" + BOUNDARY)
        .putHeader("Cache-Control", "no-cache, no-store, must-revalidate")
        .putHeader("Connection", "close")
        .putHeader("Pragma", "no-cache");

    clients.add(response);
    response.closeHandler(v -> clients.remove(response));
    response.exceptionHandler(v -> clients.remove(response));
  }

  public void pushFrame(byte[] jpegBytes) {
    if (clients.isEmpty() || jpegBytes == null) return;

    String header = "\r\n--" + BOUNDARY + "\r\n" +
        "Content-Type: image/jpeg\r\n" +
        "Content-Length: " + jpegBytes.length + "\r\n\r\n";

    Buffer buffer = Buffer.buffer(header).appendBytes(jpegBytes);

    for (HttpServerResponse response : clients) {
      try {
        if (!response.closed()) {
          response.write(buffer);
        } else {
          clients.remove(response);
        }
      } catch (Exception e) {
        clients.remove(response);
      }
    }
  }
}