package com.example.expensetracker.utils;

import android.util.Log;
import android.util.Pair;

import com.example.expensetracker.database.TransactionDao;
import com.example.expensetracker.models.Transaction;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class for detecting duplicate transactions using multiple strategies
 */
public class TransactionDuplicateDetector {
    private static final String TAG = "DuplicateDetector";

    // Patterns for OTP messages
    private static final Pattern OTP_PATTERN = Pattern.compile(
            "(?i)(OTP|one.?time.?password|verification.?code|secure.?code|security.?code)");

    /**
     * Generate a unique fingerprint for a transaction
     * @param transaction The transaction to fingerprint
     * @return A unique fingerprint string
     */
    public static String generateFingerprint(Transaction transaction) {
        if (transaction == null) return null;

        try {
            // Generate components for the fingerprint
            String amountStr = String.format(Locale.US, "%.2f", transaction.getAmount());
            String dayStr = getDayOnly(transaction.getDate());
            String merchantStr = extractMerchantKey(transaction);
            String typeStr = transaction.getType();

            // Combine components into a single string
            String content = amountStr + "|" + dayStr + "|" + merchantStr + "|" + typeStr;

            // Generate SHA-256 hash
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(content.getBytes());

            // Convert to Base64 string
            return Base64.getEncoder().encodeToString(hashBytes);

        } catch (NoSuchAlgorithmException e) {
            Log.e(TAG, "Error generating fingerprint", e);
            return null;
        }
    }

    /**
     * Extract the day part of a timestamp as a string
     * @param timestamp The timestamp in milliseconds
     * @return A string representing just the day (YYYY-MM-DD)
     */
    private static String getDayOnly(long timestamp) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(timestamp);

        // Reset time to start of day
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);

        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        return dateFormat.format(calendar.getTime());
    }

    /**
     * Extract a key identifier from the merchant or description
     * @param transaction The transaction to analyze
     * @return A normalized merchant/description key
     */
    private static String extractMerchantKey(Transaction transaction) {
        // Try to use merchant name first if available
        if (transaction.getMerchantName() != null && !transaction.getMerchantName().isEmpty()) {
            return normalizeText(transaction.getMerchantName());
        }

        // Fall back to description if needed
        if (transaction.getDescription() != null && !transaction.getDescription().isEmpty()) {
            String desc = transaction.getDescription();

            // Extract the first part of the description (first 15 chars or up to first space)
            int endIndex = Math.min(15, desc.length());
            int spaceIndex = desc.indexOf(' ');
            if (spaceIndex > 0 && spaceIndex < endIndex) {
                endIndex = spaceIndex;
            }

            return normalizeText(desc.substring(0, endIndex));
        }

        return "UNKNOWN";
    }

    /**
     * Normalize text by removing special characters and converting to lowercase
     * @param text Text to normalize
     * @return Normalized text
     */
    private static String normalizeText(String text) {
        if (text == null) return "";

        // Remove special characters, spaces, and convert to lowercase
        return text.replaceAll("[^a-zA-Z0-9]", "").toLowerCase();
    }

    /**
     * Check if a transaction is a duplicate in the database.
     * Note: This doesn't skip the transaction but marks it for exclusion.
     * @param transaction The transaction to check
     * @param dao The TransactionDao to use for database queries
     * @return true if this is a duplicate that should be marked as excluded
     */
    public static boolean isDuplicate(Transaction transaction, TransactionDao dao) {
        if (transaction == null || dao == null) {
            return false;
        }

        // First, check by fingerprint if we have an exact match
        String fingerprint = generateFingerprint(transaction);
        if (fingerprint == null) {
            return false;
        }

        // Get potential duplicates from the same day
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(transaction.getDate());
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        long startOfDay = cal.getTimeInMillis();

        cal.add(Calendar.DAY_OF_MONTH, 1);
        long endOfDay = cal.getTimeInMillis() - 1; // End of the day

        List<Transaction> sameDayTransactions = dao.getTransactionsBetweenDatesSync(
                startOfDay, endOfDay);

        // Check for fingerprint matches
        for (Transaction existing : sameDayTransactions) {
            String existingFingerprint = generateFingerprint(existing);
            if (fingerprint.equals(existingFingerprint)) {
                // Log found duplicate
                Log.d(TAG, "Found duplicate by fingerprint: " + transaction.getDescription());
                return true;
            }
        }

        // If no exact fingerprint match, check for high similarity in same time window
        return hasHighSimilarityDuplicate(transaction, sameDayTransactions);
    }

    /**
     * Check if a transaction has a high similarity duplicate in a list
     * @param transaction The transaction to check
     * @param potentialDuplicates List of potential duplicates
     * @return true if a high similarity duplicate is found
     */
    private static boolean hasHighSimilarityDuplicate(Transaction transaction,
                                                      List<Transaction> potentialDuplicates) {
        if (potentialDuplicates == null || potentialDuplicates.isEmpty()) {
            return false;
        }

        for (Transaction existing : potentialDuplicates) {
            // Skip comparing with self (different object but same ID)
            if (existing.getId() == transaction.getId()) {
                continue;
            }

            // Calculate similarity score
            int score = calculateSimilarityScore(transaction, existing);

            // High confidence duplicate: same amount, close time, similar merchant/description
            if (score >= 80) {
                Log.d(TAG, "Found duplicate by similarity score (" + score +
                        "): " + transaction.getDescription());
                return true;
            }
        }

        return false;
    }

    /**
     * Calculate a similarity score between two transactions
     * @param t1 First transaction
     * @param t2 Second transaction
     * @return A score from 0-100 representing similarity
     */
    private static int calculateSimilarityScore(Transaction t1, Transaction t2) {
        int score = 0;

        // Same amount is a strong indicator (40 points)
        if (Math.abs(t1.getAmount() - t2.getAmount()) < 0.01) {
            score += 40;
        }

        // Same transaction type (20 points)
        if (t1.getType() != null && t1.getType().equals(t2.getType())) {
            score += 20;
        }

        // Time proximity (up to 20 points)
        long timeDiffMs = Math.abs(t1.getDate() - t2.getDate());
        if (timeDiffMs < TimeUnit.MINUTES.toMillis(1)) {
            // Within 1 minute: 20 points
            score += 20;
        } else if (timeDiffMs < TimeUnit.MINUTES.toMillis(10)) {
            // Within 10 minutes: 15 points
            score += 15;
        } else if (timeDiffMs < TimeUnit.HOURS.toMillis(1)) {
            // Within 1 hour: 10 points
            score += 10;
        } else if (timeDiffMs < TimeUnit.HOURS.toMillis(4)) {
            // Within 4 hours: 5 points
            score += 5;
        }

        // Merchant/description similarity (up to 20 points)
        String m1 = extractMerchantKey(t1);
        String m2 = extractMerchantKey(t2);

        if (m1.equals(m2)) {
            score += 20;
        } else if (m1.contains(m2) || m2.contains(m1)) {
            score += 15;
        } else {
            // Check for partial match
            int minLength = Math.min(m1.length(), m2.length());
            if (minLength > 3) {
                String shorter = m1.length() <= m2.length() ? m1 : m2;
                String longer = m1.length() > m2.length() ? m1 : m2;
                if (longer.contains(shorter)) {
                    score += 10;
                }
            }
        }

        return score;
    }

    /**
     * Find potential duplicates for a transaction in a time window
     * @param transaction The transaction to check
     * @param dao The TransactionDao to use for queries
     * @return A list of potential duplicates with confidence scores
     */
    public static List<Pair<Transaction, Integer>> findPotentialDuplicates(
            Transaction transaction, TransactionDao dao) {
        List<Pair<Transaction, Integer>> results = new ArrayList<>();

        if (transaction == null || dao == null) {
            return results;
        }

        // Define time window for potential duplicates (8 hours on either side)
        long startTime = transaction.getDate() - TimeUnit.HOURS.toMillis(8);
        long endTime = transaction.getDate() + TimeUnit.HOURS.toMillis(8);

        // Get transactions in the time window
        List<Transaction> timeWindowTransactions = dao.getTransactionsBetweenDatesSync(
                startTime, endTime);

        // Calculate similarity scores
        for (Transaction existing : timeWindowTransactions) {
            // Skip comparing with self
            if (existing.getId() == transaction.getId()) {
                continue;
            }

            int score = calculateSimilarityScore(transaction, existing);

            // Only include reasonable potential matches
            if (score >= 50) {
                results.add(new Pair<>(existing, score));
            }
        }

        return results;
    }

}