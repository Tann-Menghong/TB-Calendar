package com.khmercalendar.update

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * What a release manifest says about the newest version.
 *
 * A plain JSON file in the project's own repository rather than a service: the app has no
 * backend, no account and no analytics, and an update check should not be the thing that
 * introduces one. Publishing a release means editing this file next to the release itself.
 *
 * @property versionCode Compared against `BuildConfig.VERSION_CODE`. The only field that
 *   decides whether an update exists; version names are for people.
 * @property apkSha256 Optional lowercase hex digest. When present the downloaded file must
 *   match it or the update is refused.
 */
@Serializable
data class UpdateManifest(
    @SerialName("versionCode") val versionCode: Int,
    @SerialName("versionName") val versionName: String,
    @SerialName("apkUrl") val apkUrl: String,
    @SerialName("apkSizeBytes") val apkSizeBytes: Long = 0L,
    @SerialName("apkSha256") val apkSha256: String? = null,
    @SerialName("minSdk") val minSdk: Int = 26,
    @SerialName("releaseNotesKm") val releaseNotesKm: String = "",
    @SerialName("releaseNotesEn") val releaseNotesEn: String = "",
    @SerialName("releasePageUrl") val releasePageUrl: String? = null,
) {
    companion object {
        val json: Json = Json { ignoreUnknownKeys = true; isLenient = true }
    }
}

/** The outcome of asking whether a newer version exists. */
sealed interface UpdateStatus {
    data object Idle : UpdateStatus
    data object Checking : UpdateStatus

    /** Checked successfully; this build is the newest published one. */
    data class UpToDate(val checkedAtMillis: Long) : UpdateStatus

    data class Available(val manifest: UpdateManifest) : UpdateStatus

    /**
     * A newer version exists but this device cannot run it.
     *
     * Worth saying rather than hiding: an Android 8 device should be told the update needs a
     * newer system, not left thinking it is already current.
     */
    data class Incompatible(val manifest: UpdateManifest, val reason: String) : UpdateStatus

    data class Failed(val reason: String) : UpdateStatus
}

/** How far along an update download is. */
sealed interface UpdateDownload {
    data object Idle : UpdateDownload
    data class Running(val downloadedBytes: Long, val totalBytes: Long) : UpdateDownload {
        val percent: Int
            get() = if (totalBytes <= 0) 0 else ((downloadedBytes * 100) / totalBytes).toInt()
    }

    /** Downloaded and verified. Nothing is installed until the user confirms. */
    data class Ready(val filePath: String, val versionName: String) : UpdateDownload
    data class Failed(val reason: String) : UpdateDownload
}

/** The version this build actually is. */
data class InstalledVersion(val versionCode: Int, val versionName: String)

internal fun installedVersion(context: Context): InstalledVersion {
    val pm = context.packageManager
    val info = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(context.packageName, 0)
        }
    }.getOrNull()
    val code = when {
        info == null -> 0
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.P -> info.longVersionCode.toInt()
        else -> @Suppress("DEPRECATION") info.versionCode
    }
    return InstalledVersion(code, info?.versionName ?: "?")
}
