package com.xxx.bargirl;

import android.animation.Animator;
import android.animation.AnimatorInflater;
import android.animation.AnimatorListenerAdapter;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.MediaStore;
import android.transition.AutoTransition;
import android.transition.TransitionManager;
import android.util.Log;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.load.resource.gif.GifDrawable;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.android.material.navigation.NavigationView;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity implements
        CreateProfileBottomSheetFragment.ProfileCreationListener,
        NavigationView.OnNavigationItemSelectedListener,
        ProfileAdapter.OnProfileInteractionListener {

    // --- Constants for Persistence ---
    private static final String PREFS_NAME = "BarGirlPrefs";
    private static final String PREF_FAVORITE_PREFIX = "isFavorite_";
    private static final String PREF_IMAGE_URI_PREFIX = "imageUri_";
    private static final String PREF_DELETED_PREFIX = "isDeleted_";
    private static final String PREF_SMASHED_PREFIX = "isSmashed_";
    private static final String PREF_BODY_COUNT_PREFIX = "bodyCount_";
    private static final String PREF_NAME_PREFIX = "name_";
    private static final String PREF_AGE_PREFIX = "age_";
    private static final String PREF_LOCATION_PREFIX = "location_";
    private static final String PREF_BABIES_PREFIX = "babies_";
    private static final String PREF_BAR_FINE_PREFIX = "barFine_";
    private static final String PREF_USER_AVATAR_URI = "userAvatarUri";
    private static final String PREF_USER_NAME = "userName";
    private static final String PREF_USER_EMAIL = "userEmail";

    // --- Views ---
    private DrawerLayout drawerLayout;
    private NavigationView navigationView;
    private ImageButton menuIcon;
    private ShapeableImageView searchBarProfileImageView;
    private ShapeableImageView navHeaderAvatar;
    private TextView navHeaderUserName;
    private LinearLayout expandableLayout;
    private LinearLayout profileDetailsContainer;
    private ImageButton arrowButton;
    private MaterialCardView cardView;
    private RelativeLayout mainContentContainer;
    private LinearLayout cardFrontLayout;
    private LinearLayout cardBackLayout;
    private ImageView cardProfileImageView;
    private ImageView cardBackgroundImageView;
    private ImageView activeStatusGifView;
    private MaterialCardView cameraIconContainer;
    private ImageButton favoriteButton;
    private ImageButton shareButton;
    private ImageButton deleteButton;
    private FloatingActionButton fabAddProfile;
    private FloatingActionButton fabSwitchView;
    private TextView nameTextView;
    private TextView ageTextView;
    private TextView titleTextView;
    private MaterialButton smashedButton;
    private ImageView gifOverlayImageView;
    private RecyclerView profileRecyclerView;

    // --- State & Logic ---
    private boolean isFrontVisible = true;
    private boolean isListView = false;
    private boolean useHighFiveAnimation = true;
    private Animator outAnimator, inAnimator;
    private ActivityResultLauncher<Intent> cardImagePickerLauncher;
    private ActivityResultLauncher<Intent> userAvatarPickerLauncher;
    private GestureDetector gestureDetector;
    private final List<Profile> profileList = new ArrayList<>();
    private int currentProfileIndex = 0;
    private Uri cameraImageUri;
    private ProfileAdapter profileAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main_activity);

        findViews();
        initializeLaunchers();
        loadHeaderInfo();
        createProfileData();
        loadAllProfileStates();
        setupRecyclerView();
        setupListeners();
        loadAnimators();
        updateUIForCurrentProfile();
        setupNavigationDrawer();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadHeaderInfo();
    }

    private void findViews() {
        drawerLayout = findViewById(R.id.drawer_layout);
        navigationView = findViewById(R.id.navigation_view);
        menuIcon = findViewById(R.id.menu_icon);
        searchBarProfileImageView = findViewById(R.id.search_bar_profile_image);

        View headerView = navigationView.getHeaderView(0);
        if (headerView != null) {
            navHeaderAvatar = headerView.findViewById(R.id.nav_header_avatar);
            navHeaderUserName = headerView.findViewById(R.id.nav_header_user_name);
        }

        cardView = findViewById(R.id.card);
        expandableLayout = findViewById(R.id.expandable_layout);
        profileDetailsContainer = findViewById(R.id.profile_details_container);
        arrowButton = findViewById(R.id.arrow_button);
        mainContentContainer = findViewById(R.id.main_content_container);
        cardFrontLayout = findViewById(R.id.card_front_layout);
        cardBackLayout = findViewById(R.id.card_back_layout);
        cardProfileImageView = findViewById(R.id.card_profile_image);
        cardBackgroundImageView = findViewById(R.id.card_background_image);
        activeStatusGifView = findViewById(R.id.active_status_gif);
        cameraIconContainer = findViewById(R.id.camera_icon_container);
        favoriteButton = findViewById(R.id.favorite_icon);
        shareButton = findViewById(R.id.share_icon);
        deleteButton = findViewById(R.id.delete_button);
        fabAddProfile = findViewById(R.id.fab_add_profile);
        fabSwitchView = findViewById(R.id.fab_switch_view);
        nameTextView = findViewById(R.id.profile_name);
        ageTextView = findViewById(R.id.profile_age);
        titleTextView = findViewById(R.id.card_title);
        smashedButton = findViewById(R.id.smashed_button);
        gifOverlayImageView = findViewById(R.id.gif_overlay_image_view);
        profileRecyclerView = findViewById(R.id.profile_recycler_view);
    }

    private void setupRecyclerView() {
        profileAdapter = new ProfileAdapter(this, profileList);
        profileAdapter.setOnProfileInteractionListener(this);
        profileRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        profileRecyclerView.setAdapter(profileAdapter);
    }

    private void setupNavigationDrawer() {
        menuIcon.setOnClickListener(v -> {
            if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                drawerLayout.closeDrawer(GravityCompat.START);
            } else {
                drawerLayout.openDrawer(GravityCompat.START);
            }
        });

        navigationView.setNavigationItemSelectedListener(this);

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    drawerLayout.closeDrawer(GravityCompat.START);
                } else {
                    if (isEnabled()) {
                        setEnabled(false);
                        getOnBackPressedDispatcher().onBackPressed();
                    }
                }
            }
        });
    }

    private void createProfileData() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        if (profileList.isEmpty()) {
            if (!prefs.getBoolean(PREF_DELETED_PREFIX + "profile_1", false)) {
                profileList.add(new Profile("profile_1","Namthip Saevngvirod", "22", R.drawable.media, "Wrath Bar, Pattaya", true, 0, 2, 2500));
            }
            if (!prefs.getBoolean(PREF_DELETED_PREFIX + "profile_2", false)) {
                profileList.add(new Profile("profile_2", "Arthitaya Thatiporn", "24", R.drawable.media, "Smoke & Mirror Bar, Pattaya", false, 2, 1, 2500));
            }
            if (!prefs.getBoolean(PREF_DELETED_PREFIX + "profile_3", false)) {
                profileList.add(new Profile("profile_3", "Panatda Jirawan", "27", R.drawable.media, "Butterfly Nana Plaza, Bangkok", true, 0, 2, 3000));
            }
            if (!prefs.getBoolean(PREF_DELETED_PREFIX + "profile_4", false)) {
                profileList.add(new Profile("profile_4", "Nattaporn Marrapatti", "26", R.drawable.media, "XS GoGo, Pattaya", true, 1, 0, 3000));
            }
            if (!prefs.getBoolean(PREF_DELETED_PREFIX + "profile_5", false)) {
                profileList.add(new Profile("profile_5", "Zammi Bangkok", "26", R.drawable.media, "XS GoGo, Pattaya", true, 1, 0, 2500));
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setupListeners() {
        gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(MotionEvent e) { return true; }

            @Override
            public boolean onDoubleTap(MotionEvent e) {
                toggleCardExpansion();
                return true;
            }

            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                if (profileList.size() <= 1) return false;
                if (e1 == null || e2 == null) return false;
                float diffX = e2.getX() - e1.getX();
                if (Math.abs(diffX) > 100 && Math.abs(velocityX) > 100) {
                    if (diffX > 0) onSwipeRight(); else onSwipeLeft();
                    return true;
                }
                return false;
            }
        });

        cardView.setOnTouchListener((v, event) -> gestureDetector.onTouchEvent(event));
        arrowButton.setOnClickListener(v -> toggleCardExpansion());
        findViewById(R.id.title_layout).setOnClickListener(v -> toggleCardExpansion());
        ImageButton flipQrButton = findViewById(R.id.flip_qr_button);
        MaterialButton flipToFrontButton = findViewById(R.id.flip_to_front_button);
        flipQrButton.setOnClickListener(v -> flipCard());
        flipToFrontButton.setOnClickListener(v -> flipCard());
        cameraIconContainer.setOnClickListener(v -> openCardImagePicker());
        favoriteButton.setOnClickListener(v -> toggleFavorite());
        shareButton.setOnClickListener(v -> shareCard());
        deleteButton.setOnClickListener(v -> showDeleteConfirmationDialog(currentProfileIndex));
        fabAddProfile.setOnClickListener(v -> {
            CreateProfileBottomSheetFragment bottomSheet = new CreateProfileBottomSheetFragment();
            bottomSheet.show(getSupportFragmentManager(), bottomSheet.getTag());
        });
        fabSwitchView.setOnClickListener(v -> toggleViewMode());
        searchBarProfileImageView.setOnClickListener(v -> openUserAvatarPicker());
        if (navHeaderAvatar != null) {
            navHeaderAvatar.setOnClickListener(v -> openUserAvatarPicker());
        }
        smashedButton.setOnClickListener(v -> handleSmashedClick(currentProfileIndex));
    }

    private void toggleViewMode() {
        isListView = !isListView;
        if (isListView) {
            mainContentContainer.setVisibility(View.GONE);
            profileRecyclerView.setVisibility(View.VISIBLE);
            fabSwitchView.setImageResource(android.R.drawable.ic_menu_view);
        } else {
            mainContentContainer.setVisibility(View.VISIBLE);
            profileRecyclerView.setVisibility(View.GONE);
            fabSwitchView.setImageResource(android.R.drawable.ic_menu_sort_by_size);
            updateUIForCurrentProfile();
        }
    }

    private void updateUIForCurrentProfile() {
        if (profileList.isEmpty()) {
            cardView.setVisibility(View.GONE);
            return;
        }
        cardView.setVisibility(View.VISIBLE);
        if (isListView) return;

        if (currentProfileIndex >= profileList.size()) currentProfileIndex = profileList.size() - 1;
        if (currentProfileIndex < 0) {
            currentProfileIndex = 0;
            if (profileList.isEmpty()) {
                cardView.setVisibility(View.GONE);
                return;
            }
        }

        Profile currentProfile = profileList.get(currentProfileIndex);
        Glide.with(this).asGif().load(R.drawable.active_status).into(activeStatusGifView);
        nameTextView.setText(currentProfile.getName());
        ageTextView.setText(currentProfile.getAge());
        titleTextView.setText(currentProfile.getName());
        favoriteButton.setImageDrawable(ContextCompat.getDrawable(this, currentProfile.isFavorite() ? R.drawable.ic_favorite_filled : R.drawable.ic_favorite_border));
        updateSmashedButtonState(currentProfile);

        if (currentProfile.getImageUriString() != null) {
            try {
                Uri imageUri = Uri.parse(currentProfile.getImageUriString());
                cardProfileImageView.setImageURI(imageUri);
                cardBackgroundImageView.setImageURI(imageUri);
            } catch (Exception e) {
                cardProfileImageView.setImageResource(currentProfile.getDefaultImageResource());
                cardBackgroundImageView.setImageResource(currentProfile.getDefaultImageResource());
            }
        } else {
            cardProfileImageView.setImageResource(currentProfile.getDefaultImageResource());
            cardBackgroundImageView.setImageResource(currentProfile.getDefaultImageResource()); // <-- FIX: Reset the background
        }
        populateProfileDetails(currentProfile);
        if (expandableLayout.getVisibility() == View.VISIBLE) toggleCardExpansion();
        if (!isFrontVisible) flipCard();
    }

    private void populateProfileDetails(Profile profile) {
        profileDetailsContainer.removeAllViews();
        // Add long-click listeners to each editable row
        addInfoRow(profileDetailsContainer, R.drawable.ic_person, "Name", profile.getName(), null, v -> {
            showEditDialog(profile, "name", "Update Name", profile.getName());
            return true;
        });
        addInfoRow(profileDetailsContainer, R.drawable.ic_person, "Age", profile.getAge(), null, v -> {
            showEditDialog(profile, "age", "Update Age", profile.getAge());
            return true;
        });
        addInfoRow(profileDetailsContainer, R.drawable.ic_location, "Location", profile.getLocation(), v -> openMap(profile.getLocation()), v -> {
            showEditDialog(profile, "location", "Update Location", profile.getLocation());
            return true;
        });
        addInfoRow(profileDetailsContainer, R.drawable.ic_status, "Status", profile.isActive() ? "Active" : "Inactive", null, null); // Status is not editable
        addInfoRow(profileDetailsContainer, R.drawable.ic_body_count, "Body Count", String.valueOf(profile.getBodyCount()), null, v -> {
            showEditDialog(profile, "bodyCount", "Update Body Count", String.valueOf(profile.getBodyCount()));
            return true;
        });
        addInfoRow(profileDetailsContainer, R.drawable.ic_kids, "Babies", String.valueOf(profile.getBabies()), null, v-> {
            showEditDialog(profile, "babies", "Update Babies", String.valueOf(profile.getBabies()));
            return true;
        });
        addInfoRow(profileDetailsContainer, R.drawable.ic_bar_fine, "Bar Fine", "฿" + profile.getBarFine(), null, v -> {
            showEditDialog(profile, "barFine", "Update Bar Fine", String.valueOf(profile.getBarFine()));
            return true;
        });
    }

    // Handles the long-press callback from the adapter
    @Override
    public void onInfoRowLongClicked(int position, String key, String currentValue) {
        if (position >= 0 && position < profileList.size()) {
            Profile profile = profileList.get(position);
            showEditDialog(profile, key, "Update " + key, currentValue);
        }
    }

    // Shows the dialog to edit a value
    private void showEditDialog(final Profile profile, final String key, final String title, final String currentValue) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(title);

        // Set up the input
        final android.widget.EditText input = new android.widget.EditText(this);
        // Set input type based on the key
        switch (key) {
            case "age":
            case "bodyCount":
            case "babies":
            case "barFine":
                input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
                break;
            default:
                input.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
                break;
        }
        input.setText(currentValue.replace("฿", "")); // Remove currency symbol for editing
        builder.setView(input);

        // Set up the buttons
        builder.setPositiveButton("OK", (dialog, which) -> {
            String newValue = input.getText().toString();
            SharedPreferences.Editor editor = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit();

            try {
                switch (key) {
                    case "name":
                        profile.setName(newValue);
                        editor.putString(PREF_NAME_PREFIX + profile.getId(), newValue);
                        break;
                    case "age":
                        profile.setAge(newValue);
                        editor.putString(PREF_AGE_PREFIX + profile.getId(), newValue);
                        break;
                    case "location":
                        profile.setLocation(newValue);
                        editor.putString(PREF_LOCATION_PREFIX + profile.getId(), newValue);
                        break;
                    case "bodyCount":
                        int bodyCount = Integer.parseInt(newValue);
                        profile.setBodyCount(bodyCount);
                        editor.putInt(PREF_BODY_COUNT_PREFIX + profile.getId(), bodyCount);
                        break;
                    case "babies":
                        int babies = Integer.parseInt(newValue);
                        profile.setBabies(babies);
                        editor.putInt(PREF_BABIES_PREFIX + profile.getId(), babies);
                        break;
                    case "barFine":
                        int barFine = Integer.parseInt(newValue);
                        profile.setBarFine(barFine);
                        editor.putInt(PREF_BAR_FINE_PREFIX + profile.getId(), barFine);
                        break;
                }
                editor.apply();

                // Refresh the UI
                if (isListView) {
                    int index = profileList.indexOf(profile);
                    if (index != -1) {
                        profileAdapter.notifyItemChanged(index);
                    }
                } else {
                    updateUIForCurrentProfile();
                }
            } catch (NumberFormatException e) {
                Toast.makeText(this, "Invalid number format.", Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());

        builder.show();
    }

    private void addInfoRow(LinearLayout container, @DrawableRes int iconRes, String label, String value, View.OnClickListener clickListener, View.OnLongClickListener longClickListener) {
        LayoutInflater inflater = LayoutInflater.from(this);
        View rowView = inflater.inflate(R.layout.info_row_item, container, false);
        ImageView iconView = rowView.findViewById(R.id.info_icon);
        TextView labelView = rowView.findViewById(R.id.info_label);
        TextView valueView = rowView.findViewById(R.id.info_value);
        iconView.setImageResource(iconRes);
        labelView.setText(label);
        valueView.setText(value);
        if (clickListener != null) {
            rowView.setOnClickListener(clickListener);
        }
        if (longClickListener != null) {
            rowView.setOnLongClickListener(longClickListener);
        }
        container.addView(rowView);
    }

    private void showDeleteConfirmationDialog(int position) {
        if (profileList.isEmpty() || position >= profileList.size()) return;
        Profile profileToDelete = profileList.get(position);
        new AlertDialog.Builder(this)
                .setTitle("Delete Profile")
                .setMessage("Are you sure you want to delete " + profileToDelete.getName() + "?")
                .setPositiveButton("Delete", (dialog, which) -> deleteProfileAt(position))
                .setNegativeButton("Cancel", null)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .show();
    }

    private void deleteProfileAt(int position) {
        if (profileList.isEmpty() || position >= profileList.size()) return;
        Profile profileToDelete = profileList.get(position);
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.remove(PREF_FAVORITE_PREFIX + profileToDelete.getId());
        editor.remove(PREF_IMAGE_URI_PREFIX + profileToDelete.getId());
        editor.remove(PREF_SMASHED_PREFIX + profileToDelete.getId());
        editor.remove(PREF_BODY_COUNT_PREFIX + profileToDelete.getId());
        editor.putBoolean(PREF_DELETED_PREFIX + profileToDelete.getId(), true);
        editor.apply();
        profileList.remove(position);
        profileAdapter.notifyItemRemoved(position);
        profileAdapter.notifyItemRangeChanged(position, profileList.size());
        Toast.makeText(this, "Profile for " + profileToDelete.getName() + " deleted", Toast.LENGTH_SHORT).show();
        updateUIForCurrentProfile();
    }

    private void onSwipeLeft() {
        if (profileList.isEmpty()) return; // Guard against empty list

        if (currentProfileIndex < profileList.size() - 1) {
            // It's a normal swipe forward
            currentProfileIndex++;
            triggerFlickVibration();
        } else {
            // It's a wrap-around from the last card to the first
            currentProfileIndex = 0;
            triggerWrapAroundFeedback();
        }
        updateUIForCurrentProfile();
    }

    private void onSwipeRight() {
        if (profileList.isEmpty()) return; // Guard against empty list

        if (currentProfileIndex > 0) {
            // It's a normal swipe backward
            currentProfileIndex--;
            triggerFlickVibration();
        } else {
            // It's a wrap-around from the first card to the last
            currentProfileIndex = profileList.size() - 1;
            triggerWrapAroundFeedback();
        }
        updateUIForCurrentProfile();
    }

    private void triggerFlickVibration() {
        Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        v.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK));
    }

    private void triggerWrapAroundFeedback() {
        if (cardView != null) {
            Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            v.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE));
            Animation shake = AnimationUtils.loadAnimation(this, R.anim.shake);
            cardView.startAnimation(shake);
        }
    }

    private void loadAllProfileStates() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        for (Profile profile : profileList) {
            profile.setFavorite(prefs.getBoolean(PREF_FAVORITE_PREFIX + profile.getId(), false));
            profile.setImageUriString(prefs.getString(PREF_IMAGE_URI_PREFIX + profile.getId(), null));
            profile.setSmashed(prefs.getBoolean(PREF_SMASHED_PREFIX + profile.getId(), false));
            // Load persisted editable fields
            profile.setName(prefs.getString(PREF_NAME_PREFIX + profile.getId(), profile.getName()));
            profile.setAge(prefs.getString(PREF_AGE_PREFIX + profile.getId(), profile.getAge()));
            profile.setLocation(prefs.getString(PREF_LOCATION_PREFIX + profile.getId(), profile.getLocation()));
            profile.setBodyCount(prefs.getInt(PREF_BODY_COUNT_PREFIX + profile.getId(), profile.getBodyCount()));
            profile.setBabies(prefs.getInt(PREF_BABIES_PREFIX + profile.getId(), profile.getBabies()));
            profile.setBarFine(prefs.getInt(PREF_BAR_FINE_PREFIX + profile.getId(), profile.getBarFine()));
        }
    }

    private void loadHeaderInfo() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String avatarUriString = prefs.getString(PREF_USER_AVATAR_URI, null);
        if (avatarUriString != null) {
            try {
                Uri imageUri = Uri.parse(avatarUriString);
                if (searchBarProfileImageView != null) searchBarProfileImageView.setImageURI(imageUri);
                if (navHeaderAvatar != null) navHeaderAvatar.setImageURI(imageUri);
            } catch (Exception e) {
                if (searchBarProfileImageView != null) searchBarProfileImageView.setImageResource(R.drawable.ic_profile_placeholder);
                if (navHeaderAvatar != null) navHeaderAvatar.setImageResource(R.drawable.ic_profile_placeholder);
            }
        }
        String name = prefs.getString(PREF_USER_NAME, null);
        if (navHeaderUserName != null) {
            if (name != null && !name.trim().isEmpty()) {
                navHeaderUserName.setText(name);
                navHeaderUserName.setVisibility(View.VISIBLE);
            } else {
                navHeaderUserName.setVisibility(View.GONE);
            }
        }
    }

    private void saveSmashedState(Profile profile) {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean(PREF_SMASHED_PREFIX + profile.getId(), profile.isSmashed());
        editor.putInt(PREF_BODY_COUNT_PREFIX + profile.getId(), profile.getBodyCount());
        editor.apply();
    }

    private void toggleFavorite() {
        if (profileList.isEmpty()) return;
        Profile currentProfile = profileList.get(currentProfileIndex);
        currentProfile.setFavorite(!currentProfile.isFavorite());
        updateUIForCurrentProfile();
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(PREF_FAVORITE_PREFIX + currentProfile.getId(), currentProfile.isFavorite()).apply();
    }

    private void initializeLaunchers() {
        cardImagePickerLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == Activity.RESULT_OK) {
                Uri uri = (result.getData() != null && result.getData().getData() != null) ? result.getData().getData() : cameraImageUri;
                if (uri != null) {
                    try {
                        getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    } catch (SecurityException e) { Log.e("MainActivity", "Permissions failed", e); }
                    Profile currentProfile = profileList.get(currentProfileIndex);
                    currentProfile.setImageUriString(uri.toString()); // <-- FIX: Update the object in the list
                    cardProfileImageView.setImageURI(uri);
                    cardBackgroundImageView.setImageURI(uri);
                    SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
                    prefs.edit().putString(PREF_IMAGE_URI_PREFIX + currentProfile.getId(), uri.toString()).apply();
                }
            }
        });
        userAvatarPickerLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == Activity.RESULT_OK) {
                Uri uri = (result.getData() != null && result.getData().getData() != null) ? result.getData().getData() : cameraImageUri;
                if (uri != null) {
                    try {
                        getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    } catch (SecurityException e) { Log.e("MainActivity", "Permissions failed", e); }
                    if (searchBarProfileImageView != null) searchBarProfileImageView.setImageURI(uri);
                    if (navHeaderAvatar != null) navHeaderAvatar.setImageURI(uri);
                    SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
                    prefs.edit().putString(PREF_USER_AVATAR_URI, uri.toString()).apply();
                }
            }
        });
    }

    private void showImagePickerDialog(ActivityResultLauncher<Intent> launcher, String title) {
        final CharSequence[] options = {"Take Photo", "Choose from Gallery", "Cancel"};
        new AlertDialog.Builder(this).setTitle(title).setItems(options, (dialog, item) -> {
            if (options[item].equals("Take Photo")) {
                Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                try {
                    File photoFile = createImageFile();
                    cameraImageUri = FileProvider.getUriForFile(this, getPackageName() + ".provider", photoFile);
                    cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, cameraImageUri);
                    launcher.launch(cameraIntent);
                } catch (IOException ex) { Log.e("MainActivity", "File creation failed", ex); }
            } else if (options[item].equals("Choose from Gallery")) {
                Intent galleryIntent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                galleryIntent.addCategory(Intent.CATEGORY_OPENABLE);
                galleryIntent.setType("image/*");
                launcher.launch(galleryIntent);
            } else {
                dialog.dismiss();
            }
        }).show();
    }

    private void openCardImagePicker() {
        if (profileList.isEmpty()) return;
        showImagePickerDialog(cardImagePickerLauncher, "Select Profile Picture");
    }

    private void openUserAvatarPicker() {
        showImagePickerDialog(userAvatarPickerLauncher, "Select Your Avatar");
    }

    private File createImageFile() throws IOException {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        return File.createTempFile(imageFileName, ".jpg", storageDir);
    }

    private void shareCard() {
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_SUBJECT, "My Profile Card");
        shareIntent.putExtra(Intent.EXTRA_TEXT, "Check out this awesome profile card I made!");
        startActivity(Intent.createChooser(shareIntent, "Share using"));
    }

    private void loadAnimators() {
        outAnimator = AnimatorInflater.loadAnimator(getApplicationContext(), R.animator.card_flip_out);
        inAnimator = AnimatorInflater.loadAnimator(getApplicationContext(), R.animator.card_flip_in);
    }

    private void toggleCardExpansion() {
        if (!isFrontVisible) return;
        TransitionManager.beginDelayedTransition((ViewGroup) cardView.getParent(), new AutoTransition().setDuration(300));
        if (expandableLayout.getVisibility() == View.GONE) {
            expandableLayout.setVisibility(View.VISIBLE);
            arrowButton.animate().rotation(180f).setDuration(300).start();
            fabAddProfile.hide();
            fabSwitchView.hide();
        } else {
            expandableLayout.setVisibility(View.GONE);
            arrowButton.animate().rotation(0f).setDuration(300).start();
            fabAddProfile.show();
            fabSwitchView.show();
        }
    }

    private void flipCard() {
        if (expandableLayout.getVisibility() == View.VISIBLE) toggleCardExpansion();
        final View visibleView = isFrontVisible ? cardFrontLayout : cardBackLayout;
        final View invisibleView = isFrontVisible ? cardBackLayout : cardFrontLayout;
        outAnimator.setTarget(visibleView);
        outAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                visibleView.setVisibility(View.GONE);
                invisibleView.setVisibility(View.VISIBLE);
                inAnimator.setTarget(invisibleView);
                inAnimator.start();
                isFrontVisible = !isFrontVisible;
                outAnimator.removeListener(this);
            }
        });
        outAnimator.start();
    }

    private void playHighFiveGif() {
        if (gifOverlayImageView == null) return;
        int gifResource;
        long gifDuration;
        if (useHighFiveAnimation) {
            gifResource = R.drawable.high_five;
            gifDuration = 2500;
        } else {
            gifResource = R.drawable.fist_bump;
            gifDuration = 3500;
        }
        Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        v.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK));
        try {
            MediaPlayer mediaPlayer = MediaPlayer.create(this, R.raw.power_up);
            mediaPlayer.setOnCompletionListener(MediaPlayer::release);
            mediaPlayer.start();
        } catch (Exception e) { Log.e("MainActivity", "Sound error", e); }
        gifOverlayImageView.setElevation(getResources().getDisplayMetrics().density * 30);
        gifOverlayImageView.setVisibility(View.VISIBLE);
        Glide.with(this).asGif().load(gifResource).listener(new RequestListener<GifDrawable>() {
            @Override
            public boolean onLoadFailed(@Nullable GlideException e, @Nullable Object model, @NonNull Target<GifDrawable> target, boolean isFirstResource) {
                gifOverlayImageView.setVisibility(View.GONE);
                return false;
            }
            @Override
            public boolean onResourceReady(@NonNull GifDrawable resource, @NonNull Object model, @NonNull Target<GifDrawable> target, @NonNull DataSource dataSource, boolean isFirstResource) {
                resource.setLoopCount(1);
                return false;
            }
        }).into(gifOverlayImageView);
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            gifOverlayImageView.setVisibility(View.GONE);
            gifOverlayImageView.setElevation(0);
        }, gifDuration);
        useHighFiveAnimation = !useHighFiveAnimation;
    }

    @Override
    public void onProfileCreated(Profile newProfile) {
        profileList.add(newProfile);
        if (newProfile.getImageUriString() != null) {
            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            prefs.edit().putString(PREF_IMAGE_URI_PREFIX + newProfile.getId(), newProfile.getImageUriString()).apply();
        }
        profileAdapter.notifyItemInserted(profileList.size() - 1);
        currentProfileIndex = profileList.size() - 1;
        updateUIForCurrentProfile();
        Toast.makeText(this, "Profile for " + newProfile.getName() + " created!", Toast.LENGTH_SHORT).show();
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.nav_profile) {
            new UserProfileDialogFragment().show(getSupportFragmentManager(), "UserProfileDialog");
        } else if (itemId == R.id.nav_message) {
            Toast.makeText(this, "Message selected", Toast.LENGTH_SHORT).show();
        } else if (itemId == R.id.nav_notes) {
            Toast.makeText(this, "Notes selected", Toast.LENGTH_SHORT).show();
        } else if (itemId == R.id.nav_settings) {
            Toast.makeText(this, "Settings selected", Toast.LENGTH_SHORT).show();
        }
        drawerLayout.closeDrawer(GravityCompat.START);
        return true;
    }

    private void handleSmashedClick(int position) {
        if (position >= profileList.size() || position < 0) return;
        Profile profile = profileList.get(position);
        profile.setSmashed(!profile.isSmashed());
        if (profile.isSmashed()) {
            profile.incrementBodyCount();
            playHighFiveGif();
        } else {
            profile.decrementBodyCount();
        }
        saveSmashedState(profile);
        if (!isListView) {
            updateSmashedButtonState(profile);
            populateProfileDetails(profile);
        } else {
            profileAdapter.notifyItemChanged(position);
        }
    }

    private void updateSmashedButtonState(Profile profile) {
        smashedButton.setAlpha(profile.isSmashed() ? 0.5f : 1.0f);
    }

    // UPDATED: Method to open a map app chooser
    private void openMap(String location) {
        // Create a Uri from the location string.
        Uri gmmIntentUri = Uri.parse("geo:0,0?q=" + Uri.encode(location));

        // Create an Intent with the action to view the location.
        Intent mapIntent = new Intent(Intent.ACTION_VIEW, gmmIntentUri);

        // By not setting a specific package, the Android system will show a chooser
        // if multiple apps can handle the geo intent.
        if (mapIntent.resolveActivity(getPackageManager()) != null) {
            startActivity(mapIntent);
        } else {
            // Handle the case where no map application is installed
            Toast.makeText(this, "No map application found.", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onSmashedClicked(int position) {
        handleSmashedClick(position);
    }

    @Override
    public void onDeleteClicked(int position) {
        showDeleteConfirmationDialog(position);
    }

    @Override
    public void onLocationClicked(int position) {
        if (position >= 0 && position < profileList.size()) {
            openMap(profileList.get(position).getLocation());
        }
    }
}
