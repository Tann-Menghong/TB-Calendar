package com.khmercalendar.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.unit.dp
import com.khmercalendar.ui.theme.Elevation
import com.khmercalendar.ui.theme.Spacing
import com.khmercalendar.core.khmer.KhmerNumerals
import com.khmercalendar.ui.theme.LocalAppSettings

/**
 * Renders digits as Khmer numerals when the user has asked for them.
 *
 * Numerals are a setting rather than a constant because the two scripts are genuinely mixed
 * in Cambodian life: a Khmer date is written ១៥ and a phone number is written 015. Every
 * number the calendar draws goes through here so the choice is honoured consistently.
 */
@Composable
fun localeNumber(value: Any): String = localeNumber(value, LocalAppSettings.current.useKhmerNumerals)

/**
 * The same conversion, with the preference passed in.
 *
 * Text built inside a plain lambda - a `joinToString`, a `buildString` - is outside
 * composition, so the composable form cannot be called there; those callers read the
 * preference once and hand it down.
 */
fun localeNumber(value: Any, khmerNumerals: Boolean): String {
    val text = value.toString()
    return if (khmerNumerals) KhmerNumerals.toKhmer(text) else text
}

@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = Elevation.flat),
    ) {
        Column(Modifier.padding(Spacing.lg), content = content)
    }
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        trailing?.invoke()
    }
}

/**
 * The state every list needs when it has nothing to show.
 *
 * An empty calendar is the normal state on day one, so it gets a real explanation rather
 * than a blank screen the user has to interpret.
 */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(Spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.outline,
        )
        Spacer(Modifier.height(Spacing.md))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(Spacing.xs))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (action != null) {
            Spacer(Modifier.height(Spacing.lg))
            action()
        }
    }
}

@Composable
fun ColorDot(color: Color, size: Int = 10, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(color),
    )
}
