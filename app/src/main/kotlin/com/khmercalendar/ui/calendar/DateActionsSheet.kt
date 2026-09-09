package com.khmercalendar.ui.calendar

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChecklistRtl
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Today
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
import com.khmercalendar.core.khmer.KhmerTerms
import com.khmercalendar.ui.components.localeWrittenDate
import com.khmercalendar.ui.theme.Spacing
import java.time.LocalDate

/**
 * What you can do with a date, offered where you are already pointing at it.
 *
 * ## Why long-press
 *
 * Creating something on a specific day meant tapping the day, finding the add button, and
 * then correcting the date it had guessed. The date was already under the user's finger; the
 * gesture that says "this one, do something with it" is a long press, and it is the gesture
 * Android users already try on a grid.
 *
 * A long press is invisible, so nothing depends on it alone: every action here is reachable
 * by other routes, and the sheet exists to make the common ones one step instead of three.
 *
 * ## Why `skipPartiallyExpanded`
 *
 * A `ModalBottomSheet` opens at half the screen height, and content taller than that rest
 * position is *clipped*, not scrolled - the last action ends up under the navigation bar with
 * no way to reach it. Padding does not fix it; this does. Learned the hard way on the module
 * sheet in 1.7.0, and the same trap applies to every sheet in this app.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateActionsSheet(
    date: LocalDate,
    onDismiss: () -> Unit,
    onAddEvent: () -> Unit,
    onAddTask: () -> Unit,
    onOpenDay: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
            Column(Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm)) {
                Text(
                    text = "ថ្ងៃ" + KhmerTerms.dayOfWeek(date.dayOfWeek),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    text = localeWrittenDate(date),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Spacer(Modifier.height(Spacing.sm))

            // Three, and no more. A "countdown" row would open the same event editor -
            // a countdown is a pinned event, not a separate kind of thing - and a "note" row
            // would go to the day view, which the last row already does. Two labels for one
            // destination teaches people that the labels do not mean anything.
            ActionRow(Icons.Outlined.Event, "បន្ថែមព្រឹត្តិការណ៍", onAddEvent)
            ActionRow(Icons.Outlined.ChecklistRtl, "បន្ថែមកិច្ចការ", onAddTask)
            ActionRow(Icons.Outlined.Today, "បើកថ្ងៃ", onOpenDay)

            Spacer(Modifier.height(Spacing.lg))
        }
    }
}

@Composable
private fun ActionRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(Spacing.lg))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}
