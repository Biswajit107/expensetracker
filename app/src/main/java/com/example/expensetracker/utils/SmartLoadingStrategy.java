package com.example.expensetracker.utils;

import android.content.Context;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.example.expensetracker.MainActivity;
import com.example.expensetracker.R;
import com.example.expensetracker.adapters.DateGroupedTransactionAdapter;
import com.example.expensetracker.adapters.TransactionAdapter;
import com.example.expensetracker.database.TransactionDao;
import com.example.expensetracker.database.TransactionDatabase;
import com.example.expensetracker.models.Transaction;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;

/**
 * Smart loading strategy for transactions that adapts between individual pagination
 * and date-grouped views based on data volume
 */
public class SmartLoadingStrategy {
    private static final String TAG = "SmartLoadingStrategy";

    // Threshold for switching to grouped view
    private static final int GROUPED_VIEW_THRESHOLD = 100;

    // Current state
    private boolean isGroupedViewActive = false;
    private boolean isLoading = false;
    private boolean forceGroupedView = false;
    private final Context context;
    private final ExecutorService executorService;
    private final RecyclerView recyclerView;
    private final TransactionAdapter transactionAdapter;
    private final DateGroupedTransactionAdapter groupedAdapter;
    private final TextView emptyStateText;
    private final View loadingIndicator;
    private FilterState currentFilterState;
    private int groupingMode = 0; // 0 = Day, 1 = Week, 2 = Month, 3 = Category, 4 = Merchant, 5 = Amount Range, 6 = Bank

    // Interfaces for loading callbacks
    public interface TransactionLoadCallback {
        void onTransactionsLoaded(List<Transaction> transactions);
    }

    /**
     * Constructor with required components and internal FilterState
     */
    public SmartLoadingStrategy(Context context, ExecutorService executorService,
                                RecyclerView recyclerView, TransactionAdapter transactionAdapter,
                                TextView emptyStateText, View loadingIndicator,
                                FilterState currentFilterState) {
        this.context = context;
        this.executorService = executorService;
        this.recyclerView = recyclerView;
        this.transactionAdapter = transactionAdapter;
        this.emptyStateText = emptyStateText;
        this.loadingIndicator = loadingIndicator;
        this.currentFilterState = currentFilterState;

        // Create grouped adapter
        this.groupedAdapter = new DateGroupedTransactionAdapter(context);

        // Pass through click listener
        TransactionAdapter.OnTransactionClickListener clickListener = transactionAdapter.getOnTransactionClickListener();
        if (clickListener != null) {
            groupedAdapter.setOnTransactionClickListener(clickListener);
        }
    }

    /**
     * Constructor with MainActivity's FilterState
     */
    public SmartLoadingStrategy(Context context, ExecutorService executorService,
                                RecyclerView recyclerView, TransactionAdapter transactionAdapter,
                                TextView emptyStateText, View loadingIndicator,
                                MainActivity.FilterState mainFilterState) {
        this.context = context;
        this.executorService = executorService;
        this.recyclerView = recyclerView;
        this.transactionAdapter = transactionAdapter;
        this.emptyStateText = emptyStateText;
        this.loadingIndicator = loadingIndicator;

        // Convert MainActivity's FilterState to our internal FilterState
        this.currentFilterState = convertFilterState(mainFilterState);

        // Create grouped adapter
        this.groupedAdapter = new DateGroupedTransactionAdapter(context);

        // Pass through click listener
        TransactionAdapter.OnTransactionClickListener clickListener = transactionAdapter.getOnTransactionClickListener();
        if (clickListener != null) {
            groupedAdapter.setOnTransactionClickListener(clickListener);
        }
    }

    /**
     * Convert MainActivity's FilterState to our internal FilterState
     */
    private FilterState convertFilterState(MainActivity.FilterState mainFilterState) {
        FilterState filterState = new FilterState();
        filterState.bank = mainFilterState.bank;
        filterState.type = mainFilterState.type;
        filterState.category = mainFilterState.category;
        filterState.searchQuery = mainFilterState.searchQuery;
        filterState.minAmount = mainFilterState.minAmount;
        filterState.maxAmount = mainFilterState.maxAmount;
        filterState.showingExcluded = mainFilterState.showingExcluded;
        filterState.sortOption = mainFilterState.sortOption;
        filterState.viewingManuallyExcluded = mainFilterState.viewingManuallyExcluded;
        return filterState;
    }

    /**
     * Update filter state from MainActivity
     */
    public void updateFilterState(MainActivity.FilterState mainFilterState) {
        this.currentFilterState = convertFilterState(mainFilterState);
    }

    /**
     * Set transaction click listener for both adapters
     */
    public void setOnTransactionClickListener(TransactionAdapter.OnTransactionClickListener listener) {
        transactionAdapter.setOnTransactionClickListener(listener);
        groupedAdapter.setOnTransactionClickListener(listener);
    }

    /**
     * Setup swipe-to-exclude functionality on the current RecyclerView
     * This needs to be called whenever the adapter changes
     *
     * @param context     The activity context
     * @param listener    The SwipeToExclude action listener
     */
    public void setupSwipeToExclude(Context context, SwipeToExcludeCallback.SwipeActionListener listener) {
        if (recyclerView == null) return;

        // Create the SwipeToExcludeCallback
        SwipeToExcludeCallback swipeCallback;

        if (isGroupedViewActive) {
            // For grouped view, create a special swipe handler for the nested RecyclerViews
            setupGroupedSwipeToExclude(context, listener);
        } else {
            // For regular list view, attach the swipe handler to the main RecyclerView
            swipeCallback = new SwipeToExcludeCallback(context, transactionAdapter, listener);
            ItemTouchHelper itemTouchHelper = new ItemTouchHelper(swipeCallback);
            itemTouchHelper.attachToRecyclerView(recyclerView);
        }
    }

    /**
     * Special handling for setting up swipe in grouped view
     * We need to find all nested RecyclerViews and attach the swipe handler to each
     */
    private void setupGroupedSwipeToExclude(Context context, SwipeToExcludeCallback.SwipeActionListener listener) {
        if (recyclerView == null || groupedAdapter == null) return;

        // We need to find the nested RecyclerViews inside each group
        recyclerView.addOnChildAttachStateChangeListener(new RecyclerView.OnChildAttachStateChangeListener() {
            @Override
            public void onChildViewAttachedToWindow(@NonNull View view) {
                // Look for the nestedTransactionList RecyclerView in each group item
                RecyclerView nestedList = view.findViewById(R.id.nestedTransactionList);
                if (nestedList != null) {
                    // Get the adapter
                    RecyclerView.Adapter<?> nestedAdapter = nestedList.getAdapter();
                    if (nestedAdapter instanceof TransactionAdapter) {
                        // Attach swipe handler to this nested list
                        SwipeToExcludeCallback swipeCallback = new SwipeToExcludeCallback(
                                context,
                                (TransactionAdapter) nestedAdapter,
                                listener
                        );
                        ItemTouchHelper itemTouchHelper = new ItemTouchHelper(swipeCallback);
                        itemTouchHelper.attachToRecyclerView(nestedList);
                    }
                }
            }

            @Override
            public void onChildViewDetachedFromWindow(@NonNull View view) {
                // Nothing needed here
            }
        });
    }

    /**
     * Recursively find and setup swipe handlers on nested RecyclerViews
     */
    private void findAndSetupNestedRecyclerViews(ViewGroup viewGroup, Context context,
                                                 SwipeToExcludeCallback.SwipeActionListener listener) {
        for (int i = 0; i < viewGroup.getChildCount(); i++) {
            View child = viewGroup.getChildAt(i);

            if (child instanceof RecyclerView) {
                // Found a nested RecyclerView, set up swipe on it
                RecyclerView nestedRecyclerView = (RecyclerView) child;
                RecyclerView.Adapter<?> nestedAdapter = nestedRecyclerView.getAdapter();

                if (nestedAdapter instanceof TransactionAdapter) {
                    SwipeToExcludeCallback swipeCallback = new SwipeToExcludeCallback(
                            context,
                            (TransactionAdapter) nestedAdapter,
                            listener
                    );
                    ItemTouchHelper itemTouchHelper = new ItemTouchHelper(swipeCallback);
                    itemTouchHelper.attachToRecyclerView(nestedRecyclerView);
                }
            } else if (child instanceof ViewGroup) {
                // Recursive search for deeper nested RecyclerViews
                findAndSetupNestedRecyclerViews((ViewGroup) child, context, listener);
            }
        }
    }

    /**
     * Force a specific view mode
     *
     * @param forceGroupedView true to force grouped view, false to use automatic detection
     */
    public void setForceViewMode(boolean forceGroupedView) {
        // Only process if we're actually changing the mode
        if (this.forceGroupedView == forceGroupedView &&
                isGroupedViewActive == forceGroupedView) {
            return; // No change needed
        }

        this.forceGroupedView = forceGroupedView;

        // Log the change
        Log.d(TAG, "Manually changing view mode to " + (forceGroupedView ? "grouped" : "list"));

        // If we're changing the view mode, we need to update the adapter and reload data
        if (forceGroupedView) {
            // Switch to grouped view
            isGroupedViewActive = true;
            updateAdapterInMainThread(groupedAdapter);

            // We need to get the current date range from the context, if possible
            if (context instanceof MainActivity) {
                MainActivity activity = (MainActivity) context;
                // Get the date range from activity
                long fromDate = activity.getFromDate();
                long toDate = activity.getToDate();

                // Reload data in grouped mode
                loadGroupedTransactions(fromDate, toDate);
            } else {
                // Fallback to loading all data
                loadGroupedTransactions(0, System.currentTimeMillis());
            }
        } else {
            // Switch to list view
            isGroupedViewActive = false;
            updateAdapterInMainThread(transactionAdapter);

            // Clear the adapter to avoid showing stale data during loading
            if (context instanceof android.app.Activity) {
                ((android.app.Activity) context).runOnUiThread(() -> {
                    transactionAdapter.clearTransactions();
                });
            }

            // We need to get the current date range from the context, if possible
            if (context instanceof MainActivity) {
                MainActivity activity = (MainActivity) context;
                // Get the date range from activity
                long fromDate = activity.getFromDate();
                long toDate = activity.getToDate();

                // Reload data in list mode
                loadPaginatedTransactions(fromDate, toDate);
            } else {
                // Fallback to loading all data
                loadPaginatedTransactions(0, System.currentTimeMillis());
            }
        }
    }

    /**
     * Load transactions for the given date range, choosing appropriate strategy
     */
    public void loadTransactionsForDateRange(long fromDate, long toDate) {
        if (isLoading) return;
        isLoading = true;

        if (loadingIndicator != null) {
            loadingIndicator.setVisibility(View.VISIBLE);
        }

        executorService.execute(() -> {
            try {
                // First, check if we have a forced view mode
                if (forceGroupedView) {
                    Log.d(TAG, "Using grouped view (manually selected by user)");
                    loadGroupedTransactions(fromDate, toDate);
                    return;
                }

                // Otherwise, count transactions to determine strategy
                TransactionDao dao = TransactionDatabase.getInstance(context).transactionDao();
                int count = dao.getTransactionCountBetweenDates(fromDate, toDate);

                Log.d(TAG, "Found " + count + " transactions in date range");

                if (count > GROUPED_VIEW_THRESHOLD && !currentFilterState.viewingManuallyExcluded) {
                    // Use grouped view for large data sets
                    Log.d(TAG, "Automatically switching to grouped view due to large data volume (" + count + " transactions)");

                    // Show toast message on main thread to inform user
                    if (context instanceof android.app.Activity) {
                        ((android.app.Activity) context).runOnUiThread(() -> {
                            Toast.makeText(context,
                                    "Switched to grouped view for better performance (" + count + " transactions)",
                                    Toast.LENGTH_SHORT).show();
                        });
                    }

                    loadGroupedTransactions(fromDate, toDate);
                } else {
                    // Use regular pagination for smaller data sets
                    loadPaginatedTransactions(fromDate, toDate);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error counting transactions", e);

                // Fallback to pagination on error
                loadPaginatedTransactions(fromDate, toDate);
            }
        });
    }

    /**
     * Load transactions in grouped view
     */
    private void loadGroupedTransactions(long fromDate, long toDate) {
        Log.d(TAG, "Using grouped transaction view with mode: " + groupingMode);
        executorService.execute(() -> {
            try {
                TransactionDao dao = TransactionDatabase.getInstance(context).transactionDao();

                // Load all transactions for the date range
                List<Transaction> transactions;

                if (currentFilterState.viewingManuallyExcluded) {
                    transactions = dao.getManuallyExcludedTransactionsBetweenDatesSync(fromDate, toDate);
                } else {
                    transactions = dao.getNonExcludedTransactionsBetweenDatesSync(fromDate, toDate);
                }

                // Apply any additional filters if needed
                if (currentFilterState != null && currentFilterState.isAnyFilterActive()) {
                    transactions = currentFilterState.applyFilters(transactions);
                }

                // Switch to the grouped adapter if not already
                if (!isGroupedViewActive) {
                    isGroupedViewActive = true;
                    updateAdapterInMainThread(groupedAdapter);
                }

                // Update UI in main thread
                if (context instanceof android.app.Activity) {
                    List<Transaction> finalTransactions = transactions; // For lambda
                    ((android.app.Activity) context).runOnUiThread(() -> {
                        if (loadingIndicator != null) {
                            loadingIndicator.setVisibility(View.GONE);
                        }

                        if (finalTransactions != null && !finalTransactions.isEmpty()) {
                            // Group transactions and update adapter with the grouping mode
                            groupedAdapter.setTransactions(finalTransactions, groupingMode, currentFilterState.sortOption);

                            // Update summary in MainActivity
                            if (context instanceof MainActivity) {
                                // Get budget value from MainActivity or PreferencesManager
                                PreferencesManager preferencesManager = new PreferencesManager(context);
                                double budget = preferencesManager.getBudgetAmount(0.0);
                                ((MainActivity) context).updateSummaryWithBudget(finalTransactions, budget);
                            }

                            // Hide empty state
                            if (emptyStateText != null) {
                                emptyStateText.setVisibility(View.GONE);
                            }

                            // Update transaction count indicators if filters are active
                            updateFilterIndicator(finalTransactions);
                        } else {
                            // Show empty state
                            if (emptyStateText != null) {
                                emptyStateText.setVisibility(View.VISIBLE);
                            }

                            // Clear the adapter
                            groupedAdapter.setTransactions(new ArrayList<>(), groupingMode, currentFilterState.sortOption);
                        }

                        isLoading = false;
                    });
                }
            } catch (Exception e) {
                Log.e(TAG, "Error loading grouped transactions", e);

                // Fallback to pagination on error
                loadPaginatedTransactions(fromDate, toDate);
            }
        });
    }

    /**
     * Load transactions with pagination
     */
    private void loadPaginatedTransactions(long fromDate, long toDate) {
        Log.d(TAG, "Using paginated transaction view");

        // Switch to the regular adapter if not already
        if (isGroupedViewActive) {
            isGroupedViewActive = false;
            updateAdapterInMainThread(transactionAdapter);
        }

        // Use the regular pagination loading logic
        executorService.execute(() -> {
            try {
                TransactionDao dao = TransactionDatabase.getInstance(context).transactionDao();

                // Get all transactions for the given date range, not just the first page
                List<Transaction> transactions;

                if (currentFilterState.viewingManuallyExcluded) {
                    transactions = dao.getManuallyExcludedTransactionsBetweenDatesSync(fromDate, toDate);
                } else {
                    transactions = dao.getNonExcludedTransactionsBetweenDatesSync(fromDate, toDate);
                }

                // Apply any additional filters if needed
                if (currentFilterState != null && currentFilterState.isAnyFilterActive() && transactions != null) {
                    transactions = currentFilterState.applyFilters(transactions);
                }

                // Update UI in main thread
                if (context instanceof android.app.Activity) {
                    List<Transaction> finalTransactions = transactions; // Need final for lambda
                    ((android.app.Activity) context).runOnUiThread(() -> {
                        if (loadingIndicator != null) {
                            loadingIndicator.setVisibility(View.GONE);
                        }

                        if (finalTransactions != null && !finalTransactions.isEmpty()) {
                            // Update adapter
                            transactionAdapter.setTransactions(finalTransactions);

                            // Update summary in MainActivity
                            if (context instanceof MainActivity) {
                                // Get budget value from MainActivity or PreferencesManager
                                PreferencesManager preferencesManager = new PreferencesManager(context);
                                double budget = preferencesManager.getBudgetAmount(0.0);
                                ((MainActivity) context).updateSummaryWithBudget(finalTransactions, budget);
                            }

                            // Hide empty state
                            if (emptyStateText != null) {
                                emptyStateText.setVisibility(View.GONE);
                            }

                            // Update transaction count indicators if filters are active
                            updateFilterIndicator(finalTransactions);
                        } else {
                            // Show empty state
                            if (emptyStateText != null) {
                                emptyStateText.setVisibility(View.VISIBLE);

                                // Clear adapter
                                transactionAdapter.clearTransactions();
                            }
                        }

                        isLoading = false;
                    });
                }
            } catch (Exception e) {
                Log.e(TAG, "Error loading paginated transactions", e);

                // Handle error in main thread
                if (context instanceof android.app.Activity) {
                    ((android.app.Activity) context).runOnUiThread(() -> {
                        if (loadingIndicator != null) {
                            loadingIndicator.setVisibility(View.GONE);
                        }

                        Toast.makeText(context, "Error loading transactions", Toast.LENGTH_SHORT).show();
                        isLoading = false;
                    });
                }
            }
        });
    }

    // Update filter indicator in MainActivity
    // Update in SmartLoadingStrategy.java
    private void updateFilterIndicator(List<Transaction> transactions) {
        if (context instanceof MainActivity) {
            MainActivity activity = (MainActivity) context;
            View filterContainer = activity.findViewById(R.id.filterIndicatorContainer);
            TextView filterText = activity.findViewById(R.id.filterIndicator);
            TextView countText = activity.findViewById(R.id.resultCount);

            if (filterContainer != null && filterText != null) {
                // Build filter description
                StringBuilder desc = new StringBuilder();
                boolean hasInfo = false;

                // Add manually excluded filter text
                if (currentFilterState.viewingManuallyExcluded) {
                    desc.append("Viewing: Manually Excluded Transactions");
                    hasInfo = true;
                }
                // Add regular filter info
                else if (currentFilterState.isAnyFilterActive()) {
                    desc.append("Filtered by: ");
                    boolean hasFilter = false;

                    if (!"All Banks".equals(currentFilterState.bank)) {
                        desc.append(currentFilterState.bank);
                        hasFilter = true;
                    }

                    if (!"All Types".equals(currentFilterState.type)) {
                        if (hasFilter) desc.append(", ");
                        desc.append(currentFilterState.type);
                        hasFilter = true;
                    }

                    if (currentFilterState.category != null && !currentFilterState.category.isEmpty()) {
                        if (hasFilter) desc.append(", ");
                        desc.append(currentFilterState.category);
                        hasFilter = true;
                    }

                    if (!currentFilterState.searchQuery.isEmpty()) {
                        if (hasFilter) desc.append(", ");
                        desc.append("Search: ").append(currentFilterState.searchQuery);
                        hasFilter = true;
                    }

                    if (hasFilter) {
                        hasInfo = true;
                    }
                }

                // Add sort information if not default sort
                if (currentFilterState.sortOption != 0) {
                    String sortText = "Sorted by: ";
                    switch (currentFilterState.sortOption) {
                        case 0:
                            sortText += "Date (newest first)";
                            break;
                        case 1:
                            sortText += "Date (oldest first)";
                            break;
                        case 2:
                            sortText += "Amount (highest first)";
                            break;
                        case 3:
                            sortText += "Amount (lowest first)";
                            break;
                        case 4:
                            sortText += "Description (A-Z)";
                            break;
                        case 5:
                            sortText += "Description (Z-A)";
                            break;
                    }

                    if (hasInfo) {
                        desc.append(", ").append(sortText);
                    } else {
                        desc.append(sortText);
                        hasInfo = true;
                    }
                }

                // Show or hide the filter container based on whether we have info to display
                if (hasInfo) {
                    filterContainer.setVisibility(View.VISIBLE);
                    filterText.setText(desc.toString());

                    if (countText != null && transactions != null) {
                        countText.setText(String.format(Locale.getDefault(),
                                "%d transaction(s) found", transactions.size()));
                    }
                } else {
                    filterContainer.setVisibility(View.GONE);
                }
            }
        }
    }

    /**
     * Helper to update adapter in main thread
     */
    private void updateAdapterInMainThread(RecyclerView.Adapter<?> adapter) {
        if (context instanceof android.app.Activity) {
            ((android.app.Activity) context).runOnUiThread(() -> {
                recyclerView.setAdapter(adapter);

                // Re-setup swipe handlers if this is an Activity context
                if (context instanceof MainActivity) {
                    MainActivity activity = (MainActivity) context;
                    setupSwipeToExclude(context, transaction -> {
                        activity.excludeTransactionManually(transaction);
                    });
                }
            });
        }
    }

    /**
     * Check if currently in grouped view mode
     */
    public boolean isGroupedViewActive() {
        return isGroupedViewActive;
    }

    /**
     * Update a transaction in the appropriate adapter
     * Call this from MainActivity when a transaction is edited
     */
    public void updateTransactionInAdapters(Transaction editedTransaction) {
        if (context instanceof android.app.Activity) {
            ((android.app.Activity) context).runOnUiThread(() -> {
                // Update in regular adapter
                List<Transaction> regularList = transactionAdapter.getTransactions();
                for (int i = 0; i < regularList.size(); i++) {
                    if (regularList.get(i).getId() == editedTransaction.getId()) {
                        regularList.set(i, editedTransaction);
                        transactionAdapter.notifyItemChanged(i);
                        break;
                    }
                }

                // If grouped view is active, reload grouped data
                // This is simpler than trying to update the nested structure
                if (isGroupedViewActive) {
                    if (currentFilterState.viewingManuallyExcluded) {
                        loadGroupedTransactions(0, System.currentTimeMillis()); // Reload all
                    } else {
                        // Just reload the current group
                        groupedAdapter.updateTransaction(editedTransaction);
                    }
                }
            });
        }
    }

    /**
     * Refresh the data completely (used after filtering)
     */
    public void refreshData(long fromDate, long toDate) {
        // Clear the loading state
        isLoading = false;

        // Load transactions with the current view mode
        loadTransactionsForDateRange(fromDate, toDate);
    }

    /**
     * Set the grouping mode for transaction groups
     * @param mode The grouping mode (0 = Day, 1 = Week, 2 = Month, 3 = Category, 4 = Merchant, 5 = Amount Range, 6 = Bank)
     */
    public void setGroupingMode(int mode) {
        if (this.groupingMode != mode) {
            this.groupingMode = mode;

            // If we're in grouped view, reload with new grouping
            if (isGroupedViewActive) {
                // If context is MainActivity, get the date range
                if (context instanceof MainActivity) {
                    MainActivity activity = (MainActivity) context;
                    long fromDate = activity.getFromDate();
                    long toDate = activity.getToDate();

                    // Reload grouped data with new grouping mode
                    loadGroupedTransactions(fromDate, toDate);
                }
            }
        }
    }

    /**
     * Get the current grouping mode
     * @return The grouping mode
     */
    public int getGroupingMode() {
        return groupingMode;
    }

    /**
     * Filter helper class
     */
    public static class FilterState {
        public String bank = "All Banks";
        public String type = "All Types";
        public String category = null;
        public String searchQuery = "";
        public double minAmount = 0;
        public double maxAmount = 100000;
        public boolean showingExcluded = false;
        public int sortOption = 0; // 0 = Date (newest first)
        public boolean viewingManuallyExcluded = false;

        // Method to check if any filter is active
        public boolean isAnyFilterActive() {
            return !bank.equals("All Banks") ||
                    !type.equals("All Types") ||
                    category != null ||
                    !searchQuery.isEmpty() ||
                    minAmount > 0 ||
                    maxAmount < 100000 ||
                    showingExcluded;
        }

        // Apply filters to a list of transactions
        public List<Transaction> applyFilters(List<Transaction> transactions) {
            if (transactions == null) return new ArrayList<>();

            List<Transaction> filtered = new ArrayList<>();

            for (Transaction transaction : transactions) {
                boolean include = true;

                // Apply bank filter
                if (!bank.equals("All Banks") && !bank.equals(transaction.getBank())) {
                    include = false;
                }

                // Apply type filter
                if (include && !type.equals("All Types") && !type.equals(transaction.getType())) {
                    include = false;
                }

                // Apply category filter
                if (include && category != null && !category.isEmpty() &&
                        !category.equals(transaction.getCategory())) {
                    include = false;
                }

                // Apply amount filter
                if (include && (transaction.getAmount() < minAmount ||
                        transaction.getAmount() > maxAmount)) {
                    include = false;
                }

                // Apply search filter
                if (include && !searchQuery.isEmpty()) {
                    boolean matchesSearch = false;
                    String query = searchQuery.toLowerCase();

                    // Check description
                    if (transaction.getDescription() != null &&
                            transaction.getDescription().toLowerCase().contains(query)) {
                        matchesSearch = true;
                    }

                    // Check bank
                    if (!matchesSearch && transaction.getBank() != null &&
                            transaction.getBank().toLowerCase().contains(query)) {
                        matchesSearch = true;
                    }

                    // Check category
                    if (!matchesSearch && transaction.getCategory() != null &&
                            transaction.getCategory().toLowerCase().contains(query)) {
                        matchesSearch = true;
                    }

                    // Check merchant
                    if (!matchesSearch && transaction.getMerchantName() != null &&
                            transaction.getMerchantName().toLowerCase().contains(query)) {
                        matchesSearch = true;
                    }

                    include = matchesSearch;
                }

                if (include) {
                    filtered.add(transaction);
                }
            }

            // Apply sort
            sortTransactions(filtered, sortOption);

            return filtered;
        }

        // Helper method to sort transactions
        private void sortTransactions(List<Transaction> transactions, int sortOption) {
            switch (sortOption) {
                case 0: // Date (newest first)
                    Collections.sort(transactions, (a, b) -> Long.compare(b.getDate(), a.getDate()));
                    break;
                case 1: // Date (oldest first)
                    Collections.sort(transactions, (a, b) -> Long.compare(a.getDate(), b.getDate()));
                    break;
                case 2: // Amount (highest first)
                    Collections.sort(transactions, (a, b) -> Double.compare(b.getAmount(), a.getAmount()));
                    break;
                case 3: // Amount (lowest first)
                    Collections.sort(transactions, (a, b) -> Double.compare(a.getAmount(), b.getAmount()));
                    break;
                case 4: // Description (A-Z)
                    Collections.sort(transactions, (a, b) -> {
                        String descA = a.getDescription() != null ? a.getDescription() : "";
                        String descB = b.getDescription() != null ? b.getDescription() : "";
                        return descA.compareToIgnoreCase(descB);
                    });
                    break;
                case 5: // Description (Z-A)
                    Collections.sort(transactions, (a, b) -> {
                        String descA = a.getDescription() != null ? a.getDescription() : "";
                        String descB = b.getDescription() != null ? b.getDescription() : "";
                        return descB.compareToIgnoreCase(descA);
                    });
                    break;
            }
        }
    }
}