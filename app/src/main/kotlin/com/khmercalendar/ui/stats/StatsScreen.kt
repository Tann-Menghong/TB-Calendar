package com.khmercalendar.ui.stats

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.khmercalendar.core.khmer.KhmerNumerals
import com.khmercalendar.domain.CategorySlice
import com.khmercalendar.domain.StatsMetric
import com.khmercalendar.domain.StatsPeriod
import com.khmercalendar.domain.StatsSummary
import com.khmercalendar.domain.Statistics
import com.khmercalendar.ui.components.AppMotion
import com.khmercalendar.ui.components.EmptyState
import com.khmercalendar.ui.components.HairlineDivider
import com.khmercalendar.ui.components.LocalReducedMotion
import com.khmercalendar.ui.components.SectionTitle
import com.khmercalendar.ui.components.SurfaceCard
import com.khmercalendar.ui.theme.LocalAppSettings
import com.khmercalendar.ui.theme.Spacing

/**
 * ស្ថិតិ — what you actually did, counted from the data already on the phone.
 *
 * ## The screen's one job
 *
 * To report, not to grade. There is no score, no streak-of-streaks and no sentence telling
 * the user they were less productive than last week. Every figure is a count of something
 * they entered themselves, and the interpretation is left to the person who did the work.
 *
 * ## Two things on this screen exist to stop it lying
 *
 * The **"ដល់ថ្ងៃនេះ"** marker on a period still running: the figures below it are not final,
 * and a half-finished month sitting beside a finished one invites exactly the wrong reading.
 *
 * The **comparison line**, which says in words that this period is being measured against the
 * *same part* of the last one. Comparing Tuesday-so-far against a whole previous week would
 * make every week begin in failure, and the arrow would be reporting the day of the week.
 *
 * ## Absent is not zero
 *
 * Days that have not happened are drawn as a faint track rather than a zero-height bar. At
 * zero height, "I did nothing on Friday" and "Friday has not happened" are the same picture.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    viewModel: StatsViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val khmerNumerals = LocalAppSettings.current.useKhmerNumerals

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ស្ថិតិ") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "ត្រឡប់")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = Spacing.lg,
                end = Spacing.lg,
                top = Spacing.sm,
                bottom = Spacing.xl,
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            item("periods") {
                PeriodSwitcher(
                    selected = state.period,
                    onSelect = viewModel::setPeriod,
                )
            }

            item("range") {
                RangeHeader(
                    title = state.title,
                    isPartial = state.range?.isPartial == true,
                    canGoForward = state.canGoForward,
                    isCurrent = state.isCurrent,
                    khmerNumerals = khmerNumerals,
                    onBack = { viewModel.step(-1) },
                    onForward = { viewModel.step(1) },
                    onToday = viewModel::today,
                )
            }

            if (state.isLoading) {
                item("loading") {
                    // Deliberately quiet. The first read is a few milliseconds on any real
                    // database; a spinner that flashes for one frame is worse than nothing.
                    Spacer(Modifier.height(Spacing.lg))
                }
                return@LazyColumn
            }

            if (state.summary.isEmpty) {
                item("empty") {
                    EmptyState(
                        icon = Icons.Outlined.Insights,
                        title = "គ្មានទិន្នន័យសម្រាប់រយៈពេលនេះ",
                        message = "ស្ថិតិរាប់ពីកិច្ចការដែលបានធ្វើរួច ការផ្តោតអារម្មណ៍ ទម្លាប់ " +
                            "និងព្រឹត្តិការណ៍របស់អ្នក។ ទាំងអស់នេះស្ថិតនៅក្នុងទូរស័ព្ទតែប៉ុណ្ណោះ។",
                        modifier = Modifier.padding(top = Spacing.xl),
                    )
                }
                return@LazyColumn
            }

            item("headline") {
                HeadlineFigures(
                    summary = state.summary,
                    previous = state.previous,
                    khmerNumerals = khmerNumerals,
                )
            }

            if (state.buckets.size > 1) {
                item("chart") {
                    SurfaceCard {
                        MetricChips(selected = state.metric, onSelect = viewModel::setMetric)
                        Spacer(Modifier.height(Spacing.lg))
                        BarChart(
                            buckets = state.buckets,
                            values = state.series,
                            metric = state.metric,
                            khmerNumerals = khmerNumerals,
                        )
                    }
                }
            }

            if (state.summary.categories.isNotEmpty()) {
                item("categories-title") {
                    SectionTitle("ពេលវេលាតាមប្រភេទ", modifier = Modifier.padding(top = Spacing.sm))
                }
                item("categories") {
                    SurfaceCard {
                        CategoryBreakdown(
                            slices = state.summary.categories,
                            khmerNumerals = khmerNumerals,
                        )
                    }
                }
            }

            item("details-title") {
                SectionTitle("លម្អិត", modifier = Modifier.padding(top = Spacing.sm))
            }
            item("details") {
                SurfaceCard {
                    DetailRows(summary = state.summary, khmerNumerals = khmerNumerals)
                }
            }

            item("privacy") {
                Text(
                    text = "គ្រប់លេខទាំងអស់នេះគណនាក្នុងទូរស័ព្ទ ពីទិន្នន័យរបស់អ្នកផ្ទាល់។ " +
                        "គ្មានអ្វីត្រូវបានផ្ញើចេញទៅខាងក្រៅឡើយ។",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.md, bottom = Spacing.sm),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PeriodSwitcher(selected: StatsPeriod, onSelect: (StatsPeriod) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        StatsPeriod.entries.forEachIndexed { index, period ->
            SegmentedButton(
                selected = period == selected,
                onClick = { onSelect(period) },
                shape = SegmentedButtonDefaults.itemShape(index, StatsPeriod.entries.size),
            ) {
                Text(period.labelKm, maxLines = 1)
            }
        }
    }
}

/** The period being shown, with an arrow either side and a way back to now. */
@Composable
private fun RangeHeader(
    title: String,
    isPartial: Boolean,
    canGoForward: Boolean,
    isCurrent: Boolean,
    khmerNumerals: Boolean,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onToday: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Outlined.KeyboardArrowLeft,
                    contentDescription = "រយៈពេលមុន",
                )
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = numerals(title, khmerNumerals),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (isPartial) {
                    // The figures below are for a period that has not finished. Saying so is
                    // the difference between "a slow month" and "the third of the month".
                    Text(
                        text = "ដល់ថ្ងៃនេះ",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            // The forward arrow is disabled rather than hidden: a control that vanishes moves
            // everything beside it, and the two arrows would swap places at the edge.
            IconButton(onClick = onForward, enabled = canGoForward) {
                Icon(
                    Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                    contentDescription = "រយៈពេលបន្ទាប់",
                )
            }
        }
        // Only once there is somewhere to come back from. Six taps into last year, the way
        // home should not be six taps back.
        if (!isCurrent) {
            TextButton(
                onClick = onToday,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) {
                Text("ត្រឡប់មកបច្ចុប្បន្ន")
            }
        }
    }
}

@Composable
private fun HeadlineFigures(
    summary: StatsSummary,
    previous: StatsSummary?,
    khmerNumerals: Boolean,
) {
    SurfaceCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Figure(
                value = localeNumberOf(summary.tasksCompleted, khmerNumerals),
                label = "កិច្ចការរួចរាល់",
                delta = previous?.let { summary.tasksCompleted - it.tasksCompleted },
                khmerNumerals = khmerNumerals,
                modifier = Modifier.weight(1f),
            )
            Figure(
                value = numerals(Statistics.formatMinutes(summary.focusMinutes), khmerNumerals),
                label = "ផ្តោតអារម្មណ៍",
                delta = previous?.let { summary.focusMinutes - it.focusMinutes },
                khmerNumerals = khmerNumerals,
                modifier = Modifier.weight(1f),
            )
            Figure(
                value = summary.habitRate?.let { localeNumberOf(it, khmerNumerals) + "%" } ?: "—",
                label = "ទម្លាប់",
                // Percentage points, not a ratio of ratios: "up 12" on a rate means twelve
                // points, and saying "12%" of a percentage is a different and wrong number.
                delta = previous?.let { before ->
                    val now = summary.habitRate
                    val then = before.habitRate
                    if (now == null || then == null) null else now - then
                },
                khmerNumerals = khmerNumerals,
                modifier = Modifier.weight(1f),
            )
        }

        if (previous != null) {
            Spacer(Modifier.height(Spacing.md))
            HairlineDivider()
            Spacer(Modifier.height(Spacing.sm))
            Text(
                text = "ធៀបនឹងរយៈពេលមុន គិតត្រឹមចំណុចដូចគ្នា",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** One headline number, its caption, and how it moved. */
@Composable
private fun Figure(
    value: String,
    label: String,
    delta: Int?,
    khmerNumerals: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        Spacer(Modifier.height(Spacing.xs))
        // No arrow when nothing moved. A "0" beside every figure on a quiet week is noise
        // that has to be read before it can be dismissed.
        if (delta != null && delta != 0) {
            val up = delta > 0
            Text(
                text = (if (up) "▲ " else "▼ ") + localeNumberOf(kotlin.math.abs(delta), khmerNumerals),
                style = MaterialTheme.typography.labelSmall,
                color = if (up) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MetricChips(selected: StatsMetric, onSelect: (StatsMetric) -> Unit) {
    // Scrollable rather than wrapped: at the largest font sizes four Khmer chips will not fit
    // on any phone, and a row that silently runs off the edge hides the last option entirely.
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        StatsMetric.entries.forEach { metric ->
            FilterChip(
                selected = metric == selected,
                onClick = { onSelect(metric) },
                label = { Text(metric.labelKm, maxLines = 1) },
            )
        }
    }
}

/**
 * The bars.
 *
 * Drawn as weighted boxes rather than on a Canvas so that each one can carry its own
 * semantics: a screen reader moving across a month hears "15: 3 tasks", which is the whole
 * content of the chart. A canvas would be one unlabelled rectangle.
 */
@Composable
private fun BarChart(
    buckets: List<com.khmercalendar.domain.StatsBucket>,
    values: List<Int?>,
    metric: StatsMetric,
    khmerNumerals: Boolean,
) {
    val peak = values.filterNotNull().maxOrNull() ?: 0
    val scheme = MaterialTheme.colorScheme
    // Labels are dropped rather than crushed once a month's worth of them will not fit.
    val showLabels = buckets.size <= 16

    Column {
        Row(
            Modifier.fillMaxWidth().height(ChartHeight),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            buckets.forEachIndexed { index, bucket ->
                val value = values.getOrNull(index)
                Bar(
                    fraction = if (peak <= 0 || value == null) 0f else value.toFloat() / peak,
                    isFuture = bucket.isFuture,
                    isToday = bucket.isToday,
                    description = barDescription(bucket.label, value, metric, khmerNumerals),
                    color = scheme.primary,
                    track = scheme.surfaceVariant,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        if (showLabels) {
            Spacer(Modifier.height(Spacing.xs))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                buckets.forEach { bucket ->
                    Text(
                        text = numerals(bucket.label, khmerNumerals),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (bucket.isToday) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                        modifier = Modifier.weight(1f).clearAndSetSemantics { },
                    )
                }
            }
        }

        Spacer(Modifier.height(Spacing.sm))
        Text(
            text = if (peak > 0) {
                "កម្ពស់ខ្ពស់បំផុត៖ " + peakLabel(peak, metric, khmerNumerals)
            } else {
                "គ្មានទិន្នន័យ"
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Bar(
    fraction: Float,
    isFuture: Boolean,
    isToday: Boolean,
    description: String,
    color: Color,
    track: Color,
    modifier: Modifier = Modifier,
) {
    val reduced = LocalReducedMotion.current
    val target = fraction.coerceIn(0f, 1f)
    val height by animateFloatAsState(
        targetValue = target,
        animationSpec = AppMotion.tweenOf(com.khmercalendar.ui.theme.Motion.PROGRESS),
        label = "bar",
    )
    val shown = if (reduced) target else height

    Box(
        modifier
            .fillMaxHeight()
            .clearAndSetSemantics { contentDescription = description },
        contentAlignment = Alignment.BottomCenter,
    ) {
        // The track is what makes "has not happened" visible. A future day is a faint outline
        // of a day, not a day on which nothing was done.
        Box(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .clip(RoundedCornerShape(3.dp))
                .background(track.copy(alpha = if (isFuture) 0.35f else 0.6f)),
        )
        if (!isFuture) {
            Box(
                Modifier
                    .fillMaxWidth()
                    // A value of zero still gets a sliver, so the row reads as a row of days
                    // rather than as a gap where some days are missing.
                    .fillMaxHeight(shown.coerceAtLeast(MinBar))
                    .clip(RoundedCornerShape(3.dp))
                    .background(if (isToday) color else color.copy(alpha = 0.75f)),
            )
        }
    }
}

@Composable
private fun CategoryBreakdown(slices: List<CategorySlice>, khmerNumerals: Boolean) {
    val shown = slices.take(MaxCategories)
    val peak = shown.maxOfOrNull { it.minutes } ?: 0

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        shown.forEach { slice ->
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(10.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(
                                if (slice.colorArgb != 0) {
                                    Color(slice.colorArgb)
                                } else {
                                    MaterialTheme.colorScheme.primary
                                },
                            ),
                    )
                    Spacer(Modifier.width(Spacing.sm))
                    Text(
                        text = slice.name,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = if (slice.minutes > 0) {
                            numerals(Statistics.formatMinutes(slice.minutes), khmerNumerals)
                        } else {
                            // All-day entries have no length, so they are reported as a count.
                            // Calling a day off "24 ម៉ោង" would drown every real hour beside it.
                            localeNumberOf(slice.count, khmerNumerals) + " ព្រឹត្តិការណ៍"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (peak > 0) {
                    Spacer(Modifier.height(Spacing.xs))
                    com.khmercalendar.ui.components.ProgressBar(
                        fraction = slice.minutes.toFloat() / peak,
                        indicator = if (slice.colorArgb != 0) {
                            Color(slice.colorArgb)
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                        height = 4.dp,
                    )
                }
            }
        }

        if (slices.size > MaxCategories) {
            Text(
                text = "និង " + localeNumberOf(slices.size - MaxCategories, khmerNumerals) + " ប្រភេទទៀត",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DetailRows(summary: StatsSummary, khmerNumerals: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        DetailRow(
            "វគ្គផ្តោតអារម្មណ៍",
            localeNumberOf(summary.focusSessions, khmerNumerals) + " វគ្គ",
        )
        DetailRow(
            "ទម្លាប់ដែលបានធ្វើ",
            localeNumberOf(summary.habitTicks, khmerNumerals) + " / " +
                localeNumberOf(summary.habitExpected, khmerNumerals),
        )
        DetailRow(
            "ព្រឹត្តិការណ៍",
            localeNumberOf(summary.eventCount, khmerNumerals),
        )
        DetailRow(
            "ព្រឹត្តិការណ៍ពេញមួយថ្ងៃ",
            localeNumberOf(summary.allDayEventCount, khmerNumerals),
        )
        DetailRow(
            "ថ្ងៃដែលមានកំណត់ចំណាំ",
            localeNumberOf(summary.noteDays, khmerNumerals),
        )
        if (summary.plannedWorkMinutes > 0) {
            // "Planned", not "worked". The app has no way to know whether anybody was there,
            // and a figure labelled "worked" would be inventing that.
            DetailRow(
                "ម៉ោងការងារតាមកាលវិភាគ",
                numerals(Statistics.formatMinutes(summary.plannedWorkMinutes), khmerNumerals),
            )
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )
    }
}

private fun barDescription(
    label: String,
    value: Int?,
    metric: StatsMetric,
    khmerNumerals: Boolean,
): String {
    val head = numerals(label, khmerNumerals)
    if (value == null) return "$head៖ មិនទាន់មកដល់"
    val body = if (metric == StatsMetric.FOCUS) {
        numerals(Statistics.formatMinutes(value), khmerNumerals)
    } else {
        localeNumberOf(value, khmerNumerals)
    }
    return "$head៖ $body"
}

private fun peakLabel(peak: Int, metric: StatsMetric, khmerNumerals: Boolean): String =
    if (metric == StatsMetric.FOCUS) {
        numerals(Statistics.formatMinutes(peak), khmerNumerals)
    } else {
        localeNumberOf(peak, khmerNumerals)
    }

private fun numerals(text: String, khmerNumerals: Boolean): String =
    if (khmerNumerals) KhmerNumerals.toKhmer(text) else text

private fun localeNumberOf(value: Int, khmerNumerals: Boolean): String =
    numerals(value.toString(), khmerNumerals)

/** Tall enough to make a difference of one visible, short enough to leave room below. */
private val ChartHeight = 132.dp

/** Every elapsed day gets at least a sliver, so a row of days looks like a row of days. */
private const val MinBar = 0.015f

private const val MaxCategories = 6
