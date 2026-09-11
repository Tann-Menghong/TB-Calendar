package com.khmercalendar.data.repo

import com.khmercalendar.core.recurrence.RecurrenceRule
import com.khmercalendar.data.db.TemplateDao
import com.khmercalendar.data.db.TemplateEntity
import com.khmercalendar.domain.EventTemplate
import com.khmercalendar.domain.TaskPriority
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalTime

/** Saved event templates. */
class TemplateRepository(private val templateDao: TemplateDao) {

    fun observeAll(): Flow<List<EventTemplate>> =
        templateDao.observeAll().map { rows -> rows.map { it.toTemplate() } }

    suspend fun save(template: EventTemplate): Long =
        templateDao.insert(template.toEntity(System.currentTimeMillis()))

    suspend fun delete(id: Long) = templateDao.deleteById(id)

    companion object {
        internal fun EventTemplate.toEntity(now: Long) = TemplateEntity(
            name = name,
            title = title,
            description = description.ifBlank { null },
            location = location.ifBlank { null },
            allDay = allDay,
            startMinute = startTime.hour * 60 + startTime.minute,
            durationMinutes = durationMinutes,
            categoryId = categoryId,
            colorArgb = colorArgb,
            isTask = isTask,
            priority = priority.stored,
            reminderMinutes = reminderMinutes.joinToString(","),
            rrule = recurrence?.toRRule(),
            createdAtMillis = now,
        )

        /**
         * A stored row as a template, tolerating what a hand-edited backup might contain: a minute
         * outside the day is clamped, and reminder text that is not a number is skipped.
         */
        internal fun TemplateEntity.toTemplate(): EventTemplate {
            val minute = startMinute.coerceIn(0, 1_439)
            return EventTemplate(
                id = id,
                name = name,
                title = title,
                description = description.orEmpty(),
                location = location.orEmpty(),
                allDay = allDay,
                startTime = LocalTime.of(minute / 60, minute % 60),
                durationMinutes = durationMinutes.coerceAtLeast(1),
                categoryId = categoryId,
                colorArgb = colorArgb,
                isTask = isTask,
                priority = TaskPriority.of(priority),
                reminderMinutes = reminderMinutes.split(",")
                    .mapNotNull { it.trim().toIntOrNull()?.takeIf { m -> m >= 0 } }
                    .distinct()
                    .sorted(),
                recurrence = RecurrenceRule.parse(rrule),
            )
        }
    }
}
