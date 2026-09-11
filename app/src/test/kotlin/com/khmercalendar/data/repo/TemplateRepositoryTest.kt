package com.khmercalendar.data.repo

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.khmercalendar.core.recurrence.Frequency
import com.khmercalendar.core.recurrence.RecurrenceRule
import com.khmercalendar.data.db.CategoryEntity
import com.khmercalendar.data.db.KhmerCalendarDatabase
import com.khmercalendar.data.db.TemplateEntity
import com.khmercalendar.domain.EventTemplate
import com.khmercalendar.domain.TaskPriority
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalTime

@RunWith(RobolectricTestRunner::class)
class TemplateRepositoryTest {

    private lateinit var db: KhmerCalendarDatabase
    private lateinit var repository: TemplateRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            KhmerCalendarDatabase::class.java,
        ).allowMainThreadQueries().build()
        repository = TemplateRepository(db.templateDao())
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `a saved template reads back as it was saved`() = runTest {
        val category = db.categoryDao().upsert(CategoryEntity(name = "សុខភាព", colorArgb = 1))
        val template = EventTemplate(
            name = "រត់",
            title = "រត់ពេលព្រឹក",
            description = "ក្រោយភ្ញាក់",
            location = "សួន",
            startTime = LocalTime.of(5, 45),
            durationMinutes = 40,
            categoryId = category,
            colorArgb = 0xFF00AA00.toInt(),
            isTask = true,
            priority = TaskPriority.entries.last(),
            reminderMinutes = listOf(5, 15),
            recurrence = RecurrenceRule(frequency = Frequency.WEEKLY, interval = 1),
        )

        val id = repository.save(template)
        val read = repository.observeAll().first().single()

        assertEquals(template.copy(id = id, recurrence = null), read.copy(recurrence = null))
        assertEquals(template.recurrence!!.toRRule(), read.recurrence!!.toRRule())
    }

    @Test
    fun `a hand-edited row is read safely rather than crashing the list`() = runTest {
        db.templateDao().insert(
            TemplateEntity(
                name = "x",
                title = "x",
                startMinute = 2_000,
                durationMinutes = 0,
                priority = 99,
                reminderMinutes = "10,abc,-3,10,",
                rrule = "not a rule",
                createdAtMillis = 1,
            ),
        )

        val read = repository.observeAll().first().single()

        assertEquals(LocalTime.of(23, 59), read.startTime)
        assertEquals(1, read.durationMinutes)
        assertEquals(listOf(10), read.reminderMinutes)
        assertNull(read.recurrence)
    }

    @Test
    fun `deleting a template leaves the others`() = runTest {
        val first = repository.save(EventTemplate(name = "ក", title = "ក"))
        repository.save(EventTemplate(name = "ខ", title = "ខ"))

        repository.delete(first)

        assertEquals(listOf("ខ"), repository.observeAll().first().map { it.name })
        assertTrue(db.templateDao().all().none { it.id == first })
    }
}
