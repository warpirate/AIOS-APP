package com.mitra.safety

import com.mitra.agent.AgentRuntime
import com.mitra.agent.GateDecision
import com.mitra.agent.IntentParser
import com.mitra.agent.RuntimeEvent
import com.mitra.agent.ToolCall
import com.mitra.agent.TurnOnlyContextStore
import com.mitra.agent.UserUtterance
import com.mitra.automation.AutomationAction
import com.mitra.automation.AutomationBackend
import com.mitra.automation.AutomationTier
import com.mitra.automation.BackendResult
import com.mitra.tools.SideEffect
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

/**
 * Structural R-006 backstop. Two things this guards:
 *
 *  - For every tool the project declares as [SideEffect.Irreversible], `AgentRuntime` MUST emit
 *    [RuntimeEvent.GateRequested] before any backend dispatch happens. The user (and the modal
 *    confirmation card) is the only thing that releases the gate.
 *  - The hard-coded list below must match every Irreversible declaration in any tools source. If a
 *    contributor adds a new Irreversible tool (or downgrades an existing one) and forgets to
 *    update this list, the drift-catcher fails the build with a precise diff so the gate-fires
 *    test can't be silently bypassed for a new tool.
 *
 * This complements the per-Entry field whitelist in [AuditLogTest] — that one prevents the log
 * from leaking content; this one prevents the dispatcher from skipping the gate.
 */
class GateCoverageTest {
    // CONTRACT: every tool whose source declares `SideEffect.Irreversible` MUST appear here. The
    // drift test below proves this set matches the source. Adding a tool? Add its `Tool.name`
    // value here, then write its individual gate-fires assertion if the canned shape doesn't fit.
    private val irreversibleToolNames: Set<String> = setOf("make_call", "send_sms")

    @Test
    fun `every Irreversible tool gates before dispatch and runs only after Approve`() =
        runBlocking {
            for (name in irreversibleToolNames) {
                val backend = RecordingBackend()
                val rt = runtimeFor(name, backend)
                val collected = mutableListOf<RuntimeEvent>()
                val job =
                    launch {
                        rt.run(UserUtterance("invoke $name", "test")).collect { collected += it }
                    }
                // Wait for the gate to surface — assert nothing dispatched yet.
                while (collected.none { it is RuntimeEvent.GateRequested }) {
                    delay(1)
                }
                assertEquals(
                    "Tool '$name' is Irreversible but the backend was hit before the gate fired",
                    0,
                    backend.dispatches.size,
                )
                rt.resume(GateDecision.Approve)
                job.join()
                assertEquals(
                    "Tool '$name' should dispatch exactly once after Approve",
                    1,
                    backend.dispatches.size,
                )
                assertEquals(name, backend.dispatches.single().name)
                assertTrue(collected.last() is RuntimeEvent.Done)
            }
        }

    @Test
    fun `every Irreversible tool aborts dispatch when Cancel is chosen`() =
        runBlocking {
            for (name in irreversibleToolNames) {
                val backend = RecordingBackend()
                val rt = runtimeFor(name, backend)
                val collected = mutableListOf<RuntimeEvent>()
                val job =
                    launch {
                        rt.run(UserUtterance("invoke $name", "test")).collect { collected += it }
                    }
                while (collected.none { it is RuntimeEvent.GateRequested }) {
                    delay(1)
                }
                rt.resume(GateDecision.Cancel)
                job.join()
                assertEquals(
                    "Tool '$name' must not reach the backend when the user cancels the gate",
                    0,
                    backend.dispatches.size,
                )
                assertTrue(
                    "Tool '$name' cancel path must terminate with Failed",
                    collected.last() is RuntimeEvent.Failed,
                )
            }
        }

    @Test
    fun `coverage list matches every Irreversible declaration in tools source`() {
        val toolsDir = locateToolsSourceDir()
        val declared = mutableMapOf<String, String>() // file -> tool name
        toolsDir.listFiles { f -> f.extension == "kt" }?.forEach { file ->
            val src = file.readText()
            if (src.contains("SideEffect.Irreversible")) {
                val name = NAME_REGEX.find(src)?.groupValues?.get(1)
                if (name == null) {
                    fail("${file.name} declares SideEffect.Irreversible but no `override val name = \"...\"` found")
                } else {
                    declared[file.name] = name
                }
            }
        }
        val sourceSet = declared.values.toSet()
        if (sourceSet != irreversibleToolNames) {
            val missing = sourceSet - irreversibleToolNames
            val extra = irreversibleToolNames - sourceSet
            fail(
                buildString {
                    append("GateCoverageTest.irreversibleToolNames is out of sync with the tools sources.\n")
                    if (missing.isNotEmpty()) {
                        append("  Missing from the test set (declared in source, not covered): $missing\n")
                        append("  → add to irreversibleToolNames; gate-fires assertions will run for it automatically.\n")
                    }
                    if (extra.isNotEmpty()) {
                        append("  Extra in the test set (covered but no source declaration): $extra\n")
                        append("  → remove from irreversibleToolNames or restore the declaration in source.\n")
                    }
                },
            )
        }
    }

    private fun runtimeFor(toolName: String, backend: AutomationBackend): AgentRuntime =
        AgentRuntime(
            brain = null,
            parser =
                object : IntentParser() {
                    override fun route(input: String): ToolCall? = ToolCall(toolName, emptyMap())
                },
            sideEffectOf = { SideEffect.Irreversible },
            backends = listOf(backend),
            context = TurnOnlyContextStore { 0L },
            audit = AuditLog(),
        )

    private class RecordingBackend : AutomationBackend {
        override val tier = AutomationTier.ManagerApi
        val dispatches = mutableListOf<AutomationAction.ToolDispatch>()

        override fun supports(action: AutomationAction): Boolean = true

        override suspend fun execute(action: AutomationAction): BackendResult {
            if (action is AutomationAction.ToolDispatch) dispatches += action
            return BackendResult.Success("ok")
        }
    }

    private fun locateToolsSourceDir(): File {
        // Gradle unit tests run with the module dir as cwd, but allow a couple of fallbacks so
        // this works from `:app:test` (module root) and from running tests in an IDE (project root).
        val candidates =
            listOf(
                File("src/main/kotlin/com/mitra/tools"),
                File("app/src/main/kotlin/com/mitra/tools"),
                File("../app/src/main/kotlin/com/mitra/tools"),
            )
        return candidates.firstOrNull { it.isDirectory }
            ?: error("GateCoverageTest cannot find tools/ source dir from cwd=${File("").absolutePath}")
    }

    companion object {
        private val NAME_REGEX = Regex("override\\s+val\\s+name\\s*=\\s*\"([^\"]+)\"")
    }
}
