# Mitra — System Architecture

**Status:** Draft v0.1
**Last reviewed:** project inception

---

## Context

Mitra is an Android-native AI agent. The user speaks or types a request. A single local LLM (**Gemma 4 E2B**) decides what to do and emits a **native tool call**; a deterministic intent parser stands behind it as a fallback for the known commands. The tool call dispatches to an Android-native tool, which performs an action on the device. The result feeds back for multi-step plans, or terminates.

The entire loop runs on the user's device. There is no server. There is no cloud fallback. There is no telemetry.

## Goals

- **Latency:** single-step commands ≤ 1s from text input to action start, on a Snapdragon 7 Gen 2-class device.
- **Privacy:** no outbound network traffic in production builds, ever, except the one-time model download.
- **Extensibility:** new tools added by writing a single file.
- **Safety:** no side-effectful action fires without passing through the confirmation gate.
- **Battery:** idle drain ≤ 1%/hour; active inference burst ≤ 5%/10 min.

## Non-goals

- Real-time streaming conversation
- Long-context (>32K tokens) reasoning
- Server-side anything

## High-level diagram

```
                ┌──────────────────────────────────────────────────────────┐
                │                        UI Layer                           │
                │                  (Jetpack Compose)                        │
                └──────────────┬──────────────────────────┬────────────────┘
                               │                          │
                          text input                 voice input
                               │                          │
                               └────────────┬─────────────┘
                                            │
                                            ▼
                ┌──────────────────────────────────────────────────────────┐
                │                       Agent Loop                          │
                │                                                           │
                │   Brain (Gemma 4 E2B)   ─────►  emits a native tool call  │
                │             │                                             │
                │             │ (if it emits none, the deterministic        │
                │             ▼   IntentParser is tried as a fallback)       │
                │   IntentParser (rules)  ─────►  tool call or null         │
                └──────────────────────────┬───────────────────────────────┘
                                           │
                                       tool call
                                           │
                                           ▼
                ┌──────────────────────────────────────────────────────────┐
                │                  Tool Dispatcher                          │
                │      schema validation, side-effect classification        │
                └───────────┬─────────────────────────────────┬────────────┘
                            │                                 │
                  SideEffect.None                  SideEffect.Reversible / Irreversible
                            │                                 │
                            ▼                                 ▼
                ┌───────────────────┐             ┌───────────────────────┐
                │  Direct execute   │             │  Confirmation Gate    │
                └─────────┬─────────┘             │  (toast or modal)     │
                          │                       └──────────┬────────────┘
                          │                                  │
                          ▼                                  ▼
                ┌───────────────────────────────────────────────────────────┐
                │                    Tool Implementations                   │
                │                                                           │
                │  Hardware   │  Intents  │  Content Providers │  A11y      │
                │   Managers  │  System   │  (contacts, media) │  Service   │
                └───────────────────────────────────────────────────────────┘
                                           │
                                           ▼
                                  Result back to Agent Loop
```

## Module breakdown

### `agent/`
Hosts the orchestration loop. The single **Gemma 4 E2B brain** (in `inference/`) both chats and emits native tool calls — the model decides and acts. A deterministic **`IntentParser`** (`agent/Router.kt`) is a fallback net: if the LLM emits no tool call, the parser matches the known command phrasings (flashlight, alarm, timer, volume, open-url) so the action still happens.

Key files: `agent/AgentLoop.kt` (dispatches a tool call to the matching tool by name; exposes `runCall`, `sideEffectOf`, `parse`), `agent/Router.kt` (the `IntentParser`).

**Why one model, not a separate FunctionGemma router:** a 270M router was tried and was too weak — it *reasoned* about which tool to use but emitted prose instead of a structured call. E2B does reliable native tool-calling, so V1 uses one model for both chat and actions. Tool descriptions follow the imported `tool-calling-tutor` skill (phrased "Use this when the user wants …", distinct boundaries) — that is what makes the model trigger calls instead of narrating.

### `inference/`
On-device LLM hosting via **LiteRT-LM** (Google's actively-maintained runtime with native tool-calling + constrained decoding). **Do NOT use the deprecated MediaPipe LLM Inference API.** Owns the model lifecycle: lazy load on first command, warm-up after first load, evict under memory pressure. Exposes a low-level `generate(prompt: String, schema: ToolSchema): String` to the agent layer. **Nothing above this layer touches the runtime directly** — keep model and runtime assumptions out of the agent layer, so LiteRT-LM / llama.cpp-GGUF are swappable behind one interface (llama.cpp is the FLOSS-buildable path for an F-Droid-main build).

Single model: **Gemma 4 E2B** (~2.59 GB), loaded once at first launch and kept warm — `inference/LiteRtBrain.kt`. CPU backend (the mid-range dev SoC has no usable NPU, and the Mali GPU corrupts numeric tool args). Reasoning is curbed via the system instruction (Gemma has no `/no_think` switch — that's Qwen-only); any stray `<think>` is stripped before display. The model is set in `inference/ModelRegistry.kt`. Keep model/runtime assumptions out of the agent layer so LiteRT-LM / llama.cpp-GGUF stay swappable.

Also hosts `ModelDownloader.kt`, the *one and only* file in the project allowed to make network calls. Allowlist-locked at lint time. A **no-INTERNET build flavor** sideloads the model so the app holds no `INTERNET` permission at all — the OS-enforced proof of zero exfiltration.

### `tools/`
One file per tool. Each file contains the schema, the implementation, and the side-effect classification. Tools never depend on each other; if two tools need shared logic, the shared logic moves into `providers/` or `intents/`.

`ToolRegistry.kt` holds the registered tools; new tools must be registered there explicitly.

### `accessibility/`
Implementation of `MitraAccessibilityService` — **one implementation of a swappable `AutomationBackend` interface** (Shizuku/ADB is a second). V1 never depends on this; it is a V2 enhancement, so an Android accessibility restriction can only cap V2, never halt the project. The service detects Advanced Protection Mode via `AdvancedProtectionManager.isAdvancedProtectionEnabled()` and degrades gracefully with a calm explanation rather than failing silently. Exposes two interfaces to the tools layer:

- `readScreen(): SemanticTree` — returns a pruned representation of the active screen (visible clickables and text, with stable ids where possible)
- `performGesture(g: Gesture)` — taps, swipes, long presses; by coordinate or node reference

**This is the slowest, most update-fragile tier — the last resort, never the default.** Text injection + tap here is tier 4 in the action ladder below. For replying to an incoming message, prefer the `RemoteInput` notification path; for SMS, `SmsManager`. See "Action execution tiers".

The pruning step is critical: a raw accessibility tree can have 400+ nodes, most useless. The pruner drops invisible nodes, deduplicates scroll containers, and collapses chains of single-child wrappers. Pruned tree typically goes from ~400 nodes to ~30–80.

### `intents/`
Helpers for constructing common system intents (alarms, calendar, dial, sms, open URL). Tools that fire intents call into here rather than constructing intents inline. This is also where deep-link URI builders for known apps live.

### `providers/`
Wrappers around Content Providers: `ContactsContract`, `MediaStore`, `CalendarContract`. All read-only access to local data lives here. Permission checks happen at this boundary, not at the tool layer — so a tool implementation can assume permissions are satisfied or throw a predictable error.

### `safety/`
The confirmation gate, the action audit log, and the lint-enforced invariants live here. Also home to `LoggingAllowlist.kt` (the only place `Log.*` with non-constant strings is permitted) and the `NoUserContentInLogs` custom lint rule.

### `ui/`
Compose UI. Built: **onboarding** (welcome + in-app model download with progress/pause/resume) and the **chat screen** — streamed replies plus Gemini-style **action cards** (a card per tool call; Confirm/Cancel for side-effectful tools, auto-run for `SideEffect.None`). Theme/tokens in `ui/theme/`. Planned: settings, voice input, audit-log viewer.

## Action execution tiers (fast-path first)

"Sending a message" must feel instant. UI automation (dump tree → set text → tap send) is the SLOWEST and most brittle path, so it is the **last** resort, never the default. The dispatcher resolves each action down a capability ladder and uses the highest tier the situation allows:

1. **Structured system API** — e.g. `SmsManager.sendTextMessage()` for SMS. One call, instant, no UI. (Mitra's `send_sms` already uses this — it is *not* accessibility-driven, and is already as fast as Gemini's SMS path.)
2. **Notification inline reply (`RemoteInput`)** via `NotificationListenerService` — to REPLY to an incoming message in any messaging app (WhatsApp, Signal, etc.), fire the notification's own reply action. Sends through the app's reply pipeline with no UI driving and no screen dump. This is the "seamless, background" path for the common "reply to X" case — the thing that makes Gemini feel fast.
3. **Deep-link / `ACTION_SEND` intent** — pre-fills a target (e.g. `wa.me`); usually needs a final user tap because third-party apps don't expose an auto-send action. The right path for a NEW outbound message where no notification exists.
4. **AccessibilityService gesture + text injection** — the universal fallback for apps/flows none of the above reach. Slowest and most update-fragile; used only when tiers 1–3 can't do it.

These tiers are implementations behind the `AutomationBackend` interface (M5.5). The agent layer asks for an outcome ("reply to this message"); the dispatcher picks the tier. Honest limit: a *brand-new* message to a contact who hasn't messaged you has no notification to reply to, so it falls to tier 3 (pre-fill + tap) or tier 4 — this is true for Gemini too.

## Tool contract

Every tool exposes:

```kotlin
@Serializable
data class ToolSchema(
    val name: String,
    val description: String,            // What the model sees
    val parameters: JsonSchema,
    val sideEffect: SideEffect,         // None | Reversible | Irreversible
    val requiresAccessibility: Boolean,
    val requiresInternet: Boolean       // always false; lint-enforced
)

interface Tool {
    val schema: ToolSchema
    suspend fun execute(args: JsonObject, ctx: ToolContext): ToolResult
}

sealed class ToolResult {
    data class Success(val payload: JsonObject) : ToolResult()
    data class Failure(val code: ErrorCode, val message: String) : ToolResult()
    object NeedsConfirmation : ToolResult()
}
```

The model only ever sees `name`, `description`, and `parameters`. The other fields are dispatcher-side runtime concerns.

## Data flow: typical single-step command

1. User types "turn on the flashlight"
2. The UI sends the text to the E2B brain (`LiteRtBrain.chatStream`)
3. The model emits a native tool call `toggle_flashlight(on=true)` (if it emits none, the `IntentParser` fallback matches "flashlight")
4. `AgentLoop.runCall` looks up `toggle_flashlight` and runs it
5. `sideEffect = None` → the UI shows an action card that runs immediately (no confirm)
6. `ToggleFlashlight.execute()` calls `CameraManager.setTorchMode`
7. The card shows "✓ Done"

**Reality on the dev device (Dimensity 1300, CPU):** a few seconds — E2B decodes ~5–12 tok/s. The <1s flagship target needs an NPU path or a smaller router; not the current build.

## Data flow: typical multi-step command (V2 — not yet built)

This is the target for V2 (contact resolution + SMS aren't built yet). One model handles the chain:

1. User says "tell my brother I'll be late by 20 minutes"
2. The E2B brain needs contact resolution → emits a tool call
3. Brain emits: `{"name": "query_contacts", "args": {"relation": "brother"}}`
4. Dispatcher returns `[{"name": "Vamsi", "number": "+91…"}]`
5. Brain sees one match, emits: `{"name": "send_sms", "args": {"number": "+91…", "body": "I'll be late by 20 minutes"}}`
6. `send_sms` has `sideEffect = Irreversible` → ConfirmationGate intercepts
7. User sees modal: "Send SMS to Vamsi: 'I'll be late by 20 minutes'?" with Confirm / Cancel
8. On confirm, SMS sends via `SmsManager`
9. AgentLoop terminates with `Success`

**Target:** 1.5–2.5s including the confirmation modal display (most of which is human reaction time).

## Key decisions and trade-offs

| Decision | Alternatives considered | Why we chose this |
|---|---|---|
| Single Gemma 4 E2B brain (chat + native tool-calling) | Two-model router (FunctionGemma 270M) + planner | The 270M router was tried and was too weak — it narrated instead of emitting tool calls. E2B does reliable native tool-calling, so one model does both; a deterministic `IntentParser` is a fallback net. (Revisit a fast router only if latency forces it.) |
| AccessibilityService for in-app automation | Per-app integrations / WebViews / Shizuku / ADB | Universal, no per-app work, user-grantable; the only realistic option for a third-party app on Android |
| LiteRT-LM as the inference runtime (NOT the deprecated MediaPipe LLM Inference API) | ExecuTorch, MLC, MediaPipe LLM Inference (deprecated), llama.cpp | LiteRT-LM is Google's actively-maintained on-device LLM runtime with native tool-calling; its docs ship FunctionGemma-270m as a tested example. llama.cpp/GGUF is kept swappable as a fallback (and the only FLOSS-buildable path for an F-Droid-main build) |
| Apache 2.0 license | GPL / AGPL | Matches Gemma's license; allows community forks to ship freely; AGPL would scare off the de-Google distros we're targeting |
| Sideload + F-Droid as primary distribution | Play Store | Play accessibility-policy reviews are a structural threat; building on sideload avoids it. Play becomes a stretch goal, never a critical path |
| Single repo (monorepo) | Multi-repo | At our team size, monorepo wins. Revisit if we cross 5 active contributors |
| Compose for UI | XML views | Faster iteration, less boilerplate. Compose is mature enough for our needs |

## ADRs

Significant decisions get their own ADR in `docs/adr/`. Format: `NNNN-short-title.md`. Required sections: Context, Decision, Consequences, Alternatives, Status.

Pending ADRs:
- 0001 — Model serving runtime (LiteRT-LM vs ExecuTorch vs llama.cpp; MediaPipe LLM Inference rejected as deprecated)
- 0002 — License choice (Apache 2.0 code; Gemma 4 E2B brain Apache 2.0, runtime-downloaded)
- 0003 — Distribution strategy (GitHub Releases + IzzyOnDroid first; F-Droid main a stretch goal)
- 0004 — Confirmation gate UX
- 0005 — `IntentParser` as the deterministic fallback behind the LLM
- 0006 — Pluggable inference backend abstraction (LiteRT-LM / llama.cpp swappable)
- 0007 — Pluggable `AutomationBackend` (AccessibilityService / Shizuku / ADB)

## What we'd revisit as the project grows

- **Model swap.** Gemma 4 E2B is right for now; in 12 months the right model may be different (a faster small tool-caller, or a fine-tuned router). Keep the inference layer model-agnostic. Never let a model assumption leak into the agent layer.
- **Tool plugin system.** V1–V2 ships tools in-tree. V3 needs a plugin system so third parties can add tools without core PRs. The schema is already designed for this; runtime loading is the gap.
- **Local fine-tuning.** Eventually users should be able to fine-tune on their own command history, fully on-device. Not in V1–V2 scope, but architecturally don't preclude it.
- **Multi-user / profile support.** Currently single-user. If the project ever serves shared devices (kiosk, family tablet), the audit log and ConfirmationGate need scoping.
- **Fast router in front of E2B.** V1 leans on the E2B brain for tool-calling plus a deterministic `IntentParser` net. If latency on mid-range hurts, add a small fast router (e.g. a fine-tuned tool model) for the common single-step commands and reserve E2B for the rest.
