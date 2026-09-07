package com.khmercalendar.core.nlu

import com.khmercalendar.core.khmer.KhmerNumerals
import com.khmercalendar.core.recurrence.Frequency
import com.khmercalendar.core.recurrence.RecurrenceRule
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * A calendar event proposed from a piece of text, for the user to confirm.
 *
 * @property confidence 0..1. Below [NaturalLanguageEventParser.MIN_CONFIDENCE] the caller
 *   should treat the parse as a failure and, if a model is loaded, ask it instead.
 */
data class EventDraft(
    val title: String,
    val start: LocalDateTime,
    val end: LocalDateTime,
    val allDay: Boolean = false,
    val location: String? = null,
    val recurrence: RecurrenceRule? = null,
    val reminderMinutes: List<Int> = emptyList(),
    val confidence: Float = 0f,
    /** What the parser understood, for showing the user why it proposed this. */
    val explanation: String = "",
)

/**
 * Turns "ថ្ងៃស្អែក ម៉ោង ២ រសៀល ប្រជុំជាមួយក្រុមការងារ" into an event, with no model loaded.
 *
 * This is the first stage of the assistant and the only one guaranteed to be available: it
 * costs microseconds, no memory and no download, it cannot hallucinate a date, and it works
 * on a device that could never host a language model. The on-device LLM is asked only when
 * this declines — free-form sentences, several events in one message, unusual phrasing.
 *
 * Everything is matched against text normalised by [KhmerNumerals]: Khmer digits rewritten
 * to ASCII, zero-width characters stripped, decomposed vowels composed.
 */
class NaturalLanguageEventParser(
    /** Default length for an event whose end is not stated. */
    private val defaultDuration: Duration = Duration.ofHours(1),
) {

    companion object {
        const val MIN_CONFIDENCE = 0.45f
        private val WS = Regex("\\s+")
    }

    private data class Span(val range: IntRange, val label: String)

    fun parse(rawText: String, now: LocalDateTime): EventDraft? {
        val text = KhmerNumerals.toAscii(KhmerNumerals.composeVowels(rawText))
        if (text.isBlank()) return null
        val lower = text.lowercase()
        val spans = mutableListOf<Span>()
        val notes = mutableListOf<String>()

        val recurrence = parseRecurrence(lower, spans)
        val date = parseDate(text, lower, now, spans, notes)
        val time = parseTime(text, lower, spans, notes)
        val durationMinutes = parseDuration(lower, spans)
        val reminders = parseReminders(lower, spans)
        val location = parseLocation(text, spans)

        // A bare title with no date and no time is not an event request; refusing is better
        // than filing something on an arbitrary day.
        if (date == null && time == null && recurrence == null) return null

        val allDay = time == null
        val startDate = date ?: run {
            // A time with no date means today, unless that time has already passed.
            val t = time!!
            if (LocalDateTime.of(now.toLocalDate(), t).isBefore(now)) now.toLocalDate().plusDays(1)
            else now.toLocalDate()
        }
        val start = LocalDateTime.of(startDate, time ?: LocalTime.of(0, 0))
        val end = when {
            allDay -> start.plusDays(1)
            durationMinutes != null -> start.plusMinutes(durationMinutes.toLong())
            else -> start.plus(defaultDuration)
        }

        val title = buildTitle(text, spans).ifBlank { defaultTitle(rawText) }

        var confidence = 0f
        if (date != null) confidence += 0.4f
        if (time != null) confidence += 0.35f
        if (recurrence != null) confidence += 0.1f
        if (title.isNotBlank()) confidence += 0.15f
        if (reminders.isNotEmpty()) confidence += 0.05f

        return EventDraft(
            title = title,
            start = start,
            end = end,
            allDay = allDay,
            location = location,
            recurrence = recurrence,
            reminderMinutes = reminders,
            confidence = confidence.coerceAtMost(1f),
            explanation = notes.joinToString(" · "),
        )
    }

    // ---------------------------------------------------------------------------------
    // Date
    // ---------------------------------------------------------------------------------

    private fun parseDate(
        text: String,
        lower: String,
        now: LocalDateTime,
        spans: MutableList<Span>,
        notes: MutableList<String>,
    ): LocalDate? {
        val today = now.toLocalDate()

        // 1. Numeric: 15/04/2026, 15-4-26, 2026-04-15
        Regex("""\b(\d{4})[-/](\d{1,2})[-/](\d{1,2})\b""").find(text)?.let { m ->
            runCatching {
                LocalDate.of(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt())
            }.getOrNull()?.let {
                spans += Span(m.range, "date")
                notes += "កាលបរិច្ឆេទ $it"
                return it
            }
        }
        Regex("""\b(\d{1,2})[/-](\d{1,2})(?:[/-](\d{2,4}))?\b""").find(text)?.let { m ->
            val day = m.groupValues[1].toInt()
            val month = m.groupValues[2].toInt()
            val yearRaw = m.groupValues[3]
            val year = when {
                yearRaw.isEmpty() -> today.year
                yearRaw.length <= 2 -> 2000 + yearRaw.toInt()
                else -> yearRaw.toInt()
            }
            runCatching { LocalDate.of(year, month, day) }.getOrNull()?.let {
                spans += Span(m.range, "date")
                notes += "កាលបរិច្ឆេទ $it"
                // A bare day/month already gone by is next year's.
                return if (yearRaw.isEmpty() && it.isBefore(today)) it.plusYears(1) else it
            }
        }

        // 2. Named month: ថ្ងៃទី ១៥ ខែមេសា ២០២៦ / 15 April 2026 / April 15
        for ((name, monthNo) in KhmerLexicon.SOLAR_MONTHS) {
            val idx = lower.indexOf(name)
            if (idx < 0) continue
            val around = text.substring(maxOf(0, idx - 24), minOf(text.length, idx + name.length + 24))
            val dayMatch = Regex("""\b(\d{1,2})\b""").find(around)
            val yearMatch = Regex("""\b(20\d{2})\b""").find(around)
            if (dayMatch != null) {
                val day = dayMatch.groupValues[1].toInt()
                val year = yearMatch?.groupValues?.get(1)?.toInt() ?: today.year
                runCatching { LocalDate.of(year, monthNo, day) }.getOrNull()?.let {
                    spans += Span(idx until (idx + name.length), "month")
                    val base = maxOf(0, idx - 24)
                    spans += Span((base + dayMatch.range.first)..(base + dayMatch.range.last), "day")
                    yearMatch?.let { y -> spans += Span((base + y.range.first)..(base + y.range.last), "year") }
                    notes += "កាលបរិច្ឆេទ $it"
                    return if (yearMatch == null && it.isBefore(today)) it.plusYears(1) else it
                }
            }
        }

        // 3. Relative day words
        for ((word, offset) in KhmerLexicon.RELATIVE_DAYS) {
            val idx = lower.indexOf(word)
            if (idx < 0) continue
            spans += Span(idx until (idx + word.length), "relative")
            notes += "$word → ${today.plusDays(offset.toLong())}"
            return today.plusDays(offset.toLong())
        }

        // 4. Weekday, optionally with a "next" marker
        for ((word, dow) in KhmerLexicon.WEEKDAYS) {
            val idx = lower.indexOf(word)
            if (idx < 0) continue
            // Skip a weekday that is only part of a recurrence phrase; that is handled there.
            val tail = lower.substring(minOf(lower.length, idx + word.length))
            val wantsNextWeek = KhmerLexicon.NEXT_WORDS.any { tail.trimStart().startsWith(it) } ||
                KhmerLexicon.NEXT_WORDS.any { lower.substring(0, idx).trimEnd().endsWith(it) }
            var d = today
            // "next Monday" said on a Monday means the one after this one, not today.
            do { d = d.plusDays(1) } while (d.dayOfWeek != dow)
            if (wantsNextWeek) d = d.plusWeeks(1)
            spans += Span(idx until (idx + word.length), "weekday")
            notes += "$word → $d"
            return d
        }

        // 5. "next week" / "next month" with no weekday
        if (KhmerLexicon.WEEK_WORDS.any { lower.contains(it) } &&
            KhmerLexicon.NEXT_WORDS.any { lower.contains(it) }
        ) {
            notes += "សប្តាហ៍ក្រោយ"
            return today.plusWeeks(1)
        }
        return null
    }

    // ---------------------------------------------------------------------------------
    // Time
    // ---------------------------------------------------------------------------------

    private fun parseTime(
        text: String,
        lower: String,
        spans: MutableList<Span>,
        notes: MutableList<String>,
    ): LocalTime? {
        val period = KhmerLexicon.TIME_OF_DAY.entries.firstOrNull { (word, _) ->
            // "may" is a month and "at" a preposition; only match period words as whole tokens.
            Regex("(?<![a-z])" + Regex.escape(word) + "(?![a-z])").containsMatchIn(lower)
        }?.value

        // ម៉ោង ២:៣០ / 2:30 / 14h30
        var m = Regex("""(?:ម៉ោង\s*)?\b(\d{1,2})\s*[:.h]\s*(\d{2})\b""").find(text)
        var hour: Int? = null
        var minute = 0
        if (m != null) {
            hour = m.groupValues[1].toIntOrNull()
            minute = m.groupValues[2].toIntOrNull() ?: 0
            spans += Span(m.range, "time")
        } else {
            // ម៉ោង ២ / at 2 / 2pm
            m = Regex("""ម៉ោង\s*(\d{1,2})""").find(text)
                ?: Regex("""\b(\d{1,2})\s*(?:am|pm)\b""").find(lower)
                ?: Regex("""\bat\s+(\d{1,2})\b""").find(lower)
            if (m != null) {
                hour = m.groupValues[1].toIntOrNull()
                spans += Span(m.range, "time")
            } else {
                // ម៉ោងពីររសៀល — the hour spelled out.
                val spelled = KhmerLexicon.NUMBER_WORDS.entries.firstOrNull { (w, _) ->
                    lower.contains("ម៉ោង$w") || lower.contains("ម៉ោង $w")
                }
                if (spelled != null) {
                    hour = spelled.value
                    val i = lower.indexOf(spelled.key)
                    if (i >= 0) spans += Span(i until (i + spelled.key.length), "time")
                }
            }
        }

        if (hour == null) {
            // A bare period word still pins the event to a sensible hour.
            return when (period) {
                KhmerLexicon.TimeOfDay.MORNING -> LocalTime.of(8, 0).also { notes += "ព្រឹក → 08:00" }
                KhmerLexicon.TimeOfDay.NOON -> LocalTime.of(12, 0).also { notes += "ថ្ងៃត្រង់ → 12:00" }
                KhmerLexicon.TimeOfDay.AFTERNOON -> LocalTime.of(14, 0).also { notes += "រសៀល → 14:00" }
                KhmerLexicon.TimeOfDay.EVENING -> LocalTime.of(18, 0).also { notes += "ល្ងាច → 18:00" }
                KhmerLexicon.TimeOfDay.NIGHT -> LocalTime.of(20, 0).also { notes += "យប់ → 20:00" }
                null -> null
            }
        }

        var h = hour
        if (h !in 0..23 || minute !in 0..59) return null
        when (period) {
            KhmerLexicon.TimeOfDay.MORNING -> if (h == 12) h = 0
            KhmerLexicon.TimeOfDay.NOON -> if (h < 12) h = 12
            KhmerLexicon.TimeOfDay.AFTERNOON,
            KhmerLexicon.TimeOfDay.EVENING,
            -> if (h < 12) h += 12
            KhmerLexicon.TimeOfDay.NIGHT -> if (h < 12) h += 12
            null -> Unit
        }
        if (h !in 0..23) return null
        val t = LocalTime.of(h, minute)
        notes += "ម៉ោង $t"
        return t
    }

    // ---------------------------------------------------------------------------------

    private fun parseDuration(lower: String, spans: MutableList<Span>): Int? {
        Regex("""(?:រយៈពេល\s*)?(\d{1,3})\s*(ម៉ោង|hours?|hrs?)\b""").find(lower)?.let { m ->
            spans += Span(m.range, "duration")
            return m.groupValues[1].toInt() * 60
        }
        Regex("""(?:រយៈពេល\s*)?(\d{1,3})\s*(នាទី|minutes?|mins?)\b""").find(lower)?.let { m ->
            // Guard against reading "30 នាទីមុន" (a reminder) as a duration.
            val tail = lower.substring(minOf(lower.length, m.range.last + 1)).trimStart()
            if (KhmerLexicon.BEFORE_WORDS.none { tail.startsWith(it) }) {
                spans += Span(m.range, "duration")
                return m.groupValues[1].toInt()
            }
        }
        return null
    }

    private fun parseReminders(lower: String, spans: MutableList<Span>): List<Int> {
        val out = linkedSetOf<Int>()
        // "៣០ នាទីមុន" / "30 minutes before"
        Regex("""(\d{1,3})\s*(នាទី|minutes?|mins?)\s*(មុន|before|ahead)""").findAll(lower).forEach { m ->
            out += m.groupValues[1].toInt()
            spans += Span(m.range, "reminder")
        }
        Regex("""(\d{1,2})\s*(ម៉ោង|hours?|hrs?)\s*(មុន|before|ahead)""").findAll(lower).forEach { m ->
            out += m.groupValues[1].toInt() * 60
            spans += Span(m.range, "reminder")
        }
        if (out.isEmpty() && KhmerLexicon.REMINDER_WORDS.any { lower.contains(it) }) {
            // A request to be reminded, with no interval given.
            out += 30
        }
        return out.toList()
    }

    private fun parseRecurrence(lower: String, spans: MutableList<Span>): RecurrenceRule? {
        val everyIdx = KhmerLexicon.EVERY_WORDS.map { lower.indexOf(it) }.filter { it >= 0 }.minOrNull()
            ?: return null
        val tail = lower.substring(everyIdx)

        for ((word, dow) in KhmerLexicon.WEEKDAYS) {
            if (tail.startsWith(word, 0) || tail.take(24).contains(word)) {
                spans += Span(everyIdx until minOf(lower.length, everyIdx + 24), "recurrence")
                return RecurrenceRule(Frequency.WEEKLY, byDay = setOf(dow))
            }
        }
        val head = tail.take(24)
        return when {
            head.contains("ថ្ងៃ") || head.contains("day") ->
                RecurrenceRule(Frequency.DAILY)
            KhmerLexicon.WEEK_WORDS.any { head.contains(it) } ->
                RecurrenceRule(Frequency.WEEKLY)
            head.contains("ខែ") || head.contains("month") ->
                RecurrenceRule(Frequency.MONTHLY)
            head.contains("ឆ្នាំ") || head.contains("year") ->
                RecurrenceRule(Frequency.YEARLY)
            else -> null
        }?.also { spans += Span(everyIdx until minOf(lower.length, everyIdx + head.length), "recurrence") }
    }

    private fun parseLocation(text: String, spans: MutableList<Span>): String? {
        for (marker in KhmerLexicon.LOCATION_MARKERS) {
            val idx = text.indexOf(marker, ignoreCase = true)
            if (idx < 0) continue
            val after = text.substring(idx + marker.length).trim()
            // Take a short run of words; anything longer is almost certainly the rest of the
            // sentence rather than a place name.
            val place = after.split(WS).take(4).joinToString(" ").trim(',', '.', '។', ' ')
            if (place.length in 2..48) {
                spans += Span(idx until (idx + marker.length + place.length).coerceAtMost(text.length), "location")
                return place
            }
        }
        return null
    }

    // ---------------------------------------------------------------------------------

    /** What is left of the text once every recognised date, time and marker is removed. */
    private fun buildTitle(text: String, spans: List<Span>): String {
        if (spans.isEmpty()) return cleanTitle(text)
        val keep = BooleanArray(text.length) { true }
        for (s in spans) {
            for (i in s.range) if (i in text.indices) keep[i] = false
        }
        val sb = StringBuilder(text.length)
        for (i in text.indices) if (keep[i]) sb.append(text[i])
        return cleanTitle(sb.toString())
    }

    private fun cleanTitle(raw: String): String {
        var s = raw
        for (w in KhmerLexicon.REMINDER_WORDS + KhmerLexicon.HOUR_MARKERS + KhmerLexicon.EVERY_WORDS) {
            s = s.replace(w, " ", ignoreCase = true)
        }
        for (w in listOf("ខ្ញុំ", "ឱ្យ", "អោយ", "នៅ", "me", "to", "at", "on", "the", "a")) {
            s = s.replace(Regex("(?<![\\p{L}])" + Regex.escape(w) + "(?![\\p{L}])", RegexOption.IGNORE_CASE), " ")
        }
        return WS.replace(s, " ").trim(' ', ',', '.', '-', ':', '។')
    }

    /**
     * Falls back to the user's own words rather than inventing a name. Khmer digits are put
     * back so the title reads the way it was typed.
     */
    private fun defaultTitle(rawText: String): String {
        val words = WS.split(rawText.trim()).take(6).joinToString(" ")
        return words.ifBlank { "ព្រឹត្តិការណ៍ថ្មី" }
    }
}
