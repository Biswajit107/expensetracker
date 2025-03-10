package com.example.expensetracker.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Bundle;
import android.util.Log;
import android.widget.RemoteViews;

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

public class ExpenseTrackerWidgetProvider extends AppWidgetProvider {

    private static final String TAG = "ExpenseWidget";
    private static final String ACTION_UPDATE_WIDGET = "com.example.expensetracker.widget.UPDATE_WIDGET";

    private static ExecutorService executorService = Executors.newSingleThreadExecutor();

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int widgetId : appWidgetIds) {
            try {
                Log.d(TAG, "Starting widget update for ID: " + widgetId);

                // Create a very simple RemoteViews to test basic functionality
                RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.expense_tracker_widget);

                // Set current time to verify updates are working
                String currentTime = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
                views.setTextViewText(R.id.lastUpdatedText, "Last updated: " + currentTime);

                // Update simple text values with static content first
                views.setTextViewText(R.id.incomeText, "Income: ₹45,000");
                views.setTextViewText(R.id.expensesText, "Expenses: ₹32,500");
                views.setTextViewText(R.id.remainingText, "Remaining: ₹12,500");

                // Update the widget
                appWidgetManager.updateAppWidget(widgetId, views);
                Log.d(TAG, "Basic widget update completed for ID: " + widgetId);

                // Only after basic update works, try the more complex data loading
                // loadAndDisplayFinancialData(context, views, appWidgetManager, widgetId);
            } catch (Exception e) {
                Log.e(TAG, "Error updating widget: " + e.getMessage(), e);
            }
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "Widget received intent: " + intent.getAction());
        super.onReceive(context, intent);

        if (ACTION_UPDATE_WIDGET.equals(intent.getAction())) {
            Log.d(TAG, "Received refresh action");

            // Trigger update for all instances of the widget
            AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
            int[] appWidgetIds = appWidgetManager.getAppWidgetIds(
                    new ComponentName(context, ExpenseTrackerWidgetProvider.class));

            onUpdate(context, appWidgetManager, appWidgetIds);
        }
    }

    @Override
    public void onAppWidgetOptionsChanged(Context context, AppWidgetManager appWidgetManager,
                                          int appWidgetId, Bundle newOptions) {
        // Handle size changes
        updateWidget(context, appWidgetManager, appWidgetId);
    }

    private void updateWidget(Context context, AppWidgetManager appWidgetManager, int appWidgetId) {
        // Create RemoteViews to manipulate the widget layout
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.expense_tracker_widget);

        // Set up click intent for widget header - opens app
        Intent openAppIntent = new Intent(context, MainActivity.class);
        PendingIntent openAppPendingIntent = PendingIntent.getActivity(context, 0, openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.headerLayout, openAppPendingIntent);

        // Set up refresh button click
        Intent refreshIntent = new Intent(context, ExpenseTrackerWidgetProvider.class);
        refreshIntent.setAction(ACTION_UPDATE_WIDGET);
        PendingIntent refreshPendingIntent = PendingIntent.getBroadcast(context, 0, refreshIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.refreshButton, refreshPendingIntent);

        // Set current month/year text
        SimpleDateFormat dateFormat = new SimpleDateFormat("MMMM yyyy", Locale.getDefault());
        views.setTextViewText(R.id.currentPeriodText, dateFormat.format(new Date()));

        // Load financial data asynchronously
        loadAndDisplayFinancialData(context, views, appWidgetManager, appWidgetId);
    }

    private void loadAndDisplayFinancialData(Context context, RemoteViews views,
                                             AppWidgetManager appWidgetManager, int appWidgetId) {
        // Create repository to fetch data
        TransactionRepository repository = new TransactionRepository(
                (android.app.Application) context.getApplicationContext());

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

                // Get budget from preferences
                PreferencesManager prefsManager = new PreferencesManager(context);
                double currentBudget = 100000; // Default budget value

                // Calculate remaining amount (based on budget, not income)
                double remainingAmount = currentBudget - totalExpenses;

                // Create budget progress circle
                Bitmap progressCircle = createBudgetProgressCircle(context, totalExpenses, currentBudget);

                // Format currency values
                String formattedIncome = String.format(Locale.getDefault(), "₹%.2f", totalIncome);
                String formattedExpenses = String.format(Locale.getDefault(), "₹%.2f", totalExpenses);
                String formattedRemaining = String.format(Locale.getDefault(), "₹%.2f", remainingAmount);
                String formattedSpent = String.format(Locale.getDefault(), "₹%.2f", totalExpenses);
                String formattedBudget = String.format(Locale.getDefault(), "of ₹%.2f", currentBudget);

                // Update UI on main thread
                // Android AppWidgetManager does this automatically so we don't need runOnUiThread
//                views.setImageViewBitmap(R.id.budgetProgressImage, progressCircle);
                views.setTextViewText(R.id.incomeText, formattedIncome);
                views.setTextViewText(R.id.expensesText, formattedExpenses);
                views.setTextViewText(R.id.remainingText, formattedRemaining);
                views.setTextViewText(R.id.spentAmountText, formattedSpent);
//                views.setTextViewText(R.id.totalBudgetText, formattedBudget);

                // Update the widget
                appWidgetManager.updateAppWidget(appWidgetId, views);

            } catch (Exception e) {
                Log.e(TAG, "Error updating widget data", e);
            }
        });
    }

    private Bitmap createBudgetProgressCircle(Context context, double spent, double total) {
        // Create a bitmap to draw our progress circle
        Bitmap bitmap = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        // Calculate percentage spent
        float percentage = (float) (spent / total);
        if (percentage > 1f) percentage = 1f; // Cap at 100%

        // Create paints
        Paint backgroundPaint = new Paint();
        backgroundPaint.setColor(0xFFF5F5F5); // Light gray background
        backgroundPaint.setAntiAlias(true);
        backgroundPaint.setStyle(Paint.Style.FILL);

        Paint progressPaint = new Paint();
        progressPaint.setAntiAlias(true);
        progressPaint.setStyle(Paint.Style.STROKE);
        progressPaint.setStrokeWidth(24f);

        // Under budget = purple, over budget = red
        progressPaint.setColor(percentage >= 0.9f ? 0xFFFF5252 : 0xFF6200EE);

        // Draw background circle
        canvas.drawCircle(100, 100, 88, backgroundPaint);

        // Draw progress arc
        RectF arcRect = new RectF(12, 12, 188, 188);
        float sweepAngle = percentage * 360f;
        canvas.drawArc(arcRect, -90, sweepAngle, false, progressPaint);

        return bitmap;
    }

    @Override
    public void onDeleted(Context context, int[] appWidgetIds) {
        // Widget instance(s) removed
    }

    @Override
    public void onEnabled(Context context) {
        super.onEnabled(context);
        Log.d(TAG, "Widget enabled - first instance created");
    }

    @Override
    public void onDisabled(Context context) {
        super.onDisabled(context);
        Log.d(TAG, "Widget disabled - last instance removed");
    }
}