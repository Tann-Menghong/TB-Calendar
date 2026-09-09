package com.khmercalendar.domain

import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Which days a habit is expected on.
 *
 * ## Why not "every day" and nothing else
 *
 * "Read for ten minutes" is daily. "Go to the gym" is three particular days. "Call home" is
 * twice a week on no fixed day. Those are three genuinely different promises, and collapsing
 * them into one makes two of the three lie: a weekday habit marked missed on a Sunday is
 * telling the user they failed at something they never agreed to do.
 *
 * @property days for [Kind.DAYS], the weekdays it is due on.
 * @property target for [Kind.TIMES_PER_WEEK], how many times in a week.
 */
data class HabitSchedule(
    val kind: Kind,
    val days: Set<DayOfWeek> = emptySet(),
    val target: Int = 1,
) {
    enum class Kind(val stored: String, val labelKm: String) {
        DAILY("daily", "រៀងរាល់ថ្ងៃ"),
        DAYS("days", "ថ្ងៃជាក់លាក់"),
        TIMES_PER_WEEK("weekly", "ប៉ុន្មានដងក្នុងសប្តាហ៍"),
    }

    /** Whether [date] is a day this habit is expected on. */
    fun isDue(date: LocalDate): Boolean = when (kind) {
        Kind.DAILY -> true
        Kind.DAYS -> date.dayOfWeek in days
        // A "twice a week" habit has no particular day, so every day is an opportunity and
        // none is a failure. Its progress is counted per week instead - see [weeklyProgress].
        Kind.TIMES_PER_WEEK -> true
    }

    /** Whether missing [date] should end a streak. */
    fun breaksStreakIfMissed(date: LocalDate): Boolean = when (kind) {
        Kind.DAILY -> true
        Kind.DAYS -> date.dayOfWeek in days
        // Never, day by day: you cannot fail a weekly target on a Tuesday.
        Kind.TIMES_PER_WEEK -> false
    }

    companion object {
        val DEFAULT = HabitSchedule(Kind.DAILY)

        fun kindOf(stored: String?): Kind =
            Kind.entries.firstOrNull { it.stored == stored } ?: Kind.DAILY

        /** Weekdays encoded as "1,2,3" (Monday = 1), which is what the column stores. */
        fun encodeDays(days: Set<DayOfWeek>): String =
            days.sortedBy { it.value }.joinToString(",") { it.value.toString() }

        fun decodeDays(raw: String?): Set<DayOfWeek> = raw
            ?.split(",")
            ?.mapNotNull { part -> part.trim().toIntOrNull()?.takeIf { it in 1..7 } }
            ?.map { DayOfWeek.of(it) }
            ?.toSet()
            .orEmpty()
    }
}

/** A habit and everything the UI needs to draw one row of it. */
data class Habit(
    val id: Long,
    val name: String,
    val colorArgb: Int,
    val schedule: HabitSchedule,
    val isArchived: Boolean = false,
)

/**
 * Streaks, and the arithmetic that makes them honest.
 *
 * ## Why this is worth testing rather than eyeballing
 *
 * A streak is a number the user is asked to care about, so being wrong by one is the whole
 * failure. Every interesting case is a boundary: a habit done today but not yesterday, a
 * weekday habit over a weekend, a habit whose last completion was the day the streak should
 * have ended. None of those is visible by looking at a list of ticks.
 *
 * ## Why today never breaks a streak
 *
 * The day is not over. A daily streak that resets at midnight and shows zero until you tick
 * it is punishing people for the time of day they opened the app - so an unticked *today* is
 * simply not counted, and the streak reads as it stood last night.
 */
object HabitStreaks {

    /**
     * How many scheduled days in a row, ending today or yesterday, were completed.
     *
     * Walks backwards from [today], skipping days the habit was never due on, and stops at
     * the first scheduled day that was missed.
     */
    fun current(schedule: HabitSchedule, done: Set<LocalDate>, today: LocalDate): Int {
        var streak = 0
        var date = today
        var scanned = 0

        while (scanned < MAX_SCAN) {
            scanned++
            when {
                date in done -> streak++

                // Today is allowed to be unfinished; the day is not over yet.
                date == today -> Unit

                schedule.breaksStreakIfMissed(date) -> return streak

                else -> Unit  // Not due, and not done: the streak steps over it.
            }
            date = date.minusDays(1)
        }
        return streak
    }

    /** The longest run of scheduled days ever completed, for the line under the streak. */
    fun longest(schedule: HabitSchedule, done: Set<LocalDate>): Int {
        if (done.isEmpty()) return 0
        val ordered = done.sorted()
        var best = 0
        var run = 0
        var cursor = ordered.first()
        val last = ordered.last()

        while (!cursor.isAfter(last)) {
            when {
                cursor in done -> {
                    run++
                    if (run > best) best = run
                }

                schedule.breaksStreakIfMissed(cursor) -> run = 0

                else -> Unit
            }
            cursor = cursor.plusDays(1)
        }
        return best
    }

    /**
     * How many times this week, against the target, for a [HabitSchedule.Kind.TIMES_PER_WEEK].
     *
     * @param weekStart the user's own first day of the week, so "this week" means the week
     *   they see everywhere else in the app rather than an ISO one.
     */
    fun weeklyProgress(
        done: Set<LocalDate>,
        today: LocalDate,
        weekStart: DayOfWeek,
    ): Int {
        val start = com.khmercalendar.core.khmer.CalendarWeek.startOfWeek(today, weekStart)
        val end = start.plusDays(6)
        return done.count { !it.isBefore(start) && !it.isAfter(end) }
    }

    /**
     * The share of scheduled days completed over the last [days] days, as a percentage.
     *
     * Counted against days the habit was actually *due*, so a three-day-a-week habit is not
     * reported at 43% for doing exactly what it promised. Today is excluded for the same
     * reason it cannot break a streak.
     */
    fun completionRate(
        schedule: HabitSchedule,
        done: Set<LocalDate>,
        today: LocalDate,
        days: Int = 30,
    ): Int {
        var due = 0
        var completed = 0
        for (offset in 1..days) {
            val date = today.minusDays(offset.toLong())
            if (!schedule.isDue(date)) continue
            due++
            if (date in done) completed++
        }
        return if (due == 0) 0 else (completed * 100) / due
    }

    /**
     * Far enough back to find any streak worth showing, and bounded so a corrupt date cannot
     * spin forever. Ten years of daily ticks is a streak nobody needs counted exactly.
     */
    private const val MAX_SCAN = 3_700
}

/**
 * Today's habits, in the order they should be shown.
 *
 * Undone first: the list exists to be cleared, and a screen that opens with what is already
 * done at the top buries the thing it is for. Within each group, by name, so the order is
 * stable rather than arbitrary.
 *
 * The cost is real and worth stating: ticking a habit moves it down, so a second tap in the
 * same spot lands on a different row. That is the conventional behaviour for a checklist and
 * the one that keeps the remaining work at the top, but it does mean the list is not safe to
 * tap through blindly.
 */
object HabitBoard {

    fun order(habits: List<Habit>, done: Set<Long>): List<Habit> = habits
        .filterNot { it.isArchived }
        .sortedWith(compareBy({ it.id in done }, { it.name }))

    /** The habits actually expected today, which is what the dashboard card counts. */
    fun dueToday(habits: List<Habit>, today: LocalDate): List<Habit> =
        habits.filterNot { it.isArchived }.filter { it.schedule.isDue(today) }
}
