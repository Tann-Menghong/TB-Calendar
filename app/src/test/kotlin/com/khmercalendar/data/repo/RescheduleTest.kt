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
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** Moving an occurrence to another day, and turning an event into a task and back. */
@RunWith(RobolectricTestRunner::class)
class RescheduleTest {

    private lateinit var db: KhmerCalendarDatabase
    private lateinit var repository: EventRepository

    private val zone: ZoneId = ZoneId.of("Asia/Phnom_Penh")
    private val monday = LocalDate.of(2026, 3, 2)

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
            checklistDao = db.checklistDao(),
            completionDao = db.taskCompletionDao(),
            zoneProvider = { zone },
        )
    }

    @After
    fun tearDown() = db.close()

    private suspend fun days(from: LocalDate, to: LocalDate) =
        repository.occurrences(from, to).filterValues { it.isNotEmpty() }

    @Test
    fun `a one-off moves as a whole and keeps its time`() = runTest {
        val id = repository.save(
            EventDraftModel(
                title = "ជួបគ្រូពេទ្យ",
                date = monday,
                startTime = LocalTime.of(14, 0),
                endTime = LocalTime.of(15, 30),
                endDate = monday,
            ),
        )

        val holder = repository.reschedule(id, monday, monday.plusDays(3))

        assertEquals(id, holder)
        val found = days(monday.minusDays(7), monday.plusDays(14))
        assertEquals(listOf(monday.plusDays(3)), found.keys.toList())
        val moved = found.getValue(monday.plusDays(3)).single()
        assertEquals(LocalTime.of(14, 0), moved.start.toLocalTime())
        assertEquals(LocalTime.of(15, 30), moved.end.toLocalTime())
        assertEquals(1, db.eventDao().count())
    }

    @Test
    fun `a one-off past midnight still ends the next morning after moving`() = runTest {
        val id = repository.save(
            EventDraftModel(
                title = "វេនយប់",
                date = monday,
                startTime = LocalTime.of(23, 0),
                endTime = LocalTime.of(1, 0),
                endDate = monday,
            ),
        )

        repository.reschedule(id, monday, monday.plusDays(1))

        val moved = db.eventDao().byId(id)!!
        assertEquals(2 * 3_600_000L, moved.endUtcMillis - moved.startUtcMillis)
        val start = repository.occurrences(monday, monday.plusDays(3)).values.flatten().first().start
        assertEquals(monday.plusDays(1).atTime(23, 0), start)
    }

    @Test
    fun `an all-day event of several days moves by whole days`() = runTest {
        val id = repository.save(
            EventDraftModel(title = "ដំណើរកម្សាន្ត", date = monday, endDate = monday.plusDays(2), allDay = true),
        )
        val before = db.eventDao().byId(id)!!

        repository.reschedule(id, monday, monday.plusDays(7))

        val after = db.eventDao().byId(id)!!
        assertEquals(7 * 86_400_000L, after.startUtcMillis - before.startUtcMillis)
        assertEquals(before.endUtcMillis - before.startUtcMillis, after.endUtcMillis - after.startUtcMillis)
    }

    @Test
    fun `one day of a series moves alone, as a copy with the series reminders`() = runTest {
        val series = repository.save(
            EventDraftModel(
                title = "ប្រជុំប្រចាំសប្តាហ៍",
                date = monday,
                startTime = LocalTime.of(8, 0),
                endTime = LocalTime.of(9, 0),
                endDate = monday,
                recurrence = RecurrenceRule(frequency = Frequency.WEEKLY, interval = 1),
                reminderMinutes = listOf(60, 10),
            ),
        )
        val secondMonday = monday.plusWeeks(1)
        val wednesday = secondMonday.plusDays(2)

        val copy = repository.reschedule(series, secondMonday, wednesday)!!

        assertNotEquals(series, copy)
        val found = days(monday, monday.plusWeeks(3))
        assertEquals(listOf(monday, wednesday, monday.plusWeeks(2), monday.plusWeeks(3)), found.keys.sorted())
        val moved = found.getValue(wednesday).single()
        assertEquals(copy, moved.eventId)
        assertFalse(moved.isRecurring)
        assertEquals(LocalTime.of(8, 0), moved.start.toLocalTime())
        assertEquals(LocalTime.of(9, 0), moved.end.toLocalTime())
        assertEquals(series, found.getValue(monday.plusWeeks(2)).single().eventId)
        assertEquals(listOf(10, 60), repository.reminders(copy).sorted())
    }

    @Test
    fun `a ticked day of a repeating task starts undone once moved`() = runTest {
        val task = repository.save(
            EventDraftModel(
                title = "ហាត់ប្រាណ",
                date = monday,
                allDay = true,
                isTask = true,
                recurrence = RecurrenceRule(frequency = Frequency.DAILY, interval = 1),
            ),
        )
        repository.setCompleted(task, monday.plusDays(1), true)

        val copy = repository.reschedule(task, monday.plusDays(1), monday.plusDays(10))!!

        assertFalse(repository.isCompleted(copy, monday.plusDays(10)))
        assertFalse(db.eventDao().byId(copy)!!.isPinned)
    }

    @Test
    fun `moving to the same day or moving nothing changes nothing`() = runTest {
        val id = repository.save(EventDraftModel(title = "x", date = monday))

        assertEquals(id, repository.reschedule(id, monday, monday))
        assertNull(repository.reschedule(9_999, monday, monday.plusDays(1)))
        assertEquals(1, db.eventDao().count())
        assertEquals(listOf(monday), days(monday.minusDays(3), monday.plusDays(3)).keys.toList())
    }

    @Test
    fun `a finished task turned into an event is not done, and turning it back restores the tick`() = runTest {
        val id = repository.save(EventDraftModel(title = "ដាក់ពាក្យ", date = monday, isTask = true))
        repository.setCompleted(id, monday, true)

        repository.setIsTask(id, false)
        val asEvent = days(monday, monday).getValue(monday).single()
        assertFalse(asEvent.isTask)
        assertFalse(asEvent.isCompleted)
        assertFalse(repository.isCompleted(id, monday))

        repository.setIsTask(id, true)
        assertTrue(days(monday, monday).getValue(monday).single().isCompleted)
        assertTrue(repository.isCompleted(id, monday))
    }

    @Test
    fun `a repeating task keeps its ticks while it is an event`() = runTest {
        val id = repository.save(
            EventDraftModel(
                title = "អានសៀវភៅ",
                date = monday,
                allDay = true,
                isTask = true,
                recurrence = RecurrenceRule(frequency = Frequency.DAILY, interval = 1),
            ),
        )
        repository.setCompleted(id, monday.plusDays(2), true)

        repository.setIsTask(id, false)
        assertFalse(days(monday, monday.plusDays(4)).values.flatten().any { it.isCompleted })
        assertEquals(1, db.taskCompletionDao().all().size)

        repository.setIsTask(id, true)
        val ticked = days(monday, monday.plusDays(4)).values.flatten().filter { it.isCompleted }
        assertEquals(listOf(monday.plusDays(2)), ticked.map { it.occurrenceDate })
    }

    @Test
    fun `a task turned into an event leaves the statistics until it is a task again`() = runTest {
        // Ticks are stamped with the real clock, so the window is around today.
        val today = LocalDate.now()
        val oneOff = repository.save(EventDraftModel(title = "ក", date = today, isTask = true))
        val daily = repository.save(
            EventDraftModel(
                title = "ខ",
                date = today.minusDays(3),
                allDay = true,
                isTask = true,
                recurrence = RecurrenceRule(frequency = Frequency.DAILY, interval = 1),
            ),
        )
        repository.setCompleted(oneOff, today, true)
        repository.setCompleted(daily, today.minusDays(1), true)

        suspend fun counted() =
            repository.observeCompletedTasks(today.minusDays(1), today.plusDays(1)).first().map { it.eventId }.sorted()

        assertEquals(listOf(oneOff, daily).sorted(), counted())

        repository.setIsTask(oneOff, false)
        repository.setIsTask(daily, false)
        assertTrue(counted().isEmpty())

        repository.setIsTask(oneOff, true)
        repository.setIsTask(daily, true)
        assertEquals(listOf(oneOff, daily).sorted(), counted())
    }
}
