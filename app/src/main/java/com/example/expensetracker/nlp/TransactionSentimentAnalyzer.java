package com.example.expensetracker.nlp;

import android.util.Log;
import java.util.HashMap;
import java.util.Map;

public class TransactionSentimentAnalyzer {
    private static final String TAG = "TransactionSentimentAnalyzer";

    // Positive sentiment words related to receiving money
    private final Map<String, Double> positiveTokens = new HashMap<>();

    // Negative sentiment words related to spending money
    private final Map<String, Double> negativeTokens = new HashMap<>();

    public TransactionSentimentAnalyzer() {
        // Initialize sentiment lexicon with weights
        positiveTokens.put("credited", 1.0);
        positiveTokens.put("received", 1.0);
        positiveTokens.put("added", 0.8);
        positiveTokens.put("deposited", 0.9);
        positiveTokens.put("cashback", 0.7);
        positiveTokens.put("refund", 0.8);
        positiveTokens.put("salary", 1.0);
        positiveTokens.put("interest", 0.6);
        positiveTokens.put("credit", 0.9);
        positiveTokens.put("income", 0.8);
        positiveTokens.put("bonus", 0.8);

        negativeTokens.put("debited", 1.0);
        negativeTokens.put("withdrawn", 0.9);
        negativeTokens.put("spent", 0.8);
        negativeTokens.put("paid", 0.8);
        negativeTokens.put("deducted", 0.9);
        negativeTokens.put("charged", 0.7);
        negativeTokens.put("payment", 0.6);
        negativeTokens.put("purchase", 0.7);
        negativeTokens.put("debit", 0.9);
        negativeTokens.put("transfer", 0.6);
        negativeTokens.put("bill", 0.7);
    }

    public TransactionType analyzeTransactionSentiment(String message) {
        message = message.toLowerCase();

        double positiveScore = 0.0;
        double negativeScore = 0.0;

        // Calculate sentiment scores
        for (Map.Entry<String, Double> entry : positiveTokens.entrySet()) {
            if (message.contains(entry.getKey())) {
                positiveScore += entry.getValue();
                //Log.d(TAG, "Positive token: " + entry.getKey() + ", Score: " + entry.getValue());
            }
        }

        for (Map.Entry<String, Double> entry : negativeTokens.entrySet()) {
            if (message.contains(entry.getKey())) {
                negativeScore += entry.getValue();
                //Log.d(TAG, "Negative token: " + entry.getKey() + ", Score: " + entry.getValue());
            }
        }

        // Check for phrases that strongly indicate credit
        if (message.contains("credited to your") ||
                message.contains("received in your") ||
                message.contains("deposited to your")) {
            positiveScore += 1.5;
        }

        // Check for phrases that strongly indicate debit
        if (message.contains("debited from your") ||
                message.contains("withdrawn from your") ||
                message.contains("paid from your")) {
            negativeScore += 1.5;
        }

        // Context modifiers - check for negation
        if (message.contains("not") || message.contains("failed") ||
                message.contains("rejected") || message.contains("declined") ||
                message.contains("unsuccessful") || message.contains("cancelled")) {

            // Invert the sentiment for negative contexts
            double temp = positiveScore;
            positiveScore = negativeScore;
            negativeScore = temp;
        }

        //Log.d(TAG, "Final positive score: " + positiveScore + ", Final negative score: " + negativeScore);

        // Determine transaction type based on sentiment
        if (positiveScore > negativeScore) {
            return TransactionType.CREDIT;
        } else if (negativeScore > positiveScore) {
            return TransactionType.DEBIT;
        } else {
            // If scores are equal, default to DEBIT as most common
            return TransactionType.DEBIT;
        }
    }
}