package com.khmercalendar.ui.habit

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.khmercalendar.core.khmer.KhmerTerms
import com.khmercalendar.domain.HabitSchedule
import com.khmercalendar.ui.components.EmptyState
import com.khmercalendar.ui.components.localeNumber
import com.khmercalendar.ui.theme.AccentPalette
import com.khmercalendar.ui.theme.LocalAppSettings
import com.khmercalendar.ui.theme.Spacing

/**
 * ទម្លាប់ — the habits you are keeping.
 *
 * ## What this is not
 *
 * It is not a game. There are no points, no badges and no notification telling you that you
 * have let a streak down, because a habit tracker that shouts is one people delete in a
 * fortnight. It shows a streak because a streak is genuinely the most useful summary of "am I
 * actually doing this", and stops there.
 *
 * ## The reading is honest about what was promised
 *
 * A habit due on three weekdays is not "43% complete" for doing exactly what it said. Every
 * figure here - streak, rate, the seven-day trail - is measured against the days the habit was
 * actually due, which is why the schedule is part of the arithmetic rather than a label.
 *
 * An unticked *today* never breaks anything. The day is not over.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabitScreen(
    viewModel: HabitViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val settings = LocalAppSettings.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ទម្លាប់") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "ត្រឡប់")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (state.dueToday > 0) {
                TodaySummary(done = state.doneToday, due = state.dueToday)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }

            QuickAdd(onAdd = { name, schedule -> viewModel.add(name, nextColor(state.rows.size), schedule) })
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            if (state.rows.isEmpty() && !state.isLoading) {
                EmptyState(
                    icon = Icons.Outlined.Bolt,
                    title = "គ្មានទម្លាប់",
                    message = "សរសេរអ្វីមួយដែលអ្នកចង់ធ្វើឱ្យបានទៀងទាត់ ខាងលើនេះ",
                )
                return@Column
            }

            LazyColumn(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 96.dp),
            ) {
                items(state.rows, key = { it.habit.id }) { row ->
                    HabitRowItem(
                        row = row,
                        khmerNumerals = settings.useKhmerNumerals,
                        onToggle = { viewModel.toggle(row.habit.id, !row.isDoneToday) },
                        onArchive = { viewModel.archive(row.habit.id) },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

/** How much of today is left to do. Counted against what is *due* today, not everything. */
@Composable
private fun TodaySummary(done: Int, due: Int) {
    val khmerNumerals = LocalAppSettings.current.useKhmerNumerals
    Column(Modifier.fillMaxWidth().padding(Spacing.lg)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "ថ្ងៃនេះ",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = localeNumber(done, khmerNumerals) + "/" + localeNumber(due, khmerNumerals),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (done == due) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
        }
        Spacer(Modifier.height(Spacing.sm))
        LinearProgressIndicator(
            progress = { if (due == 0) 0f else done.toFloat() / due },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * One line to add a habit.
 *
 * A name and how often, and nothing else. Everything a habit needs to exist is those two
 * things; asking for a colour, an icon and a reminder before the first one can be created is
 * how a tracker gets abandoned at the setup screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickAdd(onAdd: (String, HabitSchedule) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var kind by rememberSaveable { mutableStateOf(HabitSchedule.Kind.DAILY) }
    var days by remember { mutableStateOf(setOf(java.time.DayOfWeek.MONDAY, java.time.DayOfWeek.WEDNESDAY, java.time.DayOfWeek.FRIDAY)) }

    fun submit() {
        val schedule = when (kind) {
            HabitSchedule.Kind.DAILY -> HabitSchedule(kind)
            HabitSchedule.Kind.DAYS -> HabitSchedule(kind, days = days)
            HabitSchedule.Kind.TIMES_PER_WEEK -> HabitSchedule(kind, target = 3)
        }
        onAdd(name.trim(), schedule)
        name = ""
    }

    Column(Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("ទម្លាប់ថ្មី") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
            )
            Spacer(Modifier.width(Spacing.sm))
            IconButton(onClick = { submit() }, enabled = name.isNotBlank()) {
                Icon(Icons.Outlined.Add, contentDescription = "បន្ថែម")
            }
        }
        Spacer(Modifier.height(Spacing.xs))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            HabitSchedule.Kind.entries.forEach { entry ->
                FilterChip(
                    selected = kind == entry,
                    onClick = { kind = entry },
                    label = { Text(entry.labelKm, maxLines = 1) },
                )
            }
        }
        if (kind == HabitSchedule.Kind.DAYS) {
            Spacer(Modifier.height(Spacing.xs))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                com.khmercalendar.core.khmer.CalendarWeek
                    .daysOfWeek(LocalAppSettings.current.weekStart)
                    .forEach { day ->
                        val on = day in days
                        Box(
                            Modifier
                                .size(36.dp)
                                .background(
                                    if (on) MaterialTheme.colorScheme.primary else Color.Transparent,
                                    CircleShape,
                                )
                                .clickable {
                                    days = if (on) days - day else days + day
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = KhmerTerms.dayOfWeekShort(day).take(1),
                                style = MaterialTheme.typography.labelMedium,
                                color = if (on) {
                                    MaterialTheme.colorScheme.onPrimary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                    }
            }
        }
    }
}

@Composable
private fun HabitRowItem(
    row: HabitRow,
    khmerNumerals: Boolean,
    onToggle: () -> Unit,
    onArchive: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = row.isDoneToday, onCheckedChange = { onToggle() })
        Spacer(Modifier.width(Spacing.xs))

        Column(Modifier.weight(1f)) {
            Text(
                text = row.habit.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textDecoration = if (row.isDoneToday) TextDecoration.LineThrough else null,
                color = if (row.isDueToday) scheme.onSurface else scheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle(row, khmerNumerals),
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(Spacing.xs))
            RecentTrail(
                recent = row.recent,
                accent = row.habit.colorArgb.takeIf { it != 0 }?.let { Color(it) } ?: scheme.primary,
            )
        }

        // The streak, as a number rather than a flame. It is information, not a reward.
        if (row.currentStreak > 0) {
            Text(
                text = localeNumber(row.currentStreak, khmerNumerals),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = scheme.primary,
            )
            Spacer(Modifier.width(Spacing.sm))
        }
        IconButton(onClick = onArchive) {
            Icon(
                Icons.Outlined.Check,
                contentDescription = "បញ្ចប់ទម្លាប់នេះ",
                tint = scheme.onSurfaceVariant,
            )
        }
    }
}

/** The last seven days. A filled dot is a day kept; a hollow one is a day that was due. */
@Composable
private fun RecentTrail(recent: List<Boolean>, accent: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        recent.forEach { done ->
            Box(
                Modifier
                    .size(6.dp)
                    .background(
                        if (done) accent else MaterialTheme.colorScheme.outlineVariant,
                        CircleShape,
                    ),
            )
        }
    }
}

private fun subtitle(row: HabitRow, khmerNumerals: Boolean): String {
    val parts = buildList {
        add(
            when (row.habit.schedule.kind) {
                HabitSchedule.Kind.DAILY -> "រៀងរាល់ថ្ងៃ"
                HabitSchedule.Kind.DAYS -> row.habit.schedule.days
                    .sortedBy { it.value }
                    .joinToString(" ") { KhmerTerms.dayOfWeekShort(it) }
                    .ifBlank { "គ្មានថ្ងៃ" }

                HabitSchedule.Kind.TIMES_PER_WEEK ->
                    com.khmercalendar.core.khmer.KhmerNumerals.let { numerals ->
                        val done = if (khmerNumerals) numerals.toKhmer(row.weeklyDone) else "${row.weeklyDone}"
                        val target = if (khmerNumerals) {
                            numerals.toKhmer(row.habit.schedule.target)
                        } else {
                            "${row.habit.schedule.target}"
                        }
                        "សប្តាហ៍នេះ $done/$target"
                    }
            },
        )
        if (row.longestStreak > 0) {
            val best = if (khmerNumerals) {
                com.khmercalendar.core.khmer.KhmerNumerals.toKhmer(row.longestStreak)
            } else {
                "${row.longestStreak}"
            }
            add("វែងបំផុត $best")
        }
        if (row.completionRate > 0) {
            val rate = if (khmerNumerals) {
                com.khmercalendar.core.khmer.KhmerNumerals.toKhmer(row.completionRate)
            } else {
                "${row.completionRate}"
            }
            add("$rate%")
        }
    }
    return parts.joinToString(" · ")
}

/** Cycles the accent palette so consecutive habits do not all arrive the same colour. */
private fun nextColor(index: Int): Int = AccentPalette[index % AccentPalette.size].second
