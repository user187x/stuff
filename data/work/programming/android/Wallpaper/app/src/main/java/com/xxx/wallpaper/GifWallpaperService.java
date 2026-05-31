package com.xxx.wallpaper;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.service.wallpaper.WallpaperService;
import android.util.Log;
import android.view.SurfaceHolder;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.gif.GifDrawable;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;

import java.io.File;

/**
 * GifWallpaperService is the core component that renders the GIF as a live wallpaper.
 * This version uses the definitive, robust solution for loading resources in a background
 * service by holding a strong reference to a CustomTarget to prevent garbage collection.
 */
public class GifWallpaperService extends WallpaperService {
    public static final String TAG = "WallpaperAppService";

    @Override
    public Engine onCreateEngine() {
        return new GifWallpaperEngine();
    }

    private class GifWallpaperEngine extends Engine implements Drawable.Callback {

        private final Handler handler = new Handler(Looper.getMainLooper());
        private GifDrawable gifDrawable;
        private boolean isVisible;

        // *** THE DEFINITIVE FIX: Part 1 ***
        // Hold a strong reference to the CustomTarget to prevent it from being garbage collected
        // while Glide is loading the resource in the background.
        private CustomTarget<GifDrawable> glideTarget;

        @Override
        public void onCreate(SurfaceHolder surfaceHolder) {
            super.onCreate(surfaceHolder);
            Log.d(TAG, "Engine onCreate");
            loadGif();
        }

        private void loadGif() {
            SharedPreferences prefs = getApplicationContext().getSharedPreferences(
                    MainActivity.SHARED_PREFS_NAME, Context.MODE_PRIVATE);
            String pathString = prefs.getString(MainActivity.KEY_GIF_PATH, null);

            Log.d(TAG, "Attempting to load GIF from path: " + pathString);
            if (pathString == null) {
                Log.e(TAG, "GIF path is null. Cannot load wallpaper.");
                return;
            }

            File gifFile = new File(pathString);
            if (!gifFile.exists()) {
                Log.e(TAG, "GIF file does not exist at path: " + pathString);
                return;
            }

            // Instantiate the CustomTarget, which we hold a reference to.
            glideTarget = new CustomTarget<GifDrawable>() {
                @Override
                public void onResourceReady(@NonNull GifDrawable resource, @Nullable Transition<? super GifDrawable> transition) {
                    Log.d(TAG, "Glide onResourceReady: GIF has been loaded successfully.");
                    gifDrawable = resource;
                    gifDrawable.setLoopCount(GifDrawable.LOOP_FOREVER);
                    gifDrawable.setCallback(GifWallpaperEngine.this);
                    if (isVisible) {
                        gifDrawable.start();
                        draw();
                    }
                }

                @Override
                public void onLoadCleared(@Nullable Drawable placeholder) {
                    Log.d(TAG, "Glide onLoadCleared: Cleaning up resources.");
                    if (gifDrawable != null) {
                        gifDrawable.stop();
                        gifDrawable.setCallback(null);
                        gifDrawable = null;
                    }
                }

                @Override
                public void onLoadFailed(@Nullable Drawable errorDrawable) {
                    Log.e(TAG, "Glide onLoadFailed: Failed to load GIF.");
                }
            };

            // Instruct Glide to load the GIF into our persistent target.
            Glide.with(getApplicationContext())
                    .asGif()
                    .load(gifFile)
                    .into(glideTarget);
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);
            this.isVisible = visible;
            Log.d(TAG, "onVisibilityChanged: " + visible);
            if (gifDrawable != null) {
                if (visible) {
                    gifDrawable.start();
                    draw();
                } else {
                    gifDrawable.stop();
                }
            }
        }

        @Override
        public void onSurfaceDestroyed(SurfaceHolder holder) {
            super.onSurfaceDestroyed(holder);
            Log.d(TAG, "onSurfaceDestroyed");
            this.isVisible = false;

            // *** THE DEFINITIVE FIX: Part 2 ***
            // It's crucial to clear the target when the service is destroyed
            // to release memory and prevent leaks.
            if (glideTarget != null) {
                Glide.with(getApplicationContext()).clear(glideTarget);
            }
            if (gifDrawable != null) {
                gifDrawable.stop();
                gifDrawable = null;
            }
        }

        private void draw() {
            if (isVisible && gifDrawable != null && getSurfaceHolder().getSurface().isValid()) {
                Canvas canvas = null;
                try {
                    canvas = getSurfaceHolder().lockCanvas();
                    if (canvas != null) {
                        canvas.drawColor(0, android.graphics.PorterDuff.Mode.CLEAR);
                        Matrix matrix = calculateMatrix(canvas.getWidth(), canvas.getHeight(), gifDrawable.getIntrinsicWidth(), gifDrawable.getIntrinsicHeight());
                        canvas.save();
                        canvas.concat(matrix);
                        gifDrawable.draw(canvas);
                        canvas.restore();
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error while drawing canvas", e);
                } finally {
                    if (canvas != null) {
                        getSurfaceHolder().unlockCanvasAndPost(canvas);
                    }
                }
            }
        }

        private Matrix calculateMatrix(int screenWidth, int screenHeight, int gifWidth, int gifHeight) {
            Matrix matrix = new Matrix();
            if (gifWidth <= 0 || gifHeight <= 0) return matrix;

            SharedPreferences prefs = getApplicationContext().getSharedPreferences(
                    MainActivity.SHARED_PREFS_NAME, Context.MODE_PRIVATE);
            String scaleMode = prefs.getString(MainActivity.KEY_SCALING_MODE, "FULL");
            float scaleX = (float) screenWidth / gifWidth;
            float scaleY = (float) screenHeight / gifHeight;

            switch (scaleMode) {
                case "STRETCH": matrix.setScale(scaleX, scaleY); break;
                case "SPAN":
                    float scaleSpan = Math.max(scaleX, scaleY);
                    matrix.setScale(scaleSpan, scaleSpan);
                    matrix.postTranslate((screenWidth - gifWidth * scaleSpan) / 2f, (screenHeight - gifHeight * scaleSpan) / 2f);
                    break;
                case "FULL":
                default:
                    float scaleFull = Math.min(scaleX, scaleY);
                    matrix.setScale(scaleFull, scaleFull);
                    matrix.postTranslate((screenWidth - gifWidth * scaleFull) / 2f, (screenHeight - gifHeight * scaleFull) / 2f);
                    break;
            }
            return matrix;
        }

        @Override
        public void invalidateDrawable(@NonNull Drawable who) {
            draw();
        }

        @Override
        public void scheduleDrawable(@NonNull Drawable who, @NonNull Runnable what, long when) {
            handler.postAtTime(what, who, when);
        }

        @Override
        public void unscheduleDrawable(@NonNull Drawable who, @NonNull Runnable what) {
            handler.removeCallbacks(what, who);
        }
    }
}
