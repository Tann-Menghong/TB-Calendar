package com.khmercalendar.core.recurrence

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * `WKST` - which day begins a counted week.
 *
 * Invisible for a plain weekly event, and decisive for a fortnightly one: it decides which
 * side of a week boundary each occurrence falls on. The expander used to hard-code Sunday
 * while the rest of the app read the user's preference, which is the bug these cover.
 */
class WeekStartRecurrenceTest {

    private val exceptions = emptySet<LocalDate>()

    private fun expand(rule: RecurrenceRule, start: LocalDate, days: Long): List<LocalDate> =
        RecurrenceExpander.occurrences(
            start = start,
            rule = rule,
            windowStart = start,
            windowEnd = start.plusDays(days),
            exceptions = exceptions,
        )

    @Test
    fun `weekly with interval one is unaffected by the week start`() {
        val start = LocalDate.of(2026, 3, 4) // A Wednesday.
        val monday = expand(
            RecurrenceRule(Frequency.WEEKLY, interval = 1, weekStart = DayOfWeek.MONDAY),
            start, 28,
        )
        val sunday = expand(
            RecurrenceRule(Frequency.WEEKLY, interval = 1, weekStart = DayOfWeek.SUNDAY),
            start, 28,
        )
        assertEquals(monday, sunday)
        assertTrue(monday.all { it.dayOfWeek == DayOfWeek.WEDNESDAY })
    }

    @Test
    fun `fortnightly across a weekend boundary depends on the week start`() {
        // Starting Saturday, repeating every two weeks on Saturday and Sunday. Under a Monday
        // week the pair sits inside one week; under a Sunday week the Sunday belongs to the
        // next one, so it falls in a skipped week and drops out.
        val start = LocalDate.of(2026, 3, 7) // Saturday.
        val byDay = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)

        val mondayWeek = expand(
            RecurrenceRule(Frequency.WEEKLY, interval = 2, byDay = byDay, weekStart = DayOfWeek.MONDAY),
            start, 21,
        )
        val sundayWeek = expand(
            RecurrenceRule(Frequency.WEEKLY, interval = 2, byDay = byDay, weekStart = DayOfWeek.SUNDAY),
            start, 21,
        )

        assertTrue(
            "A Monday week keeps 8 March with 7 March",
            LocalDate.of(2026, 3, 8) in mondayWeek,
        )
        assertTrue(
            "A Sunday week puts 8 March in the skipped week",
            LocalDate.of(2026, 3, 8) !in sundayWeek,
        )
    }

    @Test
    fun `WKST round-trips through the RRULE only when it matters`() {
        val plain = RecurrenceRule(Frequency.WEEKLY, interval = 1, weekStart = DayOfWeek.SUNDAY)
        assertTrue("WKST is noise on a weekly rule", "WKST" !in plain.toRRule())

        val fortnightly = RecurrenceRule(
            Frequency.WEEKLY,
            interval = 2,
            byDay = setOf(DayOfWeek.SATURDAY),
            weekStart = DayOfWeek.SUNDAY,
        )
        val rrule = fortnightly.toRRule()
        assertTrue(rrule, "WKST=SU" in rrule)
        assertEquals(DayOfWeek.SUNDAY, RecurrenceRule.parse(rrule)?.weekStart)
    }

    @Test
    fun `a rule with no WKST parses as Monday, per RFC 5545`() {
        val parsed = RecurrenceRule.parse("FREQ=WEEKLY;INTERVAL=2;BYDAY=MO,WE")
        assertEquals(DayOfWeek.MONDAY, parsed?.weekStart)
    }
}
