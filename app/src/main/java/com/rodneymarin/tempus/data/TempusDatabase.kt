package com.rodneymarin.tempus.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(entities = [Tracker::class, LogEntry::class], version = 1, exportSchema = true)
@TypeConverters(Converters::class)
abstract class TempusDatabase : RoomDatabase() {
    abstract fun trackerDao(): TrackerDao
    abstract fun logEntryDao(): LogEntryDao
}

object RoomBuilder {
    fun build(context: Context): TempusDatabase =
        Room.databaseBuilder(context, TempusDatabase::class.java, "tempus.db").build()
}
