package com.example.expensetracker.viewmodel;

import android.app.Application;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.expensetracker.R;
import com.example.expensetracker.models.Category;
import com.example.expensetracker.models.Transaction;
import com.example.expensetracker.utils.PreferencesManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * ViewModel for the Quick Entry functionality.
 * Manages category data, suggested amounts, and user preferences.
 */
public class QuickEntryViewModel extends AndroidViewModel {

    private static final String PREF_CATEGORIES_LAST_USED = "categories_last_used_";
    private static final String PREF_CATEGORIES_USE_COUNT = "categories_use_count_";
    private static final String PREF_CATEGORY_AMOUNT_SUGGESTION = "category_amount_suggestion_";

    private final MutableLiveData<List<Category>> categoriesLiveData = new MutableLiveData<>();
    private final MutableLiveData<List<Category>> recentCategoriesLiveData = new MutableLiveData<>();
    private final MutableLiveData<Double> suggestedAmountLiveData = new MutableLiveData<>(0.0);

    private final ExecutorService executorService;
    private final PreferencesManager preferencesManager;

    public QuickEntryViewModel(@NonNull Application application) {
        super(application);
        this.executorService = Executors.newSingleThreadExecutor();
        this.preferencesManager = new PreferencesManager(application);

        // Initialize categories
        loadCategories();
    }

    /**
     * Get all available categories
     */
    public LiveData<List<Category>> getCategories() {
        return categoriesLiveData;
    }

    /**
     * Get recently used categories
     */
    public LiveData<List<Category>> getRecentCategories() {
        return recentCategoriesLiveData;
    }

    /**
     * Get suggested amount based on selected category and time
     */
    public LiveData<Double> getSuggestedAmount() {
        return suggestedAmountLiveData;
    }

    /**
     * Update category usage statistics when a category is used
     */
    public void updateCategoryUsage(Category category) {
        if (category == null) return;

        // Update in-memory model
        category.incrementUseCount();

        // Save to preferences for persistence
        executorService.execute(() -> {
            // Save last used timestamp
            preferencesManager.savePreference(
                    PREF_CATEGORIES_LAST_USED + category.getName(),
                    category.getLastUsedTimestamp()
            );

            // Save use count
            preferencesManager.savePreference(
                    PREF_CATEGORIES_USE_COUNT + category.getName(),
                    category.getUseCount()
            );

            // Update recent categories list
            updateRecentCategories();
        });
    }

    /**
     * Suggest an amount based on selected category and recent usage
     */
    public void suggestAmountForCategory(String categoryName) {
        executorService.execute(() -> {
            // Simple implementation - just get the last used amount for this category
            double suggestedAmount = preferencesManager.getPreference(
                    PREF_CATEGORY_AMOUNT_SUGGESTION + categoryName,
                    0.0
            );

            // Default suggestions based on category if no history
            if (suggestedAmount <= 0) {
                suggestedAmount = getDefaultAmountForCategory(categoryName);
            }

            // Update LiveData on main thread
            double finalSuggestedAmount = suggestedAmount;
            new android.os.Handler(android.os.Looper.getMainLooper()).post(() ->
                    suggestedAmountLiveData.setValue(finalSuggestedAmount));
        });
    }

    /**
     * Save the amount used for a category to improve future suggestions
     */
    public void saveAmountForCategory(String categoryName, double amount) {
        if (amount <= 0) return;

        executorService.execute(() -> {
            preferencesManager.savePreference(
                    PREF_CATEGORY_AMOUNT_SUGGESTION + categoryName,
                    amount
            );
        });
    }

    /**
     * Load all available categories
     */
    private void loadCategories() {
        List<Category> categories = new ArrayList<>();

        // Add standard categories
        categories.add(new Category("Food", R.drawable.ic_food, R.color.category_food));
        categories.add(new Category("Transport", R.drawable.ic_transport, R.color.category_transport));
        categories.add(new Category("Shopping", R.drawable.ic_shopping, R.color.category_shopping));
        categories.add(new Category("Entertainment", R.drawable.ic_entertainment, R.color.category_entertainment));
        categories.add(new Category("Bills", R.drawable.ic_bills, R.color.category_bills));
        categories.add(new Category("Health", R.drawable.ic_health, R.color.category_health));
        categories.add(new Category("Education", R.drawable.ic_education, R.color.category_education));
        categories.add(new Category("Others", R.drawable.ic_others, R.color.category_others));

        // Add custom category option
        categories.add(new Category("Add New", R.drawable.ic_add, R.color.category_others, true));

        // Load usage statistics
        loadCategoryStatistics(categories);

        // Update LiveData
        categoriesLiveData.setValue(categories);

        // Update recent categories
        updateRecentCategories();
    }

    /**
     * Load saved usage statistics for categories
     */
    private void loadCategoryStatistics(List<Category> categories) {
        for (Category category : categories) {
            // Skip the "Add New" category
            if (category.isCustom()) continue;

            // Load last used timestamp
            long lastUsed = preferencesManager.getPreference(
                    PREF_CATEGORIES_LAST_USED + category.getName(),
                    0L
            );
            category.setLastUsedTimestamp(lastUsed);

            // Load use count
            int useCount = preferencesManager.getPreference(
                    PREF_CATEGORIES_USE_COUNT + category.getName(),
                    0
            );
            category.setUseCount(useCount);
        }
    }

    /**
     * Update the list of recent categories based on usage
     */
    private void updateRecentCategories() {
        List<Category> allCategories = categoriesLiveData.getValue();
        if (allCategories == null) return;

        // Make a copy of the categories that are not custom
        List<Category> regularCategories = new ArrayList<>();
        for (Category category : allCategories) {
            if (!category.isCustom()) {
                regularCategories.add(category);
            }
        }

        // Sort by most recently used
        Collections.sort(regularCategories, (c1, c2) ->
                Long.compare(c2.getLastUsedTimestamp(), c1.getLastUsedTimestamp()));

        // Take the top 5 most recently used
        List<Category> recentCategories = new ArrayList<>();
        int count = 0;
        for (Category category : regularCategories) {
            if (category.getLastUsedTimestamp() > 0 && count < 5) {
                Category recentCategory = new Category(
                        category.getName(),
                        category.getIconResourceId(),
                        category.getColorResourceId(),
                        category.getLastUsedTimestamp()
                );
                recentCategory.setUseCount(category.getUseCount());
                recentCategory.setRecent(true);
                recentCategories.add(recentCategory);
                count++;
            }
        }

        // Update LiveData
        recentCategoriesLiveData.setValue(recentCategories);
    }

    /**
     * Get default suggested amount for a category
     */
    private double getDefaultAmountForCategory(String categoryName) {
        switch (categoryName) {
            case "Food":
                return 200.0;
            case "Transport":
                return 50.0;
            case "Shopping":
                return 500.0;
            case "Entertainment":
                return 300.0;
            case "Bills":
                return 1000.0;
            case "Health":
                return 500.0;
            case "Education":
                return 1000.0;
            default:
                return 100.0;
        }
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        executorService.shutdown();
    }
}