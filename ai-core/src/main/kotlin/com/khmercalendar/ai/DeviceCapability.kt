package com.khmercalendar.ai

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.StatFs
import androidx.core.content.getSystemService
import java.io.File

/**
 * What this phone can actually be asked to do.
 *
 * A calendar app must never assume a language model will fit. Loading a 1.5 GB bundle on a
 * 3 GB device does not fail cleanly — the process is killed by the low-memory killer, which
 * to the user looks like the calendar crashing. Everything here exists so the app can decline
 * before that happens and say why.
 */
data class DeviceProfile(
    /** Physical RAM in bytes, as reported by the platform. */
    val totalRamBytes: Long,
    /** RAM the system currently considers available. */
    val availableRamBytes: Long,
    /** True when the system is already under memory pressure. */
    val lowMemory: Boolean,
    val is64Bit: Boolean,
    val freeStorageBytes: Long,
) {
    val totalRamGb: Double get() = totalRamBytes / BYTES_PER_GB
    val availableRamGb: Double get() = availableRamBytes / BYTES_PER_GB
    val freeStorageGb: Double get() = freeStorageBytes / BYTES_PER_GB

    companion object {
        const val BYTES_PER_GB = 1024.0 * 1024.0 * 1024.0
    }
}

/** Whether a particular model can be run here, and if not, why not. */
sealed interface ModelFit {
    data object Good : ModelFit

    /** It will load, but the device will be tight while it is resident. */
    data class Tight(val reason: String) : ModelFit

    data class TooLarge(val reason: String) : ModelFit
    data class NoStorage(val reason: String) : ModelFit
    data class Unsupported(val reason: String) : ModelFit

    val runnable: Boolean get() = this is Good || this is Tight
}

object DeviceCapability {

    fun profile(context: Context, modelDir: File): DeviceProfile {
        val am = context.getSystemService<ActivityManager>()
        val info = ActivityManager.MemoryInfo()
        am?.getMemoryInfo(info)
        val stat = runCatching { StatFs(modelDir.absolutePath) }.getOrNull()
        return DeviceProfile(
            totalRamBytes = info.totalMem,
            availableRamBytes = info.availMem,
            lowMemory = info.lowMemory,
            is64Bit = Build.SUPPORTED_64_BIT_ABIS.isNotEmpty(),
            freeStorageBytes = stat?.let { it.availableBlocksLong * it.blockSizeLong } ?: 0L,
        )
    }

    /**
     * Decides whether [requiredRamBytes] of model can be hosted.
     *
     * The margin is deliberately generous. A model needs its weights resident *plus* a KV
     * cache and the app's own heap, and Android will reclaim the app long before RAM is
     * literally exhausted, so "it fits in free memory" is not the right test. Requiring
     * meaningful headroom over total RAM is what keeps the calendar alive.
     */
    fun fit(
        profile: DeviceProfile,
        requiredRamBytes: Long,
        downloadBytes: Long,
        alreadyDownloaded: Boolean,
    ): ModelFit {
        if (!profile.is64Bit) {
            return ModelFit.Unsupported("ឧបករណ៍នេះមិនមែនជាប្រព័ន្ធ 64-bit ទេ")
        }
        if (!alreadyDownloaded && profile.freeStorageBytes < downloadBytes * 1.15) {
            val needGb = "%.1f".format(downloadBytes * 1.15 / DeviceProfile.BYTES_PER_GB)
            return ModelFit.NoStorage("ត្រូវការទំហំផ្ទុកទំនេរ ${needGb}GB")
        }
        // Total RAM is the honest denominator: a device reporting 900 MB free of 3 GB will
        // still not hold a 2 GB model, because the rest is reclaimable cache, not headroom.
        if (profile.totalRamBytes < requiredRamBytes) {
            val needGb = "%.1f".format(requiredRamBytes / DeviceProfile.BYTES_PER_GB)
            val haveGb = "%.1f".format(profile.totalRamGb)
            return ModelFit.TooLarge("ត្រូវការ RAM ${needGb}GB ប៉ុន្តែឧបករណ៍មាន ${haveGb}GB")
        }
        if (profile.lowMemory) {
            return ModelFit.Tight("ឧបករណ៍កំពុងខ្វះអង្គចងចាំ — សូមបិទកម្មវិធីផ្សេងជាមុនសិន")
        }
        if (profile.availableRamBytes < requiredRamBytes * 0.6) {
            return ModelFit.Tight("អង្គចងចាំទំនេរមានតិច — ការដំណើរការអាចយឺត")
        }
        return ModelFit.Good
    }
}
