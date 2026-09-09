package com.khmercalendar.domain

import com.khmercalendar.core.khmer.KhmerNumerals
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/**
 * How a countdown is written.
 *
 * Three, because they answer three different questions and everything else is decoration.
 * [DAYS] is the one that fits a dashboard row and the one anybody wants for an exam six
 * weeks out. [DAYS_HOURS] is what a deadline needs on the day itself, when "១ ថ្ងៃ" is the
 * difference between tonight and tomorrow evening. [WEEKS] is how people actually talk about
 * a month or two out - "ប្រហែល ៦ សប្តាហ៍" rather than "៤៣ ថ្ងៃ".
 *
 * The key is what the preference stores, so it must stay stable.
 */
enum class CountdownStyle(val key: String, val labelKm: String) {
    DAYS("days", "ថ្ងៃ"),
    DAYS_HOURS("days_hours", "ថ្ងៃ និងម៉ោង"),
    WEEKS("weeks", "សប្តាហ៍"),
    ;

    companion object {
        fun of(key: String?): CountdownStyle = entries.firstOrNull { it.key == key } ?: DAYS
    }
}

/**
 * A date somebody is counting towards.
 *
 * @property eventId The pinned event, or 0 for one the app derived (a public holiday).
 * @property at The moment it happens, used only by [CountdownStyle.DAYS_HOURS]. An all-day
 *   entry has no meaningful clock time, so it carries the start of its day and is reported in
 *   whole days however the style is set - claiming "៣ ថ្ងៃ ៧ ម៉ោង" until a birthday is a
 *   precision the data does not have.
 */
data class CountdownItem(
    val eventId: Long,
    val title: String,
    val date: LocalDate,
    val at: LocalDateTime,
    val allDay: Boolean,
    val isHoliday: Boolean,
    val isPinned: Boolean,
) {
    val daysAway: Long get() = ChronoUnit.DAYS.between(LocalDate.now(), date)
}

/**
 * Building and phrasing countdowns.
 *
 * The dashboard's countdown card used to show the next holiday and the next event, chosen for
 * the user. That is a fine default and a poor feature: the whole point of a countdown is that
 * *you* decide what is worth counting, and there was no way to say so. Pinning is now a
 * property of the event (see `EventEntity.isPinned`), and this turns pinned rows into what
 * the card draws.
 *
 * `today` is always a parameter. Reading the clock in here would make every case below
 * untestable, which for arithmetic that is only ever wrong by one is the whole test.
 */
object Countdowns {

    /** How many pinned countdowns the dashboard card shows before it stops. */
    const val CARD_LIMIT = 3

    /**
     * The countdowns to show, nearest first.
     *
     * Pinned dates lead, in date order. A past pinned date is dropped rather than shown as a
     * negative: a countdown that has arrived is over, and the pin can be removed - silently
     * hiding it would be worse, so the countdown screen says so instead.
     *
     * [fallback] is the app's own suggestion - the next public holiday - and is used only
     * while nothing is pinned. Mixing it in beside pinned dates would mean the card quietly
     * added rows the user did not choose, which is what this feature exists to stop.
     */
    fun build(
        pinned: List<CountdownItem>,
        fallback: List<CountdownItem>,
        today: LocalDate,
        limit: Int = CARD_LIMIT,
    ): List<CountdownItem> {
        val upcoming = pinned
            .filterNot { it.date.isBefore(today) }
            .sortedWith(compareBy({ it.date }, { it.at }, { it.title }))
        if (upcoming.isNotEmpty()) return upcoming.take(limit)
        return fallback
            .filterNot { it.date.isBefore(today) }
            .sortedBy { it.date }
            .take(limit)
    }

    /**
     * The remaining time, in Khmer, in the user's chosen style.
     *
     * "ថ្ងៃនេះ" for today and "ថ្ងៃស្អែក" for tomorrow in every style: a countdown reading
     * "១ ថ្ងៃ" makes the reader do the arithmetic the countdown was supposed to do for them.
     */
    fun remaining(
        item: CountdownItem,
        now: LocalDateTime,
        style: CountdownStyle,
        khmerNumerals: Boolean = true,
    ): String {
        val today = now.toLocalDate()
        val days = ChronoUnit.DAYS.between(today, item.date)

        fun num(value: Long) = if (khmerNumerals) KhmerNumerals.toKhmer(value.toString()) else value.toString()

        if (days < 0L) return "ហួសកំណត់"
        if (days == 0L) return "ថ្ងៃនេះ"
        if (days == 1L) return "ថ្ងៃស្អែក"

        return when (style) {
            CountdownStyle.DAYS -> "នៅ ${num(days)} ថ្ងៃ"

            CountdownStyle.DAYS_HOURS -> {
                // An all-day entry has no clock time to be precise about.
                if (item.allDay) {
                    "នៅ ${num(days)} ថ្ងៃ"
                } else {
                    val total = Duration.between(now, item.at)
                    val wholeDays = total.toDays()
                    val hours = total.minusDays(wholeDays).toHours()
                    if (hours == 0L) {
                        "នៅ ${num(wholeDays)} ថ្ងៃ"
                    } else {
                        "នៅ ${num(wholeDays)} ថ្ងៃ ${num(hours)} ម៉ោង"
                    }
                }
            }

            CountdownStyle.WEEKS -> {
                val weeks = days / 7
                val rest = days % 7
                when {
                    // Under a week the answer is days; "០ សប្តាហ៍" is not an answer.
                    weeks == 0L -> "នៅ ${num(days)} ថ្ងៃ"
                    rest == 0L -> "នៅ ${num(weeks)} សប្តាហ៍"
                    else -> "នៅ ${num(weeks)} សប្តាហ៍ ${num(rest)} ថ្ងៃ"
                }
            }
        }
    }
}
