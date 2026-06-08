package com.mitra.agent

import com.mitra.tools.SideEffect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanTest {
    @Test
    fun `empty plan has no steps`() {
        val p = Plan(steps = emptyList(), rationale = null, confidence = 1.0f)
        assertTrue(p.steps.isEmpty())
    }

    @Test
    fun `planned step defaults dependsOn to empty`() {
        val s = PlannedStep(toolName = "toggle_flashlight", args = mapOf("on" to true), sideEffect = SideEffect.Reversible)
        assertEquals(emptyList<Int>(), s.dependsOn)
    }

    @Test
    fun `plan preserves step order`() {
        val a = PlannedStep("open_app", mapOf("name" to "whatsapp"), SideEffect.None)
        val b = PlannedStep("set_dnd", mapOf("on" to true), SideEffect.Reversible)
        val plan = Plan(steps = listOf(a, b), rationale = null, confidence = 1.0f)
        assertEquals("open_app", plan.steps[0].toolName)
        assertEquals("set_dnd", plan.steps[1].toolName)
    }
}
