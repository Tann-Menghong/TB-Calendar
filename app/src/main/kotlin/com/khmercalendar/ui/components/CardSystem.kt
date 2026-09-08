package com.khmercalendar.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.khmercalendar.ui.theme.AccentGradients
import com.khmercalendar.ui.theme.GradientTone
import com.khmercalendar.ui.theme.IconSize
import com.khmercalendar.ui.theme.Radius
import com.khmercalendar.ui.theme.Spacing
import com.khmercalendar.ui.theme.Stroke
import com.khmercalendar.ui.theme.TechShape

/**
 * The card system.
 *
 * ## Why one file
 *
 * Before this, a "card" was whatever each screen happened to write: a `Card` here, a `Box`
 * with a `clip` and a `background` there, paddings of 10, 12 and 16dp on three screens that
 * sit next to each other in the navigation bar. The result was a set of surfaces that were
 * *nearly* the same, which reads worse than surfaces that are obviously different - the eye
 * notices the 2dp, cannot name it, and the app feels unfinished.
 *
 * These are the variants the app is allowed to draw. Each one owns its padding, radius,
 * border and elevation, so a new screen inherits the design rather than re-deciding it.
 *
 * ## The three weights
 *
 * [SurfaceCard] is the default and carries most content. [HeroCard] is for the one surface on
 * a screen that is the answer to why the user opened it. [TechCard] is angular and outlined,
 * and is reserved for surfaces reporting on the machine rather than on the user's day - the
 * AI panel, a system status. Keeping that third one rare is what stops the futuristic styling
 * from turning the calendar into a control panel.
 */

/** The default card: flat, filled, rounded. Most content lives in one of these. */
@Composable
fun SurfaceCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    padding: androidx.compose.ui.unit.Dp = Spacing.lg,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(Radius.md)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(padding),
        content = content,
    )
}

/**
 * The one card on a screen that answers why the user opened it.
 *
 * A wash of the accent rather than a solid fill: the hero has to carry ordinary text at
 * ordinary contrast, and a saturated block behind body copy is the fastest way to make a
 * dashboard unreadable. The colour says "this one first"; it does not shout.
 */
@Composable
fun HeroCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(Radius.lg)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.verticalGradient(AccentGradients.heroWash()))
            .border(Stroke.hairline, MaterialTheme.colorScheme.outlineVariant, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(Spacing.xl),
        content = content,
    )
}

/**
 * A card filled with a state-carrying gradient.
 *
 * [tone] brings its own legible foreground colour, so callers never have to guess whether
 * white or near-black survives on top of it.
 */
@Composable
fun GradientCard(
    tone: GradientTone,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(Radius.lg),
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.linearGradient(tone.colors))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(Spacing.xl),
        content = content,
    )
}

/**
 * The angular, outlined surface.
 *
 * Two corners cut and a hairline in the accent, over a nearly-flat fill. Reserved for the AI
 * panel and system status - the places where the app is talking about itself. A calendar full
 * of these would be a control panel, which is the failure mode the brief explicitly warns
 * against, so there is deliberately no variant of this for events or days.
 */
@Composable
fun TechCard(
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.secondary,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(TechShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(Stroke.hairline, accent.copy(alpha = 0.45f), TechShape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(Spacing.lg),
        content = content,
    )
}

/**
 * A short state label.
 *
 * Carries its meaning in text as well as colour, because colour alone is not an accessible
 * indicator and a badge that only differs by hue is invisible to a good share of users.
 */
@Composable
fun StatusBadge(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    onColor: Color = MaterialTheme.colorScheme.onPrimary,
    solid: Boolean = false,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = if (solid) onColor else color,
        modifier = modifier
            .clip(RoundedCornerShape(Radius.sm))
            .background(if (solid) color else color.copy(alpha = 0.16f))
            .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
    )
}

/**
 * A number with a caption under it.
 *
 * Used in rows of two or three. The number is the content and the caption is the label, which
 * is the opposite of how these usually get built - a row of equal-weight pairs reads as a
 * table and nobody scans a table on a dashboard.
 */
@Composable
fun MetricTile(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurface,
) {
    Column(modifier) {
        Text(
            text = value,
            style = com.khmercalendar.ui.theme.AppType.metric(),
            color = tint,
            maxLines = 1,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
        )
    }
}

/**
 * A heading above a group, with an optional action on the right.
 *
 * The accent rule on the left is the one piece of decoration here, and it is doing a job:
 * a dashboard is a stack of cards of similar weight, and a heading with no anchor reads as
 * another line of text rather than as the start of something.
 */
@Composable
fun SectionTitle(
    title: String,
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.primary,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(width = 3.dp, height = 14.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(accent),
            )
            Spacer(Modifier.width(Spacing.sm))
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        trailing?.invoke()
    }
}

/**
 * An icon in a tinted rounded square.
 *
 * The shared shape behind Quick Actions and the metadata rows, so the tile never gets
 * re-invented at a slightly different size on the next screen.
 */
@Composable
fun IconTile(
    icon: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.primary,
    size: androidx.compose.ui.unit.Dp = 44.dp,
) {
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(Radius.md))
            .background(tint.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(IconSize.tile))
    }
}

/** A hairline the width of the card, at the card's own border colour. */
@Composable
fun HairlineDivider(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(Stroke.hairline)
            .background(MaterialTheme.colorScheme.outlineVariant),
    )
}

/**
 * The floating action button, in the accent rather than in its container tint.
 *
 * Material's default fills a FAB with `primaryContainer`, which in this scheme is the accent
 * at 22% over the panel colour - a deliberately quiet fill that works behind text and reads
 * as a dark hole when it is the primary action floating over a bright card. The accent itself
 * is the point of a FAB, so that is what it gets, and every screen with one gets the same.
 */
@Composable
fun PrimaryFab(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    androidx.compose.material3.FloatingActionButton(
        onClick = onClick,
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
    ) {
        Icon(icon, contentDescription = contentDescription)
    }
}

/**
 * Whether the user has asked the system for less movement.
 *
 * Compose has no reduced-motion flag of its own, so this reads the animator duration scale -
 * which is what both "Remove animations" in accessibility settings and the developer-options
 * animation scales write to. Every animation in the app that is decoration rather than
 * feedback checks this, so a single system switch turns them all off.
 */
@Composable
fun rememberReducedMotion(): Boolean {
    val context = androidx.compose.ui.platform.LocalContext.current
    return androidx.compose.runtime.remember(context) {
        runCatching {
            android.provider.Settings.Global.getFloat(
                context.contentResolver,
                android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            )
        }.getOrDefault(1f) == 0f
    }
}

/**
 * A flat progress bar.
 *
 * Drawn rather than themed so it can sit on a gradient card as readily as on a plain one, and
 * so the track and indicator are the caller's choice. Animates to its value unless the user
 * has asked for less motion, in which case it simply arrives there.
 */
@Composable
fun ProgressBar(
    fraction: Float,
    modifier: Modifier = Modifier,
    track: Color = MaterialTheme.colorScheme.surfaceVariant,
    indicator: Color = MaterialTheme.colorScheme.primary,
    height: androidx.compose.ui.unit.Dp = 6.dp,
) {
    val target = fraction.coerceIn(0f, 1f)
    val reduced = rememberReducedMotion()
    val animated by androidx.compose.animation.core.animateFloatAsState(
        targetValue = target,
        animationSpec = androidx.compose.animation.core.tween(
            durationMillis = if (reduced) 0 else com.khmercalendar.ui.theme.Motion.PROGRESS,
        ),
        label = "progress",
    )
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(height / 2))
            .background(track),
    ) {
        Box(
            Modifier
                .fillMaxWidth(animated)
                .height(height)
                .clip(RoundedCornerShape(height / 2))
                .background(indicator),
        )
    }
}
