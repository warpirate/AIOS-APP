package com.mitra.agent

import com.mitra.tools.SideEffect

/**
 * Planner that uses only the deterministic [IntentParser] — no LLM. Two consumers:
 *  - [com.mitra.eval.EvalSmokeTest] uses it to gate planner+dispatch regressions in CI
 *    without needing a model file or device.
 *  - AgentRuntime can fall back to it when [LiteRtBrain] fails to load (brain-less mode).
 */
class IntentParserPlanner(
    private val parser: IntentParser,
    private val sideEffectOf: (String) -> SideEffect,
) : Planner {
    override suspend fun plan(utterance: String, ctx: TurnContext): Plan {
        val call = parser.route(utterance) ?: return Plan(steps = emptyList(), rationale = null, confidence = 1.0f)
        val step = PlannedStep(toolName = call.name, args = call.args, sideEffect = sideEffectOf(call.name))
        return Plan(steps = listOf(step), rationale = null, confidence = 1.0f)
    }
}
