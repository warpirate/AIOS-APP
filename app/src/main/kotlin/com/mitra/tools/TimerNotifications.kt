package com.mitra.tools

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon

/**
 * Posts + cancels the two notification states for the tier-4 in-app timer:
 *  - **Running**: ongoing, non-dismissable, native count-down chronometer, "Cancel" action button.
 *  - **Done**: alarm-category, dismissable, plays the channel's alarm sound + vibration.
 *
 * Both states share [NOTIFICATION_ID] so posting `done` automatically replaces `running`.
 */
object TimerNotifications {
    // Two ids — see https://developer.android.com/reference/android/app/Notification.Builder#setOnlyAlertOnce(boolean).
    // The running notification uses setOnlyAlertOnce; posting the done notification on the SAME id
    // makes Android treat it as an update and suppress the alert tone. Distinct ids avoid that:
    // postDone first cancels the running id, then posts a fresh notification on the alarm id, so
    // the channel's sound + vibration fire.
    const val NOTIFICATION_ID_RUNNING = 0xCAFE
    const val NOTIFICATION_ID_DONE = 0xCAFE + 1

    fun postRunning(
        context: Context,
        label: String,
        triggerWallClockMs: Long,
    ) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        val cancelIcon = Icon.createWithResource(context, android.R.drawable.ic_menu_close_clear_cancel)
        val cancelAction =
            Notification.Action.Builder(cancelIcon, "Cancel", cancelPendingIntent(context))
                .build()
        val notification =
            Notification.Builder(context, TimerReceiver.CHANNEL_RUNNING_ID)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle(label)
                .setContentText("Timer running")
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
                .setWhen(triggerWallClockMs)
                .setShowWhen(true)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(Notification.CATEGORY_PROGRESS)
                .setContentIntent(openAppPendingIntent(context))
                .addAction(cancelAction)
                .build()
        nm.notify(NOTIFICATION_ID_RUNNING, notification)
    }

    fun postDone(
        context: Context,
        label: String,
        durationSeconds: Int,
    ) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        // Clear the silent running notification FIRST so the user only sees the alarm one in the
        // shade. Posting on a distinct id (not an update) ensures the alarm channel's sound +
        // vibration actually fire — `setOnlyAlertOnce(true)` on the running notification would
        // otherwise suppress a same-id replacement.
        nm.cancel(NOTIFICATION_ID_RUNNING)
        val stopIcon = Icon.createWithResource(context, android.R.drawable.ic_menu_close_clear_cancel)
        val stopAction =
            Notification.Action.Builder(stopIcon, "Stop", cancelPendingIntent(context))
                .build()
        val notification =
            Notification.Builder(context, TimerReceiver.CHANNEL_ALARM_ID)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle(label)
                .setContentText(formatDone(durationSeconds))
                .setCategory(Notification.CATEGORY_ALARM)
                .setAutoCancel(true)
                // Tapping the body opens the app AND stops the tone (cancel broadcast is the same
                // action the Stop button uses; the receiver-side cancel() also tears down the
                // notification, alarm schedule, and MediaPlayer).
                .setContentIntent(cancelPendingIntent(context))
                .setDeleteIntent(cancelPendingIntent(context))
                .addAction(stopAction)
                .build()
        nm.notify(NOTIFICATION_ID_DONE, notification)
    }

    fun cancel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.cancel(NOTIFICATION_ID_RUNNING)
        nm.cancel(NOTIFICATION_ID_DONE)
    }

    private fun cancelPendingIntent(context: Context): PendingIntent {
        val intent =
            Intent(context, TimerReceiver::class.java).apply {
                action = TimerReceiver.ACTION_CANCEL
            }
        return PendingIntent.getBroadcast(
            context,
            CANCEL_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun openAppPendingIntent(context: Context): PendingIntent? {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return null
        return PendingIntent.getActivity(
            context,
            OPEN_REQUEST_CODE,
            launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun formatDone(seconds: Int): String =
        when {
            seconds <= 0 -> "Timer done"
            seconds < 60 -> "${seconds}s timer finished"
            seconds % 60 == 0 -> "${seconds / 60} min timer finished"
            else -> "${seconds / 60} min ${seconds % 60} s timer finished"
        }

    private const val CANCEL_REQUEST_CODE = 0x4D54_C0DE.toInt()
    private const val OPEN_REQUEST_CODE = 0x4D54_0BEC.toInt()
}
