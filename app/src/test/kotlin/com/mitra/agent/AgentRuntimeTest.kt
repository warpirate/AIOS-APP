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
    private fun fixedParser(match: ToolCall?): IntentParser =
        object : IntentParser() {
            override fun route(input: String): ToolCall? = match
        }

    private fun runtimeWith(
        intentMatch: ToolCall? = null,
        backend: StubBackend = StubBackend(),
        sideEffectOf: (String) -> SideEffect = { SideEffect.None },
        requiresGate: (SideEffect) -> Boolean = { it == SideEffect.Irreversible },
    ): AgentRuntime =
        AgentRuntime(
            brain = null,
            parser = fixedParser(intentMatch),
            sideEffectOf = sideEffectOf,
            backends = listOf(backend),
            context = TurnOnlyContextStore { 0L },
            audit = AuditLog(),
            requiresGate = requiresGate,
        )

    @Test
    fun `empty plan emits PlanReady then Done`() =
        runBlocking {
            val rt = runtimeWith(intentMatch = null)
            val events = rt.run(UserUtterance("hi", "test")).toList()
            assertTrue(events.any { it is RuntimeEvent.PlanReady })
            assertTrue(events.last() is RuntimeEvent.Done)
        }

    @Test
    fun `single None-side-effect step runs without gate`() =
        runBlocking {
            val backend = StubBackend()
            val rt =
                runtimeWith(
                    intentMatch = ToolCall("open_url", mapOf("url" to "x.com")),
                    backend = backend,
                    sideEffectOf = { SideEffect.None },
                )
            val events = rt.run(UserUtterance("open x.com", "test")).toList()
            assertEquals(1, backend.dispatches.size)
            assertTrue(events.none { it is RuntimeEvent.GateRequested })
            assertTrue(events.any { it is RuntimeEvent.StepCompleted })
            assertTrue(events.last() is RuntimeEvent.Done)
        }

    @Test
    fun `Irreversible step pauses on GateRequested and runs after Approve`() =
        runBlocking {
            val backend = StubBackend()
            val rt =
                runtimeWith(
                    intentMatch = ToolCall("send_sms", mapOf("to" to "x", "body" to "y")),
                    backend = backend,
                    sideEffectOf = { SideEffect.Irreversible },
                )
            val collected = mutableListOf<RuntimeEvent>()
            val job =
                launch {
                    rt.run(UserUtterance("send sms", "test")).collect { collected += it }
                }
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
            val backend = StubBackend()
            val rt =
                runtimeWith(
                    intentMatch = ToolCall("send_sms", mapOf("to" to "x")),
                    backend = backend,
                    sideEffectOf = { SideEffect.Irreversible },
                )
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
            val backend = StubBackend(BackendResult.Failure("boom"))
            val rt =
                runtimeWith(
                    intentMatch = ToolCall("open_url", mapOf("url" to "x")),
                    backend = backend,
                    sideEffectOf = { SideEffect.None },
                )
            val events = rt.run(UserUtterance("open x", "test")).toList()
            assertTrue(events.last() is RuntimeEvent.Failed)
        }

    @Test
    fun `no backend supports the action - Failed`() =
        runBlocking {
            val rt =
                AgentRuntime(
                    brain = null,
                    parser = fixedParser(ToolCall("nope", emptyMap())),
                    sideEffectOf = { SideEffect.None },
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
            val backend = StubBackend()
            val rt =
                runtimeWith(
                    intentMatch = ToolCall("set_brightness", mapOf("level" to 50)),
                    backend = backend,
                    sideEffectOf = { SideEffect.Reversible },
                )
            val events = rt.run(UserUtterance("set brightness to 50%", "test")).toList()
            assertEquals(1, backend.dispatches.size)
            assertTrue("Reversible must not pause on a gate in V1", events.none { it is RuntimeEvent.GateRequested })
            assertTrue(events.last() is RuntimeEvent.Done)
        }

    @Test
    fun `Reversible step gates under STRICT confirmation mode`() =
        runBlocking {
            val backend = StubBackend()
            val rt =
                runtimeWith(
                    intentMatch = ToolCall("set_brightness", mapOf("level" to 50)),
                    backend = backend,
                    sideEffectOf = { SideEffect.Reversible },
                    requiresGate = { it != SideEffect.None },
                )
            val collected = mutableListOf<RuntimeEvent>()
            val job =
                launch {
                    rt.run(UserUtterance("set brightness to 50%", "test")).collect { collected += it }
                }
            while (collected.none { it is RuntimeEvent.GateRequested }) {
                delay(1)
            }
            assertEquals(
                "STRICT must hold dispatch until Approve",
                0,
                backend.dispatches.size,
            )
            rt.resume(GateDecision.Approve)
            job.join()
            assertEquals(1, backend.dispatches.size)
            assertTrue(collected.last() is RuntimeEvent.Done)
        }

    @Test
    fun `runStep dispatches a tool call without parser or brain`() =
        runBlocking {
            val backend = StubBackend()
            val rt =
                AgentRuntime(
                    brain = null,
                    parser = fixedParser(null),
                    sideEffectOf = { SideEffect.Reversible },
                    backends = listOf(backend),
                    context = TurnOnlyContextStore { 0L },
                    audit = AuditLog(),
                )
            val events = rt.runStep(ToolCall("set_brightness", mapOf("level" to 30)), source = "undo").toList()
            assertEquals(1, backend.dispatches.size)
            assertEquals("set_brightness", backend.dispatches.single().name)
            assertEquals(30, backend.dispatches.single().args["level"])
            assertTrue(events.last() is RuntimeEvent.Done)
        }

    @Test
    fun `runStep surfaces undo in StepCompleted result`() =
        runBlocking {
            val undoSpec = com.mitra.tools.UndoSpec("set_brightness", mapOf("level" to 80))
            val backend = StubBackend(BackendResult.Success("Brightness set to 30%", undo = undoSpec))
            val rt =
                AgentRuntime(
                    brain = null,
                    parser = fixedParser(null),
                    sideEffectOf = { SideEffect.Reversible },
                    backends = listOf(backend),
                    context = TurnOnlyContextStore { 0L },
                    audit = AuditLog(),
                )
            val events = rt.runStep(ToolCall("set_brightness", mapOf("level" to 30)), source = "chat").toList()
            val completed =
                events.filterIsInstance<RuntimeEvent.StepCompleted>().single().result
                    as BackendResult.Success
            assertEquals(undoSpec, completed.undo)
        }

    @Test
    fun `None step never gates even under STRICT mode`() =
        runBlocking {
            val backend = StubBackend()
            val rt =
                runtimeWith(
                    intentMatch = ToolCall("open_url", mapOf("url" to "x")),
                    backend = backend,
                    sideEffectOf = { SideEffect.None },
                    requiresGate = { it != SideEffect.None },
                )
            val events = rt.run(UserUtterance("open x", "test")).toList()
            assertEquals(1, backend.dispatches.size)
            assertTrue("None must NEVER pause on a gate", events.none { it is RuntimeEvent.GateRequested })
            assertTrue(events.last() is RuntimeEvent.Done)
        }

    @Test
    fun `agentic loop runs two-step chain end to end`() =
        runBlocking {
            val brain =
                com.mitra.inference.FakeBrain.script(
                    com.mitra.inference.FakeBrain.leg("") { tool("set_dnd", mapOf("on" to true)) },
                    com.mitra.inference.FakeBrain.leg("") { tool("set_ringer_mode", mapOf("mode" to "silent")) },
                    com.mitra.inference.FakeBrain.leg("silent."),
                )
            val backend = StubBackend()
            val rt =
                AgentRuntime(
                    brain = brain,
                    parser = fixedParser(null),
                    sideEffectOf = { SideEffect.Reversible },
                    backends = listOf(backend),
                    context = TurnOnlyContextStore { 0L },
                    audit = AuditLog(),
                )
            val events = rt.run(UserUtterance("quiet for meeting", "test")).toList()
            assertEquals(2, backend.dispatches.size)
            assertEquals("set_dnd", backend.dispatches[0].name)
            assertEquals("set_ringer_mode", backend.dispatches[1].name)
            val done = events.last() as RuntimeEvent.Done
            assertEquals("silent.", done.summary)
        }

    @Test
    fun `agentic loop - user cancels Irreversible step, brain receives cancelled=true`() =
        runBlocking {
            val brain =
                com.mitra.inference.FakeBrain.script(
                    com.mitra.inference.FakeBrain.leg("") {
                        tool("send_sms", mapOf("name" to "blanta", "body" to "hi"))
                    },
                    com.mitra.inference.FakeBrain.leg("okay, didn't send."),
                )
            val backend = StubBackend()
            val rt =
                AgentRuntime(
                    brain = brain,
                    parser = fixedParser(null),
                    sideEffectOf = { SideEffect.Irreversible },
                    backends = listOf(backend),
                    context = TurnOnlyContextStore { 0L },
                    audit = AuditLog(),
                )
            val collected = mutableListOf<RuntimeEvent>()
            val job = launch { rt.run(UserUtterance("text blanta hi", "test")).collect { collected += it } }
            while (collected.none { it is RuntimeEvent.GateRequested }) {
                delay(1)
            }
            rt.resume(GateDecision.Cancel)
            job.join()
            assertEquals(0, backend.dispatches.size)
            assertEquals(1, brain.sentResults.size)
            assertEquals("send_sms", brain.sentResults[0].first)
            assertEquals(true, brain.sentResults[0].second["cancelled"])
            val done = collected.last() as RuntimeEvent.Done
            assertEquals("okay, didn't send.", done.summary)
        }

    @Test
    fun `agentic loop - tool failure is fed back, brain emits final text instead of retrying`() =
        runBlocking {
            val brain =
                com.mitra.inference.FakeBrain.script(
                    com.mitra.inference.FakeBrain.leg("") { tool("make_call", mapOf("name" to "ambiguous")) },
                    com.mitra.inference.FakeBrain.leg("which one — mom (mobile) or mom (work)?"),
                )
            val backend = StubBackend(BackendResult.Failure("multiple matches for 'ambiguous'"))
            val rt =
                AgentRuntime(
                    brain = brain,
                    parser = fixedParser(null),
                    sideEffectOf = { SideEffect.Reversible },
                    backends = listOf(backend),
                    context = TurnOnlyContextStore { 0L },
                    audit = AuditLog(),
                )
            val events = rt.run(UserUtterance("call mom", "test")).toList()
            assertEquals(1, backend.dispatches.size)
            assertEquals(1, brain.sentResults.size)
            assertEquals(false, brain.sentResults[0].second["ok"])
            assertEquals("multiple matches for 'ambiguous'", brain.sentResults[0].second["error"])
            val done = events.last() as RuntimeEvent.Done
            assertEquals("which one — mom (mobile) or mom (work)?", done.summary)
        }

    @Test
    fun `agentic loop - step cap stops a runaway brain`() =
        runBlocking {
            val legs =
                (1..6).map {
                    com.mitra.inference.FakeBrain.leg("") { tool("toggle_flashlight", mapOf("on" to true)) }
                }
            val brain = com.mitra.inference.FakeBrain(legs.toMutableList())
            val backend = StubBackend()
            val rt =
                AgentRuntime(
                    brain = brain,
                    parser = fixedParser(null),
                    sideEffectOf = { SideEffect.Reversible },
                    backends = listOf(backend),
                    context = TurnOnlyContextStore { 0L },
                    audit = AuditLog(),
                )
            val events = rt.run(UserUtterance("spam flashlight", "test")).toList()
            assertEquals(AgentRuntime.STEP_CAP, backend.dispatches.size)
            val last = events.last()
            assertTrue(last is RuntimeEvent.Failed)
            assertEquals(
                "hit the tool limit — let me know what else you need",
                (last as RuntimeEvent.Failed).reason,
            )
        }

    @Test
    fun `agentic loop - brain throws on first chatStream surfaces friendly Failed`() =
        runBlocking {
            val brain =
                object : com.mitra.inference.Brain {
                    override fun chatStream(userText: String) =
                        kotlinx.coroutines.flow.flow<com.mitra.inference.BrainTurn> {
                            throw RuntimeException("simulated LiteRtLmJniException")
                        }

                    override fun sendToolResult(toolName: String, result: Map<String, Any?>) =
                        kotlinx.coroutines.flow.flow<com.mitra.inference.BrainTurn> { }
                }
            val rt =
                AgentRuntime(
                    brain = brain,
                    parser = fixedParser(null),
                    sideEffectOf = { SideEffect.Reversible },
                    backends = listOf(StubBackend()),
                    context = TurnOnlyContextStore { 0L },
                    audit = AuditLog(),
                )
            val events = rt.run(UserUtterance("hi", "test")).toList()
            val last = events.last()
            assertTrue(last is RuntimeEvent.Failed)
            assertEquals(
                "I lost my train of thought. Mind sending that again?",
                (last as RuntimeEvent.Failed).reason,
            )
        }
}
