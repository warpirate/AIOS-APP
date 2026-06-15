package com.mitra.inference

import kotlinx.coroutines.flow.Flow

/**
 * The brain abstraction the agent layer talks to. Two concrete implementations:
 *   - [LiteRtBrain] — Gemma 4 E2B running on LiteRT-LM CPU backend (production).
 *   - `FakeBrain` (test/) — scripted emissions used by [com.mitra.agent.AgentRuntimeTest].
 *
 * [chatStream] is called once at the start of a turn with the user's utterance.
 * [sendToolResult] is called after each tool the runtime dispatched on the brain's behalf, to
 * feed the outcome back into the conversation so the brain can decide its next step (call another
 * tool, ask a clarifying question, or emit a final reply).
 */
interface Brain {
    /** Stream the brain's reply to a user utterance. Each emission is the cumulative reply so
     *  far (NOT a delta) plus optionally one tool call the brain has decided to emit. */
    fun chatStream(userText: String): Flow<BrainTurn>

    /** Feed a tool's execution result back into the brain's conversation and stream the next
     *  reply / tool call. The runtime calls this once per tool dispatched within the same turn. */
    fun sendToolResult(toolName: String, result: Map<String, Any?>): Flow<BrainTurn>
}
