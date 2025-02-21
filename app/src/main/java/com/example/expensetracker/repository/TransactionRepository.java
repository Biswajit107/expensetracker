package com.example.expensetracker.repository;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;

import androidx.lifecycle.LiveData;
import com.example.expensetracker.models.Transaction;
import com.example.expensetracker.database.TransactionDao;
import com.example.expensetracker.database.TransactionDatabase;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TransactionRepository {
    private TransactionDao transactionDao;
    private ExecutorService executorService;
    private LiveData<List<Transaction>> allTransactions;

    public TransactionRepository(Application application) {
        TransactionDatabase database = TransactionDatabase.getInstance(application);
        transactionDao = database.transactionDao();
        executorService = Executors.newSingleThreadExecutor();
        allTransactions = transactionDao.getAllTransactions();
    }

    public LiveData<List<Transaction>> getAllTransactions() {
        return allTransactions;
    }

    public void getAllTransactions(final Callback<List<Transaction>> callback) {
        executorService.execute(() -> {
            List<Transaction> transactions = transactionDao.getAllTransactionsSync();
            callback.onResult(transactions);
        });
    }

    public LiveData<List<Transaction>> getTransactionsByBank(String bank) {
        return transactionDao.getTransactionsByBank(bank);
    }

    public void getTransactionsByBank(String bank, final Callback<List<Transaction>> callback) {
        executorService.execute(() -> {
            List<Transaction> transactions = transactionDao.getTransactionsByBankSync(bank);
            callback.onResult(transactions);
        });
    }

    public LiveData<List<Transaction>> getTransactionsByType(String type) {
        return transactionDao.getTransactionsByType(type);
    }

    public void getTransactionsByType(String type, final Callback<List<Transaction>> callback) {
        executorService.execute(() -> {
            List<Transaction> transactions = transactionDao.getTransactionsByTypeSync(type);
            callback.onResult(transactions);
        });
    }

    public void getTransactionsByBankAndType(String bank, String type,
                                             final Callback<List<Transaction>> callback) {
        executorService.execute(() -> {
            List<Transaction> transactions = transactionDao.getTransactionsByBankAndTypeSync(bank, type);
            callback.onResult(transactions);
        });
    }

    public void insert(Transaction transaction) {
        executorService.execute(() -> {
            transactionDao.insert(transaction);
        });
    }

    public void hasAnyTransactions(final Callback<Boolean> callback) {
        executorService.execute(() -> {
            boolean hasTransactions = transactionDao.hasAnyTransactions();
            callback.onResult(hasTransactions);
        });
    }

    public interface Callback<T> {
        void onResult(T result);
    }

    public LiveData<List<Transaction>> getTransactionsBetweenDates(long startDate, long endDate) {
        return transactionDao.getTransactionsBetweenDates(startDate, endDate);
    }

    public void getTransactionsBetweenDates(long startDate, long endDate, Callback<List<Transaction>> callback) {
        executorService.execute(() -> {
            List<Transaction> transactions = transactionDao.getTransactionsBetweenDatesSync(startDate, endDate);
            new Handler(Looper.getMainLooper()).post(() -> {
                callback.onResult(transactions);
            });
        });
    }


}