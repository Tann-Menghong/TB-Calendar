package com.khmercalendar.ui.calendar

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.EventNote
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.khmercalendar.core.khmer.KhmerTerms
import com.khmercalendar.ui.components.EmptyState
import com.khmercalendar.ui.components.localeNumber
import java.time.LocalDate
import com.khmercalendar.ui.components.localeTime
import com.khmercalendar.ui.components.localeTimeRange
import com.khmercalendar.ui.theme.LocalAppSettings

/** Seven day columns as stacked rows, which reads better one-handed than a grid of columns. */
@Composable
fun WeekScreen(
    viewModel: CalendarViewModel,
    onOpenEvent: (Long, LocalDate) -> Unit,
    onAddOn: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val week by viewModel.weekState.collectAsStateWithLifecycle()
    val today = LocalDate.now()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 12.dp, end = 12.dp, top = 8.dp, bottom = 96.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(week, key = { it.first.toEpochDay() }) { (date, events) ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = KhmerTerms.dayOfWeek(date.dayOfWeek),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = if (date == today) FontWeight.Bold else FontWeight.Medium,
                        color = if (date == today) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "${localeNumber(date.dayOfMonth)} ${KhmerTerms.solarMonth(date.monthValue)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = "+",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onAddOn(date) }
                            .padding(horizontal = 10.dp, vertical = 2.dp),
                    )
                }
                if (events.isEmpty()) {
                    Text(
                        text = "គ្មានព្រឹត្តិការណ៍",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                } else {
                    Spacer(Modifier.height(4.dp))
                    events.forEach { event ->
                        EventRow(event) { onOpenEvent(event.eventId, event.occurrenceDate) }
                    }
                }
            }
        }
    }
}

/**
 * A single day laid out against an hour ruler.
 *
 * The ruler covers the user's own waking hours from settings rather than a full 24, because
 * showing the small hours means two thirds of the screen is always empty. It is then
 * *stretched to cover every event on the day*, which is what keeps the compromise honest: a
 * fixed 06:00-22:00 window used to hide a timed event outside it completely - the event was
 * in the month grid, the agenda, the dashboard, search and the widgets, and simply absent
 * here. That became easy to hit once a new event added late in the evening correctly starts
 * at midnight.
 */
@Composable
fun DayScreen(
    viewModel: CalendarViewModel,
    onOpenEvent: (Long, LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val date by viewModel.selectedDate.collectAsStateWithLifecycle()
    val events by viewModel.selectedDayEvents.collectAsStateWithLifecycle()
    val note by viewModel.selectedDayNote.collectAsStateWithLifecycle()

    val allDay = events.filter { it.allDay }
    val timed = events.filterNot { it.allDay }
    val settings = LocalAppSettings.current

    // The preferred window, widened to whatever the day actually holds.
    val hours = remember(timed, settings.dayStartHour, settings.dayEndHour) {
        val starts = timed.map { it.start.hour }
        val first = (starts + settings.dayStartHour).min().coerceIn(0, 23)
        val last = (starts + settings.dayEndHour).max().coerceIn(first, 23)
        (first..last).toList()
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 96.dp),
    ) {
        item {
            DayHeadline(date, Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }

        note?.takeIf { it.isNotBlank() }?.let { text ->
            item {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                    Text("កំណត់ចំណាំ", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(text, style = MaterialTheme.typography.bodyMedium)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }

        if (allDay.isNotEmpty()) {
            items(allDay, key = { "allday-${it.eventId}" }) { event ->
                Box(Modifier.padding(horizontal = 12.dp, vertical = 3.dp)) {
                    EventRow(event) { onOpenEvent(event.eventId, event.occurrenceDate) }
                }
            }
            item { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant) }
        }

        if (events.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.AutoMirrored.Filled.EventNote,
                    title = "ថ្ងៃទំនេរ",
                    message = "គ្មានព្រឹត្តិការណ៍សម្រាប់ថ្ងៃនេះទេ",
                )
            }
        }

        items(hours) { hour ->
            val inHour = timed.filter { it.start.hour == hour }
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                Text(
                    text = localeNumber("%02d:00".format(hour)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(48.dp).padding(top = 8.dp),
                )
                Column(Modifier.weight(1f).padding(vertical = 2.dp)) {
                    if (inHour.isEmpty()) {
                        Box(Modifier.fillMaxWidth().height(40.dp)) {
                            HorizontalDivider(
                                Modifier.align(Alignment.TopStart),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                            )
                        }
                    } else {
                        inHour.forEach { event ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(event.colorArgb).copy(alpha = 0.16f))
                                    .clickable { onOpenEvent(event.eventId, event.occurrenceDate) }
                                    .padding(8.dp),
                            ) {
                                Column {
                                    Text(
                                        event.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        localeTimeRange(
                                            event.start.toLocalTime(),
                                            event.end.toLocalTime(),
                                            LocalAppSettings.current,
                                        ),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

