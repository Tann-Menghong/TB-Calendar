package com.khmercalendar.core.work

import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * One stretch of working time on one day.
 *
 * A day is a list of these rather than a fixed morning/afternoon pair. Most Cambodian offices
 * do work two blocks with a long lunch, but a shop might work one continuous block and a
 * clinic three, and modelling the common case as *the* case would make those unrepresentable.
 */
data class WorkBlock(
    val start: LocalTime,
    val end: LocalTime,
    /** Shown in the countdown: "ការងារពេលព្រឹក", "ការងារពេលរសៀល". */
    val labelKm: String,
) {
    init {
        require(end.isAfter(start)) { "A work block must end after it starts" }
    }

    val duration: Duration get() = Duration.between(start, end)

    fun contains(time: LocalTime): Boolean = !time.isBefore(start) && time.isBefore(end)
}

/** What a given weekday looks like. An empty [blocks] is a day off. */
data class WorkDay(val blocks: List<WorkBlock>) {

    val isOff: Boolean get() = blocks.isEmpty()

    val totalDuration: Duration
        get() = blocks.fold(Duration.ZERO) { acc, b -> acc.plus(b.duration) }

    companion object {
        val OFF = WorkDay(emptyList())
    }
}

/**
 * The user's working week.
 *
 * Defaults follow the ordinary Cambodian office week - 7:30 to 11:30, a two-hour lunch, then
 * 1:30 to 5:30, with a half-day Saturday and Sunday off. They are only defaults: the whole
 * point of storing this as data rather than constants is that a teacher, a shop owner and a
 * civil servant keep genuinely different hours.
 */
data class WorkSchedule(
    val days: Map<DayOfWeek, WorkDay>,
    val enabled: Boolean = true,
) {

    fun dayOf(day: DayOfWeek): WorkDay = days[day] ?: WorkDay.OFF

    /** Total scheduled working time on [day]. */
    fun totalFor(day: DayOfWeek): Duration = dayOf(day).totalDuration

    fun withDay(day: DayOfWeek, workDay: WorkDay): WorkSchedule =
        copy(days = days + (day to workDay))

    companion object {
        const val MORNING_KM = "ការងារពេលព្រឹក"
        const val AFTERNOON_KM = "ការងារពេលរសៀល"

        private val WEEKDAY = WorkDay(
            listOf(
                WorkBlock(LocalTime.of(7, 30), LocalTime.of(11, 30), MORNING_KM),
                WorkBlock(LocalTime.of(13, 30), LocalTime.of(17, 30), AFTERNOON_KM),
            ),
        )

        private val SATURDAY = WorkDay(
            listOf(WorkBlock(LocalTime.of(7, 30), LocalTime.of(11, 30), MORNING_KM)),
        )

        /** Monday–Friday 7:30–11:30 and 13:30–17:30, Saturday 7:30–11:30, Sunday off. */
        val DEFAULT = WorkSchedule(
            days = mapOf(
                DayOfWeek.MONDAY to WEEKDAY,
                DayOfWeek.TUESDAY to WEEKDAY,
                DayOfWeek.WEDNESDAY to WEEKDAY,
                DayOfWeek.THURSDAY to WEEKDAY,
                DayOfWeek.FRIDAY to WEEKDAY,
                DayOfWeek.SATURDAY to SATURDAY,
                DayOfWeek.SUNDAY to WorkDay.OFF,
            ),
            enabled = true,
        )
    }
}

/** Where the user is in their working day. */
enum class WorkState(val labelKm: String) {
    /** The day has work on it, but it has not started yet. */
    BEFORE_WORK("ត្រៀមចូលធ្វើការ"),
    WORKING("កំពុងធ្វើការ"),

    /** Between two work blocks - lunch, or any other gap the schedule leaves. */
    BREAK("ពេលសម្រាក"),

    /** The last block has ended. */
    FINISHED("ដល់ម៉ោងទៅផ្ទះ"),

    /** No work scheduled for this day at all. */
    DAY_OFF("ថ្ងៃឈប់សម្រាក"),

    /** The user has switched the countdown off. */
    DISABLED(""),
}

/**
 * A snapshot of the working day, computed for one instant.
 *
 * @property remaining Time until [nextTransitionAt]. Null when nothing is coming - the day is
 *   over, or is a day off.
 * @property progress 0..1 through the current block, or null outside one. A break has its own
 *   progress, which is what makes "45 minutes of lunch left" showable as a bar.
 * @property remainingToday Working time still scheduled today, across all remaining blocks.
 *   This is what answers "how much more work is there", which is not the same as "how long
 *   until the next thing happens".
 */
data class WorkStatus(
    val state: WorkState,
    val now: LocalDateTime,
    val currentBlock: WorkBlock? = null,
    val nextBlock: WorkBlock? = null,
    val nextTransitionAt: LocalDateTime? = null,
    val remaining: Duration? = null,
    val progress: Float? = null,
    val remainingToday: Duration = Duration.ZERO,
    val totalToday: Duration = Duration.ZERO,
) {
    val isWorking: Boolean get() = state == WorkState.WORKING

    /** True when the countdown has something live to tick. */
    val hasCountdown: Boolean get() = remaining != null && !remaining.isNegative
}
