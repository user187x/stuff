package com.xxx.notify;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.GmailScopes;
import com.google.api.services.gmail.model.Message;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.mail.Session;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

public class NotificationMonitorService extends NotificationListenerService {

    private static final String CHANNEL_ID = "NotificationMonitorChannel";
    private static final int NOTIFICATION_ID = 1;

    private List<String> notificationBatch = new ArrayList<>();
    private SharedPreferences prefs;
    private Set<String> monitoredApps;
    private int batchSize, maxWaitTimeSeconds, interceptedNotificationCount = 0;
    private boolean isImmediateSend;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable emailRunnable;
    private final ExecutorService emailExecutor = Executors.newSingleThreadExecutor();

    @Override
    public void onCreate() {
        super.onCreate();
        prefs = getSharedPreferences(MainActivity.PREFS_NAME, MODE_PRIVATE);
        loadSettings();
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, createServiceNotification());

        emailRunnable = () -> {
            if (!notificationBatch.isEmpty()) {
                sendEmailWithGmailApi();
            }
        };
    }

    private void loadSettings() {
        monitoredApps = prefs.getStringSet("selected_apps", new HashSet<>());
        isImmediateSend = prefs.getBoolean("immediate_send", false);
        batchSize = prefs.getInt("batch_size", 10);
        maxWaitTimeSeconds = prefs.getInt("wait_time_seconds", 3600);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        loadSettings();

        // ✅ FIX: Handle incoming actions from notification buttons
        if (intent != null && intent.getAction() != null) {
            switch (intent.getAction()) {
                case "STOP_SERVICE":
                    prefs.edit().putBoolean("is_service_active", false).apply();
                    stopForeground(true);
                    stopSelf();
                    return START_NOT_STICKY;
                case "DISMISS_COUNT":
                    interceptedNotificationCount = 0;
                    break;
            }
        }

        prefs.edit().putBoolean("is_service_active", true).apply();
        updateNotification();
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (monitoredApps == null || !monitoredApps.contains(sbn.getPackageName()) || GoogleSignIn.getLastSignedInAccount(this) == null) {
            return;
        }

        interceptedNotificationCount++;
        updateNotification();

        Bundle extras = sbn.getNotification().extras;
        String title = extras.getString(Notification.EXTRA_TITLE, "No Title");
        CharSequence textChars = extras.getCharSequence(Notification.EXTRA_TEXT);
        String text = (textChars != null) ? textChars.toString() : "No Text";
        String formattedNotification = String.format("App: %s\nTitle: %s\nText: %s\n---\n", sbn.getPackageName(), title, text);

        notificationBatch.add(formattedNotification);

        if (isImmediateSend) {
            sendEmailWithGmailApi();
        } else {
            handler.removeCallbacks(emailRunnable);
            handler.postDelayed(emailRunnable, maxWaitTimeSeconds * 1000L);
            if (notificationBatch.size() >= batchSize) {
                sendEmailWithGmailApi();
                handler.removeCallbacks(emailRunnable);
            }
        }
    }

    private void sendEmailWithGmailApi() {
        if (notificationBatch.isEmpty()) return;

        String recipientEmail = prefs.getString("email", "");
        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(this);
        if (account == null) return;
        if (TextUtils.isEmpty(recipientEmail)) {
            recipientEmail = account.getEmail();
        }
        final String finalRecipient = recipientEmail;

        List<String> batchToSend = new ArrayList<>(notificationBatch);
        notificationBatch.clear();

        String body = String.join("\n", batchToSend);
        String subject = "Android Notification Batch";

        emailExecutor.execute(() -> {
            try {
                GoogleAccountCredential credential = GoogleAccountCredential.usingOAuth2(
                        this, Collections.singleton(GmailScopes.GMAIL_SEND));
                credential.setSelectedAccount(account.getAccount());

                Gmail gmailService = new Gmail.Builder(AndroidHttp.newCompatibleTransport(), new GsonFactory(), credential)
                        .setApplicationName(getString(R.string.app_name)).build();

                Properties props = new Properties();
                Session session = Session.getDefaultInstance(props, null);
                MimeMessage email = new MimeMessage(session);
                email.setFrom(new InternetAddress(account.getEmail()));
                email.addRecipient(javax.mail.Message.RecipientType.TO, new InternetAddress(finalRecipient));
                email.setSubject(subject);
                email.setText(body);

                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                email.writeTo(buffer);
                byte[] bytes = buffer.toByteArray();
                String encodedEmail = Base64.encodeToString(bytes, Base64.URL_SAFE);

                Message message = new Message();
                message.setRaw(encodedEmail);
                gmailService.users().messages().send("me", message).execute();
                Log.d("GmailAPI", "Email sent successfully via Gmail API to " + finalRecipient);
            } catch (Exception e) {
                Log.e("GmailAPI", "Failed to send email via API.", e);
            }
        });
    }

    private void updateNotification() {
        getSystemService(NotificationManager.class).notify(NOTIFICATION_ID, createServiceNotification());
    }

    private Notification createServiceNotification() {
        int flags = PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT;

        Intent settingsIntent = new Intent(this, MainActivity.class);
        PendingIntent settingsPI = PendingIntent.getActivity(this, 0, settingsIntent, PendingIntent.FLAG_IMMUTABLE);

        Intent stopIntent = new Intent(this, NotificationMonitorService.class).setAction("STOP_SERVICE");
        PendingIntent stopPI = PendingIntent.getService(this, 1, stopIntent, flags);

        // ✅ FIX: Create the intent and pending intent for the "Dismiss" action.
        Intent dismissIntent = new Intent(this, NotificationMonitorService.class).setAction("DISMISS_COUNT");
        PendingIntent dismissPI = PendingIntent.getService(this, 2, dismissIntent, flags);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Monitoring Active")
                .setContentText("Intercepted: " + interceptedNotificationCount + " notifications.")
                .setSmallIcon(R.drawable.notify)
                .setOngoing(true)
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                .addAction(android.R.drawable.ic_menu_manage, "Settings", settingsPI)
                .addAction(android.R.drawable.ic_media_pause, "Stop", stopPI)
                // ✅ FIX: Add the "Dismiss" action button to the notification.
                .addAction(android.R.drawable.ic_menu_delete, "Dismiss", dismissPI)
                .build();
    }

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Notification Monitor Service", NotificationManager.IMPORTANCE_DEFAULT);
        channel.setDescription("Channel for the notification monitoring service.");
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        prefs.edit().putBoolean("is_service_active", false).apply();
        handler.removeCallbacks(emailRunnable);
        emailExecutor.shutdown();
    }
}