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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import com.khmercalendar.domain.EventTemplate
import com.khmercalendar.domain.EventTemplates
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
    /**
     * Whether the occurrence on screen is done.
     *
     * Separate from the draft's flag, which belongs to the series: for a repeating task the
     * detail screen shows one date, and that date is what its button ticks.
     */
    val occurrenceCompleted: Boolean = false,
)

/** Backs the add and edit form, and the delete paths on the detail screen. */
class EventEditViewModel(
    private val repository: EventRepository,
    private val scheduler: ReminderScheduler,
    private val settings: StateFlow<AppSettings>,
    private val templates: com.khmercalendar.data.repo.TemplateRepository,
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
                occurrenceCompleted = repository.isCompleted(entity.id, initialDate ?: start.toLocalDate()),
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

    /**
     * Saved templates.
     *
     * A flow of its own rather than a field on [EventEditState]: [load] replaces the whole state,
     * and a list kept there would be wiped every time an event opened.
     */
    val templateList: StateFlow<List<EventTemplate>> = templates.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Fills the form from [template], keeping the day already chosen. */
    fun applyTemplate(template: EventTemplate) = update { draft ->
        EventTemplates.toDraft(template, draft.date, draft)
    }

    /**
     * Saves the form as a template. The event itself is not saved: a template is a shape, not a
     * commitment, and the user may still be deciding.
     */
    fun saveAsTemplate(name: String, onSaved: () -> Unit) {
        val draft = _state.value.draft
        if (draft.title.isBlank()) {
            _state.update { it.copy(error = "សូមបញ្ចូលចំណងជើងមុនរក្សាទុកជាគំរូ") }
            onSaved()
            return
        }
        viewModelScope.launch {
            templates.save(EventTemplates.from(draft, name))
            onSaved()
        }
    }

    fun deleteTemplate(id: Long) {
        viewModelScope.launch { templates.delete(id) }
    }

    /**
     * Moves the occurrence on [from] to [to].
     *
     * @param onMoved called with the event and date that now hold it, so the screen can reopen
     *   there - for a day of a repeating series that is a different event.
     */
    fun reschedule(eventId: Long, from: LocalDate, to: LocalDate, onMoved: (Long, LocalDate) -> Unit) {
        if (from == to) return
        viewModelScope.launch {
            val id = repository.reschedule(eventId, from, to) ?: return@launch
            scheduler.rescheduleAll()
            onMoved(id, to)
        }
    }

    /** Event to task or back, reflected on the screen at once. */
    fun setIsTask(eventId: Long, date: LocalDate, isTask: Boolean) {
        viewModelScope.launch {
            repository.setIsTask(eventId, isTask)
            _state.update {
                it.copy(
                    draft = it.draft.copy(isTask = isTask),
                    occurrenceCompleted = repository.isCompleted(eventId, date),
                )
            }
            // A task that was done had its reminders skipped; as an event it is reminded again.
            scheduler.rescheduleAll()
        }
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

    /** Ticks the occurrence on [date]; for a repeating task, that one occurrence only. */
    fun setCompleted(eventId: Long, date: LocalDate, completed: Boolean) {
        viewModelScope.launch {
            repository.setCompleted(eventId, date, completed)
            _state.update { it.copy(occurrenceCompleted = repository.isCompleted(eventId, date)) }
        }
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
