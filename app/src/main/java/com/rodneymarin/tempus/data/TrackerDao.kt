package com.rodneymarin.tempus.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackerDao {
    @Query("SELECT * FROM trackers ORDER BY createdAt ASC")
    fun observeAll(): Flow<List<Tracker>>

    @Query("SELECT * FROM trackers WHERE id = :id")
    fun observeById(id: Long): Flow<Tracker?>

    @Insert
    suspend fun insert(tracker: Tracker): Long

    @Update
    suspend fun update(tracker: Tracker)

    @Query("DELETE FROM trackers WHERE id = :id")
    suspend fun deleteById(id: Long)
}
