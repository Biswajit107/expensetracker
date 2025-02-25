package com.example.expensetracker.nlp;

import android.util.Log;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Arrays;

public class TransactionLexicalAnalyzer {
    private static final String TAG = "TransactionLexicalAnalyzer";

    // Word categories for transaction classification
    private final Set<String> creditWords = new HashSet<>(Arrays.asList(
            "credited", "received", "deposited", "added", "transferred to you",
            "payment received", "cashback", "refund", "interest", "reimbursed",
            "credit", "salary", "income"
    ));

    private final Set<String> debitWords = new HashSet<>(Arrays.asList(
            "debited", "withdrawn", "spent", "paid", "sent", "deducted",
            "payment made", "transferred", "purchased", "charged", "payment towards",
            "debit", "purchase", "bill payment", "shopping"
    ));

    private final Set<String> merchantIndicators = new HashSet<>(Arrays.asList(
            "at", "to", "for", "towards", "merchant", "shop", "store", "via", "through", "paytm",
            "with", "on", "by", "payee", "beneficiary"
    ));

    // Bank-specific patterns for extracting amounts
    private final Map<String, Pattern> amountPatterns = new HashMap<>();

    public TransactionLexicalAnalyzer() {
        initializeAmountPatterns();
    }

    private void initializeAmountPatterns() {
        // General patterns
        amountPatterns.put("general_1", Pattern.compile("(?:Rs\\.?|INR|₹)\\s*(\\d+(?:,\\d+)*(?:\\.\\d{1,2})?)"));
        amountPatterns.put("general_2", Pattern.compile("(\\d+(?:,\\d+)*(?:\\.\\d{1,2})?)\\s*(?:Rs\\.?|INR|₹)"));

        // HDFC patterns
        amountPatterns.put("hdfc_1", Pattern.compile("(?:Info|Alert)(?:.*)(?:Rs\\.?|INR)\\s*(\\d+(?:,\\d+)*(?:\\.\\d{1,2})?)"));

        // SBI patterns
        amountPatterns.put("sbi_1", Pattern.compile("(?:Rs\\.?|INR)\\s*(\\d+(?:,\\d+)*(?:\\.\\d{1,2})?)\\s*(?:credited|debited)"));

        // ICICI patterns
        amountPatterns.put("icici_1", Pattern.compile("(?:Rs\\.?|INR)\\s*(\\d+(?:,\\d+)*(?:\\.\\d{1,2})?)\\s*has been"));
    }

    public TransactionType classifyTransactionType(String message) {
        // Convert to lowercase for case-insensitive matching
        String lowerMessage = message.toLowerCase();

        // Count occurrence of credit and debit indicators
        int creditScore = 0;
        int debitScore = 0;

        for (String creditWord : creditWords) {
            if (lowerMessage.contains(creditWord)) {
                creditScore++;
                // Log.d(TAG, "Credit indicator found: " + creditWord);
            }
        }

        for (String debitWord : debitWords) {
            if (lowerMessage.contains(debitWord)) {
                debitScore++;
                // Log.d(TAG, "Debit indicator found: " + debitWord);
            }
        }

        // Add contextual analysis
        if (lowerMessage.contains("received") && lowerMessage.contains("from")) {
            creditScore += 2;
        }

        if (lowerMessage.contains("sent") && lowerMessage.contains("to")) {
            debitScore += 2;
        }

        if (lowerMessage.contains("spent") || lowerMessage.contains("purchase")) {
            debitScore += 2;
        }

        // Handle cancellations or failed transactions
        if (lowerMessage.contains("failed") ||
                lowerMessage.contains("declined") ||
                lowerMessage.contains("cancelled") ||
                lowerMessage.contains("reversed")) {
            // Invert scores for cancellations
            int temp = creditScore;
            creditScore = debitScore;
            debitScore = temp;
        }

        // Log.d(TAG, "Credit score: " + creditScore + ", Debit score: " + debitScore);

        // Determine transaction type based on scores
        if (creditScore > debitScore) {
            return TransactionType.CREDIT;
        } else if (debitScore > creditScore) {
            return TransactionType.DEBIT;
        }

        // Default case - most transaction messages are debits
        return TransactionType.DEBIT;
    }

    public Double extractAmount(String message, String bankName) {
        message = message.replaceAll("\\s+", " ").trim();

        // Try bank-specific patterns first
        if (bankName != null) {
            String bankKey = bankName.toLowerCase() + "_1";
            if (amountPatterns.containsKey(bankKey)) {
                Matcher matcher = amountPatterns.get(bankKey).matcher(message);
                if (matcher.find()) {
                    try {
                        return Double.parseDouble(matcher.group(1).replace(",", ""));
                    } catch (NumberFormatException e) {
                        Log.e(TAG, "Error parsing bank-specific amount: " + matcher.group(1), e);
                    }
                }
            }
        }

        // Try all general patterns
        for (Map.Entry<String, Pattern> entry : amountPatterns.entrySet()) {
            if (!entry.getKey().startsWith("general")) continue;

            Matcher matcher = entry.getValue().matcher(message);
            if (matcher.find()) {
                try {
                    return Double.parseDouble(matcher.group(1).replace(",", ""));
                } catch (NumberFormatException e) {
                    Log.e(TAG, "Error parsing amount: " + matcher.group(1), e);
                }
            }
        }

        // Last resort: look for any number preceded by Rs or INR or ₹
        Pattern lastResortPattern = Pattern.compile("(?:Rs\\.?|INR|₹)\\s*(\\d+(?:[.,]\\d+)*)");
        Matcher matcher = lastResortPattern.matcher(message);
        if (matcher.find()) {
            try {
                return Double.parseDouble(matcher.group(1).replace(",", ""));
            } catch (NumberFormatException e) {
                Log.e(TAG, "Error parsing amount with last resort: " + matcher.group(1), e);
            }
        }

        return null;
    }

    public String extractMerchantName(String message) {
        String lowerMessage = message.toLowerCase();
        String[] sentences = lowerMessage.split("[.!?]");

        // Try to find merchant name using different patterns
        for (String sentence : sentences) {
            for (String indicator : merchantIndicators) {
                int position = sentence.indexOf(" " + indicator + " ");
                if (position >= 0) {
                    // Extract the next few words after the indicator
                    String afterIndicator = sentence.substring(position + indicator.length() + 2);
                    String[] words = afterIndicator.split("\\s+");

                    // Take up to 3 words as the merchant name (or until end markers)
                    StringBuilder merchantName = new StringBuilder();
                    int maxWords = Math.min(4, words.length);
                    for (int i = 0; i < maxWords; i++) {
                        // Stop on common end markers
                        if (words[i].matches("on|of|via|using|through|for|info|alert|\\d+|inr|rs|upi|dated")) break;

                        if (merchantName.length() > 0) merchantName.append(" ");
                        merchantName.append(words[i]);
                    }

                    String result = merchantName.toString().trim();
                    // Remove any trailing punctuation
                    result = result.replaceAll("[,.;:]$", "");

                    if (!result.isEmpty()) {
                        // Capitalize first letter of each word
                        String[] resultWords = result.split("\\s+");
                        StringBuilder capitalizedResult = new StringBuilder();

                        for (String word : resultWords) {
                            if (capitalizedResult.length() > 0) capitalizedResult.append(" ");
                            if (word.length() > 0) {
                                capitalizedResult.append(Character.toUpperCase(word.charAt(0)))
                                        .append(word.substring(1));
                            }
                        }

                        return capitalizedResult.toString();
                    }
                }
            }
        }

        // Try to extract a merchant name from UPI transactions
        Pattern upiPattern = Pattern.compile("UPI(?:-| )([A-Za-z0-9\\s]+?)(?:-|\\s+)");
        Matcher upiMatcher = upiPattern.matcher(message);
        if (upiMatcher.find()) {
            return upiMatcher.group(1).trim();
        }

        // Look for merchant names after transaction ID
        Pattern refPattern = Pattern.compile("(?:ref|id)\\s*(?:no)?\\s*:?\\s*[A-Za-z0-9]+\\s+([A-Za-z0-9\\s]+)");
        Matcher refMatcher = refPattern.matcher(lowerMessage);
        if (refMatcher.find()) {
            String potentialMerchant = refMatcher.group(1).trim();
            // Only use if it's not a date or short number
            if (!potentialMerchant.matches("\\d{1,2}/\\d{1,2}") && potentialMerchant.length() > 3) {
                return capitalizeWords(potentialMerchant);
            }
        }

        return "";
    }

    public String generateDescription(String message, TransactionType type, String merchantName) {
        // Start with transaction type
        StringBuilder description = new StringBuilder();

        // Determine transaction method
        String method = determineTransactionMethod(message);

        if (type == TransactionType.DEBIT) {
            description.append(method).append(" payment");

            if (merchantName != null && !merchantName.isEmpty()) {
                description.append(" to ").append(merchantName);
            }
        } else { // CREDIT
            description.append(method).append(" received");

            if (merchantName != null && !merchantName.isEmpty()) {
                description.append(" from ").append(merchantName);
            }
        }

        // Add reference number if present
        String reference = extractReferenceNumber(message);
        if (!reference.isEmpty()) {
            description.append(" (Ref: ").append(reference).append(")");
        }

        return description.toString();
    }

    private String determineTransactionMethod(String message) {
        message = message.toLowerCase();

        if (message.contains("upi")) return "UPI";
        if (message.contains("neft")) return "NEFT";
        if (message.contains("imps")) return "IMPS";
        if (message.contains("rtgs")) return "RTGS";
        if (message.contains("atm") || message.contains("cash withdrawal")) return "ATM";
        if (message.contains("netbanking") || message.contains("net banking")) return "NetBanking";
        if (message.contains("card") || message.contains("debit card") || message.contains("credit card")) {
            return message.contains("credit card") ? "Credit Card" : "Debit Card";
        }

        return "Transaction";
    }

    private String extractReferenceNumber(String message) {
        // Patterns for reference numbers
        Pattern[] refPatterns = {
                Pattern.compile("(?:Ref\\s?(?:No)?|Reference)\\s*:?\\s*([A-Za-z0-9]+)"),
                Pattern.compile("(?:txn|transaction)\\s*(?:id|no)\\s*:?\\s*([A-Za-z0-9]+)", Pattern.CASE_INSENSITIVE),
                Pattern.compile("UPI\\s*Ref\\s*No\\s*:\\s*([A-Za-z0-9]+)", Pattern.CASE_INSENSITIVE),
                Pattern.compile("IMPS\\s*([A-Za-z0-9]+)", Pattern.CASE_INSENSITIVE)
        };

        for (Pattern pattern : refPatterns) {
            Matcher matcher = pattern.matcher(message);
            if (matcher.find()) {
                return matcher.group(1);
            }
        }

        return "";
    }

    // Helper method to capitalize first letter of each word
    private String capitalizeWords(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }

        StringBuilder result = new StringBuilder();
        String[] words = input.split("\\s+");

        for (String word : words) {
            if (result.length() > 0) result.append(" ");
            if (!word.isEmpty()) {
                result.append(Character.toUpperCase(word.charAt(0)));
                if (word.length() > 1) {
                    result.append(word.substring(1).toLowerCase());
                }
            }
        }

        return result.toString();
    }
}