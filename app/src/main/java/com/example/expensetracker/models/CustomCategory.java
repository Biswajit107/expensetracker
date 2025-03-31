package com.example.expensetracker.models;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.ColumnInfo;

@Entity(tableName = "custom_categories")
public class CustomCategory {
    @PrimaryKey(autoGenerate = true)
    private long id;

    @ColumnInfo(name = "name")
    @NonNull  // Add NonNull annotation to match the database schema
    private String name;

    @ColumnInfo(name = "color")
    @NonNull  // Add NonNull annotation to match the database schema
    private String color; // Hex color code

    @ColumnInfo(name = "created_date")
    private long createdDate;

    @ColumnInfo(name = "use_count", defaultValue = "0")
    private int useCount;

    // Constructor
    public CustomCategory(@NonNull String name, @NonNull String color) {
        this.name = name;
        this.color = color;
        this.createdDate = System.currentTimeMillis();
        this.useCount = 0;
    }

    // Getters and setters
    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    @NonNull
    public String getName() { return name; }
    public void setName(@NonNull String name) { this.name = name; }

    @NonNull
    public String getColor() { return color; }
    public void setColor(@NonNull String color) { this.color = color; }

    public long getCreatedDate() { return createdDate; }
    public void setCreatedDate(long createdDate) { this.createdDate = createdDate; }

    public int getUseCount() { return useCount; }
    public void setUseCount(int useCount) { this.useCount = useCount; }
    public void incrementUseCount() { this.useCount++; }
}