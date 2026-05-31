package com.xxx.server.filesystem;

import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.documentfile.provider.DocumentFile;
import androidx.recyclerview.widget.RecyclerView;

import com.xxx.server.R;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class FileAdapter extends RecyclerView.Adapter<FileAdapter.FileViewHolder> {

    private List<DocumentFile> files = new ArrayList<>();
    private OnItemClickListener listener;
    private OnContextMenuListener contextMenuListener;
    private Set<String> hiddenFiles = new HashSet<>();
    private Set<String> offeredFiles = new HashSet<>();

    public interface OnItemClickListener {
        void onItemClick(DocumentFile file);
    }

    public interface OnContextMenuListener {
        void onOfferFile(DocumentFile file);
        void onHideFile(DocumentFile file);
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.listener = listener;
    }

    public void setOnContextMenuListener(OnContextMenuListener listener) {
        this.contextMenuListener = listener;
    }

    public void setFiles(List<DocumentFile> files) {
        this.files = files;
        notifyDataSetChanged();
    }

    public void hideFile(DocumentFile file) {
        if (file != null && file.getName() != null) {
            hiddenFiles.add(file.getName());
            notifyDataSetChanged();
        }
    }

    public void offerFile(DocumentFile file) {
        if (file != null && file.getName() != null) {
            offeredFiles.add(file.getName());
            notifyDataSetChanged();
        }
    }

    public boolean isFileHidden(DocumentFile file) {
        return file != null && file.getName() != null && hiddenFiles.contains(file.getName());
    }

    public boolean isFileOffered(DocumentFile file) {
        return file != null && file.getName() != null && offeredFiles.contains(file.getName());
    }

    @NonNull
    @Override
    public FileViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.file_item_enhanced, parent, false);
        return new FileViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull FileViewHolder holder, int position) {
        holder.bind(files.get(position));
    }

    @Override
    public int getItemCount() {
        return files.size();
    }

    class FileViewHolder extends RecyclerView.ViewHolder implements View.OnCreateContextMenuListener {
        private final TextView fileNameTextView;
        private final ImageView fileIconImageView;
        private final ImageView statusIconImageView;
        private DocumentFile currentFile;

        public FileViewHolder(@NonNull View itemView) {
            super(itemView);
            fileNameTextView = itemView.findViewById(R.id.fileName);
            fileIconImageView = itemView.findViewById(R.id.fileIcon);
            statusIconImageView = itemView.findViewById(R.id.statusIcon);

            itemView.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (listener != null && position != RecyclerView.NO_POSITION) {
                    listener.onItemClick(files.get(position));
                }
            });

            itemView.setOnCreateContextMenuListener(this);
            itemView.setOnLongClickListener(v -> {
                v.showContextMenu();
                return true;
            });
        }

        public void bind(DocumentFile file) {
            currentFile = file;
            fileNameTextView.setText(file.getName());

            // Set file type icon
            if (file.isDirectory()) {
                fileIconImageView.setImageResource(R.drawable.ic_folder_24);
            } else {
                fileIconImageView.setImageResource(R.drawable.ic_file);
            }

            // Set status icon
            if (isFileHidden(file)) {
                statusIconImageView.setImageResource(R.drawable.ic_visibility_off_24);
                statusIconImageView.setVisibility(View.VISIBLE);
                itemView.setAlpha(0.5f);
            } else if (isFileOffered(file)) {
                statusIconImageView.setImageResource(R.drawable.ic_share_24);
                statusIconImageView.setVisibility(View.VISIBLE);
                itemView.setAlpha(1.0f);
            } else {
                statusIconImageView.setVisibility(View.GONE);
                itemView.setAlpha(1.0f);
            }
        }

        @Override
        public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
            if (currentFile == null) return;

            menu.setHeaderTitle(currentFile.getName());

            if (isFileOffered(currentFile)) {
                menu.add(0, 1, 0, "Stop Offering");
            } else {
                menu.add(0, 1, 0, "Offer");
            }

            if (isFileHidden(currentFile)) {
                menu.add(0, 2, 0, "Show");
            } else {
                menu.add(0, 2, 0, "Hide");
            }

            // Handle menu item clicks
            for (int i = 0; i < menu.size(); i++) {
                MenuItem item = menu.getItem(i);
                item.setOnMenuItemClickListener(menuItem -> {
                    if (contextMenuListener != null) {
                        switch (menuItem.getItemId()) {
                            case 1: // Offer/Stop Offering
                                if (isFileOffered(currentFile)) {
                                    offeredFiles.remove(currentFile.getName());
                                } else {
                                    contextMenuListener.onOfferFile(currentFile);
                                    offerFile(currentFile);
                                }
                                break;
                            case 2: // Hide/Show
                                if (isFileHidden(currentFile)) {
                                    hiddenFiles.remove(currentFile.getName());
                                } else {
                                    contextMenuListener.onHideFile(currentFile);
                                    hideFile(currentFile);
                                }
                                break;
                        }
                        notifyDataSetChanged();
                    }
                    return true;
                });
            }
        }
    }
}