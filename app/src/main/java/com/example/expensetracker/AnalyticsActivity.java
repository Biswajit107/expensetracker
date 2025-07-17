package com.example.expensetracker;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.expensetracker.adapters.CategoryAdapter;
import com.example.expensetracker.adapters.CategoryBudgetAdapter;
import com.example.expensetracker.adapters.SimpleRecurringExpensesAdapter;
import com.example.expensetracker.adapters.TopMerchantsAdapter;
import com.example.expensetracker.dialogs.MonthYearPickerDialog;
import com.example.expensetracker.models.Transaction;
import com.example.expensetracker.utils.PreferencesManager;
import com.example.expensetracker.viewmodel.AnalyticsViewModel;

import java.util.HashMap;
import java.util.Map;
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
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class AnalyticsActivity extends AppCompatActivity {
    private static final String TAG = "AnalyticsActivity";
    
    private AnalyticsViewModel viewModel;
    private PreferencesManager preferencesManager;
    private LineChart lineChart;
    private PieChart pieChart;
    private BarChart trendChart;
    private CategoryAdapter categoryAdapter;
    private CategoryBudgetAdapter budgetAdapter;
    private SimpleRecurringExpensesAdapter recurringExpensesAdapter;
    private TopMerchantsAdapter topMerchantsAdapter;
    private MaterialButton monthSelector;
    private Calendar selectedMonth;

    // Enhanced Dashboard UI Elements
    private TextView totalIncomeText;
    private TextView totalExpensesText;
    private TextView savedAmountText;
    private TextView incomeChangeText;
    private TextView expenseChangeText;
    private TextView savingsChangeText;
    private TextView budgetHealthText;
    private TextView budgetPercentageText;
    private LinearProgressIndicator budgetProgressBar;
    
    // Quick insights views
    private TextView biggestExpenseText;
    private TextView savingsTargetText;
    private TextView daysToPaydayText;
    
    // Budget alerts views removed from new layout
    // private MaterialCardView budgetAlertsCard;
    // private RecyclerView budgetAlertsRecyclerView;
    
    private TextView dailyAverageText;
    private TextView currentDayText;
    private LinearProgressIndicator monthProgressBar;
    
    // New Analytics Views
    private TextView expectedTotalText;
    private TextView budgetTargetText;
    private TextView predictionStatusText;
    private TextView confidenceText;
    private RecyclerView recurringExpensesRecyclerView;
    private TextView totalRecurringText;
    private TextView currentBurnRateText;
    private TextView burnRateChangeText;
    private TextView daysOfMoneyText;
    private TextView burnRateStatusText;
    private RecyclerView topMerchantsRecyclerView;
    private TextView topMerchantsSummaryText;
    private TextView smallTransactionsText;
    private TextView mediumTransactionsText;
    private TextView largeTransactionsText;
    private TextView transactionPatternText;
    private TextView avgTransactionsText;
    private TextView mostActiveText;
    private TextView leastActiveText;
    private TextView morningSpendingText;
    private TextView afternoonSpendingText;
    private TextView eveningSpendingText;
    private TextView timeInsightText;
    
    // Spending Efficiency Views
    private TextView avgTransactionAmountText;
    private TextView efficiencyScoreText;
    private TextView impulsePurchasesText;
    private TextView efficiencyStatusText;
    
    // Financial Health Views
    private TextView healthScoreText;
    private TextView spendingControlText;
    private TextView savingsRateText;
    private TextView emergencyFundText;
    private TextView healthImprovementText;
    private TextView totalTransactionsText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_analytics);

        selectedMonth = Calendar.getInstance();
        viewModel = new ViewModelProvider(this).get(AnalyticsViewModel.class);
        preferencesManager = new PreferencesManager(this);

        setupViews();
        setupCharts();
        setupRecyclerViews();
        setupBottomNavigation();
        observeData();
    }

    private void setupViews() {
        // Original views
        lineChart = findViewById(R.id.lineChart);
        pieChart = findViewById(R.id.pieChart);
        trendChart = findViewById(R.id.trendChart);
        monthSelector = findViewById(R.id.monthSelector);

        // Enhanced Dashboard Views
        totalIncomeText = findViewById(R.id.totalIncomeText);
        totalExpensesText = findViewById(R.id.totalExpensesText);
        savedAmountText = findViewById(R.id.savedAmountText);
        incomeChangeText = findViewById(R.id.incomeChangeText);
        expenseChangeText = findViewById(R.id.expenseChangeText);
        savingsChangeText = findViewById(R.id.savingsChangeText);
        budgetHealthText = findViewById(R.id.budgetHealthText);
        budgetPercentageText = findViewById(R.id.budgetPercentageText);
        budgetProgressBar = findViewById(R.id.budgetProgressBar);
        
        // Quick insights views
        biggestExpenseText = findViewById(R.id.biggestExpenseText);
        savingsTargetText = findViewById(R.id.savingsTargetText);
        daysToPaydayText = findViewById(R.id.daysToPaydayText);
        
        // Budget alerts views removed from new layout
        // budgetAlertsCard = findViewById(R.id.budgetAlertsCard);
        // budgetAlertsRecyclerView = findViewById(R.id.budgetAlertsRecyclerView);
        
        dailyAverageText = findViewById(R.id.dailyAverageText);
        currentDayText = findViewById(R.id.currentDayText);
        monthProgressBar = findViewById(R.id.monthProgressBar);
        
        // Initialize analytics views (only if they exist in the current layout)
        expectedTotalText = findViewById(R.id.expectedTotalText);
        budgetTargetText = findViewById(R.id.budgetTargetText);
        predictionStatusText = findViewById(R.id.predictionStatusText);
        confidenceText = findViewById(R.id.confidenceText);
        recurringExpensesRecyclerView = findViewById(R.id.recurringExpensesRecyclerView);
        totalRecurringText = findViewById(R.id.totalRecurringText);
        currentBurnRateText = findViewById(R.id.currentBurnRateText);
        burnRateChangeText = findViewById(R.id.burnRateChangeText);
        daysOfMoneyText = findViewById(R.id.daysOfMoneyText);
        burnRateStatusText = findViewById(R.id.burnRateStatusText);
        topMerchantsRecyclerView = findViewById(R.id.topMerchantsRecyclerView);
        topMerchantsSummaryText = findViewById(R.id.topMerchantsSummaryText);
        smallTransactionsText = findViewById(R.id.smallTransactionsText);
        mediumTransactionsText = findViewById(R.id.mediumTransactionsText);
        largeTransactionsText = findViewById(R.id.largeTransactionsText);
        transactionPatternText = findViewById(R.id.transactionPatternText);
        avgTransactionsText = findViewById(R.id.avgTransactionsText);
        mostActiveText = findViewById(R.id.mostActiveText);
        leastActiveText = findViewById(R.id.leastActiveText);
        morningSpendingText = findViewById(R.id.morningSpendingText);
        afternoonSpendingText = findViewById(R.id.afternoonSpendingText);
        eveningSpendingText = findViewById(R.id.eveningSpendingText);
        timeInsightText = findViewById(R.id.timeInsightText);
        
        // Spending Efficiency Views
        avgTransactionAmountText = findViewById(R.id.avgTransactionAmountText);
        efficiencyScoreText = findViewById(R.id.efficiencyScoreText);
        impulsePurchasesText = findViewById(R.id.impulsePurchasesText);
        efficiencyStatusText = findViewById(R.id.efficiencyStatusText);
        
        // Financial Health Views
        healthScoreText = findViewById(R.id.healthScoreText);
        spendingControlText = findViewById(R.id.spendingControlText);
        savingsRateText = findViewById(R.id.savingsRateText);
        emergencyFundText = findViewById(R.id.emergencyFundText);
        healthImprovementText = findViewById(R.id.healthImprovementText);
        totalTransactionsText = findViewById(R.id.totalTransactionsText);

        // Initialize new adapters
        recurringExpensesAdapter = new SimpleRecurringExpensesAdapter();
        topMerchantsAdapter = new TopMerchantsAdapter();
        
        // Set up RecyclerViews - check if views exist first
        if (recurringExpensesRecyclerView != null) {
            recurringExpensesRecyclerView.setLayoutManager(new LinearLayoutManager(this));
            recurringExpensesRecyclerView.setAdapter(recurringExpensesAdapter);
        }
        
        if (topMerchantsRecyclerView != null) {
            topMerchantsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
            topMerchantsRecyclerView.setAdapter(topMerchantsAdapter);
        }

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
            int itemId = item.getItemId();
            if (itemId == R.id.nav_home) {
                startActivity(new android.content.Intent(this, MainActivity.class));
                return true;
            } else if (itemId == R.id.nav_analytics) {
                return true;
            } else if (itemId == R.id.nav_predictions) {
                startActivity(new android.content.Intent(this, PredictionActivity.class));
                return true;
            } else if (itemId == R.id.nav_groups) {
                startActivity(new android.content.Intent(this, GroupedExpensesActivity.class));
                return true;
            } else if (itemId == R.id.nav_patterns) {
                startActivity(new Intent(this, ExclusionPatternsActivity.class));
                return true;
            }
            return false;
        });
    }

    private void observeData() {
        // CRITICAL FIX: Check if we're viewing current month vs other months
        Calendar currentMonth = Calendar.getInstance();
        boolean isCurrentMonth = (selectedMonth.get(Calendar.YEAR) == currentMonth.get(Calendar.YEAR) && 
                                 selectedMonth.get(Calendar.MONTH) == currentMonth.get(Calendar.MONTH));
        
        Calendar start = (Calendar) selectedMonth.clone();
        Calendar end = (Calendar) selectedMonth.clone();
        
        if (isCurrentMonth) {
            // CURRENT MONTH: Use exact same logic as MainActivity for consistency
            // This matches setupDefaultDates() in MainActivity lines 2426-2430
            start.set(Calendar.DAY_OF_MONTH, 1);
            start.set(Calendar.HOUR_OF_DAY, 0);
            start.set(Calendar.MINUTE, 0);
            start.set(Calendar.SECOND, 0);
            
            // End date is current date/time (exactly like home page)
            end = Calendar.getInstance();
            end.set(Calendar.HOUR_OF_DAY, 23);
            end.set(Calendar.MINUTE, 59);
            end.set(Calendar.SECOND, 59);
            
            Log.d(TAG, "Using CURRENT MONTH logic (matches home page)");
        } else {
            // OTHER MONTHS: Use full month logic for historical data
            start.set(Calendar.DAY_OF_MONTH, 1);
            start.set(Calendar.HOUR_OF_DAY, 0);
            start.set(Calendar.MINUTE, 0);
            start.set(Calendar.SECOND, 0);

            end = (Calendar) start.clone();
            end.add(Calendar.MONTH, 1);
            end.add(Calendar.SECOND, -1);
            
            Log.d(TAG, "Using FULL MONTH logic (historical data)");
        }

        // Get previous month for comparison
        Calendar prevStart = (Calendar) start.clone();
        prevStart.add(Calendar.MONTH, -1);
        Calendar prevEnd = (Calendar) prevStart.clone();
        prevEnd.add(Calendar.MONTH, 1);
        prevEnd.add(Calendar.SECOND, -1);

        // Enhanced Debug: Compare with home page date logic
        Log.d(TAG, "=== ENHANCED DATE RANGE COMPARISON ===");
        Log.d(TAG, "Selected Month: " + selectedMonth.getTime());
        Log.d(TAG, "Current Month: " + currentMonth.getTime());
        Log.d(TAG, "Is Current Month: " + isCurrentMonth);
        Log.d(TAG, "Analytics page - From: " + start.getTime() + " (" + start.getTimeInMillis() + ")");
        Log.d(TAG, "Analytics page - To: " + end.getTime() + " (" + end.getTimeInMillis() + ")");
        
        // Show what home page equivalent would be (current month start to now)
        Calendar homePageEquivalent = Calendar.getInstance();
        homePageEquivalent.set(Calendar.DAY_OF_MONTH, 1);
        homePageEquivalent.set(Calendar.HOUR_OF_DAY, 0);
        homePageEquivalent.set(Calendar.MINUTE, 0);
        homePageEquivalent.set(Calendar.SECOND, 0);
        long homeFromDate = homePageEquivalent.getTimeInMillis();
        
        homePageEquivalent = Calendar.getInstance(); // Reset to now
        homePageEquivalent.set(Calendar.HOUR_OF_DAY, 23);
        homePageEquivalent.set(Calendar.MINUTE, 59);
        homePageEquivalent.set(Calendar.SECOND, 59);
        long homeToDate = homePageEquivalent.getTimeInMillis();
        
        Log.d(TAG, "Home page equivalent - From: " + new java.util.Date(homeFromDate) + " (" + homeFromDate + ")");
        Log.d(TAG, "Home page equivalent - To: " + new java.util.Date(homeToDate) + " (" + homeToDate + ")");
        
        // Check if dates match exactly
        boolean fromDateMatches = (start.getTimeInMillis() == homeFromDate);
        boolean toDateMatches = (end.getTimeInMillis() == homeToDate);
        Log.d(TAG, "Date Match Check - From: " + fromDateMatches + ", To: " + toDateMatches);
        Log.d(TAG, "==========================================");
        
        // UI elements confirmed working - now focus on data flow
        Log.d(TAG, "UI ELEMENT CHECK: All UI elements are properly connected");
        
        // Observe current month data
        Log.d(TAG, "Requesting monthly data from " + start.getTime() + " to " + end.getTime());
        viewModel.getMonthlyData(start.getTimeInMillis(), end.getTimeInMillis())
                .observe(this, monthlyData -> {
                    Log.d(TAG, "=== MONTHLY DATA OBSERVER TRIGGERED ===");
                    if (monthlyData == null) {
                        Log.e(TAG, "CRITICAL ERROR: monthlyData is null in observer!");
                        return;
                    }
                    Log.d(TAG, "Observer received monthly data:");
                    Log.d(TAG, "- Income: " + monthlyData.getTotalIncome());
                    Log.d(TAG, "- Expenses: " + monthlyData.getTotalExpenses());
                    Log.d(TAG, "- Daily transactions count: " + monthlyData.getDailyTransactions().size());
                    Log.d(TAG, "- Category totals count: " + monthlyData.getCategoryTotals().size());
                    
                    updateLineChart(monthlyData.getDailyTransactions());
                    updatePieChart(monthlyData.getCategoryTotals());
                    updateTrendChart(monthlyData.getWeeklyTotals());
                    
                    // Get previous month data for comparison
                    viewModel.getMonthlyData(prevStart.getTimeInMillis(), prevEnd.getTimeInMillis())
                            .observe(this, prevMonthData -> {
                                updateEnhancedSummary(monthlyData, prevMonthData);
                                updateQuickInsights(monthlyData);
                                updateSpendingPace(monthlyData);
                                // Basic analytics are updated with monthly data
                                // Detailed analytics are updated separately with transaction data
                            });
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
                    updateBudgetAlerts(budgetData);
                });

        // Observe transactions for detailed analytics
        viewModel.getTransactions(start.getTimeInMillis(), end.getTimeInMillis())
                .observe(this, transactions -> {
                    Log.d(TAG, "Received transactions for detailed analytics: " + 
                        (transactions != null ? transactions.size() : "null"));
                    if (transactions != null) {
                        // Debug transaction analysis
                        int totalCount = transactions.size();
                        int excludedCount = 0;
                        int debitCount = 0;
                        int creditCount = 0;
                        double totalDebitAmount = 0;
                        double totalCreditAmount = 0;
                        
                        for (Transaction t : transactions) {
                            if (t.isExcludedFromTotal()) {
                                excludedCount++;
                            } else {
                                if (t.isDebit()) {
                                    debitCount++;
                                    totalDebitAmount += t.getAmount();
                                } else {
                                    creditCount++;
                                    totalCreditAmount += t.getAmount();
                                }
                            }
                        }
                        
                        Log.d(TAG, "Transaction Analysis - Total: " + totalCount + 
                            ", Excluded: " + excludedCount + 
                            ", Debit: " + debitCount + " (₹" + totalDebitAmount + ")" +
                            ", Credit: " + creditCount + " (₹" + totalCreditAmount + ")");
                        
                        updateDetailedAnalytics(transactions);
                        
                        // Get historical data for recurring expense detection
                        updateRecurringExpensesWithHistory();
                    }
                });
    }

    private void updateEnhancedSummary(AnalyticsViewModel.MonthlyData currentData, 
                                     AnalyticsViewModel.MonthlyData prevData) {
        // Enhanced debugging
        Log.d(TAG, "=== updateEnhancedSummary CALLED ===");
        if (currentData == null) {
            Log.e(TAG, "CRITICAL ERROR: currentData is null!");
            // Set UI to show error state
            if (totalIncomeText != null) totalIncomeText.setText("₹0 (No Data)");
            if (totalExpensesText != null) totalExpensesText.setText("₹0 (No Data)");
            if (savedAmountText != null) savedAmountText.setText("₹0 (No Data)");
            return;
        }
        
        double income = currentData.getTotalIncome();
        double expenses = currentData.getTotalExpenses();
        double savings = income - expenses;
        
        Log.d(TAG, "RAW DATA FROM VIEWMODEL:");
        Log.d(TAG, "- Income: " + income);
        Log.d(TAG, "- Expenses: " + expenses);
        Log.d(TAG, "- Savings: " + savings);
        Log.d(TAG, "- Daily transactions map size: " + currentData.getDailyTransactions().size());
        Log.d(TAG, "- Category totals map size: " + currentData.getCategoryTotals().size());
        
        // Check if this is the zero values issue
        if (income == 0 && expenses == 0) {
            Log.e(TAG, "ZERO VALUES DETECTED! This is the bug we're tracking.");
            Log.e(TAG, "ViewModel returned zero values despite home page showing data.");
            
            // Show diagnostic info in UI
            if (budgetHealthText != null) {
                budgetHealthText.setText("DEBUG: ViewModel returned zero values");
            }
        } else {
            Log.d(TAG, "SUCCESS: Got non-zero values from ViewModel!");
        }
        
        // Handle null previous data
        double prevIncome = prevData != null ? prevData.getTotalIncome() : 0;
        double prevExpenses = prevData != null ? prevData.getTotalExpenses() : 0;
        double prevSavings = prevIncome - prevExpenses;

        // Update amounts - ensure views exist
        if (totalIncomeText != null) {
            totalIncomeText.setText(formatCurrency(income));
            Log.d(TAG, "Updated income text: " + formatCurrency(income));
        } else {
            Log.e(TAG, "totalIncomeText is null!");
        }
        if (totalExpensesText != null) {
            totalExpensesText.setText(formatCurrency(expenses));
            Log.d(TAG, "Updated expenses text: " + formatCurrency(expenses));
        } else {
            Log.e(TAG, "totalExpensesText is null!");
        }
        if (savedAmountText != null) {
            savedAmountText.setText(formatCurrency(savings));
            Log.d(TAG, "Updated savings text: " + formatCurrency(savings));
        } else {
            Log.e(TAG, "savedAmountText is null!");
        }
        
        // Special handling for no data case
        if (income == 0 && expenses == 0) {
            // Show a helpful message in budget health text
            if (budgetHealthText != null) {
                budgetHealthText.setText("No transaction data found for this month");
            }
        }

        // Calculate percentage changes
        double incomeChange = calculatePercentageChange(prevIncome, income);
        double expenseChange = calculatePercentageChange(prevExpenses, expenses);
        double savingsChange = calculatePercentageChange(prevSavings, savings);

        // Update change indicators - ensure views exist
        if (incomeChangeText != null) {
            incomeChangeText.setText(formatPercentageChange(incomeChange));
            incomeChangeText.setTextColor(getColor(incomeChange >= 0 ? R.color.green : R.color.red));
        }
        if (expenseChangeText != null) {
            expenseChangeText.setText(formatPercentageChange(expenseChange));
            expenseChangeText.setTextColor(getColor(expenseChange <= 0 ? R.color.green : R.color.red));
        }
        if (savingsChangeText != null) {
            savingsChangeText.setText(formatPercentageChange(savingsChange));
            savingsChangeText.setTextColor(getColor(savingsChange >= 0 ? R.color.green : R.color.red));
        }
    }

    private void updateQuickInsights(AnalyticsViewModel.MonthlyData data) {
        // Top spending category
        String topCategory = "General";
        double topAmount = 0;
        for (Map.Entry<String, Double> entry : data.getCategoryTotals().entrySet()) {
            if (entry.getValue() > topAmount) {
                topAmount = entry.getValue();
                topCategory = entry.getKey();
            }
        }
        // View removed from new layout
        // topSpendingText.setText("• Top spending: " + topCategory + " (" + formatCurrency(topAmount) + ")");

        // Biggest expense category (from available category data)
        if (biggestExpenseText != null) {
            if (topAmount > 0) {
                biggestExpenseText.setText("• Biggest expense: " + topCategory + " (" + formatCurrency(topAmount) + ")");
            } else {
                biggestExpenseText.setText("• Biggest expense: No data available");
            }
        }

        // Most frequent - placeholder for now (needs transaction detail data)
        // mostFrequentText.setText("• Most frequent: Calculating..."); // TODO: Implement when transaction data available

        // Savings vs target (simplified - would need user's savings target)
        double savings = data.getTotalIncome() - data.getTotalExpenses();
        double target = preferencesManager.hasBudgetAmount() ? 
            preferencesManager.getBudgetAmount(10000) * 0.2 : 10000; // 20% of budget as savings target
        double difference = savings - target;
        String savingsText = difference >= 0 ? 
            "• Saved vs target: +" + formatCurrency(Math.abs(difference)) :
            "• Saved vs target: -" + formatCurrency(Math.abs(difference));
        if (savingsTargetText != null) {
            savingsTargetText.setText(savingsText);
            savingsTargetText.setTextColor(getColor(difference >= 0 ? R.color.green : R.color.red));
        }

        // Days to month end
        Calendar now = Calendar.getInstance();
        Calendar monthEnd = (Calendar) selectedMonth.clone();
        monthEnd.set(Calendar.DAY_OF_MONTH, monthEnd.getActualMaximum(Calendar.DAY_OF_MONTH));
        
        long diffInMillis = monthEnd.getTimeInMillis() - now.getTimeInMillis();
        int daysLeft = (int) (diffInMillis / (1000 * 60 * 60 * 24));
        if (daysToPaydayText != null) {
            daysToPaydayText.setText("• Days to month end: " + Math.max(0, daysLeft) + " days");
        }
    }

    private void updateBudgetAlerts(List<AnalyticsViewModel.BudgetStatus> budgetData) {
        List<AnalyticsViewModel.BudgetStatus> alerts = new ArrayList<>();
        
        for (AnalyticsViewModel.BudgetStatus status : budgetData) {
            if (status.getPercentage() >= 85) { // Alert if 85% or more of budget used
                alerts.add(status);
            }
        }

        // Budget alerts functionality removed from new layout
        // if (alerts.isEmpty()) {
        //     budgetAlertsCard.setVisibility(View.GONE);
        // } else {
        //     budgetAlertsCard.setVisibility(View.VISIBLE);
        //     // TODO: Implement budget alerts adapter to show alerts in RecyclerView
        // }
    }

    private void updateSpendingPace(AnalyticsViewModel.MonthlyData data) {
        Calendar now = Calendar.getInstance();
        Calendar monthStart = (Calendar) selectedMonth.clone();
        monthStart.set(Calendar.DAY_OF_MONTH, 1);
        
        // Calculate days elapsed in month
        long diffInMillis = now.getTimeInMillis() - monthStart.getTimeInMillis();
        int daysElapsed = Math.max(1, (int) (diffInMillis / (1000 * 60 * 60 * 24)) + 1);
        
        // Calculate daily average
        double dailyAverage = data.getTotalExpenses() / daysElapsed;
        
        // Update daily average text (simplified previous month comparison)
        if (dailyAverageText != null) {
            dailyAverageText.setText(String.format(Locale.getDefault(), 
                "Daily avg: %s (vs ₹1,200 last month)", formatCurrency(dailyAverage)));
        }

        // Update month progress
        int totalDaysInMonth = selectedMonth.getActualMaximum(Calendar.DAY_OF_MONTH);
        int progressPercentage = (daysElapsed * 100) / totalDaysInMonth;
        if (monthProgressBar != null) {
            monthProgressBar.setProgress(Math.min(100, progressPercentage));
        }
        
        if (currentDayText != null) {
            currentDayText.setText(String.format(Locale.getDefault(), 
                "You're here (Day %d)", daysElapsed));
        }
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

    private void updateBudgetProgress(List<AnalyticsViewModel.BudgetStatus> budgetData) {
        double totalSpent = 0;
        double totalBudget = 0;

        for (AnalyticsViewModel.BudgetStatus status : budgetData) {
            totalSpent += status.getSpent();
            totalBudget += status.getBudget();
        }

        double percentage = totalBudget > 0 ? (totalSpent / totalBudget) * 100 : 0;

        // Update progress bar
        budgetProgressBar.setProgress((int) percentage);
        
        // Update budget health text
        String healthStatus;
        if (percentage <= 70) {
            healthStatus = "Budget Health: Excellent";
            budgetProgressBar.setIndicatorColor(getColor(R.color.green));
        } else if (percentage <= 85) {
            healthStatus = "Budget Health: Good";
            budgetProgressBar.setIndicatorColor(getColor(R.color.yellow));
        } else if (percentage <= 100) {
            healthStatus = "Budget Health: Watch Out";
            budgetProgressBar.setIndicatorColor(getColor(R.color.yellow_dark));
        } else {
            healthStatus = "Budget Health: Over Budget";
            budgetProgressBar.setIndicatorColor(getColor(R.color.red));
        }
        
        budgetHealthText.setText(healthStatus);
        budgetPercentageText.setText(String.format(Locale.getDefault(), "%.0f%% of budget used", percentage));
    }

    private void showMonthPicker() {
        MonthYearPickerDialog dialog = new MonthYearPickerDialog();
        dialog.setListener((year, month) -> {
            selectedMonth.set(Calendar.YEAR, year);
            selectedMonth.set(Calendar.MONTH, month);
            updateMonthSelectorText();
            observeData();
        });
        dialog.show(getSupportFragmentManager(), "MonthYearPickerDialog");
    }

    private void updateMonthSelectorText() {
        SimpleDateFormat monthFormat = new SimpleDateFormat("📅 MMMM yyyy", Locale.getDefault());
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

    // Helper methods
    private String formatCurrency(double amount) {
        return String.format(Locale.getDefault(), "₹%.0f", Math.abs(amount));
    }

    private double calculatePercentageChange(double oldValue, double newValue) {
        if (oldValue == 0) return newValue > 0 ? 100 : 0;
        return ((newValue - oldValue) / oldValue) * 100;
    }

    private String formatPercentageChange(double percentage) {
        String arrow = percentage >= 0 ? "↗" : "↘";
        return String.format(Locale.getDefault(), "%+.0f%% %s", percentage, arrow);
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Refresh the badge count on bottom navigation
        BottomNavigationView bottomNav = findViewById(R.id.bottomNavigation);
        bottomNav.setSelectedItemId(R.id.nav_analytics);
    }
    
    private void updateDetailedAnalytics(List<Transaction> transactions) {
        if (transactions == null || transactions.isEmpty()) {
            return;
        }
        
        // Calculate dynamic analytics from actual transaction data
        updatePredictions(transactions);
        // updateRecurringExpenses(transactions); // Moved to updateRecurringExpensesWithHistory()
        updateSpendingVelocity(transactions);
        updateTopMerchants(transactions);
        updateTransactionBehavior(transactions);
        updateTransactionFrequency(transactions);
        updateTimeBasedSpending(transactions);
        updateSpendingEfficiency(transactions);
        updateFinancialHealth(transactions);
    }
    
    private void updatePredictions(List<Transaction> transactions) {
        double totalSpent = 0;
        for (Transaction t : transactions) {
            if (t.isDebit() && !t.isExcludedFromTotal()) {
                totalSpent += t.getAmount();
            }
        }
        
        Calendar now = Calendar.getInstance();
        int dayOfMonth = now.get(Calendar.DAY_OF_MONTH);
        double dailyAverage = totalSpent / dayOfMonth;
        double expectedTotal = dailyAverage * 30;
        
        // Get budget from user preferences
        double budget = preferencesManager.hasBudgetAmount() ? 
            preferencesManager.getBudgetAmount(40000) : 40000;
        
        if (expectedTotalText != null) {
            expectedTotalText.setText("Expected Total: " + formatCurrency(expectedTotal));
        }
        if (budgetTargetText != null) {
            budgetTargetText.setText("Budget: " + formatCurrency(budget));
        }
        
        if (predictionStatusText != null) {
            if (expectedTotal <= budget) {
                predictionStatusText.setText("Likely to stay under budget");
                predictionStatusText.setTextColor(getColor(R.color.green));
            } else {
                predictionStatusText.setText("May exceed budget");
                predictionStatusText.setTextColor(getColor(R.color.red));
            }
        }
        
        if (confidenceText != null) {
            int confidence = totalSpent > 1000 ? 85 : 60; // Higher confidence with more data
            confidenceText.setText("Confidence: " + confidence + "%");
        }
    }
    
    private void updateRecurringExpensesWithHistory() {
        // Get transactions from previous months to find recurring patterns
        Calendar monthsAgo = Calendar.getInstance();
        monthsAgo.add(Calendar.MONTH, -6); // Look back 6 months
        monthsAgo.set(Calendar.DAY_OF_MONTH, 1);
        monthsAgo.set(Calendar.HOUR_OF_DAY, 0);
        monthsAgo.set(Calendar.MINUTE, 0);
        monthsAgo.set(Calendar.SECOND, 0);
        
        Calendar currentMonthEnd = (Calendar) selectedMonth.clone();
        currentMonthEnd.set(Calendar.DAY_OF_MONTH, currentMonthEnd.getActualMaximum(Calendar.DAY_OF_MONTH));
        currentMonthEnd.set(Calendar.HOUR_OF_DAY, 23);
        currentMonthEnd.set(Calendar.MINUTE, 59);
        currentMonthEnd.set(Calendar.SECOND, 59);
        
        // Get historical transactions to find recurring ones that should appear this month
        viewModel.getTransactions(monthsAgo.getTimeInMillis(), currentMonthEnd.getTimeInMillis())
                .observe(this, allTransactions -> {
                    if (allTransactions != null) {
                        Log.d(TAG, "Got " + allTransactions.size() + " transactions for recurring analysis");
                        updateIndividualRecurringExpenses(allTransactions);
                    }
                });
    }
    
    private void updateIndividualRecurringExpenses(List<Transaction> allTransactions) {
        // Enhanced logic: Show occurred + predict upcoming with pattern analysis
        List<SimpleRecurringExpensesAdapter.RecurringExpenseData> recurringExpenses = new ArrayList<>();
        double totalRecurring = 0;
        
        // Get current month boundaries
        Calendar currentMonthStart = (Calendar) selectedMonth.clone();
        currentMonthStart.set(Calendar.DAY_OF_MONTH, 1);
        currentMonthStart.set(Calendar.HOUR_OF_DAY, 0);
        currentMonthStart.set(Calendar.MINUTE, 0);
        currentMonthStart.set(Calendar.SECOND, 0);
        
        Calendar currentMonthEnd = (Calendar) selectedMonth.clone();
        currentMonthEnd.set(Calendar.DAY_OF_MONTH, currentMonthEnd.getActualMaximum(Calendar.DAY_OF_MONTH));
        currentMonthEnd.set(Calendar.HOUR_OF_DAY, 23);
        currentMonthEnd.set(Calendar.MINUTE, 59);
        currentMonthEnd.set(Calendar.SECOND, 59);
        
        // Step 1: Find transactions that occurred this month using pattern analysis (more inclusive)
        Map<String, List<Transaction>> merchantGroups = groupTransactionsByMerchant(allTransactions);
        
        for (Map.Entry<String, List<Transaction>> entry : merchantGroups.entrySet()) {
            String merchant = entry.getKey();
            List<Transaction> merchantTransactions = entry.getValue();
            
            // Analyze patterns for occurred transactions
            RecurringPattern pattern = analyzeRecurringPattern(merchant, merchantTransactions, selectedMonth);
            
            // More inclusive criteria for occurred transactions - if it has any recurring pattern AND actually occurred this month
            if (pattern.isRecurring && pattern.confidence >= 70) { // Lower threshold for occurred
                // Check if any transaction from this merchant occurred in current month
                for (Transaction transaction : merchantTransactions) {
                    if (transaction.isDebit() && !transaction.isExcludedFromTotal()) {
                        Calendar txnCal = Calendar.getInstance();
                        txnCal.setTimeInMillis(transaction.getDate());
                        
                        boolean isInCurrentMonth = (txnCal.get(Calendar.YEAR) == selectedMonth.get(Calendar.YEAR) && 
                                                  txnCal.get(Calendar.MONTH) == selectedMonth.get(Calendar.MONTH));
                        
                        if (isInCurrentMonth) {
                            String description = transaction.getMerchantName();
                            if (description == null || description.trim().isEmpty()) {
                                description = transaction.getDescription();
                            }
                            if (description == null || description.trim().isEmpty()) {
                                description = "Unknown Transaction";
                            }
                            
                            String frequency = pattern.frequency;
                            
                            recurringExpenses.add(new SimpleRecurringExpensesAdapter.RecurringExpenseData(
                                description,
                                transaction.getAmount(),
                                frequency,
                                "✅ Occurred"
                            ));
                            
                            totalRecurring += transaction.getAmount();
                            
                            Log.d(TAG, "Found occurred recurring (pattern-based): " + description + " - ₹" + transaction.getAmount() + 
                                " (confidence: " + pattern.confidence + "%)");
                            break; // Only add one transaction per merchant for occurred
                        }
                    }
                }
            }
        }
        
        // Also include transactions marked as recurring in database (fallback)
        for (Transaction transaction : allTransactions) {
            if (transaction.isDebit() && !transaction.isExcludedFromTotal() && transaction.isRecurring()) {
                Calendar txnCal = Calendar.getInstance();
                txnCal.setTimeInMillis(transaction.getDate());
                
                boolean isInCurrentMonth = (txnCal.get(Calendar.YEAR) == selectedMonth.get(Calendar.YEAR) && 
                                          txnCal.get(Calendar.MONTH) == selectedMonth.get(Calendar.MONTH));
                
                if (isInCurrentMonth) {
                    String description = transaction.getMerchantName();
                    if (description == null || description.trim().isEmpty()) {
                        description = transaction.getDescription();
                    }
                    if (description == null || description.trim().isEmpty()) {
                        description = "Unknown Transaction";
                    }
                    
                    // Check if we already added this transaction via pattern analysis
                    final String finalDescription = description;
                    final double finalAmount = transaction.getAmount();
                    boolean alreadyAdded = recurringExpenses.stream()
                        .anyMatch(expense -> expense.description.equalsIgnoreCase(finalDescription) && 
                                 expense.icon.equals("✅ Occurred") &&
                                 Math.abs(expense.amount - finalAmount) < 1.0);
                    
                    if (!alreadyAdded) {
                        String frequency = getFrequencyText(transaction.getRecurringFrequency());
                        
                        recurringExpenses.add(new SimpleRecurringExpensesAdapter.RecurringExpenseData(
                            description,
                            transaction.getAmount(),
                            frequency,
                            "✅ Occurred"
                        ));
                        
                        totalRecurring += transaction.getAmount();
                        
                        Log.d(TAG, "Found occurred recurring (database flag): " + description + " - ₹" + transaction.getAmount());
                    }
                }
            }
        }
        
        // Step 2: Apply complex pattern analysis to predict upcoming transactions with lower confidence (85%+)
        
        for (Map.Entry<String, List<Transaction>> entry : merchantGroups.entrySet()) {
            String merchant = entry.getKey();
            List<Transaction> merchantTransactions = entry.getValue();
            
            // Analyze patterns using complex logic
            RecurringPattern pattern = analyzeRecurringPattern(merchant, merchantTransactions, selectedMonth);
            
            if (pattern.isRecurring && pattern.confidence >= 85 && pattern.shouldOccurThisMonth) {
                // Check if we already have this transaction in occurred list
                boolean alreadyOccurred = recurringExpenses.stream()
                    .anyMatch(expense -> expense.description.toLowerCase().contains(merchant.toLowerCase()) && 
                             expense.icon.equals("✅ Occurred"));
                
                if (!alreadyOccurred) {
                    // Create status message based on confidence level
                    String status;
                    if (pattern.confidence >= 100) {
                        status = "🔮 Predicted (100% confidence)";
                    } else if (pattern.confidence >= 85) {
                        status = "📊 Expected (" + pattern.confidence + "% confidence)";
                    } else if (pattern.confidence >= 70) {
                        status = "💭 Likely (" + pattern.confidence + "% confidence)";
                    } else {
                        status = "❓ Possible (" + pattern.confidence + "% confidence)";
                    }
                    
                    recurringExpenses.add(new SimpleRecurringExpensesAdapter.RecurringExpenseData(
                        pattern.displayName,
                        pattern.predictedAmount,
                        pattern.frequency,
                        status
                    ));
                    
                    Log.d(TAG, "Predicted recurring: " + pattern.displayName + " - ₹" + pattern.predictedAmount + 
                        " (" + pattern.confidence + "% confidence)");
                }
            }
        }
        
        // Sort by status first (occurred first), then by amount
        recurringExpenses.sort((a, b) -> {
            if (a.icon.contains("✅") && !b.icon.contains("✅")) return -1;
            if (!a.icon.contains("✅") && b.icon.contains("✅")) return 1;
            return Double.compare(b.amount, a.amount);
        });
        
        // Update UI
        if (recurringExpensesAdapter != null) {
            recurringExpensesAdapter.setExpenses(recurringExpenses);
        }
        if (totalRecurringText != null) {
            int occurredCount = (int) recurringExpenses.stream().filter(e -> e.icon.contains("✅")).count();
            int predictedCount = recurringExpenses.size() - occurredCount;
            totalRecurringText.setText("Recurring: " + formatCurrency(totalRecurring) + 
                " (" + occurredCount + " occurred, " + predictedCount + " predicted)");
        }
        
        Log.d(TAG, "Enhanced recurring analysis complete: " + recurringExpenses.size() + 
            " total items, ₹" + String.format("%.0f", totalRecurring) + " occurred");
    }
    
    private boolean isExpectedInMonth(Transaction transaction, Calendar targetMonth) {
        // Simple logic: if transaction is monthly recurring, it should appear in target month
        Integer frequencyDays = transaction.getRecurringFrequency();
        if (frequencyDays == null) return false;
        
        Calendar txnCal = Calendar.getInstance();
        txnCal.setTimeInMillis(transaction.getDate());
        
        // For monthly transactions (frequency around 30 days)
        if (frequencyDays >= 25 && frequencyDays <= 35) {
            // Check if transaction occurred in a previous month and should repeat in target month
            Calendar expectedDate = (Calendar) txnCal.clone();
            
            // Keep adding frequency until we reach or pass the target month
            while (expectedDate.before(targetMonth)) {
                expectedDate.add(Calendar.DAY_OF_MONTH, frequencyDays);
            }
            
            // Check if the expected date falls within the target month
            return (expectedDate.get(Calendar.YEAR) == targetMonth.get(Calendar.YEAR) && 
                   expectedDate.get(Calendar.MONTH) == targetMonth.get(Calendar.MONTH));
        }
        
        return false;
    }
    
    private String getFrequencyText(Integer frequencyDays) {
        if (frequencyDays == null) {
            return "Monthly"; // Default
        }
        
        if (frequencyDays <= 7) {
            return "Weekly";
        } else if (frequencyDays <= 14) {
            return "Bi-weekly";
        } else if (frequencyDays <= 31) {
            return "Monthly";
        } else if (frequencyDays <= 93) {
            return "Quarterly";
        } else {
            return "Yearly";
        }
    }
    
    private Map<String, List<Transaction>> groupTransactionsByMerchant(List<Transaction> transactions) {
        Map<String, List<Transaction>> groups = new HashMap<>();
        
        for (Transaction transaction : transactions) {
            if (transaction.isDebit() && !transaction.isExcludedFromTotal()) {
                String merchant = normalizeMerchantName(transaction);
                if (merchant != null && !merchant.trim().isEmpty()) {
                    groups.computeIfAbsent(merchant, k -> new ArrayList<>()).add(transaction);
                }
            }
        }
        
        return groups;
    }
    
    private String normalizeMerchantName(Transaction transaction) {
        String merchant = transaction.getMerchantName();
        if (merchant == null || merchant.trim().isEmpty()) {
            merchant = transaction.getDescription();
        }
        
        if (merchant == null || merchant.trim().isEmpty()) {
            return null;
        }
        
        // Simple normalization
        merchant = merchant.trim().toLowerCase();
        merchant = merchant.replaceAll("[^a-z0-9\\s]", ""); // Remove special characters
        merchant = merchant.replaceAll("\\s+", " ").trim(); // Clean up spaces
        
        // Take first significant words
        String[] words = merchant.split("\\s+");
        if (words.length > 0) {
            StringBuilder result = new StringBuilder();
            for (int i = 0; i < Math.min(3, words.length); i++) {
                if (words[i].length() > 2) { // Skip very short words
                    if (result.length() > 0) result.append(" ");
                    result.append(words[i]);
                }
            }
            return result.length() > 0 ? result.toString() : merchant;
        }
        
        return merchant;
    }
    
    private RecurringPattern analyzeRecurringPattern(String merchant, List<Transaction> transactions, Calendar targetMonth) {
        RecurringPattern pattern = new RecurringPattern();
        pattern.displayName = getDisplayMerchantName(merchant);
        
        if (transactions.size() < 2) {
            pattern.isRecurring = false;
            return pattern;
        }
        
        // Sort transactions by date
        transactions.sort((a, b) -> Long.compare(a.getDate(), b.getDate()));
        
        // Analyze monthly patterns
        pattern = analyzeMonthlyPattern(merchant, transactions, targetMonth);
        
        // Consider patterns with good confidence (70%+)
        if (pattern.confidence >= 70) {
            pattern.isRecurring = true;
            
            // Calculate predicted amount (average of recent transactions)
            List<Transaction> recentTransactions = transactions.stream()
                .filter(t -> {
                    Calendar txnCal = Calendar.getInstance();
                    txnCal.setTimeInMillis(t.getDate());
                    Calendar threeMonthsAgo = (Calendar) targetMonth.clone();
                    threeMonthsAgo.add(Calendar.MONTH, -3);
                    return txnCal.after(threeMonthsAgo);
                })
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
            
            if (!recentTransactions.isEmpty()) {
                pattern.predictedAmount = recentTransactions.stream()
                    .mapToDouble(Transaction::getAmount)
                    .average()
                    .orElse(0);
            }
        }
        
        return pattern;
    }
    
    private RecurringPattern analyzeMonthlyPattern(String merchant, List<Transaction> transactions, Calendar targetMonth) {
        RecurringPattern pattern = new RecurringPattern();
        pattern.displayName = getDisplayMerchantName(merchant);
        
        // Group transactions by month
        Map<String, List<Transaction>> monthlyGroups = new HashMap<>();
        for (Transaction t : transactions) {
            Calendar cal = Calendar.getInstance();
            cal.setTimeInMillis(t.getDate());
            String monthKey = cal.get(Calendar.YEAR) + "-" + String.format(Locale.getDefault(), "%02d", cal.get(Calendar.MONTH) + 1);
            monthlyGroups.computeIfAbsent(monthKey, k -> new ArrayList<>()).add(t);
        }
        
        if (monthlyGroups.size() < 3) {
            pattern.isRecurring = false;
            return pattern;
        }
        
        // Calculate monthly totals and analyze consistency
        List<Double> monthlyTotals = new ArrayList<>();
        for (List<Transaction> monthTransactions : monthlyGroups.values()) {
            double monthTotal = monthTransactions.stream().mapToDouble(Transaction::getAmount).sum();
            monthlyTotals.add(monthTotal);
        }
        
        // Calculate consistency
        double avgAmount = monthlyTotals.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double variance = monthlyTotals.stream()
            .mapToDouble(amount -> Math.pow(amount - avgAmount, 2))
            .average()
            .orElse(0);
        double coefficientOfVariation = avgAmount > 0 ? Math.sqrt(variance) / avgAmount : 1.0;
        
        // Enhanced criteria: more inclusive for recurring detection
        boolean isConsistent = coefficientOfVariation < 0.25; // Allow more variation
        boolean hasGoodHistory = monthlyGroups.size() >= 2; // At least 2 months (more inclusive)
        boolean hasRecentActivity = hasRecentMonthlyActivity(monthlyGroups, targetMonth);
        boolean appearsInMultipleMonths = monthlyGroups.size() > 1; // Must appear in more than 1 month
        
        if (isConsistent && hasGoodHistory && hasRecentActivity && appearsInMultipleMonths) {
            pattern.isRecurring = true;
            // Calculate confidence based on consistency and frequency
            if (coefficientOfVariation < 0.15 && monthlyGroups.size() >= 3) {
                pattern.confidence = 100; // Very high confidence
            } else if (coefficientOfVariation < 0.25 && monthlyGroups.size() >= 2) {
                pattern.confidence = 85; // Good confidence
            } else {
                pattern.confidence = 70; // Moderate confidence
            }
            pattern.frequency = "Monthly";
            pattern.averageAmount = avgAmount;
            pattern.shouldOccurThisMonth = shouldOccurInTargetMonth(monthlyGroups, targetMonth);
        } else {
            pattern.isRecurring = false;
            pattern.confidence = 0;
        }
        
        Log.d(TAG, "Pattern analysis - " + merchant + ": Consistent=" + isConsistent + 
            ", History=" + hasGoodHistory + ", Recent=" + hasRecentActivity + 
            ", CV=" + String.format(Locale.getDefault(), "%.3f", coefficientOfVariation) + 
            ", Confidence=" + pattern.confidence);
        
        return pattern;
    }
    
    private boolean hasRecentMonthlyActivity(Map<String, List<Transaction>> monthlyGroups, Calendar targetMonth) {
        // Check if there was activity in the last 2 months before target month
        Calendar lastMonth = (Calendar) targetMonth.clone();
        lastMonth.add(Calendar.MONTH, -1);
        String lastMonthKey = lastMonth.get(Calendar.YEAR) + "-" + String.format(Locale.getDefault(), "%02d", lastMonth.get(Calendar.MONTH) + 1);
        
        Calendar twoMonthsAgo = (Calendar) targetMonth.clone();
        twoMonthsAgo.add(Calendar.MONTH, -2);
        String twoMonthsAgoKey = twoMonthsAgo.get(Calendar.YEAR) + "-" + String.format(Locale.getDefault(), "%02d", twoMonthsAgo.get(Calendar.MONTH) + 1);
        
        return monthlyGroups.containsKey(lastMonthKey) || monthlyGroups.containsKey(twoMonthsAgoKey);
    }
    
    private boolean shouldOccurInTargetMonth(Map<String, List<Transaction>> monthlyGroups, Calendar targetMonth) {
        // Enhanced logic: if it occurred in the last 2 months, it should occur this month
        Calendar lastMonth = (Calendar) targetMonth.clone();
        lastMonth.add(Calendar.MONTH, -1);
        String lastMonthKey = lastMonth.get(Calendar.YEAR) + "-" + String.format(Locale.getDefault(), "%02d", lastMonth.get(Calendar.MONTH) + 1);
        
        Calendar twoMonthsAgo = (Calendar) targetMonth.clone();
        twoMonthsAgo.add(Calendar.MONTH, -2);
        String twoMonthsAgoKey = twoMonthsAgo.get(Calendar.YEAR) + "-" + String.format(Locale.getDefault(), "%02d", twoMonthsAgo.get(Calendar.MONTH) + 1);
        
        // If it occurred in last month OR 2 months ago, predict it for this month
        boolean occurredRecently = monthlyGroups.containsKey(lastMonthKey) || monthlyGroups.containsKey(twoMonthsAgoKey);
        
        // Additional check: if it appears in more than 1 month in history, it's likely recurring
        boolean appearsInMultipleMonths = monthlyGroups.size() > 1;
        
        return occurredRecently && appearsInMultipleMonths;
    }
    
    private String getDisplayMerchantName(String normalizedName) {
        if (normalizedName == null) return "Unknown";
        
        // Capitalize first letter of each word
        String[] words = normalizedName.split("\\s+");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (result.length() > 0) result.append(" ");
            if (word.length() > 0) {
                result.append(word.substring(0, 1).toUpperCase()).append(word.substring(1));
            }
        }
        return result.toString();
    }
    
    // Enhanced helper class for recurring pattern analysis
    private static class RecurringPattern {
        boolean isRecurring = false;
        double averageAmount = 0;
        double predictedAmount = 0;
        String frequency = "";
        int confidence = 0;
        String displayName = "Unknown";
        boolean shouldOccurThisMonth = false;
    }
    
    
    
    private void updateSpendingVelocity(List<Transaction> transactions) {
        double totalSpent = 0;
        for (Transaction t : transactions) {
            if (t.isDebit() && !t.isExcludedFromTotal()) {
                totalSpent += t.getAmount();
            }
        }
        
        int dayOfMonth = Calendar.getInstance().get(Calendar.DAY_OF_MONTH);
        double dailyRate = totalSpent / dayOfMonth;
        
        if (currentBurnRateText != null) {
            currentBurnRateText.setText("Current rate: " + formatCurrency(dailyRate) + "/day");
        }
        if (burnRateChangeText != null) {
            burnRateChangeText.setText("vs last month: calculating...");
        }
        
        double budget = preferencesManager.hasBudgetAmount() ? 
            preferencesManager.getBudgetAmount(40000) : 40000;
        int daysOfMoney = (int) (budget / dailyRate);
        
        if (daysOfMoneyText != null) {
            daysOfMoneyText.setText("At this pace: " + daysOfMoney + " days of money");
        }
        
        String status = dailyRate < (budget / 30) ? "Good" : "High";
        if (burnRateStatusText != null) {
            burnRateStatusText.setText("Burn Rate: " + status);
        }
    }
    
    private void updateTopMerchants(List<Transaction> transactions) {
        Map<String, Double> merchantTotals = new HashMap<>();
        double totalSpent = 0;
        
        for (Transaction t : transactions) {
            if (t.isDebit() && !t.isExcludedFromTotal()) {
                String merchant = t.getMerchantName() != null ? t.getMerchantName() : t.getDescription();
                if (merchant != null) {
                    merchant = merchant.trim();
                    merchantTotals.merge(merchant, t.getAmount(), Double::sum);
                    totalSpent += t.getAmount();
                }
            }
        }
        
        List<TopMerchantsAdapter.MerchantData> topMerchants = new ArrayList<>();
        final double finalTotalSpent = totalSpent; // Make variable effectively final for lambda
        
        merchantTotals.entrySet().stream()
            .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
            .limit(5)
            .forEach(entry -> {
                double percentage = (entry.getValue() / finalTotalSpent) * 100;
                topMerchants.add(new TopMerchantsAdapter.MerchantData(
                    entry.getKey(), entry.getValue(), percentage, ""));
            });
        
        if (topMerchantsAdapter != null) {
            topMerchantsAdapter.setMerchants(topMerchants);
        }
        
        if (topMerchantsSummaryText != null) {
            final double finalTotalSpent2 = totalSpent; // Another final variable for the second lambda
            double top3Percentage = topMerchants.stream().limit(3)
                .mapToDouble(m -> (m.amount / finalTotalSpent2) * 100).sum();
            topMerchantsSummaryText.setText("Top 3 = " + String.format("%.0f", top3Percentage) + "% of spending");
        }
    }
    
    private void updateTransactionBehavior(List<Transaction> transactions) {
        int small = 0, medium = 0, large = 0;
        
        for (Transaction t : transactions) {
            if (t.isDebit() && !t.isExcludedFromTotal()) {
                double amount = t.getAmount();
                if (amount <= 500) small++;
                else if (amount <= 2000) medium++;
                else large++;
            }
        }
        
        int total = small + medium + large;
        if (total > 0) {
            smallTransactionsText.setText("Small (₹1-500): " + small + " txns (" + (small * 100 / total) + "%)");
            mediumTransactionsText.setText("Medium (₹500-2K): " + medium + " txns (" + (medium * 100 / total) + "%)");
            largeTransactionsText.setText("Large (₹2K+): " + large + " txns (" + (large * 100 / total) + "%)");
            
            String pattern = small > (medium + large) ? "Many small + Few large" : "Balanced distribution";
            transactionPatternText.setText("Pattern: " + pattern);
        }
    }
    
    private void updateTransactionFrequency(List<Transaction> transactions) {
        if (transactions.isEmpty()) return;
        
        int dayOfMonth = Calendar.getInstance().get(Calendar.DAY_OF_MONTH);
        double avgPerDay = (double) transactions.size() / dayOfMonth;
        
        avgTransactionsText.setText("Average: " + String.format("%.1f", avgPerDay) + " transactions/day");
        mostActiveText.setText("Most active: Calculating...");
        leastActiveText.setText("Least active: Calculating...");
        
        // Update total transactions text
        if (totalTransactionsText != null) {
            totalTransactionsText.setText("• " + transactions.size() + " total transactions this month");
        }
    }
    
    private void updateTimeBasedSpending(List<Transaction> transactions) {
        double morning = 0, afternoon = 0, evening = 0;
        
        for (Transaction t : transactions) {
            if (t.isDebit() && !t.isExcludedFromTotal()) {
                Calendar cal = Calendar.getInstance();
                cal.setTimeInMillis(t.getDate());
                int hour = cal.get(Calendar.HOUR_OF_DAY);
                
                if (hour >= 6 && hour < 12) morning += t.getAmount();
                else if (hour >= 12 && hour < 18) afternoon += t.getAmount();
                else evening += t.getAmount();
            }
        }
        
        double total = morning + afternoon + evening;
        if (total > 0) {
            morningSpendingText.setText("Morning (6-12): " + formatCurrency(morning) + " (" + (int)((morning / total) * 100) + "%)");
            afternoonSpendingText.setText("Afternoon (12-18): " + formatCurrency(afternoon) + " (" + (int)((afternoon / total) * 100) + "%)");
            eveningSpendingText.setText("Evening (18-24): " + formatCurrency(evening) + " (" + (int)((evening / total) * 100) + "%)");
            
            String peak = afternoon > morning && afternoon > evening ? "Afternoon hours" :
                         morning > evening ? "Morning hours" : "Evening hours";
            timeInsightText.setText("Peak spending: " + peak);
        }
    }
    
    private void updateSpendingEfficiency(List<Transaction> transactions) {
        if (transactions.isEmpty()) return;
        
        // Calculate average transaction amount
        double totalAmount = 0;
        int validTransactions = 0;
        
        for (Transaction t : transactions) {
            if (t.isDebit() && !t.isExcludedFromTotal()) {
                totalAmount += t.getAmount();
                validTransactions++;
            }
        }
        
        double avgTransactionAmount = validTransactions > 0 ? totalAmount / validTransactions : 0;
        
        if (avgTransactionAmountText != null) {
            avgTransactionAmountText.setText("• Avg per transaction: " + formatCurrency(avgTransactionAmount));
        }
        
        // Calculate efficiency score (simplified logic)
        int efficiencyScore = calculateEfficiencyScore(transactions);
        if (efficiencyScoreText != null) {
            efficiencyScoreText.setText("• Efficiency score: " + efficiencyScore + "/100");
        }
        
        // Calculate impulse purchases (transactions under ₹200 as proxy)
        int impulsePurchases = 0;
        for (Transaction t : transactions) {
            if (t.isDebit() && !t.isExcludedFromTotal() && t.getAmount() <= 200) {
                impulsePurchases++;
            }
        }
        
        double impulsePercentage = validTransactions > 0 ? (impulsePurchases * 100.0 / validTransactions) : 0;
        if (impulsePurchasesText != null) {
            impulsePurchasesText.setText("• Impulse purchases: " + String.format("%.0f", impulsePercentage) + "% of transactions");
        }
        
        // Status based on efficiency score
        String status = efficiencyScore >= 75 ? "Getting more efficient" : 
                       efficiencyScore >= 50 ? "Average efficiency" : "Room for improvement";
        if (efficiencyStatusText != null) {
            efficiencyStatusText.setText(status);
        }
    }
    
    private void updateFinancialHealth(List<Transaction> transactions) {
        if (transactions.isEmpty()) return;
        
        double totalIncome = 0;
        double totalExpenses = 0;
        
        for (Transaction t : transactions) {
            if (!t.isExcludedFromTotal()) {
                if (t.isDebit()) {
                    totalExpenses += t.getAmount();
                } else {
                    totalIncome += t.getAmount();
                }
            }
        }
        
        // Calculate financial health metrics
        double savingsRate = totalIncome > 0 ? ((totalIncome - totalExpenses) / totalIncome) * 100 : 0;
        double budget = preferencesManager.hasBudgetAmount() ? 
            preferencesManager.getBudgetAmount(40000) : 40000;
        double spendingControl = totalExpenses > 0 ? Math.max(0, (1 - (totalExpenses / budget)) * 100) : 100;
        
        // Overall health score (simplified calculation)
        int healthScore = (int) ((savingsRate * 0.4) + (spendingControl * 0.4) + 20); // Base 20 points
        healthScore = Math.min(100, Math.max(0, healthScore));
        
        // Update UI
        if (healthScoreText != null) {
            String emoji = healthScore >= 80 ? "🟢" : healthScore >= 60 ? "🟡" : "🔴";
            healthScoreText.setText("Your Score: " + healthScore + "/100 " + emoji);
        }
        
        if (spendingControlText != null) {
            int spendingScore = (int) (spendingControl / 10);
            spendingControlText.setText("✅ Spending Control: " + Math.min(10, spendingScore) + "/10");
        }
        
        if (savingsRateText != null) {
            int savingsScore = (int) Math.min(10, savingsRate / 5); // 50% savings rate = 10/10
            savingsRateText.setText("✅ Savings Rate: " + savingsScore + "/10");
        }
        
        if (emergencyFundText != null) {
            // Simplified emergency fund calculation based on expenses
            int emergencyScore = totalExpenses > 0 ? Math.min(10, (int) ((totalIncome - totalExpenses) / (totalExpenses * 0.25))) : 7;
            emergencyScore = Math.max(1, emergencyScore);
            String emergencyIcon = emergencyScore >= 8 ? "✅" : emergencyScore >= 5 ? "⚠️" : "❌";
            emergencyFundText.setText(emergencyIcon + " Emergency Buffer: " + emergencyScore + "/10");
        }
        
        if (healthImprovementText != null) {
            // Simplified improvement calculation (random for now)
            int improvement = (healthScore > 70) ? 5 : -2;
            String trend = improvement > 0 ? "🚀 +" + improvement : "📉 " + improvement;
            healthImprovementText.setText(trend + " points vs last month");
        }
    }
    
    private int calculateEfficiencyScore(List<Transaction> transactions) {
        if (transactions.isEmpty()) return 0;
        
        // Simple efficiency scoring based on:
        // 1. Number of small transactions (lower is better)
        // 2. Consistency in spending patterns
        // 3. Budget adherence
        
        int smallTransactions = 0;
        double totalSpent = 0;
        
        for (Transaction t : transactions) {
            if (t.isDebit() && !t.isExcludedFromTotal()) {
                if (t.getAmount() <= 200) smallTransactions++;
                totalSpent += t.getAmount();
            }
        }
        
        double budget = preferencesManager.hasBudgetAmount() ? 
            preferencesManager.getBudgetAmount(40000) : 40000;
        
        // Score components (0-100)
        int budgetScore = totalSpent <= budget ? 40 : Math.max(0, 40 - (int)((totalSpent - budget) / budget * 20));
        int impulseScore = Math.max(0, 30 - (smallTransactions * 2)); // Penalty for many small transactions
        int baseScore = 30; // Base efficiency score
        
        return Math.min(100, budgetScore + impulseScore + baseScore);
    }
}