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
import com.example.expensetracker.viewmodel.PredictionViewModel.CategoryPrediction;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class CategoryPredictionsAdapter extends RecyclerView.Adapter<CategoryPredictionsAdapter.ViewHolder> {
    private List<CategoryPrediction> predictions = new ArrayList<>();
    private double maxPredictedAmount = 0;

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_category_prediction, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        CategoryPrediction prediction = predictions.get(position);
        holder.bind(prediction, maxPredictedAmount);
    }

    @Override
    public int getItemCount() {
        return predictions.size();
    }

    public void setCategoryPredictions(List<CategoryPrediction> predictions) {
        this.predictions = predictions;
        // Find max amount for progress bar scaling
        maxPredictedAmount = 0;
        for (CategoryPrediction prediction : predictions) {
            maxPredictedAmount = Math.max(maxPredictedAmount, prediction.getPredictedAmount());
        }
        notifyDataSetChanged();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        private final TextView categoryText;
        private final TextView predictionText;
        private final TextView rangeText;
        private final TextView trendText;
        private final LinearProgressIndicator progressBar;
        private final Context context;

        ViewHolder(View itemView) {
            super(itemView);
            context = itemView.getContext();
            categoryText = itemView.findViewById(R.id.categoryText);
            predictionText = itemView.findViewById(R.id.predictionText);
            rangeText = itemView.findViewById(R.id.rangeText);
            trendText = itemView.findViewById(R.id.trendText);
            progressBar = itemView.findViewById(R.id.progressBar);
        }

        void bind(CategoryPrediction prediction, double maxAmount) {
            categoryText.setText(prediction.getCategory());

            // Set prediction amount
            predictionText.setText(String.format(Locale.getDefault(),
                    "₹%.2f", prediction.getPredictedAmount()));

            // Set range text
            rangeText.setText(String.format(Locale.getDefault(),
                    "Range: ₹%.2f - ₹%.2f",
                    prediction.getLowerBound(),
                    prediction.getUpperBound()));

            // Set trend indicator and color
            double trend = prediction.getTrend() * 100; // Convert to percentage
            if (Math.abs(trend) > 5) { // Only show significant trends (>5%)
                trendText.setVisibility(View.VISIBLE);
                if (trend > 0) {
                    trendText.setText(String.format(Locale.getDefault(), "+%.1f%%", trend));
                    trendText.setTextColor(ContextCompat.getColor(context, R.color.red));
                } else {
                    trendText.setText(String.format(Locale.getDefault(), "%.1f%%", trend));
                    trendText.setTextColor(ContextCompat.getColor(context, R.color.green));
                }
            } else {
                trendText.setVisibility(View.GONE);
            }

            // Set progress bar (with null check)
            if (progressBar != null) {
                int progress = (int) ((prediction.getPredictedAmount() / maxAmount) * 100);
                progressBar.setProgress(progress);

                // Set progress bar color based on trend
                int color;
                if (trend > 10) {
                    color = ContextCompat.getColor(context, R.color.red);
                } else if (trend < -10) {
                    color = ContextCompat.getColor(context, R.color.green);
                } else {
                    color = ContextCompat.getColor(context, R.color.primary);
                }
                progressBar.setIndicatorColor(color);
            }
        }
    }
}