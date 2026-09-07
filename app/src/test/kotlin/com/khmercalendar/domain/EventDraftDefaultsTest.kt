package com.khmercalendar.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * The times a new event opens with, at the hours that used to go wrong.
 *
 * Two defects lived here, both from doing "the next whole hour" on a [LocalTime] with no date
 * attached: after 23:00 the start wrapped back to midnight *this morning*, and between 22:01
 * and 23:00 the end landed at 00:00, which the editor's own validation then read as earlier
 * than the start and refused to save.
 */
class EventDraftDefaultsTest {

    private val duration = 60
    private val today = LocalDate.of(2026, 9, 7)

    @Test
    fun `a mid-afternoon event starts on the next whole hour`() {
        val t = EventDraftDefaults.times(LocalDateTime.of(today, LocalTime.of(14, 22)), duration)

        assertEquals(today, t.date)
        assertEquals(LocalTime.of(15, 0), t.startTime)
        assertEquals(today, t.endDate)
        assertEquals(LocalTime.of(16, 0), t.endTime)
    }

    @Test
    fun `exactly on the hour still moves to the next one`() {
        val t = EventDraftDefaults.times(LocalDateTime.of(today, LocalTime.of(9, 0)), duration)

        assertEquals(LocalTime.of(10, 0), t.startTime)
        assertEquals(LocalTime.of(11, 0), t.endTime)
    }

    @Test
    fun `late evening rolls the end date forward instead of looking backwards`() {
        // 22:51: start 23:00, end midnight. The end date has to move, or the editor reads a
        // 23:00 start with a 00:00 end on the same day and calls it invalid.
        val t = EventDraftDefaults.times(LocalDateTime.of(today, LocalTime.of(22, 51)), duration)

        assertEquals(today, t.date)
        assertEquals(LocalTime.of(23, 0), t.startTime)
        assertEquals(today.plusDays(1), t.endDate)
        assertEquals(LocalTime.MIDNIGHT, t.endTime)
    }

    @Test
    fun `after eleven the whole event moves to tomorrow`() {
        // The old arithmetic wrapped to 00:00 and kept today's date, opening the editor on a
        // slot twenty-three hours in the past.
        val t = EventDraftDefaults.times(LocalDateTime.of(today, LocalTime.of(23, 20)), duration)

        assertEquals(today.plusDays(1), t.date)
        assertEquals(LocalTime.MIDNIGHT, t.startTime)
        assertEquals(today.plusDays(1), t.endDate)
        assertEquals(LocalTime.of(1, 0), t.endTime)
    }

    @Test
    fun `a date picked from the calendar grid wins over today`() {
        val picked = LocalDate.of(2026, 12, 25)
        val t = EventDraftDefaults.times(
            now = LocalDateTime.of(today, LocalTime.of(14, 22)),
            durationMinutes = duration,
            preferredDate = picked,
        )

        assertEquals(picked, t.date)
        assertEquals(picked, t.endDate)
        assertEquals(LocalTime.of(15, 0), t.startTime)
    }

    @Test
    fun `a picked date keeps its own end date when the duration crosses midnight`() {
        val picked = LocalDate.of(2026, 12, 25)
        val t = EventDraftDefaults.times(
            now = LocalDateTime.of(today, LocalTime.of(22, 51)),
            durationMinutes = duration,
            preferredDate = picked,
        )

        assertEquals(picked, t.date)
        assertEquals(picked.plusDays(1), t.endDate)
    }

    @Test
    fun `today passed in explicitly still rolls past midnight`() {
        // Every "add" button outside the calendar grid passes today's date, so the roll has
        // to survive that. Without this, the release build opened a new event at 00:00 this
        // morning when tapped at 23:07.
        val t = EventDraftDefaults.times(
            now = LocalDateTime.of(today, LocalTime.of(23, 7)),
            durationMinutes = duration,
            preferredDate = today,
        )

        assertEquals(today.plusDays(1), t.date)
        assertEquals(LocalTime.MIDNIGHT, t.startTime)
        assertEquals(today.plusDays(1), t.endDate)
        assertEquals(LocalTime.of(1, 0), t.endTime)
    }

    @Test
    fun `a past date chosen from the grid is left alone`() {
        val past = LocalDate.of(2026, 1, 2)
        val t = EventDraftDefaults.times(
            now = LocalDateTime.of(today, LocalTime.of(23, 7)),
            durationMinutes = duration,
            preferredDate = past,
        )

        assertEquals(past, t.date)
        assertEquals(LocalTime.MIDNIGHT, t.startTime)
    }

    @Test
    fun `a long default duration still lands on the right day`() {
        val t = EventDraftDefaults.times(
            now = LocalDateTime.of(today, LocalTime.of(21, 5)),
            durationMinutes = 8 * 60,
        )

        assertEquals(today, t.date)
        assertEquals(LocalTime.of(22, 0), t.startTime)
        assertEquals(today.plusDays(1), t.endDate)
        assertEquals(LocalTime.of(6, 0), t.endTime)
    }
}
