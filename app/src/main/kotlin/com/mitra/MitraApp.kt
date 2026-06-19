package com.mitra

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import com.mitra.inference.BrainHolder
import com.mitra.inference.BrainResidentService
import com.mitra.inference.LiteRtBrain
import com.mitra.inference.ModelDownloader
import com.mitra.inference.ModelRegistry
import com.mitra.tools.TimerReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import java.io.File

/**
 * Process-lifetime container for the singleton [BrainHolder]. Activity recreation (rotation,
 * theme change, finish/relaunch, swipe-up + reopen within minutes) no longer reloads the
 * 2.6 GB Gemma 4 E2B model — the brain instance + its conversation KV cache stay alive for
 * as long as the OS keeps the process.
 *
 * Eager prewarm only happens when the model file is already on disk: on a fresh install the
 * file doesn't exist yet, the factory would fail, and [BrainHolder] treats failure as sticky.
 * For the freshly-downloaded path, AppRoot calls prewarm() itself once the user reaches
 * Phase.LOADING.
 */
class MitraApp : Application() {
    lateinit var brainHolder: BrainHolder
        private set

    override fun onCreate() {
        super.onCreate()
        val appScope = CoroutineScope(SupervisorJob())
        val modelFile = File(getExternalFilesDir(null), ModelRegistry.MODEL_FILE)
        brainHolder =
            BrainHolder(
                factory = { runCatching { LiteRtBrain(modelFile.absolutePath, cacheDir.path) }.getOrNull() },
                scope = appScope,
            )
        if (ModelDownloader(modelFile).isComplete()) {
            brainHolder.prewarm()
        }
        ensureTimerNotificationChannels()
        BrainResidentService.ensureChannel(this)
    }

    /** Two timer notification channels — silent running + audible alarm. See
     *  docs/superpowers/specs/2026-06-18-timer-live-ux-design.md. Idempotent. Deletes the
     *  legacy single-channel id from earlier builds; no-op on fresh installs. */
    private fun ensureTimerNotificationChannels() {
        val nm = getSystemService(NotificationManager::class.java) ?: return

        nm.deleteNotificationChannel(TimerReceiver.LEGACY_CHANNEL_ID)

        if (nm.getNotificationChannel(TimerReceiver.CHANNEL_RUNNING_ID) == null) {
            val running =
                NotificationChannel(
                    TimerReceiver.CHANNEL_RUNNING_ID,
                    TimerReceiver.CHANNEL_RUNNING_NAME,
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = TimerReceiver.CHANNEL_RUNNING_DESCRIPTION
                    enableVibration(false)
                    setSound(null, null)
                    setShowBadge(false)
                }
            nm.createNotificationChannel(running)
        }

        if (nm.getNotificationChannel(TimerReceiver.CHANNEL_ALARM_ID) == null) {
            val alarm =
                NotificationChannel(
                    TimerReceiver.CHANNEL_ALARM_ID,
                    TimerReceiver.CHANNEL_ALARM_NAME,
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    description = TimerReceiver.CHANNEL_ALARM_DESCRIPTION
                    enableVibration(true)
                    // Bypass Do Not Disturb so a scheduled timer alarm rings even when the user
                    // had DnD on. ACCESS_NOTIFICATION_POLICY is declared in the manifest already
                    // (for SetDnd / SetRingerMode). Matches the OS alarm clock behaviour.
                    setBypassDnd(true)
                    setSound(
                        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build(),
                    )
                }
            nm.createNotificationChannel(alarm)
        }
    }
}
