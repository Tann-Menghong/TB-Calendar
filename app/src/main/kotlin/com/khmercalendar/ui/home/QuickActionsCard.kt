package com.khmercalendar.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.khmercalendar.ui.components.SectionCard

/**
 * The five things people open the app to do.
 *
 * A row of tinted circles rather than a menu: on a dashboard the user is scanning, an icon
 * with a two-word Khmer caption is faster to hit than a list, and it keeps the whole set on
 * one line even on a small screen.
 */
@Composable
fun QuickActionsCard(
    onAddEvent: () -> Unit,
    onAddTask: () -> Unit,
    onAddNote: () -> Unit,
    onAssistant: () -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SectionCard(modifier) {
        Text(
            "សកម្មភាពរហ័ស",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            QuickAction(Icons.Outlined.Event, "ព្រឹត្តិការណ៍", MaterialTheme.colorScheme.primary, onAddEvent)
            QuickAction(Icons.Outlined.CheckCircle, "កិច្ចការ", MaterialTheme.colorScheme.tertiary, onAddTask)
            QuickAction(Icons.Outlined.EditNote, "កំណត់ចំណាំ", MaterialTheme.colorScheme.secondary, onAddNote)
            QuickAction(Icons.Outlined.AutoAwesome, "ជំនួយការ", MaterialTheme.colorScheme.primary, onAssistant)
            QuickAction(Icons.Outlined.Search, "ស្វែងរក", MaterialTheme.colorScheme.secondary, onSearch)
        }
    }
}

@Composable
private fun QuickAction(
    icon: ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 6.dp),
    ) {
        Box(
            Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(tint.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.height(4.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
