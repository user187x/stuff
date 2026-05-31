package com.xxx.wallpaper;

import android.app.WallpaperManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.RadioGroup;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * MainActivity displays the selected GIF, allows the user to choose a scaling option,
 * and provides a button to set the live wallpaper.
 */
public class MainActivity extends AppCompatActivity {

    public static final String TAG = "WallpaperAppMain";
    private Uri gifUri;
    private ImageView gifPreview;
    private RadioGroup scalingOptions;
    private Button setWallpaperButton;

    // Constants for SharedPreferences
    public static final String SHARED_PREFS_NAME = "wallpaper_prefs";
    public static final String KEY_GIF_PATH = "gif_path";
    public static final String KEY_SCALING_MODE = "scaling_mode";
    private static final String WALLPAPER_GIF_NAME = "wallpaper.gif";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        gifPreview = findViewById(R.id.gif_preview);
        scalingOptions = findViewById(R.id.scaling_options);
        setWallpaperButton = findViewById(R.id.button_set_wallpaper);

        gifUri = getIntent().getData();

        if (gifUri != null) {
            Glide.with(this)
                    .asGif()
                    .load(gifUri)
                    .diskCacheStrategy(DiskCacheStrategy.DATA)
                    .into(gifPreview);
        } else {
            Toast.makeText(this, "No GIF selected.", Toast.LENGTH_LONG).show();
            finish();
        }

        setWallpaperButton.setOnClickListener(v -> setLiveWallpaper());

        analyzeScreenDimensions();
    }

    private void setLiveWallpaper() {
        Log.d(TAG, "setLiveWallpaper called.");
        String internalPath = copyGifToInternalStorage(gifUri);
        if (internalPath == null) {
            Toast.makeText(this, "Failed to copy GIF for wallpaper.", Toast.LENGTH_LONG).show();
            return;
        }

        SharedPreferences prefs = getSharedPreferences(SHARED_PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(KEY_GIF_PATH, internalPath);
        editor.putString(KEY_SCALING_MODE, getSelectedScalingMode());
        Log.d(TAG, "Committing to SharedPreferences: Path=" + internalPath + ", Mode=" + getSelectedScalingMode());
        editor.commit();

        try {
            Intent intent = new Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER);
            intent.putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                    new ComponentName(this, GifWallpaperService.class));
            startActivity(intent);
            Toast.makeText(this, "Select 'My GIF Wallpaper' and apply!", Toast.LENGTH_LONG).show();
        } catch (android.content.ActivityNotFoundException e) {
            Toast.makeText(this, "Your device does not support Live Wallpapers.", Toast.LENGTH_LONG).show();
        }
    }

    private String copyGifToInternalStorage(Uri sourceUri) {
        if (sourceUri == null) {
            Log.e(TAG, "Source URI is null, cannot copy.");
            return null;
        }

        File destinationFile = new File(getFilesDir(), WALLPAPER_GIF_NAME);
        Log.d(TAG, "Attempting to copy to: " + destinationFile.getAbsolutePath());

        try (InputStream in = getContentResolver().openInputStream(sourceUri);
             OutputStream out = new FileOutputStream(destinationFile)) {

            if (in == null) {
                Log.e(TAG, "Could not open input stream from URI.");
                return null;
            }

            byte[] buf = new byte[4096];
            int len;
            while ((len = in.read(buf)) > 0) {
                out.write(buf, 0, len);
            }
            Log.d(TAG, "File copy successful.");
            return destinationFile.getAbsolutePath();
        } catch (IOException e) {
            Log.e(TAG, "Failed to copy file", e);
            return null;
        }
    }

    private String getSelectedScalingMode() {
        int selectedId = scalingOptions.getCheckedRadioButtonId();
        if (selectedId == R.id.option_span) {
            return "SPAN";
        } else if (selectedId == R.id.option_stretch) {
            return "STRETCH";
        } else {
            return "FULL";
        }
    }

    private void analyzeScreenDimensions() {
        DisplayMetrics displayMetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
    }
}
