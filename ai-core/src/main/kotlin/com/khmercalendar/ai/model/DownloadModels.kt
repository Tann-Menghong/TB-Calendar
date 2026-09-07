package com.khmercalendar.ai.model

/**
 * Why a download stopped.
 *
 * Typed rather than a bare string so the UI can offer the right action: a network failure
 * gets "retry", a full disk gets "free up space", a corrupt file gets "start again". A
 * message alone would leave the screen guessing, and the screen guessing is how a user ends
 * up tapping retry forever against a disk that will never have room.
 */
enum class DownloadFailure {
    /** Transient: connection lost, timeout, DNS. The partial file is kept. */
    NETWORK,

    /** The device ran out of room. The partial file is kept; deleting it is the user's call. */
    STORAGE_FULL,

    /** The server refused or returned something unusable. */
    SERVER,

    /** What arrived is not the file it should be. The partial file is discarded. */
    CORRUPT,

    /** The user stopped it. The partial file is kept so resuming is cheap. */
    CANCELLED,

    /** This model cannot be fetched here at all - no URL, or the device cannot host it. */
    UNSUPPORTED,

    UNKNOWN;

    companion object {
        fun fromName(name: String?): DownloadFailure =
            entries.firstOrNull { it.name == name } ?: UNKNOWN
    }
}

/**
 * A snapshot of a running download.
 *
 * [bytesPerSecond] and [etaSeconds] are null until enough of the transfer has happened to
 * mean anything; showing "0 seconds remaining" for the first second is worse than showing
 * nothing.
 */
data class DownloadProgress(
    val modelId: String,
    val downloadedBytes: Long,
    val totalBytes: Long,
    val bytesPerSecond: Long? = null,
    val etaSeconds: Long? = null,
) {
    val percent: Int
        get() = if (totalBytes <= 0) 0 else ((downloadedBytes * 100) / totalBytes).toInt().coerceIn(0, 100)
}

/** The result of checking a bundle already on disk. */
sealed interface ModelIntegrity {
    data object Ok : ModelIntegrity

    data object Missing : ModelIntegrity

    /** A `.part` file exists: the download was interrupted and can be resumed. */
    data class Partial(val downloadedBytes: Long, val totalBytes: Long) : ModelIntegrity

    /** Present but wrong - truncated, over-long, or not a readable bundle. */
    data class Damaged(val reason: String) : ModelIntegrity
}
