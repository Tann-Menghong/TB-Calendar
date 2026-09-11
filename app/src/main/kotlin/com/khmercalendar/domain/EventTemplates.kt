package com.khmercalendar.domain

import com.khmercalendar.core.recurrence.RecurrenceRule
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit

/**
 * A reusable shape for a new event or task.
 *
 * Holds everything about an event except *when*: the time of day and the length, but no date.
 * The date is always the one the user is creating something on.
 */
data class EventTemplate(
    val id: Long = 0,
    val name: String,
    val title: String,
    val description: String = "",
    val location: String = "",
    val allDay: Boolean = false,
    val startTime: LocalTime = LocalTime.of(9, 0),
    val durationMinutes: Int = 60,
    val categoryId: Long? = null,
    val colorArgb: Int? = null,
    val isTask: Boolean = false,
    val priority: TaskPriority = TaskPriority.NORMAL,
    val reminderMinutes: List<Int> = emptyList(),
    val recurrence: RecurrenceRule? = null,
)

/** Turning a form into a template and a template back into a form. */
object EventTemplates {

    const val MAX_NAME = 40

    private const val MINUTES_PER_DAY = 1_440

    /**
     * What a draft looks like with its date taken out.
     *
     * The length follows the same rule [com.khmercalendar.data.repo.EventRepository.save] applies
     * when it stores an event: an end at or before the start on the same day means the event runs
     * past midnight. A template captured from 23:00-01:00 is two hours, not minus twenty-two.
     *
     * @param name what the template is called; blank falls back to the event's own title.
     */
    fun from(draft: EventDraftModel, name: String): EventTemplate {
        val duration = if (draft.allDay) {
            val days = (ChronoUnit.DAYS.between(draft.date, draft.endDate) + 1).coerceAtLeast(1)
            (days * MINUTES_PER_DAY).toInt()
        } else {
            val start = LocalDateTime.of(draft.date, draft.startTime)
            var end = LocalDateTime.of(draft.endDate, draft.endTime)
            if (!end.isAfter(start) && draft.endDate == draft.date) end = end.plusDays(1)
            Duration.between(start, end).toMinutes().toInt().coerceAtLeast(1)
        }
        return EventTemplate(
            name = name.trim().ifBlank { draft.title.trim() }.take(MAX_NAME),
            title = draft.title.trim(),
            description = draft.description.trim(),
            location = draft.location.trim(),
            allDay = draft.allDay,
            startTime = draft.startTime,
            durationMinutes = duration,
            categoryId = draft.categoryId,
            colorArgb = draft.colorArgb,
            isTask = draft.isTask,
            priority = draft.priority,
            reminderMinutes = draft.reminderMinutes.filter { it >= 0 }.distinct().sorted(),
            recurrence = draft.recurrence,
        )
    }

    /**
     * [current] filled in from [template], on [date].
     *
     * The date is the one already chosen - applying a template on the 20th makes something on the
     * 20th. The id is kept from [current], so applying a template can never quietly turn "editing
     * this event" into "creating another one". A template with no category keeps the form's.
     */
    fun toDraft(template: EventTemplate, date: LocalDate, current: EventDraftModel): EventDraftModel {
        val filled = current.copy(
            title = template.title,
            description = template.description,
            location = template.location,
            categoryId = template.categoryId ?: current.categoryId,
            colorArgb = template.colorArgb,
            isTask = template.isTask,
            isCompleted = false,
            priority = template.priority,
            reminderMinutes = template.reminderMinutes,
            recurrence = template.recurrence,
        )
        if (template.allDay) {
            val days = (template.durationMinutes / MINUTES_PER_DAY).coerceAtLeast(1)
            return filled.copy(allDay = true, date = date, endDate = date.plusDays((days - 1).toLong()))
        }
        val start = LocalDateTime.of(date, template.startTime)
        val end = start.plusMinutes(template.durationMinutes.coerceAtLeast(1).toLong())
        return filled.copy(
            allDay = false,
            date = date,
            startTime = template.startTime,
            endDate = end.toLocalDate(),
            endTime = end.toLocalTime(),
        )
    }
}
