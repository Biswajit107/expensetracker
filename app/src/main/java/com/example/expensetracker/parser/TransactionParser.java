package com.example.expensetracker.parser;

import android.util.Base64;
import android.util.Log;

import com.example.expensetracker.database.TransactionDao;
import com.example.expensetracker.models.Transaction;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TransactionParser {
    private static final String TAG = "TransactionParser";

    // Bank identification patterns
    private final Map<String, Pattern> BANK_PATTERNS = new HashMap<>();
    // Transaction patterns (debit/credit)
    private final Map<String, List<Pattern>> TRANSACTION_PATTERNS = new HashMap<>();
    // Amount extraction patterns
    private final List<Pattern> AMOUNT_PATTERNS = new ArrayList<>();
    // Merchants extraction patterns
    private final List<Pattern> MERCHANT_PATTERNS = new ArrayList<>();
    // Patterns for messages to ignore
    private final List<Pattern> IGNORE_PATTERNS = new ArrayList<>();
    // Reference number patterns
    private final List<Pattern> REFERENCE_PATTERNS = new ArrayList<>();
    // Bank-specific patterns for detecting transactions
    private final Map<String, List<Pattern>> BANK_TRANSACTION_PATTERNS = new HashMap<>();

    public TransactionParser() {
        initializeBankPatterns();
        initializeTransactionPatterns();
        initializeAmountPatterns();
        initializeMerchantPatterns();
        initializeIgnorePatterns();
        initializeReferencePatterns();
        initializeBankTransactionPatterns();
    }

    private void initializeBankPatterns() {
        // Initialize bank patterns for sender ID or message content
        BANK_PATTERNS.put("HDFC", Pattern.compile("(?i)HDFC(BK)?|HD\\s?FC"));
        BANK_PATTERNS.put("SBI", Pattern.compile("(?i)SBI(INB)?|State Bank"));
        BANK_PATTERNS.put("ICICI", Pattern.compile("(?i)ICICI(B)?"));
        BANK_PATTERNS.put("AXIS", Pattern.compile("(?i)AXIS(BK)?"));
        BANK_PATTERNS.put("KOTAK", Pattern.compile("(?i)KOTAK(B)?"));
        BANK_PATTERNS.put("YES", Pattern.compile("(?i)YES(BNK)?"));
        BANK_PATTERNS.put("BOI", Pattern.compile("(?i)BOI(IND)?|Bank of India"));
        BANK_PATTERNS.put("PNB", Pattern.compile("(?i)PNB(SMS)?|Punjab National"));
        BANK_PATTERNS.put("CANARA", Pattern.compile("(?i)CANBNK|CANARA"));
        BANK_PATTERNS.put("BOB", Pattern.compile("(?i)BOB(TXN)?|Bank of Baroda"));
        BANK_PATTERNS.put("IDBI", Pattern.compile("(?i)IDBI(BK)?"));
        BANK_PATTERNS.put("UNION", Pattern.compile("(?i)UNION(BK)?|UBI"));
    }

    private void initializeTransactionPatterns() {
        // Initialize patterns for identifying debit transactions
        List<Pattern> debitPatterns = new ArrayList<>();
        debitPatterns.add(Pattern.compile("(?i)debited (from|in|for)"));
        debitPatterns.add(Pattern.compile("(?i)withdrawn from"));
        debitPatterns.add(Pattern.compile("(?i)paid (to|for)"));
        debitPatterns.add(Pattern.compile("(?i)spent at"));
        debitPatterns.add(Pattern.compile("(?i)payment (made|of|for)"));
        debitPatterns.add(Pattern.compile("(?i)purchase (at|of|for)"));
        debitPatterns.add(Pattern.compile("(?i)txn of.*(?:debited|debit|paid)"));
        debitPatterns.add(Pattern.compile("(?i)debit from"));
        debitPatterns.add(Pattern.compile("(?i)debit txn"));
        debitPatterns.add(Pattern.compile("(?i)deducted from"));

        // Initialize patterns for identifying credit transactions
        List<Pattern> creditPatterns = new ArrayList<>();
        creditPatterns.add(Pattern.compile("(?i)credited (to|in)"));
        creditPatterns.add(Pattern.compile("(?i)received (in|from)"));
        creditPatterns.add(Pattern.compile("(?i)deposit(ed)? (to|in|of)"));
        creditPatterns.add(Pattern.compile("(?i)refund (to|of|for)"));
        creditPatterns.add(Pattern.compile("(?i)payment received"));
        creditPatterns.add(Pattern.compile("(?i)cashback (of|for)"));
        creditPatterns.add(Pattern.compile("(?i)txn of.*(?:credited|credit)"));
        creditPatterns.add(Pattern.compile("(?i)credit to"));
        creditPatterns.add(Pattern.compile("(?i)credit txn"));
        creditPatterns.add(Pattern.compile("(?i)added to"));

        TRANSACTION_PATTERNS.put("DEBIT", debitPatterns);
        TRANSACTION_PATTERNS.put("CREDIT", creditPatterns);
    }

    private void initializeAmountPatterns() {
        // Common amount patterns - different banks use different formats
        AMOUNT_PATTERNS.add(Pattern.compile("(?i)(?:Rs\\.?|INR|₹)\\s*(\\d+(?:,\\d+)*(?:\\.\\d{1,2})?)"));
        AMOUNT_PATTERNS.add(Pattern.compile("(?i)(\\d+(?:,\\d+)*(?:\\.\\d{1,2})?)\\s*(?:Rs\\.?|INR|₹)"));
        AMOUNT_PATTERNS.add(Pattern.compile("(?i)(?:amount|amt|of|for)\\s*(?:Rs\\.?|INR|₹)?\\s*(\\d+(?:,\\d+)*(?:\\.\\d{1,2})?)"));
        AMOUNT_PATTERNS.add(Pattern.compile("(?i)(?:debited|credited|paid|spent)\\s*(?:Rs\\.?|INR|₹)?\\s*(\\d+(?:,\\d+)*(?:\\.\\d{1,2})?)"));
        AMOUNT_PATTERNS.add(Pattern.compile("(?i)(?:Rs\\.?|INR|₹)\\s*(\\d+(?:,\\d+)*(?:\\.\\d{1,2})?)\\s*(?:debited|credited|paid|spent)"));
    }

    private void initializeMerchantPatterns() {
        // Patterns to extract merchant names based on transaction context
        MERCHANT_PATTERNS.add(Pattern.compile("(?i)(?:at|to)\\s+([A-Za-z0-9\\s&.,'\\-]+?)(?=\\s+on|\\s+info|\\s+[0-9]|$)"));
        MERCHANT_PATTERNS.add(Pattern.compile("(?i)(?:towards|for)\\s+([A-Za-z0-9\\s&.,'\\-]+?)(?=\\s+on|\\s+info|\\s+[0-9]|$)"));
        MERCHANT_PATTERNS.add(Pattern.compile("(?i)(?:from)\\s+([A-Za-z0-9\\s&.,'\\-]+?)(?=\\s+on|\\s+info|\\s+[0-9]|$)"));
        MERCHANT_PATTERNS.add(Pattern.compile("(?i)UPI-([A-Za-z0-9\\s&.,'\\-]+?)(?=-|\\s+|$)"));
        MERCHANT_PATTERNS.add(Pattern.compile("(?i)VPA-([A-Za-z0-9\\s&.,'\\-@]+?)(?=-|\\s+|$)"));
        MERCHANT_PATTERNS.add(Pattern.compile("(?i)merchant-([A-Za-z0-9\\s&.,'\\-]+?)(?=-|\\s+|$)"));
    }

    private void initializeIgnorePatterns() {
        // Patterns for messages that should be ignored

        // Future/scheduled transactions
        IGNORE_PATTERNS.add(Pattern.compile("(?i)will be (debited|credited|charged)"));
        IGNORE_PATTERNS.add(Pattern.compile("(?i)scheduled (payment|transaction)"));
        IGNORE_PATTERNS.add(Pattern.compile("(?i)upcoming (payment|transaction|due)"));
        IGNORE_PATTERNS.add(Pattern.compile("(?i)due (date|payment|on)"));
        IGNORE_PATTERNS.add(Pattern.compile("(?i)(reminder|reminded|notify|notification)"));

        // OTP and verification messages
        IGNORE_PATTERNS.add(Pattern.compile("(?i)(otp|password|verification|secure) code"));
        IGNORE_PATTERNS.add(Pattern.compile("(?i)generated .* otp"));
        IGNORE_PATTERNS.add(Pattern.compile("(?i)login (alert|notification)"));

        // Balance inquiries (without transactions)
        IGNORE_PATTERNS.add(Pattern.compile("(?i)(?:available|avl)\\s+(?:bal|balance).*[^(debited|credited|payment|transaction)]$"));
        IGNORE_PATTERNS.add(Pattern.compile("(?i)your.*balance is"));

        // Card statements
        IGNORE_PATTERNS.add(Pattern.compile("(?i)(?:statement|stmt).*generated"));
        IGNORE_PATTERNS.add(Pattern.compile("(?i)card statement"));
        IGNORE_PATTERNS.add(Pattern.compile("(?i)min amount due"));
        IGNORE_PATTERNS.add(Pattern.compile("(?i)total amount due"));

        // Promotional messages
        IGNORE_PATTERNS.add(Pattern.compile("(?i)(offer|discount|cashback).*(apply|avail|eligible)"));
        IGNORE_PATTERNS.add(Pattern.compile("(?i)(loan|credit).*(offer|eligible|pre-approved)"));
        IGNORE_PATTERNS.add(Pattern.compile("(?i)(personal|home|car|gold) loan"));

        // General non-transaction information
        IGNORE_PATTERNS.add(Pattern.compile("(?i)rewards (point|earned)"));
        IGNORE_PATTERNS.add(Pattern.compile("(?i)update (on|your)"));
        IGNORE_PATTERNS.add(Pattern.compile("(?i)subject to clearing"));
    }

    private void initializeReferencePatterns() {
        // Patterns to extract reference numbers
        REFERENCE_PATTERNS.add(Pattern.compile("(?i)(?:ref|reference)\\s*(?:no\\.?|number|#)?\\s*[:.]?\\s*([A-Za-z0-9]+)"));
        REFERENCE_PATTERNS.add(Pattern.compile("(?i)(?:txn|transaction)\\s*(?:id|no\\.?|number|#)?\\s*[:.]?\\s*([A-Za-z0-9]+)"));
        REFERENCE_PATTERNS.add(Pattern.compile("(?i)(?:utr|upi ref|imps)\\s*(?:no\\.?|number|#)?\\s*[:.]?\\s*([A-Za-z0-9]+)"));
        REFERENCE_PATTERNS.add(Pattern.compile("(?i)(?:rrn)\\s*[:.]?\\s*([A-Za-z0-9]+)"));
    }

    private void initializeBankTransactionPatterns() {
        // HDFC Bank
        List<Pattern> hdfcPatterns = new ArrayList<>();
        hdfcPatterns.add(Pattern.compile("(?i)HDFC Bank: Rs\\.?\\s*[\\d,.]+\\s*(has been|is)\\s*(debited|credited)"));
        hdfcPatterns.add(Pattern.compile("(?i)HDFC Bank(\\s+A/c.*?|:)\\s*(debited|credited|paid)\\s*(with|for)\\s*Rs\\.?\\s*[\\d,.]+"));
        BANK_TRANSACTION_PATTERNS.put("HDFC", hdfcPatterns);

        // SBI
        List<Pattern> sbiPatterns = new ArrayList<>();
        sbiPatterns.add(Pattern.compile("(?i)(?:DEBIT|CREDIT|IMPS)\\s*(?:Alrt|Alert).*(A/c).*(?:Rs|INR)"));
        sbiPatterns.add(Pattern.compile("(?i)(?:Rs|INR)\\s*[\\d,.]+\\s*(?:debited|credited|transferred)\\s*from.*SBI"));
        BANK_TRANSACTION_PATTERNS.put("SBI", sbiPatterns);

        // ICICI
        List<Pattern> iciciPatterns = new ArrayList<>();
        iciciPatterns.add(Pattern.compile("(?i)ICICI Bank(?:: |\\s+)(?:INR|Rs\\.?)\\s*[\\d,.]+\\s*(?:debited|credited)"));
        iciciPatterns.add(Pattern.compile("(?i)ICICI(?:: |\\s+)(?:Txn|Transaction)\\s*(?:of|for)\\s*(?:INR|Rs\\.?)"));
        BANK_TRANSACTION_PATTERNS.put("ICICI", iciciPatterns);

        // AXIS
        List<Pattern> axisPatterns = new ArrayList<>();
        axisPatterns.add(Pattern.compile("(?i)(?:INR|Rs\\.?)\\s*[\\d,.]+\\s*(?:debited|credited)\\s*from.*Axis"));
        axisPatterns.add(Pattern.compile("(?i)Axis\\s+Bank(?:: |\\s+)(?:INR|Rs\\.?)\\s*[\\d,.]+\\s*(?:spent|received)"));
        BANK_TRANSACTION_PATTERNS.put("AXIS", axisPatterns);

        // Add default patterns for other banks
        List<Pattern> defaultPatterns = new ArrayList<>();
        defaultPatterns.add(Pattern.compile("(?i)(?:INR|Rs\\.?)\\s*[\\d,.]+\\s*(?:debited|credited|paid|spent|received)"));
        defaultPatterns.add(Pattern.compile("(?i)(?:debited|credited|paid|spent|received)\\s*(?:with|for)\\s*(?:INR|Rs\\.?)\\s*[\\d,.]+"));

        for (String bank : BANK_PATTERNS.keySet()) {
            if (!BANK_TRANSACTION_PATTERNS.containsKey(bank)) {
                BANK_TRANSACTION_PATTERNS.put(bank, defaultPatterns);
            }
        }
    }

    public boolean isTransactionMessage(String message, String sender) {
        if (message == null || message.trim().isEmpty()) {
            return false;
        }

        String lowerMessage = message.toLowerCase();

        // First, check if this is a message we should ignore
        for (Pattern ignorePattern : IGNORE_PATTERNS) {
            if (ignorePattern.matcher(lowerMessage).find()) {
                Log.d(TAG, "Ignored message due to ignore pattern: " + ignorePattern.pattern());
                return false;
            }
        }

        // Future tense indicators - these are stronger signals to ignore
        if (lowerMessage.contains("will be") ||
                lowerMessage.contains("scheduled") ||
                lowerMessage.contains("upcoming") ||
                lowerMessage.contains("due on")) {

            // Only ignore if no past tense indicators present
            if (!lowerMessage.contains("has been") &&
                    !lowerMessage.contains("was") &&
                    !lowerMessage.contains("debited") &&
                    !lowerMessage.contains("credited")) {

                Log.d(TAG, "Ignored message due to future tense indicator");
                return false;
            }
        }

        // Promotional message indicators
        if ((lowerMessage.contains("loan") || lowerMessage.contains("offer")) &&
                (lowerMessage.contains("apply") || lowerMessage.contains("avail") ||
                        lowerMessage.contains("eligible") || lowerMessage.contains("pre-approved"))) {

            // Only ignore if no transaction indicators present
            if (!lowerMessage.contains("debited") &&
                    !lowerMessage.contains("credited") &&
                    !lowerMessage.contains("transaction")) {

                Log.d(TAG, "Ignored message due to promotional content");
                return false;
            }
        }

        // Check for statements and balances without transactions
        if ((lowerMessage.contains("statement") || lowerMessage.contains("balance")) &&
                !lowerMessage.contains("debited") &&
                !lowerMessage.contains("credited") &&
                !lowerMessage.contains("transaction") &&
                !lowerMessage.contains("payment")) {

            Log.d(TAG, "Ignored message as it's a statement or balance notification");
            return false;
        }

        // Must have an amount
        boolean hasAmount = false;
        for (Pattern pattern : AMOUNT_PATTERNS) {
            if (pattern.matcher(message).find()) {
                hasAmount = true;
                break;
            }
        }

        if (!hasAmount) {
            Log.d(TAG, "Not a transaction: no amount found");
            return false;
        }

        // Must have transaction indicators
        boolean hasTransactionIndicator = false;

        // Check debit indicators
        for (Pattern pattern : TRANSACTION_PATTERNS.get("DEBIT")) {
            if (pattern.matcher(message).find()) {
                hasTransactionIndicator = true;
                break;
            }
        }

        // Check credit indicators if no debit indicators found
        if (!hasTransactionIndicator) {
            for (Pattern pattern : TRANSACTION_PATTERNS.get("CREDIT")) {
                if (pattern.matcher(message).find()) {
                    hasTransactionIndicator = true;
                    break;
                }
            }
        }

        // If basic indicators not found, try bank-specific patterns
        if (!hasTransactionIndicator) {
            // Identify bank first
            String bankName = identifyBank(message, sender);

            if (bankName != null) {
                List<Pattern> bankPatterns = BANK_TRANSACTION_PATTERNS.get(bankName);
                if (bankPatterns != null) {
                    for (Pattern pattern : bankPatterns) {
                        if (pattern.matcher(message).find()) {
                            hasTransactionIndicator = true;
                            break;
                        }
                    }
                }
            }
        }

        // Final check - some keywords are strong indicators regardless of other patterns
        if (!hasTransactionIndicator) {
            if (message.toLowerCase().matches(".*(transaction|txn|upi|neft|imps|rtgs).*")
                    && hasAmount) {

                // Must have additional context to avoid false positives
                if (message.toLowerCase().contains("debited") ||
                        message.toLowerCase().contains("credited") ||
                        message.toLowerCase().contains("paid") ||
                        message.toLowerCase().contains("received")) {
                    hasTransactionIndicator = true;
                }
            }
        }

        return hasTransactionIndicator;
    }

    public Transaction parseTransaction(String message, String sender, long timestamp) {
        if (!isTransactionMessage(message, sender)) {
            return null;
        }

        // 1. Identify bank
        String bank = identifyBank(message, sender);
        if (bank == null) bank = "OTHER";

        // 2. Determine transaction type
        String type = determineTransactionType(message);

        // 3. Extract amount
        Double amount = extractAmount(message);
        if (amount == null) {
            Log.d(TAG, "Could not extract amount from: " + message);
            return null;
        }

        // 4. Extract date (use SMS timestamp if not found in message)
        Long transactionDate = extractDate(message);
        if (transactionDate == null) {
            transactionDate = timestamp;
        }

        // 5. Extract merchant name
        String merchantName = extractMerchant(message);

        // 6. Generate description
        String description = generateDescription(message, type, merchantName);

        // 7. Create transaction object
        Transaction transaction = new Transaction(bank, type, amount, transactionDate, description);

        // 8. Set additional properties
        transaction.setMerchantName(merchantName);
        transaction.setOriginalSms(message);

        // 9. Attempt to determine category (if possible)
        String category = determineCategory(message, merchantName);
        if (category != null) {
            transaction.setCategory(category);
        }

        // 10. Set message hash for duplicate detection
        String messageHash = generateMessageHash(amount, transactionDate, description, merchantName);
        transaction.setMessageHash(messageHash);

        Log.d(TAG, "Parsed transaction: " + description + ", Amount: " + amount + ", Type: " + type + ", Bank: " + bank);

        return transaction;
    }

    private String identifyBank(String message, String sender) {
        // First check sender ID if available
        if (sender != null && !sender.isEmpty()) {
            for (Map.Entry<String, Pattern> entry : BANK_PATTERNS.entrySet()) {
                if (entry.getValue().matcher(sender).find()) {
                    return entry.getKey();
                }
            }
        }

        // Then check message content
        for (Map.Entry<String, Pattern> entry : BANK_PATTERNS.entrySet()) {
            if (entry.getValue().matcher(message).find()) {
                return entry.getKey();
            }
        }

        return null;
    }

    private String determineTransactionType(String message) {
        String lowerMessage = message.toLowerCase();

        // Check for explicit debit indicators
        for (Pattern pattern : TRANSACTION_PATTERNS.get("DEBIT")) {
            if (pattern.matcher(lowerMessage).find()) {
                return "DEBIT";
            }
        }

        // Check for explicit credit indicators
        for (Pattern pattern : TRANSACTION_PATTERNS.get("CREDIT")) {
            if (pattern.matcher(lowerMessage).find()) {
                return "CREDIT";
            }
        }

        // Additional checks for ambiguous cases
        if (lowerMessage.contains("debited") ||
                lowerMessage.contains("withdrawn") ||
                lowerMessage.contains("spent") ||
                lowerMessage.contains("paid") ||
                lowerMessage.contains("payment made") ||
                lowerMessage.contains("purchase")) {
            return "DEBIT";
        }

        if (lowerMessage.contains("credited") ||
                lowerMessage.contains("deposited") ||
                lowerMessage.contains("received") ||
                lowerMessage.contains("payment received") ||
                lowerMessage.contains("refund") ||
                lowerMessage.contains("cashback")) {
            return "CREDIT";
        }

        // Default to DEBIT (most common type)
        return "DEBIT";
    }

    private Double extractAmount(String message) {
        for (Pattern pattern : AMOUNT_PATTERNS) {
            Matcher matcher = pattern.matcher(message);
            if (matcher.find()) {
                try {
                    // Remove commas before parsing
                    String amountStr = matcher.group(1).replace(",", "");
                    return Double.parseDouble(amountStr);
                } catch (NumberFormatException | IllegalStateException | IndexOutOfBoundsException e) {
                    // Continue to next pattern if this fails
                    Log.d(TAG, "Failed to parse amount with pattern: " + pattern.pattern());
                }
            }
        }

        // Try a more general approach if specific patterns fail
        Pattern generalPattern = Pattern.compile("(\\d+(?:,\\d+)*(?:\\.\\d{1,2})?)");
        Matcher matcher = generalPattern.matcher(message);

        while (matcher.find()) {
            // Check if this number is likely an amount (nearby currency indicators)
            String context = getNearbyText(message, matcher.start(), 15);
            if (context.toLowerCase().contains("rs") ||
                    context.toLowerCase().contains("inr") ||
                    context.toLowerCase().contains("₹") ||
                    context.toLowerCase().contains("amount")) {

                try {
                    String amountStr = matcher.group(1).replace(",", "");
                    return Double.parseDouble(amountStr);
                } catch (NumberFormatException e) {
                    // Continue to next match
                }
            }
        }

        return null;
    }

    private String getNearbyText(String text, int position, int radius) {
        int start = Math.max(0, position - radius);
        int end = Math.min(text.length(), position + radius);
        return text.substring(start, end);
    }

    private Long extractDate(String message) {
        // Try to find a date in the message
        String[] datePatterns = {
                "(\\d{2}/\\d{2}/\\d{2})",      // dd/MM/yy
                "(\\d{2}-\\d{2}-\\d{2})",      // dd-MM-yy
                "(\\d{2}/\\d{2}/\\d{4})",      // dd/MM/yyyy
                "(\\d{2}-\\d{2}-\\d{4})",      // dd-MM-yyyy
                "(\\d{2}[A-Za-z]{3}\\d{2})",   // ddMMMyy (e.g., 01Jan22)
                "(\\d{2}[A-Za-z]{3}\\d{4})"    // ddMMMyyyy (e.g., 01Jan2022)
        };

        for (String datePattern : datePatterns) {
            Pattern pattern = Pattern.compile(datePattern);
            Matcher matcher = pattern.matcher(message);

            if (matcher.find()) {
                String dateStr = matcher.group(1);
                try {
                    SimpleDateFormat dateFormat;

                    if (dateStr.matches("\\d{2}/\\d{2}/\\d{2}") || dateStr.matches("\\d{2}-\\d{2}-\\d{2}")) {
                        dateStr = dateStr.replace("-", "/");
                        dateFormat = new SimpleDateFormat("dd/MM/yy", Locale.getDefault());
                    } else if (dateStr.matches("\\d{2}/\\d{2}/\\d{4}") || dateStr.matches("\\d{2}-\\d{2}-\\d{4}")) {
                        dateStr = dateStr.replace("-", "/");
                        dateFormat = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
                    } else if (dateStr.matches("\\d{2}[A-Za-z]{3}\\d{2}")) {
                        dateFormat = new SimpleDateFormat("ddMMMyy", Locale.getDefault());
                    } else {
                        dateFormat = new SimpleDateFormat("ddMMMyyyy", Locale.getDefault());
                    }

                    return dateFormat.parse(dateStr).getTime();
                } catch (ParseException e) {
                    Log.e(TAG, "Error parsing date: " + dateStr, e);
                }
            }
        }

        // No date found in the message
        return null;
    }

    private String extractMerchant(String message) {
        for (Pattern pattern : MERCHANT_PATTERNS) {
            Matcher matcher = pattern.matcher(message);
            if (matcher.find()) {
                String merchant = matcher.group(1).trim();

                // Clean up the merchant name
                merchant = cleanMerchantName(merchant);

                if (!merchant.isEmpty()) {
                    return merchant;
                }
            }
        }

        // Try UPI specific extraction if pattern matching fails
        Pattern upiPattern = Pattern.compile("(?i)UPI-([A-Za-z0-9\\s]+?)(?:-|\\s|$)");
        Matcher upiMatcher = upiPattern.matcher(message);
        if (upiMatcher.find()) {
            String merchant = upiMatcher.group(1).trim();
            return cleanMerchantName(merchant);
        }

        return "";
    }

    private String cleanMerchantName(String merchant) {
        // Remove any trailing punctuation
        merchant = merchant.replaceAll("[,.;:'\\s]+$", "");

        // Remove any leading punctuation
        merchant = merchant.replaceAll("^[,.;:'\\s]+", "");

        // Limit to 3-5 words to avoid including too much extra text
        String[] words = merchant.split("\\s+");
        StringBuilder cleanMerchant = new StringBuilder();

        int wordLimit = Math.min(5, words.length);
        for (int i = 0; i < wordLimit; i++) {
            String word = words[i].trim();

            // Skip common words that aren't part of merchant names
            if (word.equalsIgnoreCase("on") ||
                    word.equalsIgnoreCase("at") ||
                    word.equalsIgnoreCase("via") ||
                    word.equalsIgnoreCase("using") ||
                    word.equalsIgnoreCase("info") ||
                    word.equalsIgnoreCase("ref") ||
                    word.equalsIgnoreCase("id") ||
                    word.equalsIgnoreCase("dated") ||
                    word.equalsIgnoreCase("for") ||
                    word.equals("a/c") ||
                    word.equals("ac") ||
                    word.equals("upi")) {
                break;
            }

            if (cleanMerchant.length() > 0) {
                cleanMerchant.append(" ");
            }
            cleanMerchant.append(words[i]);
        }

        // Capitalize first letter of each word
        String[] finalWords = cleanMerchant.toString().split("\\s+");
        StringBuilder capitalizedMerchant = new StringBuilder();

        for (String word : finalWords) {
            if (!word.isEmpty()) {
                if (capitalizedMerchant.length() > 0) {
                    capitalizedMerchant.append(" ");
                }
                capitalizedMerchant.append(Character.toUpperCase(word.charAt(0)));

                if (word.length() > 1) {
                    capitalizedMerchant.append(word.substring(1).toLowerCase());
                }
            }
        }

        return capitalizedMerchant.toString();
    }

    private String generateDescription(String message, String type, String merchantName) {
        // Determine transaction method (UPI, NEFT, Card, etc.)
        String transactionMethod = determineTransactionMethod(message);
        StringBuilder description = new StringBuilder();

        // Extract reference number if available
        String reference = extractReferenceNumber(message);

        // Extract purpose if available (e.g., "payment for electricity")
        String purpose = extractPurpose(message);

        // Extract location if available
        String location = extractLocation(message);

        if (type.equals("DEBIT")) {
            description.append(transactionMethod).append(" payment");

            if (merchantName != null && !merchantName.isEmpty()) {
                description.append(" to ").append(merchantName);
            }

            if (purpose != null && !purpose.isEmpty()) {
                description.append(" for ").append(purpose);
            }

            if (location != null && !location.isEmpty()) {
                description.append(" at ").append(location);
            }
        } else { // CREDIT
            description.append(transactionMethod).append(" received");

            if (merchantName != null && !merchantName.isEmpty()) {
                description.append(" from ").append(merchantName);
            }

            if (purpose != null && !purpose.isEmpty()) {
                description.append(" for ").append(purpose);
            }
        }

        // Add reference number if available
        if (reference != null && !reference.isEmpty()) {
            description.append(" (Ref: ").append(reference).append(")");
        }

        // Try to extract special transaction types
        if (message.toLowerCase().contains("refund")) {
            description.append(" (Refund)");
        } else if (message.toLowerCase().contains("cashback")) {
            description.append(" (Cashback)");
        } else if (message.toLowerCase().contains("emi") || message.toLowerCase().contains("installment")) {
            description.append(" (EMI Payment)");
        }

        // If the description is overly generic, try harder to extract information
        if ((merchantName == null || merchantName.isEmpty()) &&
                (description.toString().equals("Transaction payment") ||
                        description.toString().equals("Transaction received"))) {

            // Extract more context from the message
            String enhancedDescription = enhanceGenericDescription(message, type);
            if (enhancedDescription != null && !enhancedDescription.isEmpty()) {
                return enhancedDescription;
            }
        }

        return description.toString();
    }

    // Helper methods that would be needed for the enhanced generateDescription function
    private String extractPurpose(String message) {
        String lowerMessage = message.toLowerCase();

        // Look for purpose indicators
        String[] purposeIndicators = {"for", "towards", "purpose"};

        for (String indicator : purposeIndicators) {
            int index = lowerMessage.indexOf(" " + indicator + " ");
            if (index >= 0) {
                // Get text after the indicator
                String afterIndicator = lowerMessage.substring(index + indicator.length() + 2);

                // Extract a reasonable-length purpose (up to next punctuation or 3-4 words)
                String[] words = afterIndicator.split("\\s+");
                StringBuilder purpose = new StringBuilder();

                int wordLimit = Math.min(4, words.length);
                for (int i = 0; i < wordLimit; i++) {
                    // Stop on common end markers
                    if (words[i].matches("on|of|via|using|through|info|alert|dated")) break;

                    if (purpose.length() > 0) purpose.append(" ");
                    purpose.append(words[i]);

                    // Stop at punctuation
                    if (words[i].endsWith(".") || words[i].endsWith(",") ||
                            words[i].endsWith(";") || words[i].endsWith(":")) {
                        // Remove the punctuation
                        purpose = new StringBuilder(purpose.substring(0, purpose.length() - 1));
                        break;
                    }
                }

                String result = purpose.toString().trim();
                if (!result.isEmpty()) {
                    return cleanExtractedText(result);
                }
            }
        }

        return null;
    }

    private String extractLocation(String message) {
        String lowerMessage = message.toLowerCase();

        // Look for location indicators
        if (lowerMessage.contains(" at ")) {
            int index = lowerMessage.indexOf(" at ");

            // Check if "at" is followed by a merchant name (already extracted)
            if (index < lowerMessage.length() - 4) {
                String afterAt = lowerMessage.substring(index + 4);
                String[] words = afterAt.split("\\s+");

                // If there are words after "at" that might be a location
                if (words.length >= 1) {
                    StringBuilder location = new StringBuilder();

                    // Take up to 3 words as potential location
                    int wordLimit = Math.min(3, words.length);
                    for (int i = 0; i < wordLimit; i++) {
                        // Stop on common end markers
                        if (words[i].matches("on|info|alert|dated|ref|id|upi")) break;

                        if (location.length() > 0) location.append(" ");
                        location.append(words[i]);

                        // Stop at punctuation
                        if (words[i].endsWith(".") || words[i].endsWith(",") ||
                                words[i].endsWith(";") || words[i].endsWith(":")) {
                            // Remove the punctuation
                            location = new StringBuilder(location.substring(0, location.length() - 1));
                            break;
                        }
                    }

                    String result = location.toString().trim();
                    if (!result.isEmpty()) {
                        return cleanExtractedText(result);
                    }
                }
            }
        }

        return null;
    }

    private String cleanExtractedText(String text) {
        // Remove leading/trailing punctuation
        text = text.replaceAll("^[^a-zA-Z0-9]+", "").replaceAll("[^a-zA-Z0-9]+$", "");

        // Capitalize first letter of each word
        String[] words = text.split("\\s+");
        StringBuilder cleanText = new StringBuilder();

        for (String word : words) {
            if (!word.isEmpty()) {
                if (cleanText.length() > 0) cleanText.append(" ");
                cleanText.append(Character.toUpperCase(word.charAt(0)));

                if (word.length() > 1) {
                    cleanText.append(word.substring(1).toLowerCase());
                }
            }
        }

        return cleanText.toString();
    }

    private String enhanceGenericDescription(String message, String type) {
        String lowerMessage = message.toLowerCase();
        StringBuilder enhancedDesc = new StringBuilder();

        // Try to identify bill payments
        if (lowerMessage.contains("bill pay") || lowerMessage.contains("billpay")) {
            enhancedDesc.append("Bill payment");

            // Try to identify bill type
            if (lowerMessage.contains("electric")) enhancedDesc.append(" for electricity");
            else if (lowerMessage.contains("water")) enhancedDesc.append(" for water");
            else if (lowerMessage.contains("gas")) enhancedDesc.append(" for gas");
            else if (lowerMessage.contains("phone") || lowerMessage.contains("mobile"))
                enhancedDesc.append(" for phone");
            else if (lowerMessage.contains("internet") || lowerMessage.contains("broadband"))
                enhancedDesc.append(" for internet");
        }
        // Try to identify fund transfers
        else if (lowerMessage.contains("transfer") || lowerMessage.contains("sent to") ||
                lowerMessage.contains("received from")) {

            if (type.equals("DEBIT")) {
                enhancedDesc.append("Money sent");

                // Try to find recipient
                if (lowerMessage.contains("to vpa")) {
                    int index = lowerMessage.indexOf("to vpa");
                    if (index >= 0 && index + 7 < lowerMessage.length()) {
                        String vpa = lowerMessage.substring(index + 7).trim();
                        String[] parts = vpa.split("\\s+");
                        if (parts.length > 0) {
                            enhancedDesc.append(" to ").append(parts[0]);
                        }
                    }
                }
            } else {
                enhancedDesc.append("Money received");

                // Try to find sender
                if (lowerMessage.contains("from vpa")) {
                    int index = lowerMessage.indexOf("from vpa");
                    if (index >= 0 && index + 9 < lowerMessage.length()) {
                        String vpa = lowerMessage.substring(index + 9).trim();
                        String[] parts = vpa.split("\\s+");
                        if (parts.length > 0) {
                            enhancedDesc.append(" from ").append(parts[0]);
                        }
                    }
                }
            }
        }
        // Try to identify ATM withdrawals
        else if (lowerMessage.contains("atm") || lowerMessage.contains("cash withdrawal")) {
            enhancedDesc.append("ATM withdrawal");

            // Try to identify location
            Pattern locationPattern = Pattern.compile("(?:at|in)\\s+([A-Za-z\\s]+)");
            Matcher matcher = locationPattern.matcher(lowerMessage);
            if (matcher.find()) {
                String location = matcher.group(1).trim();
                if (!location.isEmpty()) {
                    enhancedDesc.append(" at ").append(cleanExtractedText(location));
                }
            }
        }

        // Add reference number if available
        String reference = extractReferenceNumber(message);
        if (reference != null && !reference.isEmpty()) {
            enhancedDesc.append(" (Ref: ").append(reference).append(")");
        }

        return enhancedDesc.toString();
    }

    private String determineTransactionMethod(String message) {
        String lowerMessage = message.toLowerCase();

        if (lowerMessage.contains("upi")) return "UPI";
        if (lowerMessage.contains("neft")) return "NEFT";
        if (lowerMessage.contains("imps")) return "IMPS";
        if (lowerMessage.contains("rtgs")) return "RTGS";
        if (lowerMessage.contains("atm")) return "ATM";
        if (lowerMessage.contains("netbanking") || lowerMessage.contains("net banking")) return "NetBanking";
        if (lowerMessage.contains("credit card")) return "Credit Card";
        if (lowerMessage.contains("debit card")) return "Debit Card";
        if (lowerMessage.contains("card")) return "Card";

        return "Transaction";
    }

    private String extractReferenceNumber(String message) {
        for (Pattern pattern : REFERENCE_PATTERNS) {
            Matcher matcher = pattern.matcher(message);
            if (matcher.find()) {
                return matcher.group(1).trim();
            }
        }

        return null;
    }

    private String determineCategory(String message, String merchantName) {
        String lowerMessage = message.toLowerCase();
        String combinedText = lowerMessage;

        if (merchantName != null && !merchantName.isEmpty()) {
            combinedText += " " + merchantName.toLowerCase();
        }

        // Food & Dining
        if (containsAny(combinedText, new String[]{
                "restaurant", "cafe", "food", "dinner", "lunch", "breakfast",
                "swiggy", "zomato", "uber eat", "pizza", "burger", "grocery",
                "supermarket", "hotel", "dining", "kitchen", "eatery"})) {
            return Transaction.Categories.FOOD;
        }

        // Shopping
        if (containsAny(combinedText, new String[]{
                "shop", "store", "mall", "mart", "amazon", "flipkart", "myntra",
                "retail", "purchase", "buy", "clothing", "fashion", "apparel",
                "electronics", "gadget", "furniture", "decor"})) {
            return Transaction.Categories.SHOPPING;
        }

        // Bills & Utilities
        if (containsAny(combinedText, new String[]{
                "bill", "utility", "electric", "water", "gas", "internet", "broadband",
                "wifi", "mobile", "phone", "recharge", "dth", "cable", "subscription",
                "rent", "maintenance", "insurance", "tax", "loan", "emi", "mortgage"})) {
            return Transaction.Categories.BILLS;
        }

        // Entertainment
        if (containsAny(combinedText, new String[]{
                "movie", "cinema", "theater", "netflix", "amazon prime", "hotstar",
                "spotify", "music", "concert", "show", "event", "ticket", "game",
                "entertainment", "play", "festival", "park", "sport"})) {
            return Transaction.Categories.ENTERTAINMENT;
        }

        // Transport
        if (containsAny(combinedText, new String[]{
                "uber", "ola", "taxi", "cab", "auto", "bus", "train", "metro",
                "railway", "flight", "airline", "travel", "transport", "petrol",
                "diesel", "fuel", "gas station", "parking", "toll"})) {
            return Transaction.Categories.TRANSPORT;
        }

        // Health
        if (containsAny(combinedText, new String[]{
                "hospital", "clinic", "doctor", "medical", "pharmacy", "medicine", "health",
                "healthcare", "dental", "therapy", "fitness", "gym", "wellness", "spa"})) {
            return Transaction.Categories.HEALTH;
        }

        // Education
        if (containsAny(combinedText, new String[]{
                "school", "college", "university", "education", "tuition", "class", "course",
                "tutorial", "coaching", "exam", "fee", "book", "library", "stationery"})) {
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

    private String generateMessageHash(Double amount, Long date, String description, String merchantName) {
        // Format amount with 2 decimal places
        String amountStr = String.format("%.2f", amount);

        // Use day-level precision for date to handle minor time differences
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(date);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        String dateStr = String.valueOf(cal.getTimeInMillis());

        // Merchant info (if available)
        String merchantInfo = (merchantName != null && !merchantName.isEmpty()) ?
                merchantName.toLowerCase() : "";

        // Create a unique fingerprint
        String content = amountStr + dateStr + description + merchantInfo;

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            return Base64.encodeToString(hash, Base64.NO_WRAP);
        } catch (NoSuchAlgorithmException e) {
            Log.e(TAG, "Error generating hash", e);
            return content;
        }
    }

    public boolean isDuplicate(Transaction transaction, TransactionDao dao) {
        // Check if this transaction's hash already exists in the database
        String hash = transaction.getMessageHash();
        return dao.hasTransaction(hash);
    }
}
