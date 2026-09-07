package com.khmercalendar.ai.engine

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.khmercalendar.ai.AiEngine
import com.khmercalendar.ai.AiRuntimeConfig
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * On-device inference through MediaPipe's LLM Inference API.
 *
 * Chosen over llama.cpp because it ships as an ordinary Gradle dependency with no NDK build,
 * which keeps the project buildable from a fresh clone. Nothing above [AiEngine] knows this
 * class exists; see docs/AI_MODELS.md for how to add a different backend.
 *
 * Two properties matter more than speed here:
 *
 * - **Loading is explicit and reversible.** A bundle is hundreds of megabytes to gigabytes of
 *   resident memory. Holding that while the user is reading their calendar is the difference
 *   between the process surviving in the background and being killed, so the engine is
 *   loaded on demand and unloaded when the assistant screen is left.
 * - **Inference is serialised.** A single [LlmInference] handle is not safe to drive from two
 *   coroutines, and the obvious user behaviour — retrying while a slow answer is still
 *   running — would otherwise do exactly that.
 *
 * `LlmInference` is marked deprecated in tasks-genai 0.10.35 in favour of the LiteRT-LM
 * engine API. It still works and is what the catalogue's `.task` bundles are built for;
 * migrating is a new [AiEngine] implementation and one branch in [AiEngineFactory], which is
 * exactly the change this seam exists to contain.
 */
@Suppress("DEPRECATION")
class MediaPipeEngine(
    context: Context,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : AiEngine {

    private val appContext = context.applicationContext
    private val mutex = Mutex()

    @Volatile
    private var inference: LlmInference? = null

    override val id = "mediapipe"

    override val isReady: Boolean get() = inference != null

    override suspend fun load(modelPath: String, config: AiRuntimeConfig): Result<Unit> =
        withContext(dispatcher) {
            mutex.withLock {
                if (inference != null) return@withLock Result.success(Unit)
                // runCatching catches Throwable, which matters here: a bundle built for a
                // different runtime version fails with an UnsatisfiedLinkError, not an
                // exception, and that must not be allowed to take the calendar down.
                runCatching {
                    val options = LlmInference.LlmInferenceOptions.builder()
                        .setModelPath(modelPath)
                        .setMaxTokens(config.maxTokens)
                        .build()
                    inference = LlmInference.createFromOptions(appContext, options)
                    Unit
                }.onFailure {
                    Log.e(TAG, "Could not load $modelPath", it)
                    inference = null
                }
            }
        }

    override suspend fun unload() = withContext(dispatcher) {
        mutex.withLock {
            runCatching { inference?.close() }
            inference = null
        }
    }

    override suspend fun generate(prompt: String, maxTokens: Int): Result<String> =
        withContext(dispatcher) {
            mutex.withLock {
                val engine = inference
                    ?: return@withLock Result.failure(IllegalStateException("No model is loaded"))
                runCatching { engine.generateResponse(prompt).orEmpty() }
                    .onFailure { Log.e(TAG, "Inference failed", it) }
            }
        }

    /**
     * Streaming is emulated on top of the blocking call.
     *
     * MediaPipe's async listener API delivers partial results, but wiring it through a Flow
     * requires the session variant of the API, which is not available in every bundle format
     * this catalog supports. Emitting the finished answer in chunks keeps the UI contract
     * identical for whichever backend is plugged in later, and a backend that can genuinely
     * stream simply overrides this.
     */
    override fun generateStream(prompt: String, maxTokens: Int): Flow<String> = flow {
        val result = generate(prompt, maxTokens)
        val text = result.getOrElse { throw it }
        var i = 0
        while (i < text.length) {
            val end = minOf(i + CHUNK, text.length)
            emit(text.substring(i, end))
            i = end
        }
    }

    private companion object {
        const val TAG = "MediaPipeEngine"
        const val CHUNK = 12
    }
}
