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

    @Query(
        "UPDATE events SET isCompleted = :completed, completedAtMillis = :atMillis, " +
            "updatedAtMillis = :atMillis WHERE id = :id"
    )
    suspend fun setCompleted(id: Long, completed: Boolean, atMillis: Long)

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

    @Query("SELECT COUNT(*) FROM events WHERE isTask = 1 AND isCompleted = 1")
    suspend fun completedTaskCount(): Int
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

    @Upsert
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
}
