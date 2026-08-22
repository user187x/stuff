package com.xxx.server;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.os.IBinder;
import android.view.View;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.bumptech.glide.Glide;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.common.BitMatrix;
import com.xxx.server.databinding.MainActivityBinding;
import com.xxx.server.log.LogAdapter;
import com.xxx.server.web.HttpServerManager;
import com.xxx.server.web.HttpServerService;
import com.xxx.server.web.ServerStatsManager;
import java.util.ArrayList;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

  private static final String PREFS_NAME = "HttpServerPrefs";
  private final int MAX_CHART_POINTS = 60;
  private SharedPreferences preferences;
  private MainActivityBinding binding;
  private HttpServerService httpServerService;
  private final BroadcastReceiver stateReceiver = new BroadcastReceiver() {
    @Override
    public void onReceive(Context context, Intent intent) {
      if (HttpServerService.ACTION_SERVER_STATE_CHANGED.equals(intent.getAction())) {
        updateUIFromService();
      }
    }
  };
  private boolean isServiceBound = false;
  private LogAdapter logAdapter;
  private final ServiceConnection serviceConnection = new ServiceConnection() {
    @Override
    public void onServiceConnected(ComponentName className, IBinder service) {
      HttpServerService.LocalBinder binder = (HttpServerService.LocalBinder) service;
      httpServerService = binder.getService();
      isServiceBound = true;
      updateUIFromService();

      httpServerService.setLogger(logMessage -> {
        runOnUiThread(() -> {
          logAdapter.addLogMessage(logMessage);
          if (logAdapter.isAutoScroll()) {
            binding.logRecyclerView.scrollToPosition(logAdapter.getItemCount() - 1);
          }
        });
      });
    }

    @Override
    public void onServiceDisconnected(ComponentName arg0) {
      isServiceBound = false;
    }
  };
  private LineDataSet rxSet;
  private LineDataSet txSet;
  private LineDataSet wsSet;
  private int chartIndex = 0;
  private final BroadcastReceiver statsReceiver = new BroadcastReceiver() {
    @Override
    public void onReceive(Context context, Intent intent) {
      if (ServerStatsManager.ACTION_STATS_UPDATE.equals(intent.getAction())) {
        updateStats(intent);
      }
    }
  };
  private boolean isSoundEnabled = false;

  @Override
  protected void onStart() {
    super.onStart();
    LocalBroadcastManager lbm = LocalBroadcastManager.getInstance(this);
    lbm.registerReceiver(statsReceiver, new IntentFilter(ServerStatsManager.ACTION_STATS_UPDATE));
    lbm.registerReceiver(stateReceiver,
        new IntentFilter(HttpServerService.ACTION_SERVER_STATE_CHANGED));
    updateUIFromService();
  }

  @Override
  protected void onStop() {
    super.onStop();
    LocalBroadcastManager lbm = LocalBroadcastManager.getInstance(this);
    lbm.unregisterReceiver(statsReceiver);
    lbm.unregisterReceiver(stateReceiver);
  }

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    binding = MainActivityBinding.inflate(getLayoutInflater());
    setContentView(binding.getRoot());

    preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

    setupLogView();
    setupTrafficChart();
    loadSettings();
    setupControls();

    Intent intent = new Intent(this, HttpServerService.class);
    startService(intent);
    bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
  }

  private void loadSettings() {
    binding.portEditText.setText(String.valueOf(preferences.getInt("port", 8080)));
    binding.rootFolderText.setText(preferences.getString("root_folder", "No folder selected"));
    binding.redirectIndexSwitch.setChecked(preferences.getBoolean("redirect_index", true));
    binding.renderFolderSwitch.setChecked(preferences.getBoolean("render_folder", true));
    binding.allowUploadsSwitch.setChecked(preferences.getBoolean("allow_uploads", false));
    binding.basicAuthSwitch.setChecked(preferences.getBoolean("basic_auth", false));
    binding.usernameEditText.setText(preferences.getString("auth_username", ""));
    binding.passwordEditText.setText(preferences.getString("auth_password", ""));
    binding.tlsSwitch.setChecked(preferences.getBoolean("tls_enabled", false));
    binding.autostartSwitch.setChecked(preferences.getBoolean("autostart_on_boot", false));
    binding.autoShutdownSwitch.setChecked(preferences.getBoolean("auto_shutdown_enabled", false));

    isSoundEnabled = preferences.getBoolean("sound_enabled", false);
    updateSoundButton();

    binding.basicAuthPanel.setVisibility(
        binding.basicAuthSwitch.isChecked() ? View.VISIBLE : View.GONE);
    binding.inactivitySeekBar.setEnabled(binding.autoShutdownSwitch.isChecked());
  }

  private void updateSoundButton() {
    binding.connectSoundButton.setText((isSoundEnabled ? "DISABLE" : "ENABLE") + " CONNECT SOUND");
    binding.connectSoundButton.setTextColor(isSoundEnabled ? 0xFF00FF41 : 0xFF00F3FF);
  }

  private void saveSettings() {
    SharedPreferences.Editor editor = preferences.edit();
    try {
      editor.putInt("port", Integer.parseInt(binding.portEditText.getText().toString()));
    } catch (Exception ignored) {
    }
    editor.putString("root_folder", binding.rootFolderText.getText().toString());
    editor.putBoolean("redirect_index", binding.redirectIndexSwitch.isChecked());
    editor.putBoolean("render_folder", binding.renderFolderSwitch.isChecked());
    editor.putBoolean("allow_uploads", binding.allowUploadsSwitch.isChecked());
    editor.putBoolean("basic_auth", binding.basicAuthSwitch.isChecked());
    editor.putString("auth_username", binding.usernameEditText.getText().toString());
    editor.putString("auth_password", binding.passwordEditText.getText().toString());
    editor.putBoolean("tls_enabled", binding.tlsSwitch.isChecked());
    editor.putBoolean("autostart_on_boot", binding.autostartSwitch.isChecked());
    editor.putBoolean("auto_shutdown_enabled", binding.autoShutdownSwitch.isChecked());
    editor.apply();

    com.xxx.server.web.BootReceiver.setEnabled(this, binding.autostartSwitch.isChecked());
  }

  private void setupControls() {
    binding.startStopButton.setOnClickListener(v -> {
      if (isServiceBound) {
        HttpServerManager manager = httpServerService.getServerManager();
        if (manager.isHttpServerRunning()) {
          httpServerService.stopServer();
        } else {
          saveSettings();
          applySettingsFromUI();
          httpServerService.startServer();
        }
      }
    });

    binding.startStopWebSocketButton.setOnClickListener(v -> {
      if (isServiceBound) {
        HttpServerManager manager = httpServerService.getServerManager();
        if (manager.isWebSocketServerRunning()) {
          manager.stopWebSocketServer();
        } else {
          manager.startWebSocketServer();
        }
        updateUIFromService();
      }
    });

    binding.messengerButton.setOnClickListener(v -> {
      Intent intent = new Intent(this, com.xxx.server.web.ChatActivity.class);
      startActivity(intent);
    });

    binding.selectFolderButton.setOnClickListener(v -> {
      Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
      startActivityForResult(intent, 1001);
    });

    binding.ipConfigButton.setOnClickListener(v -> toggleVisibility(binding.ipConfigLayout));
    binding.portConfigButton.setOnClickListener(v -> toggleVisibility(binding.portConfigLayout));

    binding.connectSoundButton.setOnClickListener(v -> {
      isSoundEnabled = !isSoundEnabled;
      preferences.edit().putBoolean("sound_enabled", isSoundEnabled).apply();
      updateSoundButton();
    });

    binding.basicAuthSwitch.setOnCheckedChangeListener((bv, checked) -> {
      binding.basicAuthPanel.setVisibility(checked ? View.VISIBLE : View.GONE);
    });
  }

  private void setupTrafficChart() {
    LineChart chart = binding.trafficChart;
    chart.getDescription().setEnabled(false);
    chart.setTouchEnabled(false);
    chart.setDragEnabled(false);
    chart.setScaleEnabled(false);
    chart.setPinchZoom(false);
    chart.setDrawGridBackground(false);
    chart.setBackgroundColor(Color.BLACK);

    XAxis xAxis = chart.getXAxis();
    xAxis.setEnabled(false);

    YAxis leftAxis = chart.getAxisLeft();
    leftAxis.setTextColor(Color.GRAY);
    leftAxis.setDrawGridLines(true);
    leftAxis.setGridColor(Color.parseColor("#22FFFFFF"));
    leftAxis.setAxisMinimum(0f);
    leftAxis.setLabelCount(3);

    chart.getAxisRight().setEnabled(false);
    chart.getLegend().setEnabled(true);
    chart.getLegend().setTextColor(Color.GRAY);
    chart.getLegend().setTextSize(7f);
    chart.getLegend().setForm(com.github.mikephil.charting.components.Legend.LegendForm.LINE);
    chart.getLegend().setVerticalAlignment(
        com.github.mikephil.charting.components.Legend.LegendVerticalAlignment.BOTTOM);
    chart.getLegend().setHorizontalAlignment(
        com.github.mikephil.charting.components.Legend.LegendHorizontalAlignment.CENTER);
    chart.getLegend().setOrientation(
        com.github.mikephil.charting.components.Legend.LegendOrientation.HORIZONTAL);
    chart.getLegend().setDrawInside(false);

    rxSet = createDataSet("RX", ContextCompat.getColor(this, R.color.glow_green));
    txSet = createDataSet("TX", ContextCompat.getColor(this, R.color.glow_blue));
    wsSet = createDataSet("CHAT", Color.parseColor("#00FF41")); // Neon Green

    chart.setData(new LineData(rxSet, txSet, wsSet));
  }

  private LineDataSet createDataSet(String label, int color) {
    LineDataSet set = new LineDataSet(new ArrayList<>(), label);
    set.setMode(LineDataSet.Mode.CUBIC_BEZIER);
    set.setCubicIntensity(0.2f);
    set.setDrawCircles(false);
    set.setLineWidth(2f);
    set.setColor(color);
    set.setDrawValues(false);
    set.setDrawFilled(true);
    set.setFillColor(color);
    set.setFillAlpha(30);
    return set;
  }

  private void setupLogView() {
    logAdapter = new LogAdapter();
    binding.logRecyclerView.setLayoutManager(new LinearLayoutManager(this));
    binding.logRecyclerView.setAdapter(logAdapter);

    binding.autoScrollButton.setOnClickListener(v -> {
      boolean current = logAdapter.isAutoScroll();
      logAdapter.setAutoScroll(!current);
      binding.autoScrollButton.setText("AUTO_SCROLL: " + (!current ? "ON" : "OFF"));
      binding.autoScrollButton.setTextColor(!current ? 0xFF00FF41 : 0xFF555555);
    });
  }

  private void applySettingsFromUI() {
    if (!isServiceBound) {
      return;
    }
    HttpServerManager manager = httpServerService.getServerManager();

    try {
      int port = Integer.parseInt(binding.portEditText.getText().toString());
      manager.setPort(port);
    } catch (Exception ignored) {
    }

    manager.setRedirectToIndex(binding.redirectIndexSwitch.isChecked());
    manager.setRenderFolderContent(binding.renderFolderSwitch.isChecked());
    manager.setAllowUploads(binding.allowUploadsSwitch.isChecked());

    manager.setBasicAuth(binding.basicAuthSwitch.isChecked());
    manager.setAuthUsername(binding.usernameEditText.getText().toString());
    manager.setAuthPassword(binding.passwordEditText.getText().toString());

    manager.setTlsEnabled(binding.tlsSwitch.isChecked());

    String root = binding.rootFolderText.getText().toString();
    if (!"NONE".equals(root) && !"No folder selected".equals(root)) {
      manager.setRootFolder(root);
    }
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
    if (requestCode == 1001 && resultCode == RESULT_OK) {
      if (data != null && data.getData() != null) {
        String uri = data.getData().toString();
        binding.rootFolderText.setText(uri);
        if (isServiceBound) {
          httpServerService.getServerManager().setRootFolder(uri);
        }
        // Persist Uri permission
        getContentResolver().takePersistableUriPermission(data.getData(),
            Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
      }
    }
  }

  private void toggleVisibility(View view) {
    view.setVisibility(view.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
  }

  @Override
  protected void onResume() {
    super.onResume();
    updateUIFromService();
  }

  private void updateUIFromService() {
    if (httpServerService == null || httpServerService.getServerManager() == null) {
      return;
    }

    HttpServerManager manager = httpServerService.getServerManager();
    boolean isHttpRunning = manager.isHttpServerRunning();
    boolean isWsRunning = manager.isWebSocketServerRunning();

    binding.serverStatusText.setText(isHttpRunning ? "RUNNING" : "OFFLINE");
    binding.serverStatusText.setTextColor(isHttpRunning ? 0xFF00FF41 : 0xFFFF003C);
    binding.gearsImageView.setVisibility(isHttpRunning ? View.VISIBLE : View.GONE);

    if (isHttpRunning) {
      binding.onlineGifView.setVisibility(View.VISIBLE);
      Glide.with(this).asGif().load(R.drawable.online_status).into(binding.onlineGifView);

      String ip = manager.getServerAddress();
      int port = manager.getPort();
      String url = "http://" + ip + ":" + port;
      binding.serverAddressText.setVisibility(View.VISIBLE);
      binding.serverAddressText.setText(url);
      binding.serverAddressText.setTextColor(0xFF00F3FF); // Neon Blue
      generateQRCode(url);
    } else {
      binding.onlineGifView.setVisibility(View.GONE);
      binding.serverAddressText.setVisibility(View.GONE);
      binding.qrCodeCard.setVisibility(View.GONE);
    }

    binding.startStopButton.setText(isHttpRunning ? "STOP HTTP" : "START HTTP");
    binding.startStopWebSocketButton.setText(isWsRunning ? "STOP CHAT" : "START CHAT");
    binding.messengerButton.setVisibility(isWsRunning ? View.VISIBLE : View.GONE);
  }

  private void generateQRCode(String text) {
    try {
      MultiFormatWriter writer = new MultiFormatWriter();
      BitMatrix matrix = writer.encode(text, BarcodeFormat.QR_CODE, 512, 512);
      int width = matrix.getWidth();
      int height = matrix.getHeight();
      Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);
      for (int x = 0; x < width; x++) {
        for (int y = 0; y < height; y++) {
          bitmap.setPixel(x, y, matrix.get(x, y) ? Color.BLACK : Color.WHITE);
        }
      }
      binding.qrCodeImageView.setImageBitmap(bitmap);
      binding.qrCodeCard.setVisibility(View.VISIBLE);
    } catch (Exception e) {
      binding.qrCodeCard.setVisibility(View.GONE);
    }
  }

  private void updateStats(Intent intent) {
    long uptime = intent.getLongExtra(ServerStatsManager.EXTRA_UPTIME, 0);
    long totalRx = intent.getLongExtra(ServerStatsManager.EXTRA_TOTAL_RX, 0);
    long totalTx = intent.getLongExtra(ServerStatsManager.EXTRA_TOTAL_TX, 0);
    long totalRequests = intent.getLongExtra(ServerStatsManager.EXTRA_TOTAL_REQUESTS, 0);
    long totalConnections = intent.getLongExtra(ServerStatsManager.EXTRA_TOTAL_CONNECTIONS, 0);
    double rxRate = intent.getDoubleExtra(ServerStatsManager.EXTRA_CURRENT_RX_RATE, 0);
    double txRate = intent.getDoubleExtra(ServerStatsManager.EXTRA_CURRENT_TX_RATE, 0);
    double wsRxRate = intent.getDoubleExtra(ServerStatsManager.EXTRA_WEBSOCKET_RX_RATE, 0);
    double wsTxRate = intent.getDoubleExtra(ServerStatsManager.EXTRA_WEBSOCKET_TX_RATE, 0);

    binding.uptimeText.setText(formatUptime(uptime));
    binding.totalRxText.setText(formatBytes(totalRx));
    binding.totalTxText.setText(formatBytes(totalTx));
    binding.totalRequestsText.setText(String.valueOf(totalRequests));
    binding.totalConnectionsText.setText(String.valueOf(totalConnections));
    binding.currentRateText.setText(
        String.format(Locale.getDefault(), "%.1f KB/s", txRate / 1024.0));

    updateChart(rxRate, txRate, wsRxRate + wsTxRate);
  }

  private void updateChart(double rxRate, double txRate, double wsRate) {
    LineData data = binding.trafficChart.getData();
    if (data == null) {
      return;
    }

    data.addEntry(new Entry(chartIndex, (float) rxRate / 1024f), 0);
    data.addEntry(new Entry(chartIndex, (float) txRate / 1024f), 1);
    data.addEntry(new Entry(chartIndex, (float) wsRate / 1024f), 2);
    chartIndex++;

    if (rxSet.getEntryCount() > MAX_CHART_POINTS) {
      rxSet.removeFirst();
      txSet.removeFirst();
      wsSet.removeFirst();
      // Adjust X values for remaining entries
      for (int i = 0; i < rxSet.getEntryCount(); i++) {
        rxSet.getEntryForIndex(i).setX(i);
        txSet.getEntryForIndex(i).setX(i);
        wsSet.getEntryForIndex(i).setX(i);
      }
      chartIndex = MAX_CHART_POINTS;
    }

    data.notifyDataChanged();
    binding.trafficChart.notifyDataSetChanged();
    binding.trafficChart.invalidate();
  }

  private String formatUptime(long millis) {
    long seconds = millis / 1000;
    long minutes = seconds / 60;
    long hours = minutes / 60;
    return String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes % 60, seconds % 60);
  }

  private String formatBytes(long bytes) {
    if (bytes < 1024) {
      return bytes + " B";
    }
    int exp = (int) (Math.log(bytes) / Math.log(1024));
    char pre = "KMGTPE".charAt(exp - 1);
    return String.format(Locale.getDefault(), "%.1f %cB", bytes / Math.pow(1024, exp), pre);
  }
}