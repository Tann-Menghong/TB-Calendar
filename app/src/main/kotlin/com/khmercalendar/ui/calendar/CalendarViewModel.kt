package com.khmercalendar.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.khmercalendar.core.holiday.Holiday
import com.khmercalendar.core.holiday.KhmerHolidays
import com.khmercalendar.core.khmer.Chhankitek
import com.khmercalendar.data.prefs.AppSettings
import com.khmercalendar.data.repo.EventRepository
import com.khmercalendar.domain.CalendarNavigation
import com.khmercalendar.domain.CalendarViewMode
import com.khmercalendar.domain.EventOccurrence
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import com.khmercalendar.core.khmer.CalendarWeek
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

/**
 * Drives the month, week and day views.
 *
 * The visible month is the only input; everything else is derived. Each month's grid is
 * assembled off the main thread because a full grid means 42 lunar conversions plus a
 * holiday lookup, and doing that inside composition is what makes a calendar stutter when
 * the user swipes between months.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CalendarViewModel(
    private val repository: EventRepository,
    private val settings: StateFlow<AppSettings>,
) : ViewModel() {

    private val _visibleMonth = MutableStateFlow(YearMonth.now())
    val visibleMonth: StateFlow<YearMonth> = _visibleMonth.asStateFlow()

    private val _selectedDate = MutableStateFlow(LocalDate.now())
    val selectedDate: StateFlow<LocalDate> = _selectedDate.asStateFlow()

    private val _viewMode = MutableStateFlow(CalendarViewMode.MONTH)

    /**
     * Which of the four views the calendar is showing.
     *
     * State on the view model rather than a route argument, so that the calendar is one entry
     * in the back stack however the user has it set, and so that switching to the day view and
     * back to the month keeps the date you were looking at.
     */
    val viewMode: StateFlow<CalendarViewMode> = _viewMode.asStateFlow()

    val monthState: StateFlow<MonthState> = combine(
        _visibleMonth,
        _selectedDate,
        settings,
    ) { month, selected, prefs -> Triple(month, selected, prefs) }
        .flatMapLatest { (month, selected, prefs) ->
            val gridStart = gridStart(month, prefs.weekStart)
            val gridEnd = gridStart.plusDays(41)
            combine(
                repository.observeOccurrences(gridStart, gridEnd),
                repository.observeNotes(gridStart, gridEnd),
            ) { events, notes ->
                buildMonth(month, selected, prefs, gridStart, events, notes)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MonthState())

    /** Occurrences on the selected day, for the panel under the grid and the day view. */
    val selectedDayEvents: StateFlow<List<EventOccurrence>> = _selectedDate
        .flatMapLatest { date -> repository.observeOccurrences(date, date).map { it[date].orEmpty() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val selectedDayNote: StateFlow<String?> = _selectedDate
        .flatMapLatest { repository.observeNote(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** The seven days of the week containing the selection, with their events. */
    val weekState: StateFlow<List<Pair<LocalDate, List<EventOccurrence>>>> =
        combine(_selectedDate, settings) { date, prefs -> weekStart(date, prefs.weekStart) }
            .flatMapLatest { start ->
                repository.observeOccurrences(start, start.plusDays(6)).map { map ->
                    (0..6).map { i ->
                        val d = start.plusDays(i.toLong())
                        d to map[d].orEmpty()
                    }
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun showMonth(month: YearMonth) {
        _visibleMonth.value = month
    }

    fun show(mode: CalendarViewMode) {
        _viewMode.value = mode
    }

    /**
     * Move one unit of the current view.
     *
     * The month steps the *visible month* and leaves the selection where it is, which is what
     * the month header always did: browsing forward a few months to see what is there should
     * not silently move the day whose events are listed underneath. The week and day views
     * have no separate notion of what is visible, so for them the selection is the position.
     */
    fun step(forward: Boolean) {
        when (_viewMode.value) {
            CalendarViewMode.MONTH ->
                showMonth(if (forward) _visibleMonth.value.plusMonths(1) else _visibleMonth.value.minusMonths(1))

            CalendarViewMode.WEEK, CalendarViewMode.DAY ->
                select(CalendarNavigation.step(_viewMode.value, _selectedDate.value, forward))

            CalendarViewMode.AGENDA -> Unit
        }
    }

    fun select(date: LocalDate) {
        _selectedDate.value = date
        if (YearMonth.from(date) != _visibleMonth.value) _visibleMonth.value = YearMonth.from(date)
    }

    fun goToToday() = select(LocalDate.now())

    private suspend fun buildMonth(
        month: YearMonth,
        selected: LocalDate,
        prefs: AppSettings,
        gridStart: LocalDate,
        events: Map<LocalDate, List<EventOccurrence>>,
        notes: Set<LocalDate>,
    ): MonthState = withContext(Dispatchers.Default) {
        val today = LocalDate.now()
        val holidaysByDate: Map<LocalDate, List<Holiday>> = if (prefs.showHolidays) {
            KhmerHolidays.inRange(gridStart, gridStart.plusDays(41)).groupBy { it.date }
        } else {
            emptyMap()
        }

        val cells = (0..41).map { offset ->
            val date = gridStart.plusDays(offset.toLong())
            DayCellState(
                date = date,
                inCurrentMonth = YearMonth.from(date) == month,
                isToday = date == today,
                lunar = if (prefs.showKhmerLunarDates) Chhankitek.toLunarOrNull(date) else null,
                holidays = holidaysByDate[date].orEmpty(),
                events = events[date].orEmpty(),
                hasNote = date in notes,
            )
        }

        MonthState(
            yearMonth = month,
            weeks = cells.chunked(7),
            selected = selected,
            isLoading = false,
            outOfRangeMessage = when {
                month.atDay(1).isBefore(Chhankitek.MIN_DATE) ->
                    "ចន្ទគតិខ្មែរគណនាបានចាប់ពីឆ្នាំ ១៩០០ ទៅមុខ"
                month.atEndOfMonth().isAfter(Chhankitek.MAX_DATE) ->
                    "ចន្ទគតិខ្មែរគណនាបានដល់ឆ្នាំ ២១៩៩ ប៉ុណ្ណោះ"
                else -> null
            },
        )
    }

    companion object {
        /**
         * Thin forwarders onto [CalendarWeek].
         *
         * Kept so existing call sites read naturally, but the arithmetic lives in one place:
         * the month grid, the week view, the widgets and the recurrence engine must agree
         * about where a week begins, and they only do if they share the implementation.
         */
        fun gridStart(month: YearMonth, weekStart: DayOfWeek): LocalDate =
            CalendarWeek.startOfMonthGrid(month, weekStart)

        fun weekStart(date: LocalDate, weekStart: DayOfWeek): LocalDate =
            CalendarWeek.startOfWeek(date, weekStart)

        /** Column headings in the user's chosen week order. */
        fun weekDays(weekStart: DayOfWeek): List<DayOfWeek> = CalendarWeek.daysOfWeek(weekStart)
    }
}
