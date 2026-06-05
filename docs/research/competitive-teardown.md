# Mitra Competitive UX Teardown

A UX-only teardown of eight assistants in Mitra's adjacency. The goal is concrete: identify patterns to steal, patterns to invert, and anti-patterns to avoid. Quality, hallucination, and accuracy are **not** discussed — those are model-level concerns, separate from UX. Indian-context apps (Kruti, Indus) are evaluated on their UX patterns only, not on quality or output.

**Eight apps in scope**:
1. Google Gemini (Android)
2. Legacy Google Assistant overlay
3. Layla (offline LLM Android)
4. MLC Chat Android
5. ChatGPT (Android)
6. Krutrim / Kruti (Ola — *historical, app withdrawn April 2026*)
7. Sarvam Indus (Android)
8. Inflection Pi (Android — tone-reference)

**Cross-cutting evaluation axes**:
- First-run / onboarding
- Permission request choreography
- Confirmation before destructive actions
- Error state design
- Empty / loading state design
- Dark mode
- Voice trigger affordance
- Offline / local-only indicator

---

## 1. Google Gemini (Android)

The market-defining product Mitra is trying to live alongside, not replace for everyone. Sets user expectations for "AI assistant on phone."

### Strengths
- **Long-press power button gesture** — Gemini's invocation surface is industry-leading. The user doesn't need to find an app icon; the assistant is one hardware press away. No notification clutter, no widget required.
- **Single-prompt multi-action support** — "turn up media volume, lower notification volume, and enable battery saver" — three actions one prompt. Worth stealing for Mitra (already planned per action-cards.md).
- **Utilities extension's actual scope** — alarms, timers, media, notifications, camera, app launch. Most assistants under-promise on device control; Gemini's Utilities sets the surface area expectation.

### Weaknesses
- **Account required.** A Google account is mandatory. There is no way to use Gemini on Android without one — a hard fail for Mitra's privacy-conscious audience.
- **Voice + visual hybrid is loud.** Gemini overlays your screen with a coloured glow, transcribes voice with bouncing waveforms, and announces with TTS by default. Reads as "AI is happening" — the opposite of calm.
- **The "Ask about screen" affordance is unclear.** The pop-up sheet that asks "ask about this screen?" / "ask about this video?" appears contextually, which is good — but its dismissal is fiddly and many users report accidental triggers.

### Anti-pattern to NOT copy
**The gradient brand identity (blue → purple → orange) applied to UI surfaces.** This is the visual language of "AI flashy" — it announces itself constantly. Mitra's whole identity is the opposite: solid clay, paper-cream background, no glow.

---

## 2. Legacy Google Assistant overlay

The previous generation. Still active on older devices, on Android Auto, on Wear OS. Less capable but UX-coherent.

### Strengths
- **Card-based response model.** Each response was a card: title, body, sometimes a media thumbnail, action chips. The card pattern survives in Mitra's ActionCards.
- **The "Ok Google, …" voice trigger** had a single, well-known phrase. Mental model was simple: speak the phrase, talk.
- **Visual brevity.** Responses were small overlay sheets, not full-screen takeovers. The user stayed in the app they were already using.

### Weaknesses
- **Card explosion.** A "what's the weather?" query produced 4-5 stacked cards (current, forecast, allergens, sunset…). Information dense, decision fatigue.
- **Inconsistent action affordances** — some cards had buttons, some had chips, some had neither. No predictable interaction model.
- **Hard reliance on Google services.** Without Google Play Services, Assistant did not work at all. A non-starter for de-Googled devices.

### Anti-pattern to NOT copy
**The aggressive "Hey Google" hotword detection.** Always-listening as a default invites both privacy concerns and accidental triggers. Mitra V1 does not ship voice; M4 will, and only as opt-in push-to-talk, never always-listening.

---

## 3. Layla (offline LLM Android)

A consumer-facing on-device chat app. Direct comparison point for Mitra's local-LLM angle.

### Strengths
- **Model picker upfront and honest.** First-run shows multiple models with sizes and recommended use cases. The user knows what they are downloading and why.
- **Offline-first messaging is the headline.** No second-guessing about what data goes where — the first screen says "this is offline."
- **Character / persona system** is a clean UX abstraction for "talk to different models for different tasks." Not relevant to Mitra (we don't do personas), but the pattern of treating models as first-class user-visible objects is right.

### Weaknesses
- **No device control.** Layla is purely a chatbot. Users who want their on-device LLM to actually do things on the phone have to look elsewhere — that's the gap Mitra fills.
- **Heavy app size at install.** The app bundle plus first-model is multi-GB before the user knows if they like it. Mitra splits these — small app, deliberate model download.
- **Settings UI is dense.** Power-user knobs (temperature, top-p, top-k, repetition penalty) live on the chat screen, not in a settings drawer. Cognitive overhead.

### Anti-pattern to NOT copy
**Surfacing model hyperparameters in the main UI.** Top-k and temperature do not belong on the chat screen. They belong, if anywhere, in an "Advanced" settings section that a power user has to opt into. Most users will never look at them.

---

## 4. MLC Chat Android

The open-source / developer-leaning local LLM app. Reference for Mitra's open-source positioning.

### Strengths
- **Minimal, focused UI.** Single chat screen, single model selector, single settings sheet. The restraint is its identity.
- **Model performance visibility.** Token/sec is shown discreetly. Power users care; the placement (not in the prompt area) means casual users don't notice.
- **GitHub link prominent in About.** The open-source ethos is surfaced, not buried.

### Weaknesses
- **No onboarding.** Drops the user into a chat with no model loaded. The new user is left to figure out "download a model" without context. Mitra's PrivacyPromise + ModelDownload screens directly address this gap.
- **Error messages are technical** ("CUDA out of memory" surfaced verbatim from logs). The user sees implementation details they cannot act on.
- **No device control** — pure chat. Same as Layla.

### Anti-pattern to NOT copy
**Leaking error strings from the inference runtime directly into the UI.** When LiteRT-LM returns an internal error, Mitra's UI translates it into the standard `[what failed]. [why, if known]. [next step].` voice pattern from voice.md — never the raw exception message.

---

## 5. ChatGPT (Android)

Not a competitor — different category — but the reference for "AI chat done at scale" and worth studying for what to avoid.

### Strengths
- **Voice mode pacing.** The voice mode in ChatGPT is unusually calm and human — slow turn-taking, natural pauses, no rush to fill silence. Tone reference (alongside Pi).
- **Empty state suggestion chips.** First-launch shows a small set of starter prompts. Same pattern Mitra uses on the Chat empty state.
- **Multimodal input button placement.** Camera, mic, attach, send — single bar, clear iconography.

### Weaknesses
- **Account-and-cloud assumption baked in.** Every interaction is cloud-bound. No on-device option exists. Out of scope for a privacy-first product.
- **Heavy onboarding** — sign-in, sign-up, choose-a-plan, learn-features carousel, personalisation prompts. Multiple drop-off points before the first message.
- **Notification spam** — ChatGPT routinely sends product-update notifications. Anti-pattern for Mitra's "no notifications except foreground service" rule.

### Anti-pattern to NOT copy
**The "personalisation" survey during onboarding** ("What do you want ChatGPT to know about you?"). This trades trust for personalisation in the user's first 60 seconds. Mitra's onboarding deliberately does not ask about the user.

---

## 6. Krutrim / Kruti (Ola — historical)

Released June 2025, withdrawn from Google Play and Apple App Store in April 2026 after the company's restructuring. Evaluated here as a case study, not as a live competitor. UX-only analysis.

### Strengths
- **Multilingual input acceptance** without a language picker. The user typed or spoke in any of 13 Indian languages and Kruti responded in kind. Mitra V1 cannot match this (English V1, Hindi V1, regional beta per R-003), but the pattern of "infer the input language, mirror it back" is correct — language pickers feel like friction.
- **Agentic action surface for Indian use cases** — booking cabs, paying bills, ordering food via integrations. The category framing of "assistant that does things" mapped well to the Indian user's expectations (not just chat).
- **Visual identity distinct from global AI brands.** A warmer, less futuristic palette than Gemini / ChatGPT. The direction was right even if the specifics differed from Mitra's.

### Weaknesses
- **Heavy reliance on Ola ecosystem.** Many "agentic" actions worked only inside Ola services. Users outside that ecosystem saw a chat-only app with empty promises.
- **No transparent on-device option.** Kruti was cloud-based; "made in India" became the trust framing instead of "runs on your device." A different brand promise, not directly comparable to Mitra's.
- **Permission patterns were standard mobile-app pattern**, not specifically thoughtful — runtime dialogs at first launch for several permissions, no priming layer.

### Anti-pattern to NOT copy
**Bundling assistant capability with a vendor ecosystem.** Kruti's deepest features required Ola accounts. Mitra's tools work against the user's own apps (default SMS, default Phone, default Calendar) — never a single-vendor lock-in.

---

## 7. Sarvam Indus (Android)

Sarvam's consumer-facing AI assistant, launched recently on Play Store as the closest live Indian-context comparison.

### Strengths
- **Language-fluid prompt area.** Users can type or speak in Hindi, English, Tamil, or a mix — the input accepts whatever the user wrote without a switcher. The "Chat the way you speak" framing on the listing is strong.
- **Voice-prominent in first-run.** The mic affordance is large and central; voice is positioned as the primary input, not a secondary one. Worth observing for Mitra's M4 design.
- **"Built in India" trust framing.** Local provenance is the lead value-prop on the Play listing — similar in spirit to Mitra's "runs on your phone" but a different axis.

### Weaknesses
- **Cloud-bound.** All conversations require connectivity. The Indian-context trust angle (local provenance) is undermined by data leaving the device — a gap Mitra's on-device approach fills.
- **No device-control tools** in the consumer app — it is a web-search-and-chat product. Like Kruti, agentic actions are limited to first-party integrations.
- **Permission patterns are standard mobile-app pattern.** Microphone permission asked at first run without priming. Worth doing better.

### Anti-pattern to NOT copy
**Conflating "local provenance" with "local processing."** Indus is a useful product but the marketing implication that "built in India" means "your data stays in India" can mislead. Mitra's privacy framing is the literal technical reality (runs on this device), not a national-origin claim.

---

## 8. Inflection Pi (Android)

Not an Indian product, not a device-control app — included specifically as a **tone reference** for calm, conversational voice. Pi's tone is the closest external benchmark for Mitra's "calm friend" voice.

### Strengths
- **Voice as the primary affordance.** Pi's headline feature is the voice mode. The visual UI is deliberately quiet — most chat happens audially.
- **Pacing is slow and human.** Pi pauses, breathes, doesn't rush. The TTS voice itself is unusually calm — not chirpy, not over-enunciated.
- **No exclamation marks in copy.** Pi's UI strings demonstrate that warmth is achievable without performative punctuation. Direct evidence for Mitra's "no exclamation marks except onboarding" rule.

### Weaknesses
- **Heavy reliance on one model voice.** Pi has very limited customisation. For a product that lives in users' ears, lack of voice variety hurts. Out of scope for Mitra V1 (no voice yet) but worth noting for M4.
- **No device control.** Companion / chat only — same gap as Layla, MLC Chat, ChatGPT.
- **Cloud-only.** Same constraint as ChatGPT. Out of category for direct comparison.

### Anti-pattern to NOT copy
**Tying the brand entirely to voice.** Pi is voice-first to a fault — users who want to type feel like second-class users. Mitra's M4 voice will be additive, never primary; the keyboard remains the equal path.

---

## 9. Cross-cutting comparison table

| | First-run | Permission asks | Destructive-action confirm | Error states | Empty / loading | Dark mode | Voice trigger | Offline / local indicator |
|---|---|---|---|---|---|---|---|---|
| **Gemini (Android)** | Sign-in required; feature tour | Mostly system runtime dialogs, sometimes primed | Inconsistent — some actions confirm, some don't | Verbose, sometimes apologetic ("Sorry…") | Branded chips, friendly | Strong, dynamic | Long-press power; "Hey Google" optional | None — assumes cloud |
| **Legacy Assistant** | Setup wizard with mic test | Eager (asks at startup) | Rare — assistant runs first, asks rarely | Generic "Something went wrong" | Card-based suggestions | Yes, system-following | "Ok Google" / "Hey Google" hotword | None |
| **Layla** | Model picker, no privacy explainer | Few (it's offline) | None — chat only | Plain | Functional | Yes | Optional voice button | Strong — "offline" framing in header |
| **MLC Chat** | Drops into chat; user must figure out download | Few | None — chat only | Raw runtime errors leaked | Minimal | Yes | None | Implicit (no network indicator) |
| **ChatGPT (Android)** | Sign-up, plan, personalisation, walkthrough | Standard runtime | Rare ("delete chat" confirms) | Branded apology copy | Suggestion chips, warm copy | Yes | Voice mode button + voice trigger | "Offline" lock when no network |
| **Kruti** (historical) | Language picker, then chat | At-launch dialogs, multiple | Some — booking flows confirmed | Standard mobile-app | Branded, vibrant | Yes | Microphone prominent | None — cloud-bound |
| **Sarvam Indus** | Branded splash → chat | Microphone at first run, others on use | Limited surface — chat / search only | Standard mobile-app | Suggestion chips | Yes | Mic central in input bar | None — cloud-bound |
| **Pi (Inflection)** | Brief, voice-led | Microphone primed | N/A | Calm, conversational | Voice waveform | Yes | Voice toggle prominent | None — cloud-bound |
| **Mitra (target)** | Welcome → Privacy → Model download → Loading → Capabilities → Chat | Lazy, every permission primed, 24h cooldown | **ConfirmationGate for all Irreversible (R-006)** | Voice.md error pattern: `what + why + next step` | Suggestion chips, calm | Yes — warm-dark | None V1; opt-in push-to-talk M4 | Constant — "On device" badge in About; never any cloud arrows in UI |

---

## 10. Five concrete recommendations for Mitra

Drawn from this teardown, in priority order.

### 1. Make the privacy claim literal, not implied
Several apps in this list (Kruti, Indus, Pi, ChatGPT) gesture at privacy or locality through marketing copy — "built in India", "your data is safe", etc. Mitra's privacy claim is **a literal technical fact** the user can verify by reading the source. Surface this distinction:
- PrivacyPromise screen says "Mitra runs entirely on this device. You can read every line."
- About & privacy links directly to the GitHub repo.
- The audit log is the receipts. No competitor in this list ships a per-action audit log.

### 2. Default to silence; treat sound, glow, and motion as opt-in or earned
Gemini glows. Pi waveforms. Kruti animates. Mitra does not. The Linear / Things / Notion aesthetic anchor exists for one reason — calm IS the brand. Specifically:
- No coloured glows on RUNNING states (action-cards.md §7).
- No bouncing motion (tokens.md §6).
- No exclamation marks except onboarding (voice.md §1).
- Foreground-service notification uses `PRIORITY_MIN` (permissions.md §2).

### 3. Treat the model as a first-class user-visible object
Layla and MLC do this well; Gemini and ChatGPT hide the model entirely. Mitra surfaces:
- Model name (Gemma 4 E2B) + size + source attribution on ModelDownload (onboarding.md §4).
- Settings → Model section shows current state and lets the user manage.
- The model itself is open. The user can verify provenance.

### 4. Confirm exactly the actions Gemini does NOT confirm
Gemini's biggest UX weakness for device control is inconsistent confirmation. Mitra's ConfirmationGate is the inversion (R-006): every Irreversible action gets a modal, every Reversible gets a Toast with Undo, every None auto-runs but is logged. The user always knows what state they are in. Consistency itself is a feature.

### 5. Multilingual without a language picker
Kruti and Indus both demonstrate this is possible — accept input in whatever the user writes, respond in kind. Mitra V1 ships English only with parity; Hindi follows in M5. But the **architecture** assumption from now should be "no language picker — locale is set by the system, input language is accepted as-is, response language defaults to locale but can mirror input as it scales out." Designing the prompt area without a switcher today saves a rewrite tomorrow.

---

## 11. What this teardown does NOT do

- **No quality / hallucination criticism** of any product, especially Krutrim and Sarvam. The brief is UX patterns only. Output accuracy is a model-level concern.
- **No nation-coded judgments.** "Made in India" is a fact, not a quality signal. Apps are evaluated on what their UX does, not on where it was built.
- **No comparison to "Western standards."** The bar is calm and useful. Mitra is not trying to look like a Silicon Valley product.
- **No business-model criticism.** Whether an app is free, paid, ad-supported, or subscription is out of scope. UX only.
- **No language coded as "crude" or "low-end".** All eight apps were built by competent teams making different tradeoffs. The teardown identifies patterns, not value judgments.

---

## 12. Recurring review

This document is consulted **on every PR that adds a screen, modal, or significant UX surface to Mitra.** A small heuristic: open this file, look at the eight apps' patterns for the surface being designed, and answer "which patterns are we stealing, which are we inverting, and why?" If the answer is unclear, the PR is not ready.
