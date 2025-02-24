package com.example.expensetracker.viewmodel;

import android.app.Application;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.expensetracker.models.Transaction;
import com.example.expensetracker.repository.TransactionRepository;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PredictionViewModel extends AndroidViewModel {
    private final TransactionRepository repository;
    private final ExecutorService executorService;
    private final MutableLiveData<MonthlyPrediction> monthlyPrediction;
    private final MutableLiveData<List<RecurringExpense>> recurringExpenses;
    private final MutableLiveData<List<CategoryPrediction>> categoryPredictions;
    private final MutableLiveData<List<SavingsSuggestion>> savingsSuggestions;

    public PredictionViewModel(Application application) {
        super(application);
        repository = new TransactionRepository(application);
        executorService = Executors.newSingleThreadExecutor();
        monthlyPrediction = new MutableLiveData<>();
        recurringExpenses = new MutableLiveData<>();
        categoryPredictions = new MutableLiveData<>();
        savingsSuggestions = new MutableLiveData<>();

        loadPredictions();
    }

    public LiveData<MonthlyPrediction> getMonthlyPrediction() {
        return monthlyPrediction;
    }

    public LiveData<List<RecurringExpense>> getRecurringExpenses() {
        return recurringExpenses;
    }

    public LiveData<List<CategoryPrediction>> getCategoryPredictions() {
        return categoryPredictions;
    }

    public LiveData<List<SavingsSuggestion>> getSavingsSuggestions() {
        return savingsSuggestions;
    }

    private void loadPredictions() {
        executorService.execute(() -> {
            // Get last 6 months of transactions
            Calendar cal = Calendar.getInstance();
            long endDate = cal.getTimeInMillis();
            cal.add(Calendar.MONTH, -6);
            long startDate = cal.getTimeInMillis();

            List<Transaction> transactions = repository
                    .getTransactionsBetweenDatesSync(startDate, endDate);

            MonthlyPrediction prediction = calculateMonthlyPrediction(transactions);
            monthlyPrediction.postValue(prediction);

            List<RecurringExpense> recurring = identifyRecurringExpenses(transactions);
            recurringExpenses.postValue(recurring);

            List<CategoryPrediction> categories = predictCategoryExpenses(transactions);
            categoryPredictions.postValue(categories);

            List<SavingsSuggestion> suggestions = generateSavingsSuggestions(
                    transactions, categories);
            savingsSuggestions.postValue(suggestions);
        });
    }

    private MonthlyPrediction calculateMonthlyPrediction(List<Transaction> transactions) {
        // Group transactions by month
        Map<Integer, Double> monthlyTotals = new HashMap<>();
        Calendar cal = Calendar.getInstance();

        for (Transaction transaction : transactions) {
            if (transaction.isDebit() && !transaction.isExcludedFromTotal()) {
                cal.setTimeInMillis(transaction.getDate());
                int monthKey = cal.get(Calendar.YEAR) * 12 + cal.get(Calendar.MONTH);
                monthlyTotals.merge(monthKey, transaction.getAmount(), Double::sum);
            }
        }

        // Calculate average and standard deviation
        double sum = 0;
        List<Double> monthlyAmounts = new ArrayList<>(monthlyTotals.values());
        for (Double amount : monthlyAmounts) {
            sum += amount;
        }
        double mean = sum / monthlyAmounts.size();

        double varianceSum = 0;
        for (Double amount : monthlyAmounts) {
            varianceSum += Math.pow(amount - mean, 2);
        }
        double stdDev = Math.sqrt(varianceSum / monthlyAmounts.size());

        return new MonthlyPrediction(mean, stdDev, monthlyAmounts);
    }

    private List<RecurringExpense> identifyRecurringExpenses(List<Transaction> transactions) {
        Map<String, List<Transaction>> merchantTransactions = new HashMap<>();

        // Group transactions by merchant/description
        for (Transaction transaction : transactions) {
            if (transaction.isDebit()) {
                merchantTransactions.computeIfAbsent(
                        transaction.getDescription(),
                        k -> new ArrayList<>()
                ).add(transaction);
            }
        }

        List<RecurringExpense> recurringExpenses = new ArrayList<>();

        // Analyze each merchant's transactions
        for (Map.Entry<String, List<Transaction>> entry : merchantTransactions.entrySet()) {
            List<Transaction> merchantTxns = entry.getValue();
            if (merchantTxns.size() >= 3) { // At least 3 occurrences
                double averageAmount = calculateAverageAmount(merchantTxns);
                int frequency = calculateFrequency(merchantTxns);

                if (frequency > 0) {
                    recurringExpenses.add(new RecurringExpense(
                            entry.getKey(),
                            averageAmount,
                            frequency,
                            merchantTxns.size(),
                            predictNextDate(merchantTxns, frequency)
                    ));
                }
            }
        }

        return recurringExpenses;
    }

    private List<CategoryPrediction> predictCategoryExpenses(List<Transaction> transactions) {
        Map<String, List<Double>> categoryAmounts = new HashMap<>();

        // Group amounts by category
        for (Transaction transaction : transactions) {
            if (transaction.isDebit() && !transaction.isExcludedFromTotal()) {
                categoryAmounts.computeIfAbsent(
                        transaction.getCategory(),
                        k -> new ArrayList<>()
                ).add(transaction.getAmount());
            }
        }

        List<CategoryPrediction> predictions = new ArrayList<>();

        // Calculate prediction for each category
        for (Map.Entry<String, List<Double>> entry : categoryAmounts.entrySet()) {
            List<Double> amounts = entry.getValue();
            double mean = calculateMean(amounts);
            double stdDev = calculateStdDev(amounts, mean);

            predictions.add(new CategoryPrediction(
                    entry.getKey(),
                    mean,
                    mean - stdDev,
                    mean + stdDev,
                    calculateTrend(amounts)
            ));
        }

        predictions.sort((a, b) -> Double.compare(b.predictedAmount, a.predictedAmount));
        return predictions;
    }

    private List<SavingsSuggestion> generateSavingsSuggestions(
            List<Transaction> transactions,
            List<CategoryPrediction> predictions) {
        List<SavingsSuggestion> suggestions = new ArrayList<>();

        // Analyze high-frequency small transactions
        Map<String, List<Transaction>> merchantTransactions = new HashMap<>();
        for (Transaction t : transactions) {
            if (t.isDebit()) {
                merchantTransactions.computeIfAbsent(
                        t.getMerchantName(),
                        k -> new ArrayList<>()
                ).add(t);
            }
        }

        for (Map.Entry<String, List<Transaction>> entry : merchantTransactions.entrySet()) {
            List<Transaction> merchantTxns = entry.getValue();
            double avgAmount = calculateAverageAmount(merchantTxns);

            if (merchantTxns.size() >= 5 && avgAmount < 500) {
                suggestions.add(new SavingsSuggestion(
                        "Consolidate small purchases",
                        "Consider combining multiple small purchases at " + entry.getKey(),
                        String.format("Potential monthly savings: ₹%.2f",
                                avgAmount * merchantTxns.size() * 0.2)
                ));
            }
        }

        // Analyze category overspending
        for (CategoryPrediction prediction : predictions) {
            if (prediction.getTrend() > 0.1) { // 10% increase trend
                suggestions.add(new SavingsSuggestion(
                        "Reduce " + prediction.getCategory() + " expenses",
                        "This category shows an increasing trend in spending",
                        String.format("Target reduction: ₹%.2f",
                                prediction.getPredictedAmount() * 0.1)
                ));
            }
        }

        return suggestions;
    }

    private double calculateAverageAmount(List<Transaction> transactions) {
        double sum = 0;
        for (Transaction t : transactions) {
            sum += t.getAmount();
        }
        return sum / transactions.size();
    }

    private int calculateFrequency(List<Transaction> transactions) {
        if (transactions.size() < 2) return 0;

        // Sort transactions by date
        transactions.sort((a, b) -> Long.compare(a.getDate(), b.getDate()));

        // Calculate average days between transactions
        long totalDays = 0;
        for (int i = 1; i < transactions.size(); i++) {
            long diff = transactions.get(i).getDate() - transactions.get(i-1).getDate();
            totalDays += diff / (24 * 60 * 60 * 1000);
        }

        return (int) (totalDays / (transactions.size() - 1));
    }

    private long predictNextDate(List<Transaction> transactions, int frequency) {
        transactions.sort((a, b) -> Long.compare(a.getDate(), b.getDate()));
        long lastDate = transactions.get(transactions.size() - 1).getDate();
        return lastDate + (frequency * 24 * 60 * 60 * 1000L);
    }

    private double calculateMean(List<Double> values) {
        double sum = 0;
        for (Double value : values) {
            sum += value;
        }
        return sum / values.size();
    }

    private double calculateStdDev(List<Double> values, double mean) {
        double variance = 0;
        for (Double value : values) {
            variance += Math.pow(value - mean, 2);
        }
        return Math.sqrt(variance / values.size());
    }

    private double calculateTrend(List<Double> values) {
        if (values.size() < 2) return 0;

        // Simple linear regression
        int n = values.size();
        double sumX = 0, sumY = 0, sumXY = 0, sumX2 = 0;

        for (int i = 0; i < n; i++) {
            sumX += i;
            sumY += values.get(i);
            sumXY += i * values.get(i);
            sumX2 += i * i;
        }

        double slope = (n * sumXY - sumX * sumY) / (n * sumX2 - sumX * sumX);
        return slope / values.get(0); // Normalize by first value
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        executorService.shutdown();
    }

    // Data Classes
    public static class MonthlyPrediction {
        private final double predictedAmount;
        private final double standardDeviation;
        private final List<Double> historicalData;

        public MonthlyPrediction(double predictedAmount, double standardDeviation,
                                 List<Double> historicalData) {
            this.predictedAmount = predictedAmount;
            this.standardDeviation = standardDeviation;
            this.historicalData = historicalData;
        }

        public double getPredictedAmount() { return predictedAmount; }
        public double getStandardDeviation() { return standardDeviation; }
        public List<Double> getHistoricalData() { return historicalData; }
        public double getLowerBound() { return predictedAmount - standardDeviation; }
        public double getUpperBound() { return predictedAmount + standardDeviation; }
    }

    public static class RecurringExpense {
        private final String description;
        private final double amount;
        private final int frequencyDays;
        private final int occurrences;
        private final long nextExpectedDate;

        public RecurringExpense(String description, double amount, int frequencyDays,
                                int occurrences, long nextExpectedDate) {
            this.description = description;
            this.amount = amount;
            this.frequencyDays = frequencyDays;
            this.occurrences = occurrences;
            this.nextExpectedDate = nextExpectedDate;
        }

        public String getDescription() { return description; }
        public double getAmount() { return amount; }
        public int getFrequencyDays() { return frequencyDays; }
        public int getOccurrences() { return occurrences; }
        public long getNextExpectedDate() { return nextExpectedDate; }
    }

    public static class CategoryPrediction {
        private final String category;
        private final double predictedAmount;
        private final double lowerBound;
        private final double upperBound;
        private final double trend;

        public CategoryPrediction(String category, double predictedAmount,
                                  double lowerBound, double upperBound, double trend) {
            this.category = category;
            this.predictedAmount = predictedAmount;
            this.lowerBound = lowerBound;
            this.upperBound = upperBound;
            this.trend = trend;
        }

        public String getCategory() { return category; }
        public double getPredictedAmount() { return predictedAmount; }
        public double getLowerBound() { return lowerBound; }
        public double getUpperBound() { return upperBound; }
        public double getTrend() { return trend; }
    }

    public static class SavingsSuggestion {
        private final String title;
        private final String description;
        private final String impact;

        public SavingsSuggestion(String title, String description, String impact) {
            this.title = title;
            this.description = description;
            this.impact = impact;
        }

        public String getTitle() { return title; }
        public String getDescription() { return description; }
        public String getImpact() { return impact; }
    }
}