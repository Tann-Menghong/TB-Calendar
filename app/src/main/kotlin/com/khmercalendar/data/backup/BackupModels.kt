package com.khmercalendar.data.backup

import kotlinx.serialization.Serializable

/**
 * The on-disk backup format.
 *
 * A plain, documented JSON file the user owns, written to wherever they choose through the
 * system file picker. No account, no server, and readable in a text editor if this app ever
 * stops being maintained - which for a calendar holding years of someone's life is the point.
 */
@Serializable
data class BackupArchive(
    val formatVersion: Int = FORMAT_VERSION,
    val appVersion: String,
    val exportedAtMillis: Long,
    val categories: List<BackupCategory>,
    val events: List<BackupEvent>,
    val notes: List<BackupNote>,
) {
    companion object {
        /** Bumped only for a change old readers could not survive. */
        const val FORMAT_VERSION = 1
    }
}

@Serializable
data class BackupCategory(
    val id: Long,
    val name: String,
    val colorArgb: Int,
    val isVisible: Boolean,
    val sortOrder: Int,
    val isBuiltIn: Boolean,
)

@Serializable
data class BackupEvent(
    val id: Long,
    val title: String,
    val description: String? = null,
    val location: String? = null,
    val startUtcMillis: Long,
    val endUtcMillis: Long,
    val allDay: Boolean,
    val zoneId: String,
    val rrule: String? = null,
    val categoryId: Long? = null,
    val colorArgb: Int? = null,
    val isTask: Boolean = false,
    val isCompleted: Boolean = false,
    val reminderMinutes: List<Int> = emptyList(),
    val exceptionEpochDays: List<Long> = emptyList(),
)

@Serializable
data class BackupNote(val epochDay: Long, val text: String)
