package com.khmercalendar.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Streak arithmetic.
 *
 * A streak is a number the user is asked to care about, so being wrong by one is the entire
 * failure - and every interesting case is a boundary that cannot be seen by looking at a list
 * of ticks. The weekday cases matter most: a habit due on Monday, Wednesday and Friday must
 * survive the weekend, and telling somebody they broke a streak on a day they never promised
 * anything is worse than showing no streak at all.
 */
class HabitStreaksTest {

    // Wednesday.
    private val today = LocalDate.of(2026, 9, 9)

    private val daily = HabitSchedule(HabitSchedule.Kind.DAILY)
    private val weekdays = HabitSchedule(
        kind = HabitSchedule.Kind.DAYS,
        days = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY),
    )
    private val twiceAWeek = HabitSchedule(HabitSchedule.Kind.TIMES_PER_WEEK, target = 2)

    private fun days(vararg offsets: Long): Set<LocalDate> =
        offsets.map { today.minusDays(it) }.toSet()

    // --- current streak, daily ---

    @Test
    fun `an unbroken run counts every day`() {
        assertEquals(4, HabitStreaks.current(daily, days(0, 1, 2, 3), today))
    }

    @Test
    fun `today not yet done does not reset the streak`() {
        // The day is not over. Yesterday and the day before still count.
        assertEquals(2, HabitStreaks.current(daily, days(1, 2), today))
    }

    @Test
    fun `a missed yesterday ends the streak even if today is done`() {
        assertEquals(1, HabitStreaks.current(daily, days(0, 2, 3), today))
    }

    @Test
    fun `nothing done is no streak`() {
        assertEquals(0, HabitStreaks.current(daily, emptySet(), today))
    }

    @Test
    fun `a gap two days back stops the count there`() {
        assertEquals(3, HabitStreaks.current(daily, days(0, 1, 2, 4, 5), today))
    }

    // --- current streak, specific weekdays ---

    @Test
    fun `a weekday habit survives the weekend it was never due on`() {
        // Today is Wednesday. Due Mon/Wed/Fri. Done: this Wed, this Mon, last Fri.
        val done = setOf(today, today.minusDays(2), today.minusDays(5))
        assertEquals(3, HabitStreaks.current(weekdays, done, today))
    }

    @Test
    fun `a weekday habit breaks on a day it was due and missed`() {
        // Done this Wednesday and last Friday, but Monday between them was missed.
        val done = setOf(today, today.minusDays(5))
        assertEquals(1, HabitStreaks.current(weekdays, done, today))
    }

    @Test
    fun `doing a weekday habit on an off day does not break the following count`() {
        // Tuesday is not a scheduled day; ticking it anyway is a bonus, not a problem.
        val done = setOf(today, today.minusDays(1), today.minusDays(2))
        assertEquals(3, HabitStreaks.current(weekdays, done, today))
    }

    // --- current streak, times per week ---

    @Test
    fun `a weekly-target habit cannot be failed on a particular day`() {
        // Two ticks a week apart: neither of the days between is a failure, so both count.
        val done = setOf(today, today.minusDays(4))
        assertEquals(2, HabitStreaks.current(twiceAWeek, done, today))
    }

    // --- longest ---

    @Test
    fun `the longest run is found even when it is not the current one`() {
        val done = days(0, 1) + days(5, 6, 7, 8)
        assertEquals(4, HabitStreaks.longest(daily, done))
    }

    @Test
    fun `longest is zero when nothing was ever done`() {
        assertEquals(0, HabitStreaks.longest(daily, emptySet()))
    }

    @Test
    fun `longest ignores days a weekday habit was not due on`() {
        // Fri, Mon, Wed across a weekend: three scheduled days in a row.
        val done = setOf(today, today.minusDays(2), today.minusDays(5))
        assertEquals(3, HabitStreaks.longest(weekdays, done))
    }

    // --- weekly progress ---

    @Test
    fun `weekly progress counts only this week`() {
        val done = setOf(today, today.minusDays(1), today.minusDays(10))
        assertEquals(2, HabitStreaks.weeklyProgress(done, today, DayOfWeek.MONDAY))
    }

    @Test
    fun `weekly progress follows the user's own week start`() {
        // Today is Wednesday 9 Sept. Sunday 6 Sept is in this week for a Sunday start and in
        // the previous one for a Monday start.
        val done = setOf(LocalDate.of(2026, 9, 6))
        assertEquals(0, HabitStreaks.weeklyProgress(done, today, DayOfWeek.MONDAY))
        assertEquals(1, HabitStreaks.weeklyProgress(done, today, DayOfWeek.SUNDAY))
    }

    // --- completion rate ---

    @Test
    fun `completion rate is measured against days the habit was due`() {
        // A weekday habit that never misses is 100%, not 43%.
        val done = (1..30L)
            .map { today.minusDays(it) }
            .filter { it.dayOfWeek in weekdays.days }
            .toSet()
        assertEquals(100, HabitStreaks.completionRate(weekdays, done, today))
    }

    @Test
    fun `completion rate halves when half the due days are missed`() {
        val due = (1..30L).map { today.minusDays(it) }.filter { it.dayOfWeek in weekdays.days }
        val done = due.filterIndexed { index, _ -> index % 2 == 0 }.toSet()
        val rate = HabitStreaks.completionRate(weekdays, done, today)
        assertTrue("rate was $rate", rate in 45..55)
    }

    @Test
    fun `a habit with no due days in the window reports zero rather than dividing by zero`() {
        val sundays = HabitSchedule(HabitSchedule.Kind.DAYS, days = setOf(DayOfWeek.SUNDAY))
        assertEquals(0, HabitStreaks.completionRate(sundays, emptySet(), today, days = 3))
    }

    @Test
    fun `today is excluded from the rate for the same reason it cannot break a streak`() {
        // Done every day including today; the window looks only at completed days.
        val done = (0..30L).map { today.minusDays(it) }.toSet()
        assertEquals(100, HabitStreaks.completionRate(daily, done, today))
    }

    // --- schedule ---

    @Test
    fun `due days follow the kind`() {
        assertTrue(daily.isDue(today))
        assertTrue(weekdays.isDue(today))
        assertFalse(weekdays.isDue(today.plusDays(1)))
        assertTrue(twiceAWeek.isDue(today.plusDays(1)))
    }

    @Test
    fun `weekdays survive a storage round trip`() {
        val encoded = HabitSchedule.encodeDays(weekdays.days)
        assertEquals("1,3,5", encoded)
        assertEquals(weekdays.days, HabitSchedule.decodeDays(encoded))
    }

    @Test
    fun `a corrupt weekday string decodes to what it can rather than crashing`() {
        assertEquals(setOf(DayOfWeek.MONDAY), HabitSchedule.decodeDays("1,x,0,9"))
        assertEquals(emptySet<DayOfWeek>(), HabitSchedule.decodeDays(null))
        assertEquals(emptySet<DayOfWeek>(), HabitSchedule.decodeDays(""))
    }

    @Test
    fun `an unknown stored kind falls back to daily`() {
        assertEquals(HabitSchedule.Kind.DAILY, HabitSchedule.kindOf(null))
        assertEquals(HabitSchedule.Kind.DAILY, HabitSchedule.kindOf("monthly"))
        assertEquals(HabitSchedule.Kind.DAYS, HabitSchedule.kindOf("days"))
    }

    // --- board ordering ---

    @Test
    fun `undone habits come first, then by name`() {
        val habits = listOf(
            Habit(1, "ក", 0, daily),
            Habit(2, "ខ", 0, daily),
            Habit(3, "គ", 0, daily),
        )
        val ordered = HabitBoard.order(habits, done = setOf(1L))
        assertEquals(listOf(2L, 3L, 1L), ordered.map { it.id })
    }

    @Test
    fun `archived habits are not shown`() {
        val habits = listOf(Habit(1, "ក", 0, daily), Habit(2, "ខ", 0, daily, isArchived = true))
        assertEquals(listOf(1L), HabitBoard.order(habits, emptySet()).map { it.id })
        assertEquals(listOf(1L), HabitBoard.dueToday(habits, today).map { it.id })
    }

    @Test
    fun `only habits due today count as due today`() {
        val habits = listOf(Habit(1, "ក", 0, daily), Habit(2, "ខ", 0, weekdays))
        // Thursday: the Mon/Wed/Fri habit is not due.
        val thursday = today.plusDays(1)
        assertEquals(listOf(1L), HabitBoard.dueToday(habits, thursday).map { it.id })
    }
}
