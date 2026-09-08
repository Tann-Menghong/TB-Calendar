package com.khmercalendar.data.prefs

import com.khmercalendar.core.khmer.CalendarWeek
import com.khmercalendar.core.work.WorkSchedule
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
/**
 * How a date is written when it is shown on its own.
 *
 * [KHMER] is the default and the app's own voice - ១៥ មករា ២០២៦. The numeric forms exist for
 * people who prefer a compact date, and are the reason the enum carries a pattern at all.
 */
enum class DateFormat(val labelKm: String, val pattern: String) {
    KHMER("ថ្ងៃ ខែ ឆ្នាំ (ខ្មែរ)", "d MMMM yyyy"),
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

/**
 * How much room the dashboard cards take.
 *
 * Separate from [CalendarDensity], which sizes the month grid's cells. This one changes the
 * padding inside a dashboard card and the gap between cards, so someone who wants to see five
 * cards without scrolling can, and someone who finds that cramped is not forced into it.
 */
enum class DashboardDensity(val cardPaddingDp: Int, val gapDp: Int, val labelKm: String) {
    COMPACT(12, 8, "តូច"),
    COMFORTABLE(16, 12, "មធ្យម"),
    SPACIOUS(20, 16, "ធំ"),
}

/**
 * How much the interface is allowed to move.
 *
 * [MINIMAL] is not "off with the decoration on" - it collapses every duration to zero, so
 * state changes still happen and simply arrive rather than travel. That is the same behaviour
 * the system's own reduced-motion switch produces, which is deliberate: this setting exists
 * for people who want less movement without turning it off system-wide.
 */
enum class AnimationLevel(val scale: Float, val labelKm: String) {
    MINIMAL(0f, "តិចបំផុត"),
    STANDARD(1f, "ធម្មតា"),
    ENHANCED(1.3f, "ច្រើន"),
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
/**
 * The dashboard's modules, in their default order.
 *
 * The order here is the design: now, then today, then what is ahead. Entries are appended to
 * a saved order rather than replacing it - see [SettingsStore] - so adding a module cannot
 * discard a layout someone has already arranged.
 */
enum class DashboardCard(val key: String, val labelKm: String) {
    TODAY("today", "ថ្ងៃនេះ"),
    WORK("work", "ស្ថានភាពការងារ"),
    STATS("stats", "បន្ទាប់ និងវឌ្ឍនភាព"),
    TIMELINE("timeline", "កាលវិភាគថ្ងៃនេះ"),
    WEEK("week", "ទិដ្ឋភាពសប្តាហ៍"),
    TASKS("tasks", "កិច្ចការសំខាន់"),
    LUNAR("lunar", "ចន្ទគតិខ្មែរ"),
    UPCOMING("upcoming", "ព្រឹត្តិការណ៍ខាងមុខ"),
    HOLIDAYS("holidays", "បុណ្យជាតិខាងមុខ"),
    AI("ai", "ជំនួយការ AI"),
    COUNTDOWN("countdown", "រាប់ថយក្រោយ"),
    NOTE("note", "កំណត់ចំណាំថ្ងៃនេះ"),

    /**
     * Superseded by the dock at the foot of the dashboard.
     *
     * Kept so that a saved order containing it still decodes; it simply draws nothing.
     */
    QUICK_ACTIONS("quick", "សកម្មភាពរហ័ស"),
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
    /**
     * The app's own identity colour, and the first entry in [com.khmercalendar.ui.theme.AccentPalette].
     *
     * Every other accent still produces a complete, coherent scheme - the secondary and
     * tertiary are derived from whatever is chosen - so this is a default rather than an
     * assumption baked into the screens.
     */
    val accentArgb: Int = 0xFF00FF88.toInt(),
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
    /**
     * Default is [DateFormat.KHMER], which is what every screen already drew while this
     * setting was being ignored. Anyone who never touched it sees no change; anyone who did
     * pick a numeric format now finally gets it.
     */
    val dateFormat: DateFormat = DateFormat.KHMER,
    /** Start of the working day, used by free-slot suggestions and the week grid. */
    val dayStartHour: Int = 8,
    /** End of the working day. Always later than [dayStartHour]. */
    val dayEndHour: Int = 18,

    val startScreen: StartScreen = StartScreen.HOME,
    val defaultReminderMinutes: Int = 30,
    val defaultEventDurationMinutes: Int = 60,
    val dashboardCards: List<DashboardCard> = DashboardCard.entries.toList(),
    val hiddenDashboardCards: Set<String> = emptySet(),
    val dashboardDensity: DashboardDensity = DashboardDensity.COMFORTABLE,
    val animationLevel: AnimationLevel = AnimationLevel.STANDARD,

    // --- work schedule ---
    /**
     * The working week, decoded from its stored form.
     *
     * Held as the real object rather than the encoded string so no screen has to know the
     * storage format; [com.khmercalendar.core.work.WorkScheduleCodec] is the only place that
     * does.
     */
    val workSchedule: WorkSchedule = WorkSchedule.DEFAULT,
    val workNotifications: Boolean = false,
    /**
     * Minutes of warning before a shift change, or 0 for none.
     *
     * Off by default. A warning doubles the day's notifications, and a countdown that
     * announces itself too often gets silenced within a week - taking the event reminders the
     * user actually wanted with it, since Android silences per channel.
     */
    val workNotifyLeadMinutes: Int = 0,

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
