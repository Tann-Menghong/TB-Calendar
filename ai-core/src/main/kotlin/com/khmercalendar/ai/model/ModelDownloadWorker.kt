package com.khmercalendar.ai.model

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.StatFs
import android.util.Log
import androidx.core.content.getSystemService
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.io.InterruptedIOException
import java.io.RandomAccessFile
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

/**
 * Downloads a model bundle in the background.
 *
 * A foreground worker, because these files run to gigabytes and a download that dies the
 * moment the user switches apps is worse than no download at all. Partial progress is kept
 * in a `.part` file and resumed with a Range request, so an interrupted transfer costs the
 * user the bytes already spent rather than starting over - which on a Cambodian mobile plan
 * is the difference between a usable feature and an expensive one.
 *
 * ## Why this class is defensive to the point of paranoia
 *
 * This worker used to take the whole application down. `setForeground` with
 * `FOREGROUND_SERVICE_TYPE_DATA_SYNC` throws inside `Service.onStartCommand` on the main
 * thread when the manifest does not declare the type, which is a stack no `try` in here can
 * reach. The manifest now declares it, but the lesson generalises: **a failure to download
 * an optional extra must never be able to end the calendar.** So every step below has an
 * explicit outcome, retries are bounded, and foreground promotion is treated as a nicety
 * that may be refused rather than something to depend on.
 */
class ModelDownloadWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "ModelDownload"

        const val KEY_MODEL_ID = "model_id"
        const val KEY_PROGRESS = "progress"
        const val KEY_DOWNLOADED = "downloaded"
        const val KEY_TOTAL = "total"
        const val KEY_SPEED = "bytes_per_second"
        const val KEY_ETA = "eta_seconds"
        const val KEY_ERROR = "error"
        const val KEY_FAILURE = "failure"

        const val WORK_NAME = "khmercalendar_model_download"

        /**
         * Attempts before giving up.
         *
         * Bounded on purpose. `Result.retry()` with no ceiling means a download that can
         * never succeed - no route to the host, a disk that stays full - reschedules with
         * backoff forever, and the UI shows "running" for a job that is going nowhere.
         */
        private const val MAX_ATTEMPTS = 4

        /** How often the notification and progress may be refreshed. */
        private const val PROGRESS_INTERVAL_MS = 700L

        private const val BUFFER_BYTES = 256 * 1024

        /** Headroom demanded over the file size, for the filesystem and the final rename. */
        private const val STORAGE_MARGIN = 1.10

        private const val FALLBACK_CHANNEL = "downloads"
        private const val FALLBACK_NOTIFICATION_ID = 4201

        /**
         * Supplied by the app module: builds the foreground notification shown while the
         * download runs. Optional - [fallbackForegroundInfo] covers its absence, because a
         * missing notification must not be the reason a download cannot start.
         */
        @Volatile
        var notificationFactory: ((Context, ModelSpec, Int) -> ForegroundInfo)? = null

        fun enqueue(context: Context, spec: ModelSpec) {
            val request = OneTimeWorkRequestBuilder<ModelDownloadWorker>()
                .setInputData(workDataOf(KEY_MODEL_ID to spec.id))
                .build()
            // KEEP, not REPLACE: tapping download twice should join the running download,
            // not cancel it and start again from the same byte.
            WorkManager.getInstance(context)
                .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, request)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            // No read timeout on the body: a slow connection on a large file is not a
            // failure. A stalled one is caught by the call timeout instead.
            .readTimeout(0, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    /**
     * Never throws.
     *
     * WorkManager calls this before the worker runs; an exception here fails the job before
     * `doWork` can report anything useful, so a missing notification factory degrades to a
     * plain notification built here rather than taking the download with it.
     */
    override suspend fun getForegroundInfo(): ForegroundInfo {
        val spec = ModelCatalog.byId(inputData.getString(KEY_MODEL_ID))
        return buildForegroundInfo(spec, 0)
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val spec = ModelCatalog.byId(inputData.getString(KEY_MODEL_ID))
            ?: return@withContext fail(DownloadFailure.UNSUPPORTED, "Unknown model")
        val url = spec.downloadUrl
            ?: return@withContext fail(DownloadFailure.UNSUPPORTED, "This model must be imported from a file")

        val store = ModelStore(applicationContext)
        if (store.isInstalled(spec)) return@withContext Result.success()

        val part = store.partFor(spec)
        var already = if (part.isFile) part.length() else 0L
        // A partial file at or beyond the published size is not a resumable download, it is
        // a corrupt one. Start it again rather than resuming past the end.
        if (already >= spec.sizeBytes) {
            part.delete()
            already = 0L
        }

        val remaining = spec.sizeBytes - already
        freeSpaceFor(store)?.let { free ->
            if (free < remaining * STORAGE_MARGIN) {
                return@withContext fail(
                    DownloadFailure.STORAGE_FULL,
                    "Needs ${(remaining * STORAGE_MARGIN / 1_000_000_000.0).format1()} GB free",
                )
            }
        }

        try {
            promoteToForeground(spec, percentOf(already, spec.sizeBytes))

            val request = Request.Builder().url(url).apply {
                if (already > 0) header("Range", "bytes=$already-")
            }.build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    // 4xx is the server telling us this will never work; 5xx might pass.
                    val terminal = response.code in 400..499
                    return@withContext if (terminal) {
                        fail(DownloadFailure.SERVER, "HTTP ${response.code}")
                    } else {
                        retryOrFail(DownloadFailure.SERVER, "HTTP ${response.code}")
                    }
                }
                // A server that ignores the Range header answers 200 with the whole file.
                if (already > 0 && response.code != 206) {
                    part.delete()
                    already = 0L
                }
                val body = response.body
                    ?: return@withContext retryOrFail(DownloadFailure.SERVER, "Empty response")

                val outcome = streamToFile(body.byteStream(), part, already, spec)
                if (outcome != null) return@withContext outcome
            }

            // --- validate before anything is allowed to call itself installed -------------
            when (val integrity = ModelStore.inspect(part, spec.sizeBytes)) {
                is ModelIntegrity.Partial -> {
                    // Fewer bytes than promised and the stream ended: the connection was cut.
                    // Keep the file - the next attempt resumes from here.
                    return@withContext retryOrFail(
                        DownloadFailure.NETWORK,
                        "Transfer ended early at ${integrity.downloadedBytes} of ${integrity.totalBytes}",
                    )
                }
                is ModelIntegrity.Damaged -> {
                    part.delete()
                    return@withContext fail(DownloadFailure.CORRUPT, integrity.reason)
                }
                else -> Unit
            }

            val target = store.fileFor(spec)
            if (target.exists()) target.delete()
            if (!part.renameTo(target)) {
                return@withContext fail(DownloadFailure.UNKNOWN, "Could not finish writing the file")
            }
            Log.i(TAG, "Installed ${spec.id} (${target.length()} bytes)")
            Result.success()
        } catch (e: CancellationException) {
            // The user stopped it, or the system did. Keep the partial file.
            throw e
        } catch (e: Throwable) {
            Log.w(TAG, "Download of ${spec.id} failed", e)
            retryOrFail(classify(e), e.message ?: e::class.java.simpleName)
        }
    }

    // -------------------------------------------------------------------------------------

    /**
     * Copies the body into [part], reporting progress.
     *
     * Returns null when the stream finished normally, or a terminal [Result] when it could
     * not continue - a full disk mid-transfer being the case worth naming, since it arrives
     * as an ordinary IOException that would otherwise be retried pointlessly.
     */
    private suspend fun streamToFile(
        input: java.io.InputStream,
        part: java.io.File,
        startAt: Long,
        spec: ModelSpec,
    ): Result? {
        RandomAccessFile(part, "rw").use { out ->
            out.seek(startAt)
            val buffer = ByteArray(BUFFER_BYTES)
            var written = startAt
            val startedAt = System.currentTimeMillis()
            var lastReportAt = 0L

            input.use { stream ->
                while (true) {
                    if (isStopped) {
                        publishProgress(spec, written, null, null)
                        return failCancelled()
                    }
                    val read = try {
                        stream.read(buffer)
                    } catch (e: IOException) {
                        return retryOrFail(classify(e), e.message ?: "Read failed")
                    }
                    if (read <= 0) break
                    try {
                        out.write(buffer, 0, read)
                    } catch (e: IOException) {
                        // Writing is where a full disk actually shows up.
                        val failure = if (isNoSpace(e)) DownloadFailure.STORAGE_FULL else DownloadFailure.UNKNOWN
                        return if (failure == DownloadFailure.STORAGE_FULL) {
                            fail(failure, "The device ran out of storage")
                        } else {
                            retryOrFail(failure, e.message ?: "Write failed")
                        }
                    }
                    written += read

                    val now = System.currentTimeMillis()
                    if (now - lastReportAt >= PROGRESS_INTERVAL_MS) {
                        lastReportAt = now
                        val elapsed = (now - startedAt).coerceAtLeast(1)
                        val speed = ((written - startAt) * 1000L) / elapsed
                        val eta = if (speed > 0) (spec.sizeBytes - written) / speed else null
                        publishProgress(spec, written, speed.takeIf { it > 0 }, eta)
                        promoteToForeground(spec, percentOf(written, spec.sizeBytes))
                    }
                }
            }
            publishProgress(spec, written, null, null)
        }
        return null
    }

    private suspend fun publishProgress(spec: ModelSpec, written: Long, speed: Long?, eta: Long?) {
        runCatching {
            setProgress(
                workDataOf(
                    KEY_MODEL_ID to spec.id,
                    KEY_PROGRESS to percentOf(written, spec.sizeBytes),
                    KEY_DOWNLOADED to written,
                    KEY_TOTAL to spec.sizeBytes,
                    KEY_SPEED to (speed ?: -1L),
                    KEY_ETA to (eta ?: -1L),
                ),
            )
        }
    }

    /**
     * Asks to run in the foreground, and carries on if the answer is no.
     *
     * Foreground promotion can be refused for reasons that have nothing to do with this
     * download - a background start restriction, an OEM policy, a daily data-sync quota on
     * Android 15. None of them are a reason to stop transferring bytes.
     */
    private suspend fun promoteToForeground(spec: ModelSpec, percent: Int) {
        runCatching { setForeground(buildForegroundInfo(spec, percent)) }
            .onFailure { Log.i(TAG, "Continuing without a foreground service: ${it.message}") }
    }

    private fun buildForegroundInfo(spec: ModelSpec?, percent: Int): ForegroundInfo {
        val factory = notificationFactory
        if (factory != null && spec != null) {
            runCatching { return factory(applicationContext, spec, percent) }
        }
        return fallbackForegroundInfo(percent)
    }

    /**
     * A notification built without the app module.
     *
     * Deliberately plain and in English: it exists only so a download can still run when the
     * app module has not installed its own factory, which in practice means a process
     * started for the worker alone.
     */
    private fun fallbackForegroundInfo(percent: Int): ForegroundInfo {
        val manager = applicationContext.getSystemService<NotificationManager>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && manager != null) {
            runCatching {
                manager.createNotificationChannel(
                    NotificationChannel(
                        FALLBACK_CHANNEL,
                        "Model download",
                        NotificationManager.IMPORTANCE_LOW,
                    ),
                )
            }
        }
        val notification: Notification = Notification.Builder(applicationContext, FALLBACK_CHANNEL)
            .setContentTitle("Downloading AI model")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(100, percent, false)
            .setOngoing(true)
            .build()
        return foregroundInfoCompat(FALLBACK_NOTIFICATION_ID, notification)
    }

    private fun freeSpaceFor(store: ModelStore): Long? = runCatching {
        val stat = StatFs(store.directory.absolutePath)
        stat.availableBlocksLong * stat.blockSizeLong
    }.getOrNull()

    /** Retries while attempts remain, then reports a real failure the UI can act on. */
    private fun retryOrFail(failure: DownloadFailure, message: String): Result =
        if (runAttemptCount + 1 < MAX_ATTEMPTS && failure.isTransient) {
            Log.i(TAG, "Attempt ${runAttemptCount + 1} failed ($failure: $message); retrying")
            Result.retry()
        } else {
            fail(failure, message)
        }

    private fun fail(failure: DownloadFailure, message: String): Result =
        Result.failure(workDataOf(KEY_FAILURE to failure.name, KEY_ERROR to message))

    private fun failCancelled(): Result = fail(DownloadFailure.CANCELLED, "Stopped")

    private fun classify(e: Throwable): DownloadFailure = when {
        isNoSpace(e) -> DownloadFailure.STORAGE_FULL
        e is UnknownHostException || e is SocketTimeoutException || e is InterruptedIOException ->
            DownloadFailure.NETWORK
        e is IOException -> DownloadFailure.NETWORK
        else -> DownloadFailure.UNKNOWN
    }

    private fun isNoSpace(e: Throwable): Boolean {
        var cause: Throwable? = e
        while (cause != null) {
            val m = cause.message?.lowercase().orEmpty()
            if ("enospc" in m || "no space left" in m) return true
            cause = cause.cause
        }
        return false
    }

    private fun percentOf(written: Long, total: Long): Int =
        if (total <= 0) 0 else ((written * 100) / total).toInt().coerceIn(0, 100)
}

private val DownloadFailure.isTransient: Boolean
    get() = this == DownloadFailure.NETWORK || this == DownloadFailure.SERVER

private fun Double.format1(): String = "%.1f".format(this)

/** Wraps a notification in the data-sync foreground type Android 14 and later require. */
fun foregroundInfoCompat(id: Int, notification: Notification): ForegroundInfo =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        ForegroundInfo(id, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    } else {
        ForegroundInfo(id, notification)
    }
