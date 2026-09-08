package com.khmercalendar.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.khmercalendar.core.khmer.CalendarWeek
import com.khmercalendar.core.work.WorkSchedule
import com.khmercalendar.core.work.WorkScheduleCodec
import com.khmercalendar.domain.DockLayout
import com.khmercalendar.domain.DockSlot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.DayOfWeek

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * Reads and writes [AppSettings].
 *
 * Preferences DataStore rather than SharedPreferences: reads are a Flow, so the theme and the
 * grid react to a change without a listener, and writes are transactional rather than
 * fire-and-forget.
 */
class SettingsStore(context: Context) {

    private val store = context.applicationContext.dataStore

    val settings: Flow<AppSettings> = store.data
        // A corrupt preferences file must not stop the calendar from opening; falling back
        // to defaults loses customisation, which is recoverable, rather than access.
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { it.toSettings() }

    private fun Preferences.toSettings() = AppSettings(
        themeMode = enumOf(this[Keys.THEME], ThemeMode.SYSTEM),
        accentArgb = this[Keys.ACCENT] ?: AppSettings().accentArgb,
        useDynamicColor = this[Keys.DYNAMIC_COLOR] ?: false,
        fontScale = this[Keys.FONT_SCALE] ?: 1.0f,
        density = enumOf(this[Keys.DENSITY], CalendarDensity.COMFORTABLE),
        backgroundImageUri = this[Keys.BACKGROUND_URI],
        backgroundOpacity = this[Keys.BACKGROUND_OPACITY] ?: 0.12f,

        weekStart = CalendarWeek.fromValue(this[Keys.WEEK_START]),
        showKhmerLunarDates = this[Keys.SHOW_LUNAR] ?: true,
        showGregorianDates = this[Keys.SHOW_GREGORIAN] ?: true,
        showHolidays = this[Keys.SHOW_HOLIDAYS] ?: true,
        showEventDots = this[Keys.SHOW_DOTS] ?: true,
        showWeekNumbers = this[Keys.SHOW_WEEK_NUMBERS] ?: false,
        useKhmerNumerals = this[Keys.KHMER_NUMERALS] ?: true,
        highlightWeekends = this[Keys.HIGHLIGHT_WEEKENDS] ?: true,

        timeFormat = enumOf(this[Keys.TIME_FORMAT], TimeFormat.SYSTEM),
        dateFormat = enumOf(this[Keys.DATE_FORMAT], DateFormat.KHMER),
        dayStartHour = this[Keys.DAY_START_HOUR] ?: 8,
        dayEndHour = this[Keys.DAY_END_HOUR] ?: 18,

        startScreen = enumOf(this[Keys.START_SCREEN], StartScreen.HOME),
        defaultReminderMinutes = this[Keys.DEFAULT_REMINDER] ?: 30,
        defaultEventDurationMinutes = this[Keys.DEFAULT_DURATION] ?: 60,
        dashboardCards = this[Keys.DASHBOARD_ORDER]
            ?.split(",")
            ?.mapNotNull { key -> DashboardCard.entries.firstOrNull { it.key == key } }
            ?.takeIf { it.isNotEmpty() }
            // Any card added in a later version is appended rather than lost.
            ?.let { saved -> saved + DashboardCard.entries.filterNot { it in saved } }
            ?: DashboardCard.entries.toList(),
        hiddenDashboardCards = this[Keys.DASHBOARD_HIDDEN] ?: emptySet(),
        dashboardDensity = enumOf(this[Keys.DASHBOARD_DENSITY], DashboardDensity.COMFORTABLE),
        animationLevel = enumOf(this[Keys.ANIMATION_LEVEL], AnimationLevel.STANDARD),
        dockSlots = DockLayout.decode(this[Keys.DOCK_SLOTS]?.split(",").orEmpty()),
        displayName = this[Keys.DISPLAY_NAME].orEmpty(),

        workSchedule = WorkScheduleCodec
            .decode(this[Keys.WORK_SCHEDULE])
            .copy(enabled = this[Keys.WORK_ENABLED] ?: true),
        workNotifications = this[Keys.WORK_NOTIFICATIONS] ?: false,
        workNotifyLeadMinutes = this[Keys.WORK_NOTIFY_LEAD] ?: 0,

        widgetTheme = enumOf(this[Keys.WIDGET_THEME], ThemeMode.SYSTEM),
        widgetOpacity = this[Keys.WIDGET_OPACITY] ?: 0.92f,
        widgetShowLunar = this[Keys.WIDGET_SHOW_LUNAR] ?: true,

        notificationsEnabled = this[Keys.NOTIFICATIONS] ?: true,
        notificationSoundUri = this[Keys.SOUND_URI],
        notificationVibrate = this[Keys.VIBRATE] ?: true,
        holidayNotifications = this[Keys.HOLIDAY_NOTIFICATIONS] ?: false,

        appLockEnabled = this[Keys.LOCK_ENABLED] ?: false,
        pinHash = this[Keys.PIN_HASH],
        pinSalt = this[Keys.PIN_SALT],
        biometricUnlock = this[Keys.BIOMETRIC] ?: true,

        aiEnabled = this[Keys.AI_ENABLED] ?: false,
        aiModelId = this[Keys.AI_MODEL],
        aiMaxTokens = this[Keys.AI_MAX_TOKENS] ?: 512,
        aiTemperature = this[Keys.AI_TEMPERATURE] ?: 0.2f,
    )

    private inline fun <reified T : Enum<T>> enumOf(name: String?, fallback: T): T =
        name?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: fallback

    // --- writes ------------------------------------------------------------------------

    suspend fun setThemeMode(mode: ThemeMode) = put(Keys.THEME, mode.name)
    suspend fun setAccent(argb: Int) = put(Keys.ACCENT, argb)
    suspend fun setDynamicColor(on: Boolean) = put(Keys.DYNAMIC_COLOR, on)
    suspend fun setFontScale(scale: Float) = put(Keys.FONT_SCALE, scale.coerceIn(0.8f, 1.6f))
    suspend fun setDensity(density: CalendarDensity) = put(Keys.DENSITY, density.name)
    suspend fun setBackgroundImage(uri: String?) = putNullable(Keys.BACKGROUND_URI, uri)
    suspend fun setBackgroundOpacity(value: Float) = put(Keys.BACKGROUND_OPACITY, value.coerceIn(0f, 0.6f))

    suspend fun setWeekStart(day: DayOfWeek) = put(Keys.WEEK_START, day.value)
    suspend fun setShowLunar(on: Boolean) = put(Keys.SHOW_LUNAR, on)
    suspend fun setShowGregorian(on: Boolean) = put(Keys.SHOW_GREGORIAN, on)
    suspend fun setShowHolidays(on: Boolean) = put(Keys.SHOW_HOLIDAYS, on)
    suspend fun setShowDots(on: Boolean) = put(Keys.SHOW_DOTS, on)
    suspend fun setShowWeekNumbers(on: Boolean) = put(Keys.SHOW_WEEK_NUMBERS, on)
    suspend fun setKhmerNumerals(on: Boolean) = put(Keys.KHMER_NUMERALS, on)
    suspend fun setHighlightWeekends(on: Boolean) = put(Keys.HIGHLIGHT_WEEKENDS, on)

    suspend fun setTimeFormat(format: TimeFormat) = put(Keys.TIME_FORMAT, format.name)
    suspend fun setDateFormat(format: DateFormat) = put(Keys.DATE_FORMAT, format.name)

    /** Keeps the working day a real interval; an inverted one would break free-slot search. */
    suspend fun setWorkingHours(startHour: Int, endHour: Int) {
        val from = startHour.coerceIn(0, 23)
        val to = endHour.coerceIn(from + 1, 24)
        store.edit {
            it[Keys.DAY_START_HOUR] = from
            it[Keys.DAY_END_HOUR] = to
        }
    }

    suspend fun setStartScreen(screen: StartScreen) = put(Keys.START_SCREEN, screen.name)
    suspend fun setDefaultReminder(minutes: Int) = put(Keys.DEFAULT_REMINDER, minutes)
    suspend fun setDefaultDuration(minutes: Int) = put(Keys.DEFAULT_DURATION, minutes)
    suspend fun setDashboardOrder(cards: List<DashboardCard>) =
        put(Keys.DASHBOARD_ORDER, cards.joinToString(",") { it.key })

    suspend fun setDashboardHidden(keys: Set<String>) = put(Keys.DASHBOARD_HIDDEN, keys)

    /**
     * Writes an order and a hidden set together.
     *
     * Applying a preset or resetting the dashboard changes both, and they are one user action.
     * Two separate writes would let a crash in between leave a module ordered into a position
     * it is also hidden from - recoverable only by finding the editor the user was already in.
     */
    suspend fun setDashboardArrangement(cards: List<DashboardCard>, hidden: Set<String>) {
        store.edit {
            it[Keys.DASHBOARD_ORDER] = cards.joinToString(",") { card -> card.key }
            it[Keys.DASHBOARD_HIDDEN] = hidden
        }
    }

    suspend fun setDashboardDensity(density: DashboardDensity) =
        put(Keys.DASHBOARD_DENSITY, density.name)

    suspend fun setAnimationLevel(level: AnimationLevel) =
        put(Keys.ANIMATION_LEVEL, level.name)

    suspend fun setDockSlots(slots: List<DockSlot>) =
        put(Keys.DOCK_SLOTS, slots.joinToString(",") { it.key })

    /** Trimmed and capped: the header has one line for it, and no screen validates it. */
    suspend fun setDisplayName(name: String) =
        put(Keys.DISPLAY_NAME, name.trim().take(24))

    suspend fun setWorkSchedule(schedule: WorkSchedule) {
        // The blocks and the on/off switch are separate keys but one user action, so they are
        // written in one edit: a crash between two writes must not leave the countdown
        // enabled against a schedule that was never saved.
        store.edit {
            it[Keys.WORK_SCHEDULE] = WorkScheduleCodec.encode(schedule)
            it[Keys.WORK_ENABLED] = schedule.enabled
        }
    }

    suspend fun setWorkCountdownEnabled(on: Boolean) = put(Keys.WORK_ENABLED, on)
    suspend fun setWorkNotifications(on: Boolean) = put(Keys.WORK_NOTIFICATIONS, on)

    /** Clamped rather than trusted: an absurd lead time would arm alarms on the wrong day. */
    suspend fun setWorkNotifyLead(minutes: Int) =
        put(Keys.WORK_NOTIFY_LEAD, minutes.coerceIn(0, 60))

    suspend fun setWidgetTheme(mode: ThemeMode) = put(Keys.WIDGET_THEME, mode.name)
    suspend fun setWidgetOpacity(value: Float) = put(Keys.WIDGET_OPACITY, value.coerceIn(0.2f, 1f))
    suspend fun setWidgetShowLunar(on: Boolean) = put(Keys.WIDGET_SHOW_LUNAR, on)

    suspend fun setNotificationsEnabled(on: Boolean) = put(Keys.NOTIFICATIONS, on)
    suspend fun setNotificationSound(uri: String?) = putNullable(Keys.SOUND_URI, uri)
    suspend fun setVibrate(on: Boolean) = put(Keys.VIBRATE, on)
    suspend fun setHolidayNotifications(on: Boolean) = put(Keys.HOLIDAY_NOTIFICATIONS, on)

    suspend fun setBiometricUnlock(on: Boolean) = put(Keys.BIOMETRIC, on)

    suspend fun setAiEnabled(on: Boolean) = put(Keys.AI_ENABLED, on)
    suspend fun setAiModel(id: String?) = putNullable(Keys.AI_MODEL, id)
    suspend fun setAiMaxTokens(value: Int) = put(Keys.AI_MAX_TOKENS, value.coerceIn(128, 2048))
    suspend fun setAiTemperature(value: Float) = put(Keys.AI_TEMPERATURE, value.coerceIn(0f, 1f))

    // --- app lock ----------------------------------------------------------------------

    /**
     * Stores a salted SHA-256 of the PIN.
     *
     * The lock guards a private calendar on a shared phone, not a bank account, and the data
     * it protects already sits in the app's sandbox. A salted hash defeats reading the PIN
     * out of the preferences file, which is the threat that actually applies; anything
     * stronger would need a key the app cannot keep secret from itself anyway.
     */
    suspend fun setPin(pin: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val saltHex = salt.toHex()
        store.edit {
            it[Keys.PIN_SALT] = saltHex
            it[Keys.PIN_HASH] = hashPin(pin, saltHex)
            it[Keys.LOCK_ENABLED] = true
        }
    }

    suspend fun clearPin() = store.edit {
        it.remove(Keys.PIN_HASH)
        it.remove(Keys.PIN_SALT)
        it[Keys.LOCK_ENABLED] = false
    }

    fun verifyPin(pin: String, settings: AppSettings): Boolean {
        val salt = settings.pinSalt ?: return false
        val hash = settings.pinHash ?: return false
        return hashPin(pin, salt) == hash
    }

    private fun hashPin(pin: String, saltHex: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(saltHex.toByteArray())
        return digest.digest(pin.toByteArray()).toHex()
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    // --- plumbing ----------------------------------------------------------------------

    private suspend fun <T> put(key: Preferences.Key<T>, value: T) {
        store.edit { it[key] = value }
    }

    private suspend fun putNullable(key: Preferences.Key<String>, value: String?) {
        store.edit { if (value == null) it.remove(key) else it[key] = value }
    }

    private object Keys {
        val THEME = stringPreferencesKey("theme_mode")
        val ACCENT = intPreferencesKey("accent_argb")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val FONT_SCALE = floatPreferencesKey("font_scale")
        val DENSITY = stringPreferencesKey("density")
        val BACKGROUND_URI = stringPreferencesKey("background_uri")
        val BACKGROUND_OPACITY = floatPreferencesKey("background_opacity")

        val WEEK_START = intPreferencesKey("week_start")
        val SHOW_LUNAR = booleanPreferencesKey("show_lunar")
        val SHOW_GREGORIAN = booleanPreferencesKey("show_gregorian")
        val SHOW_HOLIDAYS = booleanPreferencesKey("show_holidays")
        val SHOW_DOTS = booleanPreferencesKey("show_dots")
        val SHOW_WEEK_NUMBERS = booleanPreferencesKey("show_week_numbers")
        val KHMER_NUMERALS = booleanPreferencesKey("khmer_numerals")
        val HIGHLIGHT_WEEKENDS = booleanPreferencesKey("highlight_weekends")

        val TIME_FORMAT = stringPreferencesKey("time_format")
        val DATE_FORMAT = stringPreferencesKey("date_format")
        val DAY_START_HOUR = intPreferencesKey("day_start_hour")
        val DAY_END_HOUR = intPreferencesKey("day_end_hour")

        val START_SCREEN = stringPreferencesKey("start_screen")
        val DEFAULT_REMINDER = intPreferencesKey("default_reminder")
        val DEFAULT_DURATION = intPreferencesKey("default_duration")
        val DASHBOARD_ORDER = stringPreferencesKey("dashboard_order")
        val DASHBOARD_HIDDEN = stringSetPreferencesKey("dashboard_hidden")
        val DASHBOARD_DENSITY = stringPreferencesKey("dashboard_density")
        val ANIMATION_LEVEL = stringPreferencesKey("animation_level")
        val DOCK_SLOTS = stringPreferencesKey("dock_slots")
        val DISPLAY_NAME = stringPreferencesKey("display_name")

        val WORK_SCHEDULE = stringPreferencesKey("work_schedule")
        val WORK_ENABLED = booleanPreferencesKey("work_enabled")
        val WORK_NOTIFICATIONS = booleanPreferencesKey("work_notifications")
        val WORK_NOTIFY_LEAD = intPreferencesKey("work_notify_lead")

        val WIDGET_THEME = stringPreferencesKey("widget_theme")
        val WIDGET_OPACITY = floatPreferencesKey("widget_opacity")
        val WIDGET_SHOW_LUNAR = booleanPreferencesKey("widget_show_lunar")

        val NOTIFICATIONS = booleanPreferencesKey("notifications")
        val SOUND_URI = stringPreferencesKey("sound_uri")
        val VIBRATE = booleanPreferencesKey("vibrate")
        val HOLIDAY_NOTIFICATIONS = booleanPreferencesKey("holiday_notifications")

        val LOCK_ENABLED = booleanPreferencesKey("lock_enabled")
        val PIN_HASH = stringPreferencesKey("pin_hash")
        val PIN_SALT = stringPreferencesKey("pin_salt")
        val BIOMETRIC = booleanPreferencesKey("biometric")

        val AI_ENABLED = booleanPreferencesKey("ai_enabled")
        val AI_MODEL = stringPreferencesKey("ai_model")
        val AI_MAX_TOKENS = intPreferencesKey("ai_max_tokens")
        val AI_TEMPERATURE = floatPreferencesKey("ai_temperature")
    }
}
