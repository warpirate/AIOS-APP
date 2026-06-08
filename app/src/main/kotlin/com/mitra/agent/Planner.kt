// app/src/main/kotlin/com/mitra/agent/Planner.kt
package com.mitra.agent

/**
 * Turns a user utterance + the current [TurnContext] into a [Plan]. V1 impl is single-shot
 * (wraps the brain once); V2 will be plan-then-execute; V3 will be hierarchical. AgentRuntime
 * never knows which impl is plugged in.
 */
interface Planner {
    suspend fun plan(utterance: String, ctx: TurnContext): Plan
}
