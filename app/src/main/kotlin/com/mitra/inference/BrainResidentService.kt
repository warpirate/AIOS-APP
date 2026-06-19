package com.mitra.inference

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.content.ContextCompat

/**
 * Foreground service that keeps the Mitra process resident in RAM so the ~2.6 GB Gemma 4 E2B
 * model + its conversation KV cache survive across activity teardown and most LMK pressure.
 * Without this, swiping the app from recents or even tight system RAM kills the process and
 * the next launch repeats the 6–12 s cold load. With it, re-opening the app is instant.
 *
 * The service does no work — it exists solely to hold a `startForeground` notification, which
 * promotes the process to the foreground-service oom-adj tier and exempts it from background
 * limits. The notification doubles as a trust signal: "On-device — no data leaves," reinforcing
 * Mitra's pitch every time the user pulls down the shade.
 *
 * Started from [com.mitra.MainActivity]'s loading-complete callback so the system sees the
 * service start request originate from a visible activity (avoiding
 * `ForegroundServiceStartNotAllowedException` on API 31+). `START_STICKY` so a process
 * restart re-creates the service even after a system kill.
 *
 * No bind interface; communication with the brain goes through the application-scoped
 * [BrainHolder] singleton. No wake lock; no CPU work; idle drain is dominated by RAM occupancy.
 */
class BrainResidentService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification())
        return START_STICKY
    }

    private fun buildNotification(): Notification {
        val launchIntent =
            packageManager.getLaunchIntentForPackage(packageName) ?: Intent()
        val tap =
            PendingIntent.getActivity(
                this,
                0,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setContentTitle("Mitra ready")
            .setContentText("On-device — no data leaves")
            .setOngoing(true)
            .setShowWhen(false)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setContentIntent(tap)
            .build()
    }

    companion object {
        const val CHANNEL_ID = "mitra.resident"
        const val CHANNEL_NAME = "Always ready"
        const val CHANNEL_DESCRIPTION =
            "Keeps the on-device model in memory so Mitra responds instantly without reloading. " +
                "No data ever leaves your phone."
        const val NOTIFICATION_ID = 0xB1A1

        /** Starts the service from an allowed FGS context (typically a visible activity). Idempotent. */
        fun start(context: Context) {
            val intent = Intent(context, BrainResidentService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, BrainResidentService::class.java))
        }

        /** Idempotent channel creation. Called from [com.mitra.MitraApp.onCreate]. */
        fun ensureChannel(context: Context) {
            val nm = context.getSystemService(NotificationManager::class.java) ?: return
            if (nm.getNotificationChannel(CHANNEL_ID) != null) return
            val channel =
                NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = CHANNEL_DESCRIPTION
                    setShowBadge(false)
                    enableVibration(false)
                    setSound(null, null)
                }
            nm.createNotificationChannel(channel)
        }
    }
}
