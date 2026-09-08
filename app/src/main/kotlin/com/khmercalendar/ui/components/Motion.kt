package com.khmercalendar.ui.components

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import com.khmercalendar.core.khmer.KhmerNumerals
import com.khmercalendar.data.prefs.AnimationLevel
import com.khmercalendar.ui.theme.LocalAppSettings
import com.khmercalendar.ui.theme.Motion
import kotlin.math.roundToInt

/**
 * One gate for every animation in the app.
 *
 * ## Why a gate rather than a convention
 *
 * Two things can ask for less movement: the system's accessibility switch, and the app's own
 * animation-level setting. A convention that each animation should "check reduced motion"
 * would be honoured in the places somebody remembered and quietly broken everywhere else, and
 * the failure is invisible in review - the animation simply runs for a user who asked it not
 * to.
 *
 * So durations come from here or they do not exist. [durationOf] returns zero whenever either
 * signal says so, which turns a `tween` into an instant assignment: the state still changes,
 * it just arrives rather than travels. That is the right behaviour for reduced motion, which
 * is about movement rather than about information.
 */
object AppMotion {

    /** The multiplier in force: the user's level, unless the system has overridden it. */
    @Composable
    @ReadOnlyComposable
    fun scale(): Float {
        val level = LocalAppSettings.current.animationLevel
        // The system switch wins. Someone who has turned motion off at the OS level has said
        // so more emphatically than any in-app preference.
        return if (LocalReducedMotion.current) 0f else level.scale
    }

    /** A duration from the [Motion] scale, adjusted for the level in force. */
    @Composable
    @ReadOnlyComposable
    fun durationOf(base: Int): Int = (base * scale()).roundToInt()

    /** A tween over [base] milliseconds, or a snap when motion is off. */
    @Composable
    @ReadOnlyComposable
    fun <T> tweenOf(base: Int, easing: androidx.compose.animation.core.Easing = FastOutSlowInEasing): AnimationSpec<T> {
        val duration = durationOf(base)
        return if (duration <= 0) snap() else tween(durationMillis = duration, easing = easing)
    }

    /** The spec for something entering the screen: slightly softer than a state change. */
    @Composable
    @ReadOnlyComposable
    fun <T> enterOf(base: Int = Motion.MEDIUM, delayMillis: Int = 0): AnimationSpec<T> {
        val duration = durationOf(base)
        if (duration <= 0) return snap()
        return tween(
            durationMillis = duration,
            delayMillis = (delayMillis * scale()).roundToInt(),
            easing = LinearOutSlowInEasing,
        )
    }
}

/**
 * Whether the system has asked for less movement.
 *
 * Provided at the root rather than read per component: it needs a `Context` and a
 * `Settings.Global` lookup, and doing that inside every animated component would be both
 * wasteful and easy to forget.
 */
val LocalReducedMotion = androidx.compose.runtime.staticCompositionLocalOf { false }

/**
 * A number that counts to its new value instead of jumping.
 *
 * Only where a change means something - a task ticked off, an event added. A figure that
 * animates every time it is recomposed is noise, so this animates on *value change* and is
 * inert otherwise.
 */
@Composable
fun animatedCount(value: Int, base: Int = Motion.MEDIUM): Int {
    val animated by animateFloatAsState(
        targetValue = value.toFloat(),
        animationSpec = AppMotion.tweenOf(base),
        label = "count",
    )
    return animated.roundToInt()
}

/** The same, rendered in the user's digits. */
@Composable
fun AnimatedNumberText(
    value: Int,
    modifier: Modifier = Modifier,
    style: androidx.compose.ui.text.TextStyle = androidx.compose.material3.MaterialTheme.typography.headlineMedium,
    color: Color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
    suffix: String = "",
) {
    val shown = animatedCount(value)
    val khmer = LocalAppSettings.current.useKhmerNumerals
    androidx.compose.material3.Text(
        text = (if (khmer) KhmerNumerals.toKhmer(shown.toString()) else shown.toString()) + suffix,
        style = style.copy(fontFeatureSettings = "tnum"),
        color = color,
        modifier = modifier,
        maxLines = 1,
    )
}

/** A colour that crossfades when it changes, so a state transition reads as one. */
@Composable
fun animatedAccent(target: Color, base: Int = Motion.MEDIUM): State<Color> =
    animateColorAsState(
        targetValue = target,
        animationSpec = AppMotion.tweenOf(base),
        label = "accent",
    )

/**
 * Press feedback: the surface dips very slightly under the finger.
 *
 * Three per cent, not ten. The purpose is to confirm that the touch landed on *this* thing,
 * and a card that visibly shrinks reads as a toy. Disabled entirely when motion is reduced,
 * where the ripple already confirms the press.
 */
@Composable
fun Modifier.pressScale(interactionSource: MutableInteractionSource): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = AppMotion.tweenOf(Motion.MICRO),
        label = "pressScale",
    )
    return if (AppMotion.scale() == 0f) this else this.scale(scale)
}

/** A remembered interaction source, for callers that only need one for [pressScale]. */
@Composable
fun rememberPressSource(): MutableInteractionSource = remember { MutableInteractionSource() }

/**
 * The reduced-motion signal, read once.
 *
 * Kept as a function so the root can provide [LocalReducedMotion]; components read the
 * composition local instead of calling this.
 */
@Composable
fun rememberReducedMotion(): Boolean {
    val context = androidx.compose.ui.platform.LocalContext.current
    return remember(context) {
        runCatching {
            android.provider.Settings.Global.getFloat(
                context.contentResolver,
                android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            )
        }.getOrDefault(1f) == 0f
    }
}

/** True when the user has asked for the busiest setting; gates the optional flourishes. */
@Composable
@ReadOnlyComposable
fun enhancedMotion(): Boolean =
    LocalAppSettings.current.animationLevel == AnimationLevel.ENHANCED &&
        !LocalReducedMotion.current
