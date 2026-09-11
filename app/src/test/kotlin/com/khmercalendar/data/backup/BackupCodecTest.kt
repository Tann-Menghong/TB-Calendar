package com.khmercalendar.data.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Reading the backup file itself: telling a backup from everything else, and saying precisely
 * what is wrong with one that cannot be read.
 */
class BackupCodecTest {

    /** Built from its code point: a literal byte-order mark in source is invisible. */
    private val bom = Char(0xFEFF).toString()

    private val minimal = """{"formatVersion": 2, "appVersion": "2.4.0", "exportedAtMillis": 1}"""

    @Test
    fun `an ordinary JSON object is not a backup`() {
        // With defaults on the identifying fields this decoded as a valid, empty backup.
        assertThrows(BackupError.NotABackup::class.java) { BackupCodec.decode("""{"hello": 1}""") }
        assertThrows(BackupError.NotABackup::class.java) { BackupCodec.decode("{}") }
    }

    @Test
    fun `a calendar file, an array and an empty file are not backups`() {
        assertThrows(BackupError.NotABackup::class.java) {
            BackupCodec.decode("BEGIN:VCALENDAR\r\nVERSION:2.0\r\nEND:VCALENDAR\r\n")
        }
        assertThrows(BackupError.NotABackup::class.java) { BackupCodec.decode("[1, 2, 3]") }
        assertThrows(BackupError.NotABackup::class.java) { BackupCodec.decode("") }
    }

    @Test
    fun `a file from a newer app says so rather than claiming to be damaged`() {
        // Even though the body is unreadable to this version, the answer is "update the app".
        val error = assertThrows(BackupError.TooNew::class.java) {
            BackupCodec.decode("""{"formatVersion": 4, "appVersion": "9.0", "exportedAtMillis": 1, "events": "changed"}""")
        }
        assertEquals(4, error.version)
    }

    @Test
    fun `a backup cut short is damaged, not foreign`() {
        val truncated = """{"formatVersion": 2, "appVersion": "2.4.0", "exportedAtMillis": 1, "events": [ {"id": 1, "tit"""
        assertThrows(BackupError.Damaged::class.java) { BackupCodec.decode(truncated) }
    }

    @Test
    fun `a backup with a field of the wrong shape is damaged`() {
        assertThrows(BackupError.Damaged::class.java) {
            BackupCodec.decode("""{"formatVersion": 2, "appVersion": "2.4.0", "exportedAtMillis": 1, "events": "nope"}""")
        }
        assertThrows(BackupError.Damaged::class.java) {
            BackupCodec.decode("""{"formatVersion": 2, "exportedAtMillis": 1}""")
        }
    }

    @Test
    fun `a byte-order mark from a Windows editor is accepted`() {
        val archive = BackupCodec.decode(bom + minimal)
        assertEquals(2, archive.formatVersion)
    }

    @Test
    fun `an archive survives encoding and decoding unchanged`() {
        val archive = BackupArchive(
            formatVersion = BackupArchive.FORMAT_VERSION,
            appVersion = "2.4.0",
            exportedAtMillis = 1_789_000_000_000,
            events = listOf(
                BackupEvent(
                    id = 7,
                    title = "ប្រជុំ",
                    startUtcMillis = 1,
                    endUtcMillis = 2,
                    zoneId = "Asia/Phnom_Penh",
                    isTask = true,
                    isCompleted = true,
                    priority = 1,
                    isPinned = true,
                    completedAtMillis = 3,
                    checklist = listOf(BackupChecklistItem("ជំហាន", isDone = true, sortOrder = 2)),
                ),
            ),
            habits = listOf(
                BackupHabit(
                    id = 1,
                    name = "អាន",
                    colorArgb = 5,
                    scheduleKind = "weekly",
                    weeklyTarget = 3,
                    createdAtMillis = 9,
                    entries = listOf(BackupHabitEntry(20_700, 11)),
                ),
            ),
            focusSessions = listOf(BackupFocusSession("focus", 100, 25, 1_600, eventId = 7)),
            settings = BackupSettings(accentArgb = 42, fontScale = 1.2f, dashboardHidden = listOf("note")),
        )

        assertEquals(archive, BackupCodec.decode(BackupCodec.encode(archive)))
    }

    @Test
    fun `a file is recognised by its content, not its name`() {
        assertEquals(ImportKind.BACKUP, BackupCodec.kindOf(minimal))
        assertEquals(ImportKind.BACKUP, BackupCodec.kindOf(bom + "  \n{\"formatVersion\""))
        assertEquals(ImportKind.ICS, BackupCodec.kindOf("BEGIN:VCALENDAR\r\n"))
        assertEquals(ImportKind.ICS, BackupCodec.kindOf("\r\nbegin:vcalendar"))
        assertEquals(ImportKind.UNKNOWN, BackupCodec.kindOf("PK"))
        assertEquals(ImportKind.UNKNOWN, BackupCodec.kindOf(""))
    }
}
