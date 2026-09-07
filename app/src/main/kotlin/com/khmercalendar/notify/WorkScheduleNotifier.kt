package com.khmercalendar.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.util.Log
import androidx.core.content.getSystemService
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

    fun reschedule(context: Context, schedule: WorkSchedule, enabled: Boolean) {
        val alarmManager = context.getSystemService<AlarmManager>() ?: return
        cancelAll(context, alarmManager)
        if (!enabled || !schedule.enabled) return

        val now = LocalDateTime.now()
        val zone = ZoneId.systemDefault()

        WorkClock.transitionsFor(schedule, now)
            .filter { it.at.isAfter(now) }
            .forEachIndexed { index, transition ->
                val title = titleFor(transition.becomes)
                val body = bodyFor(transition.becomes, transition.block.labelKm)
                if (title == null) return@forEachIndexed

                val code = CODE_BASE + index
                val intent = ReminderReceiver.holidayIntent(
                    context = context,
                    nameKm = title,
                    date = transition.at.toLocalDate(),
                    bodyKm = body,
                    requestCode = code,
                ).apply {
                    // Distinct data per transition, so four alarms in one day do not collapse
                    // into a single PendingIntent.
                    data = android.net.Uri.parse("khmercalendar://work/${transition.at}")
                }

                val pending = PendingIntent.getBroadcast(
                    context, code, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
                val triggerAt = transition.at.atZone(zone).toInstant().toEpochMilli()
                runCatching {
                    // Inexact on purpose: a work-shift notice a minute or two late costs the
                    // user nothing, and an exact alarm here would spend a scarce, battery-
                    // relevant resource that event reminders need more.
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
                    armed += code
                }.onFailure { Log.w(TAG, "Could not arm a work notification", it) }
            }
    }

    private val armed = mutableSetOf<Int>()

    private fun cancelAll(context: Context, alarmManager: AlarmManager) {
        armed.forEach { code ->
            val intent = android.content.Intent(context, ReminderReceiver::class.java)
            PendingIntent.getBroadcast(
                context, code, intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
            )?.let {
                alarmManager.cancel(it)
                it.cancel()
            }
        }
        armed.clear()
    }

    private fun titleFor(state: WorkState): String? = when (state) {
        WorkState.WORKING -> "ដល់ម៉ោងចូលធ្វើការ"
        WorkState.BREAK -> "ដល់ម៉ោងសម្រាក"
        WorkState.FINISHED -> "ចប់ការងារហើយ"
        else -> null
    }

    private fun bodyFor(state: WorkState, blockLabel: String): String = when (state) {
        WorkState.WORKING -> blockLabel
        WorkState.BREAK -> "សម្រាកសិន រួចចូលធ្វើការវិញ"
        WorkState.FINISHED -> "ថ្ងៃនេះការងារបានបញ្ចប់"
        else -> ""
    }
}
