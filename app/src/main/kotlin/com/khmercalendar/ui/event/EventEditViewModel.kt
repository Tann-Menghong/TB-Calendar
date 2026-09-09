package com.khmercalendar.ui.event

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.khmercalendar.core.recurrence.Frequency
import com.khmercalendar.core.recurrence.RecurrenceRule
import com.khmercalendar.data.db.CategoryEntity
import com.khmercalendar.data.prefs.AppSettings
import com.khmercalendar.data.repo.EventRepository
import com.khmercalendar.domain.EventDraftDefaults
import com.khmercalendar.domain.EventDraftModel
import com.khmercalendar.domain.ChecklistItem
import com.khmercalendar.domain.Checklist
import com.khmercalendar.domain.TaskPriority
import com.khmercalendar.domain.EventTimes
import com.khmercalendar.notify.ReminderScheduler
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

data class EventEditState(
    val draft: EventDraftModel = EventDraftModel(),
    val categories: List<CategoryEntity> = emptyList(),
    val isNew: Boolean = true,
    val isSaving: Boolean = false,
    val saved: Boolean = false,
    val error: String? = null,
    /**
     * Whether this date is pinned as a countdown.
     *
     * Kept beside the draft rather than on it: the editor has no pin control, and a value on
     * the draft would be saved back by the editor and could unpin what you were editing.
     */
    val isPinned: Boolean = false,
    /** The steps inside this task, in their own order. Empty for an ordinary event. */
    val checklist: List<ChecklistItem> = emptyList(),
)

/** Backs the add and edit form, and the delete paths on the detail screen. */
class EventEditViewModel(
    private val repository: EventRepository,
    private val scheduler: ReminderScheduler,
    private val settings: StateFlow<AppSettings>,
) : ViewModel() {

    private var checklistJob: Job? = null

    private val _state = MutableStateFlow(EventEditState())
    val state: StateFlow<EventEditState> = _state.asStateFlow()

    /**
     * @param asTask seeds a brand-new draft as a task, so "add a task on this date" is one
     *   step rather than "add an event, then find the switch". Ignored when opening an
     *   existing event, whose own stored value is the answer.
     */
    fun load(eventId: Long, initialDate: LocalDate?, asTask: Boolean = false) {
        viewModelScope.launch {
            val categories = repository.categories()
            if (eventId <= 0L) {
                val prefs = settings.value
                // A new event starts at the next whole hour. Computed with the date attached,
                // so that late in the evening it rolls into tomorrow instead of wrapping back
                // to this morning - see [EventDraftDefaults].
                val times = EventDraftDefaults.times(
                    now = LocalDateTime.now(),
                    durationMinutes = prefs.defaultEventDurationMinutes,
                    preferredDate = initialDate,
                )
                _state.value = EventEditState(
                    draft = EventDraftModel(
                        date = times.date,
                        endDate = times.endDate,
                        startTime = times.startTime,
                        endTime = times.endTime,
                        categoryId = categories.firstOrNull()?.id,
                        reminderMinutes = listOf(prefs.defaultReminderMinutes),
                        isTask = asTask,
                        // A task is a day, not an appointment: the task list creates all-day
                        // tasks, and one created from a date should match rather than arrive
                        // with an hour somebody has to clear.
                        allDay = asTask,
                    ),
                    categories = categories,
                    isNew = true,
                )
                return@launch
            }

            val entity = repository.event(eventId)
            if (entity == null) {
                _state.update { it.copy(error = "រកមិនឃើញព្រឹត្តិការណ៍", categories = categories) }
                return@launch
            }
            val zone = ZoneId.systemDefault()
            val start = EventTimes.fromUtcMillis(entity.startUtcMillis, zone, entity.allDay)
            val end = EventTimes.fromUtcMillis(entity.endUtcMillis, zone, entity.allDay)
            _state.value = EventEditState(
                draft = EventDraftModel(
                    id = entity.id,
                    title = entity.title,
                    description = entity.description.orEmpty(),
                    location = entity.location.orEmpty(),
                    date = start.toLocalDate(),
                    startTime = start.toLocalTime(),
                    // An all-day event is stored ending at midnight of the following day, so
                    // the last day the user actually chose is one back from that.
                    endDate = if (entity.allDay) end.toLocalDate().minusDays(1) else end.toLocalDate(),
                    endTime = end.toLocalTime(),
                    allDay = entity.allDay,
                    categoryId = entity.categoryId,
                    colorArgb = entity.colorArgb,
                    recurrence = RecurrenceRule.parse(entity.rrule),
                    reminderMinutes = repository.reminders(entity.id),
                    isTask = entity.isTask,
                    isCompleted = entity.isCompleted,
                    priority = TaskPriority.of(entity.priority),
                ),
                categories = categories,
                isNew = false,
                isPinned = entity.isPinned,
            )
        }
    }

    fun prefill(draft: EventDraftModel) {
        viewModelScope.launch {
            val categories = repository.categories()
            _state.value = EventEditState(
                draft = draft.copy(categoryId = draft.categoryId ?: categories.firstOrNull()?.id),
                categories = categories,
                isNew = draft.id <= 0L,
            )
        }
    }

    fun update(block: (EventDraftModel) -> EventDraftModel) {
        _state.update { it.copy(draft = block(it.draft), error = null) }
    }

    fun setAllDay(allDay: Boolean) = update { it.copy(allDay = allDay) }

    fun setRepeat(frequency: Frequency?) = update { draft ->
        draft.copy(recurrence = frequency?.let { RecurrenceRule(it) })
    }

    fun toggleReminder(minutes: Int) = update { draft ->
        val current = draft.reminderMinutes
        draft.copy(
            reminderMinutes = if (minutes in current) current - minutes else (current + minutes).sorted(),
        )
    }

    fun save() {
        val draft = _state.value.draft
        if (draft.title.isBlank() && draft.description.isBlank()) {
            _state.update { it.copy(error = "សូមបញ្ចូលចំណងជើងព្រឹត្តិការណ៍") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true) }
            runCatching {
                repository.save(draft)
                scheduler.rescheduleAll()
            }.onSuccess {
                _state.update { it.copy(isSaving = false, saved = true) }
            }.onFailure { e ->
                _state.update { it.copy(isSaving = false, error = e.message ?: "រក្សាទុកមិនបានជោគជ័យ") }
            }
        }
    }

    fun delete(eventId: Long, onDone: () -> Unit) {
        viewModelScope.launch {
            repository.deleteEvent(eventId)
            scheduler.rescheduleAll()
            onDone()
        }
    }

    fun deleteOccurrence(eventId: Long, date: LocalDate, onDone: () -> Unit) {
        viewModelScope.launch {
            repository.deleteOccurrence(eventId, date)
            scheduler.rescheduleAll()
            onDone()
        }
    }

    fun duplicate(eventId: Long, onDone: (Long?) -> Unit) {
        viewModelScope.launch {
            val newId = repository.duplicate(eventId)
            scheduler.rescheduleAll()
            onDone(newId)
        }
    }

    fun setCompleted(eventId: Long, completed: Boolean) {
        viewModelScope.launch { repository.setCompleted(eventId, completed) }
    }

    /**
     * Watches this event's checklist.
     *
     * A second collection rather than part of [load]: the list has to update as lines are
     * ticked and added, and re-running the whole load for a tick would reset every field the
     * user is part-way through editing.
     */
    fun watchChecklist(eventId: Long) {
        if (eventId <= 0L) return
        checklistJob?.cancel()
        checklistJob = viewModelScope.launch {
            repository.observeChecklist(eventId).collect { items ->
                _state.update { it.copy(checklist = Checklist.ordered(items)) }
            }
        }
    }

    fun addChecklistItem(eventId: Long, text: String) {
        viewModelScope.launch { repository.addChecklistItem(eventId, text) }
    }

    fun setChecklistItemDone(itemId: Long, done: Boolean) {
        viewModelScope.launch { repository.setChecklistItemDone(itemId, done) }
    }

    fun deleteChecklistItem(itemId: Long) {
        viewModelScope.launch { repository.deleteChecklistItem(itemId) }
    }

    fun moveChecklistItem(eventId: Long, from: Int, to: Int) {
        viewModelScope.launch { repository.moveChecklistItem(eventId, from, to) }
    }

    /**
     * Pins or unpins this date as a countdown.
     *
     * Optimistic: the flag is a single column and the write cannot fail in a way the user
     * could act on, and a pin that takes a database round trip to appear reads as a button
     * that did not work.
     */
    fun setPinned(eventId: Long, pinned: Boolean) {
        if (eventId <= 0L) return
        _state.value = _state.value.copy(isPinned = pinned)
        viewModelScope.launch { repository.setPinned(eventId, pinned) }
    }
}
