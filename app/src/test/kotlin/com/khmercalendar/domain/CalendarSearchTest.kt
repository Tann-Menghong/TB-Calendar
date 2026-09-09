package com.khmercalendar.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * The text handling behind search.
 *
 * The snippet is the part worth testing: it is the only place in search that cuts Khmer text,
 * and cutting Khmer badly is how this codebase has produced bugs before.
 */
class CalendarSearchTest {

    // --- what counts as a query ---

    @Test
    fun `a single character is not a query`() {
        assertFalse(CalendarSearch.isSearchable("ប"))
        assertFalse(CalendarSearch.isSearchable(" "))
        assertFalse(CalendarSearch.isSearchable(""))
        assertTrue(CalendarSearch.isSearchable("ប្រ"))
        assertTrue(CalendarSearch.isSearchable("ab"))
    }

    @Test
    fun `surrounding space does not make a query searchable`() {
        assertFalse(CalendarSearch.isSearchable("  a  "))
        assertTrue(CalendarSearch.isSearchable("  ab  "))
    }

    // --- matching ---

    @Test
    fun `matching ignores case and surrounding space`() {
        assertTrue(CalendarSearch.matches("Work Meeting", "  meeting "))
        assertTrue(CalendarSearch.matches("ការងារ", "ការ"))
        assertFalse(CalendarSearch.matches("ការងារ", "គ្រួសារ"))
    }

    @Test
    fun `a blank haystack never matches`() {
        assertFalse(CalendarSearch.matches(null, "a"))
        assertFalse(CalendarSearch.matches("", "a"))
        assertFalse(CalendarSearch.matches("   ", "a"))
    }

    // --- snippets ---

    @Test
    fun `a short note is shown whole`() {
        val note = "ទិញអំបិល"
        assertEquals(note, CalendarSearch.snippet(note, "អំបិល"))
    }

    @Test
    fun `a long note is centred on the match`() {
        val filler = "ក".repeat(100)
        val note = filler + "អំបិល" + filler
        val snippet = CalendarSearch.snippet(note, "អំបិល", radius = 10)

        assertTrue("must contain the match: $snippet", snippet.contains("អំបិល"))
        assertTrue("must mark the cut at the start", snippet.startsWith("…"))
        assertTrue("must mark the cut at the end", snippet.endsWith("…"))
        // 10 either side, the needle itself, and the two ellipses.
        assertEquals(10 + "អំបិល".length + 10 + 2, snippet.length)
    }

    @Test
    fun `a match at the very start is not given a leading ellipsis`() {
        val snippet = CalendarSearch.snippet("អំបិល" + "ក".repeat(100), "អំបិល", radius = 10)
        assertFalse(snippet, snippet.startsWith("…"))
        assertTrue(snippet, snippet.endsWith("…"))
    }

    @Test
    fun `newlines are flattened so a snippet stays one line`() {
        val note = "ដំបូង\nទីពីរ\nទីបី"
        assertEquals("ដំបូង ទីពីរ ទីបី", CalendarSearch.snippet(note, "ទីពីរ"))
    }

    @Test
    fun `a note that does not contain the query still shows its opening`() {
        // The database matched on something this function cannot see - a different case, or
        // an escaped wildcard - so the honest fallback is the start of the note rather than
        // an empty row.
        val note = "ក".repeat(200)
        val snippet = CalendarSearch.snippet(note, "zzz", radius = 10)
        assertTrue(snippet, snippet.startsWith("ក"))
        assertTrue("must mark that it was cut: $snippet", snippet.endsWith("…"))
        assertEquals(10 * 2 + "zzz".length + 1, snippet.length)
    }

    @Test
    fun `matching is case insensitive when centring`() {
        val note = "x".repeat(50) + "Meeting" + "x".repeat(50)
        val snippet = CalendarSearch.snippet(note, "meeting", radius = 5)
        assertTrue(snippet, snippet.contains("Meeting"))
    }

    // --- the results container ---

    @Test
    fun `results are empty only when every section is`() {
        assertTrue(SearchResults().isEmpty)
        val withDate = SearchResults(
            dates = listOf(SearchResult.DateJump(LocalDate.of(2026, 9, 9), yearAssumed = false)),
        )
        assertFalse(withDate.isEmpty)
        assertEquals(1, withDate.total)
    }

    @Test
    fun `total counts every section`() {
        val results = SearchResults(
            dates = listOf(SearchResult.DateJump(LocalDate.of(2026, 9, 9), false)),
            events = listOf(
                SearchResult.Event(1, "a", "", LocalDate.of(2026, 9, 9), 0, false, false),
            ),
            notes = listOf(SearchResult.Note(LocalDate.of(2026, 9, 9), "n")),
            holidays = listOf(SearchResult.Holiday(LocalDate.of(2026, 9, 9), "h")),
        )
        assertEquals(4, results.total)
    }
}
