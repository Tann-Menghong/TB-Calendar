package com.khmercalendar.data.backup

import androidx.room.withTransaction
import android.content.Context
import android.net.Uri
import com.khmercalendar.data.db.EventEntity
import com.khmercalendar.data.db.KhmerCalendarDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * iCalendar (RFC 5545) import and export.
 *
 * The point of supporting it is that a Khmer calendar should not be a place data goes to die:
 * an `.ics` file opens in Google Calendar, Outlook and every other calendar app, so the
 * user's events remain theirs.
 *
 * The writer emits the subset this app models. The reader accepts rather more than that,
 * skipping properties it does not understand, because real-world files carry timezone
 * definitions and vendor extensions that are none of this app's business.
 */
object IcsFormat {

    private val UTC_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
    private val DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd")

    /**
     * A reminder offset, in any of the shapes calendars actually write it.
     *
     * The old pattern was `TRIGGER:-PT(\d+H)?(\d+M)?`, which recognised only what this app
     * itself emits. Two very common forms fell straight through it and the reminder was lost
     * in silence: `TRIGGER;VALUE=DURATION:-PT15M`, where a parameter sits before the colon,
     * and `-P1D`, which is what Google Calendar writes for a day-before reminder.
     */
    private val TRIGGER =
        Regex("""TRIGGER(?:;[^:\n]*)?:-P(?:(\d+)D)?(?:T(?:(\d+)H)?(?:(\d+)M)?)?""")

    suspend fun export(
        context: Context,
        database: KhmerCalendarDatabase,
        uri: Uri,
    ): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val events = database.eventDao().allEvents()
            val reminders = database.reminderDao().all().groupBy { it.eventId }
            val out = StringBuilder()
            fun line(text: String) = out.append(fold(text)).append("\r\n")

            line("BEGIN:VCALENDAR")
            line("VERSION:2.0")
            line("PRODID:-//KhmerCalendar//KH//EN")
            line("CALSCALE:GREGORIAN")
            events.forEach { e ->
                line("BEGIN:VEVENT")
                line("UID:${e.id}@khmercalendar")
                line("DTSTAMP:${Instant.ofEpochMilli(e.updatedAtMillis).atZone(ZoneOffset.UTC).format(UTC_FORMAT)}")
                if (e.allDay) {
                    val start = Instant.ofEpochMilli(e.startUtcMillis).atZone(ZoneOffset.UTC).toLocalDate()
                    val end = Instant.ofEpochMilli(e.endUtcMillis).atZone(ZoneOffset.UTC).toLocalDate()
                    line("DTSTART;VALUE=DATE:${start.format(DATE_FORMAT)}")
                    line("DTEND;VALUE=DATE:${end.format(DATE_FORMAT)}")
                } else {
                    line("DTSTART:${Instant.ofEpochMilli(e.startUtcMillis).atZone(ZoneOffset.UTC).format(UTC_FORMAT)}")
                    line("DTEND:${Instant.ofEpochMilli(e.endUtcMillis).atZone(ZoneOffset.UTC).format(UTC_FORMAT)}")
                }
                line("SUMMARY:${escape(e.title)}")
                e.description?.let { line("DESCRIPTION:${escape(it)}") }
                e.location?.let { line("LOCATION:${escape(it)}") }
                e.rrule?.let { line("RRULE:$it") }
                reminders[e.id].orEmpty().forEach { r ->
                    line("BEGIN:VALARM")
                    line("ACTION:DISPLAY")
                    line("DESCRIPTION:${escape(e.title)}")
                    line("TRIGGER:-PT${r.minutesBefore}M")
                    line("END:VALARM")
                }
                line("END:VEVENT")
            }
            line("END:VCALENDAR")

            context.contentResolver.openOutputStream(uri, "wt")?.use { stream ->
                stream.write(out.toString().toByteArray())
            } ?: error("Could not open the chosen file for writing")
            events.size
        }
    }

    /**
     * What an import did.
     *
     * [skipped] is reported rather than swallowed. Importing the same file twice used to
     * silently double every event in the calendar, and a user who does that by accident
     * deserves to be told which of the two things happened.
     */
    data class ImportResult(val imported: Int, val skipped: Int)

    suspend fun import(
        context: Context,
        database: KhmerCalendarDatabase,
        uri: Uri,
    ): Result<ImportResult> = withContext(Dispatchers.IO) {
        runCatching {
            val raw = context.contentResolver.openInputStream(uri)?.use {
                it.readBytes().decodeToString()
            } ?: error("Could not read the chosen file")
            parseInto(database, unfold(raw), ZoneId.systemDefault(), System.currentTimeMillis())
        }
    }

    /**
     * The reading half, separated from the file so it can be tested against a real database
     * without a `content://` URI.
     */
    internal suspend fun parseInto(
        database: KhmerCalendarDatabase,
        unfolded: String,
        zone: ZoneId,
        now: Long,
    ): ImportResult = database.withTransaction {
        // One transaction for the whole file. Rows used to be written one at a time, so a file
        // that failed halfway left half of itself in the calendar while the screen reported
        // that the import had failed - and the user had no way to tell which events came in.
        //
        // A natural key over what is already stored. An .ics carries a UID, but this app has
        // never had a column for one, and adding a schema migration purely to deduplicate an
        // import is a larger risk to the user's data than the thing it prevents.
        val seen = database.eventDao().allEvents()
            .mapTo(HashSet()) { Triple(it.title, it.startUtcMillis, it.allDay) }

        var imported = 0
        var skipped = 0

        unfolded.split("BEGIN:VEVENT").drop(1).forEach { block ->
            val body = block.substringBefore("END:VEVENT")
            val summary = property(body, "SUMMARY") ?: return@forEach
            val dtStart = property(body, "DTSTART") ?: return@forEach
            val allDay = isDateOnly(body, "DTSTART")
            val start = parseDateTime(dtStart, allDay, zone, tzid(body, "DTSTART")) ?: return@forEach
            val end = property(body, "DTEND")?.let { parseDateTime(it, allDay, zone, tzid(body, "DTEND")) }
                ?: if (allDay) start.plusDays(1) else start.plusHours(1)

            val startMillis = if (allDay) {
                start.toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
            } else {
                start.atZone(zone).toInstant().toEpochMilli()
            }
            val endMillis = if (allDay) {
                end.toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
            } else {
                end.atZone(zone).toInstant().toEpochMilli()
            }

            val title = unescape(summary)
            if (!seen.add(Triple(title, startMillis, allDay))) {
                skipped++
                return@forEach
            }

            val id = database.eventDao().insert(
                EventEntity(
                    title = title,
                    description = property(body, "DESCRIPTION")?.let { unescape(it) },
                    location = property(body, "LOCATION")?.let { unescape(it) },
                    startUtcMillis = startMillis,
                    endUtcMillis = endMillis,
                    allDay = allDay,
                    zoneId = zone.id,
                    rrule = property(body, "RRULE"),
                    createdAtMillis = now,
                    updatedAtMillis = now,
                )
            )
            val minutes = triggerMinutes(body)
            if (minutes.isNotEmpty()) database.reminderDao().replaceForEvent(id, minutes)
            imported++
        }
        ImportResult(imported = imported, skipped = skipped)
    }

    /**
     * Every reminder offset in a VEVENT, in minutes before the start.
     *
     * Zero is kept. `-PT0M` is a reminder at the moment the event starts, which this app
     * offers as "ពេលចាប់ផ្តើម"; the old code required a positive value and so dropped it on
     * every round trip through a file this app had written itself.
     */
    internal fun triggerMinutes(body: String): List<Int> =
        TRIGGER.findAll(body).mapNotNull { match ->
            val (days, hours, mins) = match.destructured
            if (days.isEmpty() && hours.isEmpty() && mins.isEmpty()) return@mapNotNull null
            (days.toIntOrNull() ?: 0) * 1440 +
                (hours.toIntOrNull() ?: 0) * 60 +
                (mins.toIntOrNull() ?: 0)
        }.distinct().toList()

    /** iCalendar wraps long lines; a continuation begins with a space or tab. */
    internal fun unfold(text: String): String =
        text.replace("\r\n", "\n").replace(Regex("\n[ \t]"), "")

    /**
     * The other half of that contract, which the writer never held up.
     *
     * RFC 5545 section 3.1 caps a line at 75 octets. Khmer is three bytes per character in
     * UTF-8, so a twenty-five character description already overruns it — and strict parsers
     * reject the file rather than guessing. Folding counts octets, not characters, and never
     * splits one: a break inside a multi-byte sequence would corrupt the text it is trying
     * to preserve.
     */
    internal fun fold(line: String): String {
        val bytes = line.toByteArray()
        if (bytes.size <= MAX_OCTETS) return line

        val out = StringBuilder()
        var used = 0
        var budget = MAX_OCTETS
        line.forEach { ch ->
            val width = ch.toString().toByteArray().size
            if (used + width > budget) {
                out.append("\r\n ")
                used = 0
                // A continuation line spends one octet on its leading space.
                budget = MAX_OCTETS - 1
            }
            out.append(ch)
            used += width
        }
        return out.toString()
    }

    private fun property(block: String, name: String): String? =
        Regex("(?m)^$name(?:;[^:\n]*)?:(.*)$").find(block)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }

    /** The parameters on a property, split out: `TZID=Asia/Bangkok`, `VALUE=DATE`, and so on. */
    private fun parameters(block: String, name: String): List<String> =
        Regex("(?m)^$name;([^:\n]*):").find(block)
            ?.groupValues?.get(1)
            ?.split(';')
            .orEmpty()

    /** The TZID parameter on a property, if it names a zone this device knows. */
    private fun tzid(block: String, name: String): ZoneId? =
        parameters(block, name)
            .firstOrNull { it.startsWith("TZID=", ignoreCase = true) }
            ?.substringAfter('=')
            ?.let { runCatching { ZoneId.of(it) }.getOrNull() }

    /** True when the property carries `VALUE=DATE`, whatever else sits beside it. */
    private fun isDateOnly(block: String, name: String): Boolean =
        parameters(block, name).any { it.equals("VALUE=DATE", ignoreCase = true) }

    /**
     * One DTSTART or DTEND value, as wall-clock time on this device.
     *
     * Three forms, and the third is where this used to corrupt data. A value ending in `Z` is
     * UTC and is converted. A bare value carrying a `TZID` parameter is wall-clock time *in
     * that zone*, and was previously read as though it were already local — so a 09:00
     * meeting exported from New York arrived as 09:00 in Phnom Penh, eleven hours out. A bare
     * value with no TZID is "floating" time, which RFC 5545 defines as local wherever the
     * file is opened, so that one was always right.
     */
    private fun parseDateTime(
        raw: String,
        allDay: Boolean,
        zone: ZoneId,
        tzid: ZoneId? = null,
    ): LocalDateTime? = runCatching {
        val value = raw.trim()
        when {
            allDay || value.length == 8 -> LocalDate.parse(value.take(8), DATE_FORMAT).atStartOfDay()
            value.endsWith("Z") ->
                Instant.from(UTC_FORMAT.withZone(ZoneOffset.UTC).parse(value)).atZone(zone).toLocalDateTime()
            else -> {
                val local = LocalDateTime.parse(
                    value.take(15),
                    DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss"),
                )
                if (tzid == null) local else local.atZone(tzid).withZoneSameInstant(zone).toLocalDateTime()
            }
        }
    }.getOrNull()

    // RFC 5545 section 3.3.11: backslash, semicolon, comma and newline are escaped in text
    // values. Order matters - the backslash must be doubled first on the way out and undone
    // last on the way back, or an escaped backslash swallows the character after it.
    private fun escape(text: String): String = text
        .replace("""\""", """\\""")
        .replace(";", """\;""")
        .replace(",", """\,""")
        .replace("\r\n", """\n""")
        .replace("\n", """\n""")

    private fun unescape(text: String): String = text
        .replace("""\n""", "\n")
        .replace("""\,""", ",")
        .replace("""\;""", ";")
        .replace("""\\""", """\""")

    private const val MAX_OCTETS = 75
}
