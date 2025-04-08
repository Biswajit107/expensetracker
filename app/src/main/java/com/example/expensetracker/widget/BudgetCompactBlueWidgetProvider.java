package com.example.expensetracker.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.RemoteViews;
import android.widget.Toast;

import com.example.expensetracker.MainActivity;
import com.example.expensetracker.R;
import com.example.expensetracker.models.Transaction;
import com.example.expensetracker.repository.TransactionRepository;
import com.example.expensetracker.utils.PreferencesManager;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class BudgetCompactBlueWidgetProvider extends AppWidgetProvider {

    private static final String TAG = "CompactBudgetWidget";
    private static final String ACTION_UPDATE_WIDGET = "com.example.expensetracker.widget.UPDATE_COMPACT_BLUE_WIDGET";
    private static final String ACTION_REFRESH_WIDGET = "com.example.expensetracker.widget.REFRESH_COMPACT_BLUE_WIDGET";
    private static final String ACTION_RECONFIGURE_WIDGET = "com.example.expensetracker.widget.RECONFIGURE_COMPACT_BLUE_WIDGET";

    // Preference keys for storing widget date ranges
    public static final String PREFS_NAME = "com.example.expensetracker.widget.BudgetCompactBlueWidget";
    public static final String PREF_PREFIX_FROM_DATE = "widget_from_date_";
    public static final String PREF_PREFIX_TO_DATE = "widget_to_date_";

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

        // Handle reconfigure action
        if (ACTION_RECONFIGURE_WIDGET.equals(intent.getAction())) {
            int widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,
                    AppWidgetManager.INVALID_APPWIDGET_ID);

            if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                // Launch configuration activity for the specific widget
                Intent configIntent = new Intent(context, BudgetCompactBlueWidgetConfigActivity.class);
                configIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId);
                configIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(configIntent);
            }
            return;
        }

        // Handle both update and refresh actions
        if (ACTION_UPDATE_WIDGET.equals(intent.getAction()) ||
                ACTION_REFRESH_WIDGET.equals(intent.getAction())) {

            // Show toast for refresh action
            if (ACTION_REFRESH_WIDGET.equals(intent.getAction())) {
                Toast.makeText(context, "Refreshing budget data...", Toast.LENGTH_SHORT).show();
            }

            // Trigger update for all instances of the widget
            AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
            int[] appWidgetIds = appWidgetManager.getAppWidgetIds(
                    new ComponentName(context, BudgetCompactBlueWidgetProvider.class));

            onUpdate(context, appWidgetManager, appWidgetIds);
        } else {
            super.onReceive(context, intent);
        }
    }

    public static void updateAppWidget(Context context, AppWidgetManager appWidgetManager, int appWidgetId) {
        Log.d(TAG, "Starting updateWidget for ID: " + appWidgetId);

        try {
            // Create RemoteViews
            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_budget_compact_blue);

            // Set up click intent for widget - opens app
            Intent openAppIntent = new Intent(context, MainActivity.class);
            PendingIntent openAppPendingIntent = PendingIntent.getActivity(context, 0, openAppIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            views.setOnClickPendingIntent(R.id.widgetTitle, openAppPendingIntent);

            // Set up refresh button click intent
            Intent refreshIntent = new Intent(context, BudgetCompactBlueWidgetProvider.class);
            refreshIntent.setAction(ACTION_REFRESH_WIDGET);
            PendingIntent refreshPendingIntent = PendingIntent.getBroadcast(context, 0, refreshIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            views.setOnClickPendingIntent(R.id.refreshButton, refreshPendingIntent);

            // New: Set up reconfigure intent for clicking on the date text
            Intent reconfigureIntent = new Intent(context, BudgetCompactBlueWidgetProvider.class);
            reconfigureIntent.setAction(ACTION_RECONFIGURE_WIDGET);
            reconfigureIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
            PendingIntent reconfigurePendingIntent = PendingIntent.getBroadcast(
                    context,
                    appWidgetId, // Use widget ID as request code to make intents unique
                    reconfigureIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            views.setOnClickPendingIntent(R.id.dateText, reconfigurePendingIntent);

            // Get the date range from preferences for this widget
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            long fromDate = prefs.getLong(PREF_PREFIX_FROM_DATE + appWidgetId, 0);
            long toDate = prefs.getLong(PREF_PREFIX_TO_DATE + appWidgetId, 0);

            // If no custom date range, use current month
            if (fromDate == 0 || toDate == 0) {
                Calendar cal = Calendar.getInstance();

                // End of today
                cal.set(Calendar.HOUR_OF_DAY, 23);
                cal.set(Calendar.MINUTE, 59);
                cal.set(Calendar.SECOND, 59);
                toDate = cal.getTimeInMillis();

                // Start of current month
                cal.set(Calendar.DAY_OF_MONTH, 1);
                cal.set(Calendar.HOUR_OF_DAY, 0);
                cal.set(Calendar.MINUTE, 0);
                cal.set(Calendar.SECOND, 0);
                fromDate = cal.getTimeInMillis();
            }

            // Set current date in widget
            SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd", Locale.getDefault());

            // Format date range
            String fromDateStr = dateFormat.format(new Date(fromDate));
            String toDateStr = dateFormat.format(new Date(toDate));
            String dateRangeText = fromDateStr + " - " + toDateStr;

            views.setTextViewText(R.id.dateText, dateRangeText);

            // Update the widget with static data first to ensure it displays
            appWidgetManager.updateAppWidget(appWidgetId, views);
            Log.d(TAG, "Updated widget with static data");

            // Then load real data asynchronously
            loadFinancialData(context, views, appWidgetManager, appWidgetId, fromDate, toDate);

        } catch (Exception e) {
            Log.e(TAG, "Error updating widget: " + e.getMessage(), e);
        }
    }

    private static void loadFinancialData(Context context, RemoteViews views,
                                          AppWidgetManager appWidgetManager, int appWidgetId,
                                          long startDate, long endDate) {
        Log.d(TAG, "Starting to load financial data");

        // Create repository to fetch data
        TransactionRepository repository = new TransactionRepository(
                (android.app.Application) context.getApplicationContext());

        // Use a thread pool for background work
        ExecutorService executorService = Executors.newSingleThreadExecutor();

        executorService.execute(() -> {
            try {
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

                // Get actual budget value from preferences
                PreferencesManager preferencesManager = new PreferencesManager(context);
                double budget = preferencesManager.getBudgetAmount(0.0);

                // If no budget is set, use a default
                if (budget <= 0) {
                    budget = 100000; // Default budget if none is set
                }

                // Calculate remaining amount
                double remainingAmount = budget - totalExpenses;
                if (remainingAmount < 0) remainingAmount = 0;

                // Calculate budget percentage
                int budgetPercentage = (int)((totalExpenses / budget) * 100);
                if (budgetPercentage > 100) budgetPercentage = 100;

                // Format currency values - use compact format to save space
                String formattedIncome = formatCurrencyCompact(totalIncome);
                String formattedExpenses = formatCurrencyCompact(totalExpenses);
                String formattedRemaining = formatCurrencyCompact(remainingAmount);

                int finalBudgetPercentage = budgetPercentage;
                // Update UI on main thread
                new Handler(Looper.getMainLooper()).post(() -> {
                    try {
                        views.setTextViewText(R.id.remainingText, formattedRemaining);
                        views.setTextViewText(R.id.incomeText, formattedIncome);
                        views.setTextViewText(R.id.spentText, formattedExpenses);
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

    /**
     * Format currency in a compact way for the small widget
     */
    private static String formatCurrencyCompact(double amount) {
        if (amount >= 100000) {
            return String.format(Locale.getDefault(), "₹%.1fL", amount / 100000);
        } else if (amount >= 1000) {
            return String.format(Locale.getDefault(), "₹%.1fK", amount / 1000);
        } else {
            return String.format(Locale.getDefault(), "₹%.0f", amount);
        }
    }

    @Override
    public void onEnabled(Context context) {
        Log.d(TAG, "onEnabled called - first widget instance created");
    }

    @Override
    public void onDisabled(Context context) {
        Log.d(TAG, "onDisabled called - last widget instance removed");
    }

    @Override
    public void onDeleted(Context context, int[] appWidgetIds) {
        // Clean up preferences when widgets are removed
        SharedPreferences.Editor prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit();
        for (int appWidgetId : appWidgetIds) {
            prefs.remove(PREF_PREFIX_FROM_DATE + appWidgetId);
            prefs.remove(PREF_PREFIX_TO_DATE + appWidgetId);
        }
        prefs.apply();

        super.onDeleted(context, appWidgetIds);
    }
}