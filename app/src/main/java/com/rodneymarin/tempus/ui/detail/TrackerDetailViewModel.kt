package com.rodneymarin.tempus.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.rodneymarin.tempus.TempusApp
import com.rodneymarin.tempus.data.LogEntry
import com.rodneymarin.tempus.data.Tracker
import com.rodneymarin.tempus.data.TrackersRepository
import com.rodneymarin.tempus.domain.StatsCalculator
import com.rodneymarin.tempus.domain.StatsCalculator.PeriodPoint
import com.rodneymarin.tempus.domain.StatsCalculator.TrackStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

data class DetailUiState(
    val tracker: Tracker? = null,
    val status: TrackStatus? = null,
    val periodPoints: List<PeriodPoint> = emptyList(),
    val dailyCounts: Map<LocalDate, Int> = emptyMap(),
    val history: List<LogEntry> = emptyList(),
)

data class LogConfirmation(val insertedId: Long)

class TrackerDetailViewModel(
    private val repo: TrackersRepository,
    private val trackerId: Long,
) : ViewModel() {

    val ui: StateFlow<DetailUiState> =
        combine(repo.logsFor(trackerId), repo.trackers) { logs, trackers ->
            val tracker = trackers.find { it.id == trackerId }
            val today = LocalDate.now()
            DetailUiState(
                tracker = tracker,
                status = tracker?.let {
                    StatsCalculator.statusFor(logs, it.minFrequency, it.maxFrequency, it.period, today)
                },
                periodPoints = tracker?.let {
                    StatsCalculator.lastPeriods(logs, it.period, today)
                } ?: emptyList(),
                dailyCounts = StatsCalculator.dailyCounts(logs, today = today),
                history = logs.sortedByDescending { it.epochDay },
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DetailUiState())

    private val _lastLog = MutableStateFlow<LogConfirmation?>(null)
    val lastLog: StateFlow<LogConfirmation?> = _lastLog.asStateFlow()

    private val _exited = MutableStateFlow(false)
    val exited: StateFlow<Boolean> = _exited.asStateFlow()

    fun logToday() {
        val now = LocalTime.now()
        logOn(LocalDate.now(), now.hour * 60 + now.minute)
    }

    /** Register an occurrence on [date]; [timeMinutes] null = "sin hora". */
    fun logOn(date: LocalDate, timeMinutes: Int?, comment: String? = null) {
        viewModelScope.launch {
            val id = repo.logEvent(trackerId, date.toEpochDay(), timeMinutes, comment)
            _lastLog.value = LogConfirmation(id)
        }
    }

    fun undoLastLog() {
        _lastLog.value?.let { conf -> viewModelScope.launch { repo.deleteLog(conf.insertedId) } }
        _lastLog.value = null
    }

    fun clearLastLog() {
        _lastLog.value = null
    }

    fun deleteLog(id: Long) = viewModelScope.launch { repo.deleteLog(id) }

    fun deleteDay(date: LocalDate) {
        _lastLog.value = null
        viewModelScope.launch {
            repo.deleteLogsForDay(trackerId, date.toEpochDay())
        }
    }

    /** Edita (o borra) el comentario de un día con evento registrado. */
    fun updateComment(date: LocalDate, comment: String?) {
        viewModelScope.launch {
            repo.updateComment(trackerId, date.toEpochDay(), comment?.trim()?.takeIf { it.isNotEmpty() })
        }
    }

    fun deleteTracker() {
        viewModelScope.launch {
            repo.deleteTracker(trackerId)
            _exited.value = true
        }
    }

    companion object {
        fun Factory(trackerId: Long): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as TempusApp
                TrackerDetailViewModel(app.container.repository, trackerId)
            }
        }
    }
}
