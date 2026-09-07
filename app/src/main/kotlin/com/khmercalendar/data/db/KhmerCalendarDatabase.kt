package com.khmercalendar.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
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
    ],
    version = 1,
    exportSchema = true,
)
abstract class KhmerCalendarDatabase : RoomDatabase() {

    abstract fun eventDao(): EventDao
    abstract fun categoryDao(): CategoryDao
    abstract fun reminderDao(): ReminderDao
    abstract fun eventExceptionDao(): EventExceptionDao
    abstract fun dayNoteDao(): DayNoteDao

    companion object {
        private const val NAME = "khmer_calendar.db"

        @Volatile
        private var instance: KhmerCalendarDatabase? = null

        fun get(context: Context, scope: CoroutineScope): KhmerCalendarDatabase =
            instance ?: synchronized(this) {
                instance ?: build(context.applicationContext, scope).also { instance = it }
            }

        private fun build(context: Context, scope: CoroutineScope): KhmerCalendarDatabase =
            Room.databaseBuilder(context, KhmerCalendarDatabase::class.java, NAME)
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
