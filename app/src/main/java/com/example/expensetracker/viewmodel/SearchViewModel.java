package com.example.expensetracker.viewmodel;

import android.app.Application;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.MutableLiveData;
import androidx.sqlite.db.SupportSQLiteQuery;

import com.example.expensetracker.database.TransactionDao;
import com.example.expensetracker.database.TransactionDatabase;
import com.example.expensetracker.database.TransactionSearchFilter;
import com.example.expensetracker.models.Transaction;
import com.example.expensetracker.repository.TransactionRepository;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SearchViewModel extends AndroidViewModel {
    private final TransactionRepository repository;
    private final ExecutorService executorService;

    // Only use LiveData for UI updates
    private final MutableLiveData<List<Transaction>> searchResults = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();

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
        repository = new TransactionRepository(application);
        executorService = Executors.newSingleThreadExecutor();

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

    // Search text methods
    public void setSearchText(String text) {
        this.searchText = text;
        if (text != null && !text.isEmpty()) {
            performSearch();
        } else if (hasAnyFilter()) {
            applyAllFilters();
        } else {
            loadAllTransactions(null);
        }
    }

    public String getSearchText() {
        return searchText;
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

    public Double getMinAmount() {
        return minAmount;
    }

    public Double getMaxAmount() {
        return maxAmount;
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

    // Method to search by text in non-UI thread
    public List<Transaction> searchByTextSync(String query) {
        try {
            TransactionDao dao = TransactionDatabase.getInstance(getApplication()).transactionDao();

            // If we have other filters, build a complex query
            if (hasAnyFilterExceptText()) {
                TransactionSearchFilter filter = new TransactionSearchFilter.Builder()
                        .searchText(query)
                        .bank(bank)
                        .type(type)
                        .category(category)
                        .amountRange(minAmount, maxAmount)
                        .dateRange(startDate, endDate)
                        .excludedFromTotal(excludedFromTotal)
                        .isRecurring(isRecurring)
                        .merchantName(merchantName)
                        .build();

                SupportSQLiteQuery sqliteQuery = filter.buildSearchQuery();
                return dao.searchTransactionsWithFilterSync(sqliteQuery);
            } else {
                // Otherwise just do a simple text search
                List<Transaction> transactions = dao.getAllTransactionsSync();

                List<Transaction> filteredList = new ArrayList<>();
                String lowerQuery = query.toLowerCase();

                for (Transaction transaction : transactions) {
                    if ((transaction.getDescription() != null &&
                            transaction.getDescription().toLowerCase().contains(lowerQuery)) ||
                            (transaction.getMerchantName() != null &&
                                    transaction.getMerchantName().toLowerCase().contains(lowerQuery)) ||
                            (transaction.getCategory() != null &&
                                    transaction.getCategory().toLowerCase().contains(lowerQuery)) ||
                            (transaction.getBank() != null &&
                                    transaction.getBank().toLowerCase().contains(lowerQuery)) ||
                            (transaction.getOriginalSms() != null &&
                                    transaction.getOriginalSms().toLowerCase().contains(lowerQuery))) {

                        filteredList.add(transaction);
                    }
                }

                return filteredList;
            }
        } catch (Exception e) {
            // Log error and return empty list
            return new ArrayList<>();
        }
    }

    // Export filtered transactions (background operation)
    public void exportFilteredTransactions(ExportCallback callback) {
        // Set loading state
        isLoading.setValue(true);

        executorService.execute(() -> {
            try {
                // Get current filtered results
                List<Transaction> transactions;

                if (hasAnyFilter()) {
                    // Use current filters
                    TransactionSearchFilter filter = buildCurrentFilter();
                    SupportSQLiteQuery query = filter.buildSearchQuery();

                    transactions = TransactionDatabase.getInstance(getApplication())
                            .transactionDao()
                            .searchTransactionsWithFilterSync(query);
                } else {
                    // Get all transactions
                    transactions = TransactionDatabase.getInstance(getApplication())
                            .transactionDao()
                            .getAllTransactionsSync();
                }

                // Call export callback with the transactions
                isLoading.postValue(false);
                if (callback != null) {
                    callback.onTransactionsReady(transactions);
                }
            } catch (Exception e) {
                isLoading.postValue(false);
                if (callback != null) {
                    callback.onError("Export error: " + e.getMessage());
                }
            }
        });
    }

    // Callback for export operation
    public interface ExportCallback {
        void onTransactionsReady(List<Transaction> transactions);
        void onError(String message);
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        executorService.shutdown();
    }
}