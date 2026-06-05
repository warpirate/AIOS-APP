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
class SetBrightness(private val context: Context) : Tool {
    override val name = "set_brightness"
    override val sideEffect = SideEffect.Reversible

    override fun execute(args: Map<String, Any?>): ToolResult {
        val pct = argInt(args["level"])?.coerceIn(0, 100)
            ?: return ToolResult.Failure("I need a brightness from 0 to 100")

        if (!Settings.System.canWrite(context)) {
            val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS)
                .setData(Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(intent) }
            return ToolResult.Failure("Grant brightness control to Mitra in the settings page I just opened, then ask again")
        }

        val resolver = context.contentResolver
        return try {
            // Lift any auto-brightness override so the manual value is respected.
            Settings.System.putInt(
                resolver,
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL,
            )
            val value = (pct * 255 / 100).coerceIn(1, 255) // 0 means "screen off" on some OEMs
            Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS, value)
            ToolResult.Success("Brightness set to $pct%")
        } catch (_: Exception) {
            ToolResult.Failure("Couldn't change brightness")
        }
    }
}
