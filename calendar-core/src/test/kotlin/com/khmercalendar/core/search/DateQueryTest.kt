package com.khmercalendar.core.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Reading a date out of a search box.
 *
 * The failure mode worth testing is not "does not parse" - it is *parses into the wrong day*,
 * silently, and takes the user to the wrong month. So the ambiguous cases are asserted on
 * their whole ordered list rather than on the first entry.
 */
class DateQueryTest {

    private val today = LocalDate.of(2026, 9, 9)

    private fun parse(raw: String, order: DateQuery.Order = DateQuery.Order.DMY) =
        DateQuery.parse(raw, today, order)

    private fun dates(raw: String, order: DateQuery.Order = DateQuery.Order.DMY) =
        parse(raw, order).map { it.date.toString() }

    // --- numeric ---

    @Test
    fun `a full date in the user's own order is read first`() {
        assertEquals("2027-01-15", dates("15/1/2027").first())
        assertEquals("2027-01-15", dates("2027-01-15", DateQuery.Order.YMD).first())
        assertEquals("2027-01-15", dates("1/15/2027", DateQuery.Order.MDY).first())
    }

    @Test
    fun `every separator the app itself prints is accepted`() {
        for (raw in listOf("15/1/2027", "15-1-2027", "15.1.2027", "15 1 2027")) {
            assertEquals(raw, "2027-01-15", dates(raw).first())
        }
    }

    @Test
    fun `a four digit year is found wherever it sits`() {
        // Read as DMY first, but 2027 cannot be a day, so that reading is dropped and the
        // year-first one survives.
        assertEquals("2027-01-15", dates("2027/1/15").first())
    }

    @Test
    fun `an ambiguous pair offers both readings, preference first`() {
        assertEquals(listOf("2027-02-01", "2027-01-02"), dates("1/2"))
        assertEquals(listOf("2027-01-02", "2027-02-01"), dates("1/2", DateQuery.Order.MDY))
    }

    @Test
    fun `an unambiguous pair offers only the reading that is a date`() {
        // 15 cannot be a month, so there is one reading.
        assertEquals(listOf("2027-01-15"), dates("15/1"))
    }

    @Test
    fun `a missing year means the next time that day comes round`() {
        val match = parse("1/10").first()
        assertEquals(LocalDate.of(2026, 10, 1), match.date)
        assertTrue("the caller must be told the year was assumed", match.yearAssumed)
    }

    @Test
    fun `today's own date finds today rather than next year`() {
        assertEquals(LocalDate.of(2026, 9, 9), parse("9/9").first().date)
    }

    @Test
    fun `a day already past this year rolls to next year`() {
        assertEquals(LocalDate.of(2027, 1, 1), parse("1/1").first().date)
    }

    @Test
    fun `29 February skips to a year that has one`() {
        assertEquals(LocalDate.of(2028, 2, 29), parse("29/2").first().date)
    }

    @Test
    fun `an impossible date is not a date`() {
        assertTrue(dates("31/4/2027").toString(), parse("31/4/2027").isEmpty())
        assertTrue(parse("32/1/2027").isEmpty())
        assertTrue(parse("15/13/2027").isEmpty())
    }

    @Test
    fun `a year outside the app's range is rejected`() {
        assertTrue(parse("15/1/1800").isEmpty())
        assertTrue(parse("15/1/2500").isEmpty())
    }

    // --- Khmer ---

    @Test
    fun `khmer numerals parse the same as ascii`() {
        assertEquals(dates("15/1/2027"), dates("១៥/១/២០២៧"))
    }

    @Test
    fun `a written khmer month is understood in either order`() {
        assertEquals("2027-01-15", dates("15 មករា 2027").first())
        assertEquals("2027-01-15", dates("មករា 15 2027").first())
    }

    @Test
    fun `a written month without a year rolls forward`() {
        val match = parse("15 មករា").first()
        assertEquals(LocalDate.of(2027, 1, 15), match.date)
        assertTrue(match.yearAssumed)
    }

    @Test
    fun `a written date in khmer numerals as the app prints it`() {
        assertEquals("2027-01-15", dates("១៥ មករា ២០២៧").first())
    }

    // --- not dates ---

    @Test
    fun `ordinary search text is not a date`() {
        for (raw in listOf("", "   ", "ប្រជុំ", "meeting", "12345", "a/b", "1/2/3/4")) {
            assertTrue("'$raw' should not parse", parse(raw).isEmpty())
        }
    }

    @Test
    fun `a bare number is not a date`() {
        // Deliberate: "15" alone is far more likely to be part of a title than a request to
        // jump to the 15th, and offering a date row for every number typed is noise.
        assertTrue(parse("15").isEmpty())
    }
}
