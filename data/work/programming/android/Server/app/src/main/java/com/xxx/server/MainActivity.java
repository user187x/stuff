package com.xxx.server;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.xxx.server.databinding.MainActivityBinding;
import com.xxx.server.log.LogViewerFragment;
import com.xxx.server.web.ChatFragment;
import com.xxx.server.web.HttpServerService;
import com.xxx.server.web.ServerControlFragment;

public class MainActivity extends AppCompatActivity {

    private HttpServerService httpServerService;
    private boolean isServiceBound = false;
    private MainActivityBinding binding;

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            HttpServerService.LocalBinder binder = (HttpServerService.LocalBinder) service;
            httpServerService = binder.getService();
            isServiceBound = true;
            // Pass a logging consumer to the service
            httpServerService.setLogger(logMessage -> {
                // Handle log message in UI, e.g., add to a log fragment
            });
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            isServiceBound = false;
        }
    };

    private final BroadcastReceiver logBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (HttpServerService.ACTION_LOG_BROADCAST.equals(intent.getAction())) {
                String logMessage = intent.getStringExtra(HttpServerService.EXTRA_LOG_MESSAGE);
                // Here, you would pass the log message to your LogViewerFragment
                // For now, we'll just toast it
                Toast.makeText(context, logMessage, Toast.LENGTH_SHORT).show();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = MainActivityBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.navView.setOnNavigationItemSelectedListener(this::onNavigationItemSelected);

        // Load the default fragment
        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.nav_host_fragment, new ServerControlFragment())
                    .commit();
        }

        Intent intent = new Intent(this, HttpServerService.class);
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onStart() {
        super.onStart();
        LocalBroadcastManager.getInstance(this).registerReceiver(logBroadcastReceiver, new IntentFilter(HttpServerService.ACTION_LOG_BROADCAST));
    }

    @Override
    protected void onStop() {
        super.onStop();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(logBroadcastReceiver);
    }

    @Override
    protected void onDestroy() {
        if (isServiceBound) {
            unbindService(serviceConnection);
            isServiceBound = false;
        }
        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            // Handle settings action
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private boolean onNavigationItemSelected(@NonNull MenuItem item) {
        Fragment selectedFragment = null;
        int itemId = item.getItemId();
        if (itemId == R.id.navigation_server) {
            selectedFragment = new ServerControlFragment();
        } else if (itemId == R.id.navigation_logs) {
            selectedFragment = new LogViewerFragment();
        } else if (itemId == R.id.navigation_chat) {
            selectedFragment = new ChatFragment();
        }
        // Add other fragment selections here

        if (selectedFragment != null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.nav_host_fragment, selectedFragment)
                    .commit();
            return true;
        }
        return false;
    }
}