package com.khmercalendar.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Celebration
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.khmercalendar.domain.DayGreeting
import com.khmercalendar.ui.components.AppMotion
import com.khmercalendar.ui.components.CalendarFormats
import com.khmercalendar.ui.components.LocalUses24Hour
import com.khmercalendar.ui.components.localeNumber
import com.khmercalendar.ui.theme.AccentGradients
import com.khmercalendar.ui.theme.AppType
import com.khmercalendar.ui.theme.CardAccent
import com.khmercalendar.ui.theme.IconSize
import com.khmercalendar.ui.theme.LocalAppSettings
import com.khmercalendar.ui.theme.Motion
import com.khmercalendar.ui.theme.Radius
import com.khmercalendar.ui.theme.Spacing
import com.khmercalendar.ui.theme.Stroke
import com.khmercalendar.ui.theme.color
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * The live header, and the day itself.
 *
 * ## Why the toolbar is gone
 *
 * The dashboard used to open with a Material top app bar carrying the app's name and two
 * icons - a fixed 64dp of screen saying "ប្រតិទិនខ្មែរ" to someone who has just tapped the
 * ប្រតិទិនខ្មែរ icon. On the screen whose whole job is to answer *what is happening now*, that
 * is the least useful row available. It is replaced by a status strip that spends the same
 * height on the date, a running clock and the app's offline state.
 *
 * ## Compact
 *
 * The header does not scroll away, because the time and the way back to search are wanted at
 * any depth of the dashboard. What it does instead is shed the two lines that are only worth
 * reading at the top - the date, which the hero card repeats, and the offline badge, which is
 * a statement rather than a live value - so the strip costs about half its height once the
 * user has started reading. The greeting, the clock and the two controls never move.
 *
 * @param compact set by the screen from its scroll position.
 */
@Composable
fun DashboardHeader(
    date: LocalDate,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val settings = LocalAppSettings.current
    val uses24Hour = LocalUses24Hour.current
    val now = rememberCurrentSecond()
    val scheme = MaterialTheme.colorScheme

    val verticalPadding by animateDpAsState(
        targetValue = if (compact) Spacing.sm else Spacing.md,
        animationSpec = AppMotion.tweenOf(Motion.MEDIUM),
        label = "headerPadding",
    )

    Column(modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = verticalPadding)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                // The greeting rather than the app's own name. Someone who has just tapped
                // the ប្រតិទិនខ្មែរ icon does not need to be told which app opened.
                Text(
                    text = DayGreeting.of(now.toLocalTime(), settings.displayName),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = scheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                AnimatedVisibility(
                    visible = !compact,
                    enter = fadeIn(AppMotion.finiteTweenOf(Motion.SMALL)) +
                        expandVertically(AppMotion.finiteTweenOf(Motion.SMALL)),
                    exit = fadeOut(AppMotion.finiteTweenOf(Motion.MICRO)) +
                        shrinkVertically(AppMotion.finiteTweenOf(Motion.MICRO)),
                ) {
                    Column {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "ថ្ងៃ${KhmerTerms.dayOfWeek(date.dayOfWeek)} · " +
                                "${localeNumber(date.dayOfMonth)} " +
                                KhmerTerms.solarMonth(date.monthValue),
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            // A running clock with seconds. This is the one place in the app that ticks at
            // that rate, and it is confined to a single Text so the second does not
            // recompose the dashboard behind it.
            Text(
                text = CalendarFormats.time(
                    now.toLocalTime(),
                    uses24Hour,
                    settings.useKhmerNumerals,
                ),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontFeatureSettings = "tnum",
                ),
                fontWeight = FontWeight.SemiBold,
                color = scheme.onSurface,
                maxLines = 1,
            )
            IconButton(onClick = onSearch) {
                Icon(Icons.Outlined.Search, contentDescription = "ស្វែងរក")
            }
            IconButton(onClick = onSettings) {
                Icon(Icons.Outlined.Settings, contentDescription = "ការកំណត់")
            }
        }
        AnimatedVisibility(
            visible = !compact,
            enter = fadeIn(AppMotion.finiteTweenOf(Motion.SMALL)) +
                expandVertically(AppMotion.finiteTweenOf(Motion.SMALL)),
            exit = fadeOut(AppMotion.finiteTweenOf(Motion.MICRO)) +
                shrinkVertically(AppMotion.finiteTweenOf(Motion.MICRO)),
        ) {
            Column {
                Spacer(Modifier.height(Spacing.sm))
                OfflineChip()
            }
        }
    }
}

/**
 * The offline badge.
 *
 * Static on purpose. It is not reporting a connection state that might change - it is stating
 * the app's design: the calendar has never needed a network and never will. Saying so on the
 * first screen is the clearest privacy claim the app can make, and it costs one line.
 */
@Composable
private fun OfflineChip() {
    val accent = MaterialTheme.colorScheme.primary
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(accent))
        Spacer(Modifier.width(Spacing.sm))
        Text(
            text = "OFFLINE · ទិន្នន័យនៅក្នុងឧបករណ៍",
            style = AppType.techLabel(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * The day, set as a headline rather than as a row of fields.
 *
 * The old version was a tidy little card: greeting, clock, date, lunar line, summary, all at
 * roughly one weight. Tidy is the problem - nothing in it was the answer, so the eye had to
 * read all of it. Here the day of the month is set at display size against the month and the
 * weekday, and everything else sits underneath it as supporting text. You can read the date
 * from across a room, which is what a calendar's front page is for.
 */
@Composable
fun TodayHeroCard(
    date: LocalDate,
    eventsToday: Int,
    onOpenDay: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    lunarText: String? = null,
    holidayName: String? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(Radius.lg)

    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.verticalGradient(AccentGradients.heroWash()))
            .border(Stroke.hairline, scheme.outlineVariant, shape)
            .clickable { onOpenDay(date) }
            .padding(Spacing.xl),
    ) {
        ModuleLabel("TODAY", color = scheme.primary)
        Spacer(Modifier.height(Spacing.md))

        // The asymmetric part: one very large number, and the words that qualify it stacked
        // beside it at a fraction of the size.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = localeNumber(date.dayOfMonth),
                style = MaterialTheme.typography.displaySmall.copy(fontFeatureSettings = "tnum"),
                fontWeight = FontWeight.Bold,
                color = scheme.onSurface,
            )
            Spacer(Modifier.width(Spacing.lg))
            Column {
                Text(
                    text = KhmerTerms.solarMonth(date.monthValue),
                    style = MaterialTheme.typography.titleLarge,
                    color = scheme.onSurface,
                    maxLines = 1,
                )
                Text(
                    text = "ថ្ងៃ${KhmerTerms.dayOfWeek(date.dayOfWeek)} · ${localeNumber(date.year)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }

        lunarText?.let { text ->
            Spacer(Modifier.height(Spacing.lg))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(Stroke.hairline)
                    .background(scheme.outlineVariant),
            )
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
                    tint = CardAccent.HOLIDAY.color(),
                    modifier = Modifier.size(IconSize.inline),
                )
                Spacer(Modifier.width(Spacing.sm))
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = CardAccent.HOLIDAY.color(),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        if (eventsToday > 0) {
            Spacer(Modifier.height(Spacing.md))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(
                    text = localeNumber(eventsToday),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = scheme.primary,
                )
                Text(
                    text = "ព្រឹត្តិការណ៍ថ្ងៃនេះ",
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * The current time, refreshed on the second.
 *
 * Confined to the header. A second-by-second value read anywhere wider would recompose that
 * width of the dashboard sixty times a minute for a number nobody is watching that closely;
 * here it is one line of text. Bound to [Lifecycle.State.STARTED], so it stops when the app
 * does.
 */
/**
 * The current minute, for everything on the dashboard that is "now" but not a clock face.
 *
 * The modules were reading `LocalTime.now()` inside composition, which is not a state: the
 * value was captured when the dashboard was first drawn and never changed again, so the
 * "starting soon" badge and the timeline's now-marker were both frozen at whatever time the
 * screen happened to open. This ticks once a minute, which is the resolution any of them
 * shows, and stops with the lifecycle so a backgrounded app is not waking up to do nothing.
 */
@Composable
internal fun rememberCurrentMinute(): LocalDateTime {
    val owner = LocalLifecycleOwner.current
    val state by produceState(initialValue = LocalDateTime.now(), owner) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                val now = LocalDateTime.now()
                value = now
                // Land on the next whole minute rather than drifting a second later each time.
                delay(60_000L - (now.second * 1_000L + now.nano / 1_000_000L))
            }
        }
    }
    return state
}

@Composable
private fun rememberCurrentSecond(): LocalDateTime {
    val owner = LocalLifecycleOwner.current
    val state by produceState(initialValue = LocalDateTime.now(), owner) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                val now = LocalDateTime.now()
                value = now
                delay(1_000L - now.nano / 1_000_000L)
            }
        }
    }
    return state
}
