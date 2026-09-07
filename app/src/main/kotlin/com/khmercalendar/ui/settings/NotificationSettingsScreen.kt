package com.khmercalendar.ui.settings

import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.IntentCompat
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.khmercalendar.data.prefs.AppSettings
import com.khmercalendar.data.prefs.SettingsStore
import com.khmercalendar.notify.ReminderScheduler
import com.khmercalendar.ui.components.localeNumber
import kotlinx.coroutines.launch

/**
 * Notification preferences.
 *
 * Changing the sound or the vibration pattern rewrites the notification channel, and rearming
 * is what makes an existing reminder pick up the new default, so both are done here rather
 * than left until the next reminder happens to fire.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationSettingsScreen(
    settings: AppSettings,
    settingsStore: SettingsStore,
    scheduler: ReminderScheduler,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var canScheduleExact by remember { mutableStateOf(scheduler.canScheduleExactAlarms()) }

    // The user leaves the app to grant the exact-alarm privilege, so the state is re-read
    // when they come back rather than cached from when the screen opened.
    LifecycleResumeEffect(Unit) {
        canScheduleExact = scheduler.canScheduleExactAlarms()
        onPauseOrDispose { }
    }

    val pickSound = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri: Uri? = result.data?.let {
                IntentCompat.getParcelableExtra(
                    it,
                    RingtoneManager.EXTRA_RINGTONE_PICKED_URI,
                    Uri::class.java,
                )
            }
            scope.launch {
                settingsStore.setNotificationSound(uri?.toString())
                scheduler.rescheduleAll()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ការជូនដំណឹង") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "ត្រឡប់")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()),
        ) {
            if (!canScheduleExact) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            "ការរំលឹកនឹងមកដល់យឺតបន្តិច",
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            "ប្រព័ន្ធមិនអនុញ្ញាតឱ្យកំណត់ម៉ោងជាក់លាក់ទេ។ " +
                                "ការរំលឹកនៅតែដំណើរការ ប៉ុន្តែអាចយឺតពីរបីនាទី។",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            TextButton(
                                onClick = {
                                    runCatching {
                                        context.startActivity(
                                            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                                                .setData(Uri.parse("package:${context.packageName}")),
                                        )
                                    }
                                },
                            ) { Text("បើកការអនុញ្ញាត") }
                        }
                    }
                }
            }

            SettingsGroup("ការរំលឹក") {
                SwitchRow(
                    title = "បើកការជូនដំណឹង",
                    checked = settings.notificationsEnabled,
                    onChange = {
                        scope.launch {
                            settingsStore.setNotificationsEnabled(it)
                            scheduler.rescheduleAll()
                        }
                    },
                )
                SwitchRow(
                    title = "ញ័រ",
                    checked = settings.notificationVibrate,
                    enabled = settings.notificationsEnabled,
                    onChange = { scope.launch { settingsStore.setVibrate(it) } },
                )
                SettingsRow(
                    title = "សំឡេងជូនដំណឹង",
                    subtitle = settings.notificationSoundUri?.let { "ផ្ទាល់ខ្លួន" } ?: "លំនាំដើមរបស់ប្រព័ន្ធ",
                    onClick = {
                        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                            .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION)
                            .putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "សំឡេងជូនដំណឹង")
                            .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
                            .putExtra(
                                RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
                                settings.notificationSoundUri?.let(Uri::parse),
                            )
                        runCatching { pickSound.launch(intent) }
                    },
                    trailing = {
                        if (settings.notificationSoundUri != null) {
                            TextButton(
                                onClick = {
                                    scope.launch { settingsStore.setNotificationSound(null) }
                                },
                            ) { Text("លំនាំដើម") }
                        }
                    },
                )
                SwitchRow(
                    title = "ជូនដំណឹងអំពីបុណ្យជាតិ",
                    subtitle = "រំលឹកមួយថ្ងៃមុនបុណ្យនីមួយៗ",
                    checked = settings.holidayNotifications,
                    onChange = {
                        scope.launch {
                            settingsStore.setHolidayNotifications(it)
                            scheduler.rescheduleAll()
                        }
                    },
                )
            }

            HorizontalDivider()

            SettingsGroup("តម្លៃលំនាំដើម") {
                Text(
                    "រំលឹកមុនព្រឹត្តិការណ៍",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(start = 16.dp, top = 8.dp),
                )
                listOf(0, 5, 10, 15, 30, 60, 1440).forEach { minutes ->
                    ChoiceRow(
                        title = reminderLabel(minutes),
                        selected = settings.defaultReminderMinutes == minutes,
                        onClick = { scope.launch { settingsStore.setDefaultReminder(minutes) } },
                    )
                }

                Text(
                    "រយៈពេលព្រឹត្តិការណ៍",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(start = 16.dp, top = 16.dp),
                )
                listOf(30, 60, 90, 120).forEach { minutes ->
                    ChoiceRow(
                        title = "${localeNumber(minutes)} នាទី",
                        selected = settings.defaultEventDurationMinutes == minutes,
                        onClick = { scope.launch { settingsStore.setDefaultDuration(minutes) } },
                    )
                }
            }

            Spacer(Modifier.height(64.dp))
        }
    }
}

@Composable
private fun reminderLabel(minutes: Int): String = when {
    minutes == 0 -> "ពេលចាប់ផ្តើម"
    minutes < 60 -> "${localeNumber(minutes)} នាទីមុន"
    minutes < 1440 -> "${localeNumber(minutes / 60)} ម៉ោងមុន"
    else -> "${localeNumber(minutes / 1440)} ថ្ងៃមុន"
}
