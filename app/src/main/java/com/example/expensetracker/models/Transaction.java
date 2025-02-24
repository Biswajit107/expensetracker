package com.example.expensetracker.models;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.ColumnInfo;
import androidx.room.Index;

@Entity(
        tableName = "transactions",
        indices = {
                @Index(value = {"messageHash"}, unique = true),
                @Index(value = {"date"}),
                @Index(value = {"category"})
        }
)
public class Transaction {
    @PrimaryKey(autoGenerate = true)
    private long id;

    @ColumnInfo(name = "bank")
    private String bank;

    @ColumnInfo(name = "type")
    private String type;  // DEBIT or CREDIT

    @ColumnInfo(name = "amount")
    private double amount;

    @ColumnInfo(name = "date")
    private long date;

    @ColumnInfo(name = "description")
    private String description;

    @ColumnInfo(name = "messageHash")
    private String messageHash;

    @ColumnInfo(name = "category")
    private String category;  // Food, Shopping, Bills, etc.

    @ColumnInfo(name = "merchant_name")
    private String merchantName;

    @ColumnInfo(name = "is_other_debit", defaultValue = "0")
    private boolean isOtherDebit;

    @ColumnInfo(name = "is_recurring", defaultValue = "0")
    private boolean isRecurring;

    @ColumnInfo(name = "recurring_frequency")
    private Integer recurringFrequency;  // in days, null if not recurring

    @ColumnInfo(name = "group_key")
    private String groupKey;  // for grouping related transactions

    @ColumnInfo(name = "is_excluded_from_total", defaultValue = "0")
    private boolean isExcludedFromTotal;

    // Constructor
    public Transaction(String bank, String type, double amount, long date, String description) {
        this.bank = bank;
        this.type = type;
        this.amount = amount;
        this.date = date;
        this.description = description;
    }

    // Getters and Setters
    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public String getBank() { return bank; }
    public void setBank(String bank) { this.bank = bank; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public double getAmount() { return amount; }
    public void setAmount(double amount) { this.amount = amount; }

    public long getDate() { return date; }
    public void setDate(long date) { this.date = date; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getMessageHash() { return messageHash; }
    public void setMessageHash(String messageHash) { this.messageHash = messageHash; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getMerchantName() { return merchantName; }
    public void setMerchantName(String merchantName) { this.merchantName = merchantName; }

    public boolean isOtherDebit() { return isOtherDebit; }
    public void setOtherDebit(boolean otherDebit) { isOtherDebit = otherDebit; }

    public boolean isRecurring() { return isRecurring; }
    public void setRecurring(boolean recurring) { isRecurring = recurring; }

    public Integer getRecurringFrequency() { return recurringFrequency; }
    public void setRecurringFrequency(Integer recurringFrequency) {
        this.recurringFrequency = recurringFrequency;
    }

    public String getGroupKey() { return groupKey; }
    public void setGroupKey(String groupKey) { this.groupKey = groupKey; }

    public boolean isExcludedFromTotal() { return isExcludedFromTotal; }
    public void setExcludedFromTotal(boolean excludedFromTotal) {
        isExcludedFromTotal = excludedFromTotal;
    }

    // Helper Methods
    public boolean isDebit() {
        return "DEBIT".equals(type);
    }

    public boolean isCredit() {
        return "CREDIT".equals(type);
    }

    // For displaying formatted amount
    public String getFormattedAmount() {
        return String.format("₹%.2f", amount);
    }

    // For prediction and analysis
    public boolean shouldIncludeInAnalysis() {
        return !isExcludedFromTotal;
    }

    // For category management
    public static class Categories {
        public static final String FOOD = "Food";
        public static final String SHOPPING = "Shopping";
        public static final String BILLS = "Bills";
        public static final String ENTERTAINMENT = "Entertainment";
        public static final String TRANSPORT = "Transport";
        public static final String HEALTH = "Health";
        public static final String EDUCATION = "Education";
        public static final String OTHERS = "Others";

        public static String[] getAllCategories() {
            return new String[]{
                    FOOD, SHOPPING, BILLS, ENTERTAINMENT,
                    TRANSPORT, HEALTH, EDUCATION, OTHERS
            };
        }
    }
}