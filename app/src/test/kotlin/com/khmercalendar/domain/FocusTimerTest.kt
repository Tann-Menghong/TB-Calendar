package com.khmercalendar.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * The focus timer.
 *
 * Nothing here counts down in memory - a session is a start time and a planned length, and
 * every figure is derived from the clock. That makes the whole thing testable without waiting
 * for real seconds to pass, which is the point: the cases that matter are a session left
 * running overnight, one stopped early, and one that crosses midnight, and none of those can
 * be observed by watching a timer for a minute.
 */
class FocusTimerTest {

    private val zone: ZoneId = ZoneId.of("Asia/Phnom_Penh")
    private val day = LocalDate.of(2026, 9, 9)

    private fun millis(hour: Int, minute: Int, date: LocalDate = day): Long =
        LocalDateTime.of(date, java.time.LocalTime.of(hour, minute))
            .atZone(zone).toInstant().toEpochMilli()

    private fun session(
        kind: FocusKind = FocusKind.FOCUS,
        startHour: Int = 9,
        startMinute: Int = 0,
        planned: Int = 25,
        endedAt: Long? = null,
        startDate: LocalDate = day,
    ) = FocusSession(
        id = 1,
        kind = kind,
        startedAtMillis = millis(startHour, startMinute, startDate),
        plannedMinutes = planned,
        endedAtMillis = endedAt,
        eventId = null,
    )

    // --- countdown ---

    @Test
    fun `remaining counts down from the planned length`() {
        val s = session(planned = 25)
        assertEquals(25 * 60_000L, FocusTimer.remainingMillis(s, millis(9, 0)))
        assertEquals(15 * 60_000L, FocusTimer.remainingMillis(s, millis(9, 10)))
    }

    @Test
    fun `remaining never goes negative`() {
        val s = session(planned = 25)
        assertEquals(0L, FocusTimer.remainingMillis(s, millis(23, 0)))
        assertTrue(FocusTimer.isOver(s, millis(23, 0)))
        assertFalse(FocusTimer.isOver(s, millis(9, 24)))
    }

    @Test
    fun `progress runs zero to one and is clamped at both ends`() {
        val s = session(planned = 20)
        assertEquals(0f, FocusTimer.progress(s, millis(9, 0)), 0.001f)
        assertEquals(0.5f, FocusTimer.progress(s, millis(9, 10)), 0.001f)
        assertEquals(1f, FocusTimer.progress(s, millis(12, 0)), 0.001f)
        // A clock that jumped backwards must not produce a negative ring.
        assertEquals(0f, FocusTimer.progress(s, millis(8, 0)), 0.001f)
    }

    @Test
    fun `a zero-length session is simply over`() {
        val s = session(planned = 0)
        assertEquals(1f, FocusTimer.progress(s, millis(9, 0)), 0.001f)
        assertTrue(FocusTimer.isOver(s, millis(9, 0)))
    }

    @Test
    fun `the countdown is written as minutes and seconds`() {
        assertEquals("25:00", FocusTimer.format(25 * 60_000L))
        assertEquals("04:09", FocusTimer.format(249_000L))
        assertEquals("00:00", FocusTimer.format(0L))
        assertEquals("00:00", FocusTimer.format(-5_000L))
    }

    // --- elapsed ---

    @Test
    fun `a session stopped early reports the time it really ran`() {
        val s = session(planned = 25, endedAt = millis(9, 12))
        assertEquals(12, FocusTimer.elapsedMinutes(s))
    }

    @Test
    fun `a session left running overnight is capped at what was planned`() {
        // Nine hours of wall clock; the honest figure is the twenty-five that were planned.
        val s = session(planned = 25, endedAt = millis(18, 0))
        assertEquals(25, FocusTimer.elapsedMinutes(s))
    }

    @Test
    fun `a running session has contributed nothing yet`() {
        assertEquals(0, FocusTimer.elapsedMinutes(session()))
    }

    @Test
    fun `a clock that went backwards does not produce negative minutes`() {
        val s = session(startHour = 9, planned = 25, endedAt = millis(8, 0))
        assertEquals(0, FocusTimer.elapsedMinutes(s))
    }

    // --- the cycle ---

    @Test
    fun `a break always leads back to focus`() {
        val settings = FocusSettings()
        assertEquals(FocusKind.FOCUS, FocusTimer.next(FocusKind.SHORT_BREAK, 3, settings))
        assertEquals(FocusKind.FOCUS, FocusTimer.next(FocusKind.LONG_BREAK, 4, settings))
    }

    @Test
    fun `every fourth focus earns the long break`() {
        val settings = FocusSettings(sessionsBeforeLongBreak = 4)
        assertEquals(FocusKind.SHORT_BREAK, FocusTimer.next(FocusKind.FOCUS, 1, settings))
        assertEquals(FocusKind.SHORT_BREAK, FocusTimer.next(FocusKind.FOCUS, 3, settings))
        assertEquals(FocusKind.LONG_BREAK, FocusTimer.next(FocusKind.FOCUS, 4, settings))
        assertEquals(FocusKind.LONG_BREAK, FocusTimer.next(FocusKind.FOCUS, 8, settings))
    }

    @Test
    fun `the very first focus of the day leads to a short break`() {
        // focusesToday is 0 before any has completed; that must not read as "a multiple of 4".
        assertEquals(FocusKind.SHORT_BREAK, FocusTimer.next(FocusKind.FOCUS, 0, FocusSettings()))
    }

    @Test
    fun `a cycle length of zero does not divide by zero`() {
        val settings = FocusSettings(sessionsBeforeLongBreak = 0)
        assertEquals(FocusKind.LONG_BREAK, FocusTimer.next(FocusKind.FOCUS, 3, settings))
    }

    // --- statistics ---

    @Test
    fun `only completed focus sessions count towards a day`() {
        val sessions = listOf(
            session(planned = 25, endedAt = millis(9, 25)),
            session(kind = FocusKind.SHORT_BREAK, startHour = 10, planned = 5, endedAt = millis(10, 5)),
            session(startHour = 11, planned = 25),
        )
        // 25 from the finished focus; the break and the running session contribute nothing.
        assertEquals(25, FocusTimer.minutesOn(sessions, day, zone))
        assertEquals(1, FocusTimer.completedFocusesOn(sessions, day, zone))
    }

    @Test
    fun `a session is credited to the day it started on`() {
        // 23:50 to 00:10 belongs to the evening it began in.
        val late = FocusSession(
            id = 9,
            kind = FocusKind.FOCUS,
            startedAtMillis = millis(23, 50),
            plannedMinutes = 25,
            endedAtMillis = millis(0, 10, day.plusDays(1)),
            eventId = null,
        )
        assertEquals(20, FocusTimer.minutesOn(listOf(late), day, zone))
        assertEquals(0, FocusTimer.minutesOn(listOf(late), day.plusDays(1), zone))
    }

    @Test
    fun `a range totals every day inside it and nothing outside`() {
        val sessions = listOf(
            session(planned = 25, endedAt = millis(9, 25)),
            session(planned = 30, endedAt = millis(9, 30, day.plusDays(2)), startDate = day.plusDays(2)),
            session(planned = 30, endedAt = millis(9, 30, day.plusDays(9)), startDate = day.plusDays(9)),
        )
        assertEquals(55, FocusTimer.minutesBetween(sessions, day, day.plusDays(6), zone))
    }

    @Test
    fun `an empty history totals zero rather than failing`() {
        assertEquals(0, FocusTimer.minutesOn(emptyList(), day, zone))
        assertEquals(0, FocusTimer.minutesBetween(emptyList(), day, day, zone))
    }

    // --- settings ---

    @Test
    fun `a length outside the sane range is clamped rather than accepted`() {
        val silly = FocusSettings(focusMinutes = 0, longBreakMinutes = 9_000)
        assertEquals(FocusSettings.MIN_MINUTES, silly.minutesFor(FocusKind.FOCUS))
        assertEquals(FocusSettings.MAX_MINUTES, silly.minutesFor(FocusKind.LONG_BREAK))
    }

    @Test
    fun `an unknown stored kind falls back to focus`() {
        assertEquals(FocusKind.FOCUS, FocusKind.of(null))
        assertEquals(FocusKind.FOCUS, FocusKind.of("deep"))
        assertEquals(FocusKind.LONG_BREAK, FocusKind.of("long"))
    }
}
