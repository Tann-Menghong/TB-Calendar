package com.khmercalendar.domain

import com.khmercalendar.core.khmer.CalendarWeek
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

/**
 * One day in the month grid.
 *
 * [level] rather than a raw count is what the cell draws. A heatmap is read by comparing
 * shades, and the eye cannot tell four steps of opacity from five - so the count is bucketed
 * here, where the choice is visible and testable, instead of inside a colour expression.
 */
data class MonthCell(
    val date: LocalDate,
    val events: Int,
    val tasks: Int,
    val level: Int,
    val isToday: Boolean,
    val isPast: Boolean,
    val isHoliday: Boolean,
) {
    val total: Int get() = events + tasks
}

/** A row of the grid. Days outside the month are null so the columns stay aligned. */
data class MonthWeek(val days: List<MonthCell?>)

/**
 * The month, as a heatmap.
 *
 * The week module answers "is Thursday going to be bad". This answers the question one step
 * out - where the heavy stretches of the month are - which is the difference between planning
 * a week and planning a month, and neither view substitutes for the other.
 *
 * Pure, like the week's builder, because everything interesting about it is arithmetic:
 * which column a day falls in for a user whose week starts on Sunday, how many rows a month
 * needs, and what a month with one busy day looks like.
 */
object MonthOverview {

    /** Shades above "empty". Four, because a fifth is not a difference anyone can see. */
    const val LEVELS = 4

    /**
     * @param month the month to draw. Passed in rather than derived from [today] so the same
     *   builder can draw a month the user has scrolled to.
     * @param byDate occurrences keyed by day; days missing from it are empty rather than absent.
     */
    fun build(
        month: YearMonth,
        today: LocalDate,
        weekStart: DayOfWeek,
        byDate: Map<LocalDate, List<EventOccurrence>>,
        holidayDates: Set<LocalDate> = emptySet(),
    ): List<MonthWeek> {
        val first = month.atDay(1)
        val length = month.lengthOfMonth()
        val peak = peak(month, byDate)

        val cells = (1..length).map { dayOfMonth ->
            val date = month.atDay(dayOfMonth)
            val onDay = byDate[date].orEmpty()
            val events = onDay.count { !it.isTask }
            val tasks = onDay.count { it.isTask }
            MonthCell(
                date = date,
                events = events,
                tasks = tasks,
                level = level(events + tasks, peak),
                isToday = date == today,
                isPast = date.isBefore(today),
                isHoliday = date in holidayDates,
            )
        }

        // Blank cells before the 1st so the first row lines up under the right weekday.
        val lead = CalendarWeek.indexInWeek(first, weekStart)
        val padded = List<MonthCell?>(lead) { null } + cells
        val trailing = (7 - padded.size % 7) % 7
        return (padded + List<MonthCell?>(trailing) { null })
            .chunked(7)
            .map { MonthWeek(it) }
    }

    /**
     * The busiest day in the month, floored.
     *
     * Floored at [MIN_PEAK] for the same reason the week's bars are: with a single event in
     * the month, one cell at full strength and thirty at nothing is not a comparison, it is
     * an alarm.
     */
    fun peak(month: YearMonth, byDate: Map<LocalDate, List<EventOccurrence>>): Int {
        val busiest = (1..month.lengthOfMonth())
            .maxOfOrNull { byDate[month.atDay(it)].orEmpty().size } ?: 0
        return maxOf(busiest, MIN_PEAK)
    }

    /**
     * Which shade a day gets, from 0 (nothing) to [LEVELS].
     *
     * A day with anything on it is never level 0: the lightest shade has to mean "something",
     * or a quiet day and a free day look identical and the grid stops being a calendar.
     */
    fun level(total: Int, peak: Int): Int = when {
        total <= 0 -> 0
        peak <= 0 -> 1
        else -> (1 + (total - 1) * (LEVELS - 1) / maxOf(peak - 1, 1)).coerceIn(1, LEVELS)
    }

    /** The month's totals, for the line beside the heading. */
    fun totals(weeks: List<MonthWeek>): Totals {
        val cells = weeks.flatMap { it.days }.filterNotNull()
        return Totals(
            events = cells.sumOf { it.events },
            tasks = cells.sumOf { it.tasks },
            freeDays = cells.count { it.total == 0 },
            busiest = cells.filter { it.total > 0 }.maxByOrNull { it.total }?.date,
        )
    }

    data class Totals(
        val events: Int,
        val tasks: Int,
        val freeDays: Int,
        val busiest: LocalDate?,
    )

    private const val MIN_PEAK = 3
}
