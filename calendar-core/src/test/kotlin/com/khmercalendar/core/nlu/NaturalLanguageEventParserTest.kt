package com.khmercalendar.core.nlu

import com.khmercalendar.core.recurrence.Frequency
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDateTime

class NaturalLanguageEventParserTest {

    private val parser = NaturalLanguageEventParser()

    /** A Monday, so weekday arithmetic is easy to reason about. */
    private val now = LocalDateTime.of(2026, 9, 7, 9, 0)

    @Test
    fun `the example from the product brief`() {
        val d = parser.parse("ថ្ងៃស្អែកម៉ោង ២ រសៀល រំលឹកខ្ញុំឱ្យប្រជុំជាមួយក្រុមការងារ", now)
        assertNotNull(d)
        d!!
        assertEquals(LocalDateTime.of(2026, 9, 8, 14, 0), d.start)
        assertTrue("title was '${d.title}'", d.title.contains("ប្រជុំ"))
        assertTrue(d.reminderMinutes.isNotEmpty())
        assertTrue(d.confidence >= NaturalLanguageEventParser.MIN_CONFIDENCE)
    }

    @Test
    fun `Khmer numerals are understood`() {
        val d = parser.parse("ស្អែក ម៉ោង ៨ ព្រឹក ជួបគ្រូពេទ្យ", now)!!
        assertEquals(LocalDateTime.of(2026, 9, 8, 8, 0), d.start)
    }

    @Test
    fun `afternoon and evening push the hour past noon`() {
        assertEquals(14, parser.parse("ថ្ងៃនេះ ម៉ោង ២ រសៀល", now)!!.start.hour)
        assertEquals(18, parser.parse("ថ្ងៃនេះ ម៉ោង ៦ ល្ងាច", now)!!.start.hour)
        assertEquals(20, parser.parse("ថ្ងៃនេះ ម៉ោង ៨ យប់", now)!!.start.hour)
        assertEquals(8, parser.parse("ថ្ងៃនេះ ម៉ោង ៨ ព្រឹក", now)!!.start.hour)
        // 12 in the morning is midnight, not noon.
        assertEquals(0, parser.parse("ថ្ងៃនេះ ម៉ោង ១២ ព្រឹក", now)!!.start.hour)
    }

    @Test
    fun `minutes are kept`() {
        val d = parser.parse("ស្អែក ម៉ោង ២:៣០ រសៀល ប្រជុំ", now)!!
        assertEquals(LocalDateTime.of(2026, 9, 8, 14, 30), d.start)
    }

    @Test
    fun `a weekday means the next one, not today`() {
        // now is a Monday.
        val d = parser.parse("ថ្ងៃចន្ទ ម៉ោង ៩ ព្រឹក ប្រជុំក្រុម", now)!!
        assertEquals(LocalDateTime.of(2026, 9, 14, 9, 0), d.start)
    }

    @Test
    fun `an explicit Khmer date is used as written`() {
        val d = parser.parse("ថ្ងៃទី ១៥ ខែមេសា ២០២៧ ម៉ោង ១០ ព្រឹក ពិធីមង្គលការ", now)!!
        assertEquals(LocalDateTime.of(2027, 4, 15, 10, 0), d.start)
    }

    @Test
    fun `English input works too`() {
        val d = parser.parse("Meeting with the team tomorrow at 3 pm for 2 hours", now)!!
        assertEquals(LocalDateTime.of(2026, 9, 8, 15, 0), d.start)
        assertEquals(LocalDateTime.of(2026, 9, 8, 17, 0), d.end)
        assertTrue("title was '${d.title}'", d.title.lowercase().contains("meeting"))
    }

    @Test
    fun `duration sets the end, otherwise an hour is assumed`() {
        assertEquals(
            LocalDateTime.of(2026, 9, 8, 15, 0),
            parser.parse("ស្អែក ម៉ោង ២ រសៀល រយៈពេល ១ ម៉ោង ប្រជុំ", now)!!.end,
        )
        assertEquals(
            LocalDateTime.of(2026, 9, 8, 15, 0),
            parser.parse("ស្អែក ម៉ោង ២ រសៀល ប្រជុំ", now)!!.end,
        )
    }

    @Test
    fun `an explicit reminder interval is read, not the default`() {
        val d = parser.parse("ស្អែក ម៉ោង ៩ ព្រឹក ប្រជុំ រំលឹក ៣០ នាទីមុន", now)!!
        assertEquals(listOf(30), d.reminderMinutes)
        val h = parser.parse("ស្អែក ម៉ោង ៩ ព្រឹក ប្រជុំ រំលឹក ២ ម៉ោងមុន", now)!!
        assertEquals(listOf(120), h.reminderMinutes)
    }

    @Test
    fun `a reminder interval is not mistaken for a duration`() {
        val d = parser.parse("ស្អែក ម៉ោង ៩ ព្រឹក ប្រជុំ រំលឹក ៣០ នាទីមុន", now)!!
        assertEquals(LocalDateTime.of(2026, 9, 8, 10, 0), d.end)
    }

    @Test
    fun `recurrence phrases become rules`() {
        assertEquals(Frequency.DAILY, parser.parse("រៀងរាល់ថ្ងៃ ម៉ោង ៦ ព្រឹក រត់ប្រណាំង", now)!!.recurrence?.frequency)
        assertEquals(Frequency.MONTHLY, parser.parse("រៀងរាល់ខែ ម៉ោង ៩ ព្រឹក បង់ថ្លៃផ្ទះ", now)!!.recurrence?.frequency)
        val weekly = parser.parse("រាល់ថ្ងៃចន្ទ ម៉ោង ៨ ព្រឹក ប្រជុំ", now)!!.recurrence
        assertEquals(Frequency.WEEKLY, weekly?.frequency)
        assertEquals(setOf(DayOfWeek.MONDAY), weekly?.byDay)
        assertEquals(Frequency.WEEKLY, parser.parse("every friday at 5pm football", now)!!.recurrence?.frequency)
    }

    @Test
    fun `a date with no time is all-day`() {
        val d = parser.parse("ថ្ងៃស្អែក ខួបកំណើតម្តាយ", now)!!
        assertTrue(d.allDay)
        assertEquals(LocalDateTime.of(2026, 9, 8, 0, 0), d.start)
    }

    @Test
    fun `a time already past today rolls to tomorrow`() {
        // now is 09:00; 8am has gone.
        val d = parser.parse("ម៉ោង ៨ ព្រឹក ជួបគ្រូពេទ្យ", now)!!
        assertEquals(LocalDateTime.of(2026, 9, 8, 8, 0), d.start)
    }

    @Test
    fun `text with no date or time is declined rather than guessed`() {
        assertNull(parser.parse("ប្រជុំជាមួយក្រុមការងារ", now))
        assertNull(parser.parse("", now))
        assertNull(parser.parse("hello there", now))
    }

    @Test
    fun `a title is always produced`() {
        val d = parser.parse("ស្អែក ម៉ោង ២ រសៀល", now)!!
        assertTrue(d.title.isNotBlank())
    }
}
