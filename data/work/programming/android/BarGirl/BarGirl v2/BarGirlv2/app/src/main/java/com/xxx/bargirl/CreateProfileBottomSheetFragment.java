package com.xxx.bargirl;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.card.MaterialCardView;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class CreateProfileBottomSheetFragment extends BottomSheetDialogFragment {

    public interface ProfileCreationListener {
        void onProfileCreated(Profile newProfile);
    }

    private ProfileCreationListener listener;
    private ImageView newProfileImageView;
    private EditText nameEditText, ageEditText;
    private ActivityResultLauncher<Intent> imagePickerLauncher;
    private Uri selectedImageUri;
    private Uri cameraImageUri;

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof ProfileCreationListener) {
            listener = (ProfileCreationListener) context;
        } else {
            throw new RuntimeException(context.toString() + " must implement ProfileCreationListener");
        }

        imagePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK) {
                        Uri uri = null;
                        if (result.getData() != null && result.getData().getData() != null) {
                            uri = result.getData().getData();
                            final int takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION;
                            try {
                                requireContext().getContentResolver().takePersistableUriPermission(uri, takeFlags);
                            } catch (SecurityException e) {
                                Log.e("BottomSheet", "Failed to take persistable permission", e);
                            }
                        } else {
                            uri = cameraImageUri;
                        }

                        if (uri != null) {
                            selectedImageUri = uri;
                            newProfileImageView.setImageURI(selectedImageUri);
                        }
                    }
                }
        );
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.bottom_sheet_create_profile, container, false);

        newProfileImageView = view.findViewById(R.id.new_profile_image);
        nameEditText = view.findViewById(R.id.name_edit_text);
        ageEditText = view.findViewById(R.id.age_edit_text);
        Button saveButton = view.findViewById(R.id.save_button);
        MaterialCardView cameraIconContainer = view.findViewById(R.id.new_camera_icon_container);

        cameraIconContainer.setOnClickListener(v -> showImagePickerDialog());

        saveButton.setOnClickListener(v -> {
            String name = nameEditText.getText().toString().trim();
            String age = ageEditText.getText().toString().trim();

            if (name.isEmpty() || age.isEmpty()) {
                Toast.makeText(getContext(), "Please fill out all fields", Toast.LENGTH_SHORT).show();
                return;
            }

            String ageText = "Age: " + age;
            Profile newProfile = new Profile(name, ageText, R.drawable.ic_profile_placeholder);

            if (selectedImageUri != null) {
                newProfile.setImageUriString(selectedImageUri.toString());
            }

            listener.onProfileCreated(newProfile);
            dismiss();
        });

        return view;
    }

    // MODIFIED: This method now shows a dialog to give the user a choice
    private void showImagePickerDialog() {
        final CharSequence[] options = {"Take Photo", "Choose from Gallery", "Cancel"};
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle("Select an Option");

        builder.setItems(options, (dialog, item) -> {
            if (options[item].equals("Take Photo")) {
                Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                File photoFile = null;
                try {
                    photoFile = createImageFile();
                } catch (IOException ex) {
                    Log.e("BottomSheet", "Error occurred while creating the file", ex);
                }

                if (photoFile != null) {
                    cameraImageUri = FileProvider.getUriForFile(requireContext(),
                            requireContext().getPackageName() + ".provider",
                            photoFile);
                    cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, cameraImageUri);
                    imagePickerLauncher.launch(cameraIntent);
                }
            } else if (options[item].equals("Choose from Gallery")) {
                Intent galleryIntent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                galleryIntent.addCategory(Intent.CATEGORY_OPENABLE);
                galleryIntent.setType("image/*");
                imagePickerLauncher.launch(galleryIntent);
            } else if (options[item].equals("Cancel")) {
                dialog.dismiss();
            }
        });
        builder.show();
    }

    private File createImageFile() throws IOException {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storageDir = requireContext().getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        return File.createTempFile(imageFileName, ".jpg", storageDir);
    }
}