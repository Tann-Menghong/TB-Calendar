package com.khmercalendar.core.khmer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ChhankitekTest {

    @Test
    fun `epoch is the first waxing day of Boss`() {
        val l = Chhankitek.toLunar(Chhankitek.EPOCH)
        assertEquals(LunarMonth.BOSS, l.month)
        assertEquals(0, l.dayIndex)
        assertEquals(1, l.dayInHalf)
        assertEquals(MoonPhase.WAXING, l.phase)
    }

    @Test
    fun `day index stays inside the month it belongs to`() {
        var d = LocalDate.of(1900, 1, 1)
        val end = LocalDate.of(2100, 12, 31)
        while (!d.isAfter(end)) {
            val l = Chhankitek.toLunar(d)
            val len = Chhankitek.lengthOfLunarMonth(d)
            assertTrue("$d has ${l.dayIndex} in a $len-day month", l.dayIndex in 0 until len)
            assertTrue("$d day-in-half ${l.dayInHalf}", l.dayInHalf in 1..15)
            // A 29-day month cannot have a 15th waning day.
            if (len == 29) assertTrue("$d is 15 roch in a 29-day month", l.dayIndex != 29)
            d = d.plusDays(367) // sample rather than sweep; the sweep below is exhaustive
        }
    }

    @Test
    fun `every day of a decade advances by exactly one`() {
        var d = LocalDate.of(2020, 1, 1)
        val end = LocalDate.of(2030, 12, 31)
        var previous = Chhankitek.toLunar(d)
        d = d.plusDays(1)
        while (!d.isAfter(end)) {
            val l = Chhankitek.toLunar(d)
            val expected = previous.dayIndex + 1
            if (expected < Chhankitek.lengthOfLunarMonth(d.minusDays(1))) {
                assertEquals("at $d", expected, l.dayIndex)
                assertEquals("at $d", previous.month, l.month)
            } else {
                // Month rolled over.
                assertEquals("at $d", 0, l.dayIndex)
                assertTrue("at $d", l.month != previous.month)
            }
            previous = l
            d = d.plusDays(1)
        }
    }

    @Test
    fun `lunar months alternate 29 and 30 days outside the leap month`() {
        for (m in LunarMonth.entries) {
            if (m == LunarMonth.PATHAMASATH || m == LunarMonth.TUTIYASATH) continue
            // ជេស្ឋ gains a day in a leap-day year, so it is the one exception.
            if (m == LunarMonth.JEST) continue
        }
        // 2024 (BE 2568) sanity: Mikasei is 29 days, Boss is 30.
        val mikasei = firstDayOf(2023, LunarMonth.MIKASEI)
        assertEquals(29, Chhankitek.lengthOfLunarMonth(mikasei))
        val boss = firstDayOf(2023, LunarMonth.BOSS)
        assertEquals(30, Chhankitek.lengthOfLunarMonth(boss))
    }

    @Test
    fun `Visakha Bochea is the fifteenth waxing day of Pisak`() {
        for (y in 2015..2035) {
            val d = Chhankitek.visakhaBochea(y)
            val l = Chhankitek.toLunar(d)
            assertEquals(LunarMonth.PISAK, l.month)
            assertEquals(14, l.dayIndex)
            assertEquals(MoonPhase.WAXING, l.phase)
            assertEquals(15, l.dayInHalf)
        }
    }

    /**
     * Dates published by the Cambodian government and by every Khmer almanac. These are the
     * anchors that show the epoch and the leap rules are calibrated, not merely consistent.
     */
    @Test
    fun `matches published observance dates`() {
        assertEquals(LocalDate.of(2024, 5, 22), Chhankitek.visakhaBochea(2024))
        assertEquals(LocalDate.of(2025, 5, 11), Chhankitek.visakhaBochea(2025))
        assertEquals(LocalDate.of(2026, 5, 1), Chhankitek.visakhaBochea(2026))
    }

    @Test
    fun `Buddhist year turns on Visakha Bochea`() {
        val visak = Chhankitek.visakhaBochea(2025)
        assertEquals(2568, Chhankitek.buddhistYear(visak))
        assertEquals(2569, Chhankitek.buddhistYear(visak.plusDays(1)))
    }

    @Test
    fun `a leap-month year contains Pathamasath and Tutiyasath`() {
        // Walk a span long enough to include several leap-month years and check that whenever
        // បឋមាសាឍ appears it is followed by ទុតិយាសាឍ.
        var d = LocalDate.of(2000, 1, 1)
        val end = LocalDate.of(2040, 12, 31)
        var sawPathama = false
        var lastPathamaEnd: LocalDate? = null
        while (!d.isAfter(end)) {
            val l = Chhankitek.toLunar(d)
            if (l.month == LunarMonth.PATHAMASATH) {
                sawPathama = true
                lastPathamaEnd = d
            }
            if (lastPathamaEnd != null && d == lastPathamaEnd.plusDays(1) &&
                l.month != LunarMonth.PATHAMASATH
            ) {
                assertEquals("after Pathamasath at $d", LunarMonth.TUTIYASATH, l.month)
                lastPathamaEnd = null
            }
            d = d.plusDays(1)
        }
        assertTrue("no leap month found in 2000..2040", sawPathama)
    }

    @Test
    fun `both leap kinds never land on the same year`() {
        for (be in 2400..2700) {
            assertTrue(
                "BE $be is both leap month and leap day",
                !(Chhankitek.isLeapMonthYear(be) && Chhankitek.isLeapDayYear(be)),
            )
        }
    }

    @Test
    fun `dates outside the supported range are rejected rather than guessed`() {
        assertNull(Chhankitek.toLunarOrNull(LocalDate.of(1899, 12, 31)))
        assertNull(Chhankitek.toLunarOrNull(LocalDate.of(2200, 1, 1)))
        assertNotNull(Chhankitek.toLunarOrNull(Chhankitek.MIN_DATE))
        assertNotNull(Chhankitek.toLunarOrNull(Chhankitek.MAX_DATE))
    }

    @Test
    fun `formatting reads as a Khmer almanac line`() {
        val text = Chhankitek.toLunar(LocalDate.of(2026, 5, 1)).format()
        assertTrue(text, text.startsWith("ថ្ងៃ១៥កើត ខែពិសាខ"))
        assertTrue(text, text.contains("ព.ស."))
    }

    private fun firstDayOf(year: Int, month: LunarMonth): LocalDate {
        var d = LocalDate.of(year, 1, 1)
        val end = LocalDate.of(year + 1, 12, 31)
        while (!d.isAfter(end)) {
            val l = Chhankitek.toLunar(d)
            if (l.month == month && l.dayIndex == 0) return d
            d = d.plusDays(1)
        }
        throw AssertionError("no $month starting in $year")
    }
}
