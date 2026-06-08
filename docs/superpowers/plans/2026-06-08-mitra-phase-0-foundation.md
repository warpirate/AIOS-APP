# Mitra Phase 0 — Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Land the five load-bearing seams (`Planner`, `AgentRuntime`, `InvocationSource`, `AutomationBackend`, `ContextStore`) and the `EvalHarness` so every later phase has somewhere to plug in. No user-visible behavior change. All 13 existing tools keep working through the refactor.

**Architecture:** Replace today's `AgentLoop` (text → router → tool → result) with `AgentRuntime` — a state machine that owns turn lifecycle and consumes a `Plan` produced by a `Planner` (V1 impl = `SingleShotPlanner` wrapping `LiteRtBrain` with `IntentParser` fallback). Tool dispatch goes through an `AutomationBackend` (V1 impl = `ManagerApiBackend`). Turn state lives in a `ContextStore` (V1 impl = `TurnOnlyContextStore`, no cross-turn memory). `EvalHarness` runs in JUnit (fixture mode, no device) against an `IntentParserPlanner` to gate planner/dispatch regressions in CI.

**Tech Stack:** Kotlin, Jetpack Compose, JUnit 4, kotlinx-coroutines, SnakeYAML 2.3 (test-only), LiteRT-LM 0.13.0 (already in deps).

**Spec:** `docs/superpowers/specs/2026-06-08-mitra-autonomous-interaction-design.md`

---

## File Structure

### New files (production)
- `app/src/main/kotlin/com/mitra/agent/Plan.kt` — `Plan` + `PlannedStep` data classes
- `app/src/main/kotlin/com/mitra/agent/Planner.kt` — `Planner` interface
- `app/src/main/kotlin/com/mitra/agent/SingleShotPlanner.kt` — wraps `LiteRtBrain` + `IntentParser` fallback
- `app/src/main/kotlin/com/mitra/agent/IntentParserPlanner.kt` — Planner backed only by `IntentParser` (used by eval + as a brain-less fallback)
- `app/src/main/kotlin/com/mitra/agent/AgentRuntime.kt` — state machine, owns turn lifecycle
- `app/src/main/kotlin/com/mitra/agent/RuntimeEvent.kt` — sealed event hierarchy
- `app/src/main/kotlin/com/mitra/agent/InvocationSource.kt` — interface + `UserUtterance` + `ScreenOrigin`
- `app/src/main/kotlin/com/mitra/agent/ContextStore.kt` — interface + `TurnContext` + `TurnOnlyContextStore`
- `app/src/main/kotlin/com/mitra/automation/AutomationBackend.kt` — interface + `AutomationAction` + `AutomationTier` + `BackendResult`
- `app/src/main/kotlin/com/mitra/automation/ManagerApiBackend.kt` — dispatches `ToolDispatch` via `ToolRegistry`

### Modified files (production)
- `app/src/main/kotlin/com/mitra/tools/Tool.kt` — add `val tier: AutomationTier` (default `ManagerApi`)
- `app/src/main/kotlin/com/mitra/MainActivity.kt` — wire `AgentRuntime` instead of `AgentLoop`
- `app/src/main/kotlin/com/mitra/ui/ChatScreen.kt` — consume `Flow<RuntimeEvent>` from `AgentRuntime`
- `app/build.gradle.kts` — add `testImplementation("org.yaml:snakeyaml:2.3")`

### Deleted files (production)
- `app/src/main/kotlin/com/mitra/agent/AgentLoop.kt` — fully replaced by `AgentRuntime` + `Planner`

### New files (test)
- `app/src/test/kotlin/com/mitra/agent/PlanTest.kt`
- `app/src/test/kotlin/com/mitra/agent/AgentRuntimeTest.kt`
- `app/src/test/kotlin/com/mitra/agent/SingleShotPlannerTest.kt`
- `app/src/test/kotlin/com/mitra/agent/IntentParserPlannerTest.kt`
- `app/src/test/kotlin/com/mitra/agent/TurnOnlyContextStoreTest.kt`
- `app/src/test/kotlin/com/mitra/automation/AutomationBackendTest.kt`
- `app/src/test/kotlin/com/mitra/automation/ManagerApiBackendTest.kt`
- `app/src/test/kotlin/com/mitra/eval/EvalCommand.kt`
- `app/src/test/kotlin/com/mitra/eval/EvalLoader.kt`
- `app/src/test/kotlin/com/mitra/eval/EvalLoaderTest.kt`
- `app/src/test/kotlin/com/mitra/eval/PlannerEvalRunner.kt`
- `app/src/test/kotlin/com/mitra/eval/PlannerEvalRunnerTest.kt`
- `app/src/test/kotlin/com/mitra/eval/EvalSmokeTest.kt`
- `app/src/test/resources/eval/commands.yaml` — 50-case starter set

### Modified files (test)
- (none deleted; `IntentParserTest.kt` keeps running unchanged)

### Spec deviation note
Spec §4.4 says "every `Tool` declares the action+tier it needs." Phase 0 ships **tier only** on `Tool`; `AutomationAction` is constructed by the dispatcher from `(tool.name, step.args)`. Per-tool `AutomationAction` templates land in Phase 4 when non-ManagerApi tiers exist. Spec §5 says EvalHarness lives at `mitra/training/eval/`; Phase 0 places it under `app/src/test/...` so the existing CI `:app:testDebugUnitTest` step picks it up without a new gradle module. Both deviations are pragmatic Phase 0 shortcuts and can move later without behavior change.

---

## Task 1: `Plan` + `PlannedStep` types

**Files:**
- Create: `app/src/main/kotlin/com/mitra/agent/Plan.kt`
- Test: `app/src/test/kotlin/com/mitra/agent/PlanTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
// app/src/test/kotlin/com/mitra/agent/PlanTest.kt
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
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests com.mitra.agent.PlanTest`
Expected: FAIL — `Unresolved reference: Plan` / `PlannedStep`.

- [ ] **Step 3: Write minimal implementation**

```kotlin
// app/src/main/kotlin/com/mitra/agent/Plan.kt
package com.mitra.agent

import com.mitra.tools.SideEffect

/** What the [Planner] returns: an ordered list of tool calls to execute, plus model self-report fields. */
data class Plan(
    val steps: List<PlannedStep>,
    val rationale: String?,
    val confidence: Float,
)

data class PlannedStep(
    val toolName: String,
    val args: Map<String, Any?>,
    val sideEffect: SideEffect,
    val dependsOn: List<Int> = emptyList(),
)
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests com.mitra.agent.PlanTest`
Expected: PASS, 3 tests.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/mitra/agent/Plan.kt app/src/test/kotlin/com/mitra/agent/PlanTest.kt
git commit -m "feat(agent): Plan + PlannedStep types (seam)"
```

---

## Task 2: `Planner` interface

**Files:**
- Create: `app/src/main/kotlin/com/mitra/agent/Planner.kt`

- [ ] **Step 1: Write the interface**

No test for an empty interface; the impls in later tasks test it.

```kotlin
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
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL (will fail on `TurnContext` — fine; Task 6 creates it).

If it fails on `TurnContext`, leave it failing for now — Task 6 fixes it. Do not commit yet; Task 6 closes the loop.

- [ ] **Step 3: Hold commit until Task 6**

(Combined commit in Task 6 covers Planner + ContextStore together so the tree never has a non-compiling state on a single commit.)

---

## Task 3: `AutomationBackend` + `AutomationAction` + `AutomationTier`

**Files:**
- Create: `app/src/main/kotlin/com/mitra/automation/AutomationBackend.kt`
- Test: `app/src/test/kotlin/com/mitra/automation/AutomationBackendTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
// app/src/test/kotlin/com/mitra/automation/AutomationBackendTest.kt
package com.mitra.automation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomationBackendTest {
    @Test
    fun `tier ordering is ManagerApi RemoteInput Deeplink A11yGesture`() {
        // ordinal order matters — dispatcher picks highest tier first
        assertEquals(0, AutomationTier.ManagerApi.ordinal)
        assertEquals(1, AutomationTier.RemoteInput.ordinal)
        assertEquals(2, AutomationTier.Deeplink.ordinal)
        assertEquals(3, AutomationTier.A11yGesture.ordinal)
    }

    @Test
    fun `tool dispatch action carries name and args`() {
        val action = AutomationAction.ToolDispatch("toggle_flashlight", mapOf("on" to true))
        assertEquals("toggle_flashlight", action.name)
        assertEquals(true, action.args["on"])
    }

    @Test
    fun `backend result success and failure are distinguishable`() {
        val ok: BackendResult = BackendResult.Success("done")
        val err: BackendResult = BackendResult.Failure("nope")
        assertTrue(ok is BackendResult.Success)
        assertTrue(err is BackendResult.Failure)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests com.mitra.automation.AutomationBackendTest`
Expected: FAIL — unresolved references.

- [ ] **Step 3: Write minimal implementation**

```kotlin
// app/src/main/kotlin/com/mitra/automation/AutomationBackend.kt
package com.mitra.automation

/**
 * Tiers in priority order: dispatcher picks the lowest ordinal that supports the action.
 * ManagerApi = direct Android Manager (Camera/Bluetooth/AudioManager). RemoteInput = notification
 * inline reply (no UI). Deeplink = intent / ACTION_SEND. A11yGesture = AccessibilityService text
 * injection or gesture (last resort, slow + brittle).
 */
enum class AutomationTier { ManagerApi, RemoteInput, Deeplink, A11yGesture }

/** What a backend is asked to do. Phase 0 has one shape; later tiers add new sealed cases. */
sealed interface AutomationAction {
    data class ToolDispatch(val name: String, val args: Map<String, Any?>) : AutomationAction
    // Future:
    // data class ReplyToNotification(val pkg: String, val text: String) : AutomationAction
    // data class OpenDeeplink(val uri: String) : AutomationAction
    // data class A11yGesture(...) : AutomationAction
}

sealed interface BackendResult {
    data class Success(val message: String) : BackendResult
    data class Failure(val message: String) : BackendResult
}

interface AutomationBackend {
    val tier: AutomationTier
    fun supports(action: AutomationAction): Boolean
    suspend fun execute(action: AutomationAction): BackendResult
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests com.mitra.automation.AutomationBackendTest`
Expected: PASS, 3 tests.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/mitra/automation/AutomationBackend.kt app/src/test/kotlin/com/mitra/automation/AutomationBackendTest.kt
git commit -m "feat(automation): AutomationBackend interface + tier enum (seam)"
```

---

## Task 4: Add `tier` to `Tool` interface

**Files:**
- Modify: `app/src/main/kotlin/com/mitra/tools/Tool.kt`

- [ ] **Step 1: Add tier field with default**

```kotlin
// app/src/main/kotlin/com/mitra/tools/Tool.kt
package com.mitra.tools

import com.mitra.automation.AutomationTier

/** How risky a tool's action is. Anything but [None] is gated by [com.mitra.safety.ConfirmationGate]. */
enum class SideEffect { None, Reversible, Irreversible }

sealed interface ToolResult {
    data class Success(val message: String) : ToolResult
    data class Failure(val message: String) : ToolResult
}

/** One device action. One tool per file. */
interface Tool {
    val name: String
    val sideEffect: SideEffect

    /** Lowest-cost backend tier that can execute this tool. Defaults to ManagerApi — all V1 tools
     *  go through ManagerApiBackend. Later tools (e.g. WhatsApp reply) override to RemoteInput. */
    val tier: AutomationTier get() = AutomationTier.ManagerApi

    fun execute(args: Map<String, Any?>): ToolResult
}

// The model emits args as JSON; values may arrive as Number, String, or Boolean. Coerce defensively.
fun argInt(value: Any?): Int? = when (value) {
    is Number -> value.toInt()
    is String -> value.trim().toDoubleOrNull()?.toInt()
    else -> null
}

fun argString(value: Any?): String? = (value as? String)?.trim()?.ifBlank { null }

fun argBool(value: Any?): Boolean? = when (value) {
    is Boolean -> value
    is String -> value.trim().lowercase().toBooleanStrictOrNull()
    else -> null
}
```

- [ ] **Step 2: Verify all existing tools still compile**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL. All 13 tool files inherit the default `tier = ManagerApi` — no per-file change needed.

- [ ] **Step 3: Verify existing tests still pass**

Run: `./gradlew :app:testDebugUnitTest`
Expected: PASS, no new failures.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/kotlin/com/mitra/tools/Tool.kt
git commit -m "feat(tools): Tool.tier with ManagerApi default (seam, no behavior change)"
```

---

## Task 5: `ManagerApiBackend`

**Files:**
- Create: `app/src/main/kotlin/com/mitra/automation/ManagerApiBackend.kt`
- Test: `app/src/test/kotlin/com/mitra/automation/ManagerApiBackendTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
// app/src/test/kotlin/com/mitra/automation/ManagerApiBackendTest.kt
package com.mitra.automation

import com.mitra.tools.SideEffect
import com.mitra.tools.Tool
import com.mitra.tools.ToolResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeTool(
    override val name: String,
    private val result: ToolResult,
) : Tool {
    override val sideEffect = SideEffect.None
    var lastArgs: Map<String, Any?>? = null
    override fun execute(args: Map<String, Any?>): ToolResult {
        lastArgs = args
        return result
    }
}

class ManagerApiBackendTest {
    @Test
    fun `supports tool dispatch only`() {
        val backend = ManagerApiBackend(toolsByName = emptyMap())
        assertTrue(backend.supports(AutomationAction.ToolDispatch("foo", emptyMap())))
    }

    @Test
    fun `execute dispatches to the tool by name and forwards args`() = runBlocking {
        val tool = FakeTool("toggle_flashlight", ToolResult.Success("on"))
        val backend = ManagerApiBackend(mapOf(tool.name to tool))
        val r = backend.execute(AutomationAction.ToolDispatch("toggle_flashlight", mapOf("on" to true)))
        assertTrue(r is BackendResult.Success)
        assertEquals("on", (r as BackendResult.Success).message)
        assertEquals(true, tool.lastArgs?.get("on"))
    }

    @Test
    fun `unknown tool returns failure`() = runBlocking {
        val backend = ManagerApiBackend(toolsByName = emptyMap())
        val r = backend.execute(AutomationAction.ToolDispatch("nope", emptyMap()))
        assertTrue(r is BackendResult.Failure)
    }

    @Test
    fun `tool failure surfaces as backend failure`() = runBlocking {
        val tool = FakeTool("x", ToolResult.Failure("boom"))
        val backend = ManagerApiBackend(mapOf("x" to tool))
        val r = backend.execute(AutomationAction.ToolDispatch("x", emptyMap()))
        assertTrue(r is BackendResult.Failure)
        assertEquals("boom", (r as BackendResult.Failure).message)
    }

    @Test
    fun `tier is ManagerApi`() {
        assertEquals(AutomationTier.ManagerApi, ManagerApiBackend(emptyMap()).tier)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests com.mitra.automation.ManagerApiBackendTest`
Expected: FAIL — `ManagerApiBackend` unresolved.

- [ ] **Step 3: Write minimal implementation**

```kotlin
// app/src/main/kotlin/com/mitra/automation/ManagerApiBackend.kt
package com.mitra.automation

import com.mitra.tools.Tool
import com.mitra.tools.ToolResult

/**
 * Default V1 backend: dispatches a [AutomationAction.ToolDispatch] to the matching [Tool] by name
 * via direct Android Manager-API calls (whatever the tool implementation does inside execute).
 * No notification listener, no a11y, no intent — those are higher-tier backends added later.
 */
class ManagerApiBackend(private val toolsByName: Map<String, Tool>) : AutomationBackend {
    override val tier = AutomationTier.ManagerApi

    override fun supports(action: AutomationAction): Boolean = action is AutomationAction.ToolDispatch

    override suspend fun execute(action: AutomationAction): BackendResult {
        val td = action as? AutomationAction.ToolDispatch
            ?: return BackendResult.Failure("ManagerApiBackend cannot execute ${action::class.simpleName}")
        val tool = toolsByName[td.name]
            ?: return BackendResult.Failure("no tool registered for ${td.name}")
        return when (val r = tool.execute(td.args)) {
            is ToolResult.Success -> BackendResult.Success(r.message)
            is ToolResult.Failure -> BackendResult.Failure(r.message)
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests com.mitra.automation.ManagerApiBackendTest`
Expected: PASS, 5 tests.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/mitra/automation/ManagerApiBackend.kt app/src/test/kotlin/com/mitra/automation/ManagerApiBackendTest.kt
git commit -m "feat(automation): ManagerApiBackend dispatches ToolDispatch via ToolRegistry"
```

---

## Task 6: `ContextStore` + `TurnContext` + `TurnOnlyContextStore`

**Files:**
- Create: `app/src/main/kotlin/com/mitra/agent/ContextStore.kt`
- Test: `app/src/test/kotlin/com/mitra/agent/TurnOnlyContextStoreTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
// app/src/test/kotlin/com/mitra/agent/TurnOnlyContextStoreTest.kt
package com.mitra.agent

import com.mitra.tools.ToolResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TurnOnlyContextStoreTest {
    @Test
    fun `turn is empty before beginTurn`() {
        val s = TurnOnlyContextStore()
        assertNull(s.turn())
    }

    @Test
    fun `beginTurn exposes the utterance via turn`() = runBlocking {
        val s = TurnOnlyContextStore()
        val u = UserUtterance(text = "open whatsapp", source = "qs-tile")
        s.beginTurn(u)
        assertEquals("open whatsapp", s.turn()?.utterance?.text)
    }

    @Test
    fun `endTurn clears the turn context`() = runBlocking {
        val s = TurnOnlyContextStore()
        s.beginTurn(UserUtterance("hi", "qs-tile"))
        s.endTurn()
        assertNull(s.turn())
    }

    @Test
    fun `lastToolResult is null at turn start`() = runBlocking {
        val s = TurnOnlyContextStore()
        s.beginTurn(UserUtterance("hi", "qs-tile"))
        assertNull(s.turn()?.lastToolResult)
    }

    @Test
    fun `recordToolResult attaches to current turn`() = runBlocking {
        val s = TurnOnlyContextStore()
        s.beginTurn(UserUtterance("hi", "qs-tile"))
        s.recordToolResult(ToolResult.Success("ok"))
        val r = s.turn()?.lastToolResult
        assertEquals("ok", (r as? ToolResult.Success)?.message)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests com.mitra.agent.TurnOnlyContextStoreTest`
Expected: FAIL — unresolved references.

- [ ] **Step 3: Write the production code (interface + impl + UserUtterance + ScreenOrigin)**

This single commit also closes the loop on Task 2's `Planner` interface (which referenced `TurnContext`).

```kotlin
// app/src/main/kotlin/com/mitra/agent/InvocationSource.kt
package com.mitra.agent

import kotlinx.coroutines.flow.Flow

enum class ScreenOrigin { Foreground, Lockscreen, Background }

/** A single text utterance from a user-facing source (tile / assistant role / power key / wake word). */
data class UserUtterance(
    val text: String,
    val source: String,
    val origin: ScreenOrigin = ScreenOrigin.Foreground,
)

/**
 * Phase 1 will land impls (QuickSettingsTile, AssistantRole, PowerKey). Phase 0 ships the
 * interface only so AgentRuntime callers can be typed against it without behavior change.
 */
interface InvocationSource {
    val id: String
    fun events(): Flow<UserUtterance>
}
```

```kotlin
// app/src/main/kotlin/com/mitra/agent/ContextStore.kt
package com.mitra.agent

import com.mitra.tools.ToolResult

/** Per-turn read-only snapshot exposed to [Planner.plan]. */
data class TurnContext(
    val utterance: UserUtterance,
    val startedAt: Long,
    val lastToolResult: ToolResult?,
)

/**
 * Where AgentRuntime parks turn state. V1 impl is TurnOnlyContextStore — in-memory, cleared on
 * endTurn(). Session/Long-scope impls land in later phases without consumer changes.
 */
interface ContextStore {
    fun turn(): TurnContext?
    suspend fun beginTurn(utterance: UserUtterance)
    suspend fun recordToolResult(result: ToolResult)
    suspend fun endTurn()
}

class TurnOnlyContextStore(private val clockMs: () -> Long = { System.currentTimeMillis() }) : ContextStore {
    private var current: TurnContext? = null

    override fun turn(): TurnContext? = current

    override suspend fun beginTurn(utterance: UserUtterance) {
        current = TurnContext(utterance = utterance, startedAt = clockMs(), lastToolResult = null)
    }

    override suspend fun recordToolResult(result: ToolResult) {
        current = current?.copy(lastToolResult = result)
    }

    override suspend fun endTurn() {
        current = null
    }
}
```

- [ ] **Step 4: Run test to verify everything compiles and passes**

Run: `./gradlew :app:testDebugUnitTest --tests com.mitra.agent.TurnOnlyContextStoreTest`
Expected: PASS, 5 tests.

Then full build:

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL — `Planner` interface from Task 2 now compiles too (TurnContext exists).

- [ ] **Step 5: Commit (covers Planner from Task 2 + ContextStore + InvocationSource)**

```bash
git add app/src/main/kotlin/com/mitra/agent/Planner.kt \
        app/src/main/kotlin/com/mitra/agent/ContextStore.kt \
        app/src/main/kotlin/com/mitra/agent/InvocationSource.kt \
        app/src/test/kotlin/com/mitra/agent/TurnOnlyContextStoreTest.kt
git commit -m "feat(agent): Planner + ContextStore + InvocationSource seams"
```

---

## Task 7: `RuntimeEvent` sealed hierarchy

**Files:**
- Create: `app/src/main/kotlin/com/mitra/agent/RuntimeEvent.kt`

- [ ] **Step 1: Write the file (no test — pure data)**

```kotlin
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
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/kotlin/com/mitra/agent/RuntimeEvent.kt
git commit -m "feat(agent): RuntimeEvent sealed hierarchy + GateDecision"
```

---

## Task 8: `IntentParserPlanner`

**Files:**
- Create: `app/src/main/kotlin/com/mitra/agent/IntentParserPlanner.kt`
- Test: `app/src/test/kotlin/com/mitra/agent/IntentParserPlannerTest.kt`

This planner is used by `EvalSmokeTest` to run on CI without a model, and also as a brain-less fallback when `LiteRtBrain` fails to load.

- [ ] **Step 1: Write the failing test**

```kotlin
// app/src/test/kotlin/com/mitra/agent/IntentParserPlannerTest.kt
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
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests com.mitra.agent.IntentParserPlannerTest`
Expected: FAIL — `IntentParserPlanner` unresolved.

- [ ] **Step 3: Write the implementation**

```kotlin
// app/src/main/kotlin/com/mitra/agent/IntentParserPlanner.kt
package com.mitra.agent

import com.mitra.tools.SideEffect

/**
 * Planner that uses only the deterministic [IntentParser] — no LLM. Two consumers:
 *  - [com.mitra.eval.EvalSmokeTest] uses it to gate planner+dispatch regressions in CI
 *    without needing a model file or device.
 *  - AgentRuntime can fall back to it when [LiteRtBrain] fails to load (brain-less mode).
 */
class IntentParserPlanner(
    private val parser: IntentParser,
    private val sideEffectOf: (String) -> SideEffect,
) : Planner {
    override suspend fun plan(utterance: String, ctx: TurnContext): Plan {
        val call = parser.route(utterance) ?: return Plan(steps = emptyList(), rationale = null, confidence = 1.0f)
        val step = PlannedStep(toolName = call.name, args = call.args, sideEffect = sideEffectOf(call.name))
        return Plan(steps = listOf(step), rationale = null, confidence = 1.0f)
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests com.mitra.agent.IntentParserPlannerTest`
Expected: PASS, 3 tests.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/mitra/agent/IntentParserPlanner.kt app/src/test/kotlin/com/mitra/agent/IntentParserPlannerTest.kt
git commit -m "feat(agent): IntentParserPlanner — deterministic Planner impl for eval + fallback"
```

---

## Task 9: `SingleShotPlanner`

**Files:**
- Create: `app/src/main/kotlin/com/mitra/agent/SingleShotPlanner.kt`
- Test: `app/src/test/kotlin/com/mitra/agent/SingleShotPlannerTest.kt`

Wraps `LiteRtBrain.chatStream` + falls back to `IntentParser` when the brain emits no tool call. Streams chat text via an `onChunk` callback so `AgentRuntime` can emit `RuntimeEvent.Speaking`.

- [ ] **Step 1: Write the failing test**

```kotlin
// app/src/test/kotlin/com/mitra/agent/SingleShotPlannerTest.kt
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
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests com.mitra.agent.SingleShotPlannerTest`
Expected: FAIL — `SingleShotPlanner` unresolved.

- [ ] **Step 3: Write the implementation**

```kotlin
// app/src/main/kotlin/com/mitra/agent/SingleShotPlanner.kt
package com.mitra.agent

import com.mitra.inference.BrainTurn
import com.mitra.tools.SideEffect
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect

/**
 * V1 [Planner] impl: runs the brain once, takes whatever tool call (if any) it emitted, or falls
 * back to [IntentParser] if the brain only chatted. `onChunk` receives streaming chat text as it
 * arrives so [AgentRuntime] can emit [RuntimeEvent.Speaking].
 *
 * Confidence is fixed at 1.0 in V1 — there is no data yet to calibrate against. Phase 2's
 * PlanThenExecutePlanner is where confidence becomes meaningful.
 */
class SingleShotPlanner(
    private val brainStream: (String) -> Flow<BrainTurn>,
    private val parser: IntentParser,
    private val sideEffectOf: (String) -> SideEffect,
    private val onChunk: (String) -> Unit,
) : Planner {
    override suspend fun plan(utterance: String, ctx: TurnContext): Plan {
        var lastText = ""
        var lastCall: ToolCall? = null
        brainStream(utterance).collect { turn ->
            if (turn.text.isNotEmpty()) {
                lastText = turn.text
                onChunk(turn.text)
            }
            turn.toolCall?.let { lastCall = it }
        }
        val call = lastCall ?: parser.route(utterance)
        if (call == null) {
            return Plan(steps = emptyList(), rationale = lastText.ifBlank { null }, confidence = 1.0f)
        }
        val step = PlannedStep(toolName = call.name, args = call.args, sideEffect = sideEffectOf(call.name))
        return Plan(steps = listOf(step), rationale = lastText.ifBlank { null }, confidence = 1.0f)
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests com.mitra.agent.SingleShotPlannerTest`
Expected: PASS, 4 tests.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/mitra/agent/SingleShotPlanner.kt app/src/test/kotlin/com/mitra/agent/SingleShotPlannerTest.kt
git commit -m "feat(agent): SingleShotPlanner wraps brain + parser fallback (Planner impl)"
```

---

## Task 10: `AgentRuntime` state machine

**Files:**
- Create: `app/src/main/kotlin/com/mitra/agent/AgentRuntime.kt`
- Test: `app/src/test/kotlin/com/mitra/agent/AgentRuntimeTest.kt`

State machine that owns turn lifecycle. Calls planner, emits `Flow<RuntimeEvent>`, dispatches each step through `AutomationBackend`, consults `ConfirmationGate` for non-None steps, writes to `AuditLog`.

- [ ] **Step 1: Write the failing test**

```kotlin
// app/src/test/kotlin/com/mitra/agent/AgentRuntimeTest.kt
package com.mitra.agent

import com.mitra.automation.AutomationAction
import com.mitra.automation.AutomationBackend
import com.mitra.automation.AutomationTier
import com.mitra.automation.BackendResult
import com.mitra.safety.AuditLog
import com.mitra.tools.SideEffect
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private class StubPlanner(private val plan: Plan) : Planner {
    override suspend fun plan(utterance: String, ctx: TurnContext): Plan = plan
}

private class StubBackend(private val result: BackendResult = BackendResult.Success("ok")) : AutomationBackend {
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
    fun `empty plan emits PlanReady then Done`() = runBlocking {
        val rt = runtimeWith(Plan(emptyList(), null, 1.0f))
        val events = rt.run(UserUtterance("hi", "test")).toList()
        assertTrue(events.any { it is RuntimeEvent.PlanReady })
        assertTrue(events.last() is RuntimeEvent.Done)
    }

    @Test
    fun `single None-side-effect step runs without gate`() = runBlocking {
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
    fun `Irreversible step pauses on GateRequested and runs after Approve`() = runBlocking {
        val plan = Plan(listOf(PlannedStep("send_sms", mapOf("to" to "x", "body" to "y"), SideEffect.Irreversible)), null, 1.0f)
        val backend = StubBackend()
        val rt = runtimeWith(plan, backend)
        val collected = mutableListOf<RuntimeEvent>()
        val job = launch {
            rt.run(UserUtterance("send sms", "test")).collect { collected += it }
        }
        // Wait for the gate event to appear.
        while (collected.none { it is RuntimeEvent.GateRequested }) { /* spin briefly */ }
        assertEquals(0, backend.dispatches.size)
        rt.resume(GateDecision.Approve)
        job.join()
        assertEquals(1, backend.dispatches.size)
        assertTrue(collected.last() is RuntimeEvent.Done)
    }

    @Test
    fun `Irreversible step cancelled by user produces Failed terminal`() = runBlocking {
        val plan = Plan(listOf(PlannedStep("send_sms", mapOf("to" to "x"), SideEffect.Irreversible)), null, 1.0f)
        val backend = StubBackend()
        val rt = runtimeWith(plan, backend)
        val collected = mutableListOf<RuntimeEvent>()
        val job = launch {
            rt.run(UserUtterance("send sms", "test")).collect { collected += it }
        }
        while (collected.none { it is RuntimeEvent.GateRequested }) { }
        rt.resume(GateDecision.Cancel)
        job.join()
        assertEquals(0, backend.dispatches.size)
        assertTrue(collected.last() is RuntimeEvent.Failed)
    }

    @Test
    fun `backend failure makes runtime emit Failed`() = runBlocking {
        val plan = Plan(listOf(PlannedStep("open_url", mapOf("url" to "x"), SideEffect.None)), null, 1.0f)
        val backend = StubBackend(BackendResult.Failure("boom"))
        val rt = runtimeWith(plan, backend)
        val events = rt.run(UserUtterance("open x", "test")).toList()
        assertTrue(events.last() is RuntimeEvent.Failed)
    }

    @Test
    fun `no backend supports the action - Failed`() = runBlocking {
        val plan = Plan(listOf(PlannedStep("nope", emptyMap(), SideEffect.None)), null, 1.0f)
        val rt = AgentRuntime(
            planner = StubPlanner(plan),
            backends = emptyList(),
            context = TurnOnlyContextStore { 0L },
            audit = AuditLog(),
        )
        val events = rt.run(UserUtterance("x", "test")).toList()
        assertTrue(events.last() is RuntimeEvent.Failed)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests com.mitra.agent.AgentRuntimeTest`
Expected: FAIL — `AgentRuntime` unresolved.

- [ ] **Step 3: Write the implementation**

```kotlin
// app/src/main/kotlin/com/mitra/agent/AgentRuntime.kt
package com.mitra.agent

import com.mitra.automation.AutomationAction
import com.mitra.automation.AutomationBackend
import com.mitra.automation.BackendResult
import com.mitra.safety.AuditLog
import com.mitra.safety.ConfirmationGate
import com.mitra.tools.SideEffect
import com.mitra.tools.ToolResult
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * State machine: utterance -> plan -> dispatch each step (gating Irreversible) -> Done/Failed.
 * Replaces the old AgentLoop. Owns the turn lifecycle (no caller touches ContextStore).
 *
 * V1 keeps it deliberately small: no retry-on-failure, no replan-from-runtime (Planner can replan
 * itself but the runtime won't re-call it for a single-shot failure). Those land with
 * [com.mitra.agent.SingleShotPlanner]'s Phase 2 successor.
 */
class AgentRuntime(
    private val planner: Planner,
    private val backends: List<AutomationBackend>,
    private val context: ContextStore,
    private val audit: AuditLog,
) {
    private val gateChannel = Channel<GateDecision>(capacity = Channel.RENDEZVOUS)

    fun run(utterance: UserUtterance): Flow<RuntimeEvent> = flow {
        context.beginTurn(utterance)
        try {
            val ctx = context.turn() ?: error("turn missing after beginTurn")
            val plan = planner.plan(utterance.text, ctx)
            emit(RuntimeEvent.PlanReady(plan))

            if (plan.steps.isEmpty()) {
                emit(RuntimeEvent.Done(summary = plan.rationale ?: "nothing to do"))
                return@flow
            }

            for ((index, step) in plan.steps.withIndex()) {
                if (ConfirmationGate.requiresConfirm(step.sideEffect) && step.sideEffect == SideEffect.Irreversible) {
                    emit(RuntimeEvent.GateRequested(index, step))
                    val decision = gateChannel.receive()
                    if (decision == GateDecision.Cancel) {
                        audit.record(step.toolName, step.sideEffect, ok = false)
                        emit(RuntimeEvent.Failed(reason = "cancelled by user"))
                        return@flow
                    }
                }

                emit(RuntimeEvent.StepStarted(index, step))
                val action = AutomationAction.ToolDispatch(step.toolName, step.args)
                val backend = backends.firstOrNull { it.supports(action) }
                val result: BackendResult = backend?.execute(action)
                    ?: BackendResult.Failure("no backend supports ${step.toolName}")

                audit.record(step.toolName, step.sideEffect, ok = result is BackendResult.Success)
                context.recordToolResult(
                    when (result) {
                        is BackendResult.Success -> ToolResult.Success(result.message)
                        is BackendResult.Failure -> ToolResult.Failure(result.message)
                    },
                )
                emit(RuntimeEvent.StepCompleted(index, step, result))

                if (result is BackendResult.Failure) {
                    emit(RuntimeEvent.Failed(reason = result.message))
                    return@flow
                }
            }

            emit(RuntimeEvent.Done(summary = "done"))
        } finally {
            context.endTurn()
        }
    }

    /** Called by UI in response to [RuntimeEvent.GateRequested]. */
    suspend fun resume(decision: GateDecision) {
        gateChannel.send(decision)
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests com.mitra.agent.AgentRuntimeTest`
Expected: PASS, 6 tests.

(Note: the two tests that use `while (collected.none { ... }) { }` to wait for a gate event poll a `mutableListOf` from a `launch`-ed coroutine — if they flake on slow runners, replace with a `CompletableDeferred<RuntimeEvent>` triggered inside `.collect { }`. Keep the simple form first and only switch if flake appears.)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/mitra/agent/AgentRuntime.kt app/src/test/kotlin/com/mitra/agent/AgentRuntimeTest.kt
git commit -m "feat(agent): AgentRuntime state machine (replaces AgentLoop)"
```

---

## Task 11: Wire `MainActivity` to use `AgentRuntime`

**Files:**
- Modify: `app/src/main/kotlin/com/mitra/MainActivity.kt`

- [ ] **Step 1: Replace AgentLoop construction with AgentRuntime + supporting seam wiring**

```kotlin
// app/src/main/kotlin/com/mitra/MainActivity.kt — replace the whole onCreate body up to setContent
package com.mitra

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.mitra.agent.AgentRuntime
import com.mitra.agent.IntentParser
import com.mitra.agent.IntentParserPlanner
import com.mitra.agent.SingleShotPlanner
import com.mitra.agent.TurnOnlyContextStore
import com.mitra.automation.ManagerApiBackend
import com.mitra.inference.LiteRtBrain
import com.mitra.inference.ModelDownloader
import com.mitra.inference.ModelRegistry
import com.mitra.permissions.Onboarding
import com.mitra.safety.AuditLog
import com.mitra.tools.ToolRegistry
import com.mitra.ui.ChatScreen
import com.mitra.ui.DownloadScreen
import com.mitra.ui.ErrorScreen
import com.mitra.ui.LoadingBrainScreen
import com.mitra.ui.PermissionsScreen
import com.mitra.ui.SettingsScreen
import com.mitra.ui.WelcomeScreen
import com.mitra.ui.theme.MitraTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private enum class Phase { BOOT, WELCOME, DOWNLOAD, LOADING, PERMISSIONS, CHAT, SETTINGS, ERROR }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val tools = ToolRegistry.all(applicationContext)
        val toolsByName = tools.associateBy { it.name }
        val sideEffectOf: (String) -> com.mitra.tools.SideEffect = { name ->
            toolsByName[name]?.sideEffect ?: com.mitra.tools.SideEffect.Reversible
        }
        val backend = ManagerApiBackend(toolsByName)
        val audit = AuditLog()
        val context = TurnOnlyContextStore()
        // The runtime is constructed below once the brain is (or isn't) loaded — see AppRoot.
        val modelFile = File(applicationContext.getExternalFilesDir(null), ModelRegistry.MODEL_FILE)
        val cacheDir = applicationContext.cacheDir.path
        setContent {
            MitraTheme {
                AppRoot(
                    modelFile = modelFile,
                    cacheDir = cacheDir,
                    sideEffectOf = sideEffectOf,
                    buildRuntime = { brain, onChunk ->
                        val parser = IntentParser()
                        val planner = if (brain != null) {
                            SingleShotPlanner(
                                brainStream = brain::chatStream,
                                parser = parser,
                                sideEffectOf = sideEffectOf,
                                onChunk = onChunk,
                            )
                        } else {
                            IntentParserPlanner(parser, sideEffectOf)
                        }
                        AgentRuntime(planner, listOf(backend), context, audit)
                    },
                )
            }
        }
    }
}

@Composable
private fun AppRoot(
    modelFile: File,
    cacheDir: String,
    sideEffectOf: (String) -> com.mitra.tools.SideEffect,
    buildRuntime: (LiteRtBrain?, (String) -> Unit) -> AgentRuntime,
) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var phase by remember { mutableStateOf(Phase.BOOT) }
    var brain by remember { mutableStateOf<LiteRtBrain?>(null) }

    var downloaded by remember { mutableLongStateOf(0L) }
    var total by remember { mutableLongStateOf(0L) }
    var paused by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        phase = if (ModelDownloader(modelFile).isComplete()) Phase.LOADING else Phase.WELCOME
    }

    LaunchedEffect(phase, paused) {
        if (phase == Phase.DOWNLOAD && !paused && !done) {
            try {
                ModelDownloader(modelFile).download(ModelRegistry.MODEL_URL) { p ->
                    downloaded = p.downloaded
                    total = p.total
                }
                done = true
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                errorMsg = t.message ?: "network error"
                phase = Phase.ERROR
            }
        }
    }

    LaunchedEffect(phase) {
        if (phase == Phase.LOADING) {
            brain = withContext(Dispatchers.IO) {
                try { LiteRtBrain(modelFile.absolutePath, cacheDir) } catch (t: Throwable) { null }
            }
            phase = if (Onboarding.isComplete(ctx)) Phase.CHAT else Phase.PERMISSIONS
        }
    }

    when (phase) {
        Phase.BOOT, Phase.LOADING -> LoadingBrainScreen()
        Phase.WELCOME -> WelcomeScreen(onStart = { phase = Phase.DOWNLOAD })
        Phase.DOWNLOAD -> DownloadScreen(
            downloaded = downloaded,
            total = total,
            paused = paused,
            done = done,
            onPauseResume = { paused = !paused },
            onContinue = { phase = Phase.LOADING },
        )
        Phase.PERMISSIONS -> PermissionsScreen(
            onContinue = {
                Onboarding.markComplete(ctx)
                phase = Phase.CHAT
            },
        )
        Phase.CHAT -> ChatScreen(
            brainReady = brain != null,
            buildRuntime = { onChunk -> buildRuntime(brain, onChunk) },
            onOpenSettings = { phase = Phase.SETTINGS },
        )
        Phase.SETTINGS -> SettingsScreen(onBack = { phase = Phase.CHAT })
        Phase.ERROR -> ErrorScreen(
            message = errorMsg,
            onRetry = {
                errorMsg = ""
                done = false
                paused = false
                phase = Phase.DOWNLOAD
            },
            onSkip = { phase = Phase.PERMISSIONS },
        )
    }
}
```

- [ ] **Step 2: Verify build (will fail on ChatScreen signature mismatch — that's Task 12)**

Run: `./gradlew :app:compileDebugKotlin`
Expected: FAIL — `ChatScreen(brainReady = ..., buildRuntime = ..., onOpenSettings = ...)` does not match current signature. Hold the commit until Task 12 lands the new ChatScreen.

- [ ] **Step 3: Hold commit — bundled in Task 12**

---

## Task 12: Migrate `ChatScreen` to consume `Flow<RuntimeEvent>`

**Files:**
- Modify: `app/src/main/kotlin/com/mitra/ui/ChatScreen.kt`

The whole `send()` function changes shape: instead of calling `brain.chatStream` then `agent.runCall`, it asks for a fresh `AgentRuntime` per send and consumes `runtime.run(utterance)` as a `Flow<RuntimeEvent>`. Confirmation cards become `resume(Approve/Cancel)` calls instead of `agent.runCall(...)`.

- [ ] **Step 1: Rewrite the `ChatScreen` composable signature + send/runCard/cancelCard helpers**

Apply this diff to `app/src/main/kotlin/com/mitra/ui/ChatScreen.kt`. The non-business helpers (`UserBubble`, `MitraReply`, `ActionCardView`, etc.) stay unchanged.

Replace the imports for `AgentLoop` and `LiteRtBrain`:

```kotlin
// Remove:
// import com.mitra.agent.AgentLoop
// import com.mitra.inference.LiteRtBrain

// Add:
import com.mitra.agent.AgentRuntime
import com.mitra.agent.GateDecision
import com.mitra.agent.RuntimeEvent
```

Replace the `ChatScreen` composable signature and its `send/runCard/cancelCard/addCard` helpers with:

```kotlin
@Composable
fun ChatScreen(
    brainReady: Boolean,
    buildRuntime: (onChunk: (String) -> Unit) -> AgentRuntime,
    onOpenSettings: () -> Unit = {},
) {
    val items = remember { mutableStateListOf<ChatItem>() }
    var input by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var nextId by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    // The runtime that is currently mid-turn (used to resume gate decisions). Null between turns.
    var activeRuntime by remember { mutableStateOf<AgentRuntime?>(null) }

    LaunchedEffect(items.size) {
        if (items.isNotEmpty()) listState.animateScrollToItem(items.size - 1)
    }

    fun cardIndex(id: Int) = items.indexOfFirst { it is ActionCard && it.id == id }

    fun finishCard(id: Int, success: Boolean, detail: String) {
        val i = cardIndex(id); if (i < 0) return
        val card = items[i] as ActionCard
        items[i] = card.copy(
            state = if (success) ActionState.DONE else ActionState.FAILED,
            detail = detail,
        )
    }

    fun runCard(id: Int) {
        // Approve gate: tell the active runtime to proceed.
        val i = cardIndex(id); if (i < 0) return
        val card = items[i] as ActionCard
        items[i] = card.copy(state = ActionState.RUNNING)
        scope.launch { activeRuntime?.resume(GateDecision.Approve) }
    }

    fun cancelCard(id: Int) {
        val i = cardIndex(id); if (i >= 0) items[i] = (items[i] as ActionCard).copy(state = ActionState.CANCELLED)
        scope.launch { activeRuntime?.resume(GateDecision.Cancel) }
    }

    fun send(textOverride: String? = null) {
        val text = (textOverride ?: input).trim()
        if (text.isEmpty() || busy) return
        input = ""
        items.add(UserMsg(text))
        busy = true
        val msgIdx = items.size
        items.add(MitraMsg(""))
        scope.launch {
            val runtime = buildRuntime { chunk ->
                // Update the streaming reply bubble as chunks arrive.
                if (msgIdx < items.size) items[msgIdx] = MitraMsg(chunk)
            }
            activeRuntime = runtime
            var lastCardId: Int? = null
            runtime.run(com.mitra.agent.UserUtterance(text = text, source = "chat")).collect { event ->
                when (event) {
                    is RuntimeEvent.Speaking -> { /* handled by onChunk */ }
                    is RuntimeEvent.PlanReady -> {
                        if (event.plan.steps.isNotEmpty()) {
                            // Drop the streaming bubble in favour of an action card.
                            if (msgIdx < items.size) items.removeAt(msgIdx)
                            val step = event.plan.steps.first()
                            val call = com.mitra.agent.ToolCall(step.toolName, step.args)
                            val id = nextId++
                            lastCardId = id
                            val gated = ConfirmationGate.requiresConfirm(step.sideEffect)
                            items.add(
                                ActionCard(
                                    id = id,
                                    title = actionTitle(call),
                                    detail = actionDetail(call),
                                    state = if (gated && step.sideEffect == com.mitra.tools.SideEffect.Irreversible)
                                        ActionState.CONFIRM else ActionState.RUNNING,
                                    call = call,
                                ),
                            )
                        }
                    }
                    is RuntimeEvent.StepCompleted -> {
                        val id = lastCardId ?: return@collect
                        finishCard(
                            id = id,
                            success = event.result is com.mitra.automation.BackendResult.Success,
                            detail = when (val r = event.result) {
                                is com.mitra.automation.BackendResult.Success -> r.message
                                is com.mitra.automation.BackendResult.Failure -> r.message
                            },
                        )
                    }
                    is RuntimeEvent.Done -> {
                        if (lastCardId == null && msgIdx < items.size) {
                            val msg = (items[msgIdx] as? MitraMsg)?.text.orEmpty().ifBlank { event.summary }
                            items[msgIdx] = MitraMsg(msg)
                        }
                    }
                    is RuntimeEvent.Failed -> {
                        if (lastCardId == null && msgIdx < items.size) {
                            items[msgIdx] = MitraMsg("Sorry — ${event.reason}")
                        }
                    }
                    is RuntimeEvent.StepStarted, is RuntimeEvent.GateRequested, is RuntimeEvent.Replan -> {
                        // No additional UI work needed in V1; gate state already on the card.
                    }
                }
            }
            activeRuntime = null
            busy = false
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize()) {
            MinimalHeader(brainReady = brainReady, onOpenSettings = onOpenSettings)
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                if (items.isEmpty()) {
                    item { EmptyHero(brainReady = brainReady, onQuickPrompt = { send(it) }) }
                }
                items(items) { item ->
                    when (item) {
                        is UserMsg -> UserBubble(item.text)
                        is MitraMsg -> MitraReply(item.text, busy)
                        is ActionCard -> ActionCardView(item, onConfirm = ::runCard, onCancel = ::cancelCard)
                    }
                }
            }
            FloatingInputBar(value = input, onValueChange = { input = it }, onSend = { send() }, enabled = !busy)
            Spacer(Modifier.size(8.dp))
        }
    }
}
```

(The `MinimalHeader` no longer takes a `LiteRtBrain?` — change its first param to `brainReady: Boolean`. Same for `EmptyHero`.)

- [ ] **Step 2: Verify build**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Run unit tests**

Run: `./gradlew :app:testDebugUnitTest`
Expected: PASS (no UI tests in V1; just ensure nothing else broke).

- [ ] **Step 4: Manual smoke (developer device)**

Plug in the OnePlus Nord 2T or any test device with the model already downloaded:

Run: `./gradlew :app:installDebug`
Then on device: type "turn on the flashlight" → expect ActionCard → tap Confirm → flashlight toggles.
Then: type "how are you" → expect a streamed chat reply (no action card).

Mark step done only after both checks pass.

- [ ] **Step 5: Commit (bundles Task 11 + 12)**

```bash
git add app/src/main/kotlin/com/mitra/MainActivity.kt app/src/main/kotlin/com/mitra/ui/ChatScreen.kt
git commit -m "feat(ui+main): wire AgentRuntime through ChatScreen; remove AgentLoop dependency"
```

---

## Task 13: Delete `AgentLoop`

**Files:**
- Delete: `app/src/main/kotlin/com/mitra/agent/AgentLoop.kt`

- [ ] **Step 1: Confirm no remaining references**

Run: `./gradlew :app:compileDebugKotlin`
Then: search the repo.

Run: search for `AgentLoop` in the codebase via Grep tool.
Expected: only the file itself + maybe `plan.md`.

If any other file imports `AgentLoop`, fix that file first (it should not happen if Tasks 11 + 12 were complete).

- [ ] **Step 2: Delete the file**

```bash
rm app/src/main/kotlin/com/mitra/agent/AgentLoop.kt
```

- [ ] **Step 3: Verify build + tests**

Run: `./gradlew :app:testDebugUnitTest`
Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add -A app/src/main/kotlin/com/mitra/agent/AgentLoop.kt
git commit -m "refactor(agent): delete AgentLoop — fully replaced by AgentRuntime"
```

---

## Task 14: SnakeYAML test dependency

**Files:**
- Modify: `app/build.gradle.kts`

- [ ] **Step 1: Add the dependency**

Append inside the `dependencies { ... }` block in `app/build.gradle.kts`:

```kotlin
testImplementation("org.yaml:snakeyaml:2.3")
```

Final `dependencies { }` block reads:

```kotlin
dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.09.03"))
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.core:core-ktx:1.13.1")

    implementation("com.google.ai.edge.litertlm:litertlm-android:0.13.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.yaml:snakeyaml:2.3")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
}
```

(`kotlinx-coroutines-test` was already used implicitly in earlier tasks via `runBlocking`; add it explicitly so tests that need `TestScope` later don't break.)

- [ ] **Step 2: Verify dependency resolves**

Run: `./gradlew :app:dependencies --configuration testDebugRuntimeClasspath | grep -i snakeyaml`
Expected: `org.yaml:snakeyaml:2.3` appears.

- [ ] **Step 3: Commit**

```bash
git add app/build.gradle.kts
git commit -m "build(test): add snakeyaml 2.3 + coroutines-test for eval harness"
```

---

## Task 15: `commands.yaml` 50-case starter set

**Files:**
- Create: `app/src/test/resources/eval/commands.yaml`

- [ ] **Step 1: Author the fixture**

Create `app/src/test/resources/eval/commands.yaml` with 50 entries. The format below uses single-step gold for every case (Phase 0 ships only single-step scoring; multi-step support lands Phase 2). All `language: en`.

```yaml
# Mitra eval starter set — Phase 0
# 50 commands: 6 chit-chat negatives + 44 tool cases covering all 13 V1 tools.
# Format: id, utterance, gold (ordered list of tool calls), language, optional multi_step flag.

- id: "0001"
  utterance: "turn on the flashlight"
  gold:
    - tool: toggle_flashlight
      args: { on: true }
  language: en

- id: "0002"
  utterance: "torch off"
  gold:
    - tool: toggle_flashlight
      args: { on: false }
  language: en

- id: "0003"
  utterance: "turn the flashlight off"
  gold:
    - tool: toggle_flashlight
      args: { on: false }
  language: en

- id: "0004"
  utterance: "set an alarm for 7:30 am"
  gold:
    - tool: set_alarm
      args: { hour: 7, minute: 30 }
  language: en

- id: "0005"
  utterance: "wake me at 6 am"
  gold:
    - tool: set_alarm
      args: { hour: 6, minute: 0 }
  language: en

- id: "0006"
  utterance: "wake me at 6 pm"
  gold:
    - tool: set_alarm
      args: { hour: 18, minute: 0 }
  language: en

- id: "0007"
  utterance: "start a 5 minute timer"
  gold:
    - tool: start_timer
      args: { seconds: 300 }
  language: en

- id: "0008"
  utterance: "10 second countdown"
  gold:
    - tool: start_timer
      args: { seconds: 10 }
  language: en

- id: "0009"
  utterance: "1 hour timer"
  gold:
    - tool: start_timer
      args: { seconds: 3600 }
  language: en

- id: "0010"
  utterance: "open youtube.com"
  gold:
    - tool: open_url
      args: { url: "youtube.com" }
  language: en

- id: "0011"
  utterance: "go to example.org"
  gold:
    - tool: open_url
      args: { url: "example.org" }
  language: en

- id: "0012"
  utterance: "open spotify"
  gold:
    - tool: open_app
      args: { name: "spotify" }
  language: en

- id: "0013"
  utterance: "launch the calculator app"
  gold:
    - tool: open_app
      args: { name: "calculator" }
  language: en

- id: "0014"
  utterance: "open whatsapp"
  gold:
    - tool: open_app
      args: { name: "whatsapp" }
  language: en

- id: "0015"
  utterance: "bluetooth?"
  gold:
    - tool: open_settings
      args: { panel: "bluetooth" }
  language: en

- id: "0016"
  utterance: "wifi off"
  gold:
    - tool: open_settings
      args: { panel: "wifi" }
  language: en

- id: "0017"
  utterance: "airplane mode"
  gold:
    - tool: open_settings
      args: { panel: "airplane" }
  language: en

- id: "0018"
  utterance: "mobile data settings"
  gold:
    - tool: open_settings
      args: { panel: "mobile_data" }
  language: en

- id: "0019"
  utterance: "hotspot"
  gold:
    - tool: open_settings
      args: { panel: "hotspot" }
  language: en

- id: "0020"
  utterance: "set volume to 40%"
  gold:
    - tool: set_media_volume
      args: { level: 40 }
  language: en

- id: "0021"
  utterance: "mute volume"
  gold:
    - tool: set_media_volume
      args: { level: 0 }
  language: en

- id: "0022"
  utterance: "max volume"
  gold:
    - tool: set_media_volume
      args: { level: 100 }
  language: en

- id: "0023"
  utterance: "set brightness to 40%"
  gold:
    - tool: set_brightness
      args: { level: 40 }
  language: en

- id: "0024"
  utterance: "dim the screen"
  gold:
    - tool: set_brightness
      args: { level: 10 }
  language: en

- id: "0025"
  utterance: "max brightness"
  gold:
    - tool: set_brightness
      args: { level: 100 }
  language: en

- id: "0026"
  utterance: "turn on do not disturb"
  gold:
    - tool: set_dnd
      args: { on: true }
  language: en

- id: "0027"
  utterance: "turn off dnd"
  gold:
    - tool: set_dnd
      args: { on: false }
  language: en

- id: "0028"
  utterance: "zen mode off"
  gold:
    - tool: set_dnd
      args: { on: false }
  language: en

- id: "0029"
  utterance: "vibrate only"
  gold:
    - tool: set_ringer_mode
      args: { mode: "vibrate" }
  language: en

- id: "0030"
  utterance: "set ringer to silent"
  gold:
    - tool: set_ringer_mode
      args: { mode: "silent" }
  language: en

- id: "0031"
  utterance: "ringer to ring"
  gold:
    - tool: set_ringer_mode
      args: { mode: "ring" }
  language: en

- id: "0032"
  utterance: "turn on auto rotate"
  gold:
    - tool: set_auto_rotate
      args: { on: true }
  language: en

- id: "0033"
  utterance: "disable auto rotation"
  gold:
    - tool: set_auto_rotate
      args: { on: false }
  language: en

- id: "0034"
  utterance: "screen timeout 30 seconds"
  gold:
    - tool: set_screen_timeout
      args: { seconds: 30 }
  language: en

- id: "0035"
  utterance: "screen sleep 5 minutes"
  gold:
    - tool: set_screen_timeout
      args: { seconds: 300 }
  language: en

- id: "0036"
  utterance: "bluetooth on"
  gold:
    - tool: set_bluetooth
      args: { on: true }
  language: en

- id: "0037"
  utterance: "turn off bluetooth"
  gold:
    - tool: set_bluetooth
      args: { on: false }
  language: en

- id: "0038"
  utterance: "switch on flashlight"
  gold:
    - tool: toggle_flashlight
      args: { on: true }
  language: en

- id: "0039"
  utterance: "kill the torch"
  gold:
    - tool: toggle_flashlight
      args: { on: false }
  language: en

- id: "0040"
  utterance: "set an alarm for 12 pm"
  gold:
    - tool: set_alarm
      args: { hour: 12, minute: 0 }
  language: en

- id: "0041"
  utterance: "set an alarm for 12 am"
  gold:
    - tool: set_alarm
      args: { hour: 0, minute: 0 }
  language: en

- id: "0042"
  utterance: "30 minute timer"
  gold:
    - tool: start_timer
      args: { seconds: 1800 }
  language: en

- id: "0043"
  utterance: "location settings"
  gold:
    - tool: open_settings
      args: { panel: "location" }
  language: en

- id: "0044"
  utterance: "battery saver"
  gold:
    - tool: open_settings
      args: { panel: "battery" }
  language: en

# Chit-chat negatives — gold is empty: planner should produce no tool calls.
- id: "9001"
  utterance: "how are you today"
  gold: []
  language: en

- id: "9002"
  utterance: "what's the weather"
  gold: []
  language: en

- id: "9003"
  utterance: "tell me a joke"
  gold: []
  language: en

- id: "9004"
  utterance: "good morning"
  gold: []
  language: en

- id: "9005"
  utterance: "who are you"
  gold: []
  language: en

- id: "9006"
  utterance: "thanks"
  gold: []
  language: en
```

- [ ] **Step 2: Commit**

```bash
git add app/src/test/resources/eval/commands.yaml
git commit -m "test(eval): 50-command starter set covering all 13 V1 tools + chit-chat negatives"
```

---

## Task 16: `EvalCommand` + `EvalLoader`

**Files:**
- Create: `app/src/test/kotlin/com/mitra/eval/EvalCommand.kt`
- Create: `app/src/test/kotlin/com/mitra/eval/EvalLoader.kt`
- Test: `app/src/test/kotlin/com/mitra/eval/EvalLoaderTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
// app/src/test/kotlin/com/mitra/eval/EvalLoaderTest.kt
package com.mitra.eval

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EvalLoaderTest {
    @Test
    fun `loads the starter set with the expected count`() {
        val cases = EvalLoader.loadFromClasspath("/eval/commands.yaml")
        assertTrue("expected at least 44 tool cases + 6 negatives = 50", cases.size >= 50)
    }

    @Test
    fun `parses a tool case with args`() {
        val cases = EvalLoader.loadFromClasspath("/eval/commands.yaml")
        val c = cases.first { it.id == "0001" }
        assertEquals("turn on the flashlight", c.utterance)
        assertEquals(1, c.gold.size)
        assertEquals("toggle_flashlight", c.gold[0].tool)
        assertEquals(true, c.gold[0].args["on"])
    }

    @Test
    fun `parses a chit-chat negative as empty gold`() {
        val cases = EvalLoader.loadFromClasspath("/eval/commands.yaml")
        val c = cases.first { it.id == "9001" }
        assertTrue(c.gold.isEmpty())
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests com.mitra.eval.EvalLoaderTest`
Expected: FAIL — `EvalLoader` unresolved.

- [ ] **Step 3: Write the implementation**

```kotlin
// app/src/test/kotlin/com/mitra/eval/EvalCommand.kt
package com.mitra.eval

data class GoldCall(val tool: String, val args: Map<String, Any?>)

data class EvalCommand(
    val id: String,
    val utterance: String,
    val gold: List<GoldCall>,
    val language: String,
    val multiStep: Boolean = false,
)
```

```kotlin
// app/src/test/kotlin/com/mitra/eval/EvalLoader.kt
package com.mitra.eval

import org.yaml.snakeyaml.Yaml

object EvalLoader {
    fun loadFromClasspath(path: String): List<EvalCommand> {
        val stream = EvalLoader::class.java.getResourceAsStream(path)
            ?: error("eval file not found on classpath: $path")
        val raw = stream.use { Yaml().load<List<Map<String, Any?>>>(it) }
        return raw.map { row ->
            @Suppress("UNCHECKED_CAST")
            val goldRaw = (row["gold"] as? List<Map<String, Any?>>) ?: emptyList()
            EvalCommand(
                id = row["id"].toString(),
                utterance = row["utterance"].toString(),
                gold = goldRaw.map { g ->
                    @Suppress("UNCHECKED_CAST")
                    GoldCall(
                        tool = g["tool"].toString(),
                        args = (g["args"] as? Map<String, Any?>) ?: emptyMap(),
                    )
                },
                language = row["language"].toString(),
                multiStep = row["multi_step"] as? Boolean ?: false,
            )
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests com.mitra.eval.EvalLoaderTest`
Expected: PASS, 3 tests.

- [ ] **Step 5: Commit**

```bash
git add app/src/test/kotlin/com/mitra/eval/EvalCommand.kt app/src/test/kotlin/com/mitra/eval/EvalLoader.kt app/src/test/kotlin/com/mitra/eval/EvalLoaderTest.kt
git commit -m "test(eval): EvalCommand + YAML loader (SnakeYAML)"
```

---

## Task 17: `PlannerEvalRunner`

**Files:**
- Create: `app/src/test/kotlin/com/mitra/eval/PlannerEvalRunner.kt`
- Test: `app/src/test/kotlin/com/mitra/eval/PlannerEvalRunnerTest.kt`

Runs a `Planner` against a list of `EvalCommand`s and scores each: tool-name match + args-shape match. Phase 0 only scores single-step plans (`multi_step: false` cases); multi-step scoring lands Phase 2.

- [ ] **Step 1: Write the failing test**

```kotlin
// app/src/test/kotlin/com/mitra/eval/PlannerEvalRunnerTest.kt
package com.mitra.eval

import com.mitra.agent.IntentParser
import com.mitra.agent.IntentParserPlanner
import com.mitra.tools.SideEffect
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlannerEvalRunnerTest {
    private val planner = IntentParserPlanner(IntentParser()) { SideEffect.Reversible }

    @Test
    fun `scores a correct single-step case as pass`() = runBlocking {
        val cmd = EvalCommand(
            id = "t1",
            utterance = "turn on the flashlight",
            gold = listOf(GoldCall("toggle_flashlight", mapOf("on" to true))),
            language = "en",
        )
        val report = PlannerEvalRunner(planner).run(listOf(cmd))
        assertEquals(1, report.results.size)
        assertTrue(report.results[0].passed)
    }

    @Test
    fun `scores a wrong tool name as fail`() = runBlocking {
        val cmd = EvalCommand(
            id = "t2",
            utterance = "turn on the flashlight",
            gold = listOf(GoldCall("set_dnd", mapOf("on" to true))),
            language = "en",
        )
        val report = PlannerEvalRunner(planner).run(listOf(cmd))
        assertEquals(1, report.results.size)
        assertTrue(!report.results[0].passed)
    }

    @Test
    fun `scores an empty-gold case as pass when planner returns empty plan`() = runBlocking {
        val cmd = EvalCommand(id = "t3", utterance = "how are you", gold = emptyList(), language = "en")
        val report = PlannerEvalRunner(planner).run(listOf(cmd))
        assertTrue(report.results[0].passed)
    }

    @Test
    fun `aggregate reports overall pass rate`() = runBlocking {
        val cmds = listOf(
            EvalCommand("p1", "turn on the flashlight", listOf(GoldCall("toggle_flashlight", mapOf("on" to true))), "en"),
            EvalCommand("f1", "turn on the flashlight", listOf(GoldCall("set_dnd", mapOf("on" to true))), "en"),
        )
        val report = PlannerEvalRunner(planner).run(cmds)
        assertEquals(2, report.results.size)
        assertEquals(0.5f, report.passRate, 0.0001f)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests com.mitra.eval.PlannerEvalRunnerTest`
Expected: FAIL — `PlannerEvalRunner` unresolved.

- [ ] **Step 3: Write the implementation**

```kotlin
// app/src/test/kotlin/com/mitra/eval/PlannerEvalRunner.kt
package com.mitra.eval

import com.mitra.agent.Plan
import com.mitra.agent.Planner
import com.mitra.agent.TurnContext
import com.mitra.agent.UserUtterance

data class CaseResult(
    val id: String,
    val passed: Boolean,
    val expected: List<GoldCall>,
    val actual: List<Pair<String, Map<String, Any?>>>,
    val reason: String? = null,
)

data class EvalReport(val results: List<CaseResult>) {
    val passRate: Float get() = if (results.isEmpty()) 0f else results.count { it.passed } / results.size.toFloat()
}

class PlannerEvalRunner(private val planner: Planner) {
    suspend fun run(cases: List<EvalCommand>): EvalReport {
        val results = cases.map { c ->
            val plan: Plan = planner.plan(c.utterance, TurnContext(UserUtterance(c.utterance, "eval"), 0L, null))
            val actual = plan.steps.map { it.toolName to it.args }
            val passed = matches(c.gold, actual)
            CaseResult(
                id = c.id,
                passed = passed,
                expected = c.gold,
                actual = actual,
                reason = if (passed) null else "actual=$actual expected=${c.gold.map { it.tool to it.args }}",
            )
        }
        return EvalReport(results)
    }

    private fun matches(gold: List<GoldCall>, actual: List<Pair<String, Map<String, Any?>>>): Boolean {
        if (gold.size != actual.size) return false
        gold.zip(actual).forEach { (g, a) ->
            if (g.tool != a.first) return false
            // Args shape match: every gold key must be present and equal (allow planner to over-fill).
            g.args.forEach { (k, v) ->
                val av = a.second[k] ?: return false
                if (!sameNumberOrEqual(v, av)) return false
            }
        }
        return true
    }

    private fun sameNumberOrEqual(a: Any?, b: Any?): Boolean {
        if (a is Number && b is Number) return a.toDouble() == b.toDouble()
        return a == b
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests com.mitra.eval.PlannerEvalRunnerTest`
Expected: PASS, 4 tests.

- [ ] **Step 5: Commit**

```bash
git add app/src/test/kotlin/com/mitra/eval/PlannerEvalRunner.kt app/src/test/kotlin/com/mitra/eval/PlannerEvalRunnerTest.kt
git commit -m "test(eval): PlannerEvalRunner — scores tool-name + args-shape per case"
```

---

## Task 18: `EvalSmokeTest` (CI gate)

**Files:**
- Create: `app/src/test/kotlin/com/mitra/eval/EvalSmokeTest.kt`

Runs the full 50-command set against `IntentParserPlanner` wired with the production `ToolRegistry` side-effect classifications. Asserts pass rate ≥ threshold so CI catches dispatch / parser regressions.

- [ ] **Step 1: Write the test**

```kotlin
// app/src/test/kotlin/com/mitra/eval/EvalSmokeTest.kt
package com.mitra.eval

import com.mitra.agent.IntentParser
import com.mitra.agent.IntentParserPlanner
import com.mitra.tools.SideEffect
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test

class EvalSmokeTest {

    /** Mirrors production tool-name → SideEffect mapping without needing Android Context. */
    private val sideEffects: Map<String, SideEffect> = mapOf(
        "toggle_flashlight" to SideEffect.Reversible,
        "set_alarm" to SideEffect.Irreversible,
        "start_timer" to SideEffect.Reversible,
        "open_url" to SideEffect.None,
        "open_app" to SideEffect.None,
        "open_settings" to SideEffect.None,
        "set_media_volume" to SideEffect.Reversible,
        "set_brightness" to SideEffect.Reversible,
        "set_dnd" to SideEffect.Reversible,
        "set_ringer_mode" to SideEffect.Reversible,
        "set_auto_rotate" to SideEffect.Reversible,
        "set_screen_timeout" to SideEffect.Reversible,
        "set_bluetooth" to SideEffect.Reversible,
    )

    private val planner = IntentParserPlanner(
        parser = IntentParser(),
        sideEffectOf = { sideEffects[it] ?: SideEffect.Reversible },
    )

    @Test
    fun `IntentParserPlanner passes at least 80 percent of starter set`() = runBlocking {
        val cases = EvalLoader.loadFromClasspath("/eval/commands.yaml")
            .filterNot { it.multiStep } // Phase 0 only scores single-step
        val report = PlannerEvalRunner(planner).run(cases)

        val failed = report.results.filterNot { it.passed }
        // Print failed cases so CI logs show which dropped — useful for future regressions.
        if (failed.isNotEmpty()) {
            println("--- Eval failures (${failed.size} of ${report.results.size}) ---")
            failed.forEach { println("  ${it.id} :: ${it.reason}") }
        }

        // 80% threshold: any drop below this fails CI. Raise as the eval set grows + parser improves.
        assertTrue(
            "eval pass rate ${report.passRate} below 0.80 threshold (${failed.size} failures)",
            report.passRate >= 0.80f,
        )
    }
}
```

- [ ] **Step 2: Run the test**

Run: `./gradlew :app:testDebugUnitTest --tests com.mitra.eval.EvalSmokeTest`
Expected: PASS. If it fails, the run output prints each failed case's reason — those reveal parser gaps. Either:
  - Update `commands.yaml` to a phrasing the parser handles (only if the new phrasing is honest natural language; don't game the gate).
  - Fix `IntentParser` to handle the failing phrasing (a small additional commit before this one).

Hit the 80% threshold honestly. The test exists to keep us honest.

- [ ] **Step 3: Commit**

```bash
git add app/src/test/kotlin/com/mitra/eval/EvalSmokeTest.kt
git commit -m "test(eval): smoke test gates planner+dispatch on starter set (≥80% pass)"
```

---

## Task 19: CI documentation note (no workflow change)

**Files:**
- Modify: `app/.github/workflows/ci.yml` is already at `.github/workflows/ci.yml` — leave content unchanged.

EvalSmokeTest runs as part of `./gradlew :app:testDebugUnitTest`, which `ci.yml` already invokes. No yml change required.

- [ ] **Step 1: Add a comment to ci.yml documenting the eval gate**

Open `.github/workflows/ci.yml`. Find the line:

```yaml
      - name: Unit tests
        run: ./gradlew :app:testDebugUnitTest --stacktrace
```

Replace with:

```yaml
      - name: Unit tests (includes Mitra eval gate)
        # EvalSmokeTest in com.mitra.eval gates planner+dispatch on the starter set
        # at app/src/test/resources/eval/commands.yaml (≥80% pass required).
        run: ./gradlew :app:testDebugUnitTest --stacktrace
```

- [ ] **Step 2: Commit**

```bash
git add .github/workflows/ci.yml
git commit -m "ci: document that Mitra eval gate runs inside testDebugUnitTest"
```

---

## Task 20: Update `CLAUDE.md` repo-layout section

**Files:**
- Modify: `mitra/CLAUDE.md`

- [ ] **Step 1: Patch the repo-layout block to mention the new seams**

In `CLAUDE.md`, find the block:

```
│   │   ├── agent/                    # AgentLoop + IntentParser router
│   │   ├── inference/                # LiteRT-LM model hosting + ModelDownloader
│   │   ├── tools/                    # ONE tool per file + ToolRegistry
│   │   ├── safety/                   # ConfirmationGate + AuditLog (M2)
│   │   ├── ui/                       # Compose UI (chat, onboarding, download)
│   │   ├── accessibility/            # (planned, M6) AccessibilityService impl
│   │   ├── intents/                  # (planned, M5.5) Intent dispatch helpers
│   │   └── providers/                # (planned, M1) Content Provider wrappers
```

Replace with:

```
│   │   ├── agent/                    # AgentRuntime + Planner + ContextStore + InvocationSource + IntentParser
│   │   ├── automation/               # AutomationBackend tier system + ManagerApiBackend
│   │   ├── inference/                # LiteRT-LM model hosting + ModelDownloader
│   │   ├── tools/                    # ONE tool per file + ToolRegistry (declares AutomationTier)
│   │   ├── safety/                   # ConfirmationGate + AuditLog (M2)
│   │   ├── ui/                       # Compose UI (chat, onboarding, download)
│   │   ├── accessibility/            # (planned, Phase 4) AccessibilityService impl behind AutomationBackend
│   │   ├── intents/                  # (planned, Phase 2) Intent dispatch helpers (Deeplink tier)
│   │   └── providers/                # (planned, M1) Content Provider wrappers
```

Also find the existing block about AgentLoop and edit references to it. Search for the word "AgentLoop" in `CLAUDE.md` and replace each with "AgentRuntime" where the meaning is preserved. Specifically the line that reads:

```
What the **model** sees is the `@Tool` / `@ToolParam` annotations on the matching method in `inference/LiteRtBrain.kt` `PhoneTools` (LiteRT-LM auto-generates the schema from them). The `Tool` implementation above is dispatcher-side — `AgentLoop.runCall` maps a model-emitted call to it by `name`.
```

becomes:

```
What the **model** sees is the `@Tool` / `@ToolParam` annotations on the matching method in `inference/LiteRtBrain.kt` `PhoneTools` (LiteRT-LM auto-generates the schema from them). The `Tool` implementation above is dispatcher-side — `AgentRuntime` (via `ManagerApiBackend`) maps a model-emitted call to it by `name`.
```

- [ ] **Step 2: Commit**

```bash
git add mitra/CLAUDE.md
git commit -m "docs(claude): repo-layout reflects AgentRuntime + AutomationBackend seams"
```

(If `CLAUDE.md` is at `mitra/CLAUDE.md`, use that path. Adjust if the file is at repo root.)

---

## Task 21: Full verification

**Files:** none (verification only)

- [ ] **Step 1: Clean build + all tests**

Run: `./gradlew clean :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --stacktrace`
Expected: BUILD SUCCESSFUL, all tests pass, no new lint issues.

- [ ] **Step 2: On-device smoke (developer device with model already downloaded)**

Run: `./gradlew :app:installDebug`
On device, exercise each tool category at least once:
- `turn on the flashlight` → flashlight toggles, ActionCard goes DONE.
- `Bluetooth?` → opens Bluetooth settings panel.
- `set brightness to 50%` → brightness changes, ActionCard goes DONE.
- `set an alarm for 7:30 am` → ActionCard shows Confirm (Irreversible), tap Confirm → alarm intent fires.
- `how are you` → no ActionCard, streamed chat reply.

All five must work. If any fail, fix before continuing — Phase 0 exit criterion is "all existing tools work through new runtime."

- [ ] **Step 3: Update plan.md to reflect Phase 0 completion**

Append a one-paragraph note at the top of `mitra/plan.md` (or to the "Right-now tasks" section) recording that Phase 0 landed and the next phase to plan is Phase 1 (invocation surface). No checkbox ticks here — the new phases live in their own spec/plan files.

- [ ] **Step 4: Final commit**

```bash
git add mitra/plan.md
git commit -m "docs(plan): record Phase 0 completion; next is Phase 1 (invocation surface)"
```

---

## Plan Self-Review

### Spec coverage check
- ✓ §4.1 Planner — Tasks 2, 8, 9
- ✓ §4.2 AgentRuntime + RuntimeEvent — Tasks 7, 10
- ✓ §4.3 InvocationSource interface (impls land Phase 1) — Task 6
- ✓ §4.4 AutomationBackend + Tier — Tasks 3, 4, 5 (per-tool AutomationAction templates explicitly deferred to Phase 4 — see "Spec deviation note" at top)
- ✓ §4.5 ContextStore + TurnOnlyContextStore — Task 6
- ✓ §5 EvalHarness — Tasks 14–18 (filesystem location moved to test-resources — see deviation note)
- ✓ §6 Phase 0 exit ("13 tools pass eval through new runtime; no UI behavior change") — Task 21
- ✓ §7 Data flow — implemented in Task 10's AgentRuntime
- ✓ §9 Testing strategy (unit + fixture eval + privacy invariant preserved) — every task ships a JUnit test where appropriate

### Placeholder scan
- Searched for "TBD", "TODO", "fill in", "similar to": none in this plan.
- Every code step has a complete code block.
- Every command has expected output.
- Confidence threshold (`1.0f` in SingleShotPlanner) is intentional and documented in the code comment.

### Type consistency check
- `Plan(steps, rationale, confidence)` — same shape every reference.
- `PlannedStep(toolName, args, sideEffect, dependsOn)` — same every reference.
- `RuntimeEvent` cases match between Task 7 (definition) and Task 10/12 (consumers).
- `AutomationAction.ToolDispatch(name, args)` — same in Tasks 3, 5, 10.
- `BackendResult.Success(message)` / `Failure(message)` — same throughout.
- `ContextStore.beginTurn(utterance)` / `recordToolResult(result)` / `endTurn()` — same in Task 6 def and Task 10 use.

### Scope check
- Plan covers Phase 0 only, per spec §6 directive. Phases 1–4 explicitly out of scope.
- No phase-creep tasks (e.g. no QuickSettingsTile impl, no plan-then-execute, no wake word).

### Ambiguity check
- AgentRuntimeTest's poll-loop for `GateRequested` is flagged inline with a fallback (CompletableDeferred) — see Task 10 Step 4 note.
- "CLAUDE.md path" — Task 20 notes "adjust if file is at repo root" because both `mitra/CLAUDE.md` and root `CLAUDE.md` exist in the imported context above.
