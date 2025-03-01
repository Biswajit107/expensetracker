package com.example.expensetracker.utils;

import com.example.expensetracker.models.Transaction;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Utility class for searching and sorting transactions
 */
public class TransactionSearchSortUtil {

    /**
     * Search transactions based on query text
     * @param transactions List of transactions to search in
     * @param query Search query
     * @return Filtered list of transactions matching the query
     */
    public static List<Transaction> searchTransactions(List<Transaction> transactions, String query) {
        if (query == null || query.trim().isEmpty()) {
            return transactions;
        }

        String lowerQuery = query.toLowerCase().trim();
        List<Transaction> filteredList = new ArrayList<>();

        for (Transaction transaction : transactions) {
            // Check if any field contains the query
            if ((transaction.getDescription() != null &&
                    transaction.getDescription().toLowerCase().contains(lowerQuery)) ||
                    (transaction.getBank() != null &&
                            transaction.getBank().toLowerCase().contains(lowerQuery)) ||
                    (transaction.getCategory() != null &&
                            transaction.getCategory().toLowerCase().contains(lowerQuery)) ||
                    (transaction.getMerchantName() != null &&
                            transaction.getMerchantName().toLowerCase().contains(lowerQuery)) ||
                    String.valueOf(transaction.getAmount()).contains(lowerQuery)) {

                filteredList.add(transaction);
            }
        }

        return filteredList;
    }

    /**
     * Advanced search with multiple criteria
     * @param transactions List of transactions to search
     * @param criteria SearchCriteria object containing the search parameters
     * @return Filtered list of transactions matching all criteria
     */
    public static List<Transaction> advancedSearch(List<Transaction> transactions, SearchCriteria criteria) {
        List<Transaction> filteredList = new ArrayList<>();

        for (Transaction transaction : transactions) {
            boolean matches = true;

            // Check each criterion if it's set
            if (criteria.getMinAmount() != null && transaction.getAmount() < criteria.getMinAmount()) {
                matches = false;
            }

            if (criteria.getMaxAmount() != null && transaction.getAmount() > criteria.getMaxAmount()) {
                matches = false;
            }

            if (criteria.getStartDate() != null && transaction.getDate() < criteria.getStartDate()) {
                matches = false;
            }

            if (criteria.getEndDate() != null && transaction.getDate() > criteria.getEndDate()) {
                matches = false;
            }

            if (criteria.getBank() != null && !criteria.getBank().isEmpty() &&
                    !criteria.getBank().equals("All Banks") &&
                    (transaction.getBank() == null ||
                            !transaction.getBank().equalsIgnoreCase(criteria.getBank()))) {
                matches = false;
            }

            if (criteria.getType() != null && !criteria.getType().isEmpty() &&
                    !criteria.getType().equals("All Types") &&
                    (transaction.getType() == null ||
                            !transaction.getType().equalsIgnoreCase(criteria.getType()))) {
                matches = false;
            }

            if (criteria.getCategory() != null && !criteria.getCategory().isEmpty() &&
                    (transaction.getCategory() == null ||
                            !transaction.getCategory().equalsIgnoreCase(criteria.getCategory()))) {
                matches = false;
            }

            if (criteria.getSearchText() != null && !criteria.getSearchText().isEmpty()) {
                String query = criteria.getSearchText().toLowerCase();
                boolean containsText = (transaction.getDescription() != null &&
                        transaction.getDescription().toLowerCase().contains(query)) ||
                        (transaction.getMerchantName() != null &&
                                transaction.getMerchantName().toLowerCase().contains(query));

                if (!containsText) {
                    matches = false;
                }
            }

            if (matches) {
                filteredList.add(transaction);
            }
        }

        return filteredList;
    }

    /**
     * Sort transactions based on specified field and direction
     * @param transactions List of transactions to sort
     * @param sortBy Field to sort by (date, amount, description, etc.)
     * @param ascending True for ascending order, false for descending
     * @return Sorted list of transactions
     */
    public static List<Transaction> sortTransactions(List<Transaction> transactions,
                                                     SortBy sortBy, boolean ascending) {
        List<Transaction> sortedList = new ArrayList<>(transactions);

        Comparator<Transaction> comparator;

        switch (sortBy) {
            case DATE:
                comparator = Comparator.comparing(Transaction::getDate);
                break;
            case AMOUNT:
                comparator = Comparator.comparing(Transaction::getAmount);
                break;
            case DESCRIPTION:
                comparator = Comparator.comparing(
                        transaction -> transaction.getDescription() != null ?
                                transaction.getDescription() : "",
                        String.CASE_INSENSITIVE_ORDER);
                break;
            case BANK:
                comparator = Comparator.comparing(
                        transaction -> transaction.getBank() != null ?
                                transaction.getBank() : "",
                        String.CASE_INSENSITIVE_ORDER);
                break;
            case CATEGORY:
                comparator = Comparator.comparing(
                        transaction -> transaction.getCategory() != null ?
                                transaction.getCategory() : "",
                        String.CASE_INSENSITIVE_ORDER);
                break;
            case MERCHANT:
                comparator = Comparator.comparing(
                        transaction -> transaction.getMerchantName() != null ?
                                transaction.getMerchantName() : "",
                        String.CASE_INSENSITIVE_ORDER);
                break;
            default:
                comparator = Comparator.comparing(Transaction::getDate);
        }

        if (!ascending) {
            comparator = comparator.reversed();
        }

        Collections.sort(sortedList, comparator);
        return sortedList;
    }

    /**
     * Enum for sorting options
     */
    public enum SortBy {
        DATE,
        AMOUNT,
        DESCRIPTION,
        BANK,
        CATEGORY,
        MERCHANT
    }

    /**
     * Class to hold search criteria for advanced searches
     */
    public static class SearchCriteria {
        private Double minAmount;
        private Double maxAmount;
        private Long startDate;
        private Long endDate;
        private String bank;
        private String type;
        private String category;
        private String searchText;

        public SearchCriteria() {}

        // Builder pattern for easy construction
        public static class Builder {
            private Double minAmount;
            private Double maxAmount;
            private Long startDate;
            private Long endDate;
            private String bank;
            private String type;
            private String category;
            private String searchText;

            public Builder minAmount(Double minAmount) {
                this.minAmount = minAmount;
                return this;
            }

            public Builder maxAmount(Double maxAmount) {
                this.maxAmount = maxAmount;
                return this;
            }

            public Builder dateRange(Long startDate, Long endDate) {
                this.startDate = startDate;
                this.endDate = endDate;
                return this;
            }

            public Builder bank(String bank) {
                this.bank = bank;
                return this;
            }

            public Builder type(String type) {
                this.type = type;
                return this;
            }

            public Builder category(String category) {
                this.category = category;
                return this;
            }

            public Builder searchText(String searchText) {
                this.searchText = searchText;
                return this;
            }

            public SearchCriteria build() {
                SearchCriteria criteria = new SearchCriteria();
                criteria.minAmount = this.minAmount;
                criteria.maxAmount = this.maxAmount;
                criteria.startDate = this.startDate;
                criteria.endDate = this.endDate;
                criteria.bank = this.bank;
                criteria.type = this.type;
                criteria.category = this.category;
                criteria.searchText = this.searchText;
                return criteria;
            }
        }

        // Getters
        public Double getMinAmount() { return minAmount; }
        public Double getMaxAmount() { return maxAmount; }
        public Long getStartDate() { return startDate; }
        public Long getEndDate() { return endDate; }
        public String getBank() { return bank; }
        public String getType() { return type; }
        public String getCategory() { return category; }
        public String getSearchText() { return searchText; }
    }
}