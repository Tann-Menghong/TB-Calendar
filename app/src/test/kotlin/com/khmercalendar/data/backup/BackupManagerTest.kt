package com.khmercalendar.data.backup

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.khmercalendar.data.db.CategoryEntity
import com.khmercalendar.data.db.ChecklistItemEntity
import com.khmercalendar.data.db.DayNoteEntity
import com.khmercalendar.data.db.EventEntity
import com.khmercalendar.data.db.EventExceptionEntity
import com.khmercalendar.data.db.FocusSessionEntity
import com.khmercalendar.data.db.HabitEntity
import com.khmercalendar.data.db.HabitEntryEntity
import com.khmercalendar.data.db.KhmerCalendarDatabase
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Backup and restore against real in-memory databases.
 *
 * Two databases - the phone that made the backup and the phone restoring it - because nearly
 * everything that can go wrong is a link that has to be rewritten on the way across: a category,
 * a checklist step, a habit tick, the task a focus session was for.
 */
@RunWith(RobolectricTestRunner::class)
class BackupManagerTest {

    private lateinit var source: KhmerCalendarDatabase
    private lateinit var target: KhmerCalendarDatabase

    private val everything = RestoreSelection(calendar = true, productivity = true, settings = false)

    @Before
    fun setUp() {
        source = memoryDatabase()
        target = memoryDatabase()
    }

    @After
    fun tearDown() {
        source.close()
        target.close()
    }

    private fun memoryDatabase() = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext(),
        KhmerCalendarDatabase::class.java,
    ).allowMainThreadQueries().build()

    private fun manager(db: KhmerCalendarDatabase, settings: PortableSettings? = null) =
        BackupManager(ApplicationProvider.getApplicationContext(), db, "test", settings)

    private suspend fun backupOf(db: KhmerCalendarDatabase, settings: PortableSettings? = null): String =
        BackupCodec.encode(manager(db, settings).snapshot())

    private suspend fun restore(
        text: String,
        into: KhmerCalendarDatabase = target,
        selection: RestoreSelection = everything,
        settings: PortableSettings? = null,
    ): RestoreSummary {
        val manager = manager(into, settings)
        return manager.restoreArchive(manager.previewOf(text).archive, selection)
    }

    /** One of everything, with every field that format 1 did not carry set to something. */
    private suspend fun seed(db: KhmerCalendarDatabase): Long {
        val category = db.categoryDao().upsert(
            CategoryEntity(name = "ការងារ", colorArgb = 0xFF0000FF.toInt(), isVisible = false, sortOrder = 3),
        )
        val task = db.eventDao().insert(
            EventEntity(
                title = "ដាក់របាយការណ៍",
                description = "ផ្ញើមុនម៉ោង ៥",
                location = "ការិយាល័យ",
                startUtcMillis = 1_789_000_000_000,
                endUtcMillis = 1_789_003_600_000,
                zoneId = "Asia/Phnom_Penh",
                rrule = "FREQ=WEEKLY",
                categoryId = category,
                colorArgb = 0xFF112233.toInt(),
                isTask = true,
                isCompleted = true,
                priority = 1,
                isPinned = true,
                completedAtMillis = 1_789_001_000_000,
                createdAtMillis = 1_700_000_000_000,
                updatedAtMillis = 1_750_000_000_000,
            ),
        )
        db.reminderDao().replaceForEvent(task, listOf(0, 30))
        db.eventExceptionDao().insert(EventExceptionEntity(task, 20_710))
        db.checklistDao().insert(
            ChecklistItemEntity(eventId = task, text = "ពិនិត្យលេខ", isDone = true, sortOrder = 0, createdAtMillis = 1),
        )
        db.checklistDao().insert(
            ChecklistItemEntity(eventId = task, text = "ផ្ញើអ៊ីមែល", isDone = false, sortOrder = 1, createdAtMillis = 2),
        )
        db.dayNoteDao().upsert(DayNoteEntity(epochDay = 20_706, text = "ចំណាំ", updatedAtMillis = 5))
        val habit = db.habitDao().upsert(
            HabitEntity(
                name = "អានសៀវភៅ",
                colorArgb = 7,
                scheduleKind = "days",
                scheduleDays = "1,3,5",
                sortOrder = 2,
                archivedAtMillis = 1_760_000_000_000,
                createdAtMillis = 1_600_000_000_000,
            ),
        )
        db.habitDao().insertEntry(HabitEntryEntity(habitId = habit, epochDay = 20_700, completedAtMillis = 11))
        db.habitDao().insertEntry(HabitEntryEntity(habitId = habit, epochDay = 20_702, completedAtMillis = 12))
        db.focusDao().insert(
            FocusSessionEntity(
                kind = "focus",
                startedAtMillis = 1_789_002_000_000,
                plannedMinutes = 25,
                endedAtMillis = 1_789_003_500_000,
                eventId = task,
            ),
        )
        return task
    }

    // --- nothing lost ------------------------------------------------------------------

    @Test
    fun `a backup restores every field, including everything format 1 dropped`() = runTest {
        seed(source)

        val summary = restore(backupOf(source))

        val event = target.eventDao().allEvents().single()
        assertEquals("ដាក់របាយការណ៍", event.title)
        assertEquals("ផ្ញើមុនម៉ោង ៥", event.description)
        assertEquals("ការិយាល័យ", event.location)
        assertEquals("FREQ=WEEKLY", event.rrule)
        assertEquals(0xFF112233.toInt(), event.colorArgb)
        assertTrue(event.isTask)
        assertTrue(event.isCompleted)
        assertEquals(1, event.priority)
        assertTrue(event.isPinned)
        assertEquals(1_789_001_000_000, event.completedAtMillis)
        assertEquals(1_700_000_000_000, event.createdAtMillis)

        val category = target.categoryDao().byId(event.categoryId!!)!!
        assertEquals("ការងារ", category.name)
        assertFalse(category.isVisible)

        assertEquals(listOf(0, 30), target.reminderDao().forEvent(event.id).map { it.minutesBefore })
        assertEquals(listOf(20_710L), target.eventExceptionDao().all().map { it.exceptionEpochDay })

        val steps = target.checklistDao().all().sortedBy { it.sortOrder }
        assertEquals(listOf("ពិនិត្យលេខ", "ផ្ញើអ៊ីមែល"), steps.map { it.text })
        assertEquals(listOf(true, false), steps.map { it.isDone })
        assertTrue(steps.all { it.eventId == event.id })

        assertEquals("ចំណាំ", target.dayNoteDao().all().single().text)

        val habit = target.habitDao().all().single()
        assertEquals("1,3,5", habit.scheduleDays)
        assertEquals(1_760_000_000_000, habit.archivedAtMillis)
        assertEquals(1_600_000_000_000, habit.createdAtMillis)
        assertEquals(listOf(20_700L, 20_702L), target.habitDao().allEntries().map { it.epochDay })
        assertTrue(target.habitDao().allEntries().all { it.habitId == habit.id })

        val session = target.focusDao().all().single()
        assertEquals(event.id, session.eventId)
        assertEquals(1_789_003_500_000, session.endedAtMillis)

        assertEquals(1, summary.added.events)
        assertEquals(2, summary.added.checklistItems)
        assertEquals(2, summary.added.habitDays)
        assertEquals(0, summary.unreadable)
    }

    // --- nothing doubled ---------------------------------------------------------------

    @Test
    fun `restoring the same backup twice adds nothing the second time`() = runTest {
        seed(source)
        val text = backupOf(source)

        restore(text)
        val second = restore(text)

        assertTrue(second.added.isEmpty)
        assertEquals(1, second.skipped.events)
        assertEquals(1, second.skipped.notes)
        assertEquals(1, second.skipped.habits)
        assertEquals(2, second.skipped.habitDays)
        assertEquals(1, second.skipped.focusSessions)

        assertEquals(1, target.eventDao().count())
        assertEquals(1, target.categoryDao().count())
        assertEquals(2, target.checklistDao().all().size)
        assertEquals(1, target.habitDao().all().size)
        assertEquals(2, target.habitDao().allEntries().size)
        assertEquals(1, target.focusDao().count())
    }

    @Test
    fun `the preview predicts what the restore then skips`() = runTest {
        seed(source)
        val text = backupOf(source)
        restore(text)

        val preview = manager(target).previewOf(text)
        val second = restore(text)

        assertEquals(preview.alreadyHere, second.skipped)
    }

    @Test
    fun `reading a preview writes nothing`() = runTest {
        seed(source)

        val preview = manager(target).previewOf(backupOf(source))

        assertEquals(1, preview.inFile.events)
        assertEquals(2, preview.inFile.habitDays)
        assertEquals(0, target.eventDao().count())
        assertEquals(0, target.habitDao().all().size)
    }

    @Test
    fun `an event repeated inside one file is restored once`() = runTest {
        val event = BackupEvent(id = 1, title = "ដដែល", startUtcMillis = 10, endUtcMillis = 20, zoneId = "UTC")
        val archive = BackupArchive(
            formatVersion = 2,
            appVersion = "t",
            exportedAtMillis = 1,
            events = listOf(event, event.copy(id = 2)),
        )

        val summary = restore(BackupCodec.encode(archive))

        assertEquals(1, target.eventDao().count())
        assertEquals(1, summary.skipped.events)
    }

    // --- nothing overwritten -----------------------------------------------------------

    @Test
    fun `a note already on the phone is never overwritten`() = runTest {
        seed(source)
        target.dayNoteDao().upsert(DayNoteEntity(epochDay = 20_706, text = "សរសេរក្រោយ", updatedAtMillis = 9))

        val summary = restore(backupOf(source))

        val text = target.dayNoteDao().all().single().text
        assertTrue(text.startsWith("សរសេរក្រោយ"))
        assertTrue(text.contains("ចំណាំ"))
        assertEquals(1, summary.notesMerged)
    }

    @Test
    fun `a note that already says the same thing is left alone`() = runTest {
        seed(source)
        target.dayNoteDao().upsert(DayNoteEntity(epochDay = 20_706, text = "ចំណាំ", updatedAtMillis = 9))

        val summary = restore(backupOf(source))

        assertEquals("ចំណាំ", target.dayNoteDao().all().single().text)
        assertEquals(0, summary.notesMerged)
        assertEquals(1, summary.skipped.notes)
    }

    @Test
    fun `a habit with the same name gains the file's ticks rather than a twin`() = runTest {
        seed(source)
        val existing = target.habitDao().upsert(
            HabitEntity(name = "អានសៀវភៅ", colorArgb = 1, scheduleKind = "daily", createdAtMillis = 1),
        )
        target.habitDao().insertEntry(HabitEntryEntity(habitId = existing, epochDay = 20_700, completedAtMillis = 1))

        val summary = restore(backupOf(source))

        assertEquals(1, target.habitDao().all().size)
        assertEquals(setOf(20_700L, 20_702L), target.habitDao().allEntries().map { it.epochDay }.toSet())
        assertTrue(target.habitDao().allEntries().all { it.habitId == existing })
        // The habit on the phone keeps its own schedule; a restore adds history, not opinions.
        assertEquals("daily", target.habitDao().all().single().scheduleKind)
        assertEquals(1, summary.added.habitDays)
        assertEquals(1, summary.skipped.habitDays)
    }

    // --- all or nothing ----------------------------------------------------------------

    @Test
    fun `a failure partway through leaves the phone exactly as it was`() = runTest {
        seed(source)
        source.eventDao().insert(
            EventEntity(title = "ទីពីរ", startUtcMillis = 1, endUtcMillis = 2, zoneId = "UTC", createdAtMillis = 1, updatedAtMillis = 1),
        )
        val manager = manager(target)
        val archive = manager.previewOf(backupOf(source)).archive

        var written = 0
        try {
            manager.restoreArchive(archive, everything) {
                written++
                if (written == 2) throw IllegalStateException("simulated failure after the second event")
            }
            fail("the simulated failure should have propagated")
        } catch (expected: IllegalStateException) {
            // The point of the test is what is left behind.
        }

        assertEquals(2, written)
        assertEquals(0, target.eventDao().count())
        assertEquals(0, target.categoryDao().count())
        assertEquals(0, target.checklistDao().all().size)
        assertEquals(0, target.reminderDao().all().size)
    }

    // --- older, newer and damaged files ------------------------------------------------

    @Test
    fun `a format 1 file still restores, without inventing when its tasks were done`() = runTest {
        val formatOne = """
            {
              "formatVersion": 1,
              "appVersion": "1.4.0",
              "exportedAtMillis": 1757000000000,
              "categories": [ { "id": 4, "name": "គ្រួសារ", "colorArgb": -13721461, "isVisible": true, "sortOrder": 2, "isBuiltIn": true } ],
              "events": [ { "id": 9, "title": "ទិញអំណោយ", "description": null, "location": null,
                            "startUtcMillis": 1757030400000, "endUtcMillis": 1757034000000, "allDay": false,
                            "zoneId": "Asia/Phnom_Penh", "rrule": null, "categoryId": 4, "colorArgb": null,
                            "isTask": true, "isCompleted": true, "reminderMinutes": [15], "exceptionEpochDays": [] } ],
              "notes": [ { "epochDay": 20336, "text": "ថ្ងៃកំណើតម៉ាក់" } ]
            }
        """.trimIndent()

        restore(formatOne)

        val event = target.eventDao().allEvents().single()
        assertTrue(event.isCompleted)
        // Format 1 never recorded this. It used to be restored as "now", which put every task
        // the user had ever finished into today's statistics.
        assertNull(event.completedAtMillis)
        assertEquals(0, event.priority)
        assertFalse(event.isPinned)
        assertEquals("គ្រួសារ", target.categoryDao().byId(event.categoryId!!)!!.name)
        assertEquals(listOf(15), target.reminderDao().forEvent(event.id).map { it.minutesBefore })
        assertEquals("ថ្ងៃកំណើតម៉ាក់", target.dayNoteDao().all().single().text)
    }

    @Test
    fun `a file from a newer app is refused before anything is written`() = runTest {
        try {
            manager(target).previewOf("""{"formatVersion": 3, "appVersion": "9", "exportedAtMillis": 1}""")
            fail("a newer format must be refused")
        } catch (expected: BackupError.TooNew) {
            assertEquals(3, expected.version)
        }
        assertEquals(0, target.eventDao().count())
    }

    @Test
    fun `unreadable rows are skipped and counted, and the rest still restore`() = runTest {
        fun event(id: Long, title: String, zone: String = "Asia/Phnom_Penh", start: Long = 0, end: Long = 1) =
            BackupEvent(id = id, title = title, startUtcMillis = start, endUtcMillis = end, zoneId = zone)

        val archive = BackupArchive(
            formatVersion = 2,
            appVersion = "t",
            exportedAtMillis = 1,
            events = listOf(
                event(1, "ល្អ"),
                event(2, "តំបន់ម៉ោងមិនស្គាល់", zone = "Mars/Olympus"),
                event(3, "   "),
                event(4, "បញ្ច្រាស", start = 10, end = 5),
            ),
        )

        val summary = restore(BackupCodec.encode(archive))

        assertEquals("ល្អ", target.eventDao().allEvents().single().title)
        assertEquals(3, summary.unreadable)
    }

    @Test
    fun `a session still running when the backup was made is not restored`() = runTest {
        source.focusDao().insert(
            FocusSessionEntity(kind = "focus", startedAtMillis = 10, plannedMinutes = 25, endedAtMillis = null),
        )

        val summary = restore(backupOf(source))

        assertEquals(0, target.focusDao().count())
        assertEquals(1, summary.unreadable)
    }

    // --- selective restore ---------------------------------------------------------------

    @Test
    fun `restoring only productivity links a session to the matching task already here`() = runTest {
        seed(source)
        val text = backupOf(source)

        restore(text, selection = RestoreSelection(calendar = true, productivity = false))
        assertEquals(0, target.focusDao().count())

        val summary = restore(text, selection = RestoreSelection(calendar = false, productivity = true))

        assertEquals(1, target.eventDao().count())
        assertEquals(0, summary.added.events)
        assertEquals(target.eventDao().allEvents().single().id, target.focusDao().all().single().eventId)
    }

    @Test
    fun `a session whose task is not in the file loses the link rather than borrowing another`() = runTest {
        val archive = BackupArchive(
            formatVersion = 2,
            appVersion = "t",
            exportedAtMillis = 1,
            focusSessions = listOf(BackupFocusSession("focus", 100, 25, 1_600, eventId = 1)),
        )
        // An unrelated event that happens to hold id 1 on this phone.
        target.eventDao().insert(
            EventEntity(title = "ផ្សេង", startUtcMillis = 0, endUtcMillis = 1, zoneId = "UTC", createdAtMillis = 1, updatedAtMillis = 1),
        )

        restore(BackupCodec.encode(archive))

        assertNull(target.focusDao().all().single().eventId)
    }

    // --- settings ----------------------------------------------------------------------

    private class FakeSettings(
        private val stored: BackupSettings = BackupSettings(),
        private val failOnApply: Boolean = false,
    ) : PortableSettings {
        var applied: BackupSettings? = null

        override suspend fun exportPortable(): BackupSettings = stored

        override suspend fun applyPortable(incoming: BackupSettings) {
            if (failOnApply) throw java.io.IOException("simulated DataStore failure")
            applied = incoming
        }
    }

    @Test
    fun `settings travel in the file but are applied only when chosen`() = runTest {
        val text = backupOf(source, FakeSettings(BackupSettings(accentArgb = 42, weekStart = 7)))
        val here = FakeSettings()

        assertTrue(manager(target, here).previewOf(text).hasSettings)

        val without = restore(text, settings = here)
        assertNull(here.applied)
        assertFalse(without.settingsApplied)

        val with = restore(text, selection = everything.copy(settings = true), settings = here)
        assertEquals(42, here.applied?.accentArgb)
        assertEquals(7, here.applied?.weekStart)
        assertTrue(with.settingsApplied)
    }

    @Test
    fun `settings that cannot be written do not undo a restored calendar`() = runTest {
        seed(source)
        val text = backupOf(source, FakeSettings(BackupSettings(accentArgb = 1)))

        val summary = restore(
            text,
            selection = everything.copy(settings = true),
            settings = FakeSettings(failOnApply = true),
        )

        assertTrue(summary.settingsFailed)
        assertFalse(summary.settingsApplied)
        assertEquals(1, target.eventDao().count())
    }
}
