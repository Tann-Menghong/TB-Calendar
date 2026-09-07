package com.khmercalendar.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.khmercalendar.ai.AiEngine
import com.khmercalendar.ai.AiRuntimeConfig
import com.khmercalendar.ai.DeviceCapability
import com.khmercalendar.ai.DeviceProfile
import com.khmercalendar.ai.ModelFit
import com.khmercalendar.ai.model.DownloadFailure
import com.khmercalendar.ai.model.ModelCatalog
import com.khmercalendar.ai.model.ModelDownloadWorker
import com.khmercalendar.ai.model.ModelIntegrity
import com.khmercalendar.ai.model.ModelSpec
import com.khmercalendar.ai.model.ModelStore
import com.khmercalendar.data.prefs.AppSettings
import com.khmercalendar.data.prefs.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One row of the model list. */
data class ModelRow(
    val spec: ModelSpec,
    val integrity: ModelIntegrity,
    val fit: ModelFit,
    val isSelected: Boolean,
) {
    val installed: Boolean get() = integrity is ModelIntegrity.Ok
    val partial: ModelIntegrity.Partial? get() = integrity as? ModelIntegrity.Partial
    val damaged: ModelIntegrity.Damaged? get() = integrity as? ModelIntegrity.Damaged
}

/** A download the user can watch and act on. */
data class ActiveDownload(
    val modelId: String,
    val displayName: String,
    val percent: Int,
    val downloadedBytes: Long,
    val totalBytes: Long,
    val bytesPerSecond: Long?,
    val etaSeconds: Long?,
    /** Enqueued but not yet transferring - waiting on the scheduler. */
    val waiting: Boolean = false,
)

/** A download that stopped, and what the user can do about it. */
data class DownloadProblem(
    val modelId: String?,
    val failure: DownloadFailure,
    val detail: String?,
)

data class AiModelState(
    val profile: DeviceProfile? = null,
    val rows: List<ModelRow> = emptyList(),
    val loaded: Boolean = false,
    val loading: Boolean = false,
    val recommendedId: String? = null,
    val usedBytes: Long = 0L,
    val download: ActiveDownload? = null,
    val problem: DownloadProblem? = null,
    /** Set while the size-and-cost confirmation is on screen. */
    val confirming: ModelSpec? = null,
    val message: String? = null,
)

/**
 * AI model management.
 *
 * The screen this backs is the only place the app talks about memory and network cost, and
 * it is deliberately blunt about both: a model too large for the device is shown with the
 * reason rather than hidden, and a multi-gigabyte download is confirmed before it starts
 * rather than after the data is spent.
 */
class AiModelViewModel(
    private val context: Context,
    private val modelStore: ModelStore,
    private val engine: AiEngine,
    private val settingsStore: SettingsStore,
    private val settings: StateFlow<AppSettings>,
) : ViewModel() {

    private val _state = MutableStateFlow(AiModelState())
    val state: StateFlow<AiModelState> = _state.asStateFlow()

    val prefs: StateFlow<AppSettings> = settings

    init {
        refresh()
        observeDownload()
    }

    fun refresh() {
        viewModelScope.launch {
            val profile = withContext(Dispatchers.IO) {
                DeviceCapability.profile(context, modelStore.directory)
            }
            val selected = settings.value.aiModelId
            val rows = withContext(Dispatchers.IO) {
                ModelCatalog.all.map { spec ->
                    val integrity = modelStore.inspect(spec)
                    ModelRow(
                        spec = spec,
                        integrity = integrity,
                        fit = DeviceCapability.fit(
                            profile = profile,
                            requiredRamBytes = spec.requiredRamBytes,
                            downloadBytes = spec.sizeBytes,
                            alreadyDownloaded = integrity is ModelIntegrity.Ok,
                        ),
                        isSelected = spec.id == selected,
                    )
                }
            }
            _state.update {
                it.copy(
                    profile = profile,
                    rows = rows,
                    loaded = engine.isReady,
                    recommendedId = ModelCatalog.recommendedFor(profile.totalRamBytes).id,
                    usedBytes = withContext(Dispatchers.IO) { modelStore.totalBytesUsed() },
                )
            }
        }
    }

    /**
     * Mirrors the download worker into UI state.
     *
     * The worker's own output is the source of truth for both progress and failure, so a
     * download that started before this screen was opened - or survived the screen being
     * closed - shows up correctly rather than appearing not to exist.
     */
    private fun observeDownload() {
        viewModelScope.launch {
            WorkManager.getInstance(context)
                .getWorkInfosForUniqueWorkFlow(ModelDownloadWorker.WORK_NAME)
                .collect { infos ->
                    val info = infos.lastOrNull()
                    when (info?.state) {
                        WorkInfo.State.RUNNING, WorkInfo.State.ENQUEUED -> applyRunning(info)
                        WorkInfo.State.FAILED -> applyFailure(info)
                        WorkInfo.State.SUCCEEDED -> {
                            _state.update { it.copy(download = null, problem = null) }
                            refresh()
                        }
                        WorkInfo.State.CANCELLED -> _state.update { it.copy(download = null) }
                        else -> _state.update { it.copy(download = null) }
                    }
                }
        }
    }

    private fun applyRunning(info: WorkInfo) {
        val progress = info.progress
        val id = progress.getString(ModelDownloadWorker.KEY_MODEL_ID)
            ?: _state.value.download?.modelId
            ?: pendingModelId
        if (id == null) {
            // Enqueued by a previous process and no progress reported yet; there is nothing
            // to name on screen until the worker publishes its first update.
            _state.update { it.copy(problem = null) }
            return
        }
        val spec = ModelCatalog.byId(id)
        val total = progress.getLong(ModelDownloadWorker.KEY_TOTAL, spec?.sizeBytes ?: 0L)
        val downloaded = progress.getLong(ModelDownloadWorker.KEY_DOWNLOADED, 0L)
        _state.update {
            it.copy(
                problem = null,
                download = ActiveDownload(
                    modelId = id,
                    displayName = spec?.displayName.orEmpty(),
                    percent = progress.getInt(ModelDownloadWorker.KEY_PROGRESS, 0),
                    downloadedBytes = downloaded,
                    totalBytes = total,
                    bytesPerSecond = progress.getLong(ModelDownloadWorker.KEY_SPEED, -1L)
                        .takeIf { v -> v > 0 },
                    etaSeconds = progress.getLong(ModelDownloadWorker.KEY_ETA, -1L)
                        .takeIf { v -> v >= 0 },
                    waiting = info.state == WorkInfo.State.ENQUEUED || downloaded == 0L,
                ),
            )
        }
    }

    private fun applyFailure(info: WorkInfo) {
        val failure = DownloadFailure.fromName(
            info.outputData.getString(ModelDownloadWorker.KEY_FAILURE),
        )
        _state.update {
            it.copy(
                download = null,
                problem = DownloadProblem(
                    modelId = it.download?.modelId ?: pendingModelId,
                    failure = failure,
                    detail = info.outputData.getString(ModelDownloadWorker.KEY_ERROR),
                ),
            )
        }
        refresh()
    }

    /** Remembers which model the user asked for, since WorkInfo carries no input data. */
    private var pendingModelId: String? = null

    // --- download actions -----------------------------------------------------------------

    /** Opens the confirmation. Nothing is fetched until [confirmDownload]. */
    fun requestDownload(spec: ModelSpec) {
        _state.update { it.copy(confirming = spec, problem = null) }
    }

    fun dismissConfirm() = _state.update { it.copy(confirming = null) }

    fun confirmDownload() {
        val spec = _state.value.confirming ?: return
        pendingModelId = spec.id
        ModelDownloadWorker.enqueue(context, spec)
        _state.update {
            it.copy(
                confirming = null,
                problem = null,
                download = ActiveDownload(
                    modelId = spec.id,
                    displayName = spec.displayName,
                    percent = 0,
                    downloadedBytes = modelStore.installedBytes(spec),
                    totalBytes = spec.sizeBytes,
                    bytesPerSecond = null,
                    etaSeconds = null,
                    waiting = true,
                ),
            )
        }
    }

    /** Resumes an interrupted or paused download from the bytes already on disk. */
    fun resume(spec: ModelSpec) {
        pendingModelId = spec.id
        ModelDownloadWorker.enqueue(context, spec)
        _state.update { it.copy(problem = null) }
    }

    /** Stops the transfer but keeps the partial file, so resuming costs nothing. */
    fun pause() {
        ModelDownloadWorker.cancel(context)
        _state.update { it.copy(download = null) }
        refresh()
    }

    /** Stops and throws away what was downloaded. */
    fun cancelAndDiscard(spec: ModelSpec) {
        ModelDownloadWorker.cancel(context)
        viewModelScope.launch {
            withContext(Dispatchers.IO) { modelStore.deletePartial(spec) }
            _state.update { it.copy(download = null, problem = null) }
            refresh()
        }
    }

    fun dismissProblem() = _state.update { it.copy(problem = null) }

    // --- model actions --------------------------------------------------------------------

    fun select(spec: ModelSpec) {
        viewModelScope.launch {
            settingsStore.setAiModel(spec.id)
            refresh()
        }
    }

    fun delete(spec: ModelSpec) {
        viewModelScope.launch {
            if (settings.value.aiModelId == spec.id) {
                engine.unload()
                settingsStore.setAiModel(null)
            }
            withContext(Dispatchers.IO) { modelStore.delete(spec) }
            refresh()
        }
    }

    fun load() {
        val spec = ModelCatalog.byId(settings.value.aiModelId) ?: return
        val integrity = modelStore.inspect(spec)
        if (integrity !is ModelIntegrity.Ok) {
            _state.update {
                it.copy(
                    message = when (integrity) {
                        is ModelIntegrity.Partial -> "ការទាញយកមិនទាន់ចប់ — សូមបន្តទាញយកជាមុនសិន"
                        is ModelIntegrity.Damaged -> "ឯកសារម៉ូដែលខូច — សូមទាញយកម្តងទៀត"
                        else -> "សូមទាញយកម៉ូដែលជាមុនសិន"
                    },
                )
            }
            return
        }
        _state.update { it.copy(loading = true) }
        viewModelScope.launch {
            val prefs = settings.value
            val result = engine.load(
                modelPath = modelStore.fileFor(spec).absolutePath,
                config = AiRuntimeConfig(
                    maxTokens = prefs.aiMaxTokens,
                    temperature = prefs.aiTemperature,
                ),
            )
            _state.update {
                it.copy(
                    loading = false,
                    loaded = engine.isReady,
                    message = result.exceptionOrNull()?.let { e -> "ផ្ទុកមិនបាន៖ ${e.message}" },
                )
            }
        }
    }

    fun unload() {
        viewModelScope.launch {
            engine.unload()
            _state.update { it.copy(loaded = false) }
        }
    }

    fun setEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsStore.setAiEnabled(enabled)
            if (!enabled) {
                engine.unload()
                _state.update { it.copy(loaded = false) }
            }
        }
    }

    fun setMaxTokens(value: Int) {
        viewModelScope.launch { settingsStore.setAiMaxTokens(value) }
    }

    fun setTemperature(value: Float) {
        viewModelScope.launch { settingsStore.setAiTemperature(value) }
    }

    fun dismissMessage() = _state.update { it.copy(message = null) }
}
