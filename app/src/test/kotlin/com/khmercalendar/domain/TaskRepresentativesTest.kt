package com.khmercalendar.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.random.Random

/**
 * Which occurrence of each task a list shows.
 *
 * Every case feeds the occurrences in shuffled order, because that is how they really arrive:
 * grouped under a hash map of days. On the device, a daily chore whose today was ticked showed
 * up dated eleven months ahead, and ticking it ticked that day.
 */
class TaskRepresentativesTest {

    private val today = LocalDate.of(2026, 9, 11)

    private fun occurrence(id: Long, date: LocalDate, done: Boolean = false) = EventOccurrence(
        eventId = id,
        title = "task $id",
        description = null,
        location = null,
        occurrenceDate = date,
        start = date.atStartOfDay(),
        end = date.plusDays(1).atStartOfDay(),
        allDay = true,
        colorArgb = 0,
        categoryId = null,
        categoryName = null,
        isTask = true,
        isCompleted = done,
        isRecurring = true,
    )

    /** A daily task from the 8th to a year on, with the given days done. */
    private fun dailyChore(vararg doneDays: LocalDate): List<EventOccurrence> =
        (0L..365L).map { offset ->
            val date = LocalDate.of(2026, 9, 8).plusDays(offset)
            occurrence(1, date, done = date in doneDays)
        }

    private fun shuffled(list: List<EventOccurrence>, seed: Int) = list.shuffled(Random(seed))

    @Test
    fun `a daily chore done today is shown on tomorrow, whatever order it arrives in`() {
        repeat(20) { seed ->
            val shown = TaskBoard.representatives(shuffled(dailyChore(today), seed), today).single()
            assertEquals("seed $seed", today.plusDays(1), shown.occurrenceDate)
        }
    }

    @Test
    fun `a daily chore not yet done today is shown on today`() {
        repeat(20) { seed ->
            val shown = TaskBoard.representatives(shuffled(dailyChore(), seed), today).single()
            assertEquals("seed $seed", today, shown.occurrenceDate)
        }
    }

    @Test
    fun `with nothing left to do from today, the latest occurrence up to today is shown`() {
        val onlyPastAndToday = (0L..3L).map { occurrence(1, today.minusDays(it), done = true) }

        repeat(10) { seed ->
            val shown = TaskBoard.representatives(shuffled(onlyPastAndToday, seed), today).single()
            assertEquals(today, shown.occurrenceDate)
        }
    }

    @Test
    fun `an overdue one-off stays on its own day`() {
        val shown = TaskBoard.representatives(listOf(occurrence(2, today.minusDays(3))), today).single()

        assertEquals(today.minusDays(3), shown.occurrenceDate)
    }

    @Test
    fun `a task running past midnight is represented by its real start`() {
        val start = LocalDateTime.of(today, java.time.LocalTime.of(23, 0))
        val first = occurrence(3, today).copy(start = start, end = start.plusHours(2), allDay = false, isRecurring = false)
        val listedAgain = first.copy(occurrenceDate = today.plusDays(1))

        repeat(10) { seed ->
            val shown = TaskBoard.representatives(shuffled(listOf(first, listedAgain), seed), today).single()
            assertEquals(today, shown.occurrenceDate)
        }
    }

    @Test
    fun `each task gets exactly one row`() {
        val mixed = dailyChore(today) + listOf(occurrence(2, today.plusDays(4)), occurrence(4, today.minusDays(1)))

        val shown = TaskBoard.representatives(shuffled(mixed, 7), today)

        assertEquals(setOf(1L, 2L, 4L), shown.map { it.eventId }.toSet())
        assertEquals(3, shown.size)
    }
}
