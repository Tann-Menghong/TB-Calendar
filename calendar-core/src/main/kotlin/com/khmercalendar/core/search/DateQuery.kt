package com.khmercalendar.core.search

import com.khmercalendar.core.khmer.KhmerNumerals
import com.khmercalendar.core.khmer.KhmerTerms
import java.time.DateTimeException
import java.time.LocalDate

/**
 * Reading a date out of whatever the user typed into search.
 *
 * ## Why search should understand a date at all
 *
 * "Search" in a calendar means two different questions, and only one of them was answered.
 * "ប្រជុំ" asks *what did I call it*; "15/1" asks *take me to that day*, and the second is the
 * one you use when you know exactly where you are going. Typing a date used to return nothing
 * at all, which reads as an empty calendar rather than as a search that does not parse dates.
 *
 * ## What is accepted
 *
 * Numeric, in the three orders the app itself can display - `15/1/2027`, `2027-01-15`,
 * `1/15/2027` - separated by `/`, `-` or `.`; and written, `15 មករា 2027`, in either order.
 * Khmer numerals throughout, because the app writes dates in them by default and a user
 * copying what they see must get a match.
 *
 * ## Why the year may be omitted
 *
 * People type "15/1" far more often than "15/1/2027". Without a year the answer is the next
 * occurrence on or after [today] - the meaning "15/1" has when you are planning - and the
 * caller is told the year was assumed so the UI can say so rather than silently choosing.
 *
 * ## Why ambiguity yields more than one date
 *
 * "1/2" is the 1st of February and the 2nd of January, and no rule can settle which without
 * guessing at the user. Both are returned, most likely first by the user's own date format,
 * and the screen shows both. Picking one silently is how a jump-to-date lands you in the
 * wrong month.
 */
object DateQuery {

    /** A date the query could mean. */
    data class Match(
        val date: LocalDate,
        /** True when the query carried no year and [date] is the next occurrence. */
        val yearAssumed: Boolean,
    )

    /** The order the numeric parts of a bare `a/b/c` are read in. */
    enum class Order { DMY, YMD, MDY }

    private val SEPARATORS = Regex("[/.\\-\\s]+")

    /** Nothing outside this can be a plausible calendar year in this app. */
    private val YEARS = 1900..2199

    /**
     * Every date [raw] could mean, best first, or empty when it is not a date at all.
     *
     * [preferred] decides only the order of otherwise equally valid readings; it never
     * discards one.
     */
    fun parse(raw: String, today: LocalDate, preferred: Order = Order.DMY): List<Match> {
        val text = KhmerNumerals.toAscii(raw.trim())
        if (text.isEmpty()) return emptyList()

        val written = parseWritten(text, today)
        if (written.isNotEmpty()) return written

        val parts = text.split(SEPARATORS).filter { it.isNotEmpty() }
        if (parts.size !in 2..3) return emptyList()
        if (parts.any { it.length > 4 || !it.all(Char::isDigit) }) return emptyList()

        val numbers = parts.map { it.toInt() }
        val candidates = when (numbers.size) {
            2 -> twoPart(numbers[0], numbers[1], today, preferred)
            else -> threePart(numbers, preferred)
        }
        return candidates.distinctBy { it.date }
    }

    /** `15 មករា 2027`, or `មករា 15`, with the year optional. */
    private fun parseWritten(text: String, today: LocalDate): List<Match> {
        val month = (1..12).firstOrNull { text.contains(KhmerTerms.solarMonth(it)) } ?: return emptyList()
        val numbers = Regex("\\d{1,4}").findAll(text).map { it.value.toInt() }.toList()
        val day = numbers.firstOrNull { it in 1..31 } ?: return emptyList()
        val year = numbers.firstOrNull { it in YEARS }

        return if (year != null) {
            listOfNotNull(dateOrNull(year, month, day)?.let { Match(it, yearAssumed = false) })
        } else {
            listOfNotNull(nextOccurrence(month, day, today))
        }
    }

    /** `15/1` - a day and a month, in whichever order each reading allows. */
    private fun twoPart(a: Int, b: Int, today: LocalDate, preferred: Order): List<Match> {
        val readings = when (preferred) {
            // In a year-first format the leading number is the month.
            Order.YMD, Order.MDY -> listOf(a to b, b to a)
            Order.DMY -> listOf(b to a, a to b)
        }
        return readings.mapNotNull { (month, day) -> nextOccurrence(month, day, today) }
    }

    /** `15/1/2027` - the year is wherever a four-digit number is, or in the preferred slot. */
    private fun threePart(n: List<Int>, preferred: Order): List<Match> {
        val orders = buildList {
            add(preferred)
            addAll(Order.entries.filterNot { it == preferred })
        }
        return orders.mapNotNull { order ->
            val (y, m, d) = when (order) {
                Order.DMY -> Triple(n[2], n[1], n[0])
                Order.YMD -> Triple(n[0], n[1], n[2])
                Order.MDY -> Triple(n[2], n[0], n[1])
            }
            dateOrNull(y, m, d)?.let { Match(it, yearAssumed = false) }
        }
    }

    /**
     * The next time this day and month come round, starting from [today].
     *
     * On or after today rather than strictly after: typing today's date should find today,
     * not this date a year from now.
     */
    private fun nextOccurrence(month: Int, day: Int, today: LocalDate): Match? {
        val thisYear = dateOrNull(today.year, month, day)
        if (thisYear != null && !thisYear.isBefore(today)) return Match(thisYear, yearAssumed = true)
        // 29 February is only a date in some years, so the next occurrence may be years out.
        for (offset in 1..8) {
            val later = dateOrNull(today.year + offset, month, day)
            if (later != null) return Match(later, yearAssumed = true)
        }
        return null
    }

    private fun dateOrNull(year: Int, month: Int, day: Int): LocalDate? {
        if (year !in YEARS || month !in 1..12 || day !in 1..31) return null
        return try {
            LocalDate.of(year, month, day)
        } catch (e: DateTimeException) {
            // 31 April and 29 February in a common year land here. Not an error - the reading
            // is simply not a date, and another reading of the same text may still be.
            null
        }
    }
}
