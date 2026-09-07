package com.khmercalendar.ai.engine

import android.content.Context
import android.util.Log
import com.khmercalendar.ai.AiEngine
import com.khmercalendar.ai.NoOpAiEngine

/**
 * The single place that decides which backend to build.
 *
 * Adding llama.cpp, LiteRT or an NPU runtime means writing one [AiEngine] and adding a branch
 * here. Nothing else in the app refers to a concrete engine, which is what lets the model be
 * replaced without touching the calendar.
 */
object AiEngineFactory {

    private const val TAG = "AiEngineFactory"

    /**
     * Returns the best backend this build and device can offer.
     *
     * The MediaPipe classes are on the classpath in the normal build, but a stripped or
     * minified variant may not have them, and a device with an incompatible ABI fails at
     * class-load time rather than at call time. Probing here means the AI screen can say
     * "unavailable on this device" instead of the app crashing on first use.
     */
    fun create(context: Context): AiEngine =
        if (isMediaPipeAvailable()) MediaPipeEngine(context) else NoOpAiEngine

    fun isMediaPipeAvailable(): Boolean = runCatching {
        Class.forName("com.google.mediapipe.tasks.genai.llminference.LlmInference")
        true
    }.getOrElse {
        Log.i(TAG, "MediaPipe GenAI is not present in this build; AI features are disabled")
        false
    }
}
