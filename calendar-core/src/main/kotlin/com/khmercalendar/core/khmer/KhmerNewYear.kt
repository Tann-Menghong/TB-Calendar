package com.khmercalendar.core.khmer

import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap

/**
 * The three or four days of ចូលឆ្នាំថ្មី, with the moment the new year's ទេវតា is reckoned
 * to arrive.
 *
 * @property mohaSangkran ថ្ងៃមហាសង្ក្រាន្ត — the first day, and the arrival time.
 * @property days Every day of the festival, in order.
 * @property lerngSak ថ្ងៃឡើងស័ក — the last day, on which the era year turns.
 */
data class KhmerNewYearDays(
    val jolakSakarajYear: Int,
    val mohaSangkran: LocalDateTime,
    val days: List<LocalDate>,
) {
    val vearakVanabat: LocalDate get() = days[1]
    val lerngSak: LocalDate get() = days.last()
}

/**
 * ការគណនាថ្ងៃចូលឆ្នាំខ្មែរ — the solar (សុរិយគតិ) side of the reckoning.
 *
 * Ported from `KhmerNewYearCal` in `MetheaX/khmer-chhankitek-calendar`, which follows
 * Phylypo Tum's description of the traditional method: the sun's mean longitude is stepped
 * out in រាសី/អង្សា/លិប្ដា for the last few សុទិន of the outgoing year, and the day on which
 * អង្សា reaches zero is មហាសង្ក្រាន្ត.
 *
 * The result is the *traditional* date. Cambodia's gazetted public holiday is normally the
 * same three days, but it is announced by sub-decree and has occasionally been moved; the
 * holiday layer treats the government dates as authoritative where they are known.
 */
object KhmerNewYear {

    private val cache = ConcurrentHashMap<Int, KhmerNewYearDays>()

    /** Moha Sangkran for the given Gregorian year. */
    fun mohaSangkran(gregorianYear: Int): LocalDateTime = of(gregorianYear).mohaSangkran

    fun of(gregorianYear: Int): KhmerNewYearDays =
        cache.getOrPut(gregorianYear) { compute(gregorianYear) }

    // ---------------------------------------------------------------------------------

    private data class YearInfo(val harkun: Int, val kromathopol: Int, val avaman: Int, val bodithey: Int)

    private fun info(jsYear: Int): YearInfo {
        val h = 292207L * jsYear + 373L
        val harkun = (h / 800L).toInt() + 1
        val kromathopol = 800 - (h % 800L).toInt()
        val a = 11 * harkun + 650
        return YearInfo(
            harkun = harkun,
            kromathopol = kromathopol,
            avaman = a % 692,
            bodithey = (harkun + a / 692) % 30,
        )
    }

    /** A Jolak Sakaraj year of 366 days. */
    private fun has366Days(jsYear: Int): Boolean = info(jsYear).kromathopol <= 207

    /** ឆ្នាំអធិកមាស — thirteen lunar months. */
    private fun isAthikameas(jsYear: Int): Boolean {
        val y = info(jsYear)
        val next = info(jsYear + 1)
        return !(y.bodithey == 25 && next.bodithey == 5) &&
            (y.bodithey > 24 || y.bodithey < 6 || (y.bodithey == 24 && next.bodithey == 6))
    }

    /**
     * ឆ្នាំចន្ទ្រាធិមាស — ជេស្ឋ carries an extra day.
     *
     * The reference implementation has a third clause, `previousYear.avaman == 137 &&
     * thisYear.avaman == 0`, but fetches "previous year" with the *current* year's number,
     * which makes that clause unsatisfiable. It is left out here rather than repaired: the
     * published Khmer calendars this port was checked against were produced with the
     * reference's behaviour, and silently changing it would move dates. See docs/CALENDAR.md.
     */
    private fun isChantreathimeas(jsYear: Int): Boolean {
        val y = info(jsYear)
        val next = info(jsYear + 1)
        val has366 = has366Days(jsYear)
        return (has366 && y.avaman < 127) ||
            (!(y.avaman == 137 && next.avaman == 0) && (!has366 && y.avaman < 138))
    }

    private data class LunarDateLerngSak(val day: Int, val month: Int)

    private fun lunarDateLerngSak(jsYear: Int): LunarDateLerngSak {
        var bodithey = info(jsYear).bodithey
        if (isAthikameas(jsYear - 1) && isChantreathimeas(jsYear - 1)) {
            bodithey = (bodithey + 1) % 30
        }
        return if (bodithey >= 6) {
            LunarDateLerngSak(bodithey - 1, LunarMonth.CHETR.index)
        } else {
            LunarDateLerngSak(bodithey, LunarMonth.PISAK.index)
        }
    }

    // --- the sun's position, in រាសី (30 អង្សា) / អង្សា (60 លិប្ដា) / លិប្ដា ---------------

    private const val ANGSAR_PER_REASEY = 30
    private const val LIBDA_PER_ANGSAR = 60
    private const val LIBDA_PER_REASEY = ANGSAR_PER_REASEY * LIBDA_PER_ANGSAR

    /** មធ្យមព្រះអាទិត្យ for សុទិន [sotin], expressed in លិប្ដា. */
    private fun sunAverageAsLibda(sotin: Int, previous: YearInfo): Int {
        val r2 = 800 * sotin + previous.kromathopol
        val reasey = r2 / 24350
        val r3 = r2 % 24350
        val angsar = r3 / 811
        val r4 = r3 % 811
        val libda = (r4 / 14) - 3
        return LIBDA_PER_REASEY * reasey + LIBDA_PER_ANGSAR * angsar + libda
    }

    private fun leftOver(sunAverage: Int): Int {
        val s1 = LIBDA_PER_REASEY * 2 + LIBDA_PER_ANGSAR * 20 // R2.A20.L0
        // Below R2.A20.L0 the reckoning borrows a full circle of twelve រាសី.
        return if (sunAverage < s1) sunAverage - s1 + LIBDA_PER_REASEY * 12 else sunAverage - s1
    }

    private data class Rasi(val reasey: Int, val angsar: Int, val libda: Int)

    private fun lastLeftOver(kaen: Int, leftOver: Int): Rasi {
        val rs = when (kaen) {
            0, 1, 2 -> kaen
            3, 4, 5 -> LIBDA_PER_REASEY * 6 - leftOver
            6, 7, 8 -> leftOver - LIBDA_PER_REASEY * 6
            else -> (LIBDA_PER_REASEY * 11 + LIBDA_PER_ANGSAR * 29 + 60) - leftOver
        }
        return Rasi(rs / LIBDA_PER_REASEY, (rs % LIBDA_PER_REASEY) / LIBDA_PER_ANGSAR, rs % 60)
    }

    private val PHOL_MULTIPLICITY = intArrayOf(35, 32, 27, 22, 13, 5)
    private val PHOL_CHHAYA = intArrayOf(0, 35, 67, 94, 116, 129)

    private fun phol(khan: Int, pouichalip: Int): Rasi {
        val multiplicity = if (khan in 0..5) PHOL_MULTIPLICITY[khan] else 0
        val chhaya = if (khan in 0..5) PHOL_CHHAYA[khan] else 134
        val q = (pouichalip * multiplicity) / 900
        return Rasi(0, (q + chhaya) / 60, (q + chhaya) % 60)
    }

    /** សម្ពោធព្រះអាទិត្យ for [sotin], in លិប្ដា. */
    private fun sunInaugurationAsLibda(jsYear: Int, sotin: Int): Int {
        val previous = info(jsYear - 1)
        val average = sunAverageAsLibda(sotin, previous)
        val left = leftOver(average)
        val kaen = left / LIBDA_PER_REASEY

        val last = lastLeftOver(kaen, left)
        val khan: Int
        val pouichalip: Int
        if (last.angsar >= 15) {
            khan = 2 * last.reasey + 1
            pouichalip = 60 * (last.angsar - 15) + last.libda
        } else {
            khan = 2 * last.reasey
            pouichalip = 60 * last.angsar + last.libda
        }

        val p = phol(khan, pouichalip)
        val pholAsLibda = LIBDA_PER_REASEY * p.reasey + LIBDA_PER_ANGSAR * p.angsar + p.libda
        return if (kaen <= 5) average - pholAsLibda else average + pholAsLibda
    }

    private data class Sotin(val sotin: Int, val reasey: Int, val angsar: Int, val libda: Int)

    /**
     * The last few សុទិន of the outgoing year. The one whose អង្សា is zero is មហាសង្ក្រាន្ត;
     * the days after it are វារៈវ័នបត and the last is ឡើងស័ក.
     */
    private fun newYearSotins(jsYear: Int): List<Sotin> {
        val range = if (has366Days(jsYear - 1)) listOf(363, 364, 365, 366) else listOf(362, 363, 364, 365)
        return range.map { s ->
            val inauguration = sunInaugurationAsLibda(jsYear, s)
            Sotin(
                sotin = s,
                reasey = inauguration / LIBDA_PER_REASEY,
                angsar = (inauguration % LIBDA_PER_REASEY) / LIBDA_PER_ANGSAR,
                libda = inauguration % 60,
            )
        }
    }

    /** ម៉ោងទេវតាចុះ — the hour and minute the new year is reckoned to begin. */
    private fun newYearTime(sotins: List<Sotin>): Pair<Int, Int> {
        val zero = sotins.firstOrNull { it.angsar == 0 }
            ?: // The angsar-zero សុទិន always exists for the range this app supports; falling
            // back to midnight keeps a corrupt year from taking the whole calendar down.
            return 0 to 0
        // A លិប្ដា of zero gives 1440 minutes, i.e. hour 24, which is midnight at the start of
        // the day rather than an invalid time. Wrapping keeps the whole day range legal.
        val minutes = Math.floorMod(24 * 60 - zero.libda * 24, 24 * 60)
        return (minutes / 60) to (minutes % 60)
    }

    private fun compute(gregorianYear: Int): KhmerNewYearDays {
        val jsYear = gregorianYear + 544 - 1182
        val sotins = newYearSotins(jsYear)
        val festivalDays = if (sotins.first().angsar == 0) 4 else 3
        val (hour, minute) = newYearTime(sotins)

        // ឡើងស័ក falls on a known lunar day of ចេត្រ or ពិសាខ; find the Gregorian date that
        // actually carries it.
        //
        // The reference implementation instead estimates the offset from 17 April
        // arithmetically, as `(month - 4) * 30 + day`. That treats every lunar month as 30
        // days long, so whenever the span crosses a 29-day month the answer is a day late —
        // which is why it puts Khmer New Year 2026 on 13-15 April where the sub-decree, and
        // the search below, put it on 14-16 April.
        val target = lunarDateLerngSak(jsYear)
        val lerngSak = findLunarDay(gregorianYear, target)
            ?: LocalDate.of(gregorianYear, 4, 16) // The reckoning has not left this window since 1900.

        val firstDay = lerngSak.minusDays((festivalDays - 1).toLong())
        val mohaSangkran = LocalDateTime.of(firstDay, java.time.LocalTime.of(hour, minute))
        val days = (0 until festivalDays).map { firstDay.plusDays(it.toLong()) }
        return KhmerNewYearDays(jsYear, mohaSangkran, days)
    }

    /** The date in [gregorianYear] carrying lunar day [target], searched around mid-April. */
    private fun findLunarDay(gregorianYear: Int, target: LunarDateLerngSak): LocalDate? {
        var d = LocalDate.of(gregorianYear, 3, 20)
        val end = LocalDate.of(gregorianYear, 5, 20)
        while (!d.isAfter(end)) {
            val (dayIndex, monthIndex) = Chhankitek.rawLunar(d)
            if (dayIndex == target.day && monthIndex == target.month) return d
            d = d.plusDays(1)
        }
        return null
    }
}
