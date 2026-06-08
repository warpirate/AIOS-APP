// app/src/main/kotlin/com/mitra/agent/AgentRuntime.kt
package com.mitra.agent

import com.mitra.automation.AutomationAction
import com.mitra.automation.AutomationBackend
import com.mitra.automation.BackendResult
import com.mitra.safety.AuditLog
import com.mitra.safety.ConfirmationGate
import com.mitra.tools.SideEffect
import com.mitra.tools.ToolResult
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * State machine: utterance -> plan -> dispatch each step (gating Irreversible) -> Done/Failed.
 * Replaces the old AgentLoop. Owns the turn lifecycle (no caller touches ContextStore).
 *
 * V1 keeps it deliberately small: no retry-on-failure, no replan-from-runtime (Planner can replan
 * itself but the runtime won't re-call it for a single-shot failure). Those land with
 * [com.mitra.agent.SingleShotPlanner]'s Phase 2 successor.
 */
class AgentRuntime(
    private val planner: Planner,
    private val backends: List<AutomationBackend>,
    private val context: ContextStore,
    private val audit: AuditLog,
) {
    private val gateChannel = Channel<GateDecision>(capacity = Channel.RENDEZVOUS)

    fun run(utterance: UserUtterance): Flow<RuntimeEvent> = flow {
        context.beginTurn(utterance)
        try {
            val ctx = context.turn() ?: error("turn missing after beginTurn")
            val plan = planner.plan(utterance.text, ctx)
            emit(RuntimeEvent.PlanReady(plan))

            if (plan.steps.isEmpty()) {
                emit(RuntimeEvent.Done(summary = plan.rationale ?: "nothing to do"))
                return@flow
            }

            for ((index, step) in plan.steps.withIndex()) {
                if (ConfirmationGate.requiresConfirm(step.sideEffect) && step.sideEffect == SideEffect.Irreversible) {
                    emit(RuntimeEvent.GateRequested(index, step))
                    val decision = gateChannel.receive()
                    if (decision == GateDecision.Cancel) {
                        audit.record(step.toolName, step.sideEffect, ok = false)
                        emit(RuntimeEvent.Failed(reason = "cancelled by user"))
                        return@flow
                    }
                }

                emit(RuntimeEvent.StepStarted(index, step))
                val action = AutomationAction.ToolDispatch(step.toolName, step.args)
                val backend = backends.firstOrNull { it.supports(action) }
                val result: BackendResult = backend?.execute(action)
                    ?: BackendResult.Failure("no backend supports ${step.toolName}")

                audit.record(step.toolName, step.sideEffect, ok = result is BackendResult.Success)
                context.recordToolResult(
                    when (result) {
                        is BackendResult.Success -> ToolResult.Success(result.message)
                        is BackendResult.Failure -> ToolResult.Failure(result.message)
                    },
                )
                emit(RuntimeEvent.StepCompleted(index, step, result))

                if (result is BackendResult.Failure) {
                    emit(RuntimeEvent.Failed(reason = result.message))
                    return@flow
                }
            }

            emit(RuntimeEvent.Done(summary = "done"))
        } finally {
            context.endTurn()
        }
    }

    /** Called by UI in response to [RuntimeEvent.GateRequested]. */
    suspend fun resume(decision: GateDecision) {
        gateChannel.send(decision)
    }
}
