package com.khmercalendar.data.repo

import com.khmercalendar.core.recurrence.RecurrenceExpander
import com.khmercalendar.core.recurrence.RecurrenceRule
import com.khmercalendar.data.db.CategoryDao
import com.khmercalendar.data.db.ChecklistItemEntity
import com.khmercalendar.data.db.ChecklistDao
import com.khmercalendar.data.db.CategoryEntity
import com.khmercalendar.data.db.DayNoteDao
import com.khmercalendar.data.db.DayNoteEntity
import com.khmercalendar.data.db.EventDao
import com.khmercalendar.data.db.EventEntity
import com.khmercalendar.data.db.EventExceptionDao
import com.khmercalendar.data.db.EventExceptionEntity
import com.khmercalendar.data.db.ReminderDao
import com.khmercalendar.domain.CompletedTask
import com.khmercalendar.domain.CountdownItem
import com.khmercalendar.domain.ChecklistItem
import com.khmercalendar.domain.Checklist
import com.khmercalendar.domain.EventDraftModel
import com.khmercalendar.domain.EventOccurrence
import com.khmercalendar.domain.EventTimes
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.Duration
import java.time.Instant
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
    private val checklistDao: ChecklistDao,
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
                    isPinned = event.isPinned,
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

    /** Day notes matching [query]. Same escaping as [search]; see [escapeForLike]. */
    fun searchNotes(query: String): Flow<List<DayNoteEntity>> =
        noteDao.search(escapeForLike(query.trim()))

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
            // Preserved across an edit rather than carried on the draft: pinning is done
            // from the detail screen with one tap, and the editor has no pin control, so
            // taking it from a draft would silently unpin anything you edited.
            isPinned = if (draft.id == 0L) false else (eventDao.byId(draft.id)?.isPinned ?: false),
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

    /**
     * Ticks or unticks a task.
     *
     * Un-ticking clears the completion time rather than overwriting it with now. Nothing read
     * that column until statistics did, at which point a task ticked in January and un-ticked
     * in March would have counted as March's work.
     */
    suspend fun setCompleted(eventId: Long, completed: Boolean) {
        val now = System.currentTimeMillis()
        eventDao.setCompleted(
            id = eventId,
            completed = completed,
            completedAt = if (completed) now else null,
            atMillis = now,
        )
    }

    /** Changes a task's urgency in place, without going through the full editor. */
    suspend fun setPriority(eventId: Long, priority: com.khmercalendar.domain.TaskPriority) {
        eventDao.setPriority(eventId, priority.stored, System.currentTimeMillis())
    }

    // --- checklists -----------------------------------------------------------------

    /** The steps inside one task, in their own order. */
    fun observeChecklist(eventId: Long): Flow<List<ChecklistItem>> =
        checklistDao.observeForEvent(eventId).map { rows -> rows.map { it.toItem() } }

    /**
     * Checklist progress for a screenful of tasks, keyed by task.
     *
     * One query for the whole list rather than one per row - the task list draws a progress
     * figure on every task it shows, and a query per row is how a list starts stuttering.
     */
    fun observeChecklistProgress(eventIds: List<Long>): Flow<Map<Long, Checklist.Progress>> {
        if (eventIds.isEmpty()) return flowOf(emptyMap())
        return checklistDao.observeForEvents(eventIds).map { rows ->
            rows.groupBy { it.eventId }
                .mapValues { (_, items) -> Checklist.progress(items.map { it.toItem() }) }
        }
    }

    suspend fun addChecklistItem(eventId: Long, text: String): Long? {
        val clean = Checklist.clean(text)
        if (clean.isBlank()) return null
        val existing = checklistDao.observeForEvent(eventId).first().map { it.toItem() }
        if (!Checklist.canAdd(existing)) return null
        return checklistDao.insert(
            ChecklistItemEntity(
                eventId = eventId,
                text = clean,
                sortOrder = Checklist.nextSortOrder(existing),
                createdAtMillis = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun setChecklistItemDone(itemId: Long, done: Boolean) =
        checklistDao.setDone(itemId, done)

    suspend fun deleteChecklistItem(itemId: Long) = checklistDao.deleteById(itemId)

    /**
     * Moves a line and writes the whole new order.
     *
     * Every position is rewritten rather than only the two that swapped, because that also
     * repairs a list whose positions have drifted apart after deletions - and a checklist is
     * at most fifty rows, so the cost is nothing.
     */
    suspend fun moveChecklistItem(eventId: Long, from: Int, to: Int) {
        val items = checklistDao.observeForEvent(eventId).first().map { it.toItem() }
        for ((id, order) in Checklist.reorder(items, from, to)) {
            checklistDao.setSortOrder(id, order)
        }
    }

    private fun ChecklistItemEntity.toItem() = ChecklistItem(
        id = id,
        text = text,
        isDone = isDone,
        sortOrder = sortOrder,
    )

    /** Pins or unpins a date as a countdown. */
    suspend fun setPinned(eventId: Long, pinned: Boolean) {
        eventDao.setPinned(eventId, pinned, System.currentTimeMillis())
    }

    /**
     * Pinned dates as countdowns, each resolved to its next occurrence from [today].
     *
     * The expansion is the point. A pinned birthday is a yearly series whose stored start is
     * years in the past, so the row's own date is never the answer; the countdown is to the
     * next occurrence, which only the recurrence rule can say. A one-off resolves to itself,
     * and one that has already gone resolves to nothing and drops out - the countdown screen
     * reports those separately from the row rather than showing a negative number.
     */
    fun observeCountdowns(today: LocalDate): Flow<List<CountdownItem>> {
        val zone = zoneProvider()
        return eventDao.observePinned().map { events ->
            events.mapNotNull { event -> nextOccurrence(event, today, zone) }
                .sortedWith(compareBy({ it.date }, { it.at }, { it.title }))
        }
    }

    /**
     * Pinned dates that have already gone.
     *
     * Only a one-off can be here: a repeating series always has a next occurrence, so
     * [observeCountdowns] resolves it forward and it never reaches this list. That is the
     * behaviour both want - a birthday counts to the next one, an exam you sat stays sat.
     */
    fun observePinnedPast(today: LocalDate): Flow<List<CountdownItem>> {
        val zone = zoneProvider()
        return eventDao.observePinned().map { events ->
            events
                .filter { nextOccurrence(it, today, zone) == null }
                .map { event ->
                    val start = EventTimes.fromUtcMillis(event.startUtcMillis, zone, event.allDay)
                    CountdownItem(
                        eventId = event.id,
                        title = event.title,
                        date = start.toLocalDate(),
                        at = start,
                        allDay = event.allDay,
                        isHoliday = false,
                        isPinned = true,
                    )
                }
                .sortedByDescending { it.date }
        }
    }

    /**
     * The first occurrence of [event] on or after [today], as a countdown.
     *
     * The window is a little over a year so that a yearly series always lands inside it
     * whichever side of its anniversary today falls, and a one-off further out than that is
     * still found by starting the window at the event's own date when it is in the future.
     */
    private fun nextOccurrence(event: EventEntity, today: LocalDate, zone: ZoneId): CountdownItem? {
        val start = EventTimes.fromUtcMillis(event.startUtcMillis, zone, event.allDay)
        val rule = RecurrenceRule.parse(event.rrule)
        val windowStart = if (rule == null) start.toLocalDate() else today
        val date = RecurrenceExpander.occurrences(
            start = start.toLocalDate(),
            rule = rule,
            windowStart = windowStart,
            windowEnd = maxOf(windowStart, today.plusDays(COUNTDOWN_WINDOW_DAYS)),
            exceptions = emptySet(),
        ).firstOrNull { !it.isBefore(today) } ?: return null

        return CountdownItem(
            eventId = event.id,
            title = event.title,
            date = date,
            at = LocalDateTime.of(date, start.toLocalTime()),
            allDay = event.allDay,
            isHoliday = false,
            isPinned = true,
        )
    }

    suspend fun allEvents(): List<EventEntity> = eventDao.allEvents()

    /**
     * Tasks ticked off between two dates, as the statistics screen counts them.
     *
     * The window is widened to whole local days at both ends before it becomes an instant,
     * so a task finished at ten past eleven at night belongs to that day rather than to
     * whichever day the UTC boundary happened to fall in.
     */
    fun observeCompletedTasks(from: LocalDate, to: LocalDate): Flow<List<CompletedTask>> {
        val zone = ZoneId.systemDefault()
        val fromMillis = from.atStartOfDay(zone).toInstant().toEpochMilli()
        val toMillis = to.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
        return eventDao.observeCompletedTasksBetween(fromMillis, toMillis).map { rows ->
            rows.mapNotNull { row ->
                val at = row.completedAtMillis ?: return@mapNotNull null
                CompletedTask(
                    eventId = row.id,
                    date = Instant.ofEpochMilli(at).atZone(zone).toLocalDate(),
                    priority = com.khmercalendar.domain.TaskPriority.of(row.priority),
                    categoryId = row.categoryId,
                )
            }
        }
    }

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

        /**
         * How far ahead a pinned date is looked for.
         *
         * A little over a year, so a yearly series lands inside the window whichever side of
         * its anniversary today falls on.
         */
        const val COUNTDOWN_WINDOW_DAYS = 400L
    }
}

/** Convenience for a whole-day window. */
fun LocalDate.dayWindow(): Pair<LocalDateTime, LocalDateTime> =
    atStartOfDay() to atTime(LocalTime.MAX)
