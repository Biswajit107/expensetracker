package com.example.expensetracker.models;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "transactions")
public class Transaction {
    @PrimaryKey(autoGenerate = true)
    private long id;
    private String bank;
    private String type;
    private double amount;
    private long date;
    private String description;
    private String messageHash;

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
}
