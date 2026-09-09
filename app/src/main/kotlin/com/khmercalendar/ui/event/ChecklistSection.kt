package com.khmercalendar.ui.event

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.khmercalendar.domain.Checklist
import com.khmercalendar.domain.ChecklistItem
import com.khmercalendar.ui.components.localeNumber
import com.khmercalendar.ui.theme.Spacing

/**
 * The steps inside a task.
 *
 * ## Why the parent task is never ticked for you
 *
 * Completing the last box does not complete the task, and completing the task does not tick
 * the boxes. People finish work in ways their own checklist did not predict - three of five
 * steps turn out to be unnecessary, or the job is done and the list was never updated - and
 * an app that "helpfully" flips the parent is an app that overwrites a decision the user
 * already made. The progress bar reports; it does not act.
 *
 * ## Why arrows and not drag
 *
 * The same reason the dashboard editor keeps them: a drag is one-handed-hostile, invisible to
 * a screen reader, and cannot be driven at all on the oldest device this app supports. Arrows
 * work everywhere, and a checklist is short enough that two taps is not a burden.
 */
@Composable
fun ChecklistSection(
    items: List<ChecklistItem>,
    onAdd: (String) -> Unit,
    onToggle: (Long, Boolean) -> Unit,
    onDelete: (Long) -> Unit,
    onMove: (Int, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val ordered = Checklist.ordered(items)
    val progress = Checklist.progress(ordered)

    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "ជំហាន",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            if (!progress.isEmpty) {
                Text(
                    text = localeNumber(progress.done) + "/" + localeNumber(progress.total),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (progress.isComplete) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
            }
        }

        if (!progress.isEmpty) {
            Spacer(Modifier.height(Spacing.sm))
            LinearProgressIndicator(
                progress = { progress.fraction },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(Modifier.height(Spacing.sm))
        ordered.forEachIndexed { index, item ->
            ChecklistRow(
                item = item,
                canMoveUp = index > 0,
                canMoveDown = index < ordered.lastIndex,
                onToggle = { onToggle(item.id, !item.isDone) },
                onDelete = { onDelete(item.id) },
                onMoveUp = { onMove(index, index - 1) },
                onMoveDown = { onMove(index, index + 1) },
            )
        }

        if (Checklist.canAdd(ordered)) {
            AddRow(onAdd = onAdd)
        } else {
            Text(
                text = "ដល់ចំនួនកំណត់ហើយ",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.sm),
            )
        }
    }
}

@Composable
private fun ChecklistRow(
    item: ChecklistItem,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = item.isDone, onCheckedChange = { onToggle() })
        Text(
            text = item.text,
            style = MaterialTheme.typography.bodyMedium,
            textDecoration = if (item.isDone) TextDecoration.LineThrough else null,
            color = if (item.isDone) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onMoveUp, enabled = canMoveUp) {
            Icon(
                Icons.Outlined.KeyboardArrowUp,
                contentDescription = "ផ្លាស់ឡើងលើ",
                modifier = Modifier.size(18.dp),
            )
        }
        IconButton(onClick = onMoveDown, enabled = canMoveDown) {
            Icon(
                Icons.Outlined.KeyboardArrowDown,
                contentDescription = "ផ្លាស់ចុះក្រោម",
                modifier = Modifier.size(18.dp),
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                Icons.Outlined.Close,
                contentDescription = "លុបជំហាននេះ",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/** The field keeps focus and clears after each line, so a list can be typed straight through. */
@Composable
private fun AddRow(onAdd: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }

    fun submit() {
        val clean = Checklist.clean(text)
        if (clean.isNotBlank()) {
            onAdd(clean)
            text = ""
        }
    }

    Row(
        Modifier.fillMaxWidth().padding(top = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.weight(1f),
            placeholder = { Text("បន្ថែមជំហាន") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
        )
        IconButton(onClick = { submit() }, enabled = text.isNotBlank()) {
            Icon(Icons.Outlined.Add, contentDescription = "បន្ថែម")
        }
    }
}
