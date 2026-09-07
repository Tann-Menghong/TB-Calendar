package com.khmercalendar.ai

import kotlinx.coroutines.flow.Flow

/**
 * A local text-generation backend.
 *
 * Everything above this interface — the assistant, the calendar tools, the UI — is written
 * against it and never against a particular runtime. Swapping MediaPipe for llama.cpp, LiteRT
 * or anything else is a new implementation of this interface plus one branch in
 * [com.khmercalendar.ai.engine.AiEngineFactory]; no calendar code changes. That is the whole
 * point of keeping this in its own module.
 *
 * Implementations must be safe to call when no model is installed: they report
 * [isReady] false rather than throwing.
 */
interface AiEngine {

    val id: String

    /** True once a model is loaded in memory and [generate] can be called. */
    val isReady: Boolean

    /**
     * Loads [modelPath] into memory.
     *
     * @return success, or a failure carrying a message fit to show the user. Never throws.
     */
    suspend fun load(modelPath: String, config: AiRuntimeConfig): Result<Unit>

    /** Frees the model. Safe to call when nothing is loaded. */
    suspend fun unload()

    /** Generates a full response. */
    suspend fun generate(prompt: String, maxTokens: Int = 256): Result<String>

    /**
     * Generates a response token by token.
     *
     * The assistant streams so the user sees the model working; on a mid-range phone a
     * 200-token answer can take several seconds and a frozen screen reads as a hang.
     */
    fun generateStream(prompt: String, maxTokens: Int = 256): Flow<String>
}

/**
 * Runtime knobs the user can change in AI settings.
 *
 * @property maxTokens Upper bound on a response, which is also the main lever on how long
 *   the user waits.
 * @property temperature 0 makes extraction deterministic; higher values suit free-form
 *   answers.
 * @property topK Sampling width.
 */
data class AiRuntimeConfig(
    val maxTokens: Int = 512,
    val temperature: Float = 0.2f,
    val topK: Int = 40,
) {
    companion object {
        /** Structured extraction wants the most predictable output the model can give. */
        val EXTRACTION = AiRuntimeConfig(maxTokens = 256, temperature = 0f, topK = 1)
    }
}

/** An engine that is always present and never does anything, for devices with no model. */
object NoOpAiEngine : AiEngine {
    override val id = "none"
    override val isReady = false
    override suspend fun load(modelPath: String, config: AiRuntimeConfig): Result<Unit> =
        Result.failure(IllegalStateException("No inference backend is available on this device"))

    override suspend fun unload() = Unit
    override suspend fun generate(prompt: String, maxTokens: Int): Result<String> =
        Result.failure(IllegalStateException("No model is loaded"))

    override fun generateStream(prompt: String, maxTokens: Int): Flow<String> =
        kotlinx.coroutines.flow.emptyFlow()
}
