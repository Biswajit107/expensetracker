package com.example.expensetracker.viewmodel;

import android.app.Application;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.expensetracker.models.ExclusionPattern;
import com.example.expensetracker.models.Transaction;
import com.example.expensetracker.repository.ExclusionPatternRepository;
import com.example.expensetracker.repository.TransactionRepository;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * ViewModel for managing exclusion patterns
 */
public class ExclusionPatternViewModel extends AndroidViewModel {
    private final ExclusionPatternRepository patternRepository;
    private final TransactionRepository transactionRepository;
    private final ExecutorService executorService;

    private LiveData<List<ExclusionPattern>> allPatterns;
    private MutableLiveData<Boolean> patternCreationResult = new MutableLiveData<>();

    public ExclusionPatternViewModel(Application application) {
        super(application);
        patternRepository = new ExclusionPatternRepository(application);
        transactionRepository = new TransactionRepository(application);
        executorService = Executors.newSingleThreadExecutor();

        // Load all patterns ordered by usage
        allPatterns = patternRepository.getAllPatternsOrderedByMatches();
    }

    /**
     * Get all exclusion patterns ordered by match count
     */
    public LiveData<List<ExclusionPattern>> getAllPatterns() {
        return allPatterns;
    }

    /**
     * Get result of pattern creation operation
     */
    public LiveData<Boolean> getPatternCreationResult() {
        return patternCreationResult;
    }

    /**
     * Create a new exclusion pattern from a transaction
     * that was manually excluded by the user
     */
    public void createPatternFromTransaction(Transaction transaction) {
        // Check if the transaction is manually excluded
        if (!transaction.isExcludedFromTotal() ||
                transaction.isOtherDebit() ||
                "AUTO".equals(transaction.getExclusionSource())) {

            // Not manually excluded, can't create pattern
            patternCreationResult.setValue(false);
            return;
        }

        // Mark the transaction as a source for exclusion pattern
        transaction.setExclusionSource("MANUAL_PATTERN");

        // Update the transaction in the database
        transactionRepository.updateTransaction(transaction);

        // Create pattern from the transaction
        patternRepository.createPatternFromTransaction(transaction, result -> {
            patternCreationResult.setValue(result > 0);
        });
    }

    /**
     * Deactivate an exclusion pattern
     */
    public void deactivatePattern(long patternId) {
        patternRepository.deactivatePattern(patternId);
    }

    /**
     * Delete an exclusion pattern
     */
    public void deletePattern(ExclusionPattern pattern) {
        patternRepository.deletePattern(pattern);
    }

    /**
     * Get transactions that are excluded by a specific pattern
     */
    public void getTransactionsExcludedByPattern(TransactionRepository.Callback<List<Transaction>> callback) {
        executorService.execute(() -> {
            // Find transactions that match this pattern

            // For now, a simple implementation - in a real app, you might
            // want to store a reference to the pattern ID in each transaction
            // that was auto-excluded

            // Get all auto-excluded transactions
            TransactionRepository.Callback<List<Transaction>> innerCallback = transactions -> {
                // TODO: Implement filtering based on pattern
                // This would require more complex logic to re-evaluate which transactions
                // match this specific pattern

                // For now, we'll just return all auto-excluded transactions
                callback.onResult(transactions);
            };

            // Use repository to get auto-excluded transactions
            // Note: You would need to add this method to your TransactionRepository
            transactionRepository.getAutoExcludedTransactions(innerCallback);
        });
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        if (executorService != null) {
            executorService.shutdown();
        }
    }
}