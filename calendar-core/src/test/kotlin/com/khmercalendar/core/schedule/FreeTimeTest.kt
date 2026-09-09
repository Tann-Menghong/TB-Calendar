package com.khmercalendar.core.schedule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Interval subtraction, which is what "when am I free" actually is.
 *
 * The cases that matter are the untidy ones: real busy lists arrive unsorted, overlapping,
 * and reaching past both ends of the window. Each of those produces a plausible-looking but
 * wrong answer under a naive sweep, and a free slot the app offers had better be free.
 */
class FreeTimeTest {

    private val day = LocalDate.of(2026, 9, 9)

    private fun at(time: String) = LocalDateTime.of(day, LocalTime.parse(time))
    private fun span(from: String, to: String) = TimeSpan(at(from), at(to))
    private fun window(from: String = "08:00", to: String = "18:00") = span(from, to)

    private fun List<TimeSpan>.text() = joinToString(", ") {
        "${it.start.toLocalTime()}-${it.end.toLocalTime()}"
    }

    @Test
    fun `an empty day is one long gap`() {
        assertEquals("08:00-18:00", FreeTime.gaps(emptyList(), window()).text())
    }

    @Test
    fun `a meeting splits the day in two`() {
        val gaps = FreeTime.gaps(listOf(span("10:00", "11:00")), window())
        assertEquals("08:00-10:00, 11:00-18:00", gaps.text())
    }

    @Test
    fun `gaps shorter than the minimum are not offered`() {
        val gaps = FreeTime.gaps(
            busy = listOf(span("08:00", "09:00"), span("09:20", "18:00")),
            window = window(),
            minimumMinutes = 30,
        )
        assertTrue(gaps.text(), gaps.isEmpty())
    }

    @Test
    fun `an unsorted busy list gives the same answer as a sorted one`() {
        val busy = listOf(span("14:00", "15:00"), span("09:00", "10:00"), span("11:30", "12:00"))
        assertEquals(
            "08:00-09:00, 10:00-11:30, 12:00-14:00, 15:00-18:00",
            FreeTime.gaps(busy, window()).text(),
        )
    }

    @Test
    fun `a meeting nested inside a longer one does not invent a free gap`() {
        // The naive sweep sets the cursor to each event's end in turn; with the short meeting
        // second, that walks the cursor backwards and reports 11:00-17:00 as free.
        val busy = listOf(span("09:00", "17:00"), span("11:00", "11:30"))
        assertEquals("08:00-09:00, 17:00-18:00", FreeTime.gaps(busy, window()).text())
    }

    @Test
    fun `overlapping meetings are treated as one busy stretch`() {
        val busy = listOf(span("09:00", "11:00"), span("10:30", "12:00"))
        assertEquals("08:00-09:00, 12:00-18:00", FreeTime.gaps(busy, window()).text())
    }

    @Test
    fun `something that started before the window still occupies the start of it`() {
        val busy = listOf(TimeSpan(at("06:00"), at("09:30")))
        assertEquals("09:30-18:00", FreeTime.gaps(busy, window()).text())
    }

    @Test
    fun `a gap is clipped at the end of the window`() {
        val busy = listOf(span("09:00", "10:00"), TimeSpan(at("17:00"), at("23:00")))
        assertEquals("08:00-09:00, 10:00-17:00", FreeTime.gaps(busy, window()).text())
    }

    @Test
    fun `events wholly outside the window are ignored`() {
        val busy = listOf(span("06:00", "07:00"), TimeSpan(at("19:00"), at("20:00")))
        assertEquals("08:00-18:00", FreeTime.gaps(busy, window()).text())
    }

    @Test
    fun `a day booked end to end has no free time`() {
        assertTrue(FreeTime.gaps(listOf(span("08:00", "18:00")), window()).isEmpty())
    }

    @Test
    fun `an inverted or empty window is not free time`() {
        assertTrue(FreeTime.gaps(emptyList(), span("18:00", "08:00")).isEmpty())
        assertTrue(FreeTime.gaps(emptyList(), span("12:00", "12:00")).isEmpty())
    }

    @Test
    fun `onDay trims the part of today that has already gone`() {
        val gaps = FreeTime.onDay(
            busy = listOf(span("09:00", "10:00")),
            date = day,
            dayStart = LocalTime.of(8, 0),
            dayEnd = LocalTime.of(18, 0),
            notBefore = at("15:00"),
        )
        assertEquals("15:00-18:00", gaps.text())
    }

    @Test
    fun `onDay ignores a notBefore belonging to another day`() {
        val gaps = FreeTime.onDay(
            busy = emptyList(),
            date = day,
            dayStart = LocalTime.of(8, 0),
            dayEnd = LocalTime.of(18, 0),
            // Looking at tomorrow at three in the afternoon: the whole of tomorrow is free.
            notBefore = LocalDateTime.of(day.minusDays(1), LocalTime.of(15, 0)),
        )
        assertEquals("08:00-18:00", gaps.text())
    }

    @Test
    fun `nothing is left of a day whose remaining time is already booked`() {
        val gaps = FreeTime.onDay(
            busy = listOf(span("15:00", "18:00")),
            date = day,
            dayStart = LocalTime.of(8, 0),
            dayEnd = LocalTime.of(18, 0),
            notBefore = at("15:00"),
        )
        assertTrue(gaps.text(), gaps.isEmpty())
    }
}
