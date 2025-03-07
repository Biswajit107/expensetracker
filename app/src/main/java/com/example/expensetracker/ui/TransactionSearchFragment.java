package com.example.expensetracker.ui;

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
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.expensetracker.R;
import com.example.expensetracker.adapters.TransactionAdapter;
import com.example.expensetracker.models.Transaction;
import com.example.expensetracker.utils.TransactionSearchSortUtil;
import com.example.expensetracker.utils.TransactionSearchSortUtil.SearchCriteria;
import com.example.expensetracker.utils.TransactionSearchSortUtil.SortBy;
import com.example.expensetracker.viewmodel.TransactionViewModel;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.slider.RangeSlider;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Fragment for searching and sorting transactions
 */
public class TransactionSearchFragment extends Fragment {
    private TransactionViewModel viewModel;
    private TransactionAdapter adapter;
    private RecyclerView recyclerView;
    private EditText searchInput;
    private Button advancedSearchButton;
    private Button sortButton;
    private TextView resultCountText;
    private ChipGroup sortChipGroup;

    private List<Transaction> allTransactions = new ArrayList<>();
    private List<Transaction> filteredTransactions = new ArrayList<>();

    // Current search/sort state
    private SearchCriteria currentSearchCriteria;
    private SortBy currentSortBy = SortBy.DATE;
    private boolean sortAscending = false;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_transaction_search, container, false);

        // Initialize views
        recyclerView = view.findViewById(R.id.recyclerView);
        searchInput = view.findViewById(R.id.searchInput);
        advancedSearchButton = view.findViewById(R.id.advancedSearchButton);
        sortButton = view.findViewById(R.id.sortButton);
        resultCountText = view.findViewById(R.id.resultCountText);
        sortChipGroup = view.findViewById(R.id.sortChipGroup);

        // Setup RecyclerView
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new TransactionAdapter();
        recyclerView.setAdapter(adapter);

        // Initialize ViewModel
        viewModel = new ViewModelProvider(requireActivity()).get(TransactionViewModel.class);

        setupSearchInput();
        setupAdvancedSearch();
        setupSortButton();
        setupSortChips();
        observeTransactions();

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
                String query = s.toString();
                performSearch(query);
            }
        });

        // Clear button for search
        ImageButton clearButton = requireView().findViewById(R.id.clearButton);
        clearButton.setOnClickListener(v -> {
            searchInput.setText("");
            currentSearchCriteria = null;
            updateTransactionsList(allTransactions);
        });
    }

    private void setupAdvancedSearch() {
        advancedSearchButton.setOnClickListener(v -> showAdvancedSearchDialog());
    }

    private void setupSortButton() {
        sortButton.setOnClickListener(v -> {
            if (sortChipGroup.getVisibility() == View.VISIBLE) {
                sortChipGroup.setVisibility(View.GONE);
            } else {
                sortChipGroup.setVisibility(View.VISIBLE);
            }
        });
    }

    private void setupSortChips() {
        // Clear existing chips
        sortChipGroup.removeAllViews();

        // Create sort options
        String[] sortOptions = {
                "Date (newest)", "Date (oldest)",
                "Amount (highest)", "Amount (lowest)",
                "Description (A-Z)", "Description (Z-A)",
                "Bank (A-Z)", "Bank (Z-A)",
                "Category (A-Z)", "Category (Z-A)"
        };

        for (String option : sortOptions) {
            Chip chip = new Chip(requireContext());
            chip.setText(option);
            chip.setCheckable(true);

            chip.setOnClickListener(v -> {
                applySortOption(option);
                sortChipGroup.setVisibility(View.GONE);
            });

            sortChipGroup.addView(chip);
        }
    }

    private void applySortOption(String option) {
        SortBy sortBy;
        boolean ascending;

        switch (option) {
            case "Date (newest)":
                sortBy = SortBy.DATE;
                ascending = false;
                break;
            case "Date (oldest)":
                sortBy = SortBy.DATE;
                ascending = true;
                break;
            case "Amount (highest)":
                sortBy = SortBy.AMOUNT;
                ascending = false;
                break;
            case "Amount (lowest)":
                sortBy = SortBy.AMOUNT;
                ascending = true;
                break;
            case "Description (A-Z)":
                sortBy = SortBy.DESCRIPTION;
                ascending = true;
                break;
            case "Description (Z-A)":
                sortBy = SortBy.DESCRIPTION;
                ascending = false;
                break;
            case "Bank (A-Z)":
                sortBy = SortBy.BANK;
                ascending = true;
                break;
            case "Bank (Z-A)":
                sortBy = SortBy.BANK;
                ascending = false;
                break;
            case "Category (A-Z)":
                sortBy = SortBy.CATEGORY;
                ascending = true;
                break;
            case "Category (Z-A)":
                sortBy = SortBy.CATEGORY;
                ascending = false;
                break;
            default:
                sortBy = SortBy.DATE;
                ascending = false;
        }

        currentSortBy = sortBy;
        sortAscending = ascending;

        List<Transaction> sortedList = TransactionSearchSortUtil.sortTransactions(
                filteredTransactions.isEmpty() ? allTransactions : filteredTransactions,
                sortBy,
                ascending);

        adapter.setTransactions(sortedList);
    }

    private void observeTransactions() {
        viewModel.getAllTransactions().observe(getViewLifecycleOwner(), transactions -> {
            allTransactions = transactions;

            // Apply current search if any
            if (currentSearchCriteria != null) {
                filteredTransactions = TransactionSearchSortUtil.advancedSearch(
                        allTransactions, currentSearchCriteria);
            } else if (!searchInput.getText().toString().isEmpty()) {
                filteredTransactions = TransactionSearchSortUtil.searchTransactions(
                        allTransactions, searchInput.getText().toString());
            } else {
                filteredTransactions = new ArrayList<>(allTransactions);
            }

            // Apply current sort
            List<Transaction> sortedList = TransactionSearchSortUtil.sortTransactions(
                    filteredTransactions, currentSortBy, sortAscending);

            adapter.setTransactions(sortedList);

            // Update result count
            updateResultCount(sortedList.size());
        });
    }

    private void performSearch(String query) {
        if (query.isEmpty()) {
            // Clear search, show all transactions (sorted by current sort option)
            filteredTransactions = new ArrayList<>(allTransactions);
            List<Transaction> sortedList = TransactionSearchSortUtil.sortTransactions(
                    filteredTransactions, currentSortBy, sortAscending);

            adapter.setTransactions(sortedList);
            updateResultCount(sortedList.size());
        } else {
            // Perform search
            filteredTransactions = TransactionSearchSortUtil.searchTransactions(
                    allTransactions, query);

            // Apply current sort
            List<Transaction> sortedList = TransactionSearchSortUtil.sortTransactions(
                    filteredTransactions, currentSortBy, sortAscending);

            adapter.setTransactions(sortedList);
            updateResultCount(sortedList.size());
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

        // Date variables to store selected dates
        final long[] startDate = {0};
        final long[] endDate = {0};

        // Setup date buttons
        fromDateButton.setOnClickListener(v -> {
            MaterialDatePicker<Long> datePicker = MaterialDatePicker.Builder.datePicker()
                    .setTitleText("Select Start Date")
                    .setSelection(MaterialDatePicker.todayInUtcMilliseconds())
                    .build();

            datePicker.addOnPositiveButtonClickListener(selection -> {
                startDate[0] = selection;
                SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
                fromDateButton.setText(dateFormat.format(new Date(selection)));
            });

            datePicker.show(getParentFragmentManager(), "DATE_PICKER");
        });

        toDateButton.setOnClickListener(v -> {
            MaterialDatePicker<Long> datePicker = MaterialDatePicker.Builder.datePicker()
                    .setTitleText("Select End Date")
                    .setSelection(MaterialDatePicker.todayInUtcMilliseconds())
                    .build();

            datePicker.addOnPositiveButtonClickListener(selection -> {
                // Set to end of day
                Calendar calendar = Calendar.getInstance();
                calendar.setTimeInMillis(selection);
                calendar.set(Calendar.HOUR_OF_DAY, 23);
                calendar.set(Calendar.MINUTE, 59);
                calendar.set(Calendar.SECOND, 59);

                endDate[0] = calendar.getTimeInMillis();
                SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
                toDateButton.setText(dateFormat.format(new Date(selection)));
            });

            datePicker.show(getParentFragmentManager(), "DATE_PICKER");
        });

        // Initialize with current criteria if exists
        if (currentSearchCriteria != null) {
            if (currentSearchCriteria.getBank() != null) {
                bankSpinner.setText(currentSearchCriteria.getBank(), false);
            }

            if (currentSearchCriteria.getType() != null) {
                typeSpinner.setText(currentSearchCriteria.getType(), false);
            }

            if (currentSearchCriteria.getSearchText() != null) {
                merchantInput.setText(currentSearchCriteria.getSearchText());
            }

            if (currentSearchCriteria.getMinAmount() != null && currentSearchCriteria.getMaxAmount() != null) {
                amountSlider.setValues(
                        currentSearchCriteria.getMinAmount().floatValue(),
                        currentSearchCriteria.getMaxAmount().floatValue()
                );
            }

            if (currentSearchCriteria.getStartDate() != null) {
                startDate[0] = currentSearchCriteria.getStartDate();
                SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
                fromDateButton.setText(dateFormat.format(new Date(startDate[0])));
            }

            if (currentSearchCriteria.getEndDate() != null) {
                endDate[0] = currentSearchCriteria.getEndDate();
                SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
                toDateButton.setText(dateFormat.format(new Date(endDate[0])));
            }
        }

        // Setup dialog buttons
        builder.setPositiveButton("Apply", (dialog, which) -> {
            // Create search criteria
            SearchCriteria.Builder criteriaBuilder = new SearchCriteria.Builder();

            // Add bank filter if selected
            String selectedBank = bankSpinner.getText().toString();
            if (!selectedBank.isEmpty() && !selectedBank.equals("All Banks")) {
                criteriaBuilder.bank(selectedBank);
            }

            // Add type filter if selected
            String selectedType = typeSpinner.getText().toString();
            if (!selectedType.isEmpty() && !selectedType.equals("All Types")) {
                criteriaBuilder.type(selectedType);
            }

            // Add merchant/description search
            String searchText = merchantInput.getText().toString();
            if (!searchText.isEmpty()) {
                criteriaBuilder.searchText(searchText);
            }

            // Add amount range
            List<Float> values = amountSlider.getValues();
            criteriaBuilder.minAmount((double) values.get(0));
            criteriaBuilder.maxAmount((double) values.get(1));

            // Add date range if selected
            if (startDate[0] > 0 && endDate[0] > 0) {
                criteriaBuilder.dateRange(startDate[0], endDate[0]);
            } else if (startDate[0] > 0) {
                criteriaBuilder.dateRange(startDate[0], System.currentTimeMillis());
            } else if (endDate[0] > 0) {
                criteriaBuilder.dateRange(0L, endDate[0]);
            }

            // Build criteria and apply search
            currentSearchCriteria = criteriaBuilder.build();
            applyAdvancedSearch();
        });

        builder.setNegativeButton("Cancel", null);

        builder.setNeutralButton("Clear All", (dialog, which) -> {
            currentSearchCriteria = null;
            searchInput.setText("");
            updateTransactionsList(allTransactions);
        });

        builder.create().show();
    }

    private void applyAdvancedSearch() {
        if (currentSearchCriteria != null) {
            filteredTransactions = TransactionSearchSortUtil.advancedSearch(
                    allTransactions, currentSearchCriteria);

            // Apply current sort
            List<Transaction> sortedList = TransactionSearchSortUtil.sortTransactions(
                    filteredTransactions, currentSortBy, sortAscending);

            adapter.setTransactions(sortedList);
            updateResultCount(sortedList.size());
        }
    }

    private void updateTransactionsList(List<Transaction> transactions) {
        // Apply current sort
        List<Transaction> sortedList = TransactionSearchSortUtil.sortTransactions(
                transactions, currentSortBy, sortAscending);

        adapter.setTransactions(sortedList);
        updateResultCount(sortedList.size());
    }

    private void updateResultCount(int count) {
        resultCountText.setText(String.format(Locale.getDefault(),
                "%d transaction(s) found", count));
    }
}