package com.khmercalendar.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.khmercalendar.update.InstalledVersion
import com.khmercalendar.update.UpdateDownload
import com.khmercalendar.update.UpdateRepository
import com.khmercalendar.update.UpdateStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class UpdateState(
    val installed: InstalledVersion,
    val status: UpdateStatus = UpdateStatus.Idle,
    val download: UpdateDownload = UpdateDownload.Idle,
    val canRequestInstall: Boolean = false,
)

/**
 * Drives the update screen.
 *
 * Checks once when the screen is first shown and then only on demand. Nothing is downloaded
 * or installed without an explicit tap.
 */
class UpdateViewModel(private val repository: UpdateRepository) : ViewModel() {

    private val _state = MutableStateFlow(
        UpdateState(
            installed = repository.installed,
            canRequestInstall = repository.canRequestInstall(),
        ),
    )
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    init {
        check()
    }

    fun check() {
        _state.update { it.copy(status = UpdateStatus.Checking) }
        viewModelScope.launch {
            val status = repository.check()
            _state.update { it.copy(status = status, download = UpdateDownload.Idle) }
        }
    }

    fun download() {
        val manifest = (_state.value.status as? UpdateStatus.Available)?.manifest ?: return
        _state.update { it.copy(download = UpdateDownload.Running(0, manifest.apkSizeBytes)) }
        viewModelScope.launch {
            // Older downloads are cleared first: an update APK is tens of megabytes of cache
            // that serves no purpose once a newer one exists.
            repository.pruneDownloads()
            val result = repository.download(manifest) { downloaded, total ->
                _state.update { it.copy(download = UpdateDownload.Running(downloaded, total)) }
            }
            _state.update {
                it.copy(
                    download = result.fold(
                        onSuccess = { file ->
                            UpdateDownload.Ready(file.absolutePath, manifest.versionName)
                        },
                        onFailure = { e ->
                            UpdateDownload.Failed(e.message ?: "Unknown error")
                        },
                    ),
                    canRequestInstall = repository.canRequestInstall(),
                )
            }
        }
    }

    /** Opens the system installer. The user confirms there; this app never installs silently. */
    fun install(context: Context, filePath: String) {
        val file = java.io.File(filePath)
        if (!file.isFile) {
            _state.update { it.copy(download = UpdateDownload.Failed("ឯកសារបាត់")) }
            return
        }
        runCatching { context.startActivity(repository.installIntent(file)) }
            .onFailure { e ->
                _state.update {
                    it.copy(download = UpdateDownload.Failed(e.message ?: "មិនអាចបើកកម្មវិធីដំឡើង"))
                }
            }
    }

    fun refreshInstallPermission() {
        _state.update { it.copy(canRequestInstall = repository.canRequestInstall()) }
    }
}
