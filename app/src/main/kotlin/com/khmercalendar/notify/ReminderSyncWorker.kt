package com.khmercalendar.notify

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.khmercalendar.KhmerCalendarApp
import java.util.concurrent.TimeUnit

/**
 * Re-arms the rolling reminder window once a day.
 *
 * [ReminderScheduler] only holds alarms for the next couple of weeks, so something has to
 * extend the window as time passes. WorkManager rather than a repeating alarm because it
 * survives reboots and app updates by itself and respects Doze, which a self-rescheduling
 * alarm chain does not.
 */
class ReminderSyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? KhmerCalendarApp ?: return Result.success()
        return try {
            app.container.reminderScheduler.rescheduleAll()
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val NAME = "reminder_sync"

        fun enqueue(context: Context) {
            val request = PeriodicWorkRequestBuilder<ReminderSyncWorker>(1, TimeUnit.DAYS)
                .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(false).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}
