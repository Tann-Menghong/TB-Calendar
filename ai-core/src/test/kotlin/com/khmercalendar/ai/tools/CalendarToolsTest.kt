package com.khmercalendar.ai.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * The arithmetic half of the assistant.
 *
 * These answers are the ones the specification insists must never come from a model — clash
 * detection and free time are exactly computable, and a 0.5B model asked to do them would be
 * slower, hungrier and occasionally wrong. That makes them worth pinning precisely.
 */
class CalendarToolsTest {

    private val day = LocalDate.of(2026, 9, 8)

    private fun event(
        id: Long,
        from: String,
        to: String,
        title: String = "E$id",
        allDay: Boolean = false,
    ) = AiEventView(
        id = id,
        title = title,
        start = LocalDateTime.of(day, LocalTime.parse(from)),
        end = LocalDateTime.of(day, LocalTime.parse(to)),
        allDay = allDay,
    )

    // --- conflicts ---------------------------------------------------------------------

    @Test
    fun `partial overlap is reported with the overlapping minutes`() {
        val found = CalendarTools.conflicts(
            listOf(event(1, "09:00", "10:00"), event(2, "09:30", "10:30")),
        )

        assertEquals(1, found.size)
        assertEquals(30, found.single().overlapMinutes)
    }

    @Test
    fun `back-to-back events do not clash`() {
        // 09:00-10:00 followed by 10:00-11:00 is a full morning, not a double booking.
        val found = CalendarTools.conflicts(
            listOf(event(1, "09:00", "10:00"), event(2, "10:00", "11:00")),
        )

        assertTrue(found.isEmpty())
    }

    @Test
    fun `an event wholly inside another is reported at the inner length`() {
        val found = CalendarTools.conflicts(
            listOf(event(1, "09:00", "12:00"), event(2, "10:00", "10:45")),
        )

        assertEquals(45, found.single().overlapMinutes)
    }

    @Test
    fun `three overlapping events give three pairs`() {
        val found = CalendarTools.conflicts(
            listOf(event(1, "09:00", "11:00"), event(2, "09:30", "11:00"), event(3, "10:00", "11:00")),
        )

        assertEquals(3, found.size)
    }

    @Test
    fun `an all-day event does not clash with everything on the day`() {
        val found = CalendarTools.conflicts(
            listOf(event(1, "00:00", "23:59", allDay = true), event(2, "09:00", "10:00")),
        )

        assertTrue(found.isEmpty())
    }

    @Test
    fun `input order does not change the answer`() {
        val events = listOf(event(3, "10:00", "11:00"), event(1, "09:00", "10:30"), event(2, "14:00", "15:00"))

        assertEquals(1, CalendarTools.conflicts(events).size)
        assertEquals(1, CalendarTools.conflicts(events.reversed()).size)
    }

    @Test
    fun `an event never clashes with itself`() {
        // An event that runs past midnight is carried onto the second day so it shows in
        // both grids, and the assistant's flat list then held the same event twice. On a
        // device that read "Late 11pm clashes with Late 11pm for 60 minutes".
        val spansMidnight = event(1, "23:00", "23:59", title = "Late 11pm")
        val carriedOntoTheNextDay = spansMidnight.copy()

        assertTrue(CalendarTools.conflicts(listOf(spansMidnight, carriedOntoTheNextDay)).isEmpty())
    }

    @Test
    fun `two occurrences of one repeating series at the same hour do not clash`() {
        // Same id, different days: a weekly meeting is not in conflict with next week's.
        val monday = event(7, "09:00", "10:00", title = "Weekly")
        val tuesday = monday.copy(
            start = monday.start.plusDays(1),
            end = monday.end.plusDays(1),
        )

        assertTrue(CalendarTools.conflicts(listOf(monday, tuesday)).isEmpty())
    }

    // --- free slots --------------------------------------------------------------------

    @Test
    fun `a clear day is one slot spanning the availability window`() {
        val slots = CalendarTools.freeSlots(
            events = emptyList(),
            date = day,
            dayStart = LocalTime.of(8, 0),
            dayEnd = LocalTime.of(18, 0),
        )

        assertEquals(1, slots.size)
        assertEquals(LocalTime.of(8, 0), slots.single().start.toLocalTime())
        assertEquals(LocalTime.of(18, 0), slots.single().end.toLocalTime())
    }

    @Test
    fun `the availability window is honoured, not a hard-coded one`() {
        // The sliders in Appearance promised to drive this and drove nothing; every reader
        // of dayStartHour and dayEndHour was the settings screen editing its own value.
        val slots = CalendarTools.freeSlots(
            events = emptyList(),
            date = day,
            dayStart = LocalTime.of(6, 0),
            dayEnd = LocalTime.of(14, 0),
        )

        assertEquals(LocalTime.of(6, 0), slots.single().start.toLocalTime())
        assertEquals(LocalTime.of(14, 0), slots.single().end.toLocalTime())
    }

    @Test
    fun `hours already past are not offered as free`() {
        // "When am I free today", asked at three in the afternoon, cannot answer "this morning".
        val slots = CalendarTools.freeSlots(
            events = emptyList(),
            date = day,
            dayStart = LocalTime.of(8, 0),
            dayEnd = LocalTime.of(18, 0),
            notBefore = LocalDateTime.of(day, LocalTime.of(15, 20)),
        )

        assertEquals(LocalTime.of(15, 20), slots.single().start.toLocalTime())
    }

    @Test
    fun `a moment on another day does not trim the window`() {
        // Asking about tomorrow from today must give tomorrow in full.
        val slots = CalendarTools.freeSlots(
            events = emptyList(),
            date = day,
            dayStart = LocalTime.of(8, 0),
            dayEnd = LocalTime.of(18, 0),
            notBefore = LocalDateTime.of(day.minusDays(1), LocalTime.of(15, 20)),
        )

        assertEquals(LocalTime.of(8, 0), slots.single().start.toLocalTime())
    }

    @Test
    fun `once the window has passed there is nothing left to offer`() {
        val slots = CalendarTools.freeSlots(
            events = emptyList(),
            date = day,
            dayStart = LocalTime.of(8, 0),
            dayEnd = LocalTime.of(18, 0),
            notBefore = LocalDateTime.of(day, LocalTime.of(19, 0)),
        )

        assertTrue(slots.isEmpty())
    }

    @Test
    fun `gaps between meetings are found and short ones dropped`() {
        val slots = CalendarTools.freeSlots(
            events = listOf(event(1, "09:00", "10:00"), event(2, "10:20", "12:00")),
            date = day,
            minimumMinutes = 30,
            dayStart = LocalTime.of(8, 0),
            dayEnd = LocalTime.of(18, 0),
        )

        // 08:00-09:00 and 12:00-18:00 qualify; the twenty-minute gap at 10:00 does not.
        assertEquals(
            listOf(LocalTime.of(8, 0), LocalTime.of(12, 0)),
            slots.map { it.start.toLocalTime() },
        )
    }

    @Test
    fun `overlapping meetings do not produce a phantom gap between them`() {
        val slots = CalendarTools.freeSlots(
            events = listOf(event(1, "09:00", "13:00"), event(2, "10:00", "11:00")),
            date = day,
            minimumMinutes = 30,
            dayStart = LocalTime.of(8, 0),
            dayEnd = LocalTime.of(18, 0),
        )

        assertEquals(
            listOf(LocalTime.of(8, 0), LocalTime.of(13, 0)),
            slots.map { it.start.toLocalTime() },
        )
    }

    @Test
    fun `an event running past the window does not push a slot beyond it`() {
        val slots = CalendarTools.freeSlots(
            events = listOf(event(1, "16:00", "23:00")),
            date = day,
            minimumMinutes = 30,
            dayStart = LocalTime.of(8, 0),
            dayEnd = LocalTime.of(18, 0),
        )

        assertEquals(1, slots.size)
        assertEquals(LocalTime.of(16, 0), slots.single().end.toLocalTime())
    }
}
