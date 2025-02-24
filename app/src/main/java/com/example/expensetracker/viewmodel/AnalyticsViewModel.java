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
    private final MutableLiveData<Long> selectedStartDate = new MutableLiveData<>();
    private final MutableLiveData<Long> selectedEndDate = new MutableLiveData<>();

    public AnalyticsViewModel(Application application) {
        super(application);
        repository = new TransactionRepository(application);
        executorService = Executors.newSingleThreadExecutor();
    }

    public LiveData<MonthlyData> getMonthlyData(long startDate, long endDate) {
        selectedStartDate.setValue(startDate);
        selectedEndDate.setValue(endDate);

        return Transformations.switchMap(selectedStartDate, start ->
                Transformations.switchMap(selectedEndDate, end -> {
                    MutableLiveData<MonthlyData> result = new MutableLiveData<>();

                    executorService.execute(() -> {
                        List<Transaction> transactions = repository
                                .getTransactionsBetweenDatesSync(start, end);

                        MonthlyData monthlyData = calculateMonthlyData(transactions);
                        result.postValue(monthlyData);
                    });

                    return result;
                }));
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

    private MonthlyData calculateMonthlyData(List<Transaction> transactions) {
        Map<Date, Double> dailyTransactions = new TreeMap<>();
        Map<String, Double> categoryTotals = new HashMap<>();
        List<Double> weeklyTotals = new ArrayList<>();
        double totalIncome = 0;
        double totalExpenses = 0;

        // Initialize data structures
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(selectedStartDate.getValue());

        // Calculate daily and category totals
        for (Transaction transaction : transactions) {
            if (!transaction.isExcludedFromTotal()) {
                Date date = new Date(transaction.getDate());

                if (transaction.isDebit()) {
                    dailyTransactions.merge(date, transaction.getAmount(), Double::sum);
                    categoryTotals.merge(transaction.getCategory(),
                            transaction.getAmount(), Double::sum);
                    totalExpenses += transaction.getAmount();
                } else {
                    totalIncome += transaction.getAmount();
                }
            }
        }

        // Calculate weekly totals
        Calendar weekCal = Calendar.getInstance();
        weekCal.setTimeInMillis(selectedStartDate.getValue());
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