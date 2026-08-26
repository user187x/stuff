package com.xxx.server;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.media.Image;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.common.BitMatrix;
import com.xxx.server.databinding.MainActivityBinding;
import com.xxx.server.log.LogAdapter;
import com.xxx.server.web.CameraStreamManager;
import com.xxx.server.web.HttpServerManager;
import com.xxx.server.web.HttpServerService;
import com.xxx.server.web.ServerStatsManager;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

  private static final String PREFS_NAME = "HttpServerPrefs";
  private static final int LOCAL_NET_REQ_CODE = 3001;
  private static final int CAMERA_REQ_CODE = 2001;
  private final int MAX_CHART_POINTS = 60;
  private SharedPreferences preferences;
  private MainActivityBinding binding;
  private HttpServerService httpServerService;
  private String currentRootUri = "NONE";

  // ---- Inline camera state ----
  private ExecutorService cameraExecutor;
  private ProcessCameraProvider cameraProvider;
  private boolean isCameraOn = false;

  // ---- Inline chat/messenger state ----
  private final List<String> chatMessages = new ArrayList<>();
  private ChatMessageAdapter chatAdapter;
  private boolean isChatOpen = false;
  private final Handler typingHandler = new Handler(Looper.getMainLooper());
  private boolean typingSignalSent = false;
  private final Runnable typingTimeout = () -> sendTypingSignal(false);

  private final BroadcastReceiver chatReceiver = new BroadcastReceiver() {
    @Override
    public void onReceive(Context context, Intent intent) {
      String action = intent.getAction();
      if ("com.xxx.server.CHAT_MESSAGE".equals(action)) {
        String sender = intent.getStringExtra("sender");
        String message = intent.getStringExtra("message");
        if (sender != null && message != null) {
          addChatMessage("[" + sender + "]: " + message);
        }
      } else if ("com.xxx.server.CHAT_EVENT".equals(action)) {
        String type = intent.getStringExtra("type");
        String sender = intent.getStringExtra("sender");
        String data = intent.getStringExtra("data");
        if ("TYPING".equals(type)) {
          showTyping(sender, "START".equals(data));
        }
      }
    }
  };

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

    IntentFilter chatFilter = new IntentFilter();
    chatFilter.addAction("com.xxx.server.CHAT_MESSAGE");
    chatFilter.addAction("com.xxx.server.CHAT_EVENT");
    lbm.registerReceiver(chatReceiver, chatFilter);

    updateUIFromService();
  }

  @Override
  protected void onStop() {
    super.onStop();
    LocalBroadcastManager lbm = LocalBroadcastManager.getInstance(this);
    lbm.unregisterReceiver(statsReceiver);
    lbm.unregisterReceiver(stateReceiver);
    lbm.unregisterReceiver(chatReceiver);
  }

  private void ensureLocalNetworkPermission() {
    // Only exists / is enforced on API 37+. Guard so older devices skip it.
    if (android.os.Build.VERSION.SDK_INT >= 37) {
      String perm = "android.permission.ACCESS_LOCAL_NETWORK";
      if (ContextCompat.checkSelfPermission(this, perm)
          != PackageManager.PERMISSION_GRANTED) {
        ActivityCompat.requestPermissions(this, new String[]{ perm }, LOCAL_NET_REQ_CODE);
        return; // start the server from onRequestPermissionsResult once granted
      }
    }
    startServerNow();
  }

  // The actual start sequence — called once the permission has been sorted out.
  private void startServerNow() {
    if (!isServiceBound || httpServerService == null) return;
    saveSettings();
    applySettingsFromUI();
    httpServerService.startServer();
  }

  @Override
  public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
      @NonNull int[] grantResults) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults);

    if (requestCode == LOCAL_NET_REQ_CODE) {
      boolean granted = grantResults.length > 0
          && grantResults[0] == PackageManager.PERMISSION_GRANTED;
      if (!granted) {
        android.widget.Toast.makeText(this,
            "Local network permission denied — other devices may not be able to connect.",
            android.widget.Toast.LENGTH_LONG).show();
      }
      startServerNow();
    } else if (requestCode == CAMERA_REQ_CODE) {
      if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
        startInlineCamera();
      } else {
        android.widget.Toast.makeText(this, "Camera permission denied",
            android.widget.Toast.LENGTH_SHORT).show();
      }
    }
  }

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    binding = MainActivityBinding.inflate(getLayoutInflater());
    setContentView(binding.getRoot());

    preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

    setupLogView();
    setupTrafficChart();
    setupChat();
    loadSettings();
    setupControls();

    Intent intent = new Intent(this, HttpServerService.class);
    startService(intent);
    bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
  }

  // ---------------- Inline camera preview ----------------

  private void toggleCameraPreview() {
    if (isCameraOn) {
      stopInlineCamera();
    } else if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA)
        == PackageManager.PERMISSION_GRANTED) {
      startInlineCamera();
    } else {
      ActivityCompat.requestPermissions(this,
          new String[]{ android.Manifest.permission.CAMERA }, CAMERA_REQ_CODE);
    }
  }

  private void startInlineCamera() {
    if (cameraExecutor == null || cameraExecutor.isShutdown()) {
      cameraExecutor = Executors.newSingleThreadExecutor();
    }
    binding.cameraPreview.setVisibility(View.VISIBLE);

    ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(this);
    future.addListener(() -> {
      try {
        cameraProvider = future.get();

        Preview preview = new Preview.Builder().build();
        preview.setSurfaceProvider(binding.cameraPreview.getSurfaceProvider());

        ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
            .build();
        imageAnalysis.setAnalyzer(cameraExecutor, this::processImageProxy);

        cameraProvider.unbindAll();
        cameraProvider.bindToLifecycle(this, CameraSelector.DEFAULT_FRONT_CAMERA,
            preview, imageAnalysis);

        isCameraOn = true;
        binding.cameraButton.setText("STOP CAMERA");
      } catch (Exception e) {
        android.widget.Toast.makeText(this, "Failed to start camera",
            android.widget.Toast.LENGTH_SHORT).show();
        binding.cameraPreview.setVisibility(View.GONE);
      }
    }, ContextCompat.getMainExecutor(this));
  }

  private void stopInlineCamera() {
    if (cameraProvider != null) {
      cameraProvider.unbindAll();
    }
    binding.cameraPreview.setVisibility(View.GONE);
    isCameraOn = false;
    binding.cameraButton.setText("STREAM CAMERA");
  }

  @OptIn(markerClass = ExperimentalGetImage.class)
  private void processImageProxy(ImageProxy imageProxy) {
    Image image = imageProxy.getImage();
    if (image != null) {
      // How many degrees the buffer must be rotated to appear upright on this device.
      int rotation = imageProxy.getImageInfo().getRotationDegrees();
      byte[] jpeg = yuv420ToJpeg(imageProxy);
      jpeg = rotateJpeg(jpeg, rotation);
      CameraStreamManager.getInstance().pushFrame(jpeg);
    }
    imageProxy.close();
  }

  private byte[] rotateJpeg(byte[] jpeg, int degrees) {
    if (degrees == 0 || jpeg == null) return jpeg;
    Bitmap src = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.length);
    if (src == null) return jpeg;
    Matrix matrix = new Matrix();
    matrix.postRotate(degrees);
    Bitmap rotated = Bitmap.createBitmap(src, 0, 0, src.getWidth(), src.getHeight(), matrix, true);
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    rotated.compress(Bitmap.CompressFormat.JPEG, 60, out);
    if (rotated != src) {
      rotated.recycle();
    }
    src.recycle();
    return out.toByteArray();
  }

  private byte[] yuv420ToJpeg(ImageProxy image) {
    ImageProxy.PlaneProxy[] planes = image.getPlanes();
    ByteBuffer yBuffer = planes[0].getBuffer();
    ByteBuffer uBuffer = planes[1].getBuffer();
    ByteBuffer vBuffer = planes[2].getBuffer();

    int ySize = yBuffer.remaining();
    int uSize = uBuffer.remaining();
    int vSize = vBuffer.remaining();

    byte[] nv21 = new byte[ySize + uSize + vSize];
    yBuffer.get(nv21, 0, ySize);
    vBuffer.get(nv21, ySize, vSize);
    uBuffer.get(nv21, ySize + vSize, uSize);

    YuvImage yuvImage = new YuvImage(nv21, ImageFormat.NV21, image.getWidth(), image.getHeight(),
        null);
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    yuvImage.compressToJpeg(new Rect(0, 0, image.getWidth(), image.getHeight()), 60, out);
    return out.toByteArray();
  }

  // ---------------- Inline chat / messenger ----------------

  private void setupChat() {
    chatAdapter = new ChatMessageAdapter(chatMessages);
    binding.chatRecyclerView.setLayoutManager(new LinearLayoutManager(this));
    binding.chatRecyclerView.setAdapter(chatAdapter);

    binding.chatSendButton.setOnClickListener(v -> sendChatMessage());

    binding.chatInput.addTextChangedListener(new android.text.TextWatcher() {
      @Override
      public void beforeTextChanged(CharSequence s, int a, int b, int c) {
      }

      @Override
      public void onTextChanged(CharSequence s, int a, int b, int c) {
        if (!typingSignalSent) {
          typingSignalSent = true;
          sendTypingSignal(true);
        }
        typingHandler.removeCallbacks(typingTimeout);
        typingHandler.postDelayed(typingTimeout, 2000);
      }

      @Override
      public void afterTextChanged(android.text.Editable s) {
      }
    });
  }

  // Messenger button: slide the panel open/closed and enable/disable the WebSocket with it.
  private void toggleChat() {
    if (isChatOpen) {
      binding.chatPanel.animate()
          .translationY(-binding.chatPanel.getHeight())
          .alpha(0f)
          .setDuration(250)
          .setListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
              binding.chatPanel.setVisibility(View.GONE);
              binding.chatPanel.setTranslationY(0f);
              binding.chatPanel.setAlpha(1f);
            }
          });
      // Note: closing the local view does NOT disable the WebSocket, so the
      // client portal keeps its chat column beside the camera feed.
      isChatOpen = false;
      binding.messengerButton.setText("MESSENGER");
    } else {
      if (isServiceBound && httpServerService != null) {
        // Make sure chat is enabled for connected clients.
        httpServerService.getServerManager().startWebSocketServer();
      }
      binding.chatPanel.setVisibility(View.VISIBLE);
      binding.chatPanel.setAlpha(0f);
      binding.chatPanel.setTranslationY(-40f);
      binding.chatPanel.animate()
          .translationY(0f)
          .alpha(1f)
          .setDuration(250)
          .setListener(null);
      isChatOpen = true;
      binding.messengerButton.setText("HIDE MESSENGER");
    }
  }

  private void sendChatMessage() {
    String msg = binding.chatInput.getText().toString().trim();
    if (!msg.isEmpty()) {
      Intent intent = new Intent(this, HttpServerService.class);
      intent.setAction("send_chat_message");
      intent.putExtra("message", msg);
      startService(intent);
      binding.chatInput.setText("");
    }
  }

  private void sendTypingSignal(boolean start) {
    if (!start) {
      typingSignalSent = false;
    }
    Intent intent = new Intent(this, HttpServerService.class);
    intent.setAction("send_chat_event");
    intent.putExtra("type", "TYPING");
    intent.putExtra("data", start ? "START" : "STOP");
    startService(intent);
  }

  private void showTyping(String sender, boolean isTyping) {
    runOnUiThread(() -> {
      if (isTyping && !"SERVER".equals(sender)) {
        binding.chatTypingIndicator.setText("(" + sender + " is typing...)");
        binding.chatTypingIndicator.setVisibility(View.VISIBLE);
      } else {
        binding.chatTypingIndicator.setVisibility(View.INVISIBLE);
      }
    });
  }

  private void addChatMessage(String text) {
    runOnUiThread(() -> {
      chatMessages.add(text);
      chatAdapter.notifyItemInserted(chatMessages.size() - 1);
      binding.chatRecyclerView.scrollToPosition(chatMessages.size() - 1);
    });
  }

  // -------------------------------------------------------

  private void loadSettings() {
    binding.portEditText.setText(String.valueOf(preferences.getInt("port", 8080)));

    currentRootUri = preferences.getString("root_folder", "No folder selected");
    binding.rootFolderText.setText(getDirectoryName(currentRootUri));
    updateServingGifVisibility();

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
    editor.putString("root_folder", currentRootUri);

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
          ensureLocalNetworkPermission();
        }
      }
    });

    // Messenger button now toggles the inline chat panel (and the WebSocket with it).
    binding.messengerButton.setOnClickListener(v -> toggleChat());

    // Camera button toggles the inline preview.
    binding.cameraButton.setOnClickListener(v -> toggleCameraPreview());

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

    String root = currentRootUri;
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

        currentRootUri = uri;
        binding.rootFolderText.setText(getDirectoryName(currentRootUri));
        updateServingGifVisibility();

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

  @Override
  protected void onDestroy() {
    super.onDestroy();
    typingHandler.removeCallbacks(typingTimeout);
    if (cameraProvider != null) {
      cameraProvider.unbindAll();
    }
    if (cameraExecutor != null) {
      cameraExecutor.shutdown();
    }
  }

  private void updateUIFromService() {
    if (httpServerService == null || httpServerService.getServerManager() == null) {
      return;
    }

    HttpServerManager manager = httpServerService.getServerManager();
    boolean isHttpRunning = manager.isHttpServerRunning();

    binding.serverStatusText.setText(isHttpRunning ? "" : "OFFLINE");
    binding.serverStatusText.setTextColor(isHttpRunning ? 0xFF00FF41 : 0xFFFF003C);

    if (isHttpRunning) {
      binding.onlineGifView.setVisibility(View.VISIBLE);
      Glide.with(this).asGif().load(R.drawable.online_status).into(binding.onlineGifView);

      String ip = manager.getServerAddress();
      int port = manager.getPort();
      String url = "http://" + ip + ":" + port;
      binding.serverAddressText.setVisibility(View.VISIBLE);
      binding.serverAddressText.setText(" " + url);
      binding.serverAddressText.setTextColor(0xFF00F3FF); // Neon Blue
      generateQRCode(url);
    } else {
      binding.onlineGifView.setVisibility(View.GONE);
      binding.serverAddressText.setVisibility(View.GONE);
      binding.qrCodeCard.setVisibility(View.GONE);
    }

    binding.startStopButton.setText(isHttpRunning ? "STOP SERVER" : "START SERVER");

    // Freeze or animate the serving-directory GIF to match the running state.
    updateServingGifVisibility();
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

  private String getDirectoryName(String uriString) {
    if (uriString == null || uriString.equals("NONE") || uriString.equals("No folder selected")) {
      return uriString;
    }
    try {
      android.net.Uri uri = android.net.Uri.parse(uriString);
      String path = uri.getPath();
      if (path != null) {
        int lastColon = path.lastIndexOf(':');
        if (lastColon != -1) return path.substring(lastColon + 1);

        int lastSlash = path.lastIndexOf('/');
        if (lastSlash != -1) return path.substring(lastSlash + 1);
      }
    } catch (Exception ignored) {}
    return uriString;
  }

  private void updateServingGifVisibility() {
    boolean hasFolder = !"NONE".equals(currentRootUri)
        && !"No folder selected".equals(currentRootUri);
    if (!hasFolder) {
      binding.servingDirectoryGif.setVisibility(View.GONE);
      return;
    }

    binding.servingDirectoryGif.setVisibility(View.VISIBLE);

    boolean serverRunning = isServiceBound && httpServerService != null
        && httpServerService.getServerManager() != null
        && httpServerService.getServerManager().isHttpServerRunning();

    if (serverRunning) {
      // Server is live and serving — play the animation.
      Glide.with(this).asGif().load(R.drawable.serving_directory)
          .into(binding.servingDirectoryGif);
    } else {
      // Server idle — freeze on frame one (asBitmap decodes only the first frame).
      Glide.with(this).asBitmap().load(R.drawable.serving_directory)
          .into(binding.servingDirectoryGif);
    }
  }

  // Simple terminal-green adapter for the embedded chat.
  private static class ChatMessageAdapter extends RecyclerView.Adapter<ChatMessageAdapter.VH> {

    private final List<String> data;

    ChatMessageAdapter(List<String> data) {
      this.data = data;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
      View v = LayoutInflater.from(parent.getContext())
          .inflate(android.R.layout.simple_list_item_1, parent, false);
      return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
      holder.text.setText(data.get(position));
      holder.text.setTextColor(0xFF00FF41);
      holder.text.setTypeface(android.graphics.Typeface.MONOSPACE);
      holder.text.setTextSize(13);
    }

    @Override
    public int getItemCount() {
      return data.size();
    }

    static class VH extends RecyclerView.ViewHolder {

      TextView text;

      VH(View itemView) {
        super(itemView);
        text = itemView.findViewById(android.R.id.text1);
      }
    }
  }
}