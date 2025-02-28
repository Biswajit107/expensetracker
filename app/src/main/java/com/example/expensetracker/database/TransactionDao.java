package com.example.expensetracker.database;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.RawQuery;
import androidx.room.Update;
import androidx.room.Delete;
import androidx.sqlite.db.SupportSQLiteQuery;
//import androidx.room.Transaction;

import com.example.expensetracker.models.Transaction;
import java.util.List;

@Dao
public interface TransactionDao {
    // Basic CRUD operations
    @Insert
    void insert(Transaction transaction);

    @Update
    void update(Transaction transaction);

    @Delete
    void delete(Transaction transaction);

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

    // Category based queries
    @Query("SELECT * FROM transactions WHERE category = :category ORDER BY date DESC")
    LiveData<List<Transaction>> getTransactionsByCategory(String category);

    @Query("SELECT DISTINCT category FROM transactions WHERE category IS NOT NULL")
    List<String> getAllCategories();

    @Query("SELECT SUM(amount) FROM transactions WHERE category = :category AND type = 'DEBIT' AND date BETWEEN :startDate AND :endDate")
    double getCategorySpending(String category, long startDate, long endDate);

    // Bank based queries
    @Query("SELECT * FROM transactions WHERE bank = :bank")
    LiveData<List<Transaction>> getTransactionsByBank(String bank);

    @Query("SELECT * FROM transactions WHERE bank = :bank")
    List<Transaction> getTransactionsByBankSync(String bank);

    // Type based queries
    @Query("SELECT * FROM transactions WHERE type = :type")
    LiveData<List<Transaction>> getTransactionsByType(String type);

    // Combined filters
    @Query("SELECT * FROM transactions WHERE bank = :bank AND type = :type")
    LiveData<List<Transaction>> getTransactionsByBankAndType(String bank, String type);

    // Duplicate prevention
    @Query("SELECT EXISTS(SELECT 1 FROM transactions WHERE messageHash = :hash LIMIT 1)")
    boolean hasTransaction(String hash);

    @Query("SELECT * FROM transactions WHERE bank = :bank AND type = :type")
    List<Transaction> getTransactionsByBankAndTypeSync(String bank, String type);

    @Query("SELECT * FROM transactions WHERE messageHash = :hash LIMIT 1")
    Transaction getTransactionByHash(String hash);

    @Query("SELECT EXISTS(SELECT 1 FROM transactions)")
    boolean hasAnyTransactions();

    // Group related queries
    @Query("SELECT * FROM transactions WHERE group_key = :groupKey ORDER BY date DESC")
    List<Transaction> getTransactionsByGroup(String groupKey);

    @Query("SELECT DISTINCT group_key FROM transactions WHERE group_key IS NOT NULL")
    List<String> getAllGroups();

    // Analytics queries
    @Query("SELECT SUM(amount) FROM transactions WHERE type = 'DEBIT' AND is_excluded_from_total = 0 AND date BETWEEN :startDate AND :endDate")
    double getTotalDebits(long startDate, long endDate);

    @Query("SELECT SUM(amount) FROM transactions WHERE type = 'CREDIT' AND date BETWEEN :startDate AND :endDate")
    double getTotalCredits(long startDate, long endDate);

    // Recurring transactions
    @Query("SELECT * FROM transactions WHERE is_recurring = 1 ORDER BY date DESC")
    List<Transaction> getRecurringTransactions();

    @Query("SELECT * FROM transactions " +
            "WHERE description LIKE '%' || :merchantName || '%' " +
            "AND type = 'DEBIT' " +
            "AND date BETWEEN :startDate AND :endDate " +
            "ORDER BY date DESC")
    List<Transaction> getTransactionsByMerchant(String merchantName, long startDate, long endDate);

    // Budget analysis
    @Query("SELECT category, SUM(amount) as total " +
            "FROM transactions " +
            "WHERE type = 'DEBIT' " +
            "AND date BETWEEN :startDate AND :endDate " +
            "GROUP BY category")
    LiveData<List<CategoryTotal>> getCategoryTotals(long startDate, long endDate);

    // Search functionality
//    @Query("SELECT * FROM transactions " +
//            "WHERE (description LIKE '%' || :query || '%' " +
//            "OR merchant_name LIKE '%' || :query || '%' " +
//            "OR bank LIKE '%' || :query || '%') " +
//            "ORDER BY date DESC")
//    LiveData<List<Transaction>> searchTransactions(String query);

    @Query("SELECT * FROM transactions WHERE type = :type")
    List<Transaction> getTransactionsByTypeSync(String type);

    // For batch operations
    @androidx.room.Transaction
    @Query("UPDATE transactions SET is_other_debit = :isOtherDebit WHERE id IN (:transactionIds)")
    void updateOtherDebitStatus(List<Long> transactionIds, boolean isOtherDebit);

    // Statistics class for category totals
    static class CategoryTotal {
        public String category;
        public double total;
    }

    /**
     * Perform a flexible search across multiple transaction fields
     * @param query The raw SQL query built from a TransactionSearchFilter
     * @return LiveData list of matching transactions
     */
    @RawQuery(observedEntities = Transaction.class)
    LiveData<List<Transaction>> searchTransactionsWithFilter(SupportSQLiteQuery query);

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
     * Get a list of all unique merchant names in the database
     */
    @Query("SELECT DISTINCT merchant_name FROM transactions WHERE merchant_name IS NOT NULL AND merchant_name != '' ORDER BY merchant_name")
    List<String> getAllMerchants();

    /**
     * Get all transactions for a specific merchant
     */
    @Query("SELECT * FROM transactions WHERE merchant_name LIKE '%' || :merchantName || '%' ORDER BY date DESC")
    List<Transaction> getTransactionsByMerchant(String merchantName);

    /**
     * Get transactions by amount range
     */
    @Query("SELECT * FROM transactions WHERE amount BETWEEN :minAmount AND :maxAmount ORDER BY date DESC")
    List<Transaction> getTransactionsByAmountRange(double minAmount, double maxAmount);

    /**
     * Get count of auto-excluded transactions from OTHER banks
     */
    @Query("SELECT COUNT(*) FROM transactions WHERE bank = 'OTHER' AND is_excluded_from_total = 1 AND is_other_debit = 1")
    int getAutoExcludedTransactionCount();

    /**
     * Get all auto-excluded transactions from OTHER banks
     */
    @Query("SELECT * FROM transactions WHERE bank = 'OTHER' AND is_excluded_from_total = 1 AND is_other_debit = 1 ORDER BY date DESC")
    List<Transaction> getAutoExcludedTransactionsSync();

    /**
     * Include all auto-excluded transactions (set excluded status to false)
     * Returns the number of transactions updated
     */
    @Query("UPDATE transactions SET is_excluded_from_total = 0 WHERE bank = 'OTHER' AND is_excluded_from_total = 1 AND is_other_debit = 1")
    int includeAllAutoExcludedTransactions();

    /**
     * Get transactions from OTHER banks
     */
    @Query("SELECT * FROM transactions WHERE bank = 'OTHER' ORDER BY date DESC")
    List<Transaction> getOtherBankTransactionsSync();

    /**
     * Get OTHER bank transactions between dates
     */
    @Query("SELECT * FROM transactions WHERE bank = 'OTHER' AND date BETWEEN :startDate AND :endDate ORDER BY date DESC")
    List<Transaction> getOtherBankTransactionsBetweenDatesSync(long startDate, long endDate);

}