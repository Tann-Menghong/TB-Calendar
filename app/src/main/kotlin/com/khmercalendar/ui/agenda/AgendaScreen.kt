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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
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
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgendaScreen(
    viewModel: AgendaViewModel,
    onOpenEvent: (Long, LocalDate) -> Unit,
    onAdd: () -> Unit,
    onSearch: () -> Unit,
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("កាលវិភាគ") },
                actions = {
                    IconButton(onClick = onSearch) {
                        Icon(Icons.Outlined.Search, contentDescription = "ស្វែងរក")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAdd) {
                Icon(Icons.Outlined.Add, contentDescription = "បន្ថែម")
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
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

/** Full-text style search over titles, descriptions and locations. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    viewModel: AgendaViewModel,
    onBack: () -> Unit,
    onOpenEvent: (Long, LocalDate) -> Unit,
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val results by viewModel.searchResults.collectAsStateWithLifecycle()
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
                        placeholder = { Text("ស្វែងរកព្រឹត្តិការណ៍...") },
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
        when {
            query.isBlank() -> EmptyState(
                icon = Icons.Outlined.Search,
                title = "ស្វែងរក",
                message = "វាយចំណងជើង ទីតាំង ឬការពិពណ៌នា",
                modifier = Modifier.padding(padding),
            )

            results.isEmpty() -> EmptyState(
                icon = Icons.Outlined.EventBusy,
                title = "រកមិនឃើញ",
                message = "គ្មានព្រឹត្តិការណ៍ត្រូវនឹង “$query”",
                modifier = Modifier.padding(padding),
            )

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(12.dp),
            ) {
                items(results.size) { index ->
                    val hit = results[index]
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onOpenEvent(hit.eventId, hit.date) }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ColorDot(Color(hit.colorArgb.takeIf { it != 0 } ?: 0xFF2F6FED.toInt()))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                hit.title,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                listOfNotNull(
                                    CalendarFormats.date(
                                        hit.date,
                                        settings.dateFormat,
                                        settings.useKhmerNumerals,
                                    ),
                                    hit.subtitle.takeIf { it.isNotBlank() },
                                ).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}
