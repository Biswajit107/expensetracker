package com.example.expensetracker.viewmodel;

import android.app.Application;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import java.util.List;
import com.example.expensetracker.models.Transaction;
import com.example.expensetracker.repository.TransactionRepository;

public class TransactionViewModel extends AndroidViewModel {
    private TransactionRepository repository;
    private MutableLiveData<Double> budget;
    private LiveData<List<Transaction>> allTransactions;

    public TransactionViewModel(Application application) {
        super(application);
        repository = new TransactionRepository(application);
        budget = new MutableLiveData<>(0.0);
        allTransactions = repository.getAllTransactions();
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


}