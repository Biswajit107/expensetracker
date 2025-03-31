package com.example.expensetracker.database;

import com.example.expensetracker.models.CustomCategory;
import com.example.expensetracker.models.ExclusionPattern;

import android.content.Context;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.example.expensetracker.models.Transaction;

@Database(entities = {Transaction.class, ExclusionPattern.class, CustomCategory.class},
        version = 6, exportSchema = false)
public abstract class TransactionDatabase extends RoomDatabase {
    private static TransactionDatabase instance;
    public abstract TransactionDao transactionDao();
    public abstract ExclusionPatternDao exclusionPatternDao();
    public abstract CustomCategoryDao customCategoryDao();

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

    static final Migration MIGRATION_4_5 = new Migration(4, 5) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            // Create custom_categories table with nullable fields matching the entity class definition
            database.execSQL("CREATE TABLE IF NOT EXISTS custom_categories (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "name TEXT, " +              // Removed NOT NULL constraint
                    "color TEXT, " +             // Removed NOT NULL constraint
                    "created_date INTEGER NOT NULL, " +
                    "use_count INTEGER NOT NULL DEFAULT 0)");
        }
    };

    static final Migration MIGRATION_5_6 = new Migration(5, 6) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            // Drop and recreate the custom_categories table with the correct schema
            database.execSQL("DROP TABLE IF EXISTS custom_categories");
            database.execSQL("CREATE TABLE IF NOT EXISTS custom_categories (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "name TEXT NOT NULL, " +
                    "color TEXT NOT NULL, " +
                    "created_date INTEGER NOT NULL, " +
                    "use_count INTEGER NOT NULL DEFAULT 0)");
        }
    };

    public static synchronized TransactionDatabase getInstance(Context context) {
        if (instance == null) {
            instance = Room.databaseBuilder(
                            context.getApplicationContext(),
                            TransactionDatabase.class,
                            "transaction_database"
                    )
                    .addMigrations(MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6) // Add the new migration
                    .fallbackToDestructiveMigration()
                    .build();
        }
        return instance;
    }
}