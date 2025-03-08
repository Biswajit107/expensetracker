package com.example.expensetracker.utils;

import android.content.Context;
import android.util.Log;

import com.example.expensetracker.database.TransactionDao;
import com.example.expensetracker.database.TransactionDatabase;
import com.example.expensetracker.models.Transaction;

import java.util.Calendar;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Utility class to help with transaction loading decisions
 */
public class TransactionLoadingUtils {
    private static final String TAG = "TransactionLoadingUtils";

    // Maximum period to check (in days) for performance
    private static final int MAX_QUICK_CHECK_DAYS = 90;

    // Threshold for determining if grouped view is better for performance
    private static final int GROUPED_VIEW_RECOMMENDED_THRESHOLD = 100;

    /**
     * Check if grouped view is recommended based on transaction density
     * This is useful for the first app launch
     *
     * @param context Application context
     * @param executorService Executor service to use for database operations
     * @return boolean True if grouped view is recommended
     */
    public static boolean isGroupedViewRecommended(Context context, ExecutorService executorService) {
        // Get a date range for the quick check (recent 90 days)
        Calendar endDate = Calendar.getInstance();
        Calendar startDate = (Calendar) endDate.clone();
        startDate.add(Calendar.DAY_OF_YEAR, -MAX_QUICK_CHECK_DAYS);

        // Use a CountDownLatch to wait for the check to complete
        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger count = new AtomicInteger(0);

        executorService.execute(() -> {
            try {
                TransactionDao dao = TransactionDatabase.getInstance(context).transactionDao();
                int transactionCount = dao.getTransactionCountBetweenDates(
                        startDate.getTimeInMillis(), endDate.getTimeInMillis());
                count.set(transactionCount);
            } catch (Exception e) {
                Log.e(TAG, "Error getting transaction count", e);
            } finally {
                latch.countDown();
            }
        });

        try {
            // Wait with timeout
            boolean completed = latch.await(2, TimeUnit.SECONDS);
            if (!completed) {
                Log.w(TAG, "Transaction count check timed out");
                return false; // Default to list view on timeout
            }

            int transactionCount = count.get();
            boolean isGroupedRecommended = transactionCount > GROUPED_VIEW_RECOMMENDED_THRESHOLD;

            Log.d(TAG, "Found " + transactionCount + " transactions in last " +
                    MAX_QUICK_CHECK_DAYS + " days. Grouped view recommended: " +
                    isGroupedRecommended);

            return isGroupedRecommended;
        } catch (InterruptedException e) {
            Log.e(TAG, "Interrupted waiting for transaction count", e);
            Thread.currentThread().interrupt();
            return false; // Default to list view on interruption
        }
    }

    /**
     * Calculate approximate transactions per day
     * Useful for making UI decisions
     *
     * @param context Application context
     * @param executorService Executor service to use for database operations
     * @return double Average transactions per day, or -1 if calculation failed
     */
    public static double calculateTransactionsPerDay(Context context, ExecutorService executorService) {
        // Use a CountDownLatch to wait for the calculation
        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger count = new AtomicInteger(0);
        AtomicInteger days = new AtomicInteger(0);

        executorService.execute(() -> {
            try {
                TransactionDao dao = TransactionDatabase.getInstance(context).transactionDao();

                // Get total transaction count
                List<Transaction> allTransactions = dao.getAllTransactionsSync();
                if (allTransactions == null || allTransactions.isEmpty()) {
                    count.set(0);
                    days.set(1); // Avoid division by zero
                    latch.countDown();
                    return;
                }

                count.set(allTransactions.size());

                // Find date range
                long minDate = Long.MAX_VALUE;
                long maxDate = Long.MIN_VALUE;

                for (Transaction transaction : allTransactions) {
                    long date = transaction.getDate();
                    if (date < minDate) minDate = date;
                    if (date > maxDate) maxDate = date;
                }

                // Calculate days (at least 1 to avoid division by zero)
                int dayCount = Math.max(1, (int) ((maxDate - minDate) / (24 * 60 * 60 * 1000L)));
                days.set(dayCount);
            } catch (Exception e) {
                Log.e(TAG, "Error calculating transactions per day", e);
                count.set(0);
                days.set(1); // Default values on error
            } finally {
                latch.countDown();
            }
        });

        try {
            // Wait with timeout
            boolean completed = latch.await(3, TimeUnit.SECONDS);
            if (!completed) {
                Log.w(TAG, "Transaction per day calculation timed out");
                return -1; // Error indicator
            }

            double transactionsPerDay = (double) count.get() / days.get();
            Log.d(TAG, "Calculated " + transactionsPerDay +
                    " transactions per day (" + count.get() + " transactions over " +
                    days.get() + " days)");

            return transactionsPerDay;
        } catch (InterruptedException e) {
            Log.e(TAG, "Interrupted waiting for transaction calculation", e);
            Thread.currentThread().interrupt();
            return -1; // Error indicator
        }
    }
}