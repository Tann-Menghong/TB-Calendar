package com.khmercalendar.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.ChecklistRtl
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.khmercalendar.AppContainer
import com.khmercalendar.ExternalDestination
import com.khmercalendar.ui.agenda.AgendaScreen
import com.khmercalendar.ui.agenda.AgendaViewModel
import com.khmercalendar.ui.agenda.SearchScreen
import com.khmercalendar.ui.assistant.AssistantScreen
import com.khmercalendar.ui.assistant.AssistantViewModel
import com.khmercalendar.domain.CalendarViewMode
import com.khmercalendar.ui.countdown.CountdownScreen
import com.khmercalendar.ui.countdown.CountdownViewModel
import com.khmercalendar.ui.calendar.CalendarScreen
import com.khmercalendar.ui.calendar.CalendarViewModel
import com.khmercalendar.ui.event.EventDetailScreen
import com.khmercalendar.ui.event.EventEditScreen
import com.khmercalendar.ui.event.EventEditViewModel
import com.khmercalendar.ui.holiday.HolidayScreen
import com.khmercalendar.ui.home.HomeScreen
import com.khmercalendar.ui.home.HomeViewModel
import com.khmercalendar.ui.settings.AboutScreen
import com.khmercalendar.ui.settings.UpdateViewModel
import com.khmercalendar.ui.settings.UpdateScreen
import com.khmercalendar.ui.settings.WorkScheduleScreen
import com.khmercalendar.ui.settings.AiModelScreen
import com.khmercalendar.ui.settings.AiModelViewModel
import com.khmercalendar.ui.settings.AppearanceScreen
import com.khmercalendar.ui.settings.BackupScreen
import com.khmercalendar.ui.settings.CategoriesScreen
import com.khmercalendar.ui.settings.CategoriesViewModel
import com.khmercalendar.ui.settings.DashboardEditorScreen
import com.khmercalendar.ui.settings.NotificationSettingsScreen
import com.khmercalendar.ui.settings.SettingsScreen
import com.khmercalendar.ui.tasks.TaskListScreen
import com.khmercalendar.ui.tasks.TaskListViewModel
import java.time.LocalDate
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf

private data class TopLevel(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

/**
 * The five destinations.
 *
 * Named for what they hold rather than for the view they happen to open with: the second tab
 * is the calendar, not "the month", and the third is your tasks and schedule, not "the agenda
 * view". A user picking a tab is choosing a subject.
 */
private val TOP_LEVEL = listOf(
    TopLevel(Routes.HOME, "ដើម", Icons.Outlined.Dashboard),
    TopLevel(Routes.CALENDAR, "ប្រតិទិន", Icons.Outlined.CalendarMonth),
    TopLevel(Routes.TASKS, "កិច្ចការ", Icons.Outlined.ChecklistRtl),
    TopLevel(Routes.ASSISTANT, "AI", Icons.Outlined.AutoAwesome),
    TopLevel(Routes.SETTINGS, "ផ្សេងៗ", Icons.Outlined.Tune),
)

/**
 * The navigation graph.
 *
 * The month, week and day views share one [CalendarViewModel] hoisted to the host, because
 * they share a selected date: tapping a day in the month grid and then switching to the day
 * view should land on that day, not on today.
 */
@Composable
fun KhmerCalendarNavHost(
    container: AppContainer,
    external: ExternalDestination?,
    onExternalConsumed: () -> Unit,
) {
    val navController = rememberNavController()
    val factory = remember(container) { appViewModelFactory(container) }
    val settings by container.settings.collectAsStateWithLifecycle()

    val calendarViewModel: CalendarViewModel = viewModel(factory = factory)

    // The start screen setting carries a view as well as a destination, and the four calendar
    // entries all land on the same route now. Applied once, on the first composition with real
    // settings - MainActivity holds the first frame until they have loaded - so that choosing
    // the week view as your start screen still opens the week view.
    LaunchedEffect(Unit) {
        settings.startScreen.view?.let(calendarViewModel::show)
    }

    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    // Held here rather than in the route arguments: a pasted announcement can be thousands
    // of characters, and a navigation argument is a URL.
    var sharedText by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(external) {
        val destination = external ?: return@LaunchedEffect
        when (destination) {
            is ExternalDestination.Event -> {
                val date = destination.date ?: LocalDate.now()
                navController.navigate(Routes.eventDetail(destination.id, date.toString()))
            }

            is ExternalDestination.Day -> {
                calendarViewModel.select(destination.date)
                calendarViewModel.show(CalendarViewMode.DAY)
                navController.navigateTopLevel(Routes.CALENDAR)
            }

            is ExternalDestination.NewEvent -> {
                val date = destination.date ?: LocalDate.now()
                navController.navigate(Routes.eventEdit(0L, date.toString()))
            }

            ExternalDestination.Assistant -> navController.navigateTopLevel(Routes.ASSISTANT)

            is ExternalDestination.TextToEvent -> {
                sharedText = destination.text
                navController.navigateTopLevel(Routes.ASSISTANT)
            }

            is ExternalDestination.ImportIcs ->
                navController.navigate("${Routes.SETTINGS_BACKUP}?import=${destination.uri}")
        }
        onExternalConsumed()
    }

    Scaffold(
        bottomBar = {
            AnimatedVisibility(
                visible = currentRoute in TOP_LEVEL.map { it.route },
                enter = expandVertically(),
                exit = shrinkVertically(),
            ) {
                // The container is named rather than inherited: Material tints the bar with
                // surfaceTint, which on a saturated accent gave the light theme a lavender
                // bar under an otherwise white-and-green screen.
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ) {
                    TOP_LEVEL.forEach { item ->
                        NavigationBarItem(
                            selected = currentRoute == item.route,
                            onClick = { navController.navigateTopLevel(item.route) },
                            icon = { Icon(item.icon, contentDescription = item.label) },
                            label = { Text(item.label) },
                            // Every destination keeps its label. Five Khmer words fit
                            // across the bar, and an icon-only tab asks the user to learn
                            // what a sparkle means before they can find the assistant.
                            alwaysShowLabel = true,
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = settings.startScreen.route,
            modifier = Modifier.padding(padding),
        ) {
            composable(Routes.HOME) {
                val vm: HomeViewModel = viewModel(factory = factory)
                HomeScreen(
                    viewModel = vm,
                    settingsStore = container.settingsStore,
                    onOpenEvent = { id, date -> navController.navigate(Routes.eventDetail(id, date.toString())) },
                    onOpenDay = { date ->
                        calendarViewModel.select(date)
                        calendarViewModel.show(CalendarViewMode.DAY)
                        navController.navigateTopLevel(Routes.CALENDAR)
                    },
                    onAdd = { date -> navController.navigate(Routes.eventEdit(0L, date.toString())) },
                    onOpenCalendar = { mode ->
                        calendarViewModel.show(mode)
                        navController.navigateTopLevel(Routes.CALENDAR)
                    },
                    onNavigate = navController::navigate,
                )
            }

            composable(Routes.CALENDAR) {
                CalendarScreen(
                    viewModel = calendarViewModel,
                    onOpenEvent = { id, date -> navController.navigate(Routes.eventDetail(id, date.toString())) },
                    onAdd = { date -> navController.navigate(Routes.eventEdit(0L, date.toString())) },
                    onSearch = { navController.navigate(Routes.SEARCH) },
                ) {
                    // The agenda keeps its own view model - it pages a rolling window that
                    // has nothing to do with the month grid's - and is scoped to this entry,
                    // so it is built the first time somebody opens the agenda view rather
                    // than every time the calendar tab is.
                    val agendaViewModel: AgendaViewModel = viewModel(factory = factory)
                    AgendaScreen(
                        viewModel = agendaViewModel,
                        onOpenEvent = { id, date ->
                            navController.navigate(Routes.eventDetail(id, date.toString()))
                        },
                    )
                }
            }

            composable(Routes.TASKS) {
                val vm: TaskListViewModel = viewModel(factory = factory)
                TaskListScreen(
                    viewModel = vm,
                    onOpenTask = { id, date ->
                        navController.navigate(Routes.eventDetail(id, date.toString()))
                    },
                    onOpenAgenda = {
                        calendarViewModel.show(CalendarViewMode.AGENDA)
                        navController.navigateTopLevel(Routes.CALENDAR)
                    },
                    onFullEditor = { date ->
                        navController.navigate(Routes.eventEdit(0L, date.toString()))
                    },
                )
            }

            composable(Routes.SEARCH) {
                val vm: AgendaViewModel = viewModel(factory = factory)
                SearchScreen(
                    viewModel = vm,
                    onBack = navController::popBackStack,
                    onOpenEvent = { id, date -> navController.navigate(Routes.eventDetail(id, date.toString())) },
                    // A date, a note or a holiday all resolve to a day rather than to a
                    // row, so they select it and hand over to the day view - the same
                    // landing the widget's day link and the month grid already use.
                    onOpenDay = { date ->
                        calendarViewModel.select(date)
                        calendarViewModel.show(CalendarViewMode.DAY)
                        navController.navigateTopLevel(Routes.CALENDAR)
                    },
                )
            }

            composable(Routes.ASSISTANT) {
                val vm: AssistantViewModel = viewModel(factory = factory)
                AssistantScreen(
                    viewModel = vm,
                    sharedText = sharedText,
                    onOpenEvent = { id, date -> navController.navigate(Routes.eventDetail(id, date.toString())) },
                    onManageModels = { navController.navigate(Routes.SETTINGS_AI) },
                )
            }

            composable(Routes.HOLIDAYS) {
                HolidayScreen(onBack = navController::popBackStack)
            }

            composable(Routes.COUNTDOWNS) {
                val vm: CountdownViewModel = viewModel(factory = factory)
                // Re-read on every entry: a countdown screen left open overnight would
                // otherwise still be counting from yesterday.
                LaunchedEffect(Unit) { vm.refreshToday() }
                CountdownScreen(
                    viewModel = vm,
                    settingsStore = container.settingsStore,
                    onOpenEvent = { id, date ->
                        navController.navigate(Routes.eventDetail(id, date.toString()))
                    },
                    onBack = navController::popBackStack,
                )
            }

            composable(Routes.SETTINGS) {
                SettingsScreen(
                    settings = settings,
                    settingsStore = container.settingsStore,
                    onNavigate = navController::navigate,
                )
            }

            composable(Routes.SETTINGS_APPEARANCE) {
                AppearanceScreen(
                    settings = settings,
                    settingsStore = container.settingsStore,
                    onOpenDashboard = { navController.navigate(Routes.SETTINGS_DASHBOARD) },
                    onBack = navController::popBackStack,
                )
            }

            composable(Routes.SETTINGS_DASHBOARD) {
                DashboardEditorScreen(
                    settings = settings,
                    settingsStore = container.settingsStore,
                    onBack = navController::popBackStack,
                )
            }

            composable(Routes.SETTINGS_NOTIFICATIONS) {
                NotificationSettingsScreen(
                    settings = settings,
                    settingsStore = container.settingsStore,
                    scheduler = container.reminderScheduler,
                    onBack = navController::popBackStack,
                )
            }

            composable(Routes.SETTINGS_CATEGORIES) {
                val vm: CategoriesViewModel = viewModel(factory = factory)
                CategoriesScreen(viewModel = vm, onBack = navController::popBackStack)
            }

            composable(
                route = "${Routes.SETTINGS_BACKUP}?import={import}",
                arguments = listOf(
                    navArgument("import") { type = NavType.StringType; nullable = true; defaultValue = null },
                ),
            ) { entry ->
                BackupScreen(
                    backupManager = container.backupManager,
                    database = container.database,
                    scheduler = container.reminderScheduler,
                    pendingImportUri = entry.arguments?.getString("import"),
                    onBack = navController::popBackStack,
                )
            }

            composable(Routes.SETTINGS_AI) {
                val vm: AiModelViewModel = viewModel(factory = factory)
                AiModelScreen(viewModel = vm, onBack = navController::popBackStack)
            }

            composable(Routes.SETTINGS_WORK) {
                WorkScheduleScreen(
                    settingsFlow = container.settings,
                    settingsStore = container.settingsStore,
                    onBack = navController::popBackStack,
                )
            }

            composable(Routes.SETTINGS_UPDATE) {
                val vm: UpdateViewModel = viewModel(factory = factory)
                UpdateScreen(viewModel = vm, onBack = navController::popBackStack)
            }

            composable(Routes.SETTINGS_ABOUT) {
                AboutScreen(onBack = navController::popBackStack)
            }

            composable(
                route = Routes.EVENT_DETAIL,
                arguments = listOf(
                    navArgument("eventId") { type = NavType.LongType },
                    navArgument("date") { type = NavType.StringType; defaultValue = "" },
                ),
            ) { entry ->
                val vm: EventEditViewModel = viewModel(factory = factory)
                val eventId = entry.arguments?.getLong("eventId") ?: 0L
                val date = entry.arguments?.getString("date")
                    ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                    ?: LocalDate.now()
                EventDetailScreen(
                    viewModel = vm,
                    eventId = eventId,
                    occurrenceDate = date,
                    onBack = navController::popBackStack,
                    onEdit = { id -> navController.navigate(Routes.eventEdit(id, date.toString())) },
                )
            }

            composable(
                route = Routes.EVENT_EDIT,
                arguments = listOf(
                    navArgument("eventId") { type = NavType.LongType },
                    navArgument("date") { type = NavType.StringType; defaultValue = "" },
                ),
            ) { entry ->
                val vm: EventEditViewModel = viewModel(factory = factory)
                val eventId = entry.arguments?.getLong("eventId") ?: 0L
                val date = entry.arguments?.getString("date")
                    ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                LaunchedEffect(eventId, date) { vm.load(eventId, date) }
                EventEditScreen(
                    viewModel = vm,
                    onClose = navController::popBackStack,
                    onSaved = navController::popBackStack,
                )
            }
        }
    }
}

/**
 * Switching between the five main destinations should not build a back stack: pressing back
 * from any of them leaves the app rather than walking the tabs the user visited.
 */
private fun NavHostController.navigateTopLevel(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
