package com.mitra.agent

import com.mitra.safety.AuditLog
import com.mitra.tools.SideEffect
import com.mitra.tools.Tool
import com.mitra.tools.ToolResult

/**
 * The whole loop: text -> router -> tool -> result. Tools come in via [ToolRegistry]; the
 * confirmation decision lives in [com.mitra.safety.ConfirmationGate], the UI consults it
 * before running anything classified as Reversible / Irreversible.
 */
class AgentLoop(
    private val router: Router,
    tools: List<Tool>,
    private val audit: AuditLog = AuditLog(),
) {
    private val toolsByName = tools.associateBy { it.name }

    /** Parse a request into a tool call (the reliable action path), or null if it's not a known command. */
    fun parse(input: String): ToolCall? = router.route(input)

    /** Router path: text -> route -> execute. Used as the fallback when the LLM brain is absent. */
    fun handle(input: String): String {
        val call = router.route(input) ?: return "I can't do that yet."
        return execute(call)
    }

    /** Run an already-decided tool call (e.g. one the LLM brain emitted), formatted for the keyword path. */
    fun execute(call: ToolCall): String = when (val result = runCall(call)) {
        is ToolResult.Success -> "✓ ${result.message}"
        is ToolResult.Failure -> "✗ ${result.message}"
    }

    /** Run a tool call and return the raw result (the UI renders it as an action card). */
    fun runCall(call: ToolCall): ToolResult {
        val tool = toolsByName[call.name]
            ?: return ToolResult.Failure("I don't have a tool for that yet").also {
                audit.record(call.name, sideEffect = null, ok = false)
            }
        val result = tool.execute(call.args)
        audit.record(call.name, tool.sideEffect, ok = result is ToolResult.Success)
        return result
    }

    /** The side-effect class of a tool (None bypasses the confirmation card; others gate on confirm). */
    fun sideEffectOf(name: String): SideEffect? = toolsByName[name]?.sideEffect

    /** Read-only view of recent tool calls (name + class + outcome only — never content). */
    fun auditEntries() = audit.entries()
}
