package com.khmercalendar.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.content.getSystemService
import com.khmercalendar.core.holiday.HolidayKind
import com.khmercalendar.core.holiday.KhmerHolidays
import com.khmercalendar.data.prefs.SettingsStore
import com.khmercalendar.data.repo.EventRepository
import kotlinx.coroutines.flow.first
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Arms the alarms behind event reminders.
 *
 * ## Why a rolling window
 *
 * Only reminders in the next [WINDOW_DAYS] days are armed, and [ReminderSyncWorker] re-arms
 * daily. Android caps how many exact alarms an app may hold, and a daily-repeating event
 * expanded to the year 2200 would exhaust that cap on its own and starve every other
 * reminder. A rolling window keeps the count bounded no matter how the user's calendar grows.
 *
 * ## Why exact alarms, and what happens without them
 *
 * A reminder that arrives twenty minutes late has failed at its only job, so these are exact
 * alarms. From Android 12 that needs a permission the user can refuse, and from Android 13
 * it is not granted by default. When it is missing the scheduler falls back to an inexact
 * alarm rather than throwing: a slightly late reminder is worth far more than a crash, and
 * the settings screen tells the user what to grant to make it precise.
 *
 * ## Why every re-arm starts by cancelling from [AlarmRegistry]
 *
 * Re-arming is only correct if what was armed before is gone first. Until 2.5.0 it never was -
 * see [AlarmRegistry] for why - so an event moved to another day kept its old reminder, a
 * deleted event kept firing, and turning notifications off disarmed nothing.
 */
class ReminderScheduler(
    private val context: Context,
    private val repository: EventRepository,
    private val settingsStore: SettingsStore,
    private val registry: AlarmRegistry = AlarmRegistry(context),
) {

    /**
     * Re-arms every reminder, and never throws.
     *
     * Thirteen places call this, and eleven of them called it as a *side effect* of something
     * else the user asked for: saving an event, deleting one, restoring a backup, toggling a
     * notification setting, finishing a boot. None of those passed a handler, so an exception
     * here did not fail the rescheduling — it killed the process, and on `BOOT_COMPLETED` it
     * killed it at boot. Losing a reminder is bad; losing the calendar because a reminder
     * could not be armed is worse, and the user was doing something else entirely.
     *
     * The failure modes are known and all external: the exact-alarm permission can be revoked
     * between the check and the call, Android caps how many alarms an app may hold, and
     * DataStore and Room can both fail on I/O. So this is a boundary, not a blanket catch —
     * the error is logged with the component and operation behind it (there is no crash
     * reporter to send it to and there never will be), and the [Result] lets a caller that
     * genuinely needs to know check. [kotlinx.coroutines.CancellationException] is rethrown,
     * because a cancelled scope is not a failure and swallowing it would break the caller's
     * structured concurrency.
     *
     * @return the number of alarms armed, or the failure that stopped it.
     */
    suspend fun rescheduleAll(): Result<Int> = try {
        Result.success(armAll())
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.e(TAG, "ReminderScheduler.rescheduleAll failed; reminders may not fire", e)
        Result.failure(e)
    }

    private suspend fun armAll(): Int {
        val settings = settingsStore.settings.first()
        val alarmManager = context.getSystemService<AlarmManager>() ?: return 0

        cancelArmed(alarmManager)
        if (!settings.notificationsEnabled) return 0

        NotificationChannels.ensure(context, settings.notificationSoundUri, settings.notificationVibrate)

        val now = LocalDateTime.now()
        val zone = ZoneId.systemDefault()
        val occurrences = repository.upcoming(now, WINDOW_DAYS)
            // A timed event that runs past midnight is listed under every day it covers, so
            // that each day's grid can draw it - but it starts once. Keyed by the day it was
            // listed under, each copy armed its own alarm for the same minute, and the user got
            // the same reminder twice.
            .distinctBy { it.eventId to it.start }

        val armedNow = HashSet<AlarmRegistry.Armed>()
        for (occurrence in occurrences) {
            // A finished task needs no reminder. An ordinary event is never "finished": the flag
            // on one could only have come from the notification that used to offer "done" on
            // meetings too, and honouring it silenced that meeting's reminders for good.
            if (occurrence.isTask && occurrence.isCompleted) continue

            val occurrenceDay = occurrence.start.toLocalDate()
            val minutes = repository.reminders(occurrence.eventId)
            for (minutesBefore in minutes) {
                if (armedNow.size >= MAX_ALARMS) break
                val fireAt = occurrence.start.minusMinutes(minutesBefore.toLong())
                if (fireAt.isBefore(now)) continue

                val triggerMillis = fireAt.atZone(zone).toInstant().toEpochMilli()
                val requestCode = requestCode(occurrence.eventId, occurrenceDay.toEpochDay(), minutesBefore)
                val intent = ReminderReceiver.intent(
                    context = context,
                    eventId = occurrence.eventId,
                    title = occurrence.title,
                    location = occurrence.location,
                    startMillis = occurrence.start.atZone(zone).toInstant().toEpochMilli(),
                    allDay = occurrence.allDay,
                    minutesBefore = minutesBefore,
                    requestCode = requestCode,
                    isTask = occurrence.isTask,
                    occurrenceEpochDay = occurrenceDay.toEpochDay(),
                )
                val pending = PendingIntent.getBroadcast(
                    context, requestCode, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
                schedule(alarmManager, triggerMillis, pending)
                armedNow += AlarmRegistry.Armed(requestCode, intent.data.toString())
            }
        }
        // Work-shift notices ride the same daily re-arm as event reminders, so they renew
        // themselves without a second scheduler.
        WorkScheduleNotifier.reschedule(
            context = context,
            schedule = settings.workSchedule,
            enabled = settings.workNotifications && settings.notificationsEnabled,
            leadMinutes = settings.workNotifyLeadMinutes,
        )

        if (settings.holidayNotifications) {
            armHolidayReminders(alarmManager, now, zone, armedNow)
        }
        registry.replace(armedNow)
        Log.i(TAG, "Armed ${armedNow.size} reminders over the next $WINDOW_DAYS days")
        return armedNow.size
    }

    /**
     * One notification the evening before each public holiday in the window.
     *
     * The evening before rather than the morning of: the useful thing to know about a day
     * off is that tomorrow is one, while you can still change your plans. Observances are
     * skipped - they are worth showing in the calendar but not worth a notification.
     */
    private fun armHolidayReminders(
        alarmManager: AlarmManager,
        now: LocalDateTime,
        zone: ZoneId,
        armedNow: MutableSet<AlarmRegistry.Armed>,
    ) {
        val from = now.toLocalDate()
        val holidays = runCatching {
            KhmerHolidays.inRange(from, from.plusDays(WINDOW_DAYS.toLong()))
                .filter { it.kind == HolidayKind.PUBLIC }
        }.getOrDefault(emptyList())

        for (holiday in holidays) {
            if (armedNow.size >= MAX_ALARMS) break
            val fireAt = holiday.date.minusDays(1).atTime(HOLIDAY_NOTICE_HOUR, 0)
            if (fireAt.isBefore(now)) continue

            val requestCode = HOLIDAY_CODE_BASE + holiday.date.toEpochDay().toInt()
            val intent = ReminderReceiver.holidayIntent(
                context = context,
                nameKm = holiday.nameKm,
                date = holiday.date,
                bodyKm = "ថ្ងៃស្អែកជាថ្ងៃឈប់សម្រាក",
                requestCode = requestCode,
            )
            val pending = PendingIntent.getBroadcast(
                context, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            schedule(alarmManager, fireAt.atZone(zone).toInstant().toEpochMilli(), pending)
            armedNow += AlarmRegistry.Armed(requestCode, intent.data.toString())
        }
    }

    private fun schedule(alarmManager: AlarmManager, triggerMillis: Long, pending: PendingIntent) {
        val canBeExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            alarmManager.canScheduleExactAlarms()
        try {
            if (canBeExact) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pending)
            } else {
                // Still delivered, just batched by the system.
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pending)
            }
        } catch (e: SecurityException) {
            // The permission can be revoked between the check and the call.
            Log.w(TAG, "Falling back to an inexact alarm", e)
            runCatching {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pending)
            }
        }
    }

    /**
     * Cancels everything the last re-arm recorded, whichever process did it.
     *
     * The rebuilt Intent carries the stored data URI, because `filterEquals` compares it: a
     * bare Intent with the right request code matches nothing, which is how every cancel before
     * this silently did nothing. A snoozed reminder is not in the registry and is left alone -
     * see [ReminderReceiver] - so re-arming never swallows a snooze the user asked for.
     */
    private fun cancelArmed(alarmManager: AlarmManager) {
        for (entry in registry.armed()) {
            val intent = Intent(context, ReminderReceiver::class.java).apply {
                data = Uri.parse(entry.dataUri)
            }
            PendingIntent.getBroadcast(
                context, entry.requestCode, intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
            )?.let {
                alarmManager.cancel(it)
                it.cancel()
            }
        }
        registry.replace(emptySet())
    }

    /** True when reminders will fire at the exact minute rather than being batched. */
    fun canScheduleExactAlarms(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return context.getSystemService<AlarmManager>()?.canScheduleExactAlarms() ?: false
    }

    private fun requestCode(eventId: Long, epochDay: Long, minutesBefore: Int): Int =
        (eventId * 31 + epochDay * 17 + minutesBefore).hashCode()

    companion object {
        private const val TAG = "ReminderScheduler"
        const val WINDOW_DAYS = 14

        /**
         * Well under the platform cap of 500 exact alarms per app, leaving room for the
         * system to keep working if the user's calendar is unusually dense.
         */
        private const val MAX_ALARMS = 300

        /** Holiday notices fire at this hour the day before. */
        private const val HOLIDAY_NOTICE_HOUR = 18

        /** Keeps holiday request codes clear of the event ones. */
        private const val HOLIDAY_CODE_BASE = 900_000_000
    }
}
