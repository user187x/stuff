package com.xxx.server.web;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
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

import java.util.function.Consumer;

public class HttpServerService extends Service {
    private static final String TAG = "HttpServerService";
    private static final String CHANNEL_ID = "HttpServerServiceChannel";
    private static final int NOTIFICATION_ID = 1;

    public static final String ACTION_LOG_BROADCAST = "com.xxx.server.LOG_BROADCAST";
    public static final String EXTRA_LOG_MESSAGE = "com.xxx.server.LOG_MESSAGE";


    private final IBinder binder = new LocalBinder();
    private HttpServerManager serverManager;
    private final Handler handler = new Handler(Looper.getMainLooper());

    public class LocalBinder extends Binder {
        public HttpServerService getService() {
            return HttpServerService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();

        Consumer<String> logger = this::broadcastLog;
        // The consumers will be updated later when the UI is ready
        serverManager = new HttpServerManager(this,
                logger,
                req -> {}, // Placeholder for requestNotifier
                new ServerStatsManager(this::updateNotification),
                new InactivityManager(this::stopServer),
                new RestRouteManager(this),
                this::broadcastLog
        );
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
        // You might want to update the logger in serverManager if it's dynamic
    }

    public void setRequestNotifier(Consumer<Object> requestNotifier) {
        // You might want to update the notifier in serverManager if it's dynamic
    }

    public void startServer() {
        serverManager.startServer(() -> {
            startForeground(NOTIFICATION_ID, createNotification("Server is running"));
        });
    }

    public void stopServer() {
        serverManager.stopServer(() -> {
            stopForeground(true);
            stopSelf();
        });
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

    public HttpServerManager getServerManager() {
        return serverManager;
    }
}