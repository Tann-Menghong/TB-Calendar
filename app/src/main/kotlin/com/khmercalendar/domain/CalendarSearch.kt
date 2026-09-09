package com.khmercalendar.domain

import java.time.LocalDate

/**
 * One thing search found.
 *
 * A sealed hierarchy rather than a flat row with nullable fields, because the four kinds are
 * genuinely different: a date is somewhere to go, an event and a note are things to open, and
 * a holiday is a fact about a day. Flattening them would put an `eventId` on a holiday and a
 * colour on a date, and every screen reading the list would have to know which fields lie.
 */
sealed interface SearchResult {

    /** The day this result belongs to, which is what every kind can be opened at. */
    val date: LocalDate

    /**
     * The query parsed as a date - "go to this day" rather than "find this word".
     *
     * @property yearAssumed the query carried no year, so this is the next occurrence.
     */
    data class DateJump(
        override val date: LocalDate,
        val yearAssumed: Boolean,
    ) : SearchResult

    data class Event(
        val eventId: Long,
        val title: String,
        val subtitle: String,
        override val date: LocalDate,
        val colorArgb: Int,
        val isTask: Boolean,
        val isCompleted: Boolean,
    ) : SearchResult

    /** A day note. Opened at its day, since a note has no identity apart from its date. */
    data class Note(
        override val date: LocalDate,
        val snippet: String,
    ) : SearchResult

    data class Holiday(
        override val date: LocalDate,
        val name: String,
    ) : SearchResult
}

/**
 * What search found, in sections.
 *
 * Sectioned rather than interleaved by relevance. A calendar's search results are not
 * comparable to one another - there is no honest way to rank "the note on the 3rd" against
 * "a holiday called ចូលឆ្នាំ" - and a single mixed list would need a score nobody could
 * explain. Sections also mean an empty kind simply does not appear, instead of pushing what
 * you wanted below a heading you did not care about.
 */
data class SearchResults(
    val dates: List<SearchResult.DateJump> = emptyList(),
    val events: List<SearchResult.Event> = emptyList(),
    val notes: List<SearchResult.Note> = emptyList(),
    val holidays: List<SearchResult.Holiday> = emptyList(),
) {
    val isEmpty: Boolean
        get() = dates.isEmpty() && events.isEmpty() && notes.isEmpty() && holidays.isEmpty()

    val total: Int get() = dates.size + events.size + notes.size + holidays.size
}

/**
 * The parts of search that are text handling rather than database work.
 *
 * Pulled out so they can be tested without a database: both of these have failed before in
 * ways that only show on Khmer text.
 */
object CalendarSearch {

    /** Below this a query matches most of the calendar and means nothing. */
    const val MIN_QUERY_LENGTH = 2

    /** Beyond this the screen is a list nobody reads. */
    const val LIMIT_PER_SECTION = 30

    fun isSearchable(query: String): Boolean = query.trim().length >= MIN_QUERY_LENGTH

    /**
     * Whether [haystack] contains [needle], ignoring case and surrounding space.
     *
     * Used for the matches SQLite does not do - a category's name, a holiday's - so that they
     * behave the same way as the LIKE scan the event table gets.
     */
    fun matches(haystack: String?, needle: String): Boolean {
        if (haystack.isNullOrBlank()) return false
        return haystack.contains(needle.trim(), ignoreCase = true)
    }

    /**
     * The part of a note worth showing beside a hit.
     *
     * A day note can be hundreds of characters, and the first 60 of them are usually not the
     * ones that matched. This centres the window on the match.
     *
     * **Cut by character index, never by word.** Khmer is written without spaces, so a
     * "trim to the nearest word boundary" step - the obvious refinement - either does nothing
     * or swallows the entire note as one token. The ellipses say the text was cut, which is
     * all the reader needs.
     */
    fun snippet(text: String, query: String, radius: Int = 32): String {
        val flat = text.replace('\n', ' ').trim()
        val needle = query.trim()
        val window = radius * 2 + needle.length

        // Whole note when it fits. When it does not and the needle is not in it - which
        // happens when SQLite matched something this function cannot see, such as an
        // escaped wildcard - the opening is the honest fallback, measured in its own
        // right rather than in terms of a needle that is not there.
        if (flat.length <= window) return flat
        val at = flat.indexOf(needle, ignoreCase = true)
        if (at < 0) return flat.take(window) + "…"

        val start = (at - radius).coerceAtLeast(0)
        val end = (at + needle.length + radius).coerceAtMost(flat.length)
        return buildString {
            if (start > 0) append("…")
            append(flat, start, end)
            if (end < flat.length) append("…")
        }
    }
}
