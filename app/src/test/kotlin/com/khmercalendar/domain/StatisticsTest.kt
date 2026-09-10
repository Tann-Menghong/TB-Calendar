package com.khmercalendar.domain

import com.khmercalendar.core.work.WorkBlock
import com.khmercalendar.core.work.WorkDay
import com.khmercalendar.core.work.WorkSchedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * The statistics arithmetic.
 *
 * Every case here is one nobody can see on a phone without waiting for a calendar to turn
 * over: a month that has not finished, a habit created halfway through the window, a week
 * compared against the same slice of the previous one, a leap year, a session that crossed
 * midnight. They are the reason the arithmetic is a pure object rather than code inside a
 * view model.
 */
class StatisticsTest {

    private val zone: ZoneId = ZoneId.of("Asia/Phnom_Penh")
    private val monday: DayOfWeek = DayOfWeek.MONDAY

    // --- ranges ------------------------------------------------------------------------

    @Test
    fun `a month still running is elapsed only up to today`() {
        val today = LocalDate.of(2026, 3, 10)
        val range = Statistics.rangeOf(StatsPeriod.MONTH, today, today, monday)

        assertEquals(LocalDate.of(2026, 3, 1), range.start)
        assertEquals(LocalDate.of(2026, 3, 31), range.end)
        assertEquals(31, range.totalDays)
        assertEquals(10, range.elapsedDays)
        assertTrue(range.isPartial)
    }

    @Test
    fun `a finished month is not partial and counts every day`() {
        val today = LocalDate.of(2026, 3, 10)
        val february = Statistics.rangeOf(StatsPeriod.MONTH, LocalDate.of(2026, 2, 4), today, monday)

        assertEquals(28, february.totalDays)
        assertEquals(28, february.elapsedDays)
        assertFalse(february.isPartial)
    }

    @Test
    fun `a leap February has twenty-nine days`() {
        val today = LocalDate.of(2024, 6, 1)
        val february = Statistics.rangeOf(StatsPeriod.MONTH, LocalDate.of(2024, 2, 1), today, monday)

        assertEquals(29, february.totalDays)
        assertEquals(29, Statistics.buckets(february).size)
    }

    @Test
    fun `a leap year has three hundred and sixty-six days`() {
        val today = LocalDate.of(2024, 12, 31)
        val year = Statistics.rangeOf(StatsPeriod.YEAR, today, today, monday)

        assertEquals(366, year.totalDays)
        assertEquals(12, Statistics.buckets(year).size)
    }

    @Test
    fun `a period entirely ahead counts nothing at all`() {
        val today = LocalDate.of(2026, 3, 10)
        val nextMonth = Statistics.rangeOf(StatsPeriod.MONTH, LocalDate.of(2026, 4, 2), today, monday)

        assertTrue(nextMonth.isFuture)
        assertEquals(0, nextMonth.elapsedDays)
        assertFalse(nextMonth.countsIn(LocalDate.of(2026, 4, 2)))

        val input = StatsInput(
            completedTasks = listOf(task(LocalDate.of(2026, 4, 2))),
            zone = zone,
        )
        assertEquals(0, Statistics.summarise(input, nextMonth).tasksCompleted)
    }

    @Test
    fun `the week honours the user's first day`() {
        val today = LocalDate.of(2026, 1, 15)  // a Thursday

        val fromMonday = Statistics.rangeOf(StatsPeriod.WEEK, today, today, DayOfWeek.MONDAY)
        assertEquals(LocalDate.of(2026, 1, 12), fromMonday.start)

        val fromSunday = Statistics.rangeOf(StatsPeriod.WEEK, today, today, DayOfWeek.SUNDAY)
        assertEquals(LocalDate.of(2026, 1, 11), fromSunday.start)
    }

    // --- the comparison slice ----------------------------------------------------------

    @Test
    fun `this week is compared with the same part of last week`() {
        val today = LocalDate.of(2026, 1, 13)  // Tuesday: two days elapsed
        val range = Statistics.rangeOf(StatsPeriod.WEEK, today, today, monday)
        val comparison = Statistics.comparisonRange(range, monday)

        assertNotNull(comparison)
        // Last Monday and Tuesday - not the whole of last week, which would make every week
        // open in failure and close in triumph regardless of what anybody did.
        assertEquals(LocalDate.of(2026, 1, 5), comparison!!.start)
        assertEquals(LocalDate.of(2026, 1, 6), comparison.end)
        assertEquals(2, comparison.elapsedDays)
    }

    @Test
    fun `a finished period is compared with a whole previous period`() {
        val today = LocalDate.of(2026, 3, 20)
        val february = Statistics.rangeOf(StatsPeriod.MONTH, LocalDate.of(2026, 2, 10), today, monday)
        val comparison = Statistics.comparisonRange(february, monday)

        assertNotNull(comparison)
        assertEquals(LocalDate.of(2026, 1, 1), comparison!!.start)
        assertEquals(LocalDate.of(2026, 1, 28), comparison.end)
    }

    @Test
    fun `the comparison never runs past the end of a shorter previous month`() {
        // The thirty-first of March against February, which has no thirty-first.
        val today = LocalDate.of(2026, 3, 31)
        val march = Statistics.rangeOf(StatsPeriod.MONTH, today, today, monday)
        val comparison = Statistics.comparisonRange(march, monday)

        assertNotNull(comparison)
        assertEquals(LocalDate.of(2026, 2, 1), comparison!!.start)
        assertEquals(LocalDate.of(2026, 2, 28), comparison.end)
    }

    @Test
    fun `a future period has nothing to compare against`() {
        val today = LocalDate.of(2026, 3, 10)
        val ahead = Statistics.rangeOf(StatsPeriod.MONTH, LocalDate.of(2026, 5, 1), today, monday)

        assertNull(Statistics.comparisonRange(ahead, monday))
    }

    // --- buckets -----------------------------------------------------------------------

    @Test
    fun `days that have not happened are absent rather than zero`() {
        val today = LocalDate.of(2026, 1, 14)  // Wednesday
        val range = Statistics.rangeOf(StatsPeriod.WEEK, today, today, monday)
        val buckets = Statistics.buckets(range)

        assertEquals(7, buckets.size)
        assertFalse(buckets[2].isFuture)   // Wednesday, today
        assertTrue(buckets[2].isToday)
        assertTrue(buckets[3].isFuture)    // Thursday

        val series = Statistics.series(StatsInput(zone = zone), buckets, StatsMetric.TASKS)
        assertEquals(0, series[2])         // happened, nothing done
        assertNull(series[3])              // has not happened
    }

    @Test
    fun `a day has a single column, which the screen treats as no chart`() {
        val today = LocalDate.of(2026, 1, 14)
        val range = Statistics.rangeOf(StatsPeriod.DAY, today, today, monday)

        assertEquals(1, Statistics.buckets(range).size)
    }

    // --- tasks -------------------------------------------------------------------------

    @Test
    fun `a task counts on the day it was ticked, not the day it was due`() {
        val today = LocalDate.of(2026, 1, 15)
        val week = Statistics.rangeOf(StatsPeriod.WEEK, today, today, monday)   // 12th-18th

        val input = StatsInput(
            completedTasks = listOf(
                task(LocalDate.of(2026, 1, 14)),   // ticked inside this week
                task(LocalDate.of(2026, 1, 5)),    // ticked last week
            ),
            zone = zone,
        )

        assertEquals(1, Statistics.summarise(input, week).tasksCompleted)
    }

    @Test
    fun `a task ticked later today still counts today`() {
        val today = LocalDate.of(2026, 1, 15)
        val day = Statistics.rangeOf(StatsPeriod.DAY, today, today, monday)
        val input = StatsInput(completedTasks = listOf(task(today)), zone = zone)

        assertEquals(1, Statistics.summarise(input, day).tasksCompleted)
    }

    // --- focus -------------------------------------------------------------------------

    @Test
    fun `focus totals exclude breaks and running sessions`() {
        val today = LocalDate.of(2026, 1, 15)
        val day = Statistics.rangeOf(StatsPeriod.DAY, today, today, monday)
        val at = today.atTime(9, 0)

        val input = StatsInput(
            focusSessions = listOf(
                session(at, planned = 25, ranMinutes = 25),
                session(at.plusHours(1), planned = 5, ranMinutes = 5, kind = FocusKind.SHORT_BREAK),
                session(at.plusHours(2), planned = 25, ranMinutes = null),   // still running
            ),
            zone = zone,
        )

        val summary = Statistics.summarise(input, day)
        assertEquals(25, summary.focusMinutes)
        assertEquals(1, summary.focusSessions)
    }

    @Test
    fun `a session that crossed midnight belongs to the evening it started in`() {
        val evening = LocalDate.of(2026, 1, 14)
        val today = LocalDate.of(2026, 1, 15)
        val input = StatsInput(
            focusSessions = listOf(
                session(evening.atTime(23, 45), planned = 30, ranMinutes = 30),
            ),
            zone = zone,
        )

        val theFourteenth = Statistics.rangeOf(StatsPeriod.DAY, evening, today, monday)
        val theFifteenth = Statistics.rangeOf(StatsPeriod.DAY, today, today, monday)

        assertEquals(30, Statistics.summarise(input, theFourteenth).focusMinutes)
        assertEquals(0, Statistics.summarise(input, theFifteenth).focusMinutes)
    }

    // --- habits ------------------------------------------------------------------------

    @Test
    fun `a habit owes nothing for the days before it existed`() {
        val today = LocalDate.of(2026, 1, 31)
        val month = Statistics.rangeOf(StatsPeriod.MONTH, today, today, monday)

        // Created on the twenty-ninth, kept on all three days since.
        val habit = habit(id = 1, created = LocalDate.of(2026, 1, 29))
        val input = StatsInput(
            habits = listOf(habit),
            habitTicks = mapOf(
                1L to setOf(
                    LocalDate.of(2026, 1, 29),
                    LocalDate.of(2026, 1, 30),
                    LocalDate.of(2026, 1, 31),
                ),
            ),
            zone = zone,
        )

        val summary = Statistics.summarise(input, month)
        assertEquals(3, summary.habitTicks)
        assertEquals(3, summary.habitExpected)
        // Not 10%, which is what dividing by the whole month would have said about somebody
        // who has not missed a day.
        assertEquals(100, summary.habitRate)
    }

    @Test
    fun `an archived habit owes nothing after it was put away`() {
        val today = LocalDate.of(2026, 1, 31)
        val month = Statistics.rangeOf(StatsPeriod.MONTH, today, today, monday)

        val habit = habit(
            id = 1,
            created = LocalDate.of(2026, 1, 1),
            archived = LocalDate.of(2026, 1, 10),
        )
        val input = StatsInput(
            habits = listOf(habit),
            habitTicks = mapOf(1L to (1..10).map { LocalDate.of(2026, 1, it) }.toSet()),
            zone = zone,
        )

        val summary = Statistics.summarise(input, month)
        assertEquals(10, summary.habitExpected)
        assertEquals(100, summary.habitRate)
    }

    @Test
    fun `a weekday habit is measured against its own weekdays`() {
        val today = LocalDate.of(2026, 1, 18)   // Sunday, a full Monday-start week
        val week = Statistics.rangeOf(StatsPeriod.WEEK, today, today, monday)

        val habit = habit(
            id = 1,
            schedule = HabitSchedule(
                kind = HabitSchedule.Kind.DAYS,
                days = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY),
            ),
        )
        val input = StatsInput(
            habits = listOf(habit),
            habitTicks = mapOf(
                1L to setOf(
                    LocalDate.of(2026, 1, 12),  // Monday
                    LocalDate.of(2026, 1, 14),  // Wednesday
                    LocalDate.of(2026, 1, 16),  // Friday
                ),
            ),
            zone = zone,
        )

        val summary = Statistics.summarise(input, week)
        assertEquals(3, summary.habitExpected)
        // Doing exactly what was promised reads as 100%, not as 43%.
        assertEquals(100, summary.habitRate)
    }

    @Test
    fun `a twice-a-week habit is measured against its target, not against seven days`() {
        val today = LocalDate.of(2026, 1, 18)
        val week = Statistics.rangeOf(StatsPeriod.WEEK, today, today, monday)

        val habit = habit(
            id = 1,
            schedule = HabitSchedule(kind = HabitSchedule.Kind.TIMES_PER_WEEK, target = 2),
        )
        val input = StatsInput(
            habits = listOf(habit),
            habitTicks = mapOf(
                1L to setOf(LocalDate.of(2026, 1, 13), LocalDate.of(2026, 1, 16)),
            ),
            zone = zone,
        )

        val summary = Statistics.summarise(input, week)
        assertEquals(2, summary.habitExpected)
        assertEquals(100, summary.habitRate)
    }

    @Test
    fun `nothing promised is no rate rather than a zero`() {
        val today = LocalDate.of(2026, 1, 15)
        val day = Statistics.rangeOf(StatsPeriod.DAY, today, today, monday)

        assertNull(Statistics.summarise(StatsInput(zone = zone), day).habitRate)
    }

    // --- events and time ---------------------------------------------------------------

    @Test
    fun `an all-day entry is counted but never timed`() {
        val today = LocalDate.of(2026, 1, 15)
        val day = Statistics.rangeOf(StatsPeriod.DAY, today, today, monday)

        val input = StatsInput(
            occurrences = listOf(
                occurrence(id = 1, date = today, start = today.atStartOfDay(), end = today.plusDays(1).atStartOfDay(), allDay = true),
                occurrence(id = 2, date = today, start = today.atTime(9, 0), end = today.atTime(10, 30)),
            ),
            zone = zone,
        )

        val summary = Statistics.summarise(input, day)
        assertEquals(2, summary.eventCount)
        assertEquals(1, summary.allDayEventCount)
        // Ninety minutes, not ninety plus a day.
        assertEquals(90, summary.timedEventMinutes)
    }

    @Test
    fun `a multi-day event is counted once, not once per day it covers`() {
        val today = LocalDate.of(2026, 1, 16)
        val week = Statistics.rangeOf(StatsPeriod.WEEK, today, today, monday)

        val start = LocalDate.of(2026, 1, 14).atTime(22, 0)
        val end = LocalDate.of(2026, 1, 15).atTime(2, 0)
        val input = StatsInput(
            // The repository lists it under both days it covers, with the same start.
            occurrences = listOf(
                occurrence(id = 7, date = LocalDate.of(2026, 1, 14), start = start, end = end),
                occurrence(id = 7, date = LocalDate.of(2026, 1, 15), start = start, end = end),
            ),
            zone = zone,
        )

        val summary = Statistics.summarise(input, week)
        assertEquals(1, summary.eventCount)
        assertEquals(240, summary.timedEventMinutes)
    }

    @Test
    fun `an event running past the end of the window contributes only the part inside it`() {
        val today = LocalDate.of(2026, 1, 15)
        val day = Statistics.rangeOf(StatsPeriod.DAY, today, today, monday)

        val input = StatsInput(
            occurrences = listOf(
                occurrence(
                    id = 1,
                    date = today,
                    start = today.atTime(23, 0),
                    end = today.plusDays(1).atTime(1, 0),
                ),
            ),
            zone = zone,
        )

        assertEquals(60, Statistics.summarise(input, day).timedEventMinutes)
    }

    @Test
    fun `a task is not counted as time spent`() {
        val today = LocalDate.of(2026, 1, 15)
        val day = Statistics.rangeOf(StatsPeriod.DAY, today, today, monday)

        val input = StatsInput(
            occurrences = listOf(
                occurrence(id = 1, date = today, start = today.atTime(9, 0), end = today.atTime(10, 0), isTask = true),
            ),
            zone = zone,
        )

        val summary = Statistics.summarise(input, day)
        assertEquals(0, summary.eventCount)
        assertEquals(0, summary.timedEventMinutes)
    }

    @Test
    fun `entries without a category are grouped under one heading`() {
        val today = LocalDate.of(2026, 1, 15)
        val day = Statistics.rangeOf(StatsPeriod.DAY, today, today, monday)

        val input = StatsInput(
            occurrences = listOf(
                occurrence(id = 1, date = today, start = today.atTime(9, 0), end = today.atTime(10, 0)),
                occurrence(id = 2, date = today, start = today.atTime(11, 0), end = today.atTime(12, 0)),
            ),
            zone = zone,
        )

        val slices = Statistics.summarise(input, day).categories
        assertEquals(1, slices.size)
        assertEquals(Statistics.UNCATEGORISED_KM, slices.single().name)
        assertEquals(120, slices.single().minutes)
    }

    // --- work --------------------------------------------------------------------------

    @Test
    fun `planned work counts only the days that have happened`() {
        val today = LocalDate.of(2026, 1, 14)   // Wednesday
        val week = Statistics.rangeOf(StatsPeriod.WEEK, today, today, monday)

        val fourHourDay = WorkDay(listOf(WorkBlock(LocalTime.of(8, 0), LocalTime.of(12, 0), "ព្រឹក")))
        val schedule = WorkSchedule(
            days = DayOfWeek.entries.associateWith { fourHourDay },
            enabled = true,
        )

        // Monday, Tuesday, Wednesday - not the whole week.
        assertEquals(3 * 240, Statistics.plannedWorkMinutes(schedule, week.start, week.elapsedEnd))
    }

    @Test
    fun `a disabled work schedule plans nothing`() {
        val today = LocalDate.of(2026, 1, 14)
        val off = WorkSchedule.DEFAULT.copy(enabled = false)

        assertEquals(0, Statistics.plannedWorkMinutes(off, today, today))
    }

    // --- formatting --------------------------------------------------------------------

    @Test
    fun `minutes read as hours only once there is an hour`() {
        assertEquals("45 នាទី", Statistics.formatMinutes(45))
        assertEquals("1 ម៉ោង", Statistics.formatMinutes(60))
        assertEquals("2 ម៉ោង 15 នាទី", Statistics.formatMinutes(135))
        assertEquals("0 នាទី", Statistics.formatMinutes(0))
        assertEquals("0 នាទី", Statistics.formatMinutes(-5))
    }

    @Test
    fun `a week spanning two months spells both of them out`() {
        val today = LocalDate.of(2026, 2, 1)
        val week = Statistics.rangeOf(StatsPeriod.WEEK, LocalDate.of(2026, 1, 28), today, monday)

        // 26 January - 1 February: unreadable as "26-1 មករា".
        assertTrue(Statistics.title(week).contains("មករា"))
        assertTrue(Statistics.title(week).contains("កុម្ភៈ"))
    }

    @Test
    fun `a week inside one month is written once`() {
        val today = LocalDate.of(2026, 1, 15)
        val week = Statistics.rangeOf(StatsPeriod.WEEK, today, today, monday)

        assertEquals("12–18 មករា 2026", Statistics.title(week))
    }

    // --- helpers -----------------------------------------------------------------------

    private fun task(date: LocalDate) = CompletedTask(
        eventId = date.toEpochDay(),
        date = date,
        priority = TaskPriority.NORMAL,
        categoryId = null,
    )

    private fun session(
        start: LocalDateTime,
        planned: Int,
        ranMinutes: Int?,
        kind: FocusKind = FocusKind.FOCUS,
    ): FocusSession {
        val startedAt = start.atZone(zone).toInstant().toEpochMilli()
        return FocusSession(
            id = startedAt,
            kind = kind,
            startedAtMillis = startedAt,
            plannedMinutes = planned,
            endedAtMillis = ranMinutes?.let { startedAt + it * 60_000L },
            eventId = null,
        )
    }

    private fun habit(
        id: Long,
        schedule: HabitSchedule = HabitSchedule.DEFAULT,
        created: LocalDate = LocalDate.MIN,
        archived: LocalDate? = null,
    ) = Habit(
        id = id,
        name = "habit $id",
        colorArgb = 0,
        schedule = schedule,
        isArchived = archived != null,
        createdAt = created,
        archivedAt = archived,
    )

    private fun occurrence(
        id: Long,
        date: LocalDate,
        start: LocalDateTime,
        end: LocalDateTime,
        allDay: Boolean = false,
        isTask: Boolean = false,
        categoryId: Long? = null,
        categoryName: String? = null,
    ) = EventOccurrence(
        eventId = id,
        title = "event $id",
        description = null,
        location = null,
        occurrenceDate = date,
        start = start,
        end = end,
        allDay = allDay,
        colorArgb = 0,
        categoryId = categoryId,
        categoryName = categoryName,
        isTask = isTask,
        isCompleted = false,
        isRecurring = false,
    )
}
