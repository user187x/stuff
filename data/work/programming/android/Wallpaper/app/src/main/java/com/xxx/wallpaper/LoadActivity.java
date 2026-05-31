package com.xxx.wallpaper;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

/**
 * LoadActivity is the entry point of the application.
 * Its sole purpose is to provide a button for the user to select a GIF file.
 */
public class LoadActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_load);

        Button selectGifButton = findViewById(R.id.button_select_gif);

        // Register a callback for the Activity Result Launcher to handle the file picker result.
        final ActivityResultLauncher<Intent> gifPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK) {
                        Intent data = result.getData();
                        if (data != null && data.getData() != null) {
                            Uri gifUri = data.getData();

                            // *** FIX: Take persistent read permission for the selected URI. ***
                            // This is the crucial step that allows the background WallpaperService
                            // to access the file long after the app has closed.
                            try {
                                final int takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION;
                                getContentResolver().takePersistableUriPermission(gifUri, takeFlags);
                            } catch (SecurityException e) {
                                // This can happen if the user selects a file from a provider that
                                // doesn't support persistable permissions.
                                e.printStackTrace();
                                Toast.makeText(this, "Could not get permanent access to the file.", Toast.LENGTH_LONG).show();
                                return;
                            }


                            // If a GIF is successfully selected, launch MainActivity to preview it.
                            Intent intent = new Intent(LoadActivity.this, MainActivity.class);
                            intent.setData(gifUri);
                            // Also add the read flag to the intent for MainActivity
                            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                            startActivity(intent);
                        } else {
                            Toast.makeText(this, "Failed to get file.", Toast.LENGTH_SHORT).show();
                        }
                    }
                });

        // Set a click listener on the button to launch the file picker.
        selectGifButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("image/gif"); // We only want to see GIF files.
                // Add the persistable permission flag to the intent that launches the picker
                intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                gifPickerLauncher.launch(intent);
            }
        });
    }
}
