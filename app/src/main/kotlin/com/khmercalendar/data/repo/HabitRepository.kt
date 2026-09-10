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

/** Every habit and its ticks over one window, shaped for [com.khmercalendar.domain.Statistics]. */
data class HabitTicks(
    val habits: List<Habit> = emptyList(),
    val ticks: Map<Long, Set<LocalDate>> = emptyMap(),
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

    /**
     * Every habit, with the ticks that fall inside [from]..[to].
     *
     * A window rather than "the last year", because statistics can be asked about 2024 and
     * [observeAll] would read everything since then only to throw most of it away. Archived
     * habits are included: they are part of what happened in the window, and dropping them
     * would quietly rewrite a month somebody actually kept.
     */
    fun observeTicksBetween(from: LocalDate, to: LocalDate): Flow<HabitTicks> = combine(
        habitDao.observeAll(),
        habitDao.observeEntriesBetween(from.toEpochDay(), to.toEpochDay()),
    ) { habits, entries ->
        val byHabit = entries.groupBy { it.habitId }
        HabitTicks(
            habits = habits.map { it.toHabit() },
            ticks = byHabit.mapValues { (_, rows) ->
                rows.mapTo(HashSet()) { LocalDate.ofEpochDay(it.epochDay) }
            },
        )
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
        createdAt = dateOf(createdAtMillis),
        archivedAt = archivedAtMillis?.let { dateOf(it) },
    )

    /**
     * A stored instant as a local day.
     *
     * The zone is read at call time rather than captured, so a habit created in Phnom Penh
     * and read in Paris is attributed to the day it is being read in - which is the same
     * convention every other date in the app follows.
     */
    private fun dateOf(millis: Long): LocalDate =
        java.time.Instant.ofEpochMilli(millis).atZone(java.time.ZoneId.systemDefault()).toLocalDate()

    private companion object {
        /** A year: further back than any figure on screen looks. */
        const val DEFAULT_HISTORY_DAYS = 400L
    }
}
