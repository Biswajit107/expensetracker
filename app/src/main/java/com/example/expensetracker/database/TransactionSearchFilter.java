package com.example.expensetracker.database;

import androidx.sqlite.db.SimpleSQLiteQuery;
import androidx.sqlite.db.SupportSQLiteQuery;

import java.util.ArrayList;
import java.util.List;

public class TransactionSearchFilter {
    private String searchText;
    private String bank;
    private String type;
    private String category;
    private Double minAmount;
    private Double maxAmount;
    private Long startDate;
    private Long endDate;
    private Boolean excludedFromTotal;
    private Boolean isRecurring;
    private String merchantName;

    // Builder pattern for creating search filters
    public static class Builder {
        private final TransactionSearchFilter filter;

        public Builder() {
            filter = new TransactionSearchFilter();
        }

        public Builder searchText(String searchText) {
            filter.searchText = searchText;
            return this;
        }

        public Builder bank(String bank) {
            filter.bank = bank;
            return this;
        }

        public Builder type(String type) {
            filter.type = type;
            return this;
        }

        public Builder category(String category) {
            filter.category = category;
            return this;
        }

        public Builder amountRange(Double min, Double max) {
            filter.minAmount = min;
            filter.maxAmount = max;
            return this;
        }

        public Builder dateRange(Long start, Long end) {
            filter.startDate = start;
            filter.endDate = end;
            return this;
        }

        public Builder excludedFromTotal(Boolean excluded) {
            filter.excludedFromTotal = excluded;
            return this;
        }

        public Builder isRecurring(Boolean recurring) {
            filter.isRecurring = recurring;
            return this;
        }

        public Builder merchantName(String merchantName) {
            filter.merchantName = merchantName;
            return this;
        }

        public TransactionSearchFilter build() {
            return filter;
        }
    }

    // Build a query based on the filter parameters
    public SupportSQLiteQuery buildSearchQuery() {
        StringBuilder queryBuilder = new StringBuilder();
        List<Object> args = new ArrayList<>();

        queryBuilder.append("SELECT * FROM transactions WHERE 1=1");

        // Add search text filter (searches across multiple columns)
        if (searchText != null && !searchText.isEmpty()) {
            queryBuilder.append(" AND (");
            queryBuilder.append("description LIKE ?");
            args.add("%" + searchText + "%");

            queryBuilder.append(" OR merchant_name LIKE ?");
            args.add("%" + searchText + "%");

            queryBuilder.append(" OR category LIKE ?");
            args.add("%" + searchText + "%");

            queryBuilder.append(" OR bank LIKE ?");
            args.add("%" + searchText + "%");

            queryBuilder.append(" OR original_sms LIKE ?");
            args.add("%" + searchText + "%");

            queryBuilder.append(")");
        }

        // Add specific filters
        if (bank != null && !bank.isEmpty() && !bank.equals("All Banks")) {
            queryBuilder.append(" AND bank = ?");
            args.add(bank);
        }

        if (type != null && !type.isEmpty() && !type.equals("All Types")) {
            queryBuilder.append(" AND type = ?");
            args.add(type);
        }

        if (category != null && !category.isEmpty()) {
            queryBuilder.append(" AND category = ?");
            args.add(category);
        }

        if (minAmount != null) {
            queryBuilder.append(" AND amount >= ?");
            args.add(minAmount);
        }

        if (maxAmount != null) {
            queryBuilder.append(" AND amount <= ?");
            args.add(maxAmount);
        }

        if (startDate != null) {
            queryBuilder.append(" AND date >= ?");
            args.add(startDate);
        }

        if (endDate != null) {
            queryBuilder.append(" AND date <= ?");
            args.add(endDate);
        }

        if (excludedFromTotal != null) {
            queryBuilder.append(" AND is_excluded_from_total = ?");
            args.add(excludedFromTotal ? 1 : 0);
        }

        if (isRecurring != null) {
            queryBuilder.append(" AND is_recurring = ?");
            args.add(isRecurring ? 1 : 0);
        }

        if (merchantName != null && !merchantName.isEmpty()) {
            queryBuilder.append(" AND merchant_name LIKE ?");
            args.add("%" + merchantName + "%");
        }

        // Add order by date (most recent first)
        queryBuilder.append(" ORDER BY date DESC");

        return new SimpleSQLiteQuery(queryBuilder.toString(), args.toArray());
    }
}