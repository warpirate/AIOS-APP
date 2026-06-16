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
data class BrainTurn(
    val text: String,
    val toolCall: ToolCall? = null,
)

/**
 * Gemma 4 E2B as the autonomous brain — it BOTH chats and emits tool calls. The model decides and acts.
 *
 * Tool descriptions follow the tool-calling-tutor skill's rule: phrased as "Use this WHEN the user
 * wants ..." (not "what the function does"), with distinct, non-overlapping boundaries. E2B does
 * reliable native tool-calling (Qwen3-0.6B did not). automaticToolCalling = false so Mitra owns
 * dispatch via AgentLoop + the confirmation cards. CPU backend (no NPU; Mali corrupts args).
 */
class LiteRtBrain(
    modelPath: String,
    cacheDir: String,
) : Brain {
    private val engine =
        Engine(
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

        @Tool(
            description = "Use this ONLY when the user pasted a URL or explicitly named a website that has a TLD (e.g. 'open youtube.com', 'go to github.com', 'visit example.org'). The argument MUST contain a dot followed by a real TLD (com, org, in, io, co, etc.) AND NO spaces. Do NOT call open_url for: declarations ('that's a spell'), opinions, exclamations, song lyrics, spell names ('lumos maximus'), movie quotes, general statements, factual questions, or any phrase that is not literally a web address. When in doubt, answer in chat instead. Examples that MUST NOT route here: 'lumos maximus', 'that's a spell', 'what is X', 'I think Y', 'cool', 'haha', 'how are you'.",
        )
        fun open_url(
            @ToolParam(description = "the literal web address with TLD and NO spaces — never pass general chat text") url: String,
        ): Map<String, Any> = mapOf("ok" to true)

        @Tool(
            description = "Use this ONLY when the user explicitly says open / launch / start a named app like Spotify, Camera, WhatsApp. Do NOT use this for hardware toggles, system settings, Bluetooth, Wi-Fi, brightness, alarms, or single-word nouns — those have their own tools or none at all.",
        )
        fun open_app(
            @ToolParam(
                description = "the visible app name (e.g. Spotify) or its package id, only when an explicit open/launch verb was used",
            ) name: String,
        ): Map<String, Any> = mapOf("ok" to true)

        @Tool(
            description = "Use this ONLY when the user wants to find a person's phone number, ask whose number something is, or look up a contact by name (e.g. 'what's mom's number', 'find priya', 'raj's phone'). Do NOT use this for opening the Contacts app, dialling, sending a message, or general chat — it only reads the address book.",
        )
        fun query_contacts(
            @ToolParam(description = "the contact's name or partial name to search for") name: String,
        ): Map<String, Any> = mapOf("ok" to true)

        @Tool(
            description = "Use this when the user wants to adjust, see, or open a system settings page — Bluetooth, Wi-Fi, Do Not Disturb, airplane mode, mobile data, brightness, sound, display, location, battery, apps, storage. Mitra cannot toggle these directly; this opens the Android page where the user does it.",
        )
        fun open_settings(
            @ToolParam(
                description = "the panel: bluetooth, wifi, dnd, airplane, mobile_data, brightness, sound, display, location, battery, apps, storage",
            ) panel: String,
        ): Map<String, Any> = mapOf("ok" to true)

        @Tool(description = "Use this when the user wants to change, raise, lower, mute, or set the media or music volume.")
        fun set_media_volume(
            @ToolParam(description = "volume percentage, 0-100") level: Int,
        ): Map<String, Any> = mapOf("ok" to true)

        @Tool(
            description =
                "Use this ONLY when the user wants to set the screen brightness to a SPECIFIC level " +
                    "(e.g. 'set brightness to 30%', 'make it 100', 'brightness 60'). For adaptive / " +
                    "automatic brightness instead, use set_brightness_auto.",
        )
        fun set_brightness(
            @ToolParam(description = "brightness percentage, 0-100") level: Int,
        ): Map<String, Any> = mapOf("ok" to true)

        @Tool(
            description =
                "Use this when the user wants ADAPTIVE / AUTO / AUTOMATIC brightness (e.g. " +
                    "'brightness auto', 'set brightness to auto', 'automatic brightness', " +
                    "'adaptive brightness on'). No level needed — Android decides based on ambient light.",
        )
        fun set_brightness_auto(): Map<String, Any> = mapOf("ok" to true)

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

        @Tool(
            description = "Use this ONLY when the user explicitly says 'call X', 'dial X', 'phone X', 'ring X' where X is a contact name OR a phone number (e.g. 'call mom', 'call 9876543210', 'dial Priya'). Pass the target EXACTLY as the user wrote it. Do NOT call this for 'open the phone app', 'open dialer', or any non-call request — those use open_app or open_settings. Do NOT call this for general chat that happens to contain the word 'call'.",
        )
        fun make_call(
            @ToolParam(
                description = "the contact name the user said, byte-for-byte. Use this when the user gave a name like 'mom' or 'Priya'. Leave empty when the user gave only digits.",
            ) name: String,
            @ToolParam(
                description = "the phone number the user said, digits only or with + and spaces. Use this when the user gave digits like '9876543210' or '+91 98765 43210'. Leave empty when the user gave a name.",
            ) number: String,
        ): Map<String, Any> = mapOf("ok" to true)

        @Tool(
            description = "Use this ONLY when the user explicitly says 'text X', 'message X', 'sms X', 'send X a message', or 'tell X that ...' where X is a contact name OR a phone number AND the user supplied the message body (e.g. 'text mom on my way', 'message Priya I'll be late', 'sms 9876543210 hey'). Pass the recipient EXACTLY as the user wrote it; pass the message body verbatim. Do NOT call this for 'open the messages app', 'check my texts', or any read/open request — those use open_app. Do NOT call this if the user did not say what message to send.",
        )
        fun send_sms(
            @ToolParam(
                description = "the contact name the user said, byte-for-byte. Use this when the user gave a name like 'mom' or 'Priya'. Leave empty when the user gave only digits.",
            ) name: String,
            @ToolParam(
                description = "the phone number the user said, digits only or with + and spaces. Use this when the user gave digits. Leave empty when the user gave a name.",
            ) number: String,
            @ToolParam(
                description = "the composed message body to send. YOU draft it from the user's intent — see the COMPOSE system rule. The user said 'ask blanta to come over'? body is 'hey, can you swing by?', NOT 'ask blanta to come over'. Only copy the user's exact words when they wrapped the body in quotes.",
            ) body: String,
        ): Map<String, Any> = mapOf("ok" to true)
        // Real execution is dispatched by AgentLoop -> ToolRegistry, not here.
    }

    // Hoisted as its own property to keep the conversation declaration below scannable. The
    // earlier in-line warmup() variants reused this Contents to prefill against the same prompt
    // surface; the warmup approach didn't work (LiteRT-LM doesn't reuse cross-call KV state)
    // and was replaced by the Application-singleton lifecycle in BrainHolder + MitraApp.
    private val systemInstruction =
        Contents.of(
            """
            |You are Mitra, an on-device phone assistant. Answer like a smart friend who texts.
            |
            |LENGTH:
            |- Tool confirmation: 1 sentence.
            |- Teach / explain / define / translate / list: give a useful multi-sentence answer with examples or a short list. Never truncate to one line. Never punt with "what would you like to know?".
            |- Small talk: 1-2 sentences.
            |
            |VOICE — answer the question directly, never talk about yourself.
            |- WRONG: "I can", "Mitra can", "I am here to help", "Let me", "What would you like to know?".
            |- RIGHT: state the answer in second person.
            |- No greetings, no apologies, no exclamation marks, no em dashes, no emoji.
            |- No filler ("just", "really", "basically", "perhaps", "in order to").
            |- No "In conclusion", "Moreover", "Furthermore".
            |- No buzzwords (leverage, utilize, robust, seamless, comprehensive, delve, holistic, actionable, impactful, foster, harness, embark, vibrant, thriving).
            |- Use "is" / "has", not "serves as" / "features" / "boasts".
            |- Fragments and bullet lists are fine.
            |
            |TOOLS — call a tool only when the user's request matches its "Use this WHEN" boundary. Never call open_url for learn / teach / explain / define / translate questions; answer them with content.
            |
            |TOOL ARGS — when emitting a tool call, COPY proper-noun arguments (contact names, app names, URLs) BYTE-FOR-BYTE from the user's most recent message. Do NOT respell, abbreviate, "fix", or paraphrase them. "blanta" stays "blanta". "Priya Sharma" stays "Priya Sharma". The tool may fail on a misspelling and that is correct — the user will retype it.
            |
            |INDIAN ENGLISH FILLERS — "naa", "na", "haan", "haina", "kya", "yaar", "matlab", "okay na", "right?", "no?", "right na" are conversational fillers, NOT names. Never treat them as a contact name, app name, or any tool argument. Strip them before resolving intent.
            |
            |NEVER NARRATE FAKE ACTIONS. Do not write "You have found X", "Done", "Called X", "Opened X", "Set X" unless you actually emitted the matching tool call this turn. If you did not call the tool, say what is needed (e.g. "I cannot call yet — Mitra needs the call_phone permission" or "Try saying 'call Blanta' with the explicit verb"). Honest > confident-sounding.
            |
            |CALL / SMS — "call X" means dial X via the make_call tool. "Text X" / "message X" / "send X a message <body>" / "tell X <body>" uses send_sms — emit it ONLY when the user supplied an actual message body in the same utterance, AND draft the body from intent (see COMPOSE below). If they only said "text mom" with no body, ask "what should I say?" in chat instead of emitting a tool call with an empty body. Both call/sms tools treat X as either a contact name OR a phone number; pass it through unchanged to the tool's name OR number argument as you see it.
            |
            |COMPOSE — when the user gives you an instruction like "tell X ...", "ask X ...", "text X to do ...", "send X a message saying ...", YOU draft the body. Never paste the user's instruction verbatim into the body argument.
            |- "ask blanta to come over" -> body: "hey, can you swing by?"
            |- "tell mom I'll be late" -> body: "running late, see you soon"
            |- "text dad i'm not coming" -> body: "can't make it today, sorry"
            |The ONLY case where you copy verbatim is when the user wraps the body in quotes:
            |- "text mom \"on my way\"" -> body: "on my way"
            |Default tone: casual friend, contractions OK, lowercase OK. If the user wrote formally, mirror that. Keep bodies under 160 characters when possible (one SMS segment).
            |
            |TONE — after a tool fires, you MAY say ONE short clause acknowledging the user's mood, then stop. Read the user's register from the utterance:
            |- vent / swear / frustration -> "alright, sent." / "done." / "okay."
            |- neutral request -> "sent." / "done." / "set."
            |- happy / casual -> "nice, sent." / "got it."
            |Never moralize. Never lecture. Never ask if there is anything else. Hard rules from VOICE still apply — no emoji, no exclamation marks, no em dashes, no greetings.
            |
            |AGENTIC — each turn you may call up to 5 tools before you must end with a final reply.
            |- After a tool runs you receive its result as a JSON map. Decide next:
            |  - Success ({"ok": true}) -> either call another tool toward the same goal, or finish with a 1-clause reply.
            |  - Failure ({"ok": false, "error": "..."}) -> decide based on the error: retry with different args (e.g. ambiguous contact -> ask user which one), skip and continue, or finish honestly ("couldn't reach mom, line was busy"). Do NOT silently re-emit the same call.
            |  - Cancelled ({"cancelled": true}) -> user cancelled at the confirm card. Acknowledge briefly ("okay, didn't send") and stop. Do not retry.
            |- If you hit the 5-tool cap, the runtime stops you. Plan economically — one tool per goal, two at most for chains. Three or more only when the user asked for it explicitly.
            """.trimMargin(),
        )

    private val conversation =
        engine.createConversation(
            ConversationConfig(
                systemInstruction = systemInstruction,
                samplerConfig = SamplerConfig(temperature = 0.3, topK = 20, topP = 0.95),
                tools = listOf(tool(PhoneTools())),
                automaticToolCalling = false,
            ),
        )


    /** Streams the reply; the tool call (if any) is attached as soon as the runtime surfaces it. */
    override fun chatStream(userText: String): Flow<BrainTurn> =
        flow {
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

    /**
     * Feed a tool-execution result back into the conversation as a `Content.ToolResponse` and
     * stream the brain's next reply (which may carry the next tool call). The runtime calls
     * this once per tool it dispatched, in order, within the same turn.
     *
     * `result` is the structured outcome map. Convention used by [com.mitra.agent.AgentRuntime]:
     *   - On Success: `{ "ok": true, "message": "<backend message>" }`
     *   - On Failure: `{ "ok": false, "error": "<backend error>" }`
     *   - On user-cancelled gate: `{ "cancelled": true }`
     * The brain reads these to decide whether to retry, branch, or finish.
     */
    override fun sendToolResult(toolName: String, result: Map<String, Any?>): Flow<BrainTurn> =
        flow {
            var acc = ""
            var call: ToolCall? = null
            val response = Contents.of(Content.ToolResponse(name = toolName, response = result))
            conversation.sendMessageAsync(response).collect { msg ->
                val piece = textOf(msg)
                acc = if (piece.isNotEmpty() && piece.startsWith(acc)) piece else acc + piece
                msg.toolCalls.firstOrNull()?.let { call = ToolCall(it.name, argsToMap(it.arguments)) }
                emit(BrainTurn(sanitize(acc), call))
            }
        }.flowOn(Dispatchers.IO)

    private fun textOf(message: Message): String =
        message.contents.contents
            .filterIsInstance<Content.Text>()
            .joinToString("") { it.text }

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

    private fun argsToMap(raw: Any?): Map<String, Any?> =
        when (raw) {
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
