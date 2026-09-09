package com.rodneymarin.tempus.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface LogEntryDao {
    @Query("SELECT * FROM log_entries WHERE trackerId = :trackerId ORDER BY epochDay DESC, timeMinutes DESC")
    fun observeByTracker(trackerId: Long): Flow<List<LogEntry>>

    @Query("SELECT * FROM log_entries")
    fun observeAll(): Flow<List<LogEntry>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: LogEntry): Long

    @Query("DELETE FROM log_entries WHERE trackerId = :trackerId AND epochDay = :epochDay")
    suspend fun deleteByDay(trackerId: Long, epochDay: Long)

    @Query(
        "UPDATE log_entries SET comment = :comment " +
            "WHERE trackerId = :trackerId AND epochDay = :epochDay"
    )
    suspend fun updateComment(trackerId: Long, epochDay: Long, comment: String?)

    @Query("DELETE FROM log_entries WHERE id = :id")
    suspend fun deleteById(id: Long)
}
