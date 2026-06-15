# Mitra — Execution Plan

This is the working plan, milestone by milestone. Plans are living — expect this file to change weekly during active development. A stale plan is worse than no plan, so date everything and review at the cadence below.

---

## How to read this

- **Milestones (M0 … M6)** are sequenced. Don't skip ahead.
- Each milestone has an **exit criterion** — a single concrete check that says "this is done."
- Checkboxes are the unit of work. One checkbox ≈ one PR.
- **Right-now tasks** at the bottom is the section that gets edited most often.

---

## Week 0 — De-risking spikes (run BEFORE heavy build)

From the [viability assessment](docs/research/2026-06-04-mitra-viability-assessment.md). Goal: surface any fatal flaw in week 1, not month 6. **Gate: M0 architecture is not "locked" until these pass (or a documented fallback is chosen).**

> **Mostly resolved.** The model/runtime spikes below were run in practice: the brain is **Gemma 4 E2B** (not FunctionGemma — Qwen3-0.6B and the 270M router were both too weak), on **LiteRT-LM** / CPU, with **E2B emitting native tool calls** + an `IntentParser` net. Latency is the known soft spot (~5–12 tok/s, see R-001). Where the checkboxes below say "FunctionGemma 270M", read "Gemma 4 E2B". Still open: build-from-source/F-Droid spike, full 200-cmd eval, sustainability bootstrap.

- [ ] **Hardware truth test** — benchmark FunctionGemma-270m + Gemma 4 E2B on a REAL Snapdragon 7 Gen 2 / 6 GB device (all public numbers are flagship). Capture cold-start, warm p50/p95, peak RSS, and sustained throughput over 10+ back-to-back calls (find the thermal-throttle point). Pass: router <1s warm; E2B loads without LMK kill on 6 GB.
- [ ] **Runtime + tool-call reliability** — FunctionGemma-270m through LiteRT-LM (NOT MediaPipe) Kotlin tool-calling; 20-command slice incl. multi-arg tools. Pass: ≥90% schema-valid JSON with app-side validation.
- [ ] **Custom-ROM policy test** — on real GrapheneOS + CalyxOS + LineageOS: is Google's AAPM accessibility-revocation present or stubbed? Do F-Droid session installs skip the Restricted-Settings a11y gate? Can a 3rd-party VoiceInteractionService be user-set as the assistant?
- [ ] **Licensing / distribution dry-run** — archive the exact Gemma 4 E2B litert-lm artifact + LICENSE/NOTICE (confirm Apache 2.0); confirm FunctionGemma is gated → runtime-download plan; verify the no-INTERNET build flavor (model via Storage/SAF, app holds no `INTERNET` permission).
- [ ] **Build-from-source spike** — attempt a byte-reproducible Gradle+NDK build. If the runtime's native blobs aren't FLOSS-buildable, accept GitHub Releases + IzzyOnDroid as primary and llama.cpp/GGUF as the F-Droid-main path. Reframe "F-Droid main" as a stretch goal.
- [ ] **200-command eval + multilingual reality** — stand up the public eval harness + a Mitra-specific dataset across all 25+ tools (not just Google's 7), with tool-subset routing. Separate Hindi / Telugu / Tamil slices. Pass: English ≥80%; honest per-language targets (Hindi V1, Dravidian beta).
- [ ] **Sustainability bootstrap** — publish the reproducible synthetic-data + eval-CI pipeline + a CONTRIBUTING flow a stranger can use to merge a tool unaided; submit an NLnet NGI Zero / Mobifree application before the next even-month deadline.

**Exit:** all seven pass (or a fallback is chosen and documented). Only then is M0 architecture locked.

---

## Milestones

### M0 — Skeleton (Week 1–2)

The "hello, world" of the project: an Android app that loads the model, takes text, and acts on a command. (Done — went past "print the call" to real execution.)

- [x] Bootstrap Android project (Kotlin, Compose, Gradle KTS) — builds, installs, runs on device
- [ ] Pin SDK / NDK / AGP versions; commit `.tool-versions`
- [x] Integrate **LiteRT-LM** (NOT the deprecated MediaPipe LLM Inference API); model loads on the CPU backend
- [x] Load **Gemma 4 E2B** via in-app download (resumable) — the single brain. (FunctionGemma 270M was tried and dropped as too weak; Qwen3-0.6B too.)
- [x] Tool-call reliability check (instrumented): E2B emits native tool calls reliably; Qwen3-0.6B only narrated. CPU backend (Mali corrupts numeric args)
- [x] Define the `Tool` interface + `ToolResult` (the model schema comes from `@Tool` annotations, not a serialized `ToolSchema`)
- [x] Ship a single hardcoded tool (`toggle_flashlight`) end-to-end — verified on a OnePlus Nord 2T (Android 14); the **LLM emits the call** itself
- [x] Manual test: "turn on the flashlight" toggles the torch on device
- [ ] Wire up basic lint config: privacy-invariant rules from CLAUDE.md
- [x] CI: build + Android lint + unit test on push (`.github/workflows/ci.yml`)

**Exit:** a developer can clone, build, install, and watch Gemma E2B turn a spoken command into a real device action on their phone. (Met — pending CI + lint.)

---

### M1 — Tool Surface, V1 Tier (Week 3–6)

Build the V1 tool list. Each tool is one PR, fully tested, with a clear "Use this when …" `@Tool` description + an `IntentParser` fallback pattern (no fine-tuning dataset in V1 — E2B is zero-shot).

**Hardware:**
- [x] `toggle_flashlight` — LLM-driven, verified on device
- [ ] `set_wifi`
- [x] `set_bluetooth` — direct toggle via BluetoothAdapter on API 30-, opens BT page on API 33+ (system restriction). `BLUETOOTH_CONNECT` runtime perm.
- [x] `set_dnd` — direct toggle via NotificationManager; `ACCESS_NOTIFICATION_POLICY` special perm bounce on first use.
- [ ] `set_mobile_data` — system-restricted; landing as `open_settings(mobile_data)` only.
- [ ] `set_airplane_mode` — system-restricted; `open_settings(airplane)` only.

**Display:**
- [x] `set_brightness` — Reversible; WRITE_SETTINGS grant flow bounces user to system page on first call
- [x] `set_auto_rotate` — Reversible via `Settings.System.ACCELEROMETER_ROTATION` (needs WRITE_SETTINGS).
- [x] `set_screen_timeout` — Reversible via `Settings.System.SCREEN_OFF_TIMEOUT` (needs WRITE_SETTINGS).

**Audio:**
- [x] `set_volume` — media stream done (`set_media_volume`); other streams TODO
- [x] `set_ringer_mode` — direct toggle via AudioManager; silent mode requires `ACCESS_NOTIFICATION_POLICY` on API 24+.
- [ ] `mute_all`

**Time:**
- [x] `set_alarm` — via AlarmClock intent
- [ ] `cancel_alarm`
- [ ] `list_alarms`
- [x] `start_timer` — `PackageManager.resolveActivity` probe + explicit-package fallback (Google Clock / Samsung / OnePlus / ColorOS) + ACTION_SET_ALARM last-resort; manifest `<queries>` declared
- [ ] `create_calendar_event` (via intent)

**Apps & navigation:**
- [x] `open_app(name|package_name)` — PackageManager fuzzy match (exact pkg → exact label → prefix → substring)
- [x] `open_url(url)` — via ACTION_VIEW. 2026-06-15: defensive URL validator added — refuses non-URL chat text ("lumos maximus") so the brain can't auto-open a junk URL.
- [x] `open_settings(panel)` — opens system settings page for the named panel; covers bluetooth, wifi, dnd, airplane, mobile_data, brightness, sound, display, location, battery, apps, storage.

**Telephony:**
- [x] `make_call` (gated by ConfirmationGate) — shipped 2026-06-15. ACTION_CALL direct dial behind Irreversible gate; CALL_PHONE runtime perm with in-app grant dialog. Confirm card shows "Blanta — +91 76718 90230" preview.
- [x] `send_sms` (gated) — shipped 2026-06-15. SmsManager direct send behind Irreversible gate; SEND_SMS runtime perm with in-app grant dialog. Multipart split for >160 char bodies. Body composition currently verbatim — full intent-to-body composition lands with the P1 agentic-loop work below.

**Device state reads:**
- [ ] `get_battery`
- [ ] `get_location` (with explicit foreground permission flow)
- [ ] `get_wifi_state`
- [ ] `get_active_network`

**Contacts:**
- [x] `query_contacts(name)` — three-tier fuzzy match (exact / starts-with / contains), all phones with type labels, capped at 5 results. Shipped 2026-06-11.

Each tool ships with: implementation + side-effect classification + a "Use this when …" `@Tool` description (so the LLM calls it) + an `IntentParser` fallback pattern + a unit test. (No fine-tuning dataset in V1 — E2B does zero-shot tool-calling.)

**Exit:** V1 tool surface feature-complete; all tools fire via the LLM (with the parser net as backup) and pass their unit tests in CI.

---

### M2 — Safety Layer (Week 5–7, parallel with M1)

- [x] `ConfirmationGate` pure decision function (`safety/ConfirmationGate.kt`) — None bypasses, everything else (incl. unknown) gates
- [x] Per-tool side-effect classification system (`SideEffect` enum on every `Tool`)
- [x] Audit log (tool name, side-effect class, outcome, timestamp; **no content**) — `safety/AuditLog.kt` + privacy invariant test on `Entry` field set
- [x] Gate consulted in UI (`ChatScreen.addCard`) and AuditLog written from `AgentLoop.runCall`
- [ ] Three confirmation modes in UI: silent (None — done), toast-confirm (Reversible — TODO, currently modal), modal-confirm (Irreversible — done)
- [ ] User settings: confirmation aggressiveness (strict / balanced / loose), with `strict` as the default
- [ ] Lint rule: every `SideEffect != None` tool must be registered with the gate
- [ ] Test: integration test that exercises every Irreversible tool and asserts the gate fires
- [ ] Debug-only audit history screen reading `agent.auditEntries()`

**Exit:** structurally impossible for the LLM to fire an irreversible action without the gate's involvement, verified by a passing integration test that tries to bypass it.

---

### M2.5 — Agentic Loop (Week 5–7, parallel with M2)

The V1 brain is single-shot: emit ONE tool call per turn, dispatch, done. M2.5 replaces that with a multi-step agentic loop within a single turn — brain reasons → emits tool → dispatcher runs → feeds result back via `Content.ToolResponse` → brain reasons again → repeat (cap 5 steps) → final reply. Unlocks intent-to-body composition (the user's biggest UX complaint after `send_sms` shipped on 2026-06-15: brain copied the user's instruction verbatim into the SMS body), tool chaining ("text mom and turn on dnd"), and mid-turn reflection on failure (call mom → 3 Moms found → ask which).

Spec: [docs/superpowers/specs/2026-06-15-agentic-loop-design.md](docs/superpowers/specs/2026-06-15-agentic-loop-design.md).

- [x] `LiteRtBrain.sendToolResult(name, resultMap)` — pushes `Content.ToolResponse` into the conversation, returns next streaming `Flow<BrainTurn>`. Shipped 2026-06-15 in `78c9936`.
- [x] Rework `AgentRuntime` to own the agentic loop directly; delete `SingleShotPlanner`; keep `IntentParser` shortcut as the deterministic pre-brain path. Shipped 2026-06-15 in `1c701f8` + `866c5d8` + `61a76c1`.
- [x] System prompt: new `COMPOSE` / `TONE` / `AGENTIC` sections. Shipped 2026-06-15 in `c99245c`.
- [x] `send_sms.body` description rewritten so the brain drafts the body, not copies user words verbatim. Shipped 2026-06-15 in `c99245c`.
- [x] Step cap = 5; cancel-from-confirm feeds `{"cancelled": true}` to brain. Shipped 2026-06-15 in `866c5d8`.
- [x] Unit tests: chain success, cancel mid-chain, fail-then-replan, step-cap hit, JNI error path. Shipped 2026-06-15 in `866c5d8` + `7a624f5` (12 passing tests total).
- [x] Manual on-device test of 8 scenarios. Walked 2026-06-15 on Realme CPH2401, 8/8 pass — see [docs/research/2026-06-15-agentic-loop-manual-test.md](docs/research/2026-06-15-agentic-loop-manual-test.md).

**Exit:** brain handles multi-step turns with composition + tone; the 8 manual scenarios pass on the dev device; `AgentRuntimeTest` covers the chain / cancel / replan / cap / JNI-error paths.

**P2 (separate spec, not M2.5):** cross-turn referent ("text her" after "what's her number"), proactive pre-tool clarification ("text the boss" → "who's the boss?"). These need a ContextStore that survives `endTurn` and a "should I ask before emitting?" rule in the system prompt — both deferred.

---

### M3 — Eval set + (deferred) fine-tuning (Week 6–8)

**Reframed:** V1 uses **Gemma 4 E2B zero-shot** for tool-calling (no fine-tuned router), so a fine-tuning *pipeline* is no longer V1-critical. What stays critical is an **eval set** to measure and guard tool-calling accuracy as tools grow. Fine-tuning a small fast router becomes relevant only if E2B latency forces it.

- [ ] Build the V1 eval set: ~200 commands with hand-labeled gold tool calls (covers all tools + chit-chat negatives)
- [ ] Eval harness: run the set against the on-device brain, score tool-name + args accuracy (assert post-execution state, not just name match)
- [ ] CI step: re-run eval on every prompt/model/tool change; fail PR if accuracy regresses
- [ ] (Deferred) Fork Google's Mobile Actions fine-tuning cookbook into `training/recipes/` + a dataset spec — only if we add a fine-tuned router later
- [ ] (Deferred) Fine-tuning runbook (`docs/runbooks/finetune.md`)

**Exit:** a versioned eval set proves the brain hits a target accuracy on V1 commands, and the eval runs in CI on every change.

---

### M4 — Voice Input (Week 8–10)

- [ ] Integrate Android's on-device `SpeechRecognizer` with offline locale pack
- [ ] Fallback to bundled Vosk model if device lacks offline recognition
- [ ] Trigger surface: long-press a quick-settings tile (V1) and configurable hardware button (V1.5)
- [ ] Defer always-listening / wake-word to V2 — too costly for V1
- [ ] End-to-end: voice → text → brain → tool dispatch
- [ ] Latency target: < 1.5s from end-of-utterance to action initiated

**Exit:** V1 demo works hands-free, with confirmation gate respected.

---

### M5 — V1 Polish & Release Candidate (Week 10–12)

- [~] Onboarding flow: welcome + in-app model-download screen built (matches the Stitch mockup); per-permission "why" flow still TODO. Not yet device-verified.
- [ ] Settings UI: confirmation mode, default location precision, locale, model variant
- [~] First-run model download: resumable in-app `ModelDownloader` + progress/pause/resume UI built (INTERNET perm + ADR 0008). TODO: SHA verification + an **ungated mirror URL** (Gemma 4 HF repo is gated) in `ModelRegistry.MODEL_URL`. Not yet device-verified.
- [ ] Error messages that don't leak content
- [ ] Battery profile: idle drain target < 1%/hour, active inference target < 5% per 10-minute burst
- [ ] F-Droid metadata package (`fastlane/metadata/android/`)
- [ ] GitHub Actions: signed APK build on tag push
- [ ] Privacy audit: outbound-traffic test in instrumentation suite (asserts zero packets except to model CDN, only during first run)
- [ ] User-facing README rewrite
- [ ] Beta with 10–20 testers from the privacy community

**Exit:** ship **0.1.0** on F-Droid and GitHub Releases.

---

### M5.5 — AutomationBackend abstraction (pre-V2 enabler)

Formalize the seam so V2 automation never hard-depends on one mechanism — this is what makes an Android a11y nerf "caps a feature" instead of "kills the project."

- [ ] Define a **tiered** `AutomationBackend` interface — the dispatcher picks the fastest tier per action: (1) structured API (`SmsManager`), (2) notification `RemoteInput` inline reply, (3) deep-link / `ACTION_SEND`, (4) accessibility gesture + text injection as the last-resort fallback (slow/brittle)
- [ ] Implement the **`NotificationReplyBackend`** (`RemoteInput` via `NotificationListenerService`) — the seamless, no-UI path for "reply to an incoming message"; this, not text injection, is the default for replies
- [ ] Assert no V1 tool requires a backend — the Intents / Manager-API / Content-Provider floor is always available
- [ ] AAPM detection (`AdvancedProtectionManager.isAdvancedProtectionEnabled()`) + graceful degrade-to-V1
- [ ] Stub a second backend (Shizuku/ADB) as a hidden, opt-in power-user path — never in any default flow

**Exit:** the AccessibilityService (M6) and a Shizuku path are interchangeable behind the interface; swapping backends doesn't touch the agent layer.

---

### M6 — AccessibilityService MVP (Month 4–6)

Start of V2 tier. Highest-risk phase; expect rework.

- [ ] `MitraAccessibilityService` implementation with manifest declarations
- [ ] UI tree dump → pruned semantic representation (drop invisible / no-content nodes, dedupe scroll containers, keep clickables and text)
- [ ] **Reply path via `RemoteInput`** (NotificationListenerService inline reply) — the fast, no-UI default for replying to incoming messages in any app
- [ ] Gesture execution via `dispatchGesture` (tap, long-press, swipe, scroll) — tier-4 fallback only
- [ ] Text injection into focused fields — tier-4 fallback; prefer `RemoteInput` reply where a notification exists
- [ ] First target app: WhatsApp — **Gemini-parity send UX** (confirm card in chat → silent send → WhatsApp UI never opens). Per 2026-06-15 web research, Gemini does this with AccessibilityService driving WhatsApp UI invisibly. We follow the same pattern. Layered: (a) `RemoteInput` for replies to recent incoming messages — silent + robust against WhatsApp UI updates; (b) accessibility-driven type-and-send for first-message-send where no notification exists; (c) deep-link `wa.me` prefill kept as a last-resort fallback only. Tier 0 / pure-deep-link is NOT a goal — the user explicitly asked for confirm-then-silent UX matching Gemini.
- [ ] Second target app: Swiggy or Zomato (search + display results)
- [ ] Multi-step planner using Gemma 4 E2B
- [ ] Per-app reliability dashboard
- [ ] Restricted-settings onboarding flow for Android 13+

**Exit:** 60%+ success rate on a 50-task in-app eval set across 3–5 named apps. WhatsApp reply works reliably enough to demo without rehearsal.

(Beyond M6: see the V3 section in [PRD.md](PRD.md).)

---

## Top risks (live)

Mirrored from `docs/risks.md`. Highest-priority three only:

1. **Solo-maintainer attrition (R-008).** The only existential, high-likelihood risk — above every Android-policy fear. → Bootstrap the community-runnable pipeline + an NLnet grant application in weeks 1–2; stay fork-able by design.

2. **On-device feasibility on mid-range (R-001).** Latency / RAM / thermal numbers are all flagship; runtime is now LiteRT-LM (MediaPipe deprecated). → Week-0 hardware truth test + tool-call reliability check.

3. **AccessibilityService — caps V2, not the project (R-002).** Downgraded: off-Play / F-Droid-exempt / de-Google-ROM-insulated, and V1 never depends on it. → `AutomationBackend` seam (M5.5) + AAPM detection.

---

## Cadence

- **Continuous:** keep the synthetic-data + eval-CI pipeline reproducible and the CONTRIBUTING flow stranger-runnable (R-008 bus-factor mitigation)
- **Weekly:** update milestone progress checkboxes
- **Biweekly:** review risk register
- **Per milestone exit:** write a short retro in `docs/retros/`
- **Monthly:** scan open-weights landscape; document any model worth swapping toward

---

## Right-now tasks

Updated 2026-06-15. M1 tool surface effectively complete: all V1 device-control + telephony tools shipped (`toggle_flashlight`, `set_alarm`, `start_timer`, `open_url`, `open_app`, `open_settings`, `query_contacts`, `make_call`, `send_sms`, `set_media_volume`, `set_brightness`, `set_dnd`, `set_ringer_mode`, `set_auto_rotate`, `set_screen_timeout`, `set_bluetooth`). Focus shifts to making the brain *agentic* (M2.5) and laying the AutomationBackend seam (M5.5) before the AccessibilityService work in M6.

1. **P2 brain work (next up after M2.5 shipped):** cross-turn `ContextStore` (turn 1 "what's blanta's number" → turn 2 "text her hi") + proactive pre-tool clarification ("text the boss" with no boss-contact → ask "who's the boss?"). Own design + plan; same shape as the agentic-loop spec/plan we just shipped.
2. ~~**Ship M2.5 agentic loop**~~ Shipped 2026-06-15 in commits `07e5156..c99245c`. Spec [docs/superpowers/specs/2026-06-15-agentic-loop-design.md](docs/superpowers/specs/2026-06-15-agentic-loop-design.md); plan [docs/superpowers/plans/2026-06-15-agentic-loop.md](docs/superpowers/plans/2026-06-15-agentic-loop.md); manual test log [docs/research/2026-06-15-agentic-loop-manual-test.md](docs/research/2026-06-15-agentic-loop-manual-test.md). 8/8 scenarios pass on device.
3. **M2 safety landed (in-flight):** `safety/ConfirmationGate.kt` + `safety/AuditLog.kt` + JUnit coverage exist. Next: wire AuditLog into a debug-only history screen behind a dev flag.
4. **Fix `start_timer` on OnePlus Nord 2T** — `ACTION_SET_TIMER` returns "no clock app". Add Google Clock package fallback, then a `setExactAndAllowWhileIdle` in-app timer as the last resort.
5. **CI + lint (M0 close-outs):** wire GitHub Actions `build + lint + unit test on push`. Add a minimal ktlint config; defer the custom privacy-invariant rules until M2 audit log is consumed by UI.
6. **Pin SDK / NDK / AGP** + commit `.tool-versions`.
7. **Hardware truth test** — still owed on a real SD7 Gen 2 / 6 GB device. Cold-start, warm p50/p95, sustained 10-call thermal point. Park results in `docs/research/`.
8. **WhatsApp Tier 1 path (post-M5.5 + M6):** the user wants Gemini-parity UX — confirm card → silent send → WhatsApp UI never opens. Per 2026-06-15 web research, that requires AccessibilityService for first-message-send (RemoteInput alone only covers replies). Lands as M6 milestone work; no shortcut available. Tier 0 deep-link prefill is explicitly NOT a goal — UX gap is too visible.
9. ~~**Add `query_contacts`**~~ Shipped 2026-06-11; see [docs/superpowers/specs/2026-06-11-query-contacts-design.md](docs/superpowers/specs/2026-06-11-query-contacts-design.md).
10. **Sustainability bootstrap** — stranger-runnable CONTRIBUTING flow + NLnet NGI Zero application before next even-month deadline. (R-008)
11. **Pick a public name** before the first public commit if `Mitra` isn't it.
