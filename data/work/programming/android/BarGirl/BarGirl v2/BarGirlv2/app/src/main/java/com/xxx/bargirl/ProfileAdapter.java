package com.xxx.bargirl;

import android.animation.Animator;
import android.animation.AnimatorInflater;
import android.animation.AnimatorListenerAdapter;
import android.annotation.SuppressLint;
import android.content.Context;
import android.net.Uri;
import android.transition.AutoTransition;
import android.transition.TransitionManager;
import android.util.Log;
import android.util.SparseBooleanArray;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import java.util.List;

public class ProfileAdapter extends RecyclerView.Adapter<ProfileAdapter.ProfileViewHolder> {

    private final List<Profile> profileList;
    private final Context context;
    private OnProfileInteractionListener listener;

    // State trackers for each card's expanded/flipped status
    private final SparseBooleanArray expandedState = new SparseBooleanArray();
    private final SparseBooleanArray flippedState = new SparseBooleanArray();

    // Interface for click events
    // Interface for click events
    public interface OnProfileInteractionListener {
        void onSmashedClicked(int position);
        void onDeleteClicked(int position);
        void onLocationClicked(int position);
        void onInfoRowLongClicked(int position, String key, String currentValue);
    }

    public void setOnProfileInteractionListener(OnProfileInteractionListener listener) {
        this.listener = listener;
    }

    public ProfileAdapter(Context context, List<Profile> profileList) {
        this.context = context;
        this.profileList = profileList;
    }

    @NonNull
    @Override
    public ProfileViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_profile_card, parent, false);
        return new ProfileViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ProfileViewHolder holder, int position) {
        Profile profile = profileList.get(position);
        holder.bind(profile, position);
    }

    @Override
    public int getItemCount() {
        return profileList.size();
    }

    class ProfileViewHolder extends RecyclerView.ViewHolder {
        MaterialCardView cardView;
        LinearLayout cardFrontLayout, cardBackLayout, expandableLayout, profileDetailsContainer;
        RelativeLayout titleBarLayout;
        ImageView cardProfileImageView, cardBackgroundImageView, activeStatusGifView;
        ImageButton favoriteButton, shareButton, arrowButton, flipQrButton, deleteButton;
        MaterialButton flipToFrontButton, smashedButton;
        TextView nameTextView, ageTextView, titleTextView;
        Animator outAnimator, inAnimator;
        GestureDetector gestureDetector;

        public ProfileViewHolder(@NonNull View itemView) {
            super(itemView);
            cardView = itemView.findViewById(R.id.card);
            cardFrontLayout = itemView.findViewById(R.id.card_front_layout);
            cardBackLayout = itemView.findViewById(R.id.card_back_layout);
            expandableLayout = itemView.findViewById(R.id.expandable_layout);
            profileDetailsContainer = itemView.findViewById(R.id.profile_details_container);
            titleBarLayout = itemView.findViewById(R.id.title_bar_layout);
            cardProfileImageView = itemView.findViewById(R.id.card_profile_image);
            cardBackgroundImageView = itemView.findViewById(R.id.card_background_image);
            activeStatusGifView = itemView.findViewById(R.id.active_status_gif);
            favoriteButton = itemView.findViewById(R.id.favorite_icon);
            shareButton = itemView.findViewById(R.id.share_icon);
            arrowButton = itemView.findViewById(R.id.arrow_button);
            flipQrButton = itemView.findViewById(R.id.flip_qr_button);
            flipToFrontButton = itemView.findViewById(R.id.flip_to_front_button);
            deleteButton = itemView.findViewById(R.id.delete_button);
            smashedButton = itemView.findViewById(R.id.smashed_button);
            nameTextView = itemView.findViewById(R.id.profile_name);
            ageTextView = itemView.findViewById(R.id.profile_age);
            titleTextView = itemView.findViewById(R.id.card_title);
            outAnimator = AnimatorInflater.loadAnimator(context, R.animator.card_flip_out);
            inAnimator = AnimatorInflater.loadAnimator(context, R.animator.card_flip_in);
        }

        @SuppressLint("ClickableViewAccessibility")
        void bind(final Profile profile, final int position) {
            nameTextView.setText(profile.getName());
            ageTextView.setText(profile.getAge());
            titleTextView.setText(profile.getName());

            if (profile.isFavorite()) {
                favoriteButton.setImageDrawable(ContextCompat.getDrawable(context, R.drawable.ic_favorite_filled));
            } else {
                favoriteButton.setImageDrawable(ContextCompat.getDrawable(context, R.drawable.ic_favorite_border));
            }

            if (profile.isSmashed()) {
                smashedButton.setAlpha(0.5f);
            } else {
                smashedButton.setAlpha(1.0f);
            }

            if (profile.getImageUriString() != null) {
                try {
                    Uri imageUri = Uri.parse(profile.getImageUriString());
                    cardProfileImageView.setImageURI(imageUri);
                    cardBackgroundImageView.setImageURI(imageUri);
                } catch (Exception e) {
                    Log.e("ProfileAdapter", "Error parsing image URI", e);
                    cardProfileImageView.setImageResource(profile.getDefaultImageResource());
                    cardBackgroundImageView.setImageResource(profile.getDefaultImageResource());
                }
            } else {
                cardProfileImageView.setImageResource(profile.getDefaultImageResource());
                cardBackgroundImageView.setImageResource(profile.getDefaultImageResource());
            }

            Glide.with(context).asGif().load(R.drawable.active_status).into(activeStatusGifView);
            populateProfileDetails(profile);

            final boolean isExpanded = expandedState.get(position, false);
            expandableLayout.setVisibility(isExpanded ? View.VISIBLE : View.GONE);
            arrowButton.setRotation(isExpanded ? 180f : 0f);

            final boolean isFlipped = flippedState.get(position, false);
            cardFrontLayout.setVisibility(isFlipped ? View.GONE : View.VISIBLE);
            cardBackLayout.setVisibility(isFlipped ? View.VISIBLE : View.GONE);

            gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
                @Override public boolean onDown(MotionEvent e) { return true; }
                @Override public boolean onDoubleTap(MotionEvent e) {
                    toggleExpansion(position);
                    return true;
                }
            });

            itemView.setOnTouchListener((v, event) -> gestureDetector.onTouchEvent(event));
            arrowButton.setOnClickListener(v -> toggleExpansion(position));
            titleBarLayout.setOnClickListener(v -> toggleExpansion(position));

            View.OnClickListener flipListener = v -> {
                if (expandedState.get(position, false)) {
                    toggleExpansion(position);
                }
                toggleFlip(position);
            };
            flipQrButton.setOnClickListener(flipListener);
            flipToFrontButton.setOnClickListener(flipListener);

            smashedButton.setOnClickListener(v -> {
                if (listener != null) listener.onSmashedClicked(getAdapterPosition());
            });

            deleteButton.setOnClickListener(v -> {
                if (listener != null) listener.onDeleteClicked(getAdapterPosition());
            });
        }

        private void toggleExpansion(int position) {
            boolean isCurrentlyExpanded = expandedState.get(position, false);
            TransitionManager.beginDelayedTransition((ViewGroup) itemView, new AutoTransition().setDuration(300));
            if (isCurrentlyExpanded) {
                expandableLayout.setVisibility(View.GONE);
                arrowButton.animate().rotation(0f).setDuration(300).start();
                expandedState.put(position, false);
            } else {
                expandableLayout.setVisibility(View.VISIBLE);
                arrowButton.animate().rotation(180f).setDuration(300).start();
                expandedState.put(position, true);
            }
        }

        private void toggleFlip(int position) {
            boolean isCurrentlyFlipped = flippedState.get(position, false);
            final View visibleView = !isCurrentlyFlipped ? cardFrontLayout : cardBackLayout;
            final View invisibleView = !isCurrentlyFlipped ? cardBackLayout : cardFrontLayout;
            outAnimator.setTarget(visibleView);
            outAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    super.onAnimationEnd(animation);
                    visibleView.setVisibility(View.GONE);
                    invisibleView.setVisibility(View.VISIBLE);
                    inAnimator.setTarget(invisibleView);
                    inAnimator.start();
                    flippedState.put(position, !isCurrentlyFlipped);
                    outAnimator.removeListener(this);
                }
            });
            outAnimator.start();
        }

        private void populateProfileDetails(Profile profile) {
            if (profileDetailsContainer == null) return;
            profileDetailsContainer.removeAllViews();

            // Pass a lambda for the long-click listener, which calls the interface method
            addInfoRow(profileDetailsContainer, R.drawable.ic_person, "Name", profile.getName(), null, v -> {
                if (listener != null) listener.onInfoRowLongClicked(getAdapterPosition(), "name", profile.getName());
                return true;
            });
            addInfoRow(profileDetailsContainer, R.drawable.ic_person, "Age", profile.getAge(), null, v -> {
                if (listener != null) listener.onInfoRowLongClicked(getAdapterPosition(), "age", profile.getAge());
                return true;
            });
            addInfoRow(profileDetailsContainer, R.drawable.ic_location, "Location", profile.getLocation(), v -> {
                if (listener != null) listener.onLocationClicked(getAdapterPosition());
            }, v -> {
                if (listener != null) listener.onInfoRowLongClicked(getAdapterPosition(), "location", profile.getLocation());
                return true;
            });
            addInfoRow(profileDetailsContainer, R.drawable.ic_status, "Status", profile.isActive() ? "Active" : "Inactive", null, null); // Status not editable
            addInfoRow(profileDetailsContainer, R.drawable.ic_body_count, "Body Count", String.valueOf(profile.getBodyCount()), null, v -> {
                if (listener != null) listener.onInfoRowLongClicked(getAdapterPosition(), "bodyCount", String.valueOf(profile.getBodyCount()));
                return true;
            });
            addInfoRow(profileDetailsContainer, R.drawable.ic_kids, "Babies", String.valueOf(profile.getBabies()), null, v-> {
                if (listener != null) listener.onInfoRowLongClicked(getAdapterPosition(), "babies", String.valueOf(profile.getBabies()));
                return true;
            });
            addInfoRow(profileDetailsContainer, R.drawable.ic_bar_fine, "Bar Fine", "฿" + profile.getBarFine(), null, v->{
                if (listener != null) listener.onInfoRowLongClicked(getAdapterPosition(), "barFine", String.valueOf(profile.getBarFine()));
                return true;
            });
        }

        private void addInfoRow(LinearLayout container, @DrawableRes int iconRes, String label, String value, View.OnClickListener clickListener, View.OnLongClickListener longClickListener) {
            LayoutInflater inflater = LayoutInflater.from(context);
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
            // Set the long click listener if it exists
            if (longClickListener != null) {
                rowView.setOnLongClickListener(longClickListener);
            }

            container.addView(rowView);
        }
    }
}
