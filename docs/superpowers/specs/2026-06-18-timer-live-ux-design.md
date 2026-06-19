# Mitra Timer — Live UX (P1)

**Status:** Approved 2026-06-18 (chat brainstorm).
**Plan task:** plan.md right-now #4 follow-up — the 2026-06-18 tier-4 ship made the timer functional but the UX was rated "sucks" on first device walkthrough. This rewrites the timer surfaces to feel awesome.
**Owner:** @warpirate
**Scope tag:** P1.

## TL;DR

The 2026-06-18 tier-4 fallback fires correctly but the user-visible surfaces are wrong:

1. The `mitra.timers` notification channel is `IMPORTANCE_HIGH` w/ alarm sound, so the *running* countdown notification posted at schedule time triggers the alarm sound **immediately**. The intent was for the sound to play only when the timer reaches zero.
2. The chat shows two redundant timer surfaces — an action card that flips to "Done" the moment the timer is *scheduled* (factually wrong), plus a floating pill above the input bar that duplicates the card's information in an ugly muted-purple chip.
3. The action card is a one-shot result, not a live status. Users expect the card they got for asking "30 second timer" to **be** the timer.

This spec splits the notification channel in two, drops the floating pill, and turns the start_timer action card into the single live countdown surface.

## Why

The Realme CPH2401 walkthrough on 2026-06-18 produced this verbatim feedback:

> "the timer ive started, and the notification i see and if i set timer for 30 seconds, it says timer for 30 secs and it goes from 30-0, the moment 30 started, it started making sound like the timer has run to 0. and the ux in the app sucks"

Decoded:
- **Bug**: alarm sound played at schedule time, not at fire time.
- **UX**: the card showing "Done" while the timer is still counting down + a duplicate floating pill in jarring purple = "sucks".

The 2026-06-18 ship was a permission-denial fix (tier-1/2/3 rejected by OPlus DeskClock requiring `com.android.alarm.permission.SET_ALARM`). It put the fallback in place but the surfaces were treated as utility scaffolding. They are not — they are the *only* user-visible feedback that the timer is real. They must feel polished.

## Architecture

### Notification channels

Replace the single `mitra.timers` channel with two:

| Channel | Importance | Sound | Vibration | Purpose |
|---------|------------|-------|-----------|---------|
| `mitra.timers.running` | LOW | none | none | Ongoing count-down card in shade. Posted at schedule time, replaced or cancelled at fire/cancel. Stays out of the user's way. |
| `mitra.timers.alarm` | HIGH | default alarm (`RingtoneManager.TYPE_ALARM`) | yes | One-shot alarm-category notification at fire time. THIS is what the user hears. |

`mitra.timers` (legacy) is deleted in `MitraApp.onCreate` (idempotent — `nm.deleteNotificationChannel` no-ops if absent).

`TimerNotifications.postRunning` targets the running channel; `postDone` targets the alarm channel.

### Drop the floating pill

`ChatScreen.ActiveTimerPill` is deleted. The corresponding insertion site between LazyColumn and FloatingInputBar reverts. `TimerStore` stays — the **action card** observes it instead.

### Live action card

The `ActionCard` data class in `ChatScreen.kt` already has the state machine `CONFIRM → RUNNING → DONE` (plus CANCELLED / FAILED). Today the timer card hits DONE the moment `StartTimer.execute` returns (within ~ms of schedule).

New behaviour:
- When `ActionCardView` renders an entry whose `call.name == "start_timer"` AND `TimerStore.active != null`, it renders a `LiveTimerCard` instead of the static DONE card. The live variant:
  - Title: `Timer` (or label if non-default)
  - Big countdown `M:SS` rendered in `MaterialTheme.typography.headlineMedium` with `FontFamily.Monospace` so digit columns don't jitter as the seconds tick
  - Circular progress ring around a clock icon, animated from full (at schedule time) to empty (at fire). Anti-aliased; updates at the same 250 ms cadence as the text.
  - `Cancel` button (right side), full-bleed touch target ≥ 48 dp. Replaces the static Done chip.
  - Card chrome matches existing ActionCardView paddings + corners — only the inner content changes.
- When `TimerStore.active` becomes `null` (fire or cancel), the card transitions:
  - **Fire**: card flips to static DONE state w/ checkmark + `Done` chip + body `Timer finished` (using the same `formatDone` string used by the alarm notification).
  - **Cancel**: card flips to static CANCELLED state.

Identifying which card owns the currently-active timer: any DONE/RUNNING `start_timer` card observes `TimerStore.active`. If active timer's `triggerAtElapsedRealtimeMs > now`, the card renders live. Single-slot semantics mean only one timer is ever active, so multiple historical cards never compete. The match is "is there an active timer at all" — not by args. Acceptable trade-off given V1 single-slot.

### Component-level summary

| File | Change |
|------|--------|
| `tools/TimerReceiver.kt` | Replace `CHANNEL_ID` constants. Define `CHANNEL_RUNNING_ID = "mitra.timers.running"` (+ name/description) and `CHANNEL_ALARM_ID = "mitra.timers.alarm"`. Delete `CHANNEL_ID`. |
| `tools/TimerNotifications.kt` | `postRunning` → builds on `CHANNEL_RUNNING_ID`. `postDone` → builds on `CHANNEL_ALARM_ID`. Same `NOTIFICATION_ID` so the alarm notif replaces the running one in the shade. |
| `tools/MitraTimerScheduler.kt` | No change. Still calls `TimerNotifications.postRunning` after scheduling. |
| `MitraApp.kt` | `ensureTimerNotificationChannel` → `ensureTimerNotificationChannels` (plural). Creates both channels w/ correct sound/importance. Deletes the legacy `mitra.timers` channel idempotently. |
| `ui/ChatScreen.kt` | Delete `ActiveTimerPill` composable + the call site. `ActionCardView` detects `start_timer` + active timer and routes to a new local `LiveTimerCard` composable (defined inline in the same file alongside `ActionCardView`, mirroring how the existing card variants live there). |

### Data flow

```
User: "30 second timer"
  → IntentParser → start_timer(seconds=30)
  → ChatScreen adds ActionCard(state=CONFIRM)            ┐
  → Reversible auto-runs                                  │  (already today)
  → StartTimer.execute() → tier 4 → MitraTimerScheduler   │
        → AlarmManager.setExactAndAllowWhileIdle          │
        → TimerStore.set(ActiveTimer{30s, ...})           │
        → TimerNotifications.postRunning(running channel) ┘  ← silent now
  → ActionCard state := DONE                              ┐
  → ActionCardView checks TimerStore.active               │  NEW
  → renders LiveTimerCard (countdown + ring + Cancel)     │
  → 250 ms tick from LaunchedEffect tracks remaining      │
  → at t=fire:                                            │
        AlarmManager broadcasts TIMER_FIRE                │
        TimerReceiver clears TimerStore                   │
        TimerNotifications.postDone(alarm channel)        ┘  ← sound + vibe now
  → ActionCardView re-renders (StateFlow recompose)
  → TimerStore.active == null → static DONE card
```

### Cancel flow

- Cancel tap on live card → `MitraTimerScheduler(context).cancel()` → clears alarm, store, both notifications.
- Cancel tap on running shade notification → `ACTION_CANCEL` broadcast → `TimerReceiver` → same `MitraTimerScheduler.cancel()`.
- `ChatScreen` observes `TimerStore.active` going null → live card flips to CANCELLED.

## Failure modes

| Failure | Behaviour |
|---------|-----------|
| AlarmManager unavailable (rare) | `MitraTimerScheduler.schedule` returns `Failure`; card stays static-DONE w/ "Couldn't start a timer on this device". No store entry, no live card. |
| POST_NOTIFICATIONS denied on API 33+ | Notifications silently drop; in-chat live card still works because it reads `TimerStore` directly. Degraded but functional. |
| User force-stops Mitra mid-timer | AlarmManager broadcast still arrives at TimerReceiver (manifest receiver auto-spawns process), TimerStore re-initialises to null (fresh process), alarm notification still posts. Live card is gone with the chat history; user only gets the notification — same as any other backgrounded experience. |
| Two timers in quick succession | Single-slot: second `schedule()` overwrites first. The OLD chat card (now historical) is logically stale — it observes `TimerStore.active` which now reflects the NEW timer. Acceptable: the old card visibly shows the second timer's countdown (misleading per-card identity but UX-wise points at the live state). Documented as a known V1 quirk; revisited if user feedback flags it. |

## Tests

### Unit

- `TimerNotifications` — verify `postRunning` uses running channel id, `postDone` uses alarm channel id, both reuse `NOTIFICATION_ID` (replace, not stack). Use a fake `NotificationManager` mock or a `RobolectricTest` style around Notification.Builder; if too heavy, skip — the channel routing is one-line and visually verified.
- `MitraApp.ensureTimerNotificationChannels` — verify both channels created w/ correct importance after a fresh call; second call is idempotent.

### Manual on-device

Re-walk the same Realme CPH2401 path:

1. "30 second timer" → verify no sound at schedule; in-chat card shows live countdown; shade card shows count-down icon w/o sound. **Critical pass criterion.**
2. Wait 30s → alarm sound + vibration plays; shade running notif replaced w/ alarm notif; in-chat card flips to static Done.
3. "1 minute timer" then tap chat-card Cancel at ~10s remaining → both notifications gone; card flips to CANCELLED.
4. "1 minute timer" then tap shade Cancel action → same as above.
5. Start timer; swipe Mitra from recents; wait → resident service holds process, alarm still posts. (Already tested separately, smoke check.)

Recorded as a quick log appended to `docs/research/2026-06-18-timer-live-ux-manual-test.md` (created during implementation).

## Migration & rollback

No feature flag. The simpler, sound-correct behaviour replaces the old one. Rollback = revert the diff; old single-channel behaviour returns w/ the audible bug.

Legacy channel cleanup: `MitraApp.onCreate` deletes `mitra.timers` on first run after upgrade. Users get a one-time channel reshuffle in their notification settings (the running + alarm channels appear under "Timers" group implicitly via name prefix).

## Risks

- **`NotificationManager.deleteNotificationChannel` on a fresh install no-ops** — verified behaviour, safe.
- **Channel importance is sticky per-user** — if a user manually bumped `mitra.timers.running` to HIGH before realising it was the silent one, their device will be noisy. Default-LOW on creation is all we can do. Documented in notification settings description.
- **Recomposition cost of 250 ms ticks** — only the live card recomposes; LazyColumn item key keeps siblings stable. Negligible.

## Open questions

None. The brainstorm closed on the three-point fix above; no Q1/Q2/Q3 open.

## What this design does NOT do

- Multi-timer support — single-slot stays. P2 if asked.
- Pausable / resumable timer — not on V1.
- Per-card identity match (which card owns which timer) — V1 single-slot makes this moot.
- Timer editing on the card — out of scope; cancel + re-prompt is the V1 path.
- Replacing `start_timer`'s underlying AlarmManager path — that part of the 2026-06-18 ship is correct; this rewrites only the surfaces.
