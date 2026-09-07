package com.khmercalendar.ui.settings

import android.content.Intent
import android.net.Uri
import android.provider.Settings
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.khmercalendar.update.UpdateDownload
import com.khmercalendar.update.UpdateStatus
import com.khmercalendar.ui.components.localeNumber

/**
 * Application updates.
 *
 * Nothing here happens on its own. The check runs when the screen is opened or the button is
 * tapped, the download runs when the user asks for it, and the install is Android's own
 * installer with its own confirmation. An app that can quietly replace itself is a different
 * and much larger trust request than a calendar needs to make.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateScreen(viewModel: UpdateViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("បច្ចុប្បន្នភាពកម្មវិធី") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "ត្រឡប់")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            SettingsGroup("កំណែបច្ចុប្បន្ន") {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("កំណែដែលបានដំឡើង", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "${state.installed.versionName} (${localeNumber(state.installed.versionCode)})",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            HorizontalDivider()

            when (val status = state.status) {
                UpdateStatus.Idle, UpdateStatus.Checking -> {
                    Row(
                        Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (status == UpdateStatus.Checking) {
                            CircularProgressIndicator(Modifier.height(20.dp), strokeWidth = 2.dp)
                            Text("កំពុងពិនិត្យ…", style = MaterialTheme.typography.bodyMedium)
                        } else {
                            Button(onClick = viewModel::check) { Text("ពិនិត្យបច្ចុប្បន្នភាព") }
                        }
                    }
                }

                is UpdateStatus.UpToDate -> InfoCard(
                    title = "កម្មវិធីទាន់សម័យ",
                    body = "អ្នកកំពុងប្រើកំណែថ្មីបំផុតដែលបានចេញផ្សាយ។",
                    action = { Button(onClick = viewModel::check) { Text("ពិនិត្យម្តងទៀត") } },
                )

                is UpdateStatus.Failed -> InfoCard(
                    title = "មិនអាចពិនិត្យបាន",
                    body = "សូមពិនិត្យការតភ្ជាប់អ៊ីនធឺណិត ហើយព្យាយាមម្តងទៀត។\n${status.reason}",
                    error = true,
                    action = { Button(onClick = viewModel::check) { Text("ព្យាយាមម្តងទៀត") } },
                )

                is UpdateStatus.Incompatible -> InfoCard(
                    title = "កំណែថ្មី ${status.manifest.versionName} មាន",
                    body = "ប៉ុន្តែឧបករណ៍នេះមិនអាចដំណើរការវាបានទេ៖ ${status.reason}",
                    error = true,
                )

                is UpdateStatus.Available -> {
                    val manifest = status.manifest
                    SettingsGroup("កំណែថ្មី") {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text("កំណែ", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "${manifest.versionName} (${localeNumber(manifest.versionCode)})",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        if (manifest.apkSizeBytes > 0) {
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text("ទំហំ", style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    "%.1f MB".format(manifest.apkSizeBytes / 1_000_000.0),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        manifest.releaseNotesKm.takeIf { it.isNotBlank() }?.let { notes ->
                            Text(
                                "អ្វីដែលថ្មី",
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                            )
                            Text(
                                notes,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(horizontal = 16.dp),
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    DownloadSection(
                        download = state.download,
                        canInstall = state.canRequestInstall,
                        onDownload = viewModel::download,
                        onInstall = { path -> viewModel.install(context, path) },
                        onGrantInstall = {
                            runCatching {
                                context.startActivity(
                                    Intent(
                                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                        Uri.parse("package:${context.packageName}"),
                                    ),
                                )
                            }
                        },
                        onOpenReleasePage = {
                            manifest.releasePageUrl?.let { url ->
                                runCatching {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                }
                            }
                        },
                        hasReleasePage = manifest.releasePageUrl != null,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            Text(
                "កម្មវិធីមិនទាញយក ឬដំឡើងបច្ចុប្បន្នភាពដោយស្វ័យប្រវត្តិទេ។ " +
                    "ការពិនិត្យកើតឡើងតែពេលអ្នកបើកទំព័រនេះ ឬចុចប៊ូតុងប៉ុណ្ណោះ។",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
            Spacer(Modifier.height(48.dp))
        }
    }
}

@Composable
private fun DownloadSection(
    download: UpdateDownload,
    canInstall: Boolean,
    onDownload: () -> Unit,
    onInstall: (String) -> Unit,
    onGrantInstall: () -> Unit,
    onOpenReleasePage: () -> Unit,
    hasReleasePage: Boolean,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        when (download) {
            UpdateDownload.Idle -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onDownload) { Text("ទាញយកបច្ចុប្បន្នភាព") }
                if (hasReleasePage) {
                    OutlinedButton(onClick = onOpenReleasePage) { Text("មើលទំព័រចេញផ្សាយ") }
                }
            }

            is UpdateDownload.Running -> Column {
                LinearProgressIndicator(
                    progress = { download.percent / 100f },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "${localeNumber(download.percent)}%  ·  " +
                        "%.1f".format(download.downloadedBytes / 1_000_000.0) + " / " +
                        "%.1f MB".format(download.totalBytes / 1_000_000.0),
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            is UpdateDownload.Ready -> Column {
                Text(
                    "ទាញយករួច និងផ្ទៀងផ្ទាត់រួច។ " +
                        "ការដំឡើងនឹងបើកកម្មវិធីដំឡើងរបស់ Android ដែលអ្នកត្រូវបញ្ជាក់។",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                if (canInstall) {
                    Button(onClick = { onInstall(download.filePath) }) {
                        Text("ដំឡើងកំណែ ${download.versionName}")
                    }
                } else {
                    Text(
                        "ដើម្បីដំឡើង សូមអនុញ្ញាតឱ្យកម្មវិធីនេះដំឡើងកម្មវិធីផ្សេងជាមុនសិន។",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.height(4.dp))
                    Button(onClick = onGrantInstall) { Text("បើកការកំណត់") }
                }
            }

            is UpdateDownload.Failed -> Column {
                Text(
                    "ការទាញយកបរាជ័យ៖ ${download.reason}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                Text(
                    "កម្មវិធីដែលបានដំឡើងរបស់អ្នកមិនត្រូវបានប៉ះពាល់ទេ។",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onDownload) { Text("ព្យាយាមម្តងទៀត") }
                    if (hasReleasePage) {
                        OutlinedButton(onClick = onOpenReleasePage) { Text("ទាញយកដោយដៃ") }
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoCard(
    title: String,
    body: String,
    error: Boolean = false,
    action: @Composable (() -> Unit)? = null,
) {
    Card(
        Modifier.fillMaxWidth().padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (error) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        ),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            Text(body, style = MaterialTheme.typography.bodyMedium)
            action?.let {
                Spacer(Modifier.height(8.dp))
                it()
            }
        }
    }
}
