package com.khmercalendar.core.khmer

import java.time.LocalDate
import java.time.LocalDateTime

/**
 * A Gregorian date expressed in the Khmer lunar (ចន្ទគតិ / Chhankitek) calendar.
 *
 * @property dayInHalf 1..15, the day within the waxing or waning half.
 * @property dayIndex 0..29, the raw day within the lunar month. A 29-day month runs 0..28.
 * @property buddhistYear Buddhist Era year, which rolls over on Visakha Bochea.
 * @property jolakSakarajYear Jolak Sakaraj (ចុល្លសករាជ) year, which rolls over at Khmer New Year.
 */
data class KhmerLunarDate(
    val gregorian: LocalDate,
    val dayIndex: Int,
    val dayInHalf: Int,
    val phase: MoonPhase,
    val month: LunarMonth,
    val buddhistYear: Int,
    val jolakSakarajYear: Int,
    val animalYear: AnimalYear,
    val eraYear: EraYear,
) {
    /** e.g. "៥ កើត" */
    val dayText: String get() = "${KhmerNumerals.toKhmer(dayInHalf)} ${phase.khmer}"

    /** e.g. "ថ្ងៃ៥កើត ខែពិសាខ ឆ្នាំរោង ឆស័ក ព.ស.២៥៦៨" */
    fun format(): String = buildString {
        append("ថ្ងៃ").append(KhmerNumerals.toKhmer(dayInHalf)).append(phase.khmer)
        append(" ខែ").append(month.khmer)
        append(" ឆ្នាំ").append(animalYear.khmer)
        append(' ').append(eraYear.khmer)
        append(" ព.ស.").append(KhmerNumerals.toKhmer(buddhistYear))
    }
}

/**
 * Conversion from the Gregorian calendar to the Khmer lunar calendar.
 *
 * ## Source of the algorithm
 *
 * This is a port of the Chhankitek reckoning as implemented in
 * `MetheaX/khmer-chhankitek-calendar` (Java), itself a port of ThyrithSor's `momentkh`
 * (JavaScript), both of which follow the algorithm published by Phylypo Tum of Cam-CC. The
 * arithmetic below — Aharkun (អាហារគុណ), Avoman (អាវមាន), Bodithey (បូតិថី), Kromthupul
 * and the leap-month/leap-day resolution — is reproduced from that source with its constants
 * unchanged. See `docs/CALENDAR.md` for the derivation and for the limits of this claim.
 *
 * ## Two deliberate differences from the reference
 *
 * 1. **Month boundaries are tabulated, not re-walked.** The reference steps month by month
 *    from the 1900 epoch on *every* conversion — roughly 1,500 iterations for a date in
 *    2026, and a month grid needs 42 conversions. Here the boundary table is built once and
 *    searched, which turns each conversion into a binary search.
 *
 * 2. **A month's length is always computed from its own start.** The reference computes the
 *    length once more at the end using the *target's* Buddhist year, which differs from the
 *    month's own for months straddling the April/May boundary. That can report ១៥រោច in a
 *    29-day month, a date which does not exist. Advancing while `elapsed >= length`, with
 *    each length taken at that month's own start, keeps the sequence self-consistent.
 *
 * ## Supported range
 *
 * The algorithm is anchored at its epoch, 1 January 1900, and there is no anchor before it,
 * so [MIN_DATE] is that day. [MAX_DATE] is a practical horizon for the table. Dates outside
 * the range throw [IllegalArgumentException]; callers that display arbitrary user dates
 * should use [toLunarOrNull].
 */
object Chhankitek {

    /** The epoch of the reckoning: 1 January 1900 is ១ កើត ខែបុស្ស. */
    val EPOCH: LocalDate = LocalDate.of(1900, 1, 1)

    val MIN_DATE: LocalDate = EPOCH
    val MAX_DATE: LocalDate = LocalDate.of(2199, 12, 31)

    private val EPOCH_DAY = EPOCH.toEpochDay()
    private val MAX_EPOCH_DAY = MAX_DATE.toEpochDay()

    // ---------------------------------------------------------------------------------
    // Core arithmetic. Ported verbatim; the constants are not free parameters.
    // ---------------------------------------------------------------------------------

    /** អាហារគុណ — the running day count the rest of the reckoning is built on. */
    private fun aharkun(beYear: Int): Int = ((beYear * 292207L + 499L) / 800L).toInt() + 4

    private fun aharkunMod(beYear: Int): Int = ((beYear * 292207L + 499L) % 800L).toInt()

    /** អាវមាន — decides the leap *day*. */
    private fun avoman(beYear: Int): Int = ((11 * aharkun(beYear)) + 25) % 692

    /** បូតិថី — decides the leap *month*. */
    private fun bodithey(beYear: Int): Int {
        val ahk = aharkun(beYear)
        val avml = ((11L * ahk + 25L) / 692L).toInt()
        return (avml + ahk + 29) % 30
    }

    private fun kromthupul(beYear: Int): Int = 800 - aharkunMod(beYear)

    /** A Khmer solar year of 366 days. */
    private fun isKhmerSolarLeap(beYear: Int): Boolean = kromthupul(beYear) <= 207

    /** 0 regular, 1 leap month, 2 leap day, 3 both. */
    private fun boditheyLeap(beYear: Int): Int {
        val avoman = avoman(beYear)
        val bodithey = bodithey(beYear)

        var boditheyLeap = if (bodithey >= 25 || bodithey <= 5) 1 else 0

        var avomanLeap = 0
        if (isKhmerSolarLeap(beYear)) {
            if (avoman <= 126) avomanLeap = 1
        } else if (avoman <= 137) {
            // The 137/0 pair must stay a regular year.
            avomanLeap = if (avoman(beYear + 1) == 0) 0 else 1
        }

        // Of a consecutive 25/5 pair only the 5 may carry the leap month.
        if (bodithey == 25 && bodithey(beYear + 1) == 5) boditheyLeap = 0
        // Of a consecutive 24/6 pair the 24 must carry it.
        if (bodithey == 24 && bodithey(beYear + 1) == 6) boditheyLeap = 1

        return when {
            boditheyLeap == 1 && avomanLeap == 1 -> 3
            boditheyLeap == 1 -> 1
            avomanLeap == 1 -> 2
            else -> 0
        }
    }

    /**
     * 0 regular, 1 leap month, 2 leap day.
     *
     * A year cannot carry both, so when Bodithey asks for both the month wins and the day is
     * deferred to the following year.
     */
    private fun protetinLeap(beYear: Int): Int {
        val b = boditheyLeap(beYear)
        if (b == 3) return 1
        if (b == 2 || b == 1) return b
        if (boditheyLeap(beYear - 1) == 3) return 2
        return 0
    }

    /** អធិកមាស — a year of thirteen months, 384 days. */
    fun isLeapMonthYear(beYear: Int): Boolean = protetinLeap(beYear) == 1

    /** ចន្ទ្រាធិមាស — a year whose ជេស្ឋ has an extra day, 355 days. */
    fun isLeapDayYear(beYear: Int): Boolean = protetinLeap(beYear) == 2

    private fun daysInLunarMonth(monthIndex: Int, beYear: Int): Int = when {
        monthIndex == LunarMonth.JEST.index && isLeapDayYear(beYear) -> 30
        monthIndex == LunarMonth.PATHAMASATH.index -> 30
        monthIndex == LunarMonth.TUTIYASATH.index -> 30
        // មិគសិរ 29, បុស្ស 30, មាឃ 29, ផល្គុន 30 ...
        else -> if (monthIndex % 2 == 0) 29 else 30
    }

    private fun nextMonthIndex(monthIndex: Int, beYear: Int): Int = when (monthIndex) {
        LunarMonth.JEST.index ->
            if (isLeapMonthYear(beYear)) LunarMonth.PATHAMASATH.index else LunarMonth.ASATH.index
        LunarMonth.ASATH.index, LunarMonth.TUTIYASATH.index -> LunarMonth.SRAP.index
        LunarMonth.PATHAMASATH.index -> LunarMonth.TUTIYASATH.index
        LunarMonth.KADEUK.index -> LunarMonth.MIKASEI.index
        else -> monthIndex + 1
    }

    /**
     * The Buddhist year the lunar arithmetic should be evaluated against for a given
     * Gregorian date.
     *
     * The Buddhist year proper turns on Visakha Bochea, but the leap rules are indexed by a
     * year that turns around Khmer New Year, so anything up to and including April uses the
     * lower value. Determining this the exact way would require converting the date first,
     * which is what this is used to do.
     */
    private fun maybeBeYear(date: LocalDate): Int =
        if (date.monthValue <= 4) date.year + 543 else date.year + 544

    // ---------------------------------------------------------------------------------
    // Month boundary table
    // ---------------------------------------------------------------------------------

    /**
     * Start of every lunar month from the epoch to [MAX_DATE], as epoch days, with the month
     * index at that start. Built once on first use: about 3,700 entries, well under 100 kB.
     */
    private class BoundaryTable {
        val starts: LongArray
        val months: IntArray

        init {
            val s = ArrayList<Long>(4096)
            val m = ArrayList<Int>(4096)
            var day = EPOCH_DAY
            var month = LunarMonth.BOSS.index
            while (day <= MAX_EPOCH_DAY) {
                s.add(day)
                m.add(month)
                val be = maybeBeYear(LocalDate.ofEpochDay(day))
                day += daysInLunarMonth(month, be)
                month = nextMonthIndex(month, maybeBeYear(LocalDate.ofEpochDay(day)))
            }
            starts = LongArray(s.size) { s[it] }
            months = IntArray(m.size) { m[it] }
        }

        /** Index of the month containing [epochDay]. */
        fun indexOf(epochDay: Long): Int {
            var lo = 0
            var hi = starts.size - 1
            while (lo < hi) {
                val mid = (lo + hi + 1) ushr 1
                if (starts[mid] <= epochDay) lo = mid else hi = mid - 1
            }
            return lo
        }
    }

    private val table: BoundaryTable by lazy { BoundaryTable() }

    // ---------------------------------------------------------------------------------
    // Public conversion
    // ---------------------------------------------------------------------------------

    fun isSupported(date: LocalDate): Boolean = !date.isBefore(MIN_DATE) && !date.isAfter(MAX_DATE)

    /** Returns null rather than throwing for dates outside [MIN_DATE]..[MAX_DATE]. */
    fun toLunarOrNull(date: LocalDate): KhmerLunarDate? =
        if (isSupported(date)) toLunar(date) else null

    fun toLunar(dateTime: LocalDateTime): KhmerLunarDate = toLunar(dateTime.toLocalDate())

    fun toLunar(date: LocalDate): KhmerLunarDate {
        require(isSupported(date)) {
            "Chhankitek conversion is defined for $MIN_DATE..$MAX_DATE, got $date"
        }
        val epochDay = date.toEpochDay()
        val i = table.indexOf(epochDay)
        val dayIndex = (epochDay - table.starts[i]).toInt()
        val month = LunarMonth.ofIndex(table.months[i])

        val jsYear = jolakSakarajYear(date)
        return KhmerLunarDate(
            gregorian = date,
            dayIndex = dayIndex,
            dayInHalf = (dayIndex % 15) + 1,
            phase = if (dayIndex > 14) MoonPhase.WANING else MoonPhase.WAXING,
            month = month,
            buddhistYear = buddhistYear(date),
            jolakSakarajYear = jsYear,
            // The animal cycle is indexed off the Buddhist-style year, which is the Jolak
            // Sakaraj year plus 1182; the +4 is the cycle's offset at that epoch.
            animalYear = AnimalYear.ofIndex(jsYear + 1182 + 4),
            eraYear = EraYear.ofIndex(jsYear % 10),
        )
    }

    /**
     * The day index (0..29) and month index of [date], without the year fields.
     *
     * [KhmerNewYear] needs the lunar day of a Gregorian date in order to place ឡើងស័ក, and
     * [toLunar] needs Khmer New Year in order to name the animal and era year. Exposing the
     * half of the conversion that has no year in it breaks that cycle.
     */
    internal fun rawLunar(date: LocalDate): Pair<Int, Int> {
        require(isSupported(date)) {
            "Chhankitek conversion is defined for $MIN_DATE..$MAX_DATE, got $date"
        }
        val i = table.indexOf(date.toEpochDay())
        return (date.toEpochDay() - table.starts[i]).toInt() to table.months[i]
    }

    /** Number of days in the lunar month containing [date]. */
    fun lengthOfLunarMonth(date: LocalDate): Int {
        require(isSupported(date))
        val i = table.indexOf(date.toEpochDay())
        val next = if (i + 1 < table.starts.size) table.starts[i + 1] else table.starts[i] + 30
        return (next - table.starts[i]).toInt()
    }

    /**
     * ថ្ងៃវិសាខបូជា — the fifteenth waxing day of ពិសាខ, on which the Buddhist Era year turns.
     */
    fun visakhaBochea(gregorianYear: Int): LocalDate {
        val from = LocalDate.of(gregorianYear, 1, 1).toEpochDay()
        val to = LocalDate.of(gregorianYear, 12, 31).toEpochDay()
        var i = table.indexOf(from)
        while (i < table.starts.size && table.starts[i] <= to) {
            if (table.months[i] == LunarMonth.PISAK.index) {
                val d = table.starts[i] + 14
                if (d in from..to) return LocalDate.ofEpochDay(d)
            }
            i++
        }
        throw IllegalStateException("No Visakha Bochea found in $gregorianYear")
    }

    /** The Buddhist Era year of [date]; it advances on Visakha Bochea. */
    fun buddhistYear(date: LocalDate): Int {
        val y = date.year
        return if (date.isAfter(visakhaBochea(y))) y + 544 else y + 543
    }

    /** The Jolak Sakaraj year of [date]; it advances at Khmer New Year. */
    fun jolakSakarajYear(date: LocalDate): Int {
        val y = date.year
        val newYear = KhmerNewYear.mohaSangkran(y).toLocalDate()
        return if (date.isBefore(newYear)) y + 543 - 1182 else y + 544 - 1182
    }
}
