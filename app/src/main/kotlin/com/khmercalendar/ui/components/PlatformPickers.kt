package com.khmercalendar.ui.components

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.view.ContextThemeWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.khmercalendar.R
import com.khmercalendar.ui.theme.LocalAppSettings
import com.khmercalendar.ui.theme.LocalIsDarkTheme
import java.time.LocalDate
import java.time.LocalTime

/**
 * The platform date and time pickers, themed like the rest of the app.
 *
 * ## Why the platform's own dialogs
 *
 * They are the ones the user already knows, they handle the awkward parts - the 24-hour
 * switch, a spinner on a small screen, TalkBack - and they behave identically back to API 26,
 * which a hand-rolled picker would not.
 *
 * ## Why they need this wrapper
 *
 * A platform dialog inherits the *Activity's* theme, and the Activity theme is a fixed light
 * one because every pixel after the first frame is drawn by Compose. So a user with the app
 * set to dark got a white dialog, and everyone got Material's default teal on a screen with
 * no teal anywhere else. Worse, a resource qualifier cannot fix the first part: `values-night`
 * follows the *system*, and the app has its own light/dark setting that may disagree with it.
 *
 * So the theme is chosen in code from the same flag the Compose theme uses, and the buttons
 * are recoloured afterwards to the user's live accent. The clock face and calendar grid take
 * their colour from a theme attribute resolved at inflation and keep the default accent.
 */
class PlatformPickers internal constructor(
    private val context: Context,
    private val accent: Int,
    private val uses24Hour: Boolean,
) {

    fun date(initial: LocalDate, onPicked: (LocalDate) -> Unit) {
        runCatching {
            DatePickerDialog(
                context,
                { _, year, month, day -> onPicked(LocalDate.of(year, month + 1, day)) },
                initial.year,
                initial.monthValue - 1,
                initial.dayOfMonth,
            ).showTinted()
        }
    }

    /** Honours the app's 12/24-hour preference, not just the device's. */
    fun time(initial: LocalTime, onPicked: (LocalTime) -> Unit) {
        runCatching {
            TimePickerDialog(
                context,
                { _, hour, minute -> onPicked(LocalTime.of(hour, minute)) },
                initial.hour,
                initial.minute,
                uses24Hour,
            ).showTinted()
        }
    }

    /**
     * The buttons carry the accent; everything else keeps the theme's.
     *
     * They only exist once the dialog has been shown, so this cannot be done before [show].
     */
    private fun AlertDialog.showTinted() {
        show()
        for (which in intArrayOf(
            AlertDialog.BUTTON_POSITIVE,
            AlertDialog.BUTTON_NEGATIVE,
            AlertDialog.BUTTON_NEUTRAL,
        )) {
            runCatching { getButton(which)?.setTextColor(accent) }
        }
    }
}

/** The pickers for the current theme, accent and clock preference. */
@Composable
fun rememberPlatformPickers(): PlatformPickers {
    val context = LocalContext.current
    val settings = LocalAppSettings.current
    val dark = LocalIsDarkTheme.current
    val uses24Hour = LocalUses24Hour.current

    return remember(context, dark, settings.accentArgb, uses24Hour) {
        val style = if (dark) {
            R.style.Theme_KhmerCalendar_Dialog_Dark
        } else {
            R.style.Theme_KhmerCalendar_Dialog_Light
        }
        PlatformPickers(
            context = ContextThemeWrapper(context, style),
            accent = settings.accentArgb,
            uses24Hour = uses24Hour,
        )
    }
}
