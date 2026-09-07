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
    fun isInstalled(spec: ModelSpec): Boolean {
        val f = fileFor(spec)
        return f.isFile && f.length() == spec.sizeBytes
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
        check(target.length() == spec.sizeBytes) {
            "File is ${target.length()} bytes but ${spec.displayName} should be ${spec.sizeBytes}"
        }
    }
}
