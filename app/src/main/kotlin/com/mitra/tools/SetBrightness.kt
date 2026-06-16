package com.mitra.tools

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

/**
 * Sets the system screen brightness 0–100% by writing `Settings.System.SCREEN_BRIGHTNESS`.
 * SideEffect.Reversible — the user can always slide it back.
 *
 * `WRITE_SETTINGS` is a "special" permission: declared in the manifest, but the user must grant it
 * via the system settings page. On first call we detect the missing grant and bounce the user into
 * `ACTION_MANAGE_WRITE_SETTINGS` so the next attempt succeeds — no silent failure.
 *
 * Switches the brightness mode out of auto so the manual value sticks. The model passes a 0–100
 * percentage; we map to the 0–255 range Android wants.
 */
class SetBrightness(
    private val context: Context,
) : Tool {
    override val name = "set_brightness"
    override val sideEffect = SideEffect.Reversible

    /** Captures BOTH the current brightness mode (manual vs. automatic) AND the current level so
     *  Undo restores both axes faithfully. Without the mode capture, "auto → manual 30 → undo"
     *  would leave the user on manual 30 instead of returning to auto. Returns null when
     *  WRITE_SETTINGS isn't granted (execute will surface the bounce). */
    override fun captureUndo(args: Map<String, Any?>): UndoSpec? {
        if (!Settings.System.canWrite(context)) return null
        return runCatching {
            val resolver = context.contentResolver
            val mode =
                Settings.System.getInt(
                    resolver,
                    Settings.System.SCREEN_BRIGHTNESS_MODE,
                    Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL,
                )
            val current = Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS)
            val pct = ((current * 100) / 255).coerceIn(0, 100)
            val undoArgs: Map<String, Any?> =
                if (mode == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC) {
                    mapOf("auto" to true)
                } else {
                    mapOf("level" to pct)
                }
            UndoSpec(toolName = name, args = undoArgs)
        }.getOrNull()
    }

    override fun execute(args: Map<String, Any?>): ToolResult {
        val auto = argBool(args["auto"]) == true
        val pct = if (auto) null else argInt(args["level"])?.coerceIn(0, 100)
        if (!auto && pct == null) {
            return ToolResult.Failure("I need a brightness from 0 to 100, or 'auto' for adaptive brightness")
        }

        if (!Settings.System.canWrite(context)) {
            val intent =
                Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS)
                    .setData(Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(intent) }
            return ToolResult.Failure("Grant brightness control to Mitra in the settings page I just opened, then ask again")
        }

        val resolver = context.contentResolver
        return try {
            if (auto) {
                Settings.System.putInt(
                    resolver,
                    Settings.System.SCREEN_BRIGHTNESS_MODE,
                    Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC,
                )
                ToolResult.Success("Brightness set to auto")
            } else {
                // Lift any auto-brightness override so the manual value is respected.
                Settings.System.putInt(
                    resolver,
                    Settings.System.SCREEN_BRIGHTNESS_MODE,
                    Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL,
                )
                val value = (pct!! * 255 / 100).coerceIn(1, 255) // 0 means "screen off" on some OEMs
                Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS, value)
                ToolResult.Success("Brightness set to $pct%")
            }
        } catch (_: Exception) {
            ToolResult.Failure("Couldn't change brightness")
        }
    }
}
