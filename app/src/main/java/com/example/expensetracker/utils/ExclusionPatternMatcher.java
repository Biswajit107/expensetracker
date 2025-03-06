package com.example.expensetracker.utils;

import android.util.Log;
import com.example.expensetracker.models.ExclusionPattern;
import com.example.expensetracker.models.Transaction;

import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

/**
 * Utility class for creating and matching transaction exclusion patterns
 */
public class ExclusionPatternMatcher {
    private static final String TAG = "ExclusionPatternMatcher";

    // Scoring thresholds and weights
    private static final int MATCH_THRESHOLD = 85; // Minimum score to consider a match

    // Scoring weights (total = 100)
    private static final int WEIGHT_MERCHANT = 35;
    private static final int WEIGHT_DESCRIPTION = 25;
    private static final int WEIGHT_AMOUNT = 20;
    private static final int WEIGHT_TYPE = 15;
    private static final int WEIGHT_CATEGORY = 5;

    /**
     * Create an exclusion pattern from a manually excluded transaction
     * @param transaction The transaction that was manually excluded
     * @return An ExclusionPattern object based on the transaction
     */
    public static ExclusionPattern createPatternFromTransaction(Transaction transaction) {
        if (transaction == null) {
            return null;
        }

        // 1. Extract merchant pattern
        String merchantPattern = extractMerchantPattern(transaction);

        // 2. Extract description pattern
        String descriptionPattern = extractDescriptionPattern(transaction);

        // 3. Calculate amount range (±10%)
        double amount = transaction.getAmount();
        double minAmount = amount * 0.9; // 10% less
        double maxAmount = amount * 1.1; // 10% more

        // 4. Get transaction type and category
        String transactionType = transaction.getType();
        String category = transaction.getCategory();

        // 5. Create and return the pattern
        return new ExclusionPattern(
                merchantPattern,
                descriptionPattern,
                minAmount,
                maxAmount,
                transactionType,
                category,
                transaction.getId()
        );
    }

    /**
     * Match a transaction against an exclusion pattern with a scoring system
     * @param transaction The transaction to check
     * @param pattern The exclusion pattern to match against
     * @return Match score (0-100), where scores >= MATCH_THRESHOLD are considered matches
     */
    public static int calculateMatchScore(Transaction transaction, ExclusionPattern pattern) {
        if (transaction == null || pattern == null) {
            return 0;
        }

        int totalScore = 0;

        // 1. Merchant match scoring (0-35 points)
        int merchantScore = calculateMerchantScore(transaction, pattern);
        totalScore += merchantScore;

        // 2. Description match scoring (0-25 points)
        int descriptionScore = calculateDescriptionScore(transaction, pattern);
        totalScore += descriptionScore;

        // 3. Amount range scoring (0-20 points)
        int amountScore = calculateAmountScore(transaction, pattern);
        totalScore += amountScore;

        // 4. Transaction type scoring (0 or 15 points)
        int typeScore = calculateTypeScore(transaction, pattern);
        totalScore += typeScore;

        // 5. Category scoring (0 or 5 points)
        int categoryScore = calculateCategoryScore(transaction, pattern);
        totalScore += categoryScore;

        // Log scoring breakdown for debugging
        Log.d(TAG, "Match score breakdown for transaction " + transaction.getId() +
                " vs pattern from transaction " + pattern.getSourceTransactionId() + ":" +
                " Merchant: " + merchantScore + "/" + WEIGHT_MERCHANT +
                ", Description: " + descriptionScore + "/" + WEIGHT_DESCRIPTION +
                ", Amount: " + amountScore + "/" + WEIGHT_AMOUNT +
                ", Type: " + typeScore + "/" + WEIGHT_TYPE +
                ", Category: " + categoryScore + "/" + WEIGHT_CATEGORY +
                ", Total: " + totalScore);

        return totalScore;
    }

    /**
     * Check if a transaction matches any exclusion pattern with high confidence
     * @param transaction The transaction to check
     * @param patterns List of active exclusion patterns
     * @return The matching pattern if found with score >= threshold, or null if no match
     */
    public static ExclusionPattern findMatchingPattern(Transaction transaction, List<ExclusionPattern> patterns) {
        ExclusionPattern bestMatch = null;
        int highestScore = 0;

        for (ExclusionPattern pattern : patterns) {
            int score = calculateMatchScore(transaction, pattern);

            if (score >= MATCH_THRESHOLD && score > highestScore) {
                highestScore = score;
                bestMatch = pattern;
            }
        }

        return bestMatch;
    }

    /**
     * Extract a meaningful merchant pattern from a transaction
     */
    private static String extractMerchantPattern(Transaction transaction) {
        String merchantName = transaction.getMerchantName();

        if (merchantName != null && !merchantName.isEmpty()) {
            // Clean up merchant name and create pattern
            return cleanAndNormalizeText(merchantName);
        } else {
            // Try to extract merchant from description
            String description = transaction.getDescription();
            if (description != null && !description.isEmpty()) {
                // Extract the first few words which might be the merchant
                String[] words = description.split("\\s+");
                if (words.length > 0) {
                    int wordLimit = Math.min(3, words.length);
                    StringBuilder merchantBuilder = new StringBuilder();

                    for (int i = 0; i < wordLimit; i++) {
                        if (merchantBuilder.length() > 0) {
                            merchantBuilder.append(" ");
                        }
                        merchantBuilder.append(words[i]);
                    }

                    return cleanAndNormalizeText(merchantBuilder.toString());
                }
            }
        }

        return ""; // No merchant pattern could be extracted
    }

    /**
     * Extract a meaningful description pattern from a transaction
     */
    private static String extractDescriptionPattern(Transaction transaction) {
        String description = transaction.getDescription();

        if (description != null && !description.isEmpty()) {
            // Clean and normalize the description
            String cleanDesc = cleanAndNormalizeText(description);

            // Extract key words or phrases
            // For now, just use the first 5-8 words of the description
            String[] words = cleanDesc.split("\\s+");

            if (words.length > 0) {
                int wordLimit = Math.min(Math.max(5, words.length / 2), 8);
                StringBuilder patternBuilder = new StringBuilder();

                for (int i = 0; i < wordLimit; i++) {
                    if (patternBuilder.length() > 0) {
                        patternBuilder.append(" ");
                    }
                    patternBuilder.append(words[i]);
                }

                return patternBuilder.toString();
            }
        }

        return ""; // No description pattern could be extracted
    }

    /**
     * Clean and normalize text for better pattern matching
     */
    private static String cleanAndNormalizeText(String text) {
        if (text == null) return "";

        // Convert to lowercase
        String result = text.toLowerCase();

        // Remove special characters
        result = result.replaceAll("[^a-z0-9\\s]", "");

        // Remove extra spaces
        result = result.replaceAll("\\s+", " ").trim();

        return result;
    }

    /**
     * Calculate a similarity score for merchant patterns (0-35 points)
     */
    private static int calculateMerchantScore(Transaction transaction, ExclusionPattern pattern) {
        String merchantPattern = pattern.getMerchantPattern();
        if (merchantPattern == null || merchantPattern.isEmpty()) {
            return 0;
        }

        String transactionMerchant = "";

        // Get merchant from transaction
        if (transaction.getMerchantName() != null && !transaction.getMerchantName().isEmpty()) {
            transactionMerchant = cleanAndNormalizeText(transaction.getMerchantName());
        } else {
            // Try to extract from description
            transactionMerchant = extractMerchantPattern(transaction);
        }

        if (transactionMerchant.isEmpty()) {
            return 0;
        }

        // Calculate similarity
        double similarity = calculateTextSimilarity(transactionMerchant, merchantPattern);

        // Convert to score - full score for 90%+ similarity, scaled down for lower similarities
        if (similarity >= 0.9) {
            return WEIGHT_MERCHANT;
        } else if (similarity >= 0.7) {
            return (int)(WEIGHT_MERCHANT * 0.8); // 80% of weight
        } else if (similarity >= 0.5) {
            return (int)(WEIGHT_MERCHANT * 0.5); // 50% of weight
        } else if (similarity >= 0.3) {
            return (int)(WEIGHT_MERCHANT * 0.3); // 30% of weight
        }

        return 0;
    }

    /**
     * Calculate a similarity score for description patterns (0-25 points)
     */
    private static int calculateDescriptionScore(Transaction transaction, ExclusionPattern pattern) {
        String descriptionPattern = pattern.getDescriptionPattern();
        if (descriptionPattern == null || descriptionPattern.isEmpty()) {
            return 0;
        }

        String transactionDesc = "";

        // Get description from transaction
        if (transaction.getDescription() != null && !transaction.getDescription().isEmpty()) {
            transactionDesc = cleanAndNormalizeText(transaction.getDescription());
        } else {
            return 0;
        }

        // Calculate similarity
        double similarity = calculateTextSimilarity(transactionDesc, descriptionPattern);

        // Convert to score
        if (similarity >= 0.8) {
            return WEIGHT_DESCRIPTION;
        } else if (similarity >= 0.6) {
            return (int)(WEIGHT_DESCRIPTION * 0.7); // 70% of weight
        } else if (similarity >= 0.4) {
            return (int)(WEIGHT_DESCRIPTION * 0.4); // 40% of weight
        } else if (similarity >= 0.2) {
            return (int)(WEIGHT_DESCRIPTION * 0.2); // 20% of weight
        }

        return 0;
    }

    /**
     * Calculate a similarity score for the amount range (0-20 points)
     */
    private static int calculateAmountScore(Transaction transaction, ExclusionPattern pattern) {
        double amount = transaction.getAmount();
        double minAmount = pattern.getMinAmount();
        double maxAmount = pattern.getMaxAmount();

        // Full score if amount is within range
        if (amount >= minAmount && amount <= maxAmount) {
            return WEIGHT_AMOUNT;
        }

        // Calculate how close the amount is to the range
        double midpoint = (minAmount + maxAmount) / 2;
        double range = maxAmount - minAmount;

        // If range is 0, avoid division by zero
        if (range == 0) {
            // Exact match required
            return amount == midpoint ? WEIGHT_AMOUNT : 0;
        }

        // Calculate distance from range
        double distance;
        if (amount < minAmount) {
            distance = minAmount - amount;
        } else { // amount > maxAmount
            distance = amount - maxAmount;
        }

        // Convert distance to percentage of range
        double distancePercentage = distance / range;

        // Score based on distance
        if (distancePercentage <= 0.1) { // Within 10% outside the range
            return (int)(WEIGHT_AMOUNT * 0.7); // 70% of weight
        } else if (distancePercentage <= 0.25) { // Within 25% outside the range
            return (int)(WEIGHT_AMOUNT * 0.4); // 40% of weight
        } else if (distancePercentage <= 0.5) { // Within 50% outside the range
            return (int)(WEIGHT_AMOUNT * 0.2); // 20% of weight
        }

        return 0;
    }

    /**
     * Calculate score for transaction type (0 or 15 points)
     */
    private static int calculateTypeScore(Transaction transaction, ExclusionPattern pattern) {
        String type1 = transaction.getType();
        String type2 = pattern.getTransactionType();

        if (type1 != null && type2 != null && type1.equals(type2)) {
            return WEIGHT_TYPE;
        }

        return 0;
    }

    /**
     * Calculate score for category (0 or 5 points)
     */
    private static int calculateCategoryScore(Transaction transaction, ExclusionPattern pattern) {
        String category1 = transaction.getCategory();
        String category2 = pattern.getCategory();

        // Both must have a category set
        if (category1 != null && !category1.isEmpty() &&
                category2 != null && !category2.isEmpty() &&
                category1.equals(category2)) {
            return WEIGHT_CATEGORY;
        }

        return 0;
    }

    /**
     * Calculate text similarity between two strings
     * @return Similarity score between 0 and 1
     */
    private static double calculateTextSimilarity(String text1, String text2) {
        if (text1 == null || text2 == null || text1.isEmpty() || text2.isEmpty()) {
            return 0;
        }

        // Convert to lowercase for comparison
        text1 = text1.toLowerCase();
        text2 = text2.toLowerCase();

        // Check for exact match
        if (text1.equals(text2)) {
            return 1.0;
        }

        // Check if one contains the other
        if (text1.contains(text2) || text2.contains(text1)) {
            return 0.9;
        }

        // Split into words and check word overlap
        String[] words1 = text1.split("\\s+");
        String[] words2 = text2.split("\\s+");

        int matchingWords = 0;
        for (String word1 : words1) {
            for (String word2 : words2) {
                if (word1.equals(word2) ||
                        (word1.length() > 3 && word2.length() > 3 &&
                                (word1.contains(word2) || word2.contains(word1)))) {
                    matchingWords++;
                    break;
                }
            }
        }

        // Calculate Jaccard similarity
        int totalWords = words1.length + words2.length - matchingWords;
        if (totalWords == 0) return 0;

        return (double) matchingWords / totalWords;
    }
}