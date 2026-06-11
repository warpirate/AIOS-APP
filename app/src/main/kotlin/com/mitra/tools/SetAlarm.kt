package com.mitra.tools

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock

/**
 * Sets an alarm via the system clock app (AlarmClock intent — no permission needed).
 * Reversible: the user can cancel the alarm in their clock app.
 */
class SetAlarm(
    private val context: Context,
) : Tool {
    override val name = "set_alarm"
    override val sideEffect = SideEffect.Reversible

    override fun execute(args: Map<String, Any?>): ToolResult {
        val hour = argInt(args["hour"]) ?: return ToolResult.Failure("I need an hour for the alarm")
        val minute = argInt(args["minute"]) ?: 0
        if (hour !in 0..23 || minute !in 0..59) return ToolResult.Failure("That time doesn't look right")
        val intent =
            Intent(AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(AlarmClock.EXTRA_HOUR, hour)
                putExtra(AlarmClock.EXTRA_MINUTES, minute)
                putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                argString(args["label"])?.let { putExtra(AlarmClock.EXTRA_MESSAGE, it) }
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) // started from a non-Activity context
            }
        return try {
            context.startActivity(intent)
            ToolResult.Success(String.format("Alarm set for %02d:%02d", hour, minute))
        } catch (_: Exception) {
            ToolResult.Failure("No clock app to set the alarm")
        }
    }
}
