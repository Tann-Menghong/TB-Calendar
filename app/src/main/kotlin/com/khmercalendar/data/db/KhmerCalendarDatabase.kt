package com.khmercalendar.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        EventEntity::class,
        CategoryEntity::class,
        ReminderEntity::class,
        EventExceptionEntity::class,
        DayNoteEntity::class,
        HabitEntity::class,
        HabitEntryEntity::class,
        ChecklistItemEntity::class,
        FocusSessionEntity::class,
        TaskCompletionEntity::class,
    ],
    version = 7,
    exportSchema = true,
)
abstract class KhmerCalendarDatabase : RoomDatabase() {

    abstract fun eventDao(): EventDao
    abstract fun categoryDao(): CategoryDao
    abstract fun reminderDao(): ReminderDao
    abstract fun eventExceptionDao(): EventExceptionDao
    abstract fun dayNoteDao(): DayNoteDao
    abstract fun habitDao(): HabitDao
    abstract fun checklistDao(): ChecklistDao
    abstract fun focusDao(): FocusDao
    abstract fun taskCompletionDao(): TaskCompletionDao

    companion object {
        private const val NAME = "khmer_calendar.db"

        /**
         * Adds the task priority column.
         *
         * One `ALTER TABLE ... ADD COLUMN` with a default, which is the only kind of schema
         * change SQLite performs without rewriting the table - existing events keep every
         * value they had and read back as [com.khmercalendar.domain.TaskPriority.NORMAL].
         * Covered by a migration test rather than trusted: destructive fallback is never
         * configured on this database, so a wrong migration would mean a crash on open
         * rather than a silent wipe, and neither is acceptable for someone's calendar.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE events ADD COLUMN priority INTEGER NOT NULL DEFAULT 0",
                )
            }
        }

        /**
         * Adds the countdown pin.
         *
         * Same shape as [MIGRATION_1_2] and for the same reason. Stored as INTEGER because
         * that is what SQLite gives a Kotlin Boolean, and defaulted to 0 so every existing
         * event is simply not pinned.
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE events ADD COLUMN isPinned INTEGER NOT NULL DEFAULT 0",
                )
            }
        }

        /**
         * Adds the habit tables.
         *
         * Two `CREATE TABLE`s and their indices - purely additive, so nothing existing is
         * read, rewritten or at risk. The SQL is copied from the schema Room exported for
         * version 4 rather than written by hand, which is the only way to be sure the
         * migrated database and a fresh install end up identical; the migration test opens
         * the result and lets Room compare them.
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `habits` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL, `colorArgb` INTEGER NOT NULL, " +
                        "`scheduleKind` TEXT NOT NULL, `scheduleDays` TEXT NOT NULL, " +
                        "`weeklyTarget` INTEGER NOT NULL, `sortOrder` INTEGER NOT NULL, " +
                        "`archivedAtMillis` INTEGER, `createdAtMillis` INTEGER NOT NULL)",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `habit_entries` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`habitId` INTEGER NOT NULL, `epochDay` INTEGER NOT NULL, " +
                        "`completedAtMillis` INTEGER NOT NULL, " +
                        "FOREIGN KEY(`habitId`) REFERENCES `habits`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE )",
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS " +
                        "`index_habit_entries_habitId_epochDay` " +
                        "ON `habit_entries` (`habitId`, `epochDay`)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_habit_entries_epochDay` " +
                        "ON `habit_entries` (`epochDay`)",
                )
            }
        }

        /**
         * Adds the checklist table.
         *
         * Additive, and the SQL is copied from Room's exported version-5 schema rather than
         * written by hand - the same discipline as [MIGRATION_3_4], and the only way a
         * migrated database and a fresh install are provably identical.
         */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `checklist_items` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`eventId` INTEGER NOT NULL, `text` TEXT NOT NULL, " +
                        "`isDone` INTEGER NOT NULL, `sortOrder` INTEGER NOT NULL, " +
                        "`createdAtMillis` INTEGER NOT NULL, " +
                        "FOREIGN KEY(`eventId`) REFERENCES `events`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE )",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_checklist_items_eventId` " +
                        "ON `checklist_items` (`eventId`)",
                )
            }
        }

        /**
         * Adds the focus-session table.
         *
         * Additive, SQL copied from Room's exported version-6 schema. Same discipline as
         * [MIGRATION_3_4] and [MIGRATION_4_5].
         */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `focus_sessions` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`kind` TEXT NOT NULL, `startedAtMillis` INTEGER NOT NULL, " +
                        "`plannedMinutes` INTEGER NOT NULL, `endedAtMillis` INTEGER, " +
                        "`eventId` INTEGER)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_focus_sessions_startedAtMillis` " +
                        "ON `focus_sessions` (`startedAtMillis`)",
                )
            }
        }

        /**
         * Adds per-occurrence completion for repeating tasks, and repairs what the single flag did.
         *
         * The table and index SQL are copied from the schema Room exported for version 7, as
         * every migration here is.
         *
         * ## The repair
         *
         * Until now a repeating series had one `isCompleted`, so ticking one occurrence marked the
         * whole series done. Each such row becomes a tick on a single occurrence: the first one on
         * or after the day it was ticked, because the to-do list shows a repeating task as its
         * next occurrence from today, and that is the one people were ticking. If none follows -
         * the series had ended - the last one before is used. The series flag is then cleared, so
         * the other occurrences come back undone, which is what they always were.
         *
         * A series flagged done with no completion time is left exactly as it is. There is no way
         * to know which occurrence was meant, and guessing would un-finish something the user
         * finished.
         *
         * Ordinary events lose the flag entirely. It means nothing on an event, and the only thing
         * that ever set it was the reminder's "done" button, which was shown on meetings too - and
         * left each meeting it touched silenced and missing from the dashboard.
         */
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `task_completions` (`eventId` INTEGER NOT NULL, " +
                        "`epochDay` INTEGER NOT NULL, `completedAtMillis` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`eventId`, `epochDay`), FOREIGN KEY(`eventId`) REFERENCES " +
                        "`events`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_task_completions_completedAtMillis` " +
                        "ON `task_completions` (`completedAtMillis`)",
                )
                repairRepeatingCompletions(db, java.time.ZoneId.systemDefault())
                db.execSQL(
                    "UPDATE events SET isCompleted = 0, completedAtMillis = NULL " +
                        "WHERE isTask = 0 AND isCompleted = 1",
                )
            }
        }

        /** The repair step of [MIGRATION_6_7]. The zone is a parameter so a test can fix it. */
        internal fun repairRepeatingCompletions(db: SupportSQLiteDatabase, zone: java.time.ZoneId) {
            class Legacy(
                val id: Long,
                val startMillis: Long,
                val allDay: Boolean,
                val rrule: String,
                val completedAt: Long,
            )

            val rows = ArrayList<Legacy>()
            db.query(
                "SELECT id, startUtcMillis, allDay, rrule, completedAtMillis FROM events " +
                    "WHERE isTask = 1 AND isCompleted = 1 AND rrule IS NOT NULL " +
                    "AND completedAtMillis IS NOT NULL",
            ).use { c ->
                while (c.moveToNext()) {
                    rows += Legacy(c.getLong(0), c.getLong(1), c.getInt(2) != 0, c.getString(3), c.getLong(4))
                }
            }

            val expander = com.khmercalendar.core.recurrence.RecurrenceExpander
            for (row in rows) {
                // A rule that does not parse is expanded as a single occurrence, which the flag
                // already describes correctly.
                val rule = com.khmercalendar.core.recurrence.RecurrenceRule.parse(row.rrule) ?: continue
                val exceptions = HashSet<java.time.LocalDate>()
                db.query(
                    "SELECT exceptionEpochDay FROM event_exceptions WHERE eventId = ?",
                    arrayOf<Any?>(row.id),
                ).use { c ->
                    while (c.moveToNext()) exceptions += java.time.LocalDate.ofEpochDay(c.getLong(0))
                }

                val first = com.khmercalendar.domain.EventTimes.dateOf(row.startMillis, zone, row.allDay)
                val tickedOn = java.time.Instant.ofEpochMilli(row.completedAt).atZone(zone).toLocalDate()
                val day = expander.occurrences(
                    first, rule, tickedOn, tickedOn.plusDays(REPAIR_WINDOW_DAYS), exceptions,
                ).firstOrNull()
                    ?: expander.occurrences(
                        first, rule, tickedOn.minusDays(REPAIR_WINDOW_DAYS), tickedOn, exceptions,
                    ).lastOrNull()
                    ?: continue

                db.execSQL(
                    "INSERT OR IGNORE INTO task_completions (eventId, epochDay, completedAtMillis) " +
                        "VALUES (?, ?, ?)",
                    arrayOf<Any?>(row.id, day.toEpochDay(), row.completedAt),
                )
                db.execSQL(
                    "UPDATE events SET isCompleted = 0, completedAtMillis = NULL WHERE id = ?",
                    arrayOf<Any?>(row.id),
                )
            }
        }

        /** How far either side of the tick the repair looks for the occurrence it was for. */
        private const val REPAIR_WINDOW_DAYS = 400L

        @Volatile
        private var instance: KhmerCalendarDatabase? = null

        fun get(context: Context, scope: CoroutineScope): KhmerCalendarDatabase =
            instance ?: synchronized(this) {
                instance ?: build(context.applicationContext, scope).also { instance = it }
            }

        private fun build(context: Context, scope: CoroutineScope): KhmerCalendarDatabase =
            Room.databaseBuilder(context, KhmerCalendarDatabase::class.java, NAME)
                .addMigrations(
                    MIGRATION_1_2,
                    MIGRATION_2_3,
                    MIGRATION_3_4,
                    MIGRATION_4_5,
                    MIGRATION_5_6,
                    MIGRATION_6_7,
                )
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        // Seeding runs off the creation callback so first launch is never
                        // blocked on it; the UI renders an empty calendar either way.
                        scope.launch(Dispatchers.IO) {
                            instance?.let { DefaultCategories.seed(it.categoryDao()) }
                        }
                    }
                })
                .build()
    }
}

/**
 * The categories a new install starts with.
 *
 * Chosen for how a week is actually divided in Cambodia rather than as generic placeholders,
 * and all of them renameable.
 */
object DefaultCategories {

    data class Seed(val name: String, val colorArgb: Int)

    val ALL = listOf(
        Seed("ផ្ទាល់ខ្លួន", 0xFF2F6FED.toInt()),
        Seed("ការងារ", 0xFFE0662B.toInt()),
        Seed("គ្រួសារ", 0xFF2E9E6B.toInt()),
        Seed("សាលារៀន", 0xFF8A4FD3.toInt()),
        Seed("ពិធីបុណ្យ", 0xFFCE3B57.toInt()),
    )

    suspend fun seed(dao: CategoryDao) {
        if (dao.count() > 0) return
        ALL.forEachIndexed { index, seed ->
            dao.upsert(
                CategoryEntity(
                    name = seed.name,
                    colorArgb = seed.colorArgb,
                    sortOrder = index,
                    isBuiltIn = true,
                )
            )
        }
    }
}
