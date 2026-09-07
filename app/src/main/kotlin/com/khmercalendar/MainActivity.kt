package com.khmercalendar

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.khmercalendar.ui.KhmerCalendarNavHost
import com.khmercalendar.ui.lock.LockScreen
import com.khmercalendar.ui.theme.KhmerCalendarTheme
import java.time.LocalDate

/**
 * The only activity.
 *
 * Everything above it is Compose behind a single window, which keeps navigation state, the
 * theme and the app lock in one place. The activity's other job is translating the intents
 * that arrive from outside - a reminder notification, a home-screen widget, a launcher
 * shortcut, an `.ics` file - into a destination.
 */
class MainActivity : ComponentActivity() {

    private var pendingDestination by mutableStateOf<ExternalDestination?>(null)

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* Declining is allowed. The calendar works; reminders simply stay in-app. */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val container = (application as KhmerCalendarApp).container
        pendingDestination = destinationFrom(intent)

        setContent {
            val settings by container.settings.collectAsStateWithLifecycle()

            KhmerCalendarTheme(settings) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    // Re-keyed on the lock setting so turning the lock on takes effect at
                    // once, and turning it off does not leave the user staring at a keypad.
                    var unlocked by remember(settings.appLockEnabled, settings.pinHash) {
                        mutableStateOf(!settings.appLockEnabled || settings.pinHash == null)
                    }
                    if (!unlocked) {
                        LockScreen(
                            settings = settings,
                            settingsStore = container.settingsStore,
                            onUnlocked = { unlocked = true },
                        )
                    } else {
                        KhmerCalendarNavHost(
                            container = container,
                            external = pendingDestination,
                            onExternalConsumed = { pendingDestination = null },
                        )
                    }
                }
            }
        }

        requestNotificationPermissionIfNeeded()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingDestination = destinationFrom(intent)
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun destinationFrom(intent: Intent?): ExternalDestination? {
        if (intent == null) return null
        return when (intent.action) {
            ACTION_VIEW_EVENT -> intent.getLongExtra(EXTRA_EVENT_ID, 0L)
                .takeIf { it > 0L }
                ?.let { ExternalDestination.Event(it, dateExtra(intent)) }

            ACTION_VIEW_DAY -> ExternalDestination.Day(dateExtra(intent) ?: LocalDate.now())
            ACTION_NEW_EVENT -> ExternalDestination.NewEvent(dateExtra(intent))
            ACTION_ASSISTANT -> ExternalDestination.Assistant

            // Text shared in from another app - a message, an email, a notice. This is the
            // realistic way a long announcement reaches the calendar: the user long-presses
            // it where they read it rather than retyping it here.
            Intent.ACTION_SEND, Intent.ACTION_PROCESS_TEXT ->
                sharedText(intent)?.let { ExternalDestination.TextToEvent(it) }

            Intent.ACTION_VIEW -> intent.data?.let { ExternalDestination.ImportIcs(it.toString()) }
            else -> null
        }
    }

    /** Text arrives under different extras depending on which share surface sent it. */
    private fun sharedText(intent: Intent): String? = sequenceOf(
        Intent.EXTRA_TEXT,
        "android.intent.extra.PROCESS_TEXT",
        Intent.EXTRA_SUBJECT,
    ).mapNotNull { intent.getCharSequenceExtra(it)?.toString() }
        .firstOrNull { it.isNotBlank() }
        ?.take(MAX_SHARED_TEXT)

    private fun dateExtra(intent: Intent): LocalDate? =
        intent.getStringExtra(EXTRA_DATE)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    companion object {
        const val ACTION_VIEW_EVENT = "com.khmercalendar.action.VIEW_EVENT"
        const val ACTION_VIEW_DAY = "com.khmercalendar.action.VIEW_DAY"
        const val ACTION_NEW_EVENT = "com.khmercalendar.action.NEW_EVENT"
        const val ACTION_ASSISTANT = "com.khmercalendar.action.ASSISTANT"

        /** Beyond this, a share is not an announcement, it is a document. */
        private const val MAX_SHARED_TEXT = 20_000

        const val EXTRA_EVENT_ID = "eventId"
        const val EXTRA_DATE = "date"

        /** Opens straight to one event. Used by the reminder notification. */
        fun eventIntent(context: Context, eventId: Long, date: LocalDate? = null): Intent =
            base(context, ACTION_VIEW_EVENT)
                .putExtra(EXTRA_EVENT_ID, eventId)
                .apply { date?.let { putExtra(EXTRA_DATE, it.toString()) } }

        /** Opens the day view. Used by the month widget. */
        fun dayIntent(context: Context, date: LocalDate): Intent =
            base(context, ACTION_VIEW_DAY).putExtra(EXTRA_DATE, date.toString())

        /** Opens the add-event form. Used by the widget quick-add button and the shortcut. */
        fun newEventIntent(context: Context, date: LocalDate? = null): Intent =
            base(context, ACTION_NEW_EVENT)
                .apply { date?.let { putExtra(EXTRA_DATE, it.toString()) } }

        fun assistantIntent(context: Context): Intent = base(context, ACTION_ASSISTANT)

        private fun base(context: Context, action: String): Intent =
            Intent(context, MainActivity::class.java).setAction(action).addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP,
            )
    }
}

/** A destination asked for from outside the app. */
sealed interface ExternalDestination {
    data class Event(val id: Long, val date: LocalDate?) : ExternalDestination
    data class Day(val date: LocalDate) : ExternalDestination
    data class NewEvent(val date: LocalDate?) : ExternalDestination
    data class ImportIcs(val uri: String) : ExternalDestination
    data object Assistant : ExternalDestination

    /** Text shared in from another app, to be turned into an event. */
    data class TextToEvent(val text: String) : ExternalDestination
}
