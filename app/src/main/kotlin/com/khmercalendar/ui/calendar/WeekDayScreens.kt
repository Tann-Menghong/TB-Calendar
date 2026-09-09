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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.EventNote
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import com.khmercalendar.core.schedule.TimeSpan
import com.khmercalendar.core.schedule.FreeTime
import com.khmercalendar.domain.DayConflicts
import com.khmercalendar.ui.components.EmptyState
import com.khmercalendar.ui.components.localeNumber
import java.time.LocalDate
import java.time.LocalTime
import com.khmercalendar.ui.components.localeTimeRange
import com.khmercalendar.ui.theme.LocalAppSettings
import com.khmercalendar.ui.home.rememberCurrentMinute

/**
 * Seven day columns as stacked rows, which reads better one-handed than a grid of columns.
 *
 * Each day's heading opens that day, so the week is a way *into* the day view rather than a
 * dead end you have to back out of. That only became worth wiring up once the two views sat
 * behind one switcher.
 */
@Composable
fun WeekScreen(
    viewModel: CalendarViewModel,
    onOpenEvent: (Long, LocalDate) -> Unit,
    onAddOn: (LocalDate) -> Unit,
    onOpenDay: (LocalDate) -> Unit,
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
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onOpenDay(date) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
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

    // Double-booking, marked where it happens. The assistant could already answer "find my
    // conflicts" when asked; a clash matters on the day it falls on, not when you think to
    // ask about it.
    val clashing = remember(timed) { DayConflicts.overlapping(timed) }

    // When you are free, from the same subtraction the assistant uses - but computed here,
    // because this answer must not require the AI module to be switched on. Bounded to the
    // user's own day, and on today it will not offer a morning that has already gone.
    val now = rememberCurrentMinute()
    val free = remember(timed, date, settings.dayStartHour, settings.dayEndHour, now.toLocalDate()) {
        FreeTime.onDay(
            busy = timed.map { TimeSpan(it.start, it.end) },
            date = date,
            dayStart = LocalTime.of(settings.dayStartHour, 0),
            dayEnd = LocalTime.of(settings.dayEndHour, 0),
            minimumMinutes = MIN_FREE_MINUTES,
            notBefore = now,
        )
    }

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
            if (clashing.isNotEmpty()) ConflictBanner(clashing.size)
            // Only where it is useful: on an empty day "you are free 08:00-18:00" is the
            // same information the empty state already gives, in more words.
            if (free.isNotEmpty() && timed.isNotEmpty()) FreeTimeRow(free)
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
                                    // Icon and word, never colour alone: the row is already
                                    // tinted with the event's own colour, which the user
                                    // chose and which carries no meaning about clashes.
                                    if (event.eventId in clashing) ConflictTag()
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}


/**
 * The day has a double-booking.
 *
 * Counted in events rather than in pairs: "three events clash" is what you can act on, and
 * "two conflicts" for the same three events is a number nobody can map back onto the screen.
 */
@Composable
private fun ConflictBanner(count: Int) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 10.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Outlined.WarningAmber,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "ព្រឹត្តិការណ៍ ${localeNumber(count)} ត្រួតគ្នា",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onErrorContainer,
        )
    }
}

/** The same warning on the row it belongs to. */
@Composable
private fun ConflictTag() {
    Row(
        modifier = Modifier.padding(top = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Outlined.WarningAmber,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(13.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = "ត្រួតគ្នា",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

/**
 * When you are free.
 *
 * Two slots at most. The point is to answer "can I fit this in", which the first couple of
 * gaps do; listing every twenty-minute hole between meetings turns an answer back into the
 * timetable you were already looking at.
 */
@Composable
private fun FreeTimeRow(slots: List<TimeSpan>) {
    val settings = LocalAppSettings.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = "ទំនេរ",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(48.dp).padding(top = 2.dp),
        )
        Spacer(Modifier.width(8.dp))
        Column {
            slots.take(2).forEach { slot ->
                Text(
                    text = localeTimeRange(
                        slot.start.toLocalTime(),
                        slot.end.toLocalTime(),
                        settings,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (slots.size > 2) {
                Text(
                    text = "និង ${localeNumber(slots.size - 2)} ចន្លោះទៀត",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Shorter than this is a gap between meetings rather than time you could use. */
private const val MIN_FREE_MINUTES = 30L
