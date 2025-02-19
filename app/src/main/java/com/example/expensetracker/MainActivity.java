package com.example.expensetracker;

import androidx.appcompat.app.AppCompatActivity;

import android.Manifest;
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
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.expensetracker.adapters.TransactionAdapter;
import com.example.expensetracker.models.Transaction;
import com.example.expensetracker.receivers.SMSReceiver;
import com.example.expensetracker.repository.TransactionRepository;
import com.example.expensetracker.viewmodel.TransactionViewModel;
import com.google.android.material.card.MaterialCardView;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import android.app.DatePickerDialog;
import android.widget.Toast;
import android.widget.TextView;
import com.google.android.material.button.MaterialButton;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import com.example.expensetracker.utils.PreferencesManager;

public class MainActivity extends AppCompatActivity {
    private static final int SMS_PERMISSION_REQUEST_CODE = 123;

    private TransactionViewModel viewModel;
    private TransactionAdapter adapter;
    private Spinner bankSpinner;
    private Spinner typeSpinner;
    private EditText budgetInput;
    private TextView totalDebitsText;
    private TextView totalCreditsText;
    private TextView balanceText;
    private MaterialCardView alertCard;
    private TextView alertText;

    private long fromDate = 0;
    private long toDate = 0;
    private PreferencesManager preferencesManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initializeViews();
        setupSpinners();
        setupRecyclerView();
        setupViewModel();

        preferencesManager = new PreferencesManager(this);
        setupDateRangeUI();
//        setupDefaultDates();  // Add this line
        checkAndRequestSMSPermissions();
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

        // Load data for default date range
        loadExistingSMS();
    }

    private void initializeViews() {
        bankSpinner = findViewById(R.id.bankSpinner);
        typeSpinner = findViewById(R.id.typeSpinner);
        budgetInput = findViewById(R.id.budgetInput);
        totalDebitsText = findViewById(R.id.totalDebitsText);
        totalCreditsText = findViewById(R.id.totalCreditsText);
        balanceText = findViewById(R.id.balanceText);
        alertCard = findViewById(R.id.alertCard);
        alertText = findViewById(R.id.alertText);

        budgetInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                try {
                    double budget = Double.parseDouble(s.toString());
                    viewModel.setBudget(budget);
                } catch (NumberFormatException e) {
                    viewModel.setBudget(0.0);
                }
            }
        });
    }
    private void setupSpinners() {
        // Setup Bank Spinner
        ArrayAdapter<String> bankAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                new String[]{"All Banks", "HDFC", "ICICI", "SBI"}
        );
        bankAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        bankSpinner.setAdapter(bankAdapter);

        // Setup Type Spinner
        ArrayAdapter<String> typeAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                new String[]{"All Types", "DEBIT", "CREDIT"}
        );
        typeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        typeSpinner.setAdapter(typeAdapter);

        bankSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                updateTransactionsList();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        typeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                updateTransactionsList();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
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

        viewModel.getAllTransactions().observe(this, transactions -> {
            updateTransactionsList();
            updateSummary(transactions);
        });

        viewModel.getBudget().observe(this, budget -> {
            if (budget > 0) {
                double totalDebits = calculateTotalDebits(viewModel.getAllTransactions().getValue());
                double spendingPercentage = (totalDebits / budget) * 100;

                if (spendingPercentage > 70) {
                    alertCard.setVisibility(View.VISIBLE);
                    alertText.setText(String.format(Locale.getDefault(),
                            "Warning: You have spent %.1f%% of your monthly budget!",
                            spendingPercentage));
                } else {
                    alertCard.setVisibility(View.GONE);
                }
            }
        });
    }

    private void updateTransactionsList() {
        String selectedBank = bankSpinner.getSelectedItem().toString();
        String selectedType = typeSpinner.getSelectedItem().toString();

        List<Transaction> allTransactions = viewModel.getAllTransactions().getValue();
        if (allTransactions == null) return;

        List<Transaction> filteredTransactions = new ArrayList<>();
        for (Transaction transaction : allTransactions) {
            boolean bankMatch = selectedBank.equals("All Banks") || selectedBank.equals(transaction.getBank());
            boolean typeMatch = selectedType.equals("All Types") || selectedType.equals(transaction.getType());

            if (bankMatch && typeMatch) {
                filteredTransactions.add(transaction);
            }
        }

        adapter.setTransactions(filteredTransactions);
        updateSummary(filteredTransactions);
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
        balanceText.setText(String.format(Locale.getDefault(), "₹%.2f", totalCredits - totalDebits));
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

    private void checkAndRequestSMSPermissions() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS) !=
                PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) !=
                        PackageManager.PERMISSION_GRANTED) {

            // Request permissions
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{
                            Manifest.permission.RECEIVE_SMS,
                            Manifest.permission.READ_SMS
                    },
                    SMS_PERMISSION_REQUEST_CODE
            );
        } else {
            // Permissions already granted, proceed with setup
            setupDefaultDates();
        }
    }


    private void openAppSettings() {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        Uri uri = Uri.fromParts("package", getPackageName(), null);
        intent.setData(uri);
        startActivity(intent);
    }

//    @Override
//    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
//                                           @NonNull int[] grantResults) {
//        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
//        if (requestCode == SMS_PERMISSION_REQUEST_CODE) {
//            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
//                loadExistingSMS();
//            }
//        }
//    }

//    @Override
//    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
//                                           @NonNull int[] grantResults) {
//        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
//        if (requestCode == SMS_PERMISSION_REQUEST_CODE) {
//            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
//                // Permission granted, load SMS
//                loadExistingSMS();
//            } else {
//                // Permission denied, show message
//                Toast.makeText(this, "SMS permissions are required for this app to work",
//                        Toast.LENGTH_LONG).show();
//                // Optionally open settings
//                openAppSettings();
//            }
//        }
//    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
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

//    private void loadExistingSMS() {
//        Cursor cursor = getContentResolver().query(
//                Telephony.Sms.CONTENT_URI,
//                null,
//                null,
//                null,
//                Telephony.Sms.DATE + " DESC"
//        );
//
//        if (cursor != null) {
//            SMSReceiver receiver = new SMSReceiver();
//            while (cursor.moveToNext()) {
//                String messageBody = cursor.getString(cursor.getColumnIndex(Telephony.Sms.BODY));
//                receiver.parseAndSaveTransaction(this, messageBody);
//            }
//            cursor.close();
//        }
//    }

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

//    private void showDatePicker(boolean isFromDate) {
//        Calendar calendar = Calendar.getInstance();
//        calendar.setTimeInMillis(isFromDate ? fromDate : toDate);
//
//        new DatePickerDialog(this, (view, year, month, day) -> {
//            Calendar selectedDate = Calendar.getInstance();
//            selectedDate.set(year, month, day);
//            if (isFromDate) {
//                selectedDate.set(Calendar.HOUR_OF_DAY, 0);
//                selectedDate.set(Calendar.MINUTE, 0);
//                selectedDate.set(Calendar.SECOND, 0);
//                fromDate = selectedDate.getTimeInMillis();
//                ((MaterialButton) findViewById(R.id.fromDateButton))
//                        .setText(new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
//                                .format(new Date(fromDate)));
//            } else {
//                selectedDate.set(Calendar.HOUR_OF_DAY, 23);
//                selectedDate.set(Calendar.MINUTE, 59);
//                selectedDate.set(Calendar.SECOND, 59);
//                toDate = selectedDate.getTimeInMillis();
//                ((MaterialButton) findViewById(R.id.toDateButton))
//                        .setText(new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
//                                .format(new Date(toDate)));
//            }
//            loadExistingSMS(); // Load data automatically when date changes
//        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH),
//                calendar.get(Calendar.DAY_OF_MONTH)).show();
//    }

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
            loadExistingSMS(); // Load messages for new date range
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)).show();
    }

//    private void loadExistingSMS() {
////        long lastSyncTime = preferencesManager.getLastSyncTime();
////
////        // If we have already synced before, only get new messages
////        if (lastSyncTime > 0) {
////            fromDate = lastSyncTime;
////            toDate = System.currentTimeMillis();
////        }
//
//        String selection = Telephony.Sms.DATE + " BETWEEN ? AND ?";
//        String[] selectionArgs = new String[]{String.valueOf(fromDate), String.valueOf(toDate)};
//
//        Cursor cursor = getContentResolver().query(
//                Telephony.Sms.CONTENT_URI,
//                null,
//                selection,
//                selectionArgs,
//                Telephony.Sms.DATE + " DESC"
//        );
//
//        if (cursor != null) {
//            SMSReceiver receiver = new SMSReceiver();
//            int count = 0;
//            while (cursor.moveToNext()) {
//                String messageBody = cursor.getString(cursor.getColumnIndex(Telephony.Sms.BODY));
//                receiver.parseAndSaveTransaction(this, messageBody);
//                count++;
//            }
//            cursor.close();
//
//            // Update last sync time
//            preferencesManager.setLastSyncTime(System.currentTimeMillis());
//
//            Toast.makeText(this, "Processed " + count + " messages", Toast.LENGTH_SHORT).show();
//
//            // Hide date range selection after initial load
//            //findViewById(R.id.dateRangeLayout).setVisibility(View.GONE);
//        }
//    }

    private void loadExistingSMS() {
        Log.d("MainActivity", "Loading SMS between dates: " + new Date(fromDate) + " to " + new Date(toDate));

        // First convert the dates to match SMS date format (milliseconds)
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

                // Debug log to see what messages we're processing
                Log.d("MainActivity", "Message date: " + new Date(messageDate) + "\nContent: " + messageBody);

                // Only process if within date range
                if (messageDate >= fromDate && messageDate <= toDate) {
                    receiver.parseAndSaveTransaction(this, messageBody);
                    count++;
                }
            }
            cursor.close();

            Toast.makeText(this, "Processed " + count + " messages", Toast.LENGTH_SHORT).show();
        }
    }

}