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

    fun of(time: LocalTime): String = when (time.hour) {
        in 0..4 -> "រាត្រីសួស្តី"
        in 5..11 -> "អរុណសួស្តី"
        in 12..14 -> "ទិវាសួស្តី"
        in 15..17 -> "សាយណ្ហសួស្តី"
        else -> "រាត្រីសួស្តី"
    }
}
