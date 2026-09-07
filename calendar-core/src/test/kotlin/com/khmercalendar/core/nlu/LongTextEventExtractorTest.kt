package com.khmercalendar.core.nlu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Pasted text to one proposed event.
 *
 * Two properties matter more than the rest and both are asserted repeatedly: the title must
 * be a name rather than the whole paragraph, and nothing may be invented. A guessed time is
 * worse than a visibly incomplete proposal, because the incomplete one gets corrected and the
 * guess gets accepted.
 */
class LongTextEventExtractorTest {

    private val extractor = LongTextEventExtractor()

    /** A Monday, 09:00. */
    private val now: LocalDateTime = LocalDateTime.of(2026, 3, 9, 9, 0)

    @Test
    fun `the pasted team announcement becomes a concise event`() {
        val text = "សួស្តីក្រុមការងារ នៅថ្ងៃស្អែកយើងនឹងមានកិច្ចប្រជុំប្រចាំសប្តាហ៍នៅម៉ោង ២ រសៀល " +
            "នៅបន្ទប់ប្រជុំ A ដើម្បីពិភាក្សាអំពីផែនការការងារប្រចាំខែ " +
            "និងបែងចែកភារកិច្ចសម្រាប់សប្តាហ៍ក្រោយ។ សូមអ្នកទាំងអស់គ្នាត្រៀមរបាយការណ៍របស់ខ្លួនមកជាមួយ។"

        val proposal = extractor.extract(text, now)
        assertNotNull(proposal)
        proposal!!

        // The headline assertion: a name, not the paragraph.
        assertTrue(
            "Title should be short, was ${proposal.title.length} chars: ${proposal.title}",
            proposal.title.length <= 60,
        )
        assertTrue(
            "Title should name the meeting, was: ${proposal.title}",
            proposal.title.contains("ប្រជុំ"),
        )
        assertFalse(
            "Title must not swallow the greeting",
            proposal.title.contains("សួស្តី"),
        )
        assertFalse(
            "Title must not swallow the closing request",
            proposal.title.contains("របាយការណ៍"),
        )

        // Exactly the name of the meeting: no greeting in front, no "នៅម៉ោង ២ រសៀល" behind.
        assertEquals("កិច្ចប្រជុំប្រចាំសប្តាហ៍", proposal.title)

        assertEquals(LocalDate.of(2026, 3, 10), proposal.date)
        assertEquals(LocalTime.of(14, 0), proposal.startTime)

        // "នៅ" appears three times in this sentence and only the third introduces a place.
        assertEquals("បន្ទប់ប្រជុំ A", proposal.location)

        assertNotNull("The rest of the message is kept as context", proposal.description)
        assertTrue(
            "The description should say what the meeting is for: ${proposal.description}",
            proposal.description!!.contains("ពិភាក្សា"),
        )
        assertTrue(
            "...and carry the closing request too: ${proposal.description}",
            proposal.description!!.contains("របាយការណ៍"),
        )
        assertTrue(proposal.confidence > 0.6f)
    }

    @Test
    fun `a time that is not stated is left empty rather than invented`() {
        val proposal = extractor.extract("ប្រជុំជាមួយក្រុមការងារថ្ងៃស្អែក", now)
        assertNotNull(proposal)
        proposal!!

        assertEquals(LocalDate.of(2026, 3, 10), proposal.date)
        assertNull("No time was written, so none may be proposed", proposal.startTime)
        assertNull(proposal.endTime)
        assertTrue(
            "The missing time must be reported to the user",
            ProposalField.START_TIME in proposal.missing,
        )
        assertTrue(proposal.title.contains("ប្រជុំ"))
        assertTrue(proposal.allDay)
    }

    @Test
    fun `a location that is not stated is left empty and reported missing`() {
        val proposal = extractor.extract("ថ្ងៃស្អែកម៉ោង ២ រសៀល មានប្រជុំ", now)!!
        assertNull(proposal.location)
        assertTrue(ProposalField.LOCATION in proposal.missing)
    }

    @Test
    fun `a preposition introducing a time or a date is not read as a place`() {
        // "នៅ" here only ever introduces a date and a time; there is no room in this sentence.
        val timeOnly = extractor.extract("នៅថ្ងៃស្អែកយើងនឹងមានប្រជុំនៅម៉ោង ២ រសៀល", now)!!
        assertNull("A date is not a place: ${timeOnly.location}", timeOnly.location)
        assertTrue(ProposalField.LOCATION in timeOnly.missing)

        // ...and when a real place follows later in the same sentence, it is still found.
        val withRoom = extractor.extract(
            "នៅថ្ងៃស្អែកយើងនឹងមានប្រជុំនៅម៉ោង ២ រសៀល នៅសាលប្រជុំធំ",
            now,
        )!!
        assertEquals("សាលប្រជុំធំ", withRoom.location)
    }

    @Test
    fun `text handed back to the user keeps the digits they typed`() {
        // Matching needs ASCII digits internally. What comes back out must not: a room named
        // in Khmer numerals has to read as the user wrote it, in a calendar that is otherwise
        // entirely in Khmer numerals.
        val proposal = extractor.extract(
            "ថ្ងៃស្អែកម៉ោង ៩ ព្រឹក វគ្គបណ្តុះបណ្តាល នៅសាលប្រជុំធំជាន់ទី ៣",
            now,
        )!!
        assertEquals("សាលប្រជុំធំជាន់ទី ៣", proposal.location)
        assertEquals(LocalTime.of(9, 0), proposal.startTime)
    }

    @Test
    fun `explicit and inferred values are distinguished`() {
        val proposal = extractor.extract(
            "ថ្ងៃស្អែកម៉ោង ២ រសៀល កិច្ចប្រជុំនៅបន្ទប់ប្រជុំ A",
            now,
        )!!

        // The time and the place were written down.
        assertEquals(FactSource.EXPLICIT, proposal.factFor(ProposalField.START_TIME)?.source)
        assertEquals(FactSource.EXPLICIT, proposal.factFor(ProposalField.LOCATION)?.source)
        // "ស្អែក" is explicit as a word, but the calendar date it lands on is worked out.
        assertEquals(FactSource.INFERRED, proposal.factFor(ProposalField.DATE)?.source)
        // No end time was given, so the one shown is derived from the default duration.
        assertEquals(FactSource.INFERRED, proposal.factFor(ProposalField.END_TIME)?.source)
    }

    @Test
    fun `an absolute date is marked explicit`() {
        val proposal = extractor.extract("ថ្ងៃទី ១៥ ខែមេសា ម៉ោង ៣ រសៀល ជួបអតិថិជន", now)!!
        assertEquals(FactSource.EXPLICIT, proposal.factFor(ProposalField.DATE)?.source)
        assertEquals(4, proposal.date?.monthValue)
        assertEquals(15, proposal.date?.dayOfMonth)
        assertEquals(LocalTime.of(15, 0), proposal.startTime)
    }

    @Test
    fun `participants are taken only when explicitly marked`() {
        val withPeople = extractor.extract("ថ្ងៃស្អែកម៉ោង ២ រសៀល ប្រជុំជាមួយក្រុមការងារ", now)!!
        assertTrue(
            "Participants: ${withPeople.participants}",
            withPeople.participants.any { it.contains("ក្រុមការងារ") },
        )

        val withoutPeople = extractor.extract("ថ្ងៃស្អែកម៉ោង ២ រសៀល កិច្ចប្រជុំ", now)!!
        assertTrue(
            "No one was named, so no one may be listed",
            withoutPeople.participants.isEmpty(),
        )
    }

    @Test
    fun `text announcing nothing datable returns null`() {
        assertNull(extractor.extract("សួស្តី សុខសប្បាយជាទេ", now))
        assertNull(extractor.extract("អរគុណច្រើន", now))
        assertNull(extractor.extract("   ", now))
        assertNull(extractor.extract("", now))
    }

    @Test
    fun `the announcing sentence is found inside a long message`() {
        val text = buildString {
            append("សួស្តីបងប្អូនទាំងអស់គ្នា។ ")
            repeat(12) {
                append("ខ្ញុំសង្ឃឹមថាអ្នកទាំងអស់គ្នាមានសុខភាពល្អ និងកំពុងធ្វើការដោយរីករាយ។ ")
            }
            append("នៅថ្ងៃស្អែកម៉ោង ១០ ព្រឹក យើងនឹងមានកិច្ចប្រជុំនៅបន្ទប់ប្រជុំ B។ ")
            repeat(12) { append("សូមអរគុណសម្រាប់ការគាំទ្ររបស់អ្នកទាំងអស់គ្នា។ ") }
        }

        val proposal = extractor.extract(text, now)
        assertNotNull("Should find the announcement in a long message", proposal)
        proposal!!
        assertEquals(LocalDate.of(2026, 3, 10), proposal.date)
        assertEquals(LocalTime.of(10, 0), proposal.startTime)
        assertTrue(proposal.title.length <= 60)
        assertTrue(proposal.title.contains("ប្រជុំ"))
    }

    @Test
    fun `very long input is truncated rather than choking`() {
        val huge = "ការងារធម្មតា។ ".repeat(6_000) +
            "ថ្ងៃស្អែកម៉ោង ២ រសៀល ប្រជុំ។"
        assertTrue(huge.length > LongTextEventExtractor.MAX_INPUT)

        // The point is that it returns promptly and does not throw; the announcement is past
        // the cap here, so finding nothing is the correct answer.
        val started = System.currentTimeMillis()
        extractor.extract(huge, now)
        val elapsed = System.currentTimeMillis() - started
        assertTrue("Took ${elapsed}ms on oversized input", elapsed < 3_000)
    }

    @Test
    fun `a short command still works, and keeps its short title`() {
        val proposal = extractor.extract("ស្អែករំលឹកខ្ញុំទិញសៀវភៅ", now)
        // No event noun and no clock time, but "ស្អែក" plus a reminder word is enough signal.
        assertNotNull(proposal)
        proposal!!
        assertEquals(LocalDate.of(2026, 3, 10), proposal.date)
        assertTrue("Title was: ${proposal.title}", proposal.title.contains("សៀវភៅ"))
        assertTrue(proposal.title.length <= 60)
    }

    @Test
    fun `next Monday at eight in the morning`() {
        val proposal = extractor.extract("ថ្ងៃចន្ទក្រោយ ម៉ោង ៨ ព្រឹក មានប្រជុំ", now)!!
        assertEquals(LocalTime.of(8, 0), proposal.startTime)
        assertEquals(java.time.DayOfWeek.MONDAY, proposal.date?.dayOfWeek)
        assertTrue("Must be a future Monday", proposal.date!!.isAfter(now.toLocalDate()))
    }

    @Test
    fun `a proposal is only actionable with both a title and a date`() {
        val good = extractor.extract("ថ្ងៃស្អែកម៉ោង ២ រសៀល ប្រជុំ", now)!!
        assertTrue(good.isActionable)
        assertTrue(good.title.isNotBlank())
    }

    @Test
    fun `the description carries context without repeating the event line`() {
        val text = "នៅថ្ងៃស្អែកម៉ោង ២ រសៀល មានកិច្ចប្រជុំ។ " +
            "សូមត្រៀមរបាយការណ៍ប្រចាំខែរបស់អ្នកមកជាមួយផង។"
        val proposal = extractor.extract(text, now)!!

        assertNotNull(proposal.description)
        assertTrue(
            "Description should carry the second sentence: ${proposal.description}",
            proposal.description!!.contains("របាយការណ៍"),
        )
    }
}
