package com.example.expensetracker.nlp;

import java.util.HashSet;
import java.util.Set;

public class CommonWords {
    private static final Set<String> stopWords = new HashSet<>();

    static {
        // Initialize with common stop words
        String[] words = {
                "a", "about", "above", "after", "again", "against", "all", "am", "an", "and", "any", "are", "as", "at",
                "be", "because", "been", "before", "being", "below", "between", "both", "but", "by", "can", "did", "do",
                "does", "doing", "down", "during", "each", "few", "for", "from", "further", "had", "has", "have",
                "having", "he", "her", "here", "hers", "herself", "him", "himself", "his", "how", "i", "if", "in",
                "into", "is", "it", "its", "itself", "just", "me", "more", "most", "my", "myself", "no", "nor",
                "not", "now", "of", "off", "on", "once", "only", "or", "other", "our", "ours", "ourselves",
                "out", "over", "own", "same", "she", "should", "so", "some", "such", "than", "that", "the",
                "their", "theirs", "them", "themselves", "then", "there", "these", "they", "this", "those",
                "through", "to", "too", "under", "until", "up", "very", "was", "we", "were", "what", "when",
                "where", "which", "while", "who", "whom", "why", "will", "with", "would", "you", "your",
                "yours", "yourself", "yourselves",

                // Banking specific words
                "account", "bank", "transaction", "transfer", "amount", "rs", "inr", "rs.", "rupees",
                "credited", "debited", "balance", "available", "ref", "upi", "info", "alert", "id",
                "txn", "payment", "debit", "credit", "withdrawn", "received"
        };

        for (String word : words) {
            stopWords.add(word);
        }
    }

    public static Set<String> getStopWords() {
        return new HashSet<>(stopWords);
    }
}