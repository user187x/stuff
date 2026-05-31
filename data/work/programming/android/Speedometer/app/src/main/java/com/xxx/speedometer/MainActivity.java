package com.xxx.speedometer;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.GnssStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ViewFlipper;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.GestureDetectorCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.bumptech.glide.Glide;
import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.formatter.ValueFormatter;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity
    implements LocationListener,
        SensorEventListener,
        GestureDetector.OnGestureListener,
        GestureDetector.OnDoubleTapListener {

  private static final int VIBRATION_DURATION_MS = 10;
  private static final int LOCATION_PERMISSION_REQUEST_CODE = 1;
  private LocationManager locationManager;
  private SpeedometerView speedometerView;
  private boolean isFlightMode = false;
  private GestureDetector gestureDetector;
  private boolean isFullscreen = false;

  private ViewFlipper topCardViewFlipper;

  // --- Keep Screen On Button ---
  private ImageButton keepScreenOnButton;
  private boolean isKeepScreenOn = false;

  // --- Views for Speed/Spinner ---
  private TextView speedTextView;
  private ImageView spinnerImageView;

  // --- TextViews for the Info Card ---
  private TextView gpsSignalTextView;
  private TextView elevationTextView;
  private TextView coordinatesTextView;
  private TextView satellitesTextView;
  private TextView magneticFieldTextView;
  private TextView accuracyTextView;

  private ImageButton setWayPointButton;
  private TextView waypointTextView;
  private Location waypointLocation;

  private SensorManager sensorManager;
  private Vibrator vibrator;
  private final float[] rotationMatrix = new float[9];
  private final float[] adjustedRotationMatrix = new float[9];
  private final float[] orientationAngles = new float[3];
  private float lastAzimuth = 0f;

  private GnssStatus.Callback gnssStatusCallback;

  // --- Speed Histogram ---
  private BarChart speedHistogram;
  private ArrayList<Float> speedData = new ArrayList<>();

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);

    speedometerView = findViewById(R.id.speedometerView);

    setWayPointButton = findViewById(R.id.setWayPointButton);
    waypointTextView = findViewById(R.id.waypointTextView);
    setWayPointButton.setOnClickListener(v -> setWaypoint());

    // --- Initialize Keep Screen On Button ---
    keepScreenOnButton = findViewById(R.id.keepScreenOnButton);
    keepScreenOnButton.setOnClickListener(v -> toggleKeepScreenOn());
    keepScreenOnButton.setImageResource(R.drawable.awake_off); // Initial state
    keepScreenOnButton.setTranslationY(25f);
    keepScreenOnButton.setTranslationX(-10f);

    // --- Initialize Speed/Spinner Views ---
    speedTextView = findViewById(R.id.speedTextView);
    spinnerImageView = findViewById(R.id.spinnerImageView);

    // --- Initialize new TextViews ---
    gpsSignalTextView = findViewById(R.id.gpsSignalTextView);
    elevationTextView = findViewById(R.id.elevationTextView);
    coordinatesTextView = findViewById(R.id.coordinatesTextView);
    satellitesTextView = findViewById(R.id.satellitesTextView);
    magneticFieldTextView = findViewById(R.id.magneticFieldTextView);
    accuracyTextView = findViewById(R.id.accuracyTextView);

    gpsSignalTextView.setText("---");
    elevationTextView.setText("---");
    coordinatesTextView.setText("---, ---");
    satellitesTextView.setText("--- / ---");
    magneticFieldTextView.setText("--- μT");
    accuracyTextView.setText("---");

    locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
    sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
    vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

    topCardViewFlipper = findViewById(R.id.topCardViewFlipper);

    gestureDetector = new GestureDetector(this, new MyGestureListener());

    findViewById(R.id.topCard).setOnTouchListener((v, event) -> {

      gestureDetector.onTouchEvent(event);

      if (event.getAction() == MotionEvent.ACTION_UP) {
        v.performClick();
      }

      return true;

    });

    gestureDetector.setOnDoubleTapListener(this);

    // --- Initialize Speed Histogram ---
    speedHistogram = findViewById(R.id.speedHistogram);
    setupSpeedHistogram();

    setupGnssStatusCallback();
    checkLocationPermission();
  }

  private void setWaypoint() {
    if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
      return;
    }
    waypointLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
    if (waypointLocation != null) {
      String waypointText = String.format(Locale.getDefault(), "Waypoint: %.4f, %.4f", waypointLocation.getLatitude(), waypointLocation.getLongitude());
      waypointTextView.setText(waypointText);
      vibrate();
      Toast.makeText(this, "Waypoint Set", Toast.LENGTH_SHORT).show();
    } else {
      Toast.makeText(this, "Location not available to set waypoint.", Toast.LENGTH_SHORT).show();
    }
  }

  private void toggleKeepScreenOn() {
    isKeepScreenOn = !isKeepScreenOn;
    if (isKeepScreenOn) {
      getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
      keepScreenOnButton.setImageResource(R.drawable.awake_on);
      keepScreenOnButton.setAlpha(1.0f);
      vibrate();
      Toast.makeText(this, "Screen Attention Enabled", Toast.LENGTH_SHORT).show();
    } else {
      getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
      keepScreenOnButton.setImageResource(R.drawable.awake_off);
      keepScreenOnButton.setAlpha(0.4f); // A little more translucent
      vibrate();
      Toast.makeText(this, "Screen Attention Disabled", Toast.LENGTH_SHORT).show();
    }
  }

  private void setupGnssStatusCallback() {
    gnssStatusCallback =
        new GnssStatus.Callback() {
          @Override
          public void onSatelliteStatusChanged(@NonNull GnssStatus status) {
            super.onSatelliteStatusChanged(status);
            int satellitesInFix = 0;
            int totalSatellites = status.getSatelliteCount();
            float avgCn0 = 0;
            int satellitesWithCn0 = 0;

            for (int i = 0; i < totalSatellites; ++i) {
              if (status.usedInFix(i)) {
                satellitesInFix++;
              }
              if (status.getCn0DbHz(i) > 0) {
                avgCn0 += status.getCn0DbHz(i);
                satellitesWithCn0++;
              }
            }

            if (satellitesWithCn0 > 0) {
              avgCn0 /= satellitesWithCn0;
            }

            final int finalTotalSatellites = totalSatellites;
            final float finalAvgCn0 = avgCn0;
            final int finalSatellitesInFix = satellitesInFix;

            runOnUiThread(
                () -> {
                  speedometerView.updateSatelliteCount(finalSatellitesInFix);
                  satellitesTextView.setText(
                      String.format(
                          Locale.getDefault(),
                          "%d / %d",
                          finalSatellitesInFix,
                          finalTotalSatellites));
                  updateGpsSignalStrength(finalAvgCn0);

                  // --- Toggle Spinner/Speed Visibility ---
                  if (finalSatellitesInFix < 4) {
                    showSpinner();
                  } else {
                    hideSpinner();
                  }
                });
          }
        };
  }

  private void showSpinner() {
    speedTextView.setVisibility(View.GONE);
    spinnerImageView.setVisibility(View.VISIBLE);
    Glide.with(this).load(R.drawable.spinner).into(spinnerImageView);
  }

  private void hideSpinner() {
    spinnerImageView.setVisibility(View.GONE);
    speedTextView.setVisibility(View.VISIBLE);
  }

  private void updateGpsSignalStrength(float cn0) {
    if (cn0 == 0) {
      gpsSignalTextView.setText("None");
    } else if (cn0 < 35) {
      gpsSignalTextView.setText("Poor");
    } else if (cn0 < 40) {
      gpsSignalTextView.setText("Moderate");
    } else if (cn0 < 45) {
      gpsSignalTextView.setText("Good");
    } else {
      gpsSignalTextView.setText("Excellent");
    }
  }

  @Override
  protected void onResume() {
    super.onResume();
    startLocationUpdates();
    sensorManager.registerListener(
        this,
        sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR),
        SensorManager.SENSOR_DELAY_NORMAL);
    sensorManager.registerListener(
        this,
        sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD),
        SensorManager.SENSOR_DELAY_NORMAL);
    startService(new Intent(this, LocationService.class));
  }

  @Override
  protected void onPause() {
    super.onPause();
    locationManager.removeUpdates(this);
    sensorManager.unregisterListener(this);
    if (gnssStatusCallback != null) {
      if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
          != PackageManager.PERMISSION_GRANTED) {
        return;
      }
      locationManager.unregisterGnssStatusCallback(gnssStatusCallback);
    }
    stopService(new Intent(this, LocationService.class));
  }

  @Override
  public void onSensorChanged(SensorEvent event) {

    if (event.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) {
      SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values);
      int rotation = getWindowManager().getDefaultDisplay().getRotation();
      int axisX = SensorManager.AXIS_X;
      int axisY = SensorManager.AXIS_Y;

      switch (rotation) {
        case Surface.ROTATION_90:
          axisX = SensorManager.AXIS_Y;
          axisY = SensorManager.AXIS_MINUS_X;
          break;
        case Surface.ROTATION_180:
          axisX = SensorManager.AXIS_MINUS_X;
          axisY = SensorManager.AXIS_MINUS_Y;
          break;
        case Surface.ROTATION_270:
          axisX = SensorManager.AXIS_MINUS_Y;
          axisY = SensorManager.AXIS_X;
          break;
      }
      SensorManager.remapCoordinateSystem(rotationMatrix, axisX, axisY, adjustedRotationMatrix);
      updateOrientationAngles();
    } else if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
      float magneticFieldStrength =
          (float)
              Math.sqrt(
                  (event.values[0] * event.values[0])
                      + (event.values[1] * event.values[1])
                      + (event.values[2] * event.values[2]));
      magneticFieldTextView.setText(
          String.format(Locale.getDefault(), "%.1f μT", magneticFieldStrength));
    }
  }

  private void setupSpeedHistogram() {
    speedHistogram.getDescription().setEnabled(false);
    speedHistogram.getLegend().setEnabled(false);
    speedHistogram.setTouchEnabled(false);
    speedHistogram.getAxisRight().setEnabled(false);

    speedHistogram.getAxisLeft().setTextColor(Color.WHITE);
    speedHistogram.getAxisLeft().setAxisMinimum(0f);

    XAxis xAxis = speedHistogram.getXAxis();
    xAxis.setTextColor(Color.WHITE);
    xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
    xAxis.setDrawGridLines(false);
    xAxis.setValueFormatter(
        new ValueFormatter() {
          @Override
          public String getFormattedValue(float value) {
            return String.format(Locale.getDefault(), "%.0f", value);
          }
        });
  }

  private void updateSpeedHistogram() {
    if (speedData.isEmpty()) return;

    int numBins = 10;
    float maxSpeed =
        isFlightMode ? SpeedometerView.MAX_SPEED_FLIGHT : SpeedometerView.MAX_SPEED_CAR;
    float binSize = maxSpeed / numBins;

    if (binSize == 0) return;

    int[] bins = new int[numBins];
    for (float speed : speedData) {
      int binIndex = (int) (speed / binSize);
      if (binIndex >= numBins) {
        binIndex = numBins - 1;
      }
      if (binIndex >= 0) {
        bins[binIndex]++;
      }
    }

    ArrayList<BarEntry> entries = new ArrayList<>();
    for (int i = 0; i < numBins; i++) {
      entries.add(new BarEntry(i * binSize, bins[i]));
    }

    BarDataSet dataSet = new BarDataSet(entries, "Speed");
    dataSet.setColor(Color.WHITE);
    dataSet.setDrawValues(false);

    BarData barData = new BarData(dataSet);
    barData.setBarWidth(binSize * 0.9f);

    speedHistogram.setData(barData);
    speedHistogram.getXAxis().setAxisMaximum(maxSpeed);
    speedHistogram.invalidate();
  }

  @Override
  public void onAccuracyChanged(Sensor sensor, int accuracy) {
    if (sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
      switch (accuracy) {
        case SensorManager.SENSOR_STATUS_ACCURACY_HIGH:
          accuracyTextView.setText(R.string.high);
          break;
        case SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM:
          accuracyTextView.setText(R.string.medium);
          break;
        case SensorManager.SENSOR_STATUS_ACCURACY_LOW:
          accuracyTextView.setText(R.string.low);
          break;
        case SensorManager.SENSOR_STATUS_UNRELIABLE:
          accuracyTextView.setText(R.string.unreliable);
          break;
      }
    }
  }

  public void updateOrientationAngles() {
    SensorManager.getOrientation(adjustedRotationMatrix, orientationAngles);
    float azimuthInDegrees = (float) (Math.toDegrees(orientationAngles[0]) + 360) % 360;

    if (isCardinalDirection(azimuthInDegrees) && !isCardinalDirection(lastAzimuth)) {
      vibrate();
    }

    lastAzimuth = azimuthInDegrees;
    speedometerView.updateCompassDirection(azimuthInDegrees);
  }

  private boolean isCardinalDirection(float azimuth) {
    return (azimuth >= 357.5 || azimuth < 2.5)
        || (azimuth >= 87.5 && azimuth < 92.5)
        || (azimuth >= 177.5 && azimuth < 182.5)
        || (azimuth >= 267.5 && azimuth < 272.5);
  }

  private void startLocationUpdates() {
    if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
        == PackageManager.PERMISSION_GRANTED) {
      locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0, this);
      locationManager.registerGnssStatusCallback(
          gnssStatusCallback, new Handler(Looper.getMainLooper()));
    }
  }

  private void toggleFullscreen() {
    WindowInsetsControllerCompat windowInsetsController =
        new WindowInsetsControllerCompat(getWindow(), getWindow().getDecorView());
    if (isFullscreen) {
      windowInsetsController.show(WindowInsetsCompat.Type.systemBars());
    } else {
      windowInsetsController.hide(WindowInsetsCompat.Type.systemBars());
      windowInsetsController.setSystemBarsBehavior(
          WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
    }
    isFullscreen = !isFullscreen;
  }

  @Override
  public boolean onTouchEvent(MotionEvent event) {
    return gestureDetector.onTouchEvent(event) || super.onTouchEvent(event);
  }

  @Override
  public boolean onDoubleTap(MotionEvent e) {
    toggleFullscreen();
    return true;
  }

  @Override
  public boolean onDown(@NonNull MotionEvent e) {
    return true;
  }

  @Override
  public void onShowPress(@NonNull MotionEvent e) {}

  @Override
  public boolean onSingleTapUp(@NonNull MotionEvent e) {
    return true;
  }

  @Override
  public boolean onScroll(
      @NonNull MotionEvent e1, @NonNull MotionEvent e2, float distanceX, float distanceY) {
    return true;
  }

  @Override
  public void onLongPress(@NonNull MotionEvent e) {}

  @Override
  public boolean onFling(
      @NonNull MotionEvent e1, @NonNull MotionEvent e2, float velocityX, float velocityY) {
    return true;
  }

  @Override
  public boolean onSingleTapConfirmed(@NonNull MotionEvent e) {
    return true;
  }

  @Override
  public boolean onDoubleTapEvent(@NonNull MotionEvent e) {
    return true;
  }

  private void checkLocationPermission() {
    if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
        != PackageManager.PERMISSION_GRANTED) {
      ActivityCompat.requestPermissions(
          this,
          new String[] {Manifest.permission.ACCESS_FINE_LOCATION},
          LOCATION_PERMISSION_REQUEST_CODE);
    } else {
      startLocationUpdates();
    }
  }

  @Override
  public void onRequestPermissionsResult(
      int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
      if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
        startLocationUpdates();
      } else {
        Toast.makeText(this, "Location permission is required to measure speed.", Toast.LENGTH_LONG)
            .show();
      }
    }
  }

  @Override
  public void onLocationChanged(@NonNull Location location) {
    float speedMetersPerSecond = location.getSpeed();
    float speedMph = speedMetersPerSecond * 2.23694f;

    if (speedMph > 200 && !isFlightMode) {
      isFlightMode = true;
      speedometerView.setMaxSpeed(SpeedometerView.MAX_SPEED_FLIGHT);

    } else if (speedMph <= 200 && isFlightMode) {
      isFlightMode = false;
      speedometerView.setMaxSpeed(SpeedometerView.MAX_SPEED_CAR);
    }
    speedometerView.setSpeed(speedMph);
    speedTextView.setText(String.format(Locale.getDefault(), "%.0f", speedMph));

    // --- Update Speed Histogram Data ---
    speedData.add(speedMph);
    updateSpeedHistogram();

    // --- Update new info fields ---
    elevationTextView.setText(String.format(Locale.getDefault(), "%.1f m", location.getAltitude()));
    coordinatesTextView.setText(
        String.format(
            Locale.getDefault(), "%.4f, %.4f", location.getLatitude(), location.getLongitude()));
  }

  @Override
  public void onLocationChanged(@NonNull List<Location> locations) {
    if (!locations.isEmpty()) {
      onLocationChanged(locations.get(locations.size() - 1));
    }
  }

  public void vibrate() {
    if (vibrator != null && vibrator.hasVibrator()) {
      vibrator.vibrate(
          VibrationEffect.createOneShot(VIBRATION_DURATION_MS, VibrationEffect.DEFAULT_AMPLITUDE));
    }
  }

  @Override
  public void onFlushComplete(int requestCode) {
    // Handle flush complete if necessary
  }

  @Override
  public void onProviderEnabled(@NonNull String provider) {}

  @Override
  public void onProviderDisabled(@NonNull String provider) {}

  @Override
  public void onStatusChanged(String provider, int status, Bundle extras) {}

  @Override
  public void onPointerCaptureChanged(boolean hasCapture) {
    super.onPointerCaptureChanged(hasCapture);
  }

  public float[] filter(float[] input, float[] output) {

    if (output == null) {
      return input;
    }
    // ALPHA value updated to match the reference project for optimal smoothing.
    float ALPHA = 0.2f;

    for (int i = 0; i < input.length; i++) {
      output[i] = output[i] + ALPHA * (input[i] - output[i]);
    }
    return output;
  }
  private class MyGestureListener extends GestureDetector.SimpleOnGestureListener {
    private static final int SWIPE_THRESHOLD = 100;
    private static final int SWIPE_VELOCITY_THRESHOLD = 100;

    @Override
    public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
      float diffX = e2.getX() - e1.getX();
      if (Math.abs(diffX) > SWIPE_THRESHOLD && Math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
        if (diffX > 0) {
          // Swipe Right
          topCardViewFlipper.setInAnimation(getApplicationContext(), R.anim.in_from_left);
          topCardViewFlipper.setOutAnimation(getApplicationContext(), R.anim.out_to_right);
          topCardViewFlipper.showPrevious();
        } else {
          // Swipe Left
          topCardViewFlipper.setInAnimation(getApplicationContext(), R.anim.in_from_right);
          topCardViewFlipper.setOutAnimation(getApplicationContext(), R.anim.out_to_left);
          topCardViewFlipper.showNext();
        }
        return true;
      }
      return false;
    }
  }
}
