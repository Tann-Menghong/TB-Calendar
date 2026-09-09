package com.khmercalendar.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

/**
 * The year grid's arithmetic.
 *
 * Column alignment is the part that cannot be checked by looking: a mini-month whose days sit
 * one column out is still a plausible-looking calendar, and it is wrong only for users whose
 * week starts on a different day from the developer's. Twelve of them on one screen makes it
 * twelve times easier to ship and no easier to notice.
 */
class YearOverviewTest {

    private val today = LocalDate.of(2026, 9, 9)
    private val monday = DayOfWeek.MONDAY
    private val sunday = DayOfWeek.SUNDAY

    private fun build(
        year: Int = 2026,
        weekStart: DayOfWeek = monday,
        counts: Map<LocalDate, Int> = emptyMap(),
        holidays: Set<LocalDate> = emptySet(),
    ) = YearOverview.build(year, today, weekStart, counts, holidays)

    @Test
    fun `a year is twelve months in order`() {
        val grids = build()
        assertEquals(12, grids.size)
        assertEquals(YearMonth.of(2026, 1), grids.first().month)
        assertEquals(YearMonth.of(2026, 12), grids.last().month)
    }

    @Test
    fun `every row is seven wide`() {
        for (grid in build()) {
            for (week in grid.weeks) {
                assertEquals("${grid.month} has a ragged row", 7, week.size)
            }
        }
    }

    @Test
    fun `every day of every month appears exactly once`() {
        for (grid in build()) {
            val days = grid.weeks.flatten().filterNotNull().map { it.date }
            assertEquals(grid.month.lengthOfMonth(), days.size)
            assertEquals(days.size, days.distinct().size)
            assertEquals(grid.month.atDay(1), days.first())
            assertEquals(grid.month.atEndOfMonth(), days.last())
        }
    }

    @Test
    fun `the first day sits in the column its weekday belongs to`() {
        // 1 January 2026 is a Thursday: index 3 with a Monday start, 4 with a Sunday start.
        assertNull(build(weekStart = monday).first().weeks.first()[2])
        assertEquals(
            LocalDate.of(2026, 1, 1),
            build(weekStart = monday).first().weeks.first()[3]?.date,
        )
        assertEquals(
            LocalDate.of(2026, 1, 1),
            build(weekStart = sunday).first().weeks.first()[4]?.date,
        )
    }

    @Test
    fun `a month starting on the week's first day needs no leading padding`() {
        // 1 June 2026 is a Monday.
        val june = build(weekStart = monday)[5]
        assertEquals(LocalDate.of(2026, 6, 1), june.weeks.first()[0]?.date)
    }

    @Test
    fun `a leap year has 29 February`() {
        val february = build(year = 2028)[1]
        val days = february.weeks.flatten().filterNotNull()
        assertEquals(29, days.size)
        assertEquals(LocalDate.of(2028, 2, 29), days.last().date)
    }

    @Test
    fun `counts land on the right days and nowhere else`() {
        val busy = LocalDate.of(2026, 3, 15)
        val grids = build(counts = mapOf(busy to 4))
        val march = grids[2]

        assertEquals(4, march.weeks.flatten().filterNotNull().first { it.date == busy }.count)
        assertEquals(4, march.total)
        assertEquals(1, march.busyDays)
        // Nothing leaked into the neighbouring months.
        assertEquals(0, grids[1].total)
        assertEquals(0, grids[3].total)
    }

    @Test
    fun `busy days counts days rather than things on them`() {
        val grids = build(
            counts = mapOf(
                LocalDate.of(2026, 3, 2) to 5,
                LocalDate.of(2026, 3, 3) to 1,
            ),
        )
        assertEquals(6, grids[2].total)
        assertEquals(2, grids[2].busyDays)
    }

    @Test
    fun `today is marked once in the whole year`() {
        val marked = build().flatMap { it.weeks.flatten().filterNotNull() }.filter { it.isToday }
        assertEquals(1, marked.size)
        assertEquals(today, marked.single().date)
    }

    @Test
    fun `a year that is not this year marks no today`() {
        val marked = build(year = 2030).flatMap { it.weeks.flatten().filterNotNull() }
        assertTrue(marked.none { it.isToday })
    }

    @Test
    fun `holidays are marked on their own days`() {
        val newYear = LocalDate.of(2026, 4, 14)
        val grids = build(holidays = setOf(newYear))
        val april = grids[3].weeks.flatten().filterNotNull()
        assertTrue(april.first { it.date == newYear }.isHoliday)
        assertEquals(1, april.count { it.isHoliday })
    }

    // --- the summary ---

    @Test
    fun `the summary names the busiest month`() {
        val grids = build(
            counts = mapOf(
                LocalDate.of(2026, 2, 1) to 2,
                LocalDate.of(2026, 7, 1) to 9,
                LocalDate.of(2026, 7, 2) to 1,
            ),
        )
        val summary = YearOverview.summarise(grids, emptySet(), 2026)
        assertEquals(12, summary.total)
        assertEquals(YearMonth.of(2026, 7), summary.busiest)
    }

    @Test
    fun `an empty year has no busiest month rather than January`() {
        val summary = YearOverview.summarise(build(), emptySet(), 2026)
        assertEquals(0, summary.total)
        assertNull(summary.busiest)
    }

    @Test
    fun `holidays are counted for the year asked about`() {
        val holidays = setOf(
            LocalDate.of(2026, 1, 1),
            LocalDate.of(2026, 4, 14),
            LocalDate.of(2027, 1, 1),
        )
        assertEquals(2, YearOverview.summarise(build(), holidays, 2026).holidays)
    }

    @Test
    fun `the supported range matches what the lunar engine can answer`() {
        assertTrue(YearOverview.isSupported(1900))
        assertTrue(YearOverview.isSupported(2199))
        assertFalse(YearOverview.isSupported(1899))
        assertFalse(YearOverview.isSupported(2200))
    }
}
