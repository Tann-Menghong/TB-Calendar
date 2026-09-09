package com.khmercalendar.notify

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import com.khmercalendar.MainActivity
import com.khmercalendar.R
import com.khmercalendar.domain.FocusKind

/**
 * The one alarm a focus session needs.
 *
 * ## Why an alarm and not a service
 *
 * A foreground service ticking once a second is how timer apps drain batteries and get killed
 * by aggressive OEM power management - which on the phones this app targets is the norm, not
 * the exception. A session is a start time and a length in the database; the only thing that
 * has to happen while the app is away is a single notification at the end, and that is
 * exactly what one alarm does.
 *
 * ## Why it is not an exact alarm
 *
 * `setExactAndAllowWhileIdle` needs a permission the user may have refused, and a focus timer
 * is not a medical reminder: arriving within a minute or two of the end is fine, and quietly
 * degrading beats asking for another permission. Event reminders keep the exact path because
 * being late for a meeting is a different kind of wrong.
 */
object FocusAlarm {

    /** Its own request code space, so it can never collide with an event reminder's. */
    private const val REQUEST_CODE = 990_001

    fun schedule(context: Context, kind: FocusKind, endsAtMillis: Long) {
        val alarmManager = context.getSystemService<AlarmManager>() ?: return
        val pending = pendingIntent(context, kind, PendingIntent.FLAG_UPDATE_CURRENT)
        runCatching {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endsAtMillis, pending)
        }
    }

    /** Cancels the pending end-of-session alarm, and any notification it already posted. */
    fun cancel(context: Context) {
        val alarmManager = context.getSystemService<AlarmManager>()
        val pending = pendingIntent(context, FocusKind.FOCUS, PendingIntent.FLAG_UPDATE_CURRENT)
        runCatching { alarmManager?.cancel(pending) }
        runCatching { context.getSystemService<NotificationManager>()?.cancel(REQUEST_CODE) }
    }

    private fun pendingIntent(context: Context, kind: FocusKind, flags: Int): PendingIntent {
        val intent = Intent(context, FocusAlarmReceiver::class.java).apply {
            action = FocusAlarmReceiver.ACTION_FOCUS_ENDED
            putExtra(FocusAlarmReceiver.EXTRA_KIND, kind.stored)
        }
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            flags or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    internal fun notificationId(): Int = REQUEST_CODE
}

/**
 * Says the session is over.
 *
 * Deliberately does not start the next one. The method is focus, then a break you actually
 * take - and an app that silently starts a break timer while the user is still typing has
 * made the break a fiction. Tapping the notification opens the app, where the next session
 * is one button.
 */
class FocusAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_FOCUS_ENDED) return
        val kind = FocusKind.of(intent.getStringExtra(EXTRA_KIND))

        val open = PendingIntent.getActivity(
            context,
            FocusAlarm.notificationId(),
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val title = if (kind.isBreak) "សម្រាកបានចប់" else "អស់ពេលផ្តោតអារម្មណ៍"
        val body = if (kind.isBreak) "ត្រឡប់ទៅធ្វើការវិញ" else "សម្រាកបន្តិចទៅ"

        val notification = NotificationCompat.Builder(context, NotificationChannels.REMINDERS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()

        // POST_NOTIFICATIONS can be revoked at any time and the platform throws rather than
        // dropping quietly; an uncaught throw here would crash a background process.
        runCatching {
            context.getSystemService<NotificationManager>()
                ?.notify(FocusAlarm.notificationId(), notification)
        }
    }

    companion object {
        const val ACTION_FOCUS_ENDED = "com.khmercalendar.action.FOCUS_ENDED"
        const val EXTRA_KIND = "kind"
    }
}
