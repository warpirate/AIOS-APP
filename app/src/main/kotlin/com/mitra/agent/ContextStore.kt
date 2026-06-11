// app/src/main/kotlin/com/mitra/agent/ContextStore.kt
package com.mitra.agent

import com.mitra.tools.ToolResult

/** Per-turn read-only snapshot exposed to [Planner.plan]. */
data class TurnContext(
    val utterance: UserUtterance,
    val startedAt: Long,
    val lastToolResult: ToolResult?,
)

/**
 * Where AgentRuntime parks turn state. V1 impl is TurnOnlyContextStore — in-memory, cleared on
 * endTurn(). Session/Long-scope impls land in later phases without consumer changes.
 */
interface ContextStore {
    fun turn(): TurnContext?

    suspend fun beginTurn(utterance: UserUtterance)

    suspend fun recordToolResult(result: ToolResult)

    suspend fun endTurn()
}

class TurnOnlyContextStore(
    private val clockMs: () -> Long = { System.currentTimeMillis() },
) : ContextStore {
    private var current: TurnContext? = null

    override fun turn(): TurnContext? = current

    override suspend fun beginTurn(utterance: UserUtterance) {
        current = TurnContext(utterance = utterance, startedAt = clockMs(), lastToolResult = null)
    }

    override suspend fun recordToolResult(result: ToolResult) {
        current = current?.copy(lastToolResult = result)
    }

    override suspend fun endTurn() {
        current = null
    }
}
