package com.khmercalendar.core.recurrence

import com.khmercalendar.core.khmer.CalendarWeek
import java.time.DayOfWeek
import java.time.LocalDate

enum class Frequency { DAILY, WEEKLY, MONTHLY, YEARLY }

/**
 * The subset of RFC 5545 `RRULE` this app writes and reads.
 *
 * Deliberately not the whole specification. `FREQ`, `INTERVAL`, `BYDAY`, `COUNT` and `UNTIL`
 * cover every repeat a person actually sets in a calendar app, and they round-trip cleanly
 * through the `.ics` export. Anything richer that arrives on import is preserved verbatim in
 * the event's raw rule field and expanded as best it can be rather than being dropped.
 */
data class RecurrenceRule(
    val frequency: Frequency,
    val interval: Int = 1,
    /** Only meaningful for [Frequency.WEEKLY]; empty means "the weekday of the start date". */
    val byDay: Set<DayOfWeek> = emptySet(),
    /** Stop after this many occurrences. Mutually exclusive with [until]. */
    val count: Int? = null,
    /** Stop on or before this date. Mutually exclusive with [count]. */
    val until: LocalDate? = null,
    /**
     * RFC 5545 `WKST` - which day the week starts on.
     *
     * Only observable when [interval] is greater than one on a [Frequency.WEEKLY] rule, but
     * there it decides which occurrences fall in which counted week: "every two weeks on
     * Monday and Sunday" produces different dates under a Monday week than a Sunday one.
     * The specification defaults it to Monday, and so does this app's own preference.
     */
    val weekStart: DayOfWeek = CalendarWeek.DEFAULT_START,
) {
    init {
        require(interval >= 1) { "interval must be at least 1" }
        require(count == null || until == null) { "COUNT and UNTIL cannot both be set" }
        require(count == null || count >= 1) { "count must be at least 1" }
    }

    fun toRRule(): String = buildString {
        append("FREQ=").append(frequency.name)
        if (interval != 1) append(";INTERVAL=").append(interval)
        if (byDay.isNotEmpty()) {
            append(";BYDAY=")
            append(DayOfWeek.entries.filter { it in byDay }.joinToString(",") { ICAL_DAYS[it]!! })
        }
        // WKST is only emitted when it can change the expansion, keeping the common rule
        // short and identical to what other calendar apps write.
        if (frequency == Frequency.WEEKLY && interval > 1 && weekStart != DayOfWeek.MONDAY) {
            append(";WKST=").append(ICAL_DAYS[weekStart])
        }
        count?.let { append(";COUNT=").append(it) }
        until?.let {
            append(";UNTIL=")
            append("%04d%02d%02d".format(it.year, it.monthValue, it.dayOfMonth))
        }
    }

    companion object {
        private val ICAL_DAYS: Map<DayOfWeek, String> = mapOf(
            DayOfWeek.MONDAY to "MO", DayOfWeek.TUESDAY to "TU", DayOfWeek.WEDNESDAY to "WE",
            DayOfWeek.THURSDAY to "TH", DayOfWeek.FRIDAY to "FR", DayOfWeek.SATURDAY to "SA",
            DayOfWeek.SUNDAY to "SU",
        )
        private val DAYS_BY_ICAL: Map<String, DayOfWeek> = ICAL_DAYS.entries.associate { (k, v) -> v to k }

        /** Parses an `RRULE` value, returning null when it names nothing this app can expand. */
        fun parse(rrule: String?): RecurrenceRule? {
            if (rrule.isNullOrBlank()) return null
            val parts = rrule.removePrefix("RRULE:").split(';')
                .mapNotNull { p ->
                    val i = p.indexOf('=')
                    if (i <= 0) null else p.substring(0, i).uppercase() to p.substring(i + 1)
                }.toMap()

            val freq = parts["FREQ"]?.let { f -> Frequency.entries.firstOrNull { it.name == f.uppercase() } }
                ?: return null

            val until = parts["UNTIL"]?.let { raw ->
                val d = raw.takeWhile { it.isDigit() }
                if (d.length < 8) null
                else runCatching {
                    LocalDate.of(d.substring(0, 4).toInt(), d.substring(4, 6).toInt(), d.substring(6, 8).toInt())
                }.getOrNull()
            }
            val count = parts["COUNT"]?.toIntOrNull()?.takeIf { it >= 1 }

            return RecurrenceRule(
                frequency = freq,
                interval = parts["INTERVAL"]?.toIntOrNull()?.takeIf { it >= 1 } ?: 1,
                byDay = parts["BYDAY"].orEmpty().split(',')
                    // Strip any ordinal prefix such as the 2 in "2FR"; the expander does not
                    // implement nth-weekday, so the plain weekday is the closest honest reading.
                    .mapNotNull { DAYS_BY_ICAL[it.trim().takeLast(2).uppercase()] }
                    .toSet(),
                // UNTIL wins if a malformed rule carries both, matching how most clients behave.
                count = if (until != null) null else count,
                until = until,
                weekStart = parts["WKST"]?.let { DAYS_BY_ICAL[it.trim().uppercase()] }
                    ?: DayOfWeek.MONDAY,
            )
        }
    }
}
