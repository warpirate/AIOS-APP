# Mitra — Autonomous Interaction Design

**Date:** 2026-06-08
**Status:** Brainstorm-approved, awaiting plan
**Supersedes:** Implicit AgentLoop-as-single-shot assumption in `plan.md`

---

## 1. Problem

Today Mitra is a chat app that fires one tool per turn. To feel like an assistant rather than a chat client, two gaps must close:

1. **Invocation friction.** User opens an app and types. Should be one motion from any screen, including locked.
2. **Multi-step autonomy.** "Book Uber to airport and silence notifications" requires three tool calls. Today the loop returns to user after each, killing the flow.

This spec defines the architecture that closes both gaps **and survives V1 → V2 → V3 without rewrite.**

## 2. Non-goals

- Cloud anything. Same privacy invariant as `CLAUDE.md`.
- Always-listening / ambient triggers from day 1. Wake word is V3, opt-in.
- Persistent learned user model. Long-scope context out of V1 (see §6 ContextStore — only `Turn` impl ships now, `Session`/`Long` are future impls behind same seam).
- Proactive notification-triggered actions. Reachable later via same seam, not in V1 scope.

## 3. Guiding rule

Per `mitra-long-trajectory` memory: optimize for furthest reachable point, not cheapest ship. Every phase must invest in a seam used by later phases. No phase exists purely to ship a demo. Per `mitra-no-overengineering`: simple **surface**, correct **seams**. First impl can be minimal; the interface must support the V2/V3 case.

## 4. Architecture — five seams

All interfaces defined in Phase 0. First impls are the minimum that makes V1 work; later impls swap in without touching consumers.

### 4.1 `Planner`

```kotlin
interface Planner {
    suspend fun plan(utterance: String, ctx: TurnContext): Plan
}

data class Plan(
    val steps: List<PlannedStep>,
    val rationale: String?,           // model's own explanation, surfaced in UI
    val confidence: Float,            // 0..1, drives bulk vs per-step confirm
)

data class PlannedStep(
    val toolName: String,
    val args: Map<String, Any?>,
    val sideEffect: SideEffect,       // mirrored from Tool for gate consultation
    val dependsOn: List<Int> = emptyList(),  // indices into steps; enables partial replan
)
```

**V1 impl: `SingleShotPlanner`** — wraps current `LiteRtBrain`. Calls E2B once; if model emits one tool call, returns `Plan(steps=[that])`; if model emits chained calls (E2B can), returns `Plan(steps=all)`. Confidence is fixed `1.0` until eval data proves otherwise.

**V2 impl: `PlanThenExecutePlanner`** — explicit two-pass prompt: first "list the tools you would call in order"; then validate + execute. Brings confidence scoring.

**V3 impl: `HierarchicalPlanner`** — decomposes app-internal flows into subplans. Used by AccessibilityService backend.

### 4.2 `AgentRuntime`

Replaces today's `AgentLoop`. Not a loop — a state machine. Executes a `Plan`, owns: dispatch, retry, partial-failure replan, abort, audit emission, gate consultation.

```kotlin
class AgentRuntime(
    private val planner: Planner,
    private val toolRegistry: ToolRegistry,
    private val gate: ConfirmationGate,
    private val backend: AutomationBackend,
    private val context: ContextStore,
    private val audit: AuditLog,
) {
    fun run(utterance: String): Flow<RuntimeEvent>
    suspend fun abort()
    suspend fun resume(decision: GateDecision)   // user answered a confirm
}

sealed interface RuntimeEvent {
    data class PlanReady(val plan: Plan) : RuntimeEvent
    data class StepStarted(val index: Int, val step: PlannedStep) : RuntimeEvent
    data class StepCompleted(val index: Int, val result: ToolResult) : RuntimeEvent
    data class GateRequested(val index: Int, val step: PlannedStep) : RuntimeEvent
    data class Replan(val reason: String, val newPlan: Plan) : RuntimeEvent
    data class Done(val summary: String) : RuntimeEvent
    data class Failed(val cause: Throwable) : RuntimeEvent
}
```

UI consumes the `Flow<RuntimeEvent>` and renders accordingly. AgentRuntime never touches Compose.

### 4.3 `InvocationSource`

```kotlin
interface InvocationSource {
    val id: String
    fun events(): Flow<UserUtterance>
}

data class UserUtterance(
    val text: String,
    val source: String,                          // "qs-tile" | "assistant-role" | "power-key" | "wake-word"
    val origin: ScreenOrigin = ScreenOrigin.Foreground,
)
```

**Phase 1 impls:** `QuickSettingsTileSource`, `AssistantRoleSource` (VoiceInteractionService), `PowerKeySource` (where OEM allows assistant remap).
**Phase 3 impl:** `WakeWordSource` (opt-in, off by default).
**Future:** `NotificationContextSource` (proactive). Pure addition, no consumer change.

### 4.4 `AutomationBackend` (pulled forward from M5.5)

```kotlin
enum class AutomationTier { ManagerApi, RemoteInput, Deeplink, A11yGesture }

interface AutomationBackend {
    val tier: AutomationTier
    fun supports(action: AutomationAction): Boolean
    suspend fun execute(action: AutomationAction): BackendResult
}
```

Dispatcher picks the highest-tier backend that supports each action. V1 has only `ManagerApiBackend` (every current tool is ManagerAPI-tier). V2 adds `NotificationReplyBackend` (RemoteInput) and `A11yBackend`. Every `Tool` declares the action+tier it needs; tools never know which backend executed.

### 4.5 `ContextStore`

```kotlin
interface ContextStore {
    fun turn(): TurnContext
    suspend fun beginTurn(utterance: UserUtterance)
    suspend fun endTurn()
}

data class TurnContext(
    val utterance: UserUtterance,
    val startedAt: Long,
    val lastToolResult: ToolResult?,
)
```

**V1 impl: `TurnOnlyContextStore`** — in-memory, cleared on `endTurn()`. No persistence, no cross-turn state. Matches user's privacy stance for V1.

**Later impls** (not in V1 scope): `SessionContextStore` (sliding window), `LongContextStore` (encrypted prefs). Adding them touches zero `Planner`/`AgentRuntime` code.

## 5. EvalHarness — load-bearing, ships Phase 0

Without this, every planner change ships blind.

```
mitra/training/eval/
├── commands.yaml           # 50 starter cases, grows to 200+
├── EvalHarness.kt          # JVM-side runner against MockPhoneTools
├── runners/
│   └── PlannerEvalRunner.kt
└── results/
    └── 2026-06-08.json
```

`commands.yaml` format:

```yaml
- id: 0001
  utterance: "turn on the flashlight"
  gold:
    - tool: toggle_flashlight
      args: { state: on }
  language: en
- id: 0027
  utterance: "silence everything for 2 hours"
  gold:
    - tool: set_dnd
      args: { until_minutes: 120 }
  language: en
- id: 0103
  utterance: "open whatsapp and turn on do not disturb"
  gold:
    - tool: open_app
      args: { name: whatsapp }
    - tool: set_dnd
      args: { until_minutes: -1 }
  language: en
  multi_step: true
```

Multi-step gold is an ordered list. Order match is part of scoring in Phase 2; in Phase 0 only single-step cases are scored (since `SingleShotPlanner` doesn't reliably chain).

Harness runs the real `Planner` against the real `LiteRtBrain` (or a fixture for CI without device). Scores tool-name match + arg shape match. Asserts state-after where possible (e.g., `set_dnd → ringer == silent`). CI step gates PRs on no regression.

50-command starter set is the Phase 0 deliverable. Curated by hand from V1 tool surface + chit-chat negatives.

## 6. Phasing

Each phase has a single concrete exit. No phase exists without a seam payoff.

**Implementation plan scope:** the plan that follows this spec covers **Phase 0 only**. Phases 1–4 each get their own spec → plan cycle once Phase 0 lands and their prereqs are met. Do not bundle phases — Phase 0 has no demo win and bundling it with Phase 1 will pressure cutting corners on the foundation.

### Phase 0 — Foundation (load-bearing, no demo-visible change)

- Declare all 5 seam interfaces.
- Refactor `AgentLoop` → `AgentRuntime` + `SingleShotPlanner` + `TurnOnlyContextStore`.
- Migrate every existing tool to declare its `AutomationAction` + tier; `ManagerApiBackend` executes all.
- Build `EvalHarness` + 50-cmd starter set.
- CI step: run eval on every PR, fail on regression.
- All existing V1 tools and the current chat UI keep working through the refactor.

**Exit:** all 13 existing tools pass eval through the new runtime; no UI behavior change visible.

### Phase 1 — Invocation surface

- `QuickSettingsTileSource` impl + manifest entry.
- `AssistantRoleSource` impl (VoiceInteractionService) — registers Mitra as system assistant.
- `PowerKeySource` impl where OEM exposes assistant remap; gracefully no-op elsewhere.
- Lock-screen entry point that pipes utterance into `AgentRuntime` without unlocking.
- ADR documenting which intents/services are required and why.

**Exit:** on a real device, long-press home → mic open → utterance → action fires, all from any screen including lock.

### Phase 2 — Plan-then-execute + chain UX

- Swap `SingleShotPlanner` → `PlanThenExecutePlanner` (interface unchanged).
- `AgentRuntime` gains: per-step status stream, abort, replan-on-step-failure.
- Chain confirm UX — designed against real plans, not hypotheticals:
  - Confidence ≥ threshold + all steps Reversible → bulk pre-confirm card.
  - Any Irreversible step → per-step gate at that step (chain pauses, resumes on user decision).
  - Persistent Stop button throughout.
- Eval set extended with multi-step gold plans (target 100 cases).

**Exit:** "book uber + set DND" type chains run end-to-end with one confirm, abortable, replanning on tool failure. Multi-step eval ≥ 70%.

### Phase 3 — Wake word

- `WakeWordSource` impl. KWS engine: openWakeWord ported to TFLite (FLOSS) — Porcupine ruled out (proprietary).
- Opt-in toggle in Settings, default OFF. Battery + mic perm warnings shown explicitly.
- AAPM detection: if Advanced Protection blocks always-on mic, toggle disables itself with a clear message.
- No new planner/runtime work — wake word is purely another `InvocationSource`.

**Exit:** "Hey Mitra" → utterance → AgentRuntime, with toggle off by default and verified to actually disable when toggled off.

### Phase 4 — V2 automation

- `NotificationReplyBackend` (RemoteInput) impl.
- `A11yBackend` impl via `MitraAccessibilityService` (M6 scope from current plan).
- `HierarchicalPlanner` for app-internal flows.
- WhatsApp reply via RemoteInput is the first proof-of-tier-routing.

**Exit:** WhatsApp reply works via NotificationReplyBackend without ever invoking A11y; A11y kicks in only for apps without RemoteInput surface.

## 7. Data flow — happy path

```
[InvocationSource fires] → UserUtterance
   ↓
UI calls AgentRuntime.run(utterance) and subscribes to Flow<RuntimeEvent>
   ↓
AgentRuntime owns turn lifecycle:
   ContextStore.beginTurn(utterance)
   plan = Planner.plan(utterance, ContextStore.turn())
   emit PlanReady(plan)
   ↓
(if plan.confidence ≥ threshold AND all steps Reversible/None)
   UI shows bulk confirm card → user Confirm → AgentRuntime.resume(Approve)
   ↓
for each step in plan:
   if step.sideEffect == Irreversible:
       emit GateRequested → UI modal → AgentRuntime.resume(decision)
   ToolRegistry.dispatch(step) via AutomationBackend
   emit StepCompleted
   AuditLog.append(name, sideEffect, outcome)
   ↓
emit Done(summary)
ContextStore.endTurn()
```

Caller never touches `ContextStore` directly. `AgentRuntime.run` is the only entry; turn boundaries are its responsibility. This keeps `Session`/`Long` impls free to attach extra hooks without breaking callers.

## 8. Error handling

- Tool throws → `StepCompleted(Failure)` → Planner.replan called with failure context → emit `Replan` event → continue.
- Planner returns empty Plan → emit `Done("nothing to do")`, surface as friendly chat reply.
- AutomationBackend.supports returns false for every backend → fail step with "no surface available", do not silently try lower tier.
- User aborts mid-chain → emit `Failed(AbortedByUser)`, AuditLog records abort, no partial state cleanup attempted (V1 — tools are responsible for their own atomicity).
- Gate timeout (user walks away) → step fails with `GateAbandoned`, chain stops, no further steps execute.

## 9. Testing strategy

- **Unit:** every seam interface has a fake. `AgentRuntime` tested against `FakePlanner` + `FakeToolRegistry` + `FakeBackend` covering: happy chain, abort, replan, gate, all-failures.
- **Eval (Phase 0+):** `commands.yaml` runs against real `SingleShotPlanner` + `MockPhoneTools` (no device). CI-gated.
- **Instrumentation (device-only):** one smoke per `InvocationSource` impl — fires utterance, asserts AgentRuntime received it.
- **Privacy invariant test (existing):** continues to gate `AuditLog.Entry` fields. Extended to also assert no `ContextStore` impl persists to disk in V1.
- **Backend tier test:** assert dispatcher always picks highest-tier available; if a tool needs RemoteInput but only ManagerApi is registered, dispatcher errors loudly.

## 10. Open questions (resolve during Phase 0)

- Plan-confidence threshold for bulk-vs-per-step confirm — needs eval data to pick. Phase 0 ships with placeholder `1.0` (always bulk for SingleShotPlanner) and `confidence_threshold` becomes a build-time const in Phase 2.
- Power-key assistant remap reliability — varies wildly by OEM. ADR needed listing tested devices.
- KWS engine choice deferred to Phase 3 brief. openWakeWord is current frontrunner; Vosk-small is backup.

## 11. What this buys long-term

- AgentLoop refactor happens once. Three planner generations swap behind it.
- AutomationBackend tier system means an a11y nerf degrades the feature, doesn't kill the project. R-002 mitigated.
- ContextStore seam keeps the ambient/proactive door open without paying its cost in V1.
- EvalHarness from Phase 0 means every model/planner/tool change has a numeric verdict.
- Wake word becomes one source impl when it lands, not a system rewrite.

## 12. Honest costs

- Phase 0 is 2–3 weeks of refactor + labeling with no demo-visible win. Skipping it is exactly what burns the trajectory.
- Eval set needs ongoing curation. 50 → 200 cases is real human labeling work.
- State machine harder to debug than straight loop. Pays back at chain length ≥ 3.
- Every new tool now declares an `AutomationAction` + tier — small ongoing tax.
- `VoiceInteractionService` registration requires manifest entries that may complicate F-Droid packaging — ADR needed.
