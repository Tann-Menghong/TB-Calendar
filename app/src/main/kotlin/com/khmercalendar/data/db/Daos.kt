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
     */
    @Query(
        "SELECT * FROM events " +
            "WHERE title LIKE '%' || :query || '%' " +
            "   OR description LIKE '%' || :query || '%' " +
            "   OR location LIKE '%' || :query || '%' " +
            "ORDER BY startUtcMillis DESC LIMIT 300"
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

    @Upsert
    suspend fun upsert(note: DayNoteEntity)

    @Query("DELETE FROM day_notes WHERE epochDay = :epochDay")
    suspend fun deleteForDay(epochDay: Long)
}
