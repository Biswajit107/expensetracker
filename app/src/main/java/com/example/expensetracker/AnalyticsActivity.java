package com.example.expensetracker;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.expensetracker.adapters.CategoryAdapter;
import com.example.expensetracker.adapters.CategoryBudgetAdapter;
import com.example.expensetracker.dialogs.MonthYearPickerDialog;
import com.example.expensetracker.viewmodel.AnalyticsViewModel;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class AnalyticsActivity extends AppCompatActivity {
    private AnalyticsViewModel viewModel;
    private LineChart lineChart;
    private PieChart pieChart;
    private BarChart trendChart;
    private CategoryAdapter categoryAdapter;
    private CategoryBudgetAdapter budgetAdapter;
    private MaterialButton monthSelector;
    private Calendar selectedMonth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_analytics);

        selectedMonth = Calendar.getInstance();
        viewModel = new ViewModelProvider(this).get(AnalyticsViewModel.class);

        setupViews();
        setupCharts();
        setupRecyclerViews();
        setupBottomNavigation();
        observeData();
    }

    private void setupViews() {
        lineChart = findViewById(R.id.lineChart);
        pieChart = findViewById(R.id.pieChart);
        trendChart = findViewById(R.id.trendChart);
        monthSelector = findViewById(R.id.monthSelector);

        monthSelector.setOnClickListener(v -> showMonthPicker());
        updateMonthSelectorText();
    }

    private void setupCharts() {
        // Line Chart Setup
        lineChart.getDescription().setEnabled(false);
        lineChart.setDrawGridBackground(false);
        lineChart.getAxisRight().setEnabled(false);
        lineChart.getXAxis().setPosition(XAxis.XAxisPosition.BOTTOM);
        lineChart.setTouchEnabled(true);
        lineChart.setDragEnabled(true);
        lineChart.setScaleEnabled(true);

        // Pie Chart Setup
        pieChart.getDescription().setEnabled(false);
        pieChart.setDrawHoleEnabled(true);
        pieChart.setHoleColor(Color.WHITE);
        pieChart.setTransparentCircleRadius(0f);
        pieChart.setEntryLabelTextSize(12f);

        // Bar Chart Setup
        trendChart.getDescription().setEnabled(false);
        trendChart.setDrawGridBackground(false);
        trendChart.getAxisRight().setEnabled(false);
        trendChart.getXAxis().setPosition(XAxis.XAxisPosition.BOTTOM);
    }

    private void setupRecyclerViews() {
        RecyclerView categoryList = findViewById(R.id.categoryList);
        categoryList.setLayoutManager(new LinearLayoutManager(this));
        categoryAdapter = new CategoryAdapter();
        categoryList.setAdapter(categoryAdapter);

        RecyclerView budgetList = findViewById(R.id.categoryBudgetList);
        budgetList.setLayoutManager(new LinearLayoutManager(this));
        budgetAdapter = new CategoryBudgetAdapter();
        budgetList.setAdapter(budgetAdapter);
    }

    private void setupBottomNavigation() {
        BottomNavigationView bottomNav = findViewById(R.id.bottomNavigation);
        bottomNav.setSelectedItemId(R.id.nav_analytics);
        bottomNav.setOnItemSelectedListener(item -> {
            switch (item.getItemId()) {
                case R.id.nav_home:
                    startActivity(new android.content.Intent(this, MainActivity.class));
//                    finish();
                    return true;
                case R.id.nav_analytics:
                    return true;
                case R.id.nav_predictions:
                    startActivity(new android.content.Intent(this, PredictionActivity.class));
//                    finish();
                    return true;
                case R.id.nav_groups:
                    startActivity(new android.content.Intent(this, GroupedExpensesActivity.class));
//                    finish();
                    return true;
                case R.id.nav_excluded:
                    startActivity(new Intent(this, ExcludedTransactionsActivity.class));
                    return true;
            }
            return false;
        });
    }

    private void observeData() {
        // Get start and end dates for the selected month
        Calendar start = (Calendar) selectedMonth.clone();
        start.set(Calendar.DAY_OF_MONTH, 1);
        start.set(Calendar.HOUR_OF_DAY, 0);
        start.set(Calendar.MINUTE, 0);
        start.set(Calendar.SECOND, 0);

        Calendar end = (Calendar) start.clone();
        end.add(Calendar.MONTH, 1);
        end.add(Calendar.SECOND, -1);

        // Observe monthly data
        viewModel.getMonthlyData(start.getTimeInMillis(), end.getTimeInMillis())
                .observe(this, monthlyData -> {
                    updateLineChart(monthlyData.getDailyTransactions());
                    updatePieChart(monthlyData.getCategoryTotals());
                    updateTrendChart(monthlyData.getWeeklyTotals());
                    updateSummary(monthlyData);
                });

        // Observe category data
        viewModel.getCategoryData(start.getTimeInMillis(), end.getTimeInMillis())
                .observe(this, categoryData -> {
                    categoryAdapter.setCategories(categoryData);
                });

        // Observe budget data
        viewModel.getBudgetData(start.getTimeInMillis(), end.getTimeInMillis())
                .observe(this, budgetData -> {
                    budgetAdapter.setBudgetData(budgetData);
                    updateBudgetProgress(budgetData);
                });
    }

    private void updateLineChart(Map<Date, Double> dailyData) {
        List<Entry> entries = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        SimpleDateFormat dateFormat = new SimpleDateFormat("dd", Locale.getDefault());

        int index = 0;
        for (Map.Entry<Date, Double> entry : dailyData.entrySet()) {
            entries.add(new Entry(index, entry.getValue().floatValue()));
            labels.add(dateFormat.format(entry.getKey()));
            index++;
        }

        LineDataSet dataSet = new LineDataSet(entries, "Daily Expenses");
        dataSet.setColor(getColor(R.color.primary));
        dataSet.setCircleColor(getColor(R.color.primary));
        dataSet.setDrawValues(false);

        LineData lineData = new LineData(dataSet);
        lineChart.setData(lineData);

        XAxis xAxis = lineChart.getXAxis();
        xAxis.setValueFormatter(new IndexAxisValueFormatter(labels));
        xAxis.setLabelCount(Math.min(labels.size(), 6));

        lineChart.invalidate();
    }

    private void updatePieChart(Map<String, Double> categoryTotals) {
        List<PieEntry> entries = new ArrayList<>();
        for (Map.Entry<String, Double> entry : categoryTotals.entrySet()) {
            entries.add(new PieEntry(entry.getValue().floatValue(), entry.getKey()));
        }

        PieDataSet dataSet = new PieDataSet(entries, "");
        dataSet.setColors(getChartColors());
        dataSet.setSliceSpace(2f);
        dataSet.setValueTextSize(12f);

        PieData data = new PieData(dataSet);
        pieChart.setData(data);
        pieChart.invalidate();
    }

    private void updateTrendChart(List<Double> weeklyTotals) {
        List<Integer> colors = getChartColors();
        List<com.github.mikephil.charting.data.BarEntry> entries = new ArrayList<>();

        for (int i = 0; i < weeklyTotals.size(); i++) {
            entries.add(new com.github.mikephil.charting.data.BarEntry(i, weeklyTotals.get(i).floatValue()));
        }

        BarDataSet dataSet = new BarDataSet(entries, "Weekly Trends");
        dataSet.setColors(colors);

        BarData barData = new BarData(dataSet);
        trendChart.setData(barData);
        trendChart.invalidate();
    }

    private void updateSummary(AnalyticsViewModel.MonthlyData data) {
        // Update total income
        TextView totalIncomeText = findViewById(R.id.totalIncomeText);
        totalIncomeText.setText(String.format(Locale.getDefault(), "₹%.2f", data.getTotalIncome()));

        // Update total expenses
        TextView totalExpensesText = findViewById(R.id.totalExpensesText);
        totalExpensesText.setText(String.format(Locale.getDefault(), "₹%.2f", data.getTotalExpenses()));
    }

    private void updateBudgetProgress(List<AnalyticsViewModel.BudgetStatus> budgetData) {
        double totalSpent = 0;
        double totalBudget = 0;

        for (AnalyticsViewModel.BudgetStatus status : budgetData) {
            totalSpent += status.getSpent();
            totalBudget += status.getBudget();
        }

        double percentage = (totalSpent / totalBudget) * 100;

        // Update progress bar
        com.google.android.material.progressindicator.LinearProgressIndicator progressBar =
                findViewById(R.id.monthlyBudgetProgress);
        progressBar.setProgress((int) percentage);

        // Update status text
        TextView statusText = findViewById(R.id.budgetStatusText);
        statusText.setText(String.format(Locale.getDefault(),
                "%.1f%% of monthly budget used", percentage));
    }

    private void showMonthPicker() {
        MonthYearPickerDialog dialog = new MonthYearPickerDialog();
        dialog.setListener((view, year, month, dayOfMonth) -> {
            selectedMonth.set(Calendar.YEAR, year);
            selectedMonth.set(Calendar.MONTH, month);
            updateMonthSelectorText();
            observeData();
        });
        dialog.show(getSupportFragmentManager(), "MonthYearPickerDialog");
    }

    private void updateMonthSelectorText() {
        SimpleDateFormat monthFormat = new SimpleDateFormat("MMMM yyyy", Locale.getDefault());
        monthSelector.setText(monthFormat.format(selectedMonth.getTime()));
    }

    private List<Integer> getChartColors() {
        return Arrays.asList(
                getColor(R.color.primary),
                getColor(R.color.green),
                getColor(R.color.red),
                getColor(R.color.yellow),
                getColor(R.color.secondary)
        );
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Refresh the badge count on bottom navigation
        BottomNavigationView bottomNav = findViewById(R.id.bottomNavigation);
        bottomNav.setSelectedItemId(R.id.nav_analytics); // Change this ID for each activity
    }
}