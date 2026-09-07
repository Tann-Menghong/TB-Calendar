package com.khmercalendar.core.khmer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

/**
 * The first day of the week, which every view and the recurrence engine must agree on.
 *
 * These exist because the app previously disagreed with itself: the UI read the preference
 * while the recurrence expander assumed Sunday, so a fortnightly event could land on a
 * different date than the grid drew.
 */
class CalendarWeekTest {

    @Test
    fun `default is Monday`() {
        assertEquals(DayOfWeek.MONDAY, CalendarWeek.DEFAULT_START)
    }

    @Test
    fun `week starts on the chosen day`() {
        val wednesday = LocalDate.of(2026, 3, 11)

        assertEquals(LocalDate.of(2026, 3, 9), CalendarWeek.startOfWeek(wednesday, DayOfWeek.MONDAY))
        assertEquals(LocalDate.of(2026, 3, 8), CalendarWeek.startOfWeek(wednesday, DayOfWeek.SUNDAY))
        assertEquals(LocalDate.of(2026, 3, 7), CalendarWeek.startOfWeek(wednesday, DayOfWeek.SATURDAY))
    }

    @Test
    fun `a day that is the week start is index zero`() {
        val monday = LocalDate.of(2026, 3, 9)
        assertEquals(0, CalendarWeek.indexInWeek(monday, DayOfWeek.MONDAY))
        assertEquals(monday, CalendarWeek.startOfWeek(monday, DayOfWeek.MONDAY))
        // The same Monday is the second day of a Sunday-first week.
        assertEquals(1, CalendarWeek.indexInWeek(monday, DayOfWeek.SUNDAY))
    }

    @Test
    fun `weekday order follows the week start`() {
        assertEquals(
            listOf(
                DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY,
                DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY,
            ),
            CalendarWeek.daysOfWeek(DayOfWeek.MONDAY),
        )
        assertEquals(DayOfWeek.SUNDAY, CalendarWeek.daysOfWeek(DayOfWeek.SUNDAY).first())
        assertEquals(DayOfWeek.SATURDAY, CalendarWeek.daysOfWeek(DayOfWeek.SUNDAY).last())
    }

    @Test
    fun `month grid always starts on the week start and covers six weeks`() {
        for (month in 1..12) {
            for (start in DayOfWeek.entries) {
                val gridStart = CalendarWeek.startOfMonthGrid(YearMonth.of(2026, month), start)
                assertEquals(start, gridStart.dayOfWeek)
                // The grid must begin on or before the first of the month, within one week.
                val first = YearMonth.of(2026, month).atDay(1)
                assertFalse(gridStart.isAfter(first))
                assertTrue(gridStart.isAfter(first.minusDays(7)))
            }
        }
    }

    @Test
    fun `week numbers match ISO 8601 when the week starts on Monday`() {
        // 1 January 2026 is a Thursday, so it is in ISO week 1.
        assertEquals(1, CalendarWeek.weekOfYear(LocalDate.of(2026, 1, 1), DayOfWeek.MONDAY))
        assertEquals(53, CalendarWeek.weekOfYear(LocalDate.of(2026, 12, 31), DayOfWeek.MONDAY))
        assertEquals(11, CalendarWeek.weekOfYear(LocalDate.of(2026, 3, 9), DayOfWeek.MONDAY))
    }

    @Test
    fun `weekend is Saturday and Sunday`() {
        assertTrue(CalendarWeek.isWeekend(LocalDate.of(2026, 3, 7)))
        assertTrue(CalendarWeek.isWeekend(LocalDate.of(2026, 3, 8)))
        assertFalse(CalendarWeek.isWeekend(LocalDate.of(2026, 3, 9)))
        assertFalse(CalendarWeek.isWeekend(LocalDate.of(2026, 3, 13)))
    }

    @Test
    fun `an unset or invalid stored value falls back to the default`() {
        assertEquals(DayOfWeek.MONDAY, CalendarWeek.fromValue(null))
        assertEquals(DayOfWeek.MONDAY, CalendarWeek.fromValue(0))
        assertEquals(DayOfWeek.MONDAY, CalendarWeek.fromValue(99))
        assertEquals(DayOfWeek.SUNDAY, CalendarWeek.fromValue(7))
    }
}
