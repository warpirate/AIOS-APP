package com.mitra.tools

/** How risky a tool's action is. Anything but [None] is gated by [com.mitra.safety.ConfirmationGate]. */
enum class SideEffect { None, Reversible, Irreversible }

sealed interface ToolResult {
    data class Success(val message: String) : ToolResult
    data class Failure(val message: String) : ToolResult
}

/** One device action. One tool per file. */
interface Tool {
    val name: String
    val sideEffect: SideEffect
    fun execute(args: Map<String, Any?>): ToolResult
}

// The model emits args as JSON; values may arrive as Number, String, or Boolean. Coerce defensively.
fun argInt(value: Any?): Int? = when (value) {
    is Number -> value.toInt()
    is String -> value.trim().toDoubleOrNull()?.toInt()
    else -> null
}

fun argString(value: Any?): String? = (value as? String)?.trim()?.ifBlank { null }

fun argBool(value: Any?): Boolean? = when (value) {
    is Boolean -> value
    is String -> value.trim().lowercase().toBooleanStrictOrNull()
    else -> null
}
