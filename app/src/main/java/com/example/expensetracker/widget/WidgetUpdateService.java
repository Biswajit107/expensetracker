package com.example.expensetracker.widget;

import android.app.IntentService;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.example.expensetracker.R;

/**
 * Service to handle widget update requests from the app
 */
public class WidgetUpdateService extends IntentService {
    private static final String TAG = "WidgetUpdateService";
    private static final String ACTION_UPDATE_WIDGETS = "com.example.expensetracker.widget.UPDATE_WIDGETS";

    public WidgetUpdateService() {
        super("WidgetUpdateService");
    }

    /**
     * Helper method to start the service and update all widgets
     */
    public static void startActionUpdateWidgets(Context context) {
        Intent intent = new Intent(context, WidgetUpdateService.class);
        intent.setAction(ACTION_UPDATE_WIDGETS);
        context.startService(intent);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (intent != null) {
            final String action = intent.getAction();
            if (ACTION_UPDATE_WIDGETS.equals(action)) {
                handleActionUpdateWidgets();
            }
        }
    }

    /**
     * Handle the update action
     */
    private void handleActionUpdateWidgets() {
        Log.d(TAG, "Updating all widgets");

        // Get all widget IDs for our provider
        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(this);
        int[] appWidgetIds = appWidgetManager.getAppWidgetIds(
                new ComponentName(this, ExpenseTrackerWidgetProvider.class));

        // Trigger an update for each widget
        Intent updateIntent = new Intent(this, ExpenseTrackerWidgetProvider.class);
        updateIntent.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
        updateIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetIds);
        sendBroadcast(updateIntent);
    }
}