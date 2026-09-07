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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import com.khmercalendar.ai.model.DownloadFailure
import com.khmercalendar.ai.model.ModelCatalog
import com.khmercalendar.ai.model.ModelSpec
import com.khmercalendar.core.khmer.KhmerNumerals
import com.khmercalendar.ui.theme.LocalAppSettings
import com.khmercalendar.ui.components.localeNumber
import kotlin.math.roundToInt

/**
 * AI model management.
 *
 * Nothing here is required to use the calendar, and the screen says so at the top. Everything
 * expensive is stated before it is spent - the file size, the RAM the model needs, what the
 * device actually has - and while a download runs, how much is left and how long it is likely
 * to take. When something fails, the screen names the failure and offers the action that
 * matches it: "try again" against a full disk is not an action, it is a loop.
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

            state.problem?.let { problem ->
                ProblemCard(
                    problem = problem,
                    onRetry = { ModelCatalog.byId(problem.modelId)?.let(viewModel::resume) },
                    onDiscard = { ModelCatalog.byId(problem.modelId)?.let(viewModel::cancelAndDiscard) },
                    onDismiss = viewModel::dismissProblem,
                )
            }

            state.download?.let { download ->
                ActiveDownloadCard(
                    download = download,
                    onPause = viewModel::pause,
                    onCancel = { ModelCatalog.byId(download.modelId)?.let(viewModel::cancelAndDiscard) },
                )
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
                        busy = state.download != null,
                        onSelect = { viewModel.select(row.spec) },
                        onDownload = { viewModel.requestDownload(row.spec) },
                        onResume = { viewModel.resume(row.spec) },
                        onDiscardPartial = { viewModel.cancelAndDiscard(row.spec) },
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

    state.confirming?.let { spec ->
        DownloadConfirmDialog(
            spec = spec,
            alreadyBytes = state.rows.firstOrNull { it.spec.id == spec.id }
                ?.partial?.downloadedBytes ?: 0L,
            onConfirm = viewModel::confirmDownload,
            onDismiss = viewModel::dismissConfirm,
        )
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

// -----------------------------------------------------------------------------------------

/**
 * The confirmation shown before any bytes are spent.
 *
 * Mobile data in Cambodia is metered and not cheap; a two-gigabyte download started by a
 * mis-tap is a real cost to a real person, so the size is restated here and agreed to.
 */
@Composable
private fun DownloadConfirmDialog(
    spec: ModelSpec,
    alreadyBytes: Long,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val remaining = (spec.sizeBytes - alreadyBytes).coerceAtLeast(0L)
    val khmerNumerals = LocalAppSettings.current.useKhmerNumerals
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (alreadyBytes > 0) "បន្តទាញយក?" else "ទាញយកម៉ូដែល?") },
        text = {
            Column {
                Text(spec.displayName, style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                Text(
                    buildString {
                        append("ទំហំឯកសារ៖ ").append(spec.sizeLabel).append('\n')
                        if (alreadyBytes > 0) {
                            append("នៅសល់ត្រូវទាញយក៖ ")
                                .append(bytesLabel(remaining, khmerNumerals)).append('\n')
                        }
                        append("ត្រូវការ RAM៖ ").append(spec.ramLabel)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "ការទាញយកនឹងប្រើទិន្នន័យអ៊ីនធឺណិត។ " +
                        "អ្នកអាចផ្អាក ឬបន្តបានគ្រប់ពេល ហើយវានឹងបន្តពីកន្លែងដែលឈប់។",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("ទាញយក") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("បោះបង់") } },
    )
}

@Composable
private fun ActiveDownloadCard(
    download: ActiveDownload,
    onPause: () -> Unit,
    onCancel: () -> Unit,
) {
    val khmerNumerals = LocalAppSettings.current.useKhmerNumerals
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                if (download.waiting) {
                    "រង់ចាំទាញយក ${download.displayName}"
                } else {
                    "កំពុងទាញយក ${download.displayName}"
                },
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { download.percent / 100f },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                buildString {
                    append(localeNumber(download.percent)).append("%  ·  ")
                    append(bytesLabel(download.downloadedBytes, khmerNumerals))
                    append(" / ").append(bytesLabel(download.totalBytes, khmerNumerals))
                    download.bytesPerSecond?.let {
                        append("  ·  ").append(bytesLabel(it, khmerNumerals)).append("/s")
                    }
                    download.etaSeconds?.let {
                        append("  ·  នៅសល់ ").append(durationLabel(it, khmerNumerals))
                    }
                },
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onPause) { Text("ផ្អាក") }
                TextButton(onClick = onCancel) { Text("បោះបង់ និងលុប") }
            }
        }
    }
}

/**
 * The error state.
 *
 * Each failure gets the action that actually resolves it, which is why the buttons differ per
 * failure rather than always offering a retry.
 */
@Composable
private fun ProblemCard(
    problem: DownloadProblem,
    onRetry: () -> Unit,
    onDiscard: () -> Unit,
    onDismiss: () -> Unit,
) {
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                "មិនអាចទាញយក AI Model បាន",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                failureMessage(problem.failure),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "ទិន្នន័យប្រតិទិនរបស់អ្នកមិនត្រូវបានប៉ះពាល់ទេ។",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            problem.detail?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(4.dp))
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (problem.failure != DownloadFailure.STORAGE_FULL &&
                    problem.failure != DownloadFailure.UNSUPPORTED
                ) {
                    Button(onClick = onRetry) {
                        Text(
                            if (problem.failure == DownloadFailure.CANCELLED) {
                                "បន្ត"
                            } else {
                                "ព្យាយាមម្តងទៀត"
                            },
                        )
                    }
                }
                if (problem.failure != DownloadFailure.UNSUPPORTED) {
                    OutlinedButton(onClick = onDiscard) { Text("លុបឯកសារមិនពេញ") }
                }
                TextButton(onClick = onDismiss) { Text("បិទ") }
            }
        }
    }
}

@Composable
private fun ModelCard(
    row: ModelRow,
    recommended: Boolean,
    busy: Boolean,
    onSelect: () -> Unit,
    onDownload: () -> Unit,
    onResume: () -> Unit,
    onDiscardPartial: () -> Unit,
    onDelete: () -> Unit,
) {
    val khmerNumerals = LocalAppSettings.current.useKhmerNumerals
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

        row.partial?.let { partial ->
            Text(
                "ទាញយកមិនទាន់ចប់៖ ${bytesLabel(partial.downloadedBytes, khmerNumerals)} / " +
                    bytesLabel(partial.totalBytes, khmerNumerals),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        row.damaged?.let { damaged ->
            Text(
                "ឯកសារខូច៖ ${damaged.reason}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        Row(
            Modifier.padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when {
                row.installed -> {
                    OutlinedButton(onClick = onDelete) { Text("លុប") }
                    if (!row.isSelected) {
                        FilledTonalButton(onClick = onSelect) { Text("ជ្រើសរើស") }
                    }
                }

                row.partial != null -> {
                    Button(onClick = onResume, enabled = !busy && row.fit.runnable) {
                        Text("បន្តទាញយក")
                    }
                    OutlinedButton(onClick = onDiscardPartial) { Text("លុបចោល") }
                }

                row.damaged != null -> {
                    Button(onClick = onDownload, enabled = !busy && row.fit.runnable) {
                        Text("ទាញយកម្តងទៀត")
                    }
                    OutlinedButton(onClick = onDiscardPartial) { Text("លុបចោល") }
                }

                else -> Button(
                    onClick = onDownload,
                    enabled = !busy && row.fit.runnable && row.spec.downloadUrl != null,
                ) { Text("ទាញយក ${row.spec.sizeLabel}") }
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

// -----------------------------------------------------------------------------------------

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

/** What went wrong, in the terms the user experiences it. */
private fun failureMessage(failure: DownloadFailure): String = when (failure) {
    DownloadFailure.NETWORK ->
        "ការតភ្ជាប់អ៊ីនធឺណិតដាច់។ អ្វីដែលបានទាញយករួចត្រូវបានរក្សាទុក " +
            "ដូច្នេះការព្យាយាមម្តងទៀតនឹងបន្តពីកន្លែងដែលឈប់។"

    DownloadFailure.STORAGE_FULL ->
        "ការទាញយកត្រូវបានផ្អាក ដោយសារទំហំផ្ទុកមិនគ្រប់។ " +
            "សូមបង្កើនទំហំទំនេរ ហើយព្យាយាមម្តងទៀត។"

    DownloadFailure.SERVER ->
        "ម៉ាស៊ីនមេមិនអាចផ្តល់ឯកសារបានទេនៅពេលនេះ។ សូមព្យាយាមម្តងទៀតនៅពេលក្រោយ។"

    DownloadFailure.CORRUPT ->
        "ឯកសារដែលទាញយកមិនត្រឹមត្រូវ ហើយត្រូវបានលុបចោល។ សូមទាញយកម្តងទៀត។"

    DownloadFailure.CANCELLED ->
        "ការទាញយកត្រូវបានឈប់។ អ្វីដែលបានទាញយករួចនៅតែរក្សាទុក។"

    DownloadFailure.UNSUPPORTED ->
        "ឧបករណ៍នេះមិនអាចដំណើរការម៉ូដែលនេះបានទេ។"

    DownloadFailure.UNKNOWN ->
        "មានបញ្ហាមិនស្គាល់កើតឡើង។ សូមព្យាយាមម្តងទៀត។"
}

/**
 * A size, in the user's own digits.
 *
 * The numerals matter here: a progress line reading "៣% · 21 MB" mixes two scripts in one
 * sentence, which is exactly the sort of half-localised detail that makes an app feel
 * translated rather than written.
 */
private fun bytesLabel(bytes: Long, khmerNumerals: Boolean): String {
    val text = when {
        bytes >= 1_000_000_000L -> "%.2f GB".format(bytes / 1_000_000_000.0)
        bytes >= 1_000_000L -> "%.0f MB".format(bytes / 1_000_000.0)
        bytes >= 1_000L -> "%.0f KB".format(bytes / 1_000.0)
        else -> "$bytes B"
    }
    return if (khmerNumerals) KhmerNumerals.toKhmer(text) else text
}

private fun durationLabel(seconds: Long, khmerNumerals: Boolean): String {
    fun n(value: Long) = if (khmerNumerals) KhmerNumerals.toKhmer(value.toString()) else "$value"
    return when {
        seconds >= 3600 -> "${n(seconds / 3600)} ម៉ោង ${n((seconds % 3600) / 60)} នាទី"
        seconds >= 60 -> "${n(seconds / 60)} នាទី"
        else -> "${n(seconds)} វិនាទី"
    }
}
