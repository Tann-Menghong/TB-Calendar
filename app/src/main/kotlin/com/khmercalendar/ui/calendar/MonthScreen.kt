package com.khmercalendar.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.EventNote
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.khmercalendar.core.khmer.KhmerTerms
import com.khmercalendar.domain.EventOccurrence
import com.khmercalendar.ui.components.ColorDot
import com.khmercalendar.ui.components.EmptyState
import com.khmercalendar.ui.components.localeNumber
import com.khmercalendar.ui.theme.LocalAppSettings
import java.time.DayOfWeek
import java.time.YearMonth

/**
 * The month grid.
 *
 * Paged rather than scrolled: a calendar month is a fixed unit people navigate one at a time,
 * and a pager keeps each grid a stable six rows instead of reflowing as it scrolls. The pager
 * index is an offset from a fixed anchor month, which is what lets it span 1900 to 2199
 * without materialising three hundred years of pages.
 */
@Composable
fun MonthScreen(
    viewModel: CalendarViewModel,
    onOpenDay: (java.time.LocalDate) -> Unit,
    onOpenEvent: (Long, java.time.LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.monthState.collectAsStateWithLifecycle()
    val selectedEvents by viewModel.selectedDayEvents.collectAsStateWithLifecycle()
    val visibleMonth by viewModel.visibleMonth.collectAsStateWithLifecycle()
    val settings = LocalAppSettings.current

    val pagerState = rememberPagerState(
        initialPage = pageOf(visibleMonth),
        pageCount = { TOTAL_PAGES },
    )

    // Keep the pager and the view model in step in both directions: swiping changes the
    // month, and tapping "today" scrolls the pager.
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { page ->
            viewModel.showMonth(monthOf(page))
        }
    }
    LaunchedEffect(visibleMonth) {
        val target = pageOf(visibleMonth)
        if (target != pagerState.currentPage) pagerState.scrollToPage(target)
    }

    Column(modifier.fillMaxSize()) {
        WeekdayHeader(settings.weekStart)

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth(),
            // Only the current page is composed eagerly; a grid is 42 cells and pre-building
            // neighbours on a low-end device costs more than it saves.
            beyondViewportPageCount = 0,
        ) { page ->
            val month = monthOf(page)
            if (month == state.yearMonth) {
                MonthGrid(
                    state = state,
                    onSelect = viewModel::select,
                    onOpenDay = onOpenDay,
                )
            } else {
                // The neighbouring page during a swipe, before its data arrives.
                Box(Modifier.fillMaxWidth().height(settings.density.cellHeightDp.dp * 6))
            }
        }

        state.outOfRangeMessage?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                textAlign = TextAlign.Center,
            )
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        SelectedDayPanel(
            date = state.selected,
            events = selectedEvents,
            onOpenEvent = onOpenEvent,
            onOpenDay = onOpenDay,
        )
    }
}

@Composable
private fun WeekdayHeader(weekStart: DayOfWeek) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp)) {
        CalendarViewModel.weekDays(weekStart).forEach { day ->
            val weekend = day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY
            Text(
                text = KhmerTerms.dayOfWeekShort(day),
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelMedium,
                color = if (weekend) {
                    MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

@Composable
private fun MonthGrid(
    state: MonthState,
    onSelect: (java.time.LocalDate) -> Unit,
    onOpenDay: (java.time.LocalDate) -> Unit,
) {
    val settings = LocalAppSettings.current
    Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
        state.weeks.forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { cell ->
                    DayCell(
                        cell = cell,
                        isSelected = cell.date == state.selected,
                        height = settings.density.cellHeightDp,
                        modifier = Modifier.weight(1f),
                        onClick = { onSelect(cell.date) },
                        onDoubleClick = { onOpenDay(cell.date) },
                    )
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    cell: DayCellState,
    isSelected: Boolean,
    height: Int,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onDoubleClick: () -> Unit,
) {
    val settings = LocalAppSettings.current
    val scheme = MaterialTheme.colorScheme
    val weekend = cell.date.dayOfWeek == DayOfWeek.SATURDAY || cell.date.dayOfWeek == DayOfWeek.SUNDAY

    val dayColor = when {
        !cell.inCurrentMonth -> scheme.onSurfaceVariant.copy(alpha = 0.38f)
        cell.isPublicHoliday -> scheme.error
        weekend && settings.highlightWeekends -> scheme.error.copy(alpha = 0.85f)
        else -> scheme.onSurface
    }

    Box(
        modifier = modifier
            .height(height.dp)
            .padding(1.5.dp)
            .clip(RoundedCornerShape(10.dp))
            .then(
                if (isSelected) {
                    Modifier.background(scheme.primary.copy(alpha = 0.14f))
                        .border(1.dp, scheme.primary, RoundedCornerShape(10.dp))
                } else {
                    Modifier
                }
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(top = 3.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Today gets a filled disc rather than a colour change: on a grid this dense a
            // recoloured numeral is easy to miss, and holidays already use colour.
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .then(if (cell.isToday) Modifier.background(scheme.primary) else Modifier),
                contentAlignment = Alignment.Center,
            ) {
                if (settings.showGregorianDates) {
                    Text(
                        text = localeNumber(cell.date.dayOfMonth),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (cell.isToday) FontWeight.Bold else FontWeight.Medium,
                        color = if (cell.isToday) scheme.onPrimary else dayColor,
                    )
                }
            }

            if (settings.showKhmerLunarDates && cell.lunar != null) {
                Text(
                    text = cell.lunar.dayText,
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant.copy(alpha = if (cell.inCurrentMonth) 0.85f else 0.35f),
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                )
            }

            Spacer(Modifier.weight(1f))

            if (settings.showEventDots && (cell.events.isNotEmpty() || cell.hasNote)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.padding(bottom = 4.dp),
                ) {
                    cell.events.take(MAX_DOTS).forEach { ColorDot(Color(it.colorArgb), size = 5) }
                    if (cell.events.size > MAX_DOTS) {
                        ColorDot(scheme.onSurfaceVariant.copy(alpha = 0.5f), size = 5)
                    }
                    if (cell.hasNote && cell.events.isEmpty()) {
                        ColorDot(scheme.outline, size = 5)
                    }
                }
            }
        }
    }
}

@Composable
private fun SelectedDayPanel(
    date: java.time.LocalDate,
    events: List<EventOccurrence>,
    onOpenEvent: (Long, java.time.LocalDate) -> Unit,
    onOpenDay: (java.time.LocalDate) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        DayHeadline(date, Modifier.padding(horizontal = 16.dp, vertical = 10.dp).clickable { onOpenDay(date) })
        if (events.isEmpty()) {
            EmptyState(
                icon = Icons.AutoMirrored.Filled.EventNote,
                title = "គ្មានព្រឹត្តិការណ៍",
                message = "ចុចប៊ូតុង + ដើម្បីបន្ថែមព្រឹត្តិការណ៍ថ្មី",
            )
        } else {
            LazyColumn(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 12.dp, end = 12.dp, bottom = 96.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(events, key = { "${it.eventId}-${it.occurrenceDate}" }) { event ->
                    EventRow(event) { onOpenEvent(event.eventId, event.occurrenceDate) }
                }
            }
        }
    }
}

/** The Khmer and Gregorian headline for a day, used on several screens. */
@Composable
fun DayHeadline(date: java.time.LocalDate, modifier: Modifier = Modifier) {
    val settings = LocalAppSettings.current
    val lunar = remember(date, settings.showKhmerLunarDates) {
        if (settings.showKhmerLunarDates) com.khmercalendar.core.khmer.Chhankitek.toLunarOrNull(date) else null
    }
    val holidays = remember(date, settings.showHolidays) {
        if (settings.showHolidays) com.khmercalendar.core.holiday.KhmerHolidays.on(date) else emptyList()
    }
    Column(modifier) {
        Text(
            text = "ថ្ងៃ${KhmerTerms.dayOfWeek(date.dayOfWeek)} ទី${localeNumber(date.dayOfMonth)} " +
                "ខែ${KhmerTerms.solarMonth(date.monthValue)} ឆ្នាំ${localeNumber(date.year)}",
            style = MaterialTheme.typography.titleMedium,
        )
        lunar?.let {
            Text(
                text = it.format(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        holidays.forEach { holiday ->
            Text(
                text = holiday.nameKm,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

/** One event in a list: colour bar, time, title. */
@Composable
fun EventRow(
    event: EventOccurrence,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .width(4.dp)
                .height(34.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color(event.colorArgb)),
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = event.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textDecoration = if (event.isCompleted) {
                    androidx.compose.ui.text.style.TextDecoration.LineThrough
                } else {
                    null
                },
            )
            val time = if (event.allDay) {
                "ពេញមួយថ្ងៃ"
            } else {
                localeNumber("%02d:%02d".format(event.start.hour, event.start.minute)) + " – " +
                    localeNumber("%02d:%02d".format(event.end.hour, event.end.minute))
            }
            Text(
                text = listOfNotNull(time, event.location?.takeIf { it.isNotBlank() }).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private const val MAX_DOTS = 3

/**
 * Pager pages are offsets from January 1900, the first month the lunar engine can convert.
 */
private val ANCHOR: YearMonth = YearMonth.of(1900, 1)
private const val TOTAL_PAGES = 300 * 12

private fun pageOf(month: YearMonth): Int =
    (((month.year - ANCHOR.year) * 12) + (month.monthValue - ANCHOR.monthValue))
        .coerceIn(0, TOTAL_PAGES - 1)

private fun monthOf(page: Int): YearMonth = ANCHOR.plusMonths(page.toLong())
