package com.example.expensetracker.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.expensetracker.R;
import com.example.expensetracker.viewmodel.AnalyticsViewModel.BudgetStatus;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class CategoryBudgetAdapter extends RecyclerView.Adapter<CategoryBudgetAdapter.ViewHolder> {
    private List<BudgetStatus> budgetStatuses = new ArrayList<>();

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_category_budget, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        BudgetStatus status = budgetStatuses.get(position);
        holder.bind(status);
    }

    @Override
    public int getItemCount() {
        return budgetStatuses.size();
    }

    public void setBudgetData(List<BudgetStatus> budgetStatuses) {
        this.budgetStatuses = budgetStatuses;
        notifyDataSetChanged();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        private final TextView categoryText;
        private final TextView statusText;
        private final LinearProgressIndicator progressBar;
        private final Context context;

        ViewHolder(View itemView) {
            super(itemView);
            context = itemView.getContext();
            categoryText = itemView.findViewById(R.id.categoryText);
            statusText = itemView.findViewById(R.id.statusText);
            progressBar = itemView.findViewById(R.id.progressBar);
        }

        void bind(BudgetStatus status) {
            categoryText.setText(status.getCategory());

            // Format status text to show spent/budget and percentage
            statusText.setText(String.format(Locale.getDefault(),
                    "₹%.2f / ₹%.2f (%.1f%%)",
                    status.getSpent(),
                    status.getBudget(),
                    status.getPercentage()));

            // Set progress and color based on percentage
            int percentage = (int) status.getPercentage();
            progressBar.setProgress(percentage);

            // Color coding based on budget usage
            int color;
            if (percentage > 90) {
                color = ContextCompat.getColor(context, R.color.red);
            } else if (percentage > 70) {
                color = ContextCompat.getColor(context, R.color.yellow);
            } else {
                color = ContextCompat.getColor(context, R.color.green);
            }
            progressBar.setIndicatorColor(color);

            // Set text color based on budget status
            if (percentage > 90) {
                statusText.setTextColor(ContextCompat.getColor(context, R.color.red));
            } else {
                statusText.setTextColor(ContextCompat.getColor(context, R.color.text_secondary));
            }
        }
    }
}