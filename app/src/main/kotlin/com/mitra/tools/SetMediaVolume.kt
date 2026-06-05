package com.mitra.tools

import android.content.Context
import android.media.AudioManager

/** Sets the media (music) volume to a 0–100 percentage. No permission needed for the media stream. */
class SetMediaVolume(private val context: Context) : Tool {
    override val name = "set_media_volume"
    override val sideEffect = SideEffect.Reversible

    override fun execute(args: Map<String, Any?>): ToolResult {
        val level = argInt(args["level"]) ?: return ToolResult.Failure("I need a volume level from 0 to 100")
        val percent = level.coerceIn(0, 100)
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val index = Math.round(percent / 100f * max)
        return try {
            audio.setStreamVolume(AudioManager.STREAM_MUSIC, index, 0)
            ToolResult.Success("Media volume set to $percent%")
        } catch (_: Exception) {
            // e.g. blocked while Do Not Disturb / zen mode restricts volume changes
            ToolResult.Failure("Couldn't change the volume right now")
        }
    }
}
