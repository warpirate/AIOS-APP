package com.mitra.tools

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager

/**
 * Turns the device torch on/off.
 *
 * SideEffect.None — instant and trivially reversible, so no confirmation gate.
 * Uses CameraManager.setTorchMode, which does NOT require the CAMERA permission.
 */
class ToggleFlashlight(private val context: Context) : Tool {
    override val name = "toggle_flashlight"
    override val sideEffect = SideEffect.None

    override fun execute(args: Map<String, Any?>): ToolResult {
        val on = args["on"] as? Boolean ?: true
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        return try {
            val cameraId = cameraManager.cameraIdList.firstOrNull { id ->
                cameraManager.getCameraCharacteristics(id)
                    .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: return ToolResult.Failure("No flashlight on this device")
            cameraManager.setTorchMode(cameraId, on)
            ToolResult.Success(if (on) "Flashlight on" else "Flashlight off")
        } catch (_: Exception) {
            // Outcome only — never log the user's request or any content.
            ToolResult.Failure("Couldn't toggle the flashlight")
        }
    }
}
