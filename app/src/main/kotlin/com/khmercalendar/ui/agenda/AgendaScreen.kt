package com.khmercalendar.ui.agenda

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.automirrored.outlined.StickyNote2
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Celebration
import androidx.compose.ui.text.style.TextDecoration
import com.khmercalendar.data.prefs.AppSettings
import com.khmercalendar.domain.CalendarSearch
import com.khmercalendar.domain.SearchResult
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.khmercalendar.core.khmer.KhmerTerms
import com.khmercalendar.ui.calendar.EventRow
import com.khmercalendar.ui.components.ColorDot
import com.khmercalendar.ui.components.EmptyState
import com.khmercalendar.ui.components.CalendarFormats
import com.khmercalendar.ui.components.localeNumber
import com.khmercalendar.ui.theme.LocalAppSettings
import java.time.LocalDate

/**
 * The agenda: every upcoming event as one flat, scrollable list grouped by day.
 *
 * This is the view that answers "what have I got on", so days with nothing on them are
 * omitted entirely rather than shown as empty rows.
 *
 * It carries no bar and no button of its own. It is one of the calendar's four views now, and
 * [com.khmercalendar.ui.calendar.CalendarScreen] owns the header, the search action and the
 * add button for all four - two title bars stacked on one screen is what you get otherwise.
 */
@Composable
fun AgendaScreen(
    viewModel: AgendaViewModel,
    onOpenEvent: (Long, LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val days by viewModel.days.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    // Grow the window when the user is within a few rows of the end, so the list feels
    // endless without ever expanding two years of repeats up front.
    val nearEnd by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= listState.layoutInfo.totalItemsCount - 3
        }
    }
    LaunchedEffect(nearEnd) { if (nearEnd) viewModel.extendRange() }

    Column(modifier.fillMaxSize()) {
        FilterBar(
            categories = categories.map { it.id to it.name },
            selectedCategory = filter.categoryId,
            tasksOnly = filter.tasksOnly,
            showCompleted = filter.showCompleted,
            onCategory = viewModel::setCategory,
            onTasksOnly = viewModel::setTasksOnly,
            onShowCompleted = viewModel::setShowCompleted,
        )

        if (days.isEmpty()) {
            EmptyState(
                icon = Icons.Outlined.EventBusy,
                title = "គ្មានព្រឹត្តិការណ៍",
                message = "ចុចប៊ូតុង + ដើម្បីបង្កើតព្រឹត្តិការណ៍ដំបូង",
            )
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            ) {
                days.forEach { day ->
                    item(key = "h-${day.date}") { DayHeader(day.date) }
                    items(day.events.size) { index ->
                        val event = day.events[index]
                        EventRow(
                            event = event,
                            modifier = Modifier.padding(start = 8.dp, bottom = 4.dp),
                            onClick = { onOpenEvent(event.eventId, event.occurrenceDate) },
                        )
                    }
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }
}

@Composable
private fun DayHeader(date: LocalDate) {
    val isToday = date == LocalDate.now()
    Column(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "${localeNumber(date.dayOfMonth)} ${KhmerTerms.solarMonth(date.monthValue)}",
                style = MaterialTheme.typography.titleSmall,
                color = if (isToday) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = KhmerTerms.dayOfWeek(date.dayOfWeek),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        HorizontalDivider(Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun FilterBar(
    categories: List<Pair<Long, String>>,
    selectedCategory: Long?,
    tasksOnly: Boolean,
    showCompleted: Boolean,
    onCategory: (Long?) -> Unit,
    onTasksOnly: (Boolean) -> Unit,
    onShowCompleted: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        FilterChip(
            selected = selectedCategory == null && !tasksOnly,
            onClick = { onCategory(null); onTasksOnly(false) },
            label = { Text("ទាំងអស់") },
        )
        FilterChip(
            selected = tasksOnly,
            onClick = { onTasksOnly(!tasksOnly) },
            label = { Text("កិច្ចការ") },
        )
        FilterChip(
            selected = showCompleted,
            onClick = { onShowCompleted(!showCompleted) },
            label = { Text("រួចរាល់") },
        )
        categories.forEach { (id, name) ->
            FilterChip(
                selected = selectedCategory == id,
                onClick = { onCategory(if (selectedCategory == id) null else id) },
                label = { Text(name) },
            )
        }
    }
}

/**
 * Search across everything the calendar holds.
 *
 * ## What it used to look at
 *
 * The event table, and nothing else. A word you had written in a note, the name of a public
 * holiday, a category, or a date typed as a date all returned "រកមិនឃើញ" — which reads as
 * *you do not have that*, not as *search does not look there*. That is the worse of the two
 * failures, because it is indistinguishable from the data being gone.
 *
 * ## Sections rather than one ranked list
 *
 * There is no honest way to rank "the note on the 3rd" against "a holiday called ចូលឆ្នាំ",
 * so the results are grouped and each group is dropped when empty. A single mixed list would
 * need a relevance score nobody could explain and everybody would argue with.
 *
 * Dates lead, because typing a date is the one query where the user already knows exactly
 * where they want to go; making them scroll past text matches to reach it would be answering
 * a different question first.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    viewModel: AgendaViewModel,
    onBack: () -> Unit,
    onOpenEvent: (Long, LocalDate) -> Unit,
    onOpenDay: (LocalDate) -> Unit,
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val allResults by viewModel.searchResults.collectAsStateWithLifecycle()
    val filter by viewModel.searchFilter.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    // Filtered here, where the unfiltered count is still at hand for the "hidden by filters" line.
    val results = remember(allResults, filter) { filter.apply(allResults) }
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val settings = LocalAppSettings.current

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "ត្រឡប់")
                    }
                },
                title = {
                    TextField(
                        value = query,
                        onValueChange = viewModel::setQuery,
                        modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                        placeholder = { Text("ស្វែងរក...") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                        ),
                    )
                },
            )
        },
    ) { padding ->
        androidx.compose.foundation.layout.Column(Modifier.fillMaxSize().padding(padding)) {
            if (CalendarSearch.isSearchable(query)) {
                SearchFilterBar(
                    filter = filter,
                    categories = categories,
                    onChange = viewModel::setSearchFilter,
                )
            }
            when {
                !CalendarSearch.isSearchable(query) -> EmptyState(
                    icon = Icons.Outlined.Search,
                    title = "ស្វែងរក",
                    message = "ចំណងជើង ទីតាំង ប្រភេទ កំណត់ចំណាំ បុណ្យជាតិ ឬកាលបរិច្ឆេទ",
                )

                allResults.isEmpty -> EmptyState(
                    icon = Icons.Outlined.EventBusy,
                    title = "រកមិនឃើញ",
                    message = "គ្មានអ្វីត្រូវនឹង “$query”",
                )

                // Something matched the words and the filters hid all of it. Saying "nothing
                // found" here would send the user to retype a search that already worked.
                results.isEmpty -> EmptyState(
                    icon = Icons.Outlined.Search,
                    title = "តម្រងលាក់លទ្ធផលទាំងអស់",
                    message = "${com.khmercalendar.ui.components.localeNumber(allResults.total)} " +
                        "ត្រូវនឹង “$query” ប៉ុន្តែមិនត្រូវនឹងតម្រងដែលបានជ្រើស",
                    action = {
                        androidx.compose.material3.TextButton(
                            onClick = { viewModel.setSearchFilter(com.khmercalendar.domain.SearchFilter()) },
                        ) { Text("សម្អាតតម្រង") }
                    },
                )

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 24.dp),
                ) {
                    section("កាលបរិច្ឆេទ", results.dates) { hit ->
                        DateJumpRow(hit, settings) { onOpenDay(hit.date) }
                    }
                    section("ព្រឹត្តិការណ៍", results.events) { hit ->
                        EventHitRow(hit, settings) { onOpenEvent(hit.eventId, hit.date) }
                    }
                    section("កំណត់ចំណាំ", results.notes) { hit ->
                        NoteHitRow(hit, settings) { onOpenDay(hit.date) }
                    }
                    section("បុណ្យជាតិ", results.holidays) { hit ->
                        HolidayHitRow(hit, settings) { onOpenDay(hit.date) }
                    }
                }
            }
        }
    }
}

/**
 * A titled block of results, or nothing at all.
 *
 * An empty section is not drawn — a heading over nothing is a promise the screen cannot keep —
 * which is also what lets all four be declared unconditionally at the call site.
 */
private fun <T> LazyListScope.section(
    title: String,
    items: List<T>,
    row: @Composable (T) -> Unit,
) {
    if (items.isEmpty()) return
    item(key = "header-$title") {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
        )
    }
    items(items.size, key = { "$title-$it" }) { index ->
        row(items[index])
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

/** The shape every hit shares: an icon or dot, a title, and a quiet second line. */
@Composable
private fun HitRow(
    onClick: () -> Unit,
    leading: @Composable () -> Unit,
    title: String,
    subtitle: String,
    strikethrough: Boolean = false,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading()
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textDecoration = if (strikethrough) TextDecoration.LineThrough else null,
            )
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * "Go to this day."
 *
 * Names the weekday, because the reason for jumping to a date is usually to find out what day
 * it falls on. When the query carried no year it says the year was assumed, rather than
 * leaving the user to notice they have been sent to next January.
 */
@Composable
private fun DateJumpRow(hit: SearchResult.DateJump, settings: AppSettings, onClick: () -> Unit) {
    HitRow(
        onClick = onClick,
        leading = {
            Icon(
                imageVector = Icons.Outlined.CalendarMonth,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        title = "ថ្ងៃ" + KhmerTerms.dayOfWeek(hit.date.dayOfWeek) + " " +
            CalendarFormats.writtenDate(hit.date, settings.dateFormat, settings.useKhmerNumerals),
        subtitle = if (hit.yearAssumed) "ឆ្នាំបន្ទាប់ដែលមានថ្ងៃនេះ" else "",
    )
}

@Composable
private fun EventHitRow(hit: SearchResult.Event, settings: AppSettings, onClick: () -> Unit) {
    HitRow(
        onClick = onClick,
        leading = { ColorDot(Color(hit.colorArgb.takeIf { it != 0 } ?: 0xFF2F6FED.toInt())) },
        title = hit.title,
        subtitle = listOfNotNull(
            CalendarFormats.date(hit.date, settings.dateFormat, settings.useKhmerNumerals),
            "កិច្ចការ".takeIf { hit.isTask },
            hit.subtitle.takeIf { it.isNotBlank() },
        ).joinToString(" · "),
        strikethrough = hit.isCompleted,
    )
}

/** A note has no identity apart from its day, so the snippet is the title and the day is under it. */
@Composable
private fun NoteHitRow(hit: SearchResult.Note, settings: AppSettings, onClick: () -> Unit) {
    HitRow(
        onClick = onClick,
        leading = {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.StickyNote2,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        title = hit.snippet,
        subtitle = CalendarFormats.date(hit.date, settings.dateFormat, settings.useKhmerNumerals),
    )
}

@Composable
private fun HolidayHitRow(hit: SearchResult.Holiday, settings: AppSettings, onClick: () -> Unit) {
    HitRow(
        onClick = onClick,
        leading = {
            Icon(
                imageVector = Icons.Outlined.Celebration,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary,
            )
        },
        title = hit.name,
        subtitle = CalendarFormats.date(hit.date, settings.dateFormat, settings.useKhmerNumerals),
    )
}
