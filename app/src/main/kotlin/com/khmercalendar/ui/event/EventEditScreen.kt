package com.khmercalendar.ui.event

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.khmercalendar.core.recurrence.Frequency
import com.khmercalendar.ui.components.localeDate
import com.khmercalendar.ui.components.localeNumber
import java.time.LocalTime
import com.khmercalendar.ui.components.localeTime
import com.khmercalendar.ui.components.rememberPlatformPickers
import com.khmercalendar.ui.theme.LocalAppSettings

/**
 * The add and edit form.
 *
 * Platform date and time pickers rather than Compose ones on purpose: they are already
 * localised into Khmer on the user's device and they are the control every other app on the
 * phone uses. They come from [rememberPlatformPickers], which puts them in the app's own
 * light or dark theme and its own 12/24-hour preference.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventEditScreen(
    viewModel: EventEditViewModel,
    onClose: () -> Unit,
    onSaved: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val pickers = rememberPlatformPickers()
    val draft = state.draft

    LaunchedEffect(state.saved) { if (state.saved) onSaved() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.isNew) "ព្រឹត្តិការណ៍ថ្មី" else "កែសម្រួល") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = "បិទ")
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::save, enabled = !state.isSaving) {
                        Icon(Icons.Default.Check, contentDescription = "រក្សាទុក")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            state.error?.let { error ->
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            OutlinedTextField(
                value = draft.title,
                onValueChange = { value -> viewModel.update { it.copy(title = value) } },
                label = { Text("ចំណងជើង") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = draft.location,
                onValueChange = { value -> viewModel.update { it.copy(location = value) } },
                label = { Text("ទីតាំង") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("ពេញមួយថ្ងៃ", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                Switch(checked = draft.allDay, onCheckedChange = viewModel::setAllDay)
            }

            FieldRow(
                label = "ចាប់ផ្តើម",
                value = localeDate(draft.date) + if (draft.allDay) "" else "  " + formatTime(draft.startTime),
                onClick = {
                    pickers.date(draft.date) { picked ->
                        viewModel.update {
                            it.copy(date = picked, endDate = maxOf(picked, it.endDate))
                        }
                        if (!draft.allDay) {
                            pickers.time(draft.startTime) { t ->
                                viewModel.update { d ->
                                    val duration = java.time.Duration.between(d.startTime, d.endTime)
                                    d.copy(
                                        startTime = t,
                                        endTime = t.plus(
                                            if (duration.isNegative || duration.isZero) {
                                                java.time.Duration.ofHours(1)
                                            } else {
                                                duration
                                            }
                                        ),
                                    )
                                }
                            }
                        }
                    }
                },
            )

            FieldRow(
                label = "បញ្ចប់",
                value = localeDate(draft.endDate) + if (draft.allDay) "" else "  " + formatTime(draft.endTime),
                onClick = {
                    pickers.date(draft.endDate) { picked ->
                        viewModel.update { it.copy(endDate = maxOf(picked, it.date)) }
                        if (!draft.allDay) {
                            pickers.time(draft.endTime) { t ->
                                viewModel.update { it.copy(endTime = t) }
                            }
                        }
                    }
                },
            )

            // An end at or before the start on the same date means the event runs into the
            // next day. The editor used to refuse to save that, while the storage layer
            // handled it correctly - so a new event added after 11pm could not be saved at
            // all. It is allowed now, and said out loud so it is never a silent surprise.
            if (!draft.allDay && draft.endDate == draft.date && draft.endTime <= draft.startTime) {
                Text(
                    "បញ្ចប់នៅថ្ងៃបន្ទាប់",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            SectionLabel("ធ្វើម្តងទៀត")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                RepeatChip("ទេ", draft.recurrence == null) { viewModel.setRepeat(null) }
                RepeatChip("រាល់ថ្ងៃ", draft.recurrence?.frequency == Frequency.DAILY) {
                    viewModel.setRepeat(Frequency.DAILY)
                }
                RepeatChip("រាល់សប្តាហ៍", draft.recurrence?.frequency == Frequency.WEEKLY) {
                    viewModel.setRepeat(Frequency.WEEKLY)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                RepeatChip("រាល់ខែ", draft.recurrence?.frequency == Frequency.MONTHLY) {
                    viewModel.setRepeat(Frequency.MONTHLY)
                }
                RepeatChip("រាល់ឆ្នាំ", draft.recurrence?.frequency == Frequency.YEARLY) {
                    viewModel.setRepeat(Frequency.YEARLY)
                }
            }

            SectionLabel("ការរំលឹក")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                REMINDER_CHOICES.forEach { minutes ->
                    RepeatChip(
                        label = reminderLabel(minutes),
                        selected = minutes in draft.reminderMinutes,
                        onClick = { viewModel.toggleReminder(minutes) },
                    )
                }
            }

            SectionLabel("ប្រភេទ")
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                state.categories.forEach { category ->
                    Box(
                        Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color(category.colorArgb))
                            .clickable { viewModel.update { it.copy(categoryId = category.id) } },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (draft.categoryId == category.id) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = category.name,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }
            state.categories.firstOrNull { it.id == draft.categoryId }?.let {
                Text(it.name, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("ជាកិច្ចការ (អាចធីកថារួចរាល់)", Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge)
                Switch(
                    checked = draft.isTask,
                    onCheckedChange = { value -> viewModel.update { it.copy(isTask = value) } },
                )
            }

            OutlinedTextField(
                value = draft.description,
                onValueChange = { value -> viewModel.update { it.copy(description = value) } },
                label = { Text("ការពិពណ៌នា") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(8.dp))
            Button(
                onClick = viewModel::save,
                enabled = !state.isSaving,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (state.isSaving) "កំពុងរក្សាទុក..." else "រក្សាទុក")
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 6.dp),
    )
}

@Composable
private fun FieldRow(label: String, value: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(12.dp))
        Text(value, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RepeatChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label) })
}

@Composable
private fun formatTime(time: LocalTime): String =
    localeTime(time, LocalAppSettings.current)

/**
 * The chip labels, in the user's digits.
 *
 * Shorter than the wording in notification settings - a chip has to fit five across - but it
 * must agree on the numerals. This copy was written without [localeNumber], so a calendar set
 * to Khmer numerals showed "10 នាទី" on this one screen and "១០ នាទីមុន" everywhere else.
 */
@Composable
private fun reminderLabel(minutes: Int): String = when {
    minutes == 0 -> "ពេលចាប់ផ្តើម"
    minutes < 60 -> "${localeNumber(minutes)} នាទី"
    minutes < 1440 -> "${localeNumber(minutes / 60)} ម៉ោង"
    else -> "${localeNumber(minutes / 1440)} ថ្ងៃ"
}

private val REMINDER_CHOICES = listOf(0, 10, 30, 60, 1440)

