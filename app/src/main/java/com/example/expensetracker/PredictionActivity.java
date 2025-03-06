package com.example.expensetracker;

import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.expensetracker.adapters.RecurringExpensesAdapter;
import com.example.expensetracker.adapters.CategoryPredictionsAdapter;
import com.example.expensetracker.adapters.SavingsSuggestionsAdapter;
import com.example.expensetracker.viewmodel.PredictionViewModel;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class PredictionActivity extends AppCompatActivity {
    private PredictionViewModel viewModel;
    private LineChart predictionChart;
    private RecurringExpensesAdapter recurringAdapter;
    private CategoryPredictionsAdapter categoryAdapter;
    private SavingsSuggestionsAdapter savingsAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_predictions);

        viewModel = new ViewModelProvider(this).get(PredictionViewModel.class);

        setupViews();
        setupChart();
        setupRecyclerViews();
        setupBottomNavigation();
        observeData();
    }

    private void setupViews() {
        predictionChart = findViewById(R.id.predictionChart);
    }

    private void setupChart() {
        predictionChart.getDescription().setEnabled(false);
        predictionChart.setDrawGridBackground(false);
        predictionChart.getAxisRight().setEnabled(false);
        predictionChart.getXAxis().setPosition(XAxis.XAxisPosition.BOTTOM);
        predictionChart.setTouchEnabled(true);
        predictionChart.setDragEnabled(true);
        predictionChart.setScaleEnabled(true);
    }

    private void setupRecyclerViews() {
        // Setup Recurring Expenses RecyclerView
        RecyclerView recurringExpensesList = findViewById(R.id.recurringExpensesList);
        recurringExpensesList.setLayoutManager(new LinearLayoutManager(this));
        recurringAdapter = new RecurringExpensesAdapter();
        recurringExpensesList.setAdapter(recurringAdapter);

        // Setup Category Predictions RecyclerView
        RecyclerView categoryPredictionsList = findViewById(R.id.categoryPredictionsList);
        categoryPredictionsList.setLayoutManager(new LinearLayoutManager(this));
        categoryAdapter = new CategoryPredictionsAdapter();
        categoryPredictionsList.setAdapter(categoryAdapter);

        // Setup Savings Suggestions RecyclerView
        RecyclerView savingsSuggestionsList = findViewById(R.id.savingsSuggestionsList);
        savingsSuggestionsList.setLayoutManager(new LinearLayoutManager(this));
        savingsAdapter = new SavingsSuggestionsAdapter();
        savingsSuggestionsList.setAdapter(savingsAdapter);
    }

    private void setupBottomNavigation() {
        BottomNavigationView bottomNav = findViewById(R.id.bottomNavigation);
        bottomNav.setSelectedItemId(R.id.nav_predictions);
        bottomNav.setOnItemSelectedListener(item -> {
            switch (item.getItemId()) {
                case R.id.nav_home:
                    startActivity(new android.content.Intent(this, MainActivity.class));
//                    finish();
                    return true;
                case R.id.nav_analytics:
                    startActivity(new android.content.Intent(this, AnalyticsActivity.class));
//                    finish();
                    return true;
                case R.id.nav_predictions:
                    return true;
                case R.id.nav_groups:
                    startActivity(new android.content.Intent(this, GroupedExpensesActivity.class));
//                    finish();
                    return true;
//                case R.id.nav_excluded:
//                    startActivity(new Intent(this, ExcludedTransactionsActivity.class));
//                    return true;

                case R.id.nav_patterns:
                    startActivity(new Intent(this, ExclusionPatternsActivity.class));
                    return true;
            }
            return false;
        });
    }

    private void observeData() {
        // Observe monthly prediction
        viewModel.getMonthlyPrediction().observe(this, prediction -> {
            updatePredictionDisplay(prediction);
            updatePredictionChart(prediction);
        });

        // Observe recurring expenses
        viewModel.getRecurringExpenses().observe(this, recurringExpenses -> {
            recurringAdapter.setRecurringExpenses(recurringExpenses);
        });

        // Observe category predictions
        viewModel.getCategoryPredictions().observe(this, categoryPredictions -> {
            categoryAdapter.setCategoryPredictions(categoryPredictions);
        });

        // Observe savings suggestions
        viewModel.getSavingsSuggestions().observe(this, suggestions -> {
            savingsAdapter.setSuggestions(suggestions);
        });
    }

    private void updatePredictionDisplay(PredictionViewModel.MonthlyPrediction prediction) {
        TextView predictedAmountText = findViewById(R.id.predictedAmountText);
        TextView predictionRangeText = findViewById(R.id.predictionRangeText);

        predictedAmountText.setText(String.format(Locale.getDefault(),
                "₹%.2f", prediction.getPredictedAmount()));

        predictionRangeText.setText(String.format(Locale.getDefault(),
                "Expected range: ₹%.2f - ₹%.2f",
                prediction.getLowerBound(),
                prediction.getUpperBound()));
    }

    private void updatePredictionChart(PredictionViewModel.MonthlyPrediction prediction) {
        List<Entry> historicalEntries = new ArrayList<>();
        List<Entry> predictionEntries = new ArrayList<>();
        List<Entry> upperBoundEntries = new ArrayList<>();
        List<Entry> lowerBoundEntries = new ArrayList<>();

        // Add historical data points
        int index = 0;
        for (Double amount : prediction.getHistoricalData()) {
            historicalEntries.add(new Entry(index++, amount.floatValue()));
        }

        // Add prediction points
        float lastX = historicalEntries.get(historicalEntries.size() - 1).getX();
        for (int i = 1; i <= 3; i++) { // 3 months prediction
            float x = lastX + i;
            predictionEntries.add(new Entry(x, (float) prediction.getPredictedAmount()));
            upperBoundEntries.add(new Entry(x, (float) prediction.getUpperBound()));
            lowerBoundEntries.add(new Entry(x, (float) prediction.getLowerBound()));
        }

        // Create datasets
        LineDataSet historicalSet = new LineDataSet(historicalEntries, "Historical");
        historicalSet.setColor(getColor(R.color.primary));
        historicalSet.setCircleColor(getColor(R.color.primary));
        historicalSet.setDrawValues(false);

        LineDataSet predictionSet = new LineDataSet(predictionEntries, "Predicted");
        predictionSet.setColor(getColor(R.color.secondary));
        predictionSet.setCircleColor(getColor(R.color.secondary));
        predictionSet.setDrawValues(false);
        predictionSet.enableDashedLine(10f, 5f, 0f);

        LineDataSet upperBoundSet = new LineDataSet(upperBoundEntries, "Upper Bound");
        upperBoundSet.setColor(getColor(R.color.text_secondary));
        upperBoundSet.setDrawCircles(false);
        upperBoundSet.setDrawValues(false);
        upperBoundSet.enableDashedLine(5f, 5f, 0f);

        LineDataSet lowerBoundSet = new LineDataSet(lowerBoundEntries, "Lower Bound");
        lowerBoundSet.setColor(getColor(R.color.text_secondary));
        lowerBoundSet.setDrawCircles(false);
        lowerBoundSet.setDrawValues(false);
        lowerBoundSet.enableDashedLine(5f, 5f, 0f);

        // Combine all datasets
        LineData lineData = new LineData(historicalSet, predictionSet,
                upperBoundSet, lowerBoundSet);
        predictionChart.setData(lineData);
        predictionChart.invalidate();
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Refresh the badge count on bottom navigation
        BottomNavigationView bottomNav = findViewById(R.id.bottomNavigation);
        bottomNav.setSelectedItemId(R.id.nav_predictions); // Change this ID for each activity
    }
}