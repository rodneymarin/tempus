package com.rodneymarin.tempus.ui.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.rodneymarin.tempus.TempusApp
import com.rodneymarin.tempus.data.TrackersRepository
import com.rodneymarin.tempus.domain.FrequencyPeriod
import com.rodneymarin.tempus.domain.FrequencyRange
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

val EMOJI_PRESETS = listOf(
    "💧", "🚽", "💊", "🏃", "💪", "😴", "🥗", "🍎",
    "☕", "🚭", "🧘", "🦷", "🥤", "🚿", "🧹", "📵",
    "🎮", "📚", "🧠", "❤️", "🌙", "🚶", "🏋️", "💰",
    "🍽️", "🛌", "🚗", "🚲", "🏊", "🧩", "🎨", "🎵", "📝", "🛒",
)

data class EditorUiState(
    val name: String = "",
    val emoji: String = EMOJI_PRESETS.first(),
    val minFrequency: Int? = null,
    val maxFrequency: Int? = null,
    val period: FrequencyPeriod = FrequencyPeriod.WEEK,
    val nameError: Boolean = false,
    val rangeError: Int? = null,
    val loading: Boolean = true,
)

class TrackerEditorViewModel(
    private val repo: TrackersRepository,
    private val trackerId: Long?,
) : ViewModel() {

    private val _ui = MutableStateFlow(EditorUiState())
    val ui: StateFlow<EditorUiState> = _ui.asStateFlow()

    private val _done = MutableStateFlow(false)
    val done: StateFlow<Boolean> = _done

    init {
        if (trackerId != null) loadTracker(trackerId)
        else _ui.value = _ui.value.copy(loading = false)
    }

    private fun loadTracker(id: Long) {
        viewModelScope.launch {
            val t = repo.trackers.first().find { it.id == id }
            if (t != null) {
                _ui.value = EditorUiState(
                    name = t.name,
                    emoji = t.emoji,
                    minFrequency = t.minFrequency,
                    maxFrequency = t.maxFrequency,
                    period = t.period,
                    loading = false,
                )
            } else {
                _done.value = true // tracker vanished; go back
            }
        }
    }

    fun onNameChange(v: String) = _ui.update { it.copy(name = v, nameError = false) }
    fun onEmojiChange(v: String) = _ui.update { it.copy(emoji = v) }
    fun onMinChange(v: Int?) = _ui.update { it.copy(minFrequency = v, rangeError = null) }
    fun onMaxChange(v: Int?) = _ui.update { it.copy(maxFrequency = v, rangeError = null) }
    fun onPeriodChange(v: FrequencyPeriod) = _ui.update { it.copy(period = v, minFrequency = null, maxFrequency = null, rangeError = null) }

    fun save() {
        val s = _ui.value
        if (s.name.isBlank()) {
            _ui.update { it.copy(nameError = true) }
            return
        }
        val min = s.minFrequency
        val max = s.maxFrequency
        when (val r = FrequencyRange.parse(min?.toString() ?: "", max?.toString() ?: "")) {
            is FrequencyRange.Result.Valid -> {
                viewModelScope.launch {
                    if (trackerId == null) {
                        repo.createTracker(s.name, s.emoji, r.min, r.max, s.period)
                    } else {
                        val existing = repo.trackers.first().find { it.id == trackerId } ?: return@launch
                        repo.updateTracker(
                            existing.copy(
                                name = s.name.trim(), emoji = s.emoji,
                                minFrequency = r.min, maxFrequency = r.max, period = s.period,
                            )
                        )
                    }
                    _done.value = true
                }
            }
            is FrequencyRange.Result.InvalidNumber ->
                _ui.update { it.copy(rangeError = com.rodneymarin.tempus.R.string.range_error_invalid) }
            is FrequencyRange.Result.MinGreaterThanMax ->
                _ui.update { it.copy(rangeError = com.rodneymarin.tempus.R.string.range_error_min_max) }
        }
    }

    companion object {
        fun Factory(trackerId: Long?): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as TempusApp
                TrackerEditorViewModel(app.container.repository, trackerId)
            }
        }
    }
}

private fun MutableStateFlow<EditorUiState>.update(transform: (EditorUiState) -> EditorUiState) {
    value = transform(value)
}
