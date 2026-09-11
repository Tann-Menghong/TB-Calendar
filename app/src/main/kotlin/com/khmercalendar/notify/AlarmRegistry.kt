package com.khmercalendar.notify

import android.content.Context

/**
 * The reminder alarms currently armed, kept on disk.
 *
 * ## Why this exists
 *
 * Android has no way to ask "which alarms does this app hold?". The only way to cancel one is
 * to rebuild a PendingIntent that matches it - same request code *and* an Intent that
 * `filterEquals` the one that armed it, where the data URI counts. The scheduler used to keep
 * just the request codes, in memory, and rebuilt a bare Intent without the URI. That lookup
 * never matched, so nothing was ever cancelled: a moved event kept its old reminder, a deleted
 * one kept firing, and switching notifications off left everything already armed in place.
 * And because the set lived in memory, it was empty after every process death anyway.
 *
 * So both halves of the identity are stored, and they are stored where a restart cannot lose
 * them. SharedPreferences rather than Room or DataStore: this is a few hundred short strings
 * that are always read and replaced whole, it must be readable synchronously from wherever the
 * scheduler runs, and it has nothing to do with the user's calendar.
 */
class AlarmRegistry(context: Context) {

    /** Enough to rebuild a PendingIntent that matches an armed alarm. */
    data class Armed(val requestCode: Int, val dataUri: String)

    private val prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun armed(): Set<Armed> =
        prefs.getStringSet(KEY, emptySet()).orEmpty().mapNotNullTo(HashSet()) { decode(it) }

    /**
     * Replaces the whole set.
     *
     * `commit`, not `apply`: the scheduler runs off the main thread, and a set written lazily
     * after a process is killed mid-reschedule would describe alarms that were never armed - or
     * lose ones that were.
     */
    fun replace(entries: Set<Armed>) {
        prefs.edit().putStringSet(KEY, entries.mapTo(HashSet()) { encode(it) }).commit()
    }

    private fun encode(entry: Armed): String = "${entry.requestCode}$SEPARATOR${entry.dataUri}"

    private fun decode(raw: String): Armed? {
        val at = raw.indexOf(SEPARATOR)
        if (at <= 0) return null
        val code = raw.substring(0, at).toIntOrNull() ?: return null
        return Armed(code, raw.substring(at + 1))
    }

    private companion object {
        const val FILE = "armed_alarms"
        const val KEY = "armed"

        /** Never appears in a request code, and the URIs this app builds never contain it. */
        const val SEPARATOR = '|'
    }
}
