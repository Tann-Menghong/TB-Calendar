package com.khmercalendar.ui.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.khmercalendar.data.prefs.AppSettings
import com.khmercalendar.data.repo.EventRepository
import com.khmercalendar.domain.EventDraftModel
import com.khmercalendar.domain.Checklist
import com.khmercalendar.domain.TaskBoard
import com.khmercalendar.domain.TaskGroup
import com.khmercalendar.domain.TaskItem
import com.khmercalendar.domain.TaskPriority
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

/** What the to-do screen draws. */
data class TaskListState(
    val today: LocalDate = LocalDate.now(),
    val groups: List<TaskGroup> = emptyList(),
    val progress: TaskBoard.Progress = TaskBoard.Progress(0, 0, 0),
    val showCompleted: Boolean = false,
    /** Checklist progress per task id; a task with no steps is simply absent. */
    val checklists: Map<Long, Checklist.Progress> = emptyMap(),
    val isLoading: Boolean = true,
) {
    val isEmpty: Boolean get() = groups.all { it.tasks.isEmpty() }
}

/**
 * Backs the to-do list.
 *
 * ## Why this reads the calendar rather than a table of its own
 *
 * A task here *is* a calendar entry - one row in `events` with `isTask` set. That is the
 * whole point of the feature: a task with a date belongs on the day it is due, so it shows up
 * in the day timeline, in the week bars and in the month view without anything having to
 * synchronise two stores. The cost is that this screen expands recurring series like every
 * other calendar view; the benefit is that there is no second source of truth to drift.
 *
 * The window reaches back [PAST_DAYS] so that overdue work is visible. A to-do list that only
 * looks forward quietly loses the things you did not do, which are the ones that matter most.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TaskListViewModel(
    private val repository: EventRepository,
    private val settings: StateFlow<AppSettings>,
) : ViewModel() {

    private val today = MutableStateFlow(LocalDate.now())
    private val _showCompleted = MutableStateFlow(false)

    private val _lastAdded = MutableStateFlow<Long?>(null)

    /** The id of the task just added, so the list can flash it. Cleared once consumed. */
    val lastAdded: StateFlow<Long?> = _lastAdded.asStateFlow()

    /** The list itself, before checklist progress is attached. */
    private val baseState: StateFlow<TaskListState> = today
        .flatMapLatest { day ->
            combine(
                repository.observeOccurrences(day.minusDays(PAST_DAYS), day.plusDays(FUTURE_DAYS)),
                settings.map { it.weekStart }.distinctUntilChanged(),
                _showCompleted,
            ) { byDate, weekStart, showCompleted ->
                val tasks = byDate.values
                    .asSequence()
                    .flatten()
                    .filter { it.isTask }
                    // A repeating task produces one occurrence per period; the list would
                    // otherwise show the same chore fifty times. The nearest one still to do
                    // is the one you can act on.
                    .groupBy { it.eventId }
                    .values
                    .map { occurrences ->
                        occurrences.firstOrNull { !it.isCompleted && !it.occurrenceDate.isBefore(day) }
                            ?: occurrences.first()
                    }
                    .map { TaskItem(it, it.taskPriority) }
                    .toList()

                TaskListState(
                    today = day,
                    groups = TaskBoard.group(tasks, day, weekStart, includeDone = showCompleted),
                    progress = TaskBoard.progress(tasks, day),
                    showCompleted = showCompleted,
                    isLoading = false,
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TaskListState())

    /**
     * Checklist progress for whatever the list is currently showing.
     *
     * Derived from [baseState] rather than joined into the query that builds it, so ticking a
     * step does not re-run the occurrence expansion for a year of tasks. The ids are
     * distinct-until-changed, so it re-queries when the visible set changes and not when a
     * tick merely changes a figure inside it.
     */
    private val checklistProgress: Flow<Map<Long, Checklist.Progress>> = baseState
        .map { state -> state.groups.flatMap { it.tasks }.map { task -> task.occurrence.eventId } }
        .distinctUntilChanged()
        .flatMapLatest { ids -> repository.observeChecklistProgress(ids) }

    val state: StateFlow<TaskListState> =
        combine(baseState, checklistProgress) { base, progress ->
            base.copy(checklists = progress)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TaskListState())

    /** Called when the app returns to the foreground, so "overdue" does not mean yesterday. */
    fun refreshToday() {
        val now = LocalDate.now()
        if (today.value != now) today.value = now
    }

    fun setShowCompleted(show: Boolean) {
        _showCompleted.value = show
    }

    fun setCompleted(eventId: Long, completed: Boolean) {
        viewModelScope.launch { repository.setCompleted(eventId, completed) }
    }

    fun setPriority(eventId: Long, priority: TaskPriority) {
        viewModelScope.launch { repository.setPriority(eventId, priority) }
    }

    /**
     * Adds a task from the quick-add row.
     *
     * All-day rather than timed: a to-do is due on a day, and asking for a time before the
     * user has finished typing the title is the fastest way to make quick-add slower than the
     * full editor. The full editor is still there for anything that needs a clock.
     */
    fun add(title: String, date: LocalDate, priority: TaskPriority) {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            val id = repository.save(
                EventDraftModel(
                    title = trimmed,
                    date = date,
                    endDate = date,
                    allDay = true,
                    isTask = true,
                    priority = priority,
                    // No reminder by default. A to-do list that notifies for every line
                    // becomes a to-do list people turn notifications off for.
                    reminderMinutes = emptyList(),
                ),
            )
            _lastAdded.value = id
        }
    }

    fun consumeLastAdded() {
        _lastAdded.value = null
    }

    private companion object {
        /** How far back overdue work stays visible. */
        const val PAST_DAYS = 180L
        const val FUTURE_DAYS = 365L
    }
}
