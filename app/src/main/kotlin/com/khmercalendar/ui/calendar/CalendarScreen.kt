package com.khmercalendar.ui.calendar

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.EditCalendar
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.khmercalendar.core.khmer.CalendarWeek
import com.khmercalendar.core.khmer.KhmerTerms
import com.khmercalendar.domain.CalendarNavigation
import com.khmercalendar.domain.CalendarViewMode
import com.khmercalendar.ui.components.AppMotion
import com.khmercalendar.ui.components.PrimaryFab
import com.khmercalendar.ui.components.localeNumber
import com.khmercalendar.ui.components.rememberPlatformPickers
import com.khmercalendar.ui.theme.LocalAppSettings
import com.khmercalendar.ui.theme.Motion
import com.khmercalendar.ui.theme.Spacing
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * The calendar, and the switcher that chooses how to read it.
 *
 * ## Why this screen exists
 *
 * The month, the week, the day and the agenda were four separate destinations, and only two
 * of them had a way in: the month was a tab, and the day opened when you tapped a date. The
 * week view could be reached only by making it the app's start screen, and the agenda only
 * from a link on two other screens. All four were written, tested and shipped; three of them
 * were simply hard or impossible to find, which for a user is close to not having them.
 *
 * ## Why one header rather than four
 *
 * Every dated view needs the same four controls - where am I, one back, one forward, take me
 * to today - and each means something different per view. Writing that once and stepping
 * through [CalendarNavigation.step] is what stops "next" meaning next month in one view and
 * next week in another purely because two people wrote two headers.
 *
 * The agenda is the exception and says so: it is a rolling list from today rather than a
 * window onto a date, so its arrows and its jump-to-date are hidden rather than shown doing
 * nothing.
 */
@Composable
fun CalendarScreen(
    viewModel: CalendarViewModel,
    onOpenEvent: (Long, LocalDate) -> Unit,
    onAdd: (LocalDate) -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
    agenda: @Composable () -> Unit,
) {
    val mode by viewModel.viewMode.collectAsStateWithLifecycle()
    val selected by viewModel.selectedDate.collectAsStateWithLifecycle()
    val visibleMonth by viewModel.visibleMonth.collectAsStateWithLifecycle()
    val settings = LocalAppSettings.current
    val pickers = rememberPlatformPickers()

    // The month browses independently of the selection - see CalendarViewModel.step - so the
    // header's idea of "where am I" is the visible month there, and the selected day elsewhere.
    val anchor = if (mode == CalendarViewMode.MONTH) visibleMonth.atDay(1) else selected
    val today = LocalDate.now()
    val showingToday = CalendarNavigation.showsToday(mode, anchor, today, settings.weekStart)

    Scaffold(
        modifier = modifier,
        // The host already insets the whole navigation graph for the status bar and the
        // bottom bar. A nested Scaffold defaults to insetting for them again, which put a
        // second status bar's worth of blank space above the month's title.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            PrimaryFab(
                onClick = { onAdd(if (mode == CalendarViewMode.AGENDA) today else selected) },
                icon = Icons.Outlined.Add,
                contentDescription = "បង្កើតព្រឹត្តិការណ៍",
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            CalendarHeader(
                mode = mode,
                anchor = anchor,
                showingToday = showingToday,
                onPrevious = { viewModel.step(forward = false) },
                onNext = { viewModel.step(forward = true) },
                onToday = viewModel::goToToday,
                onJump = { pickers.date(selected) { viewModel.select(it) } },
                onSearch = onSearch,
            )
            ViewSwitcher(mode = mode, onSelect = viewModel::show)

            // A crossfade, not a slide. The four views differ in height by hundreds of
            // pixels, and sliding one in reads as the screen falling over rather than as a
            // change of view.
            //
            // The specs are read out here because transitionSpec is a plain lambda rather
            // than a composable one, and the motion gate can only be consulted from
            // composition - inside the lambda it would be silently ungated.
            val fadeInSpec = AppMotion.finiteTweenOf<Float>(Motion.SMALL)
            val fadeOutSpec = AppMotion.finiteTweenOf<Float>(Motion.MICRO)
            AnimatedContent(
                targetState = mode,
                transitionSpec = { fadeIn(fadeInSpec) togetherWith fadeOut(fadeOutSpec) },
                label = "calendar-view",
            ) { current ->
                when (current) {
                    CalendarViewMode.MONTH -> MonthScreen(
                        viewModel = viewModel,
                        onOpenDay = { date ->
                            viewModel.select(date)
                            viewModel.show(CalendarViewMode.DAY)
                        },
                        onOpenEvent = onOpenEvent,
                    )

                    CalendarViewMode.WEEK -> WeekScreen(
                        viewModel = viewModel,
                        onOpenEvent = onOpenEvent,
                        onAddOn = onAdd,
                        onOpenDay = { date ->
                            viewModel.select(date)
                            viewModel.show(CalendarViewMode.DAY)
                        },
                    )

                    CalendarViewMode.DAY -> DayScreen(
                        viewModel = viewModel,
                        onOpenEvent = onOpenEvent,
                    )

                    CalendarViewMode.AGENDA -> agenda()
                }
            }
        }
    }
}

/**
 * Where you are, and the ways of moving.
 *
 * Two lines rather than one: the loud label answers the question, and the quiet second line
 * carries the year and the week number, which matter only once you have browsed far enough to
 * lose track. Khmer stacks a subscript below the baseline, so the two lines are given
 * explicit space rather than relying on line height.
 *
 * "ថ្ងៃនេះ" is shown only when today is off screen, and it is asked of the whole visible span
 * rather than of the anchor date: in the month view the anchor is the 1st while today may be
 * the 20th of the same month, and offering to jump somewhere that would not move is worse
 * than offering nothing.
 */
@Composable
private fun CalendarHeader(
    mode: CalendarViewMode,
    anchor: LocalDate,
    showingToday: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
    onJump: () -> Unit,
    onSearch: () -> Unit,
) {
    val settings = LocalAppSettings.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = Spacing.lg, end = Spacing.xs, top = Spacing.md, bottom = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = headlineOf(mode, anchor),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(Spacing.xs))
            Text(
                text = captionOf(mode, anchor, settings.weekStart),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (!showingToday) {
            TextButton(onClick = onToday) { Text("ថ្ងៃនេះ") }
        }
        if (mode.isDated) {
            IconButton(onClick = onJump) {
                Icon(Icons.Outlined.EditCalendar, contentDescription = "ទៅកាន់កាលបរិច្ឆេទ")
            }
            IconButton(onClick = onPrevious) {
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, contentDescription = "មុន")
            }
            IconButton(onClick = onNext) {
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = "បន្ទាប់")
            }
        } else {
            IconButton(onClick = onSearch) {
                Icon(Icons.Outlined.Search, contentDescription = "ស្វែងរក")
            }
        }
    }
}

/**
 * The four views.
 *
 * A segmented row rather than a menu: with four options a menu hides three of them behind a
 * tap and gives no hint that the calendar can be read any other way, which is the exact
 * problem this screen was written to fix. The tick a selected segment normally draws is
 * removed - it costs width that four Khmer words need, and the fill already says which one is
 * chosen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ViewSwitcher(mode: CalendarViewMode, onSelect: (CalendarViewMode) -> Unit) {
    SingleChoiceSegmentedButtonRow(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg, vertical = Spacing.xs),
    ) {
        CalendarViewMode.entries.forEachIndexed { index, entry ->
            SegmentedButton(
                selected = entry == mode,
                onClick = { onSelect(entry) },
                shape = SegmentedButtonDefaults.itemShape(index, CalendarViewMode.entries.size),
                icon = {},
                label = {
                    Text(
                        text = entry.labelKm,
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
            )
        }
    }
}

/** The loud line: the month, the week's span, the day, or the agenda's name. */
@Composable
private fun headlineOf(mode: CalendarViewMode, anchor: LocalDate): String = when (mode) {
    CalendarViewMode.MONTH -> KhmerTerms.solarMonth(anchor.monthValue)

    CalendarViewMode.WEEK -> {
        val (from, to) = CalendarNavigation.span(mode, anchor, LocalAppSettings.current.weekStart)
        val fromDay = localeNumber(from.dayOfMonth)
        val toDay = localeNumber(to.dayOfMonth)
        // A week that straddles two months has to name both, or the span reads as running
        // backwards - "២៩ – ៤" is not a week anybody can place.
        if (from.month == to.month) {
            "$fromDay – $toDay ${KhmerTerms.solarMonth(to.monthValue)}"
        } else {
            "$fromDay ${KhmerTerms.solarMonth(from.monthValue)} – $toDay ${KhmerTerms.solarMonth(to.monthValue)}"
        }
    }

    CalendarViewMode.DAY ->
        "${localeNumber(anchor.dayOfMonth)} ${KhmerTerms.solarMonth(anchor.monthValue)}"

    CalendarViewMode.AGENDA -> "កាលវិភាគ"
}

/** The quiet line: the year, and whatever else places you. */
@Composable
private fun captionOf(mode: CalendarViewMode, anchor: LocalDate, weekStart: DayOfWeek): String =
    when (mode) {
        CalendarViewMode.MONTH -> "ឆ្នាំ" + localeNumber(anchor.year)

        CalendarViewMode.WEEK ->
            "សប្តាហ៍ទី" + localeNumber(CalendarWeek.weekOfYear(anchor, weekStart)) +
                " · ឆ្នាំ" + localeNumber(anchor.year)

        CalendarViewMode.DAY ->
            KhmerTerms.dayOfWeek(anchor.dayOfWeek) + " · ឆ្នាំ" + localeNumber(anchor.year)

        CalendarViewMode.AGENDA -> "ព្រឹត្តិការណ៍ខាងមុខ"
    }
