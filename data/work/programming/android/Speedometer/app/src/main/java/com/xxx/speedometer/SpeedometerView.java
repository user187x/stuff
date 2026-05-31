package com.xxx.speedometer;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

public class SpeedometerView extends View {

  // --- Speedometer & Compass Variables ---
  private Paint activeSegmentPaint,
      inactiveSegmentPaint,
      speedTextPaint,
      mphTextPaint,
      headingTextPaint;
  private Paint circlePaint, textPaint, northPaint, headingLinePaint;
  private RectF segmentRect;
  private float speed = 0;
  private float currentMaxSpeed = 200;
  public static final float MAX_SPEED_CAR = 200;
  public static final float MAX_SPEED_FLIGHT = 600;
  private final int NUM_SPEED_SEGMENTS = 40;
  private final float SPEEDOMETER_START_ANGLE = 90;
  private final float SPEEDOMETER_SWEEP_ANGLE = 360;
  private float azimuth = 0f;

  // --- New Satellite Ring Variables ---
  private Paint activeSatellitePaint, inactiveSatellitePaint;
  private RectF satelliteRect;
  private int satelliteCount = 0;
  private final int NUM_SATELLITE_SEGMENTS = 40;

  private static final String[] CARDINALS = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};

  public static String getCardinalDirection(float azimuth) {
    int i = (int) Math.round(((azimuth % 360) / 45));
    if (i >= 8) {
      i = 0; // Wrap around
    }
    return CARDINALS[i];
  }

  public SpeedometerView(Context context, @Nullable AttributeSet attrs) {
    super(context, attrs);
    init();
  }

  private void init() {
    // --- Satellite Ring Paints ---
    activeSatellitePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    activeSatellitePaint.setColor(
        ContextCompat.getColor(getContext(), android.R.color.holo_green_light));
    activeSatellitePaint.setStyle(Paint.Style.STROKE);
    activeSatellitePaint.setStrokeWidth(15);
    activeSatellitePaint.setStrokeCap(Paint.Cap.BUTT);

    inactiveSatellitePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    inactiveSatellitePaint.setColor(Color.DKGRAY);
    inactiveSatellitePaint.setStyle(Paint.Style.STROKE);
    inactiveSatellitePaint.setStrokeWidth(15);
    inactiveSatellitePaint.setStrokeCap(Paint.Cap.BUTT);

    // --- Compass Paints ---
    circlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    circlePaint.setStyle(Paint.Style.STROKE);
    textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    textPaint.setTextAlign(Paint.Align.CENTER);
    northPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    northPaint.setTextAlign(Paint.Align.CENTER);
    northPaint.setColor(Color.RED);
    headingLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    headingLinePaint.setColor(Color.WHITE);
    headingLinePaint.setStrokeWidth(5);

    // --- Speedometer & Text Paints ---
    headingTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    headingTextPaint.setColor(Color.WHITE);
    headingTextPaint.setTextSize(50);
    headingTextPaint.setTextAlign(Paint.Align.CENTER);
    mphTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    mphTextPaint.setColor(Color.WHITE);
    mphTextPaint.setTextSize(50);
    mphTextPaint.setTextAlign(Paint.Align.CENTER);
    activeSegmentPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    activeSegmentPaint.setColor(Color.WHITE);
    activeSegmentPaint.setStyle(Paint.Style.STROKE);
    activeSegmentPaint.setStrokeWidth(25);
    activeSegmentPaint.setStrokeCap(Paint.Cap.BUTT);
    inactiveSegmentPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    inactiveSegmentPaint.setColor(Color.DKGRAY);
    inactiveSegmentPaint.setStyle(Paint.Style.STROKE);
    inactiveSegmentPaint.setStrokeWidth(25);
    inactiveSegmentPaint.setStrokeCap(Paint.Cap.BUTT);
    speedTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    speedTextPaint.setColor(Color.WHITE);
    speedTextPaint.setTextSize(160);
    speedTextPaint.setTextAlign(Paint.Align.CENTER);

    segmentRect = new RectF();
    satelliteRect = new RectF();
    setBackgroundColor(Color.BLACK);
  }

  public void updateCompassDirection(float azimuth) {
    this.azimuth = azimuth;
    invalidate();
  }

  public void setMaxSpeed(float maxSpeed) {
    this.currentMaxSpeed = maxSpeed;
    invalidate();
  }

  public void setSpeed(float speed) {
    if (speed < 0) this.speed = 0;
    else if (speed > currentMaxSpeed) this.speed = currentMaxSpeed;
    else this.speed = speed;
    invalidate();
  }

  public void updateSatelliteCount(int count) {
    this.satelliteCount = count;
    invalidate();
  }

  @Override
  protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);

    int centerX = getWidth() / 2;
    int centerY = getHeight() / 2;
    int compassRadius = Math.min(centerX, centerY) - 110;

    drawCompass(canvas, centerX, centerY, compassRadius);

    // --- Draw Speedometer Ring ---
    int speedometerRadius = compassRadius - 60;
    segmentRect.set(
        centerX - speedometerRadius,
        centerY - speedometerRadius,
        centerX + speedometerRadius,
        centerY + speedometerRadius);
    float speedSegmentSweep = SPEEDOMETER_SWEEP_ANGLE / NUM_SPEED_SEGMENTS;
    int activeSpeedSegments = (int) ((speed / currentMaxSpeed) * NUM_SPEED_SEGMENTS);

    for (int i = 0; i < NUM_SPEED_SEGMENTS; i++) {
      float start = SPEEDOMETER_START_ANGLE + (i * speedSegmentSweep);
      Paint paintToUse = (i < activeSpeedSegments) ? activeSegmentPaint : inactiveSegmentPaint;
      float gap = (i == NUM_SPEED_SEGMENTS - 1) ? 0 : 2;
      canvas.drawArc(segmentRect, start, speedSegmentSweep - gap, false, paintToUse);
    }

    // --- Draw Satellite Ring ---
    int satelliteRadius = speedometerRadius - 40;
    satelliteRect.set(
        centerX - satelliteRadius,
        centerY - satelliteRadius,
        centerX + satelliteRadius,
        centerY + satelliteRadius);
    float satelliteSegmentSweep = 360f / NUM_SATELLITE_SEGMENTS;

    for (int i = 0; i < NUM_SATELLITE_SEGMENTS; i++) {
      float start = SPEEDOMETER_START_ANGLE + (i * satelliteSegmentSweep);
      Paint paintToUse = (i < satelliteCount) ? activeSatellitePaint : inactiveSatellitePaint;
      float gap = (i == NUM_SATELLITE_SEGMENTS - 1) ? 0 : 2;
      canvas.drawArc(satelliteRect, start, satelliteSegmentSweep - gap, false, paintToUse);
    }

    // --- Draw Central Text ---
    int textVerticalOffset = 100;
    String headingText = String.format("%.0f° %s", azimuth, getCardinalDirection(azimuth));
    canvas.drawText(headingText, centerX, centerY - textVerticalOffset, headingTextPaint);
    // REMOVED: canvas.drawText(String.format("%.0f", speed), centerX, centerY +
    // (speedTextPaint.getTextSize() / 3), speedTextPaint);
    canvas.drawText(
        "MPH",
        centerX,
        centerY + (speedTextPaint.getTextSize() / 3) + textVerticalOffset,
        mphTextPaint);
  }

  private void drawCompass(Canvas canvas, int centerX, int centerY, int radius) {
    circlePaint.setColor(ContextCompat.getColor(getContext(), android.R.color.darker_gray));
    circlePaint.setStrokeWidth(3);
    textPaint.setColor(ContextCompat.getColor(getContext(), android.R.color.white));
    textPaint.setTextSize(radius * 0.12f);
    northPaint.setTextSize(radius * 0.15f);

    canvas.drawLine(
        centerX,
        (float) (centerY - radius * 0.9),
        centerX,
        (float) (centerY - radius * 1.1),
        headingLinePaint);

    canvas.save();
    canvas.rotate(-azimuth, centerX, centerY);
    canvas.drawCircle(centerX, centerY, radius, circlePaint);

    for (int i = 0; i < 360; i += 2) {
      if (i % 30 == 0) {
        canvas.drawLine(
            centerX,
            (float) (centerY - radius),
            centerX,
            (float) (centerY - radius * 0.9),
            circlePaint);
      } else {
        canvas.drawLine(
            centerX,
            (float) (centerY - radius),
            centerX,
            (float) (centerY - radius * 0.95),
            circlePaint);
      }
      canvas.rotate(2, centerX, centerY);
    }
    for (int i = 0; i < 360; i += 30) {
      if (i % 90 == 0) {
        String cardinal = getCardinalDirection(i);
        Paint paint = (i == 0) ? northPaint : textPaint;
        canvas.drawText(cardinal, centerX, (float) (centerY - radius * 1.05), paint);
      } else {
        canvas.drawText(String.valueOf(i), centerX, (float) (centerY - radius * 1.05), textPaint);
      }
      canvas.rotate(30, centerX, centerY);
    }
    canvas.restore();
  }
}
