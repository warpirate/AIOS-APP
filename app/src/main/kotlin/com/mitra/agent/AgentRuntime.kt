// app/src/main/kotlin/com/mitra/agent/AgentRuntime.kt
package com.mitra.agent

import com.mitra.automation.AutomationAction
import com.mitra.automation.AutomationBackend
import com.mitra.automation.BackendResult
import com.mitra.safety.AuditLog
import com.mitra.tools.SideEffect
import com.mitra.tools.ToolResult
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow

/**
 * State machine: utterance -> plan -> dispatch step (gating Irreversible) -> feed result back to
 * brain -> repeat until no more tool calls or [STEP_CAP] is hit -> Done/Failed.
 *
 * Two execution modes within a single `run(utterance)` call:
 *
 *  - **IntentParser shortcut:** [parser].route matches → one step dispatched, then Done. No brain
 *    involvement. Deterministic regex fallback for the 25-ish common commands.
 *  - **Agentic loop:** parser does NOT match AND [brain] is non-null → stream brain.chatStream,
 *    dispatch each tool call as it arrives, feed `Content.ToolResponse` back via
 *    [com.mitra.inference.Brain.sendToolResult], repeat up to [STEP_CAP] iterations. Final brain
 *    text becomes Done.summary.
 *
 * Brain-less mode (model failed to load and parser didn't match) emits an empty plan + a friendly
 * Done.
 *
 * **Gate policy (V1):** Only [SideEffect.Irreversible] steps emit a [RuntimeEvent.GateRequested]
 * and wait for [resume]. [SideEffect.Reversible] / [SideEffect.None] auto-run. The bulk
 * pre-confirm card for Reversible chains is deferred to Phase 2.
 *
 * **Failure-recovery policy:** When dispatch fails inside the loop, the failure is fed back into
 * the brain conversation as `{"ok": false, "error": "..."}`. The brain decides what to do next:
 * call another tool with different args, ask the user, or finish. This is the core of the agentic
 * shape — no hard-coded retry, no silent skip.
 *
 * Cancellation at the gate is communicated to the brain as `{"cancelled": true}` so the brain can
 * finish with an acknowledging clause (e.g. "okay, didn't send.") instead of looping.
 */
class AgentRuntime(
    private val brain: com.mitra.inference.Brain?,
    private val parser: IntentParser,
    private val sideEffectOf: (String) -> SideEffect,
    private val backends: List<AutomationBackend>,
    private val context: ContextStore,
    private val audit: AuditLog,
    // Whether a given side-effect class must pause for the ConfirmationGate. Default keeps
    // V1 behaviour (Irreversible only); MainActivity wires a UserPrefs-backed lambda so
    // SideEffect.Reversible can additionally gate under STRICT mode. Evaluated per-step so a
    // mid-conversation Settings change takes effect on the next dispatch.
    private val requiresGate: (SideEffect) -> Boolean = { it == SideEffect.Irreversible },
) {
    @Volatile private var currentGate: Channel<GateDecision>? = null

    fun run(utterance: UserUtterance): Flow<RuntimeEvent> =
        flow {
            check(currentGate == null) { "AgentRuntime is single-turn-at-a-time; previous run still active" }
            val gate = Channel<GateDecision>(capacity = Channel.RENDEZVOUS)
            currentGate = gate
            context.beginTurn(utterance)
            try {
                parser.route(utterance.text)?.let { call ->
                    val step = PlannedStep(call.name, call.args, sideEffectOf(call.name))
                    emit(RuntimeEvent.PlanReady(Plan(listOf(step), null, 1.0f)))
                    val failure = dispatchStep(step, index = 0, gate = gate)
                    when (failure) {
                        null -> emit(RuntimeEvent.Done(summary = "done"))
                        CANCEL_SENTINEL -> emit(RuntimeEvent.Failed(reason = "cancelled by user"))
                        else -> emit(RuntimeEvent.Failed(reason = failure))
                    }
                    return@flow
                }

                val brain = brain
                if (brain == null) {
                    emit(RuntimeEvent.PlanReady(Plan(emptyList(), null, 1.0f)))
                    emit(RuntimeEvent.Done(summary = "I'm not sure how to help with that one yet."))
                    return@flow
                }

                var stream =
                    try {
                        brain.chatStream(utterance.text)
                    } catch (c: kotlinx.coroutines.CancellationException) {
                        throw c
                    } catch (_: Throwable) {
                        emit(RuntimeEvent.Failed(reason = JNI_FAILURE_REASON))
                        return@flow
                    }
                var stepIndex = 0
                var lastText = ""
                while (stepIndex < STEP_CAP) {
                    var emittedCall: ToolCall? = null
                    try {
                        stream.collect { turn ->
                            if (turn.text.isNotEmpty()) {
                                lastText = turn.text
                                emit(RuntimeEvent.Speaking(text = turn.text))
                            }
                            turn.toolCall?.let { emittedCall = it }
                        }
                    } catch (c: kotlinx.coroutines.CancellationException) {
                        throw c
                    } catch (_: Throwable) {
                        emit(RuntimeEvent.Failed(reason = JNI_FAILURE_REASON))
                        return@flow
                    }

                    val call = emittedCall
                    if (call == null) {
                        emit(RuntimeEvent.Done(summary = lastText.ifBlank { "done" }))
                        return@flow
                    }

                    val step = PlannedStep(call.name, call.args, sideEffectOf(call.name))
                    emit(RuntimeEvent.PlanReady(Plan(listOf(step), null, 1.0f)))
                    val failure = dispatchStep(step, index = stepIndex, gate = gate)
                    val cancelled = failure == CANCEL_SENTINEL
                    val resultMap =
                        buildResultMap(
                            toolName = call.name,
                            failure = if (cancelled) null else failure,
                            cancelled = cancelled,
                        )
                    stepIndex++
                    stream =
                        try {
                            brain.sendToolResult(call.name, resultMap)
                        } catch (c: kotlinx.coroutines.CancellationException) {
                            throw c
                        } catch (_: Throwable) {
                            emit(RuntimeEvent.Failed(reason = JNI_FAILURE_REASON))
                            return@flow
                        }
                }
                emit(RuntimeEvent.Failed(reason = "hit the tool limit — let me know what else you need"))
            } finally {
                currentGate = null
                gate.close()
                context.endTurn()
            }
        }

    /** Returns null on success, the failure reason string on backend failure, or
     *  [CANCEL_SENTINEL] on user cancellation at the gate. Emits StepStarted /
     *  StepCompleted / GateRequested events along the way. */
    private suspend fun FlowCollector<RuntimeEvent>.dispatchStep(
        step: PlannedStep,
        index: Int,
        gate: Channel<GateDecision>,
    ): String? {
        if (requiresGate(step.sideEffect)) {
            emit(RuntimeEvent.GateRequested(index, step))
            val decision = gate.receive()
            if (decision == GateDecision.Cancel) {
                audit.record(step.toolName, step.sideEffect, ok = false)
                return CANCEL_SENTINEL
            }
        }
        emit(RuntimeEvent.StepStarted(index, step))
        val action = AutomationAction.ToolDispatch(step.toolName, step.args)
        val backend = backends.firstOrNull { it.supports(action) }
        val result: BackendResult =
            backend?.execute(action) ?: BackendResult.Failure("no backend supports ${step.toolName}")
        audit.record(step.toolName, step.sideEffect, ok = result is BackendResult.Success)
        context.recordToolResult(
            when (result) {
                is BackendResult.Success -> ToolResult.Success(result.message)
                is BackendResult.Failure -> ToolResult.Failure(result.message)
            },
        )
        emit(RuntimeEvent.StepCompleted(index, step, result))
        return when (result) {
            is BackendResult.Success -> null
            is BackendResult.Failure -> result.message
        }
    }

    private fun buildResultMap(toolName: String, failure: String?, cancelled: Boolean): Map<String, Any?> =
        when {
            cancelled -> mapOf("cancelled" to true)
            failure == null -> mapOf("ok" to true, "message" to "tool $toolName ran")
            else -> mapOf("ok" to false, "error" to failure)
        }

    /** Called by UI in response to [RuntimeEvent.GateRequested]. */
    suspend fun resume(decision: GateDecision) {
        val g = currentGate ?: error("AgentRuntime.resume called with no active turn")
        g.send(decision)
    }

    /** UI-driven single-step dispatch — bypasses both [parser] and [brain]. Used by the Undo
     *  affordance on Reversible action cards: the UI hands back the [UndoSpec] captured at the
     *  forward execute, we convert it to a [ToolCall], and run it through the same
     *  [dispatchStep] (so it still gates under STRICT, writes to [audit], and surfaces step
     *  events) without the brain getting involved. No turn boundary — undo is not a fresh
     *  conversation turn; it's an attachment to the prior turn the user just performed.
     */
    fun runStep(call: ToolCall, source: String): Flow<RuntimeEvent> =
        flow {
            check(currentGate == null) { "AgentRuntime.runStep called while another turn is active" }
            val gate = Channel<GateDecision>(capacity = Channel.RENDEZVOUS)
            currentGate = gate
            try {
                val step = PlannedStep(call.name, call.args, sideEffectOf(call.name))
                emit(RuntimeEvent.PlanReady(Plan(listOf(step), null, 1.0f)))
                val failure = dispatchStep(step, index = 0, gate = gate)
                when (failure) {
                    null -> emit(RuntimeEvent.Done(summary = source))
                    CANCEL_SENTINEL -> emit(RuntimeEvent.Failed(reason = "cancelled by user"))
                    else -> emit(RuntimeEvent.Failed(reason = failure))
                }
            } finally {
                currentGate = null
                gate.close()
            }
        }

    companion object {
        const val STEP_CAP = 5
        private const val CANCEL_SENTINEL = "__cancelled__"
        private const val JNI_FAILURE_REASON = "I lost my train of thought. Mind sending that again?"
    }
}
