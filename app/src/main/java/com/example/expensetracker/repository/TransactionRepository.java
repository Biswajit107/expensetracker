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

    public List<Transaction> getTransactionsBetweenDatesSync(long startDate, long endDate) {
        return transactionDao.getTransactionsBetweenDatesSync(startDate, endDate);
    }

    // New method to get a transaction by ID
    public void getTransactionById(long transactionId, final Callback<Transaction> callback) {
        executorService.execute(() -> {
            Transaction transaction = transactionDao.getTransactionById(transactionId);
            new Handler(Looper.getMainLooper()).post(() -> {
                callback.onResult(transaction);
            });
        });
    }

    // New method to update a transaction
    public void updateTransaction(Transaction transaction) {
        executorService.execute(() -> {
            transactionDao.update(transaction);
        });
    }

    // New method to update a transaction's category
    public void updateTransactionCategory(long transactionId, String category) {
        executorService.execute(() -> {
            transactionDao.updateCategory(transactionId, category);
        });
    }

    // New method to update a transaction's excluded status
    public void updateTransactionExcludedStatus(long transactionId, boolean isExcluded) {
        executorService.execute(() -> {
            transactionDao.updateExcludedStatus(transactionId, isExcluded);
        });
    }

    /**
     * Get the count of auto-excluded transactions from OTHER banks
     * @return Count of auto-excluded transactions
     */
    public int getAutoExcludedTransactionCountSync() {
        return transactionDao.getAutoExcludedTransactionCount();
    }

    /**
     * Get all auto-excluded transactions
     * @param callback Callback with the list of transactions
     */
    public void getAutoExcludedTransactions(final Callback<List<Transaction>> callback) {
        executorService.execute(() -> {
            List<Transaction> transactions = transactionDao.getAutoExcludedTransactionsSync();
            new Handler(Looper.getMainLooper()).post(() -> {
                callback.onResult(transactions);
            });
        });
    }

    /**
     * Include all auto-excluded transactions
     * @param callback Callback with count of updated transactions
     */
    public void includeAllAutoExcludedTransactions(final Callback<Integer> callback) {
        executorService.execute(() -> {
            int count = transactionDao.includeAllAutoExcludedTransactions();
            new Handler(Looper.getMainLooper()).post(() -> {
                callback.onResult(count);
            });
        });
    }

}