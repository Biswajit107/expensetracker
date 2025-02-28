package com.example.expensetracker.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.provider.Telephony;
import android.telephony.SmsMessage;
import android.util.Log;

import com.example.expensetracker.database.TransactionDao;
import com.example.expensetracker.database.TransactionDatabase;
import com.example.expensetracker.models.Transaction;
import com.example.expensetracker.parser.EnhancedTransactionParser;
import com.example.expensetracker.utils.PreferencesManager;

import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Enhanced SMS Receiver that uses the new EnhancedTransactionParser
 * for better detection of bank transaction messages
 */
public class EnhancedSMSReceiver extends BroadcastReceiver {
    private static final String TAG = "EnhancedSMSReceiver";
    private final ExecutorService executorService;
    private final EnhancedTransactionParser parser;

    public EnhancedSMSReceiver() {
        executorService = Executors.newSingleThreadExecutor();
        parser = new EnhancedTransactionParser();
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
                if (parser.isDuplicate(transaction, dao)) {
                    Log.d(TAG, "Duplicate transaction detected, skipping: " + transaction.getDescription());
                    return;
                }

                // NEW CODE: Auto-exclude transactions from unknown banks
                if ("OTHER".equals(transaction.getBank())) {
                    transaction.setExcludedFromTotal(true);
                    transaction.setOtherDebit(true); // Mark as "other" transaction for UI distinction
                    Log.d(TAG, "Auto-excluded transaction from unknown bank: " + transaction.getDescription());
                }

                // Step 5: Save the transaction
                saveTransaction(context, transaction);
                Log.d(TAG, "Successfully saved transaction: " + transaction.getDescription() +
                        ", amount: " + transaction.getAmount() +
                        (transaction.isExcludedFromTotal() ? " (excluded)" : ""));

                // Step 6: Update last sync time
                new PreferencesManager(context).setLastSyncTime(System.currentTimeMillis());
            } catch (Exception e) {
                Log.e(TAG, "Error processing transaction", e);
            }
        });
    }

    /**
     * Parse and save a transaction from an SMS message (overloaded method)
     */
    public void parseAndSaveTransaction(Context context, String message, long messageDate) {
        parseAndSaveTransaction(context, message, null, messageDate);
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

    /**
     * For bulk processing of messages
     */
    public void processBulkMessages(Context context, List<SmsMessage> messages) {
        for (SmsMessage message : messages) {
            parseAndSaveTransaction(
                    context,
                    message.getMessageBody(),
                    message.getDisplayOriginatingAddress(),
                    message.getTimestampMillis()
            );
        }
    }

    /**
     * Clean up resources when the receiver is no longer needed
     */
    public void cleanup() {
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
        }
    }
}