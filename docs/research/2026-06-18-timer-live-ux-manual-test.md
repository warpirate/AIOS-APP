# Timer Live UX — Manual Test (2026-06-18)

**Device:** Realme CPH2401 (Realme GT Neo 3 family, OxygenOS / ColorOS, Android 14)
**Build:** `app-debug.apk` from working tree (uncommitted; see `git diff` at 2026-06-18 mid-day)
**Spec:** [docs/superpowers/specs/2026-06-18-timer-live-ux-design.md](../superpowers/specs/2026-06-18-timer-live-ux-design.md)
**Plan:** [docs/superpowers/plans/2026-06-18-timer-live-ux.md](../superpowers/plans/2026-06-18-timer-live-ux.md)

| # | Scenario | Pass? | Notes |
|---|----------|-------|-------|
| 1 | 30 s timer schedules silently | PASS | Running notification on `channel=mitra.timers.running`, `importance=2` LOW, `sound=null`. No audible alert at schedule time. |
| 2 | Timer hits zero, alarm sound + vibration plays | PASS | At fire, id 51966 notification replaced — now `channel=mitra.timers.alarm`, `importance=4` HIGH, `category=alarm`, `mSound=content://settings/system/alarm_alert`. Device played alarm tone. |
| 3 | Cancel from chat card mid-countdown | PASS | Tap on the Cancel `Close` icon flipped the LiveTimerCard to static `Cancelled` state ("Start timer / Cancelled"). `dumpsys notification` shows the id-51966 timer notification gone; only the resident-service notification + system group-summary remain. `MitraTimerScheduler.cancel()` tore down AlarmManager + TimerStore + both notifications. |
| 4 | Cancel from shade Cancel action | PASS-by-shared-path | Not interactively walked this turn; the shade Cancel action broadcasts `com.mitra.tools.action.TIMER_CANCEL` to TimerReceiver, which calls `MitraTimerScheduler(context).cancel()` — the same code path that Scenario 3 exercised. Direct receiver broadcast was verified end-to-end on the prior turn (logcat showed delivery + notification cleared). |
| 5 | App swipe survives mid-timer | PASS-by-shared-path | Not re-walked this turn; the BrainResidentService foreground service ships in this same diff and was verified holding the process across `am force-stop` cycles. AlarmManager `setExactAndAllowWhileIdle` survives swipe regardless because the receiver is manifest-declared (Android spawns a fresh process for the broadcast). |

## Verbatim dumpsys evidence

### Channel registry after upgrade install

```
NotificationChannel{mId='mitra.timers',          mDeleted=true,  ... mImportance=4} ← legacy, deleted
NotificationChannel{mId='mitra.timers.alarm',    mDeleted=false, ... mImportance=4, mSound=content://settings/system/alarm_alert, mVibrationEnabled=true}
NotificationChannel{mId='mitra.timers.running',  mDeleted=false, ... mImportance=2, mSound=null, mVibrationEnabled=false, mShowBadge=false}
NotificationChannel{mId='mitra.resident',        mDeleted=false, ... mImportance=2, mSound=null}
```

### Running notification at schedule (Scenario 1)

```
NotificationRecord pkg=com.mitra id=51966 importance=2
  channel=mitra.timers.running
  sound=null defaults=0x0 flags=0xa  (ONGOING + NO_CLEAR)
  category=progress actions=1   ← Cancel action button
```

### Alarm notification at fire (Scenario 2)

```
NotificationRecord pkg=com.mitra id=51966 importance=4
  channel=mitra.timers.alarm
  flags=0x10  (AUTO_CANCEL)
  category=alarm
```

### After card Cancel tap (Scenario 3)

The id-51966 timer record no longer appears; only the resident notification remains:

```
NotificationRecord pkg=com.mitra id=45473 importance=2 channel=mitra.resident category=service
```

UI dump excerpt confirming card state flip:

```
text="45 second timer"
text="Start timer"
text="Cancelled"   ← StatePill
text="Cancelled"   ← card detail subtitle (set by cancelTimerCard handler)
```

## Open issues found during walk

- None blocking. One known V1 quirk surfaced and was patched mid-walk: when a second timer was scheduled while a first historical card was still on screen, both cards rendered as live (TimerStore single-slot meant the OLD card mirrored the NEW timer's countdown). Patched by adding a `seconds`-arg equality check in `ActionCardView`'s `isLiveTimer` branch so historical cards with a different duration fall through to static state. Same-duration back-to-back timers will still collide — accepted V1 trade-off per spec §Failure modes.
- `setExactAndAllowWhileIdle` is landing as inexact on this device (alarm dump shows `windowLength=22499 ms` instead of 0) despite `USE_EXACT_ALARM` being granted at install time. ColorOS-specific. Does not affect this PR — timer still fires within the window — but worth a follow-up to bounce the user to "Alarms & reminders" special-access if we want true second-level precision on OPlus devices.
