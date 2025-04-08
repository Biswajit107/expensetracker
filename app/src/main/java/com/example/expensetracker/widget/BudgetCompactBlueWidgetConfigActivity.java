package com.example.expensetracker.widget;

import android.app.Activity;
import android.app.DatePickerDialog;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.example.expensetracker.R;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class BudgetCompactBlueWidgetConfigActivity extends Activity {

    private int appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID;
    private long fromDate = 0;
    private long toDate = 0;
    private Button fromDateButton;
    private Button toDateButton;
    private Button resetDatesButton;
    private Button saveButton;
    private TextView dateRangeSummary;
    private TextView titleText;

    // Flag to detect if this is a reconfiguration
    private boolean isReconfiguring = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Set result to canceled in case the user backs out
        setResult(RESULT_CANCELED);

        setContentView(R.layout.activity_widget_config);

        // Find views
        fromDateButton = findViewById(R.id.fromDateButton);
        toDateButton = findViewById(R.id.toDateButton);
        resetDatesButton = findViewById(R.id.resetDatesButton);
        saveButton = findViewById(R.id.saveButton);
        dateRangeSummary = findViewById(R.id.dateRangeSummary);
        titleText = findViewById(R.id.configTitle);

        // Get widget ID from the intent
        Intent intent = getIntent();
        Bundle extras = intent.getExtras();
        if (extras != null) {
            appWidgetId = extras.getInt(
                    AppWidgetManager.EXTRA_APPWIDGET_ID,
                    AppWidgetManager.INVALID_APPWIDGET_ID);
        }

        // If we didn't get a valid widget ID, exit
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish();
            return;
        }

        // Check if this is a reconfiguration
        SharedPreferences prefs = getSharedPreferences(BudgetCompactBlueWidgetProvider.PREFS_NAME, MODE_PRIVATE);
        fromDate = prefs.getLong(BudgetCompactBlueWidgetProvider.PREF_PREFIX_FROM_DATE + appWidgetId, 0);
        toDate = prefs.getLong(BudgetCompactBlueWidgetProvider.PREF_PREFIX_TO_DATE + appWidgetId, 0);

        if (fromDate != 0 && toDate != 0) {
            isReconfiguring = true;
            // Change title to reflect that we're updating an existing widget
            if (titleText != null) {
                titleText.setText("Update Widget Date Range");
            }
            // Change save button text
            if (saveButton != null) {
                saveButton.setText("Update Widget");
            }
        }

        // Set default dates or load existing ones
        setupDefaultDates();

        // Setup button click listeners
        fromDateButton.setOnClickListener(v -> showDatePicker(true));
        toDateButton.setOnClickListener(v -> showDatePicker(false));
        resetDatesButton.setOnClickListener(v -> resetToCurrentMonth());

        saveButton.setOnClickListener(v -> {
            // Save the widget settings and update the widget
            saveWidgetSettings();
            updateWidget();

            // Only set result when first configuring, not when reconfiguring
            if (!isReconfiguring) {
                // Create the result intent for the first-time setup
                Intent resultValue = new Intent();
                resultValue.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
                setResult(RESULT_OK, resultValue);
            }

            // Close the activity
            finish();
        });
    }

    private void setupDefaultDates() {
        // If no saved dates, use current month
        if (fromDate == 0 || toDate == 0) {
            resetToCurrentMonth();
        } else {
            updateDateButtonTexts();
        }
    }

    private void resetToCurrentMonth() {
        Calendar calendar = Calendar.getInstance();

        // Set to end of today
        calendar.set(Calendar.HOUR_OF_DAY, 23);
        calendar.set(Calendar.MINUTE, 59);
        calendar.set(Calendar.SECOND, 59);
        toDate = calendar.getTimeInMillis();

        // Set to first day of current month
        calendar.set(Calendar.DAY_OF_MONTH, 1);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        fromDate = calendar.getTimeInMillis();

        updateDateButtonTexts();
        Toast.makeText(this, "Date range reset to current month", Toast.LENGTH_SHORT).show();
    }

    private void showDatePicker(boolean isFromDate) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(isFromDate ? fromDate : toDate);

        DatePickerDialog datePickerDialog = new DatePickerDialog(this,
                (view, year, month, day) -> {
                    Calendar selectedDate = Calendar.getInstance();
                    selectedDate.set(year, month, day);

                    if (isFromDate) {
                        selectedDate.set(Calendar.HOUR_OF_DAY, 0);
                        selectedDate.set(Calendar.MINUTE, 0);
                        selectedDate.set(Calendar.SECOND, 0);
                        fromDate = selectedDate.getTimeInMillis();
                    } else {
                        selectedDate.set(Calendar.HOUR_OF_DAY, 23);
                        selectedDate.set(Calendar.MINUTE, 59);
                        selectedDate.set(Calendar.SECOND, 59);
                        toDate = selectedDate.getTimeInMillis();
                    }

                    // Validate the date range
                    if (fromDate > toDate) {
                        Toast.makeText(this, "Start date cannot be after end date", Toast.LENGTH_SHORT).show();
                        if (isFromDate) {
                            fromDate = toDate;
                        } else {
                            toDate = fromDate;
                        }
                    }

                    updateDateButtonTexts();
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH));

        datePickerDialog.show();
    }

    private void updateDateButtonTexts() {
        SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());

        fromDateButton.setText(dateFormat.format(new Date(fromDate)));
        toDateButton.setText(dateFormat.format(new Date(toDate)));

        // Update summary
        dateRangeSummary.setText(String.format("Widget will show data from %s to %s",
                dateFormat.format(new Date(fromDate)),
                dateFormat.format(new Date(toDate))));
    }

    private void saveWidgetSettings() {
        // Save date range to preferences
        SharedPreferences.Editor prefs = getSharedPreferences(BudgetCompactBlueWidgetProvider.PREFS_NAME, MODE_PRIVATE).edit();
        prefs.putLong(BudgetCompactBlueWidgetProvider.PREF_PREFIX_FROM_DATE + appWidgetId, fromDate);
        prefs.putLong(BudgetCompactBlueWidgetProvider.PREF_PREFIX_TO_DATE + appWidgetId, toDate);
        prefs.apply();

        Toast.makeText(this, isReconfiguring ? "Widget date range updated" : "Widget settings saved", Toast.LENGTH_SHORT).show();
    }

    private void updateWidget() {
        // Update the widget
        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(this);
        BudgetCompactBlueWidgetProvider.updateAppWidget(this, appWidgetManager, appWidgetId);
    }
}