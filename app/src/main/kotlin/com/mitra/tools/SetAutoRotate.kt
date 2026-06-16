package com.mitra.tools

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

/**
 * Turns screen auto-rotation on or off by writing `Settings.System.ACCELEROMETER_ROTATION`.
 *
 * SideEffect.Reversible.
 *
 * Uses the same `WRITE_SETTINGS` grant that [SetBrightness] needs — declared in manifest, but
 * the user must explicitly grant via the system page on first use.
 */
class SetAutoRotate(
    private val context: Context,
) : Tool {
    override val name = "set_auto_rotate"
    override val sideEffect = SideEffect.Reversible

    override fun captureUndo(args: Map<String, Any?>): UndoSpec? {
        if (!Settings.System.canWrite(context)) return null
        return runCatching {
            val current =
                Settings.System.getInt(
                    context.contentResolver,
                    Settings.System.ACCELEROMETER_ROTATION,
                    0,
                )
            UndoSpec(toolName = name, args = mapOf("on" to (current == 1)))
        }.getOrNull()
    }

    override fun execute(args: Map<String, Any?>): ToolResult {
        val on = argBool(args["on"]) ?: return ToolResult.Failure("I need to know on or off")

        if (!Settings.System.canWrite(context)) {
            val intent =
                Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS)
                    .setData(Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(intent) }
            return ToolResult.Failure(
                "Grant Mitra system-settings control on the page I just opened, then ask again",
            )
        }

        return try {
            Settings.System.putInt(
                context.contentResolver,
                Settings.System.ACCELEROMETER_ROTATION,
                if (on) 1 else 0,
            )
            ToolResult.Success(if (on) "Auto-rotate on" else "Auto-rotate off")
        } catch (_: Exception) {
            ToolResult.Failure("Couldn't change auto-rotate")
        }
    }
}
