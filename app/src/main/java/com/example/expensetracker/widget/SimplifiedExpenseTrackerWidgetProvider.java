package com.example.expensetracker.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.RemoteViews;

import com.example.expensetracker.MainActivity;
import com.example.expensetracker.R;
import com.example.expensetracker.models.Transaction;
import com.example.expensetracker.repository.TransactionRepository;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SimplifiedExpenseTrackerWidgetProvider extends AppWidgetProvider {

    private static final String TAG = "ExpenseWidgetSimple";
    private static final String ACTION_UPDATE_WIDGET = "com.example.expensetracker.widget.UPDATE_WIDGET";

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        Log.d(TAG, "onUpdate called for " + appWidgetIds.length + " widgets");

        // Update each widget instance
        for (int widgetId : appWidgetIds) {
            updateAppWidget(context, appWidgetManager, widgetId);
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "onReceive: " + intent.getAction());

        if (ACTION_UPDATE_WIDGET.equals(intent.getAction())) {
            // Trigger update for all instances of the widget
            AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
            int[] appWidgetIds = appWidgetManager.getAppWidgetIds(
                    new ComponentName(context, SimplifiedExpenseTrackerWidgetProvider.class));

            onUpdate(context, appWidgetManager, appWidgetIds);
        } else {
            super.onReceive(context, intent);
        }
    }

    // Make this method static so it can be called from the configure activity
    public static void updateAppWidget(Context context, AppWidgetManager appWidgetManager, int appWidgetId) {
        Log.d(TAG, "Starting updateWidget for ID: " + appWidgetId);

        try {
            // Create RemoteViews
            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.simplified_expense_tracker_widget);

            // Set up click intent for header - opens app
            Intent openAppIntent = new Intent(context, MainActivity.class);
            PendingIntent openAppPendingIntent = PendingIntent.getActivity(context, 0, openAppIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            views.setOnClickPendingIntent(R.id.headerLayout, openAppPendingIntent);

            // Set up refresh button click
            Intent refreshIntent = new Intent(context, SimplifiedExpenseTrackerWidgetProvider.class);
            refreshIntent.setAction(ACTION_UPDATE_WIDGET);
            PendingIntent refreshPendingIntent = PendingIntent.getBroadcast(context, 0, refreshIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            views.setOnClickPendingIntent(R.id.refreshButton, refreshPendingIntent);

            // Add current date to verify update
            SimpleDateFormat dateFormat = new SimpleDateFormat("MMMM yyyy", Locale.getDefault());
            views.setTextViewText(R.id.currentPeriodText, dateFormat.format(new Date()));

            // Set "last updated" text with current time
            SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
            views.setTextViewText(R.id.lastUpdatedText, "Last updated: " + timeFormat.format(new Date()));

            // Update the widget with static data first to ensure it displays
            appWidgetManager.updateAppWidget(appWidgetId, views);
            Log.d(TAG, "Updated widget with static data");

            // Then load real data asynchronously
            loadFinancialData(context, views, appWidgetManager, appWidgetId);

        } catch (Exception e) {
            Log.e(TAG, "Error updating widget: " + e.getMessage(), e);
        }
    }

    private static void loadFinancialData(Context context, RemoteViews views,
                                          AppWidgetManager appWidgetManager, int appWidgetId) {
        Log.d(TAG, "Starting to load financial data");

        // Create repository to fetch data
        TransactionRepository repository = new TransactionRepository(
                (android.app.Application) context.getApplicationContext());

        // Use a thread pool for background work
        ExecutorService executorService = Executors.newSingleThreadExecutor();

        executorService.execute(() -> {
            try {
                // Get current month date range
                Calendar cal = Calendar.getInstance();

                // End of today
                cal.set(Calendar.HOUR_OF_DAY, 23);
                cal.set(Calendar.MINUTE, 59);
                cal.set(Calendar.SECOND, 59);
                long endDate = cal.getTimeInMillis();

                // Start of current month
                cal.set(Calendar.DAY_OF_MONTH, 1);
                cal.set(Calendar.HOUR_OF_DAY, 0);
                cal.set(Calendar.MINUTE, 0);
                cal.set(Calendar.SECOND, 0);
                long startDate = cal.getTimeInMillis();

                // Fetch transactions
                List<Transaction> transactions = repository.getTransactionsBetweenDatesSync(startDate, endDate);
                Log.d(TAG, "Fetched " + transactions.size() + " transactions");

                // Calculate totals
                double totalIncome = 0;
                double totalExpenses = 0;

                for (Transaction transaction : transactions) {
                    if (!transaction.isExcludedFromTotal()) {
                        if ("CREDIT".equals(transaction.getType())) {
                            totalIncome += transaction.getAmount();
                        } else if ("DEBIT".equals(transaction.getType())) {
                            totalExpenses += transaction.getAmount();
                        }
                    }
                }

                // Placeholder budget value - get from preferences in real implementation
                double budget = 100000;

                // Calculate remaining amount
                double remainingAmount = budget - totalExpenses;

                // Calculate budget percentage
                int budgetPercentage = (int)((totalExpenses / budget) * 100);
                if (budgetPercentage > 100) budgetPercentage = 100;

                // Format currency values
                String formattedIncome = String.format(Locale.getDefault(), "₹%.2f", totalIncome);
                String formattedExpenses = String.format(Locale.getDefault(), "₹%.2f", totalExpenses);
                String formattedRemaining = String.format(Locale.getDefault(), "₹%.2f", remainingAmount);

                // Update UI on main thread
                int finalBudgetPercentage = budgetPercentage;
                new Handler(Looper.getMainLooper()).post(() -> {
                    try {
                        views.setTextViewText(R.id.incomeText, formattedIncome);
                        views.setTextViewText(R.id.expensesText, formattedExpenses);
                        views.setTextViewText(R.id.remainingText, formattedRemaining);
                        views.setProgressBar(R.id.budgetProgress, 100, finalBudgetPercentage, false);

                        // Update widget
                        appWidgetManager.updateAppWidget(appWidgetId, views);
                        Log.d(TAG, "Widget updated with real data");
                    } catch (Exception e) {
                        Log.e(TAG, "Error updating widget UI: " + e.getMessage(), e);
                    }
                });

            } catch (Exception e) {
                Log.e(TAG, "Error loading financial data: " + e.getMessage(), e);
            } finally {
                executorService.shutdown();
            }
        });
    }

    @Override
    public void onEnabled(Context context) {
        Log.d(TAG, "onEnabled called - first widget instance created");
    }

    @Override
    public void onDisabled(Context context) {
        Log.d(TAG, "onDisabled called - last widget instance removed");
    }
}