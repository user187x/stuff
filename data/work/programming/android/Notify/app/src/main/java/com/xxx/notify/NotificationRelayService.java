package com.xxx.notify;

package com.example.notificationrelay;

import android.app.Notification;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.provider.Telephony;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.mail.Authenticator;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

public class NotificationRelayService extends NotificationListenerService {

    private static final String TAG = "NotificationRelaySvc";
    private final ExecutorService emailExecutor = Executors.newSingleThreadExecutor();

    // --- IMPORTANT ---
    // For this simple example, sender credentials are hardcoded.
    // In a real-world application, you should NEVER do this.
    // Use secure methods like OAuth2 to get user consent and access tokens.
    private static final String SENDER_EMAIL = "YOUR_SENDER_EMAIL@gmail.com";
    private static final String SENDER_PASSWORD = "YOUR_APP_PASSWORD"; // Use an App Password for Gmail

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        super.onNotificationPosted(sbn);

        String packageName = sbn.getPackageName();
        String defaultSmsPackage = Telephony.Sms.getDefaultSmsPackage(this);

        // List of packages to monitor
        boolean isTargetApp = "com.whatsapp".equals(packageName) ||
                "jp.naver.line.android".equals(packageName) ||
                (defaultSmsPackage != null && defaultSmsPackage.equals(packageName));

        if (isTargetApp) {
            Notification notification = sbn.getNotification();
            Bundle extras = notification.extras;
            String title = extras.getString(Notification.EXTRA_TITLE, "No Title");
            CharSequence textChars = extras.getCharSequence(Notification.EXTRA_TEXT);
            String text = (textChars != null) ? textChars.toString() : "No Text";

            Log.d(TAG, "Notification caught from: " + packageName);
            Log.d(TAG, "Title: " + title);
            Log.d(TAG, "Text: " + text);

            sendEmail(packageName, title, text);
        }
    }

    private void sendEmail(String appName, String title, String body) {
        SharedPreferences sharedPreferences = getSharedPreferences(MainActivity.SHARED_PREFS, MODE_PRIVATE);
        String recipientEmail = sharedPreferences.getString(MainActivity.EMAIL_KEY, null);

        if (recipientEmail == null || recipientEmail.isEmpty()) {
            Log.e(TAG, "Recipient email not set. Cannot send email.");
            return;
        }

        emailExecutor.submit(() -> {
            // Configure mail properties
            Properties props = new Properties();
            props.put("mail.smtp.host", "smtp.gmail.com");
            props.put("mail.smtp.socketFactory.port", "465");
            props.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
            props.put("mail.smtp.auth", "true");
            props.put("mail.smtp.port", "465");

            Session session = Session.getInstance(props, new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(SENDER_EMAIL, SENDER_PASSWORD);
                }
            });

            try {
                MimeMessage mimeMessage = new MimeMessage(session);
                mimeMessage.setFrom(new InternetAddress(SENDER_EMAIL));
                mimeMessage.addRecipient(Message.RecipientType.TO, new InternetAddress(recipientEmail));
                mimeMessage.setSubject("New Notification from " + getAppName(appName));
                mimeMessage.setText("From: " + title + "\n\nMessage:\n" + body);
                Transport.send(mimeMessage);
                Log.d(TAG, "Email sent successfully to " + recipientEmail);
            } catch (MessagingException e) {
                Log.e(TAG, "Failed to send email", e);
            }
        });
    }

    private String getAppName(String packageName) {
        if ("com.whatsapp".equals(packageName)) return "WhatsApp";
        if ("jp.naver.line.android".equals(packageName)) return "Line";
        return "Text Message"; // Default for SMS
    }
}
