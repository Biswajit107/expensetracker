package com.example.expensetracker.nlp;

import android.util.Log;
import android.util.Base64;

import com.example.expensetracker.database.TransactionDao;
import com.example.expensetracker.models.Transaction;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class NLPTransactionProcessor {
    private static final String TAG = "NLPTransactionProcessor";

    // Comprehensive keyword sets
    private static final Set<String> TRANSACTION_KEYWORDS = new HashSet<>(Arrays.asList(
            // Debit keywords
            "debited", "debit", "paid", "spent", "withdrawn",
            "payment", "purchase", "transaction", "charged",
            "transferred", "txn", "deducted",

            // Credit keywords
            "credited", "credit", "received", "refund",
            "cashback", "deposit", "added", "incoming"
    ));

    private static final Set<String> BANK_KEYWORDS = new HashSet<>(Arrays.asList(
            "hdfc", "sbi", "icici", "axis", "kotak", "bob",
            "pnb", "canara", "indian bank", "yes bank"
    ));

    private static final Set<String> EXCLUSION_KEYWORDS = new HashSet<>(Arrays.asList(
            "balance", "available", "stmt", "statement",
            "otp", "password", "verification", "login",
            "due", "reminder", "upcoming", "scheduled",
            "expiry", "limit"
    ));

    // Amount detection pattern
    private static final Pattern AMOUNT_PATTERN = Pattern.compile(
            "(?:₹|Rs\\.?|INR)\\s*([0-9,]+(?:\\.\\d{1,2})?)",
            Pattern.CASE_INSENSITIVE
    );

    private final TransactionLexicalAnalyzer lexicalAnalyzer;
    private final SemanticSimilarityDetector similarityDetector;
    private final TransactionSentimentAnalyzer sentimentAnalyzer;
    private final TransactionCache transactionCache;

    public NLPTransactionProcessor() {
        this.lexicalAnalyzer = new TransactionLexicalAnalyzer();
        this.similarityDetector = new SemanticSimilarityDetector();
        this.sentimentAnalyzer = new TransactionSentimentAnalyzer();
        this.transactionCache = new TransactionCache();
    }

    /**
     * Comprehensive transaction message detection
     * @param message SMS message content
     * @param sender SMS sender
     * @return boolean indicating if it's a transaction
     */
    public boolean isTransactionMessage(String message, String sender) {
        String lowerMessage = message.toLowerCase();

        // Quick exclusion checks
        if (hasExclusionKeywords(lowerMessage)) {
            Log.d(TAG, "Excluded by keywords: " + message);
            return false;
        }

        // Check for amount
        if (!hasValidAmount(lowerMessage)) {
            Log.d(TAG, "No valid amount found: " + message);
            return false;
        }

        // Count transaction keywords
        int transactionKeywordCount = countTransactionKeywords(lowerMessage);

        // Sentiment-based validation
        TransactionType inferredType = sentimentAnalyzer.analyzeTransactionSentiment(message);

        // Lexical analysis validation
        TransactionType lexicalType = lexicalAnalyzer.classifyTransactionType(message);

        // Scoring mechanism
        boolean isTransaction = transactionKeywordCount > 0 &&
                inferredType != TransactionType.UNKNOWN &&
                lexicalType != null;

        Log.d(TAG, "Transaction Detection - Keywords: " + transactionKeywordCount +
                ", Sentiment Type: " + inferredType +
                ", Lexical Type: " + lexicalType +
                ", Is Transaction: " + isTransaction);

        return isTransaction;
    }

    /**
     * Parse transaction from message
     * @param message SMS message content
     * @param sender SMS sender
     * @param timestamp Message timestamp
     * @return Parsed Transaction object
     */
    public Transaction parseTransaction(String message, String sender, long timestamp) {
        // Validate as transaction first
        if (!isTransactionMessage(message, sender)) {
            Log.d(TAG, "Not a valid transaction message: " + message);
            return null;
        }

        // Extract core transaction details
        Double amount = extractAmount(message);
        if (amount == null) {
            Log.d(TAG, "Could not extract amount: " + message);
            return null;
        }

        // Determine transaction type
        TransactionType txnType = determineTransactionType(message);

        // Identify bank
        String bank = identifyBank(message, sender);

        // Extract additional details
        String merchantName = lexicalAnalyzer.extractMerchantName(message);
        String description = lexicalAnalyzer.generateDescription(
                message, txnType, merchantName
        );

        // Create transaction object
        Transaction transaction = new Transaction(
                bank,
                txnType.toString(),
                amount,
                timestamp,
                description
        );

        // Set additional metadata
        transaction.setMerchantName(merchantName);
        transaction.setOriginalSms(message);

        // Generate unique fingerprint
        String fingerprint = generateFingerprint(transaction);
        transaction.setMessageHash(fingerprint);

        return transaction;
    }

    /**
     * Determine transaction type
     * @param message SMS message content
     * @return TransactionType
     */
    public TransactionType determineTransactionType(String message) {
        // Use multiple techniques for robust type detection
        TransactionType sentimentType = sentimentAnalyzer.analyzeTransactionSentiment(message);
        TransactionType lexicalType = lexicalAnalyzer.classifyTransactionType(message);

        // Combine detection methods
        if (sentimentType == TransactionType.CREDIT &&
                lexicalType == TransactionType.CREDIT) {
            return TransactionType.CREDIT;
        }

        if (sentimentType == TransactionType.DEBIT &&
                lexicalType == TransactionType.DEBIT) {
            return TransactionType.DEBIT;
        }

        // Fallback to sentiment analysis
        return sentimentType != TransactionType.UNKNOWN ?
                sentimentType : TransactionType.DEBIT;
    }

    /**
     * Extract transaction amount
     * @param message SMS message content
     * @return Extracted amount or null
     */
    public Double extractAmount(String message) {
        Matcher matcher = AMOUNT_PATTERN.matcher(message);

        if (matcher.find()) {
            try {
                // Remove commas and parse
                String amountStr = matcher.group(1).replace(",", "");
                return Double.parseDouble(amountStr);
            } catch (NumberFormatException e) {
                Log.e(TAG, "Error parsing amount: " + matcher.group(1), e);
            }
        }

        return null;
    }

    /**
     * Identify bank from message
     * @param message SMS message content
     * @param sender SMS sender
     * @return Bank name
     */
    public String identifyBank(String message, String sender) {
        String lowerMessage = message.toLowerCase();
        String lowerSender = sender != null ? sender.toLowerCase() : "";

        // Check message content first
        for (String bank : BANK_KEYWORDS) {
            if (lowerMessage.contains(bank) || lowerSender.contains(bank)) {
                return bank.substring(0, 1).toUpperCase() + bank.substring(1);
            }
        }

        return "UNKNOWN";
    }

    /**
     * Check for duplicate transactions
     * @param transaction Transaction to check
     * @param dao Transaction DAO
     * @return true if duplicate, false otherwise
     */
    public boolean isDuplicate(Transaction transaction, TransactionDao dao) {
        // Check in-memory cache
        String fingerprint = transaction.getMessageHash();
        if (transactionCache.containsFingerprint(fingerprint)) {
            Log.d(TAG, "Duplicate found in memory cache");
            return true;
        }

        // Check database for exact fingerprint
        if (dao.hasTransaction(fingerprint)) {
            transactionCache.addFingerprint(fingerprint, transaction.getDate());
            Log.d(TAG, "Duplicate found in database");
            return true;
        }

        // Check for semantic similarity in recent transactions
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(transaction.getDate());
        cal.add(Calendar.HOUR, -24);
        long startTime = cal.getTimeInMillis();

        cal.setTimeInMillis(transaction.getDate());
        cal.add(Calendar.HOUR, 24);
        long endTime = cal.getTimeInMillis();

        List<Transaction> timeWindowTransactions = dao.getTransactionsBetweenDatesSync(startTime, endTime);

        for (Transaction existingTransaction : timeWindowTransactions) {
            if (similarityDetector.areTransactionsSimilar(transaction, existingTransaction)) {
                Log.d(TAG, "Duplicate detected through semantic similarity");
                transactionCache.addFingerprint(fingerprint, transaction.getDate());
                return true;
            }
        }

        // Add to cache and return false
        transactionCache.addFingerprint(fingerprint, transaction.getDate());
        return false;
    }

    /**
     * Generate unique transaction fingerprint
     * @param transaction Transaction object
     * @return Unique hash
     */
    private String generateFingerprint(Transaction transaction) {
        String content = transaction.getAmount() +
                transaction.getDate() +
                transaction.getDescription() +
                transaction.getBank();

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            return Base64.encodeToString(hash, Base64.NO_WRAP);
        } catch (NoSuchAlgorithmException e) {
            Log.e(TAG, "Error generating hash", e);
            return content;
        }
    }

    /**
     * Check for exclusion keywords
     * @param message Lowercase message
     * @return boolean indicating presence of exclusion keywords
     */
    private boolean hasExclusionKeywords(String message) {
        return EXCLUSION_KEYWORDS.stream().anyMatch(message::contains);
    }

    /**
     * Check if message has valid amount
     * @param message Lowercase message
     * @return boolean indicating presence of valid amount
     */
    private boolean hasValidAmount(String message) {
        return AMOUNT_PATTERN.matcher(message).find();
    }

    /**
     * Count transaction-related keywords in message
     * @param message Lowercase message
     * @return Number of transaction keywords found
     */
    private int countTransactionKeywords(String message) {
        return (int) TRANSACTION_KEYWORDS.stream()
                .filter(message::contains)
                .count();
    }

    /**
     * Cleanup transaction cache
     * @param currentTime Current timestamp
     */
    public void cleanupCache(long currentTime) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(currentTime);
        cal.add(Calendar.DAY_OF_YEAR, -7);
        transactionCache.cleanup(cal.getTimeInMillis());
    }
}