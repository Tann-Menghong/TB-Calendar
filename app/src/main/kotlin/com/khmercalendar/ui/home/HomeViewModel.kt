package com.khmercalendar.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.khmercalendar.core.holiday.Holiday
import com.khmercalendar.core.holiday.KhmerHolidays
import com.khmercalendar.core.khmer.CalendarWeek
import com.khmercalendar.core.khmer.Chhankitek
import com.khmercalendar.core.khmer.KhmerLunarDate
import com.khmercalendar.data.prefs.AppSettings
import com.khmercalendar.data.repo.EventRepository
import com.khmercalendar.domain.DayLoad
import com.khmercalendar.domain.EventOccurrence
import com.khmercalendar.domain.WeekOverview
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
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
    /** Today alone, for the dashboard's progress bar. The others span the whole window. */
    val todayTasksDone: Int = 0,
    val todayTasksTotal: Int = 0,
)

data class HomeState(
    val today: LocalDate = LocalDate.now(),
    val lunar: KhmerLunarDate? = null,
    val todayEvents: List<EventOccurrence> = emptyList(),
    val upcoming: List<EventOccurrence> = emptyList(),
    val tasks: List<EventOccurrence> = emptyList(),
    val holidays: List<Holiday> = emptyList(),
    val countdowns: List<Countdown> = emptyList(),
    /** The seven days of the current week, including the ones already behind today. */
    val week: List<DayLoad> = emptyList(),
    val weekTotals: WeekOverview.Totals = WeekOverview.Totals(0, 0, 0, null),
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

    /**
     * What the query window depends on.
     *
     * The first day of the week is in here rather than read inside [build] because it moves
     * the *start* of the range: the week module needs the days already behind today, which the
     * old window (today onwards) never loaded. Only the week-start is watched, not the whole
     * settings object - re-running the database query every time the accent colour changes
     * would be a query per tap of a colour swatch.
     */
    private val window = combine(
        today,
        settings.map { it.weekStart }.distinctUntilChanged(),
    ) { day, weekStart -> day to weekStart }

    val state: StateFlow<HomeState> = window
        .flatMapLatest { (day, weekStart) ->
            combine(
                repository.observeOccurrences(
                    CalendarWeek.startOfWeek(day, weekStart),
                    day.plusDays(WINDOW_DAYS),
                ),
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

        // Everything below "what is coming up" is deliberately clamped to today onwards. The
        // window now reaches back to the start of the week for the week module, and without
        // this clamp Monday's finished meetings would reappear in the upcoming list on a
        // Thursday.
        val flat = byDate.entries
            .filterNot { it.key.isBefore(day) }
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

        val week = WeekOverview.build(
            today = day,
            weekStart = prefs.weekStart,
            byDate = byDate,
            holidayDates = if (prefs.showHolidays) {
                KhmerHolidays.inRange(
                    CalendarWeek.startOfWeek(day, prefs.weekStart),
                    CalendarWeek.startOfWeek(day, prefs.weekStart).plusDays(6),
                ).map { it.date }.toSet()
            } else {
                emptySet()
            },
        )

        val monthEnd = day.withDayOfMonth(day.lengthOfMonth())
        HomeState(
            today = day,
            lunar = if (prefs.showKhmerLunarDates) Chhankitek.toLunarOrNull(day) else null,
            todayEvents = todayEvents,
            upcoming = upcoming,
            tasks = tasks,
            holidays = holidays,
            countdowns = countdowns,
            week = week,
            weekTotals = WeekOverview.totals(week),
            stats = HomeStats(
                eventsThisMonth = byDate
                    .filterKeys { !it.isBefore(day) && !it.isAfter(monthEnd) }
                    .values.sumOf { it.size },
                openTasks = flat.count { it.isTask && !it.isCompleted },
                doneTasks = flat.count { it.isTask && it.isCompleted },
                todayTasksDone = todayEvents.count { it.isTask && it.isCompleted },
                todayTasksTotal = todayEvents.count { it.isTask },
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
