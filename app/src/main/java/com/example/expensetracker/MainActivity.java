package com.example.expensetracker;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;
import android.view.KeyEvent;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
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
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.example.expensetracker.adapters.DateGroupedTransactionAdapter;
import com.example.expensetracker.adapters.TransactionAdapter;
import com.example.expensetracker.database.TransactionDatabase;
import com.example.expensetracker.dialogs.TransactionEditDialog;
import com.example.expensetracker.models.Transaction;
import com.example.expensetracker.receivers.EnhancedSMSReceiver;
import com.example.expensetracker.ui.ChartMarkerView;
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

    private FilterState currentFilterState = new FilterState();
    private SmartLoadingStrategy smartLoadingStrategy;
    private MaterialButton viewModeButton;
    private ViewMode currentViewMode = ViewMode.LIST; // Default to list mode

    private enum ViewMode {
        LIST,              // Individual transactions with pagination
        GROUP_BY_DAY,      // Grouped by day
        GROUP_BY_WEEK,     // Grouped by week
        GROUP_BY_MONTH,    // Grouped by month
        GROUP_BY_CATEGORY, // Grouped by expense category
        GROUP_BY_MERCHANT, // Grouped by merchant/vendor
        GROUP_BY_AMOUNT_RANGE, // Grouped by amount ranges
        GROUP_BY_BANK      // Grouped by bank
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

        // Set up components
        setupSpendingChart();
        setupCollapsibleSpendingChart();

        setupDateRangeChips();
        setupCategoryFilter();
        setupBudgetFab();
        setupSort();
        setupExpandableSearch();

        // Check permissions and setup navigation
        checkAndRequestSMSPermissions();
        setupBottomNavigation();

        // Initialize the SmartLoadingStrategy
        initializeSmartLoadingStrategy();

        // Load user's view mode preference
        boolean preferGroupedView = preferencesManager.getViewModePreference();
        int groupingMode = preferencesManager.getGroupingModePreference();

        // Restore saved sort option
        int savedSortOption = preferencesManager.getSortOption();
        currentFilterState.sortOption = savedSortOption;

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

        // Log the initial view mode
        Log.d(TAG, "Starting with " + (preferencesManager.getViewModePreference() ? "grouped" : "list") + " view based on user preference");
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
                // Reset filter state
                currentFilterState = new FilterState();

                // Reset sort in preferences
                preferencesManager.saveSortOption(0);

                // Hide filter indicator
                if (filterIndicatorContainer != null) {
                    filterIndicatorContainer.setVisibility(View.GONE);
                }

                // Uncheck category chips
                ChipGroup categoryFilterChipGroup = findViewById(R.id.categoryFilterChipGroup);
                if (categoryFilterChipGroup != null) {
                    categoryFilterChipGroup.clearCheck();

                    // Select "All Categories" chip if it exists
                    for (int i = 0; i < categoryFilterChipGroup.getChildCount(); i++) {
                        View child = categoryFilterChipGroup.getChildAt(i);
                        if (child instanceof Chip) {
                            Chip chip = (Chip) child;
                            if ("All Categories".equals(chip.getText().toString())) {
                                chip.setChecked(true);
                                break;
                            }
                        }
                    }
                }

                // Clear search box
                if (searchInput != null) {
                    searchInput.setText("");
                }

                // Reload data with no filters
                if (smartLoadingStrategy != null) {
                    smartLoadingStrategy.updateFilterState(currentFilterState);
                    smartLoadingStrategy.refreshData(fromDate, toDate);
                }
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
            case GROUP_BY_CATEGORY:
                menu.findItem(R.id.menu_category_view).setChecked(true);
                break;
            case GROUP_BY_MERCHANT:
                menu.findItem(R.id.menu_merchant_view).setChecked(true);
                break;
            case GROUP_BY_AMOUNT_RANGE:
                menu.findItem(R.id.menu_amount_view).setChecked(true);
                break;
            case GROUP_BY_BANK:
                menu.findItem(R.id.menu_bank_view).setChecked(true);
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
                    groupingMode = DateGroupedTransactionAdapter.GROUP_BY_DAY;
                    newViewMode = ViewMode.GROUP_BY_DAY;
                    break;

                case R.id.menu_week_view:
                    groupingMode = DateGroupedTransactionAdapter.GROUP_BY_WEEK;
                    newViewMode = ViewMode.GROUP_BY_WEEK;
                    break;

                case R.id.menu_month_view:
                    groupingMode = DateGroupedTransactionAdapter.GROUP_BY_MONTH;
                    newViewMode = ViewMode.GROUP_BY_MONTH;
                    break;

                case R.id.menu_category_view:
                    groupingMode = DateGroupedTransactionAdapter.GROUP_BY_CATEGORY;
                    newViewMode = ViewMode.GROUP_BY_CATEGORY;
                    break;

                case R.id.menu_merchant_view:
                    groupingMode = DateGroupedTransactionAdapter.GROUP_BY_MERCHANT;
                    newViewMode = ViewMode.GROUP_BY_MERCHANT;
                    break;

                case R.id.menu_amount_view:
                    groupingMode = DateGroupedTransactionAdapter.GROUP_BY_AMOUNT_RANGE;
                    newViewMode = ViewMode.GROUP_BY_AMOUNT_RANGE;
                    break;

                case R.id.menu_bank_view:
                    groupingMode = DateGroupedTransactionAdapter.GROUP_BY_BANK;
                    newViewMode = ViewMode.GROUP_BY_BANK;
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
            case GROUP_BY_CATEGORY:
                viewModeButton.setIcon(getDrawable(R.drawable.ic_category));
                viewModeButton.setText("Category View");
                break;
            case GROUP_BY_MERCHANT:
                viewModeButton.setIcon(getDrawable(R.drawable.ic_store));
                viewModeButton.setText("Merchant View");
                break;
            case GROUP_BY_AMOUNT_RANGE:
                viewModeButton.setIcon(getDrawable(R.drawable.ic_money));
                viewModeButton.setText("Amount View");
                break;
            case GROUP_BY_BANK:
                viewModeButton.setIcon(getDrawable(R.drawable.ic_bank));
                viewModeButton.setText("Bank View");
                break;
        }
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

            // Update in smart loading strategy
            if (smartLoadingStrategy != null) {
                smartLoadingStrategy.updateTransactionInAdapters(editedTransaction);
            }

            // Show a confirmation
            Toast.makeText(this, "Transaction updated", Toast.LENGTH_SHORT).show();
        });

        // Show the dialog
        dialog.show(getSupportFragmentManager(), "edit_transaction");
    }

    /**
     * Set up the expandable search functionality with animations
     */
    private void setupExpandableSearch() {
        // Find views
        View collapsedSearchView = findViewById(R.id.collapsedSearchView);
        MaterialCardView expandedSearchView = findViewById(R.id.expandedSearchView);
        EditText searchInput = findViewById(R.id.searchInput);
        ImageButton clearSearchButton = findViewById(R.id.clearSearchButton);

        // Other buttons that will be temporarily hidden when search expands
        MaterialButton viewModeButton = findViewById(R.id.viewModeButton);
        MaterialButton filterButton = findViewById(R.id.filterButton);

        // Load animations
        Animation fadeIn = AnimationUtils.loadAnimation(this, R.anim.fade_in);
        Animation fadeOut = AnimationUtils.loadAnimation(this, R.anim.fade_out);
        Animation slideInRight = AnimationUtils.loadAnimation(this, R.anim.slide_in_right);
        Animation slideOutLeft = AnimationUtils.loadAnimation(this, R.anim.slide_out_left);

        // Helper method to expand search
        View.OnClickListener expandSearchListener = v -> {
            // Hide collapsed view with animation
            collapsedSearchView.startAnimation(fadeOut);
            collapsedSearchView.setVisibility(View.GONE);

            // Show expanded view with animation
            expandedSearchView.setVisibility(View.VISIBLE);
            expandedSearchView.startAnimation(slideInRight);

            // Focus the search input
            searchInput.requestFocus();

            // Show keyboard
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.showSoftInput(searchInput, InputMethodManager.SHOW_IMPLICIT);

            // Hide/modify other buttons to make room for search
            viewModeButton.startAnimation(fadeOut);
            viewModeButton.setVisibility(View.GONE);

            // Keep filter button but remove text
            filterButton.setText("");
        };

        // Click listener for the collapsed search icon
        collapsedSearchView.setOnClickListener(expandSearchListener);

        // NEW: Also make the expanded search view's search icon clickable to collapse
        ImageView expandedSearchIcon = expandedSearchView.findViewById(R.id.expandedSearchIcon);
        if (expandedSearchIcon != null) {
            expandedSearchIcon.setOnClickListener(v -> {
                // Collapse search regardless of text content
                collapseSearch(fadeIn, slideOutLeft, true);
            });
        }

        // Set up clear button for search
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                // Show clear button if there's text
                if (s.length() > 0 && clearSearchButton.getVisibility() != View.VISIBLE) {
                    clearSearchButton.setVisibility(View.VISIBLE);
                    clearSearchButton.startAnimation(fadeIn);
                } else if (s.length() == 0 && clearSearchButton.getVisibility() == View.VISIBLE) {
                    clearSearchButton.startAnimation(fadeOut);
                    clearSearchButton.setVisibility(View.GONE);
                }

                // Apply search filter
                filterTransactions(s.toString());
            }
        });

        // Set up clear button click
        clearSearchButton.setOnClickListener(v -> {
            searchInput.setText("");
            clearSearchButton.startAnimation(fadeOut);
            clearSearchButton.setVisibility(View.GONE);

            // Also collapse search when text is cleared
            collapseSearch(fadeIn, slideOutLeft, true);
        });

        // Handle back button to collapse search
        searchInput.setOnKeyListener((v, keyCode, event) -> {
            if (keyCode == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_UP) {
                // Collapse search
                collapseSearch(fadeIn, slideOutLeft, true);
                return true;
            }
            return false;
        });

        // Focus change listener to collapse search when focus is lost
        searchInput.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus && searchInput.getText().toString().isEmpty()) {
                collapseSearch(fadeIn, slideOutLeft, false);
            }
        });
    }

    /**
     * Collapse the search view with animations
     * @param forceCollapse When true, collapse even if there's search text
     */
    private void collapseSearch(Animation fadeIn, Animation slideOutLeft, boolean forceCollapse) {
        View collapsedSearchView = findViewById(R.id.collapsedSearchView);
        MaterialCardView expandedSearchView = findViewById(R.id.expandedSearchView);
        EditText searchInput = findViewById(R.id.searchInput);
        MaterialButton viewModeButton = findViewById(R.id.viewModeButton);
        MaterialButton filterButton = findViewById(R.id.filterButton);

        // Hide keyboard
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(searchInput.getWindowToken(), 0);

        // Only collapse if there's no search text or if forced
        if (searchInput.getText().toString().isEmpty() || forceCollapse) {
            // Hide expanded view with animation
            expandedSearchView.startAnimation(slideOutLeft);
            expandedSearchView.setVisibility(View.GONE);

            // Show collapsed view with animation
            collapsedSearchView.setVisibility(View.VISIBLE);
            collapsedSearchView.startAnimation(fadeIn);

            // Restore other buttons with animation
            viewModeButton.setVisibility(View.VISIBLE);
            viewModeButton.startAnimation(fadeIn);

            // Restore filter button text
            filterButton.setText("Filter");
        }
    }

    private void filterTransactions(String query) {
        // Update filter state
        currentFilterState.searchQuery = query;

        // Update filter indicator UI immediately
        if (filterIndicatorContainer != null) {
            if (!query.isEmpty()) {
                filterIndicatorContainer.setVisibility(View.VISIBLE);
                filterIndicator.setText("Search: " + query);

                if (resultCount != null) {
                    resultCount.setText("Loading...");
                }
            } else if (!currentFilterState.isAnyFilterActive()) {
                filterIndicatorContainer.setVisibility(View.GONE);
            }
        }

        // Use the smart loading strategy to load filtered data
        if (smartLoadingStrategy != null) {
            smartLoadingStrategy.updateFilterState(currentFilterState);
            smartLoadingStrategy.refreshData(fromDate, toDate);
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
        // Save sort preference
        preferencesManager.saveSortOption(sortOption);

        // Use smart loading strategy to apply sort
        if (smartLoadingStrategy != null) {
            smartLoadingStrategy.updateFilterState(currentFilterState);
            smartLoadingStrategy.refreshData(fromDate, toDate);
        }
    }

    private void setupCategoryFilter() {
        ChipGroup categoryFilterChipGroup = findViewById(R.id.categoryFilterChipGroup);
        if (categoryFilterChipGroup == null) {
            Log.d(TAG, "Category filter chip group not found in layout");
            return;
        }
        // Configure the ChipGroup
        categoryFilterChipGroup.setSelectionRequired(false); // Allow deselection
        categoryFilterChipGroup.setSingleSelection(true);    // Only one chip can be selected

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
                // No chip selected, clear filter
                clearCategoryFilter();
                return;
            }

            Chip selectedChip = findViewById(checkedId);
            if (selectedChip != null) {
                String selectedCategory = selectedChip.getText().toString();

                // If "All Categories" is selected, clear the category filter
                if ("All Categories".equals(selectedCategory)) {
                    clearCategoryFilter();
                }
                // Special handling for "Manually Excluded" filter
                else if ("Manually Excluded".equals(selectedCategory)) {
                    loadManuallyExcludedTransactions();
                }
                // Regular category filtering
                else {
                    filterTransactionsByCategory(selectedCategory);
                }
            }
        });
    }

    private void loadManuallyExcludedTransactions() {
        // Update filter state
        currentFilterState.viewingManuallyExcluded = true;

        // Update filter indicator UI immediately
        if (filterIndicatorContainer != null) {
            filterIndicatorContainer.setVisibility(View.VISIBLE);
            filterIndicator.setText("Viewing: Manually Excluded Transactions");

            if (resultCount != null) {
                resultCount.setText("Loading...");
            }
        }

        // Use the smart loading strategy to load manually excluded transactions
        if (smartLoadingStrategy != null) {
            smartLoadingStrategy.updateFilterState(currentFilterState);
            smartLoadingStrategy.refreshData(fromDate, toDate);
        }
    }

    private void filterTransactionsByCategory(String category) {
        // Update filter state
        currentFilterState.category = category;
        currentFilterState.viewingManuallyExcluded = false;

        // Show or hide filter indicator based on category
        if (filterIndicatorContainer != null) {
            if (category != null && !category.isEmpty()) {
                // Show filter indicator with category info
                filterIndicatorContainer.setVisibility(View.VISIBLE);
                filterIndicator.setText("Filtered by category: " + category);

                if (resultCount != null) {
                    resultCount.setText("Loading...");
                }
            } else if (!currentFilterState.isAnyFilterActive()) {
                // Hide filter indicator if no other filters are active
                filterIndicatorContainer.setVisibility(View.GONE);
            }
        }

        // Use smart loading strategy to load filtered data
        if (smartLoadingStrategy != null) {
            smartLoadingStrategy.updateFilterState(currentFilterState);
            smartLoadingStrategy.refreshData(fromDate, toDate);
        }
    }

    private void clearCategoryFilter() {
        // Reset category-related filter state
        currentFilterState.category = null;
        currentFilterState.viewingManuallyExcluded = false;

        // Update UI if needed
        if (!currentFilterState.isAnyFilterActive() && filterIndicatorContainer != null) {
            filterIndicatorContainer.setVisibility(View.GONE);
        } else if (filterIndicatorContainer != null && filterIndicatorContainer.getVisibility() == View.VISIBLE) {
            // Update filter indicator text if other filters are still active
            StringBuilder filterDesc = new StringBuilder("Filtered by: ");
            boolean hasFilter = false;

            if (!"All Banks".equals(currentFilterState.bank)) {
                filterDesc.append(currentFilterState.bank);
                hasFilter = true;
            }

            if (!"All Types".equals(currentFilterState.type)) {
                if (hasFilter) filterDesc.append(", ");
                filterDesc.append(currentFilterState.type);
                hasFilter = true;
            }

            if (!currentFilterState.searchQuery.isEmpty()) {
                if (hasFilter) filterDesc.append(", ");
                filterDesc.append("Search: ").append(currentFilterState.searchQuery);
            }

            filterIndicator.setText(filterDesc.toString());
        }

        // Refresh data
        if (smartLoadingStrategy != null) {
            smartLoadingStrategy.updateFilterState(currentFilterState);
            smartLoadingStrategy.refreshData(fromDate, toDate);
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

    // Helper method to determine contrasting text color
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
                    setDateRangeThisMonth();
                }
            });
        }

        View chipYesterday = findViewById(R.id.chipYesterday);
        if (chipYesterday != null) {
            chipYesterday.setOnClickListener(v -> {
                setDateRangeYesterday();
                if (!((Chip)v).isChecked()) {
                    dateRangeChipGroup.clearCheck();
                    setDateRangeThisMonth();
                }
            });
        }

        View chipThisWeek = findViewById(R.id.chipThisWeek);
        if (chipThisWeek != null) {
            chipThisWeek.setOnClickListener(v -> {
                setDateRangeThisWeek();
                if (!((Chip)v).isChecked()) {
                    dateRangeChipGroup.clearCheck();
                    setDateRangeThisMonth();
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
                    setDateRangeThisMonth();
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
        preferencesManager.saveSelectedDateRange(fromDate, toDate);
        refreshTransactions();
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
        preferencesManager.saveSelectedDateRange(fromDate, toDate);
        refreshTransactions();
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
        preferencesManager.saveSelectedDateRange(fromDate, toDate);
        refreshTransactions();
        //checkForTransactionsAndLoad();

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
        preferencesManager.saveSelectedDateRange(fromDate, toDate);
        refreshTransactions();

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

        // Also save the date range
        preferencesManager.saveSelectedDateRange(fromDate, toDate);
        // Instead of just calling resetPagination(), call refreshTransactions()
        // which more thoroughly refreshes the data
        refreshTransactions();
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

//    private void setupCollapsibleSpendingChart() {
//        // Find the card and chart views
//        MaterialCardView spendingChartCard = findViewById(R.id.spendingChartCard);
//        LineChart spendingLineChart = findViewById(R.id.spendingLineChart);
//
//        // Need to add a header layout with a title and toggle button
//        LinearLayout chartHeader = findViewById(R.id.chartHeaderLayout);
//        ImageView toggleIcon = findViewById(R.id.chartToggleIcon);
//
//        // Get saved state from preferences
//        boolean isExpanded = preferencesManager.isChartExpanded();
//
//        // Initialize visibility based on saved state
//        spendingLineChart.setVisibility(isExpanded ? View.VISIBLE : View.GONE);
//        toggleIcon.setImageResource(isExpanded ? R.drawable.ic_expand_less : R.drawable.ic_expand_more);
//
//        // Load animations
//        Animation slideIn = AnimationUtils.loadAnimation(this, R.anim.slide_down);
//        Animation slideOut = AnimationUtils.loadAnimation(this, R.anim.slide_up);
//
//        // Set click listener on the header to toggle chart visibility
//        chartHeader.setOnClickListener(v -> {
//            boolean willBeExpanded = spendingLineChart.getVisibility() != View.VISIBLE;
//
//            if (!willBeExpanded) {
//                // Collapse the chart with animation
//                spendingLineChart.startAnimation(slideOut);
//                slideOut.setAnimationListener(new Animation.AnimationListener() {
//                    @Override
//                    public void onAnimationStart(Animation animation) {}
//
//                    @Override
//                    public void onAnimationEnd(Animation animation) {
//                        spendingLineChart.setVisibility(View.GONE);
//                    }
//
//                    @Override
//                    public void onAnimationRepeat(Animation animation) {}
//                });
//                toggleIcon.setImageResource(R.drawable.ic_expand_more);
//            } else {
//                // Expand the chart with animation
//                spendingLineChart.setVisibility(View.VISIBLE);
//                spendingLineChart.startAnimation(slideIn);
//                toggleIcon.setImageResource(R.drawable.ic_expand_less);
//
//                // Refresh chart data when expanding
//                updateSpendingChart();
//            }
//
//            // Save the new state
//            preferencesManager.saveChartExpandedState(willBeExpanded);
//        });
//    }

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

        // Setup filter dropdowns
        ArrayAdapter<String> bankAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_dropdown_item_1line,
                viewModel.getAvailableBanks()
        );
        bankFilterDropdown.setAdapter(bankAdapter);
        bankFilterDropdown.setText(currentFilterState.bank, false);

        ArrayAdapter<String> typeAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_dropdown_item_1line,
                new String[]{"All Types", "DEBIT", "CREDIT"}
        );
        typeFilterDropdown.setAdapter(typeAdapter);
        typeFilterDropdown.setText(currentFilterState.type, false);

        ArrayAdapter<String> categoryAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_dropdown_item_1line,
                Transaction.Categories.getAllCategories()
        );
        categoryFilterDropdown.setAdapter(categoryAdapter);
        if (currentFilterState.category != null) {
            categoryFilterDropdown.setText(currentFilterState.category, false);
        }

        // Set amount range slider
        amountRangeSlider.setValueFrom(0f);
        amountRangeSlider.setValueTo(100000f);

        // Set initial values
        List<Float> initialValues = new ArrayList<>();
        initialValues.add((float)currentFilterState.minAmount);
        initialValues.add((float)currentFilterState.maxAmount);
        amountRangeSlider.setValues(initialValues);

        // Update amount range text
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

            // Apply filters
            applyAdvancedFilters(bank, type, category, minAmount, maxAmount, showExcluded);
            dialog.dismiss();
        });

        resetButton.setOnClickListener(v -> {
            // Reset all filters
            currentFilterState = new FilterState();

            // Hide filter indicators
            if (filterIndicatorContainer != null) {
                filterIndicatorContainer.setVisibility(View.GONE);
            }

            // Update SmartLoadingStrategy
            if (smartLoadingStrategy != null) {
                smartLoadingStrategy.updateFilterState(currentFilterState);
                smartLoadingStrategy.refreshData(fromDate, toDate);
            }

            dialog.dismiss();
        });

        dialog.show();
    }

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

        // Prepare filter description to display
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

        // Update filter indicator UI immediately
        if (filterIndicatorContainer != null) {
            if (currentFilterState.isAnyFilterActive()) {
                filterIndicatorContainer.setVisibility(View.VISIBLE);
                filterIndicator.setText(filterDesc.toString());

                if (resultCount != null) {
                    resultCount.setText("Loading...");
                }
            } else {
                filterIndicatorContainer.setVisibility(View.GONE);
            }
        }

        // Use the smart loading strategy to apply filters
        if (smartLoadingStrategy != null) {
            smartLoadingStrategy.updateFilterState(currentFilterState);
            smartLoadingStrategy.refreshData(fromDate, toDate);
        }
    }

    // Update the setupSpendingChart method in MainActivity.java
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

        // Set custom "no data" text and appearance
        spendingLineChart.setNoDataText("No spending data available");
        spendingLineChart.setNoDataTextColor(getColor(R.color.text_secondary));

        // Load data for the chart
        updateSpendingChart();

        // Add month selector if date range is long
        setupMonthSelector();
    }

    // Make sure setupCollapsibleSpendingChart method is properly maintained
    private void setupCollapsibleSpendingChart() {
        // Find the card and chart views
        MaterialCardView spendingChartCard = findViewById(R.id.spendingChartCard);
        LineChart spendingLineChart = findViewById(R.id.spendingLineChart);

        // Need to add a header layout with a title and toggle button
        LinearLayout chartHeader = findViewById(R.id.chartHeaderLayout);
        ImageView toggleIcon = findViewById(R.id.chartToggleIcon);

        if (chartHeader == null || toggleIcon == null) {
            Log.e(TAG, "Chart header elements not found in layout");
            return;
        }

        // Get saved state from preferences
        boolean isExpanded = preferencesManager.isChartExpanded();

        // Initialize visibility based on saved state
        if (spendingLineChart != null) {
            spendingLineChart.setVisibility(isExpanded ? View.VISIBLE : View.GONE);
        }
        if (toggleIcon != null) {
            toggleIcon.setImageResource(isExpanded ? R.drawable.ic_expand_less : R.drawable.ic_expand_more);
        }

        // Load animations
        Animation slideIn = AnimationUtils.loadAnimation(this, R.anim.slide_down);
        Animation slideOut = AnimationUtils.loadAnimation(this, R.anim.slide_up);

        // Set click listener on the header to toggle chart visibility
        chartHeader.setOnClickListener(v -> {
            if (spendingLineChart == null) return;

            boolean willBeExpanded = spendingLineChart.getVisibility() != View.VISIBLE;

            if (!willBeExpanded) {
                // Collapse the chart with animation
                spendingLineChart.startAnimation(slideOut);
                slideOut.setAnimationListener(new Animation.AnimationListener() {
                    @Override
                    public void onAnimationStart(Animation animation) {}

                    @Override
                    public void onAnimationEnd(Animation animation) {
                        spendingLineChart.setVisibility(View.GONE);
                    }

                    @Override
                    public void onAnimationRepeat(Animation animation) {}
                });
                if (toggleIcon != null) {
                    toggleIcon.setImageResource(R.drawable.ic_expand_more);
                }
            } else {
                // Expand the chart with animation
                spendingLineChart.setVisibility(View.VISIBLE);
                spendingLineChart.startAnimation(slideIn);
                if (toggleIcon != null) {
                    toggleIcon.setImageResource(R.drawable.ic_expand_less);
                }

                // Refresh chart data when expanding
                updateSpendingChart();
            }

            // Save the new state
            preferencesManager.saveChartExpandedState(willBeExpanded);
        });
    }

    // Add this method to MainActivity.java
    // Update this method in MainActivity.java to include preferences
    private void setupMonthSelector() {
        // Calculate date range duration in days
        long dateRangeDurationMillis = toDate - fromDate;
        long dateRangeDays = dateRangeDurationMillis / (24 * 60 * 60 * 1000);

        // Get references to the month selector components
        LinearLayout monthSelectorLayout = findViewById(R.id.monthSelectorLayout);
        Spinner monthSpinner = findViewById(R.id.monthSpinner);

        // Only show month selector if date range is more than 30 days
        if (dateRangeDays > 30 && monthSelectorLayout != null && monthSpinner != null) {
            // Make month selector visible
            monthSelectorLayout.setVisibility(View.VISIBLE);

            // Prepare months list from the date range
            List<MonthOption> monthOptions = generateMonthOptions(fromDate, toDate);

            // Add "All Months" option
            monthOptions.add(0, new MonthOption(0, 0, "All Months"));

            // Create adapter for the spinner
            MonthSpinnerAdapter adapter = new MonthSpinnerAdapter(this, monthOptions);
            monthSpinner.setAdapter(adapter);

            // Determine the initial selection based on saved preferences
            int initialPosition = 0; // Default to "All Months"

            if (preferencesManager.hasSelectedChartMonth()) {
                int savedYear = preferencesManager.getSelectedChartYear();
                int savedMonth = preferencesManager.getSelectedChartMonth();

                // Find the matching option
                for (int i = 1; i < monthOptions.size(); i++) {
                    MonthOption option = monthOptions.get(i);
                    if (option.year == savedYear && option.month == savedMonth) {
                        initialPosition = i;
                        break;
                    }
                }
            }

            // Set initial selection (without triggering listener)
            if (initialPosition < monthOptions.size()) {
                monthSpinner.setSelection(initialPosition, false);
            }

            // Set listener for month selection changes
            monthSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    MonthOption selected = monthOptions.get(position);

                    if (position == 0) {
                        // "All Months" option - use full date range
                        preferencesManager.clearChartMonthSelection();
                        updateSpendingChart();
                    } else {
                        // Specific month selected - filter chart data
                        preferencesManager.saveChartMonthSelection(selected.year, selected.month);
                        updateSpendingChartForMonth(selected.year, selected.month);
                    }
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {
                    // Do nothing
                }
            });

            // Apply the saved selection by updating the chart
            if (initialPosition > 0) {
                // If a specific month was saved, update chart directly
                MonthOption savedOption = monthOptions.get(initialPosition);
                updateSpendingChartForMonth(savedOption.year, savedOption.month);
            }
        } else if (monthSelectorLayout != null) {
            // Hide month selector for shorter date ranges
            monthSelectorLayout.setVisibility(View.GONE);

            // For shorter date ranges, always clear any saved month selection
            preferencesManager.clearChartMonthSelection();
        }
    }

    // Update the spendingChart method to accept month filtering parameters
    private void updateSpendingChartForMonth(int year, int month) {
        LineChart spendingLineChart = findViewById(R.id.spendingLineChart);
        if (spendingLineChart == null) return;

        // Check if chart is currently visible
        if (spendingLineChart.getVisibility() != View.VISIBLE) {
            // Don't update data if chart is collapsed
            return;
        }

        // Create calendar for the specified month
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.YEAR, year);
        cal.set(Calendar.MONTH, month);
        cal.set(Calendar.DAY_OF_MONTH, 1);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        long monthStartDate = cal.getTimeInMillis();

        // Move to the end of the month
        cal.add(Calendar.MONTH, 1);
        cal.add(Calendar.MILLISECOND, -1);
        long monthEndDate = cal.getTimeInMillis();

        // Show loading indicator
        if (loadingIndicator != null) {
            loadingIndicator.setVisibility(View.VISIBLE);
        }

        // Get transactions for the selected month
        executorService.execute(() -> {
            List<Transaction> chartTransactions = TransactionDatabase.getInstance(this)
                    .transactionDao()
                    .getTransactionsBetweenDatesSyncAscending(monthStartDate, monthEndDate);

            updateChartWithTransactions(chartTransactions, spendingLineChart);
        });
    }

    // Update the original updateSpendingChart method to use a common method for updating the chart
    private void updateSpendingChart() {
        LineChart spendingLineChart = findViewById(R.id.spendingLineChart);
        if (spendingLineChart == null) return;

        // Check if chart is currently visible
        if (spendingLineChart.getVisibility() != View.VISIBLE) {
            // Don't update data if chart is collapsed
            return;
        }

        // Start date is beginning of selected date range
        long chartEndDate = toDate;
        long chartStartDate = fromDate;

        // Show loading indicator
        if (loadingIndicator != null) {
            loadingIndicator.setVisibility(View.VISIBLE);
        }

        // Get transactions for chart date range
        executorService.execute(() -> {
            List<Transaction> chartTransactions = TransactionDatabase.getInstance(this)
                    .transactionDao()
                    .getTransactionsBetweenDatesSyncAscending(chartStartDate, chartEndDate);

            updateChartWithTransactions(chartTransactions, spendingLineChart);
        });
    }

    // Common method for updating chart with transactions
    private void updateChartWithTransactions(List<Transaction> chartTransactions, LineChart spendingLineChart) {
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

        // Hide loading indicator
        runOnUiThread(() -> {
            if (loadingIndicator != null) {
                loadingIndicator.setVisibility(View.GONE);
            }
        });

        // Update chart on UI thread
        runOnUiThread(() -> {
            if (entries.isEmpty()) {
                // No data to display
                spendingLineChart.clear();
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

            // Make the data points clickable
            dataSet.setHighlightEnabled(true);
            dataSet.setDrawHighlightIndicators(true);
            dataSet.setHighLightColor(getColor(R.color.primary));

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

            // Configure chart interaction
            spendingLineChart.setTouchEnabled(true);
            spendingLineChart.setDragEnabled(true);
            spendingLineChart.setScaleEnabled(true);
            spendingLineChart.setPinchZoom(true);

            // Create and set custom marker view
            ChartMarkerView markerView = new ChartMarkerView(this, R.layout.chart_marker_view, dateLabels);
            markerView.setChartView(spendingLineChart);
            spendingLineChart.setMarker(markerView);

            // Refresh chart
            spendingLineChart.invalidate();
            spendingLineChart.animateX(1000);
        });
    }

    // Helper class for month options in the dropdown
    private class MonthOption {
        int year;
        int month;
        String label;

        MonthOption(int year, int month, String label) {
            this.year = year;
            this.month = month;
            this.label = label;
        }

        // Label getter for the adapter
        @Override
        public String toString() {
            return label;
        }
    }

    // Custom adapter for the month spinner
    private class MonthSpinnerAdapter extends ArrayAdapter<MonthOption> {
        public MonthSpinnerAdapter(Context context, List<MonthOption> options) {
            super(context, android.R.layout.simple_spinner_item, options);
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        }
    }

    // Helper method to generate month options from a date range
    private List<MonthOption> generateMonthOptions(long fromDate, long toDate) {
        List<MonthOption> options = new ArrayList<>();

        // Create calendars for start and end dates
        Calendar startCal = Calendar.getInstance();
        startCal.setTimeInMillis(fromDate);

        Calendar endCal = Calendar.getInstance();
        endCal.setTimeInMillis(toDate);

        // Clear day, hour, etc. to work with whole months
        startCal.set(Calendar.DAY_OF_MONTH, 1);
        startCal.set(Calendar.HOUR_OF_DAY, 0);
        startCal.set(Calendar.MINUTE, 0);
        startCal.set(Calendar.SECOND, 0);
        startCal.set(Calendar.MILLISECOND, 0);

        // Format for month labels
        SimpleDateFormat monthFormat = new SimpleDateFormat("MMMM yyyy", Locale.getDefault());

        // Add an option for each month in the range
        Calendar currentCal = (Calendar) startCal.clone();
        while (currentCal.getTimeInMillis() <= endCal.getTimeInMillis()) {
            int year = currentCal.get(Calendar.YEAR);
            int month = currentCal.get(Calendar.MONTH);
            String label = monthFormat.format(currentCal.getTime());

            options.add(new MonthOption(year, month, label));

            // Move to next month
            currentCal.add(Calendar.MONTH, 1);
        }

        return options;
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

    public void updateSummaryWithBudget(List<Transaction> transactions, double budget) {
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
        } else {
            if (alertCard != null) {
                alertCard.setVisibility(View.GONE);
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

            // Refresh transactions with new date range
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
        // Clear existing filter state except for specific flags we want to maintain
        boolean wasViewingManuallyExcluded = currentFilterState.viewingManuallyExcluded;
        String currentCategory = currentFilterState.category;
        String currentSearch = currentFilterState.searchQuery;
        int currentSortOption = currentFilterState.sortOption;

        // Create new filter state with preserved values
        FilterState newFilterState = new FilterState();
        newFilterState.viewingManuallyExcluded = wasViewingManuallyExcluded;
        newFilterState.category = currentCategory;
        newFilterState.searchQuery = currentSearch;
        newFilterState.sortOption = currentSortOption;

        // Update current filter state
        currentFilterState = newFilterState;

        // Use the smart loading strategy to refresh data
        if (smartLoadingStrategy != null) {
            smartLoadingStrategy.updateFilterState(currentFilterState);
            smartLoadingStrategy.refreshData(fromDate, toDate);
        }

        // Update spending chart
        setupSpendingChart();
    }

    // Helper method for filter indicator display
    public void showFilterIndicator(String message, int resultCount) {
        View filterContainer = findViewById(R.id.filterIndicatorContainer);
        TextView filterText = findViewById(R.id.filterIndicator);
        TextView resultCountText = findViewById(R.id.resultCount);

        if (filterContainer == null || filterText == null) {
            Log.e(TAG, "Filter indicator views not found!");
            return;
        }

        filterContainer.setVisibility(View.VISIBLE);
        filterText.setText(message);

        if (resultCountText != null) {
            if (resultCount >= 0) {
                resultCountText.setText(String.format(Locale.getDefault(),
                        "%d transaction(s) found", resultCount));
            } else {
                resultCountText.setText("Loading...");
            }
        }
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

        // Set navigation selection
        BottomNavigationView bottomNav = findViewById(R.id.bottomNavigation);
        if (bottomNav != null) {
            bottomNav.setSelectedItemId(R.id.nav_home);
        }

        // If we're viewing manually excluded transactions, refresh that view
        if (currentFilterState.viewingManuallyExcluded) {
            loadManuallyExcludedTransactions();
        } else {
            // Otherwise, refresh the regular transactions view
            refreshTransactions();
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
        public boolean viewingManuallyExcluded = false;

        // Method to check if any filter is active
        public boolean isAnyFilterActive() {
            return !bank.equals("All Banks") ||
                    !type.equals("All Types") ||
                    category != null ||
                    !searchQuery.isEmpty() ||
                    minAmount > 0 ||
                    maxAmount < 100000 ||
                    showingExcluded ||
                    viewingManuallyExcluded;
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
}