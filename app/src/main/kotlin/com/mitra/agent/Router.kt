package com.mitra.agent

/** A structured action, e.g. toggle_flashlight with {"on": true}. */
data class ToolCall(
    val name: String,
    val args: Map<String, Any?> = emptyMap(),
)

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

        // Contact lookup — possessive form first so "find priya's contact" captures "priya",
        // not "priya's contact". Matches: "what's mom's number", "find priya's phone", "raj's contact".
        Regex("""(?:what'?s|find|show|get|where'?s)\s+(.+?)'?s\s+(?:number|phone|contact|details)""")
            .find(t)?.let {
                return ToolCall("query_contacts", mapOf("name" to it.groupValues[1].trim()))
            }

        // Contact lookup — bare-noun form: "find priya", "contact raj", "number for amma".
        // Constrained to a single \w+ token so it doesn't swallow "find the brightness slider"
        // or "contact support page".
        Regex("""(?:find|contact|number for)\s+(\w+)\b""").find(t)?.let {
            return ToolCall("query_contacts", mapOf("name" to it.groupValues[1].trim()))
        }

        // Call / dial — "call mom", "dial 9876543210", "phone priya". The target is everything
        // after the verb minus trailing Indian-English fillers ("naa", "na", "haan", "please",
        // "pls", "yaar"). If the cleaned target parses as digits → number; else → name.
        Regex("""(?:call|dial|phone|ring)\s+(.+)""").find(t)?.let {
            val target = stripCallFillers(it.groupValues[1])
            if (target.isBlank()) return@let
            return if (looksLikeNumber(target)) {
                ToolCall("make_call", mapOf("number" to target))
            } else {
                ToolCall("make_call", mapOf("name" to target))
            }
        }

        // Real-API tools BEFORE the panel resolver. Each requires an explicit on/off verb so a
        // bare noun ("bluetooth?", "dnd") still falls through to the panel resolver below.
        explicitToggle(t, listOf("dnd", "do not disturb", "zen mode"))?.let {
            return ToolCall("set_dnd", mapOf("on" to it))
        }
        explicitToggle(t, listOf("bluetooth"))?.let {
            return ToolCall("set_bluetooth", mapOf("on" to it))
        }
        explicitToggle(t, listOf("auto rotate", "auto-rotate", "rotation"))?.let {
            return ToolCall("set_auto_rotate", mapOf("on" to it))
        }
        ringerMode(t)?.let { return ToolCall("set_ringer_mode", mapOf("mode" to it)) }
        if ("screen timeout" in t || "screen sleep" in t || ("screen" in t && "off after" in t)) {
            durationSeconds(t)?.let { return ToolCall("set_screen_timeout", mapOf("seconds" to it)) }
        }

        // System settings panel — handles cases without an explicit on/off verb, plus naked nouns
        // ("bluetooth?", "dnd") and toggles Android refuses 3rd-party apps (wifi / mobile / airplane).
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
                else ->
                    Regex("""(\d{1,3})\s*%?""").find(t)?.let {
                        ToolCall("set_brightness", mapOf("level" to it.groupValues[1].toInt().coerceIn(0, 100)))
                    }
            }
        }

        // Media volume: "set volume to 40", "volume 50%", "mute", "max volume"
        if (t.contains("volume") || t.contains("sound")) {
            return when {
                "mute" in t || "silent" in t -> ToolCall("set_media_volume", mapOf("level" to 0))
                "max" in t || "full" in t || "loudest" in t -> ToolCall("set_media_volume", mapOf("level" to 100))
                else ->
                    Regex("""(\d{1,3})\s*%?""").find(t)?.let {
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

    /**
     * Returns true / false if the input mentions any keyword AND an explicit on/off verb.
     * Null when no verb is present (the panel resolver below can then handle it).
     */
    private fun explicitToggle(t: String, keywords: List<String>): Boolean? {
        if (keywords.none { it in t }) return null
        val off = listOf("off", "disable", "stop", "kill", "turn off", "switch off", "lock").any { it in t }
        val on =
            listOf("turn on", "switch on", "enable", "start", "connect", "unlock").any { it in t } ||
                (" on" in t || t.endsWith(" on") || t.startsWith("on "))
        return when {
            off -> false
            on -> true
            else -> null
        }
    }

    /**
     * Detect explicit ringer-mode requests: "vibrate", "set ringer to silent", "ring mode",
     * "mute the phone" (distinct from "mute media" which goes to set_media_volume).
     */
    private fun ringerMode(t: String): String? {
        if ("vibrate" in t || "vibration" in t) return "vibrate"
        if ("ringer" in t || "ring mode" in t || "silent mode" in t || "mute the phone" in t || "mute phone" in t) {
            return when {
                "silent" in t || "mute" in t -> "silent"
                "vibrate" in t -> "vibrate"
                "ring" in t || "loud" in t || "normal" in t -> "ring"
                else -> null
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

        val keywords =
            listOf(
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

    /**
     * Strip trailing Indian-English fillers and politeness from a call target so "call blanta naa"
     * resolves to "blanta", not "blanta naa". List sourced from voice.md vocabulary.
     */
    private fun stripCallFillers(raw: String): String {
        val fillers =
            listOf(
                "naa", "na", "haan", "haina", "kya", "yaar", "matlab", "ji",
                "please", "pls", "plz", "kindly", "now", "right now",
                "right?", "no?", "ok?", "okay?", "okay na", "right na",
            )
        var s = raw.trim().trimEnd('.', '!', '?', ',')
        // Repeatedly strip the rightmost filler word until none remain.
        var changed = true
        while (changed) {
            changed = false
            for (f in fillers) {
                val pat = Regex("""\s+${Regex.escape(f)}$""")
                if (pat.containsMatchIn(s)) {
                    s = pat.replace(s, "")
                    changed = true
                }
            }
        }
        return s.trim()
    }

    /** True if the string is a plausible phone number (digits + optional + - spaces, at least 5 digits). */
    private fun looksLikeNumber(s: String): Boolean {
        val digits = s.count { it.isDigit() }
        if (digits < 5) return false
        return s.all { it.isDigit() || it == '+' || it == '-' || it == ' ' || it == '(' || it == ')' }
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
