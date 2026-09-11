package com.khmercalendar.ui.event

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.EventRepeat
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.khmercalendar.ui.components.rememberPlatformPickers
import java.time.LocalDate

/**
 * Two things people do to an event after making it, without opening the whole editor:
 * put it on another day, and change their mind about whether it is a task.
 */
@Composable
fun EventQuickActions(
    isTask: Boolean,
    onMove: () -> Unit,
    onToggleTask: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AssistChip(
            onClick = onMove,
            label = { Text("ប្តូរថ្ងៃ") },
            leadingIcon = { Icon(Icons.Outlined.EventRepeat, null, Modifier.height(AssistChipDefaults.IconSize)) },
        )
        AssistChip(
            onClick = onToggleTask,
            label = { Text(if (isTask) "ប្តូរទៅជាព្រឹត្តិការណ៍" else "ប្តូរទៅជាកិច្ចការ") },
            leadingIcon = {
                Icon(
                    if (isTask) Icons.Outlined.Event else Icons.Outlined.TaskAlt,
                    null,
                    Modifier.height(AssistChipDefaults.IconSize),
                )
            },
        )
    }
}

/**
 * Where to move the occurrence on [date].
 *
 * For a repeating event the sheet says up front that only this day moves, because that is the
 * only move there is (see EventRepository.reschedule) and "the whole series jumped a week" would
 * be the natural thing to fear.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoveSheet(
    date: LocalDate,
    isRecurring: Boolean,
    onDismiss: () -> Unit,
    onMove: (LocalDate) -> Unit,
) {
    val pickers = rememberPlatformPickers()
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
                "ប្តូរថ្ងៃ",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Text(
                if (isRecurring) {
                    "តែថ្ងៃនេះប៉ុណ្ណោះនឹងត្រូវប្តូរ។ ថ្ងៃផ្សេងទៀតនៃការធ្វើម្តងទៀតនៅដដែល។"
                } else {
                    "ម៉ោង និងរយៈពេលនៅដដែល។"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            MoveRow(Icons.Outlined.Today, "ថ្ងៃស្អែក") { onMove(date.plusDays(1)) }
            MoveRow(Icons.Outlined.DateRange, "សប្តាហ៍ក្រោយ") { onMove(date.plusWeeks(1)) }
            MoveRow(Icons.Outlined.CalendarMonth, "រើសថ្ងៃ…") {
                pickers.date(date) { picked -> if (picked != date) onMove(picked) }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun MoveRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null)
        Spacer(Modifier.width(16.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}
