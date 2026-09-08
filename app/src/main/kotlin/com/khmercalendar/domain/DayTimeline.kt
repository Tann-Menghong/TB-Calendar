package com.khmercalendar.domain

import com.khmercalendar.core.work.WorkSchedule
import java.time.LocalDate
import java.time.LocalTime

/**
 * One moment on the day's timeline.
 *
 * A timeline entry is not the same thing as an event. The working day's own transitions -
 * clocking on, lunch, going home - are the spine most people's day actually hangs on, and
 * they came from the work schedule rather than from the calendar. Putting both on one line
 * is the point: an 11:00 meeting means something different when lunch starts at 11:30.
 */
data class TimelineEntry(
    val at: LocalTime,
    val title: String,
    val kind: Kind,
    val subtitle: String? = null,
    /**
     * A time the subtitle needs to mention, left unformatted.
     *
     * The builder must not render a clock time itself: it has no access to the user's 12/24
     * hour preference or their choice of numerals, and a hard-coded "13:30" sat inside a
     * timeline where every other time read "១:៣០ រសៀល". Formatting belongs to the composable.
     */
    val subtitleAt: LocalTime? = null,
    /** Present only for calendar entries, so a tap can open the event. */
    val eventId: Long? = null,
    val date: LocalDate? = null,
    val colorArgb: Int? = null,
    val done: Boolean = false,
) {
    enum class Kind { WORK_START, BREAK, WORK_END, EVENT, TASK }
}

/**
 * Builds the day's timeline from the work schedule and the day's events.
 *
 * Pure, and separate from the composable that draws it, so every awkward shape - a day off, a
 * single unbroken shift, an event during lunch, an all-day entry with no clock time - can be
 * tested without a device.
 */
object DayTimeline {

    /**
     * @param date the day being shown, used to tell today from any other day.
     * @param events that day's occurrences, in any order.
     * @param schedule the user's working week; contributes nothing when it is switched off.
     */
    fun build(
        date: LocalDate,
        events: List<EventOccurrence>,
        schedule: WorkSchedule,
    ): List<TimelineEntry> {
        val entries = mutableListOf<TimelineEntry>()

        if (schedule.enabled) {
            val blocks = schedule.dayOf(date.dayOfWeek).blocks.sortedBy { it.start }
            blocks.forEachIndexed { index, block ->
                entries += TimelineEntry(
                    at = block.start,
                    title = if (index == 0) "ចូលធ្វើការ" else "ចូលធ្វើការវិញ",
                    subtitle = block.labelKm,
                    kind = TimelineEntry.Kind.WORK_START,
                )
                val next = blocks.getOrNull(index + 1)
                if (next != null) {
                    // The gap between two blocks. Named as a break rather than as "work ends",
                    // because the day is not over and saying so would be wrong.
                    entries += TimelineEntry(
                        at = block.end,
                        title = "ពេលសម្រាក",
                        subtitleAt = next.start,
                        kind = TimelineEntry.Kind.BREAK,
                    )
                } else {
                    entries += TimelineEntry(
                        at = block.end,
                        title = "ទៅផ្ទះ",
                        kind = TimelineEntry.Kind.WORK_END,
                    )
                }
            }
        }

        // All-day entries have no place on a clock, so they are left to the cards above the
        // timeline rather than being pinned to an arbitrary hour.
        events.filterNot { it.allDay }.forEach { event ->
            entries += TimelineEntry(
                at = event.start.toLocalTime(),
                title = event.title,
                subtitle = event.location?.takeIf { it.isNotBlank() },
                kind = if (event.isTask) TimelineEntry.Kind.TASK else TimelineEntry.Kind.EVENT,
                eventId = event.eventId,
                date = event.occurrenceDate,
                colorArgb = event.colorArgb,
                done = event.isCompleted,
            )
        }

        // Events before schedule markers at the same minute: a meeting at 07:30 is the thing
        // the user needs to see, and "work starts" is the context around it.
        return entries.sortedWith(
            compareBy({ it.at }, { if (it.kind == TimelineEntry.Kind.EVENT) 0 else 1 }),
        )
    }

    /**
     * Where the "now" marker goes: the number of entries that have already passed.
     *
     * Returned as an index rather than a time so the caller can both draw the line and scroll
     * to it without repeating the comparison.
     */
    fun nowIndex(entries: List<TimelineEntry>, now: LocalTime): Int =
        entries.count { it.at <= now }
}
