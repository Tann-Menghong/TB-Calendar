package com.khmercalendar.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.khmercalendar.data.repo.EventRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

/**
 * Saving a day's note, through the path the dashboard uses.
 *
 * The first test here failed against every release up to 2.3.0 with `expected:<edited> but
 * was:<first>`. The DAO used `@Upsert`, which falls back from a failed insert to an update keyed
 * on the primary key - and every save builds its entity with id 0, so an edit to a day that
 * already had a note matched nothing and was silently discarded. Only a day's first note ever
 * saved.
 */
@RunWith(RobolectricTestRunner::class)
class DayNoteDaoTest {

    private lateinit var db: KhmerCalendarDatabase
    private lateinit var repository: EventRepository
    private val day = LocalDate.of(2026, 9, 10)

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            KhmerCalendarDatabase::class.java,
        ).allowMainThreadQueries().build()
        repository = EventRepository(
            db.eventDao(),
            db.categoryDao(),
            db.reminderDao(),
            db.eventExceptionDao(),
            db.dayNoteDao(),
            db.checklistDao(),
        )
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `editing a day's note keeps the new text`() = runTest {
        repository.saveNote(day, "first")
        repository.saveNote(day, "edited")

        assertEquals("edited", repository.observeNote(day).first())
    }

    @Test
    fun `editing a note many times leaves exactly one row for the day`() = runTest {
        repeat(5) { repository.saveNote(day, "draft $it") }

        val rows = db.dayNoteDao().all()
        assertEquals(1, rows.size)
        assertEquals("draft 4", rows.single().text)
    }

    @Test
    fun `a note on another day is untouched`() = runTest {
        repository.saveNote(day, "today")
        repository.saveNote(day.plusDays(1), "tomorrow")
        repository.saveNote(day, "today, edited")

        assertEquals("tomorrow", repository.observeNote(day.plusDays(1)).first())
        assertEquals("today, edited", repository.observeNote(day).first())
    }

    @Test
    fun `clearing a note removes it`() = runTest {
        repository.saveNote(day, "something")
        repository.saveNote(day, "   ")

        assertNull(repository.observeNote(day).first())
    }
}
