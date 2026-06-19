package com.mitra.tools

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.SystemClock

/**
 * Tier-4 in-app timer fallback for [StartTimer]. Used when no clock app on the device honours
 * `ACTION_SET_TIMER` *or* `ACTION_SET_ALARM` — observed on OnePlus / Realme (OxygenOS / ColorOS),
 * where OPlus DeskClock's `HandleApiActivity` enforces `com.android.alarm.permission.SET_ALARM`
 * (which Mitra does not declare) so every tier-1/2/3 launch returns "Permission Denial".
 *
 * Schedules a one-shot AlarmManager broadcast at `now + seconds`. The broadcast goes to
 * [TimerReceiver], which clears [TimerStore] + posts a Done notification (replacing the
 * running notification posted here at schedule time).
 *
 * Single-slot: a second `schedule()` call overwrites the pending broadcast AND the running
 * notification (same notification id, same AlarmManager request code). Matches the single-timer
 * UX of OEM clock apps. [cancel] clears the pending broadcast, the store, and the notification.
 *
 * Exactness:
 *  - First try `setExactAndAllowWhileIdle` — survives doze.
 *  - On `SecurityException` (API 31+ revoked SCHEDULE_EXACT_ALARM) fall back to
 *    `setAndAllowWhileIdle`; timer may slip by minutes but still fires.
 */
class MitraTimerScheduler(
    private val context: Context,
) {
    enum class Result { Exact, Inexact, Failure }

    fun schedule(seconds: Int, label: String): Result {
        val alarm = context.getSystemService(AlarmManager::class.java) ?: return Result.Failure
        val triggerElapsed = SystemClock.elapsedRealtime() + seconds * 1000L
        val triggerWallClock = System.currentTimeMillis() + seconds * 1000L
        val pending = pendingIntent(label, seconds)
        val outcome =
            try {
                alarm.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerElapsed, pending)
                Result.Exact
            } catch (_: SecurityException) {
                alarm.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerElapsed, pending)
                Result.Inexact
            }
        TimerStore.set(
            ActiveTimer(
                label = label,
                totalSeconds = seconds,
                triggerAtElapsedRealtimeMs = triggerElapsed,
                triggerAtWallClockMs = triggerWallClock,
            ),
        )
        TimerNotifications.postRunning(context, label, triggerWallClock)
        return outcome
    }

    fun cancel() {
        val alarm = context.getSystemService(AlarmManager::class.java) ?: return
        alarm.cancel(pendingIntent(label = "", seconds = 0))
        TimerStore.clear()
        TimerNotifications.cancel(context)
    }

    private fun pendingIntent(label: String, seconds: Int): PendingIntent {
        val intent =
            Intent(context, TimerReceiver::class.java).apply {
                action = TimerReceiver.ACTION_FIRE
                putExtra(TimerReceiver.EXTRA_LABEL, label)
                putExtra(TimerReceiver.EXTRA_DURATION_SECONDS, seconds)
            }
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private companion object {
        const val REQUEST_CODE = 0x4D54 // 'MT'
    }
}
