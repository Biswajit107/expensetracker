package com.example.expensetracker.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.expensetracker.R;
import com.example.expensetracker.models.ExclusionPattern;
import com.google.android.material.switchmaterial.SwitchMaterial;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Adapter for displaying and managing exclusion patterns
 */
public class ExclusionPatternAdapter extends RecyclerView.Adapter<ExclusionPatternAdapter.PatternViewHolder> {

    private List<ExclusionPattern> patterns = new ArrayList<>();
    private OnPatternActionListener listener;

    /**
     * Interface for handling pattern actions
     */
    public interface OnPatternActionListener {
        void onPatternDeactivated(ExclusionPattern pattern);
        void onPatternDeleted(ExclusionPattern pattern);
        void onPatternDetailsRequested(ExclusionPattern pattern);
    }

    /**
     * Set listener for pattern actions
     */
    public void setOnPatternActionListener(OnPatternActionListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public PatternViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_exclusion_pattern, parent, false);
        return new PatternViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PatternViewHolder holder, int position) {
        ExclusionPattern pattern = patterns.get(position);
        holder.bind(pattern);
    }

    @Override
    public int getItemCount() {
        return patterns.size();
    }

    /**
     * Update the list of patterns
     */
    public void setPatterns(List<ExclusionPattern> patterns) {
        this.patterns = patterns;
        notifyDataSetChanged();
    }

    /**
     * ViewHolder for exclusion pattern items
     */
    class PatternViewHolder extends RecyclerView.ViewHolder {
        private final TextView titleText;
        private final TextView descriptionText;
        private final TextView statsText;
        private final TextView dateText;
        private final SwitchMaterial activeSwitch;
        private final Button deleteButton;
        private final Button detailsButton;

        public PatternViewHolder(@NonNull View itemView) {
            super(itemView);

            titleText = itemView.findViewById(R.id.patternTitleText);
            descriptionText = itemView.findViewById(R.id.patternDescriptionText);
            statsText = itemView.findViewById(R.id.patternStatsText);
            dateText = itemView.findViewById(R.id.patternDateText);
            activeSwitch = itemView.findViewById(R.id.patternActiveSwitch);
            deleteButton = itemView.findViewById(R.id.deletePatternButton);
            detailsButton = itemView.findViewById(R.id.viewPatternDetailsButton);

            // Set click listeners
            activeSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION && listener != null) {
                    ExclusionPattern pattern = patterns.get(position);

                    if (!isChecked) {
                        // Deactivate pattern
                        listener.onPatternDeactivated(pattern);
                    } else {
                        // Can't reactivate from here - reset to current state
                        buttonView.setChecked(pattern.isActive());
                        Toast.makeText(itemView.getContext(),
                                "To reactivate, edit the transaction that created this pattern",
                                Toast.LENGTH_SHORT).show();
                    }
                }
            });

            deleteButton.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION && listener != null) {
                    listener.onPatternDeleted(patterns.get(position));
                }
            });

            detailsButton.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION && listener != null) {
                    listener.onPatternDetailsRequested(patterns.get(position));
                }
            });
        }

        /**
         * Bind data to the view
         */
        public void bind(ExclusionPattern pattern) {
            // Set basic info
            String patternTitle = formatPatternTitle(pattern);
            titleText.setText(patternTitle);

            String patternDesc = formatPatternDescription(pattern);
            descriptionText.setText(patternDesc);

            // Set statistics
            statsText.setText(formatPatternStats(pattern));

            // Set date
            SimpleDateFormat dateFormat = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());
            dateText.setText("Created: " + dateFormat.format(new Date(pattern.getCreatedDate())));

            // Set active state
            activeSwitch.setChecked(pattern.isActive());
        }

        /**
         * Format pattern title from merchant or description
         */
        private String formatPatternTitle(ExclusionPattern pattern) {
            // Use merchant pattern if available, otherwise use description pattern
            if (pattern.getMerchantPattern() != null && !pattern.getMerchantPattern().isEmpty()) {
                return capitalizeWords(pattern.getMerchantPattern());
            } else if (pattern.getDescriptionPattern() != null && !pattern.getDescriptionPattern().isEmpty()) {
                // Use first few words of description pattern
                String[] words = pattern.getDescriptionPattern().split("\\s+");
                StringBuilder title = new StringBuilder();
                int wordLimit = Math.min(3, words.length);

                for (int i = 0; i < wordLimit; i++) {
                    if (title.length() > 0) {
                        title.append(" ");
                    }
                    title.append(words[i]);
                }

                return capitalizeWords(title.toString());
            } else {
                return "Unnamed Pattern";
            }
        }

        /**
         * Format pattern description including key attributes
         */
        private String formatPatternDescription(ExclusionPattern pattern) {
            StringBuilder description = new StringBuilder();

            // Add transaction type
            description.append(pattern.getTransactionType()).append(" transactions");

            // Add amount range
            description.append(" between ₹").append(String.format(Locale.getDefault(), "%.2f", pattern.getMinAmount()));
            description.append(" and ₹").append(String.format(Locale.getDefault(), "%.2f", pattern.getMaxAmount()));

            // Add category if available
            if (pattern.getCategory() != null && !pattern.getCategory().isEmpty()) {
                description.append(", category: ").append(pattern.getCategory());
            }

            return description.toString();
        }

        /**
         * Format pattern statistics
         */
        private String formatPatternStats(ExclusionPattern pattern) {
            return "Auto-excluded " + pattern.getPatternMatchesCount() + " transaction(s)";
        }

        /**
         * Helper to capitalize each word in a string
         */
        private String capitalizeWords(String text) {
            if (text == null || text.isEmpty()) {
                return "";
            }

            String[] words = text.split("\\s+");
            StringBuilder result = new StringBuilder();

            for (String word : words) {
                if (!word.isEmpty()) {
                    if (result.length() > 0) {
                        result.append(" ");
                    }

                    result.append(Character.toUpperCase(word.charAt(0)));

                    if (word.length() > 1) {
                        result.append(word.substring(1));
                    }
                }
            }

            return result.toString();
        }
    }
}