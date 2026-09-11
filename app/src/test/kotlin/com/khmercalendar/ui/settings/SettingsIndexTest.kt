package com.khmercalendar.ui.settings

import com.khmercalendar.ui.Routes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Finding a setting, and making sure the index still describes the real screens.
 *
 * The second half is the part that matters most. An index is a copy of words that live on
 * screens, and a copy drifts: rename a row and search would keep offering the old name, opening
 * a screen that no longer has it. These tests read the screen sources and fail on that instead.
 */
class SettingsIndexTest {

    // --- searching -----------------------------------------------------------------------

    @Test
    fun `one character is not a search`() {
        assertTrue(SettingsIndex.search("ព").isEmpty())
        assertTrue(SettingsIndex.search(" ").isEmpty())
    }

    @Test
    fun `a Khmer word finds the row it names`() {
        assertEquals("ញ័រ", SettingsIndex.search("ញ័រ").first().titleKm)
        assertEquals("លេខខ្មែរ", SettingsIndex.search("លេខខ្មែរ").first().titleKm)
    }

    @Test
    fun `an English word finds the setting another app would have called that`() {
        assertEquals("ពន្លឺ", SettingsIndex.search("dark").first().titleKm)
        assertEquals("ចាក់សោកម្មវិធី", SettingsIndex.search("PIN").first().titleKm)
        assertEquals("ថ្ងៃដំបូងនៃសប្តាហ៍", SettingsIndex.search("week start").first().titleKm)
    }

    @Test
    fun `a Khmer synonym works as well as the title`() {
        // "ងងឹត" (dark) is not on any row; the theme group is where it lives.
        assertEquals("ពន្លឺ", SettingsIndex.search("ងងឹត").first().titleKm)
    }

    @Test
    fun `the row's own words outrank a synonym`() {
        // "ពណ៌" is in the title of the accent group and a keyword of the category list.
        val results = SettingsIndex.search("ពណ៌")
        assertEquals("ពណ៌សំខាន់", results.first().titleKm)
        assertTrue(results.indexOfFirst { it.titleKm == "ប្រភេទព្រឹត្តិការណ៍" } > 0)
    }

    @Test
    fun `a search with no match finds nothing rather than everything`() {
        assertTrue(SettingsIndex.search("zzqq").isEmpty())
    }

    // --- the index against the screens ---------------------------------------------------

    private val screenFiles = mapOf(
        Routes.SETTINGS to "SettingsScreen.kt",
        Routes.SETTINGS_APPEARANCE to "AppearanceScreen.kt",
        Routes.SETTINGS_NOTIFICATIONS to "NotificationSettingsScreen.kt",
        Routes.SETTINGS_DASHBOARD to "DashboardEditorScreen.kt",
        Routes.SETTINGS_AI to "AiModelScreen.kt",
        Routes.SETTINGS_BACKUP to "BackupScreen.kt",
        Routes.SETTINGS_WORK to "WorkScheduleScreen.kt",
    )

    private fun source(name: String): String {
        val relative = "src/main/kotlin/com/khmercalendar/ui/settings/$name"
        val file = listOf(File(relative), File("app/$relative")).firstOrNull { it.exists() }
        assertTrue("screen source $name not found", file != null)
        return file!!.readText()
    }

    @Test
    fun `every group search scrolls to is a group on that screen`() {
        for (entry in SettingsIndex.ALL) {
            val section = entry.section ?: continue
            val name = screenFiles[entry.route]
            assertTrue("${entry.titleKm}: no screen source mapped for ${entry.route}", name != null)
            assertTrue(
                "${entry.titleKm}: group \"$section\" is not on ${entry.route}",
                source(name!!).contains("SettingsGroup(\"$section\")"),
            )
        }
    }

    @Test
    fun `every title search shows is written on its screen or on the settings index`() {
        val index = source("SettingsScreen.kt")
        for (entry in SettingsIndex.ALL) {
            val onScreen = screenFiles[entry.route]?.let { source(it) }.orEmpty()
            val literal = "\"${entry.titleKm}\""
            assertTrue(
                "${entry.titleKm} is not written on ${entry.route} or the settings index",
                onScreen.contains(literal) || index.contains(literal),
            )
        }
    }

    @Test
    fun `no setting is listed twice for the same screen`() {
        val keys = SettingsIndex.ALL.map { Triple(it.titleKm, it.route, it.section) }
        assertEquals(keys.size, keys.toSet().size)
    }
}
