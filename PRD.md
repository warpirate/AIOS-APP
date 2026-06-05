# Mitra — Product Requirements Document

**Status:** Draft v0.1
**Owner:** (you)
**Last updated:** project inception

---

## Vision

A fully on-device, open-source AI agent for Android that lets users control their phone, manage their data, and automate workflows through natural language — without any data leaving the device.

We exist because the major mobile assistants (Gemini, Siri, Bixby, Alexa) all phone home. Every command becomes a row in someone else's data warehouse. We make the same product, with a privacy posture they cannot match — because their business model depends on the data we refuse to collect.

## The user

Three concentric circles, in order of urgency.

**Primary (year 1):** Privacy-conscious Android users in the de-Google community — GrapheneOS, /e/OS, CalyxOS, LineageOS users, plus the broader "I sideload F-Droid" crowd. They've already chosen privacy over convenience; we give the convenience back.

**Secondary (year 1–2):** Power users who'd otherwise be on Tasker or MacroDroid. They want phone automation but don't want to write macros. Natural language is the upgrade.

**Tertiary (year 2+):** Users in low-connectivity regions where cloud assistants are unreliable — rural and tier-2/3 India, parts of Southeast Asia, anywhere LTE coverage is patchy. The offline-first design becomes a feature, not just a privacy posture.

## Problem statement

A user with an Android phone wants to:

- Control device settings by voice or text
- Send messages and place calls
- Automate multi-step tasks across apps
- Have an agent that knows their local context (contacts, calendar, location, recent activity)

The existing options are: (a) use Gemini/Siri/Bixby and accept that all of it is logged to a cloud provider; (b) write macros in Tasker and accept the friction; or (c) do it manually.

No current product delivers (a)'s experience with (b)'s privacy posture. Mitra is that product.

## Capability tiers

### V1 — Local agent, basic tier (target: 3 months)

Ship a working Android app that, fully offline, can:

- Toggle hardware: flashlight, wifi, bluetooth, DND, mobile data, airplane mode
- Adjust display: brightness, auto-rotate, screen timeout
- Adjust audio: volume per stream, mute, ringer mode
- Manage time: set/cancel alarms, start/stop timers, create calendar events via intents
- Open apps and deep-link into specific settings screens
- Make calls and send SMS to contacts resolved from the local address book
- Read device state: battery, location, current wifi, current cell
- Polish messages: take a rambling voice note, structure it into a clean message

**Success criteria for V1 release (0.1.0):**

- 80%+ task-success rate on a fixed 200-command evaluation set
- < 1 second from end-of-utterance to action initiated, for single-step commands, median
- Zero outbound network requests in production builds — proven by a layered method, not StrictMode alone (StrictMode is "not a security mechanism" and misses native/JNI traffic, exactly where the inference runtime lives): a **no-INTERNET build flavor** (model sideloaded, app holds no `INTERNET` permission → exfiltration is OS-impossible), reproducible builds, Exodus/SUSS tracker scans, and published mitmproxy / logging-VPN captures
- **V1 is fully functional with the AccessibilityService disabled** — the V1 tool surface uses only Intents, Manager APIs, and Content Providers. Accessibility is a V2 enhancement, never a V1 dependency, so no Android accessibility restriction can halt the project
- Runs on a Snapdragon 7 Gen 2-class device with 6GB RAM at acceptable latency (the brain is Gemma 4 **E2B**, not E4B)
- F-Droid build approved and shipped

### V2 — In-app automation (target: 6 months)

- AccessibilityService-driven UI automation: read the active screen, identify affordances, execute gestures
- Multi-step plans across tools (reply to that WhatsApp from my brother saying I'll be late by 20 minutes)
- Notification reading and reply
- Form filling
- Voice input as first-class entry (not a secondary mode)

**Success criteria for V2 release (0.2.0):**

- 60%+ task success on a 50-task multi-step / cross-app eval set
- Per-app reliability dashboard for the top 5 target apps (WhatsApp, Swiggy, Uber, IRCTC, Google Maps), tracked in CI
- Voice latency: < 1.5s end-to-end for V1-tier commands

### V3 — Power-user platform (target: 12 months+)

- Tool SDK: third-party developers can publish new tools as signed plugin modules
- User-defined workflows (record once, replay with parameter slots)
- Multimodal input (screenshot understanding via Gemma 4 E2B vision)
- Federated improvement: opt-in, fully local fine-tuning on the user's own command history

## Non-goals

- **iOS support.** The sandboxing model makes this kind of agent impossible without jailbreak. Acknowledge and move on.
- **Cloud fallback.** Not even opt-in. Every line of code that adds a network path is a future leak.
- **Replacement for Tasker.** Tasker users who like writing macros will keep using Tasker. We are the natural-language layer, not the rules engine.
- **Real-time conversation / chatbot.** We do task execution. There are better local models if a user wants a chat companion. We don't try to be both.
- **General-purpose LLM.** We don't run the model on long context, don't do creative writing, don't translate documents. We do agentic tool use, well.

## Constraints

- Must run on a device with 6GB RAM and a mid-range SoC (Snapdragon 7 Gen 2 or comparable Dimensity / Tensor). **The brain is Gemma 4 E2B (not E4B)** — E4B exceeds a 6 GB budget and risks low-memory-killer termination. 8 GB is the recommended tier; 6 GB is a soft floor.
- Must work fully offline after first-time model download
- Must be open source under Apache 2.0 (app code; the Gemma 4 E2B model is also Apache 2.0, downloaded at runtime)
- Initial distribution: signed APK on GitHub Releases + IzzyOnDroid. F-Droid main repo is a stretch goal (needs a FLOSS-buildable inference backend). No Play Store dependency.
- Must comply with Android 13+ restricted-settings flow for AccessibilityService

## Success metrics

Per release:

- Task-success rate, per tier, against a versioned eval set
- Median and p95 latency, per request class (single-step, multi-step, in-app)
- Active install count via F-Droid + manual APK downloads
- Number of community-contributed tools merged
- Number of fine-tuning examples in the public dataset
- Crashes per session (collected locally only, surfaced opt-in via "share this diagnostic file" — never auto-sent)

## What kills this project

Surface these now so we don't pretend later. Reordered by real severity × likelihood per the [viability assessment](docs/research/2026-06-04-mitra-viability-assessment.md):

1. **Solo-maintainer attrition — the real #1.** ~70% of OSS projects lose all core devs in their first 3 years (our exact phase), and most never recover one. This is the only genuinely existential, high-likelihood risk; no Android policy comes close. Mitigation: stay fork-able by design (100% on-device, Apache-2.0, no proprietary backend to switch off — the Mycroft-died / OpenVoiceOS-survived lesson); make the fine-tuning treadmill community-runnable (in-repo reproducible synthetic-data + eval-in-CI so any contributor can add a tool unaided); tag issues `good-first-issue`; pursue NLnet NGI Zero / Mobifree grants for funded runway.

2. **Tool-calling accuracy as the tool set grows.** V1 uses Gemma 4 E2B **zero-shot** (no fine-tuned router, so no per-tool dataset to maintain yet). The risk: as tools multiply, the model picks wrong / stops calling. Mitigation: clear "Use this when …" descriptions (the #1 lever), an eval set in CI that fails on accuracy regression, and the `IntentParser` net for the known commands. The classic fine-tuning treadmill only returns if we later add a fine-tuned router.

3. **The brain model becoming the bottleneck.** If Gemma 4 E2B is too weak or too slow for multi-step automation at acceptable latency, V2 stalls. Mitigation: track the open-weights landscape monthly; keep `inference/` model-agnostic (LiteRT-LM / llama.cpp swappable) so we can swap; consider a fast router in front of E2B if latency forces it.

4. **A single high-profile misuse.** Because the agent can send SMS / make calls / drive apps, a confidently wrong action in front of media kills trust. Mitigation: the confirmation gate is non-negotiable; conservative defaults; pre-release red-team round.

5. **AccessibilityService getting nerfed — caps V2, does NOT kill the project.** Reframed (was a top fear): every current restriction is Play-only, F-Droid/session-installer-exempt, user-overridable, or absent on the de-Google ROMs that are our core audience; Android 17's accessibility revocation lives only inside opt-in Advanced Protection Mode, which also blocks sideloading, so it can't reach our install base. Because V1 never depends on accessibility, a future nerf only caps a V2 feature. Mitigation: V2 accessibility behind a swappable `AutomationBackend` interface; Shizuku/ADB as an alternative path; detect AAPM and degrade gracefully.

6. **Play Protect malware misclassification.** The accessibility + SMS-read combo matches the Android banking-trojan profile; Play Protect could warn/block on stock-Android GitHub-APK installs in pilot regions (incl. India). F-Droid and de-Google devices are exempt. Mitigation: ship SMS-read / notification-listener as optional feature modules so the base APK doesn't carry the flagged signature.

## Open questions

- Wake-word strategy: gesture-triggered for V1; what's the right approach for V2?
- Do we ship voice input via Android's built-in `SpeechRecognizer` (varies by device) or bundle Vosk for guaranteed offline? Probably both, with detection.
- Multilingual scope: "140+ languages" is a pretraining claim, not a quality guarantee — there are zero published sub-1B-param Indic function-calling numbers. Plan: **Hindi as the V1 target** (high-resource, tractable); **Telugu/Tamil as planner-routed beta** (Dravidian, lower-resource, higher tokenizer fertility). Measure per-language before promising parity — and do not promise parity. (An Arabic FunctionGemma-270M fine-tune reached 76%, so it's a data problem, not a hard ceiling.)
- Battery profile under heavy use — needs first-week measurement on real devices to know if the architecture is viable.
