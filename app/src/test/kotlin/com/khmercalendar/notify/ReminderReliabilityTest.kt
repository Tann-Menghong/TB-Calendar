package com.khmercalendar.notify

import android.app.AlarmManager
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.khmercalendar.core.recurrence.RecurrenceRule
import com.khmercalendar.data.db.EventEntity
import com.khmercalendar.data.db.KhmerCalendarDatabase
import com.khmercalendar.data.prefs.SettingsStore
import com.khmercalendar.data.repo.EventRepository
import com.khmercalendar.domain.EventDraftModel
import com.khmercalendar.domain.EventTimes
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * What is actually armed after the calendar changes.
 *
 * Counting the alarms the platform holds, not the ones the scheduler believes it holds: every
 * failure here is an alarm that outlives the event it was for, and that is only visible from
 * the AlarmManager's side.
 *
 * The first four tests failed against 2.4.0 exactly as their messages say - two alarms for a
 * moved event, one left behind by a deleted event, two for an event past midnight, and one
 * still armed with notifications off.
 */
@RunWith(RobolectricTestRunner::class)
class ReminderReliabilityTest {

    private lateinit var context: Context
    private lateinit var db: KhmerCalendarDatabase

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, KhmerCalendarDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        if (db.isOpen) db.close()
    }

    private fun repository() = EventRepository(
        eventDao = db.eventDao(),
        categoryDao = db.categoryDao(),
        reminderDao = db.reminderDao(),
        exceptionDao = db.eventExceptionDao(),
        noteDao = db.dayNoteDao(),
        checklistDao = db.checklistDao(),
        completionDao = db.taskCompletionDao(),
    )

    private fun scheduler() = ReminderScheduler(context, repository(), SettingsStore(context))

    private fun armedAlarms(): Int =
        shadowOf(context.getSystemService(AlarmManager::class.java)).scheduledAlarms.size

    private fun meeting(
        date: LocalDate,
        start: LocalTime = LocalTime.of(10, 0),
        endDate: LocalDate = date,
        end: LocalTime = LocalTime.of(11, 0),
        id: Long = 0,
    ) = EventDraftModel(
        id = id,
        title = "ប្រជុំ",
        date = date,
        startTime = start,
        endTime = end,
        endDate = endDate,
        reminderMinutes = listOf(30),
    )

    @Test
    fun `moving an event to another day leaves only the reminder for its new date`() = runTest {
        val tomorrow = LocalDate.now().plusDays(1)
        val id = repository().save(meeting(tomorrow))
        scheduler().rescheduleAll()
        assertEquals(1, armedAlarms())

        repository().save(meeting(tomorrow.plusDays(2), id = id))
        scheduler().rescheduleAll()

        assertEquals("the reminder for the old date is still armed", 1, armedAlarms())
    }

    @Test
    fun `deleting an event disarms its reminder`() = runTest {
        val id = repository().save(meeting(LocalDate.now().plusDays(1)))
        scheduler().rescheduleAll()
        assertEquals(1, armedAlarms())

        repository().deleteEvent(id)
        scheduler().rescheduleAll()

        assertEquals("a deleted event's reminder is still armed", 0, armedAlarms())
    }

    @Test
    fun `an event running past midnight is reminded once`() = runTest {
        val tomorrow = LocalDate.now().plusDays(1)
        repository().save(
            meeting(tomorrow, start = LocalTime.of(23, 0), endDate = tomorrow.plusDays(1), end = LocalTime.of(1, 0)),
        )

        scheduler().rescheduleAll()

        assertEquals("one event, one reminder time - but two alarms", 1, armedAlarms())
    }

    @Test
    fun `turning notifications off disarms what was already armed`() = runTest {
        val store = SettingsStore(context)
        repository().save(meeting(LocalDate.now().plusDays(1)))
        scheduler().rescheduleAll()
        assertEquals(1, armedAlarms())

        try {
            store.setNotificationsEnabled(false)
            scheduler().rescheduleAll()
            assertEquals("reminders armed before switching off are still armed", 0, armedAlarms())
        } finally {
            store.setNotificationsEnabled(true)
        }
    }

    @Test
    fun `a scheduler that did not arm the alarms can still cancel them`() = runTest {
        // Stands in for process death: the scheduler that armed these is gone, and the one
        // cancelling them has never seen them. The old in-memory set was empty at this point.
        val id = repository().save(meeting(LocalDate.now().plusDays(1)))
        scheduler().rescheduleAll()

        repository().deleteEvent(id)
        ReminderScheduler(context, repository(), SettingsStore(context)).rescheduleAll()

        assertEquals(0, armedAlarms())
    }

    @Test
    fun `ticking one occurrence of a repeating task keeps the next one's reminder`() = runTest {
        val tomorrow = LocalDate.now().plusDays(1)
        val id = repository().save(
            meeting(tomorrow).copy(isTask = true, recurrence = RecurrenceRule.parse("FREQ=DAILY;COUNT=2")),
        )
        scheduler().rescheduleAll()
        assertEquals(2, armedAlarms())

        repository().setCompleted(id, tomorrow, true)
        scheduler().rescheduleAll()

        // Before, ticking one occurrence completed the series and the scheduler armed nothing.
        assertEquals(1, armedAlarms())
    }

    @Test
    fun `a meeting wrongly flagged as done is still reminded`() = runTest {
        // What the old "done" button on a meeting's notification left behind.
        val zone = ZoneId.systemDefault()
        val start = LocalDate.now().plusDays(1).atTime(10, 0)
        val id = db.eventDao().insert(
            EventEntity(
                title = "ប្រជុំប្រចាំសប្តាហ៍",
                startUtcMillis = EventTimes.toUtcMillis(start, zone, false),
                endUtcMillis = EventTimes.toUtcMillis(start.plusHours(1), zone, false),
                zoneId = zone.id,
                isTask = false,
                isCompleted = true,
                createdAtMillis = 1,
                updatedAtMillis = 1,
            ),
        )
        db.reminderDao().replaceForEvent(id, listOf(30))

        scheduler().rescheduleAll()

        assertEquals(1, armedAlarms())
    }
}
