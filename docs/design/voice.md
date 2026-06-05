# Mitra Voice & Microcopy

The single source of truth for every string in Mitra. If a screen needs copy, it comes from here or from a pattern documented here. Drift starts the moment one screen invents its own tone.

**Voice in one line**: a capable friend who is brief, honest, and never asks twice.

**Self-reference rule**: Mitra refers to itself by name only in three places — the Welcome screen, the Settings header, and audit log labels. Everywhere else, write in second person ("you") or stateful first-person of the action ("Sent.", "Alarm set."). Never write "the assistant".

**Language**: English with Indian English warmth. Light cultural markers ("do let me know", "kindly", "all set", "shall we?") are welcome. No Hinglish, no regional idioms, no honorifics ("sir/ma'am"), no festival greetings, no religious language.

---

## 1. The five voice principles

### Principle 1 — Brief over bubbly
Two sentences is usually one too many. State the outcome. Stop. The user knows what they asked for.

> ✅ "Alarm set for 7:00 AM."
> ❌ "Got it! I've set your alarm for 7:00 AM tomorrow morning. Sweet dreams!"

### Principle 2 — Honest over hedged
When something fails, name what failed. Offer the next step in the same sentence if possible. Never "oops", "uh oh", "yikes". Never apologise for the user's situation ("Sorry you're having trouble").

> ✅ "Could not send — no SMS access. Grant it?"
> ❌ "Oops! Something went wrong. Please try again."

### Principle 3 — Capable, never servile
Mitra is a friend with a job, not a butler. No begging, no scraping, no "if it's not too much trouble". Permission requests are statements of need, not pleas.

> ✅ "To set alarms, Mitra needs Clock access. Grant?"
> ❌ "We'd really appreciate it if you could give us access to your Clock app, please!"

### Principle 4 — Calm, never urgent
No exclamation marks (one allowed on the Welcome screen, no others anywhere). No "Important!", no "Action needed", no red-rimmed banners for non-emergencies. The brand is calm; urgency reads as panic.

> ✅ "Heads up — 5% battery remaining."
> ❌ "⚠️ WARNING! Battery critically low!"

### Principle 5 — Familiar, not folksy
Indian English warmth is the dial. "Do let me know", "kindly", "shall we?", "all set", "noted" are in. "Boss", "buddy", "y'all", "guys", "folks", "haan", "theek hai" are out. The Western reader should not notice; the Indian reader should feel at home.

> ✅ "All set. Do let Mitra know if you want to change this later."
> ❌ "All good, buddy! Just holler if you wanna change it later!"

---

## 2. Vocabulary do / don't (20 rows)

| Don't write | Do write | Why |
|---|---|---|
| Welcome aboard! / Howdy! | Glad you're here | Aviation / Western folksy register |
| Oops! / Uh oh! / Yikes! | (state what happened) | Treats failure as performance |
| Hi guys / folks / y'all | (no greeting; just begin) | Gendered & culturally narrow |
| Sorry / I'm sorry | Cannot ... right now | Servile; obscures the actual issue |
| Got it! / Awesome! / Cool! | Done / Saved / Noted | Bubbly performance of capability |
| Easy peasy / No problem at all | Done | Condescending |
| Don't worry / Relax | (omit entirely) | Tells the user how to feel |
| Click | Tap | Wrong input modality for mobile |
| The assistant / The bot / The AI | Mitra (3 places only) or 2nd person | Brand drift; cold |
| Settings cog / Hamburger menu | Settings | UI jargon |
| Permissions | Access | More conversational |
| AI / LLM / Model brain | (describe the action) | Tech jargon leaks into UX |
| Loading... | Loading model / Reading screen / etc. | Be specific; vague waits feel longer |
| Error / Something went wrong | (state what failed) | Useless; demands a retry guess |
| Are you sure? | Confirm: \[exact action\]? | Pre-action restatement > vague check |
| User | You | Engineering noun in product copy |
| Important! / Action needed | Heads up — \[fact\] | Faux urgency |
| Just click here / Just tap | Tap \[verb the action\] | "Just" minimises; verb the action instead |
| Hey there / Hi! | (no greeting) | Add nothing to the message |
| Sir / Ma'am / Miss / Mister | (no honorific) | Gender unknown; honorifics presume |

---

## 3. The 30 copy lines

Categorised by where they appear. Each line is paste-ready into the Voice.kt string table.

### Tool success — silent / ambient (3)
1. `Flashlight on`
2. `Volume up`
3. `Do Not Disturb on`

### Tool success — toast / inline confirmation (3)
4. `Alarm set for 7:00 AM tomorrow. Tap to edit.`
5. `Timer started — 25 minutes. Tap to cancel.`
6. `Brightness set to 60%.`

### Tool success — modal / committed (3)
7. `Sent to Priya.`
8. `Calling Amma…`
9. `2 alarms set.`

### Partial success (3)
10. `Set 2 of 3 alarms. Could not set 6:00 AM — one already exists at that time.`
11. `Sent to 2 contacts. Rahul has no number on file — kindly add one to reach him.`
12. `Opened 4 of 5 apps. One has been uninstalled.`

### Missing argument (3)
13. `Which contact should Mitra message?`
14. `What time should the alarm go off?`
15. `How long should the timer run?`

### Permission needed (4)
16. `To set alarms, Mitra needs Clock access. Grant?`
17. `To call, Mitra needs Phone access. Grant?`
18. `To send the message, Mitra needs SMS access. Grant?`
19. `Brightness needs a one-time system permission. Open settings?`

### Tool unavailable / no clock app (2)
20. `No clock app responded. Kindly check your default clock app in Settings.`
21. `Cannot find a clock app. Install one from Play Store to use alarms and timers.`

### Model not loaded (3)
22. `Loading model — about 3 seconds.`
23. `Model still loading. One moment.`
24. `Model could not load. Restart Mitra, or check storage in Settings.`

### Irreversible confirm (3)
25. `Confirm: send SMS "Running late" to Priya?`
26. `Confirm: call +91 98765 43210?`
27. `Confirm: delete all alarms? Cannot be undone.`

### Audit log entry labels (3)
28. `Flashlight turned on • 2 sec ago`
29. `SMS sent to Priya • Just now`
30. `Phone access granted • Today, 9:42 AM`

---

## 4. Patterns beyond the 30

### Onboarding welcome (the one screen allowed an exclamation)
- Headline: `Welcome to Mitra.`
- Subhead: `Your AI, on your phone.`
- CTA: `Get started`

> The exclamation is reserved here, used once on the Welcome screen, and only if A/B testing shows the subhead lands flat without it. Default: no exclamation anywhere.

### Privacy promise screen
- Headline: `Nothing leaves your phone.`
- Body: `Mitra runs entirely on this device. Your messages, your model, your audit log — all of it stays here. No accounts, no servers, no telemetry.`
- CTA: `Continue`
- Secondary: `Read the source →` (links to GitHub)

### "What Mitra cannot do" calibration
- Headline: `What Mitra cannot do (yet)`
- Body opener: `Mitra is honest about its limits. Today, Mitra cannot:`
  - `Send WhatsApp messages — only SMS through your default messaging app.`
  - `Control other apps' screens — only call its own tools.`
  - `Hear you yet — voice input arrives in a later update.`
- CTA: `All good — let's begin`

### Empty states
- Empty chat: `Try saying: "Turn on flashlight"` plus 3 suggestion chips.
- Empty audit history: `Nothing to log yet. Every action Mitra takes will appear here.`
- Empty settings search: `No match. Kindly try a shorter word.`

### Error patterns — structure
Every error follows: **\[what failed\]. \[why, if known\]. \[next step, if any\].**

Examples:
- `Could not set alarm. Clock app refused the request. Open Clock app yourself?`
- `Could not send SMS. No mobile network. Try again when signal returns.`
- `Model crashed mid-response. The chat is preserved. Send another message to retry.`

### Loading states — be specific
Don't write "Loading..." Write what is loading:
- `Loading model — about 3 seconds.`
- `Reading screen…`
- `Searching contacts for "Priya"…`
- `Setting alarm…`

### Suggestion chips on the home / chat screen
Format: imperative verb + object. Six examples, rotating:
- `Turn on flashlight`
- `Set an alarm for 7 AM`
- `Volume to 30%`
- `Open Calculator`
- `Battery level?`
- `What's playing?`

---

## 5. Localisation stance (R-003)

- **English V1**: ≥80% accuracy target across all tool calls. This file IS the V1 string set.
- **Hindi V1**: each English line above gets a Hindi (Devanagari) translation in a sibling file `voice-hi.md` before M5. Voice principles port; cultural markers ("kindly", "do let me know") map to natural Hindi register, not literal translations.
- **Telugu / Tamil beta**: shipped without parity guarantees. Fallback to English string is acceptable per R-003.
- **No language auto-detection from user input in V1.** Locale follows system setting only. Mixed-language input is replied to in the system locale's language.

### Hindi register guidance (for the future `voice-hi.md`)
- Use **तू / आप** consistently — recommend **आप** (formal/respectful, gender-neutral, fits "capable friend").
- Avoid **सर / मैडम** as a fallback; English's "no honorifics" rule applies.
- Avoid Sanskritised / formal Hindi (e.g., "कृपया प्रतीक्षा करें" instead of "एक सेकंड").
- Avoid heavily Urdu-leaning vocabulary in technical strings — keep it neutral-Hindustani.
- Festival greetings still forbidden in Hindi too.

---

## 6. Kotlin codification

Voice strings live in `ui/strings/Voice.kt`, keyed by `(toolName, outcome)` plus a small set of UI keys.

```kotlin
// ui/strings/Voice.kt
package com.mitra.ui.strings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

enum class Outcome { Success, PartialSuccess, MissingArg, PermissionNeeded, Unavailable, Failure }

data class VoiceKey(val tool: String, val outcome: Outcome)

object Voice {
    // Tool outcome lookup — populated from voice.md, expanded per tool
    private val table: Map<VoiceKey, Int> = mapOf(
        VoiceKey("flashlight_on", Outcome.Success) to R.string.flashlight_on_success,
        VoiceKey("alarm_set", Outcome.Success) to R.string.alarm_set_success,
        VoiceKey("alarm_set", Outcome.PermissionNeeded) to R.string.alarm_permission_needed,
        // … 1 row per (tool × outcome) pair
    )

    @Composable
    fun forTool(tool: String, outcome: Outcome, vararg args: Any): String {
        val resId = table[VoiceKey(tool, outcome)]
            ?: R.string.fallback_generic_failure
        return stringResource(resId, *args)
    }
}
```

`strings.xml` carries the actual translatable values (so Hindi / Tamil / Telugu can ship via standard Android resource folders `values-hi/`, `values-ta/`, `values-te/`).

A unit test enforces: every tool registered in `ToolRegistry` has a row in `Voice.table` for at least `Success` and `Failure`. Build fails if a new tool ships without its strings.

---

## 7. What this guide does NOT cover

- **Conversational LLM responses** — Gemma's free-form generation is bounded by the system prompt, not this file. The system prompt should reference this guide ("respond briefly, do not apologise, no exclamation marks") but final phrasing comes from the model.
- **Voice-mode TTS pacing** — arrives with M4.
- **Error stack traces / dev logs** — different audience, different register, lives in code.
