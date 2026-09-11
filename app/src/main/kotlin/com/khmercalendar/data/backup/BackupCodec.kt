package com.khmercalendar.data.backup

import com.khmercalendar.domain.FocusSettings
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import java.time.LocalDate
import java.time.ZoneId

/**
 * Why a backup could not be read or written, in words the user can act on.
 *
 * The screen used to show the parser's own exception text - "Unexpected JSON token at offset
 * 0" - in English, to someone reading the app in Khmer. Each case here is a different thing the
 * user can do something about: pick another file, update the app, or try another folder.
 */
sealed class BackupError(val messageKm: String, cause: Throwable? = null) : Exception(messageKm, cause) {

    /** Some other kind of file: an .ics, a photo, somebody else's JSON. */
    class NotABackup(cause: Throwable? = null) :
        BackupError("ឯកសារនេះមិនមែនជាឯកសារបម្រុងទុករបស់ប្រតិទិនខ្មែរទេ", cause)

    /** Recognisably a backup, but cut short or edited into something unreadable. */
    class Damaged(cause: Throwable? = null) :
        BackupError("ឯកសារបម្រុងទុកនេះខូច ឬមិនពេញលេញ។ មិនបានស្តារអ្វីទាំងអស់", cause)

    /** Written by a newer app, which may hold data this one would silently drop. */
    class TooNew(val version: Int) :
        BackupError("ឯកសារនេះបង្កើតដោយកំណែថ្មីជាងកម្មវិធីនេះ។ សូមធ្វើបច្ចុប្បន្នភាពកម្មវិធីជាមុនសិន")

    class Unreadable(cause: Throwable? = null) :
        BackupError("មិនអាចបើកឯកសារបានទេ", cause)

    class WriteFailed(cause: Throwable? = null) :
        BackupError("មិនអាចរក្សាទុកឯកសារបានទេ។ សូមសាកល្បងថតផ្សេង", cause)
}

/** What a chosen file turned out to be, from its first bytes rather than its name. */
enum class ImportKind { BACKUP, ICS, UNKNOWN }

/** Reading and writing the backup file. Pure: no database, no Android. */
object BackupCodec {

    /**
     * U+FEFF, written as a code point. A literal one in source is invisible, is rejected by
     * lint, and has already been silently decoded into the file once by a tool on its way in.
     */
    private val BOM: String = Char(0xFEFF).toString()

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(archive: BackupArchive): String =
        json.encodeToString(BackupArchive.serializer(), archive)

    /**
     * Parses a backup, or says precisely why it cannot.
     *
     * The version is read *before* the body is decoded. Decoding first would report a file from
     * a newer app as damaged whenever its shape had changed, when the true and actionable answer
     * is "update the app".
     *
     * A leading byte-order mark is accepted: a backup opened and saved in a Windows text editor
     * gains one, and the file is otherwise perfectly good.
     */
    fun decode(text: String): BackupArchive {
        val clean = text.removePrefix(BOM).trim()
        // Enough to tell "a backup that was cut short" from "not a backup at all" when the text
        // will not even parse - a download interrupted halfway is the common way to get one.
        val mentionsVersion = clean.contains("\"formatVersion\"")

        val root = try {
            json.parseToJsonElement(clean)
        } catch (e: IllegalArgumentException) {
            throw if (mentionsVersion) BackupError.Damaged(e) else BackupError.NotABackup(e)
        }
        val obj = root as? JsonObject ?: throw BackupError.NotABackup()
        val version = (obj["formatVersion"] as? JsonPrimitive)?.intOrNull
            ?: throw BackupError.NotABackup()
        if (version < 1) throw BackupError.NotABackup()
        if (version > BackupArchive.FORMAT_VERSION) throw BackupError.TooNew(version)

        return try {
            json.decodeFromJsonElement(BackupArchive.serializer(), obj)
        } catch (e: IllegalArgumentException) {
            // SerializationException is an IllegalArgumentException, so this covers a missing
            // required field and a field of the wrong type alike.
            throw BackupError.Damaged(e)
        }
    }

    /**
     * Tells a backup from a calendar file by content.
     *
     * The screen used to decide by whether the address ended in ".ics". Files shared from
     * another app arrive as `content://` addresses with no extension at all, so every shared
     * calendar file was handed to the backup parser and failed with a JSON error.
     */
    fun kindOf(head: String): ImportKind {
        val start = head.removePrefix(BOM).trimStart()
        return when {
            start.startsWith("{") -> ImportKind.BACKUP
            start.startsWith("BEGIN:VCALENDAR", ignoreCase = true) -> ImportKind.ICS
            else -> ImportKind.UNKNOWN
        }
    }
}

/**
 * What a row must satisfy before it is allowed into the database.
 *
 * A row that fails is skipped and counted, never allowed to abort the whole restore: one
 * hand-edited event with a typo should not cost somebody the other nine hundred.
 */
internal object BackupValidation {

    private val FIRST_DAY = LocalDate.of(1900, 1, 1).toEpochDay()
    private val LAST_DAY = LocalDate.of(2199, 12, 31).toEpochDay()

    /**
     * The zone is checked because something will call `ZoneId.of` on it later, and an unknown
     * zone would throw there - in a reminder receiver, long after the restore reported success.
     */
    fun isValid(event: BackupEvent): Boolean =
        event.title.isNotBlank() &&
            event.endUtcMillis >= event.startUtcMillis &&
            isZone(event.zoneId)

    /**
     * Only finished sessions. One that was running when the backup was made is not a record of
     * anything yet, and restoring it would resurrect a days-old timer with no alarm behind it.
     */
    fun isValid(session: BackupFocusSession): Boolean {
        val ended = session.endedAtMillis ?: return false
        return ended >= session.startedAtMillis &&
            session.plannedMinutes in FocusSettings.MIN_MINUTES..FocusSettings.MAX_MINUTES
    }

    /** The calendar's own supported range. */
    fun isValidDay(epochDay: Long): Boolean = epochDay in FIRST_DAY..LAST_DAY

    private fun isZone(id: String): Boolean = runCatching { ZoneId.of(id) }.isSuccess
}
