package com.xxx.server.log;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.xxx.server.R;
import com.xxx.server.web.HttpServerService;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LiveLogActivity extends AppCompatActivity {

  private static final String TAG = "LiveLogActivity";
  private static final int MAX_LOG_LINES = 1000;

  private TextView logTextView;
  private ScrollView scrollView;
  private FloatingActionButton fabDownload;
  private FloatingActionButton fabSearch;
  private FloatingActionButton fabPause;
  private FloatingActionButton fabDelete;

  private final List<String> logLines = new ArrayList<>();
  private boolean isAutoScrollEnabled = true;
  private boolean isPaused = false;
  private final Handler uiHandler = new Handler(Looper.getMainLooper());
  private SharedPreferences preferences;

  // Color constants for terminal-like appearance
  private int colorWhite;
  private int colorGreen;
  private int colorBlue;
  private int colorYellow;
  private int colorRed;
  private int colorGray;
  private final BroadcastReceiver logBroadcastReceiver = new BroadcastReceiver() {
    @Override
    public void onReceive(Context context, Intent intent) {
      if (isPaused) {
        return;
      }

      String message = intent.getStringExtra(HttpServerService.EXTRA_LOG_MESSAGE);
      if (message != null) {
        addLogEntry(message);
      }
    }
  };

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_live_log);

    initializeColors();
    initializeViews();
    setupFloatingActionButtons();

    preferences = getSharedPreferences("HttpServerPrefs", MODE_PRIVATE);

    // Register for log broadcasts
    LocalBroadcastManager.getInstance(this).registerReceiver(
        logBroadcastReceiver,
        new IntentFilter(HttpServerService.ACTION_LOG_BROADCAST)
    );

    // Add initial welcome message
    addWelcomeMessage();
  }

  private void initializeColors() {
    colorWhite = ContextCompat.getColor(this, android.R.color.white);
    colorGreen = ContextCompat.getColor(this, R.color.glow_green);
    colorBlue = ContextCompat.getColor(this, R.color.primary_dark);
    colorYellow = ContextCompat.getColor(this, android.R.color.holo_orange_light);
    colorRed = ContextCompat.getColor(this, android.R.color.holo_red_light);
    colorGray = ContextCompat.getColor(this, R.color.secondary_dark);
  }

  private void initializeViews() {
    logTextView = findViewById(R.id.logTextView);
    scrollView = findViewById(R.id.logScrollView);

    // Set monospace font and terminal-like appearance
    logTextView.setTypeface(Typeface.MONOSPACE);
    logTextView.setTextSize(12);
    logTextView.setBackgroundColor(ContextCompat.getColor(this, R.color.background_dark));

    // Setup action bar
    if (getSupportActionBar() != null) {
      getSupportActionBar().setTitle("Live Server Logs");
      getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    }
  }

  private void setupFloatingActionButtons() {
    fabDownload = findViewById(R.id.fabDownload);
    fabSearch = findViewById(R.id.fabSearch);
    fabPause = findViewById(R.id.fabPause);
    fabDelete = findViewById(R.id.fabDelete);

    fabDownload.setOnClickListener(v -> downloadLogs());
    fabSearch.setOnClickListener(v -> searchLogs());
    fabPause.setOnClickListener(v -> togglePause());
    fabDelete.setOnClickListener(v -> clearLogs());

    // Setup toolbar
    androidx.appcompat.widget.Toolbar toolbar = findViewById(R.id.toolbar);
    setSupportActionBar(toolbar);
  }

  private void addWelcomeMessage() {
    String timestamp = getCurrentTimestamp();
    String serverStatus = HttpServerService.isServerRunning ? "RUNNING" : "STOPPED";
    int port = preferences.getInt("port", 8080);

    SpannableStringBuilder welcomeText = new SpannableStringBuilder();

    // Add timestamp
    SpannableString timeSpan = new SpannableString(timestamp + " ");
    timeSpan.setSpan(new ForegroundColorSpan(colorGray), 0, timeSpan.length(),
        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    welcomeText.append(timeSpan);

    // Add server status
    SpannableString statusSpan = new SpannableString("Server status: " + serverStatus);
    statusSpan.setSpan(new ForegroundColorSpan(
        HttpServerService.isServerRunning ? colorGreen : colorRed
    ), 0, statusSpan.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    statusSpan.setSpan(new StyleSpan(Typeface.BOLD), 0, statusSpan.length(),
        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    welcomeText.append(statusSpan);

    if (HttpServerService.isServerRunning) {
      welcomeText.append("\n");
      SpannableString portSpan = new SpannableString(
          timestamp + " Server listening on port " + port);
      portSpan.setSpan(new ForegroundColorSpan(colorGray), 0, timestamp.length(),
          Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
      portSpan.setSpan(new ForegroundColorSpan(colorBlue), timestamp.length(), portSpan.length(),
          Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
      welcomeText.append(portSpan);
    }

    logTextView.setText(welcomeText);
    logLines.add(welcomeText.toString());
  }

  private void addLogEntry(String message) {
    uiHandler.post(() -> {
      LiveLogEntry logEntry = new LiveLogEntry(message);
      String fullLogLine = logEntry.timestamp + " " + message;

      // Parse and colorize the log entry
      SpannableStringBuilder formattedLine = formatLogEntry(fullLogLine, message);

      // Add to log lines
      logLines.add(fullLogLine);

      // Limit log lines to prevent memory issues
      if (logLines.size() > MAX_LOG_LINES) {
        logLines.remove(0);
        // Rebuild entire text view when we hit the limit
        rebuildLogDisplay();
        return;
      }

      // Append to existing text
      SpannableStringBuilder currentText = new SpannableStringBuilder(logTextView.getText());
      if (currentText.length() > 0) {
        currentText.append("\n");
      }
      currentText.append(formattedLine);

      logTextView.setText(currentText);

      // Auto-scroll to bottom if enabled
      if (isAutoScrollEnabled) {
        scrollView.post(() -> scrollView.fullScroll(View.FOCUS_DOWN));
      }
    });
  }

  private SpannableStringBuilder formatLogEntry(String fullLogLine, String message) {
    SpannableStringBuilder formatted = new SpannableStringBuilder();
    String timestamp = getCurrentTimestamp();

    // Add timestamp in gray
    SpannableString timeSpan = new SpannableString(timestamp + " ");
    timeSpan.setSpan(new ForegroundColorSpan(colorGray), 0, timeSpan.length(),
        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    formatted.append(timeSpan);

    // Parse HTTP request logs
    if (message.contains("GET") || message.contains("POST") || message.contains("PUT")
        || message.contains("DELETE")) {
      formatted.append(formatHttpRequest(message));
    } else if (message.toLowerCase().contains("error") || message.toLowerCase()
        .contains("failed")) {
      // Error messages in red
      SpannableString errorSpan = new SpannableString(message);
      errorSpan.setSpan(new ForegroundColorSpan(colorRed), 0, errorSpan.length(),
          Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
      formatted.append(errorSpan);
    } else if (message.toLowerCase().contains("started") || message.toLowerCase()
        .contains("success")) {
      // Success messages in green
      SpannableString successSpan = new SpannableString(message);
      successSpan.setSpan(new ForegroundColorSpan(colorGreen), 0, successSpan.length(),
          Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
      formatted.append(successSpan);
    } else {
      // Regular messages in white
      SpannableString regularSpan = new SpannableString(message);
      regularSpan.setSpan(new ForegroundColorSpan(colorWhite), 0, regularSpan.length(),
          Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
      formatted.append(regularSpan);
    }

    return formatted;
  }

  private SpannableStringBuilder formatHttpRequest(String message) {
    SpannableStringBuilder formatted = new SpannableStringBuilder();

    // Pattern to match HTTP request logs: [IP] METHOD [STATUS] path, response_time ms
    Pattern pattern = Pattern.compile(
        "\\[([^\\]]+)\\]\\s+(GET|POST|PUT|DELETE)\\s+\\[([^\\]]+)\\]\\s*([^,]+),?\\s*(\\d+)\\s*ms");
    Matcher matcher = pattern.matcher(message);

    if (matcher.find()) {
      String ip = matcher.group(1);
      String method = matcher.group(2);
      String status = matcher.group(3);
      String path = matcher.group(4);
      String responseTime = matcher.group(5);

      // IP in blue
      SpannableString ipSpan = new SpannableString("[" + ip + "] ");
      ipSpan.setSpan(new ForegroundColorSpan(colorBlue), 0, ipSpan.length(),
          Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
      formatted.append(ipSpan);

      // Method in yellow, bold
      SpannableString methodSpan = new SpannableString(method + " ");
      methodSpan.setSpan(new ForegroundColorSpan(colorYellow), 0, methodSpan.length(),
          Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
      methodSpan.setSpan(new StyleSpan(Typeface.BOLD), 0, methodSpan.length(),
          Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
      formatted.append(methodSpan);

      // Status code in appropriate color
      int statusColor = getStatusCodeColor(status);
      SpannableString statusSpan = new SpannableString("[" + status + "] ");
      statusSpan.setSpan(new ForegroundColorSpan(statusColor), 0, statusSpan.length(),
          Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
      statusSpan.setSpan(new StyleSpan(Typeface.BOLD), 0, statusSpan.length(),
          Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
      formatted.append(statusSpan);

      // Path in white
      SpannableString pathSpan = new SpannableString(path.trim());
      pathSpan.setSpan(new ForegroundColorSpan(colorWhite), 0, pathSpan.length(),
          Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
      formatted.append(pathSpan);

      // Response time in gray
      SpannableString timeSpan = new SpannableString(", " + responseTime + " ms");
      timeSpan.setSpan(new ForegroundColorSpan(colorGray), 0, timeSpan.length(),
          Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
      formatted.append(timeSpan);
    } else {
      // If parsing fails, just show the message in white
      SpannableString fallbackSpan = new SpannableString(message);
      fallbackSpan.setSpan(new ForegroundColorSpan(colorWhite), 0, fallbackSpan.length(),
          Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
      formatted.append(fallbackSpan);
    }

    return formatted;
  }

  private int getStatusCodeColor(String status) {
    try {
      int code = Integer.parseInt(status);
      if (code >= 200 && code < 300) {
        return colorGreen;
      }
      if (code >= 300 && code < 400) {
        return colorYellow;
      }
      if (code >= 400 && code < 500) {
        return colorRed;
      }
      if (code >= 500) {
        return colorRed;
      }
    } catch (NumberFormatException e) {
      // Ignore
    }
    return colorWhite;
  }

  private void rebuildLogDisplay() {
    SpannableStringBuilder fullText = new SpannableStringBuilder();

    for (int i = 0; i < logLines.size(); i++) {
      String line = logLines.get(i);
      SpannableStringBuilder formattedLine = formatLogEntry(line,
          line.substring(line.indexOf(' ') + 1));

      if (i > 0) {
        fullText.append("\n");
      }
      fullText.append(formattedLine);
    }

    logTextView.setText(fullText);

    if (isAutoScrollEnabled) {
      scrollView.post(() -> scrollView.fullScroll(View.FOCUS_DOWN));
    }
  }

  private String getCurrentTimestamp() {
    SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
    return sdf.format(new Date());
  }

  private void downloadLogs() {
    // TODO: Implement log download functionality
    // Could export logs to a text file in external storage
  }

  private void searchLogs() {
    // TODO: Implement search functionality
    // Could open a search dialog to filter log entries
  }

  private void togglePause() {
    isPaused = !isPaused;
    fabPause.setImageResource(isPaused ? R.drawable.ic_play_arrow_24 : R.drawable.ic_pause_24);

    // Show status in action bar
    if (getSupportActionBar() != null) {
      String title = isPaused ? "Live Server Logs (Paused)" : "Live Server Logs";
      getSupportActionBar().setTitle(title);
    }
  }

  private void clearLogs() {
    logLines.clear();
    logTextView.setText("");
    addWelcomeMessage();
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    getMenuInflater().inflate(R.menu.live_log_menu, menu);
    return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    int id = item.getItemId();

    if (id == android.R.id.home) {
      finish();
      return true;
    } else if (id == R.id.action_auto_scroll) {
      isAutoScrollEnabled = !isAutoScrollEnabled;
      item.setChecked(isAutoScrollEnabled);
      return true;
    } else if (id == R.id.action_settings) {
      // Open log settings
      return true;
    }

    return super.onOptionsItemSelected(item);
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();
    LocalBroadcastManager.getInstance(this).unregisterReceiver(logBroadcastReceiver);
  }

  // Create custom log entry class for better structure
  public static class LiveLogEntry {

    public String timestamp;
    public String ip;
    public String method;
    public String status;
    public String path;
    public String responseTime;
    public String rawMessage;

    public LiveLogEntry(String message) {
      this.rawMessage = message;
      this.timestamp = getCurrentTimestamp();
      parseHttpMessage(message);
    }

    private static String getCurrentTimestamp() {
      SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
      return sdf.format(new Date());
    }

    private void parseHttpMessage(String message) {
      // Enhanced parsing for better log structure
      Pattern httpPattern = Pattern.compile("Server was started at ([^\\s]+) in '([^']+)'");
      Matcher httpMatcher = httpPattern.matcher(message);

      if (httpMatcher.find()) {
        this.ip = httpMatcher.group(1);
        this.path = httpMatcher.group(2);
        return;
      }

      // Parse typical HTTP request format: [IP] METHOD [STATUS] path, time ms
      Pattern reqPattern = Pattern.compile(
          "\\[([^\\]]+)\\]\\s+(GET|POST|PUT|DELETE)\\s+\\[([^\\]]+)\\]\\s*([^,]+),?\\s*(\\d+)\\s*ms");
      Matcher reqMatcher = reqPattern.matcher(message);

      if (reqMatcher.find()) {
        this.ip = reqMatcher.group(1);
        this.method = reqMatcher.group(2);
        this.status = reqMatcher.group(3);
        this.path = reqMatcher.group(4).trim();
        this.responseTime = reqMatcher.group(5);
      }
    }
  }
}