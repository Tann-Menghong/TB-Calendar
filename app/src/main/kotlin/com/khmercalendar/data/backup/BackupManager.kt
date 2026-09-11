package com.khmercalendar.data.backup

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.khmercalendar.data.db.CategoryEntity
import com.khmercalendar.data.db.ChecklistItemEntity
import com.khmercalendar.data.db.DayNoteEntity
import com.khmercalendar.data.db.EventEntity
import com.khmercalendar.data.db.EventExceptionEntity
import com.khmercalendar.data.db.FocusSessionEntity
import com.khmercalendar.data.db.HabitEntity
import com.khmercalendar.data.db.HabitEntryEntity
import com.khmercalendar.data.db.KhmerCalendarDatabase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** How many of each kind of thing: in a file, added by a restore, or skipped by one. */
data class BackupCounts(
    val events: Int = 0,
    val categories: Int = 0,
    val notes: Int = 0,
    val checklistItems: Int = 0,
    val habits: Int = 0,
    val habitDays: Int = 0,
    val focusSessions: Int = 0,
) {
    val isEmpty: Boolean
        get() = events == 0 && categories == 0 && notes == 0 && checklistItems == 0 &&
            habits == 0 && habitDays == 0 && focusSessions == 0
}

/**
 * Which parts of a backup to bring back.
 *
 * [settings] is off by default while the other two are on, because they are different kinds of
 * operation. Calendar and productivity data are *merged* - nothing already on the phone is
 * removed or overwritten, so restoring them can only add. Settings can only be *replaced*, so
 * bringing them back is a separate, deliberate choice rather than a side effect.
 */
data class RestoreSelection(
    val calendar: Boolean = true,
    val productivity: Boolean = true,
    val settings: Boolean = false,
) {
    val isEmpty: Boolean get() = !calendar && !productivity && !settings
}

/**
 * What a file contains, read before anything is written.
 *
 * @property alreadyHere what a restore would skip because the phone already has it. Worked out
 *   by the same planning step the restore itself runs, so the preview cannot promise something
 *   the restore then does differently.
 * @property unreadable rows that failed validation and will not be restored.
 */
data class BackupPreview(
    val archive: BackupArchive,
    val inFile: BackupCounts,
    val alreadyHere: BackupCounts,
    val unreadable: Int,
    val hasSettings: Boolean,
)

data class RestoreSummary(
    val added: BackupCounts,
    val skipped: BackupCounts,
    /** Days whose note on the phone differed from the file's. Both texts were kept. */
    val notesMerged: Int,
    val unreadable: Int,
    val settingsApplied: Boolean,
    /** Settings were chosen but could not be written. Everything else was still restored. */
    val settingsFailed: Boolean,
)

/**
 * The settings half of a backup.
 *
 * An interface rather than a direct dependency on the settings store, so a backup can be tested
 * against a real database without a real DataStore, and so this class never has to know how any
 * preference is stored.
 */
interface PortableSettings {
    suspend fun exportPortable(): BackupSettings
    suspend fun applyPortable(incoming: BackupSettings)
}

/**
 * Export and import of everything the user has made, without a cloud account.
 *
 * ## A restore never destroys
 *
 * A restore is additive and re-keyed: every row is inserted with a fresh id and every link -
 * category, reminder, checklist step, habit tick, the task a focus session was for - is
 * rewritten to match. Keeping the file's ids would be simpler and would overwrite whatever the
 * user created since the backup, which is the one outcome a restore must never produce.
 *
 * What is already on the phone is left exactly as it is:
 *
 * - an event identical to one already there is skipped, so restoring the same file twice adds
 *   nothing the second time - it used to double the calendar;
 * - a habit with the same name gains the file's ticks rather than being created a second time;
 * - a day whose note differs keeps its own note, with the file's text added beneath it.
 *
 * ## All or nothing
 *
 * The restore runs in one transaction. It used to insert row by row, so a failure halfway left
 * half a backup in the calendar under a message saying the restore had failed - and because a
 * restore only ever adds, trying again doubled whatever half had made it in.
 */
class BackupManager(
    private val context: Context,
    private val database: KhmerCalendarDatabase,
    private val appVersion: String,
    private val settings: PortableSettings? = null,
) {

    fun suggestedFileName(): String {
        val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm"))
        return "khmer-calendar-backup-$stamp.json"
    }

    // --- export ------------------------------------------------------------------------

    suspend fun export(uri: Uri): Result<BackupCounts> = withContext(Dispatchers.IO) {
        attempt(onIoError = { BackupError.WriteFailed(it) }) {
            val archive = snapshot()
            // Encoded before the file is opened. "wt" truncates on open, so a failure while
            // encoding afterwards would have emptied a previous backup at the chosen location.
            val bytes = BackupCodec.encode(archive).toByteArray(Charsets.UTF_8)
            val stream = context.contentResolver.openOutputStream(uri, "wt")
                ?: throw BackupError.WriteFailed()
            stream.use { it.write(bytes) }
            countsOf(archive)
        }
    }

    /**
     * Everything, read as one consistent picture.
     *
     * Inside a transaction so an edit landing mid-export cannot produce a reminder for an event
     * the file does not contain. Settings are read afterwards - they live in a different store,
     * and holding the database while waiting on it would gain nothing.
     */
    internal suspend fun snapshot(): BackupArchive {
        val data = database.withTransaction {
            val reminders = database.reminderDao().all().groupBy { it.eventId }
            val exceptions = database.eventExceptionDao().all().groupBy { it.eventId }
            val steps = database.checklistDao().all().groupBy { it.eventId }
            val ticks = database.habitDao().allEntries().groupBy { it.habitId }

            BackupArchive(
                formatVersion = BackupArchive.FORMAT_VERSION,
                appVersion = appVersion,
                exportedAtMillis = System.currentTimeMillis(),
                categories = database.categoryDao().all().map {
                    BackupCategory(it.id, it.name, it.colorArgb, it.isVisible, it.sortOrder, it.isBuiltIn)
                },
                events = database.eventDao().allEvents().map { e ->
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
                        priority = e.priority,
                        isPinned = e.isPinned,
                        completedAtMillis = e.completedAtMillis,
                        createdAtMillis = e.createdAtMillis,
                        updatedAtMillis = e.updatedAtMillis,
                        checklist = steps[e.id].orEmpty()
                            .sortedWith(compareBy({ it.sortOrder }, { it.id }))
                            .map { BackupChecklistItem(it.text, it.isDone, it.sortOrder, it.createdAtMillis) },
                    )
                },
                notes = database.dayNoteDao().all().map {
                    BackupNote(it.epochDay, it.text, it.updatedAtMillis)
                },
                habits = database.habitDao().all().map { h ->
                    BackupHabit(
                        id = h.id,
                        name = h.name,
                        colorArgb = h.colorArgb,
                        scheduleKind = h.scheduleKind,
                        scheduleDays = h.scheduleDays,
                        weeklyTarget = h.weeklyTarget,
                        sortOrder = h.sortOrder,
                        archivedAtMillis = h.archivedAtMillis,
                        createdAtMillis = h.createdAtMillis,
                        entries = ticks[h.id].orEmpty().map {
                            BackupHabitEntry(it.epochDay, it.completedAtMillis)
                        },
                    )
                },
                focusSessions = database.focusDao().all().map {
                    BackupFocusSession(it.kind, it.startedAtMillis, it.plannedMinutes, it.endedAtMillis, it.eventId)
                },
            )
        }
        return data.copy(settings = settings?.exportPortable())
    }

    // --- import ------------------------------------------------------------------------

    /** Sniffs the first bytes of a chosen file, so a calendar file is never fed to the JSON parser. */
    suspend fun kindOf(uri: Uri): ImportKind = withContext(Dispatchers.IO) {
        try {
            val head = context.contentResolver.openInputStream(uri)?.use { input ->
                val buffer = ByteArray(HEAD_BYTES)
                val read = input.read(buffer)
                if (read <= 0) "" else String(buffer, 0, read, Charsets.UTF_8)
            }
            if (head == null) ImportKind.UNKNOWN else BackupCodec.kindOf(head)
        } catch (e: IOException) {
            ImportKind.UNKNOWN
        } catch (e: SecurityException) {
            ImportKind.UNKNOWN
        }
    }

    /** Reads a file and works out what restoring it would do. Writes nothing. */
    suspend fun read(uri: Uri): Result<BackupPreview> = withContext(Dispatchers.IO) {
        attempt(onIoError = { BackupError.Unreadable(it) }) {
            val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
                ?: throw BackupError.Unreadable()
            previewOf(text)
        }
    }

    suspend fun restore(archive: BackupArchive, selection: RestoreSelection): Result<RestoreSummary> =
        withContext(Dispatchers.IO) {
            attempt(onIoError = { BackupError.Unreadable(it) }) { restoreArchive(archive, selection) }
        }

    internal suspend fun previewOf(text: String): BackupPreview {
        val archive = BackupCodec.decode(text)
        val plan = plan(archive, RestoreSelection(calendar = true, productivity = true))
        return BackupPreview(
            archive = archive,
            inFile = countsOf(archive),
            alreadyHere = plan.skipped,
            unreadable = plan.unreadable,
            hasSettings = archive.settings != null,
        )
    }

    /**
     * Restores [archive] in one transaction.
     *
     * The plan is recomputed here rather than taken from the preview: the phone may have changed
     * between the two, and duplicates must be judged against what is there now.
     *
     * @param checkpoint called after each event is written. Only tests pass one - it is how a
     *   failure partway through is simulated to prove nothing survives it.
     */
    internal suspend fun restoreArchive(
        archive: BackupArchive,
        selection: RestoreSelection,
        checkpoint: () -> Unit = {},
    ): RestoreSummary {
        val now = System.currentTimeMillis()

        val plan = database.withTransaction {
            val plan = plan(archive, selection)

            // Categories first: events link to them.
            val created = HashMap<String, Long>()
            for (c in plan.newCategories) {
                created[c.name] = database.categoryDao().upsert(
                    CategoryEntity(
                        name = c.name,
                        colorArgb = c.colorArgb,
                        isVisible = c.isVisible,
                        sortOrder = c.sortOrder,
                        isBuiltIn = false,
                    ),
                )
            }
            val categoryIds = HashMap<Long, Long>()
            for (c in archive.categories) {
                val id = plan.categoriesOnDevice[c.name] ?: created[c.name] ?: continue
                categoryIds[c.id] = id
            }

            val eventIds = HashMap<Long, Long>(plan.duplicateEvents)
            for (e in plan.newEvents) {
                val id = database.eventDao().insert(
                    EventEntity(
                        title = e.title,
                        description = e.description,
                        location = e.location,
                        startUtcMillis = e.startUtcMillis,
                        endUtcMillis = e.endUtcMillis,
                        allDay = e.allDay,
                        zoneId = e.zoneId,
                        rrule = e.rrule,
                        categoryId = e.categoryId?.let { categoryIds[it] },
                        colorArgb = e.colorArgb,
                        isTask = e.isTask,
                        isCompleted = e.isCompleted,
                        priority = e.priority,
                        isPinned = e.isPinned,
                        // Only a completed task has a completion time, and a format 1 file never
                        // recorded one. Absent is the truth there; "now" put every task ever
                        // finished into today's statistics.
                        completedAtMillis = if (e.isCompleted) e.completedAtMillis else null,
                        createdAtMillis = e.createdAtMillis ?: now,
                        updatedAtMillis = e.updatedAtMillis ?: now,
                    ),
                )
                eventIds[e.id] = id

                val minutes = e.reminderMinutes.filter { it >= 0 }.distinct()
                if (minutes.isNotEmpty()) database.reminderDao().replaceForEvent(id, minutes)
                e.exceptionEpochDays.distinct().forEach {
                    database.eventExceptionDao().insert(EventExceptionEntity(id, it))
                }
                for (step in e.checklist) {
                    if (step.text.isBlank()) continue
                    database.checklistDao().insert(
                        ChecklistItemEntity(
                            eventId = id,
                            text = step.text.trim(),
                            isDone = step.isDone,
                            sortOrder = step.sortOrder,
                            createdAtMillis = step.createdAtMillis ?: now,
                        ),
                    )
                }
                checkpoint()
            }
            for ((alias, first) in plan.eventAliases) {
                eventIds[first]?.let { eventIds[alias] = it }
            }

            for (n in plan.newNotes) {
                database.dayNoteDao().upsert(
                    DayNoteEntity(epochDay = n.epochDay, text = n.text.trim(), updatedAtMillis = n.updatedAtMillis ?: now),
                )
            }
            for ((n, onDevice) in plan.mergedNotes) {
                database.dayNoteDao().upsert(
                    DayNoteEntity(
                        epochDay = n.epochDay,
                        text = onDevice.text.trimEnd() + NOTE_SEPARATOR + n.text.trim(),
                        updatedAtMillis = now,
                    ),
                )
            }

            val habitIds = HashMap<Long, Long>(plan.duplicateHabits)
            for (h in plan.newHabits) {
                habitIds[h.id] = database.habitDao().upsert(
                    HabitEntity(
                        name = h.name.trim(),
                        colorArgb = h.colorArgb,
                        scheduleKind = h.scheduleKind,
                        scheduleDays = h.scheduleDays,
                        weeklyTarget = h.weeklyTarget.coerceIn(1, 7),
                        sortOrder = h.sortOrder,
                        archivedAtMillis = h.archivedAtMillis,
                        createdAtMillis = h.createdAtMillis,
                    ),
                )
            }
            for ((alias, first) in plan.habitAliases) {
                habitIds[first]?.let { habitIds[alias] = it }
            }
            for ((backupHabitId, entry) in plan.newHabitDays) {
                val habitId = habitIds[backupHabitId] ?: continue
                database.habitDao().insertEntry(
                    HabitEntryEntity(
                        habitId = habitId,
                        epochDay = entry.epochDay,
                        completedAtMillis = entry.completedAtMillis ?: now,
                    ),
                )
            }

            val eventsInFile = archive.events.associateBy { it.id }
            for (f in plan.newFocus) {
                // The task it was for: restored just now, or already on the phone as an identical
                // event. Neither means the task is not in the file, and the link is dropped
                // rather than carried across as a number that means something else here.
                val linked = f.eventId?.let { backupId ->
                    eventIds[backupId] ?: eventsInFile[backupId]?.let { plan.eventsOnDevice[it.key()] }
                }
                database.focusDao().insert(
                    FocusSessionEntity(
                        kind = f.kind,
                        startedAtMillis = f.startedAtMillis,
                        plannedMinutes = f.plannedMinutes,
                        endedAtMillis = f.endedAtMillis,
                        eventId = linked,
                    ),
                )
            }

            plan
        }

        // After the commit, and outside the transaction: settings live in a different store,
        // and a failure there must not undo a calendar that has already been restored.
        var settingsApplied = false
        var settingsFailed = false
        val incoming = archive.settings
        if (selection.settings && incoming != null && settings != null) {
            try {
                settings.applyPortable(incoming)
                settingsApplied = true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Reported in the summary rather than thrown: the data is in, and saying the
                // restore failed would send the user to do it again.
                settingsFailed = true
            }
        }

        return RestoreSummary(
            added = plan.added,
            skipped = plan.skipped,
            notesMerged = plan.mergedNotes.size,
            unreadable = plan.unreadable,
            settingsApplied = settingsApplied,
            settingsFailed = settingsFailed,
        )
    }

    /**
     * Decides, row by row, what a restore would add, skip, merge or reject.
     *
     * Shared by the preview and the restore so the two cannot disagree. Reads only.
     */
    internal suspend fun plan(archive: BackupArchive, selection: RestoreSelection): RestorePlan {
        var unreadable = 0

        val eventsOnDevice = HashMap<EventKey, Long>()
        for (row in database.eventDao().allEvents()) eventsOnDevice.putIfAbsent(row.key(), row.id)

        val categoriesOnDevice = HashMap<String, Long>()
        val newCategories = ArrayList<BackupCategory>()
        var skippedCategories = 0
        val newEvents = ArrayList<BackupEvent>()
        val duplicateEvents = HashMap<Long, Long>()
        val eventAliases = HashMap<Long, Long>()
        val newNotes = ArrayList<BackupNote>()
        val mergedNotes = ArrayList<Pair<BackupNote, DayNoteEntity>>()
        var skippedNotes = 0

        if (selection.calendar) {
            database.categoryDao().all().forEach { categoriesOnDevice.putIfAbsent(it.name, it.id) }
            val namesInFile = HashSet<String>()
            for (c in archive.categories) {
                when {
                    c.name.isBlank() -> unreadable++
                    c.name in categoriesOnDevice -> if (namesInFile.add(c.name)) skippedCategories++
                    namesInFile.add(c.name) -> newCategories += c
                }
            }

            val firstInFile = HashMap<EventKey, Long>()
            for (e in archive.events) {
                if (!BackupValidation.isValid(e)) {
                    unreadable++
                    continue
                }
                val key = e.key()
                val onDevice = eventsOnDevice[key]
                val earlier = firstInFile[key]
                when {
                    onDevice != null -> duplicateEvents[e.id] = onDevice
                    earlier != null -> eventAliases[e.id] = earlier
                    else -> {
                        firstInFile[key] = e.id
                        newEvents += e
                    }
                }
            }

            val notesOnDevice = database.dayNoteDao().all().associateBy { it.epochDay }
            val daysInFile = HashSet<Long>()
            for (n in archive.notes) {
                val text = n.text.trim()
                when {
                    text.isEmpty() -> Unit
                    !BackupValidation.isValidDay(n.epochDay) -> unreadable++
                    !daysInFile.add(n.epochDay) -> skippedNotes++
                    else -> {
                        val onDevice = notesOnDevice[n.epochDay]
                        when {
                            onDevice == null -> newNotes += n
                            onDevice.text.contains(text) -> skippedNotes++
                            else -> mergedNotes += n to onDevice
                        }
                    }
                }
            }
        }

        val newHabits = ArrayList<BackupHabit>()
        val duplicateHabits = HashMap<Long, Long>()
        val habitAliases = HashMap<Long, Long>()
        val newHabitDays = ArrayList<Pair<Long, BackupHabitEntry>>()
        var skippedHabitDays = 0
        val newFocus = ArrayList<BackupFocusSession>()
        var skippedFocus = 0

        if (selection.productivity) {
            val habitsOnDevice = HashMap<String, Long>()
            database.habitDao().all().forEach { habitsOnDevice.putIfAbsent(it.name.trim(), it.id) }
            val ticksOnDevice = database.habitDao().allEntries().mapTo(HashSet()) { it.habitId to it.epochDay }
            val firstHabitByName = HashMap<String, Long>()
            val daysByName = HashMap<String, MutableSet<Long>>()

            for (h in archive.habits) {
                val name = h.name.trim()
                if (name.isEmpty()) {
                    unreadable++
                    continue
                }
                val onDevice = habitsOnDevice[name]
                val earlier = firstHabitByName[name]
                when {
                    onDevice != null -> duplicateHabits[h.id] = onDevice
                    earlier != null -> habitAliases[h.id] = earlier
                    else -> {
                        firstHabitByName[name] = h.id
                        newHabits += h
                    }
                }

                val seen = daysByName.getOrPut(name) { HashSet() }
                for (entry in h.entries) {
                    when {
                        !BackupValidation.isValidDay(entry.epochDay) -> unreadable++
                        !seen.add(entry.epochDay) -> skippedHabitDays++
                        onDevice != null && (onDevice to entry.epochDay) in ticksOnDevice -> skippedHabitDays++
                        else -> newHabitDays += h.id to entry
                    }
                }
            }

            val focusOnDevice = database.focusDao().all().mapTo(HashSet()) { it.kind to it.startedAtMillis }
            for (f in archive.focusSessions) {
                when {
                    !BackupValidation.isValid(f) -> unreadable++
                    !focusOnDevice.add(f.kind to f.startedAtMillis) -> skippedFocus++
                    else -> newFocus += f
                }
            }
        }

        return RestorePlan(
            categoriesOnDevice = categoriesOnDevice,
            newCategories = newCategories,
            skippedCategories = skippedCategories,
            eventsOnDevice = eventsOnDevice,
            newEvents = newEvents,
            duplicateEvents = duplicateEvents,
            eventAliases = eventAliases,
            newNotes = newNotes,
            mergedNotes = mergedNotes,
            skippedNotes = skippedNotes,
            newHabits = newHabits,
            duplicateHabits = duplicateHabits,
            habitAliases = habitAliases,
            newHabitDays = newHabitDays,
            skippedHabitDays = skippedHabitDays,
            newFocus = newFocus,
            skippedFocus = skippedFocus,
            unreadable = unreadable,
        )
    }

    internal fun countsOf(archive: BackupArchive) = BackupCounts(
        events = archive.events.size,
        categories = archive.categories.size,
        notes = archive.notes.size,
        checklistItems = archive.events.sumOf { it.checklist.size },
        habits = archive.habits.size,
        habitDays = archive.habits.sumOf { it.entries.size },
        focusSessions = archive.focusSessions.size,
    )

    /**
     * Runs [block], turning every failure into a [Result] the screen can explain.
     *
     * Cancellation is rethrown rather than reported: a restore abandoned because the screen went
     * away has not failed, and must not be dressed up as though it had.
     */
    private inline fun <T> attempt(onIoError: (Exception) -> BackupError, block: () -> T): Result<T> =
        try {
            Result.success(block())
        } catch (e: CancellationException) {
            throw e
        } catch (e: BackupError) {
            Result.failure(e)
        } catch (e: IOException) {
            Result.failure(onIoError(e))
        } catch (e: SecurityException) {
            Result.failure(onIoError(e))
        } catch (e: Exception) {
            Result.failure(e)
        }

    private companion object {
        /** Enough to see past a byte-order mark and a little whitespace to the first token. */
        const val HEAD_BYTES = 256

        /** Between a note already on the phone and the text a restore adds beneath it. */
        const val NOTE_SEPARATOR = "\n\n"
    }
}

/**
 * What makes two events "the same event" for a restore.
 *
 * Wider than the .ics importer's key, because a backup carries more: the end, the repeat rule and
 * whether it is a task. A copy of an event with a different end time is a different event and is
 * restored beside it.
 */
internal data class EventKey(
    val title: String,
    val start: Long,
    val end: Long,
    val allDay: Boolean,
    val rrule: String?,
    val isTask: Boolean,
)

internal fun EventEntity.key() = EventKey(title, startUtcMillis, endUtcMillis, allDay, rrule, isTask)

internal fun BackupEvent.key() = EventKey(title, startUtcMillis, endUtcMillis, allDay, rrule, isTask)

/**
 * The decisions for one restore, made before anything is written.
 *
 * "Aliases" are rows that repeat an earlier row in the same file; they are resolved to that row's
 * new id once it exists, so a link to either copy lands on the one that was actually restored.
 */
internal class RestorePlan(
    val categoriesOnDevice: Map<String, Long>,
    val newCategories: List<BackupCategory>,
    val skippedCategories: Int,
    val eventsOnDevice: Map<EventKey, Long>,
    val newEvents: List<BackupEvent>,
    val duplicateEvents: Map<Long, Long>,
    val eventAliases: Map<Long, Long>,
    val newNotes: List<BackupNote>,
    val mergedNotes: List<Pair<BackupNote, DayNoteEntity>>,
    val skippedNotes: Int,
    val newHabits: List<BackupHabit>,
    val duplicateHabits: Map<Long, Long>,
    val habitAliases: Map<Long, Long>,
    val newHabitDays: List<Pair<Long, BackupHabitEntry>>,
    val skippedHabitDays: Int,
    val newFocus: List<BackupFocusSession>,
    val skippedFocus: Int,
    val unreadable: Int,
) {
    val added: BackupCounts
        get() = BackupCounts(
            events = newEvents.size,
            categories = newCategories.size,
            notes = newNotes.size,
            checklistItems = newEvents.sumOf { e -> e.checklist.count { it.text.isNotBlank() } },
            habits = newHabits.size,
            habitDays = newHabitDays.size,
            focusSessions = newFocus.size,
        )

    val skipped: BackupCounts
        get() = BackupCounts(
            events = duplicateEvents.size + eventAliases.size,
            categories = skippedCategories,
            notes = skippedNotes,
            habits = duplicateHabits.size + habitAliases.size,
            habitDays = skippedHabitDays,
            focusSessions = skippedFocus,
        )
}
