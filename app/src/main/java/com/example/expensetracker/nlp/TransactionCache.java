package com.example.expensetracker.nlp;

import android.util.LruCache;

public class TransactionCache {
    private static final int MAX_CACHE_SIZE = 200;
    private final LruCache<String, Long> fingerprintCache;

    public TransactionCache() {
        fingerprintCache = new LruCache<>(MAX_CACHE_SIZE);
    }

    public void addFingerprint(String fingerprint, long timestamp) {
        fingerprintCache.put(fingerprint, timestamp);
    }

    public boolean containsFingerprint(String fingerprint) {
        return fingerprintCache.get(fingerprint) != null;
    }

    public void cleanup(long olderThan) {
        // Create a copy of all entries
        for (int i = 0; i < fingerprintCache.size(); i++) {
            String key = fingerprintCache.snapshot().keySet().toArray(new String[0])[i];
            Long timestamp = fingerprintCache.get(key);

            if (timestamp != null && timestamp < olderThan) {
                fingerprintCache.remove(key);
            }
        }
    }
}