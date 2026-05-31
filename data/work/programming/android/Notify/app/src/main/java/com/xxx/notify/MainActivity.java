package com.xxx.notify;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.Scope;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.api.services.gmail.GmailScopes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MainActivity extends AppCompatActivity {

  // --- View Declarations ---
  private LinearLayout settingsContainer;
  private TextView tvStatus;
  private Button btnSignIn, btnSignOut, btnSelectApps, btnSaveAndStart, btnStop;
  private TextInputEditText etEmail, etBatchSize, etMaxWaitTime;
  private SwitchMaterial swImmediate;
  private TextInputLayout layoutBatchSize, layoutWaitTime;

  // --- Logic and Data ---
  private GoogleSignInClient mGoogleSignInClient;
  private SharedPreferences prefs;
  private Set<String> selectedApps = new HashSet<>();
  public static final String PREFS_NAME = "NotificationPrefs";

  // ✅ FIX: Launcher for the new POST_NOTIFICATIONS permission request.
  private final ActivityResultLauncher<String> requestPermissionLauncher =
          registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
            if (isGranted) {
              // Permission is granted. Continue the action.
              Toast.makeText(this, "Notification permission granted.", Toast.LENGTH_SHORT).show();
              saveAndStartService();
            } else {
              // Explain to the user that the feature is unavailable because the
              // feature requires a permission that the user has denied.
              Toast.makeText(this, "Notification permission is required for the service to work.", Toast.LENGTH_LONG).show();
            }
          });

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main_oauth);

    // --- Find all views ---
    prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
    tvStatus = findViewById(R.id.tvStatus);
    settingsContainer = findViewById(R.id.settingsContainer);
    btnSignIn = findViewById(R.id.btnSignIn);
    btnSignOut = findViewById(R.id.btnSignOut);
    btnSelectApps = findViewById(R.id.btnSelectApps);
    btnSaveAndStart = findViewById(R.id.btnSaveAndStart);
    btnStop = findViewById(R.id.btnStop);
    etEmail = findViewById(R.id.etEmail);
    etBatchSize = findViewById(R.id.etBatchSize);
    etMaxWaitTime = findViewById(R.id.etMaxWaitTime);
    swImmediate = findViewById(R.id.swImmediate);
    layoutBatchSize = findViewById(R.id.layoutBatchSize);
    layoutWaitTime = findViewById(R.id.layoutWaitTime);

    // --- Configure Google Sign-In ---
    GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail().requestScopes(new Scope(GmailScopes.GMAIL_SEND)).build();
    mGoogleSignInClient = GoogleSignIn.getClient(this, gso);

    // --- Setup Listeners ---
    btnSignIn.setOnClickListener(v -> signIn());
    btnSignOut.setOnClickListener(v -> signOut());
    btnSelectApps.setOnClickListener(v -> showAppSelectionDialog());
    swImmediate.setOnCheckedChangeListener((buttonView, isChecked) -> toggleBatchingControls(!isChecked));
    btnSaveAndStart.setOnClickListener(v -> checkPermissionsAndStartService());
    btnStop.setOnClickListener(v -> stopService());
  }

  @Override
  protected void onStart() {
    super.onStart();
    updateUI();
  }

  // ✅ FIX: New method to handle the full permission-checking flow.
  private void checkPermissionsAndStartService() {
    // First, check for POST_NOTIFICATIONS permission
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
              PackageManager.PERMISSION_GRANTED) {
        // Permission is already granted, proceed
        saveAndStartService();
      } else {
        // Directly ask for the permission
        requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
      }
    } else {
      // No runtime permission needed for older Android versions
      saveAndStartService();
    }
  }

  private void saveAndStartService() {
    // Second, check for Notification Access permission
    if (!isNotificationServiceEnabled()) {
      new MaterialAlertDialogBuilder(this)
              .setTitle("Permission Required")
              .setMessage("Please enable Notification Access for this app to work.")
              .setPositiveButton("Go to Settings", (dialog, which) ->
                      startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)))
              .setNegativeButton("Cancel", null).show();
      return;
    }

    if (selectedApps.isEmpty()) {
      Toast.makeText(this, "Please select at least one app to monitor.", Toast.LENGTH_SHORT).show();
      return;
    }

    // Save all settings from the UI to SharedPreferences
    SharedPreferences.Editor editor = prefs.edit();
    editor.putString("email", etEmail.getText().toString());
    editor.putBoolean("immediate_send", swImmediate.isChecked());
    editor.putInt("batch_size", Integer.parseInt(etBatchSize.getText().toString()));
    editor.putInt("wait_time_seconds", Integer.parseInt(etMaxWaitTime.getText().toString()));
    editor.putStringSet("selected_apps", selectedApps);
    editor.apply();

    // Start the service
    startForegroundService(new Intent(this, NotificationMonitorService.class));
    Toast.makeText(this, "Monitoring Service Started", Toast.LENGTH_SHORT).show();
    updateUI();
  }

  // --- All other methods remain the same as the previous response ---

  private void updateUI() {
    GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(this);
    boolean serviceIsActive = prefs.getBoolean("is_service_active", false);

    if (account == null) {
      tvStatus.setText("Not Signed In");
      settingsContainer.setVisibility(View.GONE);
      btnSignIn.setVisibility(View.VISIBLE);
      btnSaveAndStart.setVisibility(View.GONE);
      btnStop.setVisibility(View.GONE);
    } else {
      tvStatus.setText("Signed in as:\n" + account.getEmail());
      settingsContainer.setVisibility(View.VISIBLE);
      btnSignIn.setVisibility(View.GONE);
      loadPreferences();

      if (serviceIsActive) {
        btnSaveAndStart.setVisibility(View.GONE);
        btnStop.setVisibility(View.VISIBLE);
      } else {
        btnSaveAndStart.setVisibility(View.VISIBLE);
        btnStop.setVisibility(View.GONE);
      }
    }
  }

  private void loadPreferences() {
    etEmail.setText(prefs.getString("email", ""));
    boolean isImmediate = prefs.getBoolean("immediate_send", false);
    swImmediate.setChecked(isImmediate);
    toggleBatchingControls(!isImmediate);
    etBatchSize.setText(String.valueOf(prefs.getInt("batch_size", 10)));
    etMaxWaitTime.setText(String.valueOf(prefs.getInt("wait_time_seconds", 3600)));
    selectedApps = new HashSet<>(prefs.getStringSet("selected_apps", new HashSet<>()));
  }

  private void stopService() {
    Intent stopIntent = new Intent(this, NotificationMonitorService.class);
    stopIntent.setAction("STOP_SERVICE");
    startService(stopIntent);
    Toast.makeText(this, "Monitoring Service Stopped", Toast.LENGTH_SHORT).show();
    updateUI();
  }

  private void signIn() {
    signInLauncher.launch(mGoogleSignInClient.getSignInIntent());
  }

  private void signOut() {
    mGoogleSignInClient.signOut().addOnCompleteListener(this, task -> {
      Toast.makeText(this, "Signed out.", Toast.LENGTH_SHORT).show();
      prefs.edit().remove("user_account_email").apply();
      if (prefs.getBoolean("is_service_active", false)) {
        stopService();
      }
      updateUI();
    });
  }

  private final ActivityResultLauncher<Intent> signInLauncher = registerForActivityResult(
          new ActivityResultContracts.StartActivityForResult(),
          result -> {
            if (result.getResultCode() == RESULT_OK) {
              try {
                GoogleSignInAccount account = GoogleSignIn.getSignedInAccountFromIntent(result.getData()).getResult(ApiException.class);
                prefs.edit().putString("user_account_email", account.getEmail()).apply();
                updateUI();
              } catch (ApiException e) {
                Log.w("SignIn", "signInResult:failed code=" + e.getStatusCode());
                Toast.makeText(this, "Sign-in failed.", Toast.LENGTH_SHORT).show();
              }
            }
          });

  private void showAppSelectionDialog() {
    PackageManager pm = getPackageManager();
    Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
    mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);

    List<ResolveInfo> launchableApps = pm.queryIntentActivities(mainIntent, 0);
    Collections.sort(launchableApps, new ResolveInfo.DisplayNameComparator(pm));

    final List<AppInfo> appList = new ArrayList<>();
    for (ResolveInfo info : launchableApps) {
      String packageName = info.activityInfo.packageName;
      String appName = info.loadLabel(pm).toString();
      Drawable icon = info.loadIcon(pm);
      boolean isChecked = selectedApps.contains(packageName);
      appList.add(new AppInfo(appName, packageName, icon, isChecked));
    }

    AppListAdapter adapter = new AppListAdapter(this, appList);
    long initialCount = appList.stream().filter(app -> app.isChecked).count();

    AlertDialog dialog = new MaterialAlertDialogBuilder(this)
            .setTitle("Select Apps to Monitor (" + initialCount + ")")
            .setAdapter(adapter, null)
            .setPositiveButton("OK", (d, which) -> {
              selectedApps.clear();
              for (AppInfo app : appList) {
                if (app.isChecked) {
                  selectedApps.add(app.packageName);
                }
              }
              prefs.edit().putStringSet("selected_apps", selectedApps).apply();
              Toast.makeText(MainActivity.this, selectedApps.size() + " apps selected.", Toast.LENGTH_SHORT).show();
            })
            .setNegativeButton("Cancel", null).create();

    ListView listView = dialog.getListView();
    listView.setOnItemClickListener((parent, view, position, id) -> {
      AppInfo appInfo = appList.get(position);
      appInfo.isChecked = !appInfo.isChecked;
      adapter.notifyDataSetChanged();
      long count = appList.stream().filter(app -> app.isChecked).count();
      dialog.setTitle("Select Apps to Monitor (" + count + ")");
    });
    dialog.show();
  }

  private void toggleBatchingControls(boolean show) {
    layoutBatchSize.setVisibility(show ? View.VISIBLE : View.GONE);
    layoutWaitTime.setVisibility(show ? View.VISIBLE : View.GONE);
  }

  private boolean isNotificationServiceEnabled() {
    Set<String> enabledListeners = NotificationManagerCompat.getEnabledListenerPackages(this);
    return enabledListeners.contains(getPackageName());
  }

  private static class AppInfo {
    String name, packageName;
    Drawable icon;
    boolean isChecked;
    AppInfo(String name, String packageName, Drawable icon, boolean isChecked) {
      this.name = name; this.packageName = packageName; this.icon = icon; this.isChecked = isChecked;
    }
  }

  private static class AppListAdapter extends ArrayAdapter<AppInfo> {
    AppListAdapter(Context context, List<AppInfo> apps) {
      super(context, R.layout.list_item_app, apps);
    }
    @NonNull @Override
    public View getView(int position, View convertView, @NonNull ViewGroup parent) {
      if (convertView == null) convertView = LayoutInflater.from(getContext()).inflate(R.layout.list_item_app, parent, false);
      AppInfo app = getItem(position);
      ((ImageView) convertView.findViewById(R.id.ivAppIcon)).setImageDrawable(app.icon);
      ((TextView) convertView.findViewById(R.id.tvAppName)).setText(app.name);
      (convertView.findViewById(R.id.ivSelectionOverlay)).setVisibility(app.isChecked ? View.VISIBLE : View.GONE);
      return convertView;
    }
  }
}