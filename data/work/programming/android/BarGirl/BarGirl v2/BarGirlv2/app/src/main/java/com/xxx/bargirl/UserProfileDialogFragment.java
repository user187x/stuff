package com.xxx.bargirl;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

public class UserProfileDialogFragment extends DialogFragment {

    private EditText userNameEditText, userEmailEditText;

    // Constants for persistence, matching MainActivity
    private static final String PREFS_NAME = "BarGirlPrefs";
    private static final String PREF_USER_NAME = "userName";
    private static final String PREF_USER_EMAIL = "userEmail";

    @Override
    public void onStart() {
        super.onStart();
        // MODIFIED: This forces the dialog to match the screen width
        if (getDialog() != null && getDialog().getWindow() != null) {
            getDialog().getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.dialog_user_profile, container, false);

        if (getDialog() != null && getDialog().getWindow() != null) {
            getDialog().getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        userNameEditText = view.findViewById(R.id.user_name_edit_text);
        userEmailEditText = view.findViewById(R.id.user_email_edit_text);
        Button saveButton = view.findViewById(R.id.user_profile_save_button);

        loadUserProfile();

        saveButton.setOnClickListener(v -> {
            saveUserProfile();
            dismiss();
        });

        return view;
    }

    private void loadUserProfile() {
        SharedPreferences prefs = requireActivity().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String name = prefs.getString(PREF_USER_NAME, "");
        String email = prefs.getString(PREF_USER_EMAIL, "");

        userNameEditText.setText(name);
        userEmailEditText.setText(email);
    }

    private void saveUserProfile() {
        String name = userNameEditText.getText().toString().trim();
        String email = userEmailEditText.getText().toString().trim();

        SharedPreferences prefs = requireActivity().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(PREF_USER_NAME, name);
        editor.putString(PREF_USER_EMAIL, email);
        editor.apply();

        Toast.makeText(getContext(), "Profile Saved", Toast.LENGTH_SHORT).show();
    }
}