package com.khmercalendar.data.backup

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.khmercalendar.data.db.KhmerCalendarDatabase
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Reading .ics files, which is where a calendar most easily corrupts someone's data.
 *
 * The reader is exercised against a real in-memory Room database rather than a fake, because
 * half of what it has to get right — deduplication, reminder rows — only exists once a row
 * has actually been written.
 */
@RunWith(RobolectricTestRunner::class)
class IcsFormatTest {

    private lateinit var db: KhmerCalendarDatabase
    private val phnomPenh: ZoneId = ZoneId.of("Asia/Phnom_Penh")
    private val now = 1_757_000_000_000L

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            KhmerCalendarDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() = db.close()

    private fun ics(vararg body: String) = buildString {
        appendLine("BEGIN:VCALENDAR")
        appendLine("VERSION:2.0")
        body.forEach { appendLine(it) }
        appendLine("END:VCALENDAR")
    }

    private suspend fun read(text: String) =
        IcsFormat.parseInto(db, IcsFormat.unfold(text), phnomPenh, now)

    private fun localStartOf(id: Long = 1): LocalDateTime {
        val row = kotlinx.coroutines.runBlocking { db.eventDao().allEvents() }
            .first { it.id == id }
        return Instant.ofEpochMilli(row.startUtcMillis).atZone(phnomPenh).toLocalDateTime()
    }

    // --- time zones --------------------------------------------------------------------

    @Test
    fun `a UTC timestamp is converted to local time`() = runTest {
        read(
            ics(
                "BEGIN:VEVENT",
                "SUMMARY:UTC meeting",
                "DTSTART:20260908T020000Z",
                "DTEND:20260908T030000Z",
                "END:VEVENT",
            )
        )

        // Phnom Penh is UTC+7 all year.
        assertEquals(LocalDateTime.of(2026, 9, 8, 9, 0), localStartOf())
    }

    @Test
    fun `a TZID datetime is converted, not read as though it were already local`() = runTest {
        // The defect this pins: the TZID parameter was parsed off and thrown away, so a 09:00
        // New York meeting arrived as 09:00 in Phnom Penh - eleven hours wrong.
        read(
            ics(
                "BEGIN:VEVENT",
                "SUMMARY:New York meeting",
                "DTSTART;TZID=America/New_York:20260908T090000",
                "DTEND;TZID=America/New_York:20260908T100000",
                "END:VEVENT",
            )
        )

        // 09:00 EDT is 13:00 UTC is 20:00 in Phnom Penh.
        assertEquals(LocalDateTime.of(2026, 9, 8, 20, 0), localStartOf())
    }

    @Test
    fun `a floating datetime stays local`() = runTest {
        // RFC 5545 defines a bare value with no TZID as local wherever the file is opened.
        read(
            ics(
                "BEGIN:VEVENT",
                "SUMMARY:Floating",
                "DTSTART:20260908T090000",
                "DTEND:20260908T100000",
                "END:VEVENT",
            )
        )

        assertEquals(LocalDateTime.of(2026, 9, 8, 9, 0), localStartOf())
    }

    @Test
    fun `an unknown zone falls back to floating rather than failing the import`() = runTest {
        val result = read(
            ics(
                "BEGIN:VEVENT",
                "SUMMARY:Odd zone",
                "DTSTART;TZID=Mars/Olympus:20260908T090000",
                "END:VEVENT",
            )
        )

        assertEquals(1, result.imported)
        assertEquals(LocalDateTime.of(2026, 9, 8, 9, 0), localStartOf())
    }

    @Test
    fun `an all-day event keeps its date whatever order its parameters come in`() = runTest {
        read(
            ics(
                "BEGIN:VEVENT",
                "SUMMARY:Khmer New Year",
                "DTSTART;TZID=Asia/Phnom_Penh;VALUE=DATE:20260414",
                "DTEND;VALUE=DATE:20260415",
                "END:VEVENT",
            )
        )

        val row = db.eventDao().allEvents().single()
        assertTrue("VALUE=DATE after another parameter must still read as all-day", row.allDay)
        assertEquals(
            java.time.LocalDate.of(2026, 4, 14),
            Instant.ofEpochMilli(row.startUtcMillis).atZone(ZoneOffset.UTC).toLocalDate(),
        )
    }

    // --- reminders ---------------------------------------------------------------------

    @Test
    fun `the plain trigger this app writes is read back`() {
        assertEquals(listOf(30), IcsFormat.triggerMinutes("TRIGGER:-PT30M"))
    }

    @Test
    fun `a trigger with a parameter before the colon is not dropped`() {
        // Very common in the wild, and previously invisible to the reader.
        assertEquals(listOf(15), IcsFormat.triggerMinutes("TRIGGER;VALUE=DURATION:-PT15M"))
    }

    @Test
    fun `a day-length trigger is understood`() {
        // What Google Calendar writes for a day-before reminder on an all-day event.
        assertEquals(listOf(1440), IcsFormat.triggerMinutes("TRIGGER:-P1D"))
    }

    @Test
    fun `hours and minutes combine`() {
        assertEquals(listOf(90), IcsFormat.triggerMinutes("TRIGGER:-PT1H30M"))
    }

    @Test
    fun `a reminder at the moment of the event survives`() {
        // "ពេលចាប់ផ្តើម" exports as -PT0M and used to be discarded for not being positive,
        // so it was lost on a round trip through this app's own file.
        assertEquals(listOf(0), IcsFormat.triggerMinutes("TRIGGER:-PT0M"))
    }

    @Test
    fun `several alarms on one event all come through`() {
        val body = "TRIGGER:-PT10M\nTRIGGER;VALUE=DURATION:-P1D\nTRIGGER:-PT1H"

        assertEquals(listOf(10, 1440, 60), IcsFormat.triggerMinutes(body))
    }

    @Test
    fun `reminder rows are written for the imported event`() = runTest {
        read(
            ics(
                "BEGIN:VEVENT",
                "SUMMARY:With alarm",
                "DTSTART:20260908T020000Z",
                "BEGIN:VALARM",
                "ACTION:DISPLAY",
                "TRIGGER;VALUE=DURATION:-P1D",
                "END:VALARM",
                "END:VEVENT",
            )
        )

        val id = db.eventDao().allEvents().single().id
        assertEquals(listOf(1440), db.reminderDao().forEvent(id).map { it.minutesBefore })
    }

    // --- duplicates --------------------------------------------------------------------

    @Test
    fun `importing the same file twice does not double the calendar`() = runTest {
        val file = ics(
            "BEGIN:VEVENT",
            "SUMMARY:ប្រជុំក្រុម",
            "DTSTART:20260908T020000Z",
            "DTEND:20260908T030000Z",
            "END:VEVENT",
        )

        assertEquals(IcsFormat.ImportResult(imported = 1, skipped = 0), read(file))
        assertEquals(IcsFormat.ImportResult(imported = 0, skipped = 1), read(file))
        assertEquals(1, db.eventDao().allEvents().size)
    }

    @Test
    fun `a duplicate inside one file is caught too`() = runTest {
        val result = read(
            ics(
                "BEGIN:VEVENT",
                "SUMMARY:Standup",
                "DTSTART:20260908T020000Z",
                "END:VEVENT",
                "BEGIN:VEVENT",
                "SUMMARY:Standup",
                "DTSTART:20260908T020000Z",
                "END:VEVENT",
            )
        )

        assertEquals(IcsFormat.ImportResult(imported = 1, skipped = 1), result)
    }

    @Test
    fun `two different events at the same time are both kept`() = runTest {
        val result = read(
            ics(
                "BEGIN:VEVENT",
                "SUMMARY:Standup",
                "DTSTART:20260908T020000Z",
                "END:VEVENT",
                "BEGIN:VEVENT",
                "SUMMARY:Dentist",
                "DTSTART:20260908T020000Z",
                "END:VEVENT",
            )
        )

        assertEquals(2, result.imported)
    }

    // --- line folding ------------------------------------------------------------------

    @Test
    fun `a short line is left alone`() {
        assertEquals("SUMMARY:Standup", IcsFormat.fold("SUMMARY:Standup"))
    }

    @Test
    fun `a long Khmer line is folded without splitting a character`() {
        // Khmer is three bytes per character in UTF-8, so this overruns the 75-octet limit
        // after twenty-five characters. Folding by character count would have cut inside a
        // multi-byte sequence and corrupted the text it exists to preserve.
        val text = "DESCRIPTION:" + "ប្រជុំ".repeat(30)
        val folded = IcsFormat.fold(text)

        assertTrue("must actually fold", folded.contains("\r\n "))
        folded.split("\r\n").forEach { line ->
            assertTrue("line of ${line.toByteArray().size} octets", line.toByteArray().size <= 75)
        }
        // Unfolding is the inverse: nothing added, nothing lost.
        assertEquals(text, IcsFormat.unfold(folded).replace("\n", ""))
    }

    @Test
    fun `folding and unfolding round-trips ASCII too`() {
        val text = "DESCRIPTION:" + "the quarterly planning meeting ".repeat(6)

        assertEquals(text, IcsFormat.unfold(IcsFormat.fold(text)).replace("\n", ""))
    }
}
