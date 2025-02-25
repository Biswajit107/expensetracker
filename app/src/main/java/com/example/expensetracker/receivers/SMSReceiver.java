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
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SMSReceiver extends BroadcastReceiver {
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private static final String TAG = "SMSReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Telephony.Sms.Intents.SMS_RECEIVED_ACTION.equals(intent.getAction())) {
            for (SmsMessage smsMessage : Telephony.Sms.Intents.getMessagesFromIntent(intent)) {
                // Get the sender ID/number
                String sender = smsMessage.getDisplayOriginatingAddress();
                long timestamp = smsMessage.getTimestampMillis();
                parseAndSaveTransaction(context, smsMessage.getMessageBody(), sender, timestamp);
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

    public void parseAndSaveTransaction(Context context, String message, long messageDate) {
        parseAndSaveTransaction(context, message, null, messageDate);
    }


    // Updated parseAndSaveTransaction method to use the sender information
    public void parseAndSaveTransaction(Context context, String message, String sender, long messageDate) {
        // Clean the message
        String cleanMessage = message.replaceAll("\n", " ")
                .replaceAll("\\s+", " ")
                .trim();

        Log.d(TAG, "Processing message from " + sender + ": " + cleanMessage);

        // Log bank transaction detection
        boolean isBankTransaction = isBankTransaction(message, null);
        Log.d(TAG, "Is Bank Transaction: " + isBankTransaction);

        // First determine if it's a bank transaction message
        if (isBankTransaction) {
            Transaction transaction = parseTransaction(context, cleanMessage, messageDate);
            if (transaction != null) {
                saveTransaction(context, transaction);
            }
        }
    }


    private boolean isBankTransaction(String message, String sender) {
        // Clean the message
        String cleanMessage = message.toLowerCase();

        // 1. Sender-based detection (if sender is available)
        if (sender != null && !sender.isEmpty()) {
            String[] bankSenders = {"HDFCBK", "SBIINB", "ICICIB", "AXISBK", "YESBNK", "KOTAKB", "BOIIND",
                    "PNBSMS", "SCISMS", "CBSSBI", "CANBNK", "CENTBK", "BOBTXN"};
            for (String bankSender : bankSenders) {
                if (sender.toUpperCase().contains(bankSender)) {
                    return true;
                }
            }
        }

        // 2. Pattern-based detection for common bank SMS formats
        String[] transactionPatterns = {
                // Amount-based patterns
                "(?i)(?:rs\\.?|inr|₹)\\s*\\d+(?:[.,]\\d+)*",

                // Transaction notifications
                "(?i)(?:transaction|txn)(?:.{0,30})(?:confirmed|completed|successful)",
                "(?i)(?:debited|credited|deducted|received)(?:.{0,30})(?:a/c|account)",
                "(?i)(?:payment|debit|credit)(?:.{0,10})(?:alert|notice|info)",

                // Account-related information
                "(?i)(?:a/c|account)(?:.{0,5})(?:no|#|number|balance)(?:.{0,10})\\d+",
                "(?i)(?:available|avl)(?:.{0,5})(?:bal|balance)(?:.{0,5})(?:rs\\.?|inr|₹)",

                // Bank-specific formats
                "(?i)(?:info|alert)(?:.{0,30})(?:hdfc|sbi|icici|axis|kotak)",
                "(?i)(?:otp|txn|ref)(?:.{0,5})(?:id|no)(?:.{0,5})\\d+"
        };

        for (String pattern : transactionPatterns) {
            if (Pattern.compile(pattern).matcher(cleanMessage).find()) {
                return true;
            }
        }

        // 3. Contextual analysis - look for combinations of key elements
        boolean hasAmount = cleanMessage.matches(".*(?:rs\\.?|inr|₹)\\s*\\d+(?:[.,]\\d+)*.*");
        boolean hasBankName = false;
        boolean hasTransactionIndicator = false;

        // Check for bank names
        String[] bankNames = {"hdfc", "sbi", "icici", "axis", "yes bank", "kotak", "bank of", "indian"};
        for (String bank : bankNames) {
            if (cleanMessage.contains(bank)) {
                hasBankName = true;
                break;
            }
        }

        // Check for transaction indicators
        String[] transactionIndicators = {"debited", "credited", "debit", "credit", "payment",
                "transaction", "transfer", "spent", "received", "withdrawn",
                "deposited", "purchase", "sent", "upi", "neft", "imps", "rtgs"};
        for (String indicator : transactionIndicators) {
            if (cleanMessage.contains(indicator)) {
                hasTransactionIndicator = true;
                break;
            }
        }

        // If we have at least an amount and either a bank name or transaction indicator,
        // it's likely a bank transaction
        if (hasAmount && (hasBankName || hasTransactionIndicator)) {
            return true;
        }

        // 4. Fallback to keyword-based detection
        String[] bankKeywords = {
                "hdfc bank", "icici bank", "sbi", "hdfc", "icici", "yes bank",
                "credited", "debited", "spent", "received", "sent", "payment",
                "a/c", "upi", "neft", "imps", "deducted", "paytm", "withdrawal",
                "debit", "credit", "alert", "transaction", "transferred", "bank",
                "balance", "statement", "atm", "cash", "online", "banking"
        };

        for (String keyword : bankKeywords) {
            if (cleanMessage.contains(keyword)) {
                return true;
            }
        }

        return false;
    }

    private Transaction parseTransaction(Context context, String message, long messageDate) {

        // First check if this is an actual transaction message or just a future notification
        if (!isActualTransactionMessage(message)) {
            Log.d("SMSReceiver", "Ignoring future/instructional message: " + message);
            return null;
        }

        // Determine transaction type first
        TransactionType type = determineTransactionType(message);
        Log.d(TAG, "Determined Transaction Type: " + type);
        if (type == null) return null;

        // Extract amount
        Double amount = extractAmount(message);
        Log.d(TAG, "Extracted Amount: " + amount);
        if (amount == null) return null;

        // Extract date
        Long date = messageDate;
        Log.d(TAG, "Extracted Date: " + (date != null ? new Date(date) : "NULL"));
        if (date == null) return null;

        // Extract description
        String description = generateDescription(message, type);
        Log.d(TAG, "Generated Description: " + description);


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

        // Common patterns in bank transaction messages - improve regex patterns for better matching
        Map<String, Pattern> patterns = new HashMap<>();
        // More flexible pattern for "towards" with better handling of trailing content
        patterns.put("towards", Pattern.compile("towards\\s+([^.\\n\\(\\)]+?)(?:\\s+on|\\s+info|$|\\.)", Pattern.CASE_INSENSITIVE));
        // Enhanced "at" pattern that better handles merchant names
        patterns.put("at", Pattern.compile("at\\s+([^.\\n\\(\\)]+?)(?:\\s+on|\\s+info|$|\\.)", Pattern.CASE_INSENSITIVE));
        // Improved pattern for "to"
        patterns.put("to", Pattern.compile("to\\s+([^.\\n\\(\\)]+?)(?:\\s+on|\\s+info|$|\\.)", Pattern.CASE_INSENSITIVE));
        // Better "from" pattern
        patterns.put("from", Pattern.compile("from\\s+([^.\\n\\(\\)]+?)(?:\\s+on|\\s+info|$|\\.)", Pattern.CASE_INSENSITIVE));
        // More flexible reference number patterns
        patterns.put("umrn", Pattern.compile("umrn[:\\s]*(\\w+)", Pattern.CASE_INSENSITIVE));
        patterns.put("ref", Pattern.compile("(?:ref|reference)[.#:\\s]*(\\w+)", Pattern.CASE_INSENSITIVE));
        patterns.put("txnid", Pattern.compile("(?:txn\\s*id|txnid)[.#:\\s]*(\\w+)", Pattern.CASE_INSENSITIVE));
        patterns.put("for", Pattern.compile("for\\s+([^.\\n\\(\\)]+?)(?:\\s+on|\\s+info|$|\\.)", Pattern.CASE_INSENSITIVE));

        // Try to identify what kind of transaction this is - expanded keyword list
        String transactionType = "Transaction";
        if (message.contains("upi")) transactionType = "UPI";
        else if (message.contains("neft")) transactionType = "NEFT";
        else if (message.contains("imps")) transactionType = "IMPS";
        else if (message.contains("rtgs")) transactionType = "RTGS";
        else if (message.contains("card")) transactionType = "Card";
        else if (message.contains("debit card") || message.contains("db card")) transactionType = "Debit Card";
        else if (message.contains("credit card") || message.contains("cr card")) transactionType = "Credit Card";
        else if (message.contains("etmoney")) transactionType = "ETMONEY";
        else if (message.contains("bill")) transactionType = "Bill";
        else if (message.contains("emi")) transactionType = "EMI";
        else if (message.contains("investment")) transactionType = "Investment";
        else if (message.contains("salary")) transactionType = "Salary";
        else if (message.contains("loan")) transactionType = "Loan";
        else if (message.contains("mandate")) transactionType = "Mandate";

        StringBuilder description = new StringBuilder();

        // Start with transaction type
        if (type == TransactionType.DEBIT) {
            description.append(transactionType).append(" payment");
        } else {
            description.append(transactionType).append(" received");
        }

        // Try to find relevant context information
        String recipient = null;
        String reference = null;
        String purpose = null;

        // Look for recipient/sender with prioritized matching
        if (type == TransactionType.DEBIT) {
            // Try in order of reliability for different banks' formats
            Matcher towardsMatcher = patterns.get("towards").matcher(message);
            Matcher atMatcher = patterns.get("at").matcher(message);
            Matcher toMatcher = patterns.get("to").matcher(message);
            Matcher forMatcher = patterns.get("for").matcher(message);

            if (towardsMatcher.find()) {
                recipient = cleanExtractedText(towardsMatcher.group(1));
            } else if (atMatcher.find()) {
                recipient = cleanExtractedText(atMatcher.group(1));
            } else if (toMatcher.find()) {
                recipient = cleanExtractedText(toMatcher.group(1));
            } else if (forMatcher.find()) {
                purpose = cleanExtractedText(forMatcher.group(1));
            }
        } else {
            Matcher fromMatcher = patterns.get("from").matcher(message);
            if (fromMatcher.find()) {
                recipient = cleanExtractedText(fromMatcher.group(1));
            }
        }

        // Look for reference numbers with better extraction
        Matcher umrnMatcher = patterns.get("umrn").matcher(message);
        Matcher refMatcher = patterns.get("ref").matcher(message);
        Matcher txnMatcher = patterns.get("txnid").matcher(message);

        if (umrnMatcher.find()) {
            reference = "UMRN: " + umrnMatcher.group(1).trim();
        } else if (refMatcher.find()) {
            reference = "Ref: " + refMatcher.group(1).trim();
        } else if (txnMatcher.find()) {
            reference = "TxnID: " + txnMatcher.group(1).trim();
        }

        // Add recipient info if found, with better handling
        if (recipient != null) {
            if (type == TransactionType.DEBIT) {
                description.append(" to ");
            } else {
                description.append(" from ");
            }
            description.append(recipient);
        } else if (purpose != null) {
            description.append(" for ").append(purpose);
        }

        // Add reference if found
        if (reference != null) {
            description.append(" (").append(reference).append(")");
        }

        // Enhanced fallback mechanism
        if (description.toString().equals("Transaction payment") ||
                description.toString().equals("Transaction received") ||
                (recipient == null && reference == null && purpose == null)) {

            // First attempt: Extract merchant names from known formats
            Pattern merchantPattern = Pattern.compile("(?:at|to|with|by)\\s+((?:\\w+\\s*){1,4})", Pattern.CASE_INSENSITIVE);
            Matcher merchantMatcher = merchantPattern.matcher(message);

            if (merchantMatcher.find()) {
                String merchant = cleanExtractedText(merchantMatcher.group(1));
                if (merchant.length() > 2 && !isCommonWord(merchant)) {
                    return type == TransactionType.DEBIT ?
                            "Payment to " + merchant : "Received from " + merchant;
                }
            }

            // Second attempt: Find significant words in the message
            String simplifiedMsg = message.replaceAll("(?i)(payment|alert|info|a/c|account|no[.:]|balance|avl|bal|rs[.:]|inr|debit|credit|transaction|towards|through|using|yours?|has|been|will|this|that)", " ");
            String[] words = simplifiedMsg.split("\\s+");

            for (String word : words) {
                word = word.trim();
                if (word.length() > 3 && !isCommonWord(word) && !isNumeric(word)) {
                    return type == TransactionType.DEBIT ?
                            "Payment related to " + word.toUpperCase() :
                            "Credit related to " + word.toUpperCase();
                }
            }

            // Final fallback: Clean the original message
            String cleanedMessage = message
                    .replaceAll("(?i)payment alert!?", "")
                    .replaceAll("(?i)info:?", "")
                    .replaceAll("(?i)a/c no[.:]?\\s*\\d+", "")
                    .replaceAll("\\s+", " ")
                    .trim();

            // Truncate if too long
            if (cleanedMessage.length() > 100) {
                cleanedMessage = cleanedMessage.substring(0, 97) + "...";
            }

            return cleanedMessage;
        }

        return description.toString();
    }

    // Helper method to clean extracted text
    private String cleanExtractedText(String text) {
        if (text == null) return "";

        // Remove common noise words at the beginning and end of extracted text
        text = text.replaceAll("^(the|a|an|your|my)\\s+", "")
                .replaceAll("\\s+(account|acc|payment|amt)$", "")
                .replaceAll("\\s+", " ")
                .trim();

        return text;
    }

    private boolean isCommonWord(String word) {
        // Expanded list of common words to ignore
        String[] commonWords = {
                "from", "your", "has", "been", "with", "for", "and", "the", "you", "have",
                "will", "this", "that", "bank", "account", "amount", "payment", "transaction",
                "info", "alert", "balance", "debit", "credit", "available", "through"
        };

        for (String common : commonWords) {
            if (common.equalsIgnoreCase(word)) return true;
        }
        return false;
    }

    // Helper to check if a string is just numbers
    private boolean isNumeric(String str) {
        return str.matches("-?\\d+(\\.\\d+)?");
    }

    private boolean isActualTransactionMessage(String message) {
        message = message.toLowerCase();

        // Check for future tense indicators
        String[] futureIndicators = {
                "will be", "scheduled", "upcoming", "future",
                "planned", "due on", "due date", "reminder",
                "please note", "kindly note", "please be informed"
        };

        for (String indicator : futureIndicators) {
            if (message.contains(indicator)) {
                return false;
            }
        }

        // Check for instructional keywords
        String[] instructionalIndicators = {
                "please pay", "kindly pay", "payment due", "pay now",
                "due for payment", "please ensure", "kindly ensure",
                "requested", "request you to", "would be", "shall be"
        };

        for (String indicator : instructionalIndicators) {
            if (message.contains(indicator)) {
                return false;
            }
        }

        // Additional check for EMI due dates (common in bank messages)
        if (message.contains("emi") && (message.contains("due") || message.contains("will"))) {
            return false;
        }

        return true;
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
