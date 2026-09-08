package com.khmercalendar.domain

import com.khmercalendar.core.khmer.CalendarWeek
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * One day's load, as the week bar draws it.
 *
 * [events] and [tasks] are counted separately rather than summed, because they are different
 * kinds of commitment: eight events is a full day, eight tasks is a full week. The bar shows
 * both stacked so a day that is busy for one reason does not look like a day that is busy for
 * the other.
 */
data class DayLoad(
    val date: LocalDate,
    val events: Int,
    val tasks: Int,
    val tasksDone: Int,
    val isToday: Boolean,
    val isPast: Boolean,
    val isHoliday: Boolean,
) {
    val total: Int get() = events + tasks

    /** True when the day has nothing on it at all, which the bar draws as a baseline stub. */
    val isEmpty: Boolean get() = total == 0
}

/**
 * The current week, counted.
 *
 * Pure and separate from the composable, because almost everything interesting about it is
 * arithmetic rather than drawing: which day the week starts on, which days are behind you,
 * how tall the tallest bar is, and what happens when the whole week is empty (every bar
 * would otherwise be full height, since every day would equal the peak).
 */
object WeekOverview {

    /**
     * @param today the day the dashboard is showing; marks the current bar and divides past
     *   from future.
     * @param weekStart the user's first day of the week. Never hard-coded - the calendar,
     *   the widgets and this all read the same preference.
     * @param byDate occurrences keyed by day. Days missing from the map count as empty
     *   rather than being dropped, so the week is always seven bars wide.
     * @param holidayDates the public holidays inside the week, for the marker under the bar.
     */
    fun build(
        today: LocalDate,
        weekStart: DayOfWeek,
        byDate: Map<LocalDate, List<EventOccurrence>>,
        holidayDates: Set<LocalDate> = emptySet(),
    ): List<DayLoad> {
        val first = CalendarWeek.startOfWeek(today, weekStart)
        return (0L..6L).map { offset ->
            val date = first.plusDays(offset)
            val onDay = byDate[date].orEmpty()
            DayLoad(
                date = date,
                events = onDay.count { !it.isTask },
                tasks = onDay.count { it.isTask },
                tasksDone = onDay.count { it.isTask && it.isCompleted },
                isToday = date == today,
                isPast = date.isBefore(today),
                isHoliday = date in holidayDates,
            )
        }
    }

    /**
     * The height every other bar is measured against.
     *
     * Floored at [MIN_PEAK] so a week with a single event does not draw one full-height bar
     * and six empty ones. A bar chart's job is to compare, and there is nothing to compare a
     * lone value against.
     */
    fun peak(days: List<DayLoad>): Int = maxOf(days.maxOfOrNull { it.total } ?: 0, MIN_PEAK)

    /** [day]'s share of the week's busiest day, in 0f..1f. */
    fun fraction(day: DayLoad, peak: Int): Float =
        if (peak <= 0) 0f else (day.total.toFloat() / peak.toFloat()).coerceIn(0f, 1f)

    /** The week's totals, for the module's summary line. */
    fun totals(days: List<DayLoad>): Totals = Totals(
        events = days.sumOf { it.events },
        tasks = days.sumOf { it.tasks },
        tasksDone = days.sumOf { it.tasksDone },
        busiest = days.filterNot { it.isEmpty }.maxByOrNull { it.total }?.date,
    )

    data class Totals(
        val events: Int,
        val tasks: Int,
        val tasksDone: Int,
        val busiest: LocalDate?,
    )

    private const val MIN_PEAK = 3
}
