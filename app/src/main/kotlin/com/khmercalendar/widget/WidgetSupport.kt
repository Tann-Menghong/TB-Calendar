package com.khmercalendar.widget

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import androidx.compose.ui.graphics.Color
import androidx.glance.appwidget.updateAll
import androidx.glance.unit.ColorProvider
import com.khmercalendar.AppContainer
import com.khmercalendar.KhmerCalendarApp
import com.khmercalendar.MainActivity
import com.khmercalendar.data.prefs.AppSettings
import com.khmercalendar.data.prefs.ThemeMode
import com.khmercalendar.core.khmer.KhmerNumerals
import com.khmercalendar.ui.components.CalendarFormats
import java.time.LocalDate
import java.time.LocalTime

/** The container, reached from a widget's own context. */
internal val Context.container: AppContainer
    get() = (applicationContext as KhmerCalendarApp).container

/**
 * A widget's resolved palette.
 *
 * Glance draws into the launcher's process, where the app's Material theme is unavailable, so
 * the few colours a widget needs are resolved here from the user's widget preference rather
 * than inherited.
 */
internal data class WidgetPalette(
    val background: ColorProvider,
    val surface: ColorProvider,
    val onSurface: ColorProvider,
    val muted: ColorProvider,
    val accent: ColorProvider,
    val onAccent: ColorProvider,
)

internal fun paletteFor(context: Context, settings: AppSettings): WidgetPalette {
    val dark = when (settings.widgetTheme) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> (
            context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
            ) == Configuration.UI_MODE_NIGHT_YES
    }
    val alpha = settings.widgetOpacity.coerceIn(0.2f, 1f)
    val accent = Color(settings.accentArgb)
    return if (dark) {
        WidgetPalette(
            background = ColorProvider(Color(0xFF15181F).copy(alpha = alpha)),
            surface = ColorProvider(Color(0xFF222732).copy(alpha = alpha)),
            onSurface = ColorProvider(Color(0xFFECEFF5)),
            muted = ColorProvider(Color(0xFF9AA2B2)),
            accent = ColorProvider(accent),
            onAccent = ColorProvider(Color.White),
        )
    } else {
        WidgetPalette(
            background = ColorProvider(Color.White.copy(alpha = alpha)),
            surface = ColorProvider(Color(0xFFEFF2F8).copy(alpha = alpha)),
            onSurface = ColorProvider(Color(0xFF11151C)),
            muted = ColorProvider(Color(0xFF636B7B)),
            accent = ColorProvider(accent),
            onAccent = ColorProvider(Color.White),
        )
    }
}

/**
 * Gives each widget intent its own data URI.
 *
 * Two intents that differ only in their extras compare equal for PendingIntent purposes, so
 * without this every day cell in the month widget would open whichever day was tapped first.
 */
internal fun Intent.uniquely(tag: String): Intent =
    setData(Uri.parse("khmercalendar://widget/$tag"))

internal fun dayIntent(context: Context, date: LocalDate): Intent =
    MainActivity.dayIntent(context, date).uniquely("day/$date")

internal fun newEventIntent(context: Context, date: LocalDate): Intent =
    MainActivity.newEventIntent(context, date).uniquely("new/$date")

internal fun eventIntent(context: Context, eventId: Long, date: LocalDate): Intent =
    MainActivity.eventIntent(context, eventId, date).uniquely("event/$eventId/$date")

/** Formats a number the way the rest of the app does. */
internal fun widgetNumber(value: Int, khmerNumerals: Boolean): String =
    if (khmerNumerals) KhmerNumerals.toKhmer(value) else value.toString()

internal fun widgetTime(context: Context, time: LocalTime, settings: AppSettings): String =
    CalendarFormats.time(
        time = time,
        use24Hour = CalendarFormats.uses24Hour(context, settings.timeFormat),
        khmerNumerals = settings.useKhmerNumerals,
    )

/** Redraws both widgets. Safe to call when neither has been placed. */
internal suspend fun refreshWidgets(context: Context) {
    runCatching { MonthWidget.updateAll(context) }
    runCatching { AgendaWidget.updateAll(context) }
}
