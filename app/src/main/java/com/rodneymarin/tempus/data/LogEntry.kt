package com.rodneymarin.tempus.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "log_entries",
    foreignKeys = [ForeignKey(
        entity = Tracker::class,
        parentColumns = ["id"],
        childColumns = ["trackerId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("trackerId"), Index("epochDay")],
)
data class LogEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val trackerId: Long,
    val epochDay: Long,
    val timeMinutes: Int? = null,
)
