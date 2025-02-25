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
import com.example.expensetracker.utils.PreferencesManager;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class EnhancedSMSReceiver extends BroadcastReceiver {
    private static final String TAG = "EnhancedSMSReceiver";
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    // Time window for duplicate detection (in milliseconds)
    private static final long DUPLICATE_TIME_WINDOW = 10 * 60 * 1000; // 10 minutes

    // Common bank sender IDs
    private static final String[] BANK_SENDERS = {
            "HDFCBK", "SBIINB", "ICICIB", "AXISBK", "YESBNK", "KOTAKB", "BOIIND",
            "PNBSMS", "SCISMS", "CBSSBI", "CANBNK", "CENTBK", "BOBTXN", "IDBIBK"
    };

    // Store the most recent transaction fingerprints to avoid duplicates
    private static final Map<String, Long> recentTransactions = new HashMap<>();

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

//    public void parseAndSaveTransaction(Context context, String message) {
//        parseAndSaveTransaction(context, message, null, System.currentTimeMillis());
//    }

//    public void parseAndSaveTransaction(Context context, String message, String sender) {
//        parseAndSaveTransaction(context, message, sender, System.currentTimeMillis());
//    }

    public void parseAndSaveTransaction(Context context, String message, String sender, long timestamp) {
        // Clean the message
        String cleanMessage = message.replaceAll("\n", " ")
                .replaceAll("\\s+", " ")
                .trim();

        Log.d(TAG, "Processing message from " + sender + ": " + cleanMessage);

        // First determine if it's a bank transaction message
        if (isBankTransaction(cleanMessage, sender)) {
            Transaction transaction = parseTransaction(context, cleanMessage, sender, timestamp);
            if (transaction != null) {
                saveTransaction(context, transaction);
            }
        }
    }

    private boolean isBankTransaction(String message, String sender) {
        // Clean the message
        String cleanMessage = message.toLowerCase();

        // First, check for common non-transaction messages
        if (isNonTransactionBankMessage(cleanMessage)) {
            return false;
        }

        // 1. Sender-based detection (if sender is available)
        boolean isBankSender = false;
        if (sender != null && !sender.isEmpty()) {
            for (String bankSender : BANK_SENDERS) {
                if (sender.toUpperCase().contains(bankSender)) {
                    isBankSender = true;
                    break;
                }
            }
        }

        // 2. Check for transaction indicators (more strict now)
        boolean hasTransactionKeyword =
                cleanMessage.contains("debited") ||
                        cleanMessage.contains("credited") ||
                        cleanMessage.contains("payment") ||
                        cleanMessage.contains("debit") && !cleanMessage.contains("debit card") ||
                        cleanMessage.contains("credit") && !cleanMessage.contains("credit card") ||
                        cleanMessage.contains("transaction") ||
                        cleanMessage.contains("transfer") ||
                        cleanMessage.contains("spent") ||
                        cleanMessage.contains("purchase") ||
                        cleanMessage.contains("withdrawal") ||
                        cleanMessage.contains("deposit");

        // 3. Check for amount patterns
        Pattern amountPattern = Pattern.compile(
                "(?i)(?:rs\\.?|inr|₹)\\s*(\\d+(?:,\\d+)*\\.?\\d{0,2})"
        );
        boolean hasAmountPattern = amountPattern.matcher(cleanMessage).find();

        // Transaction messages must have both transaction keywords AND amount patterns
        if (hasTransactionKeyword && hasAmountPattern) {
            return true;
        }

        // Special handling for UPI messages which might have different formats
        if (cleanMessage.contains("upi") &&
                (cleanMessage.contains("ref") || cleanMessage.contains("txn"))) {
            return true;
        }

        return false;
    }

    private boolean isNonTransactionBankMessage(String message) {
        // Identify balance alerts and offers
        if (message.contains("available bal") ||
                message.contains("available balance") ||
                message.contains("bal in") ||
                message.contains("balance in")) {
            return true;
        }

        // Identify promotional messages
        if ((message.contains("ready to be credited") || message.contains("offer")) &&
                !message.contains("has been credited") &&
                !message.contains("was credited")) {
            return true;
        }

        // OTP and notification patterns
        if (message.contains("otp") ||
                message.contains("password") ||
                message.contains("verification code")) {
            return true;
        }

        // Info/update messages
        if (message.contains("for real time") ||
                message.contains("for more details") ||
                message.contains("subject to clearing") ||
                message.contains("update") ||
                message.contains("advisory")) {
            return true;
        }

        // Links often indicate promotional content
        if (message.contains("http") &&
                !message.contains("debited") &&
                !message.contains("credited")) {
            return true;
        }

        return false;
    }

    private Transaction parseTransaction(Context context, String message, String sender, long timestamp) {
        // Determine transaction type first
        TransactionType type = determineTransactionType(message);
        if (type == null) return null;

        // Extract amount
        Double amount = extractAmount(message);
        if (amount == null) return null;

        // Use the provided timestamp from the SMS
        long date = timestamp;

        // Determine bank
        String bank = determineBank(message, sender);

        // Extract merchant/recipient name
        String merchantName = extractMerchantName(message, type);

        // Generate comprehensive description
        String description = generateDescription(message, type, merchantName);

        // Create a transaction fingerprint for duplicate detection
        String fingerprint = generateTransactionFingerprint(amount, date, merchantName, bank);

        // Check if this is a duplicate transaction
        if (isDuplicateTransaction(context, fingerprint, date, amount, description, merchantName)) {
            Log.d(TAG, "Duplicate transaction detected. Skipping.");
            return null;
        }

        // Create the transaction object
        Transaction transaction = new Transaction(bank, type.toString(), amount, date, description);
        transaction.setMessageHash(fingerprint);
        transaction.setOriginalSms(message); // Store the original SMS

        // Set merchant name if extracted
        if (merchantName != null && !merchantName.isEmpty()) {
            transaction.setMerchantName(merchantName);
        }

        // Try to categorize the transaction
        String category = categorizeTransaction(description, merchantName);
        if (category != null) {
            transaction.setCategory(category);
        }

        return transaction;
    }

    private boolean isDuplicateTransaction(Context context, String fingerprint, long date, double amount, String description, String merchantName) {
        // First, check recent transactions in memory cache
        long currentTime = System.currentTimeMillis();

        // Check if we've seen this fingerprint recently
        if (recentTransactions.containsKey(fingerprint)) {
            long previousTime = recentTransactions.get(fingerprint);
            if (currentTime - previousTime < DUPLICATE_TIME_WINDOW) {
                return true;
            }
        }

        // Add/update this transaction in the recent transactions map
        recentTransactions.put(fingerprint, currentTime);

        // Clean up old entries from the map (same as before)
        // ...

        // Check the database for similar transactions
        try {
            return executorService.submit(() -> {
                TransactionDao dao = TransactionDatabase.getInstance(context).transactionDao();

                // First check exact fingerprint match
                if (dao.hasTransaction(fingerprint)) {
                    return true;
                }

                // Check for similar transactions in a time window (expand to 30 minutes)
                Calendar cal = Calendar.getInstance();
                cal.setTimeInMillis(date);
                cal.add(Calendar.MINUTE, -30);
                long startTime = cal.getTimeInMillis();

                cal.setTimeInMillis(date);
                cal.add(Calendar.MINUTE, 30);
                long endTime = cal.getTimeInMillis();

                // Get transactions in the time window
                List<Transaction> similarTransactions = dao.getTransactionsBetweenDatesSync(startTime, endTime);

                // Check for similar transactions (amount, merchant, etc.)
                for (Transaction t : similarTransactions) {
                    // Amount-based check (exact match or very close)
                    double amountDiff = Math.abs(t.getAmount() - amount);
                    boolean isSameAmount = amountDiff < 0.01;

                    if (isSameAmount) {
                        // If we have the same amount, check for merchant name or description similarity
                        String tMerchant = t.getMerchantName();
                        String tDesc = t.getDescription();

                        // Merchant name check
                        boolean hasSimilarMerchant = false;
                        if (merchantName != null && !merchantName.isEmpty() &&
                                tMerchant != null && !tMerchant.isEmpty()) {
                            // Either direct match or one contains the other
                            hasSimilarMerchant = tMerchant.equalsIgnoreCase(merchantName) ||
                                    tMerchant.toLowerCase().contains(merchantName.toLowerCase()) ||
                                    merchantName.toLowerCase().contains(tMerchant.toLowerCase());
                        }

                        // Description similarity check (for sender vs receiver notifications)
                        boolean hasSimilarDescription = false;
                        if (tDesc != null && description != null) {
                            if (tDesc.toLowerCase().contains("to") && description.toLowerCase().contains("from")) {
                                // One is a "to" transaction and one is a "from" transaction - likely same transaction
                                // viewed from different sides
                                hasSimilarDescription = true;
                            } else if (tDesc.toLowerCase().contains("from") && description.toLowerCase().contains("to")) {
                                // Same as above, reversed
                                hasSimilarDescription = true;
                            } else {
                                // Calculate word similarity
                                double similarity = calculateWordSimilarity(tDesc, description);
                                hasSimilarDescription = similarity > 0.3; // 30% word overlap
                            }
                        }

                        if (hasSimilarMerchant || hasSimilarDescription) {
                            return true;
                        }
                    }
                }

                return false;
            }).get();
        } catch (Exception e) {
            Log.e(TAG, "Error checking for duplicate transaction", e);
            return false;
        }
    }

    // Method to calculate word similarity between two strings
    private double calculateWordSimilarity(String str1, String str2) {
        if (str1 == null || str2 == null) {
            return 0.0;
        }

        // Extract words (remove punctuation, split on spaces)
        String[] words1 = str1.toLowerCase().replaceAll("[^a-z0-9\\s]", " ").split("\\s+");
        String[] words2 = str2.toLowerCase().replaceAll("[^a-z0-9\\s]", " ").split("\\s+");

        // Count matching words
        int matches = 0;
        for (String word1 : words1) {
            if (word1.length() < 3) continue; // Skip short words

            for (String word2 : words2) {
                if (word2.length() < 3) continue;

                if (word1.equals(word2)) {
                    matches++;
                    break;
                }
            }
        }

        // Calculate Jaccard coefficient
        int totalWords = words1.length + words2.length - matches;
        if (totalWords == 0) return 0.0;

        return (double) matches / totalWords;
    }

    private String generateTransactionFingerprint(Double amount, Long date, String merchantName, String bank) {
        // Round amount to two decimal places to handle minor formatting differences
        String amountStr = String.format("%.2f", amount);

        // Get just the date part (ignore time) to handle time variations
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(date);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        String dateStr = String.valueOf(cal.getTimeInMillis());

        // Use merchant info if available
        String merchantInfo = (merchantName != null && !merchantName.isEmpty()) ?
                merchantName.toLowerCase() : "";

        // Include bank for additional uniqueness
        String bankInfo = (bank != null) ? bank.toLowerCase() : "";

        // Create fingerprint from the stable elements
        String content = amountStr + dateStr + merchantInfo + bankInfo;

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            return Base64.encodeToString(hash, Base64.NO_WRAP);
        } catch (NoSuchAlgorithmException e) {
            Log.e(TAG, "Error generating hash", e);
            return content;
        }
    }

    private enum TransactionType {
        DEBIT, CREDIT
    }

    private TransactionType determineTransactionType(String message) {
        message = message.toLowerCase();

        // Debit indicators
        String[] debitKeywords = {
                "debited", "spent", "sent", "paid", "withdrawn", "debit", "deducted",
                "payment", "purchase", "txn", "paying", "charged"
        };

        // Credit indicators
        String[] creditKeywords = {
                "credited", "received", "credit", "added", "deposited", "refund", "cashback"
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

        // Default to DEBIT if no clear indicator (more common)
        return TransactionType.DEBIT;
    }

    private Double extractAmount(String message) {
        // Multiple patterns to match amounts
        String[] amountPatterns = {
                // Rs. X,XXX.XX or INR X,XXX.XX format
                "(?i)(?:rs\\.?|inr|₹)\\s*(\\d+(?:,\\d+)*(?:\\.\\d{1,2})?)",
                // X,XXX.XX debited/credited format
                "(?i)(\\d+(?:,\\d+)*(?:\\.\\d{1,2})?)\\s*(?:rs\\.?|inr|₹)?\\s*(?:debited|credited|sent|received)",
                // Last resort: any number followed by decimal
                "(?i)(?:amount|amt|of|for)\\s*(?:rs\\.?|inr|₹)?\\s*(\\d+(?:,\\d+)*(?:\\.\\d{1,2})?)"
        };

        for (String patternStr : amountPatterns) {
            Pattern pattern = Pattern.compile(patternStr);
            Matcher matcher = pattern.matcher(message);
            if (matcher.find()) {
                try {
                    String amountStr = matcher.group(1).replace(",", "");
                    return Double.parseDouble(amountStr);
                } catch (NumberFormatException e) {
                    Log.e(TAG, "Error parsing amount: " + matcher.group(1), e);
                    // Continue to next pattern
                }
            }
        }
        return null;
    }

    private String determineBank(String message, String sender) {
        // First try to identify from sender ID
        if (sender != null && !sender.isEmpty()) {
            if (sender.toUpperCase().contains("HDFC")) return "HDFC";
            if (sender.toUpperCase().contains("SBI")) return "SBI";
            if (sender.toUpperCase().contains("ICICI")) return "ICICI";
            if (sender.toUpperCase().contains("AXIS")) return "AXIS";
            if (sender.toUpperCase().contains("KOTAK")) return "KOTAK";
            if (sender.toUpperCase().contains("PNB")) return "PNB";
            if (sender.toUpperCase().contains("BOI")) return "BOI";
            if (sender.toUpperCase().contains("BOB")) return "BOB";
            if (sender.toUpperCase().contains("IDBI")) return "IDBI";
            if (sender.toUpperCase().contains("YES")) return "YES";
            if (sender.toUpperCase().contains("CANBNK")) return "CANARA";
        }

        // Then try to identify from message content
        message = message.toUpperCase();
        if (message.contains("HDFC")) return "HDFC";
        if (message.contains("ICICI")) return "ICICI";
        if (message.contains("SBI")) return "SBI";
        if (message.contains("AXIS")) return "AXIS";
        if (message.contains("KOTAK")) return "KOTAK";
        if (message.contains("PNB")) return "PNB";
        if (message.contains("BANK OF INDIA")) return "BOI";
        if (message.contains("BANK OF BARODA")) return "BOB";
        if (message.contains("IDBI")) return "IDBI";
        if (message.contains("YES BANK")) return "YES";
        if (message.contains("CANARA")) return "CANARA";

        return "OTHER";
    }

    private String extractMerchantName(String message, TransactionType type) {
        message = message.toLowerCase();

        // Different patterns based on transaction type
        if (type == TransactionType.DEBIT) {
            String[] debitPatterns = {
                    "(?<=to )([\\w\\s&.,'\\-]+?)(?= on| info| [0-9]|$)",
                    "(?<=at )([\\w\\s&.,'\\-]+?)(?= on| info| [0-9]|$)",
                    "(?<=pay to )([\\w\\s&.,'\\-]+?)(?= on| info| [0-9]|$)",
                    "(?<=payee )([\\w\\s&.,'\\-]+?)(?= on| info| [0-9]|$)",
                    "(?<=merchant )([\\w\\s&.,'\\-]+?)(?= on| info| [0-9]|$)",
                    "(?<=shop )([\\w\\s&.,'\\-]+?)(?= on| via| info| [0-9]|$)",
                    "(?<=tpv-)([\\w\\s&.,'\\-]+?)(?=-)",
                    "(?<=towards )([\\w\\s&.,'\\-]+?)(?= on| info| [0-9]|$)"
            };

            return extractEntityWithPatterns(message, debitPatterns);
        } else {
            String[] creditPatterns = {
                    "(?<=from )([\\w\\s&.,'\\-]+?)(?= on| info| [0-9]|$)",
                    "(?<=by )([\\w\\s&.,'\\-]+?)(?= on| info| [0-9]|$)",
                    "(?<=sender )([\\w\\s&.,'\\-]+?)(?= on| info| [0-9]|$)",
                    "(?<=from a/c )([\\w\\s&.,'\\-]+?)(?= on| info| [0-9]|$)"
            };

            return extractEntityWithPatterns(message, creditPatterns);
        }
    }

    private String extractEntityWithPatterns(String message, String[] patterns) {
        for (String patternStr : patterns) {
            try {
                Pattern pattern = Pattern.compile(patternStr, Pattern.CASE_INSENSITIVE);
                Matcher matcher = pattern.matcher(message);
                if (matcher.find()) {
                    String entity = matcher.group(1).trim();
                    // Basic cleanup of the entity
                    entity = entity.replaceAll("\\s+", " ").trim();
                    // Make sure it's not absurdly long (likely not a real merchant name)
                    if (entity.length() <= 30) {
                        return capitalizeWords(entity);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error extracting entity with pattern: " + patternStr, e);
            }
        }
        return "";
    }

    private String generateDescription(String message, TransactionType type, String merchantName) {
        message = message.toLowerCase();
        StringBuilder description = new StringBuilder();

        // Extract transaction method
        if (message.contains("upi")) {
            description.append("UPI ");
        } else if (message.contains("neft")) {
            description.append("NEFT ");
        } else if (message.contains("imps")) {
            description.append("IMPS ");
        } else if (message.contains("rtgs")) {
            description.append("RTGS ");
        } else if (message.contains("atm")) {
            description.append("ATM ");
        } else if (message.contains("card") || message.contains("debit card") || message.contains("credit card")) {
            description.append("Card ");
        }

        // Add transaction type and merchant info
        if (type == TransactionType.DEBIT) {
            if (description.length() == 0) {
                description.append("Payment ");
            } else {
                description.append("payment ");
            }

            if (merchantName != null && !merchantName.isEmpty()) {
                description.append("to ").append(merchantName);
            }

        } else { // CREDIT
            if (description.length() == 0) {
                description.append("Received ");
            } else {
                description.append("received ");
            }

            if (merchantName != null && !merchantName.isEmpty()) {
                description.append("from ").append(merchantName);
            }
        }

        // Try to extract reference number if available
        String reference = extractEntityWithPatterns(message, new String[] {
                "(?<=ref no\\.? )([\\w\\d]+)",
                "(?<=ref )([\\w\\d]+)",
                "(?<=txn id:? )([\\w\\d]+)",
                "(?<=txnid:? )([\\w\\d]+)",
                "(?<=utr:? )([\\w\\d]+)"
        });

        if (!reference.isEmpty()) {
            description.append(" (Ref: ").append(reference).append(")");
        }

        // If we couldn't extract anything meaningful, use a fallback approach
        if (description.toString().trim().isEmpty()) {
            // Try to extract any sequence that might represent a purpose
            String purpose = extractEntityWithPatterns(message, new String[] {
                    "(?<=info:)([^.]+)",
                    "(?<=-info:)([^.]+)",
                    "(?<=purpose:)([^.]+)",
                    "(?<=for )([\\w\\s&.,'\\-]+?)(?= on| info| [0-9]|$)"
            });

            if (!purpose.isEmpty()) {
                if (type == TransactionType.DEBIT) {
                    description.append("Payment for ").append(purpose);
                } else {
                    description.append("Received payment for ").append(purpose);
                }
            } else {
                // Absolute fallback
                description.append(type == TransactionType.DEBIT ? "Payment" : "Received payment");
            }
        }

        return description.toString().trim();
    }

    private String categorizeTransaction(String description, String merchantName) {
        // Combine description and merchant name for better categorization
        String text = (description + " " + (merchantName != null ? merchantName : "")).toLowerCase();

        // Food & Dining
        if (containsAny(text, new String[] {
                "restaurant", "cafe", "bistro", "food", "dinner", "lunch", "breakfast",
                "swiggy", "zomato", "uber eat", "pizza", "burger", "sushi", "doordash",
                "mcdonald", "subway", "starbucks", "kfc", "domino", "grocery", "supermarket"
        })) {
            return Transaction.Categories.FOOD;
        }

        // Shopping
        if (containsAny(text, new String[] {
                "shop", "store", "mall", "mart", "amazon", "flipkart", "myntra", "ajio",
                "retail", "purchase", "buy", "clothing", "fashion", "apparel", "shoe",
                "electronic", "gadget", "appliance", "furniture", "decor"
        })) {
            return Transaction.Categories.SHOPPING;
        }

        // Bills & Utilities
        if (containsAny(text, new String[] {
                "bill", "utility", "electric", "water", "gas", "internet", "broadband",
                "wifi", "mobile", "phone", "recharge", "dth", "cable", "subscription",
                "rent", "maintenance", "insurance", "tax", "loan", "emi", "mortgage"
        })) {
            return Transaction.Categories.BILLS;
        }

        // Entertainment
        if (containsAny(text, new String[] {
                "movie", "cinema", "theater", "netflix", "amazon prime", "hotstar", "disney+",
                "spotify", "music", "concert", "show", "event", "ticket", "game", "play",
                "festival", "park", "museum", "exhibition", "zoo", "amusement"
        })) {
            return Transaction.Categories.ENTERTAINMENT;
        }

        // Transport
        if (containsAny(text, new String[] {
                "uber", "ola", "taxi", "cab", "auto", "rickshaw", "bus", "train", "metro",
                "railway", "flight", "airline", "travel", "transport", "petrol", "diesel",
                "fuel", "gas station", "parking", "toll"
        })) {
            return Transaction.Categories.TRANSPORT;
        }

        // Health
        if (containsAny(text, new String[] {
                "hospital", "clinic", "doctor", "medical", "pharmacy", "medicine", "health",
                "healthcare", "dental", "dentist", "eye", "optical", "therapy", "fitness",
                "gym", "wellness", "spa", "massage", "meditation", "yoga"
        })) {
            return Transaction.Categories.HEALTH;
        }

        // Education
        if (containsAny(text, new String[] {
                "school", "college", "university", "education", "tuition", "class", "course",
                "tutorial", "coaching", "exam", "fee", "book", "library", "stationery",
                "workshop", "seminar", "conference", "training"
        })) {
            return Transaction.Categories.EDUCATION;
        }

        // Default category
        return Transaction.Categories.OTHERS;
    }

    private boolean containsAny(String text, String[] keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private String capitalizeWords(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }

        StringBuilder result = new StringBuilder();
        String[] words = input.split("\\s+");
        for (String word : words) {
            if (!word.isEmpty()) {
                result.append(Character.toUpperCase(word.charAt(0)))
                        .append(word.substring(1).toLowerCase())
                        .append(" ");
            }
        }
        return result.toString().trim();
    }

    private void saveTransaction(Context context, Transaction transaction) {
        Log.d(TAG, "Saving transaction: " + transaction.getAmount() +
                " - " + transaction.getDescription());

        executorService.execute(() -> {
            TransactionDatabase.getInstance(context)
                    .transactionDao()
                    .insert(transaction);

            // Update the last sync time
            new PreferencesManager(context).setLastSyncTime(System.currentTimeMillis());
        });
    }
}