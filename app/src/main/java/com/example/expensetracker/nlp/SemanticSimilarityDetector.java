package com.example.expensetracker.nlp;

import android.util.Log;
import com.example.expensetracker.models.Transaction;
import java.util.HashSet;
import java.util.Set;

public class SemanticSimilarityDetector {
    private static final String TAG = "SemanticSimilarityDetector";
    private static final double SIMILARITY_THRESHOLD = 0.7;

    // Main method to check if two transactions are semantically similar (duplicates)
    public boolean areTransactionsSimilar(Transaction t1, Transaction t2) {
        // Must be close in time (within 24 hours)
        long timeDifference = Math.abs(t1.getDate() - t2.getDate());
        if (timeDifference > 24 * 60 * 60 * 1000) {
            return false;
        }

        // First quick check - amount must be very close
        if (Math.abs(t1.getAmount() - t2.getAmount()) > 0.01) {
            return false;
        }

        // For transactions that are in opposite directions (credit vs debit)
        // they might be the same transaction from different perspectives
        if (!t1.getType().equals(t2.getType()) &&
                isComplementaryTransactionPair(t1, t2)) {
            return true;
        }

        // For same type transactions, check if they appear to be duplicates
        if (t1.getType().equals(t2.getType())) {
            // Check semantic similarity of descriptions
            double descriptionSimilarity = calculateJaccardSimilarity(
                    tokenizeDescription(t1.getDescription()),
                    tokenizeDescription(t2.getDescription())
            );

            // If merchant names are available, check their similarity as well
            double merchantSimilarity = 0.0;
            if (t1.getMerchantName() != null && !t1.getMerchantName().isEmpty() &&
                    t2.getMerchantName() != null && !t2.getMerchantName().isEmpty()) {

                merchantSimilarity = calculateJaccardSimilarity(
                        tokenizeDescription(t1.getMerchantName()),
                        tokenizeDescription(t2.getMerchantName())
                );
            }

            // Combine similarity scores with weights
            double combinedSimilarity = (descriptionSimilarity * 0.7) + (merchantSimilarity * 0.3);

            // If merchants are very similar, it boosts the overall similarity
            if (merchantSimilarity > 0.8) {
                combinedSimilarity = Math.max(combinedSimilarity, 0.8);
            }

            return combinedSimilarity >= SIMILARITY_THRESHOLD;
        }

        return false;
    }

    private boolean isComplementaryTransactionPair(Transaction t1, Transaction t2) {
        // One must be credit and one must be debit
        if (!(t1.isCredit() && t2.isDebit()) && !(t1.isDebit() && t2.isCredit())) {
            return false;
        }

        String desc1 = t1.getDescription().toLowerCase();
        String desc2 = t2.getDescription().toLowerCase();

        // Check for complementary indicators (sent/received, to/from)
        boolean complementaryIndicators =
                (desc1.contains("sent") && desc2.contains("received")) ||
                        (desc1.contains("received") && desc2.contains("sent")) ||
                        (desc1.contains(" to ") && desc2.contains(" from ")) ||
                        (desc1.contains(" from ") && desc2.contains(" to "));

        if (!complementaryIndicators) {
            return false;
        }

        // Check for similar reference numbers
        String ref1 = extractReferenceNumber(desc1);
        String ref2 = extractReferenceNumber(desc2);

        if (!ref1.isEmpty() && !ref2.isEmpty() && ref1.equals(ref2)) {
            return true;
        }

        // Check for same merchant/person reference
        Set<String> tokens1 = tokenizeDescription(desc1);
        Set<String> tokens2 = tokenizeDescription(desc2);

        // Remove common words to focus on entity names
        tokens1.removeAll(CommonWords.getStopWords());
        tokens2.removeAll(CommonWords.getStopWords());

        // Check for shared key tokens
        Set<String> sharedTokens = new HashSet<>(tokens1);
        sharedTokens.retainAll(tokens2);

        return !sharedTokens.isEmpty();
    }

    private String extractReferenceNumber(String text) {
        // Simple pattern matching for reference numbers
        int refIndex = text.indexOf("ref:");
        if (refIndex == -1) refIndex = text.indexOf("ref ");
        if (refIndex == -1) refIndex = text.indexOf("reference:");
        if (refIndex == -1) refIndex = text.indexOf("reference ");

        if (refIndex >= 0) {
            int startIndex = refIndex + 4; // Length of "ref:"
            int endIndex = Math.min(startIndex + 15, text.length());
            String refCandidate = text.substring(startIndex, endIndex).trim();

            // Take the alphanumeric part
            StringBuilder ref = new StringBuilder();
            for (char c : refCandidate.toCharArray()) {
                if (Character.isLetterOrDigit(c)) {
                    ref.append(c);
                } else if (ref.length() > 0) {
                    break;
                }
            }

            return ref.toString();
        }

        return "";
    }

    // Tokenize description into meaningful words
    private Set<String> tokenizeDescription(String text) {
        if (text == null) return new HashSet<>();

        // Normalize text
        text = text.toLowerCase()
                .replaceAll("[^a-z0-9\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();

        // Split into tokens
        String[] tokens = text.split("\\s+");

        // Filter out short tokens and numbers
        Set<String> meaningfulTokens = new HashSet<>();
        for (String token : tokens) {
            if (token.length() > 2 && !token.matches("\\d+")) {
                meaningfulTokens.add(token);
            }
        }

        return meaningfulTokens;
    }

    // Calculate Jaccard similarity between two sets of tokens
    private double calculateJaccardSimilarity(Set<String> set1, Set<String> set2) {
        if (set1.isEmpty() && set2.isEmpty()) return 0.0;

        Set<String> union = new HashSet<>(set1);
        union.addAll(set2);

        Set<String> intersection = new HashSet<>(set1);
        intersection.retainAll(set2);

        return (double) intersection.size() / union.size();
    }

    // Calculate Levenshtein distance for string similarity
    private int levenshteinDistance(String a, String b) {
        if (a.isEmpty()) return b.length();
        if (b.isEmpty()) return a.length();

        int[][] dp = new int[a.length() + 1][b.length() + 1];

        for (int i = 0; i <= a.length(); i++) {
            for (int j = 0; j <= b.length(); j++) {
                if (i == 0) {
                    dp[i][j] = j;
                } else if (j == 0) {
                    dp[i][j] = i;
                } else {
                    dp[i][j] = Math.min(
                            dp[i - 1][j - 1] + (a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1),
                            Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1)
                    );
                }
            }
        }

        return dp[a.length()][b.length()];
    }
}