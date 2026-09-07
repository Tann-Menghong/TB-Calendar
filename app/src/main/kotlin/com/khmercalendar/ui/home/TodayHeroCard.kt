package com.khmercalendar.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.khmercalendar.core.khmer.KhmerTerms
import com.khmercalendar.ui.components.CalendarFormats
import com.khmercalendar.ui.components.LocalUses24Hour
import com.khmercalendar.ui.components.localeNumber
import com.khmercalendar.ui.theme.LocalAppSettings
import com.khmercalendar.ui.theme.Radius
import com.khmercalendar.ui.theme.Spacing
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * The top of the dashboard: who is being greeted, what day it is, and what time it is now.
 *
 * ## Why a second gradient here is a quiet one
 *
 * The work card's gradient is doing a job - green means working, amber means a break - so it
 * is saturated on purpose. If this card were saturated too, the dashboard would open with two
 * competing blocks of colour and neither would read as a signal. So the hero takes a low-alpha
 * wash of the user's own accent instead: enough to separate it from the cards below, not
 * enough to compete with the one card that is actually reporting a state.
 *
 * The wash is derived from the colour scheme rather than declared in `Gradients` because it
 * follows whatever accent the user picked, which a fixed pair of hex values cannot.
 */
@Composable
fun TodayHeroCard(
    date: LocalDate,
    eventsToday: Int,
    nextEventAt: LocalTime?,
    nextEventTitle: String?,
    onOpenDay: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val settings = LocalAppSettings.current
    val uses24Hour = LocalUses24Hour.current
    val now = rememberCurrentMinute()
    val scheme = MaterialTheme.colorScheme

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.md))
            .background(
                Brush.linearGradient(
                    listOf(
                        scheme.primary.copy(alpha = 0.14f).compositeOver(scheme.surface),
                        scheme.surface,
                    ),
                ),
            )
            .clickable { onOpenDay(date) },
    ) {
        Column(Modifier.padding(Spacing.lg).fillMaxWidth()) {
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

            Spacer(Modifier.height(Spacing.sm))

            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = localeNumber(date.dayOfMonth),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.width(Spacing.sm))
                Column(Modifier.padding(bottom = 5.dp)) {
                    Text(
                        text = "${KhmerTerms.solarMonth(date.monthValue)} ${localeNumber(date.year)}",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = "ថ្ងៃ${KhmerTerms.dayOfWeek(date.dayOfWeek)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(Spacing.md))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                Icon(
                    Icons.Outlined.Schedule,
                    contentDescription = null,
                    tint = scheme.onSurfaceVariant,
                    modifier = Modifier.height(16.dp).width(16.dp),
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
                )
            }
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
    if (nextEventAt != null && nextEventAt.isAfter(now)) {
        val until = Duration.between(now, nextEventAt)
        val name = nextEventTitle?.takeIf { it.isNotBlank() }
        val whenText = relative(until, khmerNumerals)
            ?: CalendarFormats.time(nextEventAt, uses24Hour, khmerNumerals)
        return if (name != null) "$name · $whenText" else whenText
    }
    return if (eventsToday == 0) {
        "គ្មានព្រឹត្តិការណ៍ថ្ងៃនេះ"
    } else {
        "ព្រឹត្តិការណ៍ ${localeNumber(eventsToday, khmerNumerals)} ថ្ងៃនេះ"
    }
}

/** "ក្នុងរយៈពេល ៤០ នាទី" while that is more useful than a clock time; null beyond a few hours. */
private fun relative(until: Duration, khmerNumerals: Boolean): String? {
    val minutes = until.toMinutes()
    return when {
        minutes < 1 -> "ឥឡូវនេះ"
        minutes < 60 -> "ក្នុងរយៈពេល ${localeNumber(minutes, khmerNumerals)} នាទី"
        minutes < 300 -> "ក្នុងរយៈពេល ${localeNumber(minutes / 60, khmerNumerals)} ម៉ោង"
        else -> null
    }
}

/**
 * The current time, refreshed on the minute.
 *
 * Aligned to the minute boundary rather than every sixty seconds from an arbitrary start, so
 * the displayed time changes when the clock does instead of up to a minute late - and the
 * process wakes once a minute rather than once a second, which is what separates this from the
 * countdown card. It stops entirely while the dashboard is off screen.
 */
@Composable
private fun rememberCurrentMinute(): LocalDateTime {
    val lifecycleOwner = LocalLifecycleOwner.current
    val value by produceState(initialValue = LocalDateTime.now(), lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                val now = LocalDateTime.now()
                value = now
                delay(1_000L * (60 - now.second) - now.nano / 1_000_000L)
            }
        }
    }
    return value
}

/** Flattens a translucent colour onto an opaque one; `Color.compositeOver` needs both opaque. */
private fun androidx.compose.ui.graphics.Color.compositeOver(
    background: androidx.compose.ui.graphics.Color,
): androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color(
    red = red * alpha + background.red * (1 - alpha),
    green = green * alpha + background.green * (1 - alpha),
    blue = blue * alpha + background.blue * (1 - alpha),
    alpha = 1f,
)
