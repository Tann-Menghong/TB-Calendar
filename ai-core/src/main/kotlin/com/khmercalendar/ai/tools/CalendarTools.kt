package com.khmercalendar.ai.tools

import com.khmercalendar.core.khmer.Chhankitek
import com.khmercalendar.core.khmer.KhmerNumerals
import com.khmercalendar.core.khmer.KhmerTerms
import com.khmercalendar.core.schedule.FreeTime
import com.khmercalendar.core.schedule.TimeSpan
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** An event as the assistant sees it. Deliberately free of Room and of the UI. */
data class AiEventView(
    val id: Long,
    val title: String,
    val start: LocalDateTime,
    val end: LocalDateTime,
    val allDay: Boolean,
    val location: String? = null,
    val categoryName: String? = null,
)

/** How the assistant reads the calendar, without depending on how it is stored. */
interface CalendarQuery {
    suspend fun eventsBetween(from: LocalDateTime, to: LocalDateTime): List<AiEventView>
}

data class Conflict(val a: AiEventView, val b: AiEventView, val overlapMinutes: Long)

data class FreeSlot(val start: LocalDateTime, val end: LocalDateTime) {
    val minutes: Long get() = Duration.between(start, end).toMinutes()
}

/**
 * The parts of the assistant that are arithmetic rather than generation.
 *
 * Summaries, clash detection and finding a free hour are all exactly computable from the
 * event table. Asking a 0.5B model to do them instead would be slower, need hundreds of
 * megabytes of RAM, and occasionally be wrong — for no benefit. Keeping them here means
 * these features work on every device, with the AI switched off, and with no model installed.
 * The model is reserved for what actually needs language: free-form questions and phrasing.
 */
object CalendarTools {

    /**
     * Every pair of overlapping timed events in the list.
     *
     * An event never clashes with itself. That is not a hypothetical: an event running past
     * midnight is carried onto the second day so it shows in both grids, and the assistant's
     * flat list then held the same event twice. On a device it read "Late 11pm ⟷ Late 11pm —
     * ជាន់គ្នា ៦០ នាទី", which is nonsense the moment you read it. Callers de-duplicate too,
     * but the invariant belongs here, where the claim is made.
     */
    fun conflicts(events: List<AiEventView>): List<Conflict> {
        val timed = events.filterNot { it.allDay }.sortedBy { it.start }
        val out = mutableListOf<Conflict>()
        for (i in timed.indices) {
            for (j in i + 1 until timed.size) {
                val a = timed[i]
                val b = timed[j]
                // Sorted by start, so once b begins after a ends nothing later can overlap a.
                if (!b.start.isBefore(a.end)) break
                if (a.id == b.id) continue
                val overlapEnd = minOf(a.end, b.end)
                val minutes = Duration.between(b.start, overlapEnd).toMinutes()
                if (minutes > 0) out += Conflict(a, b, minutes)
            }
        }
        return out
    }

    /** Events that clash with a proposed [start]..[end]. */
    fun clashesWith(existing: List<AiEventView>, start: LocalDateTime, end: LocalDateTime): List<AiEventView> =
        existing.filterNot { it.allDay }.filter { it.start < end && start < it.end }

    /**
     * Gaps of at least [minimumMinutes] on [date] inside the user's own day.
     *
     * The arithmetic is [FreeTime] in `:calendar-core`; this is the assistant's view of it.
     * It lives down there because the day view needs the same answer and must not have to go
     * through the AI module - which is optional and off by default - to get a subtraction.
     */
    fun freeSlots(
        events: List<AiEventView>,
        date: LocalDate,
        minimumMinutes: Long = 30,
        dayStart: LocalTime = LocalTime.of(8, 0),
        dayEnd: LocalTime = LocalTime.of(18, 0),
        notBefore: LocalDateTime? = null,
    ): List<FreeSlot> = FreeTime.onDay(
        busy = events.filterNot { it.allDay }.map { TimeSpan(it.start, it.end) },
        date = date,
        dayStart = dayStart,
        dayEnd = dayEnd,
        minimumMinutes = minimumMinutes,
        notBefore = notBefore,
    ).map { FreeSlot(it.start, it.end) }

    /** A Khmer one-line summary of a day, used in the assistant and in the widget. */
    fun describeDay(date: LocalDate, events: List<AiEventView>): String {
        val lunar = Chhankitek.toLunarOrNull(date)
        val head = buildString {
            append("ថ្ងៃ").append(KhmerTerms.dayOfWeek(date.dayOfWeek))
            append(" ទី").append(KhmerNumerals.toKhmer(date.dayOfMonth))
            append(" ខែ").append(KhmerTerms.solarMonth(date.monthValue))
            append(" ឆ្នាំ").append(KhmerNumerals.toKhmer(date.year))
            lunar?.let { append(" (").append(it.dayText).append(" ខែ").append(it.month.khmer).append(")") }
        }
        if (events.isEmpty()) return "$head — គ្មានព្រឹត្តិការណ៍"
        val lines = events.sortedBy { it.start }.joinToString("\n") { e ->
            val time = if (e.allDay) "ពេញមួយថ្ងៃ" else KhmerNumerals.toKhmer(
                "%02d:%02d".format(e.start.hour, e.start.minute)
            )
            "• $time — ${e.title}"
        }
        return "$head — ព្រឹត្តិការណ៍ ${KhmerNumerals.toKhmer(events.size)}\n$lines"
    }

    /** Compact context handed to the model when it is asked a free-form question. */
    fun contextBlock(events: List<AiEventView>, limit: Int = 40): String =
        events.sortedBy { it.start }.take(limit).joinToString("\n") { e ->
            val when_ = if (e.allDay) {
                "${e.start.toLocalDate()} (all day)"
            } else {
                "${e.start.toLocalDate()} ${"%02d:%02d".format(e.start.hour, e.start.minute)}" +
                    "-${"%02d:%02d".format(e.end.hour, e.end.minute)}"
            }
            "- $when_ | ${e.title}" + (e.location?.let { " | $it" } ?: "")
        }
}
