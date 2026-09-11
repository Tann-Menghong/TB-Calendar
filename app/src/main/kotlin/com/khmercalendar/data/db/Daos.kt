package com.khmercalendar.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

private const val CANDIDATES_SQL =
    "SELECT * FROM events " +
        "WHERE (rrule IS NULL AND endUtcMillis >= :fromMillis AND startUtcMillis <= :toMillis) " +
        "   OR (rrule IS NOT NULL AND startUtcMillis <= :toMillis) " +
        "ORDER BY startUtcMillis"

@Dao
interface CategoryDao {

    @Query("SELECT * FROM categories ORDER BY sortOrder, name")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories ORDER BY sortOrder, name")
    suspend fun all(): List<CategoryEntity>

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun byId(id: Long): CategoryEntity?

    @Upsert
    suspend fun upsert(category: CategoryEntity): Long

    @Delete
    suspend fun delete(category: CategoryEntity)

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun count(): Int
}

@Dao
interface EventDao {

    /**
     * Every row that could produce an occurrence in the window.
     *
     * A one-off qualifies when it overlaps the window. A repeating series qualifies when it
     * merely starts before the window ends, because whether it actually lands inside depends
     * on the rule, which SQLite cannot evaluate. Expanding those few rows in Kotlin is far
     * cheaper than storing every occurrence.
     */
    @Query(CANDIDATES_SQL)
    fun observeCandidates(fromMillis: Long, toMillis: Long): Flow<List<EventEntity>>

    @Query(CANDIDATES_SQL)
    suspend fun candidates(fromMillis: Long, toMillis: Long): List<EventEntity>

    @Query("SELECT * FROM events ORDER BY startUtcMillis")
    suspend fun allEvents(): List<EventEntity>

    @Query("SELECT * FROM events WHERE id = :id")
    suspend fun byId(id: Long): EventEntity?

    @Query("SELECT * FROM events WHERE id = :id")
    fun observeById(id: Long): Flow<EventEntity?>

    /**
     * Free-text search.
     *
     * A LIKE scan rather than FTS, deliberately. Khmer is written without spaces between
     * words, so an FTS tokeniser splits it in places that are not word boundaries and then
     * fails to match the substring the user typed. A substring scan over a personal calendar
     * is a few thousand rows at most, and it matches what people expect typing mid-word.
     *
     * [query] must already have its LIKE metacharacters escaped - see
     * [com.khmercalendar.data.repo.EventRepository.escapeForLike]. Without that, searching for
     * "%" returns the entire calendar and "_" matches any single character.
     *
     * The join is how a category is searched. A category is not a thing you can open,
     * so "search by category" can only mean "the events in it" - and doing that in SQL
     * keeps it one query and one flow, rather than a second lookup whose results have to
     * be merged and de-duplicated in Kotlin.
     */
    @Query(
        "SELECT events.* FROM events " +
            "LEFT JOIN categories ON events.categoryId = categories.id " +
            "WHERE events.title LIKE '%' || :query || '%' ESCAPE '\\' " +
            "   OR events.description LIKE '%' || :query || '%' ESCAPE '\\' " +
            "   OR events.location LIKE '%' || :query || '%' ESCAPE '\\' " +
            "   OR categories.name LIKE '%' || :query || '%' ESCAPE '\\' " +
            "ORDER BY events.startUtcMillis DESC LIMIT 300"
    )
    fun search(query: String): Flow<List<EventEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(event: EventEntity): Long

    @Update
    suspend fun update(event: EventEntity)

    @Query("DELETE FROM events WHERE id = :id")
    suspend fun deleteById(id: Long)

    /**
     * Ticks or unticks a task.
     *
     * [completedAt] is separate from [atMillis] because un-ticking must *clear* it. The
     * single-parameter form wrote the current time into `completedAtMillis` whichever way the
     * flag went, so a task ticked in January and un-ticked in March was left claiming it had
     * been completed in March. Nothing read the column, so nothing showed it - until the
     * statistics screen, which reads exactly this column to decide what was done when.
     *
     * Rows already carrying a stale timestamp need no repair: every query that reads it also
     * requires `isCompleted = 1`, and an un-ticked row is excluded by that alone.
     */
    @Query(
        "UPDATE events SET isCompleted = :completed, completedAtMillis = :completedAt, " +
            "updatedAtMillis = :atMillis WHERE id = :id"
    )
    suspend fun setCompleted(id: Long, completed: Boolean, completedAt: Long?, atMillis: Long)

    /**
     * Tasks ticked off inside a window, for the statistics screen.
     *
     * Keyed on when it was *completed* rather than when it was due, which is what "finished
     * this week" means. A null timestamp is excluded rather than treated as the epoch: a row
     * whose completion date is unknown is not a task completed in 1970.
     *
     * A repeating task contributes at most one row, because a series is one row and carries
     * one flag. That under-reports somebody who uses a daily repeating task as a checklist,
     * and under-reporting is the safe direction to be wrong in.
     */
    @Query(
        "SELECT * FROM events WHERE isTask = 1 AND isCompleted = 1 " +
            "AND completedAtMillis IS NOT NULL AND rrule IS NULL " +
            "AND completedAtMillis BETWEEN :fromMillis AND :toMillis " +
            "ORDER BY completedAtMillis"
    )
    fun observeCompletedTasksBetween(fromMillis: Long, toMillis: Long): Flow<List<EventEntity>>

    @Query("UPDATE events SET priority = :priority, updatedAtMillis = :atMillis WHERE id = :id")
    suspend fun setPriority(id: Long, priority: Int, atMillis: Long)

    @Query("UPDATE events SET isPinned = :pinned, updatedAtMillis = :atMillis WHERE id = :id")
    suspend fun setPinned(id: Long, pinned: Boolean, atMillis: Long)

    /**
     * Every pinned row, whatever its date.
     *
     * Unfiltered by time on purpose: a pinned birthday is a yearly series whose stored start
     * is years in the past, and a countdown to it is its *next* occurrence. Deciding that
     * needs the recurrence rule, which SQLite cannot evaluate - so the filtering happens
     * after expansion, in the repository. There are only ever a handful of pinned rows.
     */
    @Query("SELECT * FROM events WHERE isPinned = 1 ORDER BY startUtcMillis")
    fun observePinned(): Flow<List<EventEntity>>

    @Query("SELECT COUNT(*) FROM events")
    suspend fun count(): Int
}

@Dao
interface ReminderDao {

    @Query("SELECT * FROM reminders WHERE eventId = :eventId ORDER BY minutesBefore")
    suspend fun forEvent(eventId: Long): List<ReminderEntity>

    @Query("SELECT * FROM reminders WHERE eventId = :eventId ORDER BY minutesBefore")
    fun observeForEvent(eventId: Long): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders")
    suspend fun all(): List<ReminderEntity>

    @Insert
    suspend fun insertAll(reminders: List<ReminderEntity>)

    @Query("DELETE FROM reminders WHERE eventId = :eventId")
    suspend fun deleteForEvent(eventId: Long)

    @Transaction
    suspend fun replaceForEvent(eventId: Long, minutes: List<Int>) {
        deleteForEvent(eventId)
        if (minutes.isNotEmpty()) {
            insertAll(minutes.distinct().map { ReminderEntity(eventId = eventId, minutesBefore = it) })
        }
    }
}

@Dao
interface EventExceptionDao {

    @Query("SELECT * FROM event_exceptions")
    fun observeAll(): Flow<List<EventExceptionEntity>>

    @Query("SELECT * FROM event_exceptions")
    suspend fun all(): List<EventExceptionEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(exception: EventExceptionEntity)

    @Query("DELETE FROM event_exceptions WHERE eventId = :eventId")
    suspend fun deleteForEvent(eventId: Long)
}

@Dao
interface DayNoteDao {

    @Query("SELECT * FROM day_notes WHERE epochDay = :epochDay")
    fun observeForDay(epochDay: Long): Flow<DayNoteEntity?>

    @Query("SELECT * FROM day_notes WHERE epochDay BETWEEN :from AND :to")
    fun observeRange(from: Long, to: Long): Flow<List<DayNoteEntity>>

    @Query("SELECT * FROM day_notes")
    suspend fun all(): List<DayNoteEntity>

    /**
     * Free-text search over day notes.
     *
     * Same LIKE scan and the same escaping contract as [EventDao.search], for the same
     * reason: an FTS tokeniser splits Khmer where words do not end. Notes were not searchable
     * at all before - typing a word you had written into a note found nothing, which reads as
     * the note being gone.
     */
    @Query(
        "SELECT * FROM day_notes WHERE text LIKE '%' || :query || '%' ESCAPE '\\' " +
            "ORDER BY epochDay DESC LIMIT 100"
    )
    fun search(query: String): Flow<List<DayNoteEntity>>

    /**
     * Writes a day's note, replacing whatever that day held.
     *
     * `REPLACE`, not `@Upsert`. Every save builds a fresh entity with id 0, and Room's upsert
     * falls back from a failed insert to an `UPDATE ... WHERE id = ?` - so when the day
     * already had a note, the conflict was on `epochDay`, the update matched id 0, and nothing
     * was written. Editing an existing note was silently discarded from the first release:
     * the dashboard field snapped back to the old text, and only the first note of each day
     * ever saved.
     *
     * Not SQLite's `ON CONFLICT ... DO UPDATE` either, which would keep the row id: that
     * arrived in SQLite 3.24, and Android 8.0 ships 3.18. `REPLACE` deletes the old row and
     * inserts the new one, which is harmless here because nothing refers to a note by its id.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(note: DayNoteEntity)

    @Query("DELETE FROM day_notes WHERE epochDay = :epochDay")
    suspend fun deleteForDay(epochDay: Long)
}

@Dao
interface HabitDao {

    @Query("SELECT * FROM habits ORDER BY sortOrder, name")
    fun observeAll(): Flow<List<HabitEntity>>

    @Query("SELECT * FROM habits ORDER BY sortOrder, name")
    suspend fun all(): List<HabitEntity>

    @Query("SELECT * FROM habits WHERE id = :id")
    suspend fun byId(id: Long): HabitEntity?

    @Upsert
    suspend fun upsert(habit: HabitEntity): Long

    @Query("DELETE FROM habits WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE habits SET archivedAtMillis = :atMillis WHERE id = :id")
    suspend fun setArchived(id: Long, atMillis: Long?)

    /**
     * Every tick from [fromEpochDay] onwards.
     *
     * Bounded rather than the whole table: streaks and the thirty-day rate never look back
     * further than a year, and a habit kept for a decade is otherwise 3,650 rows read to
     * draw one screen.
     */
    @Query("SELECT * FROM habit_entries WHERE epochDay >= :fromEpochDay")
    fun observeEntriesSince(fromEpochDay: Long): Flow<List<HabitEntryEntity>>

    /**
     * Ticks inside a window, both ends bounded.
     *
     * [observeEntriesSince] is anchored on today, which is right for the habits screen and
     * wrong for statistics: looking at last year would otherwise read every tick since then
     * and throw away all but twelve months of them.
     */
    @Query("SELECT * FROM habit_entries WHERE epochDay BETWEEN :fromEpochDay AND :toEpochDay")
    fun observeEntriesBetween(fromEpochDay: Long, toEpochDay: Long): Flow<List<HabitEntryEntity>>

    /**
     * Ticks a habit for a day.
     *
     * IGNORE rather than REPLACE: the unique index already makes a second tick meaningless,
     * and REPLACE would delete and reinsert the row, moving `completedAtMillis` for no reason
     * every time a list redraws and somebody double-taps.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertEntry(entry: HabitEntryEntity)

    @Query("DELETE FROM habit_entries WHERE habitId = :habitId AND epochDay = :epochDay")
    suspend fun deleteEntry(habitId: Long, epochDay: Long)

    @Query("SELECT COUNT(*) FROM habits WHERE archivedAtMillis IS NULL")
    suspend fun activeCount(): Int

    /** Every tick ever recorded, for a backup. Unbounded, because a backup is everything. */
    @Query("SELECT * FROM habit_entries ORDER BY habitId, epochDay")
    suspend fun allEntries(): List<HabitEntryEntity>
}

@Dao
interface ChecklistDao {

    @Query("SELECT * FROM checklist_items WHERE eventId = :eventId ORDER BY sortOrder, id")
    fun observeForEvent(eventId: Long): Flow<List<ChecklistItemEntity>>

    /**
     * Every line belonging to any of [eventIds].
     *
     * One query for a screenful of tasks rather than one per task: the task list draws its
     * progress figures from this, and a query per row is how a list starts stuttering.
     */
    @Query("SELECT * FROM checklist_items WHERE eventId IN (:eventIds)")
    fun observeForEvents(eventIds: List<Long>): Flow<List<ChecklistItemEntity>>

    @Insert
    suspend fun insert(item: ChecklistItemEntity): Long

    @Query("UPDATE checklist_items SET isDone = :done WHERE id = :id")
    suspend fun setDone(id: Long, done: Boolean)

    @Query("UPDATE checklist_items SET text = :text WHERE id = :id")
    suspend fun setText(id: Long, text: String)

    @Query("UPDATE checklist_items SET sortOrder = :sortOrder WHERE id = :id")
    suspend fun setSortOrder(id: Long, sortOrder: Int)

    @Query("DELETE FROM checklist_items WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT COUNT(*) FROM checklist_items WHERE eventId = :eventId")
    suspend fun countForEvent(eventId: Long): Int

    /** Every step of every task, for a backup. */
    @Query("SELECT * FROM checklist_items ORDER BY eventId, sortOrder, id")
    suspend fun all(): List<ChecklistItemEntity>
}

@Dao
interface FocusDao {

    /** The running session, if any. There is at most one; see [FocusRepository.start]. */
    @Query("SELECT * FROM focus_sessions WHERE endedAtMillis IS NULL ORDER BY startedAtMillis DESC LIMIT 1")
    fun observeRunning(): Flow<FocusSessionEntity?>

    @Query("SELECT * FROM focus_sessions WHERE endedAtMillis IS NULL ORDER BY startedAtMillis DESC LIMIT 1")
    suspend fun running(): FocusSessionEntity?

    /**
     * Sessions from [fromMillis] onwards.
     *
     * Bounded: the screen shows today and this week, so reading a year of sessions to draw
     * two figures would be paid for on every redraw.
     */
    @Query("SELECT * FROM focus_sessions WHERE startedAtMillis >= :fromMillis ORDER BY startedAtMillis DESC")
    fun observeSince(fromMillis: Long): Flow<List<FocusSessionEntity>>

    /**
     * Sessions inside a window, both ends bounded, for the statistics screen.
     *
     * A session is attributed to the day it *started*, so the bound is on `startedAtMillis`
     * alone. One that runs past midnight belongs to the evening it began in, which is how the
     * person who sat through it would describe it.
     */
    @Query(
        "SELECT * FROM focus_sessions WHERE startedAtMillis BETWEEN :fromMillis AND :toMillis " +
            "ORDER BY startedAtMillis DESC"
    )
    fun observeBetween(fromMillis: Long, toMillis: Long): Flow<List<FocusSessionEntity>>

    @Insert
    suspend fun insert(session: FocusSessionEntity): Long

    @Query("UPDATE focus_sessions SET endedAtMillis = :endedAtMillis WHERE id = :id")
    suspend fun finish(id: Long, endedAtMillis: Long)

    @Query("DELETE FROM focus_sessions WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT COUNT(*) FROM focus_sessions")
    suspend fun count(): Int

    /** Every session, for a backup. */
    @Query("SELECT * FROM focus_sessions ORDER BY startedAtMillis")
    suspend fun all(): List<FocusSessionEntity>
}

/** A ticked occurrence of a repeating task, with what statistics need from its series. */
data class CompletedOccurrenceRow(
    val eventId: Long,
    val epochDay: Long,
    val completedAtMillis: Long,
    val priority: Int,
    val categoryId: Long?,
)

@Dao
interface TaskCompletionDao {

    /**
     * Ticks inside a window of days, for expanding occurrences.
     *
     * Bounded by the day the occurrence falls on, the same window the occurrence query uses, so
     * a year of a daily chore is never read to draw one week.
     */
    @Query("SELECT * FROM task_completions WHERE epochDay BETWEEN :fromEpochDay AND :toEpochDay")
    fun observeBetween(fromEpochDay: Long, toEpochDay: Long): Flow<List<TaskCompletionEntity>>

    @Query("SELECT * FROM task_completions WHERE epochDay BETWEEN :fromEpochDay AND :toEpochDay")
    suspend fun between(fromEpochDay: Long, toEpochDay: Long): List<TaskCompletionEntity>

    @Query("SELECT * FROM task_completions WHERE eventId = :eventId AND epochDay = :epochDay")
    suspend fun find(eventId: Long, epochDay: Long): TaskCompletionEntity?

    /** IGNORE, so a second tap keeps the first completion time rather than moving it. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(completion: TaskCompletionEntity)

    @Query("DELETE FROM task_completions WHERE eventId = :eventId AND epochDay = :epochDay")
    suspend fun delete(eventId: Long, epochDay: Long)

    /**
     * Occurrences ticked inside a window of instants, for statistics.
     *
     * Keyed on when each was *ticked*, the same rule a one-off task follows.
     */
    @Query(
        "SELECT c.eventId AS eventId, c.epochDay AS epochDay, " +
            "c.completedAtMillis AS completedAtMillis, e.priority AS priority, " +
            "e.categoryId AS categoryId " +
            "FROM task_completions c JOIN events e ON e.id = c.eventId " +
            "WHERE c.completedAtMillis BETWEEN :fromMillis AND :toMillis"
    )
    fun observeCompletedBetween(fromMillis: Long, toMillis: Long): Flow<List<CompletedOccurrenceRow>>

    /** Every tick, for a backup. */
    @Query("SELECT * FROM task_completions ORDER BY eventId, epochDay")
    suspend fun all(): List<TaskCompletionEntity>
}
