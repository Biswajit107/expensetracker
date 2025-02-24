package com.example.expensetracker;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.Manifest;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.provider.Telephony;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.example.expensetracker.adapters.TransactionAdapter;
import com.example.expensetracker.database.TransactionDatabase;
import com.example.expensetracker.models.Transaction;
import com.example.expensetracker.receivers.SMSReceiver;
import com.example.expensetracker.viewmodel.TransactionViewModel;
import com.example.expensetracker.utils.PreferencesManager;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import android.widget.AutoCompleteTextView;
import android.widget.ArrayAdapter;

public class MainActivity extends AppCompatActivity {
    private static final int SMS_PERMISSION_REQUEST_CODE = 123;

    private TransactionViewModel viewModel;
    private TransactionAdapter adapter;
    private AutoCompleteTextView bankSpinner;
    private AutoCompleteTextView typeSpinner;
    private EditText budgetInput;
    private TextView totalDebitsText;
    private TextView totalCreditsText;
    private TextView balanceText;
    private MaterialCardView alertCard;
    private TextView alertText;

    private long fromDate = 0;
    private long toDate = 0;
    private PreferencesManager preferencesManager;
    private ExecutorService executorService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize ExecutorService
        executorService = Executors.newSingleThreadExecutor();

        initializeViews();
        setupSpinners();
        setupRecyclerView();
        setupViewModel();

        preferencesManager = new PreferencesManager(this);
        setupDateRangeUI();
        setupDefaultDates();
        checkAndRequestSMSPermissions();
        setupBottomNavigation();
    }

    private void setupDefaultDates() {
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

        // Update button texts
        SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
        ((MaterialButton) findViewById(R.id.fromDateButton)).setText(dateFormat.format(new Date(fromDate)));
        ((MaterialButton) findViewById(R.id.toDateButton)).setText(dateFormat.format(new Date(toDate)));
    }

    private void initializeViews() {
        // Find AutoCompleteTextViews instead of Spinners
        bankSpinner = findViewById(R.id.bankSpinner);
        typeSpinner = findViewById(R.id.typeSpinner);
        budgetInput = findViewById(R.id.budgetInput);
        totalDebitsText = findViewById(R.id.totalDebitsText);
        totalCreditsText = findViewById(R.id.totalCreditsText);
        balanceText = findViewById(R.id.balanceText);
        alertCard = findViewById(R.id.alertCard);
        alertText = findViewById(R.id.alertText);

        // Rest of the method remains the same
        budgetInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                try {
                    // Parse the budget value
                    double budget = s.toString().isEmpty() ? 0 : Double.parseDouble(s.toString());

                    // Directly set the budget in ViewModel
                    viewModel.setBudget(budget);
                } catch (NumberFormatException e) {
                    // Handle invalid input
                    viewModel.setBudget(0.0);
                }
            }
        });
    }

    private void setupSpinners() {
        // Setup Bank Spinner
        ArrayAdapter<String> bankAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_dropdown_item,
                new String[]{"All Banks", "HDFC", "ICICI", "SBI"}
        );
        bankSpinner.setAdapter(bankAdapter);

        // Setup Type Spinner
        ArrayAdapter<String> typeAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_dropdown_item,
                new String[]{"All Types", "DEBIT", "CREDIT"}
        );
        typeSpinner.setAdapter(typeAdapter);

        // Set default values
        bankSpinner.setText("All Banks", false);
        typeSpinner.setText("All Types", false);

        bankSpinner.setOnItemClickListener((parent, view, position, id) -> {
            updateTransactionsList();
        });

        typeSpinner.setOnItemClickListener((parent, view, position, id) -> {
            updateTransactionsList();
        });
    }

    private void setupRecyclerView() {
        RecyclerView recyclerView = findViewById(R.id.recyclerView);
        adapter = new TransactionAdapter();
        recyclerView.setAdapter(adapter);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
    }

    private void setupViewModel() {
        viewModel = new ViewModelProvider(this).get(TransactionViewModel.class);

        // Observe transactions between dates
        executorService.execute(() -> {
            // Retrieve transactions for the current date range
            List<Transaction> transactions = TransactionDatabase.getInstance(this)
                    .transactionDao()
                    .getTransactionsBetweenDatesSync(fromDate, toDate);

            runOnUiThread(() -> {
                if (transactions != null && !transactions.isEmpty()) {
                    updateTransactionsList(transactions);
                } else {
                    adapter.setTransactions(new ArrayList<>());
                    updateSummary(new ArrayList<>());
                }
            });
        });

        viewModel.getBudget().observe(this, budget -> {
//            if (budget > 0) {
//                viewModel.getTransactionsBetweenDates(fromDate, toDate, transactions -> {
//                    double totalDebits = viewModel.calculateTotalDebits(transactions);
//                    double spendingPercentage = (totalDebits / budget) * 100;
//
//                    if (spendingPercentage > 70) {
//                        alertCard.setVisibility(View.VISIBLE);
//                        alertText.setText(String.format(Locale.getDefault(),
//                                "Warning: You have spent %.1f%% of your monthly budget!",
//                                spendingPercentage));
//                    } else {
//                        alertCard.setVisibility(View.GONE);
//                    }

//                });
//            }
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

    private void updateSummaryWithBudget(List<Transaction> transactions, double budget) {
        // Calculate total debits
        double totalDebits = 0;
        for (Transaction transaction : transactions) {
            if ("DEBIT".equals(transaction.getType())) {
                totalDebits += transaction.getAmount();
            }
        }

        // Calculate remaining balance
        double remainingBalance = budget - totalDebits;
        double spendingPercentage = (totalDebits / budget) * 100;

        // Update UI elements
        totalDebitsText.setText(String.format(Locale.getDefault(), "₹%.2f", totalDebits));
        balanceText.setText(String.format(Locale.getDefault(), "₹%.2f", remainingBalance));

        // Budget alert logic
        if (budget > 0) {
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

    private void resetBudgetUI(double budget) {
        // Reset UI when no budget is set or transactions are empty
//        totalDebitsText.setText("₹0.00");
        balanceText.setText(budget > 0 ?
                String.format(Locale.getDefault(), "₹%.2f", budget) :
                "₹0.00");
        alertCard.setVisibility(View.GONE);
    }

    private void updateTransactionsList(List<Transaction> allTransactions) {
        String selectedBank = bankSpinner.getText().toString();
        String selectedType = typeSpinner.getText().toString();

        List<Transaction> filteredTransactions = new ArrayList<>();
        for (Transaction transaction : allTransactions) {
            boolean bankMatch = selectedBank.equals("All Banks") || selectedBank.equals(transaction.getBank());
            boolean typeMatch = selectedType.equals("All Types") || selectedType.equals(transaction.getType());

            if (bankMatch && typeMatch) {
                filteredTransactions.add(transaction);
            }
        }

        adapter.setTransactions(filteredTransactions);

        // Get budget from input
        double budget = 0;
        try {
            budget = budgetInput.getText().toString().isEmpty() ? 0 :
                    Double.parseDouble(budgetInput.getText().toString());
        } catch (NumberFormatException e) {
            // Handle parsing error
        }

        updateSummaryWithBudget(filteredTransactions, budget);
    }

    private void updateTransactionsList() {
        String selectedBank = bankSpinner.getText().toString();
        String selectedType = typeSpinner.getText().toString();

        viewModel.getTransactionsBetweenDates(fromDate, toDate, transactions -> {
            if (transactions == null) return;

            List<Transaction> filteredTransactions = new ArrayList<>();
            for (Transaction transaction : transactions) {
                boolean bankMatch = selectedBank.equals("All Banks") || selectedBank.equals(transaction.getBank());
                boolean typeMatch = selectedType.equals("All Types") || selectedType.equals(transaction.getType());

                if (bankMatch && typeMatch) {
                    filteredTransactions.add(transaction);
                }
            }

            adapter.setTransactions(filteredTransactions);
            updateSummary(filteredTransactions);
        });
    }

    private void updateSummary(List<Transaction> transactions) {
        double totalDebits = 0;
        double totalCredits = 0;

        for (Transaction transaction : transactions) {
            if ("DEBIT".equals(transaction.getType())) {
                totalDebits += transaction.getAmount();
            } else if ("CREDIT".equals(transaction.getType())) {
                totalCredits += transaction.getAmount();
            }
        }

        totalDebitsText.setText(String.format(Locale.getDefault(), "₹%.2f", totalDebits));
        totalCreditsText.setText(String.format(Locale.getDefault(), "₹%.2f", totalCredits));
//        balanceText.setText(String.format(Locale.getDefault(), "₹%.2f", totalCredits - totalDebits));
    }

    private void debugTransactionRetrieval() {
        executorService.execute(() -> {
            // Synchronous retrieval for debugging
            List<Transaction> transactions = TransactionDatabase.getInstance(this)
                    .transactionDao()
                    .getTransactionsBetweenDatesSync(fromDate, toDate);

            Log.d("TransactionDebug", "Sync method - Total transactions: " + transactions.size());

            for (Transaction transaction : transactions) {
                Log.d("TransactionDebug", "Sync Transaction: " +
                        "ID: " + transaction.getId() +
                        ", Date: " + new Date(transaction.getDate()) +
                        ", Amount: " + transaction.getAmount() +
                        ", Bank: " + transaction.getBank());
            }
        });
    }

    private double calculateTotalDebits(List<Transaction> transactions) {
        if (transactions == null) return 0;
        double total = 0;
        for (Transaction transaction : transactions) {
            if ("DEBIT".equals(transaction.getType())) {
                total += transaction.getAmount();
            }
        }
        return total;
    }

    private void setupBottomNavigation() {
        BottomNavigationView bottomNav = findViewById(R.id.bottomNavigation);
        bottomNav.setSelectedItemId(R.id.nav_home);
        bottomNav.setOnItemSelectedListener(item -> {
            switch (item.getItemId()) {
                case R.id.nav_home:
                    return true;
                case R.id.nav_analytics:
                    startActivity(new Intent(this, AnalyticsActivity.class));
                    finish();
                    return true;
                case R.id.nav_predictions:
                    startActivity(new Intent(this, PredictionActivity.class));
                    finish();
                    return true;
                case R.id.nav_groups:
                    startActivity(new Intent(this, GroupedExpensesActivity.class));
                    finish();
                    return true;
            }
            return false;
        });
    }

    private void setupDateRangeUI() {
        MaterialButton fromDateButton = findViewById(R.id.fromDateButton);
        MaterialButton toDateButton = findViewById(R.id.toDateButton);
        MaterialButton loadMessagesButton = findViewById(R.id.loadMessagesButton);

        fromDateButton.setOnClickListener(v -> showDatePicker(true));
        toDateButton.setOnClickListener(v -> showDatePicker(false));

        loadMessagesButton.setOnClickListener(v -> {
            if (fromDate == 0 || toDate == 0) {
                Toast.makeText(this, "Please select both dates", Toast.LENGTH_SHORT).show();
                return;
            }
            loadExistingSMS();
        });
    }

    private void showDatePicker(boolean isFromDate) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(isFromDate ? fromDate : toDate);

        new DatePickerDialog(this, (view, year, month, day) -> {
            Calendar selectedDate = Calendar.getInstance();
            selectedDate.set(year, month, day);
            if (isFromDate) {
                selectedDate.set(Calendar.HOUR_OF_DAY, 0);
                selectedDate.set(Calendar.MINUTE, 0);
                selectedDate.set(Calendar.SECOND, 0);
                selectedDate.set(Calendar.MILLISECOND, 0);
                fromDate = selectedDate.getTimeInMillis();
                ((MaterialButton) findViewById(R.id.fromDateButton))
                        .setText(new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                                .format(new Date(fromDate)));
            } else {
                selectedDate.set(Calendar.HOUR_OF_DAY, 23);
                selectedDate.set(Calendar.MINUTE, 59);
                selectedDate.set(Calendar.SECOND, 59);
                selectedDate.set(Calendar.MILLISECOND, 999);
                toDate = selectedDate.getTimeInMillis();
                ((MaterialButton) findViewById(R.id.toDateButton))
                        .setText(new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                                .format(new Date(toDate)));
            }

            // Update transactions for new date range using callback
            viewModel.getTransactionsBetweenDates(fromDate, toDate, transactions -> {
                if (transactions != null && !transactions.isEmpty()) {
                    updateTransactionsList(transactions);
                } else {
                    adapter.setTransactions(new ArrayList<>());
                    updateSummary(new ArrayList<>());
                }
            });
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void loadExistingSMS() {
        Log.d("MainActivity", "Loading SMS between dates: " + new Date(fromDate) + " to " + new Date(toDate));

        executorService.execute(() -> {
            // First, process new SMS messages
            processSMSMessages();

            // Debug transaction retrieval
            debugTransactionRetrieval();

            // Retrieve transactions
            List<Transaction> transactions = TransactionDatabase.getInstance(this)
                    .transactionDao()
                    .getTransactionsBetweenDatesSync(fromDate, toDate);

            // Then, update the transaction list with transactions in the date range
            runOnUiThread(() -> {
                // This will trigger the observer in setupViewModel()
//                viewModel.getTransactionsBetweenDates(fromDate, toDate);
                if (transactions != null && !transactions.isEmpty()) {
                    updateTransactionsList(transactions);
                    updateSummary(transactions);
                } else {
                    // Handle empty list
                    adapter.setTransactions(new ArrayList<>());
                    updateSummary(new ArrayList<>());
                    Toast.makeText(this, "No transactions found", Toast.LENGTH_SHORT).show();
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

        Cursor cursor = getContentResolver().query(
                Telephony.Sms.CONTENT_URI,
                null,
                selection,
                selectionArgs,
                Telephony.Sms.DATE + " DESC"
        );

        if (cursor != null) {
            SMSReceiver receiver = new SMSReceiver();
            int count = 0;
            while (cursor.moveToNext()) {
                String messageBody = cursor.getString(cursor.getColumnIndex(Telephony.Sms.BODY));
                long messageDate = cursor.getLong(cursor.getColumnIndex(Telephony.Sms.DATE));

                Log.d("MainActivity", "Message date: " + new Date(messageDate) + "\nContent: " + messageBody);

                if (messageDate >= fromDate && messageDate <= toDate) {
                    receiver.parseAndSaveTransaction(this, messageBody);
                    count++;
                }
            }
            cursor.close();

            final int processedCount = count;
            runOnUiThread(() -> {
                Toast.makeText(this, "Processed " + processedCount + " messages", Toast.LENGTH_SHORT).show();
            });
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

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == SMS_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted, now set up dates and load SMS
                setupDefaultDates();
            } else {
                Toast.makeText(this, "SMS permissions are required for this app to work",
                        Toast.LENGTH_LONG).show();
            }
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
}