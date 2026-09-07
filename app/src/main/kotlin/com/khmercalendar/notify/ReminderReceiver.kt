package com.khmercalendar.notify

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import com.khmercalendar.KhmerCalendarApp
import com.khmercalendar.MainActivity
import com.khmercalendar.R
import com.khmercalendar.core.khmer.KhmerNumerals
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import com.khmercalendar.ui.components.CalendarFormats
import com.khmercalendar.data.prefs.TimeFormat

/**
 * Shows an event reminder, and handles the actions on it.
 *
 * Everything needed to draw the notification travels in the intent rather than being read
 * back from the database. A broadcast receiver has a few seconds before the system may kill
 * the process, and a reminder that fails to appear because a disk read was slow is the one
 * failure this feature cannot afford.
 */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_SNOOZE -> handleSnooze(context, intent)
            ACTION_COMPLETE -> handleComplete(context, intent)
            else -> showReminder(context, intent)
        }
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

        // A holiday reminder has no event behind it, so it opens the day rather than an
        // event that does not exist, and carries no snooze or complete action.
        val holidayEpochDay = intent.getLongExtra(EXTRA_HOLIDAY_EPOCH_DAY, -1L)
        val isHoliday = holidayEpochDay >= 0

        val openIntent = PendingIntent.getActivity(
            context,
            requestCode,
            if (isHoliday) {
                MainActivity.dayIntent(context, LocalDate.ofEpochDay(holidayEpochDay))
            } else {
                MainActivity.eventIntent(context, eventId)
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
                    addAction(
                        0,
                        "រួចរាល់",
                        actionIntent(context, ACTION_COMPLETE, intent, requestCode + COMPLETE_OFFSET),
                    )
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

    private fun handleComplete(context: Context, intent: Intent) {
        val requestCode = intent.getIntExtra(EXTRA_REQUEST_CODE, 0)
        val eventId = intent.getLongExtra(EXTRA_EVENT_ID, -1L)
        context.getSystemService<NotificationManager>()?.cancel(requestCode)
        if (eventId <= 0) return

        val app = context.applicationContext as? KhmerCalendarApp ?: return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                app.container.eventRepository.setCompleted(eventId, true)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_SNOOZE = "com.khmercalendar.action.SNOOZE"
        const val ACTION_COMPLETE = "com.khmercalendar.action.COMPLETE"

        private const val EXTRA_EVENT_ID = "event_id"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_LOCATION = "location"
        private const val EXTRA_START = "start"
        private const val EXTRA_ALL_DAY = "all_day"
        private const val EXTRA_REQUEST_CODE = "request_code"
        private const val EXTRA_HOLIDAY_EPOCH_DAY = "holiday_epoch_day"
        private const val EXTRA_BODY = "body"

        private const val SNOOZE_MINUTES = 10
        private const val SNOOZE_OFFSET = 1_000_000
        private const val COMPLETE_OFFSET = 2_000_000

        fun intent(
            context: Context,
            eventId: Long,
            title: String,
            location: String?,
            startMillis: Long,
            allDay: Boolean,
            minutesBefore: Int,
            requestCode: Int,
        ): Intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra(EXTRA_EVENT_ID, eventId)
            putExtra(EXTRA_TITLE, title)
            putExtra(EXTRA_LOCATION, location)
            putExtra(EXTRA_START, startMillis)
            putExtra(EXTRA_ALL_DAY, allDay)
            putExtra(EXTRA_REQUEST_CODE, requestCode)
            // Distinct data keeps PendingIntents for the same event but different lead times
            // from collapsing into one another.
            data = android.net.Uri.parse("khmercalendar://reminder/$eventId/$minutesBefore/$requestCode")
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
            data = android.net.Uri.parse("khmercalendar://holiday/${date.toEpochDay()}")
        }
    }
}
