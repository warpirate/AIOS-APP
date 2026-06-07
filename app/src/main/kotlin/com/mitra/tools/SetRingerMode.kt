package com.mitra.tools

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.provider.Settings

/**
 * Sets the system ringer to ring / vibrate / silent via the real AudioManager API.
 *
 * SideEffect.Reversible.
 *
 * Args: `mode` = "ring" | "vibrate" | "silent" | "normal" (normal aliases ring).
 *
 * Going to SILENT on Android 7+ requires `ACCESS_NOTIFICATION_POLICY` (system treats SILENT as
 * a DND-equivalent). Without that, AudioManager.setRingerMode silently downgrades to vibrate.
 * Mitra checks first and bounces if missing, same flow as [SetDnd].
 */
class SetRingerMode(private val context: Context) : Tool {
    override val name = "set_ringer_mode"
    override val sideEffect = SideEffect.Reversible

    override fun execute(args: Map<String, Any?>): ToolResult {
        val raw = argString(args["mode"]) ?: return ToolResult.Failure("I need ring, vibrate, or silent")
        val mode = when (raw.lowercase().trim()) {
            "ring", "normal", "loud", "on" -> AudioManager.RINGER_MODE_NORMAL
            "vibrate", "vib" -> AudioManager.RINGER_MODE_VIBRATE
            "silent", "mute", "off" -> AudioManager.RINGER_MODE_SILENT
            else -> return ToolResult.Failure("I don't know a ringer mode called \"$raw\"")
        }

        // SILENT requires DND-access on API 24+. Bounce if missing.
        if (mode == AudioManager.RINGER_MODE_SILENT) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (!nm.isNotificationPolicyAccessGranted) {
                val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { context.startActivity(intent) }
                return ToolResult.Failure(
                    "Silent mode needs Do Not Disturb access — grant Mitra on the page I just opened, then ask again",
                )
            }
        }

        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        return try {
            am.ringerMode = mode
            ToolResult.Success(
                when (mode) {
                    AudioManager.RINGER_MODE_NORMAL -> "Ringer on"
                    AudioManager.RINGER_MODE_VIBRATE -> "Ringer set to vibrate"
                    else -> "Ringer silenced"
                },
            )
        } catch (_: Exception) {
            ToolResult.Failure("Couldn't change ringer mode")
        }
    }
}
