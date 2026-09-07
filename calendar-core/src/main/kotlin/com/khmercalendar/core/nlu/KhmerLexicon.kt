package com.khmercalendar.core.nlu

import java.time.DayOfWeek

/**
 * The Khmer and English vocabulary the deterministic parser matches against.
 *
 * Several entries carry more than one spelling on purpose. Khmer is written with and
 * without the ័ in ច័ន្ទ, with and without the ៍ in អង្គារ៍, and Cambodians type ស្អែក as
 * often as ថ្ងៃស្អែក. Recognising only the dictionary form means failing on ordinary
 * messages, so every widely used variant is listed.
 */
object KhmerLexicon {

    /** Day offsets from today. */
    val RELATIVE_DAYS: Map<String, Int> = linkedMapOf(
        "ថ្ងៃខានស្អែក" to 2,
        "ខានស្អែក" to 2,
        "ថ្ងៃស្អែក" to 1,
        "ស្អែក" to 1,
        "ថ្ងៃនេះ" to 0,
        "ថ្ងៃម្សិលមិញ" to -1,
        "ម្សិលមិញ" to -1,
        "ម្សិលម្ង៉ៃ" to -2,
        "the day after tomorrow" to 2,
        "tomorrow" to 1,
        "today" to 0,
        "tonight" to 0,
        "yesterday" to -1,
    )

    val WEEKDAYS: Map<String, DayOfWeek> = linkedMapOf(
        "ថ្ងៃអាទិត្យ" to DayOfWeek.SUNDAY, "អាទិត្យ" to DayOfWeek.SUNDAY,
        "ថ្ងៃច័ន្ទ" to DayOfWeek.MONDAY, "ថ្ងៃចន្ទ" to DayOfWeek.MONDAY,
        "ច័ន្ទ" to DayOfWeek.MONDAY, "ចន្ទ" to DayOfWeek.MONDAY,
        "ថ្ងៃអង្គារ៍" to DayOfWeek.TUESDAY, "ថ្ងៃអង្គារ" to DayOfWeek.TUESDAY,
        "អង្គារ៍" to DayOfWeek.TUESDAY, "អង្គារ" to DayOfWeek.TUESDAY,
        "ថ្ងៃពុធ" to DayOfWeek.WEDNESDAY, "ពុធ" to DayOfWeek.WEDNESDAY,
        "ថ្ងៃព្រហស្បតិ៍" to DayOfWeek.THURSDAY, "ថ្ងៃព្រហស្បត៍" to DayOfWeek.THURSDAY,
        "ព្រហស្បតិ៍" to DayOfWeek.THURSDAY, "ព្រហស្បត៍" to DayOfWeek.THURSDAY,
        "ថ្ងៃសុក្រ" to DayOfWeek.FRIDAY, "សុក្រ" to DayOfWeek.FRIDAY,
        "ថ្ងៃសៅរ៍" to DayOfWeek.SATURDAY, "សៅរ៍" to DayOfWeek.SATURDAY,
        "sunday" to DayOfWeek.SUNDAY, "monday" to DayOfWeek.MONDAY,
        "tuesday" to DayOfWeek.TUESDAY, "wednesday" to DayOfWeek.WEDNESDAY,
        "thursday" to DayOfWeek.THURSDAY, "friday" to DayOfWeek.FRIDAY,
        "saturday" to DayOfWeek.SATURDAY,
    )

    val SOLAR_MONTHS: Map<String, Int> = linkedMapOf(
        "មករា" to 1, "កុម្ភៈ" to 2, "មីនា" to 3, "មេសា" to 4, "ឧសភា" to 5, "មិថុនា" to 6,
        "កក្កដា" to 7, "សីហា" to 8, "កញ្ញា" to 9, "តុលា" to 10, "វិច្ឆិកា" to 11, "ធ្នូ" to 12,
        "january" to 1, "february" to 2, "march" to 3, "april" to 4, "may" to 5, "june" to 6,
        "july" to 7, "august" to 8, "september" to 9, "october" to 10, "november" to 11, "december" to 12,
        "jan" to 1, "feb" to 2, "mar" to 3, "apr" to 4, "jun" to 6,
        "jul" to 7, "aug" to 8, "sep" to 9, "sept" to 9, "oct" to 10, "nov" to 11, "dec" to 12,
    )

    enum class TimeOfDay { MORNING, NOON, AFTERNOON, EVENING, NIGHT }

    val TIME_OF_DAY: Map<String, TimeOfDay> = linkedMapOf(
        "ថ្ងៃត្រង់" to TimeOfDay.NOON,
        "ព្រឹក" to TimeOfDay.MORNING,
        "រសៀល" to TimeOfDay.AFTERNOON,
        "ល្ងាច" to TimeOfDay.EVENING,
        "យប់" to TimeOfDay.NIGHT,
        "អាធ្រាត្រ" to TimeOfDay.NIGHT,
        "morning" to TimeOfDay.MORNING,
        "noon" to TimeOfDay.NOON,
        "afternoon" to TimeOfDay.AFTERNOON,
        "evening" to TimeOfDay.EVENING,
        "night" to TimeOfDay.NIGHT,
        "am" to TimeOfDay.MORNING,
        "pm" to TimeOfDay.AFTERNOON,
    )

    /** Spelled-out numbers, enough to cover hours, minutes and small counts. */
    val NUMBER_WORDS: Map<String, Int> = linkedMapOf(
        "មួយ" to 1, "ពីរ" to 2, "ពី" to 2, "បី" to 3, "បួន" to 4, "ប្រាំ" to 5,
        "ប្រាំមួយ" to 6, "ប្រាំពីរ" to 7, "ប្រាំបី" to 8, "ប្រាំបួន" to 9, "ដប់" to 10,
        "ដប់មួយ" to 11, "ដប់ពីរ" to 12, "ម្ភៃ" to 20, "សាមសិប" to 30,
        "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5, "six" to 6,
        "seven" to 7, "eight" to 8, "nine" to 9, "ten" to 10, "eleven" to 11, "twelve" to 12,
        "fifteen" to 15, "twenty" to 20, "thirty" to 30, "half" to 30,
    )

    /** Words that mean "set a reminder". */
    val REMINDER_WORDS = listOf("រំលឹក", "រំឮក", "កុំភ្លេច", "ជូនដំណឹង", "remind", "reminder", "alert", "don't forget")

    /** Words that introduce a place. */
    val LOCATION_MARKERS = listOf("នៅឯ", "នៅទីតាំង", "នៅ", " at ", " in ")

    val HOUR_MARKERS = listOf("ម៉ោង", "o'clock", "at")
    val MINUTE_WORDS = listOf("នាទី", "minute", "minutes", "min", "mins")
    val HOUR_WORDS = listOf("ម៉ោង", "hour", "hours", "hr", "hrs")
    val DAY_WORDS = listOf("ថ្ងៃ", "day", "days")
    val WEEK_WORDS = listOf("សប្តាហ៍", "អាទិត្យ", "week", "weeks")

    /** "before" in a reminder phrase such as ៣០ នាទីមុន. */
    val BEFORE_WORDS = listOf("មុន", "before", "ahead")

    val NEXT_WORDS = listOf("ក្រោយ", "បន្ទាប់", "next", "coming")
    val EVERY_WORDS = listOf("រៀងរាល់", "រាល់", "every", "each")
}
