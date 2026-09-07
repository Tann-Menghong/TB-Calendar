package com.khmercalendar.ui.settings

import android.net.Uri
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
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.khmercalendar.data.backup.BackupManager
import com.khmercalendar.data.backup.IcsFormat
import com.khmercalendar.data.db.KhmerCalendarDatabase
import com.khmercalendar.notify.ReminderScheduler
import kotlinx.coroutines.launch

/**
 * Backup, restore and calendar interchange.
 *
 * Both formats write through the Storage Access Framework, so a backup lands wherever the
 * user chooses - internal storage, an SD card, a cloud folder they already trust - and the
 * app never needs a storage permission or an account of its own.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(
    backupManager: BackupManager,
    database: KhmerCalendarDatabase,
    scheduler: ReminderScheduler,
    pendingImportUri: String?,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var busy by remember { mutableStateOf(false) }
    var confirmRestore by remember { mutableStateOf<Uri?>(null) }

    // An .ics handed over by another app arrives as a route argument; ask before writing it.
    LaunchedEffect(pendingImportUri) {
        pendingImportUri?.let { confirmRestore = Uri.parse(it) }
    }

    val exportJson = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            val result = backupManager.export(uri)
            busy = false
            snackbar.showSnackbar(
                result.fold(
                    onSuccess = { "នាំចេញ ${localeNumberPlain(it)} ព្រឹត្តិការណ៍" },
                    onFailure = { "នាំចេញមិនបាន៖ ${it.message}" },
                ),
            )
        }
    }

    val importJson = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> if (uri != null) confirmRestore = uri }

    val exportIcs = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/calendar"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            val result = IcsFormat.export(context, database, uri)
            busy = false
            snackbar.showSnackbar(
                result.fold(
                    onSuccess = { "នាំចេញ ${localeNumberPlain(it)} ព្រឹត្តិការណ៍ ជា .ics" },
                    onFailure = { "នាំចេញមិនបាន៖ ${it.message}" },
                ),
            )
        }
    }

    val importIcs = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            val result = IcsFormat.import(context, database, uri)
            scheduler.rescheduleAll()
            busy = false
            snackbar.showSnackbar(
                result.fold(
                    onSuccess = { "នាំចូល ${localeNumberPlain(it)} ព្រឹត្តិការណ៍" },
                    onFailure = { "នាំចូលមិនបាន៖ ${it.message}" },
                ),
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("បម្រុងទុក និងស្តារ") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "ត្រឡប់")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()),
        ) {
            Text(
                "ការបម្រុងទុកទាំងអស់ធ្វើឡើងក្នុងឧបករណ៍របស់អ្នក។ " +
                    "កម្មវិធីមិនផ្ញើទិន្នន័យប្រតិទិនទៅម៉ាស៊ីនមេណាមួយឡើយ។",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )

            SettingsGroup("បម្រុងទុកពេញលេញ (JSON)") {
                SettingsRow(
                    title = "នាំចេញទិន្នន័យទាំងអស់",
                    subtitle = "ព្រឹត្តិការណ៍ ប្រភេទ ការរំលឹក និងកំណត់ចំណាំ",
                    icon = Icons.Outlined.FileDownload,
                    onClick = { if (!busy) exportJson.launch(backupManager.suggestedFileName()) },
                )
                SettingsRow(
                    title = "ស្តារពីឯកសារ",
                    subtitle = "បន្ថែមចូលទិន្នន័យដែលមានស្រាប់",
                    icon = Icons.Outlined.FileUpload,
                    onClick = {
                        if (!busy) importJson.launch(arrayOf("application/json", "text/plain", "*/*"))
                    },
                )
            }

            HorizontalDivider()

            SettingsGroup("ប្រតិទិនស្តង់ដារ (.ics)") {
                SettingsRow(
                    title = "នាំចេញជា .ics",
                    subtitle = "បើកបានក្នុង Google Calendar, Outlook និងកម្មវិធីផ្សេងៗ",
                    icon = Icons.Outlined.FileDownload,
                    onClick = { if (!busy) exportIcs.launch("khmer-calendar.ics") },
                )
                SettingsRow(
                    title = "នាំចូលពី .ics",
                    icon = Icons.Outlined.FileUpload,
                    onClick = { if (!busy) importIcs.launch(arrayOf("text/calendar", "*/*")) },
                )
            }

            Spacer(Modifier.height(64.dp))
        }
    }

    confirmRestore?.let { uri ->
        AlertDialog(
            onDismissRequest = { confirmRestore = null },
            title = { Text("ស្តារទិន្នន័យ?") },
            text = {
                Text(
                    "ទិន្នន័យក្នុងឯកសារនឹងត្រូវបានបន្ថែមចូល។ " +
                        "ព្រឹត្តិការណ៍ដែលមានស្រាប់មិនត្រូវបានលុបទេ។",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmRestore = null
                        busy = true
                        scope.launch {
                            val isIcs = uri.toString().endsWith(".ics", ignoreCase = true)
                            val message = if (isIcs) {
                                IcsFormat.import(context, database, uri).fold(
                                    onSuccess = { "នាំចូល ${localeNumberPlain(it)} ព្រឹត្តិការណ៍" },
                                    onFailure = { "នាំចូលមិនបាន៖ ${it.message}" },
                                )
                            } else {
                                backupManager.import(uri).fold(
                                    onSuccess = {
                                        "ស្តារ ${localeNumberPlain(it.events)} ព្រឹត្តិការណ៍ " +
                                            "និង ${localeNumberPlain(it.notes)} កំណត់ចំណាំ"
                                    },
                                    onFailure = { "ស្តារមិនបាន៖ ${it.message}" },
                                )
                            }
                            scheduler.rescheduleAll()
                            busy = false
                            snackbar.showSnackbar(message)
                        }
                    },
                ) { Text("ស្តារ") }
            },
            dismissButton = {
                TextButton(onClick = { confirmRestore = null }) { Text("បោះបង់") }
            },
        )
    }
}

/** Snackbar text is built outside composition, so the Khmer digits are converted directly. */
private fun localeNumberPlain(value: Int): String =
    com.khmercalendar.core.khmer.KhmerNumerals.toKhmer(value)
