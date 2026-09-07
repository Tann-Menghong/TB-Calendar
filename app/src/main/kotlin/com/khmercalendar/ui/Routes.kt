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
    const val MONTH = "month"
    const val WEEK = "week"
    const val DAY = "day"
    const val AGENDA = "agenda"
    const val SEARCH = "search"
    const val ASSISTANT = "assistant"
    const val HOLIDAYS = "holidays"

    const val SETTINGS = "settings"
    const val SETTINGS_APPEARANCE = "settings/appearance"
    const val SETTINGS_NOTIFICATIONS = "settings/notifications"
    const val SETTINGS_CATEGORIES = "settings/categories"
    const val SETTINGS_BACKUP = "settings/backup"
    const val SETTINGS_AI = "settings/ai"
    const val SETTINGS_UPDATE = "settings/update"
    const val SETTINGS_ABOUT = "settings/about"

    const val EVENT_DETAIL = "event/{eventId}?date={date}"
    const val EVENT_EDIT = "edit/{eventId}?date={date}"

    fun eventDetail(eventId: Long, date: String): String = "event/$eventId?date=$date"

    fun eventEdit(eventId: Long, date: String): String = "edit/$eventId?date=$date"
}
