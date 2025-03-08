package com.example.expensetracker.database;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.RawQuery;
import androidx.room.Update;
import androidx.room.Delete;
import androidx.sqlite.db.SupportSQLiteQuery;

import com.example.expensetracker.models.Transaction;
import java.util.List;

@Dao
public interface TransactionDao {
    // Basic CRUD operations
    @Insert
    void insert(Transaction transaction);

    @Update
    void update(Transaction transaction);

    // New method to update a transaction's category
    @Query("UPDATE transactions SET category = :category WHERE id = :transactionId")
    void updateCategory(long transactionId, String category);

    // New method to update a transaction's excluded status
    @Query("UPDATE transactions SET is_excluded_from_total = :isExcluded WHERE id = :transactionId")
    void updateExcludedStatus(long transactionId, boolean isExcluded);

    // New method to get a transaction by ID
    @Query("SELECT * FROM transactions WHERE id = :transactionId LIMIT 1")
    Transaction getTransactionById(long transactionId);


    // Fetch all transactions
    @Query("SELECT * FROM transactions ORDER BY date DESC")
    LiveData<List<Transaction>> getAllTransactions();

    @Query("SELECT * FROM transactions ORDER BY date DESC")
    List<Transaction> getAllTransactionsSync();

    // Date range queries
    @Query("SELECT * FROM transactions WHERE date BETWEEN :startDate AND :endDate ORDER BY date DESC")
    LiveData<List<Transaction>> getTransactionsBetweenDates(long startDate, long endDate);

    @Query("SELECT * FROM transactions WHERE date BETWEEN :startDate AND :endDate ORDER BY date DESC")
    List<Transaction> getTransactionsBetweenDatesSync(long startDate, long endDate);

    // Bank based queries
    @Query("SELECT * FROM transactions WHERE bank = :bank")
    LiveData<List<Transaction>> getTransactionsByBank(String bank);

    @Query("SELECT * FROM transactions WHERE bank = :bank")
    List<Transaction> getTransactionsByBankSync(String bank);

    // Type based queries
    @Query("SELECT * FROM transactions WHERE type = :type")
    LiveData<List<Transaction>> getTransactionsByType(String type);

    // Duplicate prevention
    @Query("SELECT EXISTS(SELECT 1 FROM transactions WHERE messageHash = :hash LIMIT 1)")
    boolean hasTransaction(String hash);

    @Query("SELECT * FROM transactions WHERE bank = :bank AND type = :type")
    List<Transaction> getTransactionsByBankAndTypeSync(String bank, String type);

    @Query("SELECT * FROM transactions WHERE messageHash = :hash LIMIT 1")
    Transaction getTransactionByHash(String hash);

    @Query("SELECT EXISTS(SELECT 1 FROM transactions)")
    boolean hasAnyTransactions();

    @Query("SELECT * FROM transactions WHERE type = :type")
    List<Transaction> getTransactionsByTypeSync(String type);

    // Statistics class for category totals
    static class CategoryTotal {
        public String category;
        public double total;
    }

    /**
     * Same as above but returns results synchronously
     */
    @RawQuery
    List<Transaction> searchTransactionsWithFilterSync(SupportSQLiteQuery query);

    /**
     * Simple text search across multiple fields
     * @param query Text to search for
     * @return LiveData list of matching transactions
     */
    @Query("SELECT * FROM transactions WHERE " +
            "description LIKE '%' || :query || '%' OR " +
            "merchant_name LIKE '%' || :query || '%' OR " +
            "category LIKE '%' || :query || '%' OR " +
            "bank LIKE '%' || :query || '%' OR " +
            "original_sms LIKE '%' || :query || '%' " +
            "ORDER BY date DESC")
    List<Transaction> searchTransactions(String query);

    /**
     * Get count of auto-excluded transactions from OTHER banks
     */
    @Query("SELECT COUNT(*) FROM transactions WHERE bank = 'OTHER' AND is_excluded_from_total = 1 AND is_other_debit = 1")
    int getAutoExcludedTransactionCount();

    /**
     * Get all auto-excluded transactions
     */
    @Query("SELECT * FROM transactions WHERE bank = 'OTHER' AND is_excluded_from_total = 1 AND is_other_debit = 1 ORDER BY date DESC")
    List<Transaction> getAutoExcludedTransactionsSync();

    /**
     * Include all auto-excluded transactions
     * @return Count of updated transactions
     */
    @Query("UPDATE transactions SET is_excluded_from_total = 0 WHERE bank = 'OTHER' AND is_excluded_from_total = 1 AND is_other_debit = 1")
    int includeAllAutoExcludedTransactions();

    // And add this query to your TransactionDao interface
    @Query("SELECT DISTINCT bank FROM transactions WHERE bank IS NOT NULL ORDER BY bank")
    List<String> getUniqueBanks();

    /**
     * Get all automatically excluded transactions (both duplicates and others)
     */
    @Query("SELECT * FROM transactions WHERE is_excluded_from_total = 1 AND " +
            "(is_other_debit = 1 OR description LIKE '%[DUPLICATE]%' OR description LIKE '%[AUTO-EXCLUDED]%') " +
            "ORDER BY date DESC")
    List<Transaction> getAllAutomaticallyExcludedTransactionsSync();

    /**
     * Get all duplicate transactions specifically
     */
    @Query("SELECT * FROM transactions WHERE description LIKE '%[DUPLICATE]%' ORDER BY date DESC")
    List<Transaction> getDuplicateTransactionsSync();

    /**
     * Get all excluded transactions from unknown sources (non-duplicates)
     */
    @Query("SELECT * FROM transactions WHERE is_excluded_from_total = 1 AND is_other_debit = 1 " +
            "AND description NOT LIKE '%[DUPLICATE]%' ORDER BY date DESC")
    List<Transaction> getUnknownSourceExcludedTransactionsSync();

    @Query("SELECT * FROM transactions WHERE date BETWEEN :startDate AND :endDate ORDER BY date DESC LIMIT :limit OFFSET :offset")
    List<Transaction> getTransactionsBetweenDatesPaginatedSync(long startDate, long endDate, int limit, int offset);

    // Updated query to exclude ALL excluded transactions (both auto and manual)
    @Query("SELECT * FROM transactions WHERE date BETWEEN :startDate AND :endDate AND is_excluded_from_total = 0 ORDER BY date DESC LIMIT :limit OFFSET :offset")
    List<Transaction> getNonExcludedTransactionsBetweenDatesPaginatedSync(long startDate, long endDate, int limit, int offset);

    // New query to get only manually excluded transactions
    @Query("SELECT * FROM transactions WHERE date BETWEEN :startDate AND :endDate AND is_excluded_from_total = 1 AND is_other_debit = 0 ORDER BY date DESC LIMIT :limit OFFSET :offset")
    List<Transaction> getManuallyExcludedTransactionsPaginatedSync(long startDate, long endDate, int limit, int offset);


    @Query("SELECT COUNT(*) FROM transactions WHERE date BETWEEN :startDate AND :endDate")
    int getTransactionCountBetweenDates(long startDate, long endDate);

    /**
     * Get all manually excluded transactions between dates (no pagination)
     */
    @Query("SELECT * FROM transactions WHERE date BETWEEN :startDate AND :endDate AND is_excluded_from_total = 1 AND is_other_debit = 0 ORDER BY date DESC")
    List<Transaction> getManuallyExcludedTransactionsBetweenDatesSync(long startDate, long endDate);

    /**
     * Get all non-excluded transactions between dates (no pagination)
     */
    @Query("SELECT * FROM transactions WHERE date BETWEEN :startDate AND :endDate AND is_excluded_from_total = 0 ORDER BY date DESC")
    List<Transaction> getNonExcludedTransactionsBetweenDatesSync(long startDate, long endDate);

}