package com.mitra.safety

import com.mitra.tools.SideEffect

/**
 * Content-free audit trail of tool executions. Privacy-invariant: NEVER stores user text,
 * args, contact names, locations, message bodies, URLs, or any string from the model.
 * Only: tool name (constant, from [com.mitra.tools.Tool.name]), side-effect class, success
 * flag, and a monotonic timestamp.
 *
 * In-memory ring buffer for V1 — persisted log lands when M5 settings UI does.
 */
class AuditLog(private val capacity: Int = 200) {

    data class Entry(
        val toolName: String,
        val sideEffect: SideEffect?,
        val ok: Boolean,
        val timestampMs: Long,
    )

    private val buf = ArrayDeque<Entry>(capacity)

    @Synchronized
    fun record(toolName: String, sideEffect: SideEffect?, ok: Boolean) {
        if (buf.size == capacity) buf.removeFirst()
        buf.addLast(Entry(toolName, sideEffect, ok, System.currentTimeMillis()))
    }

    @Synchronized
    fun entries(): List<Entry> = buf.toList()

    @Synchronized
    fun clear() {
        buf.clear()
    }
}
