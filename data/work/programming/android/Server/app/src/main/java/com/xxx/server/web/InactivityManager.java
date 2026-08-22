package com.xxx.server.web;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

public class InactivityManager {

  private static final String TAG = "InactivityManager";
  private static final String PREFS_KEY = "auto_shutdown_minutes";
  private static final String PREFS_ENABLED_KEY = "auto_shutdown_enabled";

  private final Context context;
  private final SharedPreferences preferences;
  private final Handler handler;
  private Runnable shutdownRunnable;
  private long lastActivityTime;

  public InactivityManager(Context context) {
    this.context = context;
    this.preferences = context.getSharedPreferences("HttpServerPrefs", Context.MODE_PRIVATE);
    this.handler = new Handler(Looper.getMainLooper());
  }

  public void startMonitoring() {
    if (!isEnabled()) {
      return;
    }

    updateLastActivity();
    scheduleShutdownCheck();
    Log.i(TAG, "Inactivity monitoring started");
  }

  public void stopMonitoring() {
    if (shutdownRunnable != null) {
      handler.removeCallbacks(shutdownRunnable);
      shutdownRunnable = null;
    }
    Log.i(TAG, "Inactivity monitoring stopped");
  }

  public void updateLastActivity() {
    lastActivityTime = System.currentTimeMillis();
  }

  public boolean isEnabled() {
    return preferences.getBoolean(PREFS_ENABLED_KEY, false);
  }

  public void setEnabled(boolean enabled) {
    preferences.edit().putBoolean(PREFS_ENABLED_KEY, enabled).apply();
    if (enabled) {
      startMonitoring();
    } else {
      stopMonitoring();
    }
  }

  public int getInactivityMinutes() {
    return preferences.getInt(PREFS_KEY, 30); // Default 30 minutes
  }

  public void setInactivityMinutes(int minutes) {
    preferences.edit().putInt(PREFS_KEY, minutes).apply();
    if (isEnabled()) {
      stopMonitoring();
      startMonitoring();
    }
  }

  private void scheduleShutdownCheck() {
    shutdownRunnable = new Runnable() {
      @Override
      public void run() {
        long currentTime = System.currentTimeMillis();
        long inactivityThreshold = getInactivityMinutes() * 60 * 1000L;

        if (currentTime - lastActivityTime >= inactivityThreshold) {
          Log.i(TAG, "Server shutting down due to inactivity");
          shutdownServer();
        } else {
          // Check again in 1 minute
          handler.postDelayed(this, 60000);
        }
      }
    };

    // Check every minute
    handler.postDelayed(shutdownRunnable, 60000);
  }

  private void shutdownServer() {
    Intent intent = new Intent(context, HttpServerService.class);
    intent.setAction(HttpServerService.ACTION_STOP_SERVER);
    context.startService(intent);
  }
}