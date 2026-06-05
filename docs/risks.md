# Risk Register

Living document. Reviewed biweekly. Each risk has an owner, a likelihood, an impact, and a mitigation plan.

**Format:**
- **Likelihood:** Low / Medium / High
- **Impact:** Low / Medium / High / Project-killing
- Owner: who tracks the mitigation

---

## R-001 — On-device model latency on mid-range devices

**Likelihood:** High (confirmed slow) · **Impact:** Medium · **Owner:** (unassigned)

**Measured:** Gemma 4 E2B on the dev OnePlus Nord 2T (Dimensity 1300, CPU) decodes ~5–12 tok/s — a few seconds per turn, well over the sub-1s V1 target. Usable for turn-based "say it → it does it", not snappy. The trade for reliable native tool-calling (the smaller Qwen3-0.6B was fast but couldn't emit tool calls).

**Mitigation:**
- If latency hurts, add a **fast router in front of E2B** (a fine-tuned small tool model) for common single-step commands; reserve E2B for the rest
- Pursue an NPU path on supported SoCs; the `IntentParser` net already makes the keyword commands instant regardless of model speed
- Benchmark on more mid-range devices; measure cold-start, warm p50/p95, peak RSS, and **sustained throughput over 10+ back-to-back calls** (thermal-throttle point)
- Confirm **Gemma 4 E2B (~2.59 GB) loads + runs on 6 GB without an LMK kill** (not E4B)
- Document numbers in `docs/benchmarks/`

**Status:** Open. Architecture locked (Gemma 4 E2B + LiteRT-LM CPU); latency itself still untuned. Sustained-throughput benchmark on SD7 Gen 2 still owed.

---

## R-002 — AccessibilityService restriction trajectory

**Likelihood:** Low (for our audience) · **Impact:** Caps V2 only — NOT project-killing · **Owner:** (unassigned)

Re-rated down by the [viability assessment](research/2026-06-04-mitra-viability-assessment.md). Every current restriction is **Play-only** (we're off-Play), **F-Droid/session-installer-exempt**, **user-overridable**, or **absent on the de-Google ROMs** that are our core audience. Android 17's accessibility revocation lives only inside opt-in, off-by-default Advanced Protection Mode — which *also* blocks sideloading, so an AAPM user can't install Mitra in the first place; GrapheneOS only stubs `AdvancedProtectionManager`. Because **V1 never depends on accessibility**, any future nerf can only cap a V2 feature, never halt the project.

**Mitigation:**
- V1 floor uses only Intents / Manager APIs / Content Providers — prove every V1 feature works with accessibility DISABLED
- V2 accessibility behind a swappable `AutomationBackend` interface; Shizuku/ADB as an alternative path that needs no accessibility grant
- Detect AAPM (`AdvancedProtectionManager.isAdvancedProtectionEnabled()`) and degrade gracefully with a calm explanation
- Monitor A18/A19 AOSP betas for Restricted-Settings expansion

**Status:** Open, monitor (downgraded from project-killing)

---

## R-003 — Indic-language understanding (Telugu/Tamil/Hindi…)

**Likelihood:** Medium · **Impact:** Medium · **Owner:** (unassigned)

Gemma 4 supports 140+ languages as a pretraining claim, but on-device tool-calling quality for low-resource Indian languages (Telugu, Tamil, Marathi, Bengali, Gujarati, Kannada, Malayalam, Punjabi, Odia) is unmeasured and likely lags English. V1 is zero-shot (no fine-tuning), so accuracy depends on E2B's native multilingual ability + the `IntentParser` patterns.

**Mitigation:**
- Add `IntentParser` fallback patterns in target languages (catches common commands even if the model fumbles)
- Track per-language accuracy in the eval set
- Hindi = V1 target; Telugu/Tamil = best-effort beta (no parity promise)
- If a language consistently fails, that's where a fine-tuned router could later help

**Status:** Open, V2 priority

---

## R-004 — Battery profile under heavy use

**Likelihood:** Medium · **Impact:** High · **Owner:** (unassigned)

LiteRT inference on CPU is power-hungry. Frequent inferences could drain battery faster than acceptable. Target: < 5% drain per 10-minute active-use session.

**Mitigation:**
- Measure early (during M1)
- Aggressive model unload after idle period
- The `IntentParser` net runs known commands instantly without invoking the model at all — saves battery on the common path
- Consider a smaller/faster router in front of E2B for frequent single-step commands

**Status:** Open, measure during M1

---

## R-005 — Confirmation gate UX friction

**Likelihood:** High · **Impact:** Medium · **Owner:** (unassigned)

If the confirmation gate fires too often, users disable it and we lose the safety guarantee. If it fires too rarely, we ship a destructive-action footgun.

**Mitigation:**
- Three modes: strict / balanced / loose; default to balanced (silent for read-only, toast for reversible, modal for irreversible)
- "Don't ask again for this exact action in the next 5 minutes" option on the modal
- User research with beta testers before V1 lock

**Status:** Partial. `safety/ConfirmationGate.kt` + `safety/AuditLog.kt` shipped — gate fires for every non-`None` tool (currently modal for all). Toast-for-Reversible + strict/balanced/loose user setting + 5-min suppress still TODO.

---

## R-006 — A single high-profile misuse

**Likelihood:** Low · **Impact:** Project-killing · **Owner:** (project lead)

Because the agent can send SMS, place calls, and drive apps, a confidently wrong action in front of media (or in front of a popular Twitter account) damages trust permanently. "AI sends embarrassing message" is a great viral headline.

**Mitigation:**
- ConfirmationGate is non-negotiable
- Ship with conservative defaults
- Pre-release red-team round with deliberately adversarial prompts
- "What this app cannot do" section in onboarding, calibrating expectations

**Status:** Open, ongoing

---

## R-007 — Play Store policy as critical path

**Likelihood:** Low (we don't depend on it) · **Impact:** High if we ever do · **Owner:** (project lead)

If we ever ship to the Play Store, Google's accessibility-policy review is a structural threat — they can refuse the listing or remove it later. Building distribution around Play creates a single point of failure.

**Mitigation:**
- Stay sideload-native: GitHub Releases + IzzyOnDroid primary; F-Droid main a stretch goal
- Play distribution is a stretch goal, never a critical path
- If we do attempt Play, keep a feature-equivalent non-Play build always shipping

**Status:** Open, ongoing

---

## R-008 — Solo-maintainer attrition (the real existential risk)

**Likelihood:** High · **Impact:** Project-killing · **Owner:** (project lead)

The [viability assessment](research/2026-06-04-mitra-viability-assessment.md) found this is the ONE genuinely existential, high-likelihood risk — above every Android-policy fear. ~70% of OSS core-dev detachments happen in a project's first 3 years (our phase) and most projects never recover a core dev. Unlike platform risks, there is no OS-level workaround.

**Mitigation:**
- Fork-able by design: 100% on-device + Apache-2.0 code = no proprietary backend anyone can switch off (the Mycroft-died / OpenVoiceOS-survived lesson)
- Make tool-growth community-runnable: in-repo eval set + eval-in-CI, so any contributor can add a tool (impl + "Use this when" description + `IntentParser` pattern + test) unaided
- Tag 25–40% of issues `good-first-issue`; document 2-tier contributor/maintainer governance from day one
- Pursue NLnet NGI Zero / Mobifree grants (5K–50K EUR, even-month deadlines, fund exactly this profile)

**Status:** Open — bootstrap the community pipeline + submit a grant application before heavy build (de-risk task, weeks 1–2)

---

## R-009 — Play Protect malware misclassification

**Likelihood:** Medium · **Impact:** High (for stock-Android GitHub-APK users) · **Owner:** (unassigned)

The AccessibilityService + SMS-read combo matches the Android banking-trojan profile. Play Protect (on-device, runs even when sideloading on certified devices) can warn on or block this combo for stock-Android GitHub-APK installs in pilot regions, India included. F-Droid (session installer) and de-Google devices (no Play Services) are exempt.

**Mitigation:**
- Ship SMS-read / notification-listener as **optional feature modules** so the base APK doesn't declare the flagged combo
- Prefer F-Droid client / Obtainium / IzzyOnDroid distribution (session installers)
- Document the install path for stock-Android users; complete free Play developer verification early (doesn't bind ADB or de-Google ROMs)

**Status:** Open, design feature-modular permissions before M1 telephony tools

---

## R-010 — Android Developer Verification mandate

**Likelihood:** High (the mandate is rolling out) · **Impact:** Medium · **Owner:** (project lead)

From Sep 2026+, Google is phasing in developer verification for app installs on **certified** Android devices. It does not touch ADB installs or de-Google ROMs (GrapheneOS/Calyx/Lineage), which are out of scope, but it adds friction for stock-Android sideloaders.

**Mitigation:**
- Complete the free (limited-distribution) verification early
- de-Google ROMs and ADB/Obtainium paths remain unaffected — keep them first-class
- Track the rollout; it's friction, not a wall

**Status:** Open, monitor
