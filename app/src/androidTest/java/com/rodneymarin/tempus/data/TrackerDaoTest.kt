package com.rodneymarin.tempus.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rodneymarin.tempus.domain.FrequencyPeriod
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TrackerDaoTest {
    private lateinit var db: TempusDatabase

    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(), TempusDatabase::class.java
        ).allowMainThreadQueries().build()
    }

    @After fun teardown() = db.close()

    @Test fun insertAndObserveTracker() = runBlocking {
        val id = db.trackerDao().insert(
            Tracker(name = "Idas al baño", minFrequency = 3, maxFrequency = 5, period = FrequencyPeriod.WEEK)
        )
        val t = db.trackerDao().observeById(id).first()!!
        assertEquals("Idas al baño", t.name)
        assertEquals(3, t.minFrequency)
    }

    @Test fun cascadeDeleteRemovesLogs() = runBlocking {
        val trackerId = db.trackerDao().insert(Tracker(name = "A"))
        db.logEntryDao().insert(LogEntry(trackerId = trackerId, epochDay = 20000))
        db.logEntryDao().insert(LogEntry(trackerId = trackerId, epochDay = 20001))
        db.trackerDao().deleteById(trackerId)
        assertTrue(db.logEntryDao().observeByTracker(trackerId).first().isEmpty())
    }

    @Test fun logsOrderedNewestFirst() = runBlocking {
        val trackerId = db.trackerDao().insert(Tracker(name = "B"))
        db.logEntryDao().insert(LogEntry(trackerId = trackerId, epochDay = 100, timeMinutes = 60))
        db.logEntryDao().insert(LogEntry(trackerId = trackerId, epochDay = 200, timeMinutes = null))
        val logs = db.logEntryDao().observeByTracker(trackerId).first()
        assertEquals(200, logs[0].epochDay)
        assertEquals(100, logs[1].epochDay)
    }
}
