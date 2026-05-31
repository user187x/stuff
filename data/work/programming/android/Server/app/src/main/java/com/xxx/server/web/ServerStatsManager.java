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

    private final Runnable statsUpdateRunnable = new Runnable() {
        @Override
        public void run() {
            broadcastStats();
            handler.postDelayed(this, 1000); // Update every second
        }
    };

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