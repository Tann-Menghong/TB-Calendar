package com.khmercalendar.core.work

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * The working day, at every boundary.
 *
 * A countdown is only worth having if it is right at the exact minute it changes, and those
 * minutes are precisely the ones a person notices. Testing the pure function directly means
 * every boundary can be checked without waiting for a real clock to reach it.
 */
class WorkClockTest {

    // 2026-03-09 is a Monday, 2026-03-14 a Saturday, 2026-03-15 a Sunday.
    private val monday: LocalDate = LocalDate.of(2026, 3, 9)
    private val saturday: LocalDate = LocalDate.of(2026, 3, 14)
    private val sunday: LocalDate = LocalDate.of(2026, 3, 15)

    private fun statusOn(date: LocalDate, hour: Int, minute: Int = 0, second: Int = 0) =
        WorkClock.statusAt(
            WorkSchedule.DEFAULT,
            LocalDateTime.of(date, LocalTime.of(hour, minute, second)),
        )

    @Test
    fun `the default schedule is the ordinary Cambodian office week`() {
        val weekday = WorkSchedule.DEFAULT.dayOf(DayOfWeek.WEDNESDAY)
        assertEquals(2, weekday.blocks.size)
        assertEquals(LocalTime.of(7, 30), weekday.blocks[0].start)
        assertEquals(LocalTime.of(11, 30), weekday.blocks[0].end)
        assertEquals(LocalTime.of(13, 30), weekday.blocks[1].start)
        assertEquals(LocalTime.of(17, 30), weekday.blocks[1].end)
        assertEquals(Duration.ofHours(8), weekday.totalDuration)

        val sat = WorkSchedule.DEFAULT.dayOf(DayOfWeek.SATURDAY)
        assertEquals(1, sat.blocks.size)
        assertEquals(LocalTime.of(11, 30), sat.blocks[0].end)
        assertEquals(Duration.ofHours(4), sat.totalDuration)

        assertTrue(WorkSchedule.DEFAULT.dayOf(DayOfWeek.SUNDAY).isOff)
    }

    @Test
    fun `before the first block`() {
        val status = statusOn(monday, 6, 30)
        assertEquals(WorkState.BEFORE_WORK, status.state)
        assertEquals(LocalTime.of(7, 30), status.nextBlock?.start)
        assertEquals(Duration.ofHours(1), status.remaining)
        assertNull("Nothing is in progress before work", status.progress)
        assertEquals(Duration.ofHours(8), status.remainingToday)
    }

    @Test
    fun `the exact minute work starts is working, not before work`() {
        val status = statusOn(monday, 7, 30)
        assertEquals(WorkState.WORKING, status.state)
        assertEquals(Duration.ofHours(4), status.remaining)
        assertEquals(0f, status.progress!!, 0.0001f)
    }

    @Test
    fun `mid-morning counts down to the end of the morning block`() {
        val status = statusOn(monday, 9, 30)
        assertEquals(WorkState.WORKING, status.state)
        assertEquals(WorkSchedule.MORNING_KM, status.currentBlock?.labelKm)
        assertEquals(Duration.ofHours(2), status.remaining)
        assertEquals(0.5f, status.progress!!, 0.0001f)
        // Two hours left this morning plus the whole four-hour afternoon.
        assertEquals(Duration.ofHours(6), status.remainingToday)
    }

    @Test
    fun `the exact minute the morning ends is the break, counting down to the afternoon`() {
        val status = statusOn(monday, 11, 30)
        assertEquals(WorkState.BREAK, status.state)
        assertEquals(Duration.ofHours(2), status.remaining)
        assertEquals(LocalTime.of(13, 30), status.nextBlock?.start)
        assertEquals(0f, status.progress!!, 0.0001f)
        assertEquals(Duration.ofHours(4), status.remainingToday)
    }

    @Test
    fun `lunch reports its own progress`() {
        val status = statusOn(monday, 12, 30)
        assertEquals(WorkState.BREAK, status.state)
        assertEquals(Duration.ofHours(1), status.remaining)
        assertEquals(0.5f, status.progress!!, 0.0001f)
    }

    @Test
    fun `the exact minute the afternoon starts is working again`() {
        val status = statusOn(monday, 13, 30)
        assertEquals(WorkState.WORKING, status.state)
        assertEquals(WorkSchedule.AFTERNOON_KM, status.currentBlock?.labelKm)
        assertEquals(Duration.ofHours(4), status.remaining)
    }

    @Test
    fun `the exact minute work ends is finished, with nothing to count down to`() {
        val status = statusOn(monday, 17, 30)
        assertEquals(WorkState.FINISHED, status.state)
        assertNull(status.remaining)
        assertNull(status.nextTransitionAt)
        assertEquals(Duration.ZERO, status.remainingToday)
        assertTrue("A finished day has nothing to tick", !status.hasCountdown)
    }

    @Test
    fun `late evening is still finished, not before tomorrow's work`() {
        assertEquals(WorkState.FINISHED, statusOn(monday, 23, 59).state)
    }

    @Test
    fun `Saturday finishes at half past eleven`() {
        assertEquals(WorkState.WORKING, statusOn(saturday, 9, 0).state)
        assertEquals(Duration.ofHours(2).plusMinutes(30), statusOn(saturday, 9, 0).remaining)

        val afterNoon = statusOn(saturday, 11, 30)
        assertEquals(
            "Saturday has no afternoon block, so 11:30 is the end of the day",
            WorkState.FINISHED,
            afterNoon.state,
        )
        assertNull(afterNoon.remaining)
    }

    @Test
    fun `Sunday is a day off at every hour`() {
        for (hour in 0..23) {
            assertEquals(WorkState.DAY_OFF, statusOn(sunday, hour).state)
        }
        assertEquals(Duration.ZERO, statusOn(sunday, 9).totalToday)
    }

    @Test
    fun `a disabled countdown reports disabled whatever the time`() {
        val off = WorkSchedule.DEFAULT.copy(enabled = false)
        val status = WorkClock.statusAt(off, LocalDateTime.of(monday, LocalTime.of(9, 0)))
        assertEquals(WorkState.DISABLED, status.state)
        assertNull(status.remaining)
    }

    @Test
    fun `seconds are carried, so the countdown is not stuck on whole minutes`() {
        val status = statusOn(monday, 9, 0, 15)
        assertEquals(Duration.ofHours(2).plusMinutes(29).plusSeconds(45), status.remaining)
    }

    @Test
    fun `blocks entered out of order still produce a sane countdown`() {
        // A user editing settings can easily save the afternoon before the morning.
        val muddled = WorkSchedule(
            days = mapOf(
                DayOfWeek.MONDAY to WorkDay(
                    listOf(
                        WorkBlock(LocalTime.of(13, 30), LocalTime.of(17, 30), "PM"),
                        WorkBlock(LocalTime.of(7, 30), LocalTime.of(11, 30), "AM"),
                    ),
                ),
            ),
        )
        val status = WorkClock.statusAt(muddled, LocalDateTime.of(monday, LocalTime.of(9, 0)))
        assertEquals(WorkState.WORKING, status.state)
        assertEquals("AM", status.currentBlock?.labelKm)
        assertEquals(LocalTime.of(13, 30), status.nextBlock?.start)
    }

    @Test
    fun `a single continuous block has no break`() {
        val shop = WorkSchedule(
            days = mapOf(
                DayOfWeek.MONDAY to WorkDay(
                    listOf(WorkBlock(LocalTime.of(8, 0), LocalTime.of(18, 0), "ទាំងថ្ងៃ")),
                ),
            ),
        )
        val noon = WorkClock.statusAt(shop, LocalDateTime.of(monday, LocalTime.of(12, 0)))
        assertEquals(WorkState.WORKING, noon.state)
        assertEquals(Duration.ofHours(6), noon.remaining)
    }

    @Test
    fun `transitions for a weekday are the four block boundaries in order`() {
        val transitions = WorkClock.transitionsFor(
            WorkSchedule.DEFAULT,
            LocalDateTime.of(monday, LocalTime.of(0, 0)),
        )
        assertEquals(4, transitions.size)
        assertEquals(LocalTime.of(7, 30), transitions[0].at.toLocalTime())
        assertEquals(WorkState.WORKING, transitions[0].becomes)
        assertEquals(LocalTime.of(11, 30), transitions[1].at.toLocalTime())
        assertEquals("The gap before the afternoon is a break", WorkState.BREAK, transitions[1].becomes)
        assertEquals(LocalTime.of(13, 30), transitions[2].at.toLocalTime())
        assertEquals(LocalTime.of(17, 30), transitions[3].at.toLocalTime())
        assertEquals("The last block ends the day", WorkState.FINISHED, transitions[3].becomes)
    }

    @Test
    fun `a day off and a disabled schedule have no transitions`() {
        assertTrue(
            WorkClock.transitionsFor(
                WorkSchedule.DEFAULT,
                LocalDateTime.of(sunday, LocalTime.of(0, 0)),
            ).isEmpty(),
        )
        assertTrue(
            WorkClock.transitionsFor(
                WorkSchedule.DEFAULT.copy(enabled = false),
                LocalDateTime.of(monday, LocalTime.of(0, 0)),
            ).isEmpty(),
        )
    }

    @Test
    fun `next transition matches the status`() {
        val at = LocalDateTime.of(monday, LocalTime.of(9, 0))
        assertNotNull(WorkClock.nextTransition(WorkSchedule.DEFAULT, at))
        assertEquals(
            LocalDateTime.of(monday, LocalTime.of(11, 30)),
            WorkClock.nextTransition(WorkSchedule.DEFAULT, at),
        )
        assertNull(
            WorkClock.nextTransition(
                WorkSchedule.DEFAULT,
                LocalDateTime.of(monday, LocalTime.of(18, 0)),
            ),
        )
    }
}
