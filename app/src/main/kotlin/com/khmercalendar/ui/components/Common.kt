package com.khmercalendar.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
    compact: Boolean = false,
) {
    if (compact) {
        // For panels that are only a few rows tall. The full state needs about 150dp; the
        // month screen's day panel has less than half that under a six-row grid, and the
        // centred version put the icon on screen and both lines of explanation below the
        // fold - a first launch showed one grey glyph and nothing else.
        Row(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg, vertical = Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(com.khmercalendar.ui.theme.IconSize.md),
            )
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            action?.invoke()
        }
        return
    }
    Column(
        // Was 32dp on all four sides, which pushed the title and message below the fold
        // inside the month screen's day panel - the panel is short and the user saw an icon
        // with no words at all.
        modifier = modifier.fillMaxWidth().padding(horizontal = Spacing.xxl, vertical = Spacing.xl),
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

/**
 * A colour you can pick, in a row of colours you can pick.
 *
 * Two screens drew these by hand as a 32dp or 34dp circle with a bare `clickable` on it, and
 * both had the same pair of problems. The circle *was* the touch target, so it fell short of
 * the 48dp minimum on a control that is already small and round. And nothing carried a label:
 * a screen reader met a row of six identical unnamed nodes, with no way to tell which colour
 * was which or which one was selected. Only the tick on the chosen one was ever announced,
 * which is exactly backwards - you need the labels to choose.
 *
 * The circle keeps its size; the touch target around it does not.
 */
@Composable
fun ColorSwatch(
    color: Color,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    diameter: Int = 32,
) {
    Box(
        modifier
            .size(TOUCH_TARGET.dp)
            .clip(CircleShape)
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(diameter.dp)
                .clip(CircleShape)
                .background(color)
                .border(
                    width = if (selected) 3.dp else 0.dp,
                    color = MaterialTheme.colorScheme.onSurface,
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Icon(
                    Icons.Default.Check,
                    // The row already announces which one is selected, through the
                    // selectable role; repeating it here would say it twice.
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size((diameter * 0.56f).dp),
                )
            }
        }
    }
}

/** Android's minimum comfortable touch target. */
private const val TOUCH_TARGET = 48
