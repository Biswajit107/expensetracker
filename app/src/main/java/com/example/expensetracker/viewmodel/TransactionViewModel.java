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

    public LiveData<List<Transaction>> getAllTransactions() {
        return allTransactions;
    }

    public void setBudget(double amount) {
        budget.setValue(amount);
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
}