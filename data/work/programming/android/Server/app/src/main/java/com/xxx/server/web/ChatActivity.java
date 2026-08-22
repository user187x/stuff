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
            String sender = intent.getStringExtra("sender");
            String message = intent.getStringExtra("message");
            if (sender != null && message != null) {
                addMessage("[" + sender + "]: " + message);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityChatBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        adapter = new ChatMessageAdapter(messages);
        binding.chatRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        binding.chatRecyclerView.setAdapter(adapter);

        binding.chatSendButton.setOnClickListener(v -> sendMessage());

        LocalBroadcastManager.getInstance(this).registerReceiver(chatReceiver, new IntentFilter("com.xxx.server.CHAT_MESSAGE"));
    }

    private void sendMessage() {
        String msg = binding.chatInput.getText().toString().trim();
        if (!msg.isEmpty()) {
            // Find the service to broadcast
            Intent intent = new Intent(this, HttpServerService.class);
            intent.setAction("send_chat_message");
            intent.putExtra("message", msg);
            startService(intent);
            
            addMessage("[SERVER]: " + msg);
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
