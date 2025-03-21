package com.example.expensetracker.receivers;

import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.provider.Telephony;
import android.telephony.SmsMessage;
import android.util.Log;

import com.example.expensetracker.database.TransactionDao;
import com.example.expensetracker.database.TransactionDatabase;
import com.example.expensetracker.models.ExclusionPattern;
import com.example.expensetracker.models.Transaction;
import com.example.expensetracker.parser.ConfidenceScoreTransactionParser;
import com.example.expensetracker.parser.EnhancedTransactionParser;
import com.example.expensetracker.repository.ExclusionPatternRepository;
import com.example.expensetracker.utils.PreferencesManager;
import com.example.expensetracker.utils.TransactionDuplicateDetector;

import java.util.Date;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Enhanced SMS Receiver that uses the new EnhancedTransactionParser
 * for better detection of bank transaction messages, along with
 * learned exclusion pattern matching
 */
public class EnhancedSMSReceiver extends BroadcastReceiver {
    private static final String TAG = "EnhancedSMSReceiver";
    private final ExecutorService executorService;
    private final EnhancedTransactionParser parser;

    public EnhancedSMSReceiver() {
        executorService = Executors.newSingleThreadExecutor();
        parser = new ConfidenceScoreTransactionParser();
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Telephony.Sms.Intents.SMS_RECEIVED_ACTION.equals(intent.getAction())) {
            for (SmsMessage smsMessage : Telephony.Sms.Intents.getMessagesFromIntent(intent)) {
                // Get the sender ID/number
                String sender = smsMessage.getDisplayOriginatingAddress();
                String messageBody = smsMessage.getMessageBody();

                // Get the timestamp of the SMS
                long timestamp = smsMessage.getTimestampMillis();

                // Log the received message for debugging
                Log.d(TAG, "Received SMS from " + sender + " at " + new Date(timestamp) + ": " + messageBody);

                // Process the message
                parseAndSaveTransaction(context, messageBody, sender, timestamp);
            }
        }
    }

    // Inside parseAndSaveTransaction method in EnhancedSMSReceiver.java
    public void parseAndSaveTransaction(Context context, String message, String sender, long timestamp) {
        executorService.execute(() -> {
            try {
                // Step 1: Use the enhanced parser to parse the message
                Transaction transaction = parser.parseTransaction(message, sender, timestamp);

                // Step 2: If parsing failed, try fallback method for problematic messages
                if (transaction == null) {
                    Log.d(TAG, "Primary parsing failed, attempting fallback parsing");
                    transaction = parser.attemptFallbackParsing(message, sender, timestamp);

                    if (transaction == null) {
                        Log.d(TAG, "Fallback parsing also failed, skipping message");
                        return;
                    }
                }

                // Step 3: Get the DAO for duplicate checking
                TransactionDao dao = TransactionDatabase.getInstance(context).transactionDao();

                // Step 4: Check if this is a duplicate
                if (TransactionDuplicateDetector.isDuplicate(transaction, dao)) {
                    Log.d(TAG, "Duplicate transaction detected, skipping: " + transaction.getDescription());
                    return;
                }

                // NEW STEP: Check against exclusion patterns
                checkAgainstExclusionPatterns(context, transaction);

            } catch (Exception e) {
                Log.e(TAG, "Error processing transaction", e);
            }
        });
    }

    /**
     * Check transaction against learned exclusion patterns
     * This method checks if the transaction matches any exclusion pattern
     * and marks it as excluded if a match is found
     */
    private void checkAgainstExclusionPatterns(Context context, Transaction transaction) {
        // Create pattern repository
        ExclusionPatternRepository patternRepository = new ExclusionPatternRepository(getApplication(context));

        // Create a latch to wait for the async result
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<ExclusionPattern> matchingPattern = new AtomicReference<>(null);

        // Check for matching patterns
        patternRepository.checkForPatternMatch(transaction, pattern -> {
            matchingPattern.set(pattern);
            latch.countDown();
        });

        try {
            // Wait for the check to complete (with a timeout)
            boolean completed = latch.await(2, TimeUnit.SECONDS);

            if (completed) {
                if (matchingPattern.get() != null) {
                    // Found a matching pattern - mark transaction as excluded
                    transaction.setExcludedFromTotal(true);

                    // Set exclusion source to AUTO
                    transaction.setExclusionSource("AUTO");

                    Log.d(TAG, "Auto-excluded transaction based on learned pattern: " +
                            transaction.getDescription());
                } else {
                    // No matching pattern, continue with normal processing

                    // Handle unknown bank auto-exclusion (existing logic)
                    if ("OTHER".equals(transaction.getBank())) {
                        transaction.setExcludedFromTotal(true);
                        transaction.setOtherDebit(true);
                        transaction.setExclusionSource("AUTO_UNKNOWN_BANK");
                        Log.d(TAG, "Auto-excluded transaction from unknown bank: " + transaction.getDescription());
                    } else {
                        // Normal transaction, not excluded
                        transaction.setExclusionSource("NONE");
                    }
                }

                // Save the transaction
                saveTransaction(context, transaction);
                Log.d(TAG, "Successfully saved transaction: " + transaction.getDescription() +
                        ", amount: " + transaction.getAmount() +
                        (transaction.isExcludedFromTotal() ? " (excluded)" : ""));

                // Update last sync time
                new PreferencesManager(context).setLastSyncTime(System.currentTimeMillis());
            } else {
                // Timeout occurred - proceed with normal behavior
                if ("OTHER".equals(transaction.getBank())) {
                    transaction.setExcludedFromTotal(true);
                    transaction.setOtherDebit(true);
                    transaction.setExclusionSource("AUTO_UNKNOWN_BANK");
                }

                saveTransaction(context, transaction);
                Log.d(TAG, "Timeout occurred while checking patterns, saved with default behavior: " +
                        transaction.getDescription());
            }
        } catch (InterruptedException e) {
            Log.e(TAG, "Interrupted while checking exclusion patterns", e);
            Thread.currentThread().interrupt();

            // Save transaction with default behavior
            if ("OTHER".equals(transaction.getBank())) {
                transaction.setExcludedFromTotal(true);
                transaction.setOtherDebit(true);
                transaction.setExclusionSource("AUTO_UNKNOWN_BANK");
            }

            saveTransaction(context, transaction);
        }
    }

    /**
     * Utility method to get Application from context
     */
    private Application getApplication(Context context) {
        if (context.getApplicationContext() instanceof Application) {
            return (Application) context.getApplicationContext();
        }
        throw new IllegalStateException("Could not get application instance");
    }

    /**
     * Save a transaction to the database
     */
    private void saveTransaction(Context context, Transaction transaction) {
        try {
            TransactionDao dao = TransactionDatabase.getInstance(context).transactionDao();
            dao.insert(transaction);
            Log.d(TAG, "Transaction saved to database: " + transaction.getDescription());
        } catch (Exception e) {
            Log.e(TAG, "Error saving transaction to database", e);
        }
    }

}