package com.xxx.trim;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class WaveformView extends View {

    public interface WaveformListener {
        void onWaveformUpdated();
        void onTrimUpdated(long startMs, long endMs);
        void onScrubStarted();
        void onScrubbing(long positionMs);
        void onScrubEnded(long positionMs);
        void onScrub(long positionMs);
    }

    private Paint wavePaint;
    private Paint waveBackgroundPaint;
    private Paint selectionPaint;
    private Paint handlePaint;
    private Paint playheadPaint;
    private Paint tickPaint;
    private Paint tickTextPaint;
    private Paint popupTextPaint;
    private Paint popupBackgroundPaint;

    private String popupText = "";
    private float popupX = -1f;
    private boolean showPopup = false;

    private List<Float> amplitudes;
    private RectF selectionRect;
    private float leftHandleX;
    private float rightHandleX;
    private final float handleWidth;
    private final float handleTouchWidth;

    private int activeHandle = 0;
    private float playheadPosition = -1f;

    private long audioDurationMs = 0;
    private long startTrimMs = 0;
    private long endTrimMs = 0;

    private WaveformListener listener;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());

    public WaveformView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        handleWidth = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 12, getResources().getDisplayMetrics());
        handleTouchWidth = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 24, getResources().getDisplayMetrics());
        init();
    }

    private void init() {
        setBackgroundColor(Color.TRANSPARENT);

        amplitudes = new ArrayList<>();
        selectionRect = new RectF();

        wavePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        wavePaint.setColor(Color.WHITE);
        wavePaint.setStrokeWidth(2f);

        waveBackgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        waveBackgroundPaint.setColor(Color.argb(100, 200, 200, 200));
        waveBackgroundPaint.setStrokeWidth(2f);

        selectionPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        selectionPaint.setColor(Color.argb(40, 2, 136, 209));

        handlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        handlePaint.setColor(Color.rgb(2, 136, 209));

        playheadPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        playheadPaint.setColor(Color.RED);
        playheadPaint.setStrokeWidth(4f);

        tickPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        tickPaint.setColor(Color.argb(120, 0, 0, 0)); // Semi-transparent black
        tickPaint.setStrokeWidth(2f);

        tickTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        tickTextPaint.setColor(Color.argb(150, 0, 0, 0)); // Semi-transparent black
        tickTextPaint.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 10, getResources().getDisplayMetrics()));
        tickTextPaint.setTextAlign(Paint.Align.CENTER);

        popupTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        popupTextPaint.setColor(Color.WHITE);
        popupTextPaint.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 14, getResources().getDisplayMetrics()));

        popupBackgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        popupBackgroundPaint.setColor(Color.parseColor("#80000000"));
    }

    public void setListener(WaveformListener listener) {
        this.listener = listener;
    }

    public void loadAudio(Uri audioUri, long duration) {
        this.audioDurationMs = duration;

        executor.execute(() -> {
            ArrayList<Float> localAmplitudes = new ArrayList<>();
            try (InputStream inputStream = getContext().getContentResolver().openInputStream(audioUri)) {
                // --- ARTIFICIAL DELAY FOR TESTING ---
                // This makes the loading indicator visible for at least 2 seconds.
                // You can remove or adjust this for a real application.
                //Thread.sleep(2000);
                // --- END OF DELAY ---

                if (inputStream == null) return;

                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    baos.write(buffer, 0, bytesRead);
                }
                byte[] allBytes = baos.toByteArray();
                int fileLength = allBytes.length;
                if (fileLength <= 0) return;

                int desiredSamples = 1024;
                int bytesPerSample = Math.max(1, fileLength / desiredSamples);

                for (int i = 0; i < fileLength; i += bytesPerSample) {
                    float maxAmplitude = 0f;
                    for (int j = 0; j < bytesPerSample && (i + j) < fileLength; j++) {
                        float amplitude = Math.abs((float) (allBytes[i + j] & 0xFF) / 128.0f - 1.0f);
                        if (amplitude > maxAmplitude) {
                            maxAmplitude = amplitude;
                        }
                    }
                    localAmplitudes.add(maxAmplitude);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            handler.post(() -> {
                this.amplitudes = localAmplitudes;
                reset();
                if (listener != null) {
                    listener.onWaveformUpdated();
                }
                invalidate();
            });
        });
    }


    public void reset() {
        startTrimMs = 0;
        endTrimMs = audioDurationMs;
        playheadPosition = -1f;
        updateHandlePositions();
        if (listener != null) {
            listener.onTrimUpdated(startTrimMs, endTrimMs);
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        updateHandlePositions();
    }

    private void updateHandlePositions() {
        if (audioDurationMs > 0) {
            leftHandleX = timeToPosition(startTrimMs);
            rightHandleX = timeToPosition(endTrimMs);
        } else {
            leftHandleX = 0;
            rightHandleX = getWidth();
        }
        invalidate();
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        int width = getWidth();
        int height = getHeight();
        float waveformCenterY = height / 2f;

        // 1. Draw full waveform (both active and inactive parts)
        if (!amplitudes.isEmpty()) {
            float barWidth = (float) width / amplitudes.size();
            for (int i = 0; i < amplitudes.size(); i++) {
                float x = i * barWidth;
                float barHeight = amplitudes.get(i) * height;
                Paint currentPaint = (x >= leftHandleX && x <= rightHandleX) ? wavePaint : waveBackgroundPaint;
                canvas.drawLine(x, waveformCenterY - barHeight / 2, x, waveformCenterY + barHeight / 2, currentPaint);
            }
        }

        // 2. Draw time ticks on top of the waveform
        drawTicks(canvas, width, height);

        // 3. Draw the semi-transparent selection rectangle over the waveform and ticks
        selectionRect.set(leftHandleX, 0, rightHandleX, height);
        canvas.drawRect(selectionRect, selectionPaint);

        // 4. Draw handles and playhead on top of everything
        RectF leftHandleRect = new RectF(leftHandleX - handleWidth / 2, 0, leftHandleX + handleWidth / 2, height);
        canvas.drawRoundRect(leftHandleRect, 15, 15, handlePaint);
        RectF rightHandleRect = new RectF(rightHandleX - handleWidth / 2, 0, rightHandleX + handleWidth / 2, height);
        canvas.drawRoundRect(rightHandleRect, 15, 15, handlePaint);

        if (playheadPosition >= 0) {
            canvas.drawLine(playheadPosition, 0, playheadPosition, height, playheadPaint);
        }

        // 5. Draw Time Popup
        if (showPopup) {
            drawPopup(canvas);
        }
    }


    private void drawTicks(Canvas canvas, int width, int height) {
        if (audioDurationMs <= 0) return;

        long[] intervals = {
                1000, 2000, 5000, 10000, 30000, 60000,
                300000, 600000, 1800000, 3600000
        };
        long majorTickInterval = intervals[0];
        for (long interval : intervals) {
            int numTicks = (int) (audioDurationMs / interval);
            if (numTicks < 15) {
                majorTickInterval = interval;
                break;
            }
        }
        long minorTickInterval = majorTickInterval / 5;

        float majorTickHeight = height * 0.15f;
        float minorTickHeight = height * 0.08f;

        for (long t = 0; t <= audioDurationMs; t += minorTickInterval) {
            if (t > audioDurationMs) break;
            float x = timeToPosition(t);
            boolean isMajorTick = (t % majorTickInterval == 0);

            if (isMajorTick) {
                canvas.drawLine(x, height - majorTickHeight, x, height, tickPaint);
                String timeLabel = formatTickTime(t);
                canvas.drawText(timeLabel, x, height - majorTickHeight - 5, tickTextPaint);
            } else {
                canvas.drawLine(x, height - minorTickHeight, x, height, tickPaint);
            }
        }
    }


    private void drawPopup(Canvas canvas) {
        Rect textBounds = new Rect();
        popupTextPaint.getTextBounds(popupText, 0, popupText.length(), textBounds);

        float padding = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8, getResources().getDisplayMetrics());
        float popupWidth = textBounds.width() + padding * 2;
        float popupHeight = textBounds.height() + padding * 2;
        float cornerRadius = 15f;

        float popupLeft = popupX - popupWidth / 2;
        if (popupLeft < 0) popupLeft = 0;
        if (popupLeft + popupWidth > getWidth()) popupLeft = getWidth() - popupWidth;

        float popupTop = 0;
        RectF popupRect = new RectF(popupLeft, popupTop, popupLeft + popupWidth, popupTop + popupHeight);
        canvas.drawRoundRect(popupRect, cornerRadius, cornerRadius, popupBackgroundPaint);

        float textX = popupRect.centerX() - textBounds.exactCenterX();
        float textY = popupRect.centerY() - textBounds.exactCenterY();
        canvas.drawText(popupText, textX, textY, popupTextPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX();
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                if (x >= leftHandleX - handleTouchWidth && x <= leftHandleX + handleTouchWidth) {
                    activeHandle = 1;
                    showPopup = true;
                    if (listener != null) listener.onScrubStarted();
                } else if (x >= rightHandleX - handleTouchWidth && x <= rightHandleX + handleTouchWidth) {
                    activeHandle = 2;
                    showPopup = true;
                    if (listener != null) listener.onScrubStarted();
                } else {
                    activeHandle = 3;
                    if (listener != null) listener.onScrubStarted();
                }
                updatePopup(x);
                invalidate();
                return true;
            case MotionEvent.ACTION_MOVE:
                if (activeHandle == 1) { // Left handle
                    leftHandleX = Math.max(0, Math.min(x, rightHandleX - handleWidth));
                    startTrimMs = positionToTime(leftHandleX);
                    updatePopup(leftHandleX);
                    if (listener != null) {
                        listener.onTrimUpdated(startTrimMs, endTrimMs);
                        listener.onScrubbing(startTrimMs);
                    }
                    invalidate();
                } else if (activeHandle == 2) { // Right handle
                    rightHandleX = Math.min(getWidth(), Math.max(x, leftHandleX + handleWidth));
                    endTrimMs = positionToTime(rightHandleX);
                    updatePopup(rightHandleX);
                    if (listener != null) listener.onTrimUpdated(startTrimMs, endTrimMs);
                    invalidate();
                } else if (activeHandle == 3) { // Scrubbing
                    updatePopup(x);
                    if (listener != null) listener.onScrubbing(positionToTime(x));
                }
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                showPopup = false;
                if (activeHandle == 1) {
                    if (listener != null) listener.onScrubEnded(startTrimMs);
                } else if (activeHandle == 2) {
                    // No action needed
                } else if (activeHandle == 3) {
                    if (listener != null) listener.onScrubEnded(positionToTime(x));
                }
                activeHandle = 0;
                invalidate();
                return true;
        }
        return super.onTouchEvent(event);
    }

    private void updatePopup(float x) {
        float clampedX = Math.max(0f, Math.min(x, getWidth()));
        popupX = clampedX;
        popupText = formatPopupTime(positionToTime(clampedX));
    }

    public void setPlayheadPosition(long currentPosition) {
        if (audioDurationMs > 0) {
            this.playheadPosition = timeToPosition(currentPosition);
            invalidate();
        }
    }

    private float timeToPosition(long timeMs) {
        if (audioDurationMs == 0) return 0;
        return ((float) timeMs / audioDurationMs) * getWidth();
    }

    private long positionToTime(float position) {
        return (long) ((position / getWidth()) * audioDurationMs);
    }

    private String formatTickTime(long millis) {
        long hours = TimeUnit.MILLISECONDS.toHours(millis);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60;
        long seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % 60;
        if (hours > 0) {
            return String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds);
        } else {
            return String.format(Locale.getDefault(), "%d:%02d", minutes, seconds);
        }
    }

    private String formatPopupTime(long millis) {
        long minutes = TimeUnit.MILLISECONDS.toMinutes(millis);
        long seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % 60;
        long m = millis % 1000;
        return String.format(Locale.getDefault(), "%d:%02d.%03d", minutes, seconds, m);
    }

    public long getStartTrimMs() { return startTrimMs; }
    public long getEndTrimMs() { return endTrimMs; }
}