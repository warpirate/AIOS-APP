package com.mitra.agent

import com.mitra.inference.BrainTurn
import com.mitra.tools.SideEffect
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Minimal stand-in for LiteRtBrain.chatStream so tests don't load a model. */
private class FakeBrain(private val turns: List<BrainTurn>) {
    fun chatStream(@Suppress("UNUSED_PARAMETER") text: String): Flow<BrainTurn> = flow {
        for (t in turns) emit(t)
    }
}

class SingleShotPlannerTest {
    private val parser = IntentParser()
    private val sideEffectOf: (String) -> SideEffect = {
        if (it == "toggle_flashlight") SideEffect.Reversible else SideEffect.None
    }

    @Test
    fun `brain emits a tool call - planner returns single-step plan`() = runBlocking {
        val brain = FakeBrain(listOf(BrainTurn(text = "OK", toolCall = com.mitra.agent.ToolCall("toggle_flashlight", mapOf("on" to true)))))
        val planner = SingleShotPlanner(brainStream = brain::chatStream, parser = parser, sideEffectOf = sideEffectOf, onChunk = {})
        val plan = planner.plan("turn on the flashlight", TurnContext(UserUtterance("turn on the flashlight", "test"), 0L, null))
        assertEquals(1, plan.steps.size)
        assertEquals("toggle_flashlight", plan.steps[0].toolName)
    }

    @Test
    fun `brain returns chat-only - planner falls back to parser`() = runBlocking {
        val brain = FakeBrain(listOf(BrainTurn(text = "hi there", toolCall = null)))
        val planner = SingleShotPlanner(brainStream = brain::chatStream, parser = parser, sideEffectOf = sideEffectOf, onChunk = {})
        val plan = planner.plan("turn on the flashlight", TurnContext(UserUtterance("turn on the flashlight", "test"), 0L, null))
        assertEquals(1, plan.steps.size)
        assertEquals("toggle_flashlight", plan.steps[0].toolName)
    }

    @Test
    fun `brain returns chat-only and parser also has nothing - empty plan`() = runBlocking {
        val brain = FakeBrain(listOf(BrainTurn(text = "hello", toolCall = null)))
        val planner = SingleShotPlanner(brainStream = brain::chatStream, parser = parser, sideEffectOf = sideEffectOf, onChunk = {})
        val plan = planner.plan("how are you", TurnContext(UserUtterance("how are you", "test"), 0L, null))
        assertTrue(plan.steps.isEmpty())
        assertEquals("hello", plan.rationale)
    }

    @Test
    fun `streaming chunks are delivered to onChunk in order`() = runBlocking {
        val brain = FakeBrain(listOf(BrainTurn("Hel", null), BrainTurn("Hello", null), BrainTurn("Hello!", null)))
        val chunks = mutableListOf<String>()
        val planner = SingleShotPlanner(brainStream = brain::chatStream, parser = parser, sideEffectOf = sideEffectOf, onChunk = { chunks += it })
        planner.plan("hello", TurnContext(UserUtterance("hello", "test"), 0L, null))
        assertEquals(listOf("Hel", "Hello", "Hello!"), chunks)
    }
}
