// app/src/main/kotlin/com/mitra/agent/SingleShotPlanner.kt
package com.mitra.agent

import com.mitra.inference.BrainTurn
import com.mitra.tools.SideEffect
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect

/**
 * V1 [Planner] impl: runs the brain once, takes whatever tool call (if any) it emitted, or falls
 * back to [IntentParser] if the brain only chatted. `onChunk` receives streaming chat text as it
 * arrives so [AgentRuntime] can emit [RuntimeEvent.Speaking].
 *
 * Confidence is fixed at 1.0 in V1 — there is no data yet to calibrate against. Phase 2's
 * PlanThenExecutePlanner is where confidence becomes meaningful.
 */
class SingleShotPlanner(
    private val brainStream: (String) -> Flow<BrainTurn>,
    private val parser: IntentParser,
    private val sideEffectOf: (String) -> SideEffect,
    private val onChunk: (String) -> Unit,
) : Planner {
    override suspend fun plan(utterance: String, ctx: TurnContext): Plan {
        var lastText = ""
        var lastCall: ToolCall? = null
        brainStream(utterance).collect { turn ->
            if (turn.text.isNotEmpty()) {
                lastText = turn.text
                onChunk(turn.text)
            }
            turn.toolCall?.let { lastCall = it }
        }
        val call = lastCall ?: parser.route(utterance)
        if (call == null) {
            return Plan(steps = emptyList(), rationale = lastText.ifBlank { null }, confidence = 1.0f)
        }
        val step = PlannedStep(toolName = call.name, args = call.args, sideEffect = sideEffectOf(call.name))
        return Plan(steps = listOf(step), rationale = lastText.ifBlank { null }, confidence = 1.0f)
    }
}
