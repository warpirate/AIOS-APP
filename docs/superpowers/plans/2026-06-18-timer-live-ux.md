# Timer Live UX Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the audible-at-schedule timer notification + redundant in-app pill with a silent running notification, a one-shot alarm notification at fire, and a single live action card that ticks down inside the chat.

**Architecture:** Split the single `mitra.timers` notification channel into a low-importance silent running channel (count-down card) and a high-importance alarm channel (fire-time sound). Delete the floating pill above the input bar. Detect `start_timer` cards in `ActionCardView` and route to a new inline `LiveTimerCard` composable that observes `TimerStore.active`, renders a monospaced countdown + circular progress ring + Cancel button, and falls through to the static DONE card when `TimerStore.active` becomes null.

**Tech Stack:** Kotlin, Jetpack Compose Material3, Android NotificationManager / NotificationChannel API (≥26), `kotlinx.coroutines.flow.StateFlow` + `collectAsState`, `AlarmManager.ELAPSED_REALTIME_WAKEUP`.

**Spec:** [docs/superpowers/specs/2026-06-18-timer-live-ux-design.md](../specs/2026-06-18-timer-live-ux-design.md)

---

## Phase 1 — Fix the sound bug (channel split)

The single critical bug. Smallest possible diff that lands first so the user is never again jump-scared by the running notification.

### Task 1.1: Split `TimerReceiver` channel constants

**Files:**
- Modify: `app/src/main/kotlin/com/mitra/tools/TimerReceiver.kt`

- [ ] **Step 1: Replace single-channel constants with two-channel constants**

In `app/src/main/kotlin/com/mitra/tools/TimerReceiver.kt`, replace the `companion object` constants:

```kotlin
companion object {
    const val ACTION_FIRE = "com.mitra.tools.action.TIMER_FIRE"
    const val ACTION_CANCEL = "com.mitra.tools.action.TIMER_CANCEL"
    const val EXTRA_LABEL = "label"
    const val EXTRA_DURATION_SECONDS = "duration_seconds"

    // Two channels — see docs/superpowers/specs/2026-06-18-timer-live-ux-design.md.
    // Running posts at schedule time; silent so the user is not alerted twice.
    const val CHANNEL_RUNNING_ID = "mitra.timers.running"
    const val CHANNEL_RUNNING_NAME = "Timer running"
    const val CHANNEL_RUNNING_DESCRIPTION = "Silent ongoing notification while an in-app timer is counting down. No sound."

    // Alarm posts at fire time. THIS is what the user hears.
    const val CHANNEL_ALARM_ID = "mitra.timers.alarm"
    const val CHANNEL_ALARM_NAME = "Timer alarm"
    const val CHANNEL_ALARM_DESCRIPTION = "Plays when an in-app timer reaches zero. Default alarm sound + vibration."

    // Legacy id deleted at upgrade time — see MitraApp.ensureTimerNotificationChannels.
    const val LEGACY_CHANNEL_ID = "mitra.timers"
}
```

- [ ] **Step 2: Verify Kotlin compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL. `TimerNotifications` + `MitraApp` will likely fail to compile because they reference the removed `CHANNEL_ID` — that is fixed in Tasks 1.2 and 1.3 next. Note any errors but do not commit yet.

### Task 1.2: Route `TimerNotifications` postRunning/postDone to new channels

**Files:**
- Modify: `app/src/main/kotlin/com/mitra/tools/TimerNotifications.kt`

- [ ] **Step 1: Replace channel references in `postRunning` and `postDone`**

In `app/src/main/kotlin/com/mitra/tools/TimerNotifications.kt`, change the `Notification.Builder` channel argument in `postRunning`:

```kotlin
val notification =
    Notification.Builder(context, TimerReceiver.CHANNEL_RUNNING_ID)
        .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
        .setContentTitle(label)
        .setContentText("Timer running")
        .setUsesChronometer(true)
        .setChronometerCountDown(true)
        .setWhen(triggerWallClockMs)
        .setShowWhen(true)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setCategory(Notification.CATEGORY_PROGRESS)
        .setContentIntent(openAppPendingIntent(context))
        .addAction(cancelAction)
        .build()
```

And in `postDone`:

```kotlin
val notification =
    Notification.Builder(context, TimerReceiver.CHANNEL_ALARM_ID)
        .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
        .setContentTitle(label)
        .setContentText(formatDone(durationSeconds))
        .setCategory(Notification.CATEGORY_ALARM)
        .setAutoCancel(true)
        .setContentIntent(openAppPendingIntent(context))
        .build()
```

`NOTIFICATION_ID` stays the same — posting `postDone` while a running notification is on screen REPLACES it because Android keys notifications by `(pkg, id, tag)` not by channel. The replaced posting will play the alarm channel's sound because the new channel is HIGH importance.

- [ ] **Step 2: Verify compilation**

Run: `./gradlew :app:compileDebugKotlin`
Expected: still fails on `MitraApp.ensureTimerNotificationChannel` referencing the old `CHANNEL_ID`. Fixed in Task 1.3.

### Task 1.3: Create both channels + delete the legacy one in `MitraApp`

**Files:**
- Modify: `app/src/main/kotlin/com/mitra/MitraApp.kt`

- [ ] **Step 1: Replace `ensureTimerNotificationChannel` with `ensureTimerNotificationChannels`**

In `app/src/main/kotlin/com/mitra/MitraApp.kt`, replace the existing helper:

```kotlin
/** Two timer notification channels — silent running + audible alarm. See
 *  docs/superpowers/specs/2026-06-18-timer-live-ux-design.md.  Idempotent. */
private fun ensureTimerNotificationChannels() {
    val nm = getSystemService(NotificationManager::class.java) ?: return

    // Delete the legacy single-channel id from earlier builds. No-op on fresh installs.
    nm.deleteNotificationChannel(TimerReceiver.LEGACY_CHANNEL_ID)

    if (nm.getNotificationChannel(TimerReceiver.CHANNEL_RUNNING_ID) == null) {
        val running =
            NotificationChannel(
                TimerReceiver.CHANNEL_RUNNING_ID,
                TimerReceiver.CHANNEL_RUNNING_NAME,
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = TimerReceiver.CHANNEL_RUNNING_DESCRIPTION
                enableVibration(false)
                setSound(null, null)
                setShowBadge(false)
            }
        nm.createNotificationChannel(running)
    }

    if (nm.getNotificationChannel(TimerReceiver.CHANNEL_ALARM_ID) == null) {
        val alarm =
            NotificationChannel(
                TimerReceiver.CHANNEL_ALARM_ID,
                TimerReceiver.CHANNEL_ALARM_NAME,
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = TimerReceiver.CHANNEL_ALARM_DESCRIPTION
                enableVibration(true)
                setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
            }
        nm.createNotificationChannel(alarm)
    }
}
```

- [ ] **Step 2: Update the call in `onCreate`**

In the same file, in `onCreate`, replace:

```kotlin
ensureTimerNotificationChannel()
```

with:

```kotlin
ensureTimerNotificationChannels()
```

- [ ] **Step 3: Build the debug APK**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL. No references to the removed `CHANNEL_ID` constant remain.

- [ ] **Step 4: Commit Phase 1**

```bash
git add app/src/main/kotlin/com/mitra/tools/TimerReceiver.kt \
        app/src/main/kotlin/com/mitra/tools/TimerNotifications.kt \
        app/src/main/kotlin/com/mitra/MitraApp.kt \
        docs/superpowers/specs/2026-06-18-timer-live-ux-design.md \
        docs/superpowers/plans/2026-06-18-timer-live-ux.md
git commit -m "fix(timer): split notification channels so sound fires only at zero

The single mitra.timers channel was IMPORTANCE_HIGH with an alarm sound,
so the running countdown notification posted at schedule time triggered
the alarm sound IMMEDIATELY. Channels (not builder flags) control sound
on API 26+, so setOnlyAlertOnce didn't help.

Two channels now:
  - mitra.timers.running (LOW, silent) — count-down card in shade
  - mitra.timers.alarm   (HIGH, alarm) — one-shot at fire only

MitraApp.onCreate idempotently creates both and deletes the legacy
channel from earlier builds.

Spec: docs/superpowers/specs/2026-06-18-timer-live-ux-design.md
Plan: docs/superpowers/plans/2026-06-18-timer-live-ux.md"
```

---

## Phase 2 — Drop pill, make the action card the live surface

### Task 2.1: Remove the `ActiveTimerPill` composable and its insertion site

**Files:**
- Modify: `app/src/main/kotlin/com/mitra/ui/ChatScreen.kt`

- [ ] **Step 1: Delete the `ActiveTimerPill()` call from the chat layout**

In `app/src/main/kotlin/com/mitra/ui/ChatScreen.kt`, locate the line inside the chat `Column { ... }`:

```kotlin
ActiveTimerPill()
FloatingInputBar(value = input, onValueChange = { input = it }, onSend = { send() }, enabled = !busy)
```

Delete the `ActiveTimerPill()` line so only the input bar remains:

```kotlin
FloatingInputBar(value = input, onValueChange = { input = it }, onSend = { send() }, enabled = !busy)
```

- [ ] **Step 2: Delete the `ActiveTimerPill` composable function**

In the same file, locate the composable definition added on 2026-06-18 (search for `private fun ActiveTimerPill`) and delete the entire function block including its `@Composable` annotation and the KDoc comment above it.

- [ ] **Step 3: Verify compilation**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL. The `mutableLongStateOf` import may now be unused; leave it alone for this task — Task 2.2 will reuse it in `LiveTimerCard`.

### Task 2.2: Add the inline `LiveTimerCard` composable

**Files:**
- Modify: `app/src/main/kotlin/com/mitra/ui/ChatScreen.kt`

- [ ] **Step 1: Add required imports near the existing import block**

In `app/src/main/kotlin/com/mitra/ui/ChatScreen.kt`, ensure these imports exist (add any missing):

```kotlin
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Size as GeometrySize
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.IntSize
```

`mutableLongStateOf`, `collectAsState`, `TimerStore`, `MitraTimerScheduler`, `delay`, `LaunchedEffect`, `Modifier`, `Alignment`, `RoundedCornerShape`, `MaterialTheme`, `Text`, `Icon`, `Icons.Filled.Timer`, `Icons.Filled.Close`, `IconButton`, `Surface`, `Row`, `Column`, `Spacer`, `Box`, `FontWeight`, `BorderStroke`, `dp`, `size`, `padding`, `clip`, `CircleShape`, `fillMaxWidth` should already be present from earlier work.

- [ ] **Step 2: Define `LiveTimerCard` inline next to `ActionCardView`**

Append this composable just above `private fun ActionCardView(...)` (or just below, but keep the file's existing ordering style):

```kotlin
/**
 * Live count-down variant of the start_timer action card. Used when a tier-4 in-app timer
 * is active. Observes [TimerStore] so it auto-rebuilds when the timer is scheduled, fires,
 * or cancelled. When the store goes null, [ActionCardView] falls through to the static
 * DONE card instead of this composable.
 */
@Composable
private fun LiveTimerCard(
    card: ActionCard,
    timer: com.mitra.tools.ActiveTimer,
    onCancelTimer: (Int) -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var remainingMs by remember(timer.triggerAtElapsedRealtimeMs) {
        mutableLongStateOf(timer.remainingMs())
    }
    LaunchedEffect(timer.triggerAtElapsedRealtimeMs) {
        while (remainingMs > 0L) {
            remainingMs = timer.remainingMs()
            delay(250)
        }
    }
    val totalSec = (remainingMs / 1000).toInt().coerceAtLeast(0)
    val display = "%d:%02d".format(totalSec / 60, totalSec % 60)
    val progress =
        if (timer.totalSeconds <= 0) 0f
        else (remainingMs.toFloat() / (timer.totalSeconds * 1000f)).coerceIn(0f, 1f)
    val accent = MaterialTheme.colorScheme.primary

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.25f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(56.dp), contentAlignment = Alignment.Center) {
                    Canvas(modifier = Modifier.size(56.dp)) {
                        val stroke = 4.dp.toPx()
                        drawArc(
                            color = accent.copy(alpha = 0.18f),
                            startAngle = -90f,
                            sweepAngle = 360f,
                            useCenter = false,
                            topLeft = androidx.compose.ui.geometry.Offset(stroke / 2, stroke / 2),
                            size = GeometrySize(size.width - stroke, size.height - stroke),
                            style = Stroke(width = stroke, cap = StrokeCap.Round),
                        )
                        drawArc(
                            color = accent,
                            startAngle = -90f,
                            sweepAngle = 360f * progress,
                            useCenter = false,
                            topLeft = androidx.compose.ui.geometry.Offset(stroke / 2, stroke / 2),
                            size = GeometrySize(size.width - stroke, size.height - stroke),
                            style = Stroke(width = stroke, cap = StrokeCap.Round),
                        )
                    }
                    Icon(
                        Icons.Filled.Timer,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Spacer(Modifier.size(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        card.title.ifBlank { "Timer" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        display,
                        style = MaterialTheme.typography.headlineMedium,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                IconButton(onClick = { onCancelTimer(card.id) }) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "Cancel timer",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
```

- [ ] **Step 3: Verify compilation**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL. `LiveTimerCard` is defined but not yet routed.

### Task 2.3: Route `ActionCardView` to `LiveTimerCard` when a timer is live

**Files:**
- Modify: `app/src/main/kotlin/com/mitra/ui/ChatScreen.kt`

- [ ] **Step 1: Add an `onCancelTimer` callback parameter to `ActionCardView`**

Change the existing signature:

```kotlin
private fun ActionCardView(
    card: ActionCard,
    onConfirm: (Int) -> Unit,
    onCancel: (Int) -> Unit,
    onUndo: (Int) -> Unit,
)
```

to:

```kotlin
private fun ActionCardView(
    card: ActionCard,
    onConfirm: (Int) -> Unit,
    onCancel: (Int) -> Unit,
    onUndo: (Int) -> Unit,
    onCancelTimer: (Int) -> Unit,
)
```

- [ ] **Step 2: Branch at the top of `ActionCardView` to render `LiveTimerCard` when applicable**

Insert at the very top of the `ActionCardView` body, before the existing `val accent =` line:

```kotlin
val activeTimer by TimerStore.active.collectAsState()
val isLiveTimer =
    card.call?.name == "start_timer" &&
        card.state == ActionState.DONE &&
        activeTimer != null
if (isLiveTimer) {
    LiveTimerCard(card = card, timer = activeTimer!!, onCancelTimer = onCancelTimer)
    return
}
```

- [ ] **Step 3: Add the `cancelTimerCard` handler to the `ChatScreen` body**

Just below the existing `fun undoCard(id: Int)` block (around line ~330), add:

```kotlin
fun cancelTimerCard(id: Int) {
    val i = cardIndex(id)
    if (i < 0) return
    val card = items[i] as ActionCard
    // No CONFIRM-state guard here: a live timer card is always in DONE state and is owned by
    // the user, not the gate. MitraTimerScheduler.cancel tears down AlarmManager + TimerStore +
    // both notifications; the StateFlow update will re-route ActionCardView away from
    // LiveTimerCard automatically. We flip the card to CANCELLED so the static fall-through
    // surface reads "Cancelled" rather than "Done" (which would be misleading after a cancel).
    items[i] = card.copy(state = ActionState.CANCELLED, detail = "Cancelled")
    MitraTimerScheduler(context).cancel()
}
```

- [ ] **Step 4: Wire `cancelTimerCard` into the `ActionCardView` call site**

Locate the `itemsIndexed` block in the `LazyColumn` (the existing `ActionCardView(item, onConfirm = ::runCard, onCancel = ::cancelCard, onUndo = ::undoCard)` call). Change to:

```kotlin
is ActionCard ->
    ActionCardView(
        item,
        onConfirm = ::runCard,
        onCancel = ::cancelCard,
        onUndo = ::undoCard,
        onCancelTimer = ::cancelTimerCard,
    )
```

- [ ] **Step 5: Build the debug APK**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit Phase 2**

```bash
git add app/src/main/kotlin/com/mitra/ui/ChatScreen.kt
git commit -m "feat(timer): live action card replaces redundant pill

The start_timer action card now ticks down inside the chat: monospaced
M:SS countdown, circular progress ring around the clock icon, Cancel
button. Card observes TimerStore so it rebuilds on schedule / fire /
cancel and falls through to the static DONE / CANCELLED card when the
store goes null.

The floating ActiveTimerPill above the input bar is deleted — its
information is now in the card and the duplicate surface was muddy.

cancelTimerCard handler calls MitraTimerScheduler.cancel which clears
AlarmManager + TimerStore + both notifications.

Spec: docs/superpowers/specs/2026-06-18-timer-live-ux-design.md"
```

---

## Phase 3 — Manual on-device verification

No unit tests for this work (the surfaces are UI + system-notification driven; the meaningful test is the device walkthrough). Verification is the acceptance gate.

### Task 3.1: Walk the five scenarios on device

**Files:**
- Create: `docs/research/2026-06-18-timer-live-ux-manual-test.md`

- [ ] **Step 1: Install the APK on the connected device**

Run:

```bash
adb devices
adb -s <serial> install -r app/build/outputs/apk/debug/app-debug.apk
adb -s <serial> shell am force-stop com.mitra
adb -s <serial> shell am start -n com.mitra/.MainActivity
```

Expected: app launches; `Mitra ready · On-device — no data leaves` resident notification appears in the shade (verifies the unrelated resident service still works).

- [ ] **Step 2: Scenario 1 — 30 second timer, no sound at schedule**

In the chat, type or send `30 second timer`. Within 2 seconds of the action card appearing, listen for the alarm sound. The card should show a live `0:30` countdown ring; the shade should show a silent count-down card.

Pass criteria:
- No alarm sound plays at schedule time.
- Chat card displays monospaced countdown that ticks down each second.
- Notification shade card shows the system chronometer count-down icon (no audio cue when dragging the shade).
- Capture this with `adb -s <serial> shell dumpsys notification --noredact | grep -A5 "channel=mitra.timers"` — verify the running notification's channel string is `mitra.timers.running` and importance is `2` (LOW).

- [ ] **Step 3: Scenario 2 — timer hits zero, sound plays**

Wait the remaining time (~ 30 s). At zero:

Pass criteria:
- Default alarm sound plays.
- Device vibrates.
- Notification shade running card is replaced by an alarm-category card on `mitra.timers.alarm` (importance `4`).
- Chat card flips to static `Done` (no longer ticking, no progress ring, normal title/detail layout matches other completed cards).

- [ ] **Step 4: Scenario 3 — cancel from chat card mid-countdown**

Send `1 minute timer`. At ~ 0:50 remaining, tap the Cancel (close) icon on the chat card.

Pass criteria:
- No sound.
- Both shade notifications gone (running card removed, no alarm fires later).
- Chat card flips to `Cancelled` state with detail "Cancelled".
- `TimerStore.active` returns null (verify via the next scenario starting cleanly).

- [ ] **Step 5: Scenario 4 — cancel from notification shade Cancel action**

Send `1 minute timer`. Pull down the shade. Tap the `Cancel` button on the running notification.

Pass criteria:
- Same outcomes as Scenario 3: both notifications gone, no fire-time sound, chat card flips to `Cancelled`.

- [ ] **Step 6: Scenario 5 — app swipe survives mid-timer**

Send `45 second timer`. Swipe Mitra from recents. Wait the full duration.

Pass criteria:
- Alarm sound + vibration still play at zero (verifies the AlarmManager schedule survives independent of UI, which already worked pre-rewrite but should not have regressed).
- The resident-service notification keeps the process alive in the meantime.

- [ ] **Step 7: Record the test log**

Create `docs/research/2026-06-18-timer-live-ux-manual-test.md` with this template and fill in the actual observed outcomes per scenario:

```markdown
# Timer Live UX — Manual Test (2026-06-18)

**Device:** [model + Android version + ROM]
**Build:** `app-debug.apk` from commit `<sha>`
**Spec:** [docs/superpowers/specs/2026-06-18-timer-live-ux-design.md](../superpowers/specs/2026-06-18-timer-live-ux-design.md)

| # | Scenario | Pass? | Notes |
|---|----------|-------|-------|
| 1 | 30 s timer schedules silently | | |
| 2 | Timer hits zero, alarm sound + vibration plays | | |
| 3 | Cancel from chat card mid-countdown | | |
| 4 | Cancel from shade Cancel action | | |
| 5 | App swipe survives mid-timer | | |

## Verbatim adb output (Scenario 1 channel verification)

[paste output of dumpsys notification --noredact grep here]

## Open issues found during walk

[blank if none]
```

- [ ] **Step 8: Commit the manual-test log**

```bash
git add docs/research/2026-06-18-timer-live-ux-manual-test.md
git commit -m "docs(timer): manual test log for live-UX walkthrough on <device>

Records the 5-scenario walkthrough from the timer-live-ux plan.
All scenarios pass / fails noted in the table."
```

### Task 3.2: Update plan.md right-now status

**Files:**
- Modify: `plan.md`

- [ ] **Step 1: Append a follow-up note under right-now task #4**

In `plan.md`, find the existing right-now task #4 that documents the 2026-06-18 tier-4 ship. After the closing sentence, append:

```markdown
   - **Timer UX rewrite shipped 2026-06-18** (same day, after on-device feedback). Two-channel split (`mitra.timers.running` LOW silent / `mitra.timers.alarm` HIGH alarm sound) fixes the sound-fires-at-schedule bug. Floating pill deleted. `start_timer` action card now ticks down live inside the chat — monospaced `M:SS` + circular progress ring + Cancel button — and falls through to static DONE / CANCELLED when `TimerStore` clears. Spec [docs/superpowers/specs/2026-06-18-timer-live-ux-design.md](docs/superpowers/specs/2026-06-18-timer-live-ux-design.md); plan [docs/superpowers/plans/2026-06-18-timer-live-ux.md](docs/superpowers/plans/2026-06-18-timer-live-ux.md); manual test [docs/research/2026-06-18-timer-live-ux-manual-test.md](docs/research/2026-06-18-timer-live-ux-manual-test.md).
```

- [ ] **Step 2: Commit the plan update**

```bash
git add plan.md
git commit -m "docs(plan): tick timer live-UX rewrite under right-now task #4"
```

---

## Acceptance criteria (all must hold)

1. No alarm sound or vibration when a timer is scheduled.
2. Default alarm sound + vibration when a timer reaches zero.
3. `dumpsys notification` shows the running notification on `mitra.timers.running` (importance 2) at schedule time and the alarm notification on `mitra.timers.alarm` (importance 4) at fire time.
4. The chat `start_timer` action card ticks down second-by-second while the timer is live, with a visible circular progress ring.
5. Tapping the card's Cancel icon stops the timer, clears both notifications, and flips the card to `Cancelled`.
6. Tapping the running notification's Cancel action achieves the same outcome.
7. Swiping Mitra from recents does not stop the alarm from firing at the scheduled time.
8. The floating `ActiveTimerPill` no longer exists in the layout.
9. Manual-test log at `docs/research/2026-06-18-timer-live-ux-manual-test.md` is committed with all scenarios marked pass.
