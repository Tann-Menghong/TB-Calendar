package com.khmercalendar.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Countdown selection and phrasing.
 *
 * A countdown is only ever wrong by one, and being wrong by one is the entire failure mode:
 * "នៅ ១ ថ្ងៃ" for something that happens tonight, or "០ សប្តាហ៍" for six days. Every case
 * below is one of those.
 */
class CountdownsTest {

    private val today = LocalDate.of(2026, 9, 9)
    private val now = LocalDateTime.of(today, LocalTime.of(10, 0))

    private fun item(
        title: String,
        date: LocalDate,
        time: LocalTime = LocalTime.of(9, 0),
        allDay: Boolean = false,
        isHoliday: Boolean = false,
        isPinned: Boolean = true,
        eventId: Long = 1,
    ) = CountdownItem(
        eventId = eventId,
        title = title,
        date = date,
        at = LocalDateTime.of(date, time),
        allDay = allDay,
        isHoliday = isHoliday,
        isPinned = isPinned,
    )

    private fun holiday(title: String, date: LocalDate) =
        item(title, date, allDay = true, isHoliday = true, isPinned = false, eventId = 0)

    // --- selection ---

    @Test
    fun `pinned dates lead, nearest first`() {
        val built = Countdowns.build(
            pinned = listOf(
                item("exam", today.plusDays(30)),
                item("deadline", today.plusDays(2)),
                item("trip", today.plusDays(9)),
            ),
            fallback = listOf(holiday("bon", today.plusDays(1))),
            today = today,
        )
        assertEquals(listOf("deadline", "trip", "exam"), built.map { it.title })
    }

    @Test
    fun `the holiday fallback is used only while nothing is pinned`() {
        val fallback = listOf(holiday("bon", today.plusDays(20)))

        val withPin = Countdowns.build(listOf(item("exam", today.plusDays(30))), fallback, today)
        assertEquals(listOf("exam"), withPin.map { it.title })

        val without = Countdowns.build(emptyList(), fallback, today)
        assertEquals(listOf("bon"), without.map { it.title })
    }

    @Test
    fun `a countdown that has passed is not shown`() {
        val built = Countdowns.build(
            pinned = listOf(item("gone", today.minusDays(1)), item("soon", today.plusDays(3))),
            fallback = emptyList(),
            today = today,
        )
        assertEquals(listOf("soon"), built.map { it.title })
    }

    @Test
    fun `something happening today still counts`() {
        val built = Countdowns.build(listOf(item("now", today)), emptyList(), today)
        assertEquals(listOf("now"), built.map { it.title })
    }

    @Test
    fun `the card stops at its limit`() {
        val pinned = (1..8).map { item("e$it", today.plusDays(it.toLong())) }
        assertEquals(Countdowns.CARD_LIMIT, Countdowns.build(pinned, emptyList(), today).size)
    }

    @Test
    fun `nothing pinned and no holidays is empty rather than a placeholder`() {
        assertTrue(Countdowns.build(emptyList(), emptyList(), today).isEmpty())
    }

    // --- phrasing ---

    @Test
    fun `today and tomorrow are named in every style`() {
        for (style in CountdownStyle.entries) {
            assertEquals(style.name, "ថ្ងៃនេះ", Countdowns.remaining(item("x", today), now, style))
            assertEquals(
                style.name,
                "ថ្ងៃស្អែក",
                Countdowns.remaining(item("x", today.plusDays(1)), now, style),
            )
        }
    }

    @Test
    fun `days style counts whole days`() {
        assertEquals(
            "នៅ ៥ ថ្ងៃ",
            Countdowns.remaining(item("x", today.plusDays(5)), now, CountdownStyle.DAYS),
        )
    }

    @Test
    fun `days and hours uses the clock time of a timed entry`() {
        // 10:00 on the 9th to 18:00 on the 12th is three days and eight hours.
        val target = item("x", today.plusDays(3), time = LocalTime.of(18, 0))
        assertEquals(
            "នៅ ៣ ថ្ងៃ ៨ ម៉ោង",
            Countdowns.remaining(target, now, CountdownStyle.DAYS_HOURS),
        )
    }

    @Test
    fun `days and hours drops the hours when there are none`() {
        val target = item("x", today.plusDays(3), time = LocalTime.of(10, 0))
        assertEquals(
            "នៅ ៣ ថ្ងៃ",
            Countdowns.remaining(target, now, CountdownStyle.DAYS_HOURS),
        )
    }

    @Test
    fun `an all-day entry is never given an hour figure`() {
        val target = item("birthday", today.plusDays(4), allDay = true)
        assertEquals(
            "នៅ ៤ ថ្ងៃ",
            Countdowns.remaining(target, now, CountdownStyle.DAYS_HOURS),
        )
    }

    @Test
    fun `weeks style reports whole weeks and the remainder`() {
        assertEquals(
            "នៅ ២ សប្តាហ៍",
            Countdowns.remaining(item("x", today.plusDays(14)), now, CountdownStyle.WEEKS),
        )
        assertEquals(
            "នៅ ៦ សប្តាហ៍ ១ ថ្ងៃ",
            Countdowns.remaining(item("x", today.plusDays(43)), now, CountdownStyle.WEEKS),
        )
    }

    @Test
    fun `under a week the weeks style falls back to days`() {
        assertEquals(
            "នៅ ៦ ថ្ងៃ",
            Countdowns.remaining(item("x", today.plusDays(6)), now, CountdownStyle.WEEKS),
        )
    }

    @Test
    fun `a passed date says so rather than counting backwards`() {
        assertEquals(
            "ហួសកំណត់",
            Countdowns.remaining(item("x", today.minusDays(2)), now, CountdownStyle.DAYS),
        )
    }

    @Test
    fun `arabic numerals are honoured`() {
        assertEquals(
            "នៅ 5 ថ្ងៃ",
            Countdowns.remaining(
                item("x", today.plusDays(5)),
                now,
                CountdownStyle.DAYS,
                khmerNumerals = false,
            ),
        )
    }

    @Test
    fun `an unknown stored style falls back to days`() {
        assertEquals(CountdownStyle.DAYS, CountdownStyle.of(null))
        assertEquals(CountdownStyle.DAYS, CountdownStyle.of("ring"))
        assertEquals(CountdownStyle.WEEKS, CountdownStyle.of("weeks"))
    }
}
