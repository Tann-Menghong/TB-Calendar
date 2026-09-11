package com.khmercalendar.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.khmercalendar.data.backup.BackupCounts
import com.khmercalendar.data.backup.BackupError
import com.khmercalendar.data.backup.BackupManager
import com.khmercalendar.data.backup.BackupPreview
import com.khmercalendar.data.backup.IcsFormat
import com.khmercalendar.data.backup.ImportKind
import com.khmercalendar.data.backup.RestoreSelection
import com.khmercalendar.data.backup.RestoreSummary
import com.khmercalendar.data.db.KhmerCalendarDatabase
import com.khmercalendar.notify.ReminderScheduler
import com.khmercalendar.ui.components.localeNumber
import com.khmercalendar.ui.components.localeWrittenDate
import com.khmercalendar.ui.theme.LocalAppSettings
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

/**
 * Backup, restore and calendar interchange.
 *
 * Both formats write through the Storage Access Framework, so a backup lands wherever the user
 * chooses - internal storage, an SD card, a cloud folder they already trust - and the app never
 * needs a storage permission or an account of its own.
 *
 * ## Restore shows before it writes
 *
 * A restore only ever adds, which also means it cannot be undone by the app: there is no telling
 * a restored event from one the user made. So a chosen file is read and planned first, and the
 * user sees what is in it, what the phone already has, and what could not be read - then chooses
 * which parts to bring back.
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
    // Read once here: snackbar text is built inside coroutines, outside composition. It used to
    // be written in Khmer digits whatever the user had chosen.
    val khmerNumerals = LocalAppSettings.current.useKhmerNumerals
    var busy by remember { mutableStateOf(false) }
    var confirmIcs by remember { mutableStateOf<Uri?>(null) }
    var preview by remember { mutableStateOf<BackupPreview?>(null) }
    var summary by remember { mutableStateOf<RestoreSummary?>(null) }

    /**
     * Opens a file for restoring, whatever it turns out to be.
     *
     * Decided by content rather than by name: a file shared from another app arrives as a
     * `content://` address with no extension, and the old check on ".ics" sent every one of
     * them to the backup parser.
     */
    fun openForRestore(uri: Uri) {
        if (busy) return
        busy = true
        scope.launch {
            if (backupManager.kindOf(uri) == ImportKind.ICS) {
                busy = false
                confirmIcs = uri
                return@launch
            }
            val result = backupManager.read(uri)
            busy = false
            result.onSuccess { preview = it }
            result.exceptionOrNull()?.let { snackbar.showSnackbar(failureText(it)) }
        }
    }

    LaunchedEffect(pendingImportUri) {
        pendingImportUri?.let { openForRestore(it.toUri()) }
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
                result.getOrNull()?.let { exportedText(it, khmerNumerals) }
                    ?: failureText(result.exceptionOrNull()),
            )
        }
    }

    val importJson = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> if (uri != null) openForRestore(uri) }

    val exportIcs = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/calendar"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            val result = IcsFormat.export(context, database, uri)
            busy = false
            snackbar.showSnackbar(
                result.getOrNull()?.let { "នាំចេញ ${localeNumber(it, khmerNumerals)} ព្រឹត្តិការណ៍ ជា .ics" }
                    ?: failureText(result.exceptionOrNull()),
            )
        }
    }

    fun importIcs(uri: Uri) {
        busy = true
        scope.launch {
            val result = IcsFormat.import(context, database, uri)
            if (result.isSuccess) scheduler.rescheduleAll()
            busy = false
            snackbar.showSnackbar(
                result.getOrNull()?.let { icsImportMessage(it, khmerNumerals) }
                    ?: failureText(result.exceptionOrNull()),
            )
        }
    }

    val pickIcs = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> if (uri != null) importIcs(uri) }

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
            // A large restore takes a moment, and nothing on this screen changed while it ran.
            if (busy) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            } else {
                Spacer(Modifier.height(4.dp))
            }

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
                    subtitle = "ព្រឹត្តិការណ៍ កិច្ចការ កំណត់ចំណាំ ទម្លាប់ ការផ្តោតអារម្មណ៍ និងការកំណត់",
                    icon = Icons.Outlined.FileDownload,
                    onClick = { if (!busy) exportJson.launch(backupManager.suggestedFileName()) },
                )
                SettingsRow(
                    title = "ស្តារពីឯកសារ",
                    subtitle = "មើលមុនសិន ហើយបន្ថែមចូលដោយមិនលុបអ្វីទាំងអស់",
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
                    onClick = { if (!busy) pickIcs.launch(arrayOf("text/calendar", "*/*")) },
                )
            }

            Spacer(Modifier.height(64.dp))
        }
    }

    preview?.let { current ->
        RestorePreviewDialog(
            preview = current,
            khmerNumerals = khmerNumerals,
            onDismiss = { preview = null },
            onConfirm = { selection ->
                preview = null
                busy = true
                scope.launch {
                    val result = backupManager.restore(current.archive, selection)
                    // Reminders are re-armed from whatever is now stored - including the work
                    // alarms, which read the settings a restore may just have replaced.
                    if (result.isSuccess) scheduler.rescheduleAll()
                    busy = false
                    result.onSuccess { summary = it }
                    result.exceptionOrNull()?.let { snackbar.showSnackbar(failureText(it)) }
                }
            },
        )
    }

    summary?.let { done ->
        RestoreResultDialog(summary = done, khmerNumerals = khmerNumerals, onDismiss = { summary = null })
    }

    confirmIcs?.let { uri ->
        AlertDialog(
            onDismissRequest = { confirmIcs = null },
            title = { Text("នាំចូលប្រតិទិន?") },
            text = {
                Text(
                    "ព្រឹត្តិការណ៍ក្នុងឯកសារនឹងត្រូវបានបន្ថែមចូល។ " +
                        "ព្រឹត្តិការណ៍ដែលមានស្រាប់មិនត្រូវបានលុបទេ ហើយព្រឹត្តិការណ៍ស្ទួននឹងត្រូវរំលង។",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmIcs = null
                    importIcs(uri)
                }) { Text("នាំចូល") }
            },
            dismissButton = {
                TextButton(onClick = { confirmIcs = null }) { Text("បោះបង់") }
            },
        )
    }
}

/**
 * What is in the file, and which parts to bring back.
 *
 * The counts that matter to the decision - what the phone already has, what could not be read -
 * are stated before the button rather than discovered after it.
 */
@Composable
private fun RestorePreviewDialog(
    preview: BackupPreview,
    khmerNumerals: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (RestoreSelection) -> Unit,
) {
    fun n(value: Int) = localeNumber(value, khmerNumerals)

    val file = preview.inFile
    val hasCalendar = file.events + file.notes + file.categories > 0
    val hasProductivity = file.habits + file.focusSessions > 0
    var calendar by rememberSaveable { mutableStateOf(hasCalendar) }
    var productivity by rememberSaveable { mutableStateOf(hasProductivity) }
    var settings by rememberSaveable { mutableStateOf(false) }
    val selection = RestoreSelection(calendar = calendar, productivity = productivity, settings = settings)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("ស្តារពីឯកសារបម្រុងទុក") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val made = Instant.ofEpochMilli(preview.archive.exportedAtMillis)
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate()
                Text(
                    "បង្កើតនៅ ${localeWrittenDate(made)}",
                    style = MaterialTheme.typography.bodyMedium,
                )

                SelectionRow(
                    title = "ប្រតិទិន",
                    detail = "${n(file.events)} ព្រឹត្តិការណ៍ និងកិច្ចការ · ${n(file.notes)} កំណត់ចំណាំ",
                    checked = calendar,
                    enabled = hasCalendar,
                    onChange = { calendar = it },
                )
                SelectionRow(
                    title = "ទម្លាប់ និងការផ្តោតអារម្មណ៍",
                    detail = "${n(file.habits)} ទម្លាប់ · ${n(file.habitDays)} ថ្ងៃ · " +
                        "${n(file.focusSessions)} វគ្គ",
                    checked = productivity,
                    enabled = hasProductivity,
                    onChange = { productivity = it },
                )
                if (preview.hasSettings) {
                    SelectionRow(
                        title = "ការកំណត់",
                        detail = "ជំនួសការកំណត់បច្ចុប្បន្ន",
                        checked = settings,
                        enabled = true,
                        onChange = { settings = it },
                    )
                }

                val already = preview.alreadyHere
                val alreadyTotal = already.events + already.notes + already.habitDays + already.focusSessions
                if (alreadyTotal > 0) {
                    Text(
                        "${n(alreadyTotal)} ធាតុមានក្នុងទូរស័ព្ទរួចហើយ ហើយនឹងត្រូវរំលង",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (preview.unreadable > 0) {
                    Text(
                        "${n(preview.unreadable)} ធាតុមិនអាចអានបាន ហើយនឹងមិនត្រូវបានស្តារ",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Text(
                    "ទិន្នន័យត្រូវបានបន្ថែមចូល។ គ្មានអ្វីដែលមានស្រាប់ត្រូវបានលុប ឬសរសេរជាន់ពីលើឡើយ។",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selection) }, enabled = !selection.isEmpty) { Text("ស្តារ") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("បោះបង់") }
        },
    )
}

/**
 * A checkbox with its label as one target.
 *
 * The whole row toggles, and a screen reader hears one checkbox named by its title rather than an
 * unlabelled box beside two lines of text.
 */
@Composable
private fun SelectionRow(
    title: String,
    detail: String,
    checked: Boolean,
    enabled: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(value = checked, enabled = enabled, role = Role.Checkbox, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = null, enabled = enabled)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** What a restore actually did, including what it chose not to do and why. */
@Composable
private fun RestoreResultDialog(summary: RestoreSummary, khmerNumerals: Boolean, onDismiss: () -> Unit) {
    fun n(value: Int) = localeNumber(value, khmerNumerals)

    val added = summary.added
    val skipped = summary.skipped
    val lines = buildList {
        if (added.isEmpty && summary.notesMerged == 0) {
            add("គ្មានអ្វីថ្មីដើម្បីបន្ថែមទេ។ អ្វីៗក្នុងឯកសារមានក្នុងទូរស័ព្ទរួចហើយ")
        }
        if (added.events > 0) add("បានបន្ថែម ${n(added.events)} ព្រឹត្តិការណ៍ និងកិច្ចការ")
        if (added.checklistItems > 0) add("បានបន្ថែម ${n(added.checklistItems)} ជំហានកិច្ចការ")
        if (added.notes > 0) add("បានបន្ថែម ${n(added.notes)} កំណត់ចំណាំ")
        if (summary.notesMerged > 0) {
            add("${n(summary.notesMerged)} កំណត់ចំណាំបានបញ្ចូលក្រោមកំណត់ចំណាំដែលមានស្រាប់")
        }
        if (added.habits > 0 || added.habitDays > 0) {
            add("បានបន្ថែម ${n(added.habits)} ទម្លាប់ និង ${n(added.habitDays)} ថ្ងៃដែលបានធ្វើ")
        }
        if (added.focusSessions > 0) add("បានបន្ថែម ${n(added.focusSessions)} វគ្គផ្តោតអារម្មណ៍")
        if (added.templates > 0) add("បានបន្ថែម ${n(added.templates)} គំរូ")
        val skippedTotal = skipped.events + skipped.notes + skipped.habitDays + skipped.focusSessions
        if (skippedTotal > 0) add("រំលង ${n(skippedTotal)} ធាតុដែលមានរួចហើយ")
        if (summary.unreadable > 0) add("${n(summary.unreadable)} ធាតុមិនអាចអានបាន")
        if (summary.settingsApplied) add("ការកំណត់ត្រូវបានស្តារ")
        if (summary.settingsFailed) add("មិនអាចស្តារការកំណត់បានទេ។ ទិន្នន័យផ្សេងទៀតត្រូវបានស្តាររួចហើយ")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("ស្តាររួចរាល់") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                lines.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("យល់ព្រម") } },
    )
}

private fun exportedText(counts: BackupCounts, khmerNumerals: Boolean): String {
    fun n(value: Int) = localeNumber(value, khmerNumerals)
    return buildString {
        append("បានរក្សាទុក ${n(counts.events)} ព្រឹត្តិការណ៍")
        if (counts.notes > 0) append(" · ${n(counts.notes)} កំណត់ចំណាំ")
        if (counts.habits > 0) append(" · ${n(counts.habits)} ទម្លាប់")
        if (counts.focusSessions > 0) append(" · ${n(counts.focusSessions)} វគ្គ")
    }
}

/**
 * A failure, in Khmer.
 *
 * Never the exception's own text, which was English parser output. Every operation on this screen
 * is now all-or-nothing, so "nothing on your phone changed" is true of every failure it can show.
 */
private fun failureText(error: Throwable?): String =
    (error as? BackupError)?.messageKm
        ?: "មិនអាចបញ្ចប់បានទេ។ ទិន្នន័យក្នុងទូរស័ព្ទមិនត្រូវបានផ្លាស់ប្តូរឡើយ"

/**
 * What to say after reading an .ics file.
 *
 * Names the skipped events rather than hiding them: a second import of the same file adds
 * nothing, and a user who expected it to add something needs to know why it did not.
 */
private fun icsImportMessage(result: IcsFormat.ImportResult, khmerNumerals: Boolean): String {
    fun n(value: Int) = localeNumber(value, khmerNumerals)
    return when {
        result.imported == 0 && result.skipped > 0 ->
            "មានរួចហើយ។ រំលង ${n(result.skipped)} ព្រឹត្តិការណ៍ដែលស្ទួនគ្នា"
        result.skipped > 0 ->
            "នាំចូល ${n(result.imported)} · រំលង ${n(result.skipped)} ដែលស្ទួនគ្នា"
        else -> "នាំចូល ${n(result.imported)} ព្រឹត្តិការណ៍"
    }
}
