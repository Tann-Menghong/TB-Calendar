package com.khmercalendar.ui.components

import android.provider.Settings
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.khmercalendar.ui.theme.Radius
import com.khmercalendar.ui.theme.Spacing

/**
 * The shape of content that has not arrived yet.
 *
 * ## Why this exists
 *
 * The dashboard and the calendar both carried an `isLoading` flag that nothing ever read, so
 * the first frame after a cold start was an *empty state* - "គ្មានព្រឹត្តិការណ៍ថ្ងៃនេះ" - which
 * then flipped to a full screen a moment later. Telling a user they have nothing on and then
 * correcting yourself is worse than saying nothing: the empty state is a claim, and for those
 * few frames it is a false one.
 *
 * A skeleton makes the same wait honest. It also holds the layout still, so the content does
 * not jump when it lands.
 */
@Composable
fun SkeletonBlock(
    height: Dp,
    modifier: Modifier = Modifier,
    widthFraction: Float = 1f,
) {
    val alpha = skeletonAlpha()
    Column(
        modifier
            .fillMaxWidth(widthFraction)
            .height(height)
            .clip(RoundedCornerShape(Radius.sm))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)),
    ) {}
}

/**
 * A card-shaped placeholder: a heading line and a couple of body lines.
 *
 * Sized to the cards it stands in for, so the dashboard does not visibly resize as each one
 * resolves.
 */
@Composable
fun SkeletonCard(
    modifier: Modifier = Modifier,
    lines: Int = 2,
) {
    SurfaceCard(
        modifier.semantics { contentDescription = "កំពុងផ្ទុក" },
    ) {
        SkeletonBlock(height = 14.dp, widthFraction = 0.38f)
        repeat(lines) {
            androidx.compose.foundation.layout.Spacer(Modifier.height(Spacing.md))
            SkeletonBlock(height = 12.dp, widthFraction = if (it == lines - 1) 0.62f else 0.9f)
        }
    }
}

/** The whole dashboard, before any of it has loaded. */
@Composable
fun DashboardSkeleton(modifier: Modifier = Modifier) {
    Column(modifier.padding(horizontal = 0.dp)) {
        SkeletonCard(lines = 2)
        androidx.compose.foundation.layout.Spacer(Modifier.height(Spacing.md))
        SkeletonCard(lines = 3)
        androidx.compose.foundation.layout.Spacer(Modifier.height(Spacing.md))
        SkeletonCard(lines = 2)
    }
}

/**
 * The pulse, or a flat tint when the user has asked for less motion.
 *
 * A shimmer that cannot be turned off is an accessibility problem, and the placeholder still
 * reads perfectly well without it.
 *
 * The signal is the system animator duration scale, which is what "Remove animations" in
 * accessibility settings and the developer-options animation scales both write to. Compose has
 * no reduced-motion flag of its own, and an earlier version of this checked whether
 * `LocalAccessibilityManager` was null - which is never true in a running app, so the shimmer
 * ran regardless of what the user had asked for.
 */
@Composable
private fun skeletonAlpha(): Float {
    val context = LocalContext.current
    val animationsOff = remember(context) {
        runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            )
        }.getOrDefault(1f) == 0f
    }
    if (animationsOff) return 0.09f

    val transition = rememberInfiniteTransition(label = "skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.06f,
        targetValue = 0.13f,
        animationSpec = infiniteRepeatable(
            animation = tween(900),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "skeletonAlpha",
    )
    return alpha
}
