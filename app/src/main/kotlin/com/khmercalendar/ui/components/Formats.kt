package com.khmercalendar.ui.components

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import com.khmercalendar.core.khmer.KhmerNumerals
import com.khmercalendar.core.khmer.KhmerTerms
import com.khmercalendar.data.prefs.AppSettings
import com.khmercalendar.data.prefs.DateFormat
import com.khmercalendar.data.prefs.TimeFormat
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Every clock time and written date the app draws.
 *
 * These used to be nine copies of `"%02d:%02d".format(...)` scattered across screens,
 * notifications, widgets and the assistant, which is why a 12-hour preference could not be
 * added without finding all nine. One place now decides, and the preference reaches the
 * calendar views, the event forms, the reminder notifications, the widgets and the
 * assistant's replies alike.
 */
object CalendarFormats {

    /** Resolves [TimeFormat.SYSTEM] against the device setting. */
    fun uses24Hour(context: Context, format: TimeFormat): Boolean = when (format) {
        TimeFormat.H24 -> true
        TimeFormat.H12 -> false
        TimeFormat.SYSTEM -> android.text.format.DateFormat.is24HourFormat(context)
    }

    /**
     * A clock time.
     *
     * The 12-hour form uses the Khmer parts of the day rather than AM/PM, because that is
     * how the time is said: ២ រសៀល, not ２ PM.
     */
    fun time(
        time: LocalTime,
        use24Hour: Boolean,
        khmerNumerals: Boolean,
    ): String = if (use24Hour) {
        digits("%02d:%02d".format(time.hour, time.minute), khmerNumerals)
    } else {
        val hour12 = when (val h = time.hour % 12) {
            0 -> 12
            else -> h
        }
        val body = if (time.minute == 0) {
            "$hour12"
        } else {
            "%d:%02d".format(hour12, time.minute)
        }
        digits(body, khmerNumerals) + " " + KhmerTerms.partOfDay(time.hour)
    }

    fun timeRange(
        start: LocalTime,
        end: LocalTime,
        use24Hour: Boolean,
        khmerNumerals: Boolean,
    ): String = time(start, use24Hour, khmerNumerals) + " – " + time(end, use24Hour, khmerNumerals)

    /** A numeric date in the user's chosen order. */
    fun date(date: LocalDate, format: DateFormat, khmerNumerals: Boolean): String =
        digits(date.format(DateTimeFormatter.ofPattern(format.pattern)), khmerNumerals)

    /** A date written out in Khmer: ថ្ងៃច័ន្ទ ១៥ មករា ២០២៦. */
    fun longDate(date: LocalDate, khmerNumerals: Boolean): String = buildString {
        append("ថ្ងៃ").append(KhmerTerms.dayOfWeek(date.dayOfWeek)).append(' ')
        append(digits(date.dayOfMonth.toString(), khmerNumerals)).append(' ')
        append(KhmerTerms.solarMonth(date.monthValue)).append(' ')
        append(digits(date.year.toString(), khmerNumerals))
    }

    private fun digits(text: String, khmerNumerals: Boolean): String =
        if (khmerNumerals) KhmerNumerals.toKhmer(text) else text
}

/**
 * The resolved 24-hour preference, for composables.
 *
 * Resolved once near the root rather than per call site, because [TimeFormat.SYSTEM] needs a
 * Context and reading it inside every row would be wasteful and noisy.
 */
val LocalUses24Hour: ProvidableCompositionLocal<Boolean> = compositionLocalOf { true }

/** A clock time formatted from the ambient settings. */
@Composable
fun localeTime(time: LocalTime, settings: AppSettings): String =
    CalendarFormats.time(time, LocalUses24Hour.current, settings.useKhmerNumerals)

@Composable
fun localeTimeRange(start: LocalTime, end: LocalTime, settings: AppSettings): String =
    CalendarFormats.timeRange(start, end, LocalUses24Hour.current, settings.useKhmerNumerals)
