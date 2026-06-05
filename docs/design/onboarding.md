# Mitra Onboarding Journey

The first 60 seconds — or rather, the first ~5 minutes including the 2.59 GB download — decide whether the user keeps Mitra installed. The bar for an on-device assistant that requires a big download is higher than for a cloud chatbot: the user must believe the download is worth completing before it finishes.

**Audience**: non-technical Indian users + Western privacy-conscious users. The journey must work for both — meaning no Western tech jargon, no India-specific cultural assumptions, no Hinglish.

**Four beliefs the user must hold by the time onboarding ends** (R-006 calibration is non-negotiable):

1. **It runs entirely on this phone.** Nothing leaves the device.
2. **It does real device control** — not just chat.
3. **It asks before doing anything risky.**
4. **It cannot do certain things, and that is deliberate.**

**Six screens** (voice screen skipped — M4 ships voice later, per the answer to Q12): Welcome → PrivacyPromise → ModelDownload → LoadingModel → Capabilities → Chat.

**No permission prompts during onboarding.** All permission flows are lazy and triggered by feature use (per permissions.md). This is intentional — onboarding establishes trust before asking for any of it.

---

## 1. The journey at a glance

| # | Screen | Mental-model target | Drop-off risk | Mitigations |
|---|---|---|---|---|
| 1 | Welcome | "This is an AI app. Calm aesthetic. Made for me." | Lowest. | Strong visual, brief copy. |
| 2 | PrivacyPromise | "My data does not leave this phone." | Low. | Plain language, no marketing jargon, "Read the source" link. |
| 3 | ModelDownload | "Yes, the download is worth it. I trust this app to finish it." | **Highest.** | Specific size, expected speed, what the bytes are, pause/resume, "what you can do while waiting" hint after 30s. |
| 4 | LoadingModel | "Almost ready. Mitra is preparing itself." | Low-medium. | Brief (~3-10 sec), specific status text, no false progress bars. |
| 5 | Capabilities | "I understand what Mitra can and cannot do." | Low. | Bullet-list both directions, neither aspirational nor defensive. |
| 6 | Chat (first interaction) | "I can try this right now and see something happen." | Low. | Six rotating suggestion chips with immediate-feedback tools. |

---

## 2. Screen 1 — Welcome

### Mental-model target after this screen
"This is calm. It has a name. It feels like a notebook, not a control panel."

### Layout
Centered, vertical, generous breathing room. No illustration in V1 — restraint over decoration. A single small mark above the headline (Mitra wordmark in clay).

### Copy
- **Wordmark**: `Mitra` (using the Display token, `primary` colour)
- **Headline**: `Welcome to Mitra.`
- **Subhead**: `Your AI, on your phone.`
- **Primary CTA**: `Get started`
- **Secondary link**: `Read the source →`

> The Welcome screen is the one place an exclamation mark is permitted by voice.md — but the current copy does not use one. Add it only if the subhead lands flat in user testing.

### Edge cases
- **Returning after uninstall + reinstall**: the `first_run_complete` flag in DataStore is gone, so the user starts here again. Acceptable.
- **First launch on a phone with no network**: Welcome works (no network needed). ModelDownload is where the network requirement is surfaced.

---

## 3. Screen 2 — PrivacyPromise

### Mental-model target after this screen
"Nothing leaves my phone. They mean it — there is no account to make, no email to enter."

### Layout
A central headline, three short body bullets, primary CTA, secondary "Read the source" link to GitHub. A small minimal diagram between headline and body — a phone outline with an arrow looping back into itself — communicates "nothing goes out" without words.

### Copy
- **Headline**: `Nothing leaves your phone.`
- **Body**:
  > Mitra runs entirely on this device. Your messages, your model, your audit log — all of it stays here.
  >
  > No accounts. No servers. No telemetry.
  >
  > Mitra is open source. You can read every line.
- **Primary CTA**: `Continue`
- **Secondary link**: `View source on GitHub →`

### Why these specific three lines
The three body lines map 1:1 to the three doubts every privacy-conscious user has on first encounter with an AI app:

| User doubt | Line that answers it |
|---|---|
| "Where does my data go?" | Mitra runs entirely on this device. |
| "Will I need to sign up?" | No accounts. No servers. No telemetry. |
| "How do I trust them?" | Mitra is open source. You can read every line. |

### What this screen does NOT do
- It does not list specific privacy laws or compliance badges. (Those land in About & privacy for the user who wants them. The onboarding does not interrupt the journey with legalese.)
- It does not promise things Mitra cannot guarantee (e.g., "no one can ever see your data" is technically false if the device is rooted, etc.). The wording stays factual.

---

## 4. Screen 3 — ModelDownload (the drop-off screen)

### Mental-model target after this screen
"This is downloading what I need to make Mitra work. The wait is finite. I can keep my phone in my pocket."

### Why this is the highest-risk screen
The 2.59 GB download is the largest single ask in the onboarding. Without specific design choices, users abandon. The mitigations below are the difference between 30% completion and 80%+.

### Layout (idle state)
- **Model card** at top: `Gemma 4 E2B` / `2.59 GB` / `An open model from Google · Apache 2.0` / footnote line `Runs entirely on your phone.`
- **Two toggles** below the card:
  - `Wi-Fi only` — default **ON**.
  - `Continue in background` — default **ON**.
- **Storage line**: `Requires 3.2 GB free · You have 14 GB free.` (Dynamic. If insufficient, this line is `danger`-coloured and the primary CTA is disabled.)
- **Primary CTA**: `Download model`
- **Secondary link**: `Choose a different model` (opens model picker — out of V1 default flow, available for advanced users).

### Layout (downloading state)
- Model card collapses to a single line: `Gemma 4 E2B · 2.59 GB`
- **Progress section** below:
  - Determinate progress bar (clay-coloured fill on `surfaceVariant` track)
  - Caption beneath: `1.43 GB of 2.59 GB · 7 minutes remaining` (uses tabular numerals)
  - Wi-Fi status indicator: `Connected — Wi-Fi`
- **Below the progress**: `What you can do while you wait` hint (appears after 30 seconds, not before):
  > Lock your phone — the download continues. You will be notified when Mitra is ready.
- **Action row**: `Pause` (secondary) · `Cancel download` (text-only, appears only when ≥5% downloaded — the cancel is hidden under 5% to prevent accidental abandonment in the first seconds).

### Layout (paused state)
- Same as downloading, but the bar is non-animated.
- Caption: `Paused — by you` or `Paused — no Wi-Fi`
- Action row: `Resume` (primary) · `Cancel download`

### Layout (insufficient storage)
- Replaces the toggles with a panel:
  > **Not enough space.** Mitra needs 3.2 GB free. You have 1.1 GB free.
  >
  > Clear some apps or photos, then come back.
- Primary CTA changes to: `Open storage settings`
- The user can return to this screen later — DataStore remembers their intent to download.

### Layout (no Wi-Fi warning, when Wi-Fi-only is ON but Wi-Fi is off)
- Inline banner above the CTA: `Wi-Fi off. Toggle Wi-Fi-only off to use mobile data — 2.59 GB will count against your plan.`
- Primary CTA disabled until either Wi-Fi is on or the toggle is flipped.

### Drop-off mitigations summary
1. **Specific size, in GB, with a reason** — not "downloading…". Users tolerate finite waits; ambiguous waits drive abandonment.
2. **Expected time** — calculated from real download speed, updated every 2 seconds.
3. **Tabular numerals** — the byte counter does not jitter as digits change width.
4. **"What you can do while you wait" appears at 30 seconds** — not earlier (would seem panicky), not later (user is already considering quitting).
5. **Foreground service with status-bar notification** — user can lock the phone and the download continues; the notification shows the same byte counter and ETA.
6. **Cancel hidden in first 5%** — prevents the regret of abandoning at 4% and having to restart.
7. **Resume on app re-launch** — DataStore persists in-progress download state; on next launch the user returns to the same screen mid-progress, not a fresh re-start.
8. **Wi-Fi-only default ON** — Indian users on metered plans default-safe; Western users on Wi-Fi notice no change.
9. **Hash verification before "complete"** — corrupt downloads do not get to LoadingModel and then crash; failure is caught here.
10. **The model card's attribution line** — "An open model from Google · Apache 2.0" — builds trust by being specific about what the user is downloading. (Per the user's input: trust framing for Gemma.)

### Returning-user fast path
Users who have completed the download once and reinstalled (or wiped data) can re-trigger this flow from Settings → "Manage model". The Welcome / PrivacyPromise screens are not re-shown; ModelDownload alone is the path. After completion, they land back in Settings rather than Capabilities.

---

## 5. Screen 4 — LoadingModel

### Mental-model target after this screen
"Mitra is getting ready. Two more seconds."

### Layout
- Centered Mitra wordmark (same Display token / clay colour as Welcome, but slightly smaller).
- Beneath it, a single line of status text, updated as loading progresses.
- A subtle three-dot pulse animation under the status text — NOT a fake progress bar. (Fake progress bars feel insulting and break trust when they sit at 80% for 3 seconds.)
- No buttons in normal load. An escape-hatch `Cancel` button appears after 8 seconds, in case the runtime is stuck.

### Status text (three discrete strings, no animation between them)
1. `Loading model — about 3 seconds.` (initial)
2. `Almost there.` (if loading exceeds 3 sec)
3. `Model still loading. One moment.` (if it exceeds 8 sec — the cancel button also appears)

### Failure state
- Status replaced by: `Model could not load.`
- Two CTAs: `Try again` (primary) · `Open settings` (secondary).
- On second failure: routes to the Error screen (per screens.md), which offers a "Re-download model" option.

### Returning-user path
On every cold start (not just first-run), the LoadingModel screen surfaces while the runtime initialises. The user sees it briefly; it usually completes in 1-2 seconds for warm caches. The 3-second target is for cold first-load.

---

## 6. Screen 5 — Capabilities (the R-006 calibration moment)

### Mental-model target after this screen
"I know what to ask Mitra. I also know what NOT to ask Mitra. The honesty is reassuring."

### Why this screen exists
Per R-006, a single high-profile misuse — a user expecting Mitra to do something it cannot, and ending up disappointed (or worse, harmed) — is project-killing. The Capabilities screen heads this off by setting clear, calibrated expectations BEFORE the user types their first prompt.

The screen is not a feature list. It is an expectations contract.

### Layout
Two stacked sections, each headed by a small label:

**What Mitra can do** (`success` accent label)
- 🔆 `Turn on flashlight, change volume, brightness, Do Not Disturb`
- ⏰ `Set alarms, timers, stopwatch`
- 💬 `Send SMS through your default messaging app`
- 📞 `Call contacts by name`
- 📱 `Open any installed app`

**What Mitra cannot do (yet)** (`onSurfaceVariant` label — same weight, no `danger` colour. Neutral, not scary.)
- `Send WhatsApp or other messaging app texts — only SMS`
- `Control other apps' screens — only call its own tools`
- `Hear you — voice input arrives in a later update`

- **Primary CTA**: `All good — let's begin`

### Copy principles applied
- Honest about limits, not apologetic about them ("(yet)" is the only hedge).
- The "cannot do" list is presented as deliberate scope, not missing features.
- No marketing language about future capabilities ("Coming soon!" is forbidden — it sets up expectations that may not be met).
- The phrasing for what Mitra cannot do never makes the user feel they made a wrong choice ("Mitra is not for you if..." is forbidden).

### Why this screen is shown only once
After the user taps `All good — let's begin`, the `first_run_complete` flag is set in DataStore. The screen does not re-appear on subsequent launches. (It can be re-read in About & privacy, where it lives as a section called "Capabilities".)

---

## 7. Screen 6 — Chat (the first interaction)

### Mental-model target after this screen
"I just tried something simple. It worked. I want to try more."

### Layout (empty state, just landed)
- Top bar: audit-history icon left, settings icon right. No title — the empty centre is intentional.
- Centre: a small Mitra wordmark above a single-line prompt:
  > `Try saying: "Turn on flashlight"`
- Below the prompt: **6 rotating suggestion chips**, refreshing each app launch:
  - `Turn on flashlight`
  - `Set an alarm for 7 AM`
  - `Volume to 30%`
  - `Open Calculator`
  - `Battery level?`
  - `What's playing?`
- Input bar at the bottom: text field with an inert mic icon (M4 will activate it) and a send button.

### Why these six chips
- **Flashlight** — instant, visible feedback, no permission needed.
- **Set an alarm** — exercises the Clock-app flow (permission may be requested mid-flow).
- **Volume** — system-level control, immediate feedback.
- **Open an app** — proves the app-launcher works.
- **Battery level** — read-only, no permission, fast response.
- **What's playing?** — exercises the MediaSession path.

The user can tap any chip to send it as a message immediately. This is the "first success" — the moment where Mitra goes from theoretical to tangible. The chip set is chosen to make that success likely on the first try.

### First-message behaviour
- The chip-tap sends the text as a chat message.
- The LLM emits a tool call (e.g., `flashlight_on`).
- The Silent / ambient ActionCard renders in chat: `Flashlight on · just now`
- The audit log gets its first entry.
- The user sees Mitra do the thing.

This sequence is the moment the user becomes a user.

---

## 8. Drop-off measurement (planned, R-006-aligned)

Per voice.md and the privacy invariants, **no telemetry is shipped in V1**. There is no analytics SDK, no event reporting, no funnel data leaving the device. So how do we know if onboarding is working?

Two mechanisms, both **opt-in and local**:

1. **In-app feedback prompt** after 7 days of use (and after at least 10 chat messages): "How is Mitra working for you?" with a single text field that drafts an email to the user's default mail app, pre-addressed to the project's feedback email. The user reviews and sends — nothing is uploaded silently. The feedback prompt can be dismissed forever with one tap.
2. **F-Droid / GitHub issue tracker** as the canonical user-feedback channel for power users. Linked from About & privacy.

Drop-off rate during onboarding is measured **only through community-reported feedback** in V1. This is a deliberate tradeoff — no analytics in exchange for trust. If V2 needs more granular measurement, opt-in self-reported feedback (timed surveys, never auto-events) is the only path that preserves the privacy invariants.

---

## 9. Returning-user fast path (returning after install, not after wipe)

If `first_run_complete = true` in DataStore:

- Welcome, PrivacyPromise, ModelDownload, Capabilities are skipped.
- LoadingModel runs briefly (usually 1-2 sec) while the runtime warms.
- User lands directly in Chat.

If the user updates the app and the model needs migration, a small inline banner appears in Chat (`Updating model — Mitra ready in a moment`) and the LoadingModel screen briefly takes over. No re-onboarding.

If the model file is missing (rare — user cleared app storage but the app data partition kept `first_run_complete = true`), the flow re-enters at ModelDownload. PrivacyPromise and Capabilities are not re-shown.

---

## 10. Kotlin codification

```kotlin
// ui/onboarding/OnboardingScreens.kt
package com.mitra.ui.onboarding

import androidx.compose.runtime.Composable

sealed interface OnboardingDestination {
    data object Welcome : OnboardingDestination
    data object PrivacyPromise : OnboardingDestination
    data object ModelDownload : OnboardingDestination
    data object LoadingModel : OnboardingDestination
    data object Capabilities : OnboardingDestination
    data object Done : OnboardingDestination     // sets first_run_complete = true, navigates to Chat
}

@Composable
fun OnboardingGraph(
    repository: ModelRepository,
    permissionRepository: PermissionRepository,  // unused in V1 onboarding — no prompts here
    onComplete: () -> Unit,
)
```

The `Done` destination writes the DataStore flag and emits the navigation event. No permission Composable lives in this graph — onboarding is permission-free.

---

## 11. Out-of-scope decisions, recorded so they aren't proposed later

- **No accounts.** Not in V1, not planned. The "no sign-up" is a feature, not a deferral.
- **No language picker on first launch.** Locale follows system setting; English is the only V1 string set with parity (R-003).
- **No theme picker on first launch.** System theme is followed; theme picker lives in Settings.
- **No "Tour" / "Walkthrough" overlay** that points at UI elements once Chat opens. The suggestion chips ARE the tour.
- **No optional "skip onboarding" CTA.** The flow is short enough that skip dilutes the calibration moment (Capabilities) which exists for R-006 reasons. The only "skip" is uninstall.
