package com.khmercalendar.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp
import com.khmercalendar.data.prefs.AppSettings
import com.khmercalendar.data.prefs.ThemeMode
import com.khmercalendar.ui.components.CalendarFormats
import com.khmercalendar.ui.components.LocalUses24Hour

/** Settings reachable from anywhere in the tree without threading them through every call. */
val LocalAppSettings = staticCompositionLocalOf { AppSettings() }

/**
 * Whether the app is currently drawing dark.
 *
 * Published because it is not derivable further down: the app's own theme setting can
 * disagree with the system's, so `isSystemInDarkTheme()` is the wrong answer for anything
 * outside Compose's own colour scheme - a platform dialog, most of all.
 */
val LocalIsDarkTheme = staticCompositionLocalOf { false }

/**
 * The neutrals of the dark theme.
 *
 * Dark is the designed identity, so these are chosen rather than dimmed: a near-black with a
 * blue bias, panels a step above it, and a hairline that is visible without becoming a box.
 * Material's own dark surfaces are warm grey, which fights an accent this saturated.
 */
private object Night {
    val ground = Color(0xFF0A0A0F)
    val panel = Color(0xFF12121A)
    val sunk = Color(0xFF1C1C2E)
    val line = Color(0xFF2A2A3A)
    val dim = Color(0xFF8B92A5)
    val ink = Color(0xFFE4E7EC)
    val danger = Color(0xFFFF5F7E)
}

/** The neutrals of the light theme, designed as its own thing rather than an inversion. */
private object Day {
    val ground = Color(0xFFF6F7FA)
    val panel = Color(0xFFFFFFFF)
    val sunk = Color(0xFFECEEF3)
    val line = Color(0xFFD9DDE5)
    val dim = Color(0xFF5A6172)
    val ink = Color(0xFF14161C)
    val danger = Color(0xFFBA1A1A)
}

/**
 * The accent, and the two colours that travel with it.
 *
 * The visual identity wants a trio - a green that reads as "go", a cyan beside it, a magenta
 * opposite - but the accent is the user's choice from eight, and hard-coding cyan and magenta
 * would leave seven of those choices sitting next to colours from a different palette.
 *
 * So the partners are derived by rotating hue. The default green produces very nearly the
 * intended cyan and magenta; purple produces a violet and a lime that belong to *it*. One
 * rule, eight coherent palettes, and adding a ninth accent costs nothing.
 */
@androidx.compose.runtime.Immutable
data class AccentTrio(val accent: Color, val alt: Color, val far: Color)

private fun trioFrom(accent: Color): AccentTrio = AccentTrio(
    accent = accent,
    alt = accent.rotateHue(38f),
    far = accent.rotateHue(170f),
)

/** The accent trio for the current theme, for gradients that should follow the user's choice. */
val LocalAccentTrio = staticCompositionLocalOf { trioFrom(Color(0xFF00FF88)) }

/**
 * Gradients built from the live accent.
 *
 * Separate from [Gradients], which carries the fixed, state-meaning ones. These are the
 * decorative pairs - the AI card, the hero wash, a primary button - and they have to follow
 * the accent or the screen splits into two palettes.
 */
object AccentGradients {

    /** The primary pair: accent into its neighbour. Buttons, progress, selected states. */
    @Composable
    @ReadOnlyComposable
    fun primary(): GradientTone {
        val trio = LocalAccentTrio.current
        return GradientTone(trio.accent, trio.alt, onToneFor(trio.accent))
    }

    /** The long sweep across the wheel. The AI surface, and nothing else. */
    @Composable
    @ReadOnlyComposable
    fun ai(): GradientTone {
        val trio = LocalAccentTrio.current
        return GradientTone(trio.alt, trio.far, Color.White)
    }

    /** A low-alpha wash of the accent over the card colour, for hero surfaces. */
    @Composable
    @ReadOnlyComposable
    fun heroWash(): List<Color> {
        val scheme = MaterialTheme.colorScheme
        val trio = LocalAccentTrio.current
        return listOf(
            trio.accent.copy(alpha = 0.16f).compositeOver(scheme.surface),
            trio.alt.copy(alpha = 0.06f).compositeOver(scheme.surface),
            scheme.surface,
        )
    }
}

private fun onToneFor(color: Color): Color =
    if (color.luminance() > 0.45f) Color(0xFF07070C) else Color.White

/**
 * Rotates a colour's hue, keeping saturation and lightness.
 *
 * Via HSL rather than a channel shuffle, because a shuffle changes brightness as well as hue
 * and the partner colours have to stay as legible as the accent they came from.
 */
private fun Color.rotateHue(degrees: Float): Color {
    val hsl = FloatArray(3)
    android.graphics.Color.colorToHSV(toArgb(), hsl)
    hsl[0] = (hsl[0] + degrees).mod(360f)
    return Color(android.graphics.Color.HSVToColor(hsl))
}

/**
 * Typography tuned for Khmer.
 *
 * Khmer stacks diacritics above and below the baseline, so a line height sized for Latin
 * clips them and makes dense screens unreadable. Every style here uses a line height around
 * 1.55x the font size, and [LineHeightStyle] distributes that space to both halves rather
 * than only below - without which the subscript consonants in words like ស្រាពណ៍ collide with
 * the line above.
 *
 * The family is the platform default throughout, including on the countdown. A futuristic
 * display face would be the obvious way to style a timer, and it is the wrong call here:
 * Orbitron and its relatives have no Khmer coverage at all, so the digits would render in one
 * face and the label beneath them in another, and a user reading ០១:៤៨:២២ would get the
 * fallback font anyway. The timer earns its character from size, weight and tabular figures
 * instead - which cost nothing and work in both scripts.
 */
private fun khmerTypography(scale: Float): Typography {
    fun style(size: Float, weight: FontWeight, letterSpacing: Float = 0f) = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = weight,
        fontSize = (size * scale).sp,
        lineHeight = (size * scale * 1.55f).sp,
        letterSpacing = letterSpacing.sp,
        lineHeightStyle = LineHeightStyle(
            alignment = LineHeightStyle.Alignment.Center,
            trim = LineHeightStyle.Trim.None,
        ),
    )

    return Typography(
        displaySmall = style(34f, FontWeight.Normal),
        headlineLarge = style(30f, FontWeight.SemiBold),
        headlineMedium = style(26f, FontWeight.SemiBold),
        headlineSmall = style(22f, FontWeight.SemiBold),
        titleLarge = style(20f, FontWeight.SemiBold),
        titleMedium = style(17f, FontWeight.Medium),
        titleSmall = style(15f, FontWeight.Medium),
        bodyLarge = style(16f, FontWeight.Normal),
        bodyMedium = style(14f, FontWeight.Normal),
        bodySmall = style(12.5f, FontWeight.Normal),
        labelLarge = style(14f, FontWeight.Medium),
        labelMedium = style(12f, FontWeight.Medium),
        labelSmall = style(11f, FontWeight.Medium),
    )
}

/**
 * The styles Material's scale has no slot for.
 *
 * A countdown is not a headline: it is a number that changes every second, and it has to hold
 * still while it does. Tabular figures are what stop the whole string shuffling sideways as
 * the digits tick - without them "០១:៤៨:២២" is a different width from "០១:៤៨:២៣" and the card
 * jitters once a second, which is exactly the kind of motion nobody asked for.
 */
object AppType {

    /** The big timer. One per screen. */
    @Composable
    @ReadOnlyComposable
    fun countdown(): TextStyle = MaterialTheme.typography.displaySmall.copy(
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.5).sp,
        fontFeatureSettings = TABULAR,
    )

    /** A number that stands on its own: a day of the month, a percentage, a total. */
    @Composable
    @ReadOnlyComposable
    fun metric(): TextStyle = MaterialTheme.typography.headlineMedium.copy(
        fontWeight = FontWeight.Bold,
        fontFeatureSettings = TABULAR,
    )

    /**
     * Small, letter-spaced metadata.
     *
     * Latin only, by convention - the letter-spacing that makes a Latin label read as a
     * technical caption pulls Khmer clusters apart and makes them harder to read, not easier.
     */
    @Composable
    @ReadOnlyComposable
    fun techLabel(): TextStyle = MaterialTheme.typography.labelSmall.copy(
        letterSpacing = 1.4.sp,
        fontWeight = FontWeight.SemiBold,
    )

    private const val TABULAR = "tnum"
}

/**
 * Builds a Material 3 scheme around the user's accent colour.
 *
 * Every role is filled. The previous version set thirteen of them and left the rest to
 * Material's baseline, which is a purple - so the navigation bar's selected pill was baseline
 * lavender on top of a green accent, and Quick Actions asked for `tertiary` and got baseline
 * pink. Neither had anything to do with the colour the user had chosen. Deriving the scheme
 * rather than shipping a fixed palette is what makes "custom accent colour" a real feature,
 * and that only holds if the derivation is complete.
 */
private fun schemeFor(accent: Color, dark: Boolean): ColorScheme {
    val trio = trioFrom(accent)
    val onAccent = onToneFor(accent)
    return if (dark) {
        darkColorScheme(
            primary = accent,
            onPrimary = onAccent,
            primaryContainer = accent.copy(alpha = 0.22f).compositeOver(Night.panel),
            onPrimaryContainer = Night.ink,
            inversePrimary = accent.darken(0.35f),

            secondary = trio.alt,
            onSecondary = onToneFor(trio.alt),
            // The navigation pill and the assist chips read this one.
            secondaryContainer = trio.alt.copy(alpha = 0.20f).compositeOver(Night.panel),
            onSecondaryContainer = Night.ink,

            tertiary = trio.far,
            onTertiary = onToneFor(trio.far),
            tertiaryContainer = trio.far.copy(alpha = 0.20f).compositeOver(Night.panel),
            onTertiaryContainer = Night.ink,

            background = Night.ground,
            onBackground = Night.ink,
            surface = Night.panel,
            onSurface = Night.ink,
            surfaceVariant = Night.sunk,
            onSurfaceVariant = Night.dim,
            surfaceTint = accent,
            inverseSurface = Night.ink,
            inverseOnSurface = Night.ground,

            outline = Night.line,
            outlineVariant = Night.line.copy(alpha = 0.6f).compositeOver(Night.panel),
            scrim = Color(0xCC000000),

            error = Night.danger,
            onError = Color(0xFF14040A),
            errorContainer = Night.danger.copy(alpha = 0.20f).compositeOver(Night.panel),
            onErrorContainer = Night.ink,
        )
    } else {
        lightColorScheme(
            primary = accent.darkenForLight(),
            onPrimary = Color.White,
            primaryContainer = accent.copy(alpha = 0.14f).compositeOver(Day.panel),
            onPrimaryContainer = accent.darken(0.5f),
            inversePrimary = accent,

            secondary = trio.alt.darkenForLight(),
            onSecondary = Color.White,
            secondaryContainer = trio.alt.copy(alpha = 0.16f).compositeOver(Day.panel),
            onSecondaryContainer = trio.alt.darken(0.5f),

            tertiary = trio.far.darkenForLight(),
            onTertiary = Color.White,
            tertiaryContainer = trio.far.copy(alpha = 0.14f).compositeOver(Day.panel),
            onTertiaryContainer = trio.far.darken(0.5f),

            background = Day.ground,
            onBackground = Day.ink,
            surface = Day.panel,
            onSurface = Day.ink,
            surfaceVariant = Day.sunk,
            onSurfaceVariant = Day.dim,
            surfaceTint = accent,
            inverseSurface = Day.ink,
            inverseOnSurface = Day.panel,

            outline = Day.line,
            outlineVariant = Day.line.copy(alpha = 0.55f).compositeOver(Day.panel),
            scrim = Color(0x99000000),

            error = Day.danger,
            onError = Color.White,
            errorContainer = Day.danger.copy(alpha = 0.10f).compositeOver(Day.panel),
            onErrorContainer = Day.danger.darken(0.3f),
        )
    }
}

/**
 * Pulls a colour down until it can carry white text.
 *
 * The identity's accent is a neon green. On a dark ground that is exactly right; as a fill
 * behind white text on a *light* ground it is unreadable. Light mode gets the same hue at a
 * lightness that works there, rather than a second palette to maintain.
 */
private fun Color.darkenForLight(): Color {
    var c = this
    var guard = 0
    while (c.luminance() > 0.30f && guard < 12) {
        c = c.darken(0.12f)
        guard++
    }
    return c
}

private fun Color.compositeOver(background: Color): Color = Color(
    red = red * alpha + background.red * (1 - alpha),
    green = green * alpha + background.green * (1 - alpha),
    blue = blue * alpha + background.blue * (1 - alpha),
    alpha = 1f,
)

private fun Color.darken(amount: Float): Color =
    Color(red * (1 - amount), green * (1 - amount), blue * (1 - amount), alpha)

@Composable
fun KhmerCalendarTheme(
    settings: AppSettings,
    content: @Composable () -> Unit,
) {
    val dark = when (settings.themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val context = LocalContext.current
    val scheme = remember(settings.accentArgb, settings.useDynamicColor, dark) {
        val dynamicAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        when {
            settings.useDynamicColor && dynamicAvailable && dark -> dynamicDarkColorScheme(context)
            settings.useDynamicColor && dynamicAvailable -> dynamicLightColorScheme(context)
            else -> schemeFor(Color(settings.accentArgb), dark)
        }
    }
    // Taken from the resolved scheme rather than from the setting, so the trio still follows
    // the wallpaper when the user has dynamic colour switched on.
    val trio = remember(scheme.primary) { trioFrom(scheme.primary) }
    val typography = remember(settings.fontScale) { khmerTypography(settings.fontScale) }

    // TimeFormat.SYSTEM needs a Context, so it is resolved once here rather than at every
    // place a time is drawn.
    val uses24Hour = remember(settings.timeFormat, context) {
        CalendarFormats.uses24Hour(context, settings.timeFormat)
    }

    CompositionLocalProvider(
        LocalAppSettings provides settings,
        LocalUses24Hour provides uses24Hour,
        LocalIsDarkTheme provides dark,
        LocalAccentTrio provides trio,
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = typography,
            shapes = AppShapes,
            content = content,
        )
    }
}

/**
 * The accent choices offered in Appearance settings.
 *
 * The neon green leads because it is the app's own identity; the rest are ordinary colours
 * for people who do not want a neon calendar, and each still generates its own coherent trio
 * through [trioFrom].
 */
val AccentPalette: List<Pair<String, Int>> = listOf(
    "បៃតងណេអុន" to 0xFF00FF88.toInt(),
    "ស៊ីយ៉ាន" to 0xFF00D4FF.toInt(),
    "ខៀវ" to 0xFF2F6FED.toInt(),
    "ស្វាយ" to 0xFF7A4FE0.toInt(),
    "ម៉ាជេនតា" to 0xFFE83FCE.toInt(),
    "បៃតង" to 0xFF1E9E6A.toInt(),
    "ទឹកក្រូច" to 0xFFE0662B.toInt(),
    "ក្រហម" to 0xFFCE3B57.toInt(),
    "ត្នោត" to 0xFF8A6236.toInt(),
    "ប្រផេះ" to 0xFF546070.toInt(),
)

/**
 * What kind of thing a card is about, as a colour.
 *
 * Cards on a dashboard all have the same shape and weight, so the eye needs another way to
 * tell a work card from a holiday card while scanning. Category colour does that - but it is
 * never the *only* signal: every card also carries a heading in words, because colour alone
 * fails for a good share of users and fails completely for a screen reader.
 *
 * Most of these follow the user's accent through the derived trio, so the palette stays
 * coherent whichever accent is chosen. [HOLIDAY] is the exception and is deliberately fixed:
 * a Cambodian public holiday is a cultural thing rather than a system state, and gold is what
 * it is regardless of the app's accent. Making it neon-whatever would be the moment the
 * futuristic styling started overwriting the culture it is meant to serve.
 */
enum class CardAccent { CALENDAR, WORK, TASKS, AI, HOLIDAY, WARNING, NEUTRAL }

/** Resolves a [CardAccent] against the live scheme. */
@Composable
@ReadOnlyComposable
fun CardAccent.color(): Color {
    val scheme = MaterialTheme.colorScheme
    val trio = LocalAccentTrio.current
    val dark = LocalIsDarkTheme.current
    return when (this) {
        CardAccent.CALENDAR -> trio.alt
        CardAccent.WORK -> scheme.primary
        CardAccent.TASKS -> scheme.primary
        CardAccent.AI -> trio.far
        CardAccent.HOLIDAY -> if (dark) Gold.bright else Gold.deep
        CardAccent.WARNING -> if (dark) Amber.bright else Amber.deep
        CardAccent.NEUTRAL -> scheme.onSurfaceVariant
    }
}

/** The one colour in the app that does not follow the accent. */
private object Gold {
    val bright = Color(0xFFF0C24B)
    val deep = Color(0xFF8A6414)
}

private object Amber {
    val bright = Color(0xFFFFCC33)
    val deep = Color(0xFF8A5A00)
}
