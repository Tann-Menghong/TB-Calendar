package com.khmercalendar.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Celebration
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.khmercalendar.core.khmer.KhmerTerms
import com.khmercalendar.ui.components.CalendarFormats
import com.khmercalendar.ui.components.HeroCard
import com.khmercalendar.ui.components.LocalUses24Hour
import com.khmercalendar.ui.components.StatusBadge
import com.khmercalendar.ui.components.localeNumber
import com.khmercalendar.ui.theme.AppType
import com.khmercalendar.ui.theme.IconSize
import com.khmercalendar.ui.theme.LocalAppSettings
import com.khmercalendar.ui.theme.Spacing
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * The top of the dashboard: what day it is, in all three calendars, and what time it is now.
 *
 * ## What belongs here
 *
 * This card answers the two questions a calendar exists to answer - *what day is it* and
 * *what is next* - so it carries the Gregorian date, the weekday, the Khmer lunar date and a
 * live clock together, rather than scattering them across three cards the user has to
 * assemble mentally. The lunar date in particular used to sit in its own card three positions
 * down the scroll, which is the wrong place for the thing that makes this a Khmer calendar.
 *
 * ## Why a second gradient here is a quiet one
 *
 * The work card's gradient is doing a job - green means working, amber means a break - so it
 * is saturated on purpose. If this card were saturated too, the dashboard would open with two
 * competing blocks of colour and neither would read as a signal. So the hero takes a low-alpha
 * wash of the user's own accent instead: enough to separate it from the cards below, not
 * enough to compete with the one card that is actually reporting a state.
 */
@Composable
fun TodayHeroCard(
    date: LocalDate,
    eventsToday: Int,
    nextEventAt: LocalTime?,
    nextEventTitle: String?,
    onOpenDay: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    lunarText: String? = null,
    holidayName: String? = null,
) {
    val settings = LocalAppSettings.current
    val uses24Hour = LocalUses24Hour.current
    val now = rememberCurrentMinute()
    val scheme = MaterialTheme.colorScheme

    HeroCard(modifier = modifier, onClick = { onOpenDay(date) }) {
        // Greeting and clock share the top line. The clock sat on the date row until a
        // device check showed the floating action button covering it - the FAB never
        // reaches this high, and a header line of "greeting … time" reads better anyway.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = KhmerTerms.greeting(now.hour),
                style = MaterialTheme.typography.titleSmall,
                color = scheme.primary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = CalendarFormats.time(
                    now.toLocalTime(),
                    uses24Hour,
                    settings.useKhmerNumerals,
                ),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = scheme.onSurface,
                maxLines = 1,
            )
        }

        Spacer(Modifier.height(Spacing.md))

        // The day number is given its own line box rather than being aligned to the bottom of
        // the text beside it. Khmer numerals carry marks above and below the baseline, and
        // bottom-aligning a 34sp glyph against 17sp text clipped the tail of ៨ and ៩.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = localeNumber(date.dayOfMonth),
                style = AppType.metric(),
                color = scheme.onSurface,
            )
            Spacer(Modifier.width(Spacing.md))
            Column {
                Text(
                    text = "${KhmerTerms.solarMonth(date.monthValue)} ${localeNumber(date.year)}",
                    style = MaterialTheme.typography.titleMedium,
                    color = scheme.onSurface,
                    maxLines = 1,
                )
                Text(
                    text = "ថ្ងៃ${KhmerTerms.dayOfWeek(date.dayOfWeek)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }

        // The lunar date, on the card that establishes what day it is rather than in a card
        // of its own further down. Wraps to two lines on a small screen rather than being
        // cut off, which is what it did at the edge of its old card.
        lunarText?.let { text ->
            Spacer(Modifier.height(Spacing.md))
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        holidayName?.let { name ->
            Spacer(Modifier.height(Spacing.md))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.Celebration,
                    contentDescription = null,
                    tint = scheme.tertiary,
                    modifier = Modifier.size(IconSize.inline),
                )
                Spacer(Modifier.width(Spacing.sm))
                StatusBadge(text = name, color = scheme.tertiary)
            }
        }

        Spacer(Modifier.height(Spacing.md))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Icon(
                Icons.Outlined.Schedule,
                contentDescription = null,
                tint = scheme.onSurfaceVariant,
                modifier = Modifier.size(IconSize.inline),
            )
            Text(
                text = summaryLine(
                    eventsToday = eventsToday,
                    nextEventAt = nextEventAt,
                    nextEventTitle = nextEventTitle,
                    now = now.toLocalTime(),
                    uses24Hour = uses24Hour,
                    khmerNumerals = settings.useKhmerNumerals,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * What today looks like, in one line.
 *
 * Prefers the *next* event over a count, because "in 40 minutes" is the thing the user opened
 * the app to find out; the count is what is left to say when nothing is coming.
 */
private fun summaryLine(
    eventsToday: Int,
    nextEventAt: LocalTime?,
    nextEventTitle: String?,
    now: LocalTime,
    uses24Hour: Boolean,
    khmerNumerals: Boolean,
): String {
    if (nextEventAt != null && nextEventTitle != null) {
        val minutes = Duration.between(now, nextEventAt).toMinutes()
        val relative = when {
            minutes <= 0L -> "ឥឡូវនេះ"
            minutes < 60L -> "ក្នុងរយៈពេល ${localeNumber(minutes, khmerNumerals)} នាទី"
            minutes < 180L -> {
                val hours = minutes / 60
                val rest = minutes % 60
                if (rest == 0L) {
                    "ក្នុងរយៈពេល ${localeNumber(hours, khmerNumerals)} ម៉ោង"
                } else {
                    "ក្នុងរយៈពេល ${localeNumber(hours, khmerNumerals)} ម៉ោង " +
                        "${localeNumber(rest, khmerNumerals)} នាទី"
                }
            }
            else -> CalendarFormats.time(nextEventAt, uses24Hour, khmerNumerals)
        }
        return "$nextEventTitle · $relative"
    }
    if (eventsToday > 0) {
        return "ព្រឹត្តិការណ៍ ${localeNumber(eventsToday, khmerNumerals)} ថ្ងៃនេះ"
    }
    return "គ្មានព្រឹត្តិការណ៍ថ្ងៃនេះ"
}

/**
 * The current time, refreshed on the minute.
 *
 * Aligned to the minute boundary rather than ticking every sixty seconds from whenever the
 * card happened to compose, so the displayed minute changes when the clock does. Bound to
 * [Lifecycle.State.STARTED] so nothing runs while the app is in the background.
 */
@Composable
private fun rememberCurrentMinute(): LocalDateTime {
    val owner = LocalLifecycleOwner.current
    val state by produceState(initialValue = LocalDateTime.now(), owner) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                val now = LocalDateTime.now()
                value = now
                delay(1_000L * (60 - now.second) - now.nano / 1_000_000L)
            }
        }
    }
    return state
}
