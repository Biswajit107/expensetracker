package com.example.expensetracker.viewmodel;

import android.app.Application;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;

import com.example.expensetracker.models.Transaction;
import com.example.expensetracker.repository.TransactionRepository;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AnalyticsViewModel extends AndroidViewModel {
    private final TransactionRepository repository;
    private final ExecutorService executorService;

    public AnalyticsViewModel(Application application) {
        super(application);
        repository = new TransactionRepository(application);
        executorService = Executors.newSingleThreadExecutor();
    }

    public LiveData<MonthlyData> getMonthlyData(long startDate, long endDate) {
        // FIXED: Use simple MutableLiveData to avoid infinite loop from nested switchMap
        MutableLiveData<MonthlyData> result = new MutableLiveData<>();

        executorService.execute(() -> {
            android.util.Log.d("AnalyticsViewModel", "=== ANALYTICS DATA RETRIEVAL START ===");
            android.util.Log.d("AnalyticsViewModel", "Fetching transactions from " + 
                new java.util.Date(startDate) + " to " + new java.util.Date(endDate));
            android.util.Log.d("AnalyticsViewModel", "Date range in millis: " + startDate + " to " + endDate);
            
            List<Transaction> transactions = null;
            try {
                transactions = repository.getTransactionsBetweenDatesSync(startDate, endDate);
                android.util.Log.d("AnalyticsViewModel", "Repository returned: " + 
                    (transactions != null ? transactions.size() : "NULL") + " transactions");
                
                if (transactions != null && !transactions.isEmpty()) {
                    android.util.Log.d("AnalyticsViewModel", "First transaction: Amount=" + 
                        transactions.get(0).getAmount() + ", Date=" + new java.util.Date(transactions.get(0).getDate()) + 
                        ", Type=" + transactions.get(0).getType() + ", Excluded=" + transactions.get(0).isExcludedFromTotal());
                }
            } catch (Exception e) {
                android.util.Log.e("AnalyticsViewModel", "Error fetching transactions: " + e.getMessage(), e);
            }

            MonthlyData monthlyData = calculateMonthlyData(transactions);
            android.util.Log.d("AnalyticsViewModel", "MonthlyData created - Income: " + monthlyData.getTotalIncome() + 
                ", Expenses: " + monthlyData.getTotalExpenses());
            result.postValue(monthlyData);
            android.util.Log.d("AnalyticsViewModel", "=== ANALYTICS DATA RETRIEVAL END ===");
        });

        return result;
    }

    public LiveData<List<CategoryData>> getCategoryData(long startDate, long endDate) {
        MutableLiveData<List<CategoryData>> result = new MutableLiveData<>();

        executorService.execute(() -> {
            List<Transaction> transactions = repository
                    .getTransactionsBetweenDatesSync(startDate, endDate);

            Map<String, CategoryData> categoryMap = new HashMap<>();

            for (Transaction transaction : transactions) {
                if (transaction.isDebit() && !transaction.isExcludedFromTotal()) {
                    String category = transaction.getCategory();
                    CategoryData data = categoryMap.computeIfAbsent(category,
                            k -> new CategoryData(k, 0.0, 0));

                    data.totalAmount += transaction.getAmount();
                    data.transactionCount++;
                }
            }

            List<CategoryData> categoryList = new ArrayList<>(categoryMap.values());
            categoryList.sort((a, b) -> Double.compare(b.totalAmount, a.totalAmount));

            result.postValue(categoryList);
        });

        return result;
    }

    public LiveData<List<BudgetStatus>> getBudgetData(long startDate, long endDate) {
        MutableLiveData<List<BudgetStatus>> result = new MutableLiveData<>();

        executorService.execute(() -> {
            List<Transaction> transactions = repository
                    .getTransactionsBetweenDatesSync(startDate, endDate);

            Map<String, Double> categorySpending = new HashMap<>();
            for (Transaction transaction : transactions) {
                if (transaction.isDebit() && !transaction.isExcludedFromTotal()) {
                    categorySpending.merge(transaction.getCategory(),
                            transaction.getAmount(), Double::sum);
                }
            }

            // TODO: Get category budgets from preferences or database
            Map<String, Double> categoryBudgets = getDummyCategoryBudgets();

            List<BudgetStatus> budgetStatuses = new ArrayList<>();
            for (Map.Entry<String, Double> entry : categoryBudgets.entrySet()) {
                String category = entry.getKey();
                double budget = entry.getValue();
                double spent = categorySpending.getOrDefault(category, 0.0);

                budgetStatuses.add(new BudgetStatus(category, spent, budget));
            }

            budgetStatuses.sort((a, b) -> Double.compare(b.getPercentage(), a.getPercentage()));
            result.postValue(budgetStatuses);
        });

        return result;
    }

    public LiveData<List<Transaction>> getTransactions(long startDate, long endDate) {
        MutableLiveData<List<Transaction>> result = new MutableLiveData<>();
        
        executorService.execute(() -> {
            List<Transaction> transactions = repository
                    .getTransactionsBetweenDatesSync(startDate, endDate);
            result.postValue(transactions);
        });
        
        return result;
    }

    private MonthlyData calculateMonthlyData(List<Transaction> transactions) {
        Map<Date, Double> dailyTransactions = new TreeMap<>();
        Map<String, Double> categoryTotals = new HashMap<>();
        List<Double> weeklyTotals = new ArrayList<>();
        double totalIncome = 0;
        double totalExpenses = 0;

        // Debug logging
        android.util.Log.d("AnalyticsViewModel", "=== CALCULATE MONTHLY DATA START ===");
        android.util.Log.d("AnalyticsViewModel", "calculateMonthlyData called with " + 
            (transactions != null ? transactions.size() : "null") + " transactions");

        if (transactions == null) {
            android.util.Log.e("AnalyticsViewModel", "TRANSACTIONS IS NULL!");
            return new MonthlyData(dailyTransactions, categoryTotals, weeklyTotals, totalIncome, totalExpenses);
        }
        
        if (transactions.isEmpty()) {
            android.util.Log.w("AnalyticsViewModel", "TRANSACTIONS LIST IS EMPTY!");
            return new MonthlyData(dailyTransactions, categoryTotals, weeklyTotals, totalIncome, totalExpenses);
        }
        
        android.util.Log.d("AnalyticsViewModel", "Processing " + transactions.size() + " transactions...");

        // Initialize data structures
        Calendar cal = Calendar.getInstance();

        // FIXED: Use same exclusion logic as MainActivity (excludes transactions marked as excluded)
        boolean includeExcludedTransactions = false; // Match MainActivity logic: !transaction.isExcludedFromTotal()
        
        // Calculate daily and category totals
        int processedCount = 0;
        int excludedCount = 0;
        for (Transaction transaction : transactions) {
            // Modified condition to optionally include excluded transactions
            boolean shouldInclude = includeExcludedTransactions || !transaction.isExcludedFromTotal();
            
            if (shouldInclude) {
                if (transaction.isExcludedFromTotal()) {
                    android.util.Log.d("AnalyticsViewModel", "Including EXCLUDED transaction: " + transaction.getAmount());
                }
                processedCount++;
                Date date = new Date(transaction.getDate());

                if (transaction.isDebit()) {
                    dailyTransactions.merge(date, transaction.getAmount(), Double::sum);
                    categoryTotals.merge(transaction.getCategory(),
                            transaction.getAmount(), Double::sum);
                    totalExpenses += transaction.getAmount();
                    android.util.Log.d("AnalyticsViewModel", "Added debit: " + transaction.getAmount() + 
                        " Category: " + transaction.getCategory());
                } else {
                    totalIncome += transaction.getAmount();
                    android.util.Log.d("AnalyticsViewModel", "Added income: " + transaction.getAmount());
                }
            } else {
                excludedCount++;
                android.util.Log.d("AnalyticsViewModel", "EXCLUDED transaction: " + transaction.getAmount() + 
                    " Reason: " + transaction.getExclusionSource() + 
                    " IsOtherDebit: " + transaction.isOtherDebit() + 
                    " Description: " + transaction.getDescription());
            }
        }
        
        android.util.Log.d("AnalyticsViewModel", "Processed " + processedCount + " transactions, excluded " + 
            excludedCount + ". Total Income: " + totalIncome + ", Total Expenses: " + totalExpenses);
        
        if (excludedCount > 0 && processedCount == 0) {
            android.util.Log.w("AnalyticsViewModel", "ALL TRANSACTIONS ARE EXCLUDED! This is why analytics shows zero.");
        }

        // Calculate weekly totals
        Calendar weekCal = Calendar.getInstance();
        double weekTotal = 0;

        for (Map.Entry<Date, Double> entry : dailyTransactions.entrySet()) {
            weekTotal += entry.getValue();

            weekCal.setTime(entry.getKey());
            if (weekCal.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY) {
                weeklyTotals.add(weekTotal);
                weekTotal = 0;
            }
        }

        // Add remaining days if any
        if (weekTotal > 0) {
            weeklyTotals.add(weekTotal);
        }

        android.util.Log.d("AnalyticsViewModel", "=== CALCULATE MONTHLY DATA END ===");
        android.util.Log.d("AnalyticsViewModel", "FINAL RESULT - Income: " + totalIncome + ", Expenses: " + totalExpenses);
        android.util.Log.d("AnalyticsViewModel", "Daily transactions: " + dailyTransactions.size() + ", Category totals: " + categoryTotals.size());
        
        return new MonthlyData(dailyTransactions, categoryTotals,
                weeklyTotals, totalIncome, totalExpenses);
    }

    private Map<String, Double> getDummyCategoryBudgets() {
        // TODO: Replace with actual budgets from preferences or database
        Map<String, Double> budgets = new HashMap<>();
        budgets.put("Food", 10000.0);
        budgets.put("Shopping", 5000.0);
        budgets.put("Entertainment", 3000.0);
        budgets.put("Transport", 2000.0);
        budgets.put("Bills", 15000.0);
        return budgets;
    }

    // Public method for debugging - direct database access
    public List<Transaction> getTransactionsBetweenDatesSyncForDebug(long startDate, long endDate) {
        return repository.getTransactionsBetweenDatesSync(startDate, endDate);
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        executorService.shutdown();
    }

    // Data Classes
    public static class MonthlyData {
        private final Map<Date, Double> dailyTransactions;
        private final Map<String, Double> categoryTotals;
        private final List<Double> weeklyTotals;
        private final double totalIncome;
        private final double totalExpenses;

        public MonthlyData(Map<Date, Double> dailyTransactions,
                           Map<String, Double> categoryTotals,
                           List<Double> weeklyTotals,
                           double totalIncome,
                           double totalExpenses) {
            this.dailyTransactions = dailyTransactions;
            this.categoryTotals = categoryTotals;
            this.weeklyTotals = weeklyTotals;
            this.totalIncome = totalIncome;
            this.totalExpenses = totalExpenses;
        }

        public Map<Date, Double> getDailyTransactions() { return dailyTransactions; }
        public Map<String, Double> getCategoryTotals() { return categoryTotals; }
        public List<Double> getWeeklyTotals() { return weeklyTotals; }
        public double getTotalIncome() { return totalIncome; }
        public double getTotalExpenses() { return totalExpenses; }
    }

    public static class CategoryData {
        private final String category;
        private double totalAmount;
        private int transactionCount;

        public CategoryData(String category, double totalAmount, int transactionCount) {
            this.category = category;
            this.totalAmount = totalAmount;
            this.transactionCount = transactionCount;
        }

        public String getCategory() { return category; }
        public double getTotalAmount() { return totalAmount; }
        public int getTransactionCount() { return transactionCount; }
    }

    public static class BudgetStatus {
        private final String category;
        private final double spent;
        private final double budget;

        public BudgetStatus(String category, double spent, double budget) {
            this.category = category;
            this.spent = spent;
            this.budget = budget;
        }

        public String getCategory() { return category; }
        public double getSpent() { return spent; }
        public double getBudget() { return budget; }
        public double getPercentage() { return (spent / budget) * 100; }
    }
}