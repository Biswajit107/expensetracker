package com.example.expensetracker.parser;

import android.util.Log;
import android.util.Base64;

import com.example.expensetracker.database.TransactionDao;
import com.example.expensetracker.models.Transaction;

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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Enhanced Transaction Parser that uses a confidence scoring system
 * to improve accuracy in distinguishing between transaction and non-transaction messages.
 */
public class ConfidenceScoreTransactionParser extends EnhancedTransactionParser {
    private static final String TAG = "ConfidenceScoreParser";

    // Threshold score for considering a message as a transaction
    private static final double TRANSACTION_THRESHOLD = 10.0;

    // Score weights for different indicators
    private static final double SCORE_AMOUNT_PRESENT = 5.0;
    private static final double SCORE_STRONG_TRANSACTION_VERB = 10.0;
    private static final double SCORE_TRANSACTION_VERB = 5.0;
    private static final double SCORE_ACCOUNT_REFERENCE = 5.0;
    private static final double SCORE_REFERENCE_NUMBER = 8.0;
    private static final double SCORE_TRANSACTION_DATE = 6.0;
    private static final double SCORE_MERCHANT_PRESENT = 4.0;
    private static final double SCORE_BALANCE_REFERENCE = -2.0;
    private static final double SCORE_STRUCTURED_TRANSACTION_FORMAT = 10.0;

    // Strongly negative indicators
    private static final double SCORE_URL_PRESENT = -15.0;
    private static final double SCORE_TERMS_CONDITIONS = -10.0;
    private static final double SCORE_PROMOTIONAL_TERM = -5.0;
    private static final double SCORE_FUTURE_TENSE = -8.0;
    private static final double SCORE_OTP_PRESENT = -15.0;
    private static final double SCORE_BALANCE_STATEMENT = -15.0;

    /**
     * Parses an SMS message to extract transaction details
     * using a confidence scoring approach.
     */
    @Override
    public Transaction parseTransaction(String message, String sender, long timestamp) {
        if (message == null || message.trim().isEmpty()) {
            return null;
        }

        Log.d(TAG, "Starting confidence score parsing for message: " + message);

        // Calculate confidence score for the message
        MessageScore score = calculateConfidenceScore(message, sender);
        Log.d(TAG, "Message confidence score: " + score.totalScore);

        // Log detailed score breakdown
        Log.d(TAG, "Score breakdown: " + score.getScoreBreakdown());

        // If below threshold, not a transaction
        if (score.totalScore < TRANSACTION_THRESHOLD) {
            Log.d(TAG, "Message score below threshold, not a transaction");
            return null;
        }

        // Continue with transaction parsing since we're confident it's a transaction
        Log.d(TAG, "Message passed confidence threshold, parsing as transaction");

        // From this point, logic is similar to original parsing with some optimizations
        // based on indicators we've already found during scoring

        // Extract transaction components
        String bank = score.detectedBank != null ? score.detectedBank : identifyBank(message, sender);
        String type = score.transactionType != null ? score.transactionType : determineTransactionType(message);
        Double amount = score.detectedAmount != null ? score.detectedAmount : extractAmount(message);
//        Long date = score.detectedDate != null ? score.detectedDate : extractDate(message, timestamp);
        Long date = timestamp;
        String merchantName = score.detectedMerchant != null ? score.detectedMerchant : extractMerchant(message);
        String transactionMethod = determineTransactionMethod(message);
        String referenceNumber = score.referenceNumber != null ? score.referenceNumber : extractReferenceNumber(message);
        String category = determineCategory(message, merchantName);

        // Log extracted components for debugging
        Log.d(TAG, "Extracted components: "
                + "\n Bank: " + bank
                + "\n Type: " + type
                + "\n Amount: " + amount
                + "\n Date: " + (date != null ? new Date(date) : "null")
                + "\n Merchant: " + merchantName
                + "\n Method: " + transactionMethod
                + "\n Reference: " + referenceNumber
                + "\n Category: " + category);

        // If we couldn't extract essential information, return null
        if (amount == null) {
            Log.d(TAG, "Failed to extract amount - skipping message");
            return null;
        }

        if (type == null) {
            // Default to DEBIT if we can't determine type (more common)
            type = "DEBIT";
            Log.d(TAG, "Transaction type not found, defaulting to DEBIT");
        }

        if (date == null) {
            // Use message timestamp if date not found
            date = timestamp;
            Log.d(TAG, "Transaction date not found, using SMS timestamp");
        }

        if (bank == null) {
            bank = "OTHER";
            Log.d(TAG, "Bank not identified, using 'OTHER'");
        }

        // Generate description
        String description = generateDescription(message, type, merchantName, transactionMethod, referenceNumber);

        // Create transaction object
        Transaction transaction = new Transaction(bank, type, amount, date, description);

        // Set additional properties
        transaction.setMerchantName(merchantName);
        transaction.setOriginalSms(message);

        if (category != null) {
            transaction.setCategory(category);
        }

        // Generate message hash for duplicate detection
        String messageHash = generateMessageHash(amount, date, description, merchantName);
        transaction.setMessageHash(messageHash);

        // Check for recurring transaction pattern
        boolean isRecurring = detectRecurringTransaction(message, description);
        transaction.setRecurring(isRecurring);

        Log.d(TAG, "Successfully parsed transaction: " + description);

        return transaction;
    }

    /**
     * Calculate confidence score for a message to determine if it's a transaction
     */
    private MessageScore calculateConfidenceScore(String message, String sender) {
        MessageScore score = new MessageScore();
        String lowerMessage = message.toLowerCase();

        // Check for URLs (very strong negative indicator)
        if (lowerMessage.contains("http://") ||
                lowerMessage.contains("https://") ||
                lowerMessage.contains("www.") ||
                lowerMessage.contains("bit.ly") ||
                lowerMessage.contains(".io/") ||
                lowerMessage.contains(".in/")) {
            score.addScore("URL present", SCORE_URL_PRESENT);
        }

        // Check for terms and conditions (strong promotional indicator)
        if (lowerMessage.contains("t&c") ||
                lowerMessage.contains("terms and conditions") ||
                lowerMessage.contains("t & c") ||
                lowerMessage.contains("tnc")) {
            score.addScore("Terms & Conditions", SCORE_TERMS_CONDITIONS);
        }

        // Check for OTP indicators
        if (lowerMessage.contains("otp") ||
                lowerMessage.contains("one time password") ||
                lowerMessage.contains("verification code")) {
            score.addScore("OTP reference", SCORE_OTP_PRESENT);
        }

        // Check for balance statement specific patterns
        if ((lowerMessage.contains("available bal") && lowerMessage.contains("as on yesterday")) ||
                (lowerMessage.contains("cheques are subject to clearing")) ||
                (lowerMessage.contains("for real time") && lowerMessage.contains("bal dial"))) {
            score.addScore("Balance statement pattern", SCORE_BALANCE_STATEMENT);
        }

        // Check for promotional language
        String[] promotionalTerms = {
                "offer", "discount", "cashback", "exclusive", "deal", "alert",
                "chance", "reward", "voucher", "join", "refer", "recommend", "program",
                "enjoyed your", "best deal", "instant cash alert"
        };

        for (String term : promotionalTerms) {
            if (lowerMessage.contains(term)) {
                score.addScore("Promotional term: " + term, SCORE_PROMOTIONAL_TERM);
            }
        }

        // Check for future tense (often in promotional messages)
        String[] futureTenseIndicators = {
                "ready to be", "will be", "can get", "can be", "can earn",
                "get a loan", "apply now", "check emi", "click here", "click to"
        };

        for (String indicator : futureTenseIndicators) {
            if (lowerMessage.contains(indicator)) {
                score.addScore("Future tense: " + indicator, SCORE_FUTURE_TENSE);
            }
        }

        // Check for amount - positive indicator for transactions
        Double amount = extractAmount(message);
        if (amount != null) {
            score.addScore("Amount present", SCORE_AMOUNT_PRESENT);
            score.detectedAmount = amount;
        }

        // Check for strong transaction verbs
        String[] strongTransactionVerbs = {
                "debited from", "credited to", "transferred to", "withdrawn from",
                "deposited in", "paid to", "received from", "sent"
        };

        for (String verb : strongTransactionVerbs) {
            if (lowerMessage.contains(verb)) {
                score.addScore("Strong transaction verb: " + verb, SCORE_STRONG_TRANSACTION_VERB);

                // Also try to determine transaction type from verb
                if (verb.contains("debited") || verb.contains("paid") ||
                        verb.contains("transferred to") || verb.contains("withdrawn") ||
                        verb.contains("sent")) {
                    score.transactionType = "DEBIT";
                } else if (verb.contains("credited") || verb.contains("received") ||
                        verb.contains("deposited")) {
                    score.transactionType = "CREDIT";
                }
            }
        }

        // Check for regular transaction verbs
        String[] transactionVerbs = {
                "debited", "credited", "transferred", "withdrawn", "deposited",
                "paid", "spent", "purchase", "received", "sent"
        };

        for (String verb : transactionVerbs) {
            if (lowerMessage.contains(verb)) {
                score.addScore("Transaction verb: " + verb, SCORE_TRANSACTION_VERB);

                // Try to determine transaction type if not already set
                if (score.transactionType == null) {
                    if (verb.equals("debited") || verb.equals("paid") ||
                            verb.equals("spent") || verb.equals("purchase") ||
                            verb.equals("sent")) {
                        score.transactionType = "DEBIT";
                    } else if (verb.equals("credited") || verb.equals("received") ||
                            verb.equals("deposited")) {
                        score.transactionType = "CREDIT";
                    }
                }
            }
        }

        // Check for account references
        String[] accountReferences = {
                "a/c", "account", "acct", "ac no", "bank a/c", "bank account"
        };

        for (String ref : accountReferences) {
            if (lowerMessage.contains(ref)) {
                score.addScore("Account reference: " + ref, SCORE_ACCOUNT_REFERENCE);
                break;
            }
        }

        // Check for reference numbers - strong transaction indicator
        Pattern refPattern = Pattern.compile("(?i)(?:ref|reference|txn|transaction|utr|rrn|imps|neft)\\s*(?:no|number|#|id)?\\s*[:.=]?\\s*([a-zA-Z0-9]+)");
        Matcher refMatcher = refPattern.matcher(message);
        if (refMatcher.find()) {
            score.addScore("Reference number", SCORE_REFERENCE_NUMBER);
            score.referenceNumber = refMatcher.group(1);
        }

        // Check for transaction date - good transaction indicator
        Long extractedDate = extractDate(message, 0);
        if (extractedDate != null) {
            score.addScore("Transaction date", SCORE_TRANSACTION_DATE);
            score.detectedDate = extractedDate;
        }

        // Check for merchant - good transaction indicator
        String merchant = extractMerchant(message);
        if (merchant != null && !merchant.isEmpty()) {
            score.addScore("Merchant present", SCORE_MERCHANT_PRESENT);
            score.detectedMerchant = merchant;
        }

        // Check for balance references (slight negative if no transaction verbs)
        if ((lowerMessage.contains("bal") || lowerMessage.contains("balance")) &&
                !lowerMessage.contains("debited") &&
                !lowerMessage.contains("credited") &&
                !lowerMessage.contains("transferred")) {
            score.addScore("Balance reference without transaction", SCORE_BALANCE_REFERENCE);
        }

        // Check for structured transaction format (multiple lines with specific indicators)
        // This is a very strong indicator of a real transaction
        if (message.contains("\n") &&
                (message.contains("From") || message.contains("To") ||
                        message.contains("Ref") || message.contains("On") ||
                        (message.indexOf("\n") < 20 && // First line is short (like "Sent Rs.XXX")
                                (lowerMessage.contains("sent") || lowerMessage.contains("received") ||
                                        lowerMessage.contains("paid") || lowerMessage.contains("deposited"))))) {
            score.addScore("Structured transaction format", SCORE_STRUCTURED_TRANSACTION_FORMAT);
        }

        // Identify bank
        score.detectedBank = identifyBank(message, sender);

        return score;
    }

    /**
     * Class to track confidence score details
     */
    private static class MessageScore {
        double totalScore = 0.0;
        Map<String, Double> scoreComponents = new HashMap<>();
        String transactionType = null;
        Double detectedAmount = null;
        Long detectedDate = null;
        String detectedMerchant = null;
        String detectedBank = null;
        String referenceNumber = null;

        void addScore(String component, double value) {
            scoreComponents.put(component, value);
            totalScore += value;
        }

        String getScoreBreakdown() {
            StringBuilder builder = new StringBuilder();
            for (Map.Entry<String, Double> entry : scoreComponents.entrySet()) {
                builder.append(entry.getKey())
                        .append(": ")
                        .append(entry.getValue())
                        .append(", ");
            }
            return builder.toString();
        }
    }

    /**
     * Overriding the attemptFallbackParsing method to also use confidence scoring
     */
    @Override
    public Transaction attemptFallbackParsing(String message, String sender, long timestamp) {
        // Simply use the main parsing method as it already uses confidence scoring
        return parseTransaction(message, sender, timestamp);
    }

    /**
     * Override the isLikelyTransactionMessage method to use confidence scoring
     */
    @Override
    public boolean isLikelyTransactionMessage(String message, String sender) {
        if (message == null || message.trim().isEmpty()) {
            return false;
        }

        // Use confidence scoring to determine if it's a transaction
        MessageScore score = calculateConfidenceScore(message, sender);
        Log.d(TAG, "Transaction likelihood score: " + score.totalScore);

        return score.totalScore >= TRANSACTION_THRESHOLD;
    }
}