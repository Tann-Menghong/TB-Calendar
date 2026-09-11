package com.khmercalendar.ui.event

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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.khmercalendar.core.khmer.Chhankitek
import com.khmercalendar.core.khmer.KhmerTerms
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.LocalContentColor
import com.khmercalendar.domain.CountdownItem
import com.khmercalendar.domain.Countdowns
import com.khmercalendar.ui.components.localeNumber
import com.khmercalendar.ui.components.localeWrittenDate
import com.khmercalendar.ui.theme.LocalAppSettings
import java.time.LocalDate
import com.khmercalendar.ui.components.CalendarFormats
import com.khmercalendar.ui.components.LocalUses24Hour
import com.khmercalendar.ui.components.localeTimeRange

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventDetailScreen(
    viewModel: EventEditViewModel,
    eventId: Long,
    occurrenceDate: LocalDate,
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val draft = state.draft
    val context = LocalContext.current
    var showDelete by remember { mutableStateOf(false) }

    LaunchedEffect(eventId) {
        viewModel.load(eventId, occurrenceDate)
        viewModel.watchChecklist(eventId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ព័ត៌មានលម្អិត") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "ត្រឡប់")
                    }
                },
                actions = {
                    // Pinning is here rather than in the editor because it is a one-tap
                    // property of a date, not a field you fill in: you decide something is
                    // worth counting down to while looking at it.
                    IconButton(
                        onClick = { viewModel.setPinned(eventId, !state.isPinned) },
                    ) {
                        Icon(
                            imageVector = if (state.isPinned) {
                                Icons.Filled.PushPin
                            } else {
                                Icons.Outlined.PushPin
                            },
                            contentDescription = if (state.isPinned) {
                                "ដកចេញពីរាប់ថយក្រោយ"
                            } else {
                                "រាប់ថយក្រោយ"
                            },
                            tint = if (state.isPinned) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                LocalContentColor.current
                            },
                        )
                    }
                    IconButton(onClick = { onEdit(eventId) }) {
                        Icon(Icons.Default.Edit, contentDescription = "កែសម្រួល")
                    }
                    IconButton(onClick = { viewModel.duplicate(eventId) { onBack() } }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "ចម្លង")
                    }
                    IconButton(onClick = { showDelete = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "លុប")
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
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(draft.title, style = MaterialTheme.typography.headlineSmall)

            val lunar = remember(occurrenceDate) { Chhankitek.toLunarOrNull(occurrenceDate) }
            val dateLine = "ថ្ងៃ${KhmerTerms.dayOfWeek(occurrenceDate.dayOfWeek)} " +
                localeWrittenDate(occurrenceDate)
            Text(dateLine, style = MaterialTheme.typography.bodyLarge)
            lunar?.let {
                Text(
                    it.format(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (!draft.allDay) {
                Text(
                    text = localeTimeRange(draft.startTime, draft.endTime, LocalAppSettings.current),
                    style = MaterialTheme.typography.bodyLarge,
                )
            } else {
                Text("ពេញមួយថ្ងៃ", style = MaterialTheme.typography.bodyLarge)
            }

            if (draft.location.isNotBlank()) {
                DetailLine("ទីតាំង", draft.location)
            }
            draft.recurrence?.let {
                DetailLine("ធ្វើម្តងទៀត", repeatLabel(it.frequency.name))
            }
            if (draft.reminderMinutes.isNotEmpty()) {
                val khmerDigits = LocalAppSettings.current.useKhmerNumerals
                DetailLine("ការរំលឹក", draft.reminderMinutes.joinToString(", ") { m ->
                    when {
                        m == 0 -> "ពេលចាប់ផ្តើម"
                        m < 60 -> "${localeNumber(m, khmerDigits)} នាទីមុន"
                        m < 1440 -> "${localeNumber(m / 60, khmerDigits)} ម៉ោងមុន"
                        else -> "${localeNumber(m / 1440, khmerDigits)} ថ្ងៃមុន"
                    }
                })
            }
            // Says what the pin did, because an icon changing state in a top bar is easy
            // to miss and the effect is on a different screen.
            if (state.isPinned) {
                DetailLine(
                    label = "រាប់ថយក្រោយ",
                    value = Countdowns.remaining(
                        item = CountdownItem(
                            eventId = eventId,
                            title = draft.title,
                            date = occurrenceDate,
                            at = java.time.LocalDateTime.of(occurrenceDate, draft.startTime),
                            allDay = draft.allDay,
                            isHoliday = false,
                            isPinned = true,
                        ),
                        now = java.time.LocalDateTime.now(),
                        style = LocalAppSettings.current.countdownStyle,
                        khmerNumerals = LocalAppSettings.current.useKhmerNumerals,
                    ),
                )
            }
            if (draft.description.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(draft.description, style = MaterialTheme.typography.bodyMedium)
            }

            // Steps, on tasks only. An ordinary event does not get one: a checklist on a
            // meeting is an agenda, which is what the description is for, and offering it
            // everywhere would put an empty section on every event in the calendar.
            if (draft.isTask) {
                Spacer(Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(Modifier.height(12.dp))
                ChecklistSection(
                    items = state.checklist,
                    onAdd = { text -> viewModel.addChecklistItem(eventId, text) },
                    onToggle = viewModel::setChecklistItemDone,
                    onDelete = viewModel::deleteChecklistItem,
                    onMove = { from, to -> viewModel.moveChecklistItem(eventId, from, to) },
                )
            }

            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (draft.isTask) {
                    // The occurrence being looked at, not the series: this screen is opened on a
                    // date, and "done" on a daily chore means done that day.
                    OutlinedButton(
                        onClick = {
                            viewModel.setCompleted(eventId, occurrenceDate, !state.occurrenceCompleted)
                        },
                    ) {
                        Text(if (state.occurrenceCompleted) "សម្គាល់ថាមិនទាន់រួច" else "សម្គាល់ថារួចរាល់")
                    }
                }
                val use24Hour = LocalUses24Hour.current
                val khmerNumerals = LocalAppSettings.current.useKhmerNumerals
                OutlinedButton(
                    onClick = {
                        shareEvent(
                            context, draft.title, dateLine, draft, use24Hour, khmerNumerals,
                        )
                    },
                ) {
                    Icon(Icons.Default.Share, contentDescription = null)
                    Spacer(Modifier.padding(horizontal = 4.dp))
                    Text("ចែករំលែក")
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }

    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text("លុបព្រឹត្តិការណ៍") },
            text = {
                Text(
                    if (draft.recurrence != null) {
                        "នេះជាព្រឹត្តិការណ៍ដែលធ្វើម្តងទៀត។ តើអ្នកចង់លុបតែថ្ងៃនេះ ឬទាំងអស់?"
                    } else {
                        "តើអ្នកប្រាកដទេ?"
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showDelete = false
                    viewModel.delete(eventId, onBack)
                }) { Text(if (draft.recurrence != null) "លុបទាំងអស់" else "លុប") }
            },
            dismissButton = {
                Row {
                    if (draft.recurrence != null) {
                        TextButton(onClick = {
                            showDelete = false
                            viewModel.deleteOccurrence(eventId, occurrenceDate, onBack)
                        }) { Text("តែថ្ងៃនេះ") }
                    }
                    TextButton(onClick = { showDelete = false }) { Text("បោះបង់") }
                }
            },
        )
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

private fun repeatLabel(frequency: String): String = when (frequency) {
    "DAILY" -> "រាល់ថ្ងៃ"
    "WEEKLY" -> "រាល់សប្តាហ៍"
    "MONTHLY" -> "រាល់ខែ"
    "YEARLY" -> "រាល់ឆ្នាំ"
    else -> frequency
}

/**
 * Shares an event as plain text.
 *
 * Text rather than an .ics attachment: the common case is pasting the details into Telegram
 * or Messenger, where an attachment is worse than useless. Whole-calendar interchange is what
 * the .ics export in Backup is for.
 */
private fun shareEvent(
    context: android.content.Context,
    title: String,
    dateLine: String,
    draft: com.khmercalendar.domain.EventDraftModel,
    use24Hour: Boolean,
    khmerNumerals: Boolean,
) {
    val text = buildString {
        appendLine(title)
        append(dateLine)
        if (!draft.allDay) {
            append("  ")
            append(CalendarFormats.time(draft.startTime, use24Hour, khmerNumerals))
        }
        if (draft.location.isNotBlank()) appendLine().append("ទីតាំង៖ ${draft.location}")
        if (draft.description.isNotBlank()) appendLine().append(draft.description)
    }
    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(android.content.Intent.EXTRA_TEXT, text)
    }
    runCatching { context.startActivity(android.content.Intent.createChooser(intent, "ចែករំលែក")) }
}
