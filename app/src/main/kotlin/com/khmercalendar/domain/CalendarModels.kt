package com.khmercalendar.domain

import com.khmercalendar.core.recurrence.RecurrenceRule
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * One occurrence of an event, resolved to a concrete day.
 *
 * A repeating series is a single database row; this is what the UI actually draws, so a
 * weekly meeting produces one of these per week without any of them being stored.
 *
 * @property occurrenceDate The day this occurrence falls on, which is the identity the UI
 *   uses. For a one-off it equals the start date.
 */
data class EventOccurrence(
    val eventId: Long,
    val title: String,
    val description: String?,
    val location: String?,
    val occurrenceDate: LocalDate,
    val start: LocalDateTime,
    val end: LocalDateTime,
    val allDay: Boolean,
    val colorArgb: Int,
    val categoryId: Long?,
    val categoryName: String?,
    val isTask: Boolean,
    val isCompleted: Boolean,
    val isRecurring: Boolean,
) {
    val durationMinutes: Long
        get() = java.time.Duration.between(start, end).toMinutes()
}

/** An event as it is edited, before it is split into rows. */
data class EventDraftModel(
    val id: Long = 0,
    val title: String = "",
    val description: String = "",
    val location: String = "",
    val date: LocalDate = LocalDate.now(),
    val startTime: java.time.LocalTime = java.time.LocalTime.of(9, 0),
    val endTime: java.time.LocalTime = java.time.LocalTime.of(10, 0),
    val endDate: LocalDate = date,
    val allDay: Boolean = false,
    val categoryId: Long? = null,
    val colorArgb: Int? = null,
    val recurrence: RecurrenceRule? = null,
    val reminderMinutes: List<Int> = listOf(30),
    val isTask: Boolean = false,
    val isCompleted: Boolean = false,
)

/**
 * Conversion between the wall-clock times the UI works in and the UTC milliseconds the
 * database stores.
 *
 * All-day events are pinned to UTC midnight rather than local midnight. That is what keeps a
 * birthday on the same date after the user flies somewhere else, and it is the same
 * convention CalendarContract uses, so imports and exports line up.
 */
object EventTimes {

    val UTC: ZoneId = ZoneId.of("UTC")

    fun toUtcMillis(dateTime: LocalDateTime, zone: ZoneId, allDay: Boolean): Long =
        if (allDay) {
            dateTime.toLocalDate().atStartOfDay(UTC).toInstant().toEpochMilli()
        } else {
            dateTime.atZone(zone).toInstant().toEpochMilli()
        }

    fun fromUtcMillis(millis: Long, zone: ZoneId, allDay: Boolean): LocalDateTime =
        if (allDay) {
            LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), UTC)
        } else {
            LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), zone)
        }

    fun dateOf(millis: Long, zone: ZoneId, allDay: Boolean): LocalDate =
        fromUtcMillis(millis, zone, allDay).toLocalDate()
}
