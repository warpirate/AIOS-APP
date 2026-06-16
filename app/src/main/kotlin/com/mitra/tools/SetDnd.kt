package com.mitra.tools

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.provider.Settings

/**
 * Sets Do Not Disturb on or off via the real NotificationManager API.
 *
 * SideEffect.Reversible.
 *
 * On = INTERRUPTION_FILTER_PRIORITY (allow only priority interruptions — user's existing
 * priority rules apply). Off = INTERRUPTION_FILTER_ALL.
 *
 * `ACCESS_NOTIFICATION_POLICY` is a "special" permission like `WRITE_SETTINGS`: declared in the
 * manifest, but the user must explicitly grant via the system page. First call bounces.
 */
class SetDnd(
    private val context: Context,
) : Tool {
    override val name = "set_dnd"
    override val sideEffect = SideEffect.Reversible

    /** Reads the current interruption filter and returns the inverse `on` flag. Returns null when
     *  access isn't granted (execute will surface the bounce) or when the current filter is
     *  ALARMS / NONE (the user had picked a non-priority DND profile manually; "undo" would have
     *  to pick which they meant, so we withhold the affordance). */
    override fun captureUndo(args: Map<String, Any?>): UndoSpec? {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (!nm.isNotificationPolicyAccessGranted) return null
        val priorOn =
            when (nm.currentInterruptionFilter) {
                NotificationManager.INTERRUPTION_FILTER_ALL -> false
                NotificationManager.INTERRUPTION_FILTER_PRIORITY -> true
                else -> return null
            }
        return UndoSpec(toolName = name, args = mapOf("on" to priorOn))
    }

    override fun execute(args: Map<String, Any?>): ToolResult {
        val on = argBool(args["on"]) ?: return ToolResult.Failure("I need to know on or off")
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (!nm.isNotificationPolicyAccessGranted) {
            val intent =
                Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(intent) }
            return ToolResult.Failure(
                "Grant Mitra Do Not Disturb access on the page I just opened, then ask again",
            )
        }

        return try {
            nm.setInterruptionFilter(
                if (on) {
                    NotificationManager.INTERRUPTION_FILTER_PRIORITY
                } else {
                    NotificationManager.INTERRUPTION_FILTER_ALL
                },
            )
            ToolResult.Success(if (on) "Do Not Disturb on" else "Do Not Disturb off")
        } catch (_: Exception) {
            ToolResult.Failure("Couldn't change Do Not Disturb")
        }
    }
}
