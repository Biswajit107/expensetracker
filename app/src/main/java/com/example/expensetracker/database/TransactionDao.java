package com.example.expensetracker.database;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import com.example.expensetracker.models.Transaction;
import java.util.List;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import com.example.expensetracker.models.Transaction;
import java.util.List;

@Dao
public interface TransactionDao {
    @Query("SELECT * FROM transactions ORDER BY date DESC")
    LiveData<List<Transaction>> getAllTransactions();

    @Query("SELECT * FROM transactions ORDER BY date DESC")
    List<Transaction> getAllTransactionsSync();

    @Query("SELECT * FROM transactions WHERE bank = :bank")
    LiveData<List<Transaction>> getTransactionsByBank(String bank);

    @Query("SELECT * FROM transactions WHERE bank = :bank")
    List<Transaction> getTransactionsByBankSync(String bank);

    @Query("SELECT * FROM transactions WHERE type = :type")
    LiveData<List<Transaction>> getTransactionsByType(String type);

    @Query("SELECT * FROM transactions WHERE type = :type")
    List<Transaction> getTransactionsByTypeSync(String type);

    @Query("SELECT * FROM transactions WHERE bank = :bank AND type = :type")
    LiveData<List<Transaction>> getTransactionsByBankAndType(String bank, String type);

    @Query("SELECT * FROM transactions WHERE bank = :bank AND type = :type")
    List<Transaction> getTransactionsByBankAndTypeSync(String bank, String type);

    @Insert
    void insert(Transaction transaction);

    @Query("SELECT EXISTS(SELECT 1 FROM transactions)")
    boolean hasAnyTransactions();

    @Query("SELECT * FROM transactions WHERE messageHash = :hash LIMIT 1")
    Transaction getTransactionByHash(String hash);

    @Query("SELECT * FROM transactions WHERE date BETWEEN :startDate AND :endDate")
    LiveData<List<Transaction>> getTransactionsBetweenDates(long startDate, long endDate);

    @Query("SELECT * FROM transactions WHERE date BETWEEN :startDate AND :endDate ORDER BY date DESC")
    List<Transaction> getTransactionsBetweenDatesSync(long startDate, long endDate);
}