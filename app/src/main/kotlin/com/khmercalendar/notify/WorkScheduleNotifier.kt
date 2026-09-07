package com.khmercalendar.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.util.Log
import androidx.core.content.getSystemService
import com.khmercalendar.core.khmer.KhmerNumerals
import com.khmercalendar.core.work.WorkClock
import com.khmercalendar.core.work.WorkSchedule
import com.khmercalendar.core.work.WorkState
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Notifies at the moments the working day changes.
 *
 * ## Why so few
 *
 * Four boundaries a day at most, and only when the user has asked for them. A countdown that
 * announces itself every fifteen minutes gets silenced within a week, taking the reminders
 * the user actually wanted with it - Android's notification settings are per-channel, and
 * these share a channel with event reminders.
 *
 * Alarms are armed for today only and re-armed by [ReminderSyncWorker] on its daily run,
 * matching how event reminders already work: a bounded, self-renewing window rather than a
 * standing set of alarms stretching into next year.
 */
object WorkScheduleNotifier {

    private const val TAG = "WorkNotifier"

    /** Kept clear of event reminder codes and of holiday codes. */
    private const val CODE_BASE = 800_000_000

    /**
     * How many work alarms may exist at once.
     *
     * A ceiling rather than a guess: cancellation sweeps this whole range, so it has to cover
     * anything a previous run could have armed - including one by a build with a different
     * schedule. Eight is the realistic maximum (four changes plus four warnings); the rest is
     * headroom, and sweeping 64 no-op request codes costs microseconds.
     */
    private const val MAX_CODES = 64

    /**
     * The alarm's identity, derived from its request code alone.
     *
     * It must be reconstructible without any stored state, because cancelling a PendingIntent
     * requires an Intent that `filterEquals` the one that armed it - and `data` counts. This
     * used to be built from the transition's timestamp, which meant a cancel could never
     * reproduce it: work notices were armed but never removed, so turning them off, editing a
     * shift or changing the warning time left the old alarms firing. Keying on the code makes
     * the pair reproducible from nothing.
     */
    private fun dataFor(code: Int): android.net.Uri =
        android.net.Uri.parse("khmercalendar://work/$code")

    /**
     * @param leadMinutes minutes of warning before each change, or 0 for none. A warning is
     *   skipped when it would land in the past or collide with the change it warns about.
     */
    fun reschedule(
        context: Context,
        schedule: WorkSchedule,
        enabled: Boolean,
        leadMinutes: Int = 0,
    ) {
        val alarmManager = context.getSystemService<AlarmManager>() ?: return
        cancelAll(context, alarmManager)
        if (!enabled || !schedule.enabled) return

        val now = LocalDateTime.now()
        val zone = ZoneId.systemDefault()
        val lead = leadMinutes.coerceIn(0, 60).toLong()

        // Each change, plus its warning when one is asked for. Built as one list so the two
        // kinds share the request-code space and cannot overwrite each other's PendingIntent.
        val moments = WorkClock.transitionsFor(schedule, now).flatMap { transition ->
            val warning = if (lead > 0) {
                transition.at.minusMinutes(lead)
                    .takeIf { it.isAfter(now) }
                    ?.let { Moment(it, transition.becomes, transition.block.labelKm, early = true) }
            } else {
                null
            }
            listOfNotNull(
                warning,
                Moment(transition.at, transition.becomes, transition.block.labelKm, early = false),
            )
        }

        moments
            .filter { it.at.isAfter(now) }
            .take(MAX_CODES)
            .forEachIndexed { index, moment ->
                val title = titleFor(moment.becomes, moment.early, lead.toInt())
                val body = bodyFor(moment.becomes, moment.blockLabel, moment.early)
                if (title == null) return@forEachIndexed

                val code = CODE_BASE + index
                val intent = ReminderReceiver.holidayIntent(
                    context = context,
                    nameKm = title,
                    date = moment.at.toLocalDate(),
                    bodyKm = body,
                    requestCode = code,
                ).apply { data = dataFor(code) }

                val pending = PendingIntent.getBroadcast(
                    context, code, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
                val triggerAt = moment.at.atZone(zone).toInstant().toEpochMilli()
                runCatching {
                    // Inexact on purpose: a work-shift notice a minute or two late costs the
                    // user nothing, and an exact alarm here would spend a scarce, battery-
                    // relevant resource that event reminders need more.
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
                }.onFailure { Log.w(TAG, "Could not arm a work notification", it) }
            }
    }

    /** A change, or the warning that precedes it. */
    private data class Moment(
        val at: LocalDateTime,
        val becomes: WorkState,
        val blockLabel: String,
        val early: Boolean,
    )

    /**
     * Removes every work alarm, including any left by an earlier process.
     *
     * The whole request-code range is swept rather than a remembered set: the set lived in
     * memory, so after a restart it was empty and yesterday's alarms could never be reached.
     */
    private fun cancelAll(context: Context, alarmManager: AlarmManager) {
        for (offset in 0 until MAX_CODES) {
            val code = CODE_BASE + offset
            val intent = android.content.Intent(context, ReminderReceiver::class.java)
                .apply { data = dataFor(code) }
            PendingIntent.getBroadcast(
                context, code, intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
            )?.let {
                alarmManager.cancel(it)
                it.cancel()
            }
        }
    }

    private fun titleFor(state: WorkState, early: Boolean, leadMinutes: Int): String? {
        if (!early) {
            return when (state) {
                WorkState.WORKING -> "ដល់ម៉ោងចូលធ្វើការ"
                WorkState.BREAK -> "ដល់ម៉ោងសម្រាក"
                WorkState.FINISHED -> "ចប់ការងារហើយ"
                else -> null
            }
        }
        val inMinutes = KhmerNumerals.toKhmer(leadMinutes)
        return when (state) {
            WorkState.WORKING -> "ចូលធ្វើការក្នុង $inMinutes នាទី"
            WorkState.BREAK -> "ជិតដល់ម៉ោងសម្រាក"
            WorkState.FINISHED -> "ជិតចប់ការងារ"
            else -> null
        }
    }

    private fun bodyFor(state: WorkState, blockLabel: String, early: Boolean): String = when {
        early && state == WorkState.WORKING -> "ត្រៀមខ្លួន៖ $blockLabel"
        early -> "នៅសល់បន្តិចទៀត"
        state == WorkState.WORKING -> blockLabel
        state == WorkState.BREAK -> "សម្រាកសិន រួចចូលធ្វើការវិញ"
        state == WorkState.FINISHED -> "ថ្ងៃនេះការងារបានបញ្ចប់"
        else -> ""
    }
}
