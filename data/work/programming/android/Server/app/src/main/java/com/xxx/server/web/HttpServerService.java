package com.xxx.server.web;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.xxx.server.MainActivity;
import com.xxx.server.R;

import java.util.Locale;
import java.util.function.Consumer;

public class HttpServerService extends Service {
    private static final String TAG = "HttpServerService";
    private static final String CHANNEL_ID = "HttpServerServiceChannel";
    private static final int NOTIFICATION_ID = 1;

    public static final String ACTION_LOG_BROADCAST = "com.xxx.server.LOG_BROADCAST";
    public static final String EXTRA_LOG_MESSAGE = "com.xxx.server.LOG_MESSAGE";
    public static final String ACTION_START_SERVER = "start_server";
    public static final String ACTION_STOP_SERVER = "stop_server";
    public static final String ACTION_CONNECTION_EVENT = "com.xxx.server.CONNECTION_EVENT";
    public static boolean isServerRunning = false;


    private final IBinder binder = new LocalBinder();
    private HttpServerManager serverManager;
    private final Handler handler = new Handler(Looper.getMainLooper());

    public class LocalBinder extends Binder {
        public HttpServerService getService() {
            return HttpServerService.this;
        }
    }

    private final BroadcastReceiver statsReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (ServerStatsManager.ACTION_STATS_UPDATE.equals(intent.getAction())) {
                double rx = intent.getDoubleExtra(ServerStatsManager.EXTRA_CURRENT_RX_RATE, 0);
                double tx = intent.getDoubleExtra(ServerStatsManager.EXTRA_CURRENT_TX_RATE, 0);
                updateNotification(String.format(Locale.getDefault(), "RX: %.1f KB/s, TX: %.1f KB/s", rx/1024, tx/1024));
            }
        }
    };

    private Consumer<String> currentLogger;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();

        currentLogger = this::broadcastLog;
        
        ServerStatsManager statsManager = new ServerStatsManager(this);
        InactivityManager inactivityManager = new InactivityManager(this);
        RestRouteManager restRouteManager = new RestRouteManager(this);

        serverManager = new HttpServerManager(this,
                msg -> {
                    if (currentLogger != null) currentLogger.accept(msg);
                },
                req -> {}, 
                statsManager,
                inactivityManager,
                restRouteManager
        );

        serverManager.setConnectionListener(v -> {
            Intent intent = new Intent(ACTION_CONNECTION_EVENT);
            LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
            
            android.content.SharedPreferences prefs = getSharedPreferences("HttpServerPrefs", MODE_PRIVATE);
            if (prefs.getBoolean("sound_enabled", false)) {
                playConnectionSound();
            }
        });

        LocalBroadcastManager.getInstance(this).registerReceiver(statsReceiver, new IntentFilter(ServerStatsManager.ACTION_STATS_UPDATE));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getAction() != null) {
            switch (intent.getAction()) {
                case "start_server":
                    startServer();
                    break;
                case "stop_server":
                    stopServer();
                    break;
                case "start_websocket_server":
                    startWebSocketServer();
                    break;
                case "send_chat_message":
                    String chatMsg = intent.getStringExtra("message");
                    if (serverManager != null && chatMsg != null) {
                        serverManager.broadcastChatMessage("SERVER", chatMsg);
                    }
                    break;
            }
        }
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void onDestroy() {
        stopServer();
        super.onDestroy();
    }

    public void setLogger(Consumer<String> logger) {
        this.currentLogger = logger;
    }

    public void setRequestNotifier(Consumer<Object> requestNotifier) {
        // You might want to update the notifier in serverManager if it's dynamic
    }

    public static final String ACTION_SERVER_STATE_CHANGED = "com.xxx.server.SERVER_STATE_CHANGED";
    public static final String EXTRA_SERVER_STATE = "server_state";

    public void startServer() {
        serverManager.startServer(v -> {
            isServerRunning = true;
            serverManager.getStatsManager().startTracking();
            startForeground(NOTIFICATION_ID, createNotification("Server is running"));
            broadcastState();
        });
    }

    public void stopServer() {
        if (serverManager != null) {
            serverManager.getStatsManager().stopTracking();
            serverManager.stopServer(v -> {
                isServerRunning = false;
                stopForeground(true);
                stopSelf();
                broadcastState();
            });
        }
    }

    private void broadcastState() {
        Intent intent = new Intent(ACTION_SERVER_STATE_CHANGED);
        intent.putExtra(EXTRA_SERVER_STATE, isServerRunning);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    public void startWebSocketServer() {
        if (serverManager != null) {
            serverManager.startWebSocketServer();
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "HTTP Server Service Channel",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(serviceChannel);
            }
        }
    }

    private Notification createNotification(String contentText) {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("HTTP Server")
                .setContentText(contentText)
                .setSmallIcon(R.drawable.ic_server_notification)
                .setContentIntent(pendingIntent)
                .build();
    }

    private void updateNotification(String stats) {
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(NOTIFICATION_ID, createNotification("Server is running - " + stats));
        }
    }

    private void broadcastLog(String message) {
        Intent intent = new Intent(ACTION_LOG_BROADCAST);
        intent.putExtra(EXTRA_LOG_MESSAGE, message);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    public void playConnectionSound() {
        try {
            android.media.MediaPlayer mp = android.media.MediaPlayer.create(this, com.xxx.server.R.raw.connect);
            if (mp != null) {
                mp.setOnCompletionListener(android.media.MediaPlayer::release);
                mp.start();
            }
        } catch (Exception ignored) {}
    }

    public HttpServerManager getServerManager() {
        return serverManager;
    }
}
