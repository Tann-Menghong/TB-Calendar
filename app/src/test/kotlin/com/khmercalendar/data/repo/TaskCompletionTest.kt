package com.khmercalendar.data.repo

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.khmercalendar.core.recurrence.RecurrenceRule
import com.khmercalendar.data.db.KhmerCalendarDatabase
import com.khmercalendar.domain.EventDraftModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

/**
 * Completing tasks, one occurrence at a time, and keeping completion times honest.
 *
 * Before 2.5.0 a repeating series carried a single flag, so ticking one Monday ticked every
 * Monday; editing a finished task re-stamped it as finished today; and a duplicate of a
 * finished task was born finished.
 */
@RunWith(RobolectricTestRunner::class)
class TaskCompletionTest {

    private lateinit var db: KhmerCalendarDatabase
    private lateinit var repository: EventRepository
    private val today = LocalDate.now()

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
        )
    }

    @After
    fun tearDown() = db.close()

    private suspend fun dailyTask(from: LocalDate = today): Long = repository.save(
        EventDraftModel(
            title = "ស្រោចទឹកផ្កា",
            date = from,
            endDate = from,
            allDay = true,
            isTask = true,
            recurrence = RecurrenceRule.parse("FREQ=DAILY"),
            reminderMinutes = emptyList(),
        ),
    )

    private suspend fun oneOffTask(): Long = repository.save(
        EventDraftModel(
            title = "ដាក់ពាក្យ",
            date = today,
            endDate = today,
            allDay = true,
            isTask = true,
            reminderMinutes = emptyList(),
        ),
    )

    private suspend fun doneOn(id: Long, from: LocalDate, to: LocalDate): Map<LocalDate, Boolean> =
        repository.occurrences(from, to).mapValues { (_, list) -> list.single { it.eventId == id }.isCompleted }

    @Test
    fun `ticking one day of a repeating task ticks that day only`() = runTest {
        val id = dailyTask()

        repository.setCompleted(id, today.plusDays(1), true)

        val done = doneOn(id, today, today.plusDays(2))
        assertEquals(false, done[today])
        assertEquals(true, done[today.plusDays(1)])
        assertEquals(false, done[today.plusDays(2)])
        // The series itself is untouched.
        assertFalse(db.eventDao().byId(id)!!.isCompleted)
    }

    @Test
    fun `unticking a day brings it back`() = runTest {
        val id = dailyTask()
        repository.setCompleted(id, today, true)

        repository.setCompleted(id, today, false)

        assertEquals(false, doneOn(id, today, today)[today])
        assertFalse(repository.isCompleted(id, today))
    }

    @Test
    fun `a second tick keeps the first completion time`() = runTest {
        val id = dailyTask()
        repository.setCompleted(id, today, true)
        val first = db.taskCompletionDao().find(id, today.toEpochDay())!!.completedAtMillis

        Thread.sleep(5)
        repository.setCompleted(id, today, true)

        assertEquals(first, db.taskCompletionDao().find(id, today.toEpochDay())!!.completedAtMillis)
    }

    @Test
    fun `a one-off task ticked twice keeps its completion time`() = runTest {
        val id = oneOffTask()
        repository.setCompleted(id, today, true)
        val first = db.eventDao().byId(id)!!.completedAtMillis
        assertNotNull(first)

        Thread.sleep(5)
        repository.setCompleted(id, today, true)

        assertEquals(first, db.eventDao().byId(id)!!.completedAtMillis)
        // And a one-off never writes a per-occurrence row.
        assertTrue(db.taskCompletionDao().all().isEmpty())
    }

    @Test
    fun `editing a finished task does not re-stamp when it was finished`() = runTest {
        val id = oneOffTask()
        repository.setCompleted(id, today, true)
        val finishedAt = db.eventDao().byId(id)!!.completedAtMillis

        Thread.sleep(5)
        repository.save(
            EventDraftModel(
                id = id,
                title = "ដាក់ពាក្យសុំអាហារូបករណ៍",
                date = today,
                endDate = today,
                allDay = true,
                isTask = true,
                isCompleted = true,
                reminderMinutes = emptyList(),
            ),
        )

        assertEquals(finishedAt, db.eventDao().byId(id)!!.completedAtMillis)
    }

    @Test
    fun `a duplicate of a finished pinned task starts undone and unpinned`() = runTest {
        val id = oneOffTask()
        repository.setCompleted(id, today, true)
        repository.setPinned(id, true)

        val copy = db.eventDao().byId(repository.duplicate(id)!!)!!

        assertFalse(copy.isCompleted)
        assertNull(copy.completedAtMillis)
        assertFalse(copy.isPinned)
    }

    @Test
    fun `statistics count every ticked occurrence and every one-off`() = runTest {
        val daily = dailyTask(today.minusDays(2))
        repository.setCompleted(daily, today.minusDays(2), true)
        repository.setCompleted(daily, today.minusDays(1), true)
        repository.setCompleted(daily, today, true)
        repository.setCompleted(oneOffTask(), today, true)

        // All four were ticked today, which is the day completions are counted on.
        val completed = repository.observeCompletedTasks(today, today).first()

        assertEquals(4, completed.size)
        assertEquals(3, completed.count { it.eventId == daily })
    }

    @Test
    fun `deleting a repeating task takes its ticks with it`() = runTest {
        val id = dailyTask()
        repository.setCompleted(id, today, true)

        repository.deleteEvent(id)

        assertTrue(db.taskCompletionDao().all().isEmpty())
    }

    @Test
    fun `unticking a day of a series left fully done by the old behaviour clears that flag`() = runTest {
        val id = dailyTask()
        // What 2.4.0 wrote when one occurrence was ticked.
        db.eventDao().setCompleted(id, true, 1L, 1L)
        assertEquals(true, doneOn(id, today, today)[today])

        repository.setCompleted(id, today, false)

        assertFalse(db.eventDao().byId(id)!!.isCompleted)
        assertEquals(false, doneOn(id, today, today.plusDays(1))[today.plusDays(1)])
    }
}
