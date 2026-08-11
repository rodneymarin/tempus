package com.rodneymarin.tempus.data

import com.rodneymarin.tempus.domain.FrequencyPeriod
import kotlinx.coroutines.flow.Flow

class TrackersRepository(private val db: TempusDatabase) {
    private val trackerDao = db.trackerDao()
    private val logDao = db.logEntryDao()

    val trackers: Flow<List<Tracker>> = trackerDao.observeAll()
    val allLogs: Flow<List<LogEntry>> = logDao.observeAll()
    fun logsFor(trackerId: Long): Flow<List<LogEntry>> = logDao.observeByTracker(trackerId)

    suspend fun createTracker(
        name: String, emoji: String,
        min: Int?, max: Int?, period: FrequencyPeriod,
    ): Long = trackerDao.insert(
        Tracker(name = name.trim(), emoji = emoji, minFrequency = min, maxFrequency = max, period = period)
    )

    suspend fun updateTracker(tracker: Tracker) = trackerDao.update(tracker)

    suspend fun deleteTracker(id: Long) = trackerDao.deleteById(id)

    suspend fun logEvent(trackerId: Long, epochDay: Long, timeMinutes: Int?): Long =
        logDao.insert(LogEntry(trackerId = trackerId, epochDay = epochDay, timeMinutes = timeMinutes))

    suspend fun deleteLog(id: Long) = logDao.deleteById(id)
}
