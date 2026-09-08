package com.khmercalendar.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth

/**
 * The month heatmap's arithmetic.
 *
 * The grid is the part of this that cannot be checked by looking: a column that is one place
 * out is still a plausible-looking calendar, and it is only wrong for users whose week starts
 * on a different day from the one the developer happened to have set.
 */
class MonthOverviewTest {

    // September 2026 starts on a Tuesday and has 30 days, so neither week start lines up
    // neatly with the 1st and both padding cases are exercised.
    private val september = YearMonth.of(2026, 9)
    private val today = LocalDate.of(2026, 9, 9)

    private fun occurrence(date: LocalDate, isTask: Boolean = false) = EventOccurrence(
        eventId = 1,
        title = "x",
        description = null,
        location = null,
        occurrenceDate = date,
        start = LocalDateTime.of(date, LocalTime.NOON),
        end = LocalDateTime.of(date, LocalTime.NOON).plusHours(1),
        allDay = false,
        colorArgb = 0,
        categoryId = null,
        categoryName = null,
        isTask = isTask,
        isCompleted = false,
        isRecurring = false,
    )

    private fun load(vararg counts: Pair<Int, Int>): Map<LocalDate, List<EventOccurrence>> =
        counts.associate { (day, n) ->
            september.atDay(day) to List(n) { occurrence(september.atDay(day)) }
        }

    // --- the grid ------------------------------------------------------------------------

    @Test
    fun `every row is seven cells wide and every day appears once`() {
        val weeks = MonthOverview.build(september, today, DayOfWeek.MONDAY, emptyMap())

        assertTrue(weeks.all { it.days.size == 7 })
        val dates = weeks.flatMap { it.days }.filterNotNull().map { it.date.dayOfMonth }
        assertEquals((1..30).toList(), dates)
    }

    @Test
    fun `the first day is padded into the right column for the user's week start`() {
        // 1 September 2026 is a Tuesday: the second column of a Monday-start week, the third
        // of a Sunday-start one. Getting this wrong still draws a plausible calendar.
        val monday = MonthOverview.build(september, today, DayOfWeek.MONDAY, emptyMap())
        assertEquals(listOf(null, 1), monday.first().days.take(2).map { it?.date?.dayOfMonth })

        val sunday = MonthOverview.build(september, today, DayOfWeek.SUNDAY, emptyMap())
        assertEquals(listOf(null, null, 1), sunday.first().days.take(3).map { it?.date?.dayOfMonth })
    }

    @Test
    fun `the last row is padded out rather than left short`() {
        val weeks = MonthOverview.build(september, today, DayOfWeek.MONDAY, emptyMap())

        assertEquals(7, weeks.last().days.size)
        assertNull(weeks.last().days.last())
    }

    @Test
    fun `a month that starts on the week start needs no leading blanks`() {
        // June 2026 starts on a Monday.
        val june = YearMonth.of(2026, 6)
        val weeks = MonthOverview.build(june, june.atDay(1), DayOfWeek.MONDAY, emptyMap())

        assertEquals(1, weeks.first().days.first()?.date?.dayOfMonth)
    }

    @Test
    fun `today and the days behind it are marked`() {
        val weeks = MonthOverview.build(september, today, DayOfWeek.MONDAY, emptyMap())
        val cells = weeks.flatMap { it.days }.filterNotNull()

        assertEquals(listOf(9), cells.filter { it.isToday }.map { it.date.dayOfMonth })
        assertEquals((1..8).toList(), cells.filter { it.isPast }.map { it.date.dayOfMonth })
    }

    @Test
    fun `events and tasks are counted separately on a cell`() {
        val date = september.atDay(10)
        val weeks = MonthOverview.build(
            september,
            today,
            DayOfWeek.MONDAY,
            mapOf(date to listOf(occurrence(date), occurrence(date, isTask = true))),
        )
        val cell = weeks.flatMap { it.days }.filterNotNull().single { it.date == date }

        assertEquals(1, cell.events)
        assertEquals(1, cell.tasks)
        assertEquals(2, cell.total)
    }

    @Test
    fun `holidays are marked only where they fall`() {
        val holiday = september.atDay(24)
        val weeks = MonthOverview.build(september, today, DayOfWeek.MONDAY, emptyMap(), setOf(holiday))

        assertEquals(
            listOf(holiday),
            weeks.flatMap { it.days }.filterNotNull().filter { it.isHoliday }.map { it.date },
        )
    }

    // --- the shading ---------------------------------------------------------------------

    @Test
    fun `a day with anything on it is never the same shade as a free day`() {
        // The lightest shade has to mean "something", or a quiet day and a free day look
        // identical and the grid stops being a calendar.
        assertEquals(0, MonthOverview.level(total = 0, peak = 8))
        assertTrue(MonthOverview.level(total = 1, peak = 8) >= 1)
    }

    @Test
    fun `the busiest day reaches the darkest shade and nothing exceeds it`() {
        assertEquals(MonthOverview.LEVELS, MonthOverview.level(total = 8, peak = 8))
        assertEquals(MonthOverview.LEVELS, MonthOverview.level(total = 99, peak = 8))
    }

    @Test
    fun `one busy day in a quiet month is not an alarm`() {
        // Without the floor the single event is the peak, so its cell is full strength and
        // every other day is empty - which reads as a catastrophic month, not a quiet one.
        val peak = MonthOverview.peak(september, load(10 to 1))

        assertEquals(3, peak)
        assertTrue(MonthOverview.level(1, peak) < MonthOverview.LEVELS)
    }

    @Test
    fun `an empty month has a peak rather than a division by zero`() {
        assertTrue(MonthOverview.peak(september, emptyMap()) > 0)
        assertEquals(0, MonthOverview.level(0, MonthOverview.peak(september, emptyMap())))
    }

    // --- totals --------------------------------------------------------------------------

    @Test
    fun `totals count the month, including the days with nothing on them`() {
        val weeks = MonthOverview.build(september, today, DayOfWeek.MONDAY, load(3 to 2, 10 to 4))
        val totals = MonthOverview.totals(weeks)

        assertEquals(6, totals.events)
        assertEquals(28, totals.freeDays)
        assertEquals(september.atDay(10), totals.busiest)
    }

    @Test
    fun `an empty month has no busiest day rather than the first of it`() {
        val weeks = MonthOverview.build(september, today, DayOfWeek.MONDAY, emptyMap())

        assertNull(MonthOverview.totals(weeks).busiest)
        assertEquals(30, MonthOverview.totals(weeks).freeDays)
    }
}
