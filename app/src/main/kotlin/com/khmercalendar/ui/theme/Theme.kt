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
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp
import com.khmercalendar.data.prefs.AppSettings
import com.khmercalendar.data.prefs.ThemeMode

/** Settings reachable from anywhere in the tree without threading them through every call. */
val LocalAppSettings = staticCompositionLocalOf { AppSettings() }

/**
 * Typography tuned for Khmer.
 *
 * Khmer stacks diacritics above and below the baseline, so a line height sized for Latin
 * clips them and makes dense screens unreadable. Every style here uses a line height around
 * 1.55x the font size, and [LineHeightStyle] distributes that space to both halves rather
 * than only below - without which the subscript consonants in words like ស្រាពណ៍ collide with
 * the line above.
 *
 * The family is the platform default on purpose: Android has shipped a Khmer face since
 * API 21 and the system one is the face users already read everywhere else on their phone.
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
 * Builds a Material 3 scheme around the user's accent colour.
 *
 * Deriving the scheme rather than shipping a fixed palette is what makes "custom accent
 * colour" a real feature instead of a recoloured button: the accent flows through selection,
 * the today marker, chips and the FAB together.
 */
private fun schemeFor(accent: Color, dark: Boolean): ColorScheme {
    val onAccent = if (accent.luminance() > 0.5f) Color(0xFF10131A) else Color.White
    return if (dark) {
        darkColorScheme(
            primary = accent,
            onPrimary = onAccent,
            primaryContainer = accent.copy(alpha = 0.30f).compositeOverDark(),
            onPrimaryContainer = Color(0xFFE7EDF7),
            secondary = accent.copy(alpha = 0.85f).compositeOverDark(),
            background = Color(0xFF101216),
            onBackground = Color(0xFFE4E7EC),
            surface = Color(0xFF15181D),
            onSurface = Color(0xFFE4E7EC),
            surfaceVariant = Color(0xFF232830),
            onSurfaceVariant = Color(0xFFB8BFCA),
            outline = Color(0xFF39404B),
            outlineVariant = Color(0xFF2A303A),
            error = Color(0xFFF2807C),
        )
    } else {
        lightColorScheme(
            primary = accent,
            onPrimary = onAccent,
            primaryContainer = accent.copy(alpha = 0.14f).compositeOverLight(),
            onPrimaryContainer = accent.darken(0.45f),
            secondary = accent.darken(0.15f),
            background = Color(0xFFF7F8FA),
            onBackground = Color(0xFF1A1C20),
            surface = Color.White,
            onSurface = Color(0xFF1A1C20),
            surfaceVariant = Color(0xFFEDEFF3),
            onSurfaceVariant = Color(0xFF565C66),
            outline = Color(0xFFC7CCD4),
            outlineVariant = Color(0xFFE1E4EA),
            error = Color(0xFFBA1A1A),
        )
    }
}

private fun Color.compositeOverDark(): Color = Color(
    red = red * alpha + 0.08f * (1 - alpha),
    green = green * alpha + 0.09f * (1 - alpha),
    blue = blue * alpha + 0.11f * (1 - alpha),
    alpha = 1f,
)

private fun Color.compositeOverLight(): Color = Color(
    red = red * alpha + 1f * (1 - alpha),
    green = green * alpha + 1f * (1 - alpha),
    blue = blue * alpha + 1f * (1 - alpha),
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
    val typography = remember(settings.fontScale) { khmerTypography(settings.fontScale) }

    CompositionLocalProvider(LocalAppSettings provides settings) {
        MaterialTheme(colorScheme = scheme, typography = typography, content = content)
    }
}

/** The accent choices offered in Appearance settings. */
val AccentPalette: List<Pair<String, Int>> = listOf(
    "ខៀវ" to 0xFF2F6FED.toInt(),
    "ស្វាយ" to 0xFF7A4FE0.toInt(),
    "បៃតង" to 0xFF1E9E6A.toInt(),
    "ទឹកក្រូច" to 0xFFE0662B.toInt(),
    "ក្រហម" to 0xFFCE3B57.toInt(),
    "ផ្កាឈូក" to 0xFFD9457F.toInt(),
    "ត្នោត" to 0xFF8A6236.toInt(),
    "ប្រផេះ" to 0xFF546070.toInt(),
)
