package com.xxx.server.web;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.xxx.server.R;

import java.util.ArrayList;
import java.util.List;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.WebSocket;

public class ChatFragment extends Fragment {

    private RecyclerView messagesRecyclerView;
    private EditText messageInput;
    private Button sendButton;
    private MessageAdapter messageAdapter;
    private final List<String> messages = new ArrayList<>();
    private WebSocket webSocket;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_chat, container, false);

        messagesRecyclerView = view.findViewById(R.id.messagesRecyclerView);
        messageInput = view.findViewById(R.id.messageInput);
        sendButton = view.findViewById(R.id.sendButton);

        messageAdapter = new MessageAdapter(messages);
        messagesRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        messagesRecyclerView.setAdapter(messageAdapter);

        connectWebSocket();

        sendButton.setOnClickListener(v -> {
            String message = messageInput.getText().toString();
            if (!message.isEmpty() && webSocket != null) {
                webSocket.writeTextMessage(message);
                messageInput.setText("");
            }
        });

        return view;
    }

    private void connectWebSocket() {
        Vertx vertx = Vertx.vertx();
        HttpClient client = vertx.createHttpClient();

        client.webSocket(HttpServerManager.WEBSOCKET_PORT, "127.0.0.1", HttpServerManager.WEBSOCKET_PATH, res -> {
            if (res.succeeded()) {
                webSocket = res.result();
                webSocket.textMessageHandler(message -> {
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            messages.add(message);
                            messageAdapter.notifyItemInserted(messages.size() - 1);
                            messagesRecyclerView.scrollToPosition(messages.size() - 1);
                        });
                    }
                });
            } else {
                // Handle connection error
            }
        });
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (webSocket != null) {
            webSocket.close();
        }
    }
}