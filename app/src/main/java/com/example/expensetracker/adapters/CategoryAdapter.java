package com.example.expensetracker.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.expensetracker.R;
import com.example.expensetracker.viewmodel.AnalyticsViewModel.CategoryData;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class CategoryAdapter extends RecyclerView.Adapter<CategoryAdapter.ViewHolder> {
    private List<CategoryData> categories = new ArrayList<>();
    private double maxAmount = 0;

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_category, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        CategoryData category = categories.get(position);
        holder.bind(category, maxAmount);
    }

    @Override
    public int getItemCount() {
        return categories.size();
    }

    public void setCategories(List<CategoryData> categories) {
        this.categories = categories;
        // Calculate max amount for progress bar scaling
        maxAmount = 0;
        for (CategoryData category : categories) {
            maxAmount = Math.max(maxAmount, category.getTotalAmount());
        }
        notifyDataSetChanged();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        private final TextView categoryText;
        private final TextView amountText;
        private final TextView countText;
        private final LinearProgressIndicator progressBar;

        ViewHolder(View itemView) {
            super(itemView);
            categoryText = itemView.findViewById(R.id.categoryText);
            amountText = itemView.findViewById(R.id.amountText);
            countText = itemView.findViewById(R.id.countText);
            progressBar = itemView.findViewById(R.id.progressBar);
        }

        void bind(CategoryData category, double maxAmount) {
            categoryText.setText(category.getCategory());
            amountText.setText(String.format(Locale.getDefault(),
                    "₹%.2f", category.getTotalAmount()));
            countText.setText(String.format(Locale.getDefault(),
                    "%d transactions", category.getTransactionCount()));

            // Set progress relative to highest category amount
            int progress = (int) ((category.getTotalAmount() / maxAmount) * 100);
            progressBar.setProgress(progress);
        }
    }
}