package com.example.expensetracker.ui;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
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
import com.example.expensetracker.database.TransactionSearchFilter;
import com.example.expensetracker.models.Transaction;
import com.example.expensetracker.viewmodel.SearchViewModel;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.slider.RangeSlider;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textfield.TextInputLayout;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class SearchFragment extends Fragment implements SearchViewModel.SearchCallback {

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

        // Setup RecyclerView
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new TransactionAdapter();
        recyclerView.setAdapter(adapter);

        // Initialize ViewModel
        viewModel = new ViewModelProvider(this).get(SearchViewModel.class);

        setupSearchInput();
        setupCategoryChips();
        setupAdvancedSearch();
        setupClearFilters();
        observeViewModelState();

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
                    viewModel.setSearchText(s.toString());
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
}