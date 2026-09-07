package com.khmercalendar.data.backup

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

    suspend fun export(
        context: Context,
        database: KhmerCalendarDatabase,
        uri: Uri,
    ): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val events = database.eventDao().allEvents()
            val reminders = database.reminderDao().all().groupBy { it.eventId }
            val text = buildString {
                appendLine("BEGIN:VCALENDAR")
                appendLine("VERSION:2.0")
                appendLine("PRODID:-//KhmerCalendar//KH//EN")
                appendLine("CALSCALE:GREGORIAN")
                events.forEach { e ->
                    appendLine("BEGIN:VEVENT")
                    appendLine("UID:${e.id}@khmercalendar")
                    appendLine("DTSTAMP:${Instant.ofEpochMilli(e.updatedAtMillis).atZone(ZoneOffset.UTC).format(UTC_FORMAT)}")
                    if (e.allDay) {
                        val start = Instant.ofEpochMilli(e.startUtcMillis).atZone(ZoneOffset.UTC).toLocalDate()
                        val end = Instant.ofEpochMilli(e.endUtcMillis).atZone(ZoneOffset.UTC).toLocalDate()
                        appendLine("DTSTART;VALUE=DATE:${start.format(DATE_FORMAT)}")
                        appendLine("DTEND;VALUE=DATE:${end.format(DATE_FORMAT)}")
                    } else {
                        appendLine("DTSTART:${Instant.ofEpochMilli(e.startUtcMillis).atZone(ZoneOffset.UTC).format(UTC_FORMAT)}")
                        appendLine("DTEND:${Instant.ofEpochMilli(e.endUtcMillis).atZone(ZoneOffset.UTC).format(UTC_FORMAT)}")
                    }
                    appendLine("SUMMARY:${escape(e.title)}")
                    e.description?.let { appendLine("DESCRIPTION:${escape(it)}") }
                    e.location?.let { appendLine("LOCATION:${escape(it)}") }
                    e.rrule?.let { appendLine("RRULE:$it") }
                    reminders[e.id].orEmpty().forEach { r ->
                        appendLine("BEGIN:VALARM")
                        appendLine("ACTION:DISPLAY")
                        appendLine("DESCRIPTION:${escape(e.title)}")
                        appendLine("TRIGGER:-PT${r.minutesBefore}M")
                        appendLine("END:VALARM")
                    }
                    appendLine("END:VEVENT")
                }
                appendLine("END:VCALENDAR")
            }
            context.contentResolver.openOutputStream(uri, "wt")?.use { out ->
                out.write(text.toByteArray())
            } ?: error("Could not open the chosen file for writing")
            events.size
        }
    }

    suspend fun import(
        context: Context,
        database: KhmerCalendarDatabase,
        uri: Uri,
    ): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val raw = context.contentResolver.openInputStream(uri)?.use {
                it.readBytes().decodeToString()
            } ?: error("Could not read the chosen file")

            val now = System.currentTimeMillis()
            val zone = ZoneId.systemDefault()
            var imported = 0

            unfold(raw).split("BEGIN:VEVENT").drop(1).forEach { block ->
                val body = block.substringBefore("END:VEVENT")
                val summary = property(body, "SUMMARY") ?: return@forEach
                val dtStart = property(body, "DTSTART") ?: return@forEach
                val allDay = body.contains("DTSTART;VALUE=DATE:")
                val start = parseDateTime(dtStart, allDay, zone) ?: return@forEach
                val end = property(body, "DTEND")?.let { parseDateTime(it, allDay, zone) }
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

                val id = database.eventDao().insert(
                    EventEntity(
                        title = unescape(summary),
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
                val minutes = Regex("""TRIGGER:-PT(?:(\d+)H)?(?:(\d+)M)?""").findAll(body).mapNotNull { m ->
                    val h = m.groupValues[1].toIntOrNull() ?: 0
                    val mm = m.groupValues[2].toIntOrNull() ?: 0
                    (h * 60 + mm).takeIf { it > 0 }
                }.toList()
                if (minutes.isNotEmpty()) database.reminderDao().replaceForEvent(id, minutes)
                imported++
            }
            imported
        }
    }

    /** iCalendar wraps long lines; a continuation begins with a space or tab. */
    private fun unfold(text: String): String =
        text.replace("\r\n", "\n").replace(Regex("\n[ \t]"), "")

    private fun property(block: String, name: String): String? =
        Regex("(?m)^$name(?:;[^:\n]*)?:(.*)$").find(block)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }

    private fun parseDateTime(raw: String, allDay: Boolean, zone: ZoneId): LocalDateTime? = runCatching {
        val value = raw.trim()
        when {
            allDay || value.length == 8 -> LocalDate.parse(value.take(8), DATE_FORMAT).atStartOfDay()
            value.endsWith("Z") ->
                Instant.from(UTC_FORMAT.withZone(ZoneOffset.UTC).parse(value)).atZone(zone).toLocalDateTime()
            else -> LocalDateTime.parse(
                value.take(15),
                DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss"),
            )
        }
    }.getOrNull()

    // RFC 5545 section 3.3.11: backslash, semicolon, comma and newline are escaped in text
    // values. Order matters - the backslash must be doubled first on the way out and undone
    // last on the way back, or an escaped backslash swallows the character after it.
    private fun escape(text: String): String = text
        .replace("""\""", """\\""")
        .replace(";", """\;""")
        .replace(",", """\,""")
        .replace("\n", """\n""")

    private fun unescape(text: String): String = text
        .replace("""\n""", "\n")
        .replace("""\,""", ",")
        .replace("""\;""", ";")
        .replace("""\\""", """\""")
}
