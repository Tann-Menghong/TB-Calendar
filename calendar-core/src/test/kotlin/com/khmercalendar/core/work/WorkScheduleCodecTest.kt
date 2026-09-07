package com.khmercalendar.core.work

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalTime

/**
 * Round-tripping the schedule through preferences storage.
 *
 * The decode side is the one that matters: it reads whatever is on disk, including whatever a
 * damaged file or an older version left there, and it must never be the reason settings fail
 * to load.
 */
class WorkScheduleCodecTest {

    @Test
    fun `the default schedule survives a round trip`() {
        val encoded = WorkScheduleCodec.encode(WorkSchedule.DEFAULT)
        val decoded = WorkScheduleCodec.decode(encoded)

        for (day in DayOfWeek.entries) {
            assertEquals(
                "blocks for $day",
                WorkSchedule.DEFAULT.dayOf(day).blocks.map { it.start to it.end },
                decoded.dayOf(day).blocks.map { it.start to it.end },
            )
        }
        assertTrue(decoded.dayOf(DayOfWeek.SUNDAY).isOff)
    }

    @Test
    fun `a custom schedule survives a round trip`() {
        val custom = WorkSchedule.DEFAULT
            .withDay(
                DayOfWeek.WEDNESDAY,
                WorkDay(listOf(WorkBlock(LocalTime.of(9, 15), LocalTime.of(16, 45), "x"))),
            )
            .withDay(DayOfWeek.SATURDAY, WorkDay.OFF)

        val decoded = WorkScheduleCodec.decode(WorkScheduleCodec.encode(custom))

        assertEquals(1, decoded.dayOf(DayOfWeek.WEDNESDAY).blocks.size)
        assertEquals(LocalTime.of(9, 15), decoded.dayOf(DayOfWeek.WEDNESDAY).blocks[0].start)
        assertEquals(LocalTime.of(16, 45), decoded.dayOf(DayOfWeek.WEDNESDAY).blocks[0].end)
        assertTrue(decoded.dayOf(DayOfWeek.SATURDAY).isOff)
    }

    @Test
    fun `missing or blank input falls back to the default`() {
        assertEquals(
            WorkSchedule.DEFAULT.dayOf(DayOfWeek.MONDAY).blocks.size,
            WorkScheduleCodec.decode(null).dayOf(DayOfWeek.MONDAY).blocks.size,
        )
        assertEquals(
            WorkSchedule.DEFAULT.dayOf(DayOfWeek.MONDAY).blocks.size,
            WorkScheduleCodec.decode("   ").dayOf(DayOfWeek.MONDAY).blocks.size,
        )
    }

    @Test
    fun `entirely unusable input falls back rather than making every day off`() {
        // The dangerous failure: silently deciding the user never works.
        val decoded = WorkScheduleCodec.decode("garbage;;;nonsense")
        assertEquals(2, decoded.dayOf(DayOfWeek.MONDAY).blocks.size)
    }

    @Test
    fun `a malformed block is skipped, the rest of the day survives`() {
        val decoded = WorkScheduleCodec.decode("1:0730-1130|bad-range|1330-1730;7:")
        assertEquals(2, decoded.dayOf(DayOfWeek.MONDAY).blocks.size)
        assertTrue(decoded.dayOf(DayOfWeek.SUNDAY).isOff)
    }

    @Test
    fun `an inverted or zero-length block is rejected`() {
        assertEquals(0, WorkScheduleCodec.decode("1:1730-0730").dayOf(DayOfWeek.MONDAY).blocks.size)
        assertEquals(0, WorkScheduleCodec.decode("1:0900-0900").dayOf(DayOfWeek.MONDAY).blocks.size)
    }

    @Test
    fun `out-of-range times are rejected`() {
        assertEquals(0, WorkScheduleCodec.decode("1:2500-2600").dayOf(DayOfWeek.MONDAY).blocks.size)
        assertEquals(0, WorkScheduleCodec.decode("1:0770-0880").dayOf(DayOfWeek.MONDAY).blocks.size)
    }

    @Test
    fun `an explicitly empty day is a day off, not a fallback to the default`() {
        val decoded = WorkScheduleCodec.decode("1:;2:0730-1130")
        assertTrue("Monday was explicitly cleared", decoded.dayOf(DayOfWeek.MONDAY).isOff)
        assertEquals(1, decoded.dayOf(DayOfWeek.TUESDAY).blocks.size)
    }

    @Test
    fun `blocks are stored in order however they were given`() {
        val decoded = WorkScheduleCodec.decode("1:1330-1730|0730-1130")
        assertEquals(LocalTime.of(7, 30), decoded.dayOf(DayOfWeek.MONDAY).blocks[0].start)
        assertEquals(LocalTime.of(13, 30), decoded.dayOf(DayOfWeek.MONDAY).blocks[1].start)
    }
}
