package com.example.expensetracker.viewmodel;

import android.app.Application;
import android.content.Context;
import android.widget.Toast;

import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.example.expensetracker.models.Transaction;
import com.example.expensetracker.repository.TransactionRepository;

public class TransactionViewModel extends AndroidViewModel {
    private TransactionRepository repository;
    private MutableLiveData<Double> budget;
    private LiveData<List<Transaction>> allTransactions;
    private MutableLiveData<Boolean> transactionUpdated = new MutableLiveData<>(false);
    private ExecutorService executorService;

    public TransactionViewModel(Application application) {
        super(application);
        repository = new TransactionRepository(application);
        budget = new MutableLiveData<>(0.0);
        allTransactions = repository.getAllTransactions();
        executorService = Executors.newSingleThreadExecutor();
    }

    public void hasAnyTransactions(TransactionRepository.Callback<Boolean> callback) {
        repository.hasAnyTransactions(callback);
    }

    public LiveData<List<Transaction>> getAllTransactions() {
        return allTransactions;
    }

    public LiveData<List<Transaction>> getTransactionsByBank(String bank) {
        return repository.getTransactionsByBank(bank);
    }

    public LiveData<List<Transaction>> getTransactionsByType(String type) {
        return repository.getTransactionsByType(type);
    }

    public void insert(Transaction transaction) {
        repository.insert(transaction);
    }

    public void setBudget(double amount) {
        budget.setValue(amount);
    }

    public LiveData<Double> getBudget() {
        return budget;
    }

    public void updateTransactionsList(String selectedBank, String selectedType,
                                       TransactionRepository.Callback<List<Transaction>> callback) {
        if ("All Banks".equals(selectedBank) && "All Types".equals(selectedType)) {
            repository.getAllTransactions(callback);
        } else if ("All Banks".equals(selectedBank)) {
            repository.getTransactionsByType(selectedType, callback);
        } else if ("All Types".equals(selectedType)) {
            repository.getTransactionsByBank(selectedBank, callback);
        } else {
            repository.getTransactionsByBankAndType(selectedBank, selectedType, callback);
        }
    }

    public double calculateTotalDebits(List<Transaction> transactions) {
        if (transactions == null) return 0;
        double total = 0;
        for (Transaction transaction : transactions) {
            if ("DEBIT".equals(transaction.getType())) {
                total += transaction.getAmount();
            }
        }
        return total;
    }

    public double calculateTotalCredits(List<Transaction> transactions) {
        if (transactions == null) return 0;
        double total = 0;
        for (Transaction transaction : transactions) {
            if ("CREDIT".equals(transaction.getType())) {
                total += transaction.getAmount();
            }
        }
        return total;
    }

    public void refreshTransactions() {
        // This will trigger observers and update the UI
        allTransactions = repository.getAllTransactions();
    }

    public LiveData<List<Transaction>> getTransactionsBetweenDates(long startDate, long endDate) {
        return repository.getTransactionsBetweenDates(startDate, endDate);
    }

    public void getTransactionsBetweenDates(long startDate, long endDate, TransactionRepository.Callback<List<Transaction>> callback) {
        repository.getTransactionsBetweenDates(startDate, endDate, callback);
    }

    // New method to get a transaction by ID
    public void getTransactionById(long transactionId, TransactionRepository.Callback<Transaction> callback) {
        repository.getTransactionById(transactionId, callback);
    }

    // New method to update a transaction
    public void updateTransaction(Transaction transaction) {
        repository.updateTransaction(transaction);
        // Notify observers that data has changed
        transactionUpdated.setValue(!transactionUpdated.getValue());
    }

    // New method to update a transaction's category
    public void updateTransactionCategory(long transactionId, String category) {
        repository.updateTransactionCategory(transactionId, category);
        // Notify observers that data has changed
        transactionUpdated.setValue(!transactionUpdated.getValue());
    }

    // New method to update a transaction's excluded status
    public void updateTransactionExcludedStatus(long transactionId, boolean isExcluded) {
        repository.updateTransactionExcludedStatus(transactionId, isExcluded);
        // Notify observers that data has changed
        transactionUpdated.setValue(!transactionUpdated.getValue());
    }

    // New method to get available categories
    public String[] getAvailableCategories() {
        return Transaction.Categories.getAllCategories();
    }

    // Get notification when transactions are updated
    public LiveData<Boolean> getTransactionUpdatedNotifier() {
        return transactionUpdated;
    }

    /**
     * Add these methods to TransactionViewModel.java
     */

    /**
     * Toggle excluded status for a specific transaction
     * @param transactionId The ID of the transaction
     * @param excluded New excluded status (true or false)
     */
    public void toggleExcludedStatus(long transactionId, boolean excluded) {
        repository.updateTransactionExcludedStatus(transactionId, excluded);

        // Refresh data
        refreshTransactions();

        // Notify observers of change
        transactionUpdated.setValue(!transactionUpdated.getValue());
    }

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
     * Show notification for auto-excluded transactions if there are any
     * @param context The activity context
     */
    public void notifyAboutAutoExcludedTransactions(Context context) {
        getAutoExcludedCount(count -> {
            if (count > 0) {
                // Show notification or toast
                Toast.makeText(context,
                        count + " transaction(s) from unrecognized sources have been auto-excluded",
                        Toast.LENGTH_LONG).show();
            }
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

    public List<Transaction> getTransactionsBetweenDatesPaginatedSync(long startDate, long endDate, int limit, int offset) {
        return repository.getTransactionsBetweenDatesPaginatedSync(startDate, endDate, limit, offset);
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
}