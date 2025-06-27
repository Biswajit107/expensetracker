package com.example.expensetracker;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.expensetracker.adapters.TransactionAdapter;
import com.example.expensetracker.database.TransactionDao;
import com.example.expensetracker.database.TransactionDatabase;
import com.example.expensetracker.dialogs.TransactionEditDialog;
import com.example.expensetracker.models.Transaction;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Activity for reviewing all automatically excluded transactions
 */
public class ExcludedTransactionsActivity extends AppCompatActivity {

    private static final int FILTER_ALL = 0;
    private static final int FILTER_DUPLICATES = 1;
    private static final int FILTER_UNKNOWN_SOURCE = 2;

    private RecyclerView recyclerView;
    private TextView emptyView;
    private ChipGroup filterChips;
    private TransactionAdapter adapter;
    private ExecutorService executorService;
    private int currentFilter = FILTER_ALL;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_excluded_transactions);

        // Initialize toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setTitle("Excluded Transactions");

        // Initialize views
        recyclerView = findViewById(R.id.excludedRecyclerView);
        emptyView = findViewById(R.id.emptyExcludedText);
        filterChips = findViewById(R.id.filterChipGroup);
        FloatingActionButton includeAllFab = findViewById(R.id.includeAllFab);

        // Set up recycler view
        adapter = new TransactionAdapter();
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        // Set up click listener for transactions
        adapter.setOnTransactionClickListener(transaction -> {
            showTransactionEditDialog(transaction);
        });

        // Set up long-press listener for delete option
        adapter.setOnTransactionLongClickListener(transaction -> {
            showDeleteTransactionDialog(transaction);
        });

        // Set up filter chips
        setupFilterChips();

        // Set up include all button with menu options
        includeAllFab.setOnClickListener(v -> showActionMenu());

        // Initialize executor service
        executorService = Executors.newSingleThreadExecutor();

        // Load transactions with current filter
        loadTransactions();

        setupBottomNavigation();
    }

    private void setupBottomNavigation() {
        BottomNavigationView bottomNav = findViewById(R.id.bottomNavigation);
//        bottomNav.setSelectedItemId(R.id.nav_excluded);

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
//                    return true;

                case R.id.nav_patterns:
                    startActivity(new Intent(this, ExclusionPatternsActivity.class));
                    return true;
            }
            return false;
        });
    }

    private void setupFilterChips() {
        // Make sure chip group is initialized
        if (filterChips == null) {
            filterChips = findViewById(R.id.filterChipGroup);
            if (filterChips == null) {
                Log.e("ExcludedActivity", "filterChips is null!");
                return;
            }
        }

        // Add filter chips
        Chip allChip = (Chip) getLayoutInflater().inflate(R.layout.item_filter_chip, filterChips, false);
        allChip.setText("All Excluded");
        allChip.setChecked(true);

        Chip duplicatesChip = (Chip) getLayoutInflater().inflate(R.layout.item_filter_chip, filterChips, false);
        duplicatesChip.setText("Duplicates");

        Chip unknownChip = (Chip) getLayoutInflater().inflate(R.layout.item_filter_chip, filterChips, false);
        unknownChip.setText("Unknown Sources");

        filterChips.addView(allChip);
        filterChips.addView(duplicatesChip);
        filterChips.addView(unknownChip);

        // Set up chip selection listener
        filterChips.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == allChip.getId()) {
                currentFilter = FILTER_ALL;
            } else if (checkedId == duplicatesChip.getId()) {
                currentFilter = FILTER_DUPLICATES;
            } else if (checkedId == unknownChip.getId()) {
                currentFilter = FILTER_UNKNOWN_SOURCE;
            }

            loadTransactions();
        });
    }

    private void loadTransactions() {
        executorService.execute(() -> {
            TransactionDao dao = TransactionDatabase.getInstance(this).transactionDao();
            List<Transaction> transactions;

            switch (currentFilter) {
                case FILTER_DUPLICATES:
                    transactions = dao.getDuplicateTransactionsSync();
                    break;
                case FILTER_UNKNOWN_SOURCE:
                    transactions = dao.getUnknownSourceExcludedTransactionsSync();
                    break;
                case FILTER_ALL:
                default:
                    transactions = dao.getAllAutomaticallyExcludedTransactionsSync();
                    break;
            }

            runOnUiThread(() -> {
                if (transactions == null || transactions.isEmpty()) {
                    recyclerView.setVisibility(View.GONE);
                    emptyView.setVisibility(View.VISIBLE);
                } else {
                    recyclerView.setVisibility(View.VISIBLE);
                    emptyView.setVisibility(View.GONE);
                    adapter.setTransactions(transactions);
                }
            });
        });
    }

    private void showTransactionEditDialog(Transaction transaction) {
        TransactionEditDialog dialog = new TransactionEditDialog(transaction);
        dialog.setOnTransactionEditListener(this::updateTransaction);
        dialog.show(getSupportFragmentManager(), "edit_excluded_transaction");
    }

    private void updateTransaction(Transaction transaction) {
        executorService.execute(() -> {
            TransactionDao dao = TransactionDatabase.getInstance(this).transactionDao();
            dao.update(transaction);

            // If transaction is no longer excluded, we may need to remove it from the list
            if (!transaction.isExcludedFromTotal()) {
                runOnUiThread(() -> {
                    Toast.makeText(this, "Transaction included in totals", Toast.LENGTH_SHORT).show();
                    loadTransactions();
                });
            } else {
                runOnUiThread(() -> {
                    loadTransactions();
                });
            }
        });
    }

    private void showDeleteTransactionDialog(Transaction transaction) {
        new AlertDialog.Builder(this)
                .setTitle("Delete Transaction")
                .setMessage("Are you sure you want to permanently delete this transaction?\n\n" +
                        "Description: " + transaction.getDescription() + "\n" +
                        "Amount: ₹" + String.format("%.2f", transaction.getAmount()))
                .setPositiveButton("Delete", (dialog, which) -> {
                    deleteTransaction(transaction);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void deleteTransaction(Transaction transaction) {
        executorService.execute(() -> {
            TransactionDao dao = TransactionDatabase.getInstance(this).transactionDao();
            dao.deleteTransactionById(transaction.getId());

            runOnUiThread(() -> {
                Toast.makeText(this, "Transaction deleted", Toast.LENGTH_SHORT).show();
                loadTransactions();
            });
        });
    }

    private void showActionMenu() {
        String[] options = {"Include All", "Delete All"};
        
        new AlertDialog.Builder(this)
                .setTitle("Choose Action")
                .setItems(options, (dialog, which) -> {
                    switch (which) {
                        case 0:
                            showIncludeAllDialog();
                            break;
                        case 1:
                            showDeleteAllDialog();
                            break;
                    }
                })
                .show();
    }

    private void showIncludeAllDialog() {
        String message;
        switch (currentFilter) {
            case FILTER_DUPLICATES:
                message = "Include all duplicate transactions in totals?";
                break;
            case FILTER_UNKNOWN_SOURCE:
                message = "Include all unknown source transactions in totals?";
                break;
            case FILTER_ALL:
            default:
                message = "Include all automatically excluded transactions in totals?";
                break;
        }

        new AlertDialog.Builder(this)
                .setTitle("Include All")
                .setMessage(message)
                .setPositiveButton("Include All", (dialog, which) -> {
                    includeAllTransactions();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void includeAllTransactions() {
        executorService.execute(() -> {
            TransactionDao dao = TransactionDatabase.getInstance(this).transactionDao();
            int count = 0;

            switch (currentFilter) {
                case FILTER_DUPLICATES:
                    List<Transaction> duplicates = dao.getDuplicateTransactionsSync();
                    for (Transaction transaction : duplicates) {
                        // Remove [DUPLICATE] tag
                        String desc = transaction.getDescription()
                                .replace("[DUPLICATE]", "").trim();
                        transaction.setDescription(desc);
                        transaction.setExcludedFromTotal(false);
                        dao.update(transaction);
                        count++;
                    }
                    break;

                case FILTER_UNKNOWN_SOURCE:
                    List<Transaction> unknown = dao.getUnknownSourceExcludedTransactionsSync();
                    for (Transaction transaction : unknown) {
                        transaction.setExcludedFromTotal(false);
                        dao.update(transaction);
                        count++;
                    }
                    break;

                case FILTER_ALL:
                default:
                    List<Transaction> all = dao.getAllAutomaticallyExcludedTransactionsSync();
                    for (Transaction transaction : all) {
                        // Remove tags
                        String desc = transaction.getDescription()
                                .replace("[DUPLICATE]", "")
                                .replace("[AUTO-EXCLUDED]", "").trim();
                        transaction.setDescription(desc);
                        transaction.setExcludedFromTotal(false);
                        dao.update(transaction);
                        count++;
                    }
                    break;
            }

            final int updateCount = count;
            runOnUiThread(() -> {
                Toast.makeText(this,
                        updateCount + " transactions included in totals",
                        Toast.LENGTH_SHORT).show();
                loadTransactions();
            });
        });
    }

    private void showDeleteAllDialog() {
        String message;
        switch (currentFilter) {
            case FILTER_DUPLICATES:
                message = "Are you sure you want to permanently delete all duplicate transactions?";
                break;
            case FILTER_UNKNOWN_SOURCE:
                message = "Are you sure you want to permanently delete all unknown source transactions?";
                break;
            case FILTER_ALL:
            default:
                message = "Are you sure you want to permanently delete all automatically excluded transactions?";
                break;
        }

        new AlertDialog.Builder(this)
                .setTitle("Delete All")
                .setMessage(message + "\n\nThis action cannot be undone!")
                .setPositiveButton("Delete All", (dialog, which) -> {
                    deleteAllTransactions();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void deleteAllTransactions() {
        executorService.execute(() -> {
            TransactionDao dao = TransactionDatabase.getInstance(this).transactionDao();
            int count = 0;

            switch (currentFilter) {
                case FILTER_DUPLICATES:
                    List<Transaction> duplicates = dao.getDuplicateTransactionsSync();
                    for (Transaction transaction : duplicates) {
                        dao.deleteTransactionById(transaction.getId());
                        count++;
                    }
                    break;

                case FILTER_UNKNOWN_SOURCE:
                    List<Transaction> unknown = dao.getUnknownSourceExcludedTransactionsSync();
                    for (Transaction transaction : unknown) {
                        dao.deleteTransactionById(transaction.getId());
                        count++;
                    }
                    break;

                case FILTER_ALL:
                default:
                    List<Transaction> all = dao.getAllAutomaticallyExcludedTransactionsSync();
                    for (Transaction transaction : all) {
                        dao.deleteTransactionById(transaction.getId());
                        count++;
                    }
                    break;
            }

            final int deleteCount = count;
            runOnUiThread(() -> {
                Toast.makeText(this,
                        deleteCount + " transactions deleted permanently",
                        Toast.LENGTH_SHORT).show();
                loadTransactions();
            });
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
    protected void onDestroy() {
        super.onDestroy();
        if (executorService != null) {
            executorService.shutdown();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Refresh the badge count on bottom navigation
//        BottomNavigationView bottomNav = findViewById(R.id.bottomNavigation);
//        bottomNav.setSelectedItemId(R.id.nav_excluded); // Change this ID for each activity
    }
}