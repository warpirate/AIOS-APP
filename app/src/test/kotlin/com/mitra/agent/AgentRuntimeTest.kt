package com.mitra.agent

import com.mitra.automation.AutomationAction
import com.mitra.automation.AutomationBackend
import com.mitra.automation.AutomationTier
import com.mitra.automation.BackendResult
import com.mitra.safety.AuditLog
import com.mitra.tools.SideEffect
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private class StubPlanner(
    private val plan: Plan,
) : Planner {
    override suspend fun plan(utterance: String, ctx: TurnContext): Plan = plan
}

private class StubBackend(
    private val result: BackendResult = BackendResult.Success("ok"),
) : AutomationBackend {
    override val tier = AutomationTier.ManagerApi
    val dispatches = mutableListOf<AutomationAction.ToolDispatch>()

    override fun supports(action: AutomationAction): Boolean = true

    override suspend fun execute(action: AutomationAction): BackendResult {
        if (action is AutomationAction.ToolDispatch) dispatches += action
        return result
    }
}

class AgentRuntimeTest {
    private fun runtimeWith(plan: Plan, backend: StubBackend = StubBackend()): AgentRuntime =
        AgentRuntime(
            planner = StubPlanner(plan),
            backends = listOf(backend),
            context = TurnOnlyContextStore { 0L },
            audit = AuditLog(),
        )

    @Test
    fun `empty plan emits PlanReady then Done`() =
        runBlocking {
            val rt = runtimeWith(Plan(emptyList(), null, 1.0f))
            val events = rt.run(UserUtterance("hi", "test")).toList()
            assertTrue(events.any { it is RuntimeEvent.PlanReady })
            assertTrue(events.last() is RuntimeEvent.Done)
        }

    @Test
    fun `single None-side-effect step runs without gate`() =
        runBlocking {
            val plan = Plan(listOf(PlannedStep("open_url", mapOf("url" to "x.com"), SideEffect.None)), null, 1.0f)
            val backend = StubBackend()
            val rt = runtimeWith(plan, backend)
            val events = rt.run(UserUtterance("open x.com", "test")).toList()
            assertEquals(1, backend.dispatches.size)
            assertTrue(events.none { it is RuntimeEvent.GateRequested })
            assertTrue(events.any { it is RuntimeEvent.StepCompleted })
            assertTrue(events.last() is RuntimeEvent.Done)
        }

    @Test
    fun `Irreversible step pauses on GateRequested and runs after Approve`() =
        runBlocking {
            val plan = Plan(listOf(PlannedStep("send_sms", mapOf("to" to "x", "body" to "y"), SideEffect.Irreversible)), null, 1.0f)
            val backend = StubBackend()
            val rt = runtimeWith(plan, backend)
            val collected = mutableListOf<RuntimeEvent>()
            val job =
                launch {
                    rt.run(UserUtterance("send sms", "test")).collect { collected += it }
                }
            // Wait for the gate event to appear. delay(1) yields to the runBlocking dispatcher so the
            // launched collector coroutine can progress (a tight spin would deadlock here).
            while (collected.none { it is RuntimeEvent.GateRequested }) {
                delay(1)
            }
            assertEquals(0, backend.dispatches.size)
            rt.resume(GateDecision.Approve)
            job.join()
            assertEquals(1, backend.dispatches.size)
            assertTrue(collected.last() is RuntimeEvent.Done)
        }

    @Test
    fun `Irreversible step cancelled by user produces Failed terminal`() =
        runBlocking {
            val plan = Plan(listOf(PlannedStep("send_sms", mapOf("to" to "x"), SideEffect.Irreversible)), null, 1.0f)
            val backend = StubBackend()
            val rt = runtimeWith(plan, backend)
            val collected = mutableListOf<RuntimeEvent>()
            val job =
                launch {
                    rt.run(UserUtterance("send sms", "test")).collect { collected += it }
                }
            while (collected.none { it is RuntimeEvent.GateRequested }) {
                delay(1)
            }
            rt.resume(GateDecision.Cancel)
            job.join()
            assertEquals(0, backend.dispatches.size)
            assertTrue(collected.last() is RuntimeEvent.Failed)
        }

    @Test
    fun `backend failure makes runtime emit Failed`() =
        runBlocking {
            val plan = Plan(listOf(PlannedStep("open_url", mapOf("url" to "x"), SideEffect.None)), null, 1.0f)
            val backend = StubBackend(BackendResult.Failure("boom"))
            val rt = runtimeWith(plan, backend)
            val events = rt.run(UserUtterance("open x", "test")).toList()
            assertTrue(events.last() is RuntimeEvent.Failed)
        }

    @Test
    fun `no backend supports the action - Failed`() =
        runBlocking {
            val plan = Plan(listOf(PlannedStep("nope", emptyMap(), SideEffect.None)), null, 1.0f)
            val rt =
                AgentRuntime(
                    planner = StubPlanner(plan),
                    backends = emptyList(),
                    context = TurnOnlyContextStore { 0L },
                    audit = AuditLog(),
                )
            val events = rt.run(UserUtterance("x", "test")).toList()
            assertTrue(events.last() is RuntimeEvent.Failed)
        }

    @Test
    fun `Reversible step runs without GateRequested (V1 intentional)`() =
        runBlocking {
            val plan = Plan(listOf(PlannedStep("set_brightness", mapOf("level" to 50), SideEffect.Reversible)), null, 1.0f)
            val backend = StubBackend()
            val rt = runtimeWith(plan, backend)
            val events = rt.run(UserUtterance("set brightness to 50%", "test")).toList()
            assertEquals(1, backend.dispatches.size)
            assertTrue("Reversible must not pause on a gate in V1", events.none { it is RuntimeEvent.GateRequested })
            assertTrue(events.last() is RuntimeEvent.Done)
        }
}
