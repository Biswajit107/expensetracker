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
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.example.expensetracker.models.Transaction;

@Database(entities = {Transaction.class}, version = 3, exportSchema = false)
public abstract class TransactionDatabase extends RoomDatabase {
    private static TransactionDatabase instance;
    public abstract TransactionDao transactionDao();

    // Define migration from version 2 to 3
    static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            // Add the original_sms column to the transactions table
            database.execSQL("ALTER TABLE transactions ADD COLUMN original_sms TEXT");
        }
    };

    public static synchronized TransactionDatabase getInstance(Context context) {
        if (instance == null) {
            instance = Room.databaseBuilder(
                            context.getApplicationContext(),
                            TransactionDatabase.class,
                            "transaction_database"
                    )
                    .addMigrations(MIGRATION_2_3) // Add the migration to the builder
                    .fallbackToDestructiveMigration()
                    .build();
        }
        return instance;
    }
}