package com.khmercalendar.domain

import com.khmercalendar.core.work.WorkSchedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * The day's timeline, which is now the dashboard's main schedule view.
 *
 * Worth testing rather than eyeballing because it merges two sources that disagree about what
 * a day is: the work schedule knows about shifts and lunch, the calendar knows about events,
 * and the interesting cases are where they meet.
 */
class DayTimelineTest {

    private val tuesday = LocalDate.of(2026, 9, 8)
    private val sunday = LocalDate.of(2026, 9, 13)
    private val schedule = WorkSchedule.DEFAULT

    private fun event(
        id: Long,
        at: String,
        title: String = "E$id",
        allDay: Boolean = false,
        isTask: Boolean = false,
        done: Boolean = false,
        date: LocalDate = tuesday,
    ) = EventOccurrence(
        eventId = id,
        title = title,
        description = null,
        location = null,
        occurrenceDate = date,
        start = LocalDateTime.of(date, LocalTime.parse(at)),
        end = LocalDateTime.of(date, LocalTime.parse(at)).plusHours(1),
        allDay = allDay,
        colorArgb = 0xFF2F6FED.toInt(),
        categoryId = null,
        categoryName = null,
        isTask = isTask,
        isCompleted = done,
        isRecurring = false,
    )

    @Test
    fun `a weekday has both shifts, the break between them, and going home`() {
        val entries = DayTimeline.build(tuesday, emptyList(), schedule)

        assertEquals(
            listOf(
                LocalTime.of(7, 30),
                LocalTime.of(11, 30),
                LocalTime.of(13, 30),
                LocalTime.of(17, 30),
            ),
            entries.map { it.at },
        )
        assertEquals(
            listOf(
                TimelineEntry.Kind.WORK_START,
                TimelineEntry.Kind.BREAK,
                TimelineEntry.Kind.WORK_START,
                TimelineEntry.Kind.WORK_END,
            ),
            entries.map { it.kind },
        )
    }

    @Test
    fun `the gap between shifts is a break, not the end of the day`() {
        // 11:30 is when the morning stops, but calling it "going home" would be wrong - the
        // afternoon has not happened yet.
        val entries = DayTimeline.build(tuesday, emptyList(), schedule)
        val midday = entries.single { it.at == LocalTime.of(11, 30) }

        assertEquals(TimelineEntry.Kind.BREAK, midday.kind)
        // The time is carried, not rendered: the builder has no access to the user's 12/24
        // hour preference or their numerals, and a hard-coded "13:30" sat inside a timeline
        // where every other time read "១:៣០ រសៀល".
        assertEquals(LocalTime.of(13, 30), midday.subtitleAt)
        assertNull(midday.subtitle)
    }

    @Test
    fun `Saturday has one shift and ends after it`() {
        val saturday = LocalDate.of(2026, 9, 12)
        val entries = DayTimeline.build(saturday, emptyList(), schedule)

        assertEquals(2, entries.size)
        assertEquals(TimelineEntry.Kind.WORK_START, entries.first().kind)
        assertEquals(TimelineEntry.Kind.WORK_END, entries.last().kind)
        assertEquals(LocalTime.of(11, 30), entries.last().at)
    }

    @Test
    fun `a day off contributes nothing from the schedule`() {
        val entries = DayTimeline.build(sunday, listOf(event(1, "10:00")), schedule)

        assertEquals(1, entries.size)
        assertEquals(TimelineEntry.Kind.EVENT, entries.single().kind)
    }

    @Test
    fun `a disabled schedule contributes nothing on any day`() {
        val entries = DayTimeline.build(
            tuesday,
            listOf(event(1, "10:00")),
            schedule.copy(enabled = false),
        )

        assertEquals(1, entries.size)
    }

    @Test
    fun `events are interleaved with the working day in clock order`() {
        val entries = DayTimeline.build(
            tuesday,
            listOf(event(1, "15:00", "ត្រួតពិនិត្យ"), event(2, "09:00", "ប្រជុំ")),
            schedule,
        )

        assertEquals(
            listOf("ចូលធ្វើការ", "ប្រជុំ", "ពេលសម្រាក", "ចូលធ្វើការវិញ", "ត្រួតពិនិត្យ", "ទៅផ្ទះ"),
            entries.map { it.title },
        )
    }

    @Test
    fun `an event at the same minute as a shift change comes first`() {
        // A 07:30 meeting is what the user needs to see; "work starts" is the context round it.
        val entries = DayTimeline.build(tuesday, listOf(event(1, "07:30", "ប្រជុំព្រឹក")), schedule)

        assertEquals("ប្រជុំព្រឹក", entries.first().title)
        assertEquals(TimelineEntry.Kind.WORK_START, entries[1].kind)
    }

    @Test
    fun `an all-day entry is left off the clock entirely`() {
        val entries = DayTimeline.build(
            tuesday,
            listOf(event(1, "00:00", "ចូលឆ្នាំ", allDay = true)),
            schedule,
        )

        assertTrue(entries.none { it.title == "ចូលឆ្នាំ" })
    }

    @Test
    fun `a task keeps its own kind and its completion`() {
        val entries = DayTimeline.build(
            tuesday,
            listOf(event(1, "14:00", "ដាក់របាយការណ៍", isTask = true, done = true)),
            schedule,
        )
        val task = entries.single { it.eventId == 1L }

        assertEquals(TimelineEntry.Kind.TASK, task.kind)
        assertTrue(task.done)
    }

    // --- the now marker ----------------------------------------------------------------

    @Test
    fun `the now marker sits after everything already passed`() {
        val entries = DayTimeline.build(tuesday, emptyList(), schedule)

        assertEquals(0, DayTimeline.nowIndex(entries, LocalTime.of(6, 0)))
        assertEquals(1, DayTimeline.nowIndex(entries, LocalTime.of(9, 0)))
        assertEquals(2, DayTimeline.nowIndex(entries, LocalTime.of(12, 0)))
        assertEquals(4, DayTimeline.nowIndex(entries, LocalTime.of(18, 0)))
    }

    @Test
    fun `a moment exactly on an entry counts that entry as reached`() {
        val entries = DayTimeline.build(tuesday, emptyList(), schedule)

        assertEquals(1, DayTimeline.nowIndex(entries, LocalTime.of(7, 30)))
    }

    @Test
    fun `an empty timeline puts the marker at the top`() {
        assertEquals(0, DayTimeline.nowIndex(emptyList(), LocalTime.NOON))
    }
}
