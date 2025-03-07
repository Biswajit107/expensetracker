package com.example.expensetracker.models;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.ColumnInfo;

@Entity(tableName = "exclusion_patterns")
public class ExclusionPattern {
    @PrimaryKey(autoGenerate = true)
    private long id;

    @ColumnInfo(name = "merchant_pattern")
    private String merchantPattern;

    @ColumnInfo(name = "description_pattern")
    private String descriptionPattern;

    @ColumnInfo(name = "min_amount")
    private double minAmount;

    @ColumnInfo(name = "max_amount")
    private double maxAmount;

    @ColumnInfo(name = "transaction_type")
    private String transactionType; // DEBIT or CREDIT

    @ColumnInfo(name = "category")
    private String category;

    @ColumnInfo(name = "created_date")
    private long createdDate;

    @ColumnInfo(name = "source_transaction_id")
    private long sourceTransactionId;

    @ColumnInfo(name = "pattern_matches_count")
    private int patternMatchesCount;

    @ColumnInfo(name = "is_active")
    private boolean isActive;

    // Constructor
    public ExclusionPattern(String merchantPattern, String descriptionPattern,
                            double minAmount, double maxAmount,
                            String transactionType, String category,
                            long sourceTransactionId) {
        this.merchantPattern = merchantPattern;
        this.descriptionPattern = descriptionPattern;
        this.minAmount = minAmount;
        this.maxAmount = maxAmount;
        this.transactionType = transactionType;
        this.category = category;
        this.sourceTransactionId = sourceTransactionId;
        this.createdDate = System.currentTimeMillis();
        this.patternMatchesCount = 0;
        this.isActive = true;
    }

    // Getters and setters
    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public String getMerchantPattern() { return merchantPattern; }
    public void setMerchantPattern(String merchantPattern) { this.merchantPattern = merchantPattern; }

    public String getDescriptionPattern() { return descriptionPattern; }
    public void setDescriptionPattern(String descriptionPattern) { this.descriptionPattern = descriptionPattern; }

    public double getMinAmount() { return minAmount; }
    public void setMinAmount(double minAmount) { this.minAmount = minAmount; }

    public double getMaxAmount() { return maxAmount; }
    public void setMaxAmount(double maxAmount) { this.maxAmount = maxAmount; }

    public String getTransactionType() { return transactionType; }
    public void setTransactionType(String transactionType) { this.transactionType = transactionType; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public long getCreatedDate() { return createdDate; }
    public void setCreatedDate(long createdDate) { this.createdDate = createdDate; }

    public long getSourceTransactionId() { return sourceTransactionId; }

    public int getPatternMatchesCount() { return patternMatchesCount; }
    public void setPatternMatchesCount(int patternMatchesCount) { this.patternMatchesCount = patternMatchesCount; }

    public boolean isActive() { return isActive; }
    public void setActive(boolean active) { isActive = active; }
}