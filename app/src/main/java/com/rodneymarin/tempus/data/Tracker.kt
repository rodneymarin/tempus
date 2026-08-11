package com.rodneymarin.tempus.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.rodneymarin.tempus.domain.FrequencyPeriod

@Entity(tableName = "trackers")
data class Tracker(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val emoji: String = "✅",
    val minFrequency: Int? = null,
    val maxFrequency: Int? = null,
    val period: FrequencyPeriod = FrequencyPeriod.WEEK,
    val createdAt: Long = System.currentTimeMillis(),
)
