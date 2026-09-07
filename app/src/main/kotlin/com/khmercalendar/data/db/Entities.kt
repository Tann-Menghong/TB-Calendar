package com.khmercalendar.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A calendar or label an event belongs to: "ការងារ", "គ្រួសារ", "សាលារៀន".
 *
 * Every event has one, which is what makes the visibility toggles in settings a single
 * indexed query rather than a scan of the table.
 */
@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** Packed sRGB, as produced by Compose's Color.toArgb(). */
    val colorArgb: Int,
    val isVisible: Boolean = true,
    val sortOrder: Int = 0,
    /** Built-in categories can be renamed and recoloured but not deleted. */
    val isBuiltIn: Boolean = false,
)

/**
 * An event, a task, or the first occurrence of a repeating series.
 *
 * ## How time is stored
 *
 * Timed events keep UTC milliseconds plus the zone they were created in. Wall-clock text
 * would make range queries impossible to index; UTC alone would move a 09:00 meeting when
 * the user travels. All-day events keep [startUtcMillis] at UTC midnight of the day itself
 * and are always read back as a plain date - the convention CalendarContract uses, and the
 * only one that survives a timezone change intact.
 *
 * ## How repeats are stored
 *
 * A repeating series is one row. [rrule] holds the RFC 5545 rule and occurrences are
 * expanded when a range is queried. Materialising them would mean writing hundreds of rows
 * per series and rewriting them on every edit, for no gain: a month grid needs a few dozen
 * dates and computing those costs nothing.
 */
@Entity(
    tableName = "events",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index("startUtcMillis"),
        Index("endUtcMillis"),
        Index("categoryId"),
        Index("rrule"),
    ],
)
data class EventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val description: String? = null,
    val location: String? = null,
    val startUtcMillis: Long,
    val endUtcMillis: Long,
    val allDay: Boolean = false,
    /** IANA zone the event was created in, for example "Asia/Phnom_Penh". */
    val zoneId: String,
    /** RFC 5545 rule without the "RRULE:" prefix, or null for a one-off. */
    val rrule: String? = null,
    val categoryId: Long? = null,
    /** Overrides the category colour for this event alone. */
    val colorArgb: Int? = null,
    /** Tasks can be ticked off; ordinary events ignore this. */
    val isTask: Boolean = false,
    val isCompleted: Boolean = false,
    val completedAtMillis: Long? = null,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
)

/**
 * A date removed from a repeating series.
 *
 * Deleting "just this one" of a weekly meeting writes a row here rather than splitting the
 * series, so a later edit to the series still applies to every remaining occurrence.
 */
@Entity(
    tableName = "event_exceptions",
    primaryKeys = ["eventId", "exceptionEpochDay"],
    foreignKeys = [
        ForeignKey(
            entity = EventEntity::class,
            parentColumns = ["id"],
            childColumns = ["eventId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class EventExceptionEntity(
    val eventId: Long,
    val exceptionEpochDay: Long,
)

/** One notification for an event, [minutesBefore] ahead of its start. */
@Entity(
    tableName = "reminders",
    foreignKeys = [
        ForeignKey(
            entity = EventEntity::class,
            parentColumns = ["id"],
            childColumns = ["eventId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("eventId")],
)
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val eventId: Long,
    val minutesBefore: Int,
)

/** A free-text note attached to a single day. */
@Entity(tableName = "day_notes", indices = [Index(value = ["epochDay"], unique = true)])
data class DayNoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val epochDay: Long,
    val text: String,
    val updatedAtMillis: Long,
)
