package com.khmercalendar.core.work

import java.time.DayOfWeek
import java.time.LocalTime

/**
 * Encodes a [WorkSchedule] as one short string.
 *
 * Preferences storage holds primitives, and a schedule is a small nested structure. A compact
 * text form keeps it in a single DataStore key - so reading settings stays one read, and a
 * schedule change is one atomic write rather than a partially-applied set of keys.
 *
 * Format, deliberately human-readable so a backup file can be inspected:
 *
 * ```
 * 1:0730-1130|1330-1730;2:0730-1130|1330-1730;6:0730-1130;7:
 * ```
 *
 * Day numbers are [DayOfWeek.getValue] (Monday 1 … Sunday 7). A day with no blocks is a day
 * off. A day absent from the string is also a day off, so shrinking the week is expressible.
 */
object WorkScheduleCodec {

    private const val DAY_SEPARATOR = ';'
    private const val BLOCK_SEPARATOR = '|'
    private const val DAY_MARKER = ':'
    private const val RANGE_MARKER = '-'

    fun encode(schedule: WorkSchedule): String =
        DayOfWeek.entries.joinToString(DAY_SEPARATOR.toString()) { day ->
            val blocks = schedule.dayOf(day).blocks
                .sortedBy { it.start }
                .joinToString(BLOCK_SEPARATOR.toString()) { block ->
                    "${hhmm(block.start)}$RANGE_MARKER${hhmm(block.end)}"
                }
            "${day.value}$DAY_MARKER$blocks"
        }

    /**
     * Parses [encoded], falling back to [fallback] when it is missing or unusable.
     *
     * Malformed entries are skipped rather than thrown: a preferences file damaged by a
     * crash, or written by a future version with a longer format, should cost the user their
     * customisation at worst, never the ability to open the app.
     */
    fun decode(encoded: String?, fallback: WorkSchedule = WorkSchedule.DEFAULT): WorkSchedule {
        if (encoded.isNullOrBlank()) return fallback

        val days = mutableMapOf<DayOfWeek, WorkDay>()
        for (part in encoded.split(DAY_SEPARATOR)) {
            if (part.isBlank()) continue
            val marker = part.indexOf(DAY_MARKER)
            if (marker <= 0) continue
            val day = part.substring(0, marker).trim().toIntOrNull()
                ?.let { runCatching { DayOfWeek.of(it) }.getOrNull() }
                ?: continue

            val blocks = part.substring(marker + 1)
                .split(BLOCK_SEPARATOR)
                .mapNotNull { decodeBlock(it) }
                .sortedBy { it.start }

            days[day] = WorkDay(blocks)
        }

        // Nothing usable in the whole string: treat it as absent rather than as an empty week,
        // which would silently turn every day into a day off.
        if (days.isEmpty()) return fallback
        return WorkSchedule(days = days, enabled = fallback.enabled)
    }

    private fun decodeBlock(raw: String): WorkBlock? {
        val text = raw.trim()
        if (text.isEmpty()) return null
        val dash = text.indexOf(RANGE_MARKER)
        if (dash <= 0) return null
        val start = parseHhmm(text.substring(0, dash)) ?: return null
        val end = parseHhmm(text.substring(dash + 1)) ?: return null
        if (!end.isAfter(start)) return null
        return WorkBlock(start, end, labelFor(start))
    }

    /**
     * Names a block by when it starts.
     *
     * The label is presentation, not data, so it is derived rather than stored - which keeps
     * the encoded form short and means a user who shifts their hours gets a label that still
     * matches.
     */
    private fun labelFor(start: LocalTime): String = when {
        start.hour < 12 -> WorkSchedule.MORNING_KM
        start.hour < 17 -> WorkSchedule.AFTERNOON_KM
        else -> "ការងារពេលល្ងាច"
    }

    private fun hhmm(time: LocalTime): String = "%02d%02d".format(time.hour, time.minute)

    private fun parseHhmm(raw: String): LocalTime? {
        val text = raw.trim()
        if (text.length != 4 || !text.all { it.isDigit() }) return null
        val hour = text.substring(0, 2).toInt()
        val minute = text.substring(2, 4).toInt()
        if (hour !in 0..23 || minute !in 0..59) return null
        return LocalTime.of(hour, minute)
    }
}
