package com.rodneymarin.tempus.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface LogEntryDao {
    @Query("SELECT * FROM log_entries WHERE trackerId = :trackerId ORDER BY epochDay DESC, timeMinutes DESC")
    fun observeByTracker(trackerId: Long): Flow<List<LogEntry>>

    @Query("SELECT * FROM log_entries")
    fun observeAll(): Flow<List<LogEntry>>

    @Insert
    suspend fun insert(entry: LogEntry): Long

    @Query("DELETE FROM log_entries WHERE id = :id")
    suspend fun deleteById(id: Long)
}
