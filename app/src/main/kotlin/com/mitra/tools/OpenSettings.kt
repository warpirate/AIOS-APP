package com.mitra.tools

import android.content.Context
import android.content.Intent
import android.provider.Settings

/**
 * Opens an Android system settings page so the user can toggle hardware / system features that
 * Mitra cannot change directly (Bluetooth, Wi-Fi, DND, airplane mode, etc.).
 *
 * SideEffect.None — navigation only. No state change until the user taps something on the page.
 *
 * This is the honest near-term answer to "turn Bluetooth off": Android does not grant 3rd-party
 * apps the ability to flip Bluetooth / Wi-Fi / Mobile Data / Airplane Mode without either
 * `WRITE_SECURE_SETTINGS` (ADB-only on stock Android) or an AccessibilityService doing
 * QuickSettings tile taps. Until M6's accessibility path lands, "open the system page where you
 * tap it" is what we ship — same as Gemini does for these.
 */
class OpenSettings(
    private val context: Context,
) : Tool {
    override val name = "open_settings"
    override val sideEffect = SideEffect.None

    override fun execute(args: Map<String, Any?>): ToolResult {
        val raw = argString(args["panel"]) ?: return ToolResult.Failure("I need to know which settings page")
        val panel = raw.lowercase().trim().trim('.', ',', '!', '?', ':', ';')

        val action =
            PANEL_ACTIONS[panel] ?: PANEL_ALIASES[panel]?.let { PANEL_ACTIONS[it] }
                ?: return ToolResult.Failure("I don't know a settings page called \"$raw\"")

        val intent = Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { context.startActivity(intent) }
            .fold(
                onSuccess = { ToolResult.Success("Opened ${LABELS[panel] ?: panel} settings") },
                onFailure = { ToolResult.Failure("Couldn't open that settings page") },
            )
    }

    private companion object {
        val PANEL_ACTIONS: Map<String, String> =
            mapOf(
                "bluetooth" to Settings.ACTION_BLUETOOTH_SETTINGS,
                "wifi" to Settings.ACTION_WIFI_SETTINGS,
                "dnd" to Settings.ACTION_ZEN_MODE_PRIORITY_SETTINGS, // best-effort across OEMs
                "airplane" to Settings.ACTION_AIRPLANE_MODE_SETTINGS,
                "mobile_data" to Settings.ACTION_DATA_ROAMING_SETTINGS,
                "brightness" to Settings.ACTION_DISPLAY_SETTINGS,
                "sound" to Settings.ACTION_SOUND_SETTINGS,
                "display" to Settings.ACTION_DISPLAY_SETTINGS,
                "location" to Settings.ACTION_LOCATION_SOURCE_SETTINGS,
                "battery" to Settings.ACTION_BATTERY_SAVER_SETTINGS,
                "apps" to Settings.ACTION_APPLICATION_SETTINGS,
                "storage" to Settings.ACTION_INTERNAL_STORAGE_SETTINGS,
                "nfc" to Settings.ACTION_NFC_SETTINGS,
                "hotspot" to Settings.ACTION_WIRELESS_SETTINGS,
                "data_usage" to Settings.ACTION_DATA_USAGE_SETTINGS,
                "accessibility" to Settings.ACTION_ACCESSIBILITY_SETTINGS,
                "main" to Settings.ACTION_SETTINGS,
            )

        // Common phrasings the model or the parser might pass.
        val PANEL_ALIASES: Map<String, String> =
            mapOf(
                "bluetooth settings" to "bluetooth",
                "wi-fi" to "wifi",
                "wi fi" to "wifi",
                "wifi settings" to "wifi",
                "do not disturb" to "dnd",
                "zen" to "dnd",
                "silent mode" to "dnd",
                "flight mode" to "airplane",
                "aeroplane" to "airplane",
                "airplane mode" to "airplane",
                "data" to "mobile_data",
                "mobile data" to "mobile_data",
                "cellular" to "mobile_data",
                "brightness settings" to "brightness",
                "screen" to "display",
                "audio" to "sound",
                "volume" to "sound",
                "gps" to "location",
                "battery saver" to "battery",
                "power" to "battery",
                "applications" to "apps",
                "tethering" to "hotspot",
                "wireless" to "hotspot",
                "settings" to "main",
            )

        val LABELS: Map<String, String> =
            mapOf(
                "bluetooth" to "Bluetooth",
                "wifi" to "Wi-Fi",
                "dnd" to "Do Not Disturb",
                "airplane" to "airplane mode",
                "mobile_data" to "mobile data",
                "brightness" to "display",
                "sound" to "sound",
                "display" to "display",
                "location" to "location",
                "battery" to "battery saver",
                "apps" to "apps",
                "storage" to "storage",
                "nfc" to "NFC",
                "hotspot" to "hotspot",
                "data_usage" to "data usage",
                "accessibility" to "accessibility",
                "main" to "system",
            )
    }
}
