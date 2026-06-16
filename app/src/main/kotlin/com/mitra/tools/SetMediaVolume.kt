package com.mitra.tools

import android.content.Context
import android.media.AudioManager

/** Sets the media (music) volume to a 0–100 percentage. No permission needed for the media stream. */
class SetMediaVolume(
    private val context: Context,
) : Tool {
    override val name = "set_media_volume"
    override val sideEffect = SideEffect.Reversible

    /** Reads the current media-stream volume and converts back to the 0–100 percentage args
     *  use. The rounding asymmetry between `index -> percent` here and `percent -> index` in
     *  execute introduces at most a 1-step quantisation error, well within user-noticeable. */
    override fun captureUndo(args: Map<String, Any?>): UndoSpec? {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        if (max <= 0) return null
        val current = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
        val pct = Math.round(current * 100f / max).coerceIn(0, 100)
        return UndoSpec(toolName = name, args = mapOf("level" to pct))
    }

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
