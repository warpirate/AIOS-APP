# Mitra Action Cards

The heart of the UX. The LLM emits a tool call → the UI renders it as an action card → the user sees what is happening and (sometimes) approves it before it runs. Every tool in the 25-tool surface (M1) and every tool added thereafter renders through this system.

**Three side-effect classes**, mapped to **three visual modes**, each with **five states**:

| Side effect | Visual mode | Behaviour |
|---|---|---|
| `None` | **Silent / ambient ribbon** | Auto-runs. Tiny inline ribbon in chat ("Flashlight on") fades after 2 sec. Lands in audit log. |
| `Reversible` | **Toast** | Auto-runs. Toast bar with action description + 3-second undo affordance. Tap toast to expand or undo. |
| `Irreversible` | **Modal** | Does NOT auto-run. Modal card asks for explicit confirm. Confirm button executes; cancel discards. |

> The `Reversible` "auto-run with undo" is a deliberate choice over "ask first". Confirmation fatigue is a real failure mode — if Mitra asks before setting an alarm, users learn to tap-through reflexively, and the gate stops working for the actions that actually need gating (the `Irreversible` ones). Reserve the modal for things that cannot be undone.

---

## 1. Anatomy

All three modes share the same anatomy, varying in chrome and prominence.

```
┌─────────────────────────────────────────────────────┐
│  [icon]   Title                              [state] │
│           Detail line (1 line, truncate)             │
│  ─────────────────────────────────────────────────   │
│           Status footer · timestamp                  │
│                                          [actions]   │
└─────────────────────────────────────────────────────┘
```

| Slot | Content | Token |
|---|---|---|
| `icon` | Tool family glyph (see icon assignments) | 24dp, tinted with `primary` |
| `Title` | Action statement ("Set alarm 7:00 AM") | `TitleL` |
| `state` | Tiny pill: CONFIRM / RUNNING / DONE / CANCELLED / FAILED | `Caption` |
| `Detail` | One-line context ("Tomorrow, weekday") | `Body`, `onSurfaceVariant` |
| `Status footer` | "Just now" / "1 sec ago" / "Permission needed" | `Caption`, `onSurfaceVariant` |
| `actions` | Mode-specific buttons | `Label` |

Card uses `radiusLg` (20dp), `elevSurface` (no shadow, outline border), `surface` background.

---

## 2. The three modes — visual spec

### Mode A — Silent / Ambient ribbon (SideEffect.None)

Renders as a slim, full-width ribbon in the chat stream. Not a card — a stripe.

```
┌─────────────────────────────────────────────────────┐
│  ✓  Flashlight on                       · just now  │
└─────────────────────────────────────────────────────┘
```

- **Width**: matches chat message width.
- **Height**: 36dp.
- **Background**: `surfaceVariant`.
- **Radius**: `radiusMd` (12dp).
- **Border**: none.
- **Lifetime**: appears with a 120ms fade-in (motionFast / easingDecelerate), holds for 2.0 sec, fades out over 220ms. Replaced in the chat scroll by a compacted line ("Flashlight on · 2 sec ago") that persists as a regular chat-stream entry.
- **Tap behaviour**: tapping during the 2 sec window expands to a Toast variant with the undo option (if the underlying tool is undoable). After fade, tapping the compacted line opens the audit log entry.

Used for: flashlight, volume nudge, brightness nudge, DND toggle, screen rotation lock, opening an app (the app appears — the action is self-evident).

### Mode B — Toast (SideEffect.Reversible)

Renders as a card with a 3-second auto-undo affordance.

```
┌─────────────────────────────────────────────────────┐
│  🕐  Alarm set for 7:00 AM tomorrow                 │
│      Weekday only                                    │
│  ─────────────────────────────────────────────────   │
│                                Just now    [Undo]    │
└─────────────────────────────────────────────────────┘
```

- **Width**: matches chat message width.
- **Padding**: `lg` (20dp) horizontal, `md` (16dp) vertical.
- **Background**: `surface`.
- **Border**: 1dp `outline`.
- **Radius**: `radiusLg` (20dp).
- **Undo countdown**: a thin 1dp progress line at the card's bottom edge drains over 3 seconds; tapping Undo within the window reverses the action and emits a follow-up audit entry "Alarm unset (undo)".
- **After 3 sec**: Undo button fades out; card persists in chat as the canonical record of that action. Card is no longer interactive after the window.
- **Lifetime**: card stays in the chat stream forever (it IS the message Mitra returned).

Used for: setting alarms, setting timers, sending SMS where the platform supports send-cancellation (none currently — see note), creating calendar events, opening a settings page (tapping back undoes the visual navigation but state is unchanged — these are effectively reversible).

> Note on SMS: Android does not let an app cancel a sent SMS. SMS is therefore NOT classified as Reversible — it is Irreversible and uses Mode C.

### Mode C — Modal (SideEffect.Irreversible)

Renders as a modal bottom sheet that locks the chat until resolved. The ConfirmationGate. This is the R-006 backstop.

```
        ┌───────────────────────────────────────────┐
        │  ✉  Send SMS                              │
        │     To Priya — "Running late"             │
        │                                            │
        │     This sends an SMS. SMS counts toward  │
        │     your carrier plan.                     │
        │                                            │
        │  ─────────────────────────────────────    │
        │                                            │
        │            [Cancel]      [Send]            │
        └───────────────────────────────────────────┘
```

- **Surface**: `ModalBottomSheet` with `radiusLg` top-only.
- **Background**: `surface`.
- **Padding**: `xl` (24dp).
- **Scrim**: 48% black behind the sheet; tap-outside cancels the action only when in `confirm` state.
- **Buttons**: two buttons, full-width, equal weight. Confirm is `primary` filled. For delete-class actions Confirm is `danger` filled. Cancel is text-only secondary.
- **Lifetime**: persists until user resolves; cannot be dismissed by back-press while in `running` state.
- **After resolution**: dismisses, leaves behind a Toast-variant card in chat that records what happened.

Used for: sending SMS, placing a call, deleting alarms, deleting audit history, granting battery-exemption, any tool a future contributor marks `Irreversible`.

---

## 3. The fifteen variants — Mode × State

Five states are universal across all three modes. The state pill in the top-right of each card carries the current state.

| State | Pill colour | Pill text | Behaviour |
|---|---|---|---|
| `CONFIRM` | `outline` background, `onSurfaceVariant` text | "Confirm" | Mode C only. User has not yet tapped. |
| `RUNNING` | `info` background, `onPrimary` text | "Running" | Spinner replaces icon. Buttons disabled. |
| `DONE` | `success` background, `onPrimary` text | "Done" | Icon turns to checkmark. `hapticConfirm` fires. |
| `CANCELLED` | `outline` background, `onSurfaceVariant` text | "Cancelled" | Icon turns to small dash. No haptic. |
| `FAILED` | `danger` background, `onPrimary` text | "Failed" | Icon turns to `!` glyph. `hapticFail` fires. Recovery affordance appears. |

### Variant table

| | Silent (A) | Toast (B) | Modal (C) |
|---|---|---|---|
| **CONFIRM** | N/A — no confirm state (auto-runs) | N/A — no confirm state (auto-runs, undo instead) | Visible. Two buttons. |
| **RUNNING** | Spinner inline; ribbon background unchanged; <300ms typical so often skipped visually | Spinner replaces icon; button area shows "Running…" with no Undo yet | Buttons disabled; primary becomes spinner with text "Running…" |
| **DONE** | Checkmark icon + "Done" pill; fades after 2 sec | Checkmark + "Done" pill; Undo timer starts | Modal animates out over 220ms with `hapticConfirm`; Toast variant card remains in chat |
| **CANCELLED** | Not reachable (no cancel affordance) | Reached via Undo tap; card is replaced by a strikethrough variant of itself | Cancel tap dismisses modal; brief Toast-style "Cancelled" card lands in chat |
| **FAILED** | Ribbon becomes `danger` background; persists 4 sec (longer than success); compacts to "Failed: \[reason\]" in stream | Card switches to FAILED state; Undo replaced by Retry / Edit / Dismiss | Modal switches to FAILED state in place; CTA changes to Try again + Cancel |

---

## 4. Icon assignment per tool family

Use Material Symbols (Outlined weight, Grade 0, Optical Size 24). All icons render at 24dp, tinted `primary` for CONFIRM / RUNNING / DONE, `danger` for FAILED.

| Family | Tools | Material Symbol |
|---|---|---|
| **Light** | `flashlight_on`, `flashlight_off` | `flashlight_on` / `flashlight_off` |
| **Clock / time** | `set_alarm`, `set_timer`, `start_stopwatch`, `list_alarms`, `delete_alarm` | `alarm` (alarms), `timer` (timers), `timer_play` (stopwatch) |
| **Audio volume** | `volume_media`, `volume_ringer`, `volume_notification`, `mute_all` | `volume_up` / `volume_off` |
| **Display brightness** | `brightness_set`, `brightness_auto` | `brightness_6` |
| **Telephony / call** | `call_contact`, `call_number` | `call` |
| **Messaging** | `send_sms` | `sms` (outlined) |
| **App launch** | `open_app`, `list_installed_apps` | `apps` |
| **System settings** | `open_settings_page`, `toggle_dnd`, `toggle_rotation_lock`, `toggle_hotspot` | `settings` |

A `ToolIconRegistry` maps `toolName → Symbol`. New tools fail CI if they don't register an icon.

---

## 5. "Don't ask again for 5 minutes" affordance

Applies to Mode C (Modal) only. The user can suppress the confirmation gate for a repeating action within the same conversational session.

- **Where it lives**: a small inline checkbox on the modal, **below** the body description, **above** the buttons. Default OFF.
- **Label**: `Don't ask again for this action — next 5 min`
- **Scope**: keyed by `(toolName, exact argument signature)`. "Send SMS to Priya" suppresses repeated "Send SMS to Priya" confirms but does NOT suppress "Send SMS to Rahul".
- **Lifetime**: **session-only, in-memory.** No DataStore, no disk write, no persistence. The privacy invariant — "never persist a 'user said yes' beyond the live process" — is non-negotiable. App kill resets every suppression.
- **Audit**: each suppressed auto-run still writes a full audit entry with a `suppressedConsent = true` flag, so the user can audit what they pre-approved.
- **Visual on subsequent runs**: when a suppressed action fires, it renders as a **Toast variant (Mode B)** instead of skipping the UI entirely. The user sees that the action ran, with an Undo. So suppression downgrades Modal → Toast for the window, never to Silent.
- **Surfaced state**: a small inline chip in the chat ("3 actions auto-approved this session") appears when ≥1 suppression is active. Tap chip → expanded list with a "Re-enable confirmation" CTA per item.

---

## 6. Failed-state recovery affordances

When a tool fails, three recovery actions are offered in priority order. Show only the ones that apply.

| Action | When shown | Behaviour |
|---|---|---|
| **Try again** | Transient-class failure (network, race, busy resource) | Re-runs the exact same tool call. Card transitions back to RUNNING. |
| **Edit** | The cause was a bad argument (missing contact, invalid time, malformed input) | Opens an inline editor over the card with the offending field highlighted; user adjusts; Confirm re-submits as a fresh tool call (Mode resets to CONFIRM if originally Irreversible). |
| **Dismiss** | Always available | Closes the failed card. Audit entry remains. No retry. |

### Failure classifications

Each tool declares its failure types in its registry entry. Mitra-core resolves the classification and picks the recovery affordances per card.

| Class | Examples | Affordances shown |
|---|---|---|
| `Transient` | network down, file busy, runtime stalled | Try again, Dismiss |
| `BadArgument` | contact not found, time in the past, app not installed | Edit, Dismiss |
| `PermissionRevoked` | system permission was revoked mid-session | Open settings, Dismiss |
| `Unsupported` | tool exists but the device cannot perform it (no SIM, no clock app) | Dismiss only (no false hope) |
| `Unknown` | unclassified failure | Try again, Dismiss |

The error copy on the card always follows the voice.md error pattern: `[what failed]. [why, if known]. [next step].`

---

## 7. Visual styling constraints

Hard rules — these will get proposed and the answer needs to be no:

- **No gradients.** Solid `primary` / `danger` / `surface` fills only.
- **No glow / outer-glow / box-shadow halos** on RUNNING state. The state pill changes; that's enough.
- **No bouncing spring physics** on card entry. Cards fade-in + slide-up 4dp over `motionBase`.
- **No skeleton shimmer** during RUNNING. The pill says "Running" — that is the loading affordance.
- **No emoji in titles or details.** The icon slot carries the symbol; titles are plain text.
- **No coloured background fills on the whole card** for state. Only the pill changes colour. The card itself stays `surface`.
- **No state transitions that move the card's position in the list.** State changes mutate the card in place; the chat stream order is determined by emission time, never re-sorted.

---

## 8. Kotlin codification

```kotlin
// ui/components/ActionCard.kt
package com.mitra.ui.components

import androidx.compose.runtime.Composable

enum class SideEffect { None, Reversible, Irreversible }
enum class ActionState { CONFIRM, RUNNING, DONE, CANCELLED, FAILED }
enum class FailureClass { Transient, BadArgument, PermissionRevoked, Unsupported, Unknown }

data class ActionCardModel(
    val toolName: String,           // canonical tool id, drives icon lookup
    val title: String,              // "Set alarm 7:00 AM" — from Voice.kt
    val detail: String?,            // "Tomorrow, weekday"
    val sideEffect: SideEffect,
    val state: ActionState,
    val failureClass: FailureClass? = null,
    val failureMessage: String? = null,
    val timestampMs: Long,
    val onConfirm: (() -> Unit)? = null,    // Mode C only
    val onCancel: (() -> Unit)? = null,
    val onUndo: (() -> Unit)? = null,       // Mode B, within 3-sec window
    val onRetry: (() -> Unit)? = null,      // FAILED state
    val onEdit: (() -> Unit)? = null,       // FAILED + BadArgument
    val suppressedConsent: Boolean = false, // true if this auto-ran via 5-min suppression
)

@Composable
fun ActionCard(model: ActionCardModel) = when (model.sideEffect) {
    SideEffect.None -> SilentRibbon(model)
    SideEffect.Reversible -> ToastCard(model)
    SideEffect.Irreversible -> ModalCard(model)
}
```

Each of `SilentRibbon`, `ToastCard`, `ModalCard` is its own Composable in the same package. They share a `BaseActionCardScaffold` for the anatomy slots (icon / title / detail / pill / status / actions) but compose differently.

The `(toolName, ActionState) → title-string` lookup is owned by Voice.kt (see voice.md), not by these Composables. Cards take strings, not keys.

---

## 9. Tests every new tool ships with

When a contributor adds a tool, CI requires:

1. **Voice strings**: at least `Success`, `Failure` for the tool registered in Voice.kt.
2. **Icon mapping**: `ToolIconRegistry` has the tool's icon.
3. **SideEffect classification**: an explicit declaration, default `None` is not allowed — must be opted into.
4. **Failure classes**: at least one (`Transient` or `Unsupported`) declared.
5. **A unit test**: renders the card in all five states reachable for its side-effect mode; asserts the state pill text, the action affordances present, and the audit-log shape written.

These checks live in `core/tools/ToolRegistryTest.kt` and run on every PR.
