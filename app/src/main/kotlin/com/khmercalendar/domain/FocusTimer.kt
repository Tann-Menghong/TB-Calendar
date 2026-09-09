package com.khmercalendar.domain

import java.time.LocalDate
import java.time.ZoneId

/** What a focus session is for. Breaks are sessions too, so the cycle can be reasoned about. */
enum class FocusKind(val stored: String, val labelKm: String) {
    FOCUS("focus", "ផ្តោតអារម្មណ៍"),
    SHORT_BREAK("short", "សម្រាកខ្លី"),
    LONG_BREAK("long", "សម្រាកវែង"),
    ;

    val isBreak: Boolean get() = this != FOCUS

    companion object {
        fun of(stored: String?): FocusKind = entries.firstOrNull { it.stored == stored } ?: FOCUS
    }
}

/**
 * How long each part of the cycle runs, in minutes.
 *
 * Defaults are the classic 25/5/15 after four, which is what most people mean by "pomodoro".
 * They are settings rather than constants because the classic numbers suit nobody in
 * particular - a translator works in 90-minute blocks and a student revising works in 20.
 */
data class FocusSettings(
    val focusMinutes: Int = 25,
    val shortBreakMinutes: Int = 5,
    val longBreakMinutes: Int = 15,
    val sessionsBeforeLongBreak: Int = 4,
) {
    fun minutesFor(kind: FocusKind): Int = when (kind) {
        FocusKind.FOCUS -> focusMinutes
        FocusKind.SHORT_BREAK -> shortBreakMinutes
        FocusKind.LONG_BREAK -> longBreakMinutes
    }.coerceIn(MIN_MINUTES, MAX_MINUTES)

    companion object {
        const val MIN_MINUTES = 1
        const val MAX_MINUTES = 180
    }
}

/**
 * A session, running or finished.
 *
 * @property startedAtMillis when it began. This, plus [plannedMinutes], is the whole state -
 *   see [FocusTimer] for why nothing counts down in memory.
 * @property endedAtMillis when it actually stopped, or null while it is still running. A
 *   session stopped early keeps the time it really ran, so the statistics are honest about
 *   twelve minutes rather than crediting the twenty-five that were planned.
 */
data class FocusSession(
    val id: Long,
    val kind: FocusKind,
    val startedAtMillis: Long,
    val plannedMinutes: Int,
    val endedAtMillis: Long?,
    /** The task this session was for, or null. */
    val eventId: Long?,
) {
    val isRunning: Boolean get() = endedAtMillis == null
}

/**
 * The focus timer.
 *
 * ## Why nothing ticks in the background
 *
 * The obvious implementation is a foreground service counting down once a second. It is also
 * the reason timer apps drain batteries and get killed by aggressive OEM power managers - and
 * this app runs on phones where that is the norm.
 *
 * So a running session is *two numbers in the database*: when it started and how long it was
 * meant to last. Everything else is derived from the clock whenever somebody asks. The screen
 * ticks only while it is on screen, one alarm is scheduled for the end, and if the process is
 * killed the session survives it exactly, because nothing was being held in memory to lose.
 *
 * The consequence worth stating: this measures *wall-clock* time, not attention. A session
 * left running overnight has run overnight. [elapsedMinutes] is capped at the planned length
 * for exactly that reason - the statistics should not credit eight hours of focus to somebody
 * who forgot to press stop.
 */
object FocusTimer {

    /** Milliseconds left, or 0 once the planned time has passed. Never negative. */
    fun remainingMillis(session: FocusSession, nowMillis: Long): Long {
        val ends = session.startedAtMillis + session.plannedMinutes * 60_000L
        return (ends - nowMillis).coerceAtLeast(0L)
    }

    fun isOver(session: FocusSession, nowMillis: Long): Boolean =
        remainingMillis(session, nowMillis) == 0L

    /** 0f..1f through the planned time, for the ring. */
    fun progress(session: FocusSession, nowMillis: Long): Float {
        val total = session.plannedMinutes * 60_000L
        if (total <= 0L) return 1f
        val done = (nowMillis - session.startedAtMillis).coerceIn(0L, total)
        return done.toFloat() / total
    }

    /** mm:ss, which is what a countdown of at most three hours needs. */
    fun format(remainingMillis: Long): String {
        val totalSeconds = (remainingMillis / 1000L).coerceAtLeast(0L)
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return "%02d:%02d".format(minutes, seconds)
    }

    /**
     * How long a finished session actually ran, in whole minutes.
     *
     * Capped at what was planned. A session somebody forgot to stop should not report nine
     * hours of focus - the number would be false and it would poison every total it is added
     * to. Under-reporting a forgotten timer is the safe direction to be wrong in.
     */
    fun elapsedMinutes(session: FocusSession): Int {
        val ended = session.endedAtMillis ?: return 0
        val ran = ((ended - session.startedAtMillis) / 60_000L).toInt().coerceAtLeast(0)
        return minOf(ran, session.plannedMinutes)
    }

    /**
     * What should come next after [completed] finishes.
     *
     * A focus session leads to a break, and every [FocusSettings.sessionsBeforeLongBreak]-th
     * one to a long break; a break leads back to focus. [focusesToday] counts the focus
     * sessions already completed, which is what decides whether this one earns the long break.
     */
    fun next(completed: FocusKind, focusesToday: Int, settings: FocusSettings): FocusKind {
        if (completed.isBreak) return FocusKind.FOCUS
        val n = settings.sessionsBeforeLongBreak.coerceAtLeast(1)
        return if (focusesToday > 0 && focusesToday % n == 0) {
            FocusKind.LONG_BREAK
        } else {
            FocusKind.SHORT_BREAK
        }
    }

    /**
     * Total focused minutes among [sessions] on [date].
     *
     * Breaks do not count - resting is part of the method, not part of the work - and a
     * session is attributed to the day it *started* on. One that runs over midnight belongs
     * to the evening it began in, which is how the person who did it would describe it.
     */
    fun minutesOn(sessions: List<FocusSession>, date: LocalDate, zone: ZoneId): Int = sessions
        .filter { !it.kind.isBreak && !it.isRunning }
        .filter { startDate(it, zone) == date }
        .sumOf { elapsedMinutes(it) }

    /** The same, over an inclusive range - the week and month figures. */
    fun minutesBetween(
        sessions: List<FocusSession>,
        from: LocalDate,
        to: LocalDate,
        zone: ZoneId,
    ): Int = sessions
        .filter { !it.kind.isBreak && !it.isRunning }
        .filter { val d = startDate(it, zone); !d.isBefore(from) && !d.isAfter(to) }
        .sumOf { elapsedMinutes(it) }

    /** How many focus sessions were completed on [date], for the cycle and the count. */
    fun completedFocusesOn(sessions: List<FocusSession>, date: LocalDate, zone: ZoneId): Int =
        sessions.count {
            !it.kind.isBreak && !it.isRunning && startDate(it, zone) == date
        }

    private fun startDate(session: FocusSession, zone: ZoneId): LocalDate =
        java.time.Instant.ofEpochMilli(session.startedAtMillis).atZone(zone).toLocalDate()
}
