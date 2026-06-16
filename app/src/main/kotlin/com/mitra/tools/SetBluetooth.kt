package com.mitra.tools

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings

/**
 * Toggles Bluetooth on or off via `BluetoothAdapter.enable() / disable()`.
 *
 * SideEffect.Reversible.
 *
 * **Android version reality:**
 *   - API ≤ 30 (Android 11) — `BluetoothAdapter.enable/disable` works for any app holding
 *     `BLUETOOTH_ADMIN` (auto-granted at install). Real toggle, no UI flash.
 *   - API 31–32 (Android 12) — `enable/disable` still works but now requires runtime
 *     `BLUETOOTH_CONNECT`. Mitra bounces to app-permission settings if not granted.
 *   - API 33+ (Android 13+) — `enable/disable` are deprecated, restricted to
 *     `BLUETOOTH_PRIVILEGED` (signature-only). 3rd-party apps get no-op + return false.
 *     Mitra falls back to opening the Bluetooth settings page (same as `open_settings`).
 *
 * This honest tiered fallback is the only correct shape; pretending API 33+ has a real toggle
 * (e.g. by using AccessibilityService tile-tap) belongs in a separate, future feature.
 */
class SetBluetooth(
    private val context: Context,
) : Tool {
    override val name = "set_bluetooth"
    override val sideEffect = SideEffect.Reversible

    /** Returns the inverse `on` flag so Undo restores the prior Bluetooth state. Returns null on
     *  API 33+ (the forward call itself bounces to the settings page, so there's no Mitra-driven
     *  toggle to reverse) and on API 31+ without `BLUETOOTH_CONNECT` (can't read the adapter
     *  state safely). Missing permission paths get the bounce when execute runs, not now. */
    override fun captureUndo(args: Map<String, Any?>): UndoSpec? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !hasBluetoothConnect()) return null
        val adapter = getAdapter() ?: return null
        return runCatching {
            @Suppress("MissingPermission")
            val priorOn = adapter.isEnabled
            UndoSpec(toolName = name, args = mapOf("on" to priorOn))
        }.getOrNull()
    }

    override fun execute(args: Map<String, Any?>): ToolResult {
        val on = argBool(args["on"]) ?: return ToolResult.Failure("I need to know on or off")
        val adapter = getAdapter() ?: return ToolResult.Failure("This device has no Bluetooth")

        // API 31+ needs runtime BLUETOOTH_CONNECT to even read the adapter state safely.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !hasBluetoothConnect()) {
            bounceToAppPermissions()
            return ToolResult.Failure(
                "Grant Mitra Bluetooth permission on the page I just opened, then ask again",
            )
        }

        // API 33+: enable/disable are restricted. Bounce to the Bluetooth settings page so the
        // user toggles manually — same calibration we use for Wi-Fi / Mobile data / Airplane.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val intent =
                Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(intent) }
            return ToolResult.Failure(
                "On Android 13+ third-party apps can't flip Bluetooth directly. Opened the Bluetooth page so you can tap it.",
            )
        }

        return try {
            @Suppress("DEPRECATION", "MissingPermission")
            val ok = if (on) adapter.enable() else adapter.disable()
            if (ok) {
                ToolResult.Success(if (on) "Bluetooth on" else "Bluetooth off")
            } else {
                ToolResult.Failure("Couldn't toggle Bluetooth — system refused")
            }
        } catch (_: SecurityException) {
            ToolResult.Failure("Bluetooth permission missing — grant it and ask again")
        } catch (_: Exception) {
            ToolResult.Failure("Couldn't toggle Bluetooth")
        }
    }

    private fun getAdapter(): BluetoothAdapter? {
        val bm = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        return bm?.adapter
    }

    private fun hasBluetoothConnect(): Boolean = context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) ==
        PackageManager.PERMISSION_GRANTED

    private fun bounceToAppPermissions() {
        val intent =
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }
}
