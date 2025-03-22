package com.example.expensetracker.utils;

import android.content.Context;
import android.content.SharedPreferences;
public class PreferencesManager {
    private static final String PREF_NAME = "BankTransactionPrefs";
    private static final String KEY_LAST_SYNC = "last_sync_time";

    private static final String KEY_VIEW_MODE_GROUPED = "view_mode_grouped";

    private static final String KEY_GROUPING_MODE = "grouping_mode";

    private static final String KEY_BUDGET_AMOUNT = "budget_amount";

    // Add these to PreferencesManager.java
    private static final String KEY_SORT_OPTION = "sort_option";

    private static final String KEY_FROM_DATE = "from_date";
    private static final String KEY_TO_DATE = "to_date";

    private SharedPreferences prefs;

    public PreferencesManager(Context context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public void setLastSyncTime(long timestamp) {
        prefs.edit().putLong(KEY_LAST_SYNC, timestamp).apply();
    }

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

    /**
     * Save user's preference for grouping mode
     * @param groupingMode The grouping mode (0 = Day, 1 = Week, 2 = Month,
     *                     3 = Category, 4 = Merchant, 5 = Amount Range, 6 = Bank)
     */
    public void saveGroupingMode(int groupingMode) {
        prefs.edit().putInt(KEY_GROUPING_MODE, groupingMode).apply();
    }

    /**
     * Get user's preference for grouping mode
     * @return The grouping mode (0 = Day by default)
     */
    public int getGroupingModePreference() {
        return prefs.getInt(KEY_GROUPING_MODE, 0); // Default to day grouping
    }

    /**
     * Save budget amount to persistent storage
     * @param amount Budget amount to save
     */
    public void saveBudgetAmount(double amount) {
        prefs.edit().putFloat(KEY_BUDGET_AMOUNT, (float) amount).apply();
    }

    /**
     * Get saved budget amount
     * @param defaultAmount Default amount to return if no budget has been saved
     * @return The saved budget amount or defaultAmount if none exists
     */
    public double getBudgetAmount(double defaultAmount) {
        return prefs.getFloat(KEY_BUDGET_AMOUNT, (float) defaultAmount);
    }

    /**
     * Check if a budget amount has been saved
     * @return true if budget has been set, false otherwise
     */
    public boolean hasBudgetAmount() {
        return prefs.contains(KEY_BUDGET_AMOUNT);
    }

    /**
     * Clear saved budget amount
     */
    public void clearBudgetAmount() {
        prefs.edit().remove(KEY_BUDGET_AMOUNT).apply();
    }

    /**
     * Save user's preference for transaction sorting
     * @param sortOption The sort option (0-5)
     */
    public void saveSortOption(int sortOption) {
        prefs.edit().putInt(KEY_SORT_OPTION, sortOption).apply();
    }

    /**
     * Get user's preference for transaction sorting
     * @return The sort option (0 = Date newest first by default)
     */
    public int getSortOption() {
        return prefs.getInt(KEY_SORT_OPTION, 0); // Default to date newest first
    }
}