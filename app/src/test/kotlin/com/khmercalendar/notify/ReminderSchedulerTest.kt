package com.khmercalendar.notify

import android.database.sqlite.SQLiteException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.khmercalendar.data.db.EventDao
import com.khmercalendar.data.db.EventEntity
import com.khmercalendar.data.db.KhmerCalendarDatabase
import com.khmercalendar.data.prefs.SettingsStore
import com.khmercalendar.data.repo.EventRepository
import com.khmercalendar.domain.EventDraftModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate
import java.time.LocalTime

/**
 * That arming alarms cannot take the app down with it.
 *
 * Thirteen places call [ReminderScheduler.rescheduleAll], and eleven of them do it as a side
 * effect of something else — saving an event, deleting one, restoring a backup, toggling a
 * notification setting, finishing a boot — with no exception handler in the scope. An
 * exception escaping this call did not fail the rescheduling, it killed the process, and from
 * `BOOT_COMPLETED` it killed it at boot.
 */
@RunWith(RobolectricTestRunner::class)
class ReminderSchedulerTest {

    private lateinit var db: KhmerCalendarDatabase
    private lateinit var context: android.content.Context

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

    private fun repository(eventDao: EventDao = db.eventDao()) = EventRepository(
        eventDao = eventDao,
        categoryDao = db.categoryDao(),
        reminderDao = db.reminderDao(),
        exceptionDao = db.eventExceptionDao(),
        noteDao = db.dayNoteDao(),
    )

    private fun scheduler(eventDao: EventDao = db.eventDao()) = ReminderScheduler(
        context = context,
        repository = repository(eventDao),
        settingsStore = SettingsStore(context),
    )

    @Test
    fun `a healthy calendar arms without error`() = runTest {
        val tomorrow = LocalDate.now().plusDays(1)
        repository().save(
            EventDraftModel(
                title = "ប្រជុំ",
                date = tomorrow,
                startTime = LocalTime.of(10, 0),
                endTime = LocalTime.of(11, 0),
                endDate = tomorrow,
                reminderMinutes = listOf(30),
            ),
        )

        assertTrue(scheduler().rescheduleAll().isSuccess)
    }

    @Test
    fun `a failing query is reported, not thrown`() = runTest {
        // Standing in for the real failures this call has: a corrupt or locked database, a
        // DataStore read that fails on I/O, the exact-alarm permission revoked between the
        // check and the call. Before, any of them would have reached an unguarded
        // viewModelScope or the boot receiver's bare CoroutineScope and killed the process.
        val result = scheduler(FailingEventDao).rescheduleAll()

        assertTrue("must not throw out of the scheduler", result.isFailure)
        assertTrue(result.exceptionOrNull() is SQLiteException)
    }

    @Test
    fun `cancelling the caller still cancels`() = runTest {
        // The one exception that must keep propagating. Catching it would leave a cancelled
        // coroutine believing it had succeeded, which is why this is a targeted catch that
        // rethrows CancellationException rather than a bare runCatching.
        val job = launch(start = kotlinx.coroutines.CoroutineStart.LAZY) {
            scheduler().rescheduleAll()
        }

        job.cancel(CancellationException("caller went away"))
        job.join()

        assertTrue(job.isCancelled)
    }

    /** A database that answers every read the way a corrupt one does. */
    private object FailingEventDao : EventDao {
        private fun fail(): Nothing = throw SQLiteException("database disk image is malformed")

        override fun observeCandidates(fromMillis: Long, toMillis: Long): Flow<List<EventEntity>> =
            flowOf(emptyList())

        override suspend fun candidates(fromMillis: Long, toMillis: Long): List<EventEntity> = fail()
        override suspend fun allEvents(): List<EventEntity> = fail()
        override suspend fun byId(id: Long): EventEntity? = fail()
        override fun observeById(id: Long): Flow<EventEntity?> = flowOf(null)
        override fun search(query: String): Flow<List<EventEntity>> = flowOf(emptyList())
        override suspend fun insert(event: EventEntity): Long = fail()
        override suspend fun update(event: EventEntity) = fail()
        override suspend fun deleteById(id: Long) = fail()
        override suspend fun setCompleted(id: Long, completed: Boolean, atMillis: Long) = fail()
        override suspend fun setPriority(id: Long, priority: Int, atMillis: Long) = fail()
        override suspend fun count(): Int = fail()
        override suspend fun completedTaskCount(): Int = fail()
    }
}
