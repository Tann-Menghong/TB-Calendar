package com.khmercalendar.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.net.Uri
import androidx.core.content.getSystemService

/**
 * The notification channels the app uses.
 *
 * From Android 8 a channel's sound and vibration cannot be changed once it exists, which is
 * why the custom-sound channel is recreated under a new id when the user picks a different
 * tone. Editing the existing channel would silently do nothing - a bug that only shows up on
 * a real device, after the user has already changed the setting and believes it took effect.
 */
object NotificationChannels {

    const val REMINDERS = "reminders"
    const val DOWNLOADS = "downloads"
    private const val REMINDERS_CUSTOM_PREFIX = "reminders_custom_"

    fun ensure(context: Context, soundUri: String?, vibrate: Boolean): String {
        val manager = context.getSystemService<NotificationManager>() ?: return REMINDERS

        val downloads = NotificationChannel(
            DOWNLOADS,
            "ការទាញយកគំរូ AI",
            NotificationManager.IMPORTANCE_LOW,
        ).apply { description = "វឌ្ឍនភាពនៃការទាញយកគំរូ" }
        manager.createNotificationChannel(downloads)

        val channelId = if (soundUri.isNullOrBlank()) {
            REMINDERS
        } else {
            REMINDERS_CUSTOM_PREFIX + soundUri.hashCode().toUInt().toString(16)
        }

        // Retire channels for tones the user has moved on from, so the settings screen does
        // not accumulate a row per sound they ever tried.
        manager.notificationChannels
            .filter { it.id.startsWith(REMINDERS_CUSTOM_PREFIX) && it.id != channelId }
            .forEach { manager.deleteNotificationChannel(it.id) }

        val channel = NotificationChannel(
            channelId,
            "ការរំលឹកព្រឹត្តិការណ៍",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "ការជូនដំណឹងមុនពេលព្រឹត្តិការណ៍ចាប់ផ្តើម"
            enableVibration(vibrate)
            if (!soundUri.isNullOrBlank()) {
                setSound(
                    Uri.parse(soundUri),
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                        .build(),
                )
            }
        }
        manager.createNotificationChannel(channel)
        return channelId
    }
}
