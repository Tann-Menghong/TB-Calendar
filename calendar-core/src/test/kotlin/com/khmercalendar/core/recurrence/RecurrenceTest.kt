package com.khmercalendar.core.recurrence

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class RecurrenceTest {

    private fun d(y: Int, m: Int, day: Int) = LocalDate.of(y, m, day)

    @Test
    fun `rrule round trips`() {
        val rule = RecurrenceRule(
            Frequency.WEEKLY,
            interval = 2,
            byDay = setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY),
            count = 10,
        )
        val text = rule.toRRule()
        assertEquals("FREQ=WEEKLY;INTERVAL=2;BYDAY=MO,TH;COUNT=10", text)
        assertEquals(rule, RecurrenceRule.parse(text))
    }

    @Test
    fun `until round trips and wins over count`() {
        val rule = RecurrenceRule(Frequency.DAILY, until = d(2026, 12, 31))
        assertEquals("FREQ=DAILY;UNTIL=20261231", rule.toRRule())
        val parsed = RecurrenceRule.parse("FREQ=DAILY;COUNT=5;UNTIL=20261231T000000Z")
        assertEquals(d(2026, 12, 31), parsed?.until)
        assertNull(parsed?.count)
    }

    @Test
    fun `unknown or empty rules parse to null`() {
        assertNull(RecurrenceRule.parse(null))
        assertNull(RecurrenceRule.parse(""))
        assertNull(RecurrenceRule.parse("INTERVAL=2"))
        assertNull(RecurrenceRule.parse("FREQ=HOURLY"))
    }

    @Test
    fun `a non-recurring event appears once, and only inside the window`() {
        val start = d(2026, 3, 10)
        assertEquals(listOf(start), RecurrenceExpander.occurrences(start, null, d(2026, 3, 1), d(2026, 3, 31)))
        assertTrue(RecurrenceExpander.occurrences(start, null, d(2026, 4, 1), d(2026, 4, 30)).isEmpty())
    }

    @Test
    fun `daily with interval`() {
        val out = RecurrenceExpander.occurrences(
            d(2026, 1, 1), RecurrenceRule(Frequency.DAILY, interval = 3),
            d(2026, 1, 1), d(2026, 1, 10),
        )
        assertEquals(listOf(d(2026, 1, 1), d(2026, 1, 4), d(2026, 1, 7), d(2026, 1, 10)), out)
    }

    @Test
    fun `weekly on named days`() {
        // 1 January 2026 is a Thursday.
        val out = RecurrenceExpander.occurrences(
            d(2026, 1, 1),
            RecurrenceRule(Frequency.WEEKLY, byDay = setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY)),
            d(2026, 1, 1), d(2026, 1, 14),
        )
        assertEquals(
            listOf(d(2026, 1, 2), d(2026, 1, 5), d(2026, 1, 9), d(2026, 1, 12)),
            out,
        )
    }

    @Test
    fun `weekly interval counts weeks not days`() {
        val out = RecurrenceExpander.occurrences(
            d(2026, 1, 5), // Monday
            RecurrenceRule(Frequency.WEEKLY, interval = 2, byDay = setOf(DayOfWeek.MONDAY)),
            d(2026, 1, 1), d(2026, 2, 28),
        )
        assertEquals(listOf(d(2026, 1, 5), d(2026, 1, 19), d(2026, 2, 2), d(2026, 2, 16)), out)
    }

    @Test
    fun `count limits the series even when the window starts later`() {
        val rule = RecurrenceRule(Frequency.DAILY, count = 3)
        // Occurrences are 1, 2 and 3 January; a window over the rest of the month sees none.
        assertTrue(RecurrenceExpander.occurrences(d(2026, 1, 1), rule, d(2026, 1, 5), d(2026, 1, 31)).isEmpty())
        assertEquals(3, RecurrenceExpander.occurrences(d(2026, 1, 1), rule, d(2026, 1, 1), d(2026, 1, 31)).size)
    }

    @Test
    fun `monthly skips months that are too short rather than clamping`() {
        val out = RecurrenceExpander.occurrences(
            d(2026, 1, 31), RecurrenceRule(Frequency.MONTHLY),
            d(2026, 1, 1), d(2026, 5, 31),
        )
        // February, April and June have no 31st; the event simply does not occur.
        assertEquals(listOf(d(2026, 1, 31), d(2026, 3, 31), d(2026, 5, 31)), out)
    }

    @Test
    fun `yearly on 29 February only occurs in leap years`() {
        val out = RecurrenceExpander.occurrences(
            d(2024, 2, 29), RecurrenceRule(Frequency.YEARLY),
            d(2024, 1, 1), d(2032, 12, 31),
        )
        assertEquals(listOf(d(2024, 2, 29), d(2028, 2, 29), d(2032, 2, 29)), out)
    }

    @Test
    fun `exceptions are removed from the series`() {
        val out = RecurrenceExpander.occurrences(
            d(2026, 1, 1), RecurrenceRule(Frequency.DAILY),
            d(2026, 1, 1), d(2026, 1, 5),
            exceptions = setOf(d(2026, 1, 3)),
        )
        assertEquals(listOf(d(2026, 1, 1), d(2026, 1, 2), d(2026, 1, 4), d(2026, 1, 5)), out)
    }

    @Test
    fun `until stops the series`() {
        val out = RecurrenceExpander.occurrences(
            d(2026, 1, 1), RecurrenceRule(Frequency.DAILY, until = d(2026, 1, 3)),
            d(2026, 1, 1), d(2026, 1, 31),
        )
        assertEquals(listOf(d(2026, 1, 1), d(2026, 1, 2), d(2026, 1, 3)), out)
    }

    @Test
    fun `next occurrence finds a distant one and gives up on a finished series`() {
        assertEquals(
            d(2027, 6, 1),
            RecurrenceExpander.nextOccurrence(d(2020, 6, 1), RecurrenceRule(Frequency.YEARLY), d(2026, 7, 1)),
        )
        assertNull(
            RecurrenceExpander.nextOccurrence(
                d(2020, 1, 1), RecurrenceRule(Frequency.DAILY, until = d(2020, 1, 5)), d(2026, 1, 1),
            ),
        )
    }
}
