package com.example.expensetracker.viewmodel;

import android.app.Application;

import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.example.expensetracker.models.Transaction;
import com.example.expensetracker.repository.TransactionRepository;
import com.example.expensetracker.utils.PreferencesManager;

public class TransactionViewModel extends AndroidViewModel {
    private TransactionRepository repository;
    private MutableLiveData<Double> budget;
    private LiveData<List<Transaction>> allTransactions;
    private MutableLiveData<Boolean> transactionUpdated = new MutableLiveData<>(false);
    private ExecutorService executorService;
    private PreferencesManager preferencesManager;

    public TransactionViewModel(Application application) {
        super(application);
        repository = new TransactionRepository(application);
        budget = new MutableLiveData<>(0.0);
        allTransactions = repository.getAllTransactions();
        executorService = Executors.newSingleThreadExecutor();

        // Initialize PreferencesManager
        preferencesManager = new PreferencesManager(application);

        // Load saved budget, if any
        if (preferencesManager.hasBudgetAmount()) {
            double savedBudget = preferencesManager.getBudgetAmount(0.0);
            budget.setValue(savedBudget);
        }
    }

    public LiveData<List<Transaction>> getAllTransactions() {
        return allTransactions;
    }

    public void setBudget(double amount) {
        budget.setValue(amount);

        // Save budget to SharedPreferences
        preferencesManager.saveBudgetAmount(amount);
    }

    public LiveData<Double> getBudget() {
        return budget;
    }

    public void refreshTransactions() {
        // This will trigger observers and update the UI
        allTransactions = repository.getAllTransactions();
    }

    public void getTransactionsBetweenDates(long startDate, long endDate, TransactionRepository.Callback<List<Transaction>> callback) {
        repository.getTransactionsBetweenDates(startDate, endDate, callback);
    }

    // New method to update a transaction
    public void updateTransaction(Transaction transaction) {
        repository.updateTransaction(transaction);
        // Notify observers that data has changed
        transactionUpdated.setValue(!transactionUpdated.getValue());
    }

    /**
     * Add these methods to TransactionViewModel.java
     */

    /**
     * Get count of auto-excluded "OTHER" bank transactions
     * @param callback Callback with the count
     */
    public void getAutoExcludedCount(TransactionRepository.Callback<Integer> callback) {
        executorService.execute(() -> {
            int count = repository.getAutoExcludedTransactionCountSync();
            callback.onResult(count);
        });
    }

    /**
     * Get list of available banks from transaction data
     */
    public List<String> getAvailableBanks() {
        // Create a LiveData transformation to get unique bank values
        List<String> banks = new ArrayList<>();
        banks.add("All Banks");

        // Get unique bank values from repository
        repository.getUniqueBanksList(uniqueBanks -> {
            banks.addAll(uniqueBanks);
        });

        // If list is empty (first run), add defaults
        if (banks.size() <= 1) {
            banks.add("HDFC");
            banks.add("SBI");
            banks.add("ICICI");
            banks.add("AXIS");
            banks.add("OTHER");
        }

        return banks;
    }

    public List<Transaction> getNonExcludedTransactionsBetweenDatesPaginatedSync(long startDate, long endDate, int limit, int offset) {
        return repository.getNonExcludedTransactionsBetweenDatesPaginatedSync(startDate, endDate, limit, offset);
    }

    /**
     * Get paginated list of manually excluded transactions between dates
     */
    public List<Transaction> getManuallyExcludedTransactionsPaginatedSync(long startDate, long endDate, int limit, int offset) {
        return repository.getManuallyExcludedTransactionsPaginatedSync(startDate, endDate, limit, offset);
    }

    /**
     * Class to track transaction filter state
     */
    public static class FilterState {
        public String bank = "All Banks";
        public String type = "All Types";
        public String category = null;
        public String searchQuery = "";
        public double minAmount = 0;
        public double maxAmount = 100000;
        public boolean showingExcluded = false;
        public int sortOption = 0; // 0 = Date (newest first)
        public boolean filterManuallyExcluded = false;
        public boolean viewingManuallyExcluded = false;

        // Update the isAnyFilterActive method
        public boolean isAnyFilterActive() {
            return !bank.equals("All Banks") ||
                    !type.equals("All Types") ||
                    category != null ||
                    !searchQuery.isEmpty() ||
                    minAmount > 0 ||
                    maxAmount < 100000 ||
                    filterManuallyExcluded; // Include our new property here
        }

        // Apply the filters method to handle the new property
        public List<Transaction> applyFilters(List<Transaction> transactions) {
            if (transactions == null) return new ArrayList<>();

            List<Transaction> result = new ArrayList<>();

            for (Transaction transaction : transactions) {
                boolean shouldInclude = true;

                // Special case: If filtering manually excluded transactions
                if (filterManuallyExcluded) {
                    // Only include manually excluded transactions
                    shouldInclude = transaction.isExcludedFromTotal() && !transaction.isOtherDebit();

                    // No need to check other filters if this doesn't match
                    if (!shouldInclude) {
                        continue;
                    }
                }

                // Apply bank filter
                if (!bank.equals("All Banks") && !bank.equals(transaction.getBank())) {
                    shouldInclude = false;
                }

                // Apply type filter
                if (shouldInclude && !type.equals("All Types") && !type.equals(transaction.getType())) {
                    shouldInclude = false;
                }

                // Apply category filter (only if not filtering for manually excluded)
                if (shouldInclude && !filterManuallyExcluded && category != null && !category.isEmpty() &&
                        !category.equals(transaction.getCategory())) {
                    shouldInclude = false;
                }

                // Apply amount filter
                if (shouldInclude && (transaction.getAmount() < minAmount ||
                        transaction.getAmount() > maxAmount)) {
                    shouldInclude = false;
                }

                // Apply exclusion filter (but skip this check if we're explicitly filtering for manually excluded)
                if (shouldInclude && !filterManuallyExcluded && transaction.isExcludedFromTotal() && transaction.isOtherDebit() && !showingExcluded) {
                    shouldInclude = false;
                }

                // Apply search query
                if (shouldInclude && !searchQuery.isEmpty()) {
                    boolean matchesSearch = false;
                    String lowerQuery = searchQuery.toLowerCase();

                    // Check description
                    if (transaction.getDescription() != null &&
                            transaction.getDescription().toLowerCase().contains(lowerQuery)) {
                        matchesSearch = true;
                    }

                    // Check bank
                    if (!matchesSearch && transaction.getBank() != null &&
                            transaction.getBank().toLowerCase().contains(lowerQuery)) {
                        matchesSearch = true;
                    }

                    // Check category
                    if (!matchesSearch && transaction.getCategory() != null &&
                            transaction.getCategory().toLowerCase().contains(lowerQuery)) {
                        matchesSearch = true;
                    }

                    // Check merchant name
                    if (!matchesSearch && transaction.getMerchantName() != null &&
                            transaction.getMerchantName().toLowerCase().contains(lowerQuery)) {
                        matchesSearch = true;
                    }

                    // Check amount
                    if (!matchesSearch && String.valueOf(transaction.getAmount()).contains(lowerQuery)) {
                        matchesSearch = true;
                    }

                    shouldInclude = matchesSearch;
                }

                if (shouldInclude) {
                    result.add(transaction);
                }
            }

            // Apply current sort option
            sortTransactions(result, sortOption);

            return result;
        }

        // Helper method to sort transactions
        private static void sortTransactions(List<Transaction> transactions, int sortOption) {
            switch (sortOption) {
                case 0: // Date (newest first)
                    Collections.sort(transactions, (a, b) -> Long.compare(b.getDate(), a.getDate()));
                    break;
                case 1: // Date (oldest first)
                    Collections.sort(transactions, (a, b) -> Long.compare(a.getDate(), b.getDate()));
                    break;
                case 2: // Amount (highest first)
                    Collections.sort(transactions, (a, b) -> Double.compare(b.getAmount(), a.getAmount()));
                    break;
                case 3: // Amount (lowest first)
                    Collections.sort(transactions, (a, b) -> Double.compare(a.getAmount(), b.getAmount()));
                    break;
                case 4: // Description (A-Z)
                    Collections.sort(transactions, (a, b) -> {
                        String descA = a.getDescription() != null ? a.getDescription() : "";
                        String descB = b.getDescription() != null ? b.getDescription() : "";
                        return descA.compareToIgnoreCase(descB);
                    });
                    break;
                case 5: // Description (Z-A)
                    Collections.sort(transactions, (a, b) -> {
                        String descA = a.getDescription() != null ? a.getDescription() : "";
                        String descB = b.getDescription() != null ? b.getDescription() : "";
                        return descB.compareToIgnoreCase(descA);
                    });
                    break;
            }
        }
    }
}