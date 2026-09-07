package com.khmercalendar.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
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
 */
class ReminderScheduler(
    private val context: Context,
    private val repository: EventRepository,
    private val settingsStore: SettingsStore,
) {

    suspend fun rescheduleAll() {
        val settings = settingsStore.settings.first()
        val alarmManager = context.getSystemService<AlarmManager>() ?: return

        cancelAll(alarmManager)
        if (!settings.notificationsEnabled) return

        NotificationChannels.ensure(context, settings.notificationSoundUri, settings.notificationVibrate)

        val now = LocalDateTime.now()
        val zone = ZoneId.systemDefault()
        val occurrences = repository.upcoming(now, WINDOW_DAYS)

        var armed = 0
        for (occurrence in occurrences) {
            if (occurrence.isCompleted) continue
            val minutes = repository.reminders(occurrence.eventId)
            for (minutesBefore in minutes) {
                if (armed >= MAX_ALARMS) return
                val fireAt = occurrence.start.minusMinutes(minutesBefore.toLong())
                if (fireAt.isBefore(now)) continue

                val triggerMillis = fireAt.atZone(zone).toInstant().toEpochMilli()
                val requestCode = requestCode(occurrence.eventId, occurrence.occurrenceDate.toEpochDay(), minutesBefore)
                val intent = ReminderReceiver.intent(
                    context = context,
                    eventId = occurrence.eventId,
                    title = occurrence.title,
                    location = occurrence.location,
                    startMillis = occurrence.start.atZone(zone).toInstant().toEpochMilli(),
                    allDay = occurrence.allDay,
                    minutesBefore = minutesBefore,
                    requestCode = requestCode,
                )
                val pending = PendingIntent.getBroadcast(
                    context, requestCode, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
                schedule(alarmManager, triggerMillis, pending)
                trackedRequestCodes += requestCode
                armed++
            }
        }
        // Work-shift notices ride the same daily re-arm as event reminders, so they renew
        // themselves without a second scheduler.
        WorkScheduleNotifier.reschedule(
            context = context,
            schedule = settings.workSchedule,
            enabled = settings.workNotifications && settings.notificationsEnabled,
        )

        if (settings.holidayNotifications) {
            armed += armHolidayReminders(alarmManager, now, zone, armed)
        }
        Log.i(TAG, "Armed $armed reminders over the next $WINDOW_DAYS days")
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
        alreadyArmed: Int,
    ): Int {
        val from = now.toLocalDate()
        val holidays = runCatching {
            KhmerHolidays.inRange(from, from.plusDays(WINDOW_DAYS.toLong()))
                .filter { it.kind == HolidayKind.PUBLIC }
        }.getOrDefault(emptyList())

        var armed = 0
        for (holiday in holidays) {
            if (alreadyArmed + armed >= MAX_ALARMS) break
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
            trackedRequestCodes += requestCode
            armed++
        }
        return armed
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

    private fun cancelAll(alarmManager: AlarmManager) {
        trackedRequestCodes.forEach { code ->
            val intent = Intent(context, ReminderReceiver::class.java)
            PendingIntent.getBroadcast(
                context, code, intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
            )?.let {
                alarmManager.cancel(it)
                it.cancel()
            }
        }
        trackedRequestCodes.clear()
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

        /**
         * Which alarms are currently armed.
         *
         * Held in memory rather than persisted because [rescheduleAll] runs on every process
         * start, on boot and daily, so a lost set costs one duplicate cancel-and-rearm cycle
         * rather than a stuck alarm.
         */
        private val trackedRequestCodes = mutableSetOf<Int>()
    }
}
