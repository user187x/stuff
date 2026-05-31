package com.xxx.server.web;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.xxx.server.R;

import java.util.ArrayList;
import java.util.List;

public class ServerControlFragment extends Fragment {

    private Button startServerButton, stopServerButton, startWebsocketButton;
    private TextView serverStatus;
    private CardView chatPanel;
    private RecyclerView messagesRecyclerView, peersRecyclerView;
    private EditText messageInput;
    private Button sendButton;
    private TextView typingIndicator;

    private MessageAdapter messageAdapter;
    private PeerAdapter peerAdapter;
    private final List<String> messages = new ArrayList<>();
    private final List<String> peers = new ArrayList<>();
    private final Gson gson = new Gson();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_server_control, container, false);

        startServerButton = view.findViewById(R.id.start_server_button);
        stopServerButton = view.findViewById(R.id.stop_server_button);
        startWebsocketButton = view.findViewById(R.id.start_websocket_button);
        serverStatus = view.findViewById(R.id.server_status);
        chatPanel = view.findViewById(R.id.chat_panel);
        messagesRecyclerView = view.findViewById(R.id.messages_recycler_view);
        peersRecyclerView = view.findViewById(R.id.peers_recycler_view);
        messageInput = view.findViewById(R.id.message_input);
        sendButton = view.findViewById(R.id.send_button);
        typingIndicator = view.findViewById(R.id.typing_indicator);

        setupRecyclerViews();

        startServerButton.setOnClickListener(v -> startServer());
        stopServerButton.setOnClickListener(v -> stopServer());
        startWebsocketButton.setOnClickListener(v -> toggleChatPanel());
        sendButton.setOnClickListener(v -> sendMessage());

        return view;
    }

    private void setupRecyclerViews() {
        messagesRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        messageAdapter = new MessageAdapter(messages);
        messagesRecyclerView.setAdapter(messageAdapter);

        peersRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        peerAdapter = new PeerAdapter(peers);
        peersRecyclerView.setAdapter(peerAdapter);
    }

    private void startServer() {
        // Implement your server start logic here
        serverStatus.setText("Running");
    }

    private void stopServer() {
        // Implement your server stop logic here
        serverStatus.setText("Stopped");
        if (chatPanel.getVisibility() == View.VISIBLE) {
            toggleChatPanel();
        }
    }

    private void toggleChatPanel() {
        if (chatPanel.getVisibility() == View.GONE) {
            chatPanel.setVisibility(View.VISIBLE);
            chatPanel.setAlpha(0.0f);
            chatPanel.animate()
                    .translationY(0)
                    .alpha(1.0f)
                    .setListener(null);
        } else {
            chatPanel.animate()
                    .translationY(-chatPanel.getHeight())
                    .alpha(0.0f)
                    .setListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            super.onAnimationEnd(animation);
                            chatPanel.setVisibility(View.GONE);
                        }
                    });
        }
    }

    private void sendMessage() {
        String messageText = messageInput.getText().toString().trim();
        if (!messageText.isEmpty()) {
            JsonObject message = new JsonObject();
            message.addProperty("type", "chat");
            message.addProperty("user", "Server");
            message.addProperty("message", messageText);

            // This is where you would call your HttpServerManager to broadcast the message
            // httpServerManager.broadcastMessage(gson.toJson(message), null);

            addMessage("Server: " + messageText);
            messageInput.setText("");
        }
    }

    private void addMessage(String message) {
        requireActivity().runOnUiThread(() -> {
            messages.add(message);
            messageAdapter.notifyItemInserted(messages.size() - 1);
            messagesRecyclerView.scrollToPosition(messages.size() - 1);
        });
    }

    // Dummy adapters for RecyclerViews, you should customize them
    private static class MessageAdapter extends RecyclerView.Adapter<MessageAdapter.ViewHolder> {
        private final List<String> messages;

        MessageAdapter(List<String> messages) {
            this.messages = messages;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            TextView textView = new TextView(parent.getContext());
            return new ViewHolder(textView);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            holder.textView.setText(messages.get(position));
        }

        @Override
        public int getItemCount() {
            return messages.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView textView;

            ViewHolder(View itemView) {
                super(itemView);
                textView = (TextView) itemView;
            }
        }
    }

    private static class PeerAdapter extends RecyclerView.Adapter<PeerAdapter.ViewHolder> {
        private final List<String> peers;

        PeerAdapter(List<String> peers) {
            this.peers = peers;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            TextView textView = new TextView(parent.getContext());
            return new ViewHolder(textView);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            holder.textView.setText(peers.get(position));
        }

        @Override
        public int getItemCount() {
            return peers.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView textView;

            ViewHolder(View itemView) {
                super(itemView);
                textView = (TextView) itemView;
            }
        }
    }
}