package com.xxx.trim;

import android.Manifest;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.audiofx.Visualizer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.Group;
import androidx.core.content.ContextCompat;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;

import com.bumptech.glide.Glide; // <-- Import Glide
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {

    private MaterialCardView cardLoad, cardTrim;
    private ImageButton btnPlayPause;
    private TextView tvStartTime, tvEndTime, tvCurrentTime, tvTrimDuration, tvTrackName;
    private WaveformView waveformView;
    private VisualizerView visualizerView;
    private Group trimmerGroup;
    private ImageView loadingIndicator;

    private ExoPlayer player;
    private Uri selectedAudioUri;
    private Handler progressHandler;
    private Runnable progressRunnable;

    private boolean isScrubbing = false;
    private boolean wasPlayingBeforeScrub = false;
    private boolean isWaveformLoaded = false;
    private boolean trimControlsAdjusted = false;

    private Visualizer visualizer;
    private float latestRms = 0f;
    private int audioSessionId = 0;

    private long playbackPosition = 0;
    private boolean playWhenReady = true;

    private final ActivityResultLauncher<String> audioPickerLauncher = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> {
                if (uri != null) {
                    if (loadingIndicator != null) {
                        // Use Glide to load and start the GIF
                        loadingIndicator.setVisibility(View.VISIBLE);
                        Glide.with(this).asGif().load(R.drawable.loading).into(loadingIndicator);
                    }

                    playbackPosition = 0;
                    playWhenReady = true;
                    selectedAudioUri = uri;
                    releasePlayer();
                    initializePlayer();
                }
            });

    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    initVisualizer();
                } else {
                    Toast.makeText(this, "Audio visualizer requires Record Audio permission", Toast.LENGTH_SHORT).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        trimmerGroup = findViewById(R.id.trimmer_group);
        cardLoad = findViewById(R.id.cardLoad);
        cardTrim = findViewById(R.id.cardTrim);
        waveformView = findViewById(R.id.waveformView);
        visualizerView = findViewById(R.id.visualizerView);
        tvStartTime = findViewById(R.id.tvStartTime);
        tvEndTime = findViewById(R.id.tvEndTime);
        tvTrimDuration = findViewById(R.id.tvTrimDuration);
        tvTrackName = findViewById(R.id.tvTrackName);
        tvCurrentTime = findViewById(R.id.tvCurrentTime);
        btnPlayPause = findViewById(R.id.btnPlayPause);
        loadingIndicator = findViewById(R.id.loadingIndicator);

        cardLoad.setOnClickListener(v -> audioPickerLauncher.launch("audio/mpeg"));
        btnPlayPause.setOnClickListener(v -> togglePlayPause());
        cardTrim.setOnClickListener(v -> showSaveConfirmationDialog());
    }

    @Override
    protected void onStart() {
        super.onStart();
        initializePlayer();
    }

    @Override
    protected void onStop() {
        super.onStop();
        releasePlayer();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    private void initializePlayer() {
        if (player == null) {
            player = new ExoPlayer.Builder(this).build();
            player.addListener(playerListener);
        }

        if (selectedAudioUri != null) {
            setupTrimmer();
        }
    }

    private void releasePlayer() {
        if (player != null) {
            playbackPosition = player.getCurrentPosition();
            playWhenReady = player.getPlayWhenReady();
            releaseVisualizer();
            stopProgressUpdater();
            player.release();
            player = null;
        }
    }


    private void setupTrimmer() {
        isWaveformLoaded = false;
        trimControlsAdjusted = false;

        String trackName = getTrackNameFromUri(selectedAudioUri);
        tvTrackName.setText(trackName);
        tvTrackName.setSelected(true);

        if (player == null) return;

        MediaItem mediaItem = MediaItem.fromUri(selectedAudioUri);
        player.setMediaItem(mediaItem);
        player.setPlayWhenReady(playWhenReady);
        player.seekTo(playbackPosition);
        player.prepare();

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO);
        }

        waveformView.setListener(new WaveformView.WaveformListener() {
            @Override
            public void onWaveformUpdated() {
                if (loadingIndicator != null) {
                    // Use Glide to clear the view and then hide it
                    Glide.with(MainActivity.this).clear(loadingIndicator);
                    loadingIndicator.setVisibility(View.GONE);
                }
            }

            @Override
            public void onTrimUpdated(long startMs, long endMs) {
                trimControlsAdjusted = true;
                tvStartTime.setText(formatMilliseconds(startMs));
                tvEndTime.setText(formatMilliseconds(endMs));
                updateTrimDurationText(startMs, endMs);
            }

            @Override
            public void onScrubStarted() {
                if (isScrubbing) return;
                isScrubbing = true;
                wasPlayingBeforeScrub = player.isPlaying();
                if (wasPlayingBeforeScrub) {
                    player.pause();
                }
            }

            @Override
            public void onScrubbing(long positionMs) {
                long constrainedPosition = Math.max(positionMs, waveformView.getStartTrimMs());
                waveformView.setPlayheadPosition(constrainedPosition);
                updateProgressText(constrainedPosition);
            }

            @Override
            public void onScrubEnded(long positionMs) {
                if (!isScrubbing) return;
                isScrubbing = false;
                long constrainedPosition = Math.max(positionMs, waveformView.getStartTrimMs());
                waveformView.setPlayheadPosition(constrainedPosition);
                updateProgressText(constrainedPosition);
                player.seekTo(constrainedPosition);
                if (wasPlayingBeforeScrub) {
                    player.play();
                }
            }

            @Override
            public void onScrub(long positionMs) {}
        });
    }

    @OptIn(markerClass = UnstableApi.class)
    private final Player.Listener playerListener = new Player.Listener() {
        @Override
        public void onAudioSessionIdChanged(int newAudioSessionId) {
            if (audioSessionId != newAudioSessionId) {
                audioSessionId = newAudioSessionId;
                if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                    initVisualizer();
                }
            }
        }

        @Override
        public void onPlaybackStateChanged(int playbackState) {
            if (playbackState == Player.STATE_READY && !isWaveformLoaded) {
                isWaveformLoaded = true;
                long duration = player.getDuration();
                waveformView.loadAudio(selectedAudioUri, duration);
                tvCurrentTime.setText(String.format("%s / %s", formatMilliseconds(0), formatMilliseconds(duration)));
                updateTrimDurationText(0, duration);
            } else if (playbackState == Player.STATE_ENDED) {
                player.seekTo(waveformView.getStartTrimMs());
                player.pause();
            }
        }

        @Override
        public void onIsPlayingChanged(boolean isPlaying) {
            if (!isScrubbing) {
                updatePlayPauseButton(isPlaying);
            }
            if (visualizer != null) {
                visualizer.setEnabled(isPlaying);
            }
            if (isPlaying) {
                startProgressUpdater();
            } else {
                stopProgressUpdater();
            }
        }
    };

    private void showSaveConfirmationDialog() {
        String message = trimControlsAdjusted ?
                "Proceed to save the trimmed audio file?" :
                "No changes were made. This will save a copy of the original file. Continue?";

        new MaterialAlertDialogBuilder(this)
                .setTitle("Confirm Save")
                .setMessage(message)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", (dialog, which) -> performTrimAndSave())
                .show();
    }

    private String getTrackNameFromUri(Uri uri) {
        String trackName = null;
        if (ContentResolver.SCHEME_CONTENT.equals(uri.getScheme())) {
            try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (nameIndex != -1) {
                        trackName = cursor.getString(nameIndex);
                    }
                }
            } catch (Exception e) {}
        }

        if (trackName == null) {
            trackName = uri.getLastPathSegment();
            if (trackName != null) {
                int dotIndex = trackName.lastIndexOf('.');
                if (dotIndex > 0) {
                    trackName = trackName.substring(0, dotIndex);
                }
            }
        }

        return trackName != null ? trackName : "Unknown Track";
    }


    private void updateTrimDurationText(long startMs, long endMs) {
        long duration = endMs - startMs;
        tvTrimDuration.setText(String.format("(%s)", formatMilliseconds(duration)));
    }

    private void initVisualizer() {
        if (player == null || audioSessionId == 0) {
            return;
        }
        releaseVisualizer();
        try {
            visualizer = new Visualizer(audioSessionId);
            visualizer.setCaptureSize(Visualizer.getCaptureSizeRange()[1]);
            visualizer.setDataCaptureListener(new Visualizer.OnDataCaptureListener() {
                @Override
                public void onWaveFormDataCapture(Visualizer visualizer, byte[] waveform, int samplingRate) {
                    double sum = 0;
                    for (byte b : waveform) {
                        sum += b * b;
                    }
                    double rms = Math.sqrt(sum / waveform.length);
                    latestRms = (float) (rms / 128.0);
                }

                @Override
                public void onFftDataCapture(Visualizer visualizer, byte[] fft, int samplingRate) {
                    if (visualizerView != null) {
                        visualizerView.updateVisualizer(fft, latestRms);
                    }
                }
            }, Visualizer.getMaxCaptureRate(), true, true);
            visualizer.setEnabled(player.isPlaying());
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Error initializing visualizer", Toast.LENGTH_SHORT).show();
        }
    }

    private void releaseVisualizer() {
        if (visualizer != null) {
            visualizer.release();
            visualizer = null;
        }
    }

    private void togglePlayPause() {
        if (player == null) return;
        if (player.isPlaying()) {
            player.pause();
        } else {
            if (player.getCurrentPosition() < waveformView.getStartTrimMs()) {
                player.seekTo(waveformView.getStartTrimMs());
            }
            player.play();
        }
    }

    private void updatePlayPauseButton(boolean isPlaying) {
        if (isPlaying) {
            btnPlayPause.setImageResource(R.drawable.ic_pause);
            btnPlayPause.setContentDescription(getString(R.string.pause));
        } else {
            btnPlayPause.setImageResource(R.drawable.ic_play);
            btnPlayPause.setContentDescription(getString(R.string.play));
        }
    }

    private void startProgressUpdater() {
        if (progressHandler == null) {
            progressHandler = new Handler(Looper.getMainLooper());
        }
        progressRunnable = new Runnable() {
            @Override
            public void run() {
                if (player != null && player.isPlaying() && !isScrubbing) {
                    updateProgress();
                    if (player.getCurrentPosition() >= waveformView.getEndTrimMs()) {
                        player.pause();
                        player.seekTo(waveformView.getStartTrimMs());
                    }
                    progressHandler.postDelayed(this, 100);
                }
            }
        };
        progressHandler.post(progressRunnable);
    }

    private void stopProgressUpdater() {
        if (progressHandler != null) {
            progressHandler.removeCallbacks(progressRunnable);
        }
    }

    private void updateProgress() {
        if (player != null) {
            long currentPosition = player.getCurrentPosition();
            waveformView.setPlayheadPosition(currentPosition);
            updateProgressText(currentPosition);
        }
    }

    private void updateProgressText(long positionMs) {
        if (player != null) {
            long duration = player.getDuration();
            tvCurrentTime.setText(String.format("%s / %s", formatMilliseconds(positionMs), formatMilliseconds(duration)));
        }
    }

    private String formatMilliseconds(long millis) {
        if (millis < 0) millis = 0;
        return String.format(Locale.getDefault(), "%02d:%02d.%03d",
                TimeUnit.MILLISECONDS.toMinutes(millis),
                TimeUnit.MILLISECONDS.toSeconds(millis) % 60,
                millis % 1000);
    }

    private void performTrimAndSave() {
        if (selectedAudioUri == null) {
            Toast.makeText(this, "Please load a file first.", Toast.LENGTH_SHORT).show();
            return;
        }

        cardTrim.setEnabled(false);
        Toast.makeText(this, "Trimming...", Toast.LENGTH_SHORT).show();

        long startUs = waveformView.getStartTrimMs() * 1000;
        long endUs = waveformView.getEndTrimMs() * 1000;

        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String fileName = "TRIMMED_" + timeStamp + ".mp3";

        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
        values.put(MediaStore.MediaColumns.MIME_TYPE, "audio/mpeg");
        values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_MUSIC + File.separator + "AudioTrimmer");

        Uri outputUri = getContentResolver().insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values);

        if (outputUri == null) {
            Toast.makeText(this, "Failed to create output file.", Toast.LENGTH_SHORT).show();
            cardTrim.setEnabled(true);
            return;
        }

        MediaExtractor extractor = new MediaExtractor();
        try (OutputStream outputStream = getContentResolver().openOutputStream(outputUri)) {
            if (outputStream == null) {
                throw new IOException("Failed to open output stream for " + outputUri);
            }

            extractor.setDataSource(getApplicationContext(), selectedAudioUri, null);

            int trackIndex = -1;
            for (int i = 0; i < extractor.getTrackCount(); i++) {
                MediaFormat format = extractor.getTrackFormat(i);
                String mime = format.getString(MediaFormat.KEY_MIME);
                if (mime != null && mime.startsWith("audio/")) {
                    trackIndex = i;
                    break;
                }
            }

            if (trackIndex == -1) {
                throw new IOException("No audio track found in the file.");
            }

            extractor.selectTrack(trackIndex);
            extractor.seekTo(startUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC);

            ByteBuffer buffer = ByteBuffer.allocate(1024 * 1024);

            while (true) {
                int sampleSize = extractor.readSampleData(buffer, 0);
                long sampleTime = extractor.getSampleTime();

                if (sampleSize < 0 || sampleTime > endUs) {
                    break;
                }

                if (sampleTime >= startUs) {
                    byte[] data = new byte[sampleSize];
                    buffer.get(data, 0, sampleSize);
                    outputStream.write(data);
                }

                if (!extractor.advance()) {
                    break;
                }
            }

            Toast.makeText(this, "Trimmed MP3 saved successfully!", Toast.LENGTH_LONG).show();

        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Error trimming audio: " + e.getMessage(), Toast.LENGTH_LONG).show();
        } finally {
            extractor.release();
            cardTrim.setEnabled(true);
        }
    }
}