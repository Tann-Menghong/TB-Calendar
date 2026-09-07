package com.khmercalendar.data.backup

import android.content.Context
import android.net.Uri
import com.khmercalendar.data.db.CategoryEntity
import com.khmercalendar.data.db.DayNoteEntity
import com.khmercalendar.data.db.EventEntity
import com.khmercalendar.data.db.EventExceptionEntity
import com.khmercalendar.data.db.KhmerCalendarDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.time.format.DateTimeFormatter
import java.time.LocalDateTime

data class RestoreSummary(val events: Int, val categories: Int, val notes: Int)

/**
 * Export and import of the whole calendar, without a cloud account.
 *
 * Restore is additive and re-keyed: every imported row is inserted with a fresh id and the
 * category and reminder links are rewritten to match. Preserving the ids from the file would
 * be simpler but would overwrite whatever the user has created since the backup - the one
 * outcome a restore must never produce.
 */
class BackupManager(
    private val context: Context,
    private val database: KhmerCalendarDatabase,
    private val appVersion: String,
) {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun suggestedFileName(): String {
        val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm"))
        return "khmer-calendar-backup-$stamp.json"
    }

    suspend fun export(uri: Uri): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val archive = buildArchive()
            context.contentResolver.openOutputStream(uri, "wt")?.use { out ->
                out.write(json.encodeToString(BackupArchive.serializer(), archive).toByteArray())
            } ?: error("Could not open the chosen file for writing")
            archive.events.size
        }
    }

    private suspend fun buildArchive(): BackupArchive {
        val events = database.eventDao().allEvents()
        val reminders = database.reminderDao().all().groupBy { it.eventId }
        val exceptions = database.eventExceptionDao().all().groupBy { it.eventId }
        return BackupArchive(
            appVersion = appVersion,
            exportedAtMillis = System.currentTimeMillis(),
            categories = database.categoryDao().all().map {
                BackupCategory(it.id, it.name, it.colorArgb, it.isVisible, it.sortOrder, it.isBuiltIn)
            },
            events = events.map { e ->
                BackupEvent(
                    id = e.id,
                    title = e.title,
                    description = e.description,
                    location = e.location,
                    startUtcMillis = e.startUtcMillis,
                    endUtcMillis = e.endUtcMillis,
                    allDay = e.allDay,
                    zoneId = e.zoneId,
                    rrule = e.rrule,
                    categoryId = e.categoryId,
                    colorArgb = e.colorArgb,
                    isTask = e.isTask,
                    isCompleted = e.isCompleted,
                    reminderMinutes = reminders[e.id].orEmpty().map { it.minutesBefore },
                    exceptionEpochDays = exceptions[e.id].orEmpty().map { it.exceptionEpochDay },
                )
            },
            notes = database.dayNoteDao().all().map { BackupNote(it.epochDay, it.text) },
        )
    }

    suspend fun import(uri: Uri): Result<RestoreSummary> = withContext(Dispatchers.IO) {
        runCatching {
            val text = context.contentResolver.openInputStream(uri)?.use {
                it.readBytes().decodeToString()
            } ?: error("Could not read the chosen file")

            val archive = json.decodeFromString(BackupArchive.serializer(), text)
            require(archive.formatVersion <= BackupArchive.FORMAT_VERSION) {
                "This backup was written by a newer version of the app"
            }
            restore(archive)
        }
    }

    private suspend fun restore(archive: BackupArchive): RestoreSummary {
        val now = System.currentTimeMillis()
        val categoryDao = database.categoryDao()
        val eventDao = database.eventDao()

        // Match categories by name so a restore onto a fresh install reuses the seeded ones
        // instead of creating a second "ការងារ".
        val existingByName = categoryDao.all().associateBy { it.name }
        val categoryIdMap = HashMap<Long, Long>()
        var newCategories = 0
        for (c in archive.categories) {
            val existing = existingByName[c.name]
            val newId = if (existing != null) {
                existing.id
            } else {
                newCategories++
                categoryDao.upsert(
                    CategoryEntity(
                        name = c.name,
                        colorArgb = c.colorArgb,
                        isVisible = c.isVisible,
                        sortOrder = c.sortOrder,
                        isBuiltIn = false,
                    )
                )
            }
            categoryIdMap[c.id] = newId
        }

        var importedEvents = 0
        for (e in archive.events) {
            val newId = eventDao.insert(
                EventEntity(
                    id = 0,
                    title = e.title,
                    description = e.description,
                    location = e.location,
                    startUtcMillis = e.startUtcMillis,
                    endUtcMillis = e.endUtcMillis,
                    allDay = e.allDay,
                    zoneId = e.zoneId,
                    rrule = e.rrule,
                    categoryId = e.categoryId?.let { categoryIdMap[it] },
                    colorArgb = e.colorArgb,
                    isTask = e.isTask,
                    isCompleted = e.isCompleted,
                    completedAtMillis = if (e.isCompleted) now else null,
                    createdAtMillis = now,
                    updatedAtMillis = now,
                )
            )
            database.reminderDao().replaceForEvent(newId, e.reminderMinutes)
            e.exceptionEpochDays.forEach { day ->
                database.eventExceptionDao().insert(EventExceptionEntity(newId, day))
            }
            importedEvents++
        }

        var importedNotes = 0
        for (n in archive.notes) {
            database.dayNoteDao().upsert(DayNoteEntity(epochDay = n.epochDay, text = n.text, updatedAtMillis = now))
            importedNotes++
        }

        return RestoreSummary(importedEvents, newCategories, importedNotes)
    }
}
