package com.example.expensetracker.database;

import android.content.Context;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import com.example.expensetracker.models.Transaction;

import android.content.Context;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import com.example.expensetracker.models.Transaction;

@Database(entities = {Transaction.class}, version = 2, exportSchema = false)
public abstract class TransactionDatabase extends RoomDatabase {
    private static TransactionDatabase instance;
    public abstract TransactionDao transactionDao();

    public static synchronized TransactionDatabase getInstance(Context context) {
        if (instance == null) {
            instance = Room.databaseBuilder(
                    context.getApplicationContext(),
                    TransactionDatabase.class,
                    "transaction_database"
            )
            .fallbackToDestructiveMigration()
            .build();
        }
        return instance;
    }
}