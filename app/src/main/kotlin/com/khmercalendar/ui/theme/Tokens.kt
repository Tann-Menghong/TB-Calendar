package com.khmercalendar.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * The measurements the app draws with.
 *
 * ## Why these exist
 *
 * Colour and type were already systematic - one accent flows through the whole scheme, one
 * function builds every text style. Everything else was not: ten different corner radii and
 * eleven different paddings had accumulated across the screens, and `MaterialTheme.shapes`
 * was never set at all, so hand-rolled surfaces and Material's own components rounded their
 * corners to different numbers on the same screen.
 *
 * These are deliberately few. A scale with a value for every occasion is the same thing as no
 * scale: the point is that a new screen has an obvious right answer rather than a free choice.
 */
object Spacing {
    /** Between an icon and its label; inside a chip. */
    val xs = 4.dp

    /** Between stacked lines of related text. */
    val sm = 8.dp

    /** Between the rows of a list, and between a heading and its content. */
    val md = 12.dp

    /** The default inset of a card, and the gap between cards. */
    val lg = 16.dp

    /** Around a section that should read as separate from what surrounds it. */
    val xl = 24.dp

    /** Around an empty state, which needs room to not look like a mistake. */
    val xxl = 32.dp
}

/**
 * Corner radii.
 *
 * Fed into [AppShapes] so that Material's own components - cards, chips, dialogs, menus -
 * land on the same numbers as the surfaces drawn by hand, which is what stops a dashboard
 * from looking like it was assembled from two different apps.
 */
object Radius {
    /** Chips, tags, the small square that carries an event colour. */
    val sm = 10.dp

    /** Cards and list rows: the default. */
    val md = 16.dp

    /** A card that is the subject of its screen, such as the work countdown. */
    val lg = 20.dp

    /** Dialogs and bottom sheets. */
    val xl = 28.dp
}

/**
 * Elevation.
 *
 * Almost everything is flat. A tonal surface separates a card from the background more
 * clearly than a shadow does at these sizes, and a screen of drop shadows reads as noise -
 * so height is spent only where something genuinely floats above the content.
 */
object Elevation {
    /** Cards, rows, headers: the default. */
    val flat = 0.dp

    /** Something being dragged, or a menu over content. */
    val raised = 3.dp

    /** The FAB, which has to stay legible over anything it passes across. */
    val floating = 6.dp
}

/** [Radius] as Material's shape scale, so every built-in component inherits it. */
val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(Radius.sm),
    medium = RoundedCornerShape(Radius.md),
    large = RoundedCornerShape(Radius.lg),
    extraLarge = RoundedCornerShape(Radius.xl),
)

/**
 * A two-stop gradient and the colour that stays legible on it.
 *
 * Gradients are used for one job only: telling the user at a glance which of a handful of
 * states something is in - green is working, amber is a break, slate is done. Two stops in
 * one hue family, never a blend across the wheel, so a screen with two of them still reads
 * as one design rather than a paint chart.
 */
data class GradientTone(val from: Color, val to: Color, val onTone: Color = Color.White) {
    val colors: List<Color> get() = listOf(from, to)
}

/**
 * The gradients the app is allowed to use.
 *
 * Named for meaning rather than for colour, so a card asks for [working] and not for "green";
 * restyling a state is then one edit here instead of a search for a hex value.
 */
object Gradients {
    val working = GradientTone(Color(0xFF1E9E6A), Color(0xFF12795A))
    val resting = GradientTone(Color(0xFFE0902B), Color(0xFFC96F1E))
    val upcoming = GradientTone(Color(0xFF2F6FED), Color(0xFF2453B8))
    val done = GradientTone(Color(0xFF4A5568), Color(0xFF2D3748))
    val away = GradientTone(Color(0xFF7A4FE0), Color(0xFF5A34B0))
    val muted = GradientTone(Color(0xFF9AA2B2), Color(0xFF7A8294))
}
