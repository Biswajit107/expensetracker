package com.example.expensetracker.database;

import com.example.expensetracker.models.ExclusionPattern;

import android.content.Context;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.example.expensetracker.models.Transaction;

@Database(entities = {Transaction.class, ExclusionPattern.class}, version = 4, exportSchema = false)
public abstract class TransactionDatabase extends RoomDatabase {
    private static TransactionDatabase instance;
    public abstract TransactionDao transactionDao();
    public abstract ExclusionPatternDao exclusionPatternDao();

    // Define migration from version 2 to 3
    static final Migration MIGRATION_3_4 = new Migration(3, 4) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            // Add exclusion_source column to transactions
            database.execSQL("ALTER TABLE transactions ADD COLUMN exclusion_source TEXT DEFAULT 'NONE'");

            // Create the exclusion_patterns table
            database.execSQL("CREATE TABLE IF NOT EXISTS exclusion_patterns (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "merchant_pattern TEXT, " +
                    "description_pattern TEXT, " +
                    "min_amount REAL NOT NULL, " +
                    "max_amount REAL NOT NULL, " +
                    "transaction_type TEXT, " +
                    "category TEXT, " +
                    "created_date INTEGER NOT NULL, " +
                    "source_transaction_id INTEGER NOT NULL, " +
                    "pattern_matches_count INTEGER NOT NULL DEFAULT 0, " +
                    "is_active INTEGER NOT NULL DEFAULT 1)");
        }
    };

    public static synchronized TransactionDatabase getInstance(Context context) {
        if (instance == null) {
            instance = Room.databaseBuilder(
                            context.getApplicationContext(),
                            TransactionDatabase.class,
                            "transaction_database"
                    )
                    .addMigrations(MIGRATION_3_4) // Add the migration to the builder
                    .fallbackToDestructiveMigration()
                    .build();
        }
        return instance;
    }
}