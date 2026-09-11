package com.khmercalendar.ui.event

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.khmercalendar.core.khmer.KhmerNumerals
import com.khmercalendar.domain.EventTemplate
import com.khmercalendar.domain.EventTemplates
import com.khmercalendar.domain.Statistics
import com.khmercalendar.ui.components.localeTime
import com.khmercalendar.ui.theme.LocalAppSettings

/**
 * The editor's two template actions.
 *
 * Starting from a template is offered only for something new: on an existing event it would
 * overwrite the form with another event's details, which nobody asks for by tapping a bookmark.
 * Saving the form as a template makes sense from anything.
 */
@Composable
fun TemplateActions(
    isNew: Boolean,
    hasTemplates: Boolean,
    onOpenTemplates: () -> Unit,
    onSaveAsTemplate: () -> Unit,
) {
    if (isNew && hasTemplates) {
        IconButton(onClick = onOpenTemplates) {
            Icon(Icons.Outlined.Bookmarks, contentDescription = "ចាប់ផ្តើមពីគំរូ")
        }
    }
    IconButton(onClick = onSaveAsTemplate) {
        Icon(Icons.Outlined.BookmarkAdd, contentDescription = "រក្សាទុកជាគំរូ")
    }
}

/**
 * The saved templates, to start from or to delete.
 *
 * Deleting asks first, and says what it does not touch: events already made from a template are
 * ordinary events and stay exactly as they are.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplateSheet(
    templates: List<EventTemplate>,
    onDismiss: () -> Unit,
    onApply: (EventTemplate) -> Unit,
    onDelete: (Long) -> Unit,
) {
    var pendingDelete by remember { mutableStateOf<EventTemplate?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        // Half-height sheets clip rather than scroll; see DateActionsSheet.
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                "គំរូ",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Text(
                "ថ្ងៃដែលអ្នកបានជ្រើសនៅដដែល។ គំរូបំពេញអ្វីៗផ្សេងទៀត។",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            templates.forEach { template ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onApply(template) }
                        .padding(start = 16.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(template.name, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            describe(template),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { pendingDelete = template }) {
                        Icon(Icons.Outlined.Delete, contentDescription = "លុបគំរូ ${template.name}")
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    pendingDelete?.let { template ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("លុបគំរូ «${template.name}»?") },
            text = { Text("ព្រឹត្តិការណ៍ដែលបានបង្កើតពីគំរូនេះរួចហើយ មិនត្រូវបានប៉ះពាល់ទេ។") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(template.id)
                    pendingDelete = null
                }) { Text("លុប") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("បោះបង់") } },
        )
    }
}

/** Naming the template, and saying plainly what goes into one. */
@Composable
fun SaveTemplateDialog(
    suggestedName: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(suggestedName.trim().take(EventTemplates.MAX_NAME)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("រក្សាទុកជាគំរូ") },
        text = {
            Column {
                Text(
                    "ម៉ោង រយៈពេល ទីតាំង ប្រភេទ ការរំលឹក និងការធ្វើម្តងទៀតនឹងត្រូវរក្សាទុក។ " +
                        "កាលបរិច្ឆេទមិនត្រូវរក្សាទុកទេ។",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(EventTemplates.MAX_NAME) },
                    label = { Text("ឈ្មោះគំរូ") },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name) }, enabled = name.isNotBlank()) { Text("រក្សាទុក") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("បោះបង់") } },
    )
}

@Composable
private fun describe(template: EventTemplate): String {
    val settings = LocalAppSettings.current
    fun digits(text: String) = if (settings.useKhmerNumerals) KhmerNumerals.toKhmer(text) else text

    val parts = mutableListOf(if (template.isTask) "កិច្ចការ" else "ព្រឹត្តិការណ៍")
    if (template.allDay) {
        parts += "ពេញមួយថ្ងៃ"
    } else {
        parts += localeTime(template.startTime, settings)
        parts += digits(Statistics.formatMinutes(template.durationMinutes))
    }
    if (template.recurrence != null) parts += "ធ្វើម្តងទៀត"
    return parts.joinToString(" · ")
}
