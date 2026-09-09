package com.khmercalendar.data.repo

import com.khmercalendar.data.db.HabitDao
import com.khmercalendar.data.db.HabitEntity
import com.khmercalendar.data.db.HabitEntryEntity
import com.khmercalendar.domain.Habit
import com.khmercalendar.domain.HabitSchedule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.LocalDate

/** A habit plus the days it has been kept, which is everything a row needs to draw itself. */
data class HabitWithHistory(
    val habit: Habit,
    val done: Set<LocalDate>,
)

/**
 * The door to habit data.
 *
 * Separate from [EventRepository] rather than bolted onto it: habits share no table, no
 * query and no expansion logic with the calendar, and the only thing the two would have in
 * common inside one class is the word "repository".
 */
class HabitRepository(
    private val habitDao: HabitDao,
    private val historyDays: Long = DEFAULT_HISTORY_DAYS,
) {

    /**
     * Every habit with its recent history.
     *
     * The history window is bounded because nothing on screen looks back further than a
     * year: the current streak stops at the first missed day, and the completion rate spans
     * thirty. Reading a decade of ticks to draw one screen would be paid for on every redraw.
     */
    fun observeAll(today: LocalDate): Flow<List<HabitWithHistory>> {
        val from = today.minusDays(historyDays).toEpochDay()
        return combine(
            habitDao.observeAll(),
            habitDao.observeEntriesSince(from),
        ) { habits, entries ->
            val byHabit = entries.groupBy { it.habitId }
            habits.map { entity ->
                HabitWithHistory(
                    habit = entity.toHabit(),
                    done = byHabit[entity.id]
                        ?.mapTo(HashSet()) { LocalDate.ofEpochDay(it.epochDay) }
                        .orEmpty(),
                )
            }
        }
    }

    suspend fun save(habit: Habit): Long {
        val existing = if (habit.id == 0L) null else habitDao.byId(habit.id)
        return habitDao.upsert(
            HabitEntity(
                id = habit.id,
                name = habit.name.trim().ifBlank { "(គ្មានឈ្មោះ)" },
                colorArgb = habit.colorArgb,
                scheduleKind = habit.schedule.kind.stored,
                scheduleDays = HabitSchedule.encodeDays(habit.schedule.days),
                weeklyTarget = habit.schedule.target.coerceIn(1, 7),
                sortOrder = existing?.sortOrder ?: 0,
                archivedAtMillis = existing?.archivedAtMillis,
                createdAtMillis = existing?.createdAtMillis ?: System.currentTimeMillis(),
            ),
        )
    }

    /**
     * Ticks or unticks [habit] for [date].
     *
     * Idempotent in both directions, so a double tap cannot produce two ticks or leave the
     * row in a state the screen did not ask for.
     */
    suspend fun setDone(habitId: Long, date: LocalDate, done: Boolean) {
        if (done) {
            habitDao.insertEntry(
                HabitEntryEntity(
                    habitId = habitId,
                    epochDay = date.toEpochDay(),
                    completedAtMillis = System.currentTimeMillis(),
                ),
            )
        } else {
            habitDao.deleteEntry(habitId, date.toEpochDay())
        }
    }

    /**
     * Archives rather than deletes.
     *
     * A habit somebody kept for three months is a record of three months. Removing it from
     * the list should not destroy that, so "delete" from the UI archives, and only an
     * explicit delete removes the rows.
     */
    suspend fun setArchived(habitId: Long, archived: Boolean) {
        habitDao.setArchived(habitId, if (archived) System.currentTimeMillis() else null)
    }

    suspend fun delete(habitId: Long) = habitDao.deleteById(habitId)

    private fun HabitEntity.toHabit() = Habit(
        id = id,
        name = name,
        colorArgb = colorArgb,
        schedule = HabitSchedule(
            kind = HabitSchedule.kindOf(scheduleKind),
            days = HabitSchedule.decodeDays(scheduleDays),
            target = weeklyTarget,
        ),
        isArchived = archivedAtMillis != null,
    )

    private companion object {
        /** A year: further back than any figure on screen looks. */
        const val DEFAULT_HISTORY_DAYS = 400L
    }
}
