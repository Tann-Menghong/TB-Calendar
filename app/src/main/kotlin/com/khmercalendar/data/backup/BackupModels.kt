package com.khmercalendar.data.backup

import kotlinx.serialization.Serializable

/**
 * The on-disk backup format.
 *
 * A plain, documented JSON file the user owns, written to wherever they choose through the
 * system file picker. No account, no server, and readable in a text editor if this app ever
 * stops being maintained - which for a calendar holding years of someone's life is the point.
 *
 * ## Format 2
 *
 * Format 1 carried events, categories and notes, and nothing the app gained afterwards: not a
 * task's priority, not a pinned countdown, not when a task was completed, and none of the
 * habits, checklists or focus sessions. Backing up and restoring lost all of that silently,
 * because the file had nowhere to put it.
 *
 * Every field added in format 2 has a default, so a format 1 file still reads. The number was
 * bumped anyway, deliberately: an older app reading a format 2 file with `ignoreUnknownKeys`
 * would restore the events and quietly drop the habits, and a restore that reports success
 * while losing data is worse than one that refuses and says why.
 *
 * ## Format 3
 *
 * Adds the days each repeating task was ticked ([BackupEvent.completedOccurrences]). A format 2
 * reader would drop them silently, so the number moved again for the same reason as before.
 *
 * ## Format 4
 *
 * Adds event templates ([templates]), for the same reason again: a format 3 reader would drop them.
 *
 * [formatVersion], [appVersion] and [exportedAtMillis] have no defaults on purpose. They are
 * what tells this file apart from any other JSON object; with defaults, `{}` would decode as a
 * valid empty backup, and restore would cheerfully report that there was nothing to add.
 */
@Serializable
data class BackupArchive(
    val formatVersion: Int,
    val appVersion: String,
    val exportedAtMillis: Long,
    val categories: List<BackupCategory> = emptyList(),
    val events: List<BackupEvent> = emptyList(),
    val notes: List<BackupNote> = emptyList(),
    val habits: List<BackupHabit> = emptyList(),
    val focusSessions: List<BackupFocusSession> = emptyList(),
    val templates: List<BackupTemplate> = emptyList(),
    /** Null in format 1, and in any file written without settings. */
    val settings: BackupSettings? = null,
) {
    companion object {
        /** Bumped for any change after which an older reader would lose data, not only crash. */
        const val FORMAT_VERSION = 4
    }
}

@Serializable
data class BackupCategory(
    val id: Long,
    val name: String,
    val colorArgb: Int,
    val isVisible: Boolean = true,
    val sortOrder: Int = 0,
    val isBuiltIn: Boolean = false,
)

/**
 * An event or task, with everything hanging off it.
 *
 * @property completedAtMillis when a completed task was ticked. Absent in format 1, and
 *   restored as absent rather than as "now": stamping the restore time put every task ever
 *   finished into today's statistics.
 * @property checklist the task's steps, nested rather than a separate list with an `eventId`,
 *   so a step cannot be restored onto the wrong task or survive without one.
 */
@Serializable
data class BackupEvent(
    val id: Long,
    val title: String,
    val description: String? = null,
    val location: String? = null,
    val startUtcMillis: Long,
    val endUtcMillis: Long,
    val allDay: Boolean = false,
    val zoneId: String,
    val rrule: String? = null,
    val categoryId: Long? = null,
    val colorArgb: Int? = null,
    val isTask: Boolean = false,
    val isCompleted: Boolean = false,
    val reminderMinutes: List<Int> = emptyList(),
    val exceptionEpochDays: List<Long> = emptyList(),
    // --- format 2 ---
    val priority: Int = 0,
    val isPinned: Boolean = false,
    val completedAtMillis: Long? = null,
    val createdAtMillis: Long? = null,
    val updatedAtMillis: Long? = null,
    val checklist: List<BackupChecklistItem> = emptyList(),
    // --- format 3 ---
    /** The occurrences of a repeating task that were ticked off, by the day each falls on. */
    val completedOccurrences: List<BackupOccurrenceCompletion> = emptyList(),
)

@Serializable
data class BackupChecklistItem(
    val text: String,
    val isDone: Boolean = false,
    val sortOrder: Int = 0,
    val createdAtMillis: Long? = null,
)

@Serializable
data class BackupOccurrenceCompletion(
    val epochDay: Long,
    val completedAtMillis: Long? = null,
)

/** An event template. [startMinute] is minutes after midnight; see TemplateEntity. */
@Serializable
data class BackupTemplate(
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
    val reminderMinutes: List<Int> = emptyList(),
    val rrule: String? = null,
    val sortOrder: Int = 0,
    val createdAtMillis: Long? = null,
)

@Serializable
data class BackupNote(
    val epochDay: Long,
    val text: String,
    val updatedAtMillis: Long? = null,
)

/**
 * A habit and every day it was kept.
 *
 * [createdAtMillis] and [archivedAtMillis] travel with it because the statistics measure a
 * habit only across its own life; restoring one with a fresh creation date would rewrite its
 * history as a string of misses.
 */
@Serializable
data class BackupHabit(
    val id: Long,
    val name: String,
    val colorArgb: Int,
    val scheduleKind: String,
    val scheduleDays: String = "",
    val weeklyTarget: Int = 1,
    val sortOrder: Int = 0,
    val archivedAtMillis: Long? = null,
    val createdAtMillis: Long,
    val entries: List<BackupHabitEntry> = emptyList(),
)

@Serializable
data class BackupHabitEntry(
    val epochDay: Long,
    val completedAtMillis: Long? = null,
)

/**
 * A focus or break session.
 *
 * @property eventId the backup's own id for the task it was for. Rewritten on restore to the
 *   task's new id, and dropped if that task is not in the file - carrying the old number
 *   across would attach the session to whichever unrelated event happens to hold that id on
 *   the new phone.
 */
@Serializable
data class BackupFocusSession(
    val kind: String,
    val startedAtMillis: Long,
    val plannedMinutes: Int,
    val endedAtMillis: Long? = null,
    val eventId: Long? = null,
)

/**
 * The preferences worth carrying to another phone.
 *
 * Every field is nullable: a field that is absent leaves the current setting alone, so a file
 * from an older version - or one somebody trimmed by hand - never resets what it does not
 * mention.
 *
 * ## Deliberately not here
 *
 * - **The app lock, the PIN hash and its salt, and biometric unlock.** A backup is a readable
 *   file the user may copy anywhere, and restoring a lock whose PIN nobody remembers onto a new
 *   phone is a lockout rather than a convenience.
 * - **The background image and notification sound.** Both are `content://` addresses on the
 *   phone that made the backup. They grant no access on another one and point at nothing.
 * - **Everything about the AI.** The model file is hundreds of megabytes and is not in the
 *   backup, so restoring "AI on, this model" would switch on a feature with nothing to run.
 *   The AI stays off until the user turns it on, which is the rule everywhere else too.
 */
@Serializable
data class BackupSettings(
    val themeMode: String? = null,
    val accentArgb: Int? = null,
    val useDynamicColor: Boolean? = null,
    val fontScale: Float? = null,
    val density: String? = null,
    val backgroundOpacity: Float? = null,
    val weekStart: Int? = null,
    val showKhmerLunarDates: Boolean? = null,
    val showGregorianDates: Boolean? = null,
    val showHolidays: Boolean? = null,
    val showEventDots: Boolean? = null,
    val showWeekNumbers: Boolean? = null,
    val useKhmerNumerals: Boolean? = null,
    val highlightWeekends: Boolean? = null,
    val timeFormat: String? = null,
    val dateFormat: String? = null,
    val dayStartHour: Int? = null,
    val dayEndHour: Int? = null,
    val startScreen: String? = null,
    val defaultReminderMinutes: Int? = null,
    val defaultEventDurationMinutes: Int? = null,
    val dashboardOrder: List<String>? = null,
    val dashboardHidden: List<String>? = null,
    val dashboardDensity: String? = null,
    val animationLevel: String? = null,
    val dockSlots: List<String>? = null,
    val displayName: String? = null,
    val countdownStyle: String? = null,
    val focusMinutes: Int? = null,
    val shortBreakMinutes: Int? = null,
    val longBreakMinutes: Int? = null,
    val sessionsBeforeLongBreak: Int? = null,
    val workSchedule: String? = null,
    val workEnabled: Boolean? = null,
    val workNotifications: Boolean? = null,
    val workNotifyLeadMinutes: Int? = null,
    val widgetTheme: String? = null,
    val widgetOpacity: Float? = null,
    val widgetShowLunar: Boolean? = null,
    val notificationsEnabled: Boolean? = null,
    val notificationVibrate: Boolean? = null,
    val holidayNotifications: Boolean? = null,
)
