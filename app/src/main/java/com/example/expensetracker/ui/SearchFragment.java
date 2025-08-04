package com.example.expensetracker.ui;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.expensetracker.R;
import com.example.expensetracker.adapters.TransactionAdapter;
import com.example.expensetracker.database.TQLProcessor;
import com.example.expensetracker.models.Transaction;
import com.example.expensetracker.viewmodel.SearchViewModel;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.slider.RangeSlider;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.switchmaterial.SwitchMaterial;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class SearchFragment extends Fragment implements SearchViewModel.SearchCallback {
    private static final String TAG = "SearchFragment";

    private SearchViewModel viewModel;
    private TransactionAdapter adapter;
    private EditText searchInput;
    private ChipGroup categoryChipGroup;
    private Button advancedSearchButton;
    private Button clearFiltersButton;
    private RecyclerView recyclerView;
    private TextView resultsCountText;
    private LinearLayout emptyResultsLayout;
    private ProgressBar progressBar;
    
    // TQL UI elements
    private SwitchMaterial tqlModeSwitch;
    private RecyclerView tqlSuggestionsRecyclerView;
    private com.google.android.material.card.MaterialCardView tqlResultCard;
    private TextView tqlResultSummary;
    private TextView tqlOriginalQuery;

    // Debounce search implementation
    private static final long SEARCH_DEBOUNCE_TIME_MS = 300;
    private final Executor searchExecutor = Executors.newSingleThreadExecutor();
    private Runnable pendingSearchRunnable;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_search, container, false);

        // Initialize views
        searchInput = view.findViewById(R.id.searchInput);
        categoryChipGroup = view.findViewById(R.id.categoryChipGroup);
        advancedSearchButton = view.findViewById(R.id.advancedSearchButton);
        clearFiltersButton = view.findViewById(R.id.clearFiltersButton);
        recyclerView = view.findViewById(R.id.recyclerView);
        resultsCountText = view.findViewById(R.id.resultsCountText);
        emptyResultsLayout = view.findViewById(R.id.emptyResultsLayout);
        progressBar = view.findViewById(R.id.progressBar);
        
        // Initialize TQL views
        tqlModeSwitch = view.findViewById(R.id.tqlModeSwitch);
        tqlSuggestionsRecyclerView = view.findViewById(R.id.tqlSuggestionsRecyclerView);
        tqlResultCard = view.findViewById(R.id.tqlResultCard);
        tqlResultSummary = view.findViewById(R.id.tqlResultSummary);
        tqlOriginalQuery = view.findViewById(R.id.tqlOriginalQuery);

        // Setup RecyclerView
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new TransactionAdapter();
        recyclerView.setAdapter(adapter);

        // Initialize ViewModel
        viewModel = new ViewModelProvider(this).get(SearchViewModel.class);
        Log.d(TAG, "*** SearchViewModel initialized: " + (viewModel != null ? "SUCCESS" : "FAILED") + " ***");

        setupSearchInput();
        setupCategoryChips();
        setupAdvancedSearch();
        setupClearFilters();
        setupTQLMode();
        observeViewModelState();
        
        // Test TQL detection immediately
        testTQLDetection();
        
        // Add a debug test for time filtering when fragment loads
        viewModel.debugTimeFiltering();
        
        // Add direct database test
        viewModel.testDirectDatabaseQuery();
        
        // Test the exact SQL query being generated
        viewModel.testTimeOfDaySQL();
        
        // Add a simple test query after 2 seconds to see what happens
        searchInput.postDelayed(() -> {
            if (isAdded()) {
                Log.d(TAG, "*** TRIGGERING TEST QUERY: 'transactions after 6pm' ***");
                
                // Test the detection manually first
                boolean shouldDetect = SearchViewModel.looksLikeTQLQuery("transactions after 6pm");
                Log.d(TAG, "Manual TQL detection test: " + shouldDetect);
                Toast.makeText(getContext(), "Manual TQL detection: " + shouldDetect, Toast.LENGTH_LONG).show();
                
                // Test time expression detection
                boolean hasTimeExpr = com.example.expensetracker.database.dynamic.SimpleDynamicTQLParser.hasTimeExpression("transactions after 6pm");
                Log.d(TAG, "Time expression detection: " + hasTimeExpr);
                Toast.makeText(getContext(), "Time expression detected: " + hasTimeExpr, Toast.LENGTH_LONG).show();
                
                searchInput.setText("transactions after 6pm");
                
                // Debug state after 1 second
                searchInput.postDelayed(() -> {
                    if (isAdded()) {
                        viewModel.debugCurrentState();
                        Toast.makeText(getContext(), "Check logs for debug state", Toast.LENGTH_SHORT).show();
                    }
                }, 1000);
            }
        }, 2000);

        return view;
    }

    private void setupSearchInput() {
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                String searchText = s.toString();
                Log.d(TAG, "*** SEARCH INPUT CHANGED: '" + searchText + "' ***");
                Log.d(TAG, "TQL mode current state: " + viewModel.isTQLMode());
                
                // Use debounce for search to avoid excessive database queries
                if (pendingSearchRunnable != null) {
                    searchExecutor.execute(() -> {
                        try {
                            Thread.sleep(SEARCH_DEBOUNCE_TIME_MS);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    });
                }

                // Create new search runnable
                pendingSearchRunnable = () -> {
                    viewModel.setSearchText(searchText);
                };

                // Execute after delay
                searchExecutor.execute(() -> {
                    try {
                        Thread.sleep(SEARCH_DEBOUNCE_TIME_MS);
                        if (pendingSearchRunnable != null && isAdded()) {
                            requireActivity().runOnUiThread(() -> {
                                pendingSearchRunnable.run();
                                pendingSearchRunnable = null;
                            });
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
            }
        });
    }

    private void setupCategoryChips() {
        // Add chips for each transaction category
        String[] categories = Transaction.Categories.getAllCategories();

        for (String category : categories) {
            Chip chip = new Chip(getContext());
            chip.setText(category);
            chip.setCheckable(true);

            chip.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) {
                    viewModel.setCategory(category);
                } else if (viewModel.getCategory() != null && viewModel.getCategory().equals(category)) {
                    viewModel.setCategory(null);
                }
            });

            categoryChipGroup.addView(chip);
        }
    }

    private void setupAdvancedSearch() {
        advancedSearchButton.setOnClickListener(v -> showAdvancedSearchDialog());
    }

    private void setupClearFilters() {
        clearFiltersButton.setOnClickListener(v -> {
            // Clear all filters
            searchInput.setText("");
            categoryChipGroup.clearCheck();
            viewModel.clearAllFilters();
        });
    }

    private void observeViewModelState() {
        // Observe search results
        viewModel.getSearchResults().observe(getViewLifecycleOwner(), this::updateUI);

        // Observe loading state
        viewModel.getIsLoading().observe(getViewLifecycleOwner(), isLoading -> {
            progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        });

        // Observe error messages
        viewModel.getErrorMessage().observe(getViewLifecycleOwner(), errorMessage -> {
            if (errorMessage != null && !errorMessage.isEmpty()) {
                Toast.makeText(getContext(), errorMessage, Toast.LENGTH_SHORT).show();
            }
        });
        
        // Observe TQL results
        viewModel.getTQLResults().observe(getViewLifecycleOwner(), this::updateTQLResults);
        
        // Observe TQL mode suggestions
        viewModel.getSuggestTQLMode().observe(getViewLifecycleOwner(), this::showTQLSuggestion);
        
        // Observe debug logs
        viewModel.getDebugLog().observe(getViewLifecycleOwner(), debugMessage -> {
            if (debugMessage != null) {
                Toast.makeText(getContext(), "DEBUG: " + debugMessage, Toast.LENGTH_SHORT).show();
            }
        });
        
        // Observe TQL mode changes
        viewModel.getTQLMode().observe(getViewLifecycleOwner(), isTQLMode -> {
            // Update the switch without triggering the listener
            tqlModeSwitch.setOnCheckedChangeListener(null);
            tqlModeSwitch.setChecked(isTQLMode);
            tqlModeSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
                Log.d(TAG, "TQL mode toggled: " + isChecked + ", pressed: " + buttonView.isPressed());
                
                viewModel.setTQLMode(isChecked);
                updateTQLModeUI(isChecked);
                
                // Update search hint
                if (isChecked) {
                    searchInput.setHint("Ask: 'transactions > 100 from swiggy last month'");
                } else {
                    searchInput.setHint("Search transactions...");
                }
                
                // Only clear search when manually toggled (not programmatic)
                if (buttonView.isPressed()) {
                    searchInput.setText("");
                }
                
                if (isChecked) {
                    showTQLSuggestions();
                    // If there's existing text when enabling TQL mode, process it
                    String currentText = searchInput.getText().toString();
                    if (!currentText.trim().isEmpty()) {
                        Log.d(TAG, "Processing existing text as TQL: " + currentText);
                        viewModel.performTQLQuery(currentText);
                    }
                } else {
                    hideTQLSuggestions();
                    tqlResultCard.setVisibility(View.GONE);
                }
            });
            
            updateTQLModeUI(isTQLMode);
        });
    }

    private void updateUI(List<Transaction> transactions) {
        if (transactions == null) {
            return;
        }

        adapter.setTransactions(transactions);

        // Update results count
        resultsCountText.setText(String.format(Locale.getDefault(),
                "%d transactions found", transactions.size()));

        // Show/hide empty state
        if (transactions.isEmpty()) {
            recyclerView.setVisibility(View.GONE);
            emptyResultsLayout.setVisibility(View.VISIBLE);
        } else {
            recyclerView.setVisibility(View.VISIBLE);
            emptyResultsLayout.setVisibility(View.GONE);
        }
    }

    private void showAdvancedSearchDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_advanced_search, null);
        builder.setTitle("Advanced Search");
        builder.setView(dialogView);

        // Initialize dialog views
        AutoCompleteTextView bankSpinner = dialogView.findViewById(R.id.bankSpinner);
        AutoCompleteTextView typeSpinner = dialogView.findViewById(R.id.typeSpinner);
        EditText merchantInput = dialogView.findViewById(R.id.merchantInput);
        RangeSlider amountSlider = dialogView.findViewById(R.id.amountSlider);
        TextView amountRangeText = dialogView.findViewById(R.id.amountRangeText);
        MaterialButton fromDateButton = dialogView.findViewById(R.id.fromDateButton);
        MaterialButton toDateButton = dialogView.findViewById(R.id.toDateButton);
        SwitchMaterial excludedSwitch = dialogView.findViewById(R.id.excludedSwitch);
        SwitchMaterial recurringSwitch = dialogView.findViewById(R.id.recurringSwitch);

        // Setup bank spinner
        ArrayAdapter<String> bankAdapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_dropdown_item_1line,
                viewModel.getAvailableBanks()
        );
        bankSpinner.setAdapter(bankAdapter);

        // Setup type spinner
        ArrayAdapter<String> typeAdapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_dropdown_item_1line,
                new String[]{"All Types", "DEBIT", "CREDIT"}
        );
        typeSpinner.setAdapter(typeAdapter);

        // Setup amount slider
        amountSlider.setValueFrom(0);
        amountSlider.setValueTo(100000); // Adjust based on your app's typical transaction amounts
        amountSlider.setValues(0f, 100000f);

        // Update amount range text when slider changes
        amountSlider.addOnChangeListener((slider, value, fromUser) -> {
            List<Float> values = slider.getValues();
            amountRangeText.setText(String.format(Locale.getDefault(),
                    "₹%.0f - ₹%.0f", values.get(0), values.get(1)));
        });

        // Setup date buttons
        Calendar calendar = Calendar.getInstance();
        SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());

        // Set from date button text and click listener
        fromDateButton.setOnClickListener(v -> {
            DatePickerDialog datePickerDialog = new DatePickerDialog(
                    requireContext(),
                    (view, year, month, dayOfMonth) -> {
                        Calendar selectedDate = Calendar.getInstance();
                        selectedDate.set(year, month, dayOfMonth, 0, 0, 0);
                        long timestamp = selectedDate.getTimeInMillis();
                        viewModel.setStartDate(timestamp);

                        // Update button text
                        fromDateButton.setText(dateFormat.format(new Date(timestamp)));
                    },
                    calendar.get(Calendar.YEAR),
                    calendar.get(Calendar.MONTH),
                    calendar.get(Calendar.DAY_OF_MONTH)
            );
            datePickerDialog.show();
        });

        // Set to date button text and click listener
        toDateButton.setOnClickListener(v -> {
            DatePickerDialog datePickerDialog = new DatePickerDialog(
                    requireContext(),
                    (view, year, month, dayOfMonth) -> {
                        Calendar selectedDate = Calendar.getInstance();
                        selectedDate.set(year, month, dayOfMonth, 23, 59, 59);
                        long timestamp = selectedDate.getTimeInMillis();
                        viewModel.setEndDate(timestamp);

                        // Update button text
                        toDateButton.setText(dateFormat.format(new Date(timestamp)));
                    },
                    calendar.get(Calendar.YEAR),
                    calendar.get(Calendar.MONTH),
                    calendar.get(Calendar.DAY_OF_MONTH)
            );
            datePickerDialog.show();
        });

        // Set initial values based on current filters
        if (viewModel.getBank() != null) {
            bankSpinner.setText(viewModel.getBank(), false);
        }

        if (viewModel.getType() != null) {
            typeSpinner.setText(viewModel.getType(), false);
        }

        if (viewModel.getMerchantName() != null) {
            merchantInput.setText(viewModel.getMerchantName());
        }

        if (viewModel.getStartDate() != null) {
            fromDateButton.setText(dateFormat.format(new Date(viewModel.getStartDate())));
        }

        if (viewModel.getEndDate() != null) {
            toDateButton.setText(dateFormat.format(new Date(viewModel.getEndDate())));
        }

        if (viewModel.getExcludedFromTotal() != null) {
            excludedSwitch.setChecked(viewModel.getExcludedFromTotal());
        }

        if (viewModel.getIsRecurring() != null) {
            recurringSwitch.setChecked(viewModel.getIsRecurring());
        }

        // Setup dialog buttons
        builder.setPositiveButton("Apply", (dialog, which) -> {
            // Show loading indicator
            progressBar.setVisibility(View.VISIBLE);

            // Apply filters
            viewModel.setBank(bankSpinner.getText().toString());
            viewModel.setType(typeSpinner.getText().toString());
            viewModel.setMerchantName(merchantInput.getText().toString());

            List<Float> values = amountSlider.getValues();
            viewModel.setAmountRange(values.get(0).doubleValue(), values.get(1).doubleValue());

            viewModel.setExcludedFromTotal(excludedSwitch.isChecked());
            viewModel.setIsRecurring(recurringSwitch.isChecked());

            // Update search with all filters
            viewModel.performSearch(this);
        });

        builder.setNegativeButton("Cancel", null);

        AlertDialog dialog = builder.create();
        dialog.show();
    }

    @Override
    public void onSearchComplete(List<Transaction> results) {
        if (isAdded()) {
            requireActivity().runOnUiThread(() -> {
                updateUI(results);
            });
        }
    }

    @Override
    public void onError(String message) {
        if (isAdded()) {
            requireActivity().runOnUiThread(() -> {
                Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
            });
        }
    }
    
    // TQL Methods
    
    private void setupTQLMode() {
        // TQL mode setup is now handled by the observer in observeViewModelState()
        // This method is kept for any additional TQL setup that might be needed
    }
    
    private void updateTQLModeUI(boolean isTQLMode) {
        // Show/hide regular search components
        categoryChipGroup.setVisibility(isTQLMode ? View.GONE : View.VISIBLE);
        advancedSearchButton.setVisibility(isTQLMode ? View.GONE : View.VISIBLE);
        // Keep clear button visible in TQL mode to allow clearing the search text
        clearFiltersButton.setVisibility(View.VISIBLE);
        
        // Update button text based on mode
        if (isTQLMode) {
            clearFiltersButton.setText("Clear Query");
            // Update search input hint for TQL mode
            searchInput.setHint("🧠 Ask: 'transactions after 6pm', 'total spent on food', etc.");
        } else {
            clearFiltersButton.setText("Clear All");
            // Restore normal search hint
            searchInput.setHint("Search transactions...");
        }
        
        // Update search card elevation for TQL mode
        if (isTQLMode) {
            // Higher elevation to indicate smart mode
            tqlSuggestionsRecyclerView.setVisibility(View.VISIBLE);
        } else {
            tqlSuggestionsRecyclerView.setVisibility(View.GONE);
            tqlResultCard.setVisibility(View.GONE);
        }
    }
    
    private void showTQLSuggestions() {
        // Show common TQL query suggestions
        List<String> suggestions = viewModel.getTQLSuggestions("");
        // For now, just show in a simple way - you can implement a proper adapter later
        tqlSuggestionsRecyclerView.setVisibility(View.VISIBLE);
    }
    
    private void hideTQLSuggestions() {
        tqlSuggestionsRecyclerView.setVisibility(View.GONE);
    }
    
    private void updateTQLResults(TQLProcessor.TQLResult result) {
        Log.d(TAG, "*** UPDATING TQL RESULTS ***");
        Log.d(TAG, "Result: " + (result != null ? result.getClass().getSimpleName() : "null"));
        
        if (result == null) {
            tqlResultCard.setVisibility(View.GONE);
            // Show debug info via toast
            Toast.makeText(getContext(), "DEBUG: TQL result is null", Toast.LENGTH_SHORT).show();
            return;
        }
        
        if (result.isError()) {
            tqlResultCard.setVisibility(View.VISIBLE);
            tqlResultSummary.setText("❌ " + result.getErrorMessage());
            tqlOriginalQuery.setText("");
            Log.e(TAG, "TQL Error: " + result.getErrorMessage());
            Toast.makeText(getContext(), "DEBUG: TQL Error - " + result.getErrorMessage(), Toast.LENGTH_LONG).show();
            return;
        }
        
        // Show result summary
        tqlResultCard.setVisibility(View.VISIBLE);
        tqlOriginalQuery.setText("Query: \"" + result.getOriginalQuery() + "\"");
        
        if (result.isAggregateResult()) {
            // Show aggregate result
            Number value = result.getAggregateValue();
            String label = result.getAggregateLabel();
            
            if (label.contains("amount")) {
                tqlResultSummary.setText(String.format(Locale.getDefault(), 
                    "💰 Result: ₹%.2f %s", value.doubleValue(), label));
            } else {
                tqlResultSummary.setText(String.format(Locale.getDefault(), 
                    "📊 Result: %s %s", formatNumber(value), label));
            }
            
        } else if (result.isGroupResult()) {
            // Show group result summary
            List<TQLProcessor.TQLGroupResult> groups = result.getGroupResults();
            if (groups != null && !groups.isEmpty()) {
                tqlResultSummary.setText(String.format(Locale.getDefault(), 
                    "📈 Found %d groups, top: %s (₹%.2f)", 
                    groups.size(), 
                    groups.get(0).getGroupName(),
                    groups.get(0).getTotalAmount()));
            } else {
                tqlResultSummary.setText("📈 No groups found");
            }
            
        } else if (result.isTransactionResult()) {
            // Show transaction result summary with debug info
            List<Transaction> transactions = result.getTransactions();
            if (transactions != null) {
                double total = transactions.stream()
                        .filter(t -> !t.isExcludedFromTotal())
                        .mapToDouble(Transaction::getAmount)
                        .sum();
                
                String resultText = String.format(Locale.getDefault(), 
                    "💳 Found %d transactions, Total: ₹%.2f", 
                    transactions.size(), total);
                
                // Add debug info about time filtering
                if (transactions.size() > 0) {
                    Transaction firstTx = transactions.get(0);
                    Transaction lastTx = transactions.get(transactions.size() - 1);
                    
                    SimpleDateFormat timeFormat = new SimpleDateFormat("hh:mm a", Locale.getDefault());
                    resultText += "\n🕐 Times: " + timeFormat.format(new Date(firstTx.getDate())) + 
                                 " to " + timeFormat.format(new Date(lastTx.getDate()));
                }
                
                tqlResultSummary.setText(resultText);
                
                // Show debug toast
                Toast.makeText(getContext(), "DEBUG: Found " + transactions.size() + " transactions", Toast.LENGTH_SHORT).show();
            } else {
                tqlResultSummary.setText("💳 No transactions found");
                Toast.makeText(getContext(), "DEBUG: Transactions list is null", Toast.LENGTH_SHORT).show();
            }
        }
        
        Log.d(TAG, "*** TQL RESULTS UPDATED ***");
    }
    
    private String formatNumber(Number number) {
        if (number.doubleValue() == number.intValue()) {
            return String.valueOf(number.intValue());
        } else {
            return String.format(Locale.getDefault(), "%.2f", number.doubleValue());
        }
    }
    
    private void showTQLSuggestion(String query) {
        if (query == null || query.trim().isEmpty()) {
            Log.d(TAG, "showTQLSuggestion called with empty query");
            return;
        }
        
        Log.d(TAG, "*** SHOWING TQL SUGGESTION FOR: " + query + " ***");
        
        // Highlight the TQL switch temporarily
        highlightTQLSwitch();
        
        // Show snackbar suggesting to enable TQL mode
        Snackbar snackbar = Snackbar.make(getView(), 
            "🧠 This looks like a smart query! Enable TQL mode?", 
            Snackbar.LENGTH_LONG);
        
        snackbar.setAction("ENABLE", v -> {
            Log.d(TAG, "User clicked ENABLE TQL for query: " + query);
            
            // Enable TQL mode and set the switch
            tqlModeSwitch.setChecked(true);
            
            // Set the search text to trigger TQL processing
            searchInput.setText(query);
            
            // Move cursor to end
            searchInput.setSelection(query.length());
        });
        
        snackbar.setActionTextColor(getResources().getColor(android.R.color.white));
        snackbar.show();
    }
    
    private void highlightTQLSwitch() {
        // Add a subtle animation or color change to draw attention to the switch
        if (tqlModeSwitch != null) {
            tqlModeSwitch.animate()
                .scaleX(1.1f)
                .scaleY(1.1f)
                .setDuration(200)
                .withEndAction(() -> {
                    tqlModeSwitch.animate()
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .setDuration(200)
                        .start();
                })
                .start();
        }
    }
    
    private void testTQLDetection() {
        Log.d(TAG, "*** TESTING TQL DETECTION ***");
        
        // Test the detection method directly
        String[] testQueries = {
            "transactions > 100",
            "count transactions",
            "total spent",
            "group by merchant",
            "normal search text"
        };
        
        for (String query : testQueries) {
            boolean result = SearchViewModel.looksLikeTQLQuery(query);
            Log.d(TAG, "Test query: '" + query + "' -> " + result);
        }
        
        Log.d(TAG, "*** TQL DETECTION TEST COMPLETE ***");
    }
}