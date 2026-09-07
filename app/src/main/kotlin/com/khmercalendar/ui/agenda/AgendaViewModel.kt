package com.khmercalendar.ui.agenda

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.khmercalendar.data.db.CategoryEntity
import com.khmercalendar.data.repo.EventRepository
import com.khmercalendar.domain.EventOccurrence
import com.khmercalendar.domain.EventTimes
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

/** One day's worth of the agenda list. */
data class AgendaDay(
    val date: LocalDate,
    val events: List<EventOccurrence>,
)

data class AgendaFilter(
    val categoryId: Long? = null,
    val showCompleted: Boolean = false,
    val tasksOnly: Boolean = false,
)

data class SearchHit(
    val eventId: Long,
    val title: String,
    val subtitle: String,
    val date: LocalDate,
    val colorArgb: Int,
)

/**
 * Backs the agenda list and the search screen.
 *
 * The agenda is a rolling window rather than the whole table: repeating series are expanded
 * on demand, so an unbounded agenda would mean expanding every series to the end of time.
 * [extendRange] grows the window as the user scrolls.
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class AgendaViewModel(
    private val repository: EventRepository,
) : ViewModel() {

    private val rangeEnd = MutableStateFlow(LocalDate.now().plusDays(INITIAL_DAYS))
    private val rangeStart = MutableStateFlow(LocalDate.now())

    private val _filter = MutableStateFlow(AgendaFilter())
    val filter: StateFlow<AgendaFilter> = _filter.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    val categories: StateFlow<List<CategoryEntity>> = repository.observeCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val days: StateFlow<List<AgendaDay>> =
        combine(rangeStart, rangeEnd, _filter) { start, end, filter -> Triple(start, end, filter) }
            .flatMapLatest { (start, end, filter) ->
                repository.observeOccurrences(start, end).map { byDate ->
                    byDate.entries
                        .asSequence()
                        .sortedBy { it.key }
                        .map { entry ->
                            AgendaDay(
                                date = entry.key,
                                events = entry.value
                                    .filter { filter.categoryId == null || it.categoryId == filter.categoryId }
                                    .filter { !filter.tasksOnly || it.isTask }
                                    .filter { filter.showCompleted || !it.isCompleted }
                                    .sortedWith(compareBy({ !it.allDay }, { it.start })),
                            )
                        }
                        .filter { it.events.isNotEmpty() }
                        .toList()
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val searchResults: StateFlow<List<SearchHit>> = _query
        .debounce(SEARCH_DEBOUNCE_MS)
        .flatMapLatest { q ->
            if (q.isBlank()) flowOf(emptyList()) else repository.search(q).map { rows ->
                val zone = ZoneId.systemDefault()
                rows.map { row ->
                    val start = EventTimes.fromUtcMillis(row.startUtcMillis, zone, row.allDay)
                    SearchHit(
                        eventId = row.id,
                        title = row.title,
                        subtitle = listOfNotNull(
                            row.location?.takeIf { it.isNotBlank() },
                            row.description?.takeIf { it.isNotBlank() },
                        ).joinToString(" · ").take(80),
                        date = start.toLocalDate(),
                        colorArgb = row.colorArgb ?: 0,
                    )
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setQuery(value: String) {
        _query.value = value
    }

    fun setCategory(id: Long?) = _filter.update { it.copy(categoryId = id) }

    fun setShowCompleted(show: Boolean) = _filter.update { it.copy(showCompleted = show) }

    fun setTasksOnly(only: Boolean) = _filter.update { it.copy(tasksOnly = only) }

    /** Grows the window when the user reaches the end of the list. */
    fun extendRange() {
        if (rangeEnd.value.isAfter(LocalDate.now().plusDays(MAX_DAYS))) return
        rangeEnd.value = rangeEnd.value.plusDays(INITIAL_DAYS)
    }

    /** Shows the recent past too, for looking something up after the fact. */
    fun includePast(include: Boolean) {
        rangeStart.value = if (include) LocalDate.now().minusDays(PAST_DAYS) else LocalDate.now()
    }

    fun setCompleted(eventId: Long, completed: Boolean) {
        viewModelScope.launch { repository.setCompleted(eventId, completed) }
    }

    private fun MutableStateFlow<AgendaFilter>.update(block: (AgendaFilter) -> AgendaFilter) {
        value = block(value)
    }

    private companion object {
        const val INITIAL_DAYS = 90L
        const val MAX_DAYS = 730L
        const val PAST_DAYS = 90L
        const val SEARCH_DEBOUNCE_MS = 180L
    }
}
