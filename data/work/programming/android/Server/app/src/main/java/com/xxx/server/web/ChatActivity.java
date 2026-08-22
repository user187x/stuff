package com.xxx.server.web;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.xxx.server.R;
import com.xxx.server.databinding.ActivityChatBinding;

import java.util.ArrayList;
import java.util.List;

public class ChatActivity extends AppCompatActivity {

    private ActivityChatBinding binding;
    private ChatMessageAdapter adapter;
    private final List<String> messages = new ArrayList<>();

    private final BroadcastReceiver chatReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if ("com.xxx.server.CHAT_MESSAGE".equals(intent.getAction())) {
                String sender = intent.getStringExtra("sender");
                String message = intent.getStringExtra("message");
                if (sender != null && message != null) {
                    addMessage("[" + sender + "]: " + message);
                }
            } else if ("com.xxx.server.CHAT_EVENT".equals(intent.getAction())) {
                String type = intent.getStringExtra("type");
                String sender = intent.getStringExtra("sender");
                String data = intent.getStringExtra("data");
                if ("TYPING".equals(type)) {
                    handleTypingIndicator(sender, "START".equals(data));
                }
            }
        }
    };

    private void handleTypingIndicator(String sender, boolean isTyping) {
        runOnUiThread(() -> {
            if (isTyping && !"SERVER".equals(sender)) {
                binding.typingIndicator.setText("(" + sender + " is typing...)");
                binding.typingIndicator.setVisibility(View.VISIBLE);
                startTypingAnimation();
            } else {
                binding.typingIndicator.setVisibility(View.INVISIBLE);
            }
        });
    }

    private int dotCount = 0;
    private android.os.Handler animationHandler = new android.os.Handler();
    private Runnable animationRunnable = new Runnable() {
        @Override
        public void run() {
            String text = binding.typingIndicator.getText().toString();
            if (text.contains("typing")) {
                dotCount = (dotCount + 1) % 4;
                String base = text.split("\\.")[0].replace(")", "");
                StringBuilder dots = new StringBuilder();
                for (int i = 0; i < dotCount; i++) dots.append(".");
                binding.typingIndicator.setText(base + dots.toString() + ")");
                animationHandler.postDelayed(this, 500);
            }
        }
    };

    private void startTypingAnimation() {
        animationHandler.removeCallbacks(animationRunnable);
        animationHandler.post(animationRunnable);
    }

    private String serverName = "SERVER";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityChatBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        adapter = new ChatMessageAdapter(messages);
        binding.chatRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        binding.chatRecyclerView.setAdapter(adapter);

        binding.chatSendButton.setOnClickListener(v -> sendMessage());
        
        setupNameChangeListener();
        setupTypingListener();

        IntentFilter filter = new IntentFilter();
        filter.addAction("com.xxx.server.CHAT_MESSAGE");
        filter.addAction("com.xxx.server.CHAT_EVENT");
        LocalBroadcastManager.getInstance(this).registerReceiver(chatReceiver, filter);
    }

    private void setupNameChangeListener() {
        binding.chatTitleText.setOnClickListener(new View.OnClickListener() {
            private long lastClickTime = 0;
            @Override
            public void onClick(View v) {
                long currentTime = System.currentTimeMillis();
                if (currentTime - lastClickTime < 300) {
                    showNameChangeDialog();
                }
                lastClickTime = currentTime;
            }
        });
    }

    private void showNameChangeDialog() {
        android.widget.EditText input = new android.widget.EditText(this);
        input.setText(serverName);
        input.setSingleLine(true);
        input.setFilters(new android.text.InputFilter[] { new android.text.InputFilter.LengthFilter(15) });

        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Update Name")
                .setView(input)
                .setPositiveButton("OK", (dialog, which) -> {
                    String newName = input.getText().toString().trim();
                    if (!newName.isEmpty() && !newName.equals(serverName)) {
                        Intent intent = new Intent(this, HttpServerService.class);
                        intent.setAction("send_chat_event");
                        intent.putExtra("type", "NAME_CHANGE");
                        intent.putExtra("data", newName);
                        startService(intent);
                        
                        serverName = newName;
                        binding.chatTitleText.setText(serverName);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void setupTypingListener() {
        binding.chatInput.addTextChangedListener(new android.text.TextWatcher() {
            private android.os.Handler handler = new android.os.Handler();
            private Runnable typingTimeout = () -> sendTypingSignal(false);
            private boolean isTyping = false;

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (!isTyping) {
                    isTyping = true;
                    sendTypingSignal(true);
                }
                handler.removeCallbacks(typingTimeout);
                handler.postDelayed(typingTimeout, 2000);
            }

            @Override
            public void afterTextChanged(android.text.Editable s) {}

            private void sendTypingSignal(boolean start) {
                if (!start) isTyping = false;
                Intent intent = new Intent(ChatActivity.this, HttpServerService.class);
                intent.setAction("send_chat_event");
                intent.putExtra("type", "TYPING");
                intent.putExtra("data", start ? "START" : "STOP");
                startService(intent);
            }
        });
    }

    private void sendMessage() {
        String msg = binding.chatInput.getText().toString().trim();
        if (!msg.isEmpty()) {
            // Find the service to broadcast
            Intent intent = new Intent(this, HttpServerService.class);
            intent.setAction("send_chat_message");
            intent.putExtra("message", msg);
            startService(intent);
            
            binding.chatInput.setText("");
        }
    }

    private void addMessage(String text) {
        runOnUiThread(() -> {
            messages.add(text);
            adapter.notifyItemInserted(messages.size() - 1);
            binding.chatRecyclerView.scrollToPosition(messages.size() - 1);
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(chatReceiver);
    }

    private static class ChatMessageAdapter extends RecyclerView.Adapter<ChatMessageAdapter.ViewHolder> {
        private final List<String> messages;

        ChatMessageAdapter(List<String> messages) {
            this.messages = messages;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(android.R.layout.simple_list_item_1, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            holder.text.setText(messages.get(position));
            holder.text.setTextColor(0xFF00FF41); // Terminal Green
            holder.text.setTypeface(android.graphics.Typeface.MONOSPACE);
            holder.text.setTextSize(13);
        }

        @Override
        public int getItemCount() {
            return messages.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView text;
            ViewHolder(View itemView) {
                super(itemView);
                text = itemView.findViewById(android.R.id.text1);
            }
        }
    }
}
