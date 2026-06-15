# Mitra Agentic Loop — Design (P1)

**Status:** Approved 2026-06-15 (chat brainstorm).
**Implementation status:** Shipped 2026-06-15 in commits `07e5156..c99245c`. Implementation plan: [docs/superpowers/plans/2026-06-15-agentic-loop.md](../../plans/2026-06-15-agentic-loop.md). Manual test log: [docs/research/2026-06-15-agentic-loop-manual-test.md](../../../research/2026-06-15-agentic-loop-manual-test.md). 12 unit tests passing; 8/8 manual scenarios pass on Realme CPH2401.
**Plan task:** none directly — this is the V2 planner work foreshadowed in [plan.md](../../../plan.md) right-now task list and the M5/M6 PlanThenExecutePlanner row.
**Owner:** @warpirate
**Scope tag:** P1. P2 (cross-turn memory + proactive clarification) is a separate spec.

## TL;DR

Mitra moves from one-shot tool emit to a multi-step agentic loop within a single turn. Brain reasons → emits tool → dispatcher runs → feeds result back → brain reasons again → repeat → final reply. Same turn boundary as today, just N tools instead of 1.

Covers: composition (draft body from intent), chaining (multiple tools per utterance), reflection-on-failure within turn, tone (mirror user mood in one short clause).

**Out of scope:** cross-turn memory (referent resolution across utterances), proactive clarification before the first tool, lazy permission grants via a new pattern. Those land in a P2 spec.

## Why

Current V1 brain emits ONE tool call per turn via `SingleShotPlanner`. That blocks the eight scenarios the user enumerated on 2026-06-15:

1. **Compose** — "ask blanta to come over" should draft *"hey, can you swing by?"*, not paste the user's instruction verbatim into the SMS body. Today the brain copies user text into `body` byte-for-byte (that was the deliberate prompt rule for `make_call`; for `send_sms` it backfired).
2. **Chain** — "find blanta's number then text her on my way" should call `query_contacts` then `send_sms` in one turn, two cards, two confirms. Today the brain emits one tool and stops.
3. **Multi-tool single goal** — "quiet for meeting" should call `set_dnd(on)` + `set_ringer_mode(silent)`. Two cards.
4. **Reflect on fail within turn** — "call mom" with three Moms in contacts should let Mitra ask *"Mom (mobile) or Mom (work)?"* and retry without a fresh user utterance.
5. **(P2)** Cross-turn referent: "what's blanta's number" → "text her hi".
6. **Tone** — "ugh tell blanta i'm not coming" → *"alright, sent."* Not the current flat *"You sent a message to blanta with the content..."*.
7. **(P2)** Smart clarify before tool: "text the boss" with no "boss" contact → ask *"who's the boss?"* before emitting a tool call.
8. **Compose with side-arg** — "remind me to take pills at 9" should call `set_alarm(9, 0)` AND draft a label *"Pills"* (when an alarm-label arg lands).

P1 fixes 1, 2, 3, 4, 6, 8. P2 will fix 5 and 7.

## Architecture

### Loop

```
User utterance
  │
  ▼
IntentParser ──(match)──► dispatch single step ──► Done
  │ no match
  ▼
LiteRtBrain.chatStream(user) ──► collect emissions
  │
  ├── text chunk → RuntimeEvent.Speaking
  │
  └── toolCall → emit PlanReady(single step)
                  │
                  ▼
                ConfirmationGate (if Irreversible)
                  │ approve
                  ▼
                backend.execute(step)
                  │
                  ▼
                LiteRtBrain.sendToolResult(name, result-as-map)
                  │
                  └─► back into "collect emissions" loop
                       up to STEP_CAP iterations
                  │
                  └─► (no further toolCall) → final text → Done(summary = lastText)
```

Cancellation, brain JNI errors, and step-cap exhaustion route to `RuntimeEvent.Failed` with friendly reasons. CancellationException propagates structurally; LiteRT-LM conversation is left intact (the bug fixed in commit `973ede3` from this same chat session — ChatScreen mounted across nav, no mid-turn scope kill).

### Components touched

| File | Change |
|------|--------|
| `app/src/main/kotlin/com/mitra/inference/LiteRtBrain.kt` | Add `fun sendToolResult(toolName: String, result: Map<String, Any?>): Flow<BrainTurn>`. Builds a `Content.ToolResponse` message, calls `conversation.sendMessageAsync(...)` with it, streams next emissions. System prompt rewritten — see §System prompt below. Tool descriptions for `send_sms` rewritten — see §Tool descriptions. |
| `app/src/main/kotlin/com/mitra/agent/AgentRuntime.kt` | Reworked to own the loop. Pre-brain `IntentParser` shortcut kept. Calls `brain.chatStream` / `brain.sendToolResult` directly. `RuntimeEvent.PlanReady` keeps its current `Plan` shape but is emitted per tool call, each Plan containing a single `PlannedStep` (this preserves the ChatScreen handler — it already drops the streaming bubble and adds one ActionCard per PlanReady). `Done.summary` carries the final brain reply text. |
| `app/src/main/kotlin/com/mitra/agent/SingleShotPlanner.kt` | Deleted. Logic merged into AgentRuntime. |
| `app/src/main/kotlin/com/mitra/agent/IntentParserPlanner.kt` | Kept (no-brain fallback when model load fails). |
| `app/src/main/kotlin/com/mitra/MainActivity.kt` | `buildRuntime` lambda simplified — no more `planner` construction; AgentRuntime takes `brain` directly. The `brainReady = false` branch still falls through to `IntentParserPlanner` for the no-model path. |
| `app/src/main/kotlin/com/mitra/ui/ChatScreen.kt` | Already handles streaming bubble → card drop on `PlanReady`. New: between cards, the next streaming bubble re-uses the same pattern. The `Done` MitraMsg insertion path stays. Step counter and "tools so far" UI is NOT added in P1 (defer to P2 if users want it). |
| `app/src/test/kotlin/com/mitra/agent/AgentRuntimeTest.kt` | New scenarios — see §Tests below. |

### Step cap

`STEP_CAP = 5` tool calls per turn. Defined as a const at top of AgentRuntime.kt. Crossing it: dispatch is skipped, brain receives no further results, AgentRuntime emits `RuntimeEvent.Failed("hit the tool limit — let me know what else you need")` then Done. This is a hard backstop against runaway loops, NOT a target — most turns end at 1–2 tools.

### Confirm gate

Hybrid policy (Reversible auto, Irreversible per-step modal) is preserved as-is. Each step's confirm card is independent; tap Cancel on step K sends `{"cancelled": true}` as the tool result so the brain can finish gracefully (typically with *"okay, didn't send"*).

### Audit

Each tool execution records `(toolName, sideEffect, ok)` exactly as today. No body content, no recipient, no contact name. `AuditLog.Entry` whitelist test stays. Multi-step turns produce N audit entries; that is fine and intentional.

## System prompt

Three new sections added to `LiteRtBrain.systemInstruction`. The existing LENGTH / VOICE / TOOL ARGS / INDIAN ENGLISH FILLERS / NEVER NARRATE / CALL-SMS sections stay (with the CALL-SMS section updated to point at the new COMPOSE rules).

### COMPOSE

> When the user gives you an **instruction** like "tell X ...", "ask X ...", "text X to do ...", "send X a message saying ...", YOU draft the body. Never paste the user's instruction verbatim into the body argument.
>
> - "ask blanta to come over" → body: "hey, can you swing by?"
> - "tell mom I'll be late" → body: "running late, see you soon"
> - "text dad i'm not coming" → body: "can't make it today, sorry"
>
> The ONLY case where you copy verbatim is when the user wraps the body in quotes:
>
> - "text mom \"on my way\"" → body: "on my way"
>
> Default tone: casual friend, contractions OK, lowercase OK. If the user wrote formally, mirror that. Keep bodies under 160 characters when possible (one SMS segment).

### TONE

> After a tool fires, you may say ONE short clause acknowledging the user's mood, then stop. Read the user's register from the utterance:
>
> - vent / swear / frustration → "alright, sent." / "done." / "okay."
> - neutral request → "sent." / "done." / "set."
> - happy / casual → "nice, sent." / "got it."
>
> Never moralize. Never lecture. Never ask if there is anything else. Hard rules from VOICE still apply — no emoji, no exclamation marks, no em dashes, no greetings.

### AGENTIC

> Each turn you may call up to 5 tools before you must end with a final reply.
>
> After a tool runs you receive its result. Decide next:
>
> - **Success** → either call another tool toward the same goal, or finish with a 1-clause reply.
> - **Failure** → decide based on the error message: retry with different args (e.g. ambiguous contact → ask user which one), skip and continue, or finish honestly ("couldn't reach mom, line was busy"). Do NOT silently re-emit the same call.
> - **{"cancelled": true}** → user cancelled at the confirm card. Acknowledge briefly ("okay, didn't send") and stop. Do not retry.
>
> If you hit the 5-tool cap, the runtime stops you. Plan economically — one tool per goal, two at most for chains. Three or more only when the user asked for it explicitly.

## Tool descriptions

`send_sms.body` description in `inference/LiteRtBrain.kt`:

> **Before:** "the exact message text the user wants sent, byte-for-byte. Strip the verb + recipient (so 'text mom on my way' has body 'on my way'). Never paraphrase or summarise the body."
>
> **After:** "the composed message body to send. YOU draft it from the user's intent — see the COMPOSE system rule. The user said \"ask blanta to come over\"? body is \"hey, can you swing by?\", NOT \"ask blanta to come over\". Only copy the user's exact words when they wrapped the body in quotes."

`make_call` unchanged.

Other tools (`set_alarm`, `set_dnd`, `start_timer`, etc.) unchanged. They are mechanical; composition does not apply.

## Failure modes

| Failure | Surfaced to user |
|---------|------------------|
| Brain returns no tool + no text | `Failed("brain went quiet — try again")` |
| `sendToolResult` JNI error | `Failed("I lost my train of thought. Mind sending that again?")` (same string as AgentRuntime's planner-fail recovery in commit `973ede3`) |
| Step cap hit | `Failed("hit the tool limit — let me know what else you need")` + audit `step_cap_hit` event |
| User cancels at confirm card | Brain receives `{"cancelled": true}`, replies. Final `Done`. No `Failed`. |
| Cancellation mid-turn (e.g. user types new message) | Structured propagation. Conversation kept alive. No retry. |

## Tests

### Unit (`AgentRuntimeTest`)

New cases:
- **single-step still works** — IntentParser hit dispatches one step, no brain involvement. Regression guard.
- **two-step chain success** — brain emits tool A, AgentRuntime dispatches + feeds back, brain emits tool B, dispatches + feeds back, brain emits final text. Verify two `PlanReady` + two `StepCompleted` + one `Done`.
- **chain with cancel at step 2** — first step runs, second goes to gate, test cancels. Brain receives `{"cancelled": true}`, emits acknowledgement text. `Done` summary = acknowledgement.
- **fail-then-replan within turn** — brain emits tool A, dispatcher returns Failure, brain emits clarifying question (text only), turn ends with `Done`. The clarifying question is the `Done.summary`.
- **step cap hit** — brain emits 6 tool calls. Verify only 5 dispatch, then `Failed("hit the tool limit ...")` fires.
- **brain JNI error during sendToolResult** — mock brain throws. Verify `Failed("I lost my train of thought ...")` matches the existing fix's string exactly.

### Brain (Fake)

A `FakeBrain` is added under `app/src/test/kotlin/com/mitra/inference/FakeBrain.kt`. It implements the same shape (`chatStream`, `sendToolResult`) using a queue of scripted emissions. Tests inject it. Real `LiteRtBrain` is not exercised in unit tests (engine init is too heavy and non-deterministic).

### Manual (on device)

The 8 scenarios from §Why are walked manually on the Realme phone (the same CPH2401 used in the chat session). Pass criteria: each scenario produces the described UX without errors. Recorded as a quick test log in `docs/research/2026-06-15-agentic-loop-manual-test.md` (file created during implementation, not now).

## Migration & rollback

This replaces `SingleShotPlanner`. Rollback = revert the AgentRuntime + LiteRtBrain + MainActivity changes; SingleShotPlanner returns. Spec is gated behind no feature flag — the simpler, safer behavior IS the new loop, and a flag would just add an untested code path. Per the Mitra no-over-engineering principle in `~/.claude/projects/d--AIOS/memory/mitra-no-overengineering.md`: simplest path, no half-finished implementations.

## Risks

- **Gemma 4 E2B reliability on multi-step tool loops.** Untested in this codebase. Mitigation: step cap is the hard backstop; manual test catches the common bad behaviors (loop, hallucinate args, refuse to stop). If it is unacceptable, the rollback above takes the codebase back to V1 single-shot.
- **System prompt regression on simple tools.** Tightening composition for `send_sms` may shift behavior for `make_call` / `set_alarm` etc. There is no automated eval yet (M0 task #6 in plan.md week-0 spikes is still open). Mitigation: manual regression on the 5 most-used commands ("turn on flashlight", "5 minute timer", "set alarm 7am", "open youtube.com", "what's mom's number") in the same test session.
- **Latency.** CPU-only backend takes 1–3s warm per inference pass. A 3-step chain is 5–9s end to end. The streaming-text bubbles between cards keep the UI alive so it does not feel frozen. No further latency mitigation in P1.
- **AuditLog growth.** Multi-step turns mean more `AuditLog.Entry` records. The whitelist test does not care about volume; storage is in-memory only today. No mitigation needed.

## Open questions

None. All Q1–Q4 in the brainstorm session were answered before writing.

## What this design does NOT do (P2 / later)

- Cross-turn referent ("text her") — needs ContextStore that survives `endTurn`.
- Proactive pre-tool clarification — needs the brain to ask before emitting any tool call. The system prompt could nudge it, but the eval to validate is non-trivial.
- Action card "edit body before send" — needs an editable Compose text field on the card. Tracked as a P2 UX nit.
- Plan-preview UX (show the whole chain in one card before any tool runs) — explicitly rejected in Q3.
