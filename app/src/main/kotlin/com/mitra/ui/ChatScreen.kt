package com.mitra.ui

import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.RingVolume
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.mitra.agent.AgentRuntime
import com.mitra.agent.GateDecision
import com.mitra.agent.RuntimeEvent
import com.mitra.agent.ToolCall
import com.mitra.prefs.UserPrefs
import com.mitra.safety.ConfirmationGate
import com.mitra.tts.TtsReader
import com.mitra.ui.theme.Mitra
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.util.Calendar

private sealed interface ChatItem

private data class UserMsg(
    val text: String,
) : ChatItem

private data class MitraMsg(
    val text: String,
) : ChatItem

private enum class ActionState { CONFIRM, RUNNING, DONE, CANCELLED, FAILED }

private data class ActionCard(
    val id: Int,
    val title: String,
    val detail: String,
    val state: ActionState,
    val call: ToolCall? = null,
    /** Captured by the dispatcher at execute-time. Non-null only on a successful Reversible run
     *  whose tool implements `Tool.captureUndo`. UI surfaces an Undo button while this is set. */
    val undo: com.mitra.tools.UndoSpec? = null,
) : ChatItem

private data class Suggestion(
    val icon: ImageVector,
    val title: String,
    val subtitle: String,
    val prompt: String,
)

@Composable
fun ChatScreen(
    brainReady: Boolean,
    /** Returns true while the brain's background warmup is still running. Polled cheaply each
     *  recomposition. Surfaced as a calm hint on the input bar so the user doesn't blame Mitra
     *  for a cold first-token cost they didn't initiate. */
    isWarmingUp: () -> Boolean = { false },
    buildRuntime: (onChunk: (String) -> Unit) -> AgentRuntime,
    onOpenSettings: () -> Unit = {},
) {
    val items = remember { mutableStateListOf<ChatItem>() }
    var input by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var nextId by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val view = LocalView.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val tts = remember { TtsReader(context) }
    DisposableEffect(Unit) { onDispose { tts.shutdown() } }

    // Poll the brain's warmup state until it completes. The flag is a Volatile var that Compose
    // can't subscribe to natively, so we mirror it into a State<Boolean> and stop polling once
    // it flips. 400ms is well under the cost of being wrong about the hint's visibility.
    var warming by remember { mutableStateOf(isWarmingUp()) }
    LaunchedEffect(Unit) {
        while (warming) {
            kotlinx.coroutines.delay(400)
            warming = isWarmingUp()
        }
    }

    // TTS opt-in. Re-read on every ON_RESUME so toggling the Settings switch and returning to chat
    // takes effect immediately (Settings is a different Compose screen pushed over the same activity).
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var ttsEnabled by remember { mutableStateOf(UserPrefs.ttsEnabled(context)) }
    DisposableEffect(lifecycle) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) ttsEnabled = UserPrefs.ttsEnabled(context)
            }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    // System back on chat = minimize, not finish. Matches WhatsApp / Signal / Messages behavior —
    // the app survives in recents and resumes with chat state intact.
    BackHandler {
        (context as? ComponentActivity)?.moveTaskToBack(/* nonRoot = */ true)
    }

    // The runtime that is currently mid-turn (used to resume gate decisions). Null between turns.
    var activeRuntime by remember { mutableStateOf<AgentRuntime?>(null) }

    // In-app permission flow. When a tool returns the __NEED_PERM__ sentinel we hold the perm
    // name + the user's original utterance + the action card id. A LaunchedEffect keyed on the
    // perm fires the system RequestPermission dialog. The launcher callback either auto-retries
    // the user's last utterance (on grant via [pendingAutoSend]) or surfaces a denied-state card
    // (on deny). Permanent deny (rationale=false after a deny) bounces to the app-settings page
    // as a last resort. We use pendingAutoSend rather than calling send() directly so the
    // launcher callback does not need to forward-reference send (Kotlin local-fn scoping).
    var pendingPermission by remember { mutableStateOf<String?>(null) }
    var pendingRetryText by remember { mutableStateOf<String?>(null) }
    var pendingCardId by remember { mutableStateOf<Int?>(null) }
    var pendingAutoSend by remember { mutableStateOf<String?>(null) }

    val permLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            val perm = pendingPermission
            val retry = pendingRetryText
            val cardId = pendingCardId
            pendingPermission = null
            pendingRetryText = null
            pendingCardId = null
            if (perm == null) return@rememberLauncherForActivityResult

            if (granted) {
                // Drop the placeholder "asking permission" card and queue the retry.
                cardId?.let { id ->
                    val i = items.indexOfFirst { it is ActionCard && it.id == id }
                    if (i >= 0) items.removeAt(i)
                }
                retry?.let { pendingAutoSend = it }
            } else {
                val activity = context as? android.app.Activity
                val rationale =
                    activity?.let {
                        androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale(it, perm)
                    } ?: false
                val msg =
                    if (rationale) {
                        "Permission needed. Try again and tap Allow."
                    } else {
                        "Permission denied. Opening Settings — enable it there."
                    }
                cardId?.let { id ->
                    val i = items.indexOfFirst { it is ActionCard && it.id == id }
                    if (i >= 0) {
                        val card = items[i] as ActionCard
                        items[i] = card.copy(state = ActionState.FAILED, detail = msg)
                    }
                }
                if (!rationale) {
                    // Permanent-deny path: system dialog will no longer appear. The user must
                    // toggle the permission on the app-settings page; bouncing them there is the
                    // only remaining surface.
                    val intent =
                        android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                            .setData(android.net.Uri.parse("package:${context.packageName}"))
                            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    runCatching { context.startActivity(intent) }
                }
            }
        }

    LaunchedEffect(pendingPermission) {
        pendingPermission?.let { permLauncher.launch(it) }
    }

    LaunchedEffect(items.size) {
        if (items.isNotEmpty()) listState.animateScrollToItem(items.size - 1)
    }

    fun cardIndex(id: Int) = items.indexOfFirst { it is ActionCard && it.id == id }

    fun finishCard(id: Int, success: Boolean, detail: String, undo: com.mitra.tools.UndoSpec? = null) {
        val i = cardIndex(id)
        if (i < 0) return
        val card = items[i] as ActionCard
        items[i] =
            card.copy(
                state = if (success) ActionState.DONE else ActionState.FAILED,
                detail = detail,
                undo = if (success) undo else null,
            )
        // Stronger native feedback than Compose's LocalHapticFeedback. CONFIRM is API 30+ (rich
        // tactile click); LONG_PRESS is the universal fallback that still feels firm on older OEMs.
        // FLAG_IGNORE_VIEW_SETTING ensures it fires even if the view's own haptic flag is off.
        if (success) {
            val constant =
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                    android.view.HapticFeedbackConstants.CONFIRM
                } else {
                    android.view.HapticFeedbackConstants.LONG_PRESS
                }
            view.performHapticFeedback(constant, android.view.HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING)
        }
        // FAILED haptic deferred: needs VIBRATE perm + ADR for the rejection waveform pattern.
    }

    fun runCard(id: Int) {
        // Approve gate: tell the active runtime to proceed.
        val i = cardIndex(id)
        if (i < 0) return
        val card = items[i] as ActionCard
        // Guard against stale resume: only Irreversible steps enter CONFIRM and own a pending gate.
        // Reversible cards start as RUNNING — calling resume() with no active gate throws (AgentRuntime
        // hardening commit 1c3522c). See task notes.
        if (card.state != ActionState.CONFIRM) return
        items[i] = card.copy(state = ActionState.RUNNING)
        scope.launch { activeRuntime?.resume(GateDecision.Approve) }
    }

    fun cancelCard(id: Int) {
        val i = cardIndex(id)
        if (i < 0) return
        val card = items[i] as ActionCard
        // Same stale-resume guard as runCard — only CONFIRM cards have a pending gate to cancel.
        if (card.state != ActionState.CONFIRM) return
        items[i] = card.copy(state = ActionState.CANCELLED)
        scope.launch { activeRuntime?.resume(GateDecision.Cancel) }
    }

    fun undoCard(id: Int) {
        val i = cardIndex(id)
        if (i < 0) return
        val card = items[i] as ActionCard
        val spec = card.undo ?: return
        val runtime = activeRuntime ?: return
        // Clear the undo affordance immediately so a double-tap doesn't fan out two inverse calls
        // while the first is mid-dispatch. Use CANCELLED so the existing state-pill machinery
        // visibly distinguishes "undone" from "done" without inventing a new state in V1.
        items[i] = card.copy(state = ActionState.CANCELLED, undo = null, detail = "Undone")
        val inverse = ToolCall(spec.toolName, spec.args)
        val newId = nextId++
        items.add(
            ActionCard(
                id = newId,
                title = actionTitle(inverse),
                detail = actionDetail(inverse, context),
                state = ActionState.RUNNING,
                call = inverse,
            ),
        )
        scope.launch {
            runtime.runStep(inverse, source = "undo").collect { event ->
                when (event) {
                    is RuntimeEvent.StepCompleted -> {
                        val r = event.result
                        finishCard(
                            id = newId,
                            success = r is com.mitra.automation.BackendResult.Success,
                            detail =
                                when (r) {
                                    is com.mitra.automation.BackendResult.Success -> r.message
                                    is com.mitra.automation.BackendResult.Failure -> r.message
                                },
                            // Suppress chaining undos-of-undos in V1; the user has the original card
                            // sitting one row up if they want to re-apply the forward action.
                            undo = null,
                        )
                    }
                    is RuntimeEvent.Failed -> {
                        finishCard(newId, success = false, detail = event.reason)
                    }
                    else -> Unit
                }
            }
        }
    }

    fun send(textOverride: String? = null) {
        val text = (textOverride ?: input).trim()
        if (text.isEmpty() || busy) return
        view.performHapticFeedback(
            android.view.HapticFeedbackConstants.KEYBOARD_TAP,
            android.view.HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING,
        )
        UserPrefs.setLastUtterance(context, text)
        input = ""
        items.add(UserMsg(text))
        busy = true
        // msgIdx tracks the index of the live Mitra bubble for the current step. After every
        // PlanReady we OPEN A FRESH bubble below the action card and re-point msgIdx at it, so a
        // brain Speaking event arriving AFTER the tool dispatch (the agentic-loop "ok, done"
        // post-tool reply) lands in its own MitraMsg instead of overwriting the ActionCard at the
        // same slot — that overwrite was the regression where the card + Undo disappeared after a
        // brain-mediated brightness change.
        var msgIdx = items.size
        items.add(MitraMsg(""))
        scope.launch {
            // The old onChunk hook is no longer plumbed — the agentic loop emits streaming text
            // via RuntimeEvent.Speaking. The lambda parameter stays for binary compat with the
            // buildRuntime signature but is intentionally ignored.
            val runtime = buildRuntime { _ -> }
            activeRuntime = runtime
            var lastCardId: Int? = null
            runtime.run(com.mitra.agent.UserUtterance(text = text, source = "chat")).collect { event ->
                when (event) {
                    is RuntimeEvent.Speaking -> {
                        // Agentic-loop streaming text. Update the in-flight Mitra bubble each
                        // emission so the user sees the reply build up before the (optional)
                        // tool call surfaces and we drop the bubble for an action card.
                        if (msgIdx < items.size && items[msgIdx] is MitraMsg) {
                            items[msgIdx] = MitraMsg(event.text)
                        }
                    }
                    is RuntimeEvent.PlanReady -> {
                        if (event.plan.steps.isNotEmpty()) {
                            // Drop the (now-stale) streaming bubble in favour of an action card.
                            if (msgIdx < items.size && items[msgIdx] is MitraMsg) {
                                items.removeAt(msgIdx)
                            }
                            // TODO(phase-2): multi-step plans render one card per step. V1 SingleShotPlanner returns 1 step.
                            val step = event.plan.steps.first()
                            val call = ToolCall(step.toolName, step.args)
                            val id = nextId++
                            lastCardId = id
                            val gated = ConfirmationGate.requiresConfirm(step.sideEffect)
                            items.add(
                                ActionCard(
                                    id = id,
                                    title = actionTitle(call),
                                    detail = actionDetail(call, context),
                                    state =
                                        if (gated && step.sideEffect == com.mitra.tools.SideEffect.Irreversible) {
                                            ActionState.CONFIRM
                                        } else {
                                            ActionState.RUNNING
                                        },
                                    call = call,
                                ),
                            )
                            // Open a fresh bubble for the agentic loop's post-tool brain reply.
                            // If Done arrives before any Speaking, the bubble stays empty and is
                            // trimmed in the Done handler so the chat doesn't show a phantom row.
                            msgIdx = items.size
                            items.add(MitraMsg(""))
                        }
                    }
                    is RuntimeEvent.StepCompleted -> {
                        val id = lastCardId ?: return@collect
                        val r = event.result
                        if (r is com.mitra.automation.BackendResult.Failure &&
                            r.message.startsWith("__NEED_PERM__:")
                        ) {
                            // Tool needs a runtime permission. Park the card in RUNNING with a
                            // friendly message, capture the original utterance + card id, and
                            // surface the system permission dialog in-app. The Failed event that
                            // follows from AgentRuntime is silently consumed below (lastCardId
                            // is non-null so the Failed branch's MitraMsg fallback never fires).
                            val perm = r.message.removePrefix("__NEED_PERM__:")
                            val i = items.indexOfFirst { it is ActionCard && it.id == id }
                            if (i >= 0) {
                                val card = items[i] as ActionCard
                                items[i] = card.copy(
                                    state = ActionState.RUNNING,
                                    detail = "Asking for permission…",
                                )
                            }
                            pendingRetryText = text
                            pendingCardId = id
                            pendingPermission = perm
                        } else {
                            finishCard(
                                id = id,
                                success = r is com.mitra.automation.BackendResult.Success,
                                detail =
                                    when (r) {
                                        is com.mitra.automation.BackendResult.Success -> r.message
                                        is com.mitra.automation.BackendResult.Failure -> r.message
                                    },
                                undo = (r as? com.mitra.automation.BackendResult.Success)?.undo,
                            )
                        }
                    }
                    is RuntimeEvent.Done -> {
                        if (lastCardId == null && msgIdx < items.size) {
                            // No tool fired this turn — fill the bubble with whatever the brain
                            // streamed (or fall back to the runtime's summary string).
                            val spoken = (items[msgIdx] as? MitraMsg)?.text.orEmpty()
                            val msg =
                                when {
                                    spoken.isNotBlank() -> spoken
                                    event.summary == "nothing to do" -> "I'm not sure how to help with that one yet."
                                    // IntentParser shortcut path emits "done" — useless as chat
                                    // text and would just clutter the stream; drop it to empty so
                                    // the trim below removes the row.
                                    event.summary == "done" -> ""
                                    else -> event.summary
                                }
                            items[msgIdx] = MitraMsg(msg)
                        }
                        // Trim an empty trailing MitraMsg left by the fresh-bubble-per-PlanReady
                        // shape when the brain didn't actually say anything after the tool ran
                        // (the IntentParser path and many tool-only agentic turns hit this).
                        if (msgIdx < items.size) {
                            val last = items[msgIdx]
                            if (last is MitraMsg && last.text.isBlank()) {
                                items.removeAt(msgIdx)
                            }
                        }
                    }
                    is RuntimeEvent.Failed -> {
                        if (lastCardId == null && msgIdx < items.size) {
                            items[msgIdx] = MitraMsg("Sorry — ${event.reason}")
                        }
                        // Same empty-bubble trim as Done.
                        if (msgIdx < items.size) {
                            val last = items[msgIdx]
                            if (last is MitraMsg && last.text.isBlank()) {
                                items.removeAt(msgIdx)
                            }
                        }
                    }
                    is RuntimeEvent.StepStarted, is RuntimeEvent.GateRequested, is RuntimeEvent.Replan -> {
                        // No additional UI work needed in V1; gate state already on the card.
                    }
                }
            }
            activeRuntime = null
            busy = false
        }
    }

    // Auto-retry after an in-app permission grant. The launcher callback sets pendingAutoSend;
    // this effect picks it up and re-dispatches the original utterance via send(), which re-runs
    // the brain on a fresh turn with the permission now in place.
    LaunchedEffect(pendingAutoSend) {
        val t = pendingAutoSend
        if (t != null) {
            pendingAutoSend = null
            send(t)
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize()) {
            MinimalHeader(brainReady = brainReady, onOpenSettings = onOpenSettings)
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                if (items.isEmpty()) {
                    item { EmptyHero(brainReady = brainReady, onQuickPrompt = { send(it) }) }
                }
                itemsIndexed(items) { index, item ->
                    when (item) {
                        is UserMsg -> UserBubble(item.text)
                        is MitraMsg ->
                            MitraReply(
                                text = item.text,
                                busy = busy && index == items.lastIndex,
                                onSpeak = if (ttsEnabled) ({ tts.speak(item.text) }) else null,
                            )
                        is ActionCard ->
                            ActionCardView(
                                item,
                                onConfirm = ::runCard,
                                onCancel = ::cancelCard,
                                onUndo = ::undoCard,
                            )
                    }
                }
            }
            if (warming) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    androidx.compose.material3.CircularProgressIndicator(
                        modifier = Modifier.size(12.dp),
                        strokeWidth = 1.5.dp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(
                        "Warming up the brain — first message will take a few seconds.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            // Disable send while warming — LiteRT-LM serializes per-Conversation, so a user
            // message during the warmup turn would either queue silently (looks frozen) or
            // collide with the prefill. Better to make the wait honest.
            FloatingInputBar(
                value = input,
                onValueChange = { input = it },
                onSend = { send() },
                enabled = !busy && !warming,
            )
            Spacer(Modifier.size(8.dp))
        }
    }
}

private fun actionTitle(call: ToolCall): String =
    when (call.name) {
        "toggle_flashlight" -> if (call.args["on"] == false) "Turn flashlight off" else "Turn flashlight on"
        "set_alarm" -> "Set alarm"
        "start_timer" -> "Start timer"
        "open_url" -> "Open link"
        "open_app" -> "Open app"
        "open_settings" -> "Open settings"
        "set_media_volume" -> "Set volume"
        "set_brightness" -> "Set brightness"
        "set_dnd" -> if (call.args["on"] == false) "Turn Do Not Disturb off" else "Turn Do Not Disturb on"
        "set_ringer_mode" -> "Set ringer"
        "set_auto_rotate" -> if (call.args["on"] == false) "Turn auto-rotate off" else "Turn auto-rotate on"
        "set_screen_timeout" -> "Set screen timeout"
        "set_bluetooth" -> if (call.args["on"] == false) "Turn Bluetooth off" else "Turn Bluetooth on"
        "make_call" -> "Place call"
        "send_sms" -> "Send text"
        "query_contacts" -> "Look up contact"
        else -> call.name.replace('_', ' ').replaceFirstChar { it.uppercase() }
    }

private fun actionDetail(call: ToolCall, context: android.content.Context): String =
    when (call.name) {
        "set_alarm" -> {
            val h = (call.args["hour"] as? Number)?.toInt()
            val m = (call.args["minute"] as? Number)?.toInt() ?: 0
            if (h != null) String.format("for %02d:%02d", h, m) else ""
        }
        "start_timer" -> {
            val s = (call.args["seconds"] as? Number)?.toInt() ?: return ""
            when {
                s % 3600 == 0 -> "${s / 3600} hr"
                s % 60 == 0 -> "${s / 60} min"
                else -> "$s sec"
            }
        }
        "open_url" -> (call.args["url"] as? String).orEmpty()
        "open_app" -> (call.args["name"] as? String) ?: (call.args["package_name"] as? String).orEmpty()
        "open_settings" ->
            (call.args["panel"] as? String)
                ?.replace('_', ' ')
                ?.replaceFirstChar { it.uppercase() }
                .orEmpty()
        "set_media_volume" -> {
            val level = (call.args["level"] as? Number)?.toInt() ?: return ""
            "to $level%"
        }
        "set_brightness" -> {
            val level = (call.args["level"] as? Number)?.toInt() ?: return ""
            "to $level%"
        }
        "set_ringer_mode" ->
            when ((call.args["mode"] as? String)?.lowercase()) {
                "silent" -> "to silent"
                "vibrate" -> "to vibrate"
                "normal", "ring" -> "to ring"
                else -> ""
            }
        "set_screen_timeout" -> {
            val s = (call.args["seconds"] as? Number)?.toInt() ?: 0
            when {
                s == 0 -> ""
                s % 60 == 0 -> "to ${s / 60} min"
                else -> "to $s sec"
            }
        }
        "query_contacts" -> (call.args["name"] as? String).orEmpty()
        "make_call" -> {
            // Resolve through the same logic MakeCall.execute uses so the confirm card shows
            // the real target ("Blanta — +91 76718 90230") not just the raw arg ("blanta").
            // The lookup is a single ContentResolver query, fine on UI thread for action card init.
            val preview = runCatching { com.mitra.tools.MakeCall(context).previewFor(call.args) }.getOrNull()
            preview
                ?: (call.args["name"] as? String).orEmpty().ifBlank {
                    (call.args["number"] as? String).orEmpty()
                }
        }
        "send_sms" -> {
            // Same idea as make_call but with body — the confirm card must show "Blanta — +91 …
            // · on my way" so the user can verify exactly what is about to leave the device.
            val preview = runCatching { com.mitra.tools.SendSms(context).previewFor(call.args) }.getOrNull()
            preview
                ?: buildString {
                    val who = (call.args["name"] as? String).orEmpty()
                        .ifBlank { (call.args["number"] as? String).orEmpty() }
                    val body = (call.args["body"] as? String).orEmpty()
                    if (who.isNotBlank()) append(who)
                    if (who.isNotBlank() && body.isNotBlank()) append(" · ")
                    if (body.isNotBlank()) append(body)
                }
        }
        // Title already speaks for these — no detail needed.
        "toggle_flashlight", "set_dnd", "set_auto_rotate", "set_bluetooth" -> ""
        else -> ""
    }

private fun toolIcon(name: String): ImageVector =
    when (name) {
        "toggle_flashlight" -> Icons.Filled.FlashOn
        "set_alarm" -> Icons.Outlined.AccessTime
        "start_timer" -> Icons.Filled.Timer
        "open_url" -> Icons.Filled.OpenInNew
        "open_app" -> Icons.Filled.PlayArrow
        "open_settings" -> Icons.Filled.Tune
        "set_media_volume" -> Icons.Filled.VolumeUp
        "set_brightness" -> Icons.Filled.BrightnessMedium
        "set_dnd" -> Icons.Filled.NotificationsOff
        "set_ringer_mode" -> Icons.Filled.RingVolume
        "set_auto_rotate" -> Icons.Filled.PhoneAndroid
        "set_screen_timeout" -> Icons.Filled.DarkMode
        "set_bluetooth" -> Icons.Filled.Bluetooth
        "query_contacts" -> Icons.Filled.Search
        "make_call" -> Icons.Filled.Phone
        "send_sms" -> Icons.Filled.Send
        else -> Icons.Outlined.Bolt
    }

private fun greeting(): String {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return when {
        hour < 5 -> "Still up?"
        hour < 12 -> "Good morning"
        hour < 17 -> "Good afternoon"
        hour < 22 -> "Good evening"
        else -> "Good night"
    }
}

@Composable
private fun MinimalHeader(brainReady: Boolean, onOpenSettings: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                "Mitra",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            OnDeviceBadge()
        }
        IconButton(onClick = onOpenSettings) {
            Icon(
                Icons.Filled.Settings,
                contentDescription = "Settings",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun OnDeviceBadge(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                modifier =
                    Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(Mitra.semantic.success),
            )
            Text(
                "On device",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EmptyHero(brainReady: Boolean, onQuickPrompt: (String) -> Unit) {
    val suggestions =
        remember {
            listOf(
                Suggestion(Icons.Filled.FlashOn, "Turn on the flashlight", "", "Turn on the flashlight"),
                Suggestion(Icons.Outlined.AccessTime, "Set an alarm for 7:30 am", "", "Set an alarm for 7:30 am"),
                Suggestion(Icons.Filled.Timer, "Start a 5 minute timer", "", "Start a 5 minute timer"),
                Suggestion(Icons.Filled.NotificationsOff, "Turn on Do Not Disturb", "", "Turn on Do Not Disturb"),
            )
        }
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    val context = androidx.compose.ui.platform.LocalContext.current
    val name = remember { UserPrefs.name(context) }
    val lastUtterance = remember { UserPrefs.lastUtterance(context) }
    val heroText = if (name.isNotBlank()) greeting() + ", " + name + "." else greeting() + "."

    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        AnimatedHero(text = heroText)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (lastUtterance.isNotBlank()) {
                StaggeredEntry(visible = visible, delayMs = 80) {
                    RepeatLastPill(text = lastUtterance, onClick = { onQuickPrompt(lastUtterance) })
                }
            }
            suggestions.forEachIndexed { idx, s ->
                StaggeredEntry(visible = visible, delayMs = 120 + idx * 90) {
                    SuggestionCard(s, onClick = { onQuickPrompt(s.prompt) })
                }
            }
        }
    }
}

@Composable
private fun RepeatLastPill(text: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                Icons.Filled.Refresh,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Repeat",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun AnimatedHero(text: String) {
    val transition = rememberInfiniteTransition(label = "hero")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(7000, easing = LinearEasing)),
        label = "phase",
    )
    val clayDeep = Color(0xFFA85339)
    val clayLight = Color(0xFFD08561)
    val rose = Color(0xFFD9706C)
    val amber = Color(0xFFE8A368)
    val brush =
        remember(phase) {
            val span = 900f
            Brush.linearGradient(
                colors = listOf(clayDeep, amber, rose, clayLight, clayDeep),
                start = Offset(-span + phase * span * 3f, 0f),
                end = Offset(phase * span * 3f, span * 0.6f),
                tileMode = TileMode.Mirror,
            )
        }
    Text(
        text,
        fontSize = 44.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 52.sp,
        style = MaterialTheme.typography.displayLarge.copy(brush = brush),
        modifier = Modifier.padding(top = 40.dp, bottom = 8.dp),
    )
}

@Composable
private fun StaggeredEntry(visible: Boolean, delayMs: Int, content: @Composable () -> Unit) {
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 420, delayMillis = delayMs, easing = EaseOutCubic),
        label = "stagger-alpha",
    )
    val offset by animateDpAsState(
        targetValue = if (visible) 0.dp else 16.dp,
        animationSpec = tween(durationMillis = 420, delayMillis = delayMs, easing = EaseOutCubic),
        label = "stagger-offset",
    )
    Box(
        modifier =
            Modifier.graphicsLayer {
                translationY = offset.toPx()
                this.alpha = alpha
            },
    ) {
        content()
    }
}

@Composable
private fun SuggestionCard(s: Suggestion, onClick: () -> Unit) {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = tween(120, easing = FastOutSlowInEasing),
        label = "press",
    )
    val borderColor by androidx.compose.animation.animateColorAsState(
        targetValue =
            if (pressed) {
                MaterialTheme.colorScheme.primary.copy(
                    alpha = 0.6f,
                )
            } else {
                MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
            },
        animationSpec = tween(180),
        label = "border",
    )
    Surface(
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, borderColor),
        modifier =
            Modifier
                .fillMaxWidth()
                .scale(scale)
                .clickable(
                    onClick = {
                        pressed = true
                        onClick()
                    },
                ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(s.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Text(
                s.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
        }
    }
    LaunchedEffect(pressed) {
        if (pressed) {
            kotlinx.coroutines.delay(160)
            pressed = false
        }
    }
}

@Composable
private fun UserBubble(text: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Surface(
            color = MaterialTheme.colorScheme.primary,
            shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp, bottomStart = 22.dp, bottomEnd = 6.dp),
            modifier = Modifier.widthIn(max = 300.dp),
        ) {
            Text(
                text,
                color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

@Composable
private fun MitraReply(text: String, busy: Boolean, onSpeak: (() -> Unit)?) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Box(
            modifier =
                Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(14.dp),
            )
        }
        Spacer(Modifier.size(10.dp))
        Column(Modifier.weight(1f)) {
            if (text.isBlank() && busy) {
                ThinkingDots()
            } else {
                val cursor =
                    if (busy) {
                        val transition = rememberInfiniteTransition(label = "stream-cursor")
                        val visible by transition.animateFloat(
                            initialValue = 0f,
                            targetValue = 1f,
                            animationSpec =
                                infiniteRepeatable(
                                    animation = tween(520, easing = LinearEasing),
                                    repeatMode = RepeatMode.Reverse,
                                ),
                            label = "stream-cursor-alpha",
                        )
                        if (visible > 0.5f) " ▍" else "  "
                    } else {
                        ""
                    }
                Text(
                    text + cursor,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodyLarge,
                )
                if (!busy && text.isNotBlank() && onSpeak != null) {
                    Spacer(Modifier.size(6.dp))
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        onClick = onSpeak,
                    ) {
                        Icon(
                            Icons.Filled.VolumeUp,
                            contentDescription = "Read aloud",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(6.dp).size(16.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ThinkingDots() {
    val transition = rememberInfiniteTransition(label = "thinking")
    val alpha by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(900, easing = LinearEasing), repeatMode = RepeatMode.Reverse),
        label = "alpha",
    )
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(3) {
            Box(
                modifier =
                    Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha)),
            )
        }
    }
}

@Composable
private fun ActionCardView(
    card: ActionCard,
    onConfirm: (Int) -> Unit,
    onCancel: (Int) -> Unit,
    onUndo: (Int) -> Unit,
) {
    val accent =
        when (card.state) {
            ActionState.DONE -> Color(0xFF8FB97D)
            ActionState.FAILED -> MaterialTheme.colorScheme.error
            ActionState.CANCELLED -> MaterialTheme.colorScheme.onSurfaceVariant
            else -> MaterialTheme.colorScheme.primary
        }
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.25f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier =
                        Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(accent.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        card.call?.let { toolIcon(it.name) } ?: Icons.Outlined.Bolt,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Spacer(Modifier.size(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(card.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    if (card.detail.isNotBlank()) {
                        Text(
                            card.detail,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                StatePill(card.state, accent)
            }
            AnimatedVisibility(visible = card.state == ActionState.CONFIRM, enter = fadeIn(), exit = fadeOut()) {
                Column {
                    Spacer(Modifier.size(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        GhostButton("Cancel", icon = Icons.Filled.Close, onClick = { onCancel(card.id) }, modifier = Modifier.weight(1f))
                        FilledButton("Confirm", icon = Icons.Filled.Check, onClick = { onConfirm(card.id) }, modifier = Modifier.weight(1f))
                    }
                }
            }
            // Undo affordance: only when (a) the forward action succeeded AND (b) the tool
            // captured an inverse. The button stays visible indefinitely in V1 — the action-cards
            // spec's 3-second auto-fade requires a progress-line animation we'll wire alongside
            // the toast-variant card rewrite. Tap once → button disappears (state becomes
            // CANCELLED so we don't show two stacked Undo buttons after the inverse runs).
            AnimatedVisibility(
                visible = card.state == ActionState.DONE && card.undo != null,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                Column {
                    Spacer(Modifier.size(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        GhostButton(
                            label = "Undo",
                            icon = Icons.Filled.RestartAlt,
                            onClick = { onUndo(card.id) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatePill(state: ActionState, accent: Color) {
    val (label, showSpinner) =
        when (state) {
            ActionState.CONFIRM -> "Confirm" to false
            ActionState.RUNNING -> "Working" to true
            ActionState.DONE -> "Done" to false
            ActionState.CANCELLED -> "Cancelled" to false
            ActionState.FAILED -> "Failed" to false
        }
    Surface(
        color = accent.copy(alpha = 0.14f),
        shape = RoundedCornerShape(10.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (showSpinner) {
                CircularProgressIndicator(modifier = Modifier.size(10.dp), strokeWidth = 1.5.dp, color = accent)
            } else if (state == ActionState.DONE) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = accent, modifier = Modifier.size(12.dp))
            } else if (state == ActionState.FAILED) {
                Icon(Icons.Filled.ErrorOutline, contentDescription = null, tint = accent, modifier = Modifier.size(12.dp))
            }
            Text(label, style = MaterialTheme.typography.labelSmall, color = accent, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun FilledButton(label: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        shape = RoundedCornerShape(14.dp),
        modifier = modifier.height(48.dp).clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.size(6.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun GhostButton(label: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier.height(48.dp).clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.size(6.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun FloatingInputBar(value: String, onValueChange: (String) -> Unit, onSend: () -> Unit, enabled: Boolean) {
    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier =
            Modifier
                .fillMaxWidth()
                .imePadding(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                placeholder = {
                    Text(
                        "Message Mitra",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                modifier = Modifier.weight(1f),
                enabled = enabled,
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSend() }),
                colors =
                    OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    ),
            )
            val canSend = enabled && value.isNotBlank()
            Surface(
                shape = CircleShape,
                color = if (canSend) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.size(52.dp),
            ) {
                IconButton(onClick = onSend, enabled = canSend) {
                    Icon(
                        Icons.Filled.Send,
                        contentDescription = "Send",
                        tint = if (canSend) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}
