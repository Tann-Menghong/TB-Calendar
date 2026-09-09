package com.khmercalendar.ui.focus

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.khmercalendar.data.prefs.AppSettings
import com.khmercalendar.data.repo.FocusRepository
import com.khmercalendar.domain.FocusKind
import com.khmercalendar.domain.FocusSession
import com.khmercalendar.domain.FocusSettings
import com.khmercalendar.domain.FocusTimer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

data class FocusState(
    val running: FocusSession? = null,
    /** What pressing start would begin, given where the cycle is. */
    val nextKind: FocusKind = FocusKind.FOCUS,
    val settings: FocusSettings = FocusSettings(),
    val minutesToday: Int = 0,
    val minutesThisWeek: Int = 0,
    val focusesToday: Int = 0,
    val recent: List<FocusSession> = emptyList(),
)

/**
 * The focus screen.
 *
 * Holds no timer. The running session is a row; the countdown on screen is computed from it
 * and the clock by [FocusTimer], and the screen's own ticker drives redraws only while it is
 * visible. Nothing here has to be told that time has passed.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FocusViewModel(
    private val repository: FocusRepository,
    private val settings: StateFlow<AppSettings>,
) : ViewModel() {

    private val zone: ZoneId = ZoneId.systemDefault()

    val state: StateFlow<FocusState> = combine(
        repository.observeRunning(),
        repository.observeRecent(),
        settings,
    ) { running, recent, prefs ->
        val today = LocalDate.now()
        val focusSettings = prefs.focusSettings
        val focusesToday = FocusTimer.completedFocusesOn(recent, today, zone)

        FocusState(
            running = running,
            // What comes next follows from the last thing that finished, not from what is
            // running now - so the button says the right thing before anything is started.
            nextKind = recent.firstOrNull { !it.isRunning }
                ?.let { FocusTimer.next(it.kind, focusesToday, focusSettings) }
                ?: FocusKind.FOCUS,
            settings = focusSettings,
            minutesToday = FocusTimer.minutesOn(recent, today, zone),
            minutesThisWeek = FocusTimer.minutesBetween(
                sessions = recent,
                from = com.khmercalendar.core.khmer.CalendarWeek.startOfWeek(today, prefs.weekStart),
                to = today,
                zone = zone,
            ),
            focusesToday = focusesToday,
            recent = recent.take(RECENT_SHOWN),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FocusState())

    fun start(kind: FocusKind) {
        val minutes = state.value.settings.minutesFor(kind)
        viewModelScope.launch { repository.start(kind, minutes) }
    }

    /** Ends the session and keeps it, which is what "done" means after the timer has run. */
    fun stop() {
        viewModelScope.launch { repository.stop() }
    }

    /** Throws the session away - "I did not actually do this" rather than "I finished". */
    fun discard() {
        viewModelScope.launch { repository.discard() }
    }

    private companion object {
        const val RECENT_SHOWN = 12
    }
}
