package com.khmercalendar.data.repo

import android.content.Context
import com.khmercalendar.data.db.FocusDao
import com.khmercalendar.data.db.FocusSessionEntity
import com.khmercalendar.domain.FocusKind
import com.khmercalendar.domain.FocusSession
import com.khmercalendar.notify.FocusAlarm
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.ZoneId

/**
 * Focus sessions, and the single alarm that goes with the running one.
 *
 * The alarm is set here rather than in the view model because it must survive the screen: the
 * whole point of storing a session instead of ticking one is that closing the app changes
 * nothing, and an alarm scheduled from a view model would be as short-lived as the view model.
 */
class FocusRepository(
    private val context: Context,
    private val focusDao: FocusDao,
    private val zoneProvider: () -> ZoneId = { ZoneId.systemDefault() },
) {

    fun observeRunning(): Flow<FocusSession?> =
        focusDao.observeRunning().map { it?.toSession() }

    /** Sessions from [days] ago onwards - enough for today's and this week's totals. */
    fun observeRecent(days: Long = RECENT_DAYS): Flow<List<FocusSession>> {
        val from = LocalDate.now()
            .minusDays(days)
            .atStartOfDay(zoneProvider())
            .toInstant()
            .toEpochMilli()
        return focusDao.observeSince(from).map { rows -> rows.map { it.toSession() } }
    }

    /**
     * Starts a session, ending any that was already running.
     *
     * There is at most one at a time by construction rather than by hope: two overlapping
     * sessions would double-count the same minutes into every total they appear in.
     */
    suspend fun start(kind: FocusKind, minutes: Int, eventId: Long? = null): Long {
        stop()
        val now = System.currentTimeMillis()
        val id = focusDao.insert(
            FocusSessionEntity(
                kind = kind.stored,
                startedAtMillis = now,
                plannedMinutes = minutes,
                eventId = eventId,
            ),
        )
        FocusAlarm.schedule(context, kind, now + minutes * 60_000L)
        return id
    }

    /**
     * Ends the running session, keeping the time it actually ran.
     *
     * Idempotent: stopping when nothing is running cancels a stray alarm and does nothing
     * else, which is what a screen resumed after the alarm already fired needs.
     */
    suspend fun stop() {
        val running = focusDao.running()
        if (running != null) {
            focusDao.finish(running.id, System.currentTimeMillis())
        }
        FocusAlarm.cancel(context)
    }

    /** Throws the running session away entirely, for "cancel" rather than "stop". */
    suspend fun discard() {
        focusDao.running()?.let { focusDao.deleteById(it.id) }
        FocusAlarm.cancel(context)
    }

    private fun FocusSessionEntity.toSession() = FocusSession(
        id = id,
        kind = FocusKind.of(kind),
        startedAtMillis = startedAtMillis,
        plannedMinutes = plannedMinutes,
        endedAtMillis = endedAtMillis,
        eventId = eventId,
    )

    private companion object {
        /** A fortnight: more than today and this week need, and still a small read. */
        const val RECENT_DAYS = 14L
    }
}
