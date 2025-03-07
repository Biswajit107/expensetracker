package com.example.expensetracker;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.expensetracker.adapters.ExclusionPatternAdapter;
import com.example.expensetracker.adapters.TransactionAdapter;
import com.example.expensetracker.models.ExclusionPattern;
import com.example.expensetracker.viewmodel.ExclusionPatternViewModel;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Activity for managing transaction exclusion patterns
 */
public class ExclusionPatternsActivity extends AppCompatActivity
        implements ExclusionPatternAdapter.OnPatternActionListener {

    private ExclusionPatternViewModel viewModel;
    private ExclusionPatternAdapter adapter;
    private RecyclerView recyclerView;
    private TextView emptyStateText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_exclusion_patterns);

        // Set up toolbar
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setTitle("Smart Exclusion Patterns");

        // Initialize views
        recyclerView = findViewById(R.id.patternsRecyclerView);
        emptyStateText = findViewById(R.id.emptyPatternsText);

        // Set up RecyclerView
        adapter = new ExclusionPatternAdapter();
        adapter.setOnPatternActionListener(this);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        // Initialize ViewModel
        viewModel = new ViewModelProvider(this).get(ExclusionPatternViewModel.class);

        // Observe patterns
        viewModel.getAllPatterns().observe(this, this::updatePatternsList);

        setupBottomNavigation();
    }

    /**
     * Update the patterns list in the UI
     */
    private void updatePatternsList(List<ExclusionPattern> patterns) {
        if (patterns == null || patterns.isEmpty()) {
            // Show empty state
            recyclerView.setVisibility(View.GONE);
            emptyStateText.setVisibility(View.VISIBLE);
        } else {
            // Show patterns
            recyclerView.setVisibility(View.VISIBLE);
            emptyStateText.setVisibility(View.GONE);
            adapter.setPatterns(patterns);
        }
    }

    /**
     * Handle pattern deactivation
     */
    @Override
    public void onPatternDeactivated(ExclusionPattern pattern) {
        // Show confirmation dialog
        new AlertDialog.Builder(this)
                .setTitle("Deactivate Pattern")
                .setMessage("Are you sure you want to deactivate this exclusion pattern? " +
                        "It will no longer auto-exclude similar transactions.")
                .setPositiveButton("Deactivate", (dialog, which) -> {
                    viewModel.deactivatePattern(pattern.getId());
                    Toast.makeText(this, "Pattern deactivated", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    /**
     * Handle pattern deletion
     */
    @Override
    public void onPatternDeleted(ExclusionPattern pattern) {
        // Show confirmation dialog
        new AlertDialog.Builder(this)
                .setTitle("Delete Pattern")
                .setMessage("Are you sure you want to permanently delete this exclusion pattern?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    viewModel.deletePattern(pattern);
                    Toast.makeText(this, "Pattern deleted", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    /**
     * Handle pattern details request
     */
    @Override
    public void onPatternDetailsRequested(ExclusionPattern pattern) {
        // Show details dialog
        showPatternDetailsDialog(pattern);
    }

    /**
     * Show a dialog with pattern details
     */
    private void showPatternDetailsDialog(ExclusionPattern pattern) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_pattern_details, null);
        builder.setView(dialogView);

        // Set up dialog views
        TextView merchantPatternText = dialogView.findViewById(R.id.merchantPatternText);
        TextView descriptionPatternText = dialogView.findViewById(R.id.descriptionPatternText);
        TextView amountRangeText = dialogView.findViewById(R.id.amountRangeText);
        TextView transactionTypeText = dialogView.findViewById(R.id.transactionTypeText);
        TextView categoryText = dialogView.findViewById(R.id.categoryText);
        TextView createdDateText = dialogView.findViewById(R.id.createdDateText);
        TextView matchCountText = dialogView.findViewById(R.id.matchCountText);
        RecyclerView affectedTransactionsRecyclerView = dialogView.findViewById(R.id.affectedTransactionsRecyclerView);

        // Set dialog content
        merchantPatternText.setText(pattern.getMerchantPattern() != null ?
                pattern.getMerchantPattern() : "No merchant pattern");

        descriptionPatternText.setText(pattern.getDescriptionPattern() != null ?
                pattern.getDescriptionPattern() : "No description pattern");

        amountRangeText.setText(String.format(Locale.getDefault(),
                "₹%.2f - ₹%.2f", pattern.getMinAmount(), pattern.getMaxAmount()));

        transactionTypeText.setText(pattern.getTransactionType());

        categoryText.setText(pattern.getCategory() != null && !pattern.getCategory().isEmpty() ?
                pattern.getCategory() : "Any category");

        SimpleDateFormat dateFormat = new SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault());
        createdDateText.setText(dateFormat.format(new Date(pattern.getCreatedDate())));

        matchCountText.setText(String.valueOf(pattern.getPatternMatchesCount()));

        // Set up RecyclerView for affected transactions
        TransactionAdapter transactionAdapter = new TransactionAdapter();
        affectedTransactionsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        affectedTransactionsRecyclerView.setAdapter(transactionAdapter);

        // Load affected transactions
        viewModel.getTransactionsExcludedByPattern(transactions -> {
            runOnUiThread(() -> {
                if (transactions != null && !transactions.isEmpty()) {
                    transactionAdapter.setTransactions(transactions);
                    dialogView.findViewById(R.id.noAffectedTransactionsText).setVisibility(View.GONE);
                } else {
                    dialogView.findViewById(R.id.noAffectedTransactionsText).setVisibility(View.VISIBLE);
                }
            });
        });

        // Show dialog
        builder.setTitle("Pattern Details")
                .setPositiveButton("Close", null)
                .show();
    }

    private void setupBottomNavigation() {
        BottomNavigationView bottomNav = findViewById(R.id.bottomNavigation);
        bottomNav.setSelectedItemId(R.id.nav_patterns);

        bottomNav.setOnItemSelectedListener(item -> {
            Intent intent;

            switch (item.getItemId()) {
                case R.id.nav_home:
                    intent = new Intent(this, MainActivity.class);
                    startActivity(intent);
                    return true;

                case R.id.nav_analytics:
                    intent = new Intent(this, AnalyticsActivity.class);
                    startActivity(intent);
                    return true;

                case R.id.nav_predictions:
                    intent = new Intent(this, PredictionActivity.class);
                    startActivity(intent);
                    return true;

                case R.id.nav_groups:
                    intent = new Intent(this, GroupedExpensesActivity.class);
                    startActivity(intent);
                    return true;

//                case R.id.nav_excluded:
//                    intent = new Intent(this, ExcludedTransactionsActivity.class);
//                    startActivity(intent);
//                    return true;

                case R.id.nav_patterns:
                    return true;
            }
            return false;
        });
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Set the selected item in bottom navigation
        BottomNavigationView bottomNav = findViewById(R.id.bottomNavigation);
        bottomNav.setSelectedItemId(R.id.nav_patterns);
    }
}