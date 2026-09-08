package com.khmercalendar.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

/** The header greeting: four bands, and no hour without one. */
class DayGreetingTest {

    @Test
    fun `each band gets its own greeting`() {
        assertEquals("អរុណសួស្តី", DayGreeting.of(LocalTime.of(7, 0)))
        assertEquals("ទិវាសួស្តី", DayGreeting.of(LocalTime.of(13, 0)))
        assertEquals("សាយណ្ហសួស្តី", DayGreeting.of(LocalTime.of(16, 0)))
        assertEquals("រាត្រីសួស្តី", DayGreeting.of(LocalTime.of(21, 0)))
    }

    @Test
    fun `the small hours are night, not morning`() {
        assertEquals("រាត្រីសួស្តី", DayGreeting.of(LocalTime.MIDNIGHT))
        assertEquals("រាត្រីសួស្តី", DayGreeting.of(LocalTime.of(4, 59)))
        assertEquals("អរុណសួស្តី", DayGreeting.of(LocalTime.of(5, 0)))
    }

    @Test
    fun `noon is afternoon and eleven is still morning`() {
        assertEquals("អរុណសួស្តី", DayGreeting.of(LocalTime.of(11, 59)))
        assertEquals("ទិវាសួស្តី", DayGreeting.of(LocalTime.NOON))
    }

    @Test
    fun `a name is appended, and a blank one leaves a finished sentence`() {
        assertEquals("អរុណសួស្តី សុភា", DayGreeting.of(LocalTime.of(7, 0), "សុភា"))
        assertEquals("អរុណសួស្តី", DayGreeting.of(LocalTime.of(7, 0), ""))
        // Whitespace is not a name. Without this the header reads "អរុណសួស្តី " with a
        // trailing space the user never typed.
        assertEquals("អរុណសួស្តី", DayGreeting.of(LocalTime.of(7, 0), "   "))
    }

    @Test
    fun `every hour of the day has a greeting`() {
        (0..23).forEach { hour ->
            assertTrue("$hour", DayGreeting.of(LocalTime.of(hour, 30)).isNotBlank())
        }
    }
}
