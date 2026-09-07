package com.khmercalendar.core.holiday

import com.khmercalendar.core.khmer.KhmerNewYear
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class KhmerHolidaysTest {

    /**
     * Khmer New Year as gazetted. 2020, 2024 and 2028 are four-day years; the rest are three.
     */
    @Test
    fun `Khmer New Year matches the published dates`() {
        assertEquals(LocalDate.of(2024, 4, 13), KhmerNewYear.of(2024).days.first())
        assertEquals(4, KhmerNewYear.of(2024).days.size)

        assertEquals(LocalDate.of(2025, 4, 14), KhmerNewYear.of(2025).days.first())
        assertEquals(3, KhmerNewYear.of(2025).days.size)

        // Sub-decree No. 167 of 18 September 2025 sets 2026 at 14-16 April.
        assertEquals(LocalDate.of(2026, 4, 14), KhmerNewYear.of(2026).days.first())
        assertEquals(LocalDate.of(2026, 4, 16), KhmerNewYear.of(2026).days.last())
    }

    @Test
    fun `Lerng Sak is the last day of the festival`() {
        for (y in 2020..2035) {
            val kny = KhmerNewYear.of(y)
            assertEquals(kny.days.last(), kny.lerngSak)
            assertEquals(kny.mohaSangkran.toLocalDate(), kny.days.first())
        }
    }

    @Test
    fun `Pchum Ben lands on the published days`() {
        // Ben Thom is the fifteenth waning day of ភទ្របទ; the holiday runs the day before,
        // Ben Thom itself and the day after.
        val y2026 = KhmerHolidays.forYear(2026).filter { it.nameEn.startsWith("Pchum Ben") }
        assertEquals(3, y2026.size)
        assertEquals(LocalDate.of(2026, 10, 10), y2026.first().date)
        assertEquals(LocalDate.of(2026, 10, 12), y2026.last().date)

        val y2024 = KhmerHolidays.forYear(2024).filter { it.nameEn.startsWith("Pchum Ben") }
        assertEquals(LocalDate.of(2024, 10, 1), y2024.first().date)
        assertEquals(LocalDate.of(2024, 10, 3), y2024.last().date)
    }

    @Test
    fun `Visak Bochea is a public holiday on the computed day`() {
        val h = KhmerHolidays.forYear(2026).first { it.nameEn == "Visak Bochea Day" }
        assertEquals(LocalDate.of(2026, 5, 1), h.date)
        assertEquals(HolidayKind.PUBLIC, h.kind)
    }

    @Test
    fun `a gazetted override wins over the computed date`() {
        val water = KhmerHolidays.forYear(2026).filter { it.nameEn.startsWith("Water Festival") }
        assertEquals(3, water.size)
        assertEquals(LocalDate.of(2026, 11, 24), water.first().date)
        assertEquals(LocalDate.of(2026, 11, 26), water.last().date)
        assertTrue(water.all { it.source == HolidaySource.GAZETTED })
    }

    @Test
    fun `fixed holidays are present every year`() {
        for (y in 2024..2030) {
            val dates = KhmerHolidays.forYear(y).map { it.date }
            assertTrue("$y missing 7 January", LocalDate.of(y, 1, 7) in dates)
            assertTrue("$y missing Independence Day", LocalDate.of(y, 11, 9) in dates)
            assertTrue("$y missing Constitution Day", LocalDate.of(y, 9, 24) in dates)
        }
    }

    @Test
    fun `every holiday returned for a year actually falls in it`() {
        for (y in 2000..2100) {
            KhmerHolidays.forYear(y).forEach {
                assertEquals("$it", y, it.date.year)
            }
        }
    }

    @Test
    fun `range query spans year boundaries`() {
        val list = KhmerHolidays.inRange(LocalDate.of(2025, 12, 20), LocalDate.of(2026, 1, 10))
        assertTrue(list.any { it.date == LocalDate.of(2026, 1, 1) })
        assertTrue(list.any { it.date == LocalDate.of(2026, 1, 7) })
        assertTrue(list.all { !it.date.isBefore(LocalDate.of(2025, 12, 20)) })
    }

    @Test
    fun `public holiday lookup is exact`() {
        assertTrue(KhmerHolidays.isPublicHoliday(LocalDate.of(2026, 1, 7)))
        assertTrue(!KhmerHolidays.isPublicHoliday(LocalDate.of(2026, 1, 8)))
    }
}
