package com.xxx.server.log;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.xxx.server.R;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class LogAdapter extends RecyclerView.Adapter<LogAdapter.LogViewHolder> {

  private final List<LogEntry> logEntries = new ArrayList<>();
  private boolean autoScroll = true;

  @NonNull
  @Override
  public LogViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
    View view = LayoutInflater.from(parent.getContext())
        .inflate(R.layout.log_item_structured, parent, false);
    return new LogViewHolder(view);
  }

  @Override
  public void onBindViewHolder(@NonNull LogViewHolder holder, int position) {
    holder.bind(logEntries.get(position));
  }

  @Override
  public int getItemCount() {
    return logEntries.size();
  }

  public void addLogEntry(LogEntry entry) {
    logEntries.add(entry);
    notifyItemInserted(logEntries.size() - 1);
  }

  public void addLogMessage(String message) {
    // Parse format: [IP] METHOD [STATUS] path, time ms
    try {
      if (message.startsWith("[")) {
        int ipEnd = message.indexOf("]");
        String ip = message.substring(1, ipEnd);
        String rest = message.substring(ipEnd + 2);

        String[] parts = rest.split(" ", 3);
        String method = parts[0];
        String status = parts[1].replace("[", "").replace("]", "");
        String pathAndTime = parts[2];

        String[] pathParts = pathAndTime.split(", ");
        String path = pathParts[0];
        String time = pathParts.length > 1 ? pathParts[1] : "";

        LogEntry entry = new LogEntry(method + " " + path, ip,
            "Status: " + status + " (" + time + ")", "HTTP");
        addLogEntry(entry);
      } else {
        LogEntry entry = new LogEntry("EVENT", "SYSTEM", message, "HTTP");
        addLogEntry(entry);
      }
    } catch (Exception e) {
      LogEntry entry = new LogEntry("Unknown", "System", message, "HTTP");
      addLogEntry(entry);
    }
  }

  public boolean isAutoScroll() {
    return autoScroll;
  }

  public void setAutoScroll(boolean autoScroll) {
    this.autoScroll = autoScroll;
  }

  public static class LogEntry {

    public String timestamp;
    public String path;
    public String ip;
    public String data;
    public String type; // "HTTP" or "WebSocket"

    public LogEntry(String path, String ip, String data, String type) {
      SimpleDateFormat sdf = new SimpleDateFormat("h:mm:ss a M/d/yyyy", Locale.getDefault());
      this.timestamp = sdf.format(new Date());
      this.path = path;
      this.ip = ip;
      this.data = data;
      this.type = type;
    }
  }

  static class LogViewHolder extends RecyclerView.ViewHolder {

    private final TextView timestampTextView;
    private final TextView pathTextView;
    private final TextView ipTextView;
    private final TextView dataTextView;
    private final ImageView typeIcon;

    public LogViewHolder(@NonNull View itemView) {
      super(itemView);
      timestampTextView = itemView.findViewById(R.id.logTimestamp);
      pathTextView = itemView.findViewById(R.id.logPath);
      ipTextView = itemView.findViewById(R.id.logIp);
      dataTextView = itemView.findViewById(R.id.logData);
      typeIcon = itemView.findViewById(R.id.logTypeIcon);
    }

    public void bind(LogEntry entry) {
      timestampTextView.setText(entry.timestamp);
      pathTextView.setText(entry.path);
      ipTextView.setText(entry.ip);
      dataTextView.setText(entry.data);

      int color = 0xFF00FF41; // Terminal green default
      if (entry.data.contains("40") || entry.data.contains("50")) {
        color = 0xFFFF003C; // Neon red
      } else if (entry.data.contains("101")) {
        color = 0xFF00F3FF; // Neon blue
      }
      dataTextView.setTextColor(color);
      pathTextView.setTextColor(color);

      if ("WebSocket".equals(entry.type)) {
        typeIcon.setImageResource(R.drawable.ic_websocket_24);
        typeIcon.setColorFilter(0xFF00F3FF); // Neon blue
      } else {
        typeIcon.setImageResource(R.drawable.ic_server_notification);
        typeIcon.setColorFilter(0xFF00FF41); // Terminal green
      }
    }
  }
}