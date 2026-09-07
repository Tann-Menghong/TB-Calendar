package com.khmercalendar.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.khmercalendar.ai.ModelFit
import com.khmercalendar.ai.model.ModelSpec
import com.khmercalendar.ui.components.localeNumber
import kotlin.math.roundToInt

/**
 * AI model management.
 *
 * Nothing here is required to use the calendar, and the screen says so at the top: the point
 * of an on-device assistant is that it is optional, private and honest about what it costs in
 * memory and storage.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiModelScreen(viewModel: AiModelViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val prefs by viewModel.prefs.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf<ModelSpec?>(null) }

    LaunchedEffect(Unit) { viewModel.refresh() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ជំនួយការ AI") },
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
            Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)) {
                Column(Modifier.padding(16.dp)) {
                    Text("ដំណើរការក្នុងឧបករណ៍ ១០០%", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "ម៉ូដែលត្រូវបានរក្សាទុក និងដំណើរការនៅលើទូរស័ព្ទរបស់អ្នក។ " +
                            "សំណួរ និងព័ត៌មានប្រតិទិនមិនចេញពីឧបករណ៍ទេ។ " +
                            "ការទាញយកម៉ូដែលជាលើកតែមួយប៉ុណ្ណោះដែលត្រូវការអ៊ីនធឺណិត។",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            state.message?.let { message ->
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(message, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    TextButton(onClick = viewModel::dismissMessage) { Text("បិទ") }
                }
            }

            SwitchRow(
                title = "បើកជំនួយការ AI",
                subtitle = "ប្រតិទិន ការរំលឹក និងមុខងារគណនាដំណើរការទោះបិទក៏ដោយ",
                checked = prefs.aiEnabled,
                onChange = viewModel::setEnabled,
            )

            HorizontalDivider()

            state.profile?.let { profile ->
                SettingsGroup("ឧបករណ៍របស់អ្នក") {
                    InfoLine("RAM សរុប", "%.1f GB".format(profile.totalRamGb))
                    InfoLine("RAM ទំនេរ", "%.1f GB".format(profile.availableRamGb))
                    InfoLine("ទំហំផ្ទុកទំនេរ", "%.1f GB".format(profile.freeStorageGb))
                    InfoLine("ស្ថាបត្យកម្ម", if (profile.is64Bit) "64-bit" else "32-bit")
                    if (state.usedBytes > 0) {
                        InfoLine("ម៉ូដែលបានប្រើ", "%.2f GB".format(state.usedBytes / 1_073_741_824.0))
                    }
                }
            }

            HorizontalDivider()

            SettingsGroup("ម៉ូដែលដែលអាចប្រើបាន") {
                state.rows.forEach { row ->
                    ModelCard(
                        row = row,
                        recommended = row.spec.id == state.recommendedId,
                        onSelect = { viewModel.select(row.spec) },
                        onDownload = { viewModel.download(row.spec) },
                        onCancel = viewModel::cancelDownload,
                        onDelete = { confirmDelete = row.spec },
                    )
                    HorizontalDivider()
                }
            }

            SettingsGroup("ស្ថានភាព") {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        if (state.loaded) "ម៉ូដែលកំពុងដំណើរការ" else "ម៉ូដែលមិនបានផ្ទុក",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    when {
                        state.loading -> CircularProgressIndicator(
                            Modifier.height(22.dp),
                            strokeWidth = 2.dp,
                        )

                        state.loaded -> OutlinedButton(onClick = viewModel::unload) { Text("ដកចេញ") }
                        else -> Button(
                            onClick = viewModel::load,
                            enabled = prefs.aiEnabled && prefs.aiModelId != null,
                        ) { Text("ផ្ទុក") }
                    }
                }
            }

            HorizontalDivider()

            SettingsGroup("ការកំណត់ការឆ្លើយ") {
                SliderSetting(
                    label = "ប្រវែងចម្លើយអតិបរមា",
                    display = "${localeNumber(prefs.aiMaxTokens)} tokens",
                    value = prefs.aiMaxTokens.toFloat(),
                    range = 128f..2048f,
                    steps = 14,
                    onChange = { viewModel.setMaxTokens(it.roundToInt()) },
                )
                SliderSetting(
                    label = "ភាពច្នៃប្រឌិត",
                    display = "%.1f".format(prefs.aiTemperature),
                    value = prefs.aiTemperature,
                    range = 0f..1f,
                    steps = 9,
                    onChange = viewModel::setTemperature,
                )
                Text(
                    "តម្លៃទាបផ្តល់ចម្លើយស្ថិតស្ថេរ និងសមស្របសម្រាប់ការទាញយកព័ត៌មានព្រឹត្តិការណ៍។",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }

            Spacer(Modifier.height(64.dp))
        }
    }

    confirmDelete?.let { spec ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("លុបម៉ូដែល?") },
            text = { Text("${spec.displayName} (${spec.sizeLabel}) នឹងត្រូវលុបចេញពីឧបករណ៍។") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.delete(spec)
                        confirmDelete = null
                    },
                ) { Text("លុប") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = null }) { Text("បោះបង់") }
            },
        )
    }
}

@Composable
private fun ModelCard(
    row: ModelRow,
    recommended: Boolean,
    onSelect: () -> Unit,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(
                selected = row.isSelected,
                onClick = onSelect,
                enabled = row.installed,
            )
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        row.spec.displayName,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (recommended) {
                        Spacer(Modifier.padding(3.dp))
                        Text(
                            "ណែនាំ",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                Text(
                    "${row.spec.sizeLabel} · ត្រូវការ RAM ${row.spec.ramLabel} · " +
                        khmerQualityLabel(row.spec),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Text(
            row.spec.noteKm,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )

        fitMessage(row.fit)?.let { reason ->
            Text(
                reason,
                style = MaterialTheme.typography.labelMedium,
                color = if (row.fit.runnable) {
                    MaterialTheme.colorScheme.tertiary
                } else {
                    MaterialTheme.colorScheme.error
                },
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        row.downloadPercent?.let { percent ->
            LinearProgressIndicator(
                progress = { percent / 100f },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${localeNumber(percent)}%",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onCancel) { Text("បោះបង់") }
            }
        }

        if (row.downloadPercent == null) {
            Row(
                Modifier.padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (row.installed) {
                    OutlinedButton(onClick = onDelete) { Text("លុប") }
                    if (!row.isSelected) {
                        FilledTonalButton(onClick = onSelect) { Text("ជ្រើសរើស") }
                    }
                } else {
                    Button(
                        onClick = onDownload,
                        enabled = row.fit.runnable || row.fit is ModelFit.Tight,
                    ) { Text("ទាញយក ${row.spec.sizeLabel}") }
                }
            }
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SliderSetting(
    label: String,
    display: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onChange: (Float) -> Unit,
) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(
                display,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Slider(value = value, onValueChange = onChange, valueRange = range, steps = steps)
    }
}

private fun khmerQualityLabel(spec: ModelSpec): String = when (spec.khmerQuality) {
    ModelSpec.KhmerQuality.BASIC -> "ខ្មែរមូលដ្ឋាន"
    ModelSpec.KhmerQuality.GOOD -> "ខ្មែរល្អ"
    ModelSpec.KhmerQuality.BEST -> "ខ្មែរល្អបំផុត"
}

private fun fitMessage(fit: ModelFit): String? = when (fit) {
    is ModelFit.Good -> null
    is ModelFit.Tight -> fit.reason
    is ModelFit.TooLarge -> fit.reason
    is ModelFit.NoStorage -> fit.reason
    is ModelFit.Unsupported -> fit.reason
}
