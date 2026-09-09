package com.khmercalendar.core.schedule

import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** A half-open stretch of time: [start] included, [end] excluded. */
data class TimeSpan(val start: LocalDateTime, val end: LocalDateTime) {
    val minutes: Long get() = Duration.between(start, end).toMinutes()

    fun overlaps(other: TimeSpan): Boolean = start < other.end && other.start < end
}

/**
 * When you are free.
 *
 * ## Why this is in `:calendar-core`
 *
 * It was in `:ai-core`, alongside the assistant's other arithmetic. Nothing about it is AI -
 * it is subtraction over intervals - but living there meant only the assistant could ask the
 * question, and the assistant is optional and off by default. Answering "when am I free" in
 * the day view had to either duplicate this or reach into the AI module for a calculation
 * that has nothing to do with a model. So the arithmetic moved down here with the rest of the
 * deterministic calendar logic, and `CalendarTools.freeSlots` now delegates to it.
 *
 * The general form takes intervals rather than events, so callers do not have to build an
 * assistant-shaped view of their own data to ask about it.
 */
object FreeTime {

    /**
     * The gaps of at least [minimumMinutes] inside [window] that [busy] does not cover.
     *
     * [busy] may arrive unsorted, overlapping, or reaching outside the window; all three
     * happen with real calendar data - a series expanded over a range comes back in start
     * order per series rather than overall, and a meeting that began before the window is
     * still occupying the start of it.
     */
    fun gaps(busy: List<TimeSpan>, window: TimeSpan, minimumMinutes: Long = 30): List<TimeSpan> {
        if (!window.start.isBefore(window.end)) return emptyList()

        val relevant = busy.filter { it.overlaps(window) }.sortedBy { it.start }
        val out = mutableListOf<TimeSpan>()
        var cursor = window.start

        for (span in relevant) {
            if (span.start.isAfter(cursor)) {
                val gapEnd = minOf(span.start, window.end)
                if (Duration.between(cursor, gapEnd).toMinutes() >= minimumMinutes) {
                    out += TimeSpan(cursor, gapEnd)
                }
            }
            // Only ever forwards: a short meeting nested inside a long one must not pull the
            // cursor back to the short one's end and invent a gap that is already booked.
            if (span.end.isAfter(cursor)) cursor = span.end
            if (!cursor.isBefore(window.end)) break
        }

        if (cursor.isBefore(window.end) &&
            Duration.between(cursor, window.end).toMinutes() >= minimumMinutes
        ) {
            out += TimeSpan(cursor, window.end)
        }
        return out
    }

    /**
     * The same, for one day between the user's own start and end of day.
     *
     * Bounded to a waking window on purpose: 02:00 is technically free, and offering it is
     * not an answer anybody wanted. The window comes from the caller because this module has
     * no access to preferences and should not acquire any.
     *
     * [notBefore] trims a moment that has already gone. Asked at three in the afternoon when
     * you are free today, the honest answer cannot begin with this morning.
     */
    fun onDay(
        busy: List<TimeSpan>,
        date: LocalDate,
        dayStart: LocalTime,
        dayEnd: LocalTime,
        minimumMinutes: Long = 30,
        notBefore: LocalDateTime? = null,
    ): List<TimeSpan> {
        val opens = LocalDateTime.of(date, dayStart)
        val closes = LocalDateTime.of(date, dayEnd)
        val start = notBefore
            ?.takeIf { it.toLocalDate() == date }
            ?.let { maxOf(opens, it) }
            ?: opens
        return gaps(busy, TimeSpan(start, closes), minimumMinutes)
    }
}
