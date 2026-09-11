package com.khmercalendar.notify

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import com.khmercalendar.KhmerCalendarApp
import com.khmercalendar.MainActivity
import com.khmercalendar.R
import com.khmercalendar.data.prefs.TimeFormat
import com.khmercalendar.ui.components.CalendarFormats
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Shows an event reminder, and handles the actions on it.
 *
 * Everything needed to *draw* the notification travels in the intent rather than being read
 * back from the database, so a reminder can always be drawn even if the database cannot be
 * reached in time.
 *
 * ## Why it checks before posting
 *
 * An alarm is armed up to two weeks ahead, and a great deal can change in two weeks: the event
 * moves, is deleted, or - for a task - gets done. Re-arming cancels the old alarms now, but
 * alarms armed before 2.5.0 could never be cancelled at all, and the moment between an edit and
 * the next re-arm exists regardless. So before posting, one read confirms that the event still
 * exists, still starts at the instant the alarm was armed for, and is not a task already done.
 *
 * It fails open. If the check throws or takes longer than [CHECK_TIMEOUT_MS], the reminder is
 * shown anyway: a reminder for something that changed is a nuisance the user can dismiss, and
 * a reminder that never appears is a missed appointment nobody finds out about.
 */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_SNOOZE -> handleSnooze(context, intent)
            ACTION_COMPLETE -> handleComplete(context, intent)
            else -> handleReminder(context, intent)
        }
    }

    private fun handleReminder(context: Context, intent: Intent) {
        val app = context.applicationContext as? KhmerCalendarApp
        val eventId = intent.getLongExtra(EXTRA_EVENT_ID, -1L)
        val isHoliday = intent.getLongExtra(EXTRA_HOLIDAY_EPOCH_DAY, -1L) >= 0
        if (isHoliday || eventId <= 0 || app == null) {
            showReminder(context, intent)
            return
        }

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val stillDue = withTimeoutOrNull(CHECK_TIMEOUT_MS) { isStillDue(app, intent) } ?: true
                if (stillDue) showReminder(context, intent)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Could not confirm reminder for event $eventId; showing it anyway", e)
                showReminder(context, intent)
            } finally {
                pendingResult.finish()
            }
        }
    }

    /**
     * Whether the occurrence this alarm was armed for is still there and still wanted.
     *
     * Matched on the start *instant*, not only the day: an event moved from ten o'clock to three
     * on the same day keeps its request code, and its ten o'clock alarm must not ring for it.
     */
    private suspend fun isStillDue(app: KhmerCalendarApp, intent: Intent): Boolean {
        val eventId = intent.getLongExtra(EXTRA_EVENT_ID, -1L)
        val startMillis = intent.getLongExtra(EXTRA_START, 0L)
        val zone = ZoneId.systemDefault()
        val day = occurrenceDay(intent, zone)

        val occurrence = app.container.eventRepository.occurrences(day, day)[day].orEmpty()
            .firstOrNull {
                it.eventId == eventId && it.start.atZone(zone).toInstant().toEpochMilli() == startMillis
            }
            // Deleted, moved, or this date removed from its series.
            ?: return false

        return !(occurrence.isTask && occurrence.isCompleted)
    }

    private fun showReminder(context: Context, intent: Intent) {
        val app = context.applicationContext as? KhmerCalendarApp
        val settings = app?.container?.cachedSettings

        if (settings?.notificationsEnabled == false) return

        val eventId = intent.getLongExtra(EXTRA_EVENT_ID, -1L)
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        val location = intent.getStringExtra(EXTRA_LOCATION)
        val startMillis = intent.getLongExtra(EXTRA_START, 0L)
        val allDay = intent.getBooleanExtra(EXTRA_ALL_DAY, false)
        val requestCode = intent.getIntExtra(EXTRA_REQUEST_CODE, eventId.toInt())

        // A holiday reminder has no event behind it, so it opens the day rather than an
        // event that does not exist, and carries no snooze or complete action.
        val holidayEpochDay = intent.getLongExtra(EXTRA_HOLIDAY_EPOCH_DAY, -1L)
        val isHoliday = holidayEpochDay >= 0
        // Checked here as well as when arming: holiday notices armed before 2.5.0 could not be
        // cancelled, so switching them off used to leave two weeks of them still to come.
        if (isHoliday && settings?.holidayNotifications == false) return

        val channelId = NotificationChannels.ensure(
            context,
            settings?.notificationSoundUri,
            settings?.notificationVibrate ?: true,
        )

        val start = Instant.ofEpochMilli(startMillis).atZone(ZoneId.systemDefault())
        val timeText = if (allDay) {
            "ពេញមួយថ្ងៃ"
        } else {
            CalendarFormats.time(
                time = start.toLocalTime(),
                use24Hour = CalendarFormats.uses24Hour(
                    context,
                    settings?.timeFormat ?: TimeFormat.SYSTEM,
                ),
                khmerNumerals = settings?.useKhmerNumerals ?: true,
            )
        }
        val body = intent.getStringExtra(EXTRA_BODY)
            ?: listOfNotNull(timeText, location?.takeIf { it.isNotBlank() }).joinToString(" · ")

        val openIntent = PendingIntent.getActivity(
            context,
            requestCode,
            if (isHoliday) {
                MainActivity.dayIntent(context, LocalDate.ofEpochDay(holidayEpochDay))
            } else {
                // With the occurrence's own date. Without one the detail screen opened on today, so
                // "done" pressed there after tapping tomorrow's reminder ticked today instead.
                MainActivity.eventIntent(context, eventId, occurrenceDay(intent, ZoneId.systemDefault()))
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(openIntent)
            .apply {
                if (!isHoliday) {
                    addAction(
                        0,
                        "ពន្យារ ១០ នាទី",
                        actionIntent(context, ACTION_SNOOZE, intent, requestCode + SNOOZE_OFFSET),
                    )
                    // "Done" belongs to tasks. It was offered on every reminder, and on an
                    // ordinary event it marked the whole series complete - after which that
                    // meeting stopped being reminded at all and dropped off the dashboard.
                    if (intent.getBooleanExtra(EXTRA_IS_TASK, false)) {
                        addAction(
                            0,
                            "រួចរាល់",
                            actionIntent(context, ACTION_COMPLETE, intent, requestCode + COMPLETE_OFFSET),
                        )
                    }
                }
            }
            .build()

        // POST_NOTIFICATIONS can be revoked at any time; the platform throws rather than
        // silently dropping, and an uncaught throw here would crash a background process.
        runCatching {
            context.getSystemService<NotificationManager>()?.notify(requestCode, notification)
        }
    }

    private fun actionIntent(context: Context, action: String, source: Intent, code: Int): PendingIntent {
        val intent = Intent(source).apply {
            setClass(context, ReminderReceiver::class.java)
            this.action = action
            putExtra(EXTRA_REQUEST_CODE, source.getIntExtra(EXTRA_REQUEST_CODE, code))
        }
        return PendingIntent.getBroadcast(
            context, code, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun handleSnooze(context: Context, intent: Intent) {
        val requestCode = intent.getIntExtra(EXTRA_REQUEST_CODE, 0)
        context.getSystemService<NotificationManager>()?.cancel(requestCode)

        val alarmManager = context.getSystemService<android.app.AlarmManager>() ?: return
        val fireAt = System.currentTimeMillis() + SNOOZE_MINUTES * 60_000L
        val next = Intent(intent).apply {
            setClass(context, ReminderReceiver::class.java)
            action = null
            // Its own address. Re-arming cancels every alarm it armed, and this one was asked for
            // by the user rather than armed by the calendar; sharing the original's address made
            // it indistinguishable, and saving any event within ten minutes swallowed the snooze.
            data = Uri.parse("khmercalendar://snooze/$requestCode")
        }
        val pending = PendingIntent.getBroadcast(
            context, requestCode, next,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                alarmManager.setAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, fireAt, pending)
            } else {
                alarmManager.setExactAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, fireAt, pending)
            }
        }
    }

    /**
     * Ticks off the one occurrence the reminder was for.
     *
     * Not the series: a daily chore done today is still due tomorrow. And then re-arms, so any
     * other reminder for the same occurrence - an hour before and ten minutes before - does not
     * ring for something already finished.
     */
    private fun handleComplete(context: Context, intent: Intent) {
        val requestCode = intent.getIntExtra(EXTRA_REQUEST_CODE, 0)
        val eventId = intent.getLongExtra(EXTRA_EVENT_ID, -1L)
        context.getSystemService<NotificationManager>()?.cancel(requestCode)
        if (eventId <= 0) return

        val app = context.applicationContext as? KhmerCalendarApp ?: return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repository = app.container.eventRepository
                // A notification posted by an older version carries no task flag; ask the row.
                val isTask = if (intent.hasExtra(EXTRA_IS_TASK)) {
                    intent.getBooleanExtra(EXTRA_IS_TASK, false)
                } else {
                    repository.event(eventId)?.isTask == true
                }
                if (isTask) {
                    repository.setCompleted(eventId, occurrenceDay(intent, ZoneId.systemDefault()), true)
                    app.container.reminderScheduler.rescheduleAll()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Completing event $eventId from its reminder failed", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun occurrenceDay(intent: Intent, zone: ZoneId): LocalDate {
        val stored = intent.getLongExtra(EXTRA_OCCURRENCE_DAY, -1L)
        if (stored >= 0) return LocalDate.ofEpochDay(stored)
        return Instant.ofEpochMilli(intent.getLongExtra(EXTRA_START, 0L)).atZone(zone).toLocalDate()
    }

    companion object {
        const val ACTION_SNOOZE = "com.khmercalendar.action.SNOOZE"
        const val ACTION_COMPLETE = "com.khmercalendar.action.COMPLETE"

        private const val TAG = "ReminderReceiver"

        private const val EXTRA_EVENT_ID = "event_id"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_LOCATION = "location"
        private const val EXTRA_START = "start"
        private const val EXTRA_ALL_DAY = "all_day"
        private const val EXTRA_REQUEST_CODE = "request_code"
        private const val EXTRA_HOLIDAY_EPOCH_DAY = "holiday_epoch_day"
        private const val EXTRA_BODY = "body"
        private const val EXTRA_IS_TASK = "is_task"
        private const val EXTRA_OCCURRENCE_DAY = "occurrence_day"

        private const val SNOOZE_MINUTES = 10
        private const val SNOOZE_OFFSET = 1_000_000
        private const val COMPLETE_OFFSET = 2_000_000

        /** Well inside the ten seconds `goAsync` allows, with room to post afterwards. */
        private const val CHECK_TIMEOUT_MS = 4_000L

        fun intent(
            context: Context,
            eventId: Long,
            title: String,
            location: String?,
            startMillis: Long,
            allDay: Boolean,
            minutesBefore: Int,
            requestCode: Int,
            isTask: Boolean,
            occurrenceEpochDay: Long,
        ): Intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra(EXTRA_EVENT_ID, eventId)
            putExtra(EXTRA_TITLE, title)
            putExtra(EXTRA_LOCATION, location)
            putExtra(EXTRA_START, startMillis)
            putExtra(EXTRA_ALL_DAY, allDay)
            putExtra(EXTRA_REQUEST_CODE, requestCode)
            putExtra(EXTRA_IS_TASK, isTask)
            putExtra(EXTRA_OCCURRENCE_DAY, occurrenceEpochDay)
            // Distinct data keeps PendingIntents for the same event but different lead times
            // from collapsing into one another. It is also half of what cancelling needs - see
            // [AlarmRegistry].
            data = Uri.parse("khmercalendar://reminder/$eventId/$minutesBefore/$requestCode")
        }

        /**
         * A reminder that a public holiday is coming.
         *
         * Shares this receiver rather than adding a second one: the notification, the
         * channel and the "did the user turn notifications off" check are identical, and
         * only the tap target and the absence of actions differ.
         */
        fun holidayIntent(
            context: Context,
            nameKm: String,
            date: LocalDate,
            bodyKm: String,
            requestCode: Int,
        ): Intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra(EXTRA_EVENT_ID, -1L)
            putExtra(EXTRA_TITLE, nameKm)
            putExtra(EXTRA_BODY, bodyKm)
            putExtra(EXTRA_ALL_DAY, true)
            putExtra(EXTRA_REQUEST_CODE, requestCode)
            putExtra(EXTRA_HOLIDAY_EPOCH_DAY, date.toEpochDay())
            data = Uri.parse("khmercalendar://holiday/${date.toEpochDay()}")
        }
    }
}
