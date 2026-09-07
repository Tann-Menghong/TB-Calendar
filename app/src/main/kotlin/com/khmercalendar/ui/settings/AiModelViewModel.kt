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
import com.khmercalendar.ai.model.ModelCatalog
import com.khmercalendar.ai.model.ModelDownloadWorker
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
    val installed: Boolean,
    val fit: ModelFit,
    val isSelected: Boolean,
    val downloadPercent: Int? = null,
)

data class AiModelState(
    val profile: DeviceProfile? = null,
    val rows: List<ModelRow> = emptyList(),
    val loaded: Boolean = false,
    val loading: Boolean = false,
    val recommendedId: String? = null,
    val usedBytes: Long = 0L,
    val downloadingId: String? = null,
    val message: String? = null,
)

/**
 * AI model management.
 *
 * The screen this backs is the only place the app talks about memory, and it is deliberately
 * blunt about it: a model too large for the device is shown with the reason, not hidden and
 * not silently allowed to be installed and then fail at load time.
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
            val rows = ModelCatalog.all.map { spec ->
                val installed = modelStore.isInstalled(spec)
                ModelRow(
                    spec = spec,
                    installed = installed,
                    fit = DeviceCapability.fit(
                        profile = profile,
                        requiredRamBytes = spec.requiredRamBytes,
                        downloadBytes = spec.sizeBytes,
                        alreadyDownloaded = installed,
                    ),
                    isSelected = spec.id == selected,
                )
            }
            _state.update {
                it.copy(
                    profile = profile,
                    rows = rows,
                    loaded = engine.isReady,
                    recommendedId = ModelCatalog.recommendedFor(profile.totalRamBytes).id,
                    usedBytes = modelStore.totalBytesUsed(),
                )
            }
        }
    }

    private fun observeDownload() {
        viewModelScope.launch {
            WorkManager.getInstance(context)
                .getWorkInfosForUniqueWorkFlow(ModelDownloadWorker.WORK_NAME)
                .collect { infos ->
                    val running = infos.firstOrNull { !it.state.isFinished }
                    val percent = running?.progress?.getInt(ModelDownloadWorker.KEY_PROGRESS, 0)
                    _state.update { state ->
                        // WorkInfo carries no input data, so the model being fetched is the
                        // one this ViewModel enqueued; the unique work name guarantees there
                        // is only ever one.
                        val target = if (running == null) null else state.downloadingId
                        state.copy(
                            downloadingId = target,
                            rows = state.rows.map { row ->
                                row.copy(
                                    downloadPercent = percent.takeIf { target == row.spec.id },
                                )
                            },
                        )
                    }
                    if (infos.any { it.state == WorkInfo.State.SUCCEEDED }) refresh()
                }
        }
    }

    fun download(spec: ModelSpec) {
        ModelDownloadWorker.enqueue(context, spec)
        _state.update {
            it.copy(downloadingId = spec.id, message = "កំពុងទាញយក ${spec.displayName}")
        }
    }

    fun cancelDownload() {
        ModelDownloadWorker.cancel(context)
        _state.update { it.copy(downloadingId = null) }
        refresh()
    }

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
        if (!modelStore.isInstalled(spec)) {
            _state.update { it.copy(message = "សូមទាញយកម៉ូដែលជាមុនសិន") }
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
