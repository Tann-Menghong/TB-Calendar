package com.khmercalendar.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.khmercalendar.data.prefs.AppSettings
import com.khmercalendar.data.repo.EventRepository
import com.khmercalendar.data.repo.FocusRepository
import com.khmercalendar.data.repo.HabitRepository
import com.khmercalendar.domain.StatsBucket
import com.khmercalendar.domain.StatsInput
import com.khmercalendar.domain.StatsMetric
import com.khmercalendar.domain.StatsPeriod
import com.khmercalendar.domain.StatsRange
import com.khmercalendar.domain.StatsSummary
import com.khmercalendar.domain.Statistics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.LocalDate
import java.time.ZoneId

/**
 * What the statistics screen draws.
 *
 * @property previous the same *slice* of the previous period, or null when there is nothing
 *   comparable. See [Statistics.comparisonRange] for why it is a slice rather than the whole
 *   previous period.
 */
data class StatsState(
    val period: StatsPeriod = StatsPeriod.WEEK,
    val metric: StatsMetric = StatsMetric.TASKS,
    val range: StatsRange? = null,
    val title: String = "",
    val summary: StatsSummary = StatsSummary(),
    val previous: StatsSummary? = null,
    val buckets: List<StatsBucket> = emptyList(),
    val series: List<Int?> = emptyList(),
    val canGoForward: Boolean = false,
    /** The period containing today. When false, the header offers a way back to it. */
    val isCurrent: Boolean = true,
    val isLoading: Boolean = true,
)

/**
 * ស្ថិតិ — local productivity statistics.
 *
 * ## One window, two answers
 *
 * The period being shown and the slice it is compared against are read in a *single* set of
 * queries spanning both, and [Statistics.summarise] is then run twice over the same rows.
 * The alternative - a second set of queries for the previous period - doubles the database
 * work and introduces a way for the two halves of one screen to disagree, because they would
 * have been read at different moments.
 *
 * ## Why the arithmetic is moved off the main thread
 *
 * Room emits on its own executor, but the transform in a `combine` runs wherever the
 * collector is - and `stateIn(viewModelScope)` collects on the main dispatcher. A year of a
 * busy calendar is thousands of expanded occurrences, and summing them on the main thread is
 * a dropped frame at best. [flowOn] moves the whole upstream, expansion included.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StatsViewModel(
    private val events: EventRepository,
    private val focus: FocusRepository,
    private val habits: HabitRepository,
    private val settings: StateFlow<AppSettings>,
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val clock: () -> LocalDate = { LocalDate.now() },
) : ViewModel() {

    private data class Selection(
        val period: StatsPeriod = StatsPeriod.WEEK,
        val anchor: LocalDate,
        val metric: StatsMetric = StatsMetric.TASKS,
    )

    /**
     * The only two preferences this screen reads.
     *
     * Narrowed and de-duplicated rather than taking the whole [AppSettings]: every flow below
     * is resubscribed whenever this emits, so collecting the full settings object would make
     * changing the accent colour re-expand a year of recurring events.
     */
    private data class Prefs(
        val weekStart: java.time.DayOfWeek,
        val workSchedule: com.khmercalendar.core.work.WorkSchedule,
    )

    private val selection = MutableStateFlow(Selection(anchor = clock()))

    val state: StateFlow<StatsState> = combine(
        selection,
        settings.map { Prefs(it.weekStart, it.workSchedule) }.distinctUntilChanged(),
    ) { choice, prefs -> choice to prefs }
        .flatMapLatest { (choice, prefs) ->
            val today = clock()
            val range = Statistics.rangeOf(choice.period, choice.anchor, today, prefs.weekStart)
            val comparison = Statistics.comparisonRange(range, prefs.weekStart)

            // Wide enough for both answers: back to the start of the comparison slice, and
            // forward to the end of the period so the chart has every day it will draw.
            val from = comparison?.start ?: range.start
            val to = range.end

            combine(
                events.observeCompletedTasks(from, to),
                events.observeOccurrences(from, to),
                focus.observeBetween(from, to),
                habits.observeTicksBetween(from, to),
                events.observeNotes(from, to),
            ) { tasks, occurrences, sessions, habitTicks, notes ->
                val input = StatsInput(
                    completedTasks = tasks,
                    focusSessions = sessions,
                    habits = habitTicks.habits,
                    habitTicks = habitTicks.ticks,
                    occurrences = occurrences.values.flatten(),
                    noteDays = notes,
                    workSchedule = prefs.workSchedule,
                    zone = zone,
                )
                val buckets = Statistics.buckets(range)
                StatsState(
                    period = choice.period,
                    metric = choice.metric,
                    range = range,
                    title = Statistics.title(range),
                    summary = Statistics.summarise(input, range),
                    previous = comparison?.let { Statistics.summarise(input, it) },
                    buckets = buckets,
                    series = Statistics.series(input, buckets, choice.metric),
                    // Never past the period containing today. There is nothing to show in
                    // next month, and an arrow that leads to a blank screen is a bug the
                    // user has to discover.
                    canGoForward = !Statistics.shift(range, 1, prefs.weekStart).isFuture,
                    isCurrent = range.contains(today),
                    isLoading = false,
                )
            }
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsState())

    /**
     * Switches the window.
     *
     * The anchor is reset to today rather than carried across. Carrying it means tapping
     * "ឆ្នាំ" while looking at last March lands on last year, which is never what the tap
     * meant - the period buttons read as "show me this week / this month", and the arrows are
     * how you travel.
     */
    fun setPeriod(period: StatsPeriod) {
        selection.update { it.copy(period = period, anchor = clock()) }
    }

    fun setMetric(metric: StatsMetric) {
        selection.update { it.copy(metric = metric) }
    }

    /** Moves one period back, or forward when there is anywhere to go. */
    fun step(by: Long) {
        val prefs = settings.value
        selection.update { choice ->
            val today = clock()
            val range = Statistics.rangeOf(choice.period, choice.anchor, today, prefs.weekStart)
            val moved = Statistics.shift(range, by, prefs.weekStart)
            if (moved.isFuture) choice else choice.copy(anchor = moved.anchor)
        }
    }

    /** Back to the period containing today, however far away the user has travelled. */
    fun today() {
        selection.update { it.copy(anchor = clock()) }
    }
}
