package com.khmercalendar.ui.components

import com.khmercalendar.data.prefs.DateFormat
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

/**
 * The date-format preference.
 *
 * Worth testing because the preference spent three releases being written and never read: the
 * Appearance screen offered a choice of three orders and every screen in the app ignored all of
 * them. Now that [CalendarFormats.date] is the one place a date is rendered, these pin the
 * behaviour each choice promises.
 */
class CalendarFormatsTest {

    private val date = LocalDate.of(2026, 1, 5)
    private val defaultLocale: Locale = Locale.getDefault()

    @After
    fun restoreLocale() = Locale.setDefault(defaultLocale)

    @Test
    fun `each numeric format uses the order its label promises`() {
        assertEquals("05/01/2026", CalendarFormats.date(date, DateFormat.DMY, false))
        assertEquals("2026-01-05", CalendarFormats.date(date, DateFormat.YMD, false))
        assertEquals("01/05/2026", CalendarFormats.date(date, DateFormat.MDY, false))
    }

    @Test
    fun `the Khmer format writes the month out`() {
        assertEquals("5 មករា 2026", CalendarFormats.date(date, DateFormat.KHMER, false))
    }

    @Test
    fun `Khmer numerals apply to every format`() {
        assertEquals("០៥/០១/២០២៦", CalendarFormats.date(date, DateFormat.DMY, true))
        assertEquals("២០២៦-០១-០៥", CalendarFormats.date(date, DateFormat.YMD, true))
        assertEquals("៥ មករា ២០២៦", CalendarFormats.date(date, DateFormat.KHMER, true))
    }

    @Test
    fun `the Khmer month name does not follow the device locale`() {
        // KHMER once carried the pattern "d MMMM yyyy", and MMMM resolves against the JVM
        // locale - so a phone set to English would have printed "5 January 2026" inside an
        // otherwise entirely Khmer screen. The month name is looked up, not formatted.
        Locale.setDefault(Locale.US)
        assertEquals("៥ មករា ២០២៦", CalendarFormats.date(date, DateFormat.KHMER, true))

        Locale.setDefault(Locale.FRANCE)
        assertEquals("៥ មករា ២០២៦", CalendarFormats.date(date, DateFormat.KHMER, true))
    }

    @Test
    fun `the prose form carries the Khmer particles`() {
        assertEquals("ទី៥ ខែមករា ឆ្នាំ២០២៦", CalendarFormats.writtenDate(date, DateFormat.KHMER, true))
    }

    @Test
    fun `the prose form falls back to the numeric order when one is chosen`() {
        // There is no prose rendering of "05/01/2026" - someone who asked for a numeric date
        // gets it everywhere, headline included.
        assertEquals("05/01/2026", CalendarFormats.writtenDate(date, DateFormat.DMY, false))
        assertEquals("2026-01-05", CalendarFormats.writtenDate(date, DateFormat.YMD, false))
    }

    @Test
    fun `the numeric formats are stable across device locales`() {
        // A locale with its own numbering system (Arabic-Indic digits) must not leak into a
        // date the user asked to see in ASCII.
        Locale.setDefault(Locale.forLanguageTag("ar-EG"))
        val formatted = CalendarFormats.date(date, DateFormat.DMY, false)
        assertEquals("05/01/2026", formatted)
        assertTrue(formatted.all { it.isDigit() || it == '/' })
    }
}
