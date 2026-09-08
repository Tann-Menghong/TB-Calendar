package com.khmercalendar.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.khmercalendar.KhmerCalendarApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Rebuilds the alarm set after events that silently clear it.
 *
 * A reboot drops every alarm the app holds. A timezone change moves every wall-clock time
 * the alarms were computed from. Both leave reminders that will never fire, with nothing on
 * screen to say so, so both have to re-arm.
 */
class SystemEventReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_TIME_CHANGED,
            "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED",
            -> Unit

            else -> return
        }

        val app = context.applicationContext as? KhmerCalendarApp ?: return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Both are reported rather than thrown. This scope has no exception handler,
                // and it could not usefully have one: an exception escaping a launch here
                // reaches the thread's default handler and takes the process with it - at
                // boot, before the user has opened anything.
                app.container.reminderScheduler.rescheduleAll()
                    .onFailure { Log.e(TAG, "Re-arming after ${intent.action} failed", it) }
                runCatching { ReminderSyncWorker.enqueue(context) }
                    .onFailure { Log.e(TAG, "Could not enqueue the daily re-arm", it) }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private companion object {
        const val TAG = "SystemEventReceiver"
    }
}
