package com.example.expensetracker.viewmodel;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import com.example.expensetracker.database.CustomCategoryDao;
import com.example.expensetracker.database.TransactionDatabase;
import com.example.expensetracker.models.CustomCategory;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CategoryViewModel extends AndroidViewModel {
    private CustomCategoryDao categoryDao;
    private LiveData<List<CustomCategory>> allCustomCategories;
    private ExecutorService executorService;

    public CategoryViewModel(Application application) {
        super(application);
        categoryDao = TransactionDatabase.getInstance(application).customCategoryDao();
        allCustomCategories = categoryDao.getAllCategories();
        executorService = Executors.newSingleThreadExecutor();
    }

    public LiveData<List<CustomCategory>> getAllCustomCategories() {
        return allCustomCategories;
    }

    public void insertCategory(CustomCategory category) {
        executorService.execute(() -> {
            // Check if category with same name already exists
            CustomCategory existing = categoryDao.getCategoryByName(category.getName());
            if (existing == null) {
                categoryDao.insert(category);
            }
        });
    }

    public void incrementCategoryUseCount(long categoryId) {
        executorService.execute(() -> {
            categoryDao.incrementUseCount(categoryId);
        });
    }



    @Override
    protected void onCleared() {
        super.onCleared();
        if (executorService != null) {
            executorService.shutdown();
        }
    }

    /**
     * Get a custom category by name asynchronously
     * This is safe to call from any thread
     *
     * @param name The category name to look up
     * @param callback Callback to receive the result
     */
    public void getCategoryByNameAsync(String name, CategoryCallback callback) {
        if (name == null || name.isEmpty()) {
            callback.onCategoryResult(null);
            return;
        }

        executorService.execute(() -> {
            try {
                CustomCategory category = categoryDao.getCategoryByName(name);
                // Return result on main thread
                new Handler(Looper.getMainLooper()).post(() -> {
                    callback.onCategoryResult(category);
                });
            } catch (Exception e) {
                Log.e("CategoryViewModel", "Error getting category by name: " + name, e);
                new Handler(Looper.getMainLooper()).post(() -> {
                    callback.onCategoryResult(null);
                });
            }
        });
    }

    /**
     * Callback for category lookup operations
     */
    public interface CategoryCallback {
        void onCategoryResult(CustomCategory category);
    }
}