package com.khmercalendar.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * The week module's arithmetic.
 *
 * Worth testing off the device because every interesting case is one the eye cannot check on a
 * screenshot: an empty week, a week with one event in it, and a week that starts on Sunday for
 * a user who set it that way.
 */
class WeekOverviewTest {

    // A Wednesday, so the week has days on both sides of today under either week start.
    private val today = LocalDate.of(2026, 9, 9)

    private fun occurrence(
        date: LocalDate,
        isTask: Boolean = false,
        done: Boolean = false,
    ) = EventOccurrence(
        eventId = 1,
        title = "x",
        description = null,
        location = null,
        occurrenceDate = date,
        start = LocalDateTime.of(date, LocalTime.of(9, 0)),
        end = LocalDateTime.of(date, LocalTime.of(10, 0)),
        allDay = false,
        colorArgb = 0,
        categoryId = null,
        categoryName = null,
        isTask = isTask,
        isCompleted = done,
        isRecurring = false,
    )

    @Test
    fun `a week is always seven days, whatever the data holds`() {
        val days = WeekOverview.build(today, DayOfWeek.MONDAY, emptyMap())

        assertEquals(7, days.size)
        assertEquals(LocalDate.of(2026, 9, 7), days.first().date)
        assertEquals(LocalDate.of(2026, 9, 13), days.last().date)
    }

    @Test
    fun `the week starts where the preference says, not on Monday`() {
        // Monday is only the default. Hard-coding it is the bug this test exists to catch.
        val days = WeekOverview.build(today, DayOfWeek.SUNDAY, emptyMap())

        assertEquals(LocalDate.of(2026, 9, 6), days.first().date)
        assertEquals(DayOfWeek.SUNDAY, days.first().date.dayOfWeek)
    }

    @Test
    fun `events and tasks are counted apart, and completion with them`() {
        val days = WeekOverview.build(
            today,
            DayOfWeek.MONDAY,
            mapOf(
                today to listOf(
                    occurrence(today),
                    occurrence(today),
                    occurrence(today, isTask = true),
                    occurrence(today, isTask = true, done = true),
                ),
            ),
        )
        val wednesday = days.single { it.isToday }

        assertEquals(2, wednesday.events)
        assertEquals(2, wednesday.tasks)
        assertEquals(1, wednesday.tasksDone)
        assertEquals(4, wednesday.total)
        assertFalse(wednesday.isEmpty)
    }

    @Test
    fun `days before today are marked past, and today is not one of them`() {
        val days = WeekOverview.build(today, DayOfWeek.MONDAY, emptyMap())

        assertEquals(listOf(true, true, false, false, false, false, false), days.map { it.isPast })
        assertEquals(1, days.count { it.isToday })
    }

    @Test
    fun `a day missing from the map is empty rather than absent`() {
        val days = WeekOverview.build(today, DayOfWeek.MONDAY, mapOf(today to listOf(occurrence(today))))

        assertEquals(7, days.size)
        assertEquals(6, days.count { it.isEmpty })
    }

    @Test
    fun `holidays are marked only on the days given`() {
        val holiday = LocalDate.of(2026, 9, 11)
        val days = WeekOverview.build(today, DayOfWeek.MONDAY, emptyMap(), setOf(holiday))

        assertEquals(listOf(holiday), days.filter { it.isHoliday }.map { it.date })
    }

    // --- the bar heights ---------------------------------------------------------------

    @Test
    fun `one event in a whole week does not draw a full-height bar`() {
        // The failure this prevents: peak equals the only value, so that day is 100% and the
        // other six are 0%, which reads as a catastrophic week rather than a quiet one.
        val days = WeekOverview.build(today, DayOfWeek.MONDAY, mapOf(today to listOf(occurrence(today))))
        val peak = WeekOverview.peak(days)

        assertEquals(3, peak)
        assertEquals(1f / 3f, WeekOverview.fraction(days.single { it.isToday }, peak), 0.001f)
    }

    @Test
    fun `a busy day sets the scale once it passes the floor`() {
        val busy = LocalDate.of(2026, 9, 10)
        val days = WeekOverview.build(
            today,
            DayOfWeek.MONDAY,
            mapOf(busy to List(5) { occurrence(busy) }, today to listOf(occurrence(today))),
        )
        val peak = WeekOverview.peak(days)

        assertEquals(5, peak)
        assertEquals(1f, WeekOverview.fraction(days.single { it.date == busy }, peak), 0.001f)
        assertEquals(0.2f, WeekOverview.fraction(days.single { it.isToday }, peak), 0.001f)
    }

    @Test
    fun `an empty week has a peak but no fractions`() {
        val days = WeekOverview.build(today, DayOfWeek.MONDAY, emptyMap())
        val peak = WeekOverview.peak(days)

        assertTrue(peak > 0)
        assertTrue(days.all { WeekOverview.fraction(it, peak) == 0f })
    }

    // --- the summary line --------------------------------------------------------------

    @Test
    fun `totals add up across the week and name the busiest day`() {
        val busy = LocalDate.of(2026, 9, 10)
        val days = WeekOverview.build(
            today,
            DayOfWeek.MONDAY,
            mapOf(
                today to listOf(occurrence(today), occurrence(today, isTask = true, done = true)),
                busy to List(3) { occurrence(busy) },
            ),
        )
        val totals = WeekOverview.totals(days)

        assertEquals(4, totals.events)
        assertEquals(1, totals.tasks)
        assertEquals(1, totals.tasksDone)
        assertEquals(busy, totals.busiest)
    }

    @Test
    fun `an empty week has no busiest day rather than an arbitrary one`() {
        // maxByOrNull over seven equal zeroes would otherwise name Monday, and the module
        // would tell the user their busiest day was one with nothing on it.
        val totals = WeekOverview.totals(WeekOverview.build(today, DayOfWeek.MONDAY, emptyMap()))

        assertEquals(0, totals.events)
        assertNull(totals.busiest)
    }
}
