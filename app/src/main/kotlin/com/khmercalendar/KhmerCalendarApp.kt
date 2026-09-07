package com.khmercalendar

import android.app.Application
import androidx.work.Configuration
import androidx.core.app.NotificationCompat
import com.khmercalendar.ai.model.ModelDownloadWorker
import com.khmercalendar.ai.model.foregroundInfoCompat
import com.khmercalendar.core.khmer.KhmerNumerals
import com.khmercalendar.notify.NotificationChannels
import com.khmercalendar.notify.ReminderSyncWorker
import com.khmercalendar.widget.refreshWidgets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class KhmerCalendarApp : Application(), Configuration.Provider {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * WorkManager is started on demand rather than by its androidx.startup initializer, which
     * the manifest removes. Nothing this app schedules needs to exist before the user has
     * opened it once, and skipping the initializer keeps a cold start off the database.
     */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(if (BuildConfig.DEBUG) android.util.Log.DEBUG else android.util.Log.ERROR)
            .build()

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this, appScope, BuildConfig.VERSION_NAME)

        // The download worker lives in :ai-core, which has no resources of its own; the
        // notification it shows is supplied from here.
        ModelDownloadWorker.notificationFactory = { context, spec, percent ->
            NotificationChannels.ensure(context, null, false)
            val notification = NotificationCompat.Builder(context, NotificationChannels.DOWNLOADS)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("កំពុងទាញយក ${spec.displayName}")
                .setContentText("${KhmerNumerals.toKhmer(percent)}%")
                .setProgress(100, percent, percent == 0)
                .setOngoing(true)
                .setSilent(true)
                .build()
            foregroundInfoCompat(DOWNLOAD_NOTIFICATION_ID, notification)
        }

        appScope.launch {
            // Arming reminders touches the database, so it stays off the main thread and off
            // the startup path; a cold start should draw the calendar, not wait on alarms.
            runCatching { container.reminderScheduler.rescheduleAll() }
            ReminderSyncWorker.enqueue(this@KhmerCalendarApp)
        }

        observeDataForWidgets()
        observeWorkSchedule()
    }

    /**
     * Redraws the home-screen widgets when calendar data changes.
     *
     * Watching the tables rather than hooking each write path means a save, a delete, an
     * .ics import and a restore all reach the widgets, and none of them has to remember to.
     * The debounce collapses the burst of table notifications a bulk import produces into a
     * single redraw.
     */
    @OptIn(kotlinx.coroutines.FlowPreview::class)
    private fun observeDataForWidgets() {
        appScope.launch {
            runCatching {
                container.database.invalidationTracker
                    .createFlow("events", "event_exceptions", "day_notes", emitInitialState = false)
                    .debounce(WIDGET_REFRESH_DEBOUNCE_MS)
                    .collect { refreshWidgets(this@KhmerCalendarApp) }
            }
        }
    }

    /**
     * Re-arms the work-shift notices when the schedule or its settings change.
     *
     * Without this they were armed only at process start and by the daily worker, so turning
     * notifications on, editing a shift, or changing the warning time did nothing at all until
     * the app was next launched - a setting that silently takes effect tomorrow reads as a
     * setting that does not work.
     *
     * Watching the settings rather than calling the scheduler from each screen means the
     * settings UI, a restored backup and anything added later all reach it, and none of them
     * has to remember to. Only the fields that affect an alarm are compared, so unrelated
     * preference writes - a theme change, a font size - do not re-arm anything.
     */
    private fun observeWorkSchedule() {
        appScope.launch {
            runCatching {
                container.settings
                    .map {
                        WorkAlarmInputs(
                            schedule = it.workSchedule,
                            notify = it.workNotifications,
                            enabled = it.notificationsEnabled,
                            lead = it.workNotifyLeadMinutes,
                        )
                    }
                    .distinctUntilChanged()
                    .drop(1) // The first value is the one onCreate already armed.
                    .collect {
                        runCatching { container.reminderScheduler.rescheduleAll() }
                    }
            }
        }
    }

    private data class WorkAlarmInputs(
        val schedule: com.khmercalendar.core.work.WorkSchedule,
        val notify: Boolean,
        val enabled: Boolean,
        val lead: Int,
    )

    private companion object {
        const val DOWNLOAD_NOTIFICATION_ID = 4201
        const val WIDGET_REFRESH_DEBOUNCE_MS = 400L
    }
}
