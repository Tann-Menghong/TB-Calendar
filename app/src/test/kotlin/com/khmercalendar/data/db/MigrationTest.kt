package com.khmercalendar.data.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * The schema migration, against a database actually built at the old version.
 *
 * ## Why this test exists at all
 *
 * This database has no destructive fallback configured, which is the right choice for
 * somebody's calendar: a migration that does not match the entities makes the app fail to
 * open rather than quietly deleting every event. That is the safer failure, and it is still a
 * failure nobody should ship - so the migration is exercised here rather than trusted.
 *
 * The old schema is read from the exported `1.json` rather than transcribed into this file.
 * A transcription would test my copy of version 1; the export is what version 1 actually was.
 */
@RunWith(RobolectricTestRunner::class)
class MigrationTest {

    private lateinit var context: Context
    private lateinit var dbFile: File
    private var db: KhmerCalendarDatabase? = null

    private companion object {
        const val NAME = "migration-test.db"
        const val ZONE = "Asia/Phnom_Penh"
    }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        dbFile = context.getDatabasePath(NAME)
        dbFile.parentFile?.mkdirs()
        dbFile.delete()
    }

    @After
    fun tearDown() {
        db?.close()
        dbFile.delete()
    }

    /** Builds the database exactly as version 1 left it, from the exported schema. */
    private fun createVersion1() {
        val schema = JSONObject(schemaJson(1)).getJSONObject("database")
        val raw = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
        try {
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                val table = entity.getString("tableName")
                raw.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                val indices = entity.optJSONArray("indices") ?: continue
                for (j in 0 until indices.length()) {
                    raw.execSQL(
                        indices.getJSONObject(j).getString("createSql")
                            .replace("\${TABLE_NAME}", table),
                    )
                }
            }
            // room_master_table and its identity hash. Without it Room treats the file as
            // one it did not create and refuses to migrate.
            val setup = schema.getJSONArray("setupQueries")
            for (i in 0 until setup.length()) raw.execSQL(setup.getString(i))
            raw.version = 1
        } finally {
            raw.close()
        }
    }

    private fun schemaJson(version: Int): String {
        val relative = "schemas/com.khmercalendar.data.db.KhmerCalendarDatabase/$version.json"
        val file = listOf(File(relative), File("app/$relative")).firstOrNull { it.exists() }
        assertNotNull("exported schema $version.json not found - is exportSchema still on?", file)
        return file!!.readText()
    }

    private fun openMigrated(): KhmerCalendarDatabase =
        Room.databaseBuilder(context, KhmerCalendarDatabase::class.java, NAME)
            .addMigrations(KhmerCalendarDatabase.MIGRATION_1_2)
            .allowMainThreadQueries()
            .build()
            .also {
                db = it
                // Room validates the schema against the entities on first open, so touching
                // the database here is what turns a wrong migration into a failed test.
                it.openHelper.writableDatabase
            }

    @Test
    fun `an existing calendar survives the upgrade intact`() = runBlocking {
        createVersion1()
        SQLiteDatabase.openOrCreateDatabase(dbFile, null).use { raw ->
            raw.execSQL(
                "INSERT INTO categories (id, name, colorArgb, isVisible, sortOrder, isBuiltIn) " +
                    "VALUES (1, 'ការងារ', 255, 1, 0, 1)",
            )
            raw.execSQL(
                "INSERT INTO events (id, title, description, location, startUtcMillis, " +
                    "endUtcMillis, allDay, zoneId, rrule, categoryId, colorArgb, isTask, " +
                    "isCompleted, completedAtMillis, createdAtMillis, updatedAtMillis) VALUES " +
                    "(7, 'ប្រជុំ', 'កំណត់ចំណាំ', 'ភ្នំពេញ', 1000, 2000, 0, '$ZONE', " +
                    "'FREQ=WEEKLY', 1, 123, 1, 0, NULL, 500, 600)",
            )
            raw.execSQL("INSERT INTO reminders (id, eventId, minutesBefore) VALUES (1, 7, 30)")
            raw.execSQL(
                "INSERT INTO day_notes (id, epochDay, text, updatedAtMillis) " +
                    "VALUES (1, 20000, 'កំណត់ចំណាំថ្ងៃ', 700)",
            )
        }

        val migrated = openMigrated()
        val event = migrated.eventDao().byId(7)

        assertNotNull("the event was lost by the migration", event)
        requireNotNull(event)
        assertEquals("ប្រជុំ", event.title)
        assertEquals("កំណត់ចំណាំ", event.description)
        assertEquals("ភ្នំពេញ", event.location)
        assertEquals(1000L, event.startUtcMillis)
        assertEquals(2000L, event.endUtcMillis)
        assertEquals(ZONE, event.zoneId)
        assertEquals("FREQ=WEEKLY", event.rrule)
        assertEquals(1L, event.categoryId)
        assertEquals(123, event.colorArgb)
        assertTrue(event.isTask)
        assertEquals(500L, event.createdAtMillis)

        // The new column reads as NORMAL for everything that predates it.
        assertEquals(0, event.priority)

        // Nothing else was dropped on the way through.
        assertEquals(1, migrated.categoryDao().count())
        assertEquals(listOf(30), migrated.reminderDao().forEvent(7).map { it.minutesBefore })
        assertEquals(
            listOf("កំណត់ចំណាំថ្ងៃ"),
            migrated.dayNoteDao().all().map { it.text },
        )
    }

    @Test
    fun `priority is writable once the migration has run`() = runBlocking {
        createVersion1()
        val migrated = openMigrated()

        val id = migrated.eventDao().insert(
            EventEntity(
                title = "ដាក់របាយការណ៍",
                startUtcMillis = 10,
                endUtcMillis = 20,
                zoneId = ZONE,
                isTask = true,
                priority = 1,
                createdAtMillis = 1,
                updatedAtMillis = 1,
            ),
        )

        assertEquals(1, migrated.eventDao().byId(id)?.priority)

        migrated.eventDao().setPriority(id, 2, atMillis = 99)
        assertEquals(2, migrated.eventDao().byId(id)?.priority)
        assertEquals(99L, migrated.eventDao().byId(id)?.updatedAtMillis)
    }

    @Test
    fun `a fresh install opens at the current version without a migration`() = runBlocking {
        // The migration path and the create-from-scratch path produce different code in Room,
        // and only one of them is exercised by the tests above.
        val fresh = openMigrated()

        assertEquals(0, fresh.eventDao().count())
        assertEquals(2, fresh.openHelper.writableDatabase.version)
    }
}
