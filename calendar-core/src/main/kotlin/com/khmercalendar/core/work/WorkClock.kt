package com.khmercalendar.core.work

import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Works out where in the working day a given instant falls.
 *
 * Pure and total: one function of (schedule, instant) with no clock of its own, no Android
 * dependency and no state. That is what lets every boundary - the minute work starts, the
 * minute lunch ends, midnight on a day off - be tested directly rather than by waiting for a
 * real clock to reach them.
 *
 * The UI ticks a timer and calls this; it does not compute anything itself.
 */
object WorkClock {

    /**
     * The status at [now].
     *
     * Blocks are sorted defensively rather than trusted to be in order: the schedule comes
     * from user settings, and a user who sets their afternoon block before their morning one
     * should get a sane countdown rather than a nonsensical one.
     */
    fun statusAt(schedule: WorkSchedule, now: LocalDateTime): WorkStatus {
        if (!schedule.enabled) {
            return WorkStatus(state = WorkState.DISABLED, now = now)
        }

        val day = schedule.dayOf(now.dayOfWeek)
        val total = day.totalDuration
        if (day.isOff) {
            return WorkStatus(state = WorkState.DAY_OFF, now = now, totalToday = total)
        }

        val blocks = day.blocks.sortedBy { it.start }
        val time = now.toLocalTime()
        val date = now.toLocalDate()

        val current = blocks.firstOrNull { it.contains(time) }
        val next = blocks.firstOrNull { it.start > time }

        // Work still scheduled today: whatever is left of the current block, plus every block
        // that has not started. Not the same as "time until the next transition".
        val remainingToday = blocks.fold(Duration.ZERO) { acc, block ->
            acc + when {
                block.end <= time -> Duration.ZERO
                block.start > time -> block.duration
                else -> Duration.between(time, block.end)
            }
        }

        return when {
            current != null -> WorkStatus(
                state = WorkState.WORKING,
                now = now,
                currentBlock = current,
                nextBlock = next,
                nextTransitionAt = LocalDateTime.of(date, current.end),
                remaining = Duration.between(time, current.end),
                progress = progressThrough(current.start, current.end, time),
                remainingToday = remainingToday,
                totalToday = total,
            )

            next != null && blocks.any { it.end <= time } -> WorkStatus(
                // Between blocks: lunch, or any other gap the schedule leaves.
                state = WorkState.BREAK,
                now = now,
                currentBlock = null,
                nextBlock = next,
                nextTransitionAt = LocalDateTime.of(date, next.start),
                remaining = Duration.between(time, next.start),
                progress = progressThrough(
                    start = blocks.last { it.end <= time }.end,
                    end = next.start,
                    at = time,
                ),
                remainingToday = remainingToday,
                totalToday = total,
            )

            next != null -> WorkStatus(
                state = WorkState.BEFORE_WORK,
                now = now,
                nextBlock = next,
                nextTransitionAt = LocalDateTime.of(date, next.start),
                remaining = Duration.between(time, next.start),
                progress = null,
                remainingToday = remainingToday,
                totalToday = total,
            )

            else -> WorkStatus(
                state = WorkState.FINISHED,
                now = now,
                nextTransitionAt = null,
                remaining = null,
                progress = null,
                remainingToday = Duration.ZERO,
                totalToday = total,
            )
        }
    }

    /**
     * The next moment the state changes, or null when nothing else happens today.
     *
     * Used to decide when to schedule a notification, and to let the UI stop ticking on a day
     * with nothing left to count down to.
     */
    fun nextTransition(schedule: WorkSchedule, now: LocalDateTime): LocalDateTime? =
        statusAt(schedule, now).nextTransitionAt

    /**
     * Every transition on [now]'s day, in order, as (instant, the state that begins).
     *
     * This is what the notification scheduler walks: it needs all of the day's boundaries at
     * once rather than one at a time.
     */
    fun transitionsFor(schedule: WorkSchedule, now: LocalDateTime): List<WorkTransition> {
        if (!schedule.enabled) return emptyList()
        val blocks = schedule.dayOf(now.dayOfWeek).blocks.sortedBy { it.start }
        if (blocks.isEmpty()) return emptyList()
        val date = now.toLocalDate()

        val out = ArrayList<WorkTransition>(blocks.size * 2)
        blocks.forEachIndexed { index, block ->
            out += WorkTransition(
                at = LocalDateTime.of(date, block.start),
                becomes = WorkState.WORKING,
                block = block,
            )
            val isLast = index == blocks.lastIndex
            out += WorkTransition(
                at = LocalDateTime.of(date, block.end),
                becomes = if (isLast) WorkState.FINISHED else WorkState.BREAK,
                block = block,
            )
        }
        return out.sortedBy { it.at }
    }

    /** Fraction of the way from [start] to [end], clamped, or null for a zero-length span. */
    private fun progressThrough(start: LocalTime, end: LocalTime, at: LocalTime): Float? {
        val span = Duration.between(start, end).seconds
        if (span <= 0) return null
        val done = Duration.between(start, at).seconds
        return (done.toFloat() / span.toFloat()).coerceIn(0f, 1f)
    }
}

/** A moment the working state changes. */
data class WorkTransition(
    val at: LocalDateTime,
    val becomes: WorkState,
    val block: WorkBlock,
)
