package com.khmercalendar.ai.model

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.RandomAccessFile
import java.util.concurrent.TimeUnit

/**
 * Downloads a model bundle in the background.
 *
 * A foreground worker, because these files run to gigabytes and a download that dies the
 * moment the user switches apps is worse than no download at all. Partial progress is kept
 * in a `.part` file and resumed with a Range request, so an interrupted transfer costs the
 * user the bytes already spent rather than starting over — which on a Cambodian mobile plan
 * is the difference between a usable feature and an expensive one.
 *
 * The notification is supplied by the app module through [notificationFactory] so this module
 * needs no strings, icons or channels of its own.
 */
class ModelDownloadWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    companion object {
        const val KEY_MODEL_ID = "model_id"
        const val KEY_PROGRESS = "progress"
        const val KEY_DOWNLOADED = "downloaded"
        const val KEY_TOTAL = "total"
        const val KEY_ERROR = "error"

        const val WORK_NAME = "khmercalendar_model_download"

        /**
         * Supplied by the app module: builds the foreground notification shown while the
         * download runs. Set once at application start.
         */
        @Volatile
        var notificationFactory: ((Context, ModelSpec, Int) -> ForegroundInfo)? = null

        fun enqueue(context: Context, spec: ModelSpec) {
            val request = OneTimeWorkRequestBuilder<ModelDownloadWorker>()
                .setInputData(workDataOf(KEY_MODEL_ID to spec.id))
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(WORK_NAME, androidx.work.ExistingWorkPolicy.REPLACE, request)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            // No read timeout: a slow connection on a large file is not a failure.
            .readTimeout(0, TimeUnit.SECONDS)
            .build()
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val spec = ModelCatalog.byId(inputData.getString(KEY_MODEL_ID)) ?: ModelCatalog.QWEN_0_5B
        return notificationFactory?.invoke(applicationContext, spec, 0)
            ?: throw IllegalStateException("ModelDownloadWorker.notificationFactory was not set")
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val spec = ModelCatalog.byId(inputData.getString(KEY_MODEL_ID))
            ?: return@withContext Result.failure(errorData("Unknown model"))
        val url = spec.downloadUrl
            ?: return@withContext Result.failure(errorData("This model must be imported from a file"))

        val store = ModelStore(applicationContext)
        if (store.isInstalled(spec)) return@withContext Result.success()

        val target = store.fileFor(spec)
        val part = store.partFor(spec)
        var already = if (part.isFile) part.length() else 0L
        // A partial file larger than the published size is corrupt; start again.
        if (already >= spec.sizeBytes) {
            part.delete()
            already = 0L
        }

        try {
            setForegroundSafely(spec, 0)

            val request = Request.Builder().url(url).apply {
                if (already > 0) header("Range", "bytes=$already-")
            }.build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(errorData("HTTP ${response.code}"))
                }
                // A server that ignores the Range header sends 200 and the whole file.
                if (already > 0 && response.code != 206) {
                    part.delete()
                    already = 0L
                }
                val body = response.body ?: return@withContext Result.failure(errorData("Empty response"))

                RandomAccessFile(part, "rw").use { out ->
                    out.seek(already)
                    val buffer = ByteArray(256 * 1024)
                    var written = already
                    var lastReported = -1
                    body.byteStream().use { input ->
                        while (true) {
                            if (isStopped) return@withContext Result.failure(errorData("Cancelled"))
                            val read = input.read(buffer)
                            if (read <= 0) break
                            out.write(buffer, 0, read)
                            written += read
                            val percent = ((written * 100) / spec.sizeBytes).toInt().coerceIn(0, 100)
                            if (percent != lastReported) {
                                lastReported = percent
                                setProgress(
                                    workDataOf(
                                        KEY_PROGRESS to percent,
                                        KEY_DOWNLOADED to written,
                                        KEY_TOTAL to spec.sizeBytes,
                                    )
                                )
                                setForegroundSafely(spec, percent)
                            }
                        }
                    }
                }
            }

            if (part.length() != spec.sizeBytes) {
                // Keep the partial file: the next attempt resumes rather than re-downloading.
                return@withContext Result.retry()
            }
            if (!part.renameTo(target)) {
                return@withContext Result.failure(errorData("Could not finish writing the file"))
            }
            Result.success()
        } catch (e: Throwable) {
            // Network failures are ordinary; retry keeps the partial file for the next run.
            Result.retry()
        }
    }

    private suspend fun setForegroundSafely(spec: ModelSpec, percent: Int) {
        val info = notificationFactory?.invoke(applicationContext, spec, percent) ?: return
        runCatching { setForeground(info) }
    }

    private fun errorData(message: String): Data = workDataOf(KEY_ERROR to message)
}

/** Wraps a notification in the data-sync foreground type Android 14 and later require. */
fun foregroundInfoCompat(id: Int, notification: android.app.Notification): ForegroundInfo =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        ForegroundInfo(id, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    } else {
        ForegroundInfo(id, notification)
    }
