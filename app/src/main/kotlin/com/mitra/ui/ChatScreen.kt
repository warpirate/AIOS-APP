package com.mitra.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mitra.agent.AgentLoop
import com.mitra.agent.ToolCall
import com.mitra.inference.LiteRtBrain
import com.mitra.safety.ConfirmationGate
import com.mitra.tools.ToolResult
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

private sealed interface ChatItem
private data class UserMsg(val text: String) : ChatItem
private data class MitraMsg(val text: String) : ChatItem
private enum class ActionState { CONFIRM, RUNNING, DONE, CANCELLED, FAILED }
private data class ActionCard(
    val id: Int,
    val title: String,
    val detail: String,
    val state: ActionState,
    val call: ToolCall? = null,
) : ChatItem

/**
 * The conversation. The brain streams a reply; when it emits a tool call we render a Gemini-style
 * action card — side-effect-free actions run immediately, others wait on a Confirm tap.
 * [brain] null => basic keyword mode (flashlight still works via [agent]).
 */
@Composable
fun ChatScreen(brain: LiteRtBrain?, agent: AgentLoop) {
    val items = remember { mutableStateListOf<ChatItem>() }
    var input by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var nextId by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    LaunchedEffect(items.size) {
        if (items.isNotEmpty()) listState.animateScrollToItem(items.size - 1)
    }

    fun cardIndex(id: Int) = items.indexOfFirst { it is ActionCard && it.id == id }

    fun finish(id: Int, result: ToolResult) {
        val i = cardIndex(id)
        if (i < 0) return
        val card = items[i] as ActionCard
        items[i] = when (result) {
            is ToolResult.Success -> card.copy(state = ActionState.DONE, detail = result.message)
            is ToolResult.Failure -> card.copy(state = ActionState.FAILED, detail = result.message)
        }
    }

    fun runCard(id: Int) {
        val i = cardIndex(id)
        if (i < 0) return
        val card = items[i] as ActionCard
        val call = card.call ?: return
        items[i] = card.copy(state = ActionState.RUNNING)
        scope.launch { finish(id, agent.runCall(call)) }
    }

    fun cancelCard(id: Int) {
        val i = cardIndex(id)
        if (i >= 0) items[i] = (items[i] as ActionCard).copy(state = ActionState.CANCELLED)
    }

    fun addCard(call: ToolCall) {
        val id = nextId++
        val gated = ConfirmationGate.requiresConfirm(agent.sideEffectOf(call.name))
        items.add(
            ActionCard(
                id = id,
                title = actionTitle(call),
                detail = actionDetail(call),
                state = if (gated) ActionState.CONFIRM else ActionState.RUNNING,
                call = call,
            ),
        )
        if (!gated) scope.launch { finish(id, agent.runCall(call)) } // None: run immediately
    }

    fun send() {
        val text = input.trim()
        if (text.isEmpty() || busy) return
        input = ""
        items.add(UserMsg(text))
        busy = true
        scope.launch {
            if (brain != null) {
                // LLM-first: the model decides + acts (autonomy). Parser is only a safety net below.
                val msgIdx = items.size
                items.add(MitraMsg("…"))
                var call: ToolCall? = null
                brain.chatStream(text).collect { turn ->
                    turn.toolCall?.let { call = it }
                    items[msgIdx] = MitraMsg(turn.text.ifBlank { "…" })
                }
                val spoken = (items[msgIdx] as? MitraMsg)?.text.orEmpty()
                val action = call ?: agent.parse(text) // net: if the model didn't emit a call, try the parser
                when {
                    action != null -> {
                        items.removeAt(msgIdx) // the action card IS the answer — drop the model's chatter
                        addCard(action)
                    }
                    spoken.isBlank() || spoken == "…" ->
                        items[msgIdx] = MitraMsg("I'm not sure how to help with that one yet.")
                }
            } else {
                val call = agent.parse(text)
                if (call != null) addCard(call) else items.add(MitraMsg(agent.handle(text)))
            }
            busy = false
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            Header(brainReady = brain != null)
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (items.isEmpty()) item { EmptyHint() }
                items(items) { item ->
                    when (item) {
                        is UserMsg -> Bubble(item.text, fromUser = true)
                        is MitraMsg -> Bubble(item.text, fromUser = false)
                        is ActionCard -> ActionCardView(item, onConfirm = ::runCard, onCancel = ::cancelCard)
                    }
                }
            }
            InputBar(value = input, onValueChange = { input = it }, onSend = ::send, enabled = !busy)
            Spacer(Modifier.size(8.dp))
        }
    }
}

private fun actionTitle(call: ToolCall): String = when (call.name) {
    "toggle_flashlight" -> if (call.args["on"] == false) "Turn flashlight off" else "Turn flashlight on"
    "set_alarm" -> "Set alarm"
    "start_timer" -> "Start timer"
    "open_url" -> "Open link"
    "open_app" -> "Open app"
    "set_media_volume" -> "Set volume"
    "set_brightness" -> "Set brightness"
    else -> call.name.replace('_', ' ').replaceFirstChar { it.uppercase() }
}

private fun actionDetail(call: ToolCall): String = when (call.name) {
    "set_alarm" -> {
        val h = (call.args["hour"] as? Number)?.toInt()
        val m = (call.args["minute"] as? Number)?.toInt() ?: 0
        if (h != null) String.format("for %02d:%02d", h, m) else "alarm"
    }
    "start_timer" -> "${(call.args["seconds"] as? Number)?.toInt() ?: "?"} seconds"
    "open_url" -> (call.args["url"] as? String).orEmpty()
    "open_app" -> (call.args["name"] as? String) ?: (call.args["package_name"] as? String).orEmpty()
    "set_media_volume" -> "to ${(call.args["level"] as? Number)?.toInt() ?: "?"}%"
    "set_brightness" -> "to ${(call.args["level"] as? Number)?.toInt() ?: "?"}%"
    "toggle_flashlight" -> "on your device"
    else -> ""
}

@Composable
private fun Header(brainReady: Boolean) {
    Column(Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 10.dp)) {
        Text("Mitra", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.size(2.dp))
        Text(
            if (brainReady) "On-device · offline · nothing leaves this phone"
            else "Basic mode · model not loaded · flashlight works",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun EmptyHint() {
    Column(Modifier.fillMaxWidth().padding(top = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "Try “turn on the flashlight”, “set an alarm for 7:30”, or just say hi.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Bubble(text: String, fromUser: Boolean) {
    val bg = if (fromUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val fg = if (fromUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (fromUser) Arrangement.End else Arrangement.Start,
    ) {
        Surface(color = bg, shape = MaterialTheme.shapes.large, modifier = Modifier.widthIn(max = 300.dp)) {
            Text(
                text,
                color = fg,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

@Composable
private fun ActionCardView(card: ActionCard, onConfirm: (Int) -> Unit, onCancel: (Int) -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(36.dp).clip(CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = CircleShape) {
                        Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                            Text("⚡", style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
                Spacer(Modifier.size(12.dp))
                Column {
                    Text(card.title, style = MaterialTheme.typography.titleMedium)
                    if (card.detail.isNotBlank()) {
                        Text(
                            card.detail,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Spacer(Modifier.size(14.dp))
            when (card.state) {
                ActionState.CONFIRM -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = { onCancel(card.id) },
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier.weight(1f).height(48.dp),
                    ) { Text("Cancel") }
                    Button(
                        onClick = { onConfirm(card.id) },
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier.weight(1f).height(48.dp),
                    ) { Text("Confirm") }
                }
                ActionState.RUNNING -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.size(10.dp))
                    Text("Working…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                ActionState.DONE -> Text("✓ Done", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                ActionState.FAILED -> Text("✗ ${card.detail}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                ActionState.CANCELLED -> Text("Cancelled", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun InputBar(value: String, onValueChange: (String) -> Unit, onSend: () -> Unit, enabled: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text("Talk to Mitra") },
            modifier = Modifier.weight(1f),
            enabled = enabled,
            singleLine = true,
            shape = MaterialTheme.shapes.large,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            ),
        )
        val canSend = enabled && value.isNotBlank()
        Surface(
            shape = MaterialTheme.shapes.large,
            color = if (canSend) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.size(52.dp),
        ) {
            IconButton(onClick = onSend, enabled = canSend) {
                Text(
                    "➤",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (canSend) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
