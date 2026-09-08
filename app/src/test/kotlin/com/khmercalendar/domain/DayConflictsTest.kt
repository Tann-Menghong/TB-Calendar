package com.khmercalendar.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** Double-booking, as the day view marks it. */
class DayConflictsTest {

    private val day = LocalDate.of(2026, 9, 8)

    private fun event(
        id: Long,
        from: String,
        to: String,
        allDay: Boolean = false,
        isTask: Boolean = false,
        isCompleted: Boolean = false,
    ) = EventOccurrence(
        eventId = id,
        title = "e$id",
        description = null,
        location = null,
        occurrenceDate = day,
        start = LocalDateTime.of(day, LocalTime.parse(from)),
        end = LocalDateTime.of(day, LocalTime.parse(to)),
        allDay = allDay,
        colorArgb = 0,
        categoryId = null,
        categoryName = null,
        isTask = isTask,
        isCompleted = isCompleted,
        isRecurring = false,
    )

    @Test
    fun `overlapping events are both flagged`() {
        val found = DayConflicts.overlapping(
            listOf(event(1, "09:00", "10:30"), event(2, "10:00", "11:00")),
        )
        assertEquals(setOf(1L, 2L), found)
    }

    @Test
    fun `back to back is not a conflict`() {
        val found = DayConflicts.overlapping(
            listOf(event(1, "09:00", "10:00"), event(2, "10:00", "11:00")),
        )
        assertTrue(found.isEmpty())
    }

    @Test
    fun `an event wholly inside another is a conflict`() {
        val found = DayConflicts.overlapping(
            listOf(event(1, "09:00", "17:00"), event(2, "11:00", "11:30")),
        )
        assertEquals(setOf(1L, 2L), found)
    }

    @Test
    fun `only the clashing pair is flagged, not the whole day`() {
        val found = DayConflicts.overlapping(
            listOf(event(1, "09:00", "10:00"), event(2, "11:00", "12:00"), event(3, "11:30", "13:00")),
        )
        assertEquals(setOf(2L, 3L), found)
    }

    @Test
    fun `all-day entries never clash`() {
        val found = DayConflicts.overlapping(
            listOf(event(1, "00:00", "23:59", allDay = true), event(2, "11:00", "12:00")),
        )
        assertTrue(found.isEmpty())
    }

    @Test
    fun `tasks and completed items are not competing for the time`() {
        val withTask = DayConflicts.overlapping(
            listOf(event(1, "09:00", "10:00"), event(2, "09:30", "09:30", isTask = true)),
        )
        assertTrue(withTask.isEmpty())

        val withDone = DayConflicts.overlapping(
            listOf(event(1, "09:00", "10:00"), event(2, "09:30", "10:30", isCompleted = true)),
        )
        assertTrue(withDone.isEmpty())
    }

    @Test
    fun `a zero-length event still clashes with one wrapped around it`() {
        val found = DayConflicts.overlapping(
            listOf(event(1, "09:00", "10:00"), event(2, "09:30", "09:30")),
        )
        assertEquals(setOf(1L, 2L), found)
    }

    @Test
    fun `a single event cannot conflict`() {
        assertTrue(DayConflicts.overlapping(listOf(event(1, "09:00", "10:00"))).isEmpty())
        assertTrue(DayConflicts.overlapping(emptyList()).isEmpty())
    }
}
