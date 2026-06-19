package com.mitra.tools

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log

/**
 * Two broadcast paths:
 *  - [ACTION_FIRE]: scheduled by [MitraTimerScheduler] via AlarmManager. Clears [TimerStore],
 *    posts the Done notification on the alarm channel, plays the alarm tone via [MediaPlayer]
 *    with `STREAM_ALARM` + `USAGE_ALARM` so it survives DnD / channel-sound OEM quirks, and
 *    vibrates the device. The MediaPlayer is auto-stopped after [MAX_PLAY_MS].
 *  - [ACTION_CANCEL]: targeted by the "Cancel" action button on the running notification, or
 *    by the in-chat cancel button. Defers to [MitraTimerScheduler.cancel] which tears down the
 *    AlarmManager schedule, the store entry, and the notification. Also stops any in-flight
 *    alarm tone left over from a prior fire (defensive).
 *
 * Notification channel is created up-front in [com.mitra.MitraApp.onCreate]; this receiver is
 * a thin dispatcher.
 *
 * On Android 13+, POST_NOTIFICATIONS is a runtime permission; if the user denied it the Done
 * notification will silently drop. The timer still fires audibly through the MediaPlayer path.
 */
class TimerReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        when (intent.action) {
            ACTION_FIRE -> {
                val label = intent.getStringExtra(EXTRA_LABEL)?.ifBlank { null } ?: "Timer"
                val seconds = intent.getIntExtra(EXTRA_DURATION_SECONDS, 0)
                Log.i(TAG, "ACTION_FIRE label=$label seconds=$seconds")
                TimerStore.clear()
                TimerNotifications.postDone(context, label, seconds)
                // Channel-sound on ColorOS / OxygenOS doesn't reliably fire even with a fresh
                // notification id on a HIGH-importance channel (verified on Realme CPH2401 +
                // OnePlus Nord 2T family). MediaPlayer with explicit STREAM_ALARM + USAGE_ALARM
                // bypasses the notification-sound pipeline entirely.
                playAlarmTone(context.applicationContext)
                vibrateAlarm(context)
            }
            ACTION_CANCEL -> {
                Log.i(TAG, "ACTION_CANCEL")
                stopAlarmTone()
                MitraTimerScheduler(context).cancel()
            }
        }
    }

    private fun playAlarmTone(appContext: Context) {
        synchronized(playerLock) {
            stopAlarmToneLocked()
            try {
                val uri: Uri =
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                        ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                        ?: run {
                            Log.w(TAG, "no default alarm/notification URI on device")
                            return
                        }
                val player = MediaPlayer()
                val attrs =
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                player.setAudioAttributes(attrs)
                @Suppress("DEPRECATION")
                player.setAudioStreamType(AudioManager.STREAM_ALARM)
                player.setDataSource(appContext, uri)
                player.isLooping = false
                player.setOnCompletionListener { stopAlarmTone() }
                player.setOnErrorListener { _, what, extra ->
                    Log.w(TAG, "MediaPlayer error what=$what extra=$extra")
                    stopAlarmTone()
                    true
                }
                player.prepare()
                player.start()
                activePlayer = player
                Log.i(TAG, "alarm tone playing duration=${player.duration}ms uri=$uri")
                Handler(Looper.getMainLooper()).postDelayed({ stopAlarmTone() }, MAX_PLAY_MS)
            } catch (t: Throwable) {
                Log.w(TAG, "playAlarmTone failed: ${t.javaClass.simpleName}: ${t.message}")
                stopAlarmToneLocked()
            }
        }
    }

    private fun vibrateAlarm(context: Context) {
        try {
            val vibrator: Vibrator? =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    context.getSystemService(VibratorManager::class.java)?.defaultVibrator
                } else {
                    @Suppress("DEPRECATION")
                    context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                }
            vibrator ?: return
            val pattern = longArrayOf(0, 800, 300, 800, 300, 800)
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
        } catch (_: Exception) {
        }
    }

    private fun stopAlarmTone() {
        synchronized(playerLock) { stopAlarmToneLocked() }
    }

    private fun stopAlarmToneLocked() {
        activePlayer?.let { p ->
            try {
                if (p.isPlaying) p.stop()
            } catch (_: IllegalStateException) {
            }
            try {
                p.release()
            } catch (_: Exception) {
            }
        }
        activePlayer = null
    }

    companion object {
        const val ACTION_FIRE = "com.mitra.tools.action.TIMER_FIRE"
        const val ACTION_CANCEL = "com.mitra.tools.action.TIMER_CANCEL"
        const val EXTRA_LABEL = "label"
        const val EXTRA_DURATION_SECONDS = "duration_seconds"

        // Two channels — see docs/superpowers/specs/2026-06-18-timer-live-ux-design.md.
        // Running posts at schedule time; silent so the user is not alerted twice.
        const val CHANNEL_RUNNING_ID = "mitra.timers.running"
        const val CHANNEL_RUNNING_NAME = "Timer running"
        const val CHANNEL_RUNNING_DESCRIPTION = "Silent ongoing notification while an in-app timer is counting down. No sound."

        // Alarm posts at fire time. THIS is what the user hears.
        const val CHANNEL_ALARM_ID = "mitra.timers.alarm"
        const val CHANNEL_ALARM_NAME = "Timer alarm"
        const val CHANNEL_ALARM_DESCRIPTION = "Plays when an in-app timer reaches zero. Default alarm sound + vibration."

        // Legacy id deleted at upgrade time — see MitraApp.ensureTimerNotificationChannels.
        const val LEGACY_CHANNEL_ID = "mitra.timers"

        private const val TAG = "MitraTimer"

        /** Stop the MediaPlayer after this many ms even if the tone hasn't completed naturally,
         *  so a missed cancel never leaves the alarm ringing indefinitely. */
        private const val MAX_PLAY_MS = 30_000L

        private val playerLock = Any()

        @Volatile
        private var activePlayer: MediaPlayer? = null
    }
}
