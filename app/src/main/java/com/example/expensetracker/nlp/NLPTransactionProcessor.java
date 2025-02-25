package com.example.expensetracker.nlp;

import android.util.Log;
import android.util.Base64;
import com.example.expensetracker.database.TransactionDao;
import com.example.expensetracker.models.Transaction;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NLPTransactionProcessor {
    private static final String TAG = "NLPTransactionProcessor";

    private final TransactionLexicalAnalyzer lexicalAnalyzer;
    private final SemanticSimilarityDetector similarityDetector;
    private final TransactionSentimentAnalyzer sentimentAnalyzer;
    private final BankPatternDatabase bankPatternDatabase;
    private final TransactionCache transactionCache;

    // General patterns for non-transaction messages
    private final List<Pattern> nonTransactionPatterns = new ArrayList<>();

    public NLPTransactionProcessor() {
        this.lexicalAnalyzer = new TransactionLexicalAnalyzer();
        this.similarityDetector = new SemanticSimilarityDetector();
        this.sentimentAnalyzer = new TransactionSentimentAnalyzer();
        this.bankPatternDatabase = new BankPatternDatabase();
        this.transactionCache = new TransactionCache();

        initializeNonTransactionPatterns();
    }

    private void initializeNonTransactionPatterns() {
        // Patterns for non-transaction messages
        nonTransactionPatterns.add(Pattern.compile("balance|avl bal|available balance", Pattern.CASE_INSENSITIVE));
        nonTransactionPatterns.add(Pattern.compile("otp|password|verification code|secure code", Pattern.CASE_INSENSITIVE));
        nonTransactionPatterns.add(Pattern.compile("will be|upcoming|due for|reminder|alert", Pattern.CASE_INSENSITIVE));
        nonTransactionPatterns.add(Pattern.compile("offer|discount|cashback", Pattern.CASE_INSENSITIVE));
    }

    public boolean isTransactionMessage(String message, String sender) {
        String lowerMessage = message.toLowerCase();

        // 1. EXPLICITLY FILTER OUT COMMON NON-TRANSACTION MESSAGES

        // Balance enquiry/statement messages
        if ((lowerMessage.contains("balance") || lowerMessage.contains("stmt") || lowerMessage.contains("statement")) &&
                !lowerMessage.contains("debited") && !lowerMessage.contains("credited") &&
                !lowerMessage.contains("transferred") && !lowerMessage.contains("payment")) {
            Log.d(TAG, "Rejected: Balance enquiry message");
            return false;
        }

        // OTP/verification messages
        if (lowerMessage.contains("otp") || lowerMessage.contains("password") ||
                lowerMessage.contains("verification code") || lowerMessage.contains("login")) {
            Log.d(TAG, "Rejected: OTP/verification message");
            return false;
        }

        // Future/scheduled transactions
        if ((lowerMessage.contains("will be") || lowerMessage.contains("scheduled") ||
                lowerMessage.contains("upcoming") || lowerMessage.contains("reminder")) &&
                !lowerMessage.contains("has been") && !lowerMessage.contains("was")) {
            Log.d(TAG, "Rejected: Future/scheduled transaction message");
            return false;
        }

        // Card statements, due dates, etc.
        if (lowerMessage.contains("due date") || lowerMessage.contains("min amount due") ||
                (lowerMessage.contains("statement") && lowerMessage.contains("generated"))) {
            Log.d(TAG, "Rejected: Statement/due date message");
            return false;
        }

        // 2. REQUIRE SPECIFIC TRANSACTION INDICATORS

        // Must have a currency indicator with amount
        boolean hasAmount = Pattern.compile("(?:rs\\.?|inr|₹)\\s*[0-9,]+\\.?[0-9]*").matcher(lowerMessage).find();
        if (!hasAmount) {
            Log.d(TAG, "Rejected: No amount found");
            return false;
        }

        // Must have clear transaction verbs/indicators
        boolean hasTransactionVerb =
                lowerMessage.contains("debited") ||
                        lowerMessage.contains("credited") ||
                        lowerMessage.contains("paid") ||
                        lowerMessage.contains("sent") ||
                        lowerMessage.contains("received") ||
                        lowerMessage.contains("transfer") ||
                        lowerMessage.contains("payment") ||
                        lowerMessage.contains("spent") ||
                        lowerMessage.contains("purchased") ||
                        lowerMessage.contains("transaction");

        if (!hasTransactionVerb) {
            Log.d(TAG, "Rejected: No transaction verb found");
            return false;
        }

        // 3. BANK-SPECIFIC PATTERNS
        String bank = bankPatternDatabase.identifyBank(sender, message);
        if (!bank.equals("UNKNOWN")) {
            // Known bank sender - additional checks based on bank patterns
            BankPatternDatabase.BankPatterns bankPatterns = bankPatternDatabase.getPatternsForBank(bank);
            if (bankPatterns != null && bankPatterns.hasTransactionPattern()) {
                boolean matchesTransactionPattern = false;
                for (Pattern pattern : bankPatterns.getTransactionPatterns()) {
                    if (pattern.matcher(message).find()) {
                        matchesTransactionPattern = true;
                        break;
                    }
                }

                if (!matchesTransactionPattern) {
                    Log.d(TAG, "Rejected: Doesn't match bank-specific transaction pattern");
                    return false;
                }
            }
        }

        // 4. CONTEXT ANALYSIS
        boolean hasAccountReference =
                lowerMessage.contains("a/c") ||
                        lowerMessage.contains("account") ||
                        lowerMessage.contains("acct");

        boolean hasCompletedIndicator =
                lowerMessage.contains("has been") ||
                        lowerMessage.contains("was") ||
                        lowerMessage.contains("is") ||
                        lowerMessage.contains("done") ||
                        lowerMessage.contains("processed") ||
                        lowerMessage.contains("completed");

        // Higher confidence if it mentions an account and indicates completion
        if (hasAccountReference && hasCompletedIndicator) {
            return true;
        }

        // If we have a strong transaction verb and an amount, it's likely a transaction
        if (hasTransactionVerb && hasAmount &&
                (lowerMessage.contains("debited") || lowerMessage.contains("credited") ||
                        lowerMessage.contains("paid") || lowerMessage.contains("received"))) {
            return true;
        }

        // If it matches a bank sender and has amount + transaction verb, it's likely a transaction
        if (!bank.equals("UNKNOWN") && hasAmount && hasTransactionVerb) {
            return true;
        }

        // Otherwise, be conservative - more likely to be a non-transaction message
        Log.d(TAG, "Rejected: Didn't meet confidence criteria for transaction");
        return false;
    }

    public TransactionType determineTransactionType(String message) {
        String lowerMessage = message.toLowerCase();

        // Very clear debit indicators have highest priority
        if (lowerMessage.contains("debited from") ||
                lowerMessage.contains("paid from") ||
                lowerMessage.contains("withdrawn from") ||
                lowerMessage.contains("purchase at") ||
                lowerMessage.contains("spent at") ||
                lowerMessage.contains("payment made")) {
            return TransactionType.DEBIT;
        }

        // Very clear credit indicators have highest priority
        if (lowerMessage.contains("credited to") ||
                lowerMessage.contains("received in") ||
                lowerMessage.contains("deposited to") ||
                lowerMessage.contains("payment received") ||
                lowerMessage.contains("refund") ||
                lowerMessage.contains("cashback")) {
            return TransactionType.CREDIT;
        }

        // Check for transactional keywords with context
        boolean isDebit = false;
        boolean isCredit = false;

        // Look for debit keywords with account as object
        if ((lowerMessage.contains("debited") || lowerMessage.contains("debit")) &&
                (lowerMessage.contains("your") || lowerMessage.contains("a/c") ||
                        lowerMessage.contains("account"))) {
            isDebit = true;
        }

        // Look for credit keywords with account as object
        if ((lowerMessage.contains("credited") || lowerMessage.contains("credit")) &&
                (lowerMessage.contains("your") || lowerMessage.contains("a/c") ||
                        lowerMessage.contains("account"))) {
            isCredit = true;
        }

        // Handle conflict (if both debit and credit indicators are present)
        if (isDebit && isCredit) {
            // Look for context clues to resolve
            if (lowerMessage.contains("paid") || lowerMessage.contains("spent") ||
                    lowerMessage.contains("purchase")) {
                return TransactionType.DEBIT;
            }
            if (lowerMessage.contains("received") || lowerMessage.contains("income") ||
                    lowerMessage.contains("salary")) {
                return TransactionType.CREDIT;
            }

            // Default to the one that appears first in the message
            int debitIndex = lowerMessage.indexOf("debit");
            int creditIndex = lowerMessage.indexOf("credit");

            if (debitIndex != -1 && creditIndex != -1) {
                return debitIndex < creditIndex ? TransactionType.DEBIT : TransactionType.CREDIT;
            } else if (debitIndex != -1) {
                return TransactionType.DEBIT;
            } else if (creditIndex != -1) {
                return TransactionType.CREDIT;
            }
        } else if (isDebit) {
            return TransactionType.DEBIT;
        } else if (isCredit) {
            return TransactionType.CREDIT;
        }

        // Fall back to lexical analysis
        return lexicalAnalyzer.classifyTransactionType(message);
    }

    // The parseTransaction method would now use this improved determineTransactionType method:
    public Transaction parseTransaction(String message, String sender, long timestamp) {
        // 1. Check if it's a transaction message
        if (!isTransactionMessage(message, sender)) {
            Log.d(TAG, "Not a transaction message: " + message);
            return null;
        }

        // 2. Identify the bank
        String bank = bankPatternDatabase.identifyBank(sender, message);

        // 3. Get appropriate bank patterns (use general if no specific bank identified)
        BankPatternDatabase.BankPatterns patterns = bankPatternDatabase.getPatternsForBank(
                bank.equals("UNKNOWN") ? "GENERAL" : bank);

        // 3. Extract transaction amount
        Double amount = lexicalAnalyzer.extractAmount(message, bank);
        if (amount == null) {
            Log.d(TAG, "Could not extract amount from: " + message);
            return null;
        }

        // 4. Determine transaction type using improved method
        TransactionType txnType = determineTransactionType(message);

        // 5. Extract merchant name
        String merchantName = lexicalAnalyzer.extractMerchantName(message);

        // 6. Create transaction object
        Transaction transaction = new Transaction(
                bank.equals("UNKNOWN") ? "OTHER" : bank,
                txnType.toString(),
                amount,
                timestamp,
                lexicalAnalyzer.generateDescription(message, txnType, merchantName)
        );

        transaction.setMerchantName(merchantName);
        transaction.setOriginalSms(message);

        // 7. Generate a fingerprint for the transaction
        String fingerprint = generateFingerprint(transaction);
        transaction.setMessageHash(fingerprint);

        Log.d(TAG, "Parsed transaction: " + transaction.getDescription() +
                ", Amount: " + transaction.getAmount() +
                ", Type: " + transaction.getType() +
                ", Bank: " + transaction.getBank());

        return transaction;
    }

    public boolean isDuplicate(Transaction transaction, TransactionDao dao) {
        // 1. Check in memory cache
        String fingerprint = transaction.getMessageHash();
        if (transactionCache.containsFingerprint(fingerprint)) {
            Log.d(TAG, "Duplicate detected in memory cache: " + fingerprint);
            return true;
        }

        // 2. Check database for exact fingerprint match
        boolean exactMatch = dao.hasTransaction(fingerprint);
        if (exactMatch) {
            // Add to cache for future lookups
            transactionCache.addFingerprint(fingerprint, transaction.getDate());
            Log.d(TAG, "Duplicate detected with exact fingerprint match in database");
            return true;
        }

        // 3. Look for semantic duplicates in a time window
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(transaction.getDate());
        cal.add(Calendar.HOUR, -24); // Look 24 hours back
        long startTime = cal.getTimeInMillis();

        cal.setTimeInMillis(transaction.getDate());
        cal.add(Calendar.HOUR, 24); // Look 24 hours forward
        long endTime = cal.getTimeInMillis();

        List<Transaction> timeWindowTransactions = dao.getTransactionsBetweenDatesSync(startTime, endTime);

        for (Transaction existingTransaction : timeWindowTransactions) {
            if (similarityDetector.areTransactionsSimilar(transaction, existingTransaction)) {
                Log.d(TAG, "Duplicate detected through semantic similarity");
                // Add to cache for future lookups
                transactionCache.addFingerprint(fingerprint, transaction.getDate());
                return true;
            }
        }

        // No duplicate found, add to cache
        transactionCache.addFingerprint(fingerprint, transaction.getDate());
        return false;
    }

    public void cleanupCache(long currentTime) {
        // Clean up entries older than 7 days
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(currentTime);
        cal.add(Calendar.DAY_OF_YEAR, -7);

        transactionCache.cleanup(cal.getTimeInMillis());
    }

    private String generateFingerprint(Transaction transaction) {
        // Round amount to two decimal places
        String amountStr = String.format("%.2f", transaction.getAmount());

        // Format date to day resolution (ignore time)
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(transaction.getDate());
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        String dateStr = String.valueOf(cal.getTimeInMillis());

        // Include merchant info if available
        String merchantInfo = (transaction.getMerchantName() != null && !transaction.getMerchantName().isEmpty()) ?
                transaction.getMerchantName().toLowerCase() : "";

        // Include bank and type
        String transactionInfo = transaction.getBank() + ":" + transaction.getType();

        // Create fingerprint from all components
        String content = amountStr + dateStr + merchantInfo + transactionInfo;

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            return Base64.encodeToString(hash, Base64.NO_WRAP);
        } catch (NoSuchAlgorithmException e) {
            Log.e(TAG, "Error generating hash", e);
            return content;
        }
    }
}