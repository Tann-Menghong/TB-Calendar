package com.khmercalendar.domain

import com.khmercalendar.core.recurrence.Frequency
import com.khmercalendar.core.recurrence.RecurrenceRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class EventTemplatesTest {

    private val monday = LocalDate.of(2026, 4, 13)

    @Test
    fun `a template keeps the length of an event that runs past midnight`() {
        val draft = EventDraftModel(
            title = "វេនយប់",
            date = monday,
            startTime = LocalTime.of(23, 0),
            endTime = LocalTime.of(1, 0),
            endDate = monday,
        )

        val template = EventTemplates.from(draft, "វេន")
        assertEquals(120, template.durationMinutes)

        val applied = EventTemplates.toDraft(template, monday.plusDays(4), EventDraftModel(date = monday.plusDays(4)))
        assertEquals(monday.plusDays(4), applied.date)
        assertEquals(LocalTime.of(23, 0), applied.startTime)
        assertEquals(monday.plusDays(5), applied.endDate)
        assertEquals(LocalTime.of(1, 0), applied.endTime)
    }

    @Test
    fun `a timed event over several days keeps its full length`() {
        val draft = EventDraftModel(
            title = "ដំណើរ",
            date = monday,
            startTime = LocalTime.of(22, 0),
            endTime = LocalTime.of(6, 0),
            endDate = monday.plusDays(1),
        )

        assertEquals(8 * 60, EventTemplates.from(draft, "").durationMinutes)
    }

    @Test
    fun `an all-day template spans the same number of days wherever it lands`() {
        val draft = EventDraftModel(title = "បុណ្យ", date = monday, endDate = monday.plusDays(2), allDay = true)

        val template = EventTemplates.from(draft, "បុណ្យ")
        assertEquals(3 * 1_440, template.durationMinutes)

        val day = LocalDate.of(2026, 12, 30)
        val applied = EventTemplates.toDraft(template, day, EventDraftModel(date = day))
        assertEquals(true, applied.allDay)
        assertEquals(day, applied.date)
        assertEquals(LocalDate.of(2027, 1, 1), applied.endDate)
    }

    @Test
    fun `a hand-edited all-day template shorter than a day is still one day`() {
        val template = EventTemplate(name = "x", title = "x", allDay = true, durationMinutes = 60)

        val applied = EventTemplates.toDraft(template, monday, EventDraftModel(date = monday))

        assertEquals(monday, applied.endDate)
    }

    @Test
    fun `applying a template keeps the form's id, day and category, and starts undone`() {
        val current = EventDraftModel(id = 42, title = "ចាស់", date = monday, categoryId = 7, isTask = true, isCompleted = true)
        val template = EventTemplate(
            name = "អាន",
            title = "អានសៀវភៅ",
            startTime = LocalTime.of(20, 30),
            durationMinutes = 45,
            isTask = true,
            reminderMinutes = listOf(5),
            recurrence = RecurrenceRule(frequency = Frequency.DAILY, interval = 1),
        )

        val applied = EventTemplates.toDraft(template, monday, current)

        assertEquals(42L, applied.id)
        assertEquals(monday, applied.date)
        assertEquals(7L, applied.categoryId)
        assertFalse(applied.isCompleted)
        assertEquals("អានសៀវភៅ", applied.title)
        assertEquals(LocalTime.of(21, 15), applied.endTime)
        assertEquals(listOf(5), applied.reminderMinutes)
        assertEquals(Frequency.DAILY, applied.recurrence?.frequency)
    }

    @Test
    fun `a template's own category replaces the form's`() {
        val template = EventTemplate(name = "x", title = "x", categoryId = 3)

        assertEquals(3L, EventTemplates.toDraft(template, monday, EventDraftModel(date = monday, categoryId = 7)).categoryId)
    }

    @Test
    fun `a blank name falls back to the title, trimmed and cut to length`() {
        val title = "  " + "ក".repeat(60) + "  "

        val template = EventTemplates.from(EventDraftModel(title = title, date = monday), "   ")

        assertEquals("ក".repeat(EventTemplates.MAX_NAME), template.name)
        assertEquals("ក".repeat(60), template.title)
    }

    @Test
    fun `reminders are stored once each, in order, without negatives`() {
        val draft = EventDraftModel(title = "x", date = monday, reminderMinutes = listOf(30, -5, 10, 30))

        assertEquals(listOf(10, 30), EventTemplates.from(draft, "x").reminderMinutes)
    }
}
