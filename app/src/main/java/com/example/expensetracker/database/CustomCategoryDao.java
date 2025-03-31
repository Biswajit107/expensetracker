package com.example.expensetracker.database;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;
import androidx.room.Delete;

import com.example.expensetracker.models.CustomCategory;
import java.util.List;

@Dao
public interface CustomCategoryDao {
    @Insert
    long insert(CustomCategory category);

    @Update
    void update(CustomCategory category);

    @Delete
    void delete(CustomCategory category);

    @Query("SELECT * FROM custom_categories ORDER BY use_count DESC")
    LiveData<List<CustomCategory>> getAllCategories();

    @Query("SELECT * FROM custom_categories ORDER BY use_count DESC")
    List<CustomCategory> getAllCategoriesSync();

    @Query("SELECT * FROM custom_categories WHERE name = :name LIMIT 1")
    CustomCategory getCategoryByName(String name);

    @Query("UPDATE custom_categories SET use_count = use_count + 1 WHERE id = :categoryId")
    void incrementUseCount(long categoryId);
}