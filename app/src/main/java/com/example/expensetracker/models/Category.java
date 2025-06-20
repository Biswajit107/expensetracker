package com.example.expensetracker.models;

import androidx.annotation.DrawableRes;
import androidx.annotation.ColorRes;

/**
 * Model class for category items in the quick entry interface
 */
public class Category {
    private String name;
    private @DrawableRes int iconResourceId;
    private @ColorRes int colorResourceId;
    private boolean isSelected;
    private boolean isCustom;
    private boolean isRecent;
    private long lastUsedTimestamp;
    private int useCount;

    public Category(String name, @DrawableRes int iconResourceId, @ColorRes int colorResourceId) {
        this.name = name;
        this.iconResourceId = iconResourceId;
        this.colorResourceId = colorResourceId;
        this.isSelected = false;
        this.isCustom = false;
        this.isRecent = false;
        this.lastUsedTimestamp = 0;
        this.useCount = 0;
    }

    // Custom category constructor
    public Category(String name, @DrawableRes int iconResourceId, @ColorRes int colorResourceId, boolean isCustom) {
        this(name, iconResourceId, colorResourceId);
        this.isCustom = isCustom;
    }

    // Recent category constructor
    public Category(String name, @DrawableRes int iconResourceId, @ColorRes int colorResourceId, long lastUsedTimestamp) {
        this(name, iconResourceId, colorResourceId);
        this.isRecent = true;
        this.lastUsedTimestamp = lastUsedTimestamp;
    }

    // Getters and setters
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getIconResourceId() {
        return iconResourceId;
    }

    public void setIconResourceId(int iconResourceId) {
        this.iconResourceId = iconResourceId;
    }

    public int getColorResourceId() {
        return colorResourceId;
    }

    public void setColorResourceId(int colorResourceId) {
        this.colorResourceId = colorResourceId;
    }

    public boolean isSelected() {
        return isSelected;
    }

    public void setSelected(boolean selected) {
        isSelected = selected;
    }

    public boolean isCustom() {
        return isCustom;
    }

    public void setCustom(boolean custom) {
        isCustom = custom;
    }

    public boolean isRecent() {
        return isRecent;
    }

    public void setRecent(boolean recent) {
        isRecent = recent;
    }

    public long getLastUsedTimestamp() {
        return lastUsedTimestamp;
    }

    public void setLastUsedTimestamp(long lastUsedTimestamp) {
        this.lastUsedTimestamp = lastUsedTimestamp;
    }

    public void incrementUseCount() {
        this.useCount++;
        this.lastUsedTimestamp = System.currentTimeMillis();
    }

    public int getUseCount() {
        return useCount;
    }

    public void setUseCount(int useCount) {
        this.useCount = useCount;
    }
}