package com.khmercalendar.domain

import java.time.LocalTime

/**
 * The greeting at the top of the dashboard.
 *
 * Four bands rather than the usual three. Khmer distinguishes រសៀល (early afternoon) from
 * ល្ងាច (late afternoon and evening), and collapsing them the way an English morning/
 * afternoon/evening split would is the kind of small wrongness that makes an app read as
 * translated rather than written.
 *
 * The boundaries follow the same divisions the app already uses to name a time of day, so the
 * greeting and the clock never disagree about which part of the day it is.
 */
object DayGreeting {

    /**
     * The greeting, with the user's name if they gave one.
     *
     * Blank is the normal case and has to read as finished rather than as a missing value -
     * so the name is appended to a complete sentence rather than filled into a slot in one.
     * The name is trimmed at the settings screen, not here; a name that is only spaces would
     * otherwise produce a trailing separator.
     */
    fun of(time: LocalTime, name: String = ""): String {
        val greeting = of(time)
        return if (name.isBlank()) greeting else "$greeting $name"
    }

    fun of(time: LocalTime): String = when (time.hour) {
        in 0..4 -> "រាត្រីសួស្តី"
        in 5..11 -> "អរុណសួស្តី"
        in 12..14 -> "ទិវាសួស្តី"
        in 15..17 -> "សាយណ្ហសួស្តី"
        else -> "រាត្រីសួស្តី"
    }
}
