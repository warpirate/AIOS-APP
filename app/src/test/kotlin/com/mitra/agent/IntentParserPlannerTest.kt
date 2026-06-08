package com.mitra.agent

import com.mitra.tools.SideEffect
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IntentParserPlannerTest {
    private val planner = IntentParserPlanner(
        parser = IntentParser(),
        sideEffectOf = { name ->
            // Match the production tool registry's classifications for the few names we test here.
            when (name) {
                "toggle_flashlight" -> SideEffect.Reversible
                "open_url" -> SideEffect.None
                else -> SideEffect.Reversible
            }
        },
    )

    @Test
    fun `recognised command produces a single-step plan`() = runBlocking {
        val ctx = TurnContext(UserUtterance("turn on the flashlight", "test"), 0L, null)
        val plan = planner.plan("turn on the flashlight", ctx)
        assertEquals(1, plan.steps.size)
        assertEquals("toggle_flashlight", plan.steps[0].toolName)
        assertEquals(true, plan.steps[0].args["on"])
        assertEquals(SideEffect.Reversible, plan.steps[0].sideEffect)
    }

    @Test
    fun `unrecognised input returns empty plan`() = runBlocking {
        val ctx = TurnContext(UserUtterance("how are you", "test"), 0L, null)
        val plan = planner.plan("how are you", ctx)
        assertTrue(plan.steps.isEmpty())
    }

    @Test
    fun `confidence is fixed at 1 for V1 single-shot`() = runBlocking {
        val ctx = TurnContext(UserUtterance("turn on the flashlight", "test"), 0L, null)
        val plan = planner.plan("turn on the flashlight", ctx)
        assertEquals(1.0f, plan.confidence, 0.0001f)
    }
}
