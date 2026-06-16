package com.mitra.tools

import com.mitra.automation.AutomationTier

/** How risky a tool's action is. Anything but [None] is gated by [com.mitra.safety.ConfirmationGate]. */
enum class SideEffect { None, Reversible, Irreversible }

sealed interface ToolResult {
    data class Success(
        val message: String,
    ) : ToolResult

    data class Failure(
        val message: String,
    ) : ToolResult
}

/** A tool-call shape that, if dispatched, restores the state the user had before the most recent
 *  Reversible execute(). Lives in `tools/` (not `agent/`) to avoid a circular import — the agent
 *  layer already imports `tools/`, so the inverse direction would form a cycle. UI / dispatcher
 *  maps this 1:1 to an `agent.ToolCall` when the user taps Undo. */
data class UndoSpec(
    val toolName: String,
    val args: Map<String, Any?>,
)

/** One device action. One tool per file. */
interface Tool {
    val name: String
    val sideEffect: SideEffect

    /** Lowest-cost backend tier that can execute this tool. Defaults to ManagerApi — all V1 tools
     *  go through ManagerApiBackend. Later tools (e.g. WhatsApp reply) override to RemoteInput. */
    val tier: AutomationTier get() = AutomationTier.ManagerApi

    fun execute(args: Map<String, Any?>): ToolResult

    /** Called BY THE DISPATCHER immediately before [execute] for Reversible tools. Returns the
     *  inverse action — a tool-call that, if dispatched later, restores whatever state this call
     *  is about to overwrite. Default null means the tool is not undo-capable yet. The dispatcher
     *  attaches the result to `BackendResult.Success.undo` only when both capture AND execute
     *  succeed, so a captured-but-failed action never offers a misleading Undo. */
    fun captureUndo(args: Map<String, Any?>): UndoSpec? = null
}

// The model emits args as JSON; values may arrive as Number, String, or Boolean. Coerce defensively.
fun argInt(value: Any?): Int? =
    when (value) {
        is Number -> value.toInt()
        is String -> value.trim().toDoubleOrNull()?.toInt()
        else -> null
    }

fun argString(value: Any?): String? = (value as? String)?.trim()?.ifBlank { null }

fun argBool(value: Any?): Boolean? =
    when (value) {
        is Boolean -> value
        is String -> value.trim().lowercase().toBooleanStrictOrNull()
        else -> null
    }
