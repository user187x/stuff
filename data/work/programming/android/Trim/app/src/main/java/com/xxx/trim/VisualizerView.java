package com.xxx.trim;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

public class VisualizerView extends View {

    private byte[] fftBytes;
    private Paint linePaint;
    private float volumeScalar = 0f; // Smoothed volume level (0.0 to 1.0)

    // Define min and max thickness for the visualizer bars
    private static final float MIN_STROKE_WIDTH = 2f;
    private static final float MAX_STROKE_WIDTH = 10f;


    public VisualizerView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        linePaint = new Paint();
        linePaint.setAntiAlias(true);
    }

    /**
     * Clears the visualizer data, resetting it to a blank state.
     */
    public void clear() {
        this.fftBytes = null;
        this.volumeScalar = 0f;
        invalidate();
    }


    /**
     * Updates the visualizer with new FFT data and volume level.
     * @param fftBytes The new byte array of FFT data.
     * @param rms The new volume level (Root Mean Square).
     */
    public void updateVisualizer(byte[] fftBytes, float rms) {
        this.fftBytes = fftBytes;
        // Apply a simple smoothing filter (low-pass) to the volume
        this.volumeScalar = (this.volumeScalar * 0.8f) + (rms * 0.2f);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (fftBytes == null) {
            return;
        }

        int width = getWidth();
        int height = getHeight();
        // We only use half the FFT data for visualization
        final int dataPoints = fftBytes.length / 2;
        if (dataPoints == 0) return;

        // The spacing between bars is now based on the max possible thickness
        int numBars = (int) (width / MAX_STROKE_WIDTH);
        float centerY = height / 2f;

        for (int i = 0; i < numBars; i++) {
            // Calculate the screen x-coordinate for the current bar
            float barX = i * MAX_STROKE_WIDTH;

            // --- Logarithmic Mapping ---
            double logPercent = Math.pow((double) i / numBars, 2.5);
            int fftIndex = (int) (logPercent * (dataPoints - 1));
            // --- End of Mapping ---

            // Get the magnitude for the mapped FFT data point
            byte real = fftBytes[Math.min(fftIndex * 2, fftBytes.length - 2)];
            byte imag = fftBytes[Math.min(fftIndex * 2 + 1, fftBytes.length - 1)];
            float magnitude = (float) Math.hypot(real, imag);

            // Calculate the bar height based on frequency strength
            float frequencyBasedHeight = Math.max(2f, magnitude * (height / 2f) / 100);
            // NOW, scale the height by the overall volume
            float barHeight = frequencyBasedHeight * this.volumeScalar;


            // --- Dynamic Color Calculation ---
            float normalizedHeight = Math.min(1f, frequencyBasedHeight / (height / 2f));
            float hue = 120f * (1f - normalizedHeight);
            int color = Color.HSVToColor(new float[]{hue, 1f, 1f});
            linePaint.setColor(color);
            // --- End of Color Calculation ---

            // --- Dynamic Stroke Width Calculation (also scaled by volume) ---
            float strokeWidth = MIN_STROKE_WIDTH + ((normalizedHeight * this.volumeScalar) * (MAX_STROKE_WIDTH - MIN_STROKE_WIDTH));
            linePaint.setStrokeWidth(strokeWidth);
            // --- End of Stroke Width Calculation ---

            // Calculate top and bottom points relative to the center
            float top = centerY - barHeight;
            float bottom = centerY + barHeight;

            // Draw both arms from the center outwards
            canvas.drawLine(barX, centerY, barX, top, linePaint);
            canvas.drawLine(barX, centerY, barX, bottom, linePaint);
        }
    }
}