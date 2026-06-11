package com.mitra.tools

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

/**
 * Sets the screen-off timeout (in seconds) by writing `Settings.System.SCREEN_OFF_TIMEOUT`.
 *
 * SideEffect.Reversible.
 *
 * Uses the same `WRITE_SETTINGS` grant as [SetBrightness] / [SetAutoRotate].
 *
 * Args: `seconds` = integer between 15 and 1800 (30 min). Out-of-range values clamp to the
 * nearest valid system value. Sub-15s isn't useful — phone would sleep before user reads.
 */
class SetScreenTimeout(
    private val context: Context,
) : Tool {
    override val name = "set_screen_timeout"
    override val sideEffect = SideEffect.Reversible

    override fun execute(args: Map<String, Any?>): ToolResult {
        val seconds =
            argInt(args["seconds"])?.coerceIn(15, 1800)
                ?: return ToolResult.Failure("I need a timeout in seconds, between 15 and 1800")

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
                Settings.System.SCREEN_OFF_TIMEOUT,
                seconds * 1000,
            )
            ToolResult.Success(
                when {
                    seconds % 60 == 0 -> "Screen timeout set to ${seconds / 60} min"
                    else -> "Screen timeout set to ${seconds}s"
                },
            )
        } catch (_: Exception) {
            ToolResult.Failure("Couldn't change screen timeout")
        }
    }
}
