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
    /**
     * How urgent a task is, as [com.khmercalendar.domain.TaskPriority.stored].
     *
     * An Int rather than the enum so that a value written by a newer version still reads
     * back, and so the column can be added with a default rather than a data migration.
     * Ordinary events carry it and ignore it, exactly as they do [isCompleted].
     */
    val priority: Int = 0,
    /**
     * Whether this date is pinned as a countdown.
     *
     * A countdown is a named future date, which is exactly what a row of this table already
     * is - so an exam, a deadline or a birthday is an event with this flag rather than a
     * second table. It inherits recurrence for nothing, which is what makes a birthday
     * countdown work at all: the row repeats yearly and the countdown reads its next
     * occurrence. It also inherits search, categories, colours, backup and ICS export.
     */
    val isPinned: Boolean = false,
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

/**
 * A habit: something the user intends to do repeatedly.
 *
 * ## Why this is a table and tasks are not
 *
 * Tasks and countdowns are rows in `events`, deliberately, because each of them *is* a dated
 * thing and sharing the table is what stops the calendar disagreeing with itself. A habit is
 * not a dated thing - it is a rule about days, plus a tick per day it was kept. Forcing that
 * into `events` would mean writing a row per day per habit forever, and a schedule change
 * would have to rewrite history. Two small tables are the honest shape.
 *
 * @property scheduleKind [com.khmercalendar.domain.HabitSchedule.Kind.stored].
 * @property scheduleDays weekdays as "1,3,5", Monday = 1. Empty unless the kind is DAYS.
 * @property archivedAtMillis set instead of deleting, so the entries stay and a habit can
 *   come back without losing its history.
 */
@Entity(tableName = "habits")
data class HabitEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val colorArgb: Int,
    val scheduleKind: String,
    val scheduleDays: String = "",
    val weeklyTarget: Int = 1,
    val sortOrder: Int = 0,
    val archivedAtMillis: Long? = null,
    val createdAtMillis: Long,
)

/**
 * One day a habit was kept.
 *
 * Presence is the fact; there is no "not done" row, because a day nobody ticked and a day
 * somebody explicitly failed are the same thing and storing both invites them to disagree.
 * The unique index on (habitId, epochDay) is what makes ticking twice a no-op rather than a
 * duplicate - a real hazard when a tap is registered twice.
 */
@Entity(
    tableName = "habit_entries",
    foreignKeys = [
        ForeignKey(
            entity = HabitEntity::class,
            parentColumns = ["id"],
            childColumns = ["habitId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["habitId", "epochDay"], unique = true), Index("epochDay")],
)
data class HabitEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val habitId: Long,
    val epochDay: Long,
    val completedAtMillis: Long,
)

/**
 * One line of a task's checklist.
 *
 * Its own table rather than a `parentId` on [EventEntity], by the rule the habit tables set:
 * a checklist line is not a date. It has no time, no reminder and no place on a calendar, and
 * making it an event row would put every step of every task onto the day the task falls on -
 * burying the day it was meant to describe.
 *
 * The cascade is what keeps that honest: delete the task and its steps go with it, rather
 * than leaving rows nothing can reach.
 */
@Entity(
    tableName = "checklist_items",
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
data class ChecklistItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val eventId: Long,
    val text: String,
    val isDone: Boolean = false,
    val sortOrder: Int = 0,
    val createdAtMillis: Long,
)

/**
 * One focus or break session.
 *
 * ## Why a running session is a row rather than state in memory
 *
 * A timer held in a service or a view model dies with the process, and on the phones this app
 * targets the process is killed routinely. Two numbers in a table - when it started and how
 * long it was meant to last - survive that exactly, and everything the screen shows is
 * derived from them and the clock. Nothing ticks in the background; one alarm is set for the
 * end. See [com.khmercalendar.domain.FocusTimer].
 *
 * @property endedAtMillis null while running. A session stopped early keeps the time it
 *   really ran, so the statistics are honest.
 * @property eventId the task this was for, or null. Deliberately not a foreign key: deleting
 *   a task should not delete the record that you spent an hour on it.
 */
@Entity(tableName = "focus_sessions", indices = [Index("startedAtMillis")])
data class FocusSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: String,
    val startedAtMillis: Long,
    val plannedMinutes: Int,
    val endedAtMillis: Long? = null,
    val eventId: Long? = null,
)

/**
 * One occurrence of a repeating task, ticked off.
 *
 * ## Why this exists
 *
 * A repeating series is one row in `events`, and it carried one completion flag. Ticking
 * Monday's chore therefore ticked every Monday there would ever be - and the reminder
 * scheduler, which skips completed rows, stopped reminding about all of them. The only way to
 * record "this Monday is done" is a row per occurrence, keyed by the day it falls on.
 *
 * A one-off task still uses its own `events.isCompleted`: it has exactly one occurrence, and
 * moving it here would rewrite every task ever finished for no gain.
 *
 * Presence is the fact, as with habit ticks: there is no "not done" row. The composite key is
 * what makes a double tap a no-op rather than a second completion, and the cascade means a
 * deleted task takes its history with it instead of leaving rows nothing can reach.
 *
 * @property epochDay the local day the occurrence *starts* on.
 * @property completedAtMillis when it was ticked, which is the day statistics count it on.
 */
@Entity(
    tableName = "task_completions",
    primaryKeys = ["eventId", "epochDay"],
    foreignKeys = [
        ForeignKey(
            entity = EventEntity::class,
            parentColumns = ["id"],
            childColumns = ["eventId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("completedAtMillis")],
)
data class TaskCompletionEntity(
    val eventId: Long,
    val epochDay: Long,
    val completedAtMillis: Long,
)

/**
 * A reusable shape for something new: "weekly meeting, 9:00, one hour, reminder half an hour
 * before, category work".
 *
 * Its own table by the rule the habit tables set: a template is not a date. It has a time of day
 * and a length, but no day - putting it in `events` would put it on the calendar.
 *
 * @property startMinute minutes after midnight it starts at. Ignored for an all-day template.
 * @property durationMinutes how long it lasts. May carry past midnight; for an all-day template it
 *   is whole days times 1440.
 * @property reminderMinutes lead times as "30,60", the same values the editor offers.
 * @property categoryId set to null if the category is deleted: removing a category must not take
 *   the templates that used it with it.
 */
@Entity(
    tableName = "event_templates",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("categoryId")],
)
data class TemplateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val title: String,
    val description: String? = null,
    val location: String? = null,
    val allDay: Boolean = false,
    val startMinute: Int = 540,
    val durationMinutes: Int = 60,
    val categoryId: Long? = null,
    val colorArgb: Int? = null,
    val isTask: Boolean = false,
    val priority: Int = 0,
    val reminderMinutes: String = "",
    val rrule: String? = null,
    val sortOrder: Int = 0,
    val createdAtMillis: Long,
)
