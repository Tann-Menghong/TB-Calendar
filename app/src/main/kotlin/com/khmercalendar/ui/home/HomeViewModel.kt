package com.khmercalendar.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.khmercalendar.core.holiday.Holiday
import com.khmercalendar.core.holiday.KhmerHolidays
import com.khmercalendar.core.khmer.Chhankitek
import com.khmercalendar.core.khmer.KhmerLunarDate
import com.khmercalendar.data.prefs.AppSettings
import com.khmercalendar.data.repo.EventRepository
import com.khmercalendar.domain.EventOccurrence
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/** An upcoming date the dashboard counts down to. */
data class Countdown(
    val title: String,
    val date: LocalDate,
    val daysAway: Long,
    val isHoliday: Boolean,
)

data class HomeStats(
    val eventsThisMonth: Int = 0,
    val openTasks: Int = 0,
    val doneTasks: Int = 0,
)

data class HomeState(
    val today: LocalDate = LocalDate.now(),
    val lunar: KhmerLunarDate? = null,
    val todayEvents: List<EventOccurrence> = emptyList(),
    val upcoming: List<EventOccurrence> = emptyList(),
    val tasks: List<EventOccurrence> = emptyList(),
    val holidays: List<Holiday> = emptyList(),
    val countdowns: List<Countdown> = emptyList(),
    val stats: HomeStats = HomeStats(),
    val note: String = "",
    val isLoading: Boolean = true,
) {
    /**
     * The next timed event still to come today, if there is one.
     *
     * All-day entries are excluded: "in 40 minutes" is meaningless for something that has no
     * clock time, and the dashboard would otherwise announce a birthday as though it were
     * about to start.
     */
    val nextEventToday: EventOccurrence?
        get() {
            val now = java.time.LocalDateTime.now()
            return todayEvents
                .filterNot { it.allDay || it.isCompleted }
                .filter { it.start.isAfter(now) }
                .minByOrNull { it.start }
        }
}

/**
 * Backs the dashboard.
 *
 * The window is fixed at 60 days. That is long enough for "what is coming up" to be useful
 * and short enough that expanding every repeating series over it costs less than a
 * millisecond, so the dashboard does not become the reason the app is slow to open.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(
    private val repository: EventRepository,
    private val settings: StateFlow<AppSettings>,
) : ViewModel() {

    private val today = MutableStateFlow(LocalDate.now())

    private val _noteDraft = MutableStateFlow<String?>(null)

    /** The note text being typed, which must not be overwritten by the stored value. */
    val noteDraft: StateFlow<String?> = _noteDraft.asStateFlow()

    val state: StateFlow<HomeState> = today
        .flatMapLatest { day ->
            combine(
                repository.observeOccurrences(day, day.plusDays(WINDOW_DAYS)),
                repository.observeNote(day),
                settings,
            ) { byDate, note, prefs ->
                build(day, byDate, note.orEmpty(), prefs)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState())

    private suspend fun build(
        day: LocalDate,
        byDate: Map<LocalDate, List<EventOccurrence>>,
        note: String,
        prefs: AppSettings,
    ): HomeState = withContext(Dispatchers.Default) {
        val now = LocalDateTime.now()
        val todayEvents = byDate[day].orEmpty().sortedBy { it.start }
        val flat = byDate.entries
            .sortedBy { it.key }
            .flatMap { entry -> entry.value.sortedBy { it.start } }

        val upcoming = flat
            .filter { !it.isTask && (it.allDay || it.end.isAfter(now)) }
            .take(UPCOMING_LIMIT)

        val tasks = flat.filter { it.isTask && !it.isCompleted }.take(TASK_LIMIT)

        val holidays = if (prefs.showHolidays) {
            KhmerHolidays.inRange(day, day.plusDays(WINDOW_DAYS))
                .filter { it.date >= day }
                .take(HOLIDAY_LIMIT)
        } else {
            emptyList()
        }

        val countdowns = buildList {
            holidays.firstOrNull()?.let {
                add(Countdown(it.nameKm, it.date, ChronoUnit.DAYS.between(day, it.date), true))
            }
            flat.firstOrNull { it.occurrenceDate.isAfter(day) }?.let {
                add(
                    Countdown(
                        title = it.title,
                        date = it.occurrenceDate,
                        daysAway = ChronoUnit.DAYS.between(day, it.occurrenceDate),
                        isHoliday = false,
                    ),
                )
            }
        }

        val monthEnd = day.withDayOfMonth(day.lengthOfMonth())
        HomeState(
            today = day,
            lunar = if (prefs.showKhmerLunarDates) Chhankitek.toLunarOrNull(day) else null,
            todayEvents = todayEvents,
            upcoming = upcoming,
            tasks = tasks,
            holidays = holidays,
            countdowns = countdowns,
            stats = HomeStats(
                eventsThisMonth = byDate.filterKeys { !it.isAfter(monthEnd) }.values.sumOf { it.size },
                openTasks = flat.count { it.isTask && !it.isCompleted },
                doneTasks = flat.count { it.isTask && it.isCompleted },
            ),
            note = note,
            isLoading = false,
        )
    }

    /** Called when the app comes back to the foreground, so the dashboard is not stale. */
    fun refreshToday() {
        val now = LocalDate.now()
        if (today.value != now) today.value = now
    }

    fun editNote(text: String) {
        _noteDraft.value = text
    }

    fun saveNote() {
        val text = _noteDraft.value ?: return
        viewModelScope.launch {
            repository.saveNote(today.value, text)
            _noteDraft.value = null
        }
    }

    fun setTaskCompleted(eventId: Long, completed: Boolean) {
        viewModelScope.launch { repository.setCompleted(eventId, completed) }
    }

    private companion object {
        const val WINDOW_DAYS = 60L
        const val UPCOMING_LIMIT = 8
        const val TASK_LIMIT = 6
        const val HOLIDAY_LIMIT = 4
    }
}
