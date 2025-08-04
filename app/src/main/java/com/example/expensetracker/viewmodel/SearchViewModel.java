package com.example.expensetracker.viewmodel;

import android.app.Application;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.MutableLiveData;
import androidx.sqlite.db.SupportSQLiteQuery;

import com.example.expensetracker.database.TransactionDatabase;
import com.example.expensetracker.database.TransactionSearchFilter;
import com.example.expensetracker.database.TQLParser;
import com.example.expensetracker.database.TQLProcessor;
import com.example.expensetracker.database.dynamic.SimpleDynamicTQLParser;
import com.example.expensetracker.database.dynamic.TimeExpressionParser;
import com.example.expensetracker.database.dynamic.TimeFilterTestHelper;
import com.example.expensetracker.database.TQLQuery;
import com.example.expensetracker.models.Transaction;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SearchViewModel extends AndroidViewModel {
    private final ExecutorService executorService;
    private final TQLProcessor tqlProcessor;

    // Only use LiveData for UI updates
    private final MutableLiveData<List<Transaction>> searchResults = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();
    
    // TQL-specific LiveData
    private final MutableLiveData<TQLProcessor.TQLResult> tqlResults = new MutableLiveData<>();
    private final MutableLiveData<String> suggestTQLMode = new MutableLiveData<>();
    private final MutableLiveData<Boolean> tqlMode = new MutableLiveData<>(false);
    private final MutableLiveData<String> debugLog = new MutableLiveData<>();
    private boolean isTQLMode = false;

    // Filter parameters - not using LiveData for these
    private String searchText = "";
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

    // Callback interface for search operations
    public interface SearchCallback {
        void onSearchComplete(List<Transaction> results);
        void onError(String message);
    }

    public SearchViewModel(Application application) {
        super(application);
        executorService = Executors.newSingleThreadExecutor();
        tqlProcessor = new TQLProcessor(application);

        // Initialize with all transactions
        loadAllTransactions(null);
    }

    public MutableLiveData<List<Transaction>> getSearchResults() {
        return searchResults;
    }

    public MutableLiveData<Boolean> getIsLoading() {
        return isLoading;
    }

    public MutableLiveData<String> getErrorMessage() {
        return errorMessage;
    }
    
    public MutableLiveData<TQLProcessor.TQLResult> getTQLResults() {
        return tqlResults;
    }
    
    public MutableLiveData<String> getSuggestTQLMode() {
        return suggestTQLMode;
    }
    
    public MutableLiveData<Boolean> getTQLMode() {
        return tqlMode;
    }
    
    public MutableLiveData<String> getDebugLog() {
        return debugLog;
    }
    
    public boolean isTQLMode() {
        return isTQLMode;
    }
    
    public void setTQLMode(boolean tqlModeValue) {
        this.isTQLMode = tqlModeValue;
        this.tqlMode.setValue(tqlModeValue);
        if (!tqlModeValue) {
            // Clear TQL results when switching back to normal mode
            tqlResults.setValue(null);
            suggestTQLMode.setValue(null);
        }
    }
    
    /**
     * Enable TQL mode and immediately process the given query
     */
    public void enableTQLModeAndSearch(String query) {
        setTQLMode(true);
        if (query != null && !query.trim().isEmpty()) {
            performTQLQuery(query);
        }
    }

    // Search text methods
    public void setSearchText(String text) {
        this.searchText = text;
        
        String logMsg = "setSearchText: '" + text + "', TQL mode: " + isTQLMode;
        android.util.Log.d("SearchViewModel", "*** " + logMsg + " ***");
        debugLog.postValue(logMsg);
        
        // Smart detection: Check if query looks like TQL when not in TQL mode
        if (!isTQLMode && text != null && !text.trim().isEmpty() && looksLikeTQLQuery(text)) {
            String detectMsg = "Detected TQL pattern, auto-enabling TQL mode and processing: '" + text + "'";
            android.util.Log.d("SearchViewModel", "*** " + detectMsg + " ***");
            debugLog.postValue(detectMsg);
            
            // Auto-enable TQL mode for time-based queries and process immediately
            setTQLMode(true);
            performTQLQuery(text);
            return;
        } else if (!isTQLMode && text != null && !text.trim().isEmpty()) {
            // Debug why it's not being detected as TQL
            boolean tqlResult = looksLikeTQLQuery(text);
            android.util.Log.d("SearchViewModel", "*** Query '" + text + "' NOT detected as TQL. Detection result: " + tqlResult + " ***");
            debugLog.postValue("NOT detected as TQL: '" + text + "' (result: " + tqlResult + ")");
        }
        
        if (isTQLMode) {
            android.util.Log.d("SearchViewModel", "Processing as TQL query");
            // Process as TQL query
            performTQLQuery(text);
        } else {
            android.util.Log.d("SearchViewModel", "Processing as normal search");
            // Process as normal search
            if (text != null && !text.isEmpty()) {
                performSearch();
            } else if (hasAnyFilter()) {
                applyAllFilters();
            } else {
                loadAllTransactions(null);
            }
        }
    }

    // Bank methods
    public void setBank(String value) {
        this.bank = value;
    }

    public String getBank() {
        return bank;
    }

    // Type methods
    public void setType(String value) {
        this.type = value;
    }

    public String getType() {
        return type;
    }

    // Category methods
    public void setCategory(String value) {
        this.category = value;
        applyAllFilters();
    }

    public String getCategory() {
        return category;
    }

    // Amount range methods
    public void setAmountRange(Double min, Double max) {
        this.minAmount = min;
        this.maxAmount = max;
    }

    // Date range methods
    public void setStartDate(Long value) {
        this.startDate = value;
    }

    public Long getStartDate() {
        return startDate;
    }

    public void setEndDate(Long value) {
        this.endDate = value;
    }

    public Long getEndDate() {
        return endDate;
    }

    // Excluded from total methods
    public void setExcludedFromTotal(Boolean value) {
        this.excludedFromTotal = value;
    }

    public Boolean getExcludedFromTotal() {
        return excludedFromTotal;
    }

    // Is recurring methods
    public void setIsRecurring(Boolean value) {
        this.isRecurring = value;
    }

    public Boolean getIsRecurring() {
        return isRecurring;
    }

    // Merchant name methods
    public void setMerchantName(String value) {
        this.merchantName = value;
    }

    public String getMerchantName() {
        return merchantName;
    }

    // Filter application methods
    public void applyAllFilters() {
        performSearch();
    }

    // Perform search with current filters
    public void performSearch() {
        isLoading.setValue(true);

        executorService.execute(() -> {
            try {
                TransactionSearchFilter filter = buildCurrentFilter();
                SupportSQLiteQuery query = filter.buildSearchQuery();

                List<Transaction> results = TransactionDatabase.getInstance(getApplication())
                        .transactionDao()
                        .searchTransactionsWithFilterSync(query);

                // Update UI on main thread
                isLoading.postValue(false);
                searchResults.postValue(results);
            } catch (Exception e) {
                isLoading.postValue(false);
                errorMessage.postValue("Search error: " + e.getMessage());
            }
        });
    }

    // Perform search with callback
    public void performSearch(SearchCallback callback) {
        isLoading.setValue(true);

        executorService.execute(() -> {
            try {
                TransactionSearchFilter filter = buildCurrentFilter();
                SupportSQLiteQuery query = filter.buildSearchQuery();

                List<Transaction> results = TransactionDatabase.getInstance(getApplication())
                        .transactionDao()
                        .searchTransactionsWithFilterSync(query);

                // Update UI on main thread
                isLoading.postValue(false);

                if (callback != null) {
                    callback.onSearchComplete(results);
                }

                searchResults.postValue(results);
            } catch (Exception e) {
                isLoading.postValue(false);

                if (callback != null) {
                    callback.onError(e.getMessage());
                }

                errorMessage.postValue("Search error: " + e.getMessage());
            }
        });
    }

    // Clear all filters
    public void clearAllFilters() {
        searchText = "";
        bank = null;
        type = null;
        category = null;
        minAmount = null;
        maxAmount = null;
        startDate = null;
        endDate = null;
        excludedFromTotal = null;
        isRecurring = null;
        
        // Also turn off TQL mode when clearing all filters
        setTQLMode(false);
        merchantName = null;

        loadAllTransactions(null);
    }

    // Check if any filter is active
    public boolean hasAnyFilter() {
        return (searchText != null && !searchText.isEmpty()) ||
                bank != null ||
                type != null ||
                category != null ||
                minAmount != null ||
                maxAmount != null ||
                startDate != null ||
                endDate != null ||
                excludedFromTotal != null ||
                isRecurring != null ||
                (merchantName != null && !merchantName.isEmpty());
    }

    // Helper Methods

    // Load all transactions
    public void loadAllTransactions(SearchCallback callback) {
        isLoading.setValue(true);

        executorService.execute(() -> {
            try {
                List<Transaction> allTransactions = TransactionDatabase.getInstance(getApplication())
                        .transactionDao()
                        .getAllTransactionsSync();

                isLoading.postValue(false);

                if (callback != null) {
                    callback.onSearchComplete(allTransactions);
                }

                searchResults.postValue(allTransactions);
            } catch (Exception e) {
                isLoading.postValue(false);

                if (callback != null) {
                    callback.onError(e.getMessage());
                }

                errorMessage.postValue("Error loading transactions: " + e.getMessage());
            }
        });
    }

    // Check if any filter except text search is active
    private boolean hasAnyFilterExceptText() {
        return bank != null ||
                type != null ||
                category != null ||
                minAmount != null ||
                maxAmount != null ||
                startDate != null ||
                endDate != null ||
                excludedFromTotal != null ||
                isRecurring != null ||
                (merchantName != null && !merchantName.isEmpty());
    }

    // Build a filter object from current parameters
    private TransactionSearchFilter buildCurrentFilter() {
        return new TransactionSearchFilter.Builder()
                .searchText(searchText)
                .bank(bank)
                .type(type)
                .category(category)
                .amountRange(minAmount, maxAmount)
                .dateRange(startDate, endDate)
                .excludedFromTotal(excludedFromTotal)
                .isRecurring(isRecurring)
                .merchantName(merchantName)
                .build();
    }

    // Return a list of available banks
    public List<String> getAvailableBanks() {
        return Arrays.asList("All Banks", "HDFC", "SBI", "ICICI", "AXIS", "OTHER");
    }

    // Callback for export operation
    public interface ExportCallback {
        void onTransactionsReady(List<Transaction> transactions);
        void onError(String message);
    }

    // TQL Methods
    
    /**
     * Process a TQL (Transaction Query Language) query
     */
    public void performTQLQuery(String queryText) {
        if (queryText == null || queryText.trim().isEmpty()) {
            tqlResults.setValue(null);
            debugLog.postValue("performTQLQuery: empty query");
            return;
        }
        
        debugLog.postValue("performTQLQuery started: " + queryText);
        isLoading.setValue(true);
        
        executorService.execute(() -> {
            try {
                // Parse the natural language query
                TQLQuery tqlQuery;
                
                // Check if query has time expressions - use dynamic parser for better support
                boolean hasTimeExpression = SimpleDynamicTQLParser.hasTimeExpression(queryText);
                android.util.Log.d("SearchViewModel", "Query: '" + queryText + "', has time expression: " + hasTimeExpression);
                debugLog.postValue("hasTimeExpression: " + hasTimeExpression);
                
                if (hasTimeExpression) {
                    android.util.Log.d("SearchViewModel", "Time expression detected, using SimpleDynamicTQLParser");
                    debugLog.postValue("✓ TIME EXPRESSION DETECTED - Using SimpleDynamicTQLParser");
                    tqlQuery = SimpleDynamicTQLParser.parse(queryText);
                } else {
                    // Use existing parser for non-time queries
                    android.util.Log.d("SearchViewModel", "No time expression detected, using standard TQLParser");
                    debugLog.postValue("Using standard TQLParser");
                    tqlQuery = TQLParser.parse(queryText);
                }
                
                android.util.Log.d("SearchViewModel", "Parsed TQL query: " + tqlQuery.toString());
                android.util.Log.d("SearchViewModel", "Query type: " + tqlQuery.getQueryType());
                android.util.Log.d("SearchViewModel", "Filters count: " + tqlQuery.getFilters().size());
                
                debugLog.postValue("Parsed query with " + tqlQuery.getFilters().size() + " filters");
                
                // Process the query
                TQLProcessor.TQLResult result = tqlProcessor.processTQLQuery(tqlQuery);
                android.util.Log.d("SearchViewModel", "TQL Result type: " + (result.isTransactionResult() ? "Transaction" : result.isAggregateResult() ? "Aggregate" : "Group"));
                
                if (result.isTransactionResult() && result.getTransactions() != null) {
                    android.util.Log.d("SearchViewModel", "Found " + result.getTransactions().size() + " transactions");
                    debugLog.postValue("TQL found " + result.getTransactions().size() + " transactions");
                } else if (result.isError()) {
                    debugLog.postValue("TQL error: " + result.getErrorMessage());
                } else {
                    debugLog.postValue("TQL result type: " + (result.isAggregateResult() ? "Aggregate" : "Group"));
                }
                
                // Update UI on main thread
                isLoading.postValue(false);
                tqlResults.postValue(result);
                
                // For transaction results, also update the regular search results for compatibility
                if (result.isTransactionResult() && result.getTransactions() != null) {
                    searchResults.postValue(result.getTransactions());
                }
                
            } catch (Exception e) {
                android.util.Log.e("SearchViewModel", "TQL Query error", e);
                debugLog.postValue("TQL Exception: " + e.getMessage());
                isLoading.postValue(false);
                errorMessage.postValue("TQL Query error: " + e.getMessage());
            }
        });
    }
    
    /**
     * Get TQL query suggestions based on partial input
     */
    public List<String> getTQLSuggestions(String partialQuery) {
        List<String> suggestions = new ArrayList<>();
        
        if (partialQuery == null || partialQuery.trim().isEmpty()) {
            // Return common query starters
            suggestions.add("transactions > 100");
            suggestions.add("count transactions");
            suggestions.add("total spent on food");
            suggestions.add("group by merchant");
            suggestions.add("transactions from swiggy");
            suggestions.add("average transaction amount");
            return suggestions;
        }
        
        String lower = partialQuery.toLowerCase().trim();
        
        // Amount-based suggestions
        if (lower.matches(".*\\d+.*") && !lower.contains("from") && !lower.contains("on")) {
            suggestions.add(partialQuery + " from swiggy");
            suggestions.add(partialQuery + " last month");
            suggestions.add(partialQuery + " on food");
        }
        
        // Merchant-based suggestions
        if (lower.contains("from") || lower.contains("swiggy") || lower.contains("zomato") || 
            lower.contains("amazon") || lower.contains("flipkart")) {
            if (!lower.contains(">") && !lower.contains("<")) {
                suggestions.add("transactions > 100 " + (lower.startsWith("transactions") ? "" : "transactions ") + partialQuery);
            }
            if (!lower.contains("last month") && !lower.contains("this month")) {
                suggestions.add(partialQuery + " last month");
            }
        }
        
        // Category-based suggestions
        if (lower.contains("food") || lower.contains("shopping") || lower.contains("on ")) {
            if (!lower.startsWith("total")) {
                suggestions.add("total spent " + partialQuery);
            }
            if (!lower.startsWith("count")) {
                suggestions.add("count " + partialQuery);
            }
        }
        
        // Aggregation starters
        if (lower.startsWith("total") || lower.startsWith("count") || lower.startsWith("average")) {
            if (!lower.contains("food") && !lower.contains("shopping")) {
                suggestions.add(partialQuery + " on food");
                suggestions.add(partialQuery + " on shopping");
            }
            if (!lower.contains("from")) {
                suggestions.add(partialQuery + " from hdfc");
                suggestions.add(partialQuery + " from swiggy");
            }
        }
        
        return suggestions;
    }
    
    /**
     * Check if the given text looks like a TQL query
     */
    public static boolean looksLikeTQLQuery(String text) {
        if (text == null || text.trim().isEmpty()) {
            android.util.Log.d("SearchViewModel", "looksLikeTQLQuery: empty text");
            return false;
        }
        
        String lower = text.toLowerCase().trim();
        android.util.Log.d("SearchViewModel", "looksLikeTQLQuery: checking '" + lower + "'");
        
        // Check for TQL keywords (including time expressions)
        boolean result = lower.contains(">") || lower.contains("<") || lower.contains("=") ||
               lower.startsWith("count") || lower.startsWith("total") || lower.startsWith("sum") ||
               lower.startsWith("average") || lower.startsWith("avg") || lower.startsWith("max") ||
               lower.startsWith("min") || lower.contains("group by") ||
               lower.contains("from ") || lower.contains("on ") || lower.contains("between") ||
               lower.contains("last month") || lower.contains("this month") || 
               lower.contains("last week") || lower.contains("this week") ||
               SimpleDynamicTQLParser.hasTimeExpression(text); // Include time expressions
        
        android.util.Log.d("SearchViewModel", "looksLikeTQLQuery result: " + result);
        return result;
    }
    
    /**
     * Debug method to test time filtering
     */
    public void debugTimeFiltering() {
        android.util.Log.d("SearchViewModel", "=== DEBUGGING TIME FILTERING ===");
        
        // Test regex patterns first
        TimeExpressionParser.testRegexPatterns();
        
        // Test time parsing
        TimeFilterTestHelper.testTimeQueries();
        TimeFilterTestHelper.testTransactionFiltering();
        TimeFilterTestHelper.testAfter6PM();
        
        // Test specific query that user reported
        TimeFilterTestHelper.debugCurrentTimeVsFilter("transactions after 6pm");
        TimeFilterTestHelper.debugTimeQuery("transactions after 6pm");
        
        // Test manual TQL query creation
        testManualTQLQuery();
        
        android.util.Log.d("SearchViewModel", "=== DEBUG COMPLETE ===");
    }
    
    /**
     * Test creating a TQL query manually to bypass parsing issues
     */
    private void testManualTQLQuery() {
        android.util.Log.d("SearchViewModel", "=== TESTING MANUAL TQL QUERY ===");
        
        try {
            // Create a simple time range for testing (6 PM to 11:59 PM today)
            java.util.Calendar cal = java.util.Calendar.getInstance();
            
            // 6 PM today
            cal.set(java.util.Calendar.HOUR_OF_DAY, 18);
            cal.set(java.util.Calendar.MINUTE, 0);
            cal.set(java.util.Calendar.SECOND, 0);
            cal.set(java.util.Calendar.MILLISECOND, 0);
            long startTime = cal.getTimeInMillis();
            
            // End of day
            cal.set(java.util.Calendar.HOUR_OF_DAY, 23);
            cal.set(java.util.Calendar.MINUTE, 59);
            cal.set(java.util.Calendar.SECOND, 59);
            cal.set(java.util.Calendar.MILLISECOND, 999);
            long endTime = cal.getTimeInMillis();
            
            android.util.Log.d("SearchViewModel", "Manual query time range: " + startTime + " to " + endTime);
            java.text.SimpleDateFormat formatter = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault());
            android.util.Log.d("SearchViewModel", "Time range: " + formatter.format(new java.util.Date(startTime)) + " to " + formatter.format(new java.util.Date(endTime)));
            
            // Create manual TQL query
            TQLQuery manualQuery = new TQLQuery.Builder()
                .originalQuery("manual test: after 6pm")
                .queryType(TQLQuery.QueryType.FILTER)
                .addBetweenFilter("date", startTime, endTime)
                .build();
            
            android.util.Log.d("SearchViewModel", "Created manual TQL query with " + manualQuery.getFilters().size() + " filters");
            
            // Process it
            TQLProcessor.TQLResult result = tqlProcessor.processTQLQuery(manualQuery);
            
            if (result.isTransactionResult()) {
                android.util.Log.d("SearchViewModel", "Manual query returned " + (result.getTransactions() != null ? result.getTransactions().size() : 0) + " transactions");
            } else {
                android.util.Log.d("SearchViewModel", "Manual query returned non-transaction result");
            }
            
        } catch (Exception e) {
            android.util.Log.e("SearchViewModel", "Manual TQL query test failed", e);
        }
        
        android.util.Log.d("SearchViewModel", "=== MANUAL TQL QUERY TEST COMPLETE ===");
    }
    
    /**
     * Direct database test - bypass all parsing
     */
    public void testDirectDatabaseQuery() {
        android.util.Log.d("SearchViewModel", "=== DIRECT DATABASE TEST ===");
        
        executorService.execute(() -> {
            try {
                // Test 1: Get all transactions to see what we have
                android.util.Log.d("SearchViewModel", "Test 1: Getting all transactions");
                List<Transaction> allTransactions = TransactionDatabase.getInstance(getApplication())
                        .transactionDao()
                        .getAllTransactionsSync();
                
                android.util.Log.d("SearchViewModel", "Total transactions in database: " + allTransactions.size());
                
                if (!allTransactions.isEmpty()) {
                    // Show first few transactions with their times
                    java.text.SimpleDateFormat formatter = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault());
                    java.text.SimpleDateFormat timeFormatter = new java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault());
                    
                    for (int i = 0; i < Math.min(5, allTransactions.size()); i++) {
                        Transaction tx = allTransactions.get(i);
                        android.util.Log.d("SearchViewModel", "Transaction " + (i+1) + ": " + 
                                         formatter.format(new java.util.Date(tx.getDate())) + 
                                         " (" + timeFormatter.format(new java.util.Date(tx.getDate())) + ")" +
                                         " - ₹" + tx.getAmount() + " - " + tx.getMerchantName());
                    }
                }
                
                // Test 2: Create a simple time filter manually
                android.util.Log.d("SearchViewModel", "Test 2: Manual time filter test");
                
                java.util.Calendar cal = java.util.Calendar.getInstance();
                
                // Set to 6 PM today
                cal.set(java.util.Calendar.HOUR_OF_DAY, 18);
                cal.set(java.util.Calendar.MINUTE, 0);
                cal.set(java.util.Calendar.SECOND, 0);
                cal.set(java.util.Calendar.MILLISECOND, 0);
                long startTime = cal.getTimeInMillis();
                
                // Set to end of day
                cal.set(java.util.Calendar.HOUR_OF_DAY, 23);
                cal.set(java.util.Calendar.MINUTE, 59);
                cal.set(java.util.Calendar.SECOND, 59);
                cal.set(java.util.Calendar.MILLISECOND, 999);
                long endTime = cal.getTimeInMillis();
                
                android.util.Log.d("SearchViewModel", "Time filter range:");
                java.text.SimpleDateFormat formatter = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault());
                android.util.Log.d("SearchViewModel", "  Start: " + startTime + " (" + formatter.format(new java.util.Date(startTime)) + ")");
                android.util.Log.d("SearchViewModel", "  End: " + endTime + " (" + formatter.format(new java.util.Date(endTime)) + ")");
                
                // Test 3: Use TransactionSearchFilter directly
                TransactionSearchFilter timeFilter = new TransactionSearchFilter.Builder()
                        .dateRange(startTime, endTime)
                        .build();
                
                androidx.sqlite.db.SupportSQLiteQuery query = timeFilter.buildSearchQuery();
                
                List<Transaction> filteredTransactions = TransactionDatabase.getInstance(getApplication())
                        .transactionDao()
                        .searchTransactionsWithFilterSync(query);
                
                android.util.Log.d("SearchViewModel", "Filtered transactions: " + filteredTransactions.size());
                
                if (!filteredTransactions.isEmpty()) {
                    android.util.Log.d("SearchViewModel", "Sample filtered transactions:");
                    java.text.SimpleDateFormat timeFormatter = new java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault());
                    for (int i = 0; i < Math.min(3, filteredTransactions.size()); i++) {
                        Transaction tx = filteredTransactions.get(i);
                        android.util.Log.d("SearchViewModel", "  " + timeFormatter.format(new java.util.Date(tx.getDate())) + 
                                         " - ₹" + tx.getAmount() + " - " + tx.getMerchantName());
                    }
                } else {
                    android.util.Log.d("SearchViewModel", "No transactions found in time range!");
                    
                    // Test 4: Check if ANY transactions fall within today
                    android.util.Log.d("SearchViewModel", "Test 4: Checking for transactions today");
                    
                    cal = java.util.Calendar.getInstance();
                    cal.set(java.util.Calendar.HOUR_OF_DAY, 0);
                    cal.set(java.util.Calendar.MINUTE, 0);
                    cal.set(java.util.Calendar.SECOND, 0);
                    cal.set(java.util.Calendar.MILLISECOND, 0);
                    long todayStart = cal.getTimeInMillis();
                    
                    cal.set(java.util.Calendar.HOUR_OF_DAY, 23);
                    cal.set(java.util.Calendar.MINUTE, 59);
                    cal.set(java.util.Calendar.SECOND, 59);
                    cal.set(java.util.Calendar.MILLISECOND, 999);
                    long todayEnd = cal.getTimeInMillis();
                    
                    TransactionSearchFilter todayFilter = new TransactionSearchFilter.Builder()
                            .dateRange(todayStart, todayEnd)
                            .build();
                    
                    List<Transaction> todayTransactions = TransactionDatabase.getInstance(getApplication())
                            .transactionDao()
                            .searchTransactionsWithFilterSync(todayFilter.buildSearchQuery());
                    
                    android.util.Log.d("SearchViewModel", "Transactions today: " + todayTransactions.size());
                }
                
                debugLog.postValue("Direct DB test complete - check logs");
                
            } catch (Exception e) {
                android.util.Log.e("SearchViewModel", "Direct database test failed", e);
                debugLog.postValue("Direct DB test failed: " + e.getMessage());
            }
        });
        
        android.util.Log.d("SearchViewModel", "=== DIRECT DATABASE TEST COMPLETE ===");
    }
    
    /**
     * Test the exact time-of-day SQL query being generated
     */
    public void testTimeOfDaySQL() {
        android.util.Log.d("SearchViewModel", "=== TESTING TIME-OF-DAY SQL QUERY ===");
        
        executorService.execute(() -> {
            try {
                // Create a time-of-day filter manually (after 6pm = after 18:00)
                TransactionSearchFilter timeFilter = new TransactionSearchFilter.Builder()
                        .timeOfDayFilter("after", 18, 0)
                        .build();
                
                // Build the query and log it
                androidx.sqlite.db.SupportSQLiteQuery query = timeFilter.buildSearchQuery();
                android.util.Log.d("SearchViewModel", "Generated SQL: " + query.getSql());
                android.util.Log.d("SearchViewModel", "Query args count: " + query.getArgCount());
                
                // Test with a sample transaction timestamp
                java.util.Calendar cal = java.util.Calendar.getInstance();
                cal.set(2024, 0, 15, 19, 30, 0); // Jan 15, 2024 at 7:30 PM
                long sampleTimestamp = cal.getTimeInMillis();
                
                android.util.Log.d("SearchViewModel", "Testing with sample timestamp: " + sampleTimestamp);
                android.util.Log.d("SearchViewModel", "Sample time: " + 
                    new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(new java.util.Date(sampleTimestamp)));
                
                // Test the SQLite functions manually
                String testSQL = "SELECT " +
                    "strftime('%H', datetime(" + sampleTimestamp + "/1000, 'unixepoch', 'localtime')) as hour, " +
                    "strftime('%M', datetime(" + sampleTimestamp + "/1000, 'unixepoch', 'localtime')) as minute, " +
                    "(CAST(strftime('%H', datetime(" + sampleTimestamp + "/1000, 'unixepoch', 'localtime')) AS INTEGER) * 60 + " +
                    "CAST(strftime('%M', datetime(" + sampleTimestamp + "/1000, 'unixepoch', 'localtime')) AS INTEGER)) as total_minutes";
                
                android.util.Log.d("SearchViewModel", "Test SQL: " + testSQL);
                
                // Execute the actual query
                List<Transaction> results = TransactionDatabase.getInstance(getApplication())
                        .transactionDao()
                        .searchTransactionsWithFilterSync(query);
                
                android.util.Log.d("SearchViewModel", "Time-of-day filter returned " + results.size() + " results");
                
                debugLog.postValue("Time-of-day SQL test complete - check logs");
                
            } catch (Exception e) {
                android.util.Log.e("SearchViewModel", "Time-of-day SQL test failed", e);
                debugLog.postValue("Time-of-day SQL test failed: " + e.getMessage());
            }
        });
        
        android.util.Log.d("SearchViewModel", "=== TIME-OF-DAY SQL TEST COMPLETE ===");
    }
    
    /**
     * Show current state for debugging
     */
    public void debugCurrentState() {
        android.util.Log.d("SearchViewModel", "=== CURRENT STATE DEBUG ===");
        android.util.Log.d("SearchViewModel", "TQL Mode: " + isTQLMode);
        android.util.Log.d("SearchViewModel", "Search Text: '" + searchText + "'");
        android.util.Log.d("SearchViewModel", "Search Results: " + (searchResults.getValue() != null ? searchResults.getValue().size() + " transactions" : "null"));
        android.util.Log.d("SearchViewModel", "TQL Results: " + (tqlResults.getValue() != null ? "present" : "null"));
        android.util.Log.d("SearchViewModel", "=== STATE DEBUG COMPLETE ===");
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        executorService.shutdown();
    }
}