package com.khmercalendar.ui.habit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.khmercalendar.data.prefs.AppSettings
import com.khmercalendar.data.repo.HabitRepository
import com.khmercalendar.data.repo.HabitWithHistory
import com.khmercalendar.domain.Habit
import com.khmercalendar.domain.HabitBoard
import com.khmercalendar.domain.HabitSchedule
import com.khmercalendar.domain.HabitStreaks
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

/** One habit as the list draws it: the rule, the tick, and the numbers that follow from both. */
data class HabitRow(
    val habit: Habit,
    val isDoneToday: Boolean,
    val isDueToday: Boolean,
    val currentStreak: Int,
    val longestStreak: Int,
    val completionRate: Int,
    val weeklyDone: Int,
    /** The last seven days, oldest first, for the row's little trail of dots. */
    val recent: List<Boolean>,
)

data class HabitListState(
    val rows: List<HabitRow> = emptyList(),
    val today: LocalDate = LocalDate.now(),
    val doneToday: Int = 0,
    val dueToday: Int = 0,
    val isLoading: Boolean = true,
)

/**
 * The habit list.
 *
 * Every figure on a row is derived rather than stored - streaks, rates and the seven-day
 * trail are all functions of the ticks - so ticking a box recomputes them and nothing can
 * drift out of step with the underlying days. [HabitStreaks] does the arithmetic and is
 * tested on its own; this only assembles it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HabitViewModel(
    private val repository: HabitRepository,
    private val settings: StateFlow<AppSettings>,
) : ViewModel() {

    /**
     * Today, re-read when the screen resumes.
     *
     * Not `LocalDate.now()` inline: the flow is built once and would hold whatever day it was
     * when the screen first opened. A habit tracker left open overnight would then tick
     * yesterday, which is the one mistake it must never make.
     */
    private val today = MutableStateFlow(LocalDate.now())

    val state: StateFlow<HabitListState> = today
        .flatMapLatest { day ->
            combine(repository.observeAll(day), settings) { habits, prefs ->
                build(day, habits, prefs)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HabitListState())

    private fun build(
        day: LocalDate,
        habits: List<HabitWithHistory>,
        prefs: AppSettings,
    ): HabitListState {
        val doneIds = habits.filter { day in it.done }.map { it.habit.id }.toSet()
        val ordered = HabitBoard.order(habits.map { it.habit }, doneIds)
        val byId = habits.associateBy { it.habit.id }

        val rows = ordered.map { habit ->
            val history = byId.getValue(habit.id).done
            HabitRow(
                habit = habit,
                isDoneToday = day in history,
                isDueToday = habit.schedule.isDue(day),
                currentStreak = HabitStreaks.current(habit.schedule, history, day),
                longestStreak = HabitStreaks.longest(habit.schedule, history),
                completionRate = HabitStreaks.completionRate(habit.schedule, history, day),
                weeklyDone = HabitStreaks.weeklyProgress(history, day, prefs.weekStart),
                recent = (6 downTo 0).map { offset -> day.minusDays(offset.toLong()) in history },
            )
        }

        val due = HabitBoard.dueToday(ordered, day)
        return HabitListState(
            rows = rows,
            today = day,
            doneToday = due.count { it.id in doneIds },
            dueToday = due.size,
            isLoading = false,
        )
    }

    /** Called when the screen resumes, so a tracker left open overnight ticks the right day. */
    fun refreshToday() {
        today.value = LocalDate.now()
    }

    fun toggle(habitId: Long, done: Boolean) {
        viewModelScope.launch { repository.setDone(habitId, today.value, done) }
    }

    fun save(habit: Habit) {
        viewModelScope.launch { repository.save(habit) }
    }

    fun archive(habitId: Long) {
        viewModelScope.launch { repository.setArchived(habitId, archived = true) }
    }

    fun add(name: String, colorArgb: Int, schedule: HabitSchedule = HabitSchedule.DEFAULT) {
        if (name.isBlank()) return
        save(Habit(id = 0, name = name, colorArgb = colorArgb, schedule = schedule))
    }
}
