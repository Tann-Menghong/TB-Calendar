package com.khmercalendar.domain

/**
 * Which of a day's events are double-booked.
 *
 * The assistant could already answer "find my conflicts" on request. That is the wrong moment
 * to find out: a clash matters when you are looking at the day it happens on, not when you
 * think to ask. This marks them where they are drawn.
 *
 * ## What counts
 *
 * Two timed events that share any part of a minute. Deliberately not counted:
 *
 * - **All-day entries.** An all-day event covers every other event on the day, so flagging it
 *   would mark the whole screen and mean nothing.
 * - **Completed items.** A meeting you have already ticked off is not competing for the time.
 * - **Tasks.** A to-do due at three o'clock is not a booking at three o'clock, and treating it
 *   as one would flag every deadline that happened to land during a meeting.
 *
 * Back-to-back is not a conflict: an event ending at 10:00 and one starting at 10:00 do not
 * overlap. An event with no duration is treated as occupying one minute, so that a stray
 * zero-length row still clashes with something wrapped around it instead of silently
 * overlapping nothing.
 */
object DayConflicts {

    /** The ids of every event on the day that overlaps at least one other. */
    fun overlapping(events: List<EventOccurrence>): Set<Long> {
        val candidates = events.filter { !it.allDay && !it.isCompleted && !it.isTask }
        if (candidates.size < 2) return emptySet()

        val clashing = mutableSetOf<Long>()
        for (i in candidates.indices) {
            for (j in i + 1 until candidates.size) {
                val a = candidates[i]
                val b = candidates[j]
                if (a.eventId == b.eventId) continue
                if (overlaps(a, b)) {
                    clashing += a.eventId
                    clashing += b.eventId
                }
            }
        }
        return clashing
    }

    private fun overlaps(a: EventOccurrence, b: EventOccurrence): Boolean {
        val aEnd = maxOf(a.end, a.start.plusMinutes(1))
        val bEnd = maxOf(b.end, b.start.plusMinutes(1))
        return a.start.isBefore(bEnd) && b.start.isBefore(aEnd)
    }
}
