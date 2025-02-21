package com.example.expensetracker.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.provider.Telephony;
import android.telephony.SmsMessage;
import android.util.Base64;
import android.util.Log;

import com.example.expensetracker.database.TransactionDao;
import com.example.expensetracker.database.TransactionDatabase;
import com.example.expensetracker.models.Transaction;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SMSReceiver extends BroadcastReceiver {
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Telephony.Sms.Intents.SMS_RECEIVED_ACTION.equals(intent.getAction())) {
            for (SmsMessage smsMessage : Telephony.Sms.Intents.getMessagesFromIntent(intent)) {
                parseAndSaveTransaction(context, smsMessage.getMessageBody());
            }
        }
    }

    private String generateMessageHash(String amount, String date, String description) {
        String content = amount + date + description;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            return Base64.encodeToString(hash, Base64.NO_WRAP);
        } catch (NoSuchAlgorithmException e) {
            Log.e("SMSReceiver", "Error generating hash", e);
            return content;
        }
    }

    private boolean isMessageProcessed(Context context, String messageHash) {
        try {
            return executorService.submit(() -> {
                TransactionDao dao = TransactionDatabase.getInstance(context).transactionDao();
                return dao.getTransactionByHash(messageHash) != null;
            }).get(); // wait for the result
        } catch (Exception e) {
            Log.e("SMSReceiver", "Error checking message hash", e);
            return false;
        }
    }

    public void parseAndSaveTransaction(Context context, String message) {
        // Clean the message
        String cleanMessage = message.replaceAll("\n", " ")
                .replaceAll("\\s+", " ")
                .trim();

        Log.d("SMSReceiver", "Processing message: " + cleanMessage);

        // First determine if it's a bank transaction message
        if (isBankTransaction(cleanMessage)) {
            Transaction transaction = parseTransaction(context, cleanMessage);
            if (transaction != null) {
                saveTransaction(context, transaction);
            }
        }
    }
    private boolean isBankTransaction(String message) {
        // Keywords that indicate this is a bank transaction
        String[] bankKeywords = {
                "HDFC Bank", "ICICI Bank", "SBI",
                "credited", "debited", "spent", "received", "sent",
                "A/C", "UPI", "NEFT", "IMPS"
        };

        message = message.toLowerCase();
        for (String keyword : bankKeywords) {
            if (message.contains(keyword.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    private Transaction parseTransaction(Context context, String message) {
        // Determine transaction type first
        TransactionType type = determineTransactionType(message);
        if (type == null) return null;

        // Extract amount
        Double amount = extractAmount(message);
        if (amount == null) return null;

        // Extract date
        Long date = extractDate(message);
        if (date == null) return null;

        // Extract description
        String description = generateDescription(message, type);

        // Determine bank
        String bank = determineBank(message);

        String messageHash = generateMessageHash(amount.toString(), date.toString(), description);

        // Check if already processed
        if (isMessageProcessed(context, messageHash)) {
            Log.d("SMSReceiver", "Message already processed: " + messageHash);
            return null;
        }

        Transaction transaction = new Transaction(bank, type.toString(), amount, date, description);
        transaction.setMessageHash(messageHash);
        return transaction;
    }

    private enum TransactionType {
        DEBIT, CREDIT
    }

    private TransactionType determineTransactionType(String message) {
        message = message.toLowerCase();

        // Debit indicators
        String[] debitKeywords = {
                "debited", "spent", "sent", "paid", "withdrawn", "debit", "payment"
        };

        // Credit indicators
        String[] creditKeywords = {
                "credited", "received", "credit", "added"
        };

        // Check for debit keywords
        for (String keyword : debitKeywords) {
            if (message.contains(keyword)) {
                return TransactionType.DEBIT;
            }
        }

        // Check for credit keywords
        for (String keyword : creditKeywords) {
            if (message.contains(keyword)) {
                return TransactionType.CREDIT;
            }
        }

        return null;
    }

    private Double extractAmount(String message) {
        // Pattern to match amount: Rs.XXX.XX or Rs XXX.XX or INR XXX.XX
        Pattern amountPattern = Pattern.compile(
                "(?i)(?:RS\\.?|INR)\\s*(\\d+(?:,\\d+)*\\.?\\d{0,2})"
        );

        Matcher matcher = amountPattern.matcher(message);
        if (matcher.find()) {
            try {
                String amountStr = matcher.group(1).replace(",", "");
                return Double.parseDouble(amountStr);
            } catch (NumberFormatException e) {
                Log.e("SMSReceiver", "Error parsing amount: " + matcher.group(1));
            }
        }
        return null;
    }

    private Long extractDate(String message) {
        // Try different date patterns
        String[] datePatterns = {
                "(\\d{2}/\\d{2}/\\d{2})",      // dd/MM/yy
                "(\\d{2}-\\d{2}-\\d{2})",      // dd-MM-yy
                "(\\d{2}/\\d{2}/\\d{4})",      // dd/MM/yyyy
                "(\\d{2}-\\d{2}-\\d{4})"       // dd-MM-yyyy
        };

        for (String patternStr : datePatterns) {
            Pattern pattern = Pattern.compile(patternStr);
            Matcher matcher = pattern.matcher(message);
            if (matcher.find()) {
                try {
                    String dateStr = matcher.group(1);
                    // Convert to consistent format
                    dateStr = dateStr.replace("-", "/");
                    SimpleDateFormat format;
                    if (dateStr.length() == 8) { // dd/MM/yy
                        format = new SimpleDateFormat("dd/MM/yy", Locale.getDefault());
                    } else {
                        format = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
                    }
                    return format.parse(dateStr).getTime();
                } catch (ParseException e) {
                    Log.e("SMSReceiver", "Error parsing date: " + matcher.group(1));
                }
            }
        }
        return null;
    }

    private String determineBank(String message) {
        message = message.toUpperCase();
        if (message.contains("HDFC")) return "HDFC";
        if (message.contains("ICICI")) return "ICICI";
        if (message.contains("SBI")) return "SBI";
        return "OTHER";
    }

    private String generateDescription(String message, TransactionType type) {
        message = message.toLowerCase();
        StringBuilder description = new StringBuilder();

        if (type == TransactionType.DEBIT) {
            if (message.contains("upi")) {
                description.append("UPI payment");
                // Try to extract recipient
                if (message.contains("to")) {
                    int toIndex = message.indexOf("to");
                    int endIndex = message.indexOf("on", toIndex);
                    if (endIndex == -1) endIndex = message.length();
                    String recipient = message.substring(toIndex + 2, endIndex).trim();
                    description.append(" to ").append(recipient);
                }
            } else if (message.contains("card")) {
                description.append("Card payment");
                // Try to extract merchant
                if (message.contains("at")) {
                    int atIndex = message.indexOf("at");
                    int endIndex = message.indexOf("on", atIndex);
                    if (endIndex == -1) endIndex = message.length();
                    String merchant = message.substring(atIndex + 2, endIndex).trim();
                    description.append(" at ").append(merchant);
                }
            }
        } else {
            if (message.contains("upi")) {
                description.append("UPI received");
                // Try to extract sender
                if (message.contains("from")) {
                    int fromIndex = message.indexOf("from");
                    int endIndex = message.indexOf("on", fromIndex);
                    if (endIndex == -1) endIndex = message.length();
                    String sender = message.substring(fromIndex + 4, endIndex).trim();
                    description.append(" from ").append(sender);
                }
            }
        }

        return description.toString();
    }

    private void saveTransaction(Context context, Transaction transaction) {
        Log.d("SMSReceiver", "Saving transaction: " + transaction.getAmount() +
                " - " + transaction.getDescription());

        executorService.execute(() -> {
            TransactionDatabase.getInstance(context)
                    .transactionDao()
                    .insert(transaction);
        });
    }
}
