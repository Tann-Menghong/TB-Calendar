package com.khmercalendar.core.khmer

import java.time.DayOfWeek

/**
 * The twelve ordinary lunar months plus the two halves of the doubled អាសាឍ that appear in a
 * leap-month (អធិកមាស) year.
 *
 * The ordinal is the index the Chhankitek arithmetic uses and must not be reordered: month
 * length alternates on `index % 2`, and [LunarMonth.PATHAMASATH]/[LunarMonth.TUTIYASATH] are
 * required to sit at 12 and 13.
 */
enum class LunarMonth(val index: Int, val khmer: String, val romanized: String) {
    MIKASEI(0, "មិគសិរ", "Mikasei"),
    BOSS(1, "បុស្ស", "Boss"),
    MEAK(2, "មាឃ", "Meak"),
    PHALKUN(3, "ផល្គុន", "Phalkun"),
    CHETR(4, "ចេត្រ", "Chetr"),
    PISAK(5, "ពិសាខ", "Pisak"),
    JEST(6, "ជេស្ឋ", "Jest"),
    ASATH(7, "អាសាឍ", "Asath"),
    SRAP(8, "ស្រាពណ៍", "Srap"),
    PHUTRABOT(9, "ភទ្របទ", "Phutrabot"),
    ASSOJ(10, "អស្សុជ", "Assoj"),
    KADEUK(11, "កត្តិក", "Kadeuk"),
    PATHAMASATH(12, "បឋមាសាឍ", "Pathamasath"),
    TUTIYASATH(13, "ទុតិយាសាឍ", "Tutiyasath");

    companion object {
        private val BY_INDEX = entries.associateBy { it.index }
        fun ofIndex(index: Int): LunarMonth =
            BY_INDEX[index] ?: throw IllegalArgumentException("No lunar month with index $index")

        /**
         * កត្តិក is also commonly spelled កក្ដិក. Both forms are in circulation — the
         * reference Java implementation uses one and most Khmer almanacs the other — so
         * lookups accept either.
         */
        fun byKhmerName(name: String): LunarMonth? {
            val n = KhmerNumerals.normalizeForMatching(name)
            entries.firstOrNull { it.khmer == n }?.let { return it }
            return if (n == "កក្ដិក") KADEUK else null
        }
    }
}

/** Waxing (កើត) or waning (រោច) half of the lunar month. */
enum class MoonPhase(val khmer: String, val romanized: String) {
    WAXING("កើត", "kaet"),
    WANING("រោច", "roch"),
}

/** The twelve-year animal cycle. */
enum class AnimalYear(val index: Int, val khmer: String, val romanized: String, val emoji: String) {
    CHUT(0, "ជូត", "Rat", "🐀"),
    CHHLOV(1, "ឆ្លូវ", "Ox", "🐂"),
    KHAL(2, "ខាល", "Tiger", "🐅"),
    THOH(3, "ថោះ", "Rabbit", "🐇"),
    RONG(4, "រោង", "Dragon", "🐉"),
    MSAGN(5, "ម្សាញ់", "Snake", "🐍"),
    MOMI(6, "មមីរ", "Horse", "🐎"),
    MOME(7, "មមែ", "Goat", "🐐"),
    VOK(8, "វក", "Monkey", "🐒"),
    ROKA(9, "រកា", "Rooster", "🐓"),
    CHOR(10, "ច", "Dog", "🐕"),
    KOR(11, "កុរ", "Pig", "🐖");

    companion object {
        fun ofIndex(index: Int): AnimalYear = entries[((index % 12) + 12) % 12]
    }
}

/** The ten-year era cycle (ស័ក), derived from the Jolak Sakaraj year. */
enum class EraYear(val index: Int, val khmer: String) {
    SAMRETHISAK(0, "សំរឹទ្ធិស័ក"),
    EKASAK(1, "ឯកស័ក"),
    TOSAK(2, "ទោស័ក"),
    TREYSAK(3, "ត្រីស័ក"),
    CHATVASAK(4, "ចត្វាស័ក"),
    PANHCHASAK(5, "បញ្ចស័ក"),
    CHHOSAK(6, "ឆស័ក"),
    SAPTASAK(7, "សប្តស័ក"),
    ATHTHASAK(8, "អដ្ឋស័ក"),
    NOPPASAK(9, "នព្វស័ក");

    companion object {
        fun ofIndex(index: Int): EraYear = entries[((index % 10) + 10) % 10]
    }
}

/** Khmer weekday names, and the Khmer solar (Gregorian) month names. */
object KhmerTerms {

    fun dayOfWeek(day: DayOfWeek): String = when (day) {
        DayOfWeek.SUNDAY -> "អាទិត្យ"
        DayOfWeek.MONDAY -> "ច័ន្ទ"
        DayOfWeek.TUESDAY -> "អង្គារ"
        DayOfWeek.WEDNESDAY -> "ពុធ"
        DayOfWeek.THURSDAY -> "ព្រហស្បតិ៍"
        DayOfWeek.FRIDAY -> "សុក្រ"
        DayOfWeek.SATURDAY -> "សៅរ៍"
    }

    /** Single-character column headings for a month grid. */
    fun dayOfWeekShort(day: DayOfWeek): String = when (day) {
        DayOfWeek.SUNDAY -> "អា"
        DayOfWeek.MONDAY -> "ច"
        DayOfWeek.TUESDAY -> "អ"
        DayOfWeek.WEDNESDAY -> "ពុ"
        DayOfWeek.THURSDAY -> "ព្រ"
        DayOfWeek.FRIDAY -> "សុ"
        DayOfWeek.SATURDAY -> "ស"
    }

    /**
     * The part of the day a 12-hour time belongs to.
     *
     * Khmer names the parts of the day rather than using AM/PM, and the boundaries are the
     * conventional ones: ព្រឹក to noon, រសៀល through the afternoon, ល្ងាច from six, យប់ after
     * nine. A 12-hour clock written "២ PM" reads as a translation; "២ រសៀល" reads as Khmer.
     */
    fun partOfDay(hour: Int): String = when (hour) {
        in 0..4 -> "យប់"
        in 5..11 -> "ព្រឹក"
        in 12..17 -> "រសៀល"
        in 18..20 -> "ល្ងាច"
        else -> "យប់"
    }

    private val SOLAR_MONTHS = arrayOf(
        "មករា", "កុម្ភៈ", "មីនា", "មេសា", "ឧសភា", "មិថុនា",
        "កក្កដា", "សីហា", "កញ្ញា", "តុលា", "វិច្ឆិកា", "ធ្នូ",
    )

    /** [month] is 1-based, as in [java.time.LocalDate.getMonthValue]. */
    fun solarMonth(month: Int): String = SOLAR_MONTHS[month - 1]

    fun solarMonthIndex(name: String): Int? {
        val n = KhmerNumerals.normalizeForMatching(name)
        val i = SOLAR_MONTHS.indexOf(n)
        return if (i < 0) null else i + 1
    }
}
