package com.example.expensetracker;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.expensetracker.adapters.GroupedExpensesAdapter;
import com.example.expensetracker.viewmodel.GroupedExpensesViewModel;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.textfield.TextInputEditText;

import java.util.List;
import java.util.Locale;

public class GroupedExpensesActivity extends AppCompatActivity implements GroupedExpensesAdapter.OnGroupClickListener {
    private GroupedExpensesViewModel viewModel;
    private GroupedExpensesAdapter adapter;
    private TextView totalGroupsText;
    private TextView totalAmountText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_grouped_expenses);

        viewModel = new ViewModelProvider(this).get(GroupedExpensesViewModel.class);

        setupViews();
        setupRecyclerView();
        setupTimeframeSpinner();
        setupSearch();
        setupBottomNavigation();
        observeData();
    }

    private void setupViews() {
        totalGroupsText = findViewById(R.id.totalGroupsText);
        totalAmountText = findViewById(R.id.totalAmountText);
    }

    private void setupRecyclerView() {
        RecyclerView recyclerView = findViewById(R.id.expenseGroupsList);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new GroupedExpensesAdapter();
        recyclerView.setAdapter(adapter);

        // Set click listener for group expansion
        adapter.setOnGroupClickListener(this);
    }

    private void setupTimeframeSpinner() {
        Spinner timeframeSpinner = findViewById(R.id.timeframeSpinner);
        ArrayAdapter<CharSequence> spinnerAdapter = ArrayAdapter.createFromResource(this,
                R.array.timeframe_options, android.R.layout.simple_spinner_item);
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        timeframeSpinner.setAdapter(spinnerAdapter);

        timeframeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String selectedTimeframe = parent.getItemAtPosition(position).toString();
                viewModel.setTimeframe(selectedTimeframe);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void setupSearch() {
        TextInputEditText searchInput = findViewById(R.id.searchInput);
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                viewModel.setSearchQuery(s.toString());
            }
        });
    }

    private void setupBottomNavigation() {
        BottomNavigationView bottomNav = findViewById(R.id.bottomNavigation);
        bottomNav.setSelectedItemId(R.id.nav_groups);
        bottomNav.setOnItemSelectedListener(item -> {
            switch (item.getItemId()) {
                case R.id.nav_home:
                    startActivity(new android.content.Intent(this, MainActivity.class));
//                    finish();
                    return true;
                case R.id.nav_analytics:
                    startActivity(new android.content.Intent(this, AnalyticsActivity.class));
//                    finish();
                    return true;
                case R.id.nav_predictions:
                    startActivity(new android.content.Intent(this, PredictionActivity.class));
//                    finish();
                    return true;
                case R.id.nav_groups:
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

    private void observeData() {
        viewModel.getExpenseGroups().observe(this, groups -> {
            adapter.setGroups(groups);
            updateSummary(groups);
        });
    }

    private void updateSummary(List<GroupedExpensesViewModel.ExpenseGroup> groups) {
        int totalGroups = groups.size();
        double totalAmount = 0;
        for (GroupedExpensesViewModel.ExpenseGroup group : groups) {
            totalAmount += group.getTotalAmount();
        }

        totalGroupsText.setText(String.format(Locale.getDefault(), "%d", totalGroups));
        totalAmountText.setText(String.format(Locale.getDefault(), "₹%.2f", totalAmount));
    }

    @Override
    public void onGroupClick(int position) {
        // Pass the click to the ViewModel
        viewModel.toggleGroupExpansion(position);
        // Notify only that item changed
        adapter.notifyItemChanged(position);
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Refresh the badge count on bottom navigation
        BottomNavigationView bottomNav = findViewById(R.id.bottomNavigation);
        bottomNav.setSelectedItemId(R.id.nav_groups); // Change this ID for each activity
    }

}