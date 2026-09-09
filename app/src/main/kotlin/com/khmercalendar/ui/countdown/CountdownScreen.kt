package com.khmercalendar.ui.countdown

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.khmercalendar.core.khmer.KhmerTerms
import com.khmercalendar.domain.CountdownItem
import com.khmercalendar.domain.CountdownStyle
import com.khmercalendar.domain.Countdowns
import com.khmercalendar.ui.components.EmptyState
import com.khmercalendar.ui.components.SurfaceCard
import com.khmercalendar.ui.components.localeNumber
import com.khmercalendar.ui.components.localeWrittenDate
import com.khmercalendar.ui.home.rememberCurrentMinute
import com.khmercalendar.ui.theme.LocalAppSettings
import com.khmercalendar.ui.theme.Spacing
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import com.khmercalendar.data.prefs.SettingsStore
import java.time.LocalDate

/**
 * Everything you are counting down to.
 *
 * ## Why a screen and not only a card
 *
 * The dashboard card shows three, which is right for a dashboard and wrong as the only way
 * to see them: pin a fourth and it vanishes with no indication it still exists. This is where
 * the full list lives, and where the style is chosen - beside the thing it changes, rather
 * than in a settings screen two levels away from any countdown.
 *
 * ## Dates that have arrived
 *
 * A pinned date in the past is not deleted and not silently hidden. It moves to its own
 * section with its pin still on, so the exam you sat is somewhere you can find and unpin
 * rather than something the app quietly disposed of.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CountdownScreen(
    viewModel: CountdownViewModel,
    settingsStore: SettingsStore,
    onOpenEvent: (Long, LocalDate) -> Unit,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val settings = LocalAppSettings.current
    val scope = rememberCoroutineScope()
    val now = rememberCurrentMinute()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("រាប់ថយក្រោយ") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "ត្រឡប់")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            StyleRow(
                selected = settings.countdownStyle,
                onSelect = { scope.launch { settingsStore.setCountdownStyle(it) } },
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            if (state.upcoming.isEmpty() && state.passed.isEmpty()) {
                EmptyState(
                    icon = Icons.Outlined.HourglassEmpty,
                    title = "គ្មានការរាប់ថយក្រោយ",
                    message = "បើកព្រឹត្តិការណ៍មួយ រួចចុចរូបខ្ទាស់ ដើម្បីរាប់ថយក្រោយទៅកាន់ថ្ងៃនោះ",
                )
                return@Column
            }

            LazyColumn(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = Spacing.lg, end = Spacing.lg, top = Spacing.md, bottom = 96.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                items(state.upcoming, key = { "up-${it.eventId}" }) { item ->
                    CountdownCard(
                        item = item,
                        style = settings.countdownStyle,
                        khmerNumerals = settings.useKhmerNumerals,
                        now = now,
                        onClick = { onOpenEvent(item.eventId, item.date) },
                        onUnpin = { viewModel.unpin(item.eventId) },
                    )
                }

                if (state.passed.isNotEmpty()) {
                    item(key = "passed-header") {
                        Spacer(Modifier.height(Spacing.sm))
                        Text(
                            text = "បានកន្លងផុត",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    items(state.passed, key = { "past-${it.eventId}" }) { item ->
                        CountdownCard(
                            item = item,
                            style = settings.countdownStyle,
                            khmerNumerals = settings.useKhmerNumerals,
                            now = now,
                            onClick = { onOpenEvent(item.eventId, item.date) },
                            onUnpin = { viewModel.unpin(item.eventId) },
                        )
                    }
                }
            }
        }
    }
}

/**
 * How the remaining time is written.
 *
 * Here rather than in settings: it changes what is on this screen, and every row redraws as
 * you tap. A preference you can see the effect of does not need explaining.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StyleRow(selected: CountdownStyle, onSelect: (CountdownStyle) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CountdownStyle.entries.forEach { style ->
            FilterChip(
                selected = style == selected,
                onClick = { onSelect(style) },
                label = { Text(style.labelKm) },
            )
        }
    }
}

/**
 * One countdown, with the remaining time as the headline.
 *
 * The figure is the largest thing on the card because it is the only thing anybody opened
 * this screen for; the date underneath is the check you make afterwards.
 */
@Composable
private fun CountdownCard(
    item: CountdownItem,
    style: CountdownStyle,
    khmerNumerals: Boolean,
    now: java.time.LocalDateTime,
    onClick: () -> Unit,
    onUnpin: () -> Unit,
) {
    SurfaceCard(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        padding = LocalAppSettings.current.dashboardDensity.cardPaddingDp.dp,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    text = "ថ្ងៃ${KhmerTerms.dayOfWeek(item.date.dayOfWeek)} " +
                        localeWrittenDate(item.date),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onUnpin) {
                Icon(
                    imageVector = Icons.Outlined.PushPin,
                    contentDescription = "ដកចេញ",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(Spacing.sm))
        Text(
            text = Countdowns.remaining(item, now, style, khmerNumerals),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = if (item.date.isBefore(now.toLocalDate())) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.primary
            },
        )
        // Deliberately not a bar or a ring. A countdown has no start to measure progress
        // from - only a date - so any bar here would be drawn against a span the app chose,
        // and would move at a speed that means nothing.
        if (!item.allDay && item.date != now.toLocalDate()) {
            Text(
                text = "ម៉ោង " + localeNumber(item.at.hour, khmerNumerals) +
                    ":" + localeNumber("%02d".format(item.at.minute), khmerNumerals),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
