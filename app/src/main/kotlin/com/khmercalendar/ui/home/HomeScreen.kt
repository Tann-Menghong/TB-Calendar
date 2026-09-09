package com.khmercalendar.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import com.khmercalendar.core.khmer.KhmerTerms
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
import com.khmercalendar.domain.CalendarViewMode
import com.khmercalendar.domain.CountdownItem
import com.khmercalendar.domain.CountdownStyle
import com.khmercalendar.domain.Countdowns
import com.khmercalendar.domain.DockSlot
import com.khmercalendar.ui.Routes
import com.khmercalendar.ui.components.DashboardSkeleton
import com.khmercalendar.ui.components.SectionTitle
import com.khmercalendar.ui.components.SurfaceCard
import com.khmercalendar.ui.components.localeNumber
import com.khmercalendar.ui.theme.CardAccent
import com.khmercalendar.ui.theme.LocalAppSettings
import com.khmercalendar.ui.theme.Radius
import com.khmercalendar.ui.theme.Spacing
import com.khmercalendar.ui.theme.color
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime

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
    onOpenCalendar: (CalendarViewMode) -> Unit,
    onNavigate: (String) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val noteDraft by viewModel.noteDraft.collectAsStateWithLifecycle()
    val settings = LocalAppSettings.current
    val density = settings.dashboardDensity
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    // One ticking clock for the whole dashboard. Each module used to call LocalTime.now()
    // during composition, which reads once and then never changes.
    val now = rememberCurrentMinute()

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
                            now = now,
                            noteDraft = noteDraft,
                            viewModel = viewModel,
                            onOpenEvent = onOpenEvent,
                            onOpenDay = onOpenDay,
                            onAdd = onAdd,
                            onOpenCalendar = onOpenCalendar,
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
                    slots = settings.dockSlots,
                    onAction = { slot ->
                        when (slot) {
                            DockSlot.ADD_EVENT, DockSlot.ADD_TASK -> onAdd(state.today)
                            DockSlot.NOTE -> onOpenDay(state.today)
                            DockSlot.COUNTDOWN -> onNavigate(Routes.COUNTDOWNS)
                            DockSlot.TASKS -> onNavigate(Routes.TASKS)
                            DockSlot.CALENDAR -> onOpenCalendar(CalendarViewMode.MONTH)
                            DockSlot.SEARCH -> onNavigate(Routes.SEARCH)
                            DockSlot.ASSISTANT -> onNavigate(Routes.ASSISTANT)
                        }
                    },
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
    now: LocalDateTime,
    noteDraft: String?,
    viewModel: HomeViewModel,
    onOpenEvent: (Long, LocalDate) -> Unit,
    onOpenDay: (LocalDate) -> Unit,
    onAdd: (LocalDate) -> Unit,
    onOpenCalendar: (CalendarViewMode) -> Unit,
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
            next = state.nextEventToday(now),
            now = now.toLocalTime(),
            tasksDone = state.stats.todayTasksDone,
            tasksTotal = state.stats.todayTasksTotal,
            eventsToday = state.todayEvents.size,
            onOpenEvent = onOpenEvent,
            onOpenTasks = { onNavigate(Routes.TASKS) },
        )

        DashboardCard.WEEK -> WeekModule(
            days = state.week,
            totals = state.weekTotals,
            onOpenDay = onOpenDay,
        )

        DashboardCard.MONTH -> MonthModule(
            weeks = state.month,
            totals = state.monthTotals,
            weekStart = settings.weekStart,
            onOpenDay = onOpenDay,
            onOpenMonth = { onOpenCalendar(CalendarViewMode.MONTH) },
        )

        DashboardCard.TIMELINE -> {
            val entries = remember(state.todayEvents, settings.workSchedule, state.today) {
                DayTimeline.build(state.today, state.todayEvents, settings.workSchedule)
            }
            TimelineModule(
                entries = entries,
                now = now.toLocalTime(),
                isToday = state.today == now.toLocalDate(),
                onOpenEvent = onOpenEvent,
                onOpenAgenda = { onOpenCalendar(CalendarViewMode.AGENDA) },
            )
        }

        DashboardCard.TASKS -> FocusModule(
            tasks = state.tasks,
            onToggle = viewModel::setTaskCompleted,
            onOpen = onOpenEvent,
            onViewAll = { onNavigate(Routes.TASKS) },
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

        DashboardCard.COUNTDOWN -> CountdownModule(
            items = state.countdowns,
            now = now,
            onOpen = { id, date -> onOpenEvent(id, date) },
            onOpenAll = { onNavigate(Routes.COUNTDOWNS) },
        )

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
    val accent = CardAccent.HOLIDAY.color()
    if (state.holidays.isEmpty()) {
        SurfaceCard(padding = LocalAppSettings.current.dashboardDensity.cardPaddingDp.dp) {
            ModuleEmpty("បុណ្យជាតិខាងមុខ", "គ្មានបុណ្យជាតិក្នុងរយៈពេលខាងមុខ", accent = accent)
        }
        return
    }
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

/**
 * The countdown card.
 *
 * Says where each row came from, because the card shows pinned dates when there are any and
 * the next public holidays when there are not - and a card that silently swaps its source
 * is a card you cannot trust. The empty state says how to pin, since the pin lives on the
 * event's own screen and nothing else in the app points at it.
 */
@Composable
private fun CountdownModule(
    items: List<CountdownItem>,
    now: LocalDateTime,
    onOpen: (Long, LocalDate) -> Unit,
    onOpenAll: () -> Unit,
) {
    val settings = LocalAppSettings.current
    val padding = settings.dashboardDensity.cardPaddingDp.dp

    if (items.isEmpty()) {
        SurfaceCard(padding = padding) {
            ModuleEmpty("រាប់ថយក្រោយ", "បើកព្រឹត្តិការណ៍មួយ ហើយចុចរូបខ្ទាស់ ដើម្បីរាប់ថយក្រោយ")
        }
        return
    }

    SurfaceCard(padding = padding) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            ModuleLabel("រាប់ថយក្រោយ", Modifier.weight(1f))
            TextButton(onClick = onOpenAll) { Text("ទាំងអស់") }
        }
        Spacer(Modifier.height(Spacing.sm))
        items.forEach { item ->
            CountdownRow(
                item = item,
                now = now,
                style = settings.countdownStyle,
                khmerNumerals = settings.useKhmerNumerals,
                onClick = { onOpen(item.eventId, item.date) }.takeIf { item.eventId > 0L },
            )
        }
    }
}

/**
 * One countdown.
 *
 * The remaining time is the loud half and the date the quiet one: a countdown answers "how
 * long", and the date is what you check afterwards. A holiday row is labelled as such rather
 * than only tinted - the accent alone would be the app choosing a colour to mean "this one is
 * not yours", which is exactly the kind of colour-only meaning that fails for anybody who
 * cannot separate the two.
 */
@Composable
private fun CountdownRow(
    item: CountdownItem,
    now: LocalDateTime,
    style: CountdownStyle,
    khmerNumerals: Boolean,
    onClick: (() -> Unit)?,
) {
    val row = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(Radius.sm))
        .let { if (onClick != null) it.clickable(onClick = onClick) else it }
        .padding(vertical = Spacing.sm, horizontal = Spacing.xs)

    Row(row, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = buildString {
                    append(localeNumber(item.date.dayOfMonth, khmerNumerals))
                    append(" ")
                    append(KhmerTerms.solarMonth(item.date.monthValue))
                    if (item.isHoliday) append(" · បុណ្យជាតិ")
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = Countdowns.remaining(item, now, style, khmerNumerals),
            style = MaterialTheme.typography.labelLarge,
            color = if (item.isHoliday) {
                CardAccent.HOLIDAY.color()
            } else {
                MaterialTheme.colorScheme.primary
            },
        )
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
