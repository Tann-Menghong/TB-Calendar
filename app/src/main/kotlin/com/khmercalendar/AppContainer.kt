package com.khmercalendar

import android.content.Context
import com.khmercalendar.ai.AiAssistant
import com.khmercalendar.ai.AiEngine
import com.khmercalendar.ai.engine.AiEngineFactory
import com.khmercalendar.ai.model.ModelStore
import com.khmercalendar.ai.tools.AiEventView
import com.khmercalendar.ai.tools.CalendarQuery
import com.khmercalendar.data.backup.BackupManager
import com.khmercalendar.data.db.KhmerCalendarDatabase
import com.khmercalendar.data.prefs.AppSettings
import com.khmercalendar.data.prefs.SettingsStore
import com.khmercalendar.data.repo.EventRepository
import com.khmercalendar.notify.ReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDateTime

/**
 * Manual dependency wiring.
 *
 * Deliberately not Hilt. Room already pulls in KSP, and a second annotation processor buys
 * nothing here: the graph is a dozen singletons created once, constructor injection is used
 * throughout anyway, and skipping the generated component keeps cold start measurably
 * shorter on the low-end devices this app targets. If the graph ever grows scopes beyond
 * "application", swapping to Hilt is mechanical - every class already takes its
 * collaborators as constructor parameters.
 */
class AppContainer(
    private val context: Context,
    private val scope: CoroutineScope,
    appVersion: String,
) {

    /** The application context, for the few screens that need one outside composition. */
    val appContext: Context get() = context

    val database: KhmerCalendarDatabase by lazy { KhmerCalendarDatabase.get(context, scope) }

    val settingsStore: SettingsStore by lazy { SettingsStore(context) }

    val eventRepository: EventRepository by lazy {
        EventRepository(
            eventDao = database.eventDao(),
            categoryDao = database.categoryDao(),
            reminderDao = database.reminderDao(),
            exceptionDao = database.eventExceptionDao(),
            noteDao = database.dayNoteDao(),
        )
    }

    val reminderScheduler: ReminderScheduler by lazy {
        ReminderScheduler(context, eventRepository, settingsStore)
    }

    val backupManager: BackupManager by lazy { BackupManager(context, database, appVersion) }

    val modelStore: ModelStore by lazy { ModelStore(context) }

    /**
     * The inference backend.
     *
     * Created eagerly but *not* loaded: constructing it costs nothing, while loading a model
     * costs hundreds of megabytes of resident memory and only happens when the user asks.
     */
    val aiEngine: AiEngine by lazy { AiEngineFactory.create(context) }

    val aiAssistant: AiAssistant by lazy {
        AiAssistant(engine = aiEngine, calendar = repositoryAsCalendarQuery())
    }

    /**
     * The current settings, kept hot.
     *
     * A broadcast receiver has no lifecycle to collect a Flow in, and reading DataStore from
     * one risks the process being killed mid-read. Holding the latest value means the
     * reminder receiver can consult notification preferences synchronously.
     */
    val settings: StateFlow<AppSettings> =
        settingsStore.settings.stateIn(scope, SharingStarted.Eagerly, AppSettings())

    val cachedSettings: AppSettings get() = settings.value

    private fun repositoryAsCalendarQuery(): CalendarQuery = object : CalendarQuery {
        override suspend fun eventsBetween(from: LocalDateTime, to: LocalDateTime): List<AiEventView> {
            val days = java.time.Duration.between(from, to).toDays().toInt().coerceIn(1, 400)
            return eventRepository.upcoming(from, days)
                .filter { !it.start.isAfter(to) }
                .map {
                    AiEventView(
                        id = it.eventId,
                        title = it.title,
                        start = it.start,
                        end = it.end,
                        allDay = it.allDay,
                        location = it.location,
                        categoryName = it.categoryName,
                    )
                }
        }
    }
}
