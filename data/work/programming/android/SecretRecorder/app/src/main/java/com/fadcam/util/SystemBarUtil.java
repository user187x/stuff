package com.fadcam.util;

import android.os.Build;
import android.view.View;
import android.view.Window;

/**
 * Centralized system-bar styling helpers.
 *
 * Window#setStatusBarColor / setNavigationBarColor and the legacy
 * View#setSystemUiVisibility / SYSTEM_UI_FLAG_* APIs are deprecated (API 30
 * for the flags, API 35 for the color setters). This utility keeps the
 * behavior on older devices (where they still apply) while compiling cleanly
 * on current SDKs.
 */
public final class SystemBarUtil {

    private SystemBarUtil() {}

    /**
     * Sets the navigation bar color, honoring the API 35+ deprecation: on
     * API 35+ the call is a no-op under edge-to-edge, so it is skipped.
     */
    @SuppressWarnings("deprecation") // Window#setNavigationBarColor is deprecated on API 35+
    public static void setNavigationBarColor(Window window, int color) {
        if (window == null) return;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            window.setNavigationBarColor(color);
        }
    }

    /**
     * Gets the navigation bar color (guarded for API 35+ where the getter is
     * deprecated). Falls back to black on API 35+.
     */
    @SuppressWarnings("deprecation")
    public static int getNavigationBarColor(Window window) {
        if (window == null) return android.graphics.Color.BLACK;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            return android.graphics.Color.BLACK;
        }
        return window.getNavigationBarColor();
    }

    /**
     * Toggles light (dark icons) vs dark (light icons) navigation bar
     * appearance. Uses WindowInsetsController (API 30+) and falls back to the
     * legacy SYSTEM_UI_FLAG on older devices.
     */
    @SuppressWarnings("deprecation") // legacy SYSTEM_UI_FLAG fallback for < API 30
    public static void setNavigationBarIconsLight(Window window, boolean light) {
        View decor = window != null ? window.getDecorView() : null;
        if (decor == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            android.view.WindowInsetsController c = window.getInsetsController();
            if (c != null) {
                int flag = light ? android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS : 0;
                c.setSystemBarsAppearance(flag, android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            @SuppressWarnings("deprecation")
            int vis = decor.getSystemUiVisibility();
            @SuppressWarnings("deprecation")
            int result = light
                    ? vis | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
                    : vis & ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            decor.setSystemUiVisibility(result);
        }
    }

    /**
     * Toggles light (dark icons) vs dark (light icons) status bar appearance.
     * Uses WindowInsetsController (API 30+) and falls back to the legacy
     * SYSTEM_UI_FLAG on older devices.
     */
    @SuppressWarnings("deprecation") // legacy SYSTEM_UI_FLAG fallback for < API 30
    public static void setStatusBarIconsLight(Window window, boolean light) {
        View decor = window != null ? window.getDecorView() : null;
        if (decor == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            android.view.WindowInsetsController c = window.getInsetsController();
            if (c != null) {
                int flag = light ? android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS : 0;
                c.setSystemBarsAppearance(flag, android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS);
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            @SuppressWarnings("deprecation")
            int vis = decor.getSystemUiVisibility();
            @SuppressWarnings("deprecation")
            int result = light
                    ? vis | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                    : vis & ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            decor.setSystemUiVisibility(result);
        }
    }

    /**
     * Sets the status bar color, skipping the API 35+ no-op path.
     */
    @SuppressWarnings("deprecation") // Window#setStatusBarColor is deprecated on API 35+
    public static void setStatusBarColor(Window window, int color) {
        if (window == null) return;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            window.setStatusBarColor(color);
        }
    }

    /**
     * Enters immersive fullscreen (hides status + navigation bars).
     * Uses WindowInsetsController (API 30+) and falls back to the legacy
     * SYSTEM_UI_FLAG combo on older devices.
     */
    @SuppressWarnings("deprecation") // legacy SYSTEM_UI_FLAG fallback for < API 30
    public static void hideSystemBars(View view) {
        if (view == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            android.view.WindowInsetsController c = view.getWindowInsetsController();
            if (c != null) {
                c.hide(android.view.WindowInsets.Type.systemBars());
                c.setSystemBarsBehavior(
                        android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
            return;
        }
        @SuppressWarnings("deprecation")
        int flags = View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN;
        view.setSystemUiVisibility(flags);
    }

    /**
     * Restores the system bars after {@link #hideSystemBars}.
     */
    @SuppressWarnings("deprecation") // legacy SYSTEM_UI_FLAG fallback for < API 30
    public static void showSystemBars(View view) {
        if (view == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            android.view.WindowInsetsController c = view.getWindowInsetsController();
            if (c != null) {
                c.show(android.view.WindowInsets.Type.systemBars());
            }
            return;
        }
        @SuppressWarnings("deprecation")
        int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN;
        view.setSystemUiVisibility(flags);
    }
}
