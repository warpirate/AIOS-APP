package com.mitra.agent

/** A structured action, e.g. toggle_flashlight with {"on": true}. */
data class ToolCall(val name: String, val args: Map<String, Any?> = emptyMap())

/** Turns a natural-language request into a structured [ToolCall], or null if none matches. */
interface Router {
    fun route(input: String): ToolCall?
}

/**
 * Deterministic intent parser for the known commands.
 *
 * This is the RELIABLE action path. A 0.6B LLM understands intent but does not reliably emit
 * structured tool calls (proven: it narrates "Turning on the flashlight" instead of calling), so we
 * detect the common phrasings here and act instantly. Anything this doesn't match falls through to
 * the LLM for plain conversation.
 */
class IntentParser : Router {
    override fun route(input: String): ToolCall? {
        val t = input.lowercase().trim()

        // Open a link: "open youtube.com", "go to example.org/x" — URL precedes the app match.
        Regex("""\b(?:open|go to|launch|visit|browse)\s+(\S+\.\S+)""").find(t)?.let {
            return ToolCall("open_url", mapOf("url" to it.groupValues[1].trim('.', ',', '!', '?')))
        }

        // System settings panel — handles the cases the brain misfires on, plus naked nouns
        // like "bluetooth?" / "wifi off" / "dnd". Mitra cannot toggle hardware directly; opening
        // the system page is the honest near-term answer until M6's accessibility path lands.
        // Check before flashlight/timer/alarm/volume/brightness so the panel intent wins.
        resolveSettingsPanel(t)?.let { return ToolCall("open_settings", mapOf("panel" to it)) }

        // Flashlight / torch
        if (listOf("flashlight", "flash light", "torch").any { it in t } ||
            (t.contains("light") && listOf("turn", "switch", "on", "off").any { it in t })
        ) {
            val off = listOf("off", "stop", "disable", "kill", "switch off").any { it in t }
            return ToolCall("toggle_flashlight", mapOf("on" to !off))
        }

        // Timer / countdown: "5 minute timer", "set a timer for 30 seconds"
        if (t.contains("timer") || t.contains("countdown")) {
            durationSeconds(t)?.let { return ToolCall("start_timer", mapOf("seconds" to it)) }
        }

        // Alarm: "set an alarm for 7:30", "wake me at 6 am", "alarm 7"
        if (t.contains("alarm") || t.contains("wake me")) {
            timeOfDay(t)?.let { (h, m) -> return ToolCall("set_alarm", mapOf("hour" to h, "minute" to m)) }
        }

        // Brightness: "set brightness to 40", "brightness 50%", "dim the screen", "max brightness"
        if (t.contains("brightness") || (t.contains("screen") && (t.contains("dim") || t.contains("bright")))) {
            return when {
                "max" in t || "full" in t || "brightest" in t -> ToolCall("set_brightness", mapOf("level" to 100))
                "min" in t || "dim" in t || "lowest" in t || "darkest" in t -> ToolCall("set_brightness", mapOf("level" to 10))
                else -> Regex("""(\d{1,3})\s*%?""").find(t)?.let {
                    ToolCall("set_brightness", mapOf("level" to it.groupValues[1].toInt().coerceIn(0, 100)))
                }
            }
        }

        // Media volume: "set volume to 40", "volume 50%", "mute", "max volume"
        if (t.contains("volume") || t.contains("sound")) {
            return when {
                "mute" in t || "silent" in t -> ToolCall("set_media_volume", mapOf("level" to 0))
                "max" in t || "full" in t || "loudest" in t -> ToolCall("set_media_volume", mapOf("level" to 100))
                else -> Regex("""(\d{1,3})\s*%?""").find(t)?.let {
                    ToolCall("set_media_volume", mapOf("level" to it.groupValues[1].toInt().coerceIn(0, 100)))
                }
            }
        }

        // Open an app: "open Spotify", "launch settings", "start the calculator app".
        // Last — so "start a 5 minute timer" / "set an alarm" / "open youtube.com" all win first.
        Regex("""^\s*(?:open|launch|start)\s+(?:the\s+)?(.+?)(?:\s+app)?\s*$""").find(t)?.let {
            val name = it.groupValues[1].trim().trim('.', ',', '!', '?')
            // Reject digits and obvious non-app phrases ("a 5 minute …"); keep it short.
            if (name.isNotEmpty() && name.length <= 40 && !name.any(Char::isDigit) && !name.startsWith("a ")) {
                return ToolCall("open_app", mapOf("name" to name))
            }
        }

        return null
    }

    /** "5 minutes", "30 sec", "1 hour", "90s" -> seconds. */
    private fun durationSeconds(t: String): Int? {
        val m = Regex("""(\d+)\s*(h|hr|hour|hours|m|min|minute|minutes|s|sec|second|seconds)""").find(t) ?: return null
        val n = m.groupValues[1].toInt()
        return when {
            m.groupValues[2].startsWith("h") -> n * 3600
            m.groupValues[2].startsWith("m") -> n * 60
            else -> n
        }
    }

    /**
     * Map free-text mentions of system features to a settings panel name. Only fires when the
     * user mentions a hardware/system word in a context that suggests they want it changed.
     * Returns null when the request is clearly about a tool the app actually owns (volume, etc).
     */
    private fun resolveSettingsPanel(t: String): String? {
        // Skip if the message is clearly handled by an in-app tool already.
        if ("flashlight" in t || "torch" in t) return null
        if ("alarm" in t || "timer" in t || "countdown" in t || "wake me" in t) return null
        if ("brightness" in t || "dim the screen" in t) return null
        // "volume" deliberately allowed to flow through — set_media_volume runs first only when the
        // request is concrete (a number / mute / max). The plain word "volume?" falls to sound panel.

        val keywords = listOf(
            "bluetooth" to "bluetooth",
            "wi-fi" to "wifi",
            "wi fi" to "wifi",
            "wifi" to "wifi",
            "do not disturb" to "dnd",
            "dnd" to "dnd",
            "silent mode" to "dnd",
            "zen mode" to "dnd",
            "airplane" to "airplane",
            "flight mode" to "airplane",
            "aeroplane" to "airplane",
            "mobile data" to "mobile_data",
            "cellular" to "mobile_data",
            "data roaming" to "mobile_data",
            "hotspot" to "hotspot",
            "tethering" to "hotspot",
            "nfc" to "nfc",
            "location" to "location",
            "gps" to "location",
            "battery saver" to "battery",
            "power saver" to "battery",
            "data usage" to "data_usage",
            "accessibility" to "accessibility",
        )
        return keywords.firstOrNull { (kw, _) -> kw in t }?.second
    }

    /** "7:30", "7 30", "6am", "18:00", "7" -> (hour, minute) in 24-hour form. */
    private fun timeOfDay(t: String): Pair<Int, Int>? {
        val m = Regex("""\b(\d{1,2})(?::|\.|\s)?(\d{2})?\s*(am|pm)?""").find(t) ?: return null
        var hour = m.groupValues[1].toIntOrNull() ?: return null
        val minute = m.groupValues[2].toIntOrNull() ?: 0
        when (m.groupValues[3]) {
            "pm" -> if (hour < 12) hour += 12
            "am" -> if (hour == 12) hour = 0
        }
        if (hour !in 0..23 || minute !in 0..59) return null
        return hour to minute
    }
}
