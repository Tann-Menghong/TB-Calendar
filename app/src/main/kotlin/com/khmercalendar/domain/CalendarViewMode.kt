package com.khmercalendar.domain

import com.khmercalendar.core.khmer.CalendarWeek
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

/**
 * The four ways of reading the same calendar.
 *
 * These used to be four separate destinations, three of which had no way in: the month was a
 * tab, the day opened when you tapped a date, and the week and the agenda were reachable only
 * from a link on another screen. A calendar app that holds a week view nobody can find does
 * not really have one.
 *
 * The key is what a saved preference stores, so it must stay stable; the enum order is the
 * order of the switcher, which runs from the widest span to the narrowest and then to the
 * list.
 *
 * The year came last (v1.12.0) and is the widest, so it sits first - which is also why the
 * order is written here rather than in the switcher: a view added later must land in the
 * right place by construction, not because somebody remembered to reorder a `when`.
 */
enum class CalendarViewMode(val key: String, val labelKm: String) {
    YEAR("year", "ឆ្នាំ"),
    MONTH("month", "ខែ"),
    WEEK("week", "សប្តាហ៍"),
    DAY("day", "ថ្ងៃ"),
    AGENDA("agenda", "បញ្ជី"),
    ;

    /**
     * Whether this view is anchored to a particular date.
     *
     * The agenda is not: it is a rolling list that starts at today and runs forward, so
     * stepping it or jumping it to a date would be stepping something that has no position.
     * The header hides its own navigation rather than showing arrows that do nothing.
     */
    val isDated: Boolean get() = this != AGENDA

    /**
     * Whether this view browses by the visible month rather than by the selection.
     *
     * The year and the month both do: stepping either should move what you are looking at
     * without moving the day whose events are listed underneath.
     */
    val browsesByMonth: Boolean get() = this == YEAR || this == MONTH

    companion object {
        fun of(key: String?): CalendarViewMode = entries.firstOrNull { it.key == key } ?: MONTH
    }
}

/**
 * Moving around the calendar, in whatever unit is currently on screen.
 *
 * Pure date arithmetic, kept out of the composables so that "back" from the 31st of March
 * can be tested rather than discovered. [java.time] clamps a short month for us; the part
 * worth writing down is that each view steps by *its own* unit, which is the whole reason
 * one shared header can drive all four.
 */
object CalendarNavigation {

    /** The date one unit of [mode] away from [date]. */
    fun step(mode: CalendarViewMode, date: LocalDate, forward: Boolean): LocalDate {
        val sign = if (forward) 1L else -1L
        return when (mode) {
            CalendarViewMode.YEAR -> date.plusYears(sign)
            CalendarViewMode.MONTH -> date.plusMonths(sign)
            CalendarViewMode.WEEK -> date.plusWeeks(sign)
            CalendarViewMode.DAY -> date.plusDays(sign)
            CalendarViewMode.AGENDA -> date
        }
    }

    /** The span [mode] is showing around [date], inclusive at both ends. */
    fun span(mode: CalendarViewMode, date: LocalDate, weekStart: DayOfWeek): Pair<LocalDate, LocalDate> =
        when (mode) {
            CalendarViewMode.YEAR ->
                LocalDate.of(date.year, 1, 1) to LocalDate.of(date.year, 12, 31)

            CalendarViewMode.MONTH -> {
                val month = YearMonth.from(date)
                month.atDay(1) to month.atEndOfMonth()
            }

            CalendarViewMode.WEEK -> {
                val start = CalendarWeek.startOfWeek(date, weekStart)
                start to start.plusDays(6)
            }

            CalendarViewMode.DAY -> date to date

            // The agenda has no span it could report honestly; it runs from today to
            // wherever the user has scrolled. Reported as a single day so callers that ask
            // anyway get something in range rather than a null they have to handle.
            CalendarViewMode.AGENDA -> date to date
        }

    /**
     * Whether the span on screen contains today, which is what greys out the "today" button.
     *
     * Asked of the span rather than of the date, because in the month view the selected date
     * can be the 3rd while today is the 20th of the same month - and jumping to today from
     * there would move the grid not at all.
     */
    fun showsToday(
        mode: CalendarViewMode,
        date: LocalDate,
        today: LocalDate,
        weekStart: DayOfWeek,
    ): Boolean {
        if (mode == CalendarViewMode.AGENDA) return true
        val (from, to) = span(mode, date, weekStart)
        return !today.isBefore(from) && !today.isAfter(to)
    }
}
