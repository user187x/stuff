package com.xxx.server.web;

import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServerResponse;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class CameraStreamManager {
  private static final CameraStreamManager INSTANCE = new CameraStreamManager();
  private final Set<HttpServerResponse> clients = Collections.newSetFromMap(new ConcurrentHashMap<>());
  private static final String BOUNDARY = "mjpegframe";

  private Vertx vertx;
  // Last JPEG frame, resent by the single keep-alive timer so idle clients don't time out.
  private volatile byte[] lastFrame;
  // Id of the ONE keep-alive timer for the whole app (-1 = none scheduled).
  private long keepAliveTimerId = -1;

  public static CameraStreamManager getInstance() {
    return INSTANCE;
  }

  public void setVertx(Vertx vertx) {
    // If a previous Vert.x instance had a keep-alive timer, cancel it before swapping.
    if (this.vertx != null && keepAliveTimerId != -1) {
      try {
        this.vertx.cancelTimer(keepAliveTimerId);
      } catch (Exception ignored) {
      }
      keepAliveTimerId = -1;
    }

    this.vertx = vertx;

    // Schedule exactly ONE periodic timer for the lifetime of this Vert.x instance.
    if (vertx != null) {
      keepAliveTimerId = vertx.setPeriodic(2000, id -> {
        byte[] frame = lastFrame;
        if (frame != null && !clients.isEmpty()) {
          writeFrameToClients(frame);
        }
      });
    }
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
    // Abort if Vert.x isn't initialized or there's nothing to send.
    if (jpegBytes == null || vertx == null) return;

    // Remember the latest frame so the keep-alive timer can resend it when idle.
    lastFrame = jpegBytes;

    // Nothing to do if no one is watching.
    if (clients.isEmpty()) return;

    writeFrameToClients(jpegBytes);
    // NOTE: Do NOT schedule a new timer here. The camera analyzer already calls
    // pushFrame() continuously, and a single keep-alive timer is set up in setVertx().
  }

  private void writeFrameToClients(byte[] jpegBytes) {
    String header = "\r\n--" + BOUNDARY + "\r\n" +
        "Content-Type: image/jpeg\r\n" +
        "Content-Length: " + jpegBytes.length + "\r\n\r\n";
    Buffer buffer = Buffer.buffer(header).appendBytes(jpegBytes);

    // Dispatch the write onto the Vert.x event loop.
    vertx.runOnContext(v -> {
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
    });
  }
}