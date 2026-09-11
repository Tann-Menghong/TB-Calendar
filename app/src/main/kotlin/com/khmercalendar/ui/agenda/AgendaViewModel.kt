package com.khmercalendar.ui.agenda

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.khmercalendar.data.db.CategoryEntity
import com.khmercalendar.data.prefs.AppSettings
import com.khmercalendar.domain.SearchFilter
import com.khmercalendar.domain.SearchResults
import com.khmercalendar.domain.SearchResult
import com.khmercalendar.domain.CalendarSearch
import com.khmercalendar.data.db.EventEntity
import com.khmercalendar.data.db.DayNoteEntity
import com.khmercalendar.core.search.DateQuery
import com.khmercalendar.core.holiday.KhmerHolidays
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
    private val settings: StateFlow<AppSettings>,
) : ViewModel() {

    private val rangeEnd = MutableStateFlow(LocalDate.now().plusDays(INITIAL_DAYS))
    private val rangeStart = MutableStateFlow(LocalDate.now())

    private val _filter = MutableStateFlow(AgendaFilter())
    val filter: StateFlow<AgendaFilter> = _filter.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    /**
     * What search results are narrowed to.
     *
     * Held here rather than on the screen so it survives rotation, and applied on the screen
     * rather than here so the unfiltered count is still known - a screen that says "nothing
     * found" while a filter is hiding twelve results is telling the user the wrong thing.
     */
    private val _searchFilter = MutableStateFlow(SearchFilter())
    val searchFilter: StateFlow<SearchFilter> = _searchFilter.asStateFlow()

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

    /**
     * Everything matching the query, in sections.
     *
     * ## What changed
     *
     * This searched the event table and nothing else, so a word written in a note, the name
     * of a holiday, a category, or a date typed as a date all returned "រកមិនឃើញ" - which
     * reads as *you do not have that*, not as *search does not look there*. The spec asked
     * for events, tasks, notes, holidays, dates, categories and locations from the start.
     *
     * ## How the pieces are combined
     *
     * Events and notes come from the database as flows and re-run themselves when the data
     * changes. Dates and holidays are computed: a holiday is not a row anywhere, it is a
     * function of the year, so it is searched by generating the window and filtering it. That
     * keeps holidays searchable offline with no table to migrate, which is the same reason
     * they are not stored in the first place.
     *
     * The whole thing is debounced once, at the query, rather than per source - four sources
     * each debouncing separately would produce four staggered redraws per keystroke.
     */
    val searchResults: StateFlow<SearchResults> = _query
        .debounce(SEARCH_DEBOUNCE_MS)
        .flatMapLatest { raw ->
            val q = raw.trim()
            if (!CalendarSearch.isSearchable(q)) {
                flowOf(SearchResults())
            } else {
                combine(
                    repository.search(q),
                    repository.searchNotes(q),
                    repository.observeCategories(),
                ) { events, notes, categories ->
                    build(q, events, notes, categories)
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchResults())

    private fun build(
        query: String,
        events: List<EventEntity>,
        notes: List<DayNoteEntity>,
        categories: List<CategoryEntity>,
    ): SearchResults {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now()
        val categoryById = categories.associateBy { it.id }

        // Events whose category name matched are already here: the query joins categories,
        // so "search by category" costs no second lookup and nothing to de-duplicate.
        val eventHits = events
            .take(CalendarSearch.LIMIT_PER_SECTION)
            .map { row ->
                val start = EventTimes.fromUtcMillis(row.startUtcMillis, zone, row.allDay)
                SearchResult.Event(
                    eventId = row.id,
                    title = row.title,
                    subtitle = listOfNotNull(
                        row.location?.takeIf { it.isNotBlank() },
                        categoryById[row.categoryId]?.name,
                        row.description?.takeIf { it.isNotBlank() },
                    ).joinToString(" · ").take(80),
                    date = start.toLocalDate(),
                    colorArgb = row.colorArgb ?: categoryById[row.categoryId]?.colorArgb ?: 0,
                    isTask = row.isTask,
                    isCompleted = row.isCompleted,
                    categoryId = row.categoryId,
                    priority = row.priority,
                    isPinned = row.isPinned,
                )
            }

        val dateFormat = settings.value.dateFormat
        val dateMatches = DateQuery.parse(query, today, dateFormat.searchOrder)
            .map { SearchResult.DateJump(it.date, it.yearAssumed) }

        val holidayHits = KhmerHolidays
            .inRange(today.minusYears(1).withDayOfYear(1), today.plusYears(2).withDayOfYear(1))
            .filter { CalendarSearch.matches(it.nameKm, query) || CalendarSearch.matches(it.nameEn, query) }
            // Nearest first, and past ones after the ones still to come: a holiday search is
            // almost always about the next one.
            .sortedWith(compareBy({ it.date.isBefore(today) }, { it.date }))
            .take(CalendarSearch.LIMIT_PER_SECTION)
            .map { SearchResult.Holiday(it.date, it.nameKm) }

        return SearchResults(
            dates = dateMatches,
            events = eventHits,
            notes = notes.take(CalendarSearch.LIMIT_PER_SECTION).map { note ->
                SearchResult.Note(
                    date = LocalDate.ofEpochDay(note.epochDay),
                    snippet = CalendarSearch.snippet(note.text, query),
                )
            },
            holidays = holidayHits,
        )
    }

    fun setQuery(value: String) {
        _query.value = value
    }

    fun setSearchFilter(filter: SearchFilter) {
        _searchFilter.value = filter
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
