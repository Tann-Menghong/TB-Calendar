package com.khmercalendar.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.khmercalendar.core.khmer.KhmerTerms
import com.khmercalendar.core.work.WorkBlock
import com.khmercalendar.core.work.WorkDay
import com.khmercalendar.core.work.WorkSchedule
import com.khmercalendar.data.prefs.AppSettings
import com.khmercalendar.data.prefs.SettingsStore
import com.khmercalendar.ui.components.CalendarFormats
import com.khmercalendar.ui.components.LocalUses24Hour
import com.khmercalendar.ui.components.rememberPlatformPickers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalTime

/**
 * Work schedule.
 *
 * Every day is editable, including the number of blocks in it: the defaults describe an
 * ordinary Cambodian office week, but a shop that opens straight through and a teacher with
 * three separate sessions are equally ordinary, and neither is expressible if the screen only
 * offers "morning" and "afternoon" fields.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkScheduleScreen(
    settingsFlow: StateFlow<AppSettings>,
    settingsStore: SettingsStore,
    onBack: () -> Unit,
) {
    val settings by settingsFlow.collectAsStateWithLifecycle()
    val schedule = settings.workSchedule
    val scope = rememberCoroutineScope()
    val pickers = rememberPlatformPickers()
    val uses24Hour = LocalUses24Hour.current
    val khmerNumerals = settings.useKhmerNumerals

    fun update(next: WorkSchedule) {
        scope.launch { settingsStore.setWorkSchedule(next) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("កាលវិភាគការងារ") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "ត្រឡប់")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()),
        ) {
            SwitchRow(
                title = "បង្ហាញការរាប់ថយក្រោយម៉ោងធ្វើការ",
                subtitle = "នៅលើផ្ទាំងដើម",
                checked = schedule.enabled,
                onChange = { on -> update(schedule.copy(enabled = on)) },
            )
            SwitchRow(
                title = "ជូនដំណឹងពេលប្តូរវេន",
                subtitle = "ចូលធ្វើការ សម្រាក និងចប់ការងារ",
                checked = settings.workNotifications,
                enabled = schedule.enabled,
                onChange = { on -> scope.launch { settingsStore.setWorkNotifications(on) } },
            )

            HorizontalDivider()

            Text(
                "ម៉ោងធ្វើការប្រចាំថ្ងៃ",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            )

            // Monday first, matching the calendar's own week start default.
            DayOfWeek.entries.forEach { day ->
                DayRow(
                    day = day,
                    workDay = schedule.dayOf(day),
                    uses24Hour = uses24Hour,
                    khmerNumerals = khmerNumerals,
                    onEditBlock = { index, isStart ->
                        val block = schedule.dayOf(day).blocks[index]
                        val initial = if (isStart) block.start else block.end
                        pickers.time(initial) { picked ->
                            val edited = if (isStart) {
                                block.copy(start = picked)
                            } else {
                                block.copy(end = picked)
                            }
                            // An inverted range is rejected rather than saved: it would make
                            // the countdown meaningless and WorkBlock refuses to hold it.
                            if (edited.end.isAfter(edited.start)) {
                                val blocks = schedule.dayOf(day).blocks.toMutableList()
                                blocks[index] = edited
                                update(schedule.withDay(day, WorkDay(blocks.sortedBy { it.start })))
                            }
                        }
                    },
                    onRemoveBlock = { index ->
                        val blocks = schedule.dayOf(day).blocks.toMutableList()
                        blocks.removeAt(index)
                        update(schedule.withDay(day, WorkDay(blocks)))
                    },
                    onAddBlock = {
                        val existing = schedule.dayOf(day).blocks
                        // A new block starts after the last one ends, so adding one never
                        // creates an overlap the user then has to untangle.
                        val from = existing.maxOfOrNull { it.end }?.plusHours(1)
                            ?: LocalTime.of(7, 30)
                        val start = if (from.isAfter(LocalTime.of(22, 0))) LocalTime.of(20, 0) else from
                        val end = minOf(start.plusHours(4), LocalTime.of(23, 30))
                        if (end.isAfter(start)) {
                            update(
                                schedule.withDay(
                                    day,
                                    WorkDay((existing + WorkBlock(start, end, labelFor(start)))
                                        .sortedBy { it.start }),
                                ),
                            )
                        }
                    },
                )
                HorizontalDivider()
            }

            Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { update(WorkSchedule.DEFAULT) }) {
                    Text("ត្រឡប់ទៅលំនាំដើម")
                }
            }
            Text(
                "លំនាំដើម៖ ច័ន្ទ–សុក្រ ៧:៣០–១១:៣០ និង ១:៣០–៥:៣០ · " +
                    "សៅរ៍ ៧:៣០–១១:៣០ · អាទិត្យ ឈប់សម្រាក",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Spacer(Modifier.height(48.dp))
        }
    }
}

@Composable
private fun DayRow(
    day: DayOfWeek,
    workDay: WorkDay,
    uses24Hour: Boolean,
    khmerNumerals: Boolean,
    onEditBlock: (index: Int, isStart: Boolean) -> Unit,
    onRemoveBlock: (index: Int) -> Unit,
    onAddBlock: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "ថ្ងៃ${KhmerTerms.dayOfWeek(day)}",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onAddBlock) {
                Icon(Icons.Outlined.Add, contentDescription = "បន្ថែមវេន")
            }
        }

        if (workDay.isOff) {
            Text(
                "ឈប់សម្រាក",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            workDay.blocks.forEachIndexed { index, block ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    AssistChip(
                        onClick = { onEditBlock(index, true) },
                        label = {
                            Text(CalendarFormats.time(block.start, uses24Hour, khmerNumerals))
                        },
                    )
                    Text("–", style = MaterialTheme.typography.bodyMedium)
                    AssistChip(
                        onClick = { onEditBlock(index, false) },
                        label = {
                            Text(CalendarFormats.time(block.end, uses24Hour, khmerNumerals))
                        },
                    )
                    IconButton(onClick = { onRemoveBlock(index) }) {
                        Icon(
                            Icons.Outlined.Close,
                            contentDescription = "លុបវេន",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Text(
                "សរុប ${hoursLabel(workDay, khmerNumerals)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun labelFor(start: LocalTime): String = when {
    start.hour < 12 -> WorkSchedule.MORNING_KM
    start.hour < 17 -> WorkSchedule.AFTERNOON_KM
    else -> "ការងារពេលល្ងាច"
}

private fun hoursLabel(day: WorkDay, khmerNumerals: Boolean): String {
    val minutes = day.totalDuration.toMinutes()
    val text = if (minutes % 60 == 0L) {
        "${minutes / 60} ម៉ោង"
    } else {
        "${minutes / 60} ម៉ោង ${minutes % 60} នាទី"
    }
    return if (khmerNumerals) com.khmercalendar.core.khmer.KhmerNumerals.toKhmer(text) else text
}
