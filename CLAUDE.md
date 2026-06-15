# Mitra — Claude Code Project Context

> **Note:** "Mitra" (Sanskrit/Telugu for "friend") is a working codename. Find-and-replace before going public if you want something else.

This file is the canonical context for AI coding assistants working on this project. Read it before doing anything. Read it again when you're confused.

---

## What this project is

Mitra is an open-source, fully on-device AI agent for Android. It replicates the agentic phone-control capabilities of the Gemini mobile app (hardware control, alarms, messages, app automation, multi-step workflows) without sending any user data to a remote server. The model runs locally; it reads local data locally; it acts on the device locally.

**One-line invariant:** If a feature would require user data to leave the device, it does not ship.

## Stack

- **Language:** Kotlin
- **Platform:** Android (minSdk 26, targetSdk current stable)
- **Inference runtime:** **LiteRT-LM** (Google's actively-maintained on-device LLM runtime; native tool-calling + constrained decoding) on the LiteRT XNNPACK/GPU delegates. **Do NOT use the MediaPipe LLM Inference API — it is deprecated for Android.** The runtime sits behind a model-agnostic interface so LiteRT-LM / llama.cpp-GGUF are swappable (llama.cpp is the FLOSS-buildable fallback for an F-Droid-main build).
- **Model (single brain):** **Gemma 4 E2B** (`litert-community/gemma-4-E2B-it-litert-lm`, ~2.59 GB, Apache 2.0, downloaded at first run). It **both chats and emits native tool calls** — the model decides + acts. Runs on the **CPU backend** (mid-range SoCs have no usable NPU; the Mali GPU corrupts numeric tool args). Reasoning is curbed via the system prompt (Gemma has no `/no_think`). Set in `inference/ModelRegistry.kt`.
  - A 270M **FunctionGemma** router was tried and dropped — too weak (it narrated about tools instead of emitting structured calls). E2B does both roles. A deterministic `IntentParser` (`agent/Router.kt`) is kept only as a fallback net.
- **Action surface:** AccessibilityService, Intent system, Android Manager APIs (Camera, Bluetooth, Audio, Telephony, Wifi), Content Providers
- **Build:** Gradle (Kotlin DSL)
- **UI:** Jetpack Compose
- **Tests:** JUnit5 for unit, Espresso + UIAutomator for instrumentation

## Repo layout

Packages marked **(planned)** don't exist yet — they're added when the milestone that needs them lands. Don't create them empty. CLAUDE.md describes reality first, plan second.

```
mitra/
├── app/                              # Android application module
│   ├── src/main/kotlin/com/mitra/
│   │   ├── agent/                    # AgentRuntime + Planner + ContextStore + InvocationSource + IntentParser
│   │   ├── automation/               # AutomationBackend tier system + ManagerApiBackend
│   │   ├── inference/                # LiteRT-LM model hosting + ModelDownloader
│   │   ├── tools/                    # ONE tool per file + ToolRegistry (declares AutomationTier)
│   │   ├── safety/                   # ConfirmationGate + AuditLog (M2)
│   │   ├── ui/                       # Compose UI (chat, onboarding, download)
│   │   ├── accessibility/            # (planned, Phase 4) AccessibilityService impl behind AutomationBackend
│   │   ├── intents/                  # (planned, Phase 2) Intent dispatch helpers (Deeplink tier)
│   │   └── providers/                # (planned, M1) Content Provider wrappers
│   ├── src/test/                     # Unit tests (JUnit 4 today)
│   └── src/androidTest/              # (planned) Instrumentation tests
├── training/                         # (planned, M3) eval set + datasets
├── models/                           # (gitignored) downloaded model files
├── docs/                             # ADRs, retros, risk register
├── CLAUDE.md                         # ← you are here
├── README.md
├── PRD.md
├── ARCHITECTURE.md
├── plan.md
├── CONTRIBUTING.md
├── LICENSE
└── .gitignore
```

## Build & run

```bash
# Build debug APK
./gradlew :app:assembleDebug

# Install on a connected device
./gradlew :app:installDebug

# Run unit tests
./gradlew :app:test

# Run instrumentation tests on a connected device
./gradlew :app:connectedAndroidTest

# Lint (privacy invariants are enforced here — see safety/lint/)
./gradlew lintKotlin

# Format
./gradlew formatKotlin
```

**You need a physical Android device** for development. The emulator doesn't handle AccessibilityService reliably and the LiteRT XNNPACK delegate behaves differently on emulated hardware. After install, grant Accessibility permission via Settings → Accessibility → Mitra. On Android 13+, you'll have to go through "restricted settings" first.

## Coding conventions

- **One tool per file.** Each tool lives in `app/src/main/kotlin/com/mitra/tools/` as a single file containing its schema, its implementation, its side-effect classification, and a reference to its tests. The file's name matches the tool name.
- **Tools never reach across modules.** Each tool gets a `ToolContext` (with accessors to the managers it needs) injected. No singletons, no global state, no other-tool imports.
- **All side-effectful tools route through `safety/ConfirmationGate.kt`.** Non-negotiable. The model never executes a destructive action without it passing through the gate.
- **No `GlobalScope`.** Use structured concurrency. Each tool runs in `ToolScope`.
- **No network calls.** Hard rule. The lint rules block `java.net.*`, `okhttp3.*`, `retrofit2.*`, and the `INTERNET` permission outside the model-downloader allowlist. The one exception is `inference/ModelDownloader.kt`, which is the only file allowed to touch the network and is feature-flagged off after first-run completion.
- **Kotlin official style**, enforced by `ktlint`. Run the formatter before pushing.

## What NOT to do

These are listed not because they're tempting, but because each one would kill the project:

- **Do not add a cloud fallback for any reason, however well-intentioned.** The project's entire pitch dies the moment data leaves the device. If a task can't be done locally, it doesn't ship.
- **Do not log user content.** Message bodies, contact names, location, screen contents, query text — none of it. Logs may contain tool names and success/failure outcomes only. The lint config has a custom rule (`NoUserContentInLogs`) that flags string interpolation into log calls from suspicious sources.
- **Do not add analytics, telemetry, or crash reporting.** Not Crashlytics, not Firebase, not Sentry. If a contributor proposes one, point them at this section.
- **Do not call `Runtime.exec()` or shell out.** AccessibilityService is the action surface, not ADB. Shelling out is a privilege we don't have and a maintenance burden we don't want.
- **Do not commit model files.** They live in `models/` which is gitignored. Reference them by SHA in `inference/ModelRegistry.kt`.
- **Do not introduce a tool without a clear "Use this when …" description and an `IntentParser` fallback pattern.** The model selects tools by their descriptions (see the imported `tool-calling-tutor` skill) — a vague description means the model won't call it. (V1 has **no fine-tuned router** — E2B does zero-shot tool-calling — so there is no fine-tuning dataset to maintain yet.)

## How to add a new tool

1. Create `app/src/main/kotlin/com/mitra/tools/<ToolName>.kt`.
2. Implement `class <ToolName>(ctx: Context) : Tool` — `name`, `sideEffect`, and `fun execute(args: Map<String, Any?>): ToolResult`.
3. Set the side-effect class: `SideEffect.None` (auto-runs), `Reversible`, or `Irreversible`. Non-`None` shows a Confirm/Cancel **action card** before running.
4. Register it in `tools/ToolRegistry.kt`.
5. Declare it to the model in `inference/LiteRtBrain.kt` `PhoneTools` as an `@Tool` method whose description is phrased **"Use this when the user wants …"** (per the `tool-calling-tutor` skill), with distinct boundaries — this is what makes the LLM emit the call.
6. Add a matching pattern to `agent/Router.kt` `IntentParser` (the deterministic fallback net).
7. Add a unit test (e.g. extend `IntentParserTest`).
8. Open a PR with the `new-tool` label.

## Tool contract (the bit Claude needs to internalize)

```kotlin
enum class SideEffect { None, Reversible, Irreversible }

interface Tool {
    val name: String
    val sideEffect: SideEffect                       // None auto-runs; others gate behind a Confirm card
    fun execute(args: Map<String, Any?>): ToolResult // args come from the model's tool call (or IntentParser)
}

sealed interface ToolResult {
    data class Success(val message: String) : ToolResult
    data class Failure(val message: String) : ToolResult
}
```

What the **model** sees is the `@Tool` / `@ToolParam` annotations on the matching method in `inference/LiteRtBrain.kt` `PhoneTools` (LiteRT-LM auto-generates the schema from them). The `Tool` implementation above is dispatcher-side — `AgentRuntime` (via `ManagerApiBackend`) maps a model-emitted call to it by `name`.

## Privacy invariants

These are the rules every PR must satisfy. The lint that enforces them at build time is
**still TODO** (tracked in M0). Until then, reviewers enforce them by hand.

1. No `java.net.*`, `okhttp3.*`, `retrofit2.*` imports outside `inference/ModelDownloader.kt`.
2. No `Log.*` call with a non-constant string argument that could carry user content.
3. No Crashlytics / Firebase / Sentry / Mixpanel / Amplitude package imports anywhere.
4. No `Runtime.exec()` or `ProcessBuilder` anywhere.
5. AndroidManifest must declare only permission-sensitive entries that have an ADR.
6. `safety/AuditLog.Entry` fields are whitelisted (test enforces); never add a free-text field.

## Current milestone

See [plan.md](plan.md) for the milestone-by-milestone breakdown. Always link work to a milestone — if you're writing code that doesn't fit a current milestone, stop and ask.

## Keep the docs honest — non-negotiable

Code and docs drift only because no one notices. Stop the drift inside the working session, not later. **Before ending any session that landed code, perform this checklist. Skipping it counts as not finishing the work.**

1. **plan.md M1 / M2 / Mx checklists** — for every file you touched in this session, ask: "is there an unchecked `- [ ]` item that this code shipped?" Tick it. Add a short trailing note (`shipped YYYY-MM-DD` + one-line of what's notable). If you shipped something not in any checklist, add a new ticked row in the right section, don't leave it floating.
2. **plan.md right-now tasks** — if a task at the top of the file just got done, strike it (`~~...~~`) or rewrite the list. If priorities shifted because of what you discovered (e.g. an unexpected blocker, a new dependency), re-order. Never leave the right-now list reflecting last week's reality.
3. **Spec status footer** — when the spec at `docs/superpowers/specs/YYYY-MM-DD-foo-design.md` lands its implementation, add a `Status: shipped YYYY-MM-DD (commit <sha>)` line near the top. The spec stays — it's the audit trail for why we built it this way — but its status field tells future readers it's not aspirational anymore.
4. **Inline `## Right-now tasks` note** — when you ticked something in plan.md, also restate what *replaces* it as the new front of the queue. The right-now list should always end on a task that is genuinely up next, not on something that's already done.
5. **ADR (`docs/adr/NNNN-foo.md`)** — if the session made a decision a future contributor will second-guess (picking ACTION_CALL over ACTION_DIAL, swapping a runtime backend, dropping a code path), write a 1-page ADR. ADRs are append-only; numbered in order; never overwritten.
6. **Risk register (`docs/risks.md`)** — if a session surfaced a new risk (e.g. "WhatsApp UI changes break our a11y mapping every quarter"), add it. If it closed one, mark it closed with the resolution.
7. **`PRD.md` capability tiers** — when a session ships a *user-visible capability* (not just an internal refactor), check whether the tier or feature description in `PRD.md` still matches reality. Update the row. PRD.md is product truth — if `make_call` shipped behind ConfirmationGate, the PRD section on telephony should reflect that, not the pre-implementation aspiration.
8. **`ARCHITECTURE.md` component contracts** — when a session changes a contract (new interface method, new event, new tier in `AutomationBackend`, new sealed-class variant), update the matching section in `ARCHITECTURE.md`. If you added `LiteRtBrain.sendToolResult`, ARCHITECTURE.md's brain section gets a line.
9. **`docs/design/*.md`** — touched any UI behavior? Cross-check `action-cards.md`, `voice.md`, `permissions.md`, etc. If the confirm card now shows "Blanta — +91 …" instead of just "Blanta", `action-cards.md` says so. Design docs are the source of truth for designers reviewing the build.

This is a checklist you run *before* declaring "done", same way you run the test suite. The cost is 3 minutes per session. The cost of skipping it is a `plan.md` that looks like fiction within two months.

The single counter-example: pure exploration sessions where nothing landed and nothing was decided. Even then, if you read enough of the code to notice something stale, fix the stale line on your way out.

## Where to find what

- **Product vision and capability tiers:** [PRD.md](PRD.md)
- **System architecture & component contracts:** [ARCHITECTURE.md](ARCHITECTURE.md)
- **Execution plan and current milestone:** [plan.md](plan.md)
- **Architecture decision records:** `docs/adr/`
- **Open risks:** `docs/risks.md`
- **Contributing guide:** [CONTRIBUTING.md](CONTRIBUTING.md)
- **Design source of truth — consult before any UI work:**
  - Tokens (colors, type, spacing, motion, haptics): `docs/design/tokens.md`
  - Voice + microcopy: `docs/design/voice.md`
  - Action card pattern library (silent / toast / modal × 5 states): `docs/design/action-cards.md`
  - Permission grant choreography (per-permission flows): `docs/design/permissions.md`
  - Onboarding journey: `docs/design/onboarding.md`
  - Screen inventory + state matrix + Mermaid flow: `docs/design/screens.md`
  - Accessibility baseline (WCAG 2.2 AA, TalkBack, 200% font, RTL): `docs/design/a11y.md`
- **Competitive teardown** (UX patterns from Gemini / Krutrim / Sarvam / etc.): `docs/research/competitive-teardown.md`
- **Latest a11y audit findings** (rolling — review before doing UI work): `docs/design/a11y-audit-2026-06-05.md`

## Working with Claude Code in this repo

- Before generating code, scan the existing tool implementations in `app/src/main/kotlin/com/mitra/tools/` — copy the shape from a similar one rather than improvising.
- When adding a tool, write its "Use this when …" `@Tool` description AND its `IntentParser` fallback pattern in the same PR. A tool the model can't reliably call isn't done.
- If a request seems to require a network call, stop and re-read the privacy invariants. The right answer is usually "this feature doesn't ship" or "do it via an Intent."
- When in doubt about whether something is reversible, classify it as `Irreversible`. Better an extra confirmation modal than a silent destructive action.
