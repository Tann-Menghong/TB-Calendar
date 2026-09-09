package com.khmercalendar.domain

import com.khmercalendar.core.khmer.CalendarWeek
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

/** One day in a year grid's miniature month. */
data class YearDay(
    val date: LocalDate,
    val count: Int,
    val isToday: Boolean,
    val isHoliday: Boolean,
)

/**
 * One of the twelve miniature months.
 *
 * @property weeks rows of seven, padded with nulls so the columns line up under the weekday
 *   captions however the user's week starts.
 */
data class YearMonthGrid(
    val month: YearMonth,
    val weeks: List<List<YearDay?>>,
    val busyDays: Int,
    val total: Int,
)

/** What the year adds up to, for the line above the grid. */
data class YearSummary(
    val total: Int,
    val busiest: YearMonth?,
    val holidays: Int,
)

/**
 * The year, as twelve miniature months.
 *
 * ## Why a year view is worth having
 *
 * The month view answers "what is this month like"; the week and day answer what is on. None
 * of them answers "when is the quiet stretch", "which month is the exam in", or "how far away
 * is that" - the questions you ask when planning a trip, a deadline or a term. A year is the
 * only span where those are visible at a glance, and it is the last of the standard calendar
 * views this app did not have.
 *
 * ## Why it draws day numbers rather than a twelve-cell heatmap
 *
 * A heatmap of twelve months is a chart, and a chart is not a calendar: you cannot point at a
 * date in it. Twelve real grids let the user find the 15th of March and tap it, which is what
 * a year view is actually used for. The density is carried by marking the days that have
 * something on them, so the shape of the year is still readable without giving up the dates.
 *
 * Pure, like the month and week builders. Everything interesting here is arithmetic - which
 * column a day falls in, how many rows a month needs, where today is - and all of it is wrong
 * in exactly the same invisible way for anyone whose week does not start on Monday.
 */
object YearOverview {

    /** The first and last years the lunar engine can speak for. */
    const val MIN_YEAR = 1900
    const val MAX_YEAR = 2199

    /**
     * Twelve grids for [year].
     *
     * @param counts how many things fall on each day. Days missing from it are empty rather
     *   than absent - a year has 365 keys at most and building the whole map would cost more
     *   than the lookups it saves.
     */
    fun build(
        year: Int,
        today: LocalDate,
        weekStart: DayOfWeek,
        counts: Map<LocalDate, Int>,
        holidayDates: Set<LocalDate> = emptySet(),
    ): List<YearMonthGrid> = (1..12).map { monthValue ->
        val month = YearMonth.of(year, monthValue)
        buildMonth(month, today, weekStart, counts, holidayDates)
    }

    private fun buildMonth(
        month: YearMonth,
        today: LocalDate,
        weekStart: DayOfWeek,
        counts: Map<LocalDate, Int>,
        holidayDates: Set<LocalDate>,
    ): YearMonthGrid {
        val first = month.atDay(1)
        val lead = CalendarWeek.indexInWeek(first, weekStart)
        val length = month.lengthOfMonth()

        val cells = ArrayList<YearDay?>(lead + length)
        repeat(lead) { cells.add(null) }

        var total = 0
        var busy = 0
        for (day in 1..length) {
            val date = month.atDay(day)
            val count = counts[date] ?: 0
            total += count
            if (count > 0) busy++
            cells.add(
                YearDay(
                    date = date,
                    count = count,
                    isToday = date == today,
                    isHoliday = date in holidayDates,
                ),
            )
        }
        // Trailing padding, so every row is seven wide and the last one is not ragged.
        while (cells.size % 7 != 0) cells.add(null)

        return YearMonthGrid(
            month = month,
            weeks = cells.chunked(7),
            busyDays = busy,
            total = total,
        )
    }

    /**
     * The year in one line.
     *
     * The busiest month is the part worth stating: it is the thing a year view is scanned for
     * and the thing shading alone is worst at, since two nearly equal months look identical.
     * A year with nothing in it has no busiest month rather than an arbitrary January.
     */
    fun summarise(grids: List<YearMonthGrid>, holidayDates: Set<LocalDate>, year: Int): YearSummary {
        val busiest = grids.filter { it.total > 0 }.maxByOrNull { it.total }?.month
        return YearSummary(
            total = grids.sumOf { it.total },
            busiest = busiest,
            holidays = holidayDates.count { it.year == year },
        )
    }

    /** Whether the lunar engine can speak for [year]; the screen says so rather than guessing. */
    fun isSupported(year: Int): Boolean = year in MIN_YEAR..MAX_YEAR
}

/**
 * The year view's state.
 *
 * Beside [YearOverview] rather than in the UI package so the screen and the view model share
 * one shape, the same way the month's does.
 */
data class YearState(
    val year: Int = LocalDate.now().year,
    val months: List<YearMonthGrid> = emptyList(),
    val summary: YearSummary = YearSummary(0, null, 0),
    val isLoading: Boolean = true,
    /** Set when the year falls outside the range the lunar engine can convert. */
    val outOfRangeMessage: String? = null,
)
