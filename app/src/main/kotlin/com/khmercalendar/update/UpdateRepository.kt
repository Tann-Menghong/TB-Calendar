package com.khmercalendar.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Checking for, fetching and handing over an application update.
 *
 * ## What this deliberately is not
 *
 * It is not a silent updater. Android does not let a normal app install a package without the
 * user seeing and confirming the system installer, and even where a device could be coaxed
 * into it, doing so behind the user's back is exactly the behaviour a privacy-first calendar
 * should not have. So: the check runs when asked, the download runs when asked, and the
 * install is handed to the platform installer for the user to accept or refuse.
 *
 * It is also not a background poller. No network request happens unless the user opens the
 * update screen or taps check.
 */
class UpdateRepository(private val context: Context) {

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    val installed: InstalledVersion get() = installedVersion(context)

    /**
     * Fetches the manifest and compares it with this build.
     *
     * Never throws: a failed check is a state the screen shows, not a crash.
     */
    suspend fun check(): UpdateStatus = withContext(Dispatchers.IO) {
        val body = runCatching {
            client.newCall(Request.Builder().url(MANIFEST_URL).build()).execute().use { response ->
                if (!response.isSuccessful) error("HTTP ${response.code}")
                response.body?.string() ?: error("Empty response")
            }
        }.getOrElse { e ->
            Log.i(TAG, "Update check failed", e)
            return@withContext UpdateStatus.Failed(e.message ?: "Network error")
        }

        val manifest = runCatching { UpdateManifest.json.decodeFromString<UpdateManifest>(body) }
            .getOrElse { e ->
                return@withContext UpdateStatus.Failed("Malformed release manifest: ${e.message}")
            }

        val current = installed
        when {
            manifest.versionCode <= current.versionCode ->
                UpdateStatus.UpToDate(System.currentTimeMillis())

            Build.VERSION.SDK_INT < manifest.minSdk -> UpdateStatus.Incompatible(
                manifest,
                "ត្រូវការ Android API ${manifest.minSdk} ឬខ្ពស់ជាង",
            )

            else -> UpdateStatus.Available(manifest)
        }
    }

    /** Where a downloaded update lives. In the cache, so the system can reclaim it. */
    fun apkFileFor(manifest: UpdateManifest): File =
        File(updateDir(), "TB-Calendar-${manifest.versionCode}.apk")

    private fun updateDir(): File = File(context.cacheDir, "updates").apply { mkdirs() }

    /** Removes every downloaded update except [keep]. */
    fun pruneDownloads(keep: File? = null) {
        updateDir().listFiles()?.forEach { f ->
            if (f != keep) f.delete()
        }
    }

    /**
     * Downloads the update, reporting progress, and verifies it before returning.
     *
     * Verification is three checks, cheapest first: the length must match the manifest, the
     * digest must match when one is published, and - the one that actually matters - the
     * APK's signing certificate must match the installed app's. A package signed by a
     * different key cannot update this one; Android would refuse it at the end of a
     * multi-megabyte download with a message the user cannot act on, so it is refused here
     * with one they can.
     */
    suspend fun download(
        manifest: UpdateManifest,
        onProgress: (downloaded: Long, total: Long) -> Unit,
    ): Result<File> = withContext(Dispatchers.IO) {
        val target = apkFileFor(manifest)
        runCatching {
            client.newCall(Request.Builder().url(manifest.apkUrl).build()).execute().use { response ->
                if (!response.isSuccessful) error("HTTP ${response.code}")
                val body = response.body ?: error("Empty response")
                val total = if (manifest.apkSizeBytes > 0) manifest.apkSizeBytes else body.contentLength()

                target.outputStream().use { out ->
                    body.byteStream().use { input ->
                        val buffer = ByteArray(128 * 1024)
                        var written = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read <= 0) break
                            out.write(buffer, 0, read)
                            written += read
                            onProgress(written, total)
                        }
                    }
                }
            }

            if (manifest.apkSizeBytes > 0 && target.length() != manifest.apkSizeBytes) {
                error("ទំហំឯកសារមិនត្រូវគ្នា — ការទាញយកមិនពេញលេញ")
            }
            manifest.apkSha256?.lowercase()?.let { expected ->
                val actual = sha256(target)
                if (actual != expected) error("ឯកសារមិនត្រឹមត្រូវ (checksum)")
            }
            if (!signedBySameKey(target)) {
                error("ឯកសារនេះមិនបានចុះហត្ថលេខាដោយអ្នកបង្កើតដដែលទេ")
            }
            target
        }.onFailure {
            target.delete()
            Log.w(TAG, "Update download failed", it)
        }
    }

    /**
     * Hands the APK to the system installer.
     *
     * The user sees Android's own install screen and confirms there. If the app does not
     * hold the install-packages privilege the caller is told, so the screen can send the user
     * to the system setting instead of failing silently.
     */
    fun installIntent(file: File): Intent {
        val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun canRequestInstall(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
            context.packageManager.canRequestPackageInstalls()

    // ---------------------------------------------------------------------------------

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /** True when [apk] carries the same signing certificate as the running app. */
    @Suppress("DEPRECATION")
    private fun signedBySameKey(apk: File): Boolean = runCatching {
        val pm = context.packageManager
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            PackageManager.GET_SIGNATURES
        }
        val candidate = pm.getPackageArchiveInfo(apk.absolutePath, flags) ?: return false
        val mine = pm.getPackageInfo(context.packageName, flags)

        if (candidate.packageName != context.packageName) return false

        val candidateSigs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            candidate.signingInfo?.apkContentsSigners
        } else {
            candidate.signatures
        }.orEmpty().map { it.toCharsString() }.toSet()

        val mySigs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            mine.signingInfo?.apkContentsSigners
        } else {
            mine.signatures
        }.orEmpty().map { it.toCharsString() }.toSet()

        candidateSigs.isNotEmpty() && candidateSigs == mySigs
    }.getOrElse {
        Log.w(TAG, "Could not verify update signature", it)
        false
    }

    companion object {
        private const val TAG = "AppUpdate"

        /**
         * The release manifest, served from the project's own repository.
         *
         * Raw GitHub content rather than the releases API: no token, no rate limit worth
         * worrying about, and the file sits beside the code it describes.
         */
        const val MANIFEST_URL =
            "https://raw.githubusercontent.com/Tann-Menghong/TB-Calendar/main/version.json"
    }
}
