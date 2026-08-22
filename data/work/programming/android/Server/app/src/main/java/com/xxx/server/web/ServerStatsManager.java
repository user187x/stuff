package com.xxx.server.web;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.util.concurrent.atomic.AtomicLong;

public class ServerStatsManager {
    public static final String ACTION_STATS_UPDATE = "com.xxx.server.STATS_UPDATE";
    public static final String EXTRA_UPTIME = "uptime";
    public static final String EXTRA_TOTAL_RX = "total_rx";
    public static final String EXTRA_TOTAL_TX = "total_tx";
    public static final String EXTRA_TOTAL_REQUESTS = "total_requests";
    public static final String EXTRA_TOTAL_CONNECTIONS = "total_connections";
    public static final String EXTRA_CURRENT_RX_RATE = "current_rx_rate";
    public static final String EXTRA_CURRENT_TX_RATE = "current_tx_rate";
    public static final String EXTRA_WEBSOCKET_RX_RATE = "websocket_rx_rate";
    public static final String EXTRA_WEBSOCKET_TX_RATE = "websocket_tx_rate";

    private final Context context;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private long serverStartTime = 0;
    private final AtomicLong totalBytesReceived = new AtomicLong(0);
    private final AtomicLong totalBytesSent = new AtomicLong(0);
    private final AtomicLong totalRequests = new AtomicLong(0);
    private final AtomicLong totalConnections = new AtomicLong(0);
    private final AtomicLong websocketBytesReceived = new AtomicLong(0);
    private final AtomicLong websocketBytesSent = new AtomicLong(0);

    private double currentRxRate = 0.0;
    private double currentTxRate = 0.0;
    private double websocketRxRate = 0.0;
    private double websocketTxRate = 0.0;

    private long lastCheckTime = 0;
    private long lastBytesReceived = 0;
    private long lastBytesSent = 0;
    private long lastWsBytesReceived = 0;
    private long lastWsBytesSent = 0;

    private final Runnable statsUpdateRunnable = new Runnable() {
        @Override
        public void run() {
            calculateRates();
            broadcastStats();
            handler.postDelayed(this, 1000); // Update every second
        }
    };

    private void calculateRates() {
        long now = System.currentTimeMillis();
        if (lastCheckTime > 0) {
            long timeDiff = now - lastCheckTime;
            if (timeDiff > 0) {
                long rxDiff = totalBytesReceived.get() - lastBytesReceived;
                long txDiff = totalBytesSent.get() - lastBytesSent;
                long wsRxDiff = websocketBytesReceived.get() - lastWsBytesReceived;
                long wsTxDiff = websocketBytesSent.get() - lastWsBytesSent;

                currentRxRate = (rxDiff * 1000.0) / timeDiff;
                currentTxRate = (txDiff * 1000.0) / timeDiff;
                websocketRxRate = (wsRxDiff * 1000.0) / timeDiff;
                websocketTxRate = (wsTxDiff * 1000.0) / timeDiff;
            }
        }
        lastCheckTime = now;
        lastBytesReceived = totalBytesReceived.get();
        lastBytesSent = totalBytesSent.get();
        lastWsBytesReceived = websocketBytesReceived.get();
        lastWsBytesSent = websocketBytesSent.get();
    }

    public ServerStatsManager(Context context) {
        this.context = context;
    }

    public void startTracking() {
        serverStartTime = System.currentTimeMillis();
        handler.post(statsUpdateRunnable);
    }

    public void stopTracking() {
        handler.removeCallbacks(statsUpdateRunnable);
        resetStats();
    }

    public void onRequest(long bytesReceived, long bytesSent) {
        totalRequests.incrementAndGet();
        totalBytesReceived.addAndGet(bytesReceived);
        totalBytesSent.addAndGet(bytesSent);
    }

    public void onConnection() {
        totalConnections.incrementAndGet();
    }

    public void onWebSocketData(long bytesReceived, long bytesSent) {
        websocketBytesReceived.addAndGet(bytesReceived);
        websocketBytesSent.addAndGet(bytesSent);
    }

    public void updateTrafficRates(double rxRate, double txRate, double wsRxRate, double wsTxRate) {
        this.currentRxRate = rxRate;
        this.currentTxRate = txRate;
        this.websocketRxRate = wsRxRate;
        this.websocketTxRate = wsTxRate;
    }

    private void broadcastStats() {
        long uptime = serverStartTime > 0 ? System.currentTimeMillis() - serverStartTime : 0;

        Intent intent = new Intent(ACTION_STATS_UPDATE);
        intent.putExtra(EXTRA_UPTIME, uptime);
        intent.putExtra(EXTRA_TOTAL_RX, totalBytesReceived.get());
        intent.putExtra(EXTRA_TOTAL_TX, totalBytesSent.get());
        intent.putExtra(EXTRA_TOTAL_REQUESTS, totalRequests.get());
        intent.putExtra(EXTRA_TOTAL_CONNECTIONS, totalConnections.get());
        intent.putExtra(EXTRA_CURRENT_RX_RATE, currentRxRate);
        intent.putExtra(EXTRA_CURRENT_TX_RATE, currentTxRate);
        intent.putExtra(EXTRA_WEBSOCKET_RX_RATE, websocketRxRate);
        intent.putExtra(EXTRA_WEBSOCKET_TX_RATE, websocketTxRate);

        LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
    }

    private void resetStats() {
        serverStartTime = 0;
        totalBytesReceived.set(0);
        totalBytesSent.set(0);
        totalRequests.set(0);
        totalConnections.set(0);
        websocketBytesReceived.set(0);
        websocketBytesSent.set(0);
        currentRxRate = 0.0;
        currentTxRate = 0.0;
        websocketRxRate = 0.0;
        websocketTxRate = 0.0;
    }
}