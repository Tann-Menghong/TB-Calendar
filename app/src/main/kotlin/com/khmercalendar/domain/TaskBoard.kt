package com.khmercalendar.domain

import com.khmercalendar.core.khmer.CalendarWeek
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * How urgent a task is.
 *
 * Three levels, not five. A scale people actually use has to be one they can apply without
 * thinking - the difference between "high" and "normal" is a decision anyone can make in a
 * second, and the difference between a four and a three is not.
 *
 * [weight] sorts descending, and the stored value is the ordinal so an unset task reads as
 * [NORMAL] without a migration having to guess.
 */
enum class TaskPriority(val stored: Int, val labelKm: String) {
    NORMAL(0, "ធម្មតា"),
    HIGH(1, "សំខាន់"),
    LOW(2, "ទាប"),
    ;

    /** Sort order: high first, then normal, then low. Not the stored value. */
    val weight: Int
        get() = when (this) {
            HIGH -> 0
            NORMAL -> 1
            LOW -> 2
        }

    companion object {
        /**
         * Decodes a stored value.
         *
         * Anything unrecognised is [NORMAL]: a row written by a newer version, or a column
         * that defaulted, must not make a task disappear from a filter.
         */
        fun of(stored: Int?): TaskPriority =
            entries.firstOrNull { it.stored == stored } ?: NORMAL
    }
}

/**
 * Which section of the to-do list a task belongs in.
 *
 * The sections are relative to today rather than absolute dates, because a to-do list is read
 * as "what do I have to do now" and a date is only the answer to that question after you have
 * worked out what today is.
 */
enum class TaskBucket(val labelKm: String) {
    OVERDUE("ហួសកំណត់"),
    TODAY("ថ្ងៃនេះ"),
    TOMORROW("ថ្ងៃស្អែក"),
    THIS_WEEK("សប្តាហ៍នេះ"),
    LATER("ក្រោយៗ"),
    DONE("រួចរាល់"),
}

/** One section of the list, with its tasks already in the order they are drawn. */
data class TaskGroup(
    val bucket: TaskBucket,
    val tasks: List<TaskItem>,
)

/**
 * A task as the to-do list draws it.
 *
 * Carries the occurrence it came from rather than replacing it: a task in this app is a
 * calendar entry, so ticking one off, opening it, or moving it are the same operations the
 * calendar already has.
 */
data class TaskItem(
    val occurrence: EventOccurrence,
    val priority: TaskPriority,
) {
    val eventId: Long get() = occurrence.eventId
    val title: String get() = occurrence.title
    val date: LocalDate get() = occurrence.occurrenceDate
    val isCompleted: Boolean get() = occurrence.isCompleted
    val allDay: Boolean get() = occurrence.allDay
}

/**
 * The to-do list, as sorting.
 *
 * Kept pure and away from the screen because every decision in it is a judgement that is
 * easier to argue with in a test than on a phone: what counts as overdue, whether a task due
 * on Sunday is "this week" for someone whose week starts on Sunday, and what a finished task
 * does to the section it was in.
 */
object TaskBoard {

    /**
     * Which section [date] falls in.
     *
     * A completed task is [TaskBucket.DONE] wherever it is due. Leaving it in place with a
     * line through it is tidier on paper and worse in use: the section you are working
     * through stops shrinking as you work through it.
     *
     * @param weekStart the user's first day of the week, so "this week" ends where their
     *   week ends rather than on a Sunday somebody assumed.
     */
    fun bucketOf(
        date: LocalDate,
        today: LocalDate,
        weekStart: DayOfWeek,
        isCompleted: Boolean,
    ): TaskBucket = when {
        isCompleted -> TaskBucket.DONE
        date.isBefore(today) -> TaskBucket.OVERDUE
        date == today -> TaskBucket.TODAY
        date == today.plusDays(1) -> TaskBucket.TOMORROW
        !date.isAfter(CalendarWeek.startOfWeek(today, weekStart).plusDays(6)) -> TaskBucket.THIS_WEEK
        else -> TaskBucket.LATER
    }

    /**
     * Groups [tasks] into the sections, in the order they are drawn.
     *
     * Empty sections are dropped, with one exception: [TaskBucket.TODAY] is always present.
     * A to-do list whose first section is "ក្រោយៗ" reads as though today were already dealt
     * with, and an empty "ថ្ងៃនេះ" saying so is the more useful statement.
     */
    fun group(
        tasks: List<TaskItem>,
        today: LocalDate,
        weekStart: DayOfWeek,
        includeDone: Boolean = false,
    ): List<TaskGroup> {
        val byBucket = tasks
            .filter { includeDone || !it.isCompleted }
            .groupBy { bucketOf(it.date, today, weekStart, it.isCompleted) }

        return TaskBucket.entries.mapNotNull { bucket ->
            val inBucket = byBucket[bucket].orEmpty()
            when {
                inBucket.isNotEmpty() -> TaskGroup(bucket, sort(inBucket, bucket))
                bucket == TaskBucket.TODAY -> TaskGroup(bucket, emptyList())
                else -> null
            }
        }
    }

    /**
     * The order inside a section.
     *
     * Priority first, then the clock, then the title, so the list is stable: two tasks with
     * the same priority on the same day must not swap places between one recomposition and
     * the next.
     *
     * [TaskBucket.DONE] is the exception - it is ordered by date, newest first, because it is
     * a record of what happened rather than a queue of what to do.
     */
    private fun sort(tasks: List<TaskItem>, bucket: TaskBucket): List<TaskItem> =
        if (bucket == TaskBucket.DONE) {
            tasks.sortedWith(compareByDescending<TaskItem> { it.date }.thenBy { it.title })
        } else {
            tasks.sortedWith(
                compareBy<TaskItem> { it.priority.weight }
                    .thenBy { it.date }
                    .thenBy { it.allDay.not() }
                    .thenBy { it.occurrence.start }
                    .thenBy { it.title },
            )
        }

    /**
     * The tasks the dashboard's focus module should show, most pressing first.
     *
     * Overdue before today before the rest, and higher priority before lower inside each -
     * which is what "focus" has to mean if the module is going to earn its place. It
     * previously showed whichever three tasks the query happened to return first.
     */
    fun focus(tasks: List<TaskItem>, today: LocalDate, limit: Int): List<TaskItem> =
        tasks
            .filterNot { it.isCompleted }
            .sortedWith(
                compareBy<TaskItem> { if (it.date.isBefore(today)) 0 else 1 }
                    .thenBy { it.priority.weight }
                    .thenBy { it.date }
                    .thenBy { it.occurrence.start }
                    .thenBy { it.title },
            )
            .take(limit)

    /**
     * How much of the list is done, for the progress line at the top.
     *
     * [today] is passed in rather than read from the clock, so the count of overdue tasks is
     * something a test can state rather than something that changes at midnight while the
     * suite is running.
     */
    fun progress(tasks: List<TaskItem>, today: LocalDate): Progress = Progress(
        done = tasks.count { it.isCompleted },
        total = tasks.size,
        overdue = tasks.count { !it.isCompleted && it.date.isBefore(today) },
    )

    data class Progress(val done: Int, val total: Int, val overdue: Int) {
        /** 0f..1f, and 0f rather than a division by zero when there is nothing to do. */
        val fraction: Float get() = if (total <= 0) 0f else done.toFloat() / total.toFloat()
    }
}
