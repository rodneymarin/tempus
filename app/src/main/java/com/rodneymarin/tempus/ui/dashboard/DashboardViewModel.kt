package com.rodneymarin.tempus.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.rodneymarin.tempus.TempusApp
import com.rodneymarin.tempus.data.Tracker
import com.rodneymarin.tempus.data.TrackersRepository
import com.rodneymarin.tempus.domain.FrequencyRange
import com.rodneymarin.tempus.domain.StatsCalculator
import com.rodneymarin.tempus.domain.StatsCalculator.Status
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

data class TrackerRow(
    val tracker: Tracker,
    val countInPeriod: Int,
    val expectedSummary: String,
    val status: Status,
)

/** Emitted after a quick log so the UI can show "Registrado — Deshacer". */
data class LogConfirmation(val trackerId: Long, val insertedId: Long)

class DashboardViewModel(private val repo: TrackersRepository) : ViewModel() {

    val rows: StateFlow<List<TrackerRow>> =
        combine(repo.trackers, repo.allLogs) { trackers, logs ->
            val today = LocalDate.now()
            trackers.map { t ->
                val trackerLogs = logs.filter { it.trackerId == t.id }
                val status = StatsCalculator.statusFor(
                    trackerLogs, t.minFrequency, t.maxFrequency, t.period, today,
                )
                TrackerRow(
                    tracker = t,
                    countInPeriod = status.count,
                    expectedSummary = FrequencyRange.summary(t.minFrequency, t.maxFrequency),
                    status = status.status,
                )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _lastLog = MutableStateFlow<LogConfirmation?>(null)
    val lastLog: StateFlow<LogConfirmation?> = _lastLog

    /** Debounce: ignore taps while the previous insert is still being undone. */
    fun logToday(trackerId: Long) {
        viewModelScope.launch {
            val now = LocalTime.now()
            val id = repo.logEvent(
                trackerId = trackerId,
                epochDay = LocalDate.now().toEpochDay(),
                timeMinutes = now.hour * 60 + now.minute,
            )
            _lastLog.value = LogConfirmation(trackerId, id)
        }
    }

    fun undoLastLog() {
        _lastLog.value?.let { conf ->
            viewModelScope.launch { repo.deleteLog(conf.insertedId) }
        }
        _lastLog.value = null
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as TempusApp
                DashboardViewModel(app.container.repository)
            }
        }
    }
}
