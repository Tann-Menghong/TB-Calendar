package com.khmercalendar.data.prefs

import com.khmercalendar.core.khmer.CalendarWeek
import java.time.DayOfWeek

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * How a clock time is written.
 *
 * [SYSTEM] follows the device setting, which is what most people expect and what keeps the
 * app consistent with the notification shade. The explicit choices exist because Cambodia
 * uses both conventions and a shared phone often has the "wrong" one set.
 */
enum class TimeFormat(val labelKm: String) {
    SYSTEM("តាមប្រព័ន្ធ"),
    H24("២៤ ម៉ោង"),
    H12("១២ ម៉ោង"),
}

/** The order of the parts of a written date. */
enum class DateFormat(val labelKm: String, val pattern: String) {
    DMY("ថ្ងៃ/ខែ/ឆ្នាំ", "dd/MM/yyyy"),
    YMD("ឆ្នាំ-ខែ-ថ្ងៃ", "yyyy-MM-dd"),
    MDY("ខែ/ថ្ងៃ/ឆ្នាំ", "MM/dd/yyyy"),
}

/** How dense the month grid is drawn. */
enum class CalendarDensity(val cellHeightDp: Int, val labelKm: String) {
    COMPACT(46, "តូច"),
    COMFORTABLE(58, "មធ្យម"),
    SPACIOUS(72, "ធំ"),
}

/** Which screen the app opens on. */
enum class StartScreen(val route: String, val labelKm: String) {
    HOME("home", "ផ្ទាំងដើម"),
    MONTH("month", "ប្រតិទិនខែ"),
    WEEK("week", "ប្រតិទិនសប្តាហ៍"),
    DAY("day", "ប្រតិទិនថ្ងៃ"),
    AGENDA("agenda", "កាលវិភាគ"),
}

/** A card on the dashboard the user can show, hide or reorder. */
enum class DashboardCard(val key: String, val labelKm: String) {
    TODAY("today", "ថ្ងៃនេះ"),
    LUNAR("lunar", "ចន្ទគតិខ្មែរ"),
    UPCOMING("upcoming", "ព្រឹត្តិការណ៍ខាងមុខ"),
    HOLIDAYS("holidays", "បុណ្យជាតិខាងមុខ"),
    COUNTDOWN("countdown", "រាប់ថយក្រោយ"),
    TASKS("tasks", "កិច្ចការ"),
    NOTE("note", "កំណត់ចំណាំថ្ងៃនេះ"),
}

/**
 * Everything the user can change.
 *
 * One immutable snapshot rather than a bag of separate flows: the theme, the grid and the
 * dashboard all read from the same value, so a Compose recomposition sees a consistent set
 * and never a half-applied change.
 */
data class AppSettings(
    // --- appearance ---
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val accentArgb: Int = 0xFF2F6FED.toInt(),
    val useDynamicColor: Boolean = false,
    val fontScale: Float = 1.0f,
    val density: CalendarDensity = CalendarDensity.COMFORTABLE,
    val backgroundImageUri: String? = null,
    val backgroundOpacity: Float = 0.12f,

    // --- what the calendar shows ---
    /**
     * The first day of the week, honoured by every view, the week numbers, the widgets and
     * new recurring events alike. Monday by default, following Cambodian and ISO-8601
     * practice; see [com.khmercalendar.core.khmer.CalendarWeek].
     */
    val weekStart: DayOfWeek = CalendarWeek.DEFAULT_START,
    val showKhmerLunarDates: Boolean = true,
    val showGregorianDates: Boolean = true,
    val showHolidays: Boolean = true,
    val showEventDots: Boolean = true,
    val showWeekNumbers: Boolean = false,
    val useKhmerNumerals: Boolean = true,
    val highlightWeekends: Boolean = true,

    // --- behaviour ---
    val timeFormat: TimeFormat = TimeFormat.SYSTEM,
    val dateFormat: DateFormat = DateFormat.DMY,
    /** Start of the working day, used by free-slot suggestions and the week grid. */
    val dayStartHour: Int = 8,
    /** End of the working day. Always later than [dayStartHour]. */
    val dayEndHour: Int = 18,

    val startScreen: StartScreen = StartScreen.HOME,
    val defaultReminderMinutes: Int = 30,
    val defaultEventDurationMinutes: Int = 60,
    val dashboardCards: List<DashboardCard> = DashboardCard.entries.toList(),
    val hiddenDashboardCards: Set<String> = emptySet(),

    // --- home-screen widgets ---
    val widgetTheme: ThemeMode = ThemeMode.SYSTEM,
    val widgetOpacity: Float = 0.92f,
    val widgetShowLunar: Boolean = true,

    // --- notifications ---
    val notificationsEnabled: Boolean = true,
    val notificationSoundUri: String? = null,
    val notificationVibrate: Boolean = true,
    val holidayNotifications: Boolean = false,

    // --- privacy ---
    val appLockEnabled: Boolean = false,
    /** Salted SHA-256 of the PIN. The PIN itself is never stored. */
    val pinHash: String? = null,
    val pinSalt: String? = null,
    val biometricUnlock: Boolean = true,

    // --- on-device AI ---
    val aiEnabled: Boolean = false,
    val aiModelId: String? = null,
    val aiMaxTokens: Int = 512,
    val aiTemperature: Float = 0.2f,
) {
    fun visibleDashboardCards(): List<DashboardCard> =
        dashboardCards.filter { it.key !in hiddenDashboardCards }
}
