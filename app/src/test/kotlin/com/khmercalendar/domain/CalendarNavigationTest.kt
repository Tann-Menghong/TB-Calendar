package com.khmercalendar.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Stepping and titling the shared calendar header.
 *
 * One header drives four views, so the only thing that makes it feel like four views is that
 * each step moves by its own unit. The month cases are the ones worth pinning: month
 * arithmetic on a 31st is where date libraries differ, and a header that lands on the wrong
 * month after two taps is worse than no header.
 */
class CalendarNavigationTest {

    private val monday = DayOfWeek.MONDAY
    private val sunday = DayOfWeek.SUNDAY

    @Test
    fun `each view steps by its own unit`() {
        val date = LocalDate.of(2026, 9, 8)
        assertEquals(LocalDate.of(2026, 10, 8), CalendarNavigation.step(CalendarViewMode.MONTH, date, true))
        assertEquals(LocalDate.of(2026, 9, 15), CalendarNavigation.step(CalendarViewMode.WEEK, date, true))
        assertEquals(LocalDate.of(2026, 9, 9), CalendarNavigation.step(CalendarViewMode.DAY, date, true))
    }

    @Test
    fun `stepping back mirrors stepping forward`() {
        val date = LocalDate.of(2026, 9, 8)
        for (mode in CalendarViewMode.entries) {
            val forward = CalendarNavigation.step(mode, date, true)
            assertEquals(date, CalendarNavigation.step(mode, forward, false))
        }
    }

    @Test
    fun `a month step off the 31st lands on the last day of a shorter month`() {
        val end = LocalDate.of(2026, 1, 31)
        assertEquals(LocalDate.of(2026, 2, 28), CalendarNavigation.step(CalendarViewMode.MONTH, end, true))
    }

    @Test
    fun `the agenda does not step`() {
        val date = LocalDate.of(2026, 9, 8)
        assertEquals(date, CalendarNavigation.step(CalendarViewMode.AGENDA, date, true))
        assertEquals(date, CalendarNavigation.step(CalendarViewMode.AGENDA, date, false))
    }

    @Test
    fun `the week span follows the user's week start`() {
        val wednesday = LocalDate.of(2026, 9, 9)
        assertEquals(
            LocalDate.of(2026, 9, 7) to LocalDate.of(2026, 9, 13),
            CalendarNavigation.span(CalendarViewMode.WEEK, wednesday, monday),
        )
        assertEquals(
            LocalDate.of(2026, 9, 6) to LocalDate.of(2026, 9, 12),
            CalendarNavigation.span(CalendarViewMode.WEEK, wednesday, sunday),
        )
    }

    @Test
    fun `the month span is the whole month, not the grid`() {
        assertEquals(
            LocalDate.of(2026, 9, 1) to LocalDate.of(2026, 9, 30),
            CalendarNavigation.span(CalendarViewMode.MONTH, LocalDate.of(2026, 9, 9), monday),
        )
    }

    @Test
    fun `today is still on screen when another day of the same month is selected`() {
        val today = LocalDate.of(2026, 9, 20)
        val selected = LocalDate.of(2026, 9, 3)
        assertTrue(CalendarNavigation.showsToday(CalendarViewMode.MONTH, selected, today, monday))
        assertFalse(CalendarNavigation.showsToday(CalendarViewMode.WEEK, selected, today, monday))
        assertFalse(CalendarNavigation.showsToday(CalendarViewMode.DAY, selected, today, monday))
    }

    @Test
    fun `the agenda always shows today`() {
        val today = LocalDate.of(2026, 9, 20)
        val far = LocalDate.of(2030, 1, 1)
        assertTrue(CalendarNavigation.showsToday(CalendarViewMode.AGENDA, far, today, monday))
    }

    @Test
    fun `an unknown stored key falls back to the month`() {
        assertEquals(CalendarViewMode.MONTH, CalendarViewMode.of(null))
        assertEquals(CalendarViewMode.MONTH, CalendarViewMode.of("timeline"))
        assertEquals(CalendarViewMode.AGENDA, CalendarViewMode.of("agenda"))
    }

    @Test
    fun `only the agenda is undated`() {
        assertFalse(CalendarViewMode.AGENDA.isDated)
        assertTrue(CalendarViewMode.MONTH.isDated)
        assertTrue(CalendarViewMode.WEEK.isDated)
        assertTrue(CalendarViewMode.DAY.isDated)
    }
}
