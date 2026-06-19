package com.mitra.tools

import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Active in-app timer (set by [MitraTimerScheduler.schedule], cleared on fire or cancel).
 *
 * Stores BOTH the elapsed-realtime trigger (matches AlarmManager's reference frame, monotonic,
 * unaffected by clock changes) AND the wall-clock trigger (so the persistent countdown
 * notification can use `Notification.setWhen` + `setChronometerCountDown` to render natively).
 */
data class ActiveTimer(
    val label: String,
    val totalSeconds: Int,
    val triggerAtElapsedRealtimeMs: Long,
    val triggerAtWallClockMs: Long,
) {
    fun remainingMs(now: Long = SystemClock.elapsedRealtime()): Long = (triggerAtElapsedRealtimeMs - now).coerceAtLeast(0L)
}

/**
 * Process-singleton state for the currently active tier-4 timer. Single-slot — a second
 * `schedule()` overwrites whatever was here. Observed by [com.mitra.ui.ChatScreen]'s in-chat
 * countdown pill and used by [TimerNotifications] to render the persistent shade notification.
 *
 * Kept as an `object` (not DI-injected) for two reasons: (a) the scheduler is invoked from a
 * tool's `execute()` on a background dispatcher with no DI graph available, (b) state lifetime
 * matches process lifetime exactly — when the OS kills the process the AlarmManager broadcast
 * still arrives at TimerReceiver, which is restarted with a fresh `TimerStore.value = null`.
 * The persistent notification + the AlarmManager schedule are the durable surfaces; this is
 * the soft in-process mirror for UI.
 */
object TimerStore {
    private val _active = MutableStateFlow<ActiveTimer?>(null)
    val active: StateFlow<ActiveTimer?> = _active

    fun set(timer: ActiveTimer) {
        _active.value = timer
    }

    fun clear() {
        _active.value = null
    }
}
