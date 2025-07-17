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


    private static final String KEY_CHART_EXPANDED = "chart_expanded";

    private static final String KEY_SELECTED_CHART_YEAR = "selected_chart_year";
    private static final String KEY_SELECTED_CHART_MONTH = "selected_chart_month";
    
    // Filter preferences
    private static final String KEY_FILTER_BANK = "filter_bank";
    private static final String KEY_FILTER_TYPE = "filter_type";
    private static final String KEY_FILTER_CATEGORY = "filter_category";
    private static final String KEY_FILTER_SEARCH_QUERY = "filter_search_query";
    private static final String KEY_FILTER_MIN_AMOUNT = "filter_min_amount";
    private static final String KEY_FILTER_MAX_AMOUNT = "filter_max_amount";
    private static final String KEY_FILTER_SHOW_EXCLUDED = "filter_show_excluded";
    private static final String KEY_FILTER_RECURRING_ONLY = "filter_recurring_only";

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

    /**
     * Save the expanded state of the spending chart
     * @param expanded Whether the chart is expanded
     */
    public void saveChartExpandedState(boolean expanded) {
        prefs.edit().putBoolean(KEY_CHART_EXPANDED, expanded).apply();
    }

    /**
     * Get the saved expanded state of the spending chart
     * @return Whether the chart should be expanded, defaults to true
     */
    public boolean isChartExpanded() {
        return prefs.getBoolean(KEY_CHART_EXPANDED, false); // Default to expanded
    }

    /**
     * Save the selected month for the spending chart
     * @param year The selected year
     * @param month The selected month (0-11)
     */
    public void saveChartMonthSelection(int year, int month) {
        prefs.edit()
                .putInt(KEY_SELECTED_CHART_YEAR, year)
                .putInt(KEY_SELECTED_CHART_MONTH, month)
                .apply();
    }

    /**
     * Get the saved year for chart month selection
     * @return The selected year or 0 if not set (0 indicates "All Months")
     */
    public int getSelectedChartYear() {
        return prefs.getInt(KEY_SELECTED_CHART_YEAR, 0);
    }

    /**
     * Get the saved month for chart month selection
     * @return The selected month (0-11) or 0 if not set
     */
    public int getSelectedChartMonth() {
        return prefs.getInt(KEY_SELECTED_CHART_MONTH, 0);
    }

    /**
     * Check if a specific month is selected for the chart
     * @return true if a month is selected, false for "All Months"
     */
    public boolean hasSelectedChartMonth() {
        return getSelectedChartYear() > 0;
    }

    /**
     * Clear any saved month selection (revert to "All Months")
     */
    public void clearChartMonthSelection() {
        prefs.edit()
                .remove(KEY_SELECTED_CHART_YEAR)
                .remove(KEY_SELECTED_CHART_MONTH)
                .apply();
    }

    // Add these methods to your existing PreferencesManager.java class

    /**
     * Save a String preference value
     */
    public void savePreference(String key, String value) {
        prefs.edit().putString(key, value).apply();
    }

    /**
     * Get a String preference value
     */
    public String getPreference(String key, String defaultValue) {
        return prefs.getString(key, defaultValue);
    }

    /**
     * Save an int preference value
     */
    public void savePreference(String key, int value) {
        prefs.edit().putInt(key, value).apply();
    }

    /**
     * Get an int preference value
     */
    public int getPreference(String key, int defaultValue) {
        return prefs.getInt(key, defaultValue);
    }

    /**
     * Save a long preference value
     */
    public void savePreference(String key, long value) {
        prefs.edit().putLong(key, value).apply();
    }

    /**
     * Get a long preference value
     */
    public long getPreference(String key, long defaultValue) {
        return prefs.getLong(key, defaultValue);
    }

    /**
     * Save a boolean preference value
     */
    public void savePreference(String key, boolean value) {
        prefs.edit().putBoolean(key, value).apply();
    }

    /**
     * Get a boolean preference value
     */
    public boolean getPreference(String key, boolean defaultValue) {
        return prefs.getBoolean(key, defaultValue);
    }

    /**
     * Save a float preference value
     */
    public void savePreference(String key, float value) {
        prefs.edit().putFloat(key, value).apply();
    }

    /**
     * Get a float preference value
     */
    public float getPreference(String key, float defaultValue) {
        return prefs.getFloat(key, defaultValue);
    }

    /**
     * Save a double preference value
     * (Stored as float since SharedPreferences doesn't support double directly)
     */
    public void savePreference(String key, double value) {
        prefs.edit().putFloat(key, (float) value).apply();
    }

    /**
     * Get a double preference value
     */
    public double getPreference(String key, double defaultValue) {
        return prefs.getFloat(key, (float) defaultValue);
    }

    /**
     * Check if a preference key exists
     */
    public boolean hasPreference(String key) {
        return prefs.contains(key);
    }

    /**
     * Remove a preference
     */
    public void removePreference(String key) {
        prefs.edit().remove(key).apply();
    }

    /**
     * Save last used quick entry preferences
     */
    public void saveQuickEntryPreferences(String category, double amount) {
        savePreference("last_quick_entry_category", category);
        savePreference("last_quick_entry_amount", amount);
        savePreference("last_quick_entry_time", System.currentTimeMillis());
    }

    /**
     * Get last used category in quick entry
     */
    public String getLastQuickEntryCategory() {
        return getPreference("last_quick_entry_category", "");
    }

    /**
     * Get last used amount in quick entry
     */
    public double getLastQuickEntryAmount() {
        return getPreference("last_quick_entry_amount", 0.0);
    }

    /**
     * Get last time quick entry was used
     */
    public long getLastQuickEntryTime() {
        return getPreference("last_quick_entry_time", 0L);
    }

    // Filter state persistence methods
    public void saveFilterState(String bank, String type, String category, String searchQuery, 
                               double minAmount, double maxAmount, boolean showExcluded, boolean recurringOnly) {
        prefs.edit()
                .putString(KEY_FILTER_BANK, bank)
                .putString(KEY_FILTER_TYPE, type)
                .putString(KEY_FILTER_CATEGORY, category)
                .putString(KEY_FILTER_SEARCH_QUERY, searchQuery)
                .putFloat(KEY_FILTER_MIN_AMOUNT, (float) minAmount)
                .putFloat(KEY_FILTER_MAX_AMOUNT, (float) maxAmount)
                .putBoolean(KEY_FILTER_SHOW_EXCLUDED, showExcluded)
                .putBoolean(KEY_FILTER_RECURRING_ONLY, recurringOnly)
                .apply();
    }

    public String getFilterBank() {
        return prefs.getString(KEY_FILTER_BANK, "All Banks");
    }

    public String getFilterType() {
        return prefs.getString(KEY_FILTER_TYPE, "All Types");
    }

    public String getFilterCategory() {
        return prefs.getString(KEY_FILTER_CATEGORY, "");
    }

    public String getFilterSearchQuery() {
        return prefs.getString(KEY_FILTER_SEARCH_QUERY, "");
    }

    public double getFilterMinAmount() {
        return prefs.getFloat(KEY_FILTER_MIN_AMOUNT, 0f);
    }

    public double getFilterMaxAmount() {
        return prefs.getFloat(KEY_FILTER_MAX_AMOUNT, 100000f);
    }

    public boolean getFilterShowExcluded() {
        return prefs.getBoolean(KEY_FILTER_SHOW_EXCLUDED, false);
    }

    public boolean getFilterRecurringOnly() {
        return prefs.getBoolean(KEY_FILTER_RECURRING_ONLY, false);
    }

    public void clearFilterState() {
        prefs.edit()
                .remove(KEY_FILTER_BANK)
                .remove(KEY_FILTER_TYPE)
                .remove(KEY_FILTER_CATEGORY)
                .remove(KEY_FILTER_SEARCH_QUERY)
                .remove(KEY_FILTER_MIN_AMOUNT)
                .remove(KEY_FILTER_MAX_AMOUNT)
                .remove(KEY_FILTER_SHOW_EXCLUDED)
                .remove(KEY_FILTER_RECURRING_ONLY)
                .apply();
    }

    public boolean hasActiveFilters() {
        return !getFilterBank().equals("All Banks") ||
               !getFilterType().equals("All Types") ||
               !getFilterCategory().isEmpty() ||
               !getFilterSearchQuery().isEmpty() ||
               getFilterMinAmount() > 0 ||
               getFilterMaxAmount() < 100000 ||
               getFilterShowExcluded() ||
               getFilterRecurringOnly();
    }
}