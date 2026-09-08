package com.khmercalendar.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * The to-do list's sorting, without a device.
 *
 * Every judgement in it is one that is easier to argue with here than on a phone: what counts
 * as overdue, where a task lands for someone whose week starts on Sunday, and what a finished
 * task does to the section it was in.
 */
class TaskBoardTest {

    // A Wednesday, so "this week" has days on both sides of it under either week start.
    private val today = LocalDate.of(2026, 9, 9)

    private fun task(
        id: Long,
        date: LocalDate,
        title: String = "T$id",
        priority: TaskPriority = TaskPriority.NORMAL,
        done: Boolean = false,
        allDay: Boolean = true,
        at: String = "09:00",
    ) = TaskItem(
        occurrence = EventOccurrence(
            eventId = id,
            title = title,
            description = null,
            location = null,
            occurrenceDate = date,
            start = LocalDateTime.of(date, LocalTime.parse(at)),
            end = LocalDateTime.of(date, LocalTime.parse(at)).plusHours(1),
            allDay = allDay,
            colorArgb = 0,
            categoryId = null,
            categoryName = null,
            isTask = true,
            isCompleted = done,
            isRecurring = false,
            priority = priority.stored,
        ),
        priority = priority,
    )

    // --- buckets -------------------------------------------------------------------------

    @Test
    fun `each bucket takes the days it says it does`() {
        fun bucket(date: LocalDate) = TaskBoard.bucketOf(date, today, DayOfWeek.MONDAY, false)

        assertEquals(TaskBucket.OVERDUE, bucket(today.minusDays(1)))
        assertEquals(TaskBucket.TODAY, bucket(today))
        assertEquals(TaskBucket.TOMORROW, bucket(today.plusDays(1)))
        // Friday and Sunday are both still inside a Monday-start week.
        assertEquals(TaskBucket.THIS_WEEK, bucket(today.plusDays(2)))
        assertEquals(TaskBucket.THIS_WEEK, bucket(LocalDate.of(2026, 9, 13)))
        assertEquals(TaskBucket.LATER, bucket(LocalDate.of(2026, 9, 14)))
    }

    @Test
    fun `this week ends where the user's week ends`() {
        // Sunday 13 September is the last day of a Monday-start week and the *first* day of a
        // Sunday-start one. Hard-coding either is the bug this test exists to catch.
        val sunday = LocalDate.of(2026, 9, 13)

        assertEquals(TaskBucket.THIS_WEEK, TaskBoard.bucketOf(sunday, today, DayOfWeek.MONDAY, false))
        assertEquals(TaskBucket.LATER, TaskBoard.bucketOf(sunday, today, DayOfWeek.SUNDAY, false))
    }

    @Test
    fun `a completed task leaves its section whatever it was due`() {
        assertEquals(
            TaskBucket.DONE,
            TaskBoard.bucketOf(today.minusDays(3), today, DayOfWeek.MONDAY, isCompleted = true),
        )
        assertEquals(
            TaskBucket.DONE,
            TaskBoard.bucketOf(today, today, DayOfWeek.MONDAY, isCompleted = true),
        )
    }

    // --- grouping ------------------------------------------------------------------------

    @Test
    fun `sections come out in the order they are read`() {
        val groups = TaskBoard.group(
            listOf(
                task(1, today.plusDays(30)),
                task(2, today.minusDays(1)),
                task(3, today),
                task(4, today.plusDays(1)),
                task(5, today.plusDays(3)),
            ),
            today,
            DayOfWeek.MONDAY,
        )

        assertEquals(
            listOf(
                TaskBucket.OVERDUE,
                TaskBucket.TODAY,
                TaskBucket.TOMORROW,
                TaskBucket.THIS_WEEK,
                TaskBucket.LATER,
            ),
            groups.map { it.bucket },
        )
    }

    @Test
    fun `today is always a section, even with nothing in it`() {
        // A list whose first heading is "ក្រោយៗ" reads as though today were already dealt
        // with. An empty "ថ្ងៃនេះ" saying so is the more useful statement.
        val groups = TaskBoard.group(listOf(task(1, today.plusDays(30))), today, DayOfWeek.MONDAY)

        assertEquals(TaskBucket.TODAY, groups.first().bucket)
        assertTrue(groups.first().tasks.isEmpty())
    }

    @Test
    fun `empty sections other than today are dropped`() {
        val groups = TaskBoard.group(listOf(task(1, today)), today, DayOfWeek.MONDAY)

        assertEquals(listOf(TaskBucket.TODAY), groups.map { it.bucket })
    }

    @Test
    fun `completed tasks are left out until they are asked for`() {
        val tasks = listOf(task(1, today), task(2, today, done = true))

        assertEquals(
            listOf(TaskBucket.TODAY),
            TaskBoard.group(tasks, today, DayOfWeek.MONDAY).map { it.bucket },
        )
        assertEquals(
            listOf(TaskBucket.TODAY, TaskBucket.DONE),
            TaskBoard.group(tasks, today, DayOfWeek.MONDAY, includeDone = true).map { it.bucket },
        )
    }

    // --- ordering ------------------------------------------------------------------------

    @Test
    fun `inside a section, urgency wins over the clock`() {
        val groups = TaskBoard.group(
            listOf(
                task(1, today, "low", TaskPriority.LOW, at = "08:00", allDay = false),
                task(2, today, "normal", TaskPriority.NORMAL, at = "09:00", allDay = false),
                task(3, today, "high", TaskPriority.HIGH, at = "17:00", allDay = false),
            ),
            today,
            DayOfWeek.MONDAY,
        )

        assertEquals(listOf("high", "normal", "low"), groups.single().tasks.map { it.title })
    }

    @Test
    fun `two tasks the same in every way keep a stable order`() {
        // Without a final tiebreak the list can swap rows between recompositions, which reads
        // as the app rearranging itself while you look at it.
        val tasks = listOf(task(1, today, "beta"), task(2, today, "alpha"))
        val once = TaskBoard.group(tasks, today, DayOfWeek.MONDAY).single().tasks.map { it.title }
        val twice = TaskBoard.group(tasks.reversed(), today, DayOfWeek.MONDAY)
            .single().tasks.map { it.title }

        assertEquals(listOf("alpha", "beta"), once)
        assertEquals(once, twice)
    }

    @Test
    fun `the done section reads newest first`() {
        val groups = TaskBoard.group(
            listOf(
                task(1, today.minusDays(5), "older", done = true),
                task(2, today.minusDays(1), "newer", done = true),
            ),
            today,
            DayOfWeek.MONDAY,
            includeDone = true,
        )

        assertEquals(listOf("newer", "older"), groups.single { it.bucket == TaskBucket.DONE }.tasks.map { it.title })
    }

    // --- the dashboard's focus list ------------------------------------------------------

    @Test
    fun `focus puts overdue work first, then urgency`() {
        val picked = TaskBoard.focus(
            listOf(
                task(1, today, "today high", TaskPriority.HIGH),
                task(2, today.minusDays(2), "overdue normal"),
                task(3, today.plusDays(1), "tomorrow high", TaskPriority.HIGH),
                task(4, today, "today normal"),
            ),
            today,
            limit = 3,
        )

        assertEquals(listOf("overdue normal", "today high", "tomorrow high"), picked.map { it.title })
    }

    @Test
    fun `focus never offers something already done`() {
        val picked = TaskBoard.focus(
            listOf(task(1, today, "done", TaskPriority.HIGH, done = true), task(2, today, "open")),
            today,
            limit = 3,
        )

        assertEquals(listOf("open"), picked.map { it.title })
    }

    // --- priority decoding ---------------------------------------------------------------

    @Test
    fun `an unknown stored priority reads as normal rather than vanishing`() {
        // The column defaults to 0 for every row that existed before it, and a value written
        // by a newer version must not make a task unsortable.
        assertEquals(TaskPriority.NORMAL, TaskPriority.of(null))
        assertEquals(TaskPriority.NORMAL, TaskPriority.of(0))
        assertEquals(TaskPriority.NORMAL, TaskPriority.of(99))
        assertEquals(TaskPriority.HIGH, TaskPriority.of(1))
        assertEquals(TaskPriority.LOW, TaskPriority.of(2))
    }

    @Test
    fun `progress counts what is done and what is late`() {
        val progress = TaskBoard.progress(
            listOf(
                task(1, today, done = true),
                task(2, today),
                task(3, today.minusDays(1)),
                task(4, today.minusDays(4), done = true),
            ),
            today,
        )

        assertEquals(2, progress.done)
        assertEquals(4, progress.total)
        assertEquals(1, progress.overdue)
        assertEquals(0.5f, progress.fraction, 0.001f)
    }

    @Test
    fun `an empty list is not a division by zero`() {
        assertEquals(0f, TaskBoard.progress(emptyList(), today).fraction, 0.001f)
    }
}
