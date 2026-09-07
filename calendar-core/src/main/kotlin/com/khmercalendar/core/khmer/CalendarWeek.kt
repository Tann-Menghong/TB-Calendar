package com.khmercalendar.core.khmer

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.WeekFields

/**
 * Everything that depends on which day a week starts on.
 *
 * The first day of the week is a single user preference, and it has to reach the month grid,
 * the week view, the agenda, the week numbers, the widgets and the recurrence engine
 * identically. Putting the arithmetic here rather than in each screen is what stops those
 * from drifting apart - which they had, with the UI reading the preference while the
 * recurrence expander assumed Sunday.
 *
 * Cambodia follows the Monday-first convention, which is also the ISO-8601 default, so that
 * is the app's default. It remains a preference because Sunday-first calendars are common in
 * the region too.
 */
object CalendarWeek {

    /** The default, and what every caller gets if the preference has never been set. */
    val DEFAULT_START: DayOfWeek = DayOfWeek.MONDAY

    /**
     * Saturday and Sunday.
     *
     * A constant rather than a preference: the Cambodian working week is Monday to Friday
     * (with Saturday half-days common), and no screen has ever needed to disagree. It lives
     * here so "is this a weekend" has one answer rather than four copies of the same `when`.
     */
    fun isWeekend(date: LocalDate): Boolean =
        date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY

    /** The seven weekdays in display order for a week beginning [weekStart]. */
    fun daysOfWeek(weekStart: DayOfWeek): List<DayOfWeek> =
        (0..6).map { weekStart.plus(it.toLong()) }

    /** How many days [date] is into its own week. 0 for the first day. */
    fun indexInWeek(date: LocalDate, weekStart: DayOfWeek): Int =
        ((date.dayOfWeek.value - weekStart.value) + 7) % 7

    /** The first day of the week containing [date]. */
    fun startOfWeek(date: LocalDate, weekStart: DayOfWeek): LocalDate =
        date.minusDays(indexInWeek(date, weekStart).toLong())

    /**
     * The first cell of a six-row month grid.
     *
     * Always the week start on or before the first of the month, so the grid is a whole
     * number of weeks and every column is the same weekday.
     */
    fun startOfMonthGrid(month: YearMonth, weekStart: DayOfWeek): LocalDate =
        startOfWeek(month.atDay(1), weekStart)

    /**
     * The week-of-year number for [date].
     *
     * Uses the user's first day of the week with a four-day minimum, which is the ISO rule
     * generalised: week 1 is the one containing at least four days of the new year. With the
     * Monday default this is exactly ISO-8601 week numbering.
     */
    fun weekOfYear(date: LocalDate, weekStart: DayOfWeek): Int =
        date.get(weekFields(weekStart).weekOfWeekBasedYear())

    private fun weekFields(weekStart: DayOfWeek): WeekFields = WeekFields.of(weekStart, 4)

    /** Parses a stored `DayOfWeek.value`, falling back to [DEFAULT_START]. */
    fun fromValue(value: Int?): DayOfWeek =
        value?.let { runCatching { DayOfWeek.of(it) }.getOrNull() } ?: DEFAULT_START
}
