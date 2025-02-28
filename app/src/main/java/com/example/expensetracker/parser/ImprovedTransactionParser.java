package com.example.expensetracker.parser;

import android.util.Log;
import com.example.expensetracker.database.TransactionDao;
import com.example.expensetracker.models.Transaction;

/**
 * Enhanced solution to fix message classification issues
 */
public class ImprovedTransactionParser extends EnhancedTransactionParser {
    private static final String TAG = "ImprovedTransactionParser";

    /**
     * Overriding the isLikelyTransactionMessage method with improved filtering
     */
    @Override
    public boolean isLikelyTransactionMessage(String message, String sender) {
        if (message == null || message.trim().isEmpty()) {
            return false;
        }

        String lowerMessage = message.toLowerCase();

        // STEP 1: First apply strong rejection filters for obviously non-transaction messages

        // Filter out OTP messages
        if (isOTPMessage(lowerMessage)) {
            Log.d(TAG, "Rejected: OTP message");
            return false;
        }

        // Filter out promotional/marketing messages
        if (isPromotionalMessage(lowerMessage)) {
            Log.d(TAG, "Rejected: Promotional message");
            return false;
        }

        // Filter out balance inquiry/statement messages
        if (isBalanceStatementMessage(lowerMessage)) {
            Log.d(TAG, "Rejected: Balance statement message");
            return false;
        }

        // Filter out informational, service and notification messages
        if (isInformationalMessage(lowerMessage)) {
            Log.d(TAG, "Rejected: Informational message");
            return false;
        }

        // STEP 2: Look for strong positive indicators of a transaction

        // Check if message has strong transaction evidence (highest confidence)
        if (hasStrongTransactionEvidence(lowerMessage)) {
            Log.d(TAG, "Accepted: Strong transaction evidence found");
            return true;
        }

        // STEP 3: Apply more detailed transaction pattern matching

        // Check for transaction amount pattern AND at least one transaction indicator
        boolean hasAmount = messageContainsAmount(lowerMessage);
        boolean hasTransactionIndicator = hasTransactionIndicators(lowerMessage);

        if (hasAmount && hasTransactionIndicator) {
            Log.d(TAG, "Accepted: Contains transaction amount and indicators");
            return true;
        }

        // STEP 4: Apply specific bank message patterns if available
        if (matchesBankTransactionPattern(lowerMessage)) {
            Log.d(TAG, "Accepted: Matches bank-specific transaction pattern");
            return true;
        }

        // If we reach here, the message didn't meet our criteria for a transaction
        Log.d(TAG, "Rejected: Doesn't match transaction criteria");
        return false;
    }

    /**
     * Check if a message is an OTP or verification message
     */
    private boolean isOTPMessage(String message) {
        // OTP keyword patterns
        if (message.contains("otp") ||
                message.contains("one time password") ||
                message.contains("verification code") ||
                message.contains("security code") ||
                message.contains("secure code")) {
            return true;
        }

        // OTP expiry patterns
        if ((message.contains("valid for") || message.contains("expires in")) &&
                (message.contains("minutes") || message.contains("seconds"))) {
            return true;
        }

        // OTP numeric patterns with common phrases
        if (message.matches(".*\\b\\d{4,6}\\b.*") &&
                (message.contains("do not share") ||
                        message.contains("don't share") ||
                        message.contains("authentication") ||
                        message.contains("verification"))) {
            return true;
        }

        return false;
    }

    /**
     * Improved method to check if a message is a balance statement without transaction details
     */
    private boolean isBalanceStatementMessage(String message) {
        // Pure balance information
        if ((message.contains("available bal") ||
                message.contains("avl bal") ||
                message.contains("balance info") ||
                message.contains("account balance")) &&
                !message.contains("debited") &&
                !message.contains("credited") &&
                !message.contains("transfer") &&
                !message.contains("payment")) {
            return true;
        }

        // Balance as of / closing balance patterns
        if (message.contains("bal as on") ||
                message.contains("balance as on") ||
                message.contains("closing balance") ||
                message.contains("as of yesterday")) {
            return true;
        }

        // Mini statement patterns without transaction
        if ((message.contains("mini statement") || message.contains("mini stmt")) &&
                !message.contains("debited") &&
                !message.contains("credited")) {
            return true;
        }

        // "For balance" patterns
        if (message.contains("for balance") &&
                (message.contains("dial") || message.contains("sms") || message.contains("give miss call"))) {
            return true;
        }

        return false;
    }

    /**
     * Check if a message is an informational or service notification
     */
    private boolean isInformationalMessage(String message) {
        // Account service messages
        if (message.contains("account") &&
                (message.contains("upgraded") ||
                        message.contains("updated") ||
                        message.contains("activated") ||
                        message.contains("opened") ||
                        message.contains("linked"))) {
            return true;
        }

        // Card-related informational messages
        if (message.contains("card") &&
                (message.contains("delivered") ||
                        message.contains("dispatched") ||
                        message.contains("activated") ||
                        message.contains("blocked") ||
                        message.contains("unblocked") ||
                        message.contains("generated") ||
                        message.contains("is ready"))) {
            return true;
        }

        // Statement generation notices
        if (message.contains("statement") &&
                (message.contains("generated") ||
                        message.contains("available") ||
                        message.contains("ready"))) {
            return true;
        }

        // Bill/due date reminders
        if ((message.contains("bill") || message.contains("payment")) &&
                (message.contains("due") ||
                        message.contains("pending") ||
                        message.contains("reminder"))) {
            return true;
        }

        // Banking service notices that aren't transactions
        String[] serviceTerms = {"cheque", "deposit", "interest", "maintenance", "fee"};
        String[] serviceActions = {"credited", "debited", "applied", "added", "charged"};

        // Check for "will be" patterns indicating future actions
        if (message.contains("will be")) {
            for (String term : serviceTerms) {
                if (message.contains(term)) {
                    for (String action : serviceActions) {
                        if (message.contains(action)) {
                            return true;
                        }
                    }
                }
            }
        }

        // Branch/ATM information
        if (message.contains("nearest branch") ||
                message.contains("nearest atm") ||
                message.contains("branch timing")) {
            return true;
        }

        return false;
    }

    /**
     * Check if message contains a monetary amount
     */
    private boolean messageContainsAmount(String message) {
        // Currency symbol followed by digits
        if (message.matches("(?i).*(?:rs\\.?|inr|₹)\\s*\\d+(?:,\\d+)*(?:\\.\\d{1,2})?.*")) {
            return true;
        }

        // Digits followed by currency symbol
        if (message.matches("(?i).*\\d+(?:,\\d+)*(?:\\.\\d{1,2})?\\s*(?:rs\\.?|inr|₹).*")) {
            return true;
        }

        // Amount with indicators
        if (message.matches("(?i).*(?:amount|amt|payment|cost|price)\\s*(?:of|:)\\s*(?:rs\\.?|inr|₹)?\\s*\\d+(?:,\\d+)*(?:\\.\\d{1,2})?.*")) {
            return true;
        }

        return false;
    }

    /**
     * Check if message has transaction-specific indicators
     */
    private boolean hasTransactionIndicators(String message) {
        // Key transaction action phrases
        String[] transactionIndicators = {
                "debited from", "credited to", "transferred to", "paid to",
                "received from", "withdrawn at", "purchase at", "spent at",
                "txn completed", "transaction successful", "payment successful",
                "transaction of", "transaction id", "reference number",
                "upi ref", "txn id", "imps", "neft"
        };

        for (String indicator : transactionIndicators) {
            if (message.contains(indicator)) {
                return true;
            }
        }

        // Combined indicators - account reference + transaction verb
        boolean hasAccountRef = message.contains("a/c") ||
                message.contains("account") ||
                message.contains("acct");

        boolean hasTransactionVerb = message.contains("debited") ||
                message.contains("credited") ||
                message.contains("transferred") ||
                message.contains("paid") ||
                message.contains("received");

        return hasAccountRef && hasTransactionVerb;
    }

    /**
     * Check if message matches known bank transaction patterns
     */
    private boolean matchesBankTransactionPattern(String message) {
        // HDFC transaction pattern
        if (message.matches("(?i).*hdfc bank.*(?:rs\\.?|inr).*(?:debited|credited).*")) {
            return true;
        }

        // SBI transaction pattern
        if (message.matches("(?i).*(?:debited|credited|transferred).*(?:from|to).*sbi.*")) {
            return true;
        }

        // ICICI transaction pattern
        if (message.matches("(?i).*icici.*(?:rs\\.?|inr).*(?:debited|credited).*")) {
            return true;
        }

        // Axis transaction pattern
        if (message.matches("(?i).*axis.*(?:rs\\.?|inr).*(?:spent|received|debited|credited).*")) {
            return true;
        }

        // General bank alert pattern
        if (message.matches("(?i).*(?:txn alert|debit alert|credit alert).*(?:a/c|account).*(?:rs\\.?|inr).*")) {
            return true;
        }

        return false;
    }

    /**
     * Improved method to better detect promotional messages
     */
    @Override
    public boolean isPromotionalMessage(String message) {
        // Standard marketing terms
        String[] marketingTerms = {
                "offer", "discount", "cashback", "sale", "promotion", "deal", "limited time",
                "special", "exclusive", "save", "buy", "free", "new launch", "best price",
                "hurry", "only at", "limited stocks", "upgrade to", "grab now", "available at"
        };

        // Call to action phrases
        String[] ctaPhrases = {
                "apply now", "buy now", "call now", "click here", "download now",
                "register now", "shop now", "subscribe now", "visit now",
                "visit our website", "avail now", "check eligibility"
        };

        // Check for URLs (strong indicator of promotional content)
        if (message.contains("http://") ||
                message.contains("https://") ||
                message.contains("bit.ly/") ||
                message.contains(".io/") ||
                message.contains(".in/")) {
            return true;
        }

        // Check for T&C indicators
        if (message.contains("t&c apply") ||
                message.contains("t&c") ||
                message.contains("terms and conditions") ||
                message.contains("terms & conditions")) {
            return true;
        }

        // Check for multiple marketing terms (higher confidence)
        int marketingTermCount = 0;
        for (String term : marketingTerms) {
            if (message.contains(term)) {
                marketingTermCount++;
                if (marketingTermCount >= 2) {
                    return true;
                }
            }
        }

        // Check for CTA phrases (strong indicators)
        for (String phrase : ctaPhrases) {
            if (message.contains(phrase)) {
                return true;
            }
        }

        // Check for common marketing patterns
        if (message.contains("off on") ||
                message.contains("% off") ||
                message.contains("% discount") ||
                message.contains("flat") && message.contains("off")) {
            return true;
        }

        // Loan/credit offer patterns
        if ((message.contains("loan") || message.contains("credit")) &&
                (message.contains("offer") || message.contains("pre-approved") ||
                        message.contains("pre approved") || message.contains("eligible"))) {
            return true;
        }

        // Return false if no promotional indicators found
        return false;
    }

    /**
     * Improved implementation for parsing a transaction
     */
    @Override
    public Transaction parseTransaction(String message, String sender, long timestamp) {
        // First verify this is actually a transaction message
        if (!isLikelyTransactionMessage(message, sender)) {
            return null;
        }

        // Now continue with the rest of the transaction parsing
        return super.parseTransaction(message, sender, timestamp);
    }


}