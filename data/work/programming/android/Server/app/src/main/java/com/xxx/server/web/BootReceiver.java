package com.xxx.server.web;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.util.Log;

public class BootReceiver extends BroadcastReceiver {
    private static final String TAG = "BootReceiver";
    private static final String PREFS_NAME = "HttpServerPrefs";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction()) ||
                Intent.ACTION_MY_PACKAGE_REPLACED.equals(intent.getAction())) {

            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            boolean autostart = prefs.getBoolean("autostart_on_boot", false);

            if (autostart) {
                Log.i(TAG, "Autostarting HTTP server on boot");

                // Start the server service
                Intent serviceIntent = new Intent(context, HttpServerService.class);
                serviceIntent.setAction(HttpServerService.ACTION_START_SERVER);
                context.startForegroundService(serviceIntent);
            }
        }
    }

    public static void setEnabled(Context context, boolean enabled) {
        ComponentName receiver = new ComponentName(context, BootReceiver.class);
        PackageManager pm = context.getPackageManager();

        int state = enabled ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                : PackageManager.COMPONENT_ENABLED_STATE_DISABLED;

        pm.setComponentEnabledSetting(receiver, state, PackageManager.DONT_KILL_APP);

        Log.i(TAG, "Boot receiver " + (enabled ? "enabled" : "disabled"));
    }
}