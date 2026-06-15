# Agentic Loop Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Rework Mitra from single-shot brain emission to a multi-step agentic loop within a single turn.

**Spec:** `docs/superpowers/specs/2026-06-15-agentic-loop-design.md` (commit `59d990b`).

**Architecture:** Introduce a `Brain` interface (extracted from `LiteRtBrain`) with two methods: `chatStream(text)` for the first emission and `sendToolResult(name, result)` for the loop's tool-result feedback. `AgentRuntime` is reworked to own the loop: it tries `IntentParser` first (deterministic shortcut), else streams from `brain.chatStream`, dispatches each tool call as it arrives, calls `brain.sendToolResult` to feed back, repeats up to `STEP_CAP = 5`. The existing `RuntimeEvent.PlanReady` shape is preserved (one PlanReady per tool emission, each carrying a single-step `Plan`) so `ChatScreen` action-card rendering needs no change. `SingleShotPlanner` is deleted. `IntentParserPlanner` stays as the no-brain fallback.

**Tech Stack:** Kotlin 2.2, Coroutines + Flow, LiteRT-LM 0.13.0 (`com.google.ai.edge.litertlm`), JUnit 4, AndroidX Compose for UI (no change in this plan).

---

## File Structure

**Create:**
- `app/src/main/kotlin/com/mitra/inference/Brain.kt` — interface.
- `app/src/test/kotlin/com/mitra/inference/FakeBrain.kt` — scripted-emission test double.

**Modify:**
- `app/src/main/kotlin/com/mitra/inference/LiteRtBrain.kt` — implement `Brain`. Add `sendToolResult`. Rewrite system prompt + `send_sms.body` description.
- `app/src/main/kotlin/com/mitra/agent/AgentRuntime.kt` — new constructor + agentic loop body.
- `app/src/main/kotlin/com/mitra/agent/Router.kt` — open `IntentParser` + `route` for test subclassing.
- `app/src/main/kotlin/com/mitra/MainActivity.kt` — `buildRuntime` simplified.
- `app/src/main/kotlin/com/mitra/ui/ChatScreen.kt` — read streaming text via `RuntimeEvent.Speaking`.
- `app/src/test/kotlin/com/mitra/agent/AgentRuntimeTest.kt` — swap `StubPlanner` for `IntentParser` overrides + `FakeBrain`; add five new tests.
- `plan.md` — tick M2.5 items.
- `docs/superpowers/specs/2026-06-15-agentic-loop-design.md` — add status footer.

**Delete:**
- `app/src/main/kotlin/com/mitra/agent/SingleShotPlanner.kt`.

---

## Task 1: Extract `Brain` interface

**Files:**
- Create: `app/src/main/kotlin/com/mitra/inference/Brain.kt`
- Modify: `app/src/main/kotlin/com/mitra/inference/LiteRtBrain.kt:36-39`

- [ ] **Step 1: Create `Brain.kt`**

```kotlin
package com.mitra.inference

import kotlinx.coroutines.flow.Flow

interface Brain {
    fun chatStream(userText: String): Flow<BrainTurn>
    fun sendToolResult(toolName: String, result: Map<String, Any?>): Flow<BrainTurn>
}
```

- [ ] **Step 2: Make `LiteRtBrain` implement `Brain`**

Edit lines 36-39 of `LiteRtBrain.kt`. Change `class LiteRtBrain(modelPath: String, cacheDir: String) {` to `class LiteRtBrain(modelPath: String, cacheDir: String) : Brain {`. Mark the existing `fun chatStream(userText: String): Flow<BrainTurn>` with `override`.

- [ ] **Step 3: Verify build error is only missing `sendToolResult`**

Run `./gradlew :app:compileDebugKotlin`. Expect one error: `Class 'LiteRtBrain' is not abstract and does not implement abstract member fun sendToolResult`. Anything else, stop and read.

- [ ] **Step 4: Commit**

```
git add app/src/main/kotlin/com/mitra/inference/Brain.kt app/src/main/kotlin/com/mitra/inference/LiteRtBrain.kt
git commit -m "refactor(inference): extract Brain interface from LiteRtBrain

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Task 2: Add `FakeBrain` test double

**Files:**
- Create: `app/src/test/kotlin/com/mitra/inference/FakeBrain.kt`

- [ ] **Step 1: Write the file**

```kotlin
package com.mitra.inference

import com.mitra.agent.ToolCall
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow

class FakeBrain(
    private val legs: MutableList<List<BrainTurn>>,
) : Brain {
    val sentResults: MutableList<Pair<String, Map<String, Any?>>> = mutableListOf()

    override fun chatStream(userText: String): Flow<BrainTurn> = nextLeg()

    override fun sendToolResult(toolName: String, result: Map<String, Any?>): Flow<BrainTurn> {
        sentResults += toolName to result
        return nextLeg()
    }

    private fun nextLeg(): Flow<BrainTurn> =
        if (legs.isEmpty()) emptyList<BrainTurn>().asFlow() else legs.removeAt(0).asFlow()

    companion object {
        fun script(vararg legs: List<BrainTurn>): FakeBrain = FakeBrain(legs.toMutableList())

        fun leg(text: String, build: LegBuilder.() -> Unit = {}): List<BrainTurn> {
            val b = LegBuilder(text)
            b.build()
            return b.emissions
        }

        class LegBuilder(private val baseText: String) {
            val emissions = mutableListOf<BrainTurn>()
            init { if (baseText.isNotEmpty()) emissions += BrainTurn(text = baseText) }
            fun tool(name: String, args: Map<String, Any?> = emptyMap()) {
                emissions += BrainTurn(text = baseText, toolCall = ToolCall(name, args))
            }
        }
    }
}
```

- [ ] **Step 2: Spot-check the file compiles**

Run `./gradlew :app:compileDebugUnitTestKotlin 2>&1 | grep "FakeBrain.kt"`. Expect no output (LiteRtBrain error is on a different file).

- [ ] **Step 3: Commit**

```
git add app/src/test/kotlin/com/mitra/inference/FakeBrain.kt
git commit -m "test(inference): FakeBrain scripted-emission test double

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Task 3: Implement `LiteRtBrain.sendToolResult`

**Files:**
- Modify: `app/src/main/kotlin/com/mitra/inference/LiteRtBrain.kt` (insert after `chatStream` around line 226)

- [ ] **Step 1: Insert sendToolResult after chatStream**

```kotlin
    override fun sendToolResult(toolName: String, result: Map<String, Any?>): Flow<BrainTurn> =
        flow {
            var acc = ""
            var call: ToolCall? = null
            val response = Contents.of(Content.ToolResponse(name = toolName, response = result))
            conversation.sendMessageAsync(response).collect { msg ->
                val piece = textOf(msg)
                acc = if (piece.isNotEmpty() && piece.startsWith(acc)) piece else acc + piece
                msg.toolCalls.firstOrNull()?.let { call = ToolCall(it.name, argsToMap(it.arguments)) }
                emit(BrainTurn(sanitize(acc), call))
            }
        }.flowOn(Dispatchers.IO)
```

- [ ] **Step 2: Verify `Content.ToolResponse` constructor**

Run `./gradlew :app:compileDebugKotlin`. If error on `response = result`, probe the actual constructor:

```
cd /tmp && rm -rf litertlm_probe && mkdir litertlm_probe && cd litertlm_probe
unzip -p ~/.gradle/caches/modules-2/files-2.1/com.google.ai.edge.litertlm/litertlm-android/0.13.0/*/litertlm-android-0.13.0.aar classes.jar > c.jar
unzip -q c.jar
javap -p com/google/ai/edge/litertlm/Content\$ToolResponse.class
```

If `response: String`, change body to `Content.ToolResponse(name = toolName, response = org.json.JSONObject(result).toString())`.

- [ ] **Step 3: Build assembleDebug**

Run `./gradlew :app:assembleDebug`. Expect BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```
git add app/src/main/kotlin/com/mitra/inference/LiteRtBrain.kt
git commit -m "feat(inference): LiteRtBrain.sendToolResult for agentic loop

Pushes Content.ToolResponse into the conversation and streams the next
BrainTurn emissions. AgentRuntime calls this once per dispatched tool.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Task 4: Open `IntentParser` for test subclassing

**Files:**
- Modify: `app/src/main/kotlin/com/mitra/agent/Router.kt:22-23`

- [ ] **Step 1: Add `open`**

Line 22: `class IntentParser : Router {` -> `open class IntentParser : Router {`.
Line 23: `override fun route(input: String): ToolCall? {` -> `override open fun route(input: String): ToolCall? {`.

- [ ] **Step 2: Build**

Run `./gradlew :app:compileDebugKotlin`. Expect BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```
git add app/src/main/kotlin/com/mitra/agent/Router.kt
git commit -m "refactor(agent): make IntentParser open for test subclassing

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Task 5: Reshape `AgentRuntime` constructor (no loop body yet)

**Files:**
- Modify: `app/src/main/kotlin/com/mitra/agent/AgentRuntime.kt`
- Modify: `app/src/test/kotlin/com/mitra/agent/AgentRuntimeTest.kt`

- [ ] **Step 1: Replace constructor**

In `AgentRuntime.kt`, replace lines 27-33 with:

```kotlin
class AgentRuntime(
    private val brain: com.mitra.inference.Brain?,
    private val parser: IntentParser,
    private val sideEffectOf: (String) -> com.mitra.tools.SideEffect,
    private val backends: List<AutomationBackend>,
    private val context: ContextStore,
    private val audit: AuditLog,
) {
    @Volatile private var currentGate: Channel<GateDecision>? = null
```

- [ ] **Step 2: Replace `planner.plan(...)` block**

Replace lines 42-50 with:

```kotlin
                val ctx = context.turn() ?: error("turn missing after beginTurn")
                @Suppress("UNUSED_VARIABLE") val ctxRead = ctx
                val plan =
                    parser.route(utterance.text)?.let { call ->
                        Plan(
                            steps = listOf(
                                PlannedStep(
                                    toolName = call.name,
                                    args = call.args,
                                    sideEffect = sideEffectOf(call.name),
                                ),
                            ),
                            rationale = null,
                            confidence = 1.0f,
                        )
                    } ?: Plan(steps = emptyList(), rationale = null, confidence = 1.0f)
                emit(RuntimeEvent.PlanReady(plan))
```

The for-loop, gate handling, backend dispatch, audit below stay unchanged.

- [ ] **Step 3: Reshape `AgentRuntimeTest`**

Delete `StubPlanner` (lines 17-21). Replace `runtimeWith` helper with:

```kotlin
class AgentRuntimeTest {
    private fun fixedParser(match: ToolCall?): IntentParser =
        object : IntentParser() {
            override fun route(input: String): ToolCall? = match
        }

    private fun runtimeWith(
        intentMatch: ToolCall? = null,
        backend: StubBackend = StubBackend(),
        sideEffectOf: (String) -> SideEffect = { SideEffect.None },
    ): AgentRuntime =
        AgentRuntime(
            brain = null,
            parser = fixedParser(intentMatch),
            sideEffectOf = sideEffectOf,
            backends = listOf(backend),
            context = TurnOnlyContextStore { 0L },
            audit = AuditLog(),
        )
```

Update every test in the file:

- `empty plan emits PlanReady then Done` -> `runtimeWith(intentMatch = null)`.
- `single None-side-effect step runs without gate` -> `runtimeWith(intentMatch = ToolCall("open_url", mapOf("url" to "x.com")), backend = backend, sideEffectOf = { SideEffect.None })`.
- `Irreversible step pauses on GateRequested` -> `intentMatch = ToolCall("send_sms", mapOf("to" to "x", "body" to "y"))`, `sideEffectOf = { SideEffect.Irreversible }`.
- `Irreversible step cancelled by user` -> `intentMatch = ToolCall("send_sms", mapOf("to" to "x"))`, `sideEffectOf = { SideEffect.Irreversible }`.
- `backend failure makes runtime emit Failed` -> `intentMatch = ToolCall("open_url", mapOf("url" to "x"))`, `SideEffect.None`.
- `no backend supports the action - Failed` -> inline ctor with `parser = fixedParser(ToolCall("nope", emptyMap()))`, `sideEffectOf = { SideEffect.None }`, `brain = null`, `backends = emptyList()`.
- `Reversible step runs without GateRequested` -> `intentMatch = ToolCall("set_brightness", mapOf("level" to 50))`, `sideEffectOf = { SideEffect.Reversible }`.

- [ ] **Step 4: Run all tests**

Run `./gradlew :app:testDebugUnitTest --tests "com.mitra.agent.AgentRuntimeTest"`. Expect 7 passing tests.

- [ ] **Step 5: Commit**

```
git add app/src/main/kotlin/com/mitra/agent/AgentRuntime.kt app/src/test/kotlin/com/mitra/agent/AgentRuntimeTest.kt
git commit -m "refactor(agent): AgentRuntime takes Brain + IntentParser directly

Constructor: planner removed. brain, parser, sideEffectOf added. Old
behaviour preserved via the IntentParser path. Loop body lands next.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Task 6: Implement the agentic loop body

**Files:**
- Modify: `app/src/main/kotlin/com/mitra/agent/AgentRuntime.kt`
- Modify: `app/src/test/kotlin/com/mitra/agent/AgentRuntimeTest.kt`

- [ ] **Step 1: Write the failing chain test**

Append to `AgentRuntimeTest`:

```kotlin
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
```

- [ ] **Step 2: Confirm fail**

Run the test. Expect FAIL: `backend.dispatches.size` is 0.

- [ ] **Step 3: Replace `run` body in AgentRuntime.kt**

Replace the entire `fun run(...)` method with:

```kotlin
    fun run(utterance: UserUtterance): Flow<RuntimeEvent> =
        flow {
            check(currentGate == null) { "AgentRuntime is single-turn-at-a-time; previous run still active" }
            val gate = Channel<GateDecision>(capacity = Channel.RENDEZVOUS)
            currentGate = gate
            context.beginTurn(utterance)
            try {
                parser.route(utterance.text)?.let { call ->
                    val step = PlannedStep(call.name, call.args, sideEffectOf(call.name))
                    emit(RuntimeEvent.PlanReady(Plan(listOf(step), null, 1.0f)))
                    val failure = dispatchStep(step, index = 0, gate = gate)
                    when (failure) {
                        null -> emit(RuntimeEvent.Done(summary = "done"))
                        CANCEL_SENTINEL -> emit(RuntimeEvent.Failed(reason = "cancelled by user"))
                        else -> emit(RuntimeEvent.Failed(reason = failure))
                    }
                    return@flow
                }

                val brain = brain
                if (brain == null) {
                    emit(RuntimeEvent.PlanReady(Plan(emptyList(), null, 1.0f)))
                    emit(RuntimeEvent.Done(summary = "I'm not sure how to help with that one yet."))
                    return@flow
                }

                var stream =
                    try {
                        brain.chatStream(utterance.text)
                    } catch (c: kotlinx.coroutines.CancellationException) {
                        throw c
                    } catch (_: Throwable) {
                        emit(RuntimeEvent.Failed(reason = "I lost my train of thought. Mind sending that again?"))
                        return@flow
                    }
                var stepIndex = 0
                var lastText = ""
                while (stepIndex < STEP_CAP) {
                    var emittedCall: ToolCall? = null
                    try {
                        stream.collect { turn ->
                            if (turn.text.isNotEmpty()) {
                                lastText = turn.text
                                emit(RuntimeEvent.Speaking(text = turn.text))
                            }
                            turn.toolCall?.let { emittedCall = it }
                        }
                    } catch (c: kotlinx.coroutines.CancellationException) {
                        throw c
                    } catch (_: Throwable) {
                        emit(RuntimeEvent.Failed(reason = "I lost my train of thought. Mind sending that again?"))
                        return@flow
                    }

                    val call = emittedCall
                    if (call == null) {
                        emit(RuntimeEvent.Done(summary = lastText.ifBlank { "done" }))
                        return@flow
                    }

                    val step = PlannedStep(call.name, call.args, sideEffectOf(call.name))
                    emit(RuntimeEvent.PlanReady(Plan(listOf(step), null, 1.0f)))
                    val failure = dispatchStep(step, index = stepIndex, gate = gate)
                    val cancelled = failure == CANCEL_SENTINEL
                    val resultMap =
                        buildResultMap(
                            toolName = call.name,
                            failure = if (cancelled) null else failure,
                            cancelled = cancelled,
                        )
                    stepIndex++
                    stream =
                        try {
                            brain.sendToolResult(call.name, resultMap)
                        } catch (c: kotlinx.coroutines.CancellationException) {
                            throw c
                        } catch (_: Throwable) {
                            emit(RuntimeEvent.Failed(reason = "I lost my train of thought. Mind sending that again?"))
                            return@flow
                        }
                }
                emit(RuntimeEvent.Failed(reason = "hit the tool limit — let me know what else you need"))
            } finally {
                currentGate = null
                gate.close()
                context.endTurn()
            }
        }

    private suspend fun kotlinx.coroutines.flow.FlowCollector<RuntimeEvent>.dispatchStep(
        step: PlannedStep,
        index: Int,
        gate: kotlinx.coroutines.channels.Channel<GateDecision>,
    ): String? {
        if (step.sideEffect == com.mitra.tools.SideEffect.Irreversible) {
            emit(RuntimeEvent.GateRequested(index, step))
            val decision = gate.receive()
            if (decision == GateDecision.Cancel) {
                audit.record(step.toolName, step.sideEffect, ok = false)
                return CANCEL_SENTINEL
            }
        }
        emit(RuntimeEvent.StepStarted(index, step))
        val action = AutomationAction.ToolDispatch(step.toolName, step.args)
        val backend = backends.firstOrNull { it.supports(action) }
        val result: BackendResult =
            backend?.execute(action) ?: BackendResult.Failure("no backend supports ${step.toolName}")
        audit.record(step.toolName, step.sideEffect, ok = result is BackendResult.Success)
        context.recordToolResult(
            when (result) {
                is BackendResult.Success -> com.mitra.tools.ToolResult.Success(result.message)
                is BackendResult.Failure -> com.mitra.tools.ToolResult.Failure(result.message)
            },
        )
        emit(RuntimeEvent.StepCompleted(index, step, result))
        return when (result) {
            is BackendResult.Success -> null
            is BackendResult.Failure -> result.message
        }
    }

    private fun buildResultMap(toolName: String, failure: String?, cancelled: Boolean): Map<String, Any?> =
        when {
            cancelled -> mapOf("cancelled" to true)
            failure == null -> mapOf("ok" to true, "message" to "tool $toolName ran")
            else -> mapOf("ok" to false, "error" to failure)
        }

    companion object {
        const val STEP_CAP = 5
        private const val CANCEL_SENTINEL = "__cancelled__"
    }
```

Ensure these imports exist at the top of `AgentRuntime.kt`:

```kotlin
import com.mitra.automation.AutomationAction
import com.mitra.automation.AutomationBackend
import com.mitra.automation.BackendResult
import com.mitra.safety.AuditLog
import com.mitra.tools.SideEffect
import com.mitra.tools.ToolResult
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
```

- [ ] **Step 4: Re-run chain test**

Expect PASS.

- [ ] **Step 5: Run all AgentRuntime tests**

Expect 8 passing.

- [ ] **Step 6: Commit**

```
git add app/src/main/kotlin/com/mitra/agent/AgentRuntime.kt app/src/test/kotlin/com/mitra/agent/AgentRuntimeTest.kt
git commit -m "feat(agent): multi-step agentic loop in AgentRuntime

Brain emits tool -> dispatcher runs + audits -> brain.sendToolResult feeds
result back -> brain emits next tool or final text -> repeat up to
STEP_CAP=5. IntentParser still shortcuts deterministic matches.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Task 7: Cover cancel, replan, cap, JNI-error paths

**Files:**
- Modify: `app/src/test/kotlin/com/mitra/agent/AgentRuntimeTest.kt`

- [ ] **Step 1: Cancel-mid-chain**

```kotlin
    @Test
    fun `agentic loop - user cancels Irreversible step, brain receives cancelled=true`() =
        runBlocking {
            val brain =
                com.mitra.inference.FakeBrain.script(
                    com.mitra.inference.FakeBrain.leg("") { tool("send_sms", mapOf("name" to "blanta", "body" to "hi")) },
                    com.mitra.inference.FakeBrain.leg("okay, didn't send."),
                )
            val backend = StubBackend()
            val rt = AgentRuntime(brain = brain, parser = fixedParser(null), sideEffectOf = { SideEffect.Irreversible }, backends = listOf(backend), context = TurnOnlyContextStore { 0L }, audit = AuditLog())
            val collected = mutableListOf<RuntimeEvent>()
            val job = launch { rt.run(UserUtterance("text blanta hi", "test")).collect { collected += it } }
            while (collected.none { it is RuntimeEvent.GateRequested }) { delay(1) }
            rt.resume(GateDecision.Cancel)
            job.join()
            assertEquals(0, backend.dispatches.size)
            assertEquals(1, brain.sentResults.size)
            assertEquals("send_sms", brain.sentResults[0].first)
            assertEquals(true, brain.sentResults[0].second["cancelled"])
            val done = collected.last() as RuntimeEvent.Done
            assertEquals("okay, didn't send.", done.summary)
        }
```

- [ ] **Step 2: Replan-on-failure**

```kotlin
    @Test
    fun `agentic loop - tool failure is fed back, brain emits final text instead of retrying`() =
        runBlocking {
            val brain =
                com.mitra.inference.FakeBrain.script(
                    com.mitra.inference.FakeBrain.leg("") { tool("make_call", mapOf("name" to "ambiguous")) },
                    com.mitra.inference.FakeBrain.leg("which one — mom (mobile) or mom (work)?"),
                )
            val backend = StubBackend(BackendResult.Failure("multiple matches for 'ambiguous'"))
            val rt = AgentRuntime(brain = brain, parser = fixedParser(null), sideEffectOf = { SideEffect.Reversible }, backends = listOf(backend), context = TurnOnlyContextStore { 0L }, audit = AuditLog())
            val events = rt.run(UserUtterance("call mom", "test")).toList()
            assertEquals(1, backend.dispatches.size)
            assertEquals(1, brain.sentResults.size)
            assertEquals(false, brain.sentResults[0].second["ok"])
            assertEquals("multiple matches for 'ambiguous'", brain.sentResults[0].second["error"])
            val done = events.last() as RuntimeEvent.Done
            assertEquals("which one — mom (mobile) or mom (work)?", done.summary)
        }
```

- [ ] **Step 3: Step-cap**

```kotlin
    @Test
    fun `agentic loop - step cap stops a runaway brain`() =
        runBlocking {
            val legs = (1..6).map { com.mitra.inference.FakeBrain.leg("") { tool("toggle_flashlight", mapOf("on" to true)) } }
            val brain = com.mitra.inference.FakeBrain(legs.toMutableList())
            val backend = StubBackend()
            val rt = AgentRuntime(brain = brain, parser = fixedParser(null), sideEffectOf = { SideEffect.Reversible }, backends = listOf(backend), context = TurnOnlyContextStore { 0L }, audit = AuditLog())
            val events = rt.run(UserUtterance("spam flashlight", "test")).toList()
            assertEquals(AgentRuntime.STEP_CAP, backend.dispatches.size)
            val last = events.last()
            assertTrue(last is RuntimeEvent.Failed)
            assertEquals("hit the tool limit — let me know what else you need", (last as RuntimeEvent.Failed).reason)
        }
```

- [ ] **Step 4: JNI-error**

```kotlin
    @Test
    fun `agentic loop - brain throws on first chatStream surfaces friendly Failed`() =
        runBlocking {
            val brain = object : com.mitra.inference.Brain {
                override fun chatStream(userText: String) = kotlinx.coroutines.flow.flow<com.mitra.inference.BrainTurn> { throw RuntimeException("simulated LiteRtLmJniException") }
                override fun sendToolResult(toolName: String, result: Map<String, Any?>) = kotlinx.coroutines.flow.flow<com.mitra.inference.BrainTurn> { }
            }
            val rt = AgentRuntime(brain = brain, parser = fixedParser(null), sideEffectOf = { SideEffect.Reversible }, backends = listOf(StubBackend()), context = TurnOnlyContextStore { 0L }, audit = AuditLog())
            val events = rt.run(UserUtterance("hi", "test")).toList()
            val last = events.last()
            assertTrue(last is RuntimeEvent.Failed)
            assertEquals("I lost my train of thought. Mind sending that again?", (last as RuntimeEvent.Failed).reason)
        }
```

- [ ] **Step 5: Run all tests**

Expect 12 passing.

- [ ] **Step 6: Commit**

```
git add app/src/test/kotlin/com/mitra/agent/AgentRuntimeTest.kt
git commit -m "test(agent): cover cancel-mid-chain / replan / step-cap / JNI-error paths

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Task 8: Delete SingleShotPlanner + wire MainActivity + ChatScreen

**Files:**
- Delete: `app/src/main/kotlin/com/mitra/agent/SingleShotPlanner.kt`
- Modify: `app/src/main/kotlin/com/mitra/MainActivity.kt`
- Modify: `app/src/main/kotlin/com/mitra/ui/ChatScreen.kt`

- [ ] **Step 1: Delete**

```
git rm app/src/main/kotlin/com/mitra/agent/SingleShotPlanner.kt
```

- [ ] **Step 2: Update `MainActivity.buildRuntime` lambda body**

Replace the lambda body (lines 63-78) with:

```kotlin
                    buildRuntime = { brain, _ ->
                        AgentRuntime(
                            brain = brain,
                            parser = IntentParser(),
                            sideEffectOf = sideEffectOf,
                            backends = listOf(backend),
                            context = context,
                            audit = audit,
                        )
                    },
```

Remove the unused imports `com.mitra.agent.IntentParserPlanner` and `com.mitra.agent.SingleShotPlanner`.

- [ ] **Step 3: Update `ChatScreen.kt` Speaking branch**

Find `is RuntimeEvent.Speaking -> { /* handled by onChunk */ }` and replace with:

```kotlin
                    is RuntimeEvent.Speaking -> {
                        if (msgIdx < items.size) items[msgIdx] = MitraMsg(event.text)
                    }
```

Find `buildRuntime { chunk -> ... }` (around line 247) and change to `buildRuntime { _ -> }` with an empty body.

- [ ] **Step 4: Build + tests**

Run `./gradlew :app:assembleDebug` (expect SUCCESS) then `./gradlew :app:testDebugUnitTest` (expect ALL PASS).

- [ ] **Step 5: Commit**

```
git add app/src/main/kotlin/com/mitra/MainActivity.kt app/src/main/kotlin/com/mitra/ui/ChatScreen.kt
git commit -m "refactor(agent): delete SingleShotPlanner; wire MainActivity + ChatScreen to new loop

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Task 9: System prompt + send_sms.body rewrite

**Files:**
- Modify: `app/src/main/kotlin/com/mitra/inference/LiteRtBrain.kt`

- [ ] **Step 1: Replace the systemInstruction triple-quoted block**

Find the `Contents.of("""...""".trimIndent())` argument inside the `ConversationConfig(...)` block (around lines 148-176). Append three new sections (COMPOSE, TONE, AGENTIC) to the existing prompt and tighten the CALL/SMS section. Verbatim text from the design spec §System prompt:

- **CALL/SMS append:** "send_sms — emit it ONLY when the user supplied an actual message body in the same utterance, AND draft the body from intent (see COMPOSE below). If the user only said 'text mom' with no body, ask 'what should I say?' in chat instead of emitting a tool call with an empty body."

- **COMPOSE section:** "When the user gives you an instruction like 'tell X ...', 'ask X ...', 'text X to do ...', 'send X a message saying ...', YOU draft the body. Never paste the user's instruction verbatim. Examples — 'ask blanta to come over' -> body: 'hey, can you swing by?'. 'tell mom I'll be late' -> body: 'running late, see you soon'. 'text dad i'm not coming' -> body: 'can't make it today, sorry'. The ONLY verbatim case: user wrapped body in quotes ('text mom \"on my way\"' -> body: 'on my way'). Default tone: casual friend, contractions OK, lowercase OK. Mirror formal register if used. Keep bodies under 160 characters."

- **TONE section:** "After a tool fires, you MAY say ONE short clause acknowledging the user's mood, then stop. vent/swear/frustration -> 'alright, sent.' / 'done.' / 'okay.' Neutral -> 'sent.' / 'done.' / 'set.' Happy/casual -> 'nice, sent.' / 'got it.' Never moralize. Hard rules from VOICE still apply."

- **AGENTIC section:** "Each turn you may call up to 5 tools before you must end with a final reply. After a tool runs you receive a JSON result map. Success ({\"ok\": true}) -> call another tool toward the same goal, or finish with a 1-clause reply. Failure ({\"ok\": false, \"error\": \"...\"}) -> retry with different args, skip, or finish honestly. Do NOT silently re-emit the same call. Cancelled ({\"cancelled\": true}) -> user cancelled at the confirm card. Acknowledge briefly and stop. Hit cap -> runtime stops you."

- [ ] **Step 2: Rewrite `send_sms.body` `@ToolParam` description**

In the `class PhoneTools` block, find `fun send_sms(`. Replace the `body` parameter's `@ToolParam(description = ...)` with:

```kotlin
            @ToolParam(
                description = "the composed message body to send. YOU draft it from the user's intent — see the COMPOSE system rule. The user said 'ask blanta to come over'? body is 'hey, can you swing by?', NOT 'ask blanta to come over'. Only copy the user's exact words when they wrapped the body in quotes.",
            ) body: String,
```

- [ ] **Step 3: Build**

Run `./gradlew :app:assembleDebug`. Expect SUCCESS.

- [ ] **Step 4: Commit**

```
git add app/src/main/kotlin/com/mitra/inference/LiteRtBrain.kt
git commit -m "feat(inference): COMPOSE / TONE / AGENTIC system prompt + send_sms.body rewrite

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Task 10: Manual on-device test

**Files:**
- Create: `docs/research/2026-06-15-agentic-loop-manual-test.md`

- [ ] **Step 1: Install + launch**

```
./gradlew :app:installDebug
adb mdns services | grep tls-connect
adb -s 192.168.1.191:<port> shell am force-stop com.mitra
adb -s 192.168.1.191:<port> shell monkey -p com.mitra -c android.intent.category.LAUNCHER 1
```

- [ ] **Step 2: Walk 8 scenarios, log results**

Create `docs/research/2026-06-15-agentic-loop-manual-test.md` with eight sections — one per scenario. Each section: Utterance, Expected, Actual, Pass.

Scenarios:
1. Compose: "ask blanta to come over" -> drafted body (NOT verbatim)
2. Chain: "find blanta's number then text her hi" -> query_contacts + send_sms
3. Multi-tool: "quiet for meeting" -> set_dnd + set_ringer_mode
4. Reflect on fail: "call <missing-name>" -> clarification or honest failure summary
5. Cross-turn memory (P2 — known limitation): "what's blanta's number" -> "text her hi"
6. Tone: "ugh tell blanta i'm not coming" -> mood-mirroring reply
7. Smart clarify (P2 — known limitation): "text the boss" -> clarification, not empty-body SMS
8. Compose with side-arg: "remind me to take pills at 9" -> set_alarm(9, 0)

- [ ] **Step 3: Commit log**

```
git add docs/research/2026-06-15-agentic-loop-manual-test.md
git commit -m "docs(research): manual on-device test log for agentic loop

Walked the 8 spec scenarios on CPH2401. Captures real Gemma 4 E2B
behaviour vs spec target; tuning material for the next system-prompt
iteration if any scenarios underperform.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Task 11: Doc sync

**Files:**
- Modify: `plan.md`
- Modify: `docs/superpowers/specs/2026-06-15-agentic-loop-design.md`

Per the Keep-docs-honest rule in CLAUDE.md.

- [ ] **Step 1: Tick M2.5 items**

In `plan.md` M2.5 section, change every `- [ ]` to `- [x]` for the 7 listed items.

- [ ] **Step 2: Update Right-now task #1**

Replace with:

```markdown
1. ~~**Ship M2.5 agentic loop**~~ Shipped <YYYY-MM-DD> in commits `<first>..<last>`. Manual test log: [docs/research/2026-06-15-agentic-loop-manual-test.md](docs/research/2026-06-15-agentic-loop-manual-test.md). Next: P2 brain work (cross-turn memory + proactive clarification) — its own design + plan.
```

Fill `<YYYY-MM-DD>` and SHA range from `git log --oneline 59d990b..HEAD`.

- [ ] **Step 3: Stamp the spec footer**

Immediately after the `**Status:** Approved 2026-06-15 (chat brainstorm).` line in the design spec, add:

```markdown
**Implementation status:** Shipped <YYYY-MM-DD> in commits `<first>..<last>`. Implementation plan: [docs/superpowers/plans/2026-06-15-agentic-loop.md](../../plans/2026-06-15-agentic-loop.md). Manual test log: [docs/research/2026-06-15-agentic-loop-manual-test.md](../../../research/2026-06-15-agentic-loop-manual-test.md).
```

- [ ] **Step 4: Commit**

```
git add plan.md docs/superpowers/specs/2026-06-15-agentic-loop-design.md
git commit -m "docs: tick M2.5; stamp agentic-loop spec as shipped

Per the Keep-docs-honest rule in CLAUDE.md.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Self-Review Checklist

**Spec coverage:**
- LiteRtBrain.sendToolResult -> Task 3
- AgentRuntime rework -> Tasks 5 + 6
- SingleShotPlanner deletion -> Task 8
- System prompt rewrite -> Task 9
- send_sms.body description -> Task 9
- MainActivity buildRuntime -> Task 8
- ChatScreen Speaking handling -> Task 8
- Step cap -> Tasks 6 + 7
- Cancel handling -> Tasks 6 + 7
- FakeBrain -> Task 2
- Test extensions -> Tasks 6 + 7
- Manual on-device -> Task 10
- Doc sync -> Task 11

**Type consistency:**
- `Brain.sendToolResult(toolName: String, result: Map<String, Any?>): Flow<BrainTurn>` consistent across Tasks 1, 2, 3, 6.
- `STEP_CAP` const defined Task 6, asserted Task 7.
- `CANCEL_SENTINEL` internal to AgentRuntime only.
- Result-map keys (`ok`, `message`, `error`, `cancelled`) match system prompt §AGENTIC in Task 9.
- `RuntimeEvent.Speaking.text` emitted Task 6, consumed Task 8.

**No placeholders:** every step has exact code or exact command + expected output.
