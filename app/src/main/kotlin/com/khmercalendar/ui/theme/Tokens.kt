package com.khmercalendar.ui.theme

import androidx.compose.foundation.shape.CutCornerShape
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

    /** Inside a hero card, which needs more room than a list row. */
    val xl = 20.dp

    /** Around a section that should read as separate from what surrounds it. */
    val xxl = 24.dp

    /** Around an empty state, which needs room to not look like a mistake. */
    val huge = 32.dp

    /**
     * Clearance under a scrolling screen that has a floating action button.
     *
     * Not a guess: the FAB is 56dp with 16dp of margin, and on the dashboard it was sitting
     * squarely on top of the last card - the lunar date ran underneath it.
     */
    val fabClearance = 96.dp
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

    /**
     * The bite taken out of a technical surface.
     *
     * Used only by [TechShape]. A cut corner is a strong signal and it stops meaning anything
     * if every card has one - the calendar itself stays rounded, and this marks the few
     * surfaces that are reporting on a system rather than on the user's day.
     */
    val cut = 14.dp
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

/** Line weights, so a hairline is the same hairline everywhere. */
object Stroke {
    /** The border on a card or a tech surface. */
    val hairline = 1.dp

    /** A progress ring or bar. */
    val ring = 6.dp

    /** The bar that carries an event's category colour. */
    val accentBar = 4.dp
}

/** Icon sizes, from the smallest inline glyph to a primary action. */
object IconSize {
    /** Inline with body text. */
    val inline = 16.dp

    /** Inside a chip or a metadata row. */
    val small = 20.dp

    /** The default: navigation, app bar actions, list rows. */
    val md = 24.dp

    /** The tile in Quick Actions. */
    val tile = 22.dp
}

/**
 * How long things take.
 *
 * Short by design. Motion on a dashboard is feedback, not decoration: anything long enough to
 * notice as an animation is long enough to be in the way of someone checking the time.
 */
object Motion {
    /** A press, an icon, a checkbox. Feedback, not transition. */
    const val MICRO = 120

    /** A card appearing, a status colour changing. */
    const val SMALL = 180

    /** A section expanding, a sheet arriving. */
    const val MEDIUM = 260

    /** The dashboard rearranging, a hero collapsing. */
    const val LARGE = 380

    /** A progress ring or bar catching up to a new value. */
    const val PROGRESS = 600

    /** Kept for callers written before the scale had four steps. */
    const val QUICK = MICRO
    const val STANDARD = SMALL

    /** How far apart timeline rows enter, so the list arrives as a sequence. */
    const val STAGGER = 40

    /** The longest a stagger is allowed to run before every remaining row arrives at once. */
    const val STAGGER_CAP = 320
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
 * The angular surface, for the few places that report on a system.
 *
 * Two corners cut, not four: a fully chamfered card reads as a warning sign, and diagonally
 * opposite cuts give the shape a direction. Reserved for the AI card, the work countdown and
 * status badges - the surfaces that are about the machine rather than about the month.
 */
val TechShape = CutCornerShape(
    topStart = 0.dp,
    topEnd = Radius.cut,
    bottomEnd = 0.dp,
    bottomStart = Radius.cut,
)

/**
 * A two-stop gradient and the colour that stays legible on it.
 *
 * Gradients are used for one job only: telling the user at a glance which of a handful of
 * states something is in - green is working, amber is a break, slate is done. Two stops that
 * stay inside one region of the wheel, never a blend across it, so a screen with two of them
 * still reads as one design rather than a paint chart.
 */
data class GradientTone(val from: Color, val to: Color, val onTone: Color = Color.White) {
    val colors: List<Color> get() = listOf(from, to)

    /** The same tone at low alpha, for a wash behind content that has to stay readable. */
    fun wash(alpha: Float): GradientTone =
        copy(from = from.copy(alpha = alpha), to = to.copy(alpha = alpha * 0.4f))
}

/**
 * The gradients the app is allowed to use.
 *
 * Named for meaning rather than for colour, so a card asks for [working] and not for "green";
 * restyling a state is then one edit here instead of a search for a hex value.
 *
 * These are the fixed, state-carrying ones. The gradients that follow the user's accent are
 * built at runtime from the colour scheme - see [com.khmercalendar.ui.theme.AccentGradients].
 */
object Gradients {
    val working = GradientTone(Color(0xFF00C46A), Color(0xFF00907E))
    val resting = GradientTone(Color(0xFFE0902B), Color(0xFFC96F1E))
    val upcoming = GradientTone(Color(0xFF2F6FED), Color(0xFF2453B8))
    val done = GradientTone(Color(0xFF3A4356), Color(0xFF262D3B))
    val away = GradientTone(Color(0xFF7A4FE0), Color(0xFF5A34B0))
    val muted = GradientTone(Color(0xFF9AA2B2), Color(0xFF7A8294))
}
