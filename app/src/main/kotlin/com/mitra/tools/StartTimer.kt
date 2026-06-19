package com.mitra.tools

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.AlarmClock
import java.util.Calendar

/**
 * Starts a countdown timer. Reversible.
 *
 * OEM clocks vary in `ACTION_SET_TIMER` support — OnePlus / ColorOS in particular often advertise
 * the action via a stub that just toasts "no timer," so a bare `startActivity` looks successful
 * to us. Four-tier fallback chain:
 *
 *  1. Default resolver for `ACTION_SET_TIMER` — fastest, uses whatever clock the user picked.
 *  2. Explicit clock packages (Google Clock first, then AOSP / Samsung / OnePlus / ColorOS) — handles
 *     OxygenOS / ColorOS stubs that fail the default resolver.
 *  3. `ACTION_SET_ALARM` at now + duration — universally supported by clock apps.
 *  4. In-app fallback ([MitraTimerScheduler]) — AlarmManager broadcast + Mitra notification. Used on
 *     devices like OnePlus Nord 2T (OxygenOS 13) where every clock-app path above either resolves
 *     to a stub or returns "no clock app." See plan.md right-now #4.
 */
class StartTimer(
    private val context: Context,
) : Tool {
    override val name = "start_timer"
    override val sideEffect = SideEffect.Reversible

    override fun execute(args: Map<String, Any?>): ToolResult {
        val seconds = argInt(args["seconds"]) ?: return ToolResult.Failure("I need a duration in seconds")
        if (seconds <= 0) return ToolResult.Failure("The timer must be longer than zero")
        val label = argString(args["label"]) ?: "Timer"
        val pm = context.packageManager

        val timerIntent =
            Intent(AlarmClock.ACTION_SET_TIMER).apply {
                putExtra(AlarmClock.EXTRA_LENGTH, seconds)
                putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                putExtra(AlarmClock.EXTRA_MESSAGE, label)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

        // 1. Default resolver — fastest path; uses whatever clock the user picked.
        if (timerIntent.resolveActivity(pm) != null) {
            runCatching { context.startActivity(timerIntent) }.onSuccess {
                return ToolResult.Success(timerMessage(seconds))
            }
        }

        // 2. Explicit clock packages — handles ColorOS / OxygenOS stubs that fail the default resolver.
        for (pkg in PREFERRED_CLOCK_PACKAGES) {
            if (!isInstalled(pm, pkg)) continue
            val explicit = Intent(timerIntent).setPackage(pkg)
            if (explicit.resolveActivity(pm) == null) continue
            val launched = runCatching { context.startActivity(explicit) }.isSuccess
            if (launched) return ToolResult.Success(timerMessage(seconds))
        }

        // 3. Last resort — set an alarm at now + duration. Universally supported by clock apps.
        val at = Calendar.getInstance().apply { add(Calendar.SECOND, seconds) }
        val hour = at.get(Calendar.HOUR_OF_DAY)
        val minute = at.get(Calendar.MINUTE)
        val alarmIntent =
            Intent(AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(AlarmClock.EXTRA_HOUR, hour)
                putExtra(AlarmClock.EXTRA_MINUTES, minute)
                putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                putExtra(AlarmClock.EXTRA_MESSAGE, label)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        if (alarmIntent.resolveActivity(pm) != null) {
            val launched = runCatching { context.startActivity(alarmIntent) }.isSuccess
            if (launched) return ToolResult.Success(String.format("Alarm set for %02d:%02d (your clock has no timer)", hour, minute))
        }

        // 4. In-app fallback — AlarmManager broadcast → Mitra notification. No clock app required.
        return when (MitraTimerScheduler(context).schedule(seconds, label)) {
            MitraTimerScheduler.Result.Exact -> ToolResult.Success(timerMessage(seconds))
            MitraTimerScheduler.Result.Inexact -> ToolResult.Success("${timerMessage(seconds)} (approximate — exact alarms not granted)")
            MitraTimerScheduler.Result.Failure -> ToolResult.Failure("Couldn't start a timer on this device")
        }
    }

    private fun isInstalled(pm: PackageManager, pkg: String): Boolean =
        runCatching { pm.getPackageInfo(pkg, 0) }.isSuccess

    private fun timerMessage(seconds: Int): String =
        when {
            seconds % 60 == 0 -> "Timer started for ${seconds / 60} min"
            seconds < 60 -> "Timer started for $seconds s"
            else -> "Timer started for ${seconds / 60} min ${seconds % 60} s"
        }

    private companion object {
        val PREFERRED_CLOCK_PACKAGES =
            listOf(
                "com.google.android.deskclock", // Google Clock
                "com.android.deskclock", // AOSP / Lineage
                "com.sec.android.app.clockpackage", // Samsung
                "com.oneplus.deskclock", // OnePlus
                "com.coloros.alarmclock", // ColorOS (Realme/Oppo)
                "com.android.calculator2.clock",
            )
    }
}
