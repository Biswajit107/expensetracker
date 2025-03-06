package com.example.expensetracker.repository;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.lifecycle.LiveData;

import com.example.expensetracker.database.ExclusionPatternDao;
import com.example.expensetracker.database.TransactionDatabase;
import com.example.expensetracker.models.ExclusionPattern;
import com.example.expensetracker.models.Transaction;
import com.example.expensetracker.utils.ExclusionPatternMatcher;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Repository for managing exclusion patterns
 */
public class ExclusionPatternRepository {
    private static final String TAG = "ExclusionPatternRepo";

    private final ExclusionPatternDao exclusionPatternDao;
    private final ExecutorService executorService;

    public ExclusionPatternRepository(Application application) {
        TransactionDatabase database = TransactionDatabase.getInstance(application);
        exclusionPatternDao = database.exclusionPatternDao();
        executorService = Executors.newSingleThreadExecutor();
    }

    /**
     * Create and save an exclusion pattern from a manually excluded transaction
     */
    public void createPatternFromTransaction(Transaction transaction, final Callback<Long> callback) {
        executorService.execute(() -> {
            // Create pattern from transaction
            ExclusionPattern pattern = ExclusionPatternMatcher.createPatternFromTransaction(transaction);

            if (pattern != null) {
                // Check if a pattern already exists for this transaction
                ExclusionPattern existingPattern = exclusionPatternDao.getPatternBySourceTransactionId(
                        transaction.getId());

                if (existingPattern != null) {
                    Log.d(TAG, "Pattern already exists for transaction " + transaction.getId() +
                            ", updating");

                    // Update existing pattern with new values
                    existingPattern.setMerchantPattern(pattern.getMerchantPattern());
                    existingPattern.setDescriptionPattern(pattern.getDescriptionPattern());
                    existingPattern.setMinAmount(pattern.getMinAmount());
                    existingPattern.setMaxAmount(pattern.getMaxAmount());
                    existingPattern.setTransactionType(pattern.getTransactionType());
                    existingPattern.setCategory(pattern.getCategory());
                    existingPattern.setActive(true); // Re-activate if it was deactivated

                    // Update pattern
                    exclusionPatternDao.update(existingPattern);

                    // Return existing pattern ID
                    new Handler(Looper.getMainLooper()).post(() -> {
                        callback.onResult(existingPattern.getId());
                    });
                } else {
                    // Insert new pattern
                    long patternId = exclusionPatternDao.insert(pattern);
                    Log.d(TAG, "Created new exclusion pattern with ID " + patternId +
                            " from transaction " + transaction.getId());

                    // Return pattern ID
                    new Handler(Looper.getMainLooper()).post(() -> {
                        callback.onResult(patternId);
                    });
                }
            } else {
                Log.e(TAG, "Failed to create pattern from transaction " + transaction.getId());
                new Handler(Looper.getMainLooper()).post(() -> {
                    callback.onResult(-1L);
                });
            }
        });
    }

    /**
     * Check if a transaction matches any active exclusion pattern
     * Returns the matching pattern ID or -1 if no match
     */
    public void checkForPatternMatch(Transaction transaction, final Callback<ExclusionPattern> callback) {
        executorService.execute(() -> {
            // Get all active patterns
            List<ExclusionPattern> patterns = exclusionPatternDao.getAllActivePatterns();

            if (patterns != null && !patterns.isEmpty()) {
                // Find best matching pattern
                ExclusionPattern matchingPattern = ExclusionPatternMatcher.findMatchingPattern(
                        transaction, patterns);

                if (matchingPattern != null) {
                    // Increment the match count for this pattern
                    exclusionPatternDao.incrementPatternMatchCount(matchingPattern.getId());

                    Log.d(TAG, "Transaction " + transaction.getId() +
                            " matches exclusion pattern from transaction " +
                            matchingPattern.getSourceTransactionId());

                    // Return the matching pattern
                    new Handler(Looper.getMainLooper()).post(() -> {
                        callback.onResult(matchingPattern);
                    });
                    return;
                }
            }

            // No match found
            new Handler(Looper.getMainLooper()).post(() -> {
                callback.onResult(null);
            });
        });
    }

    /**
     * Get all exclusion patterns ordered by match count
     */
    public LiveData<List<ExclusionPattern>> getAllPatternsOrderedByMatches() {
        return exclusionPatternDao.getAllPatternsOrderedByMatches();
    }

    /**
     * Deactivate an exclusion pattern
     */
    public void deactivatePattern(long patternId) {
        executorService.execute(() -> {
            exclusionPatternDao.deactivatePattern(patternId);
            Log.d(TAG, "Deactivated exclusion pattern with ID " + patternId);
        });
    }

    /**
     * Delete an exclusion pattern
     */
    public void deletePattern(ExclusionPattern pattern) {
        executorService.execute(() -> {
            exclusionPatternDao.delete(pattern);
            Log.d(TAG, "Deleted exclusion pattern with ID " + pattern.getId());
        });
    }

    // Callback interface
    public interface Callback<T> {
        void onResult(T result);
    }
}