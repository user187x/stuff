package com.xxx.server.web;

import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServerResponse;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Streams MJPEG frames to connected HTTP clients.
 *
 * IMPORTANT: this server runs on a SINGLE Vert.x event-loop thread, shared with
 * every other request (file browsing, chat, and accepting NEW connections). So
 * this class must never stall or flood that thread, or other devices won't be
 * able to connect. Two safeguards below make that true:
 *   1) There is NO recursive/self-scheduling timer (the old code created a new
 *      periodic timer on every frame, which exploded and pegged the event loop).
 *   2) Every write respects back-pressure: if a client's socket write queue is
 *      full (slow or half-dead client), we DROP that frame for that client
 *      instead of queueing it. Dropping a frame is invisible on an MJPEG feed;
 *      a saturated event loop is what kills external access.
 */
public class CameraStreamManager {

  private static final CameraStreamManager INSTANCE = new CameraStreamManager();
  private static final String BOUNDARY = "mjpegframe";

  private final Set<HttpServerResponse> clients =
      Collections.newSetFromMap(new ConcurrentHashMap<>());

  private Vertx vertx;
  // Last frame, resent by the single keep-alive timer so idle feeds don't freeze/time out.
  private volatile byte[] lastFrame;
  // Id of the ONE keep-alive timer for this Vert.x instance (-1 = none scheduled).
  private long keepAliveTimerId = -1;

  public static CameraStreamManager getInstance() {
    return INSTANCE;
  }

  public void setVertx(Vertx vertx) {
    // Cancel a keep-alive tied to a previous Vert.x instance (e.g. server restart).
    if (this.vertx != null && keepAliveTimerId != -1) {
      try {
        this.vertx.cancelTimer(keepAliveTimerId);
      } catch (Exception ignored) {
      }
      keepAliveTimerId = -1;
    }

    this.vertx = vertx;

    // Exactly ONE periodic timer for the whole app. Resends the last frame every
    // 2s so a paused camera doesn't leave the browser stream hanging.
    if (vertx != null) {
      keepAliveTimerId = vertx.setPeriodic(2000, id -> {
        byte[] frame = lastFrame;
        if (frame != null && !clients.isEmpty()) {
          broadcast(frame);
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

    // Cap how much we let a single slow client buffer before back-pressure kicks in.
    response.setWriteQueueMaxSize(1024 * 1024); // ~1 MB

    clients.add(response);
    response.closeHandler(v -> clients.remove(response));
    response.exceptionHandler(v -> clients.remove(response));
  }

  public void pushFrame(byte[] jpegBytes) {
    if (jpegBytes == null || vertx == null) return;

    // Remember the newest frame for the keep-alive timer.
    lastFrame = jpegBytes;

    if (clients.isEmpty()) return;
    broadcast(jpegBytes);
    // NOTE: no timer is scheduled here. The camera analyzer already calls this
    // continuously, and the single keep-alive timer is set up in setVertx().
  }

  private void broadcast(byte[] jpegBytes) {
    final String header = "\r\n--" + BOUNDARY + "\r\n" +
        "Content-Type: image/jpeg\r\n" +
        "Content-Length: " + jpegBytes.length + "\r\n\r\n";
    final Buffer buffer = Buffer.buffer(header).appendBytes(jpegBytes);

    // Do all writes on the event loop so we never touch a response off-thread.
    vertx.runOnContext(v -> {
      for (HttpServerResponse response : clients) {
        try {
          if (response.closed() || response.ended()) {
            clients.remove(response);
            continue;
          }
          // Back-pressure: if this client's socket is backed up, drop THIS frame
          // for THIS client rather than queueing it and stalling the event loop.
          if (response.writeQueueFull()) {
            continue;
          }
          response.write(buffer);
        } catch (Exception e) {
          clients.remove(response);
        }
      }
    });
  }
}