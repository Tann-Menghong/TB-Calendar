package com.khmercalendar.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Celebration
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.khmercalendar.core.holiday.Holiday
import com.khmercalendar.core.holiday.HolidayKind
import com.khmercalendar.core.khmer.KhmerTerms
import com.khmercalendar.domain.EventOccurrence
import com.khmercalendar.ui.Routes
import com.khmercalendar.ui.calendar.EventRow
import com.khmercalendar.ui.components.DashboardSkeleton
import com.khmercalendar.ui.components.EmptyState
import com.khmercalendar.ui.components.PrimaryFab
import com.khmercalendar.ui.components.localeNumber
import com.khmercalendar.ui.components.ProgressBar
import com.khmercalendar.ui.components.SectionTitle
import com.khmercalendar.ui.components.StatusBadge
import com.khmercalendar.ui.components.SurfaceCard
import com.khmercalendar.ui.theme.CardAccent
import com.khmercalendar.ui.theme.color
import com.khmercalendar.ui.theme.LocalAppSettings
import com.khmercalendar.ui.theme.Spacing
import java.time.LocalDate

/**
 * The dashboard.
 *
 * Which cards appear, and in what order, is a setting - so the list of cards is driven by
 * [com.khmercalendar.data.prefs.AppSettings.visibleDashboardCards] rather than by the order
 * they happen to be written here.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onOpenEvent: (Long, LocalDate) -> Unit,
    onOpenDay: (LocalDate) -> Unit,
    onAdd: (LocalDate) -> Unit,
    onNavigate: (String) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val noteDraft by viewModel.noteDraft.collectAsStateWithLifecycle()
    val settings = LocalAppSettings.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ប្រតិទិនខ្មែរ") },
                actions = {
                    IconButton(onClick = { onNavigate(Routes.SEARCH) }) {
                        Icon(Icons.Outlined.Search, contentDescription = "ស្វែងរក")
                    }
                    IconButton(onClick = { onNavigate(Routes.HOLIDAYS) }) {
                        Icon(Icons.Outlined.Celebration, contentDescription = "បុណ្យជាតិ")
                    }
                },
            )
        },
        floatingActionButton = {
            PrimaryFab(
                onClick = { onAdd(state.today) },
                icon = Icons.Outlined.Add,
                contentDescription = "បន្ថែមព្រឹត្តិការណ៍",
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(
                horizontal = Spacing.md,
                vertical = Spacing.md,
            ),
            verticalArrangement = Arrangement.spacedBy(settings.dashboardDensity.gapDp.dp),
        ) {
            if (state.isLoading) {
                // Not an empty state: until the query returns, "you have nothing on" is a
                // claim the app cannot yet make.
                item { DashboardSkeleton() }
            }

            if (!state.isLoading) settings.visibleDashboardCards().forEach { card ->
                item(key = card.key) {
                    when (card) {
                        com.khmercalendar.data.prefs.DashboardCard.TODAY ->
                            TodayHeroCard(
                                date = state.today,
                                eventsToday = state.todayEvents.size,
                                nextEventAt = state.nextEventToday?.start?.toLocalTime(),
                                nextEventTitle = state.nextEventToday?.title,
                                onOpenDay = onOpenDay,
                                // The lunar date and today's holiday belong on the card that
                                // establishes what day it is, not three positions further
                                // down the scroll. The separate lunar card stays available
                                // for anyone who has it switched on, and simply repeats it.
                                lunarText = state.lunar
                                    ?.takeIf { settings.showKhmerLunarDates }
                                    ?.format(),
                                holidayName = state.holidays
                                    .firstOrNull { it.date == state.today }
                                    ?.nameKm
                                    ?.takeIf { settings.showHolidays },
                            )

                        com.khmercalendar.data.prefs.DashboardCard.LUNAR ->
                            LunarCard(state)

                        com.khmercalendar.data.prefs.DashboardCard.UPCOMING ->
                            UpcomingCard(state, onOpenEvent, onNavigate)

                        com.khmercalendar.data.prefs.DashboardCard.HOLIDAYS ->
                            HolidayCard(state.holidays, onNavigate)

                        com.khmercalendar.data.prefs.DashboardCard.COUNTDOWN ->
                            CountdownCard(state.countdowns)

                        com.khmercalendar.data.prefs.DashboardCard.WORK ->
                            WorkCountdownCard(
                                schedule = settings.workSchedule,
                                onOpenSettings = {
                                    onNavigate(com.khmercalendar.ui.Routes.SETTINGS_WORK)
                                },
                            )

                        com.khmercalendar.data.prefs.DashboardCard.QUICK_ACTIONS ->
                            QuickActionsCard(
                                onAddEvent = { onAdd(state.today) },
                                onAddTask = { onAdd(state.today) },
                                onAddNote = { onOpenDay(state.today) },
                                onAssistant = { onNavigate(Routes.ASSISTANT) },
                                onSearch = { onNavigate(Routes.SEARCH) },
                            )

                        com.khmercalendar.data.prefs.DashboardCard.TASKS ->
                            TaskCard(state, viewModel::setTaskCompleted, onOpenEvent)

                        com.khmercalendar.data.prefs.DashboardCard.NOTE ->
                            NoteCard(
                                text = noteDraft ?: state.note,
                                onChange = viewModel::editNote,
                                onSave = viewModel::saveNote,
                                dirty = noteDraft != null && noteDraft != state.note,
                            )
                    }
                }
            }

            if (!state.isLoading) item { StatsRow(state) }
            // Clearance for the floating action button, which was sitting on top of the last
            // card - the lunar date ran underneath it on a device.
            item { Spacer(Modifier.height(Spacing.fabClearance)) }
        }
    }
}

@Composable
private fun LunarCard(state: HomeState) {
    val lunar = state.lunar ?: return
    DashboardCard(accent = CardAccent.CALENDAR, title = "ចន្ទគតិខ្មែរ") {
        Text(lunar.format(), style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(Spacing.xs))
        Text(
            "ច.ស. ${localeNumber(lunar.jolakSakarajYear)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * The wrapper every dashboard card goes through.
 *
 * One place decides a card's padding, its heading and the colour of the rule beside it, so the
 * nine cards on this screen cannot drift apart the way they had - a Card here, a clipped Box
 * there, headings at three different weights. Density comes from the user's setting rather
 * than a constant, which is the whole reason the padding is not simply written into
 * [SurfaceCard].
 */
@Composable
private fun DashboardCard(
    accent: CardAccent,
    title: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    val density = LocalAppSettings.current.dashboardDensity
    SurfaceCard(modifier = modifier, padding = density.cardPaddingDp.dp) {
        SectionTitle(title = title, accent = accent.color(), trailing = trailing)
        Spacer(Modifier.height(Spacing.md))
        content()
    }
}

@Composable
private fun UpcomingCard(
    state: HomeState,
    onOpenEvent: (Long, LocalDate) -> Unit,
    onNavigate: (String) -> Unit,
) {
    DashboardCard(
        accent = CardAccent.CALENDAR,
        title = "ព្រឹត្តិការណ៍ខាងមុខ",
        trailing = {
            TextButton(onClick = { onNavigate(Routes.AGENDA) }) { Text("ទាំងអស់") }
        },
    ) {
        if (state.upcoming.isEmpty()) {
            EmptyState(
                icon = Icons.Outlined.EventAvailable,
                title = "ទំនេរ",
                message = "គ្មានព្រឹត្តិការណ៍ក្នុងរយៈពេលខាងមុខ",
                compact = true,
            )
        } else {
            state.upcoming.forEach { event ->
                UpcomingRow(event) { onOpenEvent(event.eventId, event.occurrenceDate) }
            }
        }
    }
}

@Composable
private fun UpcomingRow(event: EventOccurrence, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.width(56.dp)) {
            Text(
                localeNumber(event.occurrenceDate.dayOfMonth),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                KhmerTerms.dayOfWeekShort(event.occurrenceDate.dayOfWeek),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        EventRow(event = event, modifier = Modifier.weight(1f), onClick = onClick)
    }
}

@Composable
private fun HolidayCard(holidays: List<Holiday>, onNavigate: (String) -> Unit) {
    DashboardCard(
        accent = CardAccent.HOLIDAY,
        title = "បុណ្យជាតិខាងមុខ",
        trailing = {
            TextButton(onClick = { onNavigate(Routes.HOLIDAYS) }) { Text("ទាំងអស់") }
        },
    ) {
        if (holidays.isEmpty()) {
            Text(
                "គ្មានបុណ្យក្នុងរយៈពេលខាងមុខ",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            holidays.forEach { holiday ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Gold for a public holiday, the muted tone for an observance - and the
                    // word "public" is not carried by the dot alone: a public holiday also
                    // gets the day count, which is the thing you actually want from this row.
                    val gold = CardAccent.HOLIDAY.color()
                    Box(
                        Modifier
                            .size(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                if (holiday.kind == HolidayKind.PUBLIC) {
                                    gold
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            ),
                    )
                    Spacer(Modifier.width(Spacing.md))
                    Column(Modifier.weight(1f)) {
                        Text(
                            holiday.nameKm,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            "${localeNumber(holiday.date.dayOfMonth)} ${KhmerTerms.solarMonth(holiday.date.monthValue)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.width(Spacing.sm))
                    val away = java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), holiday.date)
                    StatusBadge(
                        text = when {
                            away <= 0L -> "ថ្ងៃនេះ"
                            away == 1L -> "ស្អែក"
                            else -> "នៅ ${localeNumber(away)} ថ្ងៃ"
                        },
                        color = gold,
                    )
                }
            }
        }
    }
}

@Composable
private fun CountdownCard(countdowns: List<Countdown>) {
    if (countdowns.isEmpty()) return
    DashboardCard(accent = CardAccent.NEUTRAL, title = "រាប់ថយក្រោយ") {
        countdowns.forEach { item ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    item.title,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (item.daysAway == 0L) "ថ្ងៃនេះ" else "នៅ ${localeNumber(item.daysAway)} ថ្ងៃ",
                    style = MaterialTheme.typography.labelLarge,
                    // The same gold the holiday card uses. It was the error colour, which
                    // meant the two cards described the same Pchum Ben in red on one and
                    // gold on the other - and red on a holiday reads as a warning.
                    color = if (item.isHoliday) {
                        CardAccent.HOLIDAY.color()
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                )
            }
        }
    }
}

@Composable
private fun TaskCard(
    state: HomeState,
    onToggle: (Long, Boolean) -> Unit,
    onOpenEvent: (Long, LocalDate) -> Unit,
) {
    val done = state.stats.todayTasksDone
    val total = state.stats.todayTasksTotal
    DashboardCard(
        accent = CardAccent.TASKS,
        title = "កិច្ចការ",
        trailing = {
            if (total > 0) {
                Text(
                    "${localeNumber(done)}/${localeNumber(total)}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    ) {
        // The count and the bar say the same thing two ways, on purpose: a bar alone is a
        // shape you have to estimate, and a fraction alone is a number you have to picture.
        if (total > 0) {
            ProgressBar(
                fraction = done.toFloat() / total.toFloat(),
                indicator = CardAccent.TASKS.color(),
            )
            Spacer(Modifier.height(Spacing.md))
        }
        if (state.tasks.isEmpty()) {
            Text(
                if (total > 0) "រួចរាល់ទាំងអស់ហើយ" else "គ្មានកិច្ចការដែលនៅសល់",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            state.tasks.forEach { task ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onOpenEvent(task.eventId, task.occurrenceDate) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = task.isCompleted,
                        onCheckedChange = { onToggle(task.eventId, it) },
                    )
                    Text(
                        task.title,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun NoteCard(
    text: String,
    onChange: (String) -> Unit,
    onSave: () -> Unit,
    dirty: Boolean,
) {
    DashboardCard(
        accent = CardAccent.NEUTRAL,
        title = "កំណត់ចំណាំថ្ងៃនេះ",
        trailing = {
            if (dirty) TextButton(onClick = onSave) { Text("រក្សាទុក") }
        },
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = onChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("សរសេរអ្វីមួយ...") },
            minLines = 2,
            maxLines = 6,
        )
    }
}

@Composable
private fun StatsRow(state: HomeState) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
        StatBox("ព្រឹត្តិការណ៍ខែនេះ", state.stats.eventsThisMonth, Modifier.weight(1f))
        StatBox("កិច្ចការនៅសល់", state.stats.openTasks, Modifier.weight(1f))
        StatBox("កិច្ចការរួចរាល់", state.stats.doneTasks, Modifier.weight(1f))
    }
}

@Composable
private fun StatBox(label: String, value: Int, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(localeNumber(value), style = MaterialTheme.typography.headlineSmall)
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
            )
        }
    }
}
