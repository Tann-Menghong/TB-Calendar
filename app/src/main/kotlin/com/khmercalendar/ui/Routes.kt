package com.khmercalendar.ui

/**
 * Every destination, as a string route.
 *
 * Typed navigation would be tidier, but string routes are what a widget or a notification can
 * hand over without reconstructing a parcelable, and this app is navigated from outside more
 * than most.
 */
object Routes {
    const val HOME = "home"

    /**
     * The calendar, in whichever of its four views is currently chosen.
     *
     * The month, week, day and agenda used to be four routes. They are one destination with a
     * switcher now, and the chosen view is state on the shared
     * [com.khmercalendar.ui.calendar.CalendarViewModel] rather than a route argument: a tab
     * that is sometimes "calendar?view=week" and sometimes "calendar?view=month" is two
     * entries in the back stack as far as the navigator is concerned, and the bottom bar
     * would stop highlighting it.
     */
    const val CALENDAR = "calendar"
    const val TASKS = "tasks"
    const val SEARCH = "search"
    const val ASSISTANT = "assistant"
    const val HOLIDAYS = "holidays"

    /** Every pinned countdown, and the ones that have already arrived. */
    const val COUNTDOWNS = "countdowns"

    /** The habits you are keeping. */
    const val HABITS = "habits"

    /** The focus timer. */
    const val FOCUS = "focus"

    /** What you actually did, counted from data already on the device. */
    const val STATS = "stats"

    const val SETTINGS = "settings"
    const val SETTINGS_APPEARANCE = "settings/appearance"
    const val SETTINGS_DASHBOARD = "settings/dashboard"
    const val SETTINGS_NOTIFICATIONS = "settings/notifications"
    const val SETTINGS_CATEGORIES = "settings/categories"
    const val SETTINGS_BACKUP = "settings/backup"
    const val SETTINGS_AI = "settings/ai"
    const val SETTINGS_WORK = "settings/work"
    const val SETTINGS_UPDATE = "settings/update"
    const val SETTINGS_ABOUT = "settings/about"

    const val EVENT_DETAIL = "event/{eventId}?date={date}"
    const val EVENT_EDIT = "edit/{eventId}?date={date}&task={task}"

    fun eventDetail(eventId: Long, date: String): String = "event/$eventId?date=$date"

    /**
     * The editor, optionally opening as a task.
     *
     * [asTask] exists so "add a task on this date" is one step rather than "add an event,
     * then find the task switch". It seeds the draft; the switch in the editor still decides.
     */
    fun eventEdit(eventId: Long, date: String, asTask: Boolean = false): String =
        "edit/$eventId?date=$date&task=$asTask"
}
