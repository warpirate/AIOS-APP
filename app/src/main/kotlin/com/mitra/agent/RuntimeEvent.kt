// app/src/main/kotlin/com/mitra/agent/RuntimeEvent.kt
package com.mitra.agent

import com.mitra.automation.BackendResult

/** Events emitted by [AgentRuntime.run]; the UI consumes them and renders accordingly. */
sealed interface RuntimeEvent {
    /** Brain is producing chat text (streamed). UI may render as a typing reply. */
    data class Speaking(val text: String) : RuntimeEvent

    /** Planner returned a plan. UI may render a confirm card. */
    data class PlanReady(val plan: Plan) : RuntimeEvent

    /** Step N is about to execute. */
    data class StepStarted(val index: Int, val step: PlannedStep) : RuntimeEvent

    /** Step N finished with a backend result. */
    data class StepCompleted(val index: Int, val step: PlannedStep, val result: BackendResult) : RuntimeEvent

    /** AgentRuntime paused: needs user decision for an Irreversible step. UI must show a modal,
     *  then call [AgentRuntime.resume] with a [GateDecision]. */
    data class GateRequested(val index: Int, val step: PlannedStep) : RuntimeEvent

    /** Planner replanned mid-execution (e.g. step failed). */
    data class Replan(val reason: String, val newPlan: Plan) : RuntimeEvent

    /** Terminal: run finished normally. */
    data class Done(val summary: String) : RuntimeEvent

    /** Terminal: run aborted or hit a fatal error. */
    data class Failed(val reason: String) : RuntimeEvent
}

/** User's answer to a [RuntimeEvent.GateRequested]. */
enum class GateDecision { Approve, Cancel }
