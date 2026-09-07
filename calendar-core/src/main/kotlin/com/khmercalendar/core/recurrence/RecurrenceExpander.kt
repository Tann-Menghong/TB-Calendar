package com.khmercalendar.core.recurrence

import java.time.LocalDate

/**
 * Turns a start date plus an [RecurrenceRule] into the dates it actually falls on.
 *
 * Occurrences are generated on demand for a window rather than materialised into the
 * database. A weekly event running for ten years is 520 rows that would have to be written,
 * migrated and kept in step with every edit; computing the handful visible in a month grid
 * costs nothing by comparison. Exceptions (dates the user deleted from the series) are
 * passed in and subtracted here.
 */
object RecurrenceExpander {

    /** Hard ceiling so a malformed rule cannot spin. */
    private const val MAX_OCCURRENCES = 10_000

    /**
     * Dates in `[windowStart, windowEnd]` on which the series starting at [start] occurs.
     *
     * @param exceptions dates removed from the series.
     */
    fun occurrences(
        start: LocalDate,
        rule: RecurrenceRule?,
        windowStart: LocalDate,
        windowEnd: LocalDate,
        exceptions: Set<LocalDate> = emptySet(),
    ): List<LocalDate> {
        if (windowEnd.isBefore(windowStart)) return emptyList()
        if (rule == null) {
            return if (!start.isBefore(windowStart) && !start.isAfter(windowEnd) && start !in exceptions) {
                listOf(start)
            } else {
                emptyList()
            }
        }

        val hardEnd = rule.until?.let { if (it.isBefore(windowEnd)) it else windowEnd } ?: windowEnd
        if (hardEnd.isBefore(start)) return emptyList()

        val out = ArrayList<LocalDate>()
        var emitted = 0

        fun offer(date: LocalDate): Boolean {
            // COUNT counts every occurrence of the series, including those before the window,
            // so the limit has to be tracked from the start date rather than from the window.
            if (rule.count != null && emitted >= rule.count) return false
            if (rule.until != null && date.isAfter(rule.until)) return false
            emitted++
            if (!date.isBefore(windowStart) && !date.isAfter(hardEnd) && date !in exceptions) {
                out += date
            }
            return true
        }

        when (rule.frequency) {
            Frequency.DAILY -> {
                var d = start
                var guard = 0
                while (!d.isAfter(hardEnd) && guard++ < MAX_OCCURRENCES) {
                    if (!offer(d)) break
                    d = d.plusDays(rule.interval.toLong())
                }
            }

            Frequency.WEEKLY -> {
                val days = rule.byDay.ifEmpty { setOf(start.dayOfWeek) }
                // Walk week blocks from the start's own week so INTERVAL counts weeks, not days.
                var weekStart = start.minusDays((start.dayOfWeek.value % 7).toLong()) // week begins Sunday
                var guard = 0
                outer@ while (!weekStart.isAfter(hardEnd) && guard++ < MAX_OCCURRENCES) {
                    for (i in 0..6) {
                        val d = weekStart.plusDays(i.toLong())
                        if (d.isBefore(start)) continue
                        if (d.isAfter(hardEnd)) break@outer
                        if (d.dayOfWeek !in days) continue
                        if (!offer(d)) break@outer
                    }
                    weekStart = weekStart.plusWeeks(rule.interval.toLong())
                }
            }

            Frequency.MONTHLY -> {
                var i = 0L
                var guard = 0
                while (guard++ < MAX_OCCURRENCES) {
                    val base = start.withDayOfMonth(1).plusMonths(i * rule.interval)
                    // An event on the 31st simply does not occur in a 30-day month. Clamping
                    // it to the 30th would silently invent a date the user never chose.
                    if (start.dayOfMonth <= base.lengthOfMonth()) {
                        val d = base.withDayOfMonth(start.dayOfMonth)
                        if (d.isAfter(hardEnd)) break
                        if (!d.isBefore(start) && !offer(d)) break
                    } else if (base.isAfter(hardEnd)) {
                        break
                    }
                    i++
                }
            }

            Frequency.YEARLY -> {
                var i = 0L
                var guard = 0
                while (guard++ < MAX_OCCURRENCES) {
                    val year = start.year + i * rule.interval
                    if (year > hardEnd.year) break
                    // 29 February only occurs in leap years, for the same reason as above.
                    val valid = start.monthValue != 2 || start.dayOfMonth != 29 ||
                        LocalDate.ofYearDay(year.toInt(), 1).isLeapYear
                    if (valid) {
                        val d = LocalDate.of(year.toInt(), start.monthValue, start.dayOfMonth)
                        if (d.isAfter(hardEnd)) break
                        if (!d.isBefore(start) && !offer(d)) break
                    }
                    i++
                }
            }
        }
        return out
    }

    /** The first occurrence on or after [from], or null if the series has ended. */
    fun nextOccurrence(
        start: LocalDate,
        rule: RecurrenceRule?,
        from: LocalDate,
        exceptions: Set<LocalDate> = emptySet(),
    ): LocalDate? {
        if (rule == null) return start.takeIf { !it.isBefore(from) && it !in exceptions }
        // Widen the search in steps rather than scanning to the year 2200 for a series that
        // ended last week.
        var horizon = 62L
        repeat(6) {
            val found = occurrences(start, rule, from, from.plusDays(horizon), exceptions).firstOrNull()
            if (found != null) return found
            horizon *= 6
        }
        return null
    }
}
