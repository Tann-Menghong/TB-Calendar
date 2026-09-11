package com.khmercalendar.ui.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.khmercalendar.ui.Routes

/**
 * One setting somebody might go looking for.
 *
 * @property titleKm the words on the row itself, exactly as the screen writes them.
 * @property screenKm the screen it lives on, shown beside the result so two rows with the same
 *   title - "បុណ្យជាតិ" is both a list of holidays and a switch on the calendar - can be told apart.
 * @property section the group title on that screen to scroll to, or null when the destination
 *   itself is the answer.
 * @property keywords other words people use for the same thing, English included: a setting is
 *   often remembered by the name another app gave it.
 */
data class SettingEntry(
    val titleKm: String,
    val screenKm: String,
    val route: String,
    val section: String? = null,
    val keywords: List<String> = emptyList(),
)

/**
 * Every setting worth finding, and how to find it.
 *
 * ## Why an index rather than searching the screens
 *
 * The settings are spread over nine screens and about sixty rows, most of them two taps deep.
 * A screen cannot be searched without composing it, so the words live here - and
 * `SettingsIndexTest` reads the screen sources and fails if any title or group named here is no
 * longer on its screen, so the index cannot quietly drift from what the user sees.
 */
object SettingsIndex {

    /** Below this, a Khmer query matches half the list and helps nobody. */
    const val MIN_QUERY_LENGTH = 2

    private const val SETTINGS = "ការកំណត់"
    private const val APPEARANCE = "រូបរាង"
    private const val NOTIFICATIONS = "ការជូនដំណឹង"
    private const val DASHBOARD = "ផ្ទាំងដើម"
    private const val AI = "ជំនួយការ AI"
    private const val BACKUP = "បម្រុងទុក និងស្តារ"
    private const val WORK = "កាលវិភាគការងារ"

    private const val SHOWN_ON_CALENDAR = "អ្វីដែលបង្ហាញលើប្រតិទិន"
    private const val PRIVACY = "ឯកជនភាព និងទិន្នន័យ"

    val ALL: List<SettingEntry> = listOf(
        // --- the settings index itself ---
        SettingEntry("រូបរាង", SETTINGS, Routes.SETTINGS_APPEARANCE, keywords = listOf("appearance", "theme", "look")),
        SettingEntry("ផ្ទាំងដើម", SETTINGS, Routes.SETTINGS_DASHBOARD, keywords = listOf("dashboard", "home", "modules", "cards", "កាត")),
        SettingEntry("ប្រភេទព្រឹត្តិការណ៍", SETTINGS, Routes.SETTINGS_CATEGORIES, keywords = listOf("categories", "labels", "colours", "colors", "ពណ៌")),
        SettingEntry("បុណ្យជាតិ", SETTINGS, Routes.HOLIDAYS, keywords = listOf("holidays", "public holidays", "ថ្ងៃឈប់សម្រាក")),
        SettingEntry("ផ្តោតអារម្មណ៍", SETTINGS, Routes.FOCUS, keywords = listOf("focus", "pomodoro", "timer")),
        SettingEntry("ទម្លាប់", SETTINGS, Routes.HABITS, keywords = listOf("habits", "streaks")),
        SettingEntry("រាប់ថយក្រោយ", SETTINGS, Routes.COUNTDOWNS, keywords = listOf("countdown", "pinned")),
        SettingEntry("ស្ថិតិ", SETTINGS, Routes.STATS, keywords = listOf("statistics", "stats", "productivity")),
        SettingEntry("អេក្រង់ចាប់ផ្តើម", SETTINGS, Routes.SETTINGS, section = "អេក្រង់ចាប់ផ្តើម", keywords = listOf("start screen", "opening screen", "launch")),
        SettingEntry("ការជូនដំណឹង", SETTINGS, Routes.SETTINGS_NOTIFICATIONS, keywords = listOf("notifications", "reminders", "ការរំលឹក")),
        SettingEntry("ម៉ូដែល AI", SETTINGS, Routes.SETTINGS_AI, keywords = listOf("ai", "assistant", "model", "ជំនួយការ")),
        SettingEntry("ចាក់សោកម្មវិធី", SETTINGS, Routes.SETTINGS, section = PRIVACY, keywords = listOf("lock", "pin", "password", "privacy", "លេខសម្ងាត់")),
        SettingEntry("ដោះសោដោយស្នាមម្រាមដៃ", SETTINGS, Routes.SETTINGS, section = PRIVACY, keywords = listOf("fingerprint", "biometric", "unlock")),
        SettingEntry("ប្តូរលេខសម្ងាត់", SETTINGS, Routes.SETTINGS, section = PRIVACY, keywords = listOf("change pin", "password")),
        SettingEntry("បម្រុងទុក និងស្តារ", SETTINGS, Routes.SETTINGS_BACKUP, keywords = listOf("backup", "restore", "export", "import", "json", "ics")),
        SettingEntry("កាលវិភាគការងារ", SETTINGS, Routes.SETTINGS_WORK, keywords = listOf("work", "schedule", "shift", "office hours", "ម៉ោងធ្វើការ")),
        SettingEntry("បច្ចុប្បន្នភាពកម្មវិធី", SETTINGS, Routes.SETTINGS_UPDATE, keywords = listOf("update", "version", "កំណែ")),
        SettingEntry("អំពីកម្មវិធី", SETTINGS, Routes.SETTINGS_ABOUT, keywords = listOf("about", "version", "privacy policy")),

        // --- appearance ---
        SettingEntry("ពន្លឺ", APPEARANCE, Routes.SETTINGS_APPEARANCE, section = "ពន្លឺ", keywords = listOf("dark mode", "theme", "light", "night", "ងងឹត", "ភ្លឺ")),
        SettingEntry("ពណ៌សំខាន់", APPEARANCE, Routes.SETTINGS_APPEARANCE, section = "ពណ៌សំខាន់", keywords = listOf("accent", "colour", "color", "ពណ៌")),
        SettingEntry("ប្រើពណ៌ពីផ្ទាំងរូបភាព", APPEARANCE, Routes.SETTINGS_APPEARANCE, section = "ពណ៌សំខាន់", keywords = listOf("material you", "dynamic colour", "dynamic color", "wallpaper colours")),
        SettingEntry("អក្សរ និងទំហំ", APPEARANCE, Routes.SETTINGS_APPEARANCE, section = "អក្សរ និងទំហំ", keywords = listOf("font", "text size", "density", "large text", "ទំហំអក្សរ")),
        SettingEntry("កាលបរិច្ឆេទចន្ទគតិខ្មែរ", APPEARANCE, Routes.SETTINGS_APPEARANCE, section = SHOWN_ON_CALENDAR, keywords = listOf("lunar", "chhankitek", "ចន្ទគតិ")),
        SettingEntry("កាលបរិច្ឆេទសុរិយគតិ", APPEARANCE, Routes.SETTINGS_APPEARANCE, section = SHOWN_ON_CALENDAR, keywords = listOf("gregorian", "solar")),
        SettingEntry("បុណ្យជាតិ", APPEARANCE, Routes.SETTINGS_APPEARANCE, section = SHOWN_ON_CALENDAR, keywords = listOf("show holidays")),
        SettingEntry("សញ្ញាព្រឹត្តិការណ៍", APPEARANCE, Routes.SETTINGS_APPEARANCE, section = SHOWN_ON_CALENDAR, keywords = listOf("event dots", "dots")),
        SettingEntry("លេខសប្តាហ៍", APPEARANCE, Routes.SETTINGS_APPEARANCE, section = SHOWN_ON_CALENDAR, keywords = listOf("week numbers")),
        SettingEntry("លេខខ្មែរ", APPEARANCE, Routes.SETTINGS_APPEARANCE, section = SHOWN_ON_CALENDAR, keywords = listOf("khmer numerals", "numbers", "digits", "១២៣")),
        SettingEntry("បន្លិចថ្ងៃចុងសប្តាហ៍", APPEARANCE, Routes.SETTINGS_APPEARANCE, section = SHOWN_ON_CALENDAR, keywords = listOf("weekend", "highlight")),
        SettingEntry("ថ្ងៃដំបូងនៃសប្តាហ៍", APPEARANCE, Routes.SETTINGS_APPEARANCE, section = "ថ្ងៃដំបូងនៃសប្តាហ៍", keywords = listOf("first day of week", "week start", "monday", "sunday")),
        SettingEntry("ទម្រង់ម៉ោង និងកាលបរិច្ឆេទ", APPEARANCE, Routes.SETTINGS_APPEARANCE, section = "ទម្រង់ម៉ោង និងកាលបរិច្ឆេទ", keywords = listOf("time format", "24 hour", "12 hour", "date format")),
        SettingEntry("ម៉ោងធ្វើការ", APPEARANCE, Routes.SETTINGS_APPEARANCE, section = "ម៉ោងធ្វើការ", keywords = listOf("working hours", "free time", "day start")),
        SettingEntry("រូបភាពផ្ទៃខាងក្រោយ", APPEARANCE, Routes.SETTINGS_APPEARANCE, section = "ផ្ទាំងខាងក្រោយ", keywords = listOf("background", "wallpaper", "image")),
        SettingEntry("ធាតុក្រាហ្វិកលើអេក្រង់ដើម", APPEARANCE, Routes.SETTINGS_APPEARANCE, section = "ធាតុក្រាហ្វិកលើអេក្រង់ដើម", keywords = listOf("widget", "home screen", "opacity")),

        // --- notifications ---
        SettingEntry("បើកការជូនដំណឹង", NOTIFICATIONS, Routes.SETTINGS_NOTIFICATIONS, section = "ការរំលឹក", keywords = listOf("notifications on", "turn off notifications", "enable")),
        SettingEntry("ញ័រ", NOTIFICATIONS, Routes.SETTINGS_NOTIFICATIONS, section = "ការរំលឹក", keywords = listOf("vibrate", "vibration")),
        SettingEntry("សំឡេងជូនដំណឹង", NOTIFICATIONS, Routes.SETTINGS_NOTIFICATIONS, section = "ការរំលឹក", keywords = listOf("sound", "ringtone", "tone")),
        SettingEntry("ជូនដំណឹងអំពីបុណ្យជាតិ", NOTIFICATIONS, Routes.SETTINGS_NOTIFICATIONS, section = "ការរំលឹក", keywords = listOf("holiday notifications", "holiday reminder")),
        SettingEntry("តម្លៃលំនាំដើម", NOTIFICATIONS, Routes.SETTINGS_NOTIFICATIONS, section = "តម្លៃលំនាំដើម", keywords = listOf("default reminder", "minutes before")),

        // --- dashboard ---
        SettingEntry("គំរូ", DASHBOARD, Routes.SETTINGS_DASHBOARD, section = "គំរូ", keywords = listOf("preset", "layout", "template")),
        SettingEntry("ឈ្មោះរបស់អ្នក", DASHBOARD, Routes.SETTINGS_DASHBOARD, section = "ឈ្មោះរបស់អ្នក", keywords = listOf("name", "greeting")),
        SettingEntry("ប៊ូតុងរហ័ស", DASHBOARD, Routes.SETTINGS_DASHBOARD, section = "ប៊ូតុងរហ័ស", keywords = listOf("dock", "quick actions", "shortcuts")),
        SettingEntry("ទំហំកាត", DASHBOARD, Routes.SETTINGS_DASHBOARD, section = "ទំហំកាត", keywords = listOf("card size", "compact", "density")),
        SettingEntry("ផ្ទាំង", DASHBOARD, Routes.SETTINGS_DASHBOARD, section = "ផ្ទាំង", keywords = listOf("modules", "show or hide", "reorder")),

        // --- on-device AI ---
        SettingEntry("បើកជំនួយការ AI", AI, Routes.SETTINGS_AI, keywords = listOf("enable ai", "turn on assistant")),
        SettingEntry("ម៉ូដែលដែលអាចប្រើបាន", AI, Routes.SETTINGS_AI, section = "ម៉ូដែលដែលអាចប្រើបាន", keywords = listOf("download model", "delete model", "models")),
        SettingEntry("ការកំណត់ការឆ្លើយ", AI, Routes.SETTINGS_AI, section = "ការកំណត់ការឆ្លើយ", keywords = listOf("temperature", "tokens", "response length")),

        // --- backup ---
        SettingEntry("នាំចេញទិន្នន័យទាំងអស់", BACKUP, Routes.SETTINGS_BACKUP, section = "បម្រុងទុកពេញលេញ (JSON)", keywords = listOf("export", "backup", "json")),
        SettingEntry("ស្តារពីឯកសារ", BACKUP, Routes.SETTINGS_BACKUP, section = "បម្រុងទុកពេញលេញ (JSON)", keywords = listOf("restore", "import backup")),
        SettingEntry("នាំចេញជា .ics", BACKUP, Routes.SETTINGS_BACKUP, section = "ប្រតិទិនស្តង់ដារ (.ics)", keywords = listOf("ics export", "google calendar", "outlook")),
        SettingEntry("នាំចូលពី .ics", BACKUP, Routes.SETTINGS_BACKUP, section = "ប្រតិទិនស្តង់ដារ (.ics)", keywords = listOf("ics import")),

        // --- work ---
        SettingEntry("បង្ហាញការរាប់ថយក្រោយម៉ោងធ្វើការ", WORK, Routes.SETTINGS_WORK, keywords = listOf("work countdown")),
        SettingEntry("ជូនដំណឹងពេលប្តូរវេន", WORK, Routes.SETTINGS_WORK, keywords = listOf("shift notification", "break reminder")),
    )

    /**
     * Settings matching [query], best first.
     *
     * The row's own words outrank a synonym, and a synonym outranks the group it sits in: someone
     * typing "ពណ៌" wants the colour settings, not every row on a screen that mentions colour.
     * Ties keep index order, which follows the order of the screens themselves.
     */
    fun search(query: String): List<SettingEntry> {
        val q = query.trim()
        if (q.length < MIN_QUERY_LENGTH) return emptyList()
        return ALL
            .mapNotNull { entry -> score(entry, q)?.let { entry to it } }
            .sortedBy { it.second }
            .map { it.first }
    }

    /**
     * Lower is better.
     *
     * A keyword that *is* the query outranks one that merely starts with it: "pin" is a prefix of
     * the countdowns' "pinned", and without this tier the countdown list, which comes earlier in
     * the index, beat the PIN lock for somebody typing "PIN".
     */
    private fun score(entry: SettingEntry, q: String): Int? = when {
        entry.titleKm.startsWith(q, ignoreCase = true) -> 0
        entry.titleKm.contains(q, ignoreCase = true) -> 1
        entry.keywords.any { it.equals(q, ignoreCase = true) } -> 2
        entry.keywords.any { it.startsWith(q, ignoreCase = true) } -> 3
        entry.keywords.any { it.contains(q, ignoreCase = true) } -> 4
        entry.section?.contains(q, ignoreCase = true) == true -> 5
        entry.screenKm.contains(q, ignoreCase = true) -> 6
        else -> null
    }
}

/**
 * The group the next settings screen should bring into view.
 *
 * Process-wide rather than a navigation argument. The group titles are Khmer strings that would
 * have to be escaped into every settings route, and nine destinations would each need to read
 * and forward the argument; instead the one place that sets it is the search result, and the one
 * place that reads it is [SettingsGroup], which clears it once it has scrolled.
 */
object SettingsFocus {
    var section: String? by mutableStateOf(null)
}
