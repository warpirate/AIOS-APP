# Mitra — Definitive Viability Assessment

> Source: deep multi-agent research workflow (12 dimensions, 32 agents, adversarial fact-check against current 2025–26 primary sources). Run date 2026-06-04.

**Question:** Can Mitra be built to completion as an open-source, fully on-device Gemini alternative for Android WITHOUT being halted mid-project by an external change (Android policy, licensing, or infeasible performance)?

**Verdict: GO, WITH CONDITIONS.** Across all 12 verified dimensions, **not a single external/platform risk is genuinely existential for Mitra's actual audience and distribution model.** Every "scary" Android mechanism (Play accessibility ban, AAPM accessibility revocation, Restricted Settings, developer verification, Play Protect install blocks) is one of: Play-only (Mitra is off-Play), F-Droid/session-installer-exempt, user-overridable, or absent on the de-Google ROMs (GrapheneOS/Calyx/Lineage) that are Mitra's primary audience. The owner's fear — being "shut down in between" by a little external tweak — is **largely unfounded for the platform/policy axis.** The one truly existential, high-likelihood risk is internal and human: **solo-maintainer attrition.**

The project documents (PRD, ARCHITECTURE, risks.md) are already unusually well-aligned with the findings: model-agnostic inference layer, single allowlisted network file, sideload-native distribution, Shizuku fallback, confirmation gate, and a stated "keep model assumptions out of the agent layer" rule. The conditions below are mostly *re-pins and explicit reframings*, not redesigns.

---

## 1. Overall call

| | |
|---|---|
| **Call** | **go-with-conditions** |
| **Can it be completed without external halt?** | **Yes** — for V1 unconditionally; for V2 conditionally (accessibility is an enhancement layer, not a dependency). |
| **Dominant threat** | Solo-maintainer burnout (internal), NOT Android policy (external). |
| **Most important reframe** | V1 must be fully functional with the AccessibilityService DISABLED. If that holds, no Android accessibility tightening can halt the project — it can only cap a V2 feature. |

The four binding conditions:
1. **Architect V1 to stand alone without accessibility** (Intent/Manager-API/ContentProvider floor; V2 a11y is a swappable enhancement).
2. **Re-pin the stack:** LiteRT-LM (not the deprecated MediaPipe LLM Inference API); Gemma 4 E2B Apache-2.0 planner; FunctionGemma router as a runtime-downloaded gated asset, never bundled.
3. **Fix the zero-exfil proof method** (no-INTERNET build flavor + reproducible builds + tracker scans + mitmproxy, not StrictMode).
4. **Structurally de-risk the bus-factor** (community-runnable eval pipeline + NLnet grant + fork-ability).

---

## 2. Existential risks (sorted by severity × likelihood)

### EX-1 — Solo-maintainer attrition (the real one). Severity: existential · Likelihood: high
This is the only cell that is both existential and high-likelihood. Of 36,000+ OSS projects studied, ~90% lose all core devs at least once; **70% of detachments occur in the first three years** — Mitra's exact phase — and only ~27% of detached projects ever recover a core dev ([arxiv.org/html/2412.00313v1](https://arxiv.org/html/2412.00313v1)). Burnout is one of several drivers (competing life priorities and loss of interest rank higher; [sonarsource.com](https://www.sonarsource.com/blog/maintainer-burnout-is-real/)). The verification downgraded the *literal claim* from existential to **high** because the project itself can survive via fork even if the owner leaves — Apache-2.0 + 100% on-device means **there is no proprietary backend to switch off** (the Mycroft-died / OpenVoiceOS-survived lesson, [en.wikipedia.org/wiki/Mycroft_(software)](https://en.wikipedia.org/wiki/Mycroft_(software))). It stays at the top because it is the highest realistic threat to *this owner completing this build*.

**Mitigation/hedge:** make the fine-tuning treadmill community-runnable (in-repo, reproducible synthetic-data generator + full tool dataset + eval-in-CI); tag 25-40% issues good-first-issue (+13-21% contributors, [daily.dev](https://daily.dev/blog/open-source-contributor-onboarding-10-tips)); 2-tier governance; pursue NLnet NGI Zero/Mobifree grants (5K-50K EUR, even-month deadlines, fit Mitra's profile exactly — [nlnet.nl/commonsfund](https://nlnet.nl/commonsfund/), [nlnet.nl/mobifree/eligibility](https://nlnet.nl/mobifree/eligibility/)).
**De-risks when:** by month 2 an external contributor can run the eval pipeline end-to-end from the README and merge a tool unaided; an NLnet application is submitted.

### EX-2 — Default-on / non-overridable accessibility kill on certified devices. Severity: medium (downgraded from existential) · Likelihood: low
If Android ever made the AAPM-style accessibility revocation default-on and non-overridable device-wide, V2's universal-automation pillar would die on Mitra's target devices. **Verification firmly downgraded this:** Android 17's revocation lives only inside **opt-in, off-by-default** Advanced Protection Mode ([developer.android.com/about/versions/17/behavior-changes-17](https://developer.android.com/about/versions/17/behavior-changes-17) lists no such default-on change); AAPM *also blocks sideloading*, so an AAPM user can't install Mitra anyway (the restrictions are coupled, [eff.org](https://www.eff.org/deeplinks/2025/06/googles-advanced-protection-arrives-android-should-you-use-it)); GrapheneOS only stubs `AdvancedProtectionManager` and uses granular per-app controls ([grapheneos.org/features](https://grapheneos.org/features)). No primary source signals a default-on device-wide kill in the 2-3yr window.

**Mitigation/hedge:** V2 accessibility behind a swappable interface; V1 Intent/Manager-API floor always works; detect AAPM via `AdvancedProtectionManager.isAdvancedProtectionEnabled()` + `QUERY_ADVANCED_PROTECTION_MODE` and degrade gracefully; keep Shizuku/ADB as an alternative automation path; prioritize community-controlled ROMs; monitor A18/A19 betas.
**De-risks when:** week-1 on-device confirmation that GrapheneOS/Calyx/Lineage strip or neuter AAPM; and every V1 feature is proven to work with accessibility off.

### EX-3 — On-device tool-calling runtime instability / deprecation. Severity: medium (downgraded from high) · Likelihood: medium
The agent loop sits on this runtime, so an API rug-pull or unfixable structured-output corruption could stall the core. **Reality:** the MediaPipe LLM Inference API for Android/iOS *is* deprecated (so building on it = guaranteed rework — [deprecation banner](https://developers.google.com/edge/mediapipe/solutions/genai/llm_inference)), but the replacement **LiteRT-LM is actively maintained (~monthly releases), GPU+NPU-capable, production-shipped by Google (Chrome/Chromebook/Pixel Watch), and its Kotlin tool-calling docs name FunctionGemma-270m-it as a tested example** ([github.com/google-ai-edge/LiteRT-LM](https://github.com/google-ai-edge/LiteRT-LM)) — i.e. converging on Mitra's exact design. The genuine cost is engineering pain: field reports show ~71% tool-call pass rate, a ~3-string-arg ceiling, and GPU numeric corruption of JSON args.

**Mitigation/hedge:** target LiteRT-LM from day one; keep the runtime behind ARCHITECTURE.md's existing model-agnostic interface so MediaPipe / LiteRT-LM / **llama.cpp-GGUF** are swappable (llama.cpp is also the F-Droid-buildable path per Maid/SmolChat); do JSON tool-call parsing/validation in Kotlin app code; cap tools at <=3 args; prefer CPU backend for arg fidelity; pin runtime versions.
**De-risks when:** week-1 on-device test of FunctionGemma-270m-it via LiteRT-LM Kotlin tool-calling shows >=90% schema-valid output with app-side validation.

---

## 3. Risk register (all 12 dimensions), sorted by severity × likelihood

| # | Risk | Sev (corrected) | Likelihood | Status |
|---|---|---|---|---|
| 1 | **Solo-maintainer attrition** (EX-1) | existential→high | high | The real threat; internal, no OS workaround |
| 2 | **Restricted Settings gate** on browser-downloaded GitHub APKs (a11y/SMS toggles greyed) | high→medium | high | F-Droid session-install exempt; one-time "Allow restricted settings" tap |
| 3 | **AAPM auto-revokes a11y** from Mitra (non-isAccessibilityTool); privacy users may enable it | high | medium | Opt-in, recoverable; GrapheneOS doesn't run Google's AAPM |
| 4 | **270M router across 25+ tools** below 80% target | high→medium | medium | Mitigable via tool-subset routing + constrained decoding + fine-tune (distil-labs hit 90% on 14 tools) |
| 5 | **MediaPipe native .so not FLOSS-buildable** → blocks F-Droid MAIN | high | high | Real for MediaPipe path; swap to llama.cpp/GGUF for F-Droid main |
| 6 | **Indic (Telugu/Tamil) tool-calling** below target | high→medium | medium | Data problem, not capability ceiling; Hindi V1, Dravidian beta |
| 7 | **Runtime instability/deprecation** (EX-3) | high→medium | medium | LiteRT-LM active; abstract behind interface |
| 8 | **Catastrophic forgetting** as tools grow (~16% MMLU drop) | high→medium | high→med | Cheap fix: data-mixing/rehearsal, per-domain LoRA, functional tokens |
| 9 | **Play Protect malware misclassification** (a11y+SMS = trojan profile) | high | medium-high | Hard for stock-Android-India GitHub-APK users; F-Droid/de-Google exempt |
| 10 | **Thermal throttling** on sustained/multi-step inference | high→medium | high→med | V1 is intermittent/short — not the stress profile; pace + CPU backend |
| 11 | **6GB RAM co-residency / LMK kills** | high→medium | medium | Use E2B not E4B; lazy-load planner; 8GB recommended tier |
| 12 | **INTERNET permission forfeits OS-enforced zero-exfil** | high→low | high→low | GrapheneOS Network toggle + no-INTERNET build flavor restore the guarantee |
| 13 | **Cannot auto-become default assistant** (ROLE_ASSISTANT requestable=false) | high→low | high | Manual one-time Settings set; gesture works after |
| 14 | **NNAPI deprecated / NPU LLM path is flagship-8-series-only** | high→medium | high | CPU(XNNPACK)/GPU baseline; NPU is flagship bonus, never promised |
| 15 | **Developer Verification mandate** (Sep 2026+, certified devices) | medium | high | de-Google ROMs out of scope; free limited-dist account; ADB exempt |
| 16 | **Single high-profile misuse** (wrong SMS/call in front of media) | project-killing | low | Confirmation gate non-negotiable; conservative defaults; red-team |
| 17 | **F-Droid model-download anti-feature flags** (gated FunctionGemma) | medium | medium | Opt-in consent screen; Apache-2.0 Gemma 4 avoids it; IzzyOnDroid fallback |
| 18 | **Civil/TCPA liability** for owner-initiated SMS/calls | low | low | TCPA targets telemarketing; Apache-2.0 disclaimers; per-action confirm |

---

## 4. Fact-corrections to the plan

1. **RUNTIME (highest priority):** The PRD context names "MediaPipe LLM Inference API" as a primary inference path. That API is officially **DEPRECATED** for Android/iOS — Google's docs carry a deprecation banner directing migration to LiteRT-LM. ARCHITECTURE.md's "LiteRT" is correct as the lower layer, but LLM inference + tool-calling must specifically target **LiteRT-LM** (Apache 2.0, native Tool Use + constrained decoding; FunctionGemma-270m-it is a tested example). Remove any reliance on the MediaPipe LLM Inference API.
2. **LICENSE SPLIT:** README's blanket "Gemma models under the Gemma Terms of Use" is version-dependent. **Gemma 4 (E2B/E4B, ~Apr 2 2026) is Apache 2.0** — first OSI-licensed Gemma — and `litert-community/gemma-4-E2B-it-litert-lm` is ungated (2.59GB). **FunctionGemma** (Gemma 3 270M-based) **is** Gemma ToU + the HF repo is **gated**. So: planner = Apache 2.0; router = Gemma ToU + gated. Pin the exact Gemma 4 revision; archive its LICENSE/NOTICE.
3. **ZERO-EXFIL PROOF:** PRD's "verifiable via StrictMode + NetworkPolicy" is wrong — StrictMode is "not a security mechanism" and misses JNI/native traffic (exactly where LiteRT runs). Replace with: (a) a no-INTERNET build flavor (model sideloaded, app holds no INTERNET permission — the only true "impossible to exfiltrate" proof); (b) reproducible builds; (c) Exodus/SUSS tracker scans; (d) published mitmproxy + logging-VPN captures. StrictMode = dev tripwire only.
4. **MODEL NAMING:** All three named models are REAL and current. FunctionGemma 270M shipped Dec 18 2025 (288MB, ~0.3s TTFT, 126-154 tok/s on flagship; 58%→85% on Google's 7-tool "Mobile Actions" eval after fine-tune). Gemma 4 E2B/E4B shipped ~Apr 2 2026. **Mitra's V1 router loop ≈ Google's shipped "Mobile Actions" demo** — validating but NOT novel; the defensible novelty is the COMBINATION (full on-device + de-Google packaging + multi-step planning + routines engine + V2 universal automation). The 85% figure is for ~6-7 tools, NOT 25+ — do not assume it generalizes.
5. **ASSISTANT ENTRY POINT:** Replicating Gemini's hold-power/corner-swipe invocation is NOT auto-grantable — `ROLE_ASSISTANT` is `requestable=false` in AOSP; the user must set Mitra as assistant manually in Settings (after which the gesture routes to Mitra). Don't architect V1 around the assistant gesture; ship launcher icon + Quick Settings tile + one-time Settings deep-link.
6. **RAM FOOTPRINT:** "~2 GB model bundle / 2-4B effective" understates resident cost on 6GB. Gemma 4 E2B int4 ≈ 2.6-2.9GB; E4B Q4_0 ≈ 4.5GB. On 6GB with OS+app at 2-3GB, **E4B + router exceeds budget** (LMK kill risk); E2B + router is borderline. Default planner to **E2B**, keep only the 270M router warm, lazy-load/unload the planner; treat 8GB as recommended (not floor).
7. **OFF-PLAY PERMISSIONS (in our favor):** "must be default SMS/Phone handler", "QUERY_ALL_PACKAGES needs Google approval", "USE_EXACT_ALARM eligibility", "accessibility-for-automation ban" are all **Play policy, not AOSP** — none binds sideloaded Mitra. SEND_SMS/CALL_PHONE work for a non-default app; USE_EXACT_ALARM is install-granted off-Play; ACTION_SET_ALARM needs no exact-alarm permission. State this so the plan doesn't over-restrict itself.
8. **MULTILINGUAL SCOPE:** "140+ languages" is a pretraining claim, not a quality guarantee; there are ZERO published sub-1B Indic function-calling numbers. Hindi (high-resource) ≫ Telugu/Tamil (Dravidian, higher tokenizer fertility). Treat **Hindi as V1 target, Telugu/Tamil as planner-routed beta** — no parity promise. An Arabic FunctionGemma-270M fine-tune hit 76% (data-centric → it's a data problem, not a hard ceiling, but still <80%).
9. **FGS / SERVICE DESIGN:** A persistent agent must NOT run under a `dataSync` FGS (6h/24h cap on API 35+, can't start at boot). Use **`specialUse` FGS** for the persistent brain and a **microphone FGS** only during active capture. Hands-free wake-word parity with Gemini is not achievable for a non-privileged app (no DSP/SoundTrigger access; background mic is while-in-use since Android 11) — default to push-to-talk/tile/gesture. Also protects the <1%/hr idle battery target.

---

## 5. De-risking sequence (run BEFORE heavy build — surface fatal flaws in week 1, not month 6)

1. **WEEK 1 — Hardware truth test** (kills latency/RAM/thermal fantasy): run AI Edge Gallery's benchmark + a FunctionGemma-270m-it harness on a REAL Snapdragon 7 Gen 2 / 6GB device (all public numbers are flagship). Capture cold-start, warm p50/p95, peak RSS, and SUSTAINED throughput over 10+ back-to-back calls (find the thermal-throttle point). Pass bar: router single-step <1s warm; E2B planner loads without LMK kill on 6GB.
2. **WEEK 1 — Runtime + tool-calling reliability** (kills stack-drift): wire FunctionGemma-270m-it through **LiteRT-LM** (not MediaPipe) Kotlin tool-calling on-device; 20-command slice incl. multi-arg tools; measure JSON-valid rate, the ~3-string-arg ceiling, GPU numeric arg corruption. Pass bar: ≥90% schema-valid with app-side validation.
3. **WEEK 1 — Custom-ROM policy test** (sizes the AAPM existential): on real GrapheneOS + CalyxOS + LineageOS, confirm (a) whether Google's AAPM accessibility-revocation is present or stubbed, (b) F-Droid session-installer installs avoid the Restricted-Settings a11y gate, (c) a third-party VoiceInteractionService can be user-set as assistant.
4. **WEEK 1 — Licensing/distribution dry-run** (kills the F-Droid surprise): download + archive the exact Gemma 4 E2B litert-lm artifact + LICENSE/NOTICE (confirm Apache 2.0); confirm FunctionGemma is gated → plan it as a runtime-downloaded asset (NOT bundled); verify the no-INTERNET build flavor concept (model via Storage/SAF, app holds no INTERNET permission).
5. **WEEK 1-2 — Build-from-source spike** (kills the F-Droid-main illusion): attempt a byte-reproducible Gradle+NDK build. If MediaPipe/LiteRT native `.so` blobs can't be built from FLOSS source (likely), accept GitHub Releases + IzzyOnDroid as primary and llama.cpp/GGUF as the F-Droid-main path. Reframe "F-Droid main" from launch-requirement to stretch goal.
6. **WEEK 2 — 200-command eval + multilingual reality** (kills the accuracy/scope fantasy): stand up the public eval harness + a Mitra-specific fine-tuning dataset for all 25+ tools. Measure router accuracy at deployed int4/int8, with tool-subset routing. Separate Hindi/Telugu/Tamil slices. Pass bar: English ≥80% on 200 commands; honest per-language targets.
7. **WEEK 2 — Sustainability bootstrap** (de-risks EX-1): publish the reproducible synthetic-data + eval-CI pipeline + a CONTRIBUTING flow a stranger can use to merge a tool unaided; submit an NLnet NGI Zero/Mobifree application before the next even-month deadline.

---

## 6. Resilience design — architecting so a "little tweak" cannot kill the project

Unifying principle: **every external dependency must have a fallback behind an interface, and the core value must never hard-depend on the most-threatened component.**

1. **Pluggable action backends (the a11y hedge).** V1 runs entirely on Intents + Manager APIs + Content Providers — *no accessibility*. V2 accessibility is one implementation of an `AutomationBackend` interface; Shizuku/ADB is a second. If Android nerfs a11y, V1 is untouched and V2 falls back to Shizuku. Formalize the interface so a backend swaps without touching the agent layer.
2. **Model-agnostic inference (the runtime + license hedge).** Extend ARCHITECTURE's "nothing above `inference/` touches LiteRT directly": the `generate()` interface must support **LiteRT-LM, MediaPipe, AND llama.cpp/GGUF**. This hedges runtime deprecation (EX-3), the F-Droid-main native-blob blocker (llama.cpp is FLOSS-buildable), and license risk (a Qwen/xLAM Apache/MIT router can replace FunctionGemma if Gemma terms turn hostile).
3. **Distribution fallbacks (the install hedge).** Primary: F-Droid client + Obtainium (session installers — exempt from Restricted Settings AND Play Protect). Secondary: signed GitHub Releases (document the one-time "Allow restricted settings" tap). Stretch: F-Droid MAIN (only via llama.cpp). Position F-Droid main as a stretch goal, not a launch requirement.
4. **Two-tier zero-exfil (the proof hedge).** Convenience flavor (holds INTERNET, single hardcoded-host downloader, GrapheneOS-Network-revocable) + flagship no-INTERNET flavor (model sideloaded, OS-enforced unexfiltratable). The latter is the artifact skeptics accept.
5. **Fork-ability as a survival feature (the maintainer hedge).** 100% on-device + Apache-2.0 + reproducible community-runnable pipeline = the project can outlive any single maintainer (OpenVoiceOS pattern). The single most important resilience property given EX-1.
6. **Feature-modular permissions (the malware-stigma hedge).** SMS-read / notification-listener as optional modules so the base APK doesn't carry the Play-Protect-flagged trojan signature; users opt into the flagged combo only when they enable those features.

---

## 7. Recommended concrete changes to PRD / ARCHITECTURE / plan

**README.md**
- Split the license line: "App code: Apache 2.0. Planner (Gemma 4 E2B): Apache 2.0. Router (FunctionGemma, Gemma 3-based): Gemma Terms of Use, downloaded at runtime, not bundled."
- State the off-Play permission win explicitly.

**PRD.md**
- V1 success criteria: replace "verifiable via StrictMode + NetworkPolicy" with the layered proof (no-INTERNET flavor + reproducible build + Exodus scan + mitmproxy capture).
- Add criterion: "V1 fully functional with AccessibilityService disabled."
- Reframe "F-Droid build approved" → "GitHub Releases + IzzyOnDroid at launch; F-Droid main is a stretch goal contingent on a FLOSS-buildable backend."
- Default the on-device planner to **E2B, not E4B**; 8GB recommended tier, 6GB soft floor.
- Multilingual: Hindi = V1 target; Telugu/Tamil = planner-routed beta (no parity promise).
- "What kills this project": elevate **solo-maintainer burnout** to #1; downgrade "AccessibilityService nerf" to "caps V2, not the project."

**ARCHITECTURE.md**
- Runtime: LLM inference + tool-calling targets **LiteRT-LM** (lower-layer LiteRT note is fine); explicitly NOT the deprecated MediaPipe LLM Inference API.
- Add a third pending ADR backend (llama.cpp/GGUF); formalize `AutomationBackend` (a11y / Shizuku / ADB) as a pluggable interface.
- Persistent agent service: **specialUse FGS** for the brain, **microphone FGS** only during capture; never dataSync.
- Add AAPM detection (`AdvancedProtectionManager`) + graceful-degrade-to-V1 to `safety/` or onboarding.
- Note the assistant entry point is a manual one-time Settings set (ROLE_ASSISTANT requestable=false).

**risks.md**
- R-008 Solo-maintainer bus-factor (existential/high) — community-pipeline + NLnet mitigation.
- R-009 Play Protect / malware misclassification (high/medium) — feature-modular-permissions mitigation.
- R-010 Developer Verification (medium/high) — complete free verification early; de-Google ROMs exempt.
- Update R-002 (AccessibilityService): re-rate to "caps V2, not project-killing"; add the AAPM/GrapheneOS-stub nuance.
- Update R-001 to include sustained-thermal and 6GB-co-residency tests, not just warm latency.

---

## 8. Bottom line

Mitra is **buildable to completion without external halt.** The platform-policy fears that motivated this assessment are real trends but, for an off-Play, F-Droid-distributed, de-Google-targeted, accessibility-optional app, every one is exempt, overridable, or absent on the target devices. The project will live or die on **execution and maintainer endurance**, not on an Android "little tweak." Run the week-1 de-risking sequence before heavy build, re-pin the four conditions, and the "shut down in between" scenario becomes a fork-able, fundable, technically-cornered risk rather than an open flank.
