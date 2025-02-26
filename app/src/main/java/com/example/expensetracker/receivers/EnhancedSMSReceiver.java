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
import com.example.expensetracker.parser.TransactionParser;
import com.example.expensetracker.utils.PreferencesManager;

import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class EnhancedSMSReceiver extends BroadcastReceiver {
    private static final String TAG = "EnhancedSMSReceiver";
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();


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

    public void parseAndSaveTransaction(Context context, String message, String sender, long timestamp) {
        executorService.execute(() -> {
            try {
                // 1. Use NLP processor to parse the message
                TransactionParser parser = new TransactionParser();
                Transaction transaction = parser.parseTransaction(message, sender, timestamp);

                if (transaction == null) {
                    Log.d(TAG, "Message could not be parsed as a transaction: " + message);
                    return;
                }

                // 2. Get the DAO for duplicate checking
                TransactionDao dao = TransactionDatabase.getInstance(context).transactionDao();

                // 3. Check if this is a duplicate
                if (parser.isDuplicate(transaction, dao)) {
                    Log.d(TAG, "Duplicate transaction detected, skipping: " + transaction.getDescription());
                    return;
                }

                // 4. Save the valid, non-duplicate transaction
                saveTransaction(context, transaction);
                Log.d(TAG, "Successfully saved transaction: " + transaction.getDescription() +
                        ", amount: " + transaction.getAmount());

//                // 5. Periodically clean up the cache
//                parser.cleanupCache(System.currentTimeMillis());

                // 6. Update last sync time
                new PreferencesManager(context).setLastSyncTime(System.currentTimeMillis());
            } catch (Exception e) {
                Log.e(TAG, "Error processing transaction", e);
            }
        });
    }

    /**
     * Save a transaction to the database
     * @param context Application context
     * @param transaction The transaction to save
     */
    private void saveTransaction(Context context, Transaction transaction) {
        Log.d(TAG, "Saving transaction: " + transaction.getAmount() +
                " - " + transaction.getDescription());

        try {
            TransactionDao dao = TransactionDatabase.getInstance(context).transactionDao();
            dao.insert(transaction);
        } catch (Exception e) {
            Log.e(TAG, "Error saving transaction to database", e);
        }
    }

    /**
     * Check if a message has already been processed
     * @param context Application context
     * @param messageHash The hash of the message
     * @return true if the message has been processed
     */
    private boolean isMessageProcessed(Context context, String messageHash) {
        try {
            TransactionDao dao = TransactionDatabase.getInstance(context).transactionDao();
            return dao.hasTransaction(messageHash);
        } catch (Exception e) {
            Log.e(TAG, "Error checking message hash", e);
            return false;
        }
    }

    /**
     * Clean up resources when this receiver is no longer needed
     */
    public void cleanup() {
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
        }
    }
}