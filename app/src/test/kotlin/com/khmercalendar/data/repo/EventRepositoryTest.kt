package com.khmercalendar.data.repo

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.khmercalendar.core.recurrence.Frequency
import com.khmercalendar.core.recurrence.RecurrenceRule
import com.khmercalendar.data.db.KhmerCalendarDatabase
import com.khmercalendar.domain.EventDraftModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Occurrence expansion, which is where the calendar is most likely to be wrong.
 *
 * These run against a real in-memory Room database rather than a fake DAO: the window query
 * that decides which rows are even candidates for expansion is half the logic, and a fake
 * would not exercise it.
 */
@RunWith(RobolectricTestRunner::class)
class EventRepositoryTest {

    private lateinit var db: KhmerCalendarDatabase
    private lateinit var repository: EventRepository

    private val zone: ZoneId = ZoneId.of("Asia/Phnom_Penh")

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            KhmerCalendarDatabase::class.java,
        ).allowMainThreadQueries().build()
        repository = EventRepository(
            eventDao = db.eventDao(),
            categoryDao = db.categoryDao(),
            reminderDao = db.reminderDao(),
            exceptionDao = db.eventExceptionDao(),
            noteDao = db.dayNoteDao(),
            zoneProvider = { zone },
        )
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `single event appears on its own day only`() = runTest {
        val date = LocalDate.of(2026, 3, 10)
        repository.save(
            EventDraftModel(
                title = "ប្រជុំការងារ",
                date = date,
                startTime = LocalTime.of(8, 0),
                endTime = LocalTime.of(9, 0),
                endDate = date,
                reminderMinutes = listOf(30),
            ),
        )

        val days = repository.occurrences(date.minusDays(2), date.plusDays(2))

        assertEquals(listOf(date), days.keys.toList())
        assertEquals("ប្រជុំការងារ", days.getValue(date).single().title)
    }

    @Test
    fun `weekly series expands across the window`() = runTest {
        val start = LocalDate.of(2026, 3, 2) // A Monday.
        repository.save(
            EventDraftModel(
                title = "ប្រជុំប្រចាំសប្តាហ៍",
                date = start,
                startTime = LocalTime.of(8, 0),
                endTime = LocalTime.of(9, 0),
                endDate = start,
                recurrence = RecurrenceRule(frequency = Frequency.WEEKLY, interval = 1),
                reminderMinutes = emptyList(),
            ),
        )

        val days = repository.occurrences(start, start.plusWeeks(4))

        assertEquals(
            listOf(
                start,
                start.plusWeeks(1),
                start.plusWeeks(2),
                start.plusWeeks(3),
                start.plusWeeks(4),
            ),
            days.keys.sorted(),
        )
    }

    @Test
    fun `deleting one occurrence leaves the rest of the series`() = runTest {
        val start = LocalDate.of(2026, 3, 2)
        val id = repository.save(
            EventDraftModel(
                title = "ហាត់ប្រាណ",
                date = start,
                startTime = LocalTime.of(6, 0),
                endTime = LocalTime.of(7, 0),
                endDate = start,
                recurrence = RecurrenceRule(frequency = Frequency.DAILY, interval = 1),
                reminderMinutes = emptyList(),
            ),
        )
        val skipped = start.plusDays(2)

        repository.deleteOccurrence(id, skipped)
        val days = repository.occurrences(start, start.plusDays(4))

        assertFalse(skipped in days)
        assertEquals(4, days.size)
    }

    @Test
    fun `all-day event survives a timezone the device is not in`() = runTest {
        val date = LocalDate.of(2026, 4, 14) // Khmer New Year.
        repository.save(
            EventDraftModel(
                title = "ចូលឆ្នាំ",
                date = date,
                endDate = date,
                allDay = true,
                reminderMinutes = emptyList(),
            ),
        )

        // Re-read through a repository pinned to a different zone: an all-day event is a
        // date, not an instant, so it must not slide a day.
        val elsewhere = EventRepository(
            eventDao = db.eventDao(),
            categoryDao = db.categoryDao(),
            reminderDao = db.reminderDao(),
            exceptionDao = db.eventExceptionDao(),
            noteDao = db.dayNoteDao(),
            zoneProvider = { ZoneId.of("America/Los_Angeles") },
        )
        val days = elsewhere.occurrences(date.minusDays(1), date.plusDays(1))

        assertTrue(date in days)
        assertTrue(days.getValue(date).single().allDay)
    }

    @Test
    fun `search matches title and location`() = runTest {
        val date = LocalDate.of(2026, 5, 1)
        repository.save(
            EventDraftModel(
                title = "ជួបអតិថិជន",
                location = "ភ្នំពេញ",
                date = date,
                reminderMinutes = emptyList(),
            ),
        )

        assertEquals(1, repository.search("អតិថិជន").first().size)
        assertEquals(1, repository.search("ភ្នំពេញ").first().size)
        assertEquals(0, repository.search("សៀមរាប").first().size)
    }
}
