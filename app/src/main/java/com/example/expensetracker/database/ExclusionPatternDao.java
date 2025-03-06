package com.example.expensetracker.database;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;
import androidx.room.Delete;

import com.example.expensetracker.models.ExclusionPattern;
import java.util.List;

@Dao
public interface ExclusionPatternDao {
    @Insert
    long insert(ExclusionPattern pattern);

    @Update
    void update(ExclusionPattern pattern);

    @Delete
    void delete(ExclusionPattern pattern);

    @Query("SELECT * FROM exclusion_patterns WHERE is_active = 1 ORDER BY created_date DESC")
    List<ExclusionPattern> getAllActivePatterns();

    @Query("SELECT * FROM exclusion_patterns WHERE source_transaction_id = :transactionId LIMIT 1")
    ExclusionPattern getPatternBySourceTransactionId(long transactionId);

    @Query("UPDATE exclusion_patterns SET pattern_matches_count = pattern_matches_count + 1 WHERE id = :patternId")
    void incrementPatternMatchCount(long patternId);

    @Query("UPDATE exclusion_patterns SET is_active = 0 WHERE id = :patternId")
    void deactivatePattern(long patternId);

    @Query("SELECT * FROM exclusion_patterns ORDER BY pattern_matches_count DESC")
    LiveData<List<ExclusionPattern>> getAllPatternsOrderedByMatches();
}