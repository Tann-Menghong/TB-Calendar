package com.khmercalendar.core.holiday

import com.khmercalendar.core.khmer.Chhankitek
import com.khmercalendar.core.khmer.KhmerNewYear
import com.khmercalendar.core.khmer.LunarMonth
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap

enum class HolidayKind {
    /** A gazetted public holiday: offices and banks close. */
    PUBLIC,

    /** Observed and widely marked, but not a day off. */
    OBSERVANCE,
}

enum class HolidaySource {
    /** Same Gregorian date every year. */
    FIXED,

    /** Derived from the lunar calendar by this app. */
    LUNAR,

    /** Taken from a published sub-decree, overriding the computed date. */
    GAZETTED,
}

data class Holiday(
    val date: LocalDate,
    val nameKm: String,
    val nameEn: String,
    val kind: HolidayKind,
    val source: HolidaySource,
)

/**
 * Cambodian public holidays and religious observances.
 *
 * Fixed-date holidays are constants. The moveable ones — Khmer New Year, Visak Bochea, Meak
 * Bochea, the Royal Ploughing Ceremony, Pchum Ben and the Water Festival — are *computed*
 * from [Chhankitek] rather than listed, so the calendar stays correct in years nobody has
 * hand-entered.
 *
 * ## Computed versus gazetted
 *
 * The lunar observance and the day off are not the same thing. Cambodia sets its public
 * holiday calendar by annual sub-decree, and the gazetted range occasionally differs from
 * the traditional reckoning by a day — the Water Festival in particular is sometimes shifted.
 * Where the sub-decree is known it is in [GAZETTED_OVERRIDES] and wins; everywhere else the
 * computed date is used and is, by construction, the traditional one.
 *
 * The public-holiday list reflects the 2020 reform, which cut the schedule to its present
 * size. Days it removed but which are still widely observed — Meak Bochea, International
 * Women's Day — are kept as [HolidayKind.OBSERVANCE].
 */
object KhmerHolidays {

    private val cache = ConcurrentHashMap<Int, List<Holiday>>()

    /**
     * Sub-decree dates that differ from the computed ones, keyed by Gregorian year.
     *
     * Only the festivals whose gazetted range has actually been published are listed. Adding
     * a year here is the supported way to track a new sub-decree; see docs/HOLIDAYS.md.
     */
    private val GAZETTED_OVERRIDES: Map<Int, Map<String, List<LocalDate>>> = mapOf(
        2026 to mapOf(
            // Sub-decree No. 167 (18 September 2025).
            "water" to listOf(
                LocalDate.of(2026, 11, 24),
                LocalDate.of(2026, 11, 25),
                LocalDate.of(2026, 11, 26),
            ),
        ),
    )

    fun forYear(year: Int): List<Holiday> = cache.getOrPut(year) { compute(year) }

    /** Every holiday between [from] and [to] inclusive. */
    fun inRange(from: LocalDate, to: LocalDate): List<Holiday> =
        (from.year..to.year)
            .flatMap { forYear(it) }
            .filter { !it.date.isBefore(from) && !it.date.isAfter(to) }
            .sortedBy { it.date }

    fun on(date: LocalDate): List<Holiday> = forYear(date.year).filter { it.date == date }

    fun isPublicHoliday(date: LocalDate): Boolean =
        on(date).any { it.kind == HolidayKind.PUBLIC }

    // ---------------------------------------------------------------------------------

    private fun compute(year: Int): List<Holiday> {
        if (year < Chhankitek.MIN_DATE.year || year > Chhankitek.MAX_DATE.year) return emptyList()
        val out = ArrayList<Holiday>(24)

        fun fixed(month: Int, day: Int, km: String, en: String, kind: HolidayKind = HolidayKind.PUBLIC) {
            out += Holiday(LocalDate.of(year, month, day), km, en, kind, HolidaySource.FIXED)
        }

        fixed(1, 1, "ទិវាចូលឆ្នាំសាកល", "International New Year's Day")
        fixed(1, 7, "ទិវាជ័យជម្នះ ៧ មករា", "Victory over Genocide Day")
        fixed(3, 8, "ទិវានារីអន្តរជាតិ", "International Women's Day", HolidayKind.OBSERVANCE)
        fixed(5, 1, "ទិវាពលកម្មអន្តរជាតិ", "International Labour Day")
        fixed(5, 14, "ព្រះរាជពិធីបុណ្យចម្រើនព្រះជន្មព្រះមហាក្សត្រ", "King Norodom Sihamoni's Birthday")
        fixed(6, 18, "ទិវាព្រះរាជសម្ភពសម្តេចព្រះមហាក្សត្រី", "Birthday of the Queen Mother")
        fixed(9, 24, "ទិវារដ្ឋធម្មនុញ្ញ", "Constitution Day")
        fixed(10, 15, "ទិវាប្រារព្ធពិធីគោរពព្រះវិញ្ញាណក្ខន្ធព្រះបរមរតនកោដ្ឋ", "Commemoration Day of King Father Norodom Sihanouk")
        fixed(10, 29, "ព្រះរាជពិធីគ្រងព្រះបរមរាជសម្បត្តិ", "Coronation Day of King Norodom Sihamoni")
        fixed(11, 9, "ទិវាបុណ្យឯករាជ្យជាតិ", "Independence Day")

        // --- Khmer New Year: three or four days, computed ---------------------------------
        runCatching { KhmerNewYear.of(year) }.getOrNull()?.let { kny ->
            kny.days.forEachIndexed { i, d ->
                val km = when {
                    i == 0 -> "ថ្ងៃមហាសង្ក្រាន្ត"
                    i == kny.days.lastIndex -> "ថ្ងៃឡើងស័ក"
                    else -> "ថ្ងៃវារៈវ័នបត"
                }
                out += Holiday(d, "បុណ្យចូលឆ្នាំថ្មី — $km", "Khmer New Year (day ${i + 1})",
                    HolidayKind.PUBLIC, HolidaySource.LUNAR)
            }
        }

        // --- Single-day lunar observances -------------------------------------------------
        lunarDay(year, LunarMonth.MEAK, 14)?.let {
            out += Holiday(it, "ពិធីបុណ្យមាឃបូជា", "Meak Bochea Day",
                HolidayKind.OBSERVANCE, HolidaySource.LUNAR)
        }
        lunarDay(year, LunarMonth.PISAK, 14)?.let {
            out += Holiday(it, "ពិធីបុណ្យវិសាខបូជា", "Visak Bochea Day",
                HolidayKind.PUBLIC, HolidaySource.LUNAR)
        }
        // ព្រះរាជពិធីច្រត់ព្រះនង្គ័ល falls on ៤រោច ខែពិសាខ.
        lunarDay(year, LunarMonth.PISAK, 18)?.let {
            out += Holiday(it, "ព្រះរាជពិធីច្រត់ព្រះនង្គ័ល", "Royal Ploughing Ceremony",
                HolidayKind.PUBLIC, HolidaySource.LUNAR)
        }

        // --- Pchum Ben: ១៤រោច and ១៥រោច ភទ្របទ, then ១កើត អស្សុជ ---------------------------
        val benThom = lunarDay(year, LunarMonth.PHUTRABOT, 29)
        addFestival(
            out, year, "pchumben",
            benThom?.let { listOf(it.minusDays(1), it, it.plusDays(1)) },
            "ពិធីបុណ្យភ្ជុំបិណ្ឌ", "Pchum Ben",
        )

        // --- Water Festival: ១៤កើត, ១៥កើត and ១រោច ខែកត្តិក --------------------------------
        val fullMoonKadeuk = lunarDay(year, LunarMonth.KADEUK, 14)
        addFestival(
            out, year, "water",
            fullMoonKadeuk?.let { listOf(it.minusDays(1), it, it.plusDays(1)) },
            "ព្រះរាជពិធីបុណ្យអុំទូក បណ្តែតប្រទីប និងសំពះព្រះខែអកអំបុក", "Water Festival",
        )

        return out.filter { it.date.year == year }.sortedBy { it.date }
    }

    private fun addFestival(
        out: MutableList<Holiday>,
        year: Int,
        key: String,
        computed: List<LocalDate>?,
        nameKm: String,
        nameEn: String,
    ) {
        val gazetted = GAZETTED_OVERRIDES[year]?.get(key)
        val dates = gazetted ?: computed ?: return
        val source = if (gazetted != null) HolidaySource.GAZETTED else HolidaySource.LUNAR
        dates.forEachIndexed { i, d ->
            out += Holiday(d, "$nameKm (ថ្ងៃទី${i + 1})", "$nameEn (day ${i + 1})",
                HolidayKind.PUBLIC, source)
        }
    }

    /** The Gregorian date in [year] carrying lunar [month]/[dayIndex], if there is one. */
    private fun lunarDay(year: Int, month: LunarMonth, dayIndex: Int): LocalDate? {
        var d = maxOf(LocalDate.of(year, 1, 1), Chhankitek.MIN_DATE)
        val end = minOf(LocalDate.of(year, 12, 31), Chhankitek.MAX_DATE)
        while (!d.isAfter(end)) {
            val l = Chhankitek.toLunar(d)
            if (l.month == month && l.dayIndex == dayIndex) return d
            // A given lunar day recurs only once a lunar year, so once the month is behind
            // us the rest of the scan is wasted; step by the remaining days of the month.
            d = d.plusDays(1)
        }
        return null
    }
}
