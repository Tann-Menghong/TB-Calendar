package com.khmercalendar.data.repo

import com.khmercalendar.core.recurrence.RecurrenceExpander
import com.khmercalendar.core.recurrence.RecurrenceRule
import com.khmercalendar.data.db.CategoryDao
import com.khmercalendar.data.db.CategoryEntity
import com.khmercalendar.data.db.DayNoteDao
import com.khmercalendar.data.db.DayNoteEntity
import com.khmercalendar.data.db.EventDao
import com.khmercalendar.data.db.EventEntity
import com.khmercalendar.data.db.EventExceptionDao
import com.khmercalendar.data.db.EventExceptionEntity
import com.khmercalendar.data.db.ReminderDao
import com.khmercalendar.domain.EventDraftModel
import com.khmercalendar.domain.EventOccurrence
import com.khmercalendar.domain.EventTimes
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * The single door to calendar data.
 *
 * Its job beyond plain CRUD is expanding repeating series: the database returns candidate
 * rows for a window and this turns them into the concrete occurrences the UI draws.
 */
class EventRepository(
    private val eventDao: EventDao,
    private val categoryDao: CategoryDao,
    private val reminderDao: ReminderDao,
    private val exceptionDao: EventExceptionDao,
    private val noteDao: DayNoteDao,
    private val zoneProvider: () -> ZoneId = { ZoneId.systemDefault() },
) {

    fun observeCategories(): Flow<List<CategoryEntity>> = categoryDao.observeAll()

    suspend fun categories(): List<CategoryEntity> = categoryDao.all()

    suspend fun upsertCategory(category: CategoryEntity): Long = categoryDao.upsert(category)

    suspend fun deleteCategory(category: CategoryEntity) {
        if (!category.isBuiltIn) categoryDao.delete(category)
    }

    /**
     * Occurrences grouped by day for the closed range [from]..[to].
     *
     * Combines three streams so that editing an event, hiding a category or deleting one
     * occurrence of a series all refresh the grid without the caller subscribing to each.
     */
    fun observeOccurrences(from: LocalDate, to: LocalDate): Flow<Map<LocalDate, List<EventOccurrence>>> {
        val zone = zoneProvider()
        val fromMillis = from.atStartOfDay(zone).toInstant().toEpochMilli()
        val toMillis = to.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return combine(
            eventDao.observeCandidates(fromMillis, toMillis),
            categoryDao.observeAll(),
            exceptionDao.observeAll(),
        ) { events, categories, exceptions ->
            expand(events, categories, exceptions, from, to, zone)
        }
    }

    suspend fun occurrences(from: LocalDate, to: LocalDate): Map<LocalDate, List<EventOccurrence>> {
        val zone = zoneProvider()
        val fromMillis = from.atStartOfDay(zone).toInstant().toEpochMilli()
        val toMillis = to.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return expand(
            eventDao.candidates(fromMillis, toMillis),
            categoryDao.all(),
            exceptionDao.all(),
            from, to, zone,
        )
    }

    private fun expand(
        events: List<EventEntity>,
        categories: List<CategoryEntity>,
        exceptions: List<EventExceptionEntity>,
        from: LocalDate,
        to: LocalDate,
        zone: ZoneId,
    ): Map<LocalDate, List<EventOccurrence>> {
        val categoryById = categories.associateBy { it.id }
        val hidden = categories.filterNot { it.isVisible }.map { it.id }.toSet()
        val exceptionsByEvent = exceptions.groupBy({ it.eventId }, { LocalDate.ofEpochDay(it.exceptionEpochDay) })

        val out = HashMap<LocalDate, MutableList<EventOccurrence>>()
        for (event in events) {
            if (event.categoryId != null && event.categoryId in hidden) continue

            val category = event.categoryId?.let { categoryById[it] }
            val start = EventTimes.fromUtcMillis(event.startUtcMillis, zone, event.allDay)
            val end = EventTimes.fromUtcMillis(event.endUtcMillis, zone, event.allDay)
            val length = Duration.between(start, end)
            val rule = RecurrenceRule.parse(event.rrule)
            val dates = RecurrenceExpander.occurrences(
                start = start.toLocalDate(),
                rule = rule,
                windowStart = from,
                windowEnd = to,
                exceptions = exceptionsByEvent[event.id].orEmpty().toSet(),
            )

            for (date in dates) {
                val occStart = LocalDateTime.of(date, start.toLocalTime())
                val occEnd = occStart.plus(length)
                val occurrence = EventOccurrence(
                    eventId = event.id,
                    title = event.title,
                    description = event.description,
                    location = event.location,
                    occurrenceDate = date,
                    start = occStart,
                    end = occEnd,
                    allDay = event.allDay,
                    colorArgb = event.colorArgb ?: category?.colorArgb ?: DEFAULT_COLOR,
                    categoryId = event.categoryId,
                    categoryName = category?.name,
                    isTask = event.isTask,
                    isCompleted = event.isCompleted,
                    isRecurring = rule != null,
                    priority = event.priority,
                )
                out.getOrPut(date) { mutableListOf() } += occurrence

                // A multi-day event should appear on each day it covers, not only the first.
                if (!event.allDay && occEnd.toLocalDate() > date) {
                    var d = date.plusDays(1)
                    while (!d.isAfter(minOf(occEnd.toLocalDate(), to))) {
                        out.getOrPut(d) { mutableListOf() } += occurrence.copy(occurrenceDate = d)
                        d = d.plusDays(1)
                    }
                }
            }
        }
        out.values.forEach { list ->
            list.sortWith(compareBy({ !it.allDay }, { it.start }, { it.title }))
        }
        return out
    }

    fun observeEvent(id: Long): Flow<EventEntity?> = eventDao.observeById(id)

    suspend fun event(id: Long): EventEntity? = eventDao.byId(id)

    suspend fun reminders(eventId: Long): List<Int> =
        reminderDao.forEvent(eventId).map { it.minutesBefore }

    fun search(query: String): Flow<List<EventEntity>> =
        eventDao.search(escapeForLike(query.trim()))

    /**
     * Makes a user's text safe to drop into a LIKE pattern.
     *
     * `%` and `_` are wildcards to LIKE, so typing them searched for something the user did
     * not ask for: "%" matched the entire calendar, and "_" matched any single character.
     * The backslash goes first, because escaping it after the others would escape the escapes.
     */
    internal fun escapeForLike(raw: String): String = raw
        .replace("\\", "\\\\")
        .replace("%", "\\%")
        .replace("_", "\\_")

    /** Inserts or updates, returning the event id. Reminders are replaced wholesale. */
    suspend fun save(draft: EventDraftModel): Long {
        val zone = zoneProvider()
        val now = System.currentTimeMillis()
        val startLocal = if (draft.allDay) {
            draft.date.atStartOfDay()
        } else {
            LocalDateTime.of(draft.date, draft.startTime)
        }
        val endLocal = when {
            draft.allDay -> draft.endDate.plusDays(1).atStartOfDay()
            // An end time at or before the start means the event runs past midnight.
            draft.endTime <= draft.startTime && draft.endDate == draft.date ->
                LocalDateTime.of(draft.date.plusDays(1), draft.endTime)
            else -> LocalDateTime.of(draft.endDate, draft.endTime)
        }

        val entity = EventEntity(
            id = draft.id,
            title = draft.title.trim().ifBlank { "(គ្មានចំណងជើង)" },
            description = draft.description.trim().ifBlank { null },
            location = draft.location.trim().ifBlank { null },
            startUtcMillis = EventTimes.toUtcMillis(startLocal, zone, draft.allDay),
            endUtcMillis = EventTimes.toUtcMillis(endLocal, zone, draft.allDay),
            allDay = draft.allDay,
            zoneId = zone.id,
            rrule = draft.recurrence?.toRRule(),
            categoryId = draft.categoryId,
            colorArgb = draft.colorArgb,
            isTask = draft.isTask,
            isCompleted = draft.isCompleted,
            priority = draft.priority.stored,
            completedAtMillis = if (draft.isCompleted) now else null,
            createdAtMillis = if (draft.id == 0L) now else (eventDao.byId(draft.id)?.createdAtMillis ?: now),
            updatedAtMillis = now,
        )

        val id = if (draft.id == 0L) {
            eventDao.insert(entity)
        } else {
            eventDao.update(entity)
            draft.id
        }
        reminderDao.replaceForEvent(id, draft.reminderMinutes)
        return id
    }

    /** Duplicates an event, including its reminders, as a new draft-ready copy. */
    suspend fun duplicate(eventId: Long): Long? {
        val original = eventDao.byId(eventId) ?: return null
        val now = System.currentTimeMillis()
        val copy = original.copy(
            id = 0,
            title = original.title + " (ច្បាប់ចម្លង)",
            createdAtMillis = now,
            updatedAtMillis = now,
        )
        val newId = eventDao.insert(copy)
        reminderDao.replaceForEvent(newId, reminders(eventId))
        return newId
    }

    suspend fun deleteEvent(eventId: Long) = eventDao.deleteById(eventId)

    /** Removes a single date from a repeating series, leaving the rest intact. */
    suspend fun deleteOccurrence(eventId: Long, date: LocalDate) {
        exceptionDao.insert(EventExceptionEntity(eventId, date.toEpochDay()))
    }

    suspend fun setCompleted(eventId: Long, completed: Boolean) {
        eventDao.setCompleted(eventId, completed, System.currentTimeMillis())
    }

    /** Changes a task's urgency in place, without going through the full editor. */
    suspend fun setPriority(eventId: Long, priority: com.khmercalendar.domain.TaskPriority) {
        eventDao.setPriority(eventId, priority.stored, System.currentTimeMillis())
    }

    suspend fun allEvents(): List<EventEntity> = eventDao.allEvents()

    suspend fun eventCount(): Int = eventDao.count()

    suspend fun completedTaskCount(): Int = eventDao.completedTaskCount()

    // --- day notes -------------------------------------------------------------------

    fun observeNote(date: LocalDate): Flow<String?> =
        noteDao.observeForDay(date.toEpochDay()).map { it?.text }

    fun observeNotes(from: LocalDate, to: LocalDate): Flow<Set<LocalDate>> =
        noteDao.observeRange(from.toEpochDay(), to.toEpochDay())
            .map { notes -> notes.map { LocalDate.ofEpochDay(it.epochDay) }.toSet() }

    suspend fun saveNote(date: LocalDate, text: String) {
        if (text.isBlank()) {
            noteDao.deleteForDay(date.toEpochDay())
        } else {
            noteDao.upsert(
                DayNoteEntity(
                    epochDay = date.toEpochDay(),
                    text = text.trim(),
                    updatedAtMillis = System.currentTimeMillis(),
                )
            )
        }
    }

    /** The next occurrences after [from], for the agenda and the widget. */
    suspend fun upcoming(from: LocalDateTime, days: Int): List<EventOccurrence> {
        val map = occurrences(from.toLocalDate(), from.toLocalDate().plusDays(days.toLong()))
        return map.values.flatten()
            .filter { it.allDay || !it.end.isBefore(from) }
            .distinctBy { it.eventId to it.occurrenceDate }
            .sortedWith(compareBy({ it.occurrenceDate }, { !it.allDay }, { it.start }))
    }

    private companion object {
        const val DEFAULT_COLOR = 0xFF2F6FED.toInt()
    }
}

/** Convenience for a whole-day window. */
fun LocalDate.dayWindow(): Pair<LocalDateTime, LocalDateTime> =
    atStartOfDay() to atTime(LocalTime.MAX)
