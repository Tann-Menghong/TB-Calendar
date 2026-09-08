package com.khmercalendar.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.khmercalendar.data.prefs.DashboardCard
import com.khmercalendar.data.prefs.SettingsStore
import com.khmercalendar.domain.DashboardArrangement
import com.khmercalendar.domain.DayTimeline
import com.khmercalendar.ui.Routes
import com.khmercalendar.ui.components.DashboardSkeleton
import com.khmercalendar.ui.components.SectionTitle
import com.khmercalendar.ui.components.SurfaceCard
import com.khmercalendar.ui.components.localeNumber
import com.khmercalendar.ui.theme.CardAccent
import com.khmercalendar.ui.theme.LocalAppSettings
import com.khmercalendar.ui.theme.Spacing
import com.khmercalendar.ui.theme.color
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

/**
 * The dashboard.
 *
 * ## The shape of this screen
 *
 * It reads top to bottom as three levels of urgency, and that ordering is the design rather
 * than an accident of which card was written first.
 *
 * **Now** - the live header, the day, and the work status. Everything the user opened the app
 * to find out is above the fold.
 *
 * **Today** - what is next and how the day is going, side by side; then the day as a
 * timeline, which is the piece that replaced a flat list of today's events. A list says what
 * is on. A timeline says where you are in it.
 *
 * **Ahead** - focus, the lunar detail, what is coming after today, the assistant, the dock.
 *
 * Which modules appear and in what order is still a setting, so a user who does not work to a
 * schedule can drop the work module and one who does not use tasks can drop focus. The
 * ordering above is the default, not a constraint.
 */
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    settingsStore: SettingsStore,
    onOpenEvent: (Long, LocalDate) -> Unit,
    onOpenDay: (LocalDate) -> Unit,
    onAdd: (LocalDate) -> Unit,
    onNavigate: (String) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val noteDraft by viewModel.noteDraft.collectAsStateWithLifecycle()
    val settings = LocalAppSettings.current
    val density = settings.dashboardDensity
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current

    val listState = rememberLazyListState()
    // derivedStateOf, not a plain read: the scroll offset changes on every frame of a fling,
    // and a boolean recomputed there would recompose the header sixty times a second to hand
    // it the same value.
    val compactHeader by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 24
        }
    }

    /** The module whose long-press menu is open, if any. */
    var sheetFor by remember { mutableStateOf<DashboardCard?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        DashboardHeader(
            date = state.today,
            onSearch = { onNavigate(Routes.SEARCH) },
            onSettings = { onNavigate(Routes.SETTINGS) },
            compact = compactHeader,
        )

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = Spacing.lg,
                end = Spacing.lg,
                bottom = Spacing.lg,
            ),
            verticalArrangement = Arrangement.spacedBy(density.gapDp.dp),
        ) {
            if (state.isLoading) {
                item { DashboardSkeleton() }
                return@LazyColumn
            }

            settings.visibleDashboardCards().forEach { card ->
                item(key = card.key) {
                    Column(
                        Modifier
                            // Long-press opens the module's own menu. The gesture sits on the
                            // wrapper rather than inside each module: a child that handles
                            // taps consumes the press first, so opening an event still opens
                            // the event, and the long press is available everywhere else.
                            .pointerInput(card) {
                                detectTapGestures(
                                    onLongPress = {
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        sheetFor = card
                                    },
                                )
                            }
                            // A long press is invisible to a screen reader, so the same menu
                            // is offered as a named action on the module.
                            .semantics {
                                customActions = listOf(
                                    CustomAccessibilityAction(EDIT_MODULE_ACTION) {
                                        sheetFor = card
                                        true
                                    },
                                )
                            },
                    ) {
                        DashboardModule(
                            card = card,
                            state = state,
                            noteDraft = noteDraft,
                            viewModel = viewModel,
                            onOpenEvent = onOpenEvent,
                            onOpenDay = onOpenDay,
                            onAdd = onAdd,
                            onNavigate = onNavigate,
                        )
                    }
                }
            }

            // The dock closes the dashboard. It is not in the reorderable list because it is
            // a control panel rather than information, and a set of controls that can end up
            // in the middle of a page is not a dock.
            item(key = "dock") {
                Spacer(Modifier.height(Spacing.sm))
                QuickDock(
                    onAddEvent = { onAdd(state.today) },
                    onAddTask = { onAdd(state.today) },
                    onAddNote = { onOpenDay(state.today) },
                    onCountdown = { onNavigate(Routes.HOLIDAYS) },
                )
            }
            item { Spacer(Modifier.height(Spacing.lg)) }
        }
    }

    sheetFor?.let { card ->
        // The order to move within is the saved one, not the visible one: moving a module up
        // past a hidden neighbour has to move it past that neighbour, or unhiding it later
        // would put it somewhere the user never placed it.
        val order = settings.dashboardCards
        val rows = DashboardArrangement.arrangeable(order)
        val index = rows.indexOf(card)
        ModuleSheet(
            card = card,
            canMoveUp = index > 0,
            canMoveDown = index >= 0 && index < rows.lastIndex,
            bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
            onDismiss = { sheetFor = null },
            onHide = {
                sheetFor = null
                scope.launch {
                    settingsStore.setDashboardHidden(settings.hiddenDashboardCards + card.key)
                }
            },
            onMove = { delta ->
                sheetFor = null
                val target = index + delta
                if (target in rows.indices) {
                    scope.launch {
                        settingsStore.setDashboardOrder(
                            DashboardArrangement.move(
                                order,
                                order.indexOf(rows[index]),
                                order.indexOf(rows[target]),
                            ),
                        )
                    }
                }
            },
            onEdit = {
                sheetFor = null
                onNavigate(Routes.SETTINGS_DASHBOARD)
            },
        )
    }
}

/** Named once so the sheet's title and the accessibility action cannot drift apart. */
internal const val EDIT_MODULE_ACTION = "កែផ្ទាំង"


/**
 * One module, chosen by its key.
 *
 * A single `when` rather than nine call sites, so the reorderable list in settings and the
 * dashboard cannot fall out of step: adding a module means adding an enum entry and a branch
 * here, and it appears in both places or in neither.
 */
@Composable
private fun DashboardModule(
    card: DashboardCard,
    state: HomeState,
    noteDraft: String?,
    viewModel: HomeViewModel,
    onOpenEvent: (Long, LocalDate) -> Unit,
    onOpenDay: (LocalDate) -> Unit,
    onAdd: (LocalDate) -> Unit,
    onNavigate: (String) -> Unit,
) {
    val settings = LocalAppSettings.current
    when (card) {
        DashboardCard.TODAY -> TodayHeroCard(
            date = state.today,
            eventsToday = state.todayEvents.size,
            onOpenDay = onOpenDay,
            lunarText = state.lunar?.takeIf { settings.showKhmerLunarDates }?.format(),
            holidayName = state.holidays
                .firstOrNull { it.date == state.today }
                ?.nameKm
                ?.takeIf { settings.showHolidays },
        )

        DashboardCard.WORK -> WorkCountdownCard(
            schedule = settings.workSchedule,
            onOpenSettings = { onNavigate(Routes.SETTINGS_WORK) },
        )

        DashboardCard.STATS -> NextAndProgress(
            next = state.nextEventToday,
            now = LocalTime.now(),
            tasksDone = state.stats.todayTasksDone,
            tasksTotal = state.stats.todayTasksTotal,
            eventsToday = state.todayEvents.size,
            onOpenEvent = onOpenEvent,
            onOpenTasks = { onNavigate(Routes.AGENDA) },
        )

        DashboardCard.WEEK -> WeekModule(
            days = state.week,
            totals = state.weekTotals,
            onOpenDay = onOpenDay,
        )

        DashboardCard.TIMELINE -> {
            val entries = remember(state.todayEvents, settings.workSchedule, state.today) {
                DayTimeline.build(state.today, state.todayEvents, settings.workSchedule)
            }
            TimelineModule(
                entries = entries,
                now = LocalTime.now(),
                isToday = state.today == LocalDate.now(),
                onOpenEvent = onOpenEvent,
                onOpenAgenda = { onNavigate(Routes.AGENDA) },
            )
        }

        DashboardCard.TASKS -> FocusModule(
            tasks = state.tasks,
            onToggle = viewModel::setTaskCompleted,
            onOpen = onOpenEvent,
            onViewAll = { onNavigate(Routes.AGENDA) },
        )

        DashboardCard.LUNAR -> LunarModule(state, onOpenDay)

        DashboardCard.UPCOMING -> UpNextStrip(
            events = state.upcoming,
            holidays = state.holidays,
            today = state.today,
            onOpenEvent = onOpenEvent,
            onOpenHolidays = { onNavigate(Routes.HOLIDAYS) },
        )

        DashboardCard.HOLIDAYS -> HolidayModule(state, onNavigate)

        DashboardCard.AI -> AiAssistStrip(
            onPaste = { onNavigate(Routes.ASSISTANT) },
            onSummarise = { onNavigate(Routes.ASSISTANT) },
        )

        DashboardCard.COUNTDOWN -> CountdownModule(state)

        DashboardCard.NOTE -> NoteModule(
            text = noteDraft ?: state.note,
            onChange = viewModel::editNote,
            onSave = viewModel::saveNote,
            dirty = noteDraft != null && noteDraft != state.note,
        )

        // Superseded by the dock at the foot of the dashboard. Kept in the enum so that a
        // user who had it in their saved order does not lose their whole layout.
        DashboardCard.QUICK_ACTIONS -> Unit
    }
}

@Composable
private fun LunarModule(state: HomeState, onOpenDay: (LocalDate) -> Unit) {
    val lunar = state.lunar ?: return
    val accent = CardAccent.HOLIDAY.color()
    SurfaceCard(
        padding = LocalAppSettings.current.dashboardDensity.cardPaddingDp.dp,
        onClick = { onOpenDay(state.today) },
    ) {
        ModuleLabel("ចន្ទគតិខ្មែរ", color = accent)
        Spacer(Modifier.height(Spacing.md))
        Text(
            lunar.format(),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(Spacing.xs))
        Text(
            "ច.ស. ${localeNumber(lunar.jolakSakarajYear)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun HolidayModule(state: HomeState, onNavigate: (String) -> Unit) {
    if (state.holidays.isEmpty()) return
    val accent = CardAccent.HOLIDAY.color()
    SurfaceCard(padding = LocalAppSettings.current.dashboardDensity.cardPaddingDp.dp) {
        SectionTitle(
            title = "បុណ្យជាតិខាងមុខ",
            accent = accent,
            trailing = {
                TextButton(onClick = { onNavigate(Routes.HOLIDAYS) }) { Text("ទាំងអស់") }
            },
        )
        Spacer(Modifier.height(Spacing.md))
        state.holidays.take(3).forEach { holiday ->
            HolidayRow(holiday = holiday, today = state.today, accent = accent)
        }
    }
}

@Composable
private fun CountdownModule(state: HomeState) {
    if (state.countdowns.isEmpty()) return
    SurfaceCard(padding = LocalAppSettings.current.dashboardDensity.cardPaddingDp.dp) {
        ModuleLabel("រាប់ថយក្រោយ")
        Spacer(Modifier.height(Spacing.md))
        state.countdowns.forEach { item ->
            androidx.compose.foundation.layout.Row(
                Modifier.fillMaxWidth().padding(vertical = Spacing.xs),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                Text(
                    item.title,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
                Text(
                    if (item.daysAway == 0L) "ថ្ងៃនេះ" else "នៅ ${localeNumber(item.daysAway)} ថ្ងៃ",
                    style = MaterialTheme.typography.labelLarge,
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
private fun NoteModule(
    text: String,
    onChange: (String) -> Unit,
    onSave: () -> Unit,
    dirty: Boolean,
) {
    SurfaceCard(padding = LocalAppSettings.current.dashboardDensity.cardPaddingDp.dp) {
        SectionTitle(
            title = "កំណត់ចំណាំថ្ងៃនេះ",
            accent = CardAccent.NEUTRAL.color(),
            trailing = { if (dirty) TextButton(onClick = onSave) { Text("រក្សាទុក") } },
        )
        Spacer(Modifier.height(Spacing.md))
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
