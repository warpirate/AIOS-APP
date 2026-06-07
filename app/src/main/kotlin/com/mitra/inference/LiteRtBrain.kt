package com.mitra.inference

import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.SamplerConfig
import com.google.ai.edge.litertlm.Tool
import com.google.ai.edge.litertlm.ToolParam
import com.google.ai.edge.litertlm.ToolSet
import com.google.ai.edge.litertlm.tool
import com.mitra.agent.ToolCall
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import org.json.JSONObject

/** One turn of the brain: the (growing) reply text, plus optionally one device action the model called. */
data class BrainTurn(val text: String, val toolCall: ToolCall? = null)

/**
 * Gemma 4 E2B as the autonomous brain — it BOTH chats and emits tool calls. The model decides and acts.
 *
 * Tool descriptions follow the tool-calling-tutor skill's rule: phrased as "Use this WHEN the user
 * wants ..." (not "what the function does"), with distinct, non-overlapping boundaries. E2B does
 * reliable native tool-calling (Qwen3-0.6B did not). automaticToolCalling = false so Mitra owns
 * dispatch via AgentLoop + the confirmation cards. CPU backend (no NPU; Mali corrupts args).
 */
class LiteRtBrain(modelPath: String, cacheDir: String) {

    private val engine = Engine(
        EngineConfig(modelPath = modelPath, backend = Backend.CPU(), cacheDir = cacheDir),
    ).apply { initialize() }

    /** Tools the model may call. Descriptions are "WHEN to use", boundaries kept distinct. */
    class PhoneTools : ToolSet {
        @Tool(description = "Use this when the user wants the flashlight, torch, or light turned on or off.")
        fun toggle_flashlight(
            @ToolParam(description = "true to turn it ON, false to turn it OFF") on: Boolean,
        ): Map<String, Any> = mapOf("ok" to true)

        @Tool(description = "Use this when the user wants to set or create an alarm at a specific clock time.")
        fun set_alarm(
            @ToolParam(description = "hour of day, 0-23") hour: Int,
            @ToolParam(description = "minute, 0-59") minute: Int,
        ): Map<String, Any> = mapOf("ok" to true)

        @Tool(description = "Use this when the user wants to start a countdown timer for some minutes or seconds.")
        fun start_timer(
            @ToolParam(description = "total duration in seconds") seconds: Int,
        ): Map<String, Any> = mapOf("ok" to true)

        @Tool(description = "Use this when the user wants to open a website, web page, app link, or URL.")
        fun open_url(
            @ToolParam(description = "the web address / URL to open") url: String,
        ): Map<String, Any> = mapOf("ok" to true)

        @Tool(description = "Use this ONLY when the user explicitly says open / launch / start a named app like Spotify, Camera, WhatsApp. Do NOT use this for hardware toggles, system settings, Bluetooth, Wi-Fi, brightness, alarms, or single-word nouns — those have their own tools or none at all.")
        fun open_app(
            @ToolParam(description = "the visible app name (e.g. Spotify) or its package id, only when an explicit open/launch verb was used") name: String,
        ): Map<String, Any> = mapOf("ok" to true)

        @Tool(description = "Use this when the user wants to adjust, see, or open a system settings page — Bluetooth, Wi-Fi, Do Not Disturb, airplane mode, mobile data, brightness, sound, display, location, battery, apps, storage. Mitra cannot toggle these directly; this opens the Android page where the user does it.")
        fun open_settings(
            @ToolParam(description = "the panel: bluetooth, wifi, dnd, airplane, mobile_data, brightness, sound, display, location, battery, apps, storage") panel: String,
        ): Map<String, Any> = mapOf("ok" to true)

        @Tool(description = "Use this when the user wants to change, raise, lower, mute, or set the media or music volume.")
        fun set_media_volume(
            @ToolParam(description = "volume percentage, 0-100") level: Int,
        ): Map<String, Any> = mapOf("ok" to true)

        @Tool(description = "Use this when the user wants to change, raise, lower, or set the screen brightness.")
        fun set_brightness(
            @ToolParam(description = "brightness percentage, 0-100") level: Int,
        ): Map<String, Any> = mapOf("ok" to true)

        @Tool(description = "Use this when the user wants to turn Do Not Disturb on or off.")
        fun set_dnd(
            @ToolParam(description = "true to turn DND ON, false to turn OFF") on: Boolean,
        ): Map<String, Any> = mapOf("ok" to true)

        @Tool(description = "Use this when the user wants to set the ringer to ring, vibrate, or silent.")
        fun set_ringer_mode(
            @ToolParam(description = "one of: ring, vibrate, silent") mode: String,
        ): Map<String, Any> = mapOf("ok" to true)

        @Tool(description = "Use this when the user wants to turn screen auto-rotation on or off.")
        fun set_auto_rotate(
            @ToolParam(description = "true to enable auto-rotate, false to lock orientation") on: Boolean,
        ): Map<String, Any> = mapOf("ok" to true)

        @Tool(description = "Use this when the user wants to change how long the screen stays on before sleeping.")
        fun set_screen_timeout(
            @ToolParam(description = "screen-off timeout in seconds, 15 to 1800") seconds: Int,
        ): Map<String, Any> = mapOf("ok" to true)

        @Tool(description = "Use this when the user wants to turn Bluetooth on or off.")
        fun set_bluetooth(
            @ToolParam(description = "true to turn Bluetooth ON, false to turn OFF") on: Boolean,
        ): Map<String, Any> = mapOf("ok" to true)
        // Real execution is dispatched by AgentLoop -> ToolRegistry, not here.
    }

    private val conversation = engine.createConversation(
        ConversationConfig(
            systemInstruction = Contents.of(
                "You are Mitra, a calm on-device phone assistant. When the user's request matches a tool, " +
                    "call that tool. Otherwise answer directly in one short, friendly sentence. " +
                    "Do not explain your reasoning.",
            ),
            samplerConfig = SamplerConfig(temperature = 0.3, topK = 20, topP = 0.95),
            tools = listOf(tool(PhoneTools())),
            automaticToolCalling = false,
        ),
    )

    /** Streams the reply; the tool call (if any) is attached as soon as the runtime surfaces it. */
    fun chatStream(userText: String): Flow<BrainTurn> = flow {
        var acc = ""
        var call: ToolCall? = null
        // No /no_think here — that's a Qwen-only switch. Gemma's reasoning is curbed via the system
        // prompt ("do not explain your reasoning") and any stray <think> is stripped by sanitize().
        conversation.sendMessageAsync(userText).collect { msg ->
            val piece = textOf(msg)
            acc = if (piece.isNotEmpty() && piece.startsWith(acc)) piece else acc + piece
            msg.toolCalls.firstOrNull()?.let { call = ToolCall(it.name, argsToMap(it.arguments)) }
            emit(BrainTurn(sanitize(acc), call))
        }
    }.flowOn(Dispatchers.IO)

    private fun textOf(message: Message): String =
        message.contents.contents.filterIsInstance<Content.Text>().joinToString("") { it.text }

    private fun sanitize(s: String): String {
        var t = s.replace(Regex("(?s)<think>.*?</think>"), "")
        val open = t.indexOf("<think>")
        if (open >= 0) t = t.substring(0, open)
        for (m in listOf("<|endoftext|>", "<|im_end|>", "<|im_start|>", "<end_of_turn>", "\nHuman:", "\nUser:")) {
            val i = t.indexOf(m)
            if (i >= 0) t = t.substring(0, i)
        }
        return t.replace(Regex("<\\|[^|]*\\|>"), "").replace("</think>", "").trim()
    }

    private fun argsToMap(raw: Any?): Map<String, Any?> = when (raw) {
        is Map<*, *> -> raw.entries.associate { (k, v) -> k.toString() to v }
        is String -> {
            val start = raw.indexOf('{')
            val end = raw.lastIndexOf('}')
            if (start < 0 || end <= start) {
                emptyMap()
            } else {
                val obj = JSONObject(raw.substring(start, end + 1))
                obj.keys().asSequence().associateWith { obj.get(it) }
            }
        }
        else -> emptyMap()
    }

    fun close() {
        conversation.close()
        engine.close()
    }
}
