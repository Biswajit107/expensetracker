package com.example.expensetracker.utils;

import android.content.Context;
import android.content.SharedPreferences;
public class PreferencesManager {
    private static final String PREF_NAME = "BankTransactionPrefs";
    private static final String KEY_LAST_SYNC = "last_sync_time";

    private static final String KEY_VIEW_MODE_GROUPED = "view_mode_grouped";

    private SharedPreferences prefs;

    public PreferencesManager(Context context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public void setLastSyncTime(long timestamp) {
        prefs.edit().putLong(KEY_LAST_SYNC, timestamp).apply();
    }

    private static final String KEY_FROM_DATE = "from_date";
    private static final String KEY_TO_DATE = "to_date";

    public void saveSelectedDateRange(long fromDate, long toDate) {
        prefs.edit()
                .putLong(KEY_FROM_DATE, fromDate)
                .putLong(KEY_TO_DATE, toDate)
                .apply();
    }

    public long getFromDate() {
        return prefs.getLong(KEY_FROM_DATE, 0);
    }

    public long getToDate() {
        return prefs.getLong(KEY_TO_DATE, 0);
    }

    /**
     * Save user's preference for transaction view mode
     * @param isGroupedView True if user prefers grouped view, false for list view
     */
    public void saveViewMode(boolean isGroupedView) {
        prefs.edit().putBoolean(KEY_VIEW_MODE_GROUPED, isGroupedView).apply();
    }

    /**
     * Get user's preference for transaction view mode
     * @return True if grouped view is preferred, false for list view
     */
    public boolean getViewModePreference() {
        return prefs.getBoolean(KEY_VIEW_MODE_GROUPED, false);
    }
}
