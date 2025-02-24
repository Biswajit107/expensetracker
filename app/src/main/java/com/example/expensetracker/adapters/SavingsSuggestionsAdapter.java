package com.example.expensetracker.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.expensetracker.R;
import com.example.expensetracker.viewmodel.PredictionViewModel.SavingsSuggestion;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;

public class SavingsSuggestionsAdapter extends RecyclerView.Adapter<SavingsSuggestionsAdapter.ViewHolder> {
    private List<SavingsSuggestion> suggestions = new ArrayList<>();
    private OnSuggestionClickListener listener;

    public interface OnSuggestionClickListener {
        void onSuggestionClick(SavingsSuggestion suggestion);
    }

    public void setOnSuggestionClickListener(OnSuggestionClickListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_savings_suggestion, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        SavingsSuggestion suggestion = suggestions.get(position);
        holder.bind(suggestion);
    }

    @Override
    public int getItemCount() {
        return suggestions.size();
    }

    public void setSuggestions(List<SavingsSuggestion> suggestions) {
        this.suggestions = suggestions;
        notifyDataSetChanged();
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        private final TextView titleText;
        private final TextView descriptionText;
        private final TextView impactText;
        private final MaterialButton actionButton;

        ViewHolder(View itemView) {
            super(itemView);
            titleText = itemView.findViewById(R.id.titleText);
            descriptionText = itemView.findViewById(R.id.descriptionText);
            impactText = itemView.findViewById(R.id.impactText);
            actionButton = itemView.findViewById(R.id.actionButton);

            itemView.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION && listener != null) {
                    listener.onSuggestionClick(suggestions.get(position));
                }
            });

            actionButton.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION && listener != null) {
                    listener.onSuggestionClick(suggestions.get(position));
                }
            });
        }

        void bind(SavingsSuggestion suggestion) {
            titleText.setText(suggestion.getTitle());
            descriptionText.setText(suggestion.getDescription());
            impactText.setText(suggestion.getImpact());
        }
    }
}