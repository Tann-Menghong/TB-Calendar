package com.khmercalendar.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Celebration
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.WorkOutline
import androidx.compose.material.icons.outlined.SystemUpdateAlt
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.khmercalendar.data.prefs.AppSettings
import com.khmercalendar.data.prefs.SettingsStore
import com.khmercalendar.data.prefs.StartScreen
import com.khmercalendar.ui.Routes
import kotlinx.coroutines.launch

/** The settings index. Each row either toggles something small or opens a dedicated screen. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: AppSettings,
    settingsStore: SettingsStore,
    onNavigate: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var pinDialog by remember { mutableStateOf(false) }
    // Saveable, so coming back from the screen a result opened finds the search still there.
    var query by rememberSaveable { mutableStateOf("") }

    Scaffold(topBar = { TopAppBar(title = { Text("ការកំណត់") }) }) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            SettingsSearchField(query = query, onQueryChange = { query = it })
            if (query.trim().length >= SettingsIndex.MIN_QUERY_LENGTH) {
                SettingsSearchResults(
                    query = query,
                    onNavigate = onNavigate,
                    onClearQuery = { query = "" },
                )
                return@Column
            }
            SettingsGroup("រូបរាង និងការបង្ហាញ") {
                SettingsRow(
                    title = "រូបរាង",
                    subtitle = "ពណ៌ ទំហំអក្សរ និងអ្វីដែលបង្ហាញ",
                    icon = Icons.Outlined.Palette,
                    onClick = { onNavigate(Routes.SETTINGS_APPEARANCE) },
                )
                SettingsRow(
                    title = "ផ្ទាំងដើម",
                    subtitle = "លំដាប់ គំរូ និងផ្ទាំងដែលបង្ហាញ",
                    icon = Icons.Outlined.Dashboard,
                    onClick = { onNavigate(Routes.SETTINGS_DASHBOARD) },
                )
                SettingsRow(
                    title = "ប្រភេទព្រឹត្តិការណ៍",
                    subtitle = "បន្ថែម ប្តូរឈ្មោះ និងពណ៌",
                    icon = Icons.Outlined.Category,
                    onClick = { onNavigate(Routes.SETTINGS_CATEGORIES) },
                )
                SettingsRow(
                    title = "បុណ្យជាតិ",
                    subtitle = "មើលបញ្ជីបុណ្យតាមឆ្នាំ",
                    icon = Icons.Outlined.Celebration,
                    onClick = { onNavigate(Routes.HOLIDAYS) },
                )
                SettingsRow(
                    title = "ផ្តោតអារម្មណ៍",
                    subtitle = "កំណត់ម៉ោងធ្វើការជាវគ្គ និងសម្រាក",
                    icon = Icons.Outlined.Timer,
                    onClick = { onNavigate(Routes.FOCUS) },
                )
                SettingsRow(
                    title = "ទម្លាប់",
                    subtitle = "តាមដានអ្វីដែលអ្នកធ្វើឱ្យបានទៀងទាត់",
                    icon = Icons.Outlined.Bolt,
                    onClick = { onNavigate(Routes.HABITS) },
                )
                SettingsRow(
                    title = "រាប់ថយក្រោយ",
                    subtitle = "ថ្ងៃដែលអ្នករាប់ថយក្រោយទៅរក",
                    icon = Icons.Outlined.HourglassEmpty,
                    onClick = { onNavigate(Routes.COUNTDOWNS) },
                )
                SettingsRow(
                    title = "ស្ថិតិ",
                    subtitle = "អ្វីដែលអ្នកបានធ្វើ គិតតាមថ្ងៃ សប្តាហ៍ ខែ និងឆ្នាំ",
                    icon = Icons.Outlined.Insights,
                    onClick = { onNavigate(Routes.STATS) },
                )
            }

            HorizontalDivider()

            SettingsGroup("អេក្រង់ចាប់ផ្តើម") {
                StartScreen.entries.forEach { screen ->
                    ChoiceRow(
                        title = screen.labelKm,
                        selected = settings.startScreen == screen,
                        onClick = { scope.launch { settingsStore.setStartScreen(screen) } },
                    )
                }
            }

            HorizontalDivider()

            SettingsGroup("ការរំលឹក") {
                SettingsRow(
                    title = "ការជូនដំណឹង",
                    subtitle = "សំឡេង ការញ័រ និងរំលឹកលំនាំដើម",
                    icon = Icons.Outlined.Notifications,
                    onClick = { onNavigate(Routes.SETTINGS_NOTIFICATIONS) },
                )
            }

            HorizontalDivider()

            SettingsGroup("ជំនួយការក្នុងឧបករណ៍") {
                SettingsRow(
                    title = "ម៉ូដែល AI",
                    subtitle = if (settings.aiEnabled) "បើក" else "បិទ — ប្រតិទិនដំណើរការធម្មតា",
                    icon = Icons.Outlined.AutoAwesome,
                    onClick = { onNavigate(Routes.SETTINGS_AI) },
                )
            }

            HorizontalDivider()

            SettingsGroup("ឯកជនភាព និងទិន្នន័យ") {
                SwitchRow(
                    title = "ចាក់សោកម្មវិធី",
                    subtitle = "ត្រូវការលេខសម្ងាត់ពេលបើកកម្មវិធី",
                    icon = Icons.Outlined.Lock,
                    checked = settings.appLockEnabled && settings.pinHash != null,
                    onChange = { on ->
                        if (on) {
                            pinDialog = true
                        } else {
                            scope.launch { settingsStore.clearPin() }
                        }
                    },
                )
                if (settings.appLockEnabled && settings.pinHash != null) {
                    SwitchRow(
                        title = "ដោះសោដោយស្នាមម្រាមដៃ",
                        checked = settings.biometricUnlock,
                        onChange = { scope.launch { settingsStore.setBiometricUnlock(it) } },
                    )
                    SettingsRow(
                        title = "ប្តូរលេខសម្ងាត់",
                        onClick = { pinDialog = true },
                    )
                }
                SettingsRow(
                    title = "បម្រុងទុក និងស្តារ",
                    subtitle = "នាំចេញ/នាំចូល JSON និង .ics",
                    icon = Icons.Outlined.Backup,
                    onClick = { onNavigate(Routes.SETTINGS_BACKUP) },
                )
            }

            HorizontalDivider()

            SettingsGroup("ការងារ") {
                SettingsRow(
                    title = "កាលវិភាគការងារ",
                    subtitle = "ម៉ោងចូល ចេញ សម្រាក និងការរាប់ថយក្រោយ",
                    icon = Icons.Outlined.WorkOutline,
                    onClick = { onNavigate(Routes.SETTINGS_WORK) },
                )
            }

            HorizontalDivider()

            SettingsGroup("អំពី") {
                SettingsRow(
                    title = "បច្ចុប្បន្នភាពកម្មវិធី",
                    subtitle = "ពិនិត្យកំណែថ្មី — មិនស្វ័យប្រវត្តិ",
                    icon = Icons.Outlined.SystemUpdateAlt,
                    onClick = { onNavigate(Routes.SETTINGS_UPDATE) },
                )
                SettingsRow(
                    title = "អំពីកម្មវិធី",
                    icon = Icons.Outlined.Info,
                    onClick = { onNavigate(Routes.SETTINGS_ABOUT) },
                )
            }

            Column(Modifier.height(80.dp)) {}
        }
    }

    if (pinDialog) {
        PinDialog(
            onDismiss = { pinDialog = false },
            onConfirm = { pin ->
                scope.launch { settingsStore.setPin(pin) }
                pinDialog = false
            },
        )
    }
}

@Composable
private fun PinDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var pin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    val valid = pin.length in 4..8 && pin == confirm

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("កំណត់លេខសម្ងាត់") },
        text = {
            Column {
                Text(
                    "លេខ ៤ ដល់ ៨ ខ្ទង់។ លេខសម្ងាត់ត្រូវបានរក្សាទុកជា hash ប៉ុណ្ណោះ " +
                        "មិនមែនជាអត្ថបទដើមទេ។",
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(
                    value = pin,
                    onValueChange = { if (it.length <= 8 && it.all(Char::isDigit)) pin = it },
                    label = { Text("លេខសម្ងាត់") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                )
                OutlinedTextField(
                    value = confirm,
                    onValueChange = { if (it.length <= 8 && it.all(Char::isDigit)) confirm = it },
                    label = { Text("បញ្ជាក់ម្តងទៀត") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(pin) }, enabled = valid) { Text("រក្សាទុក") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("បោះបង់") } },
    )
}
