package com.khmercalendar.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.khmercalendar.data.prefs.DashboardCard
import com.khmercalendar.ui.theme.IconSize
import com.khmercalendar.ui.theme.Spacing

/**
 * What you can do to one module, without leaving the dashboard.
 *
 * Rearranging a dashboard from a settings screen means holding the layout in your head while
 * you look at a list of names. This is the other half of that: the module you want to move is
 * under your finger, and the change happens where you can see its effect.
 *
 * Deliberately four actions and no more. Hide and the two moves cover almost every adjustment
 * anybody makes in the moment; anything more considered - presets, restoring something hidden,
 * a long reorder - belongs on the editor screen, which is the fourth action.
 *
 * @param bottomInset the navigation bar's height, measured by the caller. The sheet's own
 *   window reported it as zero on an API 26 device, which put the last action underneath a
 *   three-button navigation bar where it could not be tapped; the screen behind the sheet
 *   measures it correctly, so it is passed in rather than read here.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModuleSheet(
    card: DashboardCard,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    bottomInset: Dp,
    onDismiss: () -> Unit,
    onHide: () -> Unit,
    onMove: (Int) -> Unit,
    onEdit: () -> Unit,
) {
    // Straight to expanded. The half-height resting state is shorter than these four
    // actions, and a sheet that opens with its last action below the screen edge - where the
    // navigation bar sits - is a menu with an item nobody can reach.
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().padding(bottom = Spacing.lg + bottomInset)) {
            Column(Modifier.padding(horizontal = Spacing.xl)) {
                Text(
                    card.labelKm,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    EDIT_MODULE_ACTION,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(Spacing.md))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // The two moves are always drawn, and greyed at the ends of the list rather than
            // removed. A menu whose rows change places depending on where the module sits is
            // a menu you have to read every time.
            SheetAction(
                icon = Icons.Outlined.ArrowUpward,
                label = "ផ្លាស់ទីឡើងលើ",
                enabled = canMoveUp,
                onClick = { onMove(-1) },
            )
            SheetAction(
                icon = Icons.Outlined.ArrowDownward,
                label = "ផ្លាស់ទីចុះក្រោម",
                enabled = canMoveDown,
                onClick = { onMove(1) },
            )
            SheetAction(
                icon = Icons.Outlined.VisibilityOff,
                label = "លាក់ផ្ទាំងនេះ",
                // Hiding is reversible and the sheet says where from, so it is an ordinary
                // action rather than a destructive one asking for confirmation.
                supporting = "អាចបង្ហាញវិញនៅក្នុងការកំណត់ផ្ទាំង",
                onClick = onHide,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            SheetAction(
                icon = Icons.Outlined.Tune,
                label = "រៀបចំផ្ទាំងទាំងអស់",
                onClick = onEdit,
            )
        }
    }
}

@Composable
private fun SheetAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    supporting: String? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val textColor = if (enabled) scheme.onSurface else scheme.onSurface.copy(alpha = 0.38f)
    val iconColor = if (enabled) scheme.primary else scheme.onSurface.copy(alpha = 0.38f)
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = Spacing.xl, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.size(IconSize.small),
        )
        Spacer(Modifier.width(Spacing.lg))
        Column {
            Text(label, style = MaterialTheme.typography.bodyLarge, color = textColor)
            if (supporting != null && enabled) {
                Spacer(Modifier.height(2.dp))
                Text(
                    supporting,
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
            }
        }
    }
}
