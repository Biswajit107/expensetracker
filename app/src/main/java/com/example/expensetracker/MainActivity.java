package com.example.expensetracker;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import android.widget.PopupMenu;
import androidx.core.app.ActivityCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.Manifest;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Telephony;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.example.expensetracker.adapters.TransactionAdapter;
import com.example.expensetracker.database.TransactionDatabase;
import com.example.expensetracker.dialogs.TransactionEditDialog;
import com.example.expensetracker.models.Transaction;
import com.example.expensetracker.receivers.EnhancedSMSReceiver;
import com.example.expensetracker.utils.SmartLoadingStrategy;
import com.example.expensetracker.viewmodel.TransactionViewModel;
import com.example.expensetracker.utils.PreferencesManager;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.slider.RangeSlider;
import com.google.android.material.textfield.TextInputEditText;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    private static final int SMS_PERMISSION_REQUEST_CODE = 123;
    private static final String TAG = "MainActivity";

    private TransactionViewModel viewModel;
    private TransactionAdapter adapter;
    private TextView totalDebitsText;
    private TextView totalCreditsText;
    private TextView balanceText;
    private MaterialCardView alertCard;
    private TextView alertText;
    private ProgressBar loadingIndicator;
    private TextView emptyStateText;

    private View filterIndicatorContainer;
    private TextView filterIndicator;
    private Button clearFilterButton;
    private TextView resultCount;

    private long fromDate = 0;
    private long toDate = 0;
    private PreferencesManager preferencesManager;
    private ExecutorService executorService;

    private EditText searchInput;
    private Button sortButton;
    private Button filterButton;
    private List<Transaction> allTransactions = new ArrayList<>();

    private static final int PAGE_SIZE = 25;
    private int currentPage = 0;
    private boolean isLoading = false;
    private boolean hasMoreData = true;
    private FilterState currentFilterState = new FilterState();
    private SmartLoadingStrategy smartLoadingStrategy;
    private MaterialButton viewModeButton;
    private ViewMode currentViewMode = ViewMode.LIST; // Default to list mode

    private enum ViewMode {
        LIST,           // Individual transactions with pagination
        GROUP_BY_DAY,   // Grouped by day (current implementation)
        GROUP_BY_WEEK,  // Grouped by week
        GROUP_BY_MONTH  // Grouped by month
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize ExecutorService
        executorService = Executors.newSingleThreadExecutor();

        // Initialize basic views and setup
        preferencesManager = new PreferencesManager(this);
        initializeViews();
        setupRecyclerView();
        setupViewModel();
        setupDateRangeUI();
        setupDefaultDates();

        // Set up new components
        setupPagination();
        setupSpendingChart();
        setupDateRangeChips();
        setupCategoryFilter();
        setupBudgetFab();
        setupSearch();
        setupSort();

        // Check permissions and setup navigation
        checkAndRequestSMSPermissions();
        setupBottomNavigation();

        initializeSmartLoadingStrategy();

        boolean preferGroupedView = preferencesManager.getViewModePreference();
        int groupingMode = preferencesManager.getGroupingModePreference();

        if (preferGroupedView) {
            switch (groupingMode) {
                case 1:
                    currentViewMode = ViewMode.GROUP_BY_WEEK;
                    break;
                case 2:
                    currentViewMode = ViewMode.GROUP_BY_MONTH;
                    break;
                case 0:
                default:
                    currentViewMode = ViewMode.GROUP_BY_DAY;
                    break;
            }
        } else {
            currentViewMode = ViewMode.LIST;
        }

        updateViewModeButtonAppearance();

        // Apply user's preferred view mode to smart loading strategy
        if (smartLoadingStrategy != null) {
            smartLoadingStrategy.setGroupingMode(groupingMode);
            smartLoadingStrategy.setForceViewMode(preferGroupedView);
        }

    }

    private void initializeSmartLoadingStrategy() {
        // Get reference to RecyclerView
        RecyclerView recyclerView = findViewById(R.id.recyclerView);
        if (recyclerView == null) {
            Log.e(TAG, "RecyclerView not found when initializing SmartLoadingStrategy");
            return;
        }

        // Initialize the smart loading strategy
        smartLoadingStrategy = new SmartLoadingStrategy(
                this,
                executorService,
                recyclerView,
                adapter,
                emptyStateText,
                loadingIndicator,
                currentFilterState
        );

        // Set click listener for transaction editing
        smartLoadingStrategy.setOnTransactionClickListener(transaction -> {
            showEditTransactionDialog(transaction);
        });

//        // Load user's view mode preference
//        PreferencesManager preferencesManager = new PreferencesManager(this);
        boolean preferGroupedView = preferencesManager.getViewModePreference();
//        currentViewMode = preferGroupedView ? ViewMode.GROUP : ViewMode.LIST;
//
//        // Apply user's preferred view mode
//        smartLoadingStrategy.setForceViewMode(preferGroupedView);

        // Log the initial view mode
        Log.d(TAG, "Starting with " + (preferGroupedView ? "grouped" : "list") + " view based on user preference");
    }

    private void initializeViews() {
        // Find the views
        totalDebitsText = findViewById(R.id.totalDebitsText);
        totalCreditsText = findViewById(R.id.totalCreditsText);
        balanceText = findViewById(R.id.balanceText);
        alertCard = findViewById(R.id.alertCard);
        alertText = findViewById(R.id.alertText);
        loadingIndicator = findViewById(R.id.loadingIndicator);
        emptyStateText = findViewById(R.id.emptyStateText);

        // Find input and action elements
        searchInput = findViewById(R.id.searchInput);
        sortButton = findViewById(R.id.sortButton);
        filterButton = findViewById(R.id.filterButton);

        // Find filter indicator elements
        filterIndicatorContainer = findViewById(R.id.filterIndicatorContainer);
        filterIndicator = findViewById(R.id.filterIndicator);
        clearFilterButton = findViewById(R.id.clearFilterButton);
        resultCount = findViewById(R.id.resultCount);

        setupViewModeToggle();

        // Set up filter button click listener
        if (filterButton != null) {
            filterButton.setOnClickListener(v -> showAdvancedFilterDialog());
        }

        // Set default filter indicator visibility
        if (filterIndicatorContainer != null) {
            filterIndicatorContainer.setVisibility(View.GONE);
        }

        // Setup clear filter button
        if (clearFilterButton != null) {
            clearFilterButton.setOnClickListener(v -> {
                // Reset and show all transactions
                updateTransactionsList();

                // Hide filter UI
                filterIndicatorContainer.setVisibility(View.GONE);
            });
        }
    }

    private void setupViewModeToggle() {
        viewModeButton = findViewById(R.id.viewModeButton);
        if (viewModeButton == null) return;

        // Set initial icon and text
        updateViewModeButtonAppearance();

        // Set click listener
        viewModeButton.setOnClickListener(v -> {
            toggleViewMode();
        });
    }

//    private void toggleViewMode() {
//        // Toggle mode
//        currentViewMode = (currentViewMode == ViewMode.LIST) ? ViewMode.GROUP : ViewMode.LIST;
//
//        // Update button appearance
//        updateViewModeButtonAppearance();
//
//        // Force smart loading strategy to use the selected view mode
//        if (smartLoadingStrategy != null) {
//            smartLoadingStrategy.setForceViewMode(currentViewMode == ViewMode.GROUP);
//
//            // Reload data with new view mode
//            refreshTransactions();
//        }
//    }

    /**
     * Toggle the view mode - replaces the existing method
     */
//    private void toggleViewMode() {
//        showGroupingModeSelector();
//    }

    /**
     * Toggle the view mode - shows a popup menu with view options
     */
    private void toggleViewMode() {
        // Create the popup menu
        PopupMenu popup = new PopupMenu(this, viewModeButton);

        // Inflate the menu resource
        popup.getMenuInflater().inflate(R.menu.view_mode_menu, popup.getMenu());

        // Add icons to menu items (PopupMenu doesn't show icons by default)
        // This requires a little workaround
        try {
            Field field = popup.getClass().getDeclaredField("mPopup");
            field.setAccessible(true);
            Object menuPopupHelper = field.get(popup);
            Class<?> classPopupHelper = Class.forName(menuPopupHelper.getClass().getName());
            Method setForceShowIcon = classPopupHelper.getMethod("setForceShowIcon", boolean.class);
            setForceShowIcon.invoke(menuPopupHelper, true);
        } catch (Exception e) {
            Log.e(TAG, "Failed to show menu icons", e);
            // Continue without icons if they can't be shown
        }

        // Set currently selected item
        Menu menu = popup.getMenu();
        switch (currentViewMode) {
            case LIST:
                menu.findItem(R.id.menu_list_view).setChecked(true);
                break;
            case GROUP_BY_DAY:
                menu.findItem(R.id.menu_day_view).setChecked(true);
                break;
            case GROUP_BY_WEEK:
                menu.findItem(R.id.menu_week_view).setChecked(true);
                break;
            case GROUP_BY_MONTH:
                menu.findItem(R.id.menu_month_view).setChecked(true);
                break;
        }

        // Set click listener for menu items
        popup.setOnMenuItemClickListener(item -> {
            int groupingMode = 0;
            ViewMode newViewMode;

            switch (item.getItemId()) {
                case R.id.menu_list_view:
                    newViewMode = ViewMode.LIST;
                    updateViewMode(newViewMode);
                    preferencesManager.saveViewMode(false);
                    return true;

                case R.id.menu_day_view:
                    groupingMode = 0; // Day grouping
                    newViewMode = ViewMode.GROUP_BY_DAY;
                    break;

                case R.id.menu_week_view:
                    groupingMode = 1; // Week grouping
                    newViewMode = ViewMode.GROUP_BY_WEEK;
                    break;

                case R.id.menu_month_view:
                    groupingMode = 2; // Month grouping
                    newViewMode = ViewMode.GROUP_BY_MONTH;
                    break;

                default:
                    return false;
            }

            // Update view mode and grouping mode
            currentViewMode = newViewMode;
            if (smartLoadingStrategy != null) {
                smartLoadingStrategy.setGroupingMode(groupingMode);
                smartLoadingStrategy.setForceViewMode(true);
            }

            // Save preferences
            preferencesManager.saveViewMode(true);
            preferencesManager.saveGroupingMode(groupingMode);

            // Update UI
            updateViewModeButtonAppearance();
            return true;
        });

        // Show the popup menu
        popup.show();
    }

    /**
     * Update the appearance of the view mode button based on current mode
     */
    private void updateViewModeButtonAppearance() {
        if (viewModeButton == null) return;

        switch (currentViewMode) {
            case LIST:
                viewModeButton.setIcon(getDrawable(R.drawable.ic_view_list));
                viewModeButton.setText("List View");
                break;
            case GROUP_BY_DAY:
                viewModeButton.setIcon(getDrawable(R.drawable.ic_view_module));
                viewModeButton.setText("Day View");
                break;
            case GROUP_BY_WEEK:
                viewModeButton.setIcon(getDrawable(R.drawable.ic_view_module));
                viewModeButton.setText("Week View");
                break;
            case GROUP_BY_MONTH:
                viewModeButton.setIcon(getDrawable(R.drawable.ic_view_module));
                viewModeButton.setText("Month View");
                break;
        }
    }

    private void setupRecyclerView() {
        RecyclerView recyclerView = findViewById(R.id.recyclerView);
        if (recyclerView == null) {
            Log.e(TAG, "RecyclerView not found in layout!");
            return;
        }

        adapter = new TransactionAdapter();
        recyclerView.setAdapter(adapter);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        // Set click listener for transactions
        adapter.setOnTransactionClickListener(transaction -> {
            showEditTransactionDialog(transaction);
        });
    }

    private void setupViewModel() {
        viewModel = new ViewModelProvider(this).get(TransactionViewModel.class);

        // Set up initial transaction loading
        resetPagination();

        // Observe budget changes
        viewModel.getBudget().observe(this, budget -> {
            if (budget > 0) {
                viewModel.getTransactionsBetweenDates(fromDate, toDate, transactions -> {
                    if (transactions != null) {
                        updateSummaryWithBudget(transactions, budget);
                    } else {
                        resetBudgetUI(budget);
                    }
                });
            } else {
                resetBudgetUI(0);
            }
        });
    }

    private void showEditTransactionDialog(Transaction transaction) {
        // Create the dialog with the transaction
        TransactionEditDialog dialog = new TransactionEditDialog(transaction);

        // Set the listener for when the transaction is edited
        dialog.setOnTransactionEditListener(editedTransaction -> {
            // Update the transaction in the database
            viewModel.updateTransaction(editedTransaction);

            // Instead of calling updateTransactionsList() which resets filters,
            // we'll just update the transaction in our local lists and adapter
            updateTransactionInLists(editedTransaction);

            // Force adapter to refresh this specific item's visual appearance
            int position = -1;
            List<Transaction> currentTransactions = adapter.getTransactions();
            for (int i = 0; i < currentTransactions.size(); i++) {
                if (currentTransactions.get(i).getId() == editedTransaction.getId()) {
                    position = i;
                    break;
                }
            }

            if (position >= 0) {
                adapter.notifyItemChanged(position);
            }

        });

        // Show the dialog
        dialog.show(getSupportFragmentManager(), "edit_transaction");
    }

    private void updateTransactionInLists(Transaction editedTransaction) {
        // Update in allTransactions
        for (int i = 0; i < allTransactions.size(); i++) {
            if (allTransactions.get(i).getId() == editedTransaction.getId()) {
                allTransactions.set(i, editedTransaction);
                break;
            }
        }

        // Apply current filters to get the updated list
        List<Transaction> filteredList = currentFilterState.applyFilters(allTransactions);

        // Update the adapter with the properly filtered list
        adapter.setTransactions(filteredList);

        // Notify the adapter about the specific change
        // This forces a visual update of the transaction in the list
        for (int i = 0; i < adapter.getTransactions().size(); i++) {
            if (adapter.getTransactions().get(i).getId() == editedTransaction.getId()) {
                adapter.notifyItemChanged(i);
                break;
            }
        }

        // Also update in smartLoadingStrategy if available
        if (smartLoadingStrategy != null) {
            smartLoadingStrategy.updateTransactionInAdapters(editedTransaction);
        }

        // Update the summary
        updateSummary(filteredList);

        // Show a confirmation
        Toast.makeText(this, "Transaction updated", Toast.LENGTH_SHORT).show();
    }

    private void setupPagination() {
        RecyclerView recyclerView = findViewById(R.id.recyclerView);
        if (recyclerView == null) return;

        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);

                // Skip pagination if we're using grouped view
                if (smartLoadingStrategy != null && smartLoadingStrategy.isGroupedViewActive()) {
                    return;
                }

                LinearLayoutManager layoutManager = (LinearLayoutManager) recyclerView.getLayoutManager();
                if (layoutManager == null) return;

                int visibleItemCount = layoutManager.getChildCount();
                int totalItemCount = layoutManager.getItemCount();
                int firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition();

                if (!isLoading && hasMoreData) {
                    if ((visibleItemCount + firstVisibleItemPosition) >= totalItemCount
                            && firstVisibleItemPosition >= 0
                            && totalItemCount >= PAGE_SIZE) {
                        // Load more data
                        loadMoreTransactions();
                    }
                }
            }
        });
    }

    private void loadMoreTransactions() {
        // Skip if we're using grouped view
        if (smartLoadingStrategy != null && smartLoadingStrategy.isGroupedViewActive()) {
            return;
        }

        // Proceed with normal pagination if not in grouped view
        isLoading = true;

        if (loadingIndicator != null) {
            loadingIndicator.setVisibility(View.VISIBLE);
        }

        executorService.execute(() -> {
            int offset = currentPage * PAGE_SIZE;

            List<Transaction> nextPageTransactions;

            // Check if we're viewing manually excluded transactions
            if (currentFilterState.viewingManuallyExcluded) {
                nextPageTransactions = viewModel.getManuallyExcludedTransactionsPaginatedSync(
                        fromDate, toDate, PAGE_SIZE, offset);
            }
            // Otherwise, load regular transactions
            else {
                nextPageTransactions = viewModel.getNonExcludedTransactionsBetweenDatesPaginatedSync(
                        fromDate, toDate, PAGE_SIZE, offset);
            }

            // If we got less items than PAGE_SIZE, we've reached the end
            hasMoreData = nextPageTransactions != null && nextPageTransactions.size() == PAGE_SIZE;

            List<Transaction> finalNextPageTransactions = nextPageTransactions;
            runOnUiThread(() -> {
                if (loadingIndicator != null) {
                    loadingIndicator.setVisibility(View.GONE);
                }

                if (finalNextPageTransactions != null && !finalNextPageTransactions.isEmpty()) {
                    // Add new items to adapter
                    adapter.addTransactions(finalNextPageTransactions);

                    // Add to all transactions list for filtering
                    allTransactions.addAll(finalNextPageTransactions);

                    // Update summary with all transactions
                    updateSummary(allTransactions);

                    // Update empty state view
                    if (emptyStateText != null) {
                        emptyStateText.setVisibility(View.GONE);
                    }
                } else if (adapter.getItemCount() == 0 && emptyStateText != null) {
                    // Show empty state
                    emptyStateText.setVisibility(View.VISIBLE);
                }

                currentPage++;
                isLoading = false;
            });
        });
    }

    private void resetPagination() {
        currentPage = 0;
        hasMoreData = true;
        allTransactions.clear();

        if (adapter != null) {
            adapter.clearTransactions();
        }

        // Reset summary totals to zero initially
        if (totalDebitsText != null) {
            totalDebitsText.setText(String.format(Locale.getDefault(), "₹%.2f", 0.0));
        }

        if (totalCreditsText != null) {
            totalCreditsText.setText(String.format(Locale.getDefault(), "₹%.2f", 0.0));
        }

        if (balanceText != null) {
            balanceText.setText(String.format(Locale.getDefault(), "₹%.2f", 0.0));
        }

        loadMoreTransactions();

        setupSpendingChart();

        updateSummary(adapter.getTransactions());

    }

    private void setupSearch() {
        if (searchInput == null) return;

        searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                filterTransactions(s.toString());
            }
        });
    }

    private void filterTransactions(String query) {
        // Update filter state
        currentFilterState.searchQuery = query;

        // Apply filters to allTransactions
        List<Transaction> filteredTransactions = currentFilterState.applyFilters(allTransactions);

        // Update adapter
        adapter.setTransactions(filteredTransactions);

        // Update filter indicator
        if (filterIndicatorContainer != null) {
            if (!query.isEmpty()) {
                filterIndicatorContainer.setVisibility(View.VISIBLE);
                filterIndicator.setText("Search: " + query);

                if (resultCount != null) {
                    resultCount.setText(String.format(Locale.getDefault(),
                            "%d transaction(s) found", filteredTransactions.size()));
                }
            } else if (!currentFilterState.isAnyFilterActive()) {
                filterIndicatorContainer.setVisibility(View.GONE);
            }
        }
    }

    private void setupSort() {
        if (sortButton == null) {
            Log.d(TAG, "Sort button not found in layout");
            return;
        }

        sortButton.setOnClickListener(v -> {
            String[] sortOptions = {
                    "Date (newest first)",
                    "Date (oldest first)",
                    "Amount (highest first)",
                    "Amount (lowest first)",
                    "Description (A-Z)",
                    "Description (Z-A)"
            };

            new AlertDialog.Builder(this)
                    .setTitle("Sort Transactions By")
                    .setItems(sortOptions, (dialog, which) -> {
                        // Apply the selected sort
                        sortTransactions(which);
                    })
                    .show();
        });
    }

    private void sortTransactions(int sortOption) {
        // Update filter state
        currentFilterState.sortOption = sortOption;

        // Get current list and apply sort
        List<Transaction> currentList = new ArrayList<>(adapter.getTransactions());
        sortTransactions(currentList, sortOption);

        // Update adapter with sorted list
        adapter.setTransactions(currentList);
    }

    private static void sortTransactions(List<Transaction> transactions, int sortOption) {
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

    private void setupCategoryFilter() {
        ChipGroup categoryFilterChipGroup = findViewById(R.id.categoryFilterChipGroup);
        if (categoryFilterChipGroup == null) {
            Log.d(TAG, "Category filter chip group not found in layout");
            return;
        }

        // Clear any existing chips
        categoryFilterChipGroup.removeAllViews();

        // Add "All Categories" chip
        Chip allCategoriesChip = new Chip(this);
        allCategoriesChip.setText("All Categories");
        allCategoriesChip.setCheckable(true);
        allCategoriesChip.setChecked(true); // Selected by default
        categoryFilterChipGroup.addView(allCategoriesChip);

        // Get all categories from Transaction class
        String[] categories = Transaction.Categories.getAllCategories();

        // Add a chip for each category
        for (String category : categories) {
            Chip chip = new Chip(this);
            chip.setText(category);
            chip.setCheckable(true);

            // Set chip color based on category
            int categoryColor = getCategoryColor(category);
            int textColor = getContrastColor(categoryColor);

            chip.setChipBackgroundColor(ColorStateList.valueOf(categoryColor));
            chip.setTextColor(textColor);

            categoryFilterChipGroup.addView(chip);
        }

        // Add "Manually Excluded" chip - special filter
        Chip excludedChip = new Chip(this);
        excludedChip.setText("Manually Excluded");
        excludedChip.setCheckable(true);

        // Set a distinctive purple color for the excluded transactions filter
        excludedChip.setChipBackgroundColor(ColorStateList.valueOf(getColor(R.color.purple_light)));
        excludedChip.setTextColor(getColor(R.color.white));

        categoryFilterChipGroup.addView(excludedChip);

        // Set listener for chip selection
        categoryFilterChipGroup.setOnCheckedChangeListener((group, checkedId) -> {
            // Find which chip is selected
            if (checkedId == View.NO_ID) {
                // No chip selected, show all categories
                currentFilterState.viewingManuallyExcluded = false;
                resetPagination();
                return;
            }

            Chip selectedChip = findViewById(checkedId);
            if (selectedChip != null) {
                String selectedCategory = selectedChip.getText().toString();

                // If "All Categories" is selected, don't filter by category
                if ("All Categories".equals(selectedCategory)) {
                    currentFilterState.viewingManuallyExcluded = false;
                    filterTransactionsByCategory(null);
                }
                // Special handling for "Manually Excluded" filter
                else if ("Manually Excluded".equals(selectedCategory)) {
                    loadManuallyExcludedTransactions();
                }
                // Regular category filtering
                else {
                    currentFilterState.viewingManuallyExcluded = false;
                    filterTransactionsByCategory(selectedCategory);
                }
            }
        });
    }

    private void loadManuallyExcludedTransactions() {
        // Update filter state
        currentFilterState.viewingManuallyExcluded = true;

        // Clear existing data
        adapter.clearTransactions();
        allTransactions.clear();
        currentPage = 0;
        hasMoreData = true;

        // Show loading indicator
        if (loadingIndicator != null) {
            loadingIndicator.setVisibility(View.VISIBLE);
        }

        // Load manually excluded transactions
        executorService.execute(() -> {
            // Get manually excluded transactions
            List<Transaction> manuallyExcludedTransactions = viewModel.getManuallyExcludedTransactionsPaginatedSync(
                    fromDate, toDate, PAGE_SIZE, 0);

            // Update UI
            runOnUiThread(() -> {
                if (loadingIndicator != null) {
                    loadingIndicator.setVisibility(View.GONE);
                }

                if (manuallyExcludedTransactions != null && !manuallyExcludedTransactions.isEmpty()) {
                    adapter.setTransactions(manuallyExcludedTransactions);
                    allTransactions.addAll(manuallyExcludedTransactions);

                    // Update filter indicator
                    if (filterIndicatorContainer != null) {
                        filterIndicatorContainer.setVisibility(View.VISIBLE);
                        filterIndicator.setText("Viewing: Manually Excluded Transactions");

                        if (resultCount != null) {
                            resultCount.setText(String.format(Locale.getDefault(),
                                    "%d transaction(s) found", manuallyExcludedTransactions.size()));
                        }
                    }

                    // Hide empty state
                    if (emptyStateText != null) {
                        emptyStateText.setVisibility(View.GONE);
                    }
                } else {
                    // Show empty state
                    if (emptyStateText != null) {
                        emptyStateText.setVisibility(View.VISIBLE);
                        emptyStateText.setText("No manually excluded transactions found");
                    }

                    // Update filter indicator
                    if (filterIndicatorContainer != null) {
                        filterIndicatorContainer.setVisibility(View.VISIBLE);
                        filterIndicator.setText("Viewing: Manually Excluded Transactions");

                        if (resultCount != null) {
                            resultCount.setText("0 transactions found");
                        }
                    }
                }
            });
        });
    }


    private void filterTransactionsByCategory(String category) {
        // Update filter state
        currentFilterState.category = category;

        // Apply filters to allTransactions
        List<Transaction> filteredTransactions = currentFilterState.applyFilters(allTransactions);

        // Update adapter and UI
        adapter.setTransactions(filteredTransactions);
        updateSummary(filteredTransactions);

        // Show filter indicator
        if (filterIndicatorContainer != null) {
            if (category != null && !category.isEmpty()) {
                filterIndicatorContainer.setVisibility(View.VISIBLE);
                filterIndicator.setText("Filtered by category: " + category);

                if (resultCount != null) {
                    resultCount.setText(String.format(Locale.getDefault(),
                            "%d transaction(s) found", filteredTransactions.size()));
                }
            } else if (!currentFilterState.isAnyFilterActive()) {
                filterIndicatorContainer.setVisibility(View.GONE);
            }
        }
    }

    // Helper method to get category color
    private int getCategoryColor(String category) {
        switch (category) {
            case Transaction.Categories.FOOD:
                return getColor(R.color.category_food);
            case Transaction.Categories.SHOPPING:
                return getColor(R.color.category_shopping);
            case Transaction.Categories.BILLS:
                return getColor(R.color.category_bills);
            case Transaction.Categories.ENTERTAINMENT:
                return getColor(R.color.category_entertainment);
            case Transaction.Categories.TRANSPORT:
                return getColor(R.color.category_transport);
            case Transaction.Categories.HEALTH:
                return getColor(R.color.category_health);
            case Transaction.Categories.EDUCATION:
                return getColor(R.color.category_education);
            default:
                return getColor(R.color.text_secondary);
        }
    }

    // Helper method to determine contrasting text color (white or black) based on background color
    private int getContrastColor(int backgroundColor) {
        // Extract the red, green, and blue components
        int red = Color.red(backgroundColor);
        int green = Color.green(backgroundColor);
        int blue = Color.blue(backgroundColor);

        // Calculate luminance (simplified formula)
        double luminance = (0.299 * red + 0.587 * green + 0.114 * blue) / 255;

        // Use white text on dark backgrounds, black text on light backgrounds
        return luminance > 0.5 ? Color.BLACK : Color.WHITE;
    }

    private void setupDateRangeChips() {
        ChipGroup dateRangeChipGroup = findViewById(R.id.dateRangeChipGroup);
        if (dateRangeChipGroup == null) {
            Log.d(TAG, "Date range chip group not found in layout");
            return;
        }

        // Setup click listeners for each chip
        View chipToday = findViewById(R.id.chipToday);
        if (chipToday != null) {
            chipToday.setOnClickListener(v -> {
                setDateRangeToday();
                // Clear any previous chip selections if this was unselected
                if (!((Chip)v).isChecked()) {
                    dateRangeChipGroup.clearCheck();
                }
            });
        }

        View chipYesterday = findViewById(R.id.chipYesterday);
        if (chipYesterday != null) {
            chipYesterday.setOnClickListener(v -> {
                setDateRangeYesterday();
                if (!((Chip)v).isChecked()) {
                    dateRangeChipGroup.clearCheck();
                }
            });
        }

        View chipThisWeek = findViewById(R.id.chipThisWeek);
        if (chipThisWeek != null) {
            chipThisWeek.setOnClickListener(v -> {
                setDateRangeThisWeek();
                if (!((Chip)v).isChecked()) {
                    dateRangeChipGroup.clearCheck();
                }
            });
        }

        View chipThisMonth = findViewById(R.id.chipThisMonth);
        if (chipThisMonth != null) {
            chipThisMonth.setOnClickListener(v -> {
                setDateRangeThisMonth();
                if (!((Chip)v).isChecked()) {
                    dateRangeChipGroup.clearCheck();
                }
            });
        }

        View chipLast3Months = findViewById(R.id.chipLast3Months);
        if (chipLast3Months != null) {
            chipLast3Months.setOnClickListener(v -> {
                setDateRangeLast3Months();
                if (!((Chip)v).isChecked()) {
                    dateRangeChipGroup.clearCheck();
                }
            });
        }
    }

    private void setDateRangeToday() {
        // Set date range to today
        Calendar cal = Calendar.getInstance();

        // End of today
        cal.set(Calendar.HOUR_OF_DAY, 23);
        cal.set(Calendar.MINUTE, 59);
        cal.set(Calendar.SECOND, 59);
        cal.set(Calendar.MILLISECOND, 999);
        toDate = cal.getTimeInMillis();

        // Start of today
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        fromDate = cal.getTimeInMillis();

        updateDateButtonTexts();
        refreshTransactions();
        preferencesManager.saveSelectedDateRange(fromDate, toDate);
        checkForTransactionsAndLoad();
    }

    private void setDateRangeYesterday() {
        // Set date range to yesterday
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_YEAR, -1);

        // End of yesterday
        cal.set(Calendar.HOUR_OF_DAY, 23);
        cal.set(Calendar.MINUTE, 59);
        cal.set(Calendar.SECOND, 59);
        cal.set(Calendar.MILLISECOND, 999);
        toDate = cal.getTimeInMillis();

        // Start of yesterday
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        fromDate = cal.getTimeInMillis();

        updateDateButtonTexts();
        refreshTransactions();
        preferencesManager.saveSelectedDateRange(fromDate, toDate);
        checkForTransactionsAndLoad();
    }

    private void setDateRangeThisWeek() {
        // Set date range to this week (Sunday to today)
        Calendar cal = Calendar.getInstance();

        // End of today
        cal.set(Calendar.HOUR_OF_DAY, 23);
        cal.set(Calendar.MINUTE, 59);
        cal.set(Calendar.SECOND, 59);
        cal.set(Calendar.MILLISECOND, 999);
        toDate = cal.getTimeInMillis();

        // Start of this week (Sunday)
        cal.set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        fromDate = cal.getTimeInMillis();

        updateDateButtonTexts();
        refreshTransactions();
        preferencesManager.saveSelectedDateRange(fromDate, toDate);
        checkForTransactionsAndLoad();
    }

    private void setDateRangeThisMonth() {
        // Set date range to this month
        Calendar cal = Calendar.getInstance();

        // End of today
        cal.set(Calendar.HOUR_OF_DAY, 23);
        cal.set(Calendar.MINUTE, 59);
        cal.set(Calendar.SECOND, 59);
        cal.set(Calendar.MILLISECOND, 999);
        toDate = cal.getTimeInMillis();

        // Start of this month
        cal.set(Calendar.DAY_OF_MONTH, 1);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        fromDate = cal.getTimeInMillis();

        updateDateButtonTexts();
        refreshTransactions();
        preferencesManager.saveSelectedDateRange(fromDate, toDate);
        checkForTransactionsAndLoad();
    }

    private void setDateRangeLast3Months() {
        // Set date range to last 3 months
        Calendar cal = Calendar.getInstance();

        // End of today
        cal.set(Calendar.HOUR_OF_DAY, 23);
        cal.set(Calendar.MINUTE, 59);
        cal.set(Calendar.SECOND, 59);
        cal.set(Calendar.MILLISECOND, 999);
        toDate = cal.getTimeInMillis();

        // Start of 3 months ago
        cal.add(Calendar.MONTH, -3);
        cal.set(Calendar.DAY_OF_MONTH, 1);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        fromDate = cal.getTimeInMillis();

        updateDateButtonTexts();

        // Instead of just calling resetPagination(), call refreshTransactions()
        // which more thoroughly refreshes the data
        refreshTransactions();

        // Also save the date range
        preferencesManager.saveSelectedDateRange(fromDate, toDate);
        checkForTransactionsAndLoad();
    }

    private void checkForTransactionsAndLoad() {
        // Show loading indicator
        if (loadingIndicator != null) {
            loadingIndicator.setVisibility(View.VISIBLE);
        }

        executorService.execute(() -> {
            try {
                boolean shouldProcessSMS = false;

                // Get current month boundaries
                Calendar cal = Calendar.getInstance();
                cal.set(Calendar.DAY_OF_MONTH, 1); // First day of current month
                cal.set(Calendar.HOUR_OF_DAY, 0);
                cal.set(Calendar.MINUTE, 0);
                cal.set(Calendar.SECOND, 0);
                cal.set(Calendar.MILLISECOND, 0);
                long currentMonthStart = cal.getTimeInMillis();

                cal.add(Calendar.MONTH, 1); // First day of next month
                cal.add(Calendar.MILLISECOND, -1); // Last millisecond of current month
                long currentMonthEnd = cal.getTimeInMillis();

                // Check if selected date range extends beyond current month
                if (fromDate < currentMonthStart || toDate > currentMonthEnd) {
                    Log.d(TAG, "Selected date range extends beyond current month, checking for transactions");

                    // Only need to check the portion outside current month
                    long checkStartDate = Math.min(fromDate, currentMonthStart);
                    long checkEndDate = Math.max(toDate, currentMonthEnd);

                    if (fromDate < currentMonthStart) {
                        checkStartDate = fromDate;
                        checkEndDate = currentMonthStart - 1; // Up to just before current month
                    } else if (toDate > currentMonthEnd) {
                        checkStartDate = currentMonthEnd + 1; // Just after current month
                        checkEndDate = toDate;
                    }

                    // Check if we have transactions in the non-current month portion
                    int count = TransactionDatabase.getInstance(this)
                            .transactionDao()
                            .getTransactionCountBetweenDates(checkStartDate, checkEndDate);

                    Log.d(TAG, "Found " + count + " transactions in date range outside current month");

                    // Only process SMS if we don't have transactions outside current month
                    shouldProcessSMS = (count == 0);
                } else {
                    // Date range is within current month, no need to process SMS
                    Log.d(TAG, "Selected date range is within current month, no need to process SMS");
                    shouldProcessSMS = false;
                }

                final boolean finalShouldProcessSMS = shouldProcessSMS;
                runOnUiThread(() -> {
                    if (finalShouldProcessSMS) {
                        // We need to process SMS for non-current month data
                        Log.d(TAG, "Processing SMS for selected date range");
                        loadExistingSMS();
                    } else {
                        // We already have data or it's current month, just refresh display
                        Log.d(TAG, "No need to process SMS, refreshing transactions");
                        refreshTransactions();
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "Error checking for transactions", e);
                runOnUiThread(() -> {
                    // On error, fallback to just refreshing transactions
                    refreshTransactions();

                    if (loadingIndicator != null) {
                        loadingIndicator.setVisibility(View.GONE);
                    }
                });
            }
        });
    }

    private void setupBudgetFab() {
        FloatingActionButton setBudgetFab = findViewById(R.id.setBudgetFab);
        if (setBudgetFab != null) {
            setBudgetFab.setOnClickListener(v -> {
                showBudgetDialog();
            });
        }
    }

    private void showBudgetDialog() {
        // Create dialog
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_budget_management, null);
        builder.setView(dialogView);

        // Get references to views
        TextInputEditText budgetInput = dialogView.findViewById(R.id.budgetAmountInput);
        LinearProgressIndicator progressBar = dialogView.findViewById(R.id.budgetProgressBar);
        TextView spentText = dialogView.findViewById(R.id.spentAmountText);
        TextView remainingText = dialogView.findViewById(R.id.remainingAmountText);
        Button cancelButton = dialogView.findViewById(R.id.cancelBudgetButton);
        Button saveButton = dialogView.findViewById(R.id.saveBudgetButton);

        // Get current budget and expenses
        double currentBudget = 0;
        try {
            Double budgetValue = viewModel.getBudget().getValue();
            if (budgetValue != null) {
                currentBudget = budgetValue;
                budgetInput.setText(String.valueOf(currentBudget));
            } else {
                budgetInput.setText("");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting budget value", e);
            budgetInput.setText("");
        }

        // Calculate current month's expenses
        Calendar cal = Calendar.getInstance();
        // End of today
        cal.set(Calendar.HOUR_OF_DAY, 23);
        cal.set(Calendar.MINUTE, 59);
        cal.set(Calendar.SECOND, 59);
        long endDate = cal.getTimeInMillis();

        // Start of this month
        cal.set(Calendar.DAY_OF_MONTH, 1);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        long startDate = cal.getTimeInMillis();

        final double finalCurrentBudget = currentBudget;

        viewModel.getTransactionsBetweenDates(startDate, endDate, transactions -> {
            if (transactions == null) return;

            double totalExpenses = 0;
            for (Transaction transaction : transactions) {
                if ("DEBIT".equals(transaction.getType()) && !transaction.isExcludedFromTotal()) {
                    totalExpenses += transaction.getAmount();
                }
            }

            // Update UI with current expenses
            double spent = totalExpenses;
            double remaining = finalCurrentBudget - spent;
            int progressPercentage = finalCurrentBudget > 0 ? (int)((spent / finalCurrentBudget) * 100) : 0;

            // Show current status
            spentText.setText(String.format(Locale.getDefault(), "Spent: ₹%.2f", spent));
            remainingText.setText(String.format(Locale.getDefault(), "Remaining: ₹%.2f", remaining));
            progressBar.setProgress(progressPercentage);

            // Set progress bar color based on percentage
            if (progressPercentage > 90) {
                progressBar.setIndicatorColor(getColor(R.color.red));
            } else if (progressPercentage > 75) {
                progressBar.setIndicatorColor(getColor(R.color.yellow));
            } else {
                progressBar.setIndicatorColor(getColor(R.color.green));
            }
        });

        // Create and show dialog
        AlertDialog dialog = builder.create();

        // Set up button click listeners
        cancelButton.setOnClickListener(v -> {
            dialog.dismiss();
        });

        saveButton.setOnClickListener(v -> {
            // Save new budget
            try {
                double newBudget = Double.parseDouble(budgetInput.getText().toString());
                viewModel.setBudget(newBudget);
                Toast.makeText(this, "Budget updated", Toast.LENGTH_SHORT).show();
            } catch (NumberFormatException e) {
                Toast.makeText(this, "Please enter a valid amount", Toast.LENGTH_SHORT).show();
                return;
            }

            dialog.dismiss();
        });

        dialog.show();
    }

    private void setupSpendingChart() {
        LineChart spendingLineChart = findViewById(R.id.spendingLineChart);
        if (spendingLineChart == null) {
            Log.d(TAG, "Spending line chart not found in layout");
            return;
        }

        // Configure chart appearance
        spendingLineChart.getDescription().setEnabled(false);
        spendingLineChart.setDrawGridBackground(false);
        spendingLineChart.getAxisRight().setEnabled(false);

        XAxis xAxis = spendingLineChart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setGranularity(1f);
        xAxis.setGranularityEnabled(true);

        YAxis leftAxis = spendingLineChart.getAxisLeft();
        leftAxis.setDrawZeroLine(true);

        // Enable touch gestures
        spendingLineChart.setTouchEnabled(true);
        spendingLineChart.setDragEnabled(true);
        spendingLineChart.setScaleEnabled(true);

        // Load data for the chart
        updateSpendingChart();
    }

    private void updateSpendingChart() {
        LineChart spendingLineChart = findViewById(R.id.spendingLineChart);
        if (spendingLineChart == null) return;

        // Start date is beginning of selected date range or 30 days ago if range is larger
        long chartEndDate = toDate;
        long chartStartDateInt = fromDate;

        // If date range is more than 30 days, limit to last 30 days for better visualization
        long thirtyDaysInMillis = 30 * 24 * 60 * 60 * 1000L;
        if (toDate - fromDate > thirtyDaysInMillis) {
            chartStartDateInt = toDate - thirtyDaysInMillis;
        }
        long chartStartDate = chartStartDateInt;

        // Get transactions for chart date range
        executorService.execute(() -> {
            List<Transaction> chartTransactions = TransactionDatabase.getInstance(this)
                    .transactionDao()
                    .getTransactionsBetweenDatesSyncAscending(chartStartDate, chartEndDate);

            // Group transactions by day
            Map<Long, Float> dailySpending = new TreeMap<>();
            SimpleDateFormat dateKeyFormat = new SimpleDateFormat("yyyyMMdd", Locale.getDefault());
            SimpleDateFormat dateLabelFormat = new SimpleDateFormat("dd MMM", Locale.getDefault());
            List<String> dateLabels = new ArrayList<>();

            // Process transactions
            for (Transaction transaction : chartTransactions) {
                if ("DEBIT".equals(transaction.getType()) && !transaction.isExcludedFromTotal()) {
                    // Get day key by stripping time component
                    Calendar cal = Calendar.getInstance();
                    cal.setTimeInMillis(transaction.getDate());
                    cal.set(Calendar.HOUR_OF_DAY, 0);
                    cal.set(Calendar.MINUTE, 0);
                    cal.set(Calendar.SECOND, 0);
                    cal.set(Calendar.MILLISECOND, 0);
                    long dayKey = cal.getTimeInMillis();

                    // Add to daily total
                    float currentAmount = dailySpending.getOrDefault(dayKey, 0f);
                    dailySpending.put(dayKey, currentAmount + (float)transaction.getAmount());

                    // Add date label if not already present
                    String label = dateLabelFormat.format(new Date(dayKey));
                    if (!dateLabels.contains(label)) {
                        dateLabels.add(label);
                    }
                }
            }

            // Create chart entries
            List<Entry> entries = new ArrayList<>();
            int index = 0;
            for (Map.Entry<Long, Float> entry : dailySpending.entrySet()) {
                entries.add(new Entry(index++, entry.getValue()));
            }

            // Update chart on UI thread
            runOnUiThread(() -> {
                if (entries.isEmpty()) {
                    // No data to display
                    spendingLineChart.setNoDataText("No spending data available");
                    spendingLineChart.invalidate();
                    return;
                }

                // Create dataset
                LineDataSet dataSet = new LineDataSet(entries, "Daily Spending");
                dataSet.setColor(getColor(R.color.primary));
                dataSet.setLineWidth(2f);
                dataSet.setCircleColor(getColor(R.color.primary));
                dataSet.setCircleRadius(4f);
                dataSet.setDrawValues(false);
                dataSet.setMode(LineDataSet.Mode.CUBIC_BEZIER); // Smooth curve

                // Enable fill color
                dataSet.setDrawFilled(true);
                dataSet.setFillColor(getColor(R.color.primary));

                // Create line data and set to chart
                LineData lineData = new LineData(dataSet);
                spendingLineChart.setData(lineData);

                // Set X-axis labels
                XAxis xAxis = spendingLineChart.getXAxis();
                xAxis.setValueFormatter(new IndexAxisValueFormatter(dateLabels));
                xAxis.setLabelCount(Math.min(dateLabels.size(), 5));

                // Refresh chart
                spendingLineChart.invalidate();
                spendingLineChart.animateX(1000);
            });
        });
    }

    private void showAdvancedFilterDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_advanced_filter, null);
        builder.setView(dialogView);

        // Initialize dialog views
        AutoCompleteTextView bankFilterDropdown = dialogView.findViewById(R.id.bankFilterDropdown);
        AutoCompleteTextView typeFilterDropdown = dialogView.findViewById(R.id.typeFilterDropdown);
        AutoCompleteTextView categoryFilterDropdown = dialogView.findViewById(R.id.categoryFilterDropdown);
        RangeSlider amountRangeSlider = dialogView.findViewById(R.id.amountRangeSlider);
        TextView amountRangeText = dialogView.findViewById(R.id.amountRangeText);

        // Get the exclude switch
        androidx.appcompat.widget.SwitchCompat excludeSwitch = dialogView.findViewById(R.id.excludeSwitch);
        if (excludeSwitch != null) {
            // Set initial state based on current filter
            excludeSwitch.setChecked(currentFilterState.showingExcluded);
        }

        // Setup filter dropdowns (unchanged)
        ArrayAdapter<String> bankAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_dropdown_item_1line,
                viewModel.getAvailableBanks()
        );
        bankFilterDropdown.setAdapter(bankAdapter);
        bankFilterDropdown.setText("All Banks", false);

        ArrayAdapter<String> typeAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_dropdown_item_1line,
                new String[]{"All Types", "DEBIT", "CREDIT"}
        );
        typeFilterDropdown.setAdapter(typeAdapter);
        typeFilterDropdown.setText("All Types", false);

        ArrayAdapter<String> categoryAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_dropdown_item_1line,
                Transaction.Categories.getAllCategories()
        );
        categoryFilterDropdown.setAdapter(categoryAdapter);

        // Set initial values for the slider
        amountRangeSlider.setValueFrom(0f);
        amountRangeSlider.setValueTo(100000f);

        // Create a list with initial values
        List<Float> initialValues = new ArrayList<>();
        initialValues.add(0f);
        initialValues.add(100000f);
        amountRangeSlider.setValues(initialValues);

        // Update amount range text when slider changes
        amountRangeSlider.addOnChangeListener((slider, value, fromUser) -> {
            List<Float> values = slider.getValues();
            if (values != null && values.size() >= 2) {
                amountRangeText.setText(String.format(Locale.getDefault(),
                        "₹%.0f - ₹%.0f", values.get(0), values.get(1)));
            }
        });

        // Setup buttons
        Button applyButton = dialogView.findViewById(R.id.applyFilterButton);
        Button resetButton = dialogView.findViewById(R.id.resetFilterButton);

        // Create dialog
        AlertDialog dialog = builder.create();

        // Set button click listeners
        applyButton.setOnClickListener(v -> {
            // Get selected values
            String bank = bankFilterDropdown.getText().toString();
            String type = typeFilterDropdown.getText().toString();
            String category = categoryFilterDropdown.getText().toString();

            // Get amount range with safety checks
            double minAmount = 0;
            double maxAmount = 100000;
            List<Float> values = amountRangeSlider.getValues();
            if (values != null && values.size() >= 2) {
                minAmount = values.get(0);
                maxAmount = values.get(1);
            }

            // Get excluded switch state
            boolean showExcluded = excludeSwitch != null && excludeSwitch.isChecked();

            // Apply filters with excluded state
            applyAdvancedFilters(bank, type, category, minAmount, maxAmount, showExcluded);
            dialog.dismiss();
        });

        resetButton.setOnClickListener(v -> {
            // Reset all filters
            currentFilterState = new FilterState(); // Reset to default state (with showingExcluded = false)

            // Reset and reload transactions
            resetPagination();

            // Hide filter indicators
            if (filterIndicatorContainer != null) {
                filterIndicatorContainer.setVisibility(View.GONE);
            }

            dialog.dismiss();
        });

        dialog.show();
    }

    // Fix for applyAdvancedFilters method
    private void applyAdvancedFilters(String bank, String type, String category,
                                      double minAmount, double maxAmount, boolean showExcluded) {
        // Check if excluded state is changing
        boolean excludedStateChanged = currentFilterState.showingExcluded != showExcluded;

        // Update filter state
        currentFilterState.bank = bank;
        currentFilterState.type = type;
        currentFilterState.category = category;
        currentFilterState.minAmount = minAmount;
        currentFilterState.maxAmount = maxAmount;
        currentFilterState.showingExcluded = showExcluded;

        // If excluded state changed, we need to reload data from database
        if (excludedStateChanged) {
            // Reset pagination and reload
            resetPagination();
        } else {
            // Just apply filters to existing data
            List<Transaction> filteredTransactions = currentFilterState.applyFilters(allTransactions);
            adapter.setTransactions(filteredTransactions);
            updateSummary(filteredTransactions);
        }

        // Show filter indicator
        if (filterIndicatorContainer != null) {
            if (currentFilterState.isAnyFilterActive()) {
                filterIndicatorContainer.setVisibility(View.VISIBLE);

                // Prepare filter description
                StringBuilder filterDesc = new StringBuilder("Filtered by: ");
                boolean hasFilter = false;

                if (!"All Banks".equals(bank)) {
                    filterDesc.append(bank);
                    hasFilter = true;
                }

                if (!"All Types".equals(type)) {
                    if (hasFilter) filterDesc.append(", ");
                    filterDesc.append(type);
                    hasFilter = true;
                }

                if (category != null && !category.isEmpty()) {
                    if (hasFilter) filterDesc.append(", ");
                    filterDesc.append(category);
                    hasFilter = true;
                }

                if (minAmount > 0 || maxAmount < 100000) {
                    if (hasFilter) filterDesc.append(", ");
                    filterDesc.append("Amount ₹").append((int)minAmount).append("-₹").append((int)maxAmount);
                    hasFilter = true;
                }

                if (showExcluded) {
                    if (hasFilter) filterDesc.append(", ");
                    filterDesc.append("Including excluded");
                }

                filterIndicator.setText(filterDesc.toString());
            } else {
                filterIndicatorContainer.setVisibility(View.GONE);
            }

            if (resultCount != null) {
                resultCount.setText(String.format(Locale.getDefault(),
                        "%d transaction(s) found", adapter.getItemCount()));
            }
        }
    }

    private void updateTransactionsList() {
        if (loadingIndicator != null) {
            loadingIndicator.setVisibility(View.VISIBLE);
        }

        viewModel.getTransactionsBetweenDates(fromDate, toDate, transactions -> {
            if (loadingIndicator != null) {
                loadingIndicator.setVisibility(View.GONE);
            }

            if (transactions == null || transactions.isEmpty()) {
                if (adapter != null) {
                    adapter.setTransactions(new ArrayList<>());
                }

                updateSummary(new ArrayList<>());

                // Show empty state
                if (emptyStateText != null) {
                    emptyStateText.setVisibility(View.VISIBLE);
                }

                return;
            }

            // Hide empty state
            if (emptyStateText != null) {
                emptyStateText.setVisibility(View.GONE);
            }

            adapter.setTransactions(transactions);
            updateSummary(transactions);

            // Save all transactions for search filtering
            allTransactions = new ArrayList<>(transactions);
        });
    }

    public void updateSummary(List<Transaction> transactions) {
        double totalDebits = 0;
        double totalCredits = 0;

        for (Transaction transaction : transactions) {
            // Only include transactions that are not excluded from totals
            if (!transaction.isExcludedFromTotal()) {
                if ("DEBIT".equals(transaction.getType())) {
                    totalDebits += transaction.getAmount();
                } else if ("CREDIT".equals(transaction.getType())) {
                    totalCredits += transaction.getAmount();
                }
            }
        }

        if (totalDebitsText != null) {
            totalDebitsText.setText(String.format(Locale.getDefault(), "₹%.2f", totalDebits));
        }

        if (totalCreditsText != null) {
            totalCreditsText.setText(String.format(Locale.getDefault(), "₹%.2f", totalCredits));
        }

        // Also update balance display
        if (balanceText != null) {
            double balance = totalCredits - totalDebits;
            balanceText.setText(String.format(Locale.getDefault(), "₹%.2f", balance));
        }
    }

    private void updateSummaryWithBudget(List<Transaction> transactions, double budget) {
        // Calculate total debits
        double totalDebits = 0;
        double totalCredits = 0;
        for (Transaction transaction : transactions) {
            if (!transaction.isExcludedFromTotal()) {
                if ("DEBIT".equals(transaction.getType())) {
                    totalDebits += transaction.getAmount();
                } else if ("CREDIT".equals(transaction.getType())) {
                    totalCredits += transaction.getAmount();
                }
            }
        }

        // Update basic summary
        if (totalDebitsText != null) {
            totalDebitsText.setText(String.format(Locale.getDefault(), "₹%.2f", totalDebits));
        }

        if (totalCreditsText != null) {
            totalCreditsText.setText(String.format(Locale.getDefault(), "₹%.2f", totalCredits));
        }

        // Calculate remaining balance
        double remainingBalance = budget - totalDebits;
        if (balanceText != null) {
            balanceText.setText(String.format(Locale.getDefault(), "₹%.2f", remainingBalance));
        }

        // Budget alert logic
        if (budget > 0) {
            double spendingPercentage = (totalDebits / budget) * 100;

            if (alertCard != null && alertText != null) {
                if (spendingPercentage > 70) {
                    alertCard.setVisibility(View.VISIBLE);
                    alertText.setText(String.format(Locale.getDefault(),
                            "Warning: You have spent %.1f%% of your monthly budget of ₹%.2f!",
                            spendingPercentage, budget));
                } else {
                    alertCard.setVisibility(View.GONE);
                }
            }
        }
    }

    private void resetBudgetUI(double budget) {
        // Reset UI when no budget is set or transactions are empty
        if (balanceText != null) {
            balanceText.setText(budget > 0 ?
                    String.format(Locale.getDefault(), "₹%.2f", budget) :
                    "₹0.00");
        }

        if (alertCard != null) {
            alertCard.setVisibility(View.GONE);
        }
    }

    private void setupDateRangeUI() {
        MaterialButton fromDateButton = findViewById(R.id.fromDateButton);
        MaterialButton toDateButton = findViewById(R.id.toDateButton);
        MaterialButton loadMessagesButton = findViewById(R.id.loadMessagesButton);

        if (fromDateButton != null) {
            fromDateButton.setOnClickListener(v -> showDatePicker(true));
        }

        if (toDateButton != null) {
            toDateButton.setOnClickListener(v -> showDatePicker(false));
        }

        // Save date range
        preferencesManager.saveSelectedDateRange(fromDate, toDate);

        if (loadMessagesButton != null) {
            loadMessagesButton.setOnClickListener(v -> {
                if (fromDate == 0 || toDate == 0) {
                    Toast.makeText(this, "Please select both dates", Toast.LENGTH_SHORT).show();
                    return;
                }
                loadExistingSMS();
            });
        }
    }

    private void showDatePicker(boolean isFromDate) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(isFromDate ? fromDate : toDate);

        DatePickerDialog datePickerDialog = new DatePickerDialog(this, (view, year, month, day) -> {
            Calendar selectedDate = Calendar.getInstance();
            selectedDate.set(year, month, day);

            if (isFromDate) {
                selectedDate.set(Calendar.HOUR_OF_DAY, 0);
                selectedDate.set(Calendar.MINUTE, 0);
                selectedDate.set(Calendar.SECOND, 0);
                selectedDate.set(Calendar.MILLISECOND, 0);
                fromDate = selectedDate.getTimeInMillis();
            } else {
                selectedDate.set(Calendar.HOUR_OF_DAY, 23);
                selectedDate.set(Calendar.MINUTE, 59);
                selectedDate.set(Calendar.SECOND, 59);
                selectedDate.set(Calendar.MILLISECOND, 999);
                toDate = selectedDate.getTimeInMillis();
            }

            updateDateButtonTexts();
            preferencesManager.saveSelectedDateRange(fromDate, toDate);

            // Call refreshTransactions instead of resetPagination
            refreshTransactions();

        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH));

        datePickerDialog.show();
    }

    private void updateDateButtonTexts() {
        SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());

        MaterialButton fromDateButton = findViewById(R.id.fromDateButton);
        if (fromDateButton != null) {
            fromDateButton.setText(dateFormat.format(new Date(fromDate)));
        }

        MaterialButton toDateButton = findViewById(R.id.toDateButton);
        if (toDateButton != null) {
            toDateButton.setText(dateFormat.format(new Date(toDate)));
        }
    }

    private void setupDefaultDates() {
        fromDate = preferencesManager.getFromDate();
        toDate = preferencesManager.getToDate();

        // If dates haven't been set before, use default dates
        if (fromDate == 0 || toDate == 0) {
            Calendar calendar = Calendar.getInstance();

            // Set To Date as current date
            calendar.set(Calendar.HOUR_OF_DAY, 23);
            calendar.set(Calendar.MINUTE, 59);
            calendar.set(Calendar.SECOND, 59);
            toDate = calendar.getTimeInMillis();

            // Set From Date as 1st of current month
            calendar.set(Calendar.DAY_OF_MONTH, 1);
            calendar.set(Calendar.HOUR_OF_DAY, 0);
            calendar.set(Calendar.MINUTE, 0);
            calendar.set(Calendar.SECOND, 0);
            fromDate = calendar.getTimeInMillis();

            // Save these dates
            preferencesManager.saveSelectedDateRange(fromDate, toDate);
        }

        // Update button texts
        updateDateButtonTexts();
    }

    private void loadExistingSMS() {
        // Make sure we have permissions before attempting to load SMS
        //checkAndRequestSMSPermissions();

        Log.d(TAG, "Loading SMS between dates: " + new Date(fromDate) + " to " + new Date(toDate));

        preferencesManager.saveSelectedDateRange(fromDate, toDate);

        if (loadingIndicator != null) {
            loadingIndicator.setVisibility(View.VISIBLE);
        }

        executorService.execute(() -> {
            // Process new SMS messages - refreshTransactions() is called with a delay inside this method
            processSMSMessages();

            // Hide loading indicator if still showing
            runOnUiThread(() -> {
                if (loadingIndicator != null) {
                    loadingIndicator.setVisibility(View.GONE);
                }
            });
        });
    }

    private void processSMSMessages() {
        String selection = Telephony.Sms.DATE + " BETWEEN ? AND ?";
        String[] selectionArgs = new String[]{
                String.valueOf(fromDate),
                String.valueOf(toDate)
        };

        Cursor cursor = null;
        try {
            cursor = getContentResolver().query(
                    Telephony.Sms.CONTENT_URI,
                    null,
                    selection,
                    selectionArgs,
                    Telephony.Sms.DATE + " DESC"
            );

            if (cursor != null) {
                EnhancedSMSReceiver receiver = new EnhancedSMSReceiver();
                int count = 0;
                while (cursor.moveToNext()) {
                    int bodyIndex = cursor.getColumnIndex(Telephony.Sms.BODY);
                    int dateIndex = cursor.getColumnIndex(Telephony.Sms.DATE);

                    // Skip if column indices are invalid
                    if (bodyIndex < 0 || dateIndex < 0) continue;

                    String messageBody = cursor.getString(bodyIndex);
                    long messageDate = cursor.getLong(dateIndex);

                    if (messageDate >= fromDate && messageDate <= toDate) {
                        receiver.parseAndSaveTransaction(this, messageBody, null, messageDate);
                        count++;
                    }
                }

                final int processedCount = count;
                runOnUiThread(() -> {
                    Toast.makeText(this, "Processed " + processedCount + " messages", Toast.LENGTH_SHORT).show();

                    int delayMs = Math.min(500 + (processedCount * 50), 5000);

                    // Add a slight delay to ensure database operations complete
                    new Handler().postDelayed(() -> {
                        // Now force a complete refresh of transactions
                        refreshTransactions();
                    }, delayMs); // 1 second delay to ensure transactions are saved
                });
            }
        } catch (Exception e) {
            Log.e(TAG, "Error processing SMS messages", e);
            runOnUiThread(() -> {
                Toast.makeText(this, "Error processing SMS: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            });
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    private void refreshTransactions() {
        // Reset filter state except for manually excluded view
        boolean wasViewingManuallyExcluded = currentFilterState.viewingManuallyExcluded;
        currentFilterState = new FilterState();
        currentFilterState.viewingManuallyExcluded = wasViewingManuallyExcluded;

        // Clear existing data
        allTransactions.clear();
        if (adapter != null) {
            adapter.clearTransactions();
        }

        // Use smart loading strategy to load transactions
        if (smartLoadingStrategy != null) {
            smartLoadingStrategy.loadTransactionsForDateRange(fromDate, toDate);
        }

        setupSpendingChart();
    }

    private void checkAndRequestSMSPermissions() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS) !=
                PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) !=
                        PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(
                    this,
                    new String[]{
                            Manifest.permission.RECEIVE_SMS,
                            Manifest.permission.READ_SMS
                    },
                    SMS_PERMISSION_REQUEST_CODE
            );
        }
    }

    private void setupBottomNavigation() {
        BottomNavigationView bottomNav = findViewById(R.id.bottomNavigation);
        if (bottomNav == null) return;

        bottomNav.setSelectedItemId(R.id.nav_home);
        bottomNav.setOnItemSelectedListener(item -> {
            switch (item.getItemId()) {
                case R.id.nav_home:
                    return true;
                case R.id.nav_analytics:
                    startActivity(new Intent(this, AnalyticsActivity.class));
                    return true;
                case R.id.nav_predictions:
                    startActivity(new Intent(this, PredictionActivity.class));
                    return true;
                case R.id.nav_groups:
                    startActivity(new Intent(this, GroupedExpensesActivity.class));
                    return true;
//                case R.id.nav_excluded:
//                    startActivity(new Intent(this, ExcludedTransactionsActivity.class));
//                    return true;
                case R.id.nav_patterns:
                    startActivity(new Intent(this, ExclusionPatternsActivity.class));
                    return true;
            }
            return false;
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == SMS_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "SMS permissions granted", Toast.LENGTH_SHORT).show();
                loadExistingSMS();
            } else {
                Toast.makeText(this, "SMS permissions are required for this app to work",
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        BottomNavigationView bottomNav = findViewById(R.id.bottomNavigation);
        bottomNav.setSelectedItemId(R.id.nav_home);

        // If we're viewing manually excluded transactions, refresh that view
        if (currentFilterState.viewingManuallyExcluded) {
            loadManuallyExcludedTransactions();
        } else {
            // Otherwise, refresh the regular transactions view
            resetPagination();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Shutdown the ExecutorService
        if (executorService != null) {
            executorService.shutdown();
        }
    }

    public static class FilterState {
        public String bank = "All Banks";
        public String type = "All Types";
        public String category = null;
        public String searchQuery = "";
        public double minAmount = 0;
        public double maxAmount = 100000;
        public boolean showingExcluded = false;
        public int sortOption = 0; // 0 = Date (newest first)
        private boolean filterManuallyExcluded = false; // NEW PROPERTY
        public boolean viewingManuallyExcluded = false;

        // Update the isAnyFilterActive method
        public boolean isAnyFilterActive() {
            return !bank.equals("All Banks") ||
                    !type.equals("All Types") ||
                    category != null ||
                    !searchQuery.isEmpty() ||
                    minAmount > 0 ||
                    maxAmount < 100000 ||
                    filterManuallyExcluded; // Include our new property here
        }

        // Update the applyFilters method to handle the new property
        public List<Transaction> applyFilters(List<Transaction> transactions) {
            if (transactions == null) return new ArrayList<>();

            List<Transaction> result = new ArrayList<>();

            for (Transaction transaction : transactions) {
                boolean shouldInclude = true;

                // Special case: If filtering manually excluded transactions
                if (filterManuallyExcluded) {
                    // Only include manually excluded transactions
                    shouldInclude = transaction.isExcludedFromTotal() && !transaction.isOtherDebit();

                    // No need to check other filters if this doesn't match
                    if (!shouldInclude) {
                        continue;
                    }
                }

                // Apply bank filter
                if (!bank.equals("All Banks") && !bank.equals(transaction.getBank())) {
                    shouldInclude = false;
                }

                // Apply type filter
                if (shouldInclude && !type.equals("All Types") && !type.equals(transaction.getType())) {
                    shouldInclude = false;
                }

                // Apply category filter (only if not filtering for manually excluded)
                if (shouldInclude && !filterManuallyExcluded && category != null && !category.isEmpty() &&
                        !category.equals(transaction.getCategory())) {
                    shouldInclude = false;
                }

                // Apply amount filter
                if (shouldInclude && (transaction.getAmount() < minAmount ||
                        transaction.getAmount() > maxAmount)) {
                    shouldInclude = false;
                }

                // Apply exclusion filter (but skip this check if we're explicitly filtering for manually excluded)
                if (shouldInclude && !filterManuallyExcluded && transaction.isExcludedFromTotal() && transaction.isOtherDebit() && !showingExcluded) {
                    shouldInclude = false;
                }

                // Apply search query
                if (shouldInclude && !searchQuery.isEmpty()) {
                    boolean matchesSearch = false;
                    String lowerQuery = searchQuery.toLowerCase();

                    // Check description
                    if (transaction.getDescription() != null &&
                            transaction.getDescription().toLowerCase().contains(lowerQuery)) {
                        matchesSearch = true;
                    }

                    // Check bank
                    if (!matchesSearch && transaction.getBank() != null &&
                            transaction.getBank().toLowerCase().contains(lowerQuery)) {
                        matchesSearch = true;
                    }

                    // Check category
                    if (!matchesSearch && transaction.getCategory() != null &&
                            transaction.getCategory().toLowerCase().contains(lowerQuery)) {
                        matchesSearch = true;
                    }

                    // Check merchant name
                    if (!matchesSearch && transaction.getMerchantName() != null &&
                            transaction.getMerchantName().toLowerCase().contains(lowerQuery)) {
                        matchesSearch = true;
                    }

                    // Check amount
                    if (!matchesSearch && String.valueOf(transaction.getAmount()).contains(lowerQuery)) {
                        matchesSearch = true;
                    }

                    shouldInclude = matchesSearch;
                }

                if (shouldInclude) {
                    result.add(transaction);
                }
            }

            // Apply current sort option
            sortTransactions(result, sortOption);

            return result;
        }
    }

    /**
     * Get the current from date
     * @return The from date timestamp
     */
    public long getFromDate() {
        return fromDate;
    }

    /**
     * Get the current to date
     * @return The to date timestamp
     */
    public long getToDate() {
        return toDate;
    }

    /**
     * Show dialog to choose grouping mode
     */
    private void showGroupingModeSelector() {
        // Get the current view mode and grouping mode
        boolean isGroupView = currentViewMode != ViewMode.LIST;
        int currentGroupingMode = 0; // Default to day grouping

        // Get grouping mode from SmartLoadingStrategy if available
        if (smartLoadingStrategy != null) {
            currentGroupingMode = smartLoadingStrategy.getGroupingMode();
        }

        // Create bottom sheet dialog
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View dialogView = getLayoutInflater().inflate(R.layout.group_mode_selector, null);
        dialog.setContentView(dialogView);

        // Get radio group and buttons
        RadioGroup radioGroup = dialogView.findViewById(R.id.viewModeRadioGroup);
        RadioButton listViewRadio = dialogView.findViewById(R.id.listViewRadio);
        RadioButton dayGroupRadio = dialogView.findViewById(R.id.dayGroupRadio);
        RadioButton weekGroupRadio = dialogView.findViewById(R.id.weekGroupRadio);
        RadioButton monthGroupRadio = dialogView.findViewById(R.id.monthGroupRadio);

        // Set initial selection based on current view mode
        if (!isGroupView) {
            listViewRadio.setChecked(true);
        } else {
            switch (currentGroupingMode) {
                case 0: // Day grouping
                    dayGroupRadio.setChecked(true);
                    break;
                case 1: // Week grouping
                    weekGroupRadio.setChecked(true);
                    break;
                case 2: // Month grouping
                    monthGroupRadio.setChecked(true);
                    break;
            }
        }

        // Set radio button change listener
        radioGroup.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == listViewRadio.getId()) {
                // Switch to list view
                updateViewMode(ViewMode.LIST);
                // Save preference
                preferencesManager.saveViewMode(false);
            } else {
                // Determine grouping mode based on selected radio button
                int groupingMode = 0; // Default to day grouping
                ViewMode newViewMode = ViewMode.GROUP_BY_DAY;

                if (checkedId == weekGroupRadio.getId()) {
                    groupingMode = 1;
                    newViewMode = ViewMode.GROUP_BY_WEEK;
                } else if (checkedId == monthGroupRadio.getId()) {
                    groupingMode = 2;
                    newViewMode = ViewMode.GROUP_BY_MONTH;
                }

                // Update view mode and grouping mode
                currentViewMode = newViewMode;
                if (smartLoadingStrategy != null) {
                    smartLoadingStrategy.setGroupingMode(groupingMode);
                    smartLoadingStrategy.setForceViewMode(true);
                }

                // Save preferences
                preferencesManager.saveViewMode(true);
                preferencesManager.saveGroupingMode(groupingMode);

                // Update UI
                updateViewModeButtonAppearance();
            }

            dialog.dismiss();
        });

        dialog.show();
    }

    /**
     * Update the current view mode
     */
    private void updateViewMode(ViewMode viewMode) {
        // Only process if we're actually changing the mode
        if (currentViewMode == viewMode) {
            return; // No change needed
        }

        currentViewMode = viewMode;

        // Update button appearance
        updateViewModeButtonAppearance();

        // Update SmartLoadingStrategy
        if (smartLoadingStrategy != null) {
            boolean isGroupView = viewMode != ViewMode.LIST;
            smartLoadingStrategy.setForceViewMode(isGroupView);

            // Set appropriate grouping mode based on view mode
            if (isGroupView) {
                int groupingMode = 0; // Default to day grouping

                if (viewMode == ViewMode.GROUP_BY_WEEK) {
                    groupingMode = 1;
                } else if (viewMode == ViewMode.GROUP_BY_MONTH) {
                    groupingMode = 2;
                }

                smartLoadingStrategy.setGroupingMode(groupingMode);
            }

            // Reload data
            refreshTransactions();
        }
    }
}