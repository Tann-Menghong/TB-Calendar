package com.khmercalendar.ai.model

import android.content.Context
import java.io.File

/**
 * Where model bundles live on disk, and what is currently installed.
 *
 * Bundles go in the app's own files directory rather than shared storage: they are private
 * to the app, they are removed when the app is uninstalled, and no storage permission is
 * needed to write them.
 */
class ModelStore(context: Context) {

    private val appContext = context.applicationContext

    val directory: File = File(appContext.filesDir, "models").apply { mkdirs() }

    fun fileFor(spec: ModelSpec): File = File(directory, spec.fileName)

    /** A partially downloaded bundle, which is resumed rather than restarted. */
    fun partFor(spec: ModelSpec): File = File(directory, spec.fileName + ".part")

    /**
     * True when the bundle is present at exactly its published size.
     *
     * Size is checked rather than assumed. A download interrupted by the process being
     * killed leaves a plausible-looking file that fails inside the native runtime with an
     * opaque error; catching it here lets the UI say "download incomplete" instead.
     */
    fun isInstalled(spec: ModelSpec): Boolean = inspect(spec) is ModelIntegrity.Ok

    /**
     * What is actually on disk for [spec].
     *
     * This is what the model screen shows and what the engine consults before loading. The
     * distinction between [ModelIntegrity.Partial] and [ModelIntegrity.Damaged] matters to
     * the user: one is resumable and one has to be fetched again.
     */
    fun inspect(spec: ModelSpec): ModelIntegrity {
        val installed = fileFor(spec)
        if (installed.isFile) return inspect(installed, spec.sizeBytes)
        val part = partFor(spec)
        if (part.isFile) {
            return when (val partial = inspect(part, spec.sizeBytes)) {
                // A complete-looking .part never got renamed; treat it as resumable so the
                // next run finishes the job rather than reporting it as installed.
                is ModelIntegrity.Ok -> ModelIntegrity.Partial(part.length(), spec.sizeBytes)
                else -> partial
            }
        }
        return ModelIntegrity.Missing
    }

    fun installedBytes(spec: ModelSpec): Long =
        fileFor(spec).takeIf { it.isFile }?.length() ?: partFor(spec).takeIf { it.isFile }?.length() ?: 0L

    fun installed(): List<ModelSpec> = ModelCatalog.all.filter { isInstalled(it) }

    /** Deletes the bundle and any partial download. Returns bytes reclaimed. */
    fun delete(spec: ModelSpec): Long {
        var freed = 0L
        listOf(fileFor(spec), partFor(spec)).forEach { f ->
            if (f.isFile) {
                freed += f.length()
                f.delete()
            }
        }
        return freed
    }

    /** Discards only the incomplete download, keeping any installed bundle. */
    fun deletePartial(spec: ModelSpec): Long {
        val part = partFor(spec)
        if (!part.isFile) return 0L
        val freed = part.length()
        part.delete()
        return freed
    }

    /** Total space taken by every installed bundle, for the storage line in settings. */
    fun totalBytesUsed(): Long =
        directory.listFiles()?.sumOf { it.length() } ?: 0L

    /**
     * Adopts a bundle the user picked from local storage.
     *
     * Sideloading matters in Cambodia specifically: two gigabytes over a mobile connection
     * is expensive and slow, and fetching the file once on a computer and copying it across
     * is often the only practical route.
     */
    fun adopt(spec: ModelSpec, source: File): Result<Unit> = runCatching {
        require(source.isFile) { "Source file does not exist" }
        val target = fileFor(spec)
        source.copyTo(target, overwrite = true)
        val integrity = inspect(target, spec.sizeBytes)
        if (integrity !is ModelIntegrity.Ok) {
            target.delete()
            error(
                when (integrity) {
                    is ModelIntegrity.Damaged -> integrity.reason
                    is ModelIntegrity.Partial ->
                        "File is ${target.length()} bytes but ${spec.displayName} should be ${spec.sizeBytes}"
                    else -> "File could not be read"
                },
            )
        }
    }

    companion object {
        /**
         * Checks a file against the size it is supposed to be.
         *
         * Length is the whole check, and it is a real one: [ModelSpec.sizeBytes] is the exact
         * published Content-Length, so a transfer cut short anywhere is caught. That is the
         * failure that actually happens - a dropped connection, a killed process, a full disk.
         *
         * There is deliberately no magic-byte check. A `.task` bundle has no single
         * signature: the Qwen bundles are a length-prefixed zip (`PK` at offset 4) while the
         * Gemma one is a length-prefixed TFLite flatbuffer (`TFL3` at offset 4), and an
         * earlier version of this method rejected correctly downloaded files for not looking
         * like the format it guessed at. A check that fails valid input is worse than no
         * check. Catching subtler corruption - a flipped bit inside two gigabytes - needs a
         * published digest, which these bundles do not have; see docs/AI_MODELS.md.
         */
        fun inspect(file: File, expectedBytes: Long): ModelIntegrity {
            if (!file.isFile) return ModelIntegrity.Missing
            val length = file.length()
            if (length == 0L) return ModelIntegrity.Missing
            if (length < expectedBytes) return ModelIntegrity.Partial(length, expectedBytes)
            if (length > expectedBytes) {
                return ModelIntegrity.Damaged("File is $length bytes but should be $expectedBytes")
            }
            // Readable as well as present: a file the app cannot open is not installed,
            // whatever the directory listing says.
            if (!file.canRead()) return ModelIntegrity.Damaged("File could not be read")
            return ModelIntegrity.Ok
        }
    }
}
