# Application-Singleton Brain — Design

**Status:** Approved 2026-06-16 (brainstorm).
**Owner:** @warpirate
**Plan task:** plan.md right-now task #3 (M2 polish) — addresses the "first message takes 20+ seconds even after warmup" cold-start complaint logged on the dev Realme CPH2401.
**Scope tag:** P1. Foreground-Service hosting + system-prompt shrink are explicitly out of scope (separate tickets).

## TL;DR

Move `LiteRtBrain` construction from `MainActivity.onCreate` into a process-scoped singleton owned by a new `MitraApp : Application`. Drop the fake `conversation.sendMessageAsync("ok")` warmup — it ran a full prefill that the user's first real message paid *again* (LiteRT-LM doesn't reuse cross-call KV state the way we assumed), doubling the wait. With the singleton in place, the brain is constructed once per process lifetime; the first user message pays the prefill cost honestly *once*; every subsequent Activity restart in the same process is instant.

## Why

Symptom (logged on Realme CPH2401, Android 14, Gemma 4 E2B / LiteRT-LM 0.13.0 / CPU backend):

1. App launch → loading screen → permissions → chat.
2. Background warmup pill appears, sits ~10–15 seconds, disappears (`warmupComplete = true`).
3. User types `hi`, taps send.
4. Reply arrives ~20 seconds later.

Diagnosed cause: the sibling-conversation warmup compiled kernels + paged in model weights (good) but the **prefilled KV cache lived on the throwaway warmup `Conversation` instance, not the real `conversation`**. The real conversation's KV cache is empty until its first `sendMessageAsync` call, which prefills the full ~1.5k-token system prompt from cold. Net effect: we paid the prefill twice and the user saw both costs.

Architectural cause: `LiteRtBrain` is constructed inside `MainActivity.AppRoot` `LaunchedEffect(phase = LOADING)`. Activity recreation (rotation, theme change, user swiping back into the app after backgrounding) re-runs `AppRoot`, constructs a new brain, throws away the live one + its KV cache. Every cold relaunch within the same OS session pays the full prefill again.

Real fix: anchor the brain to the *process* (which survives Activity recreation), not the Activity. Stop the fake warmup. Surface the one honest prefill cost as a "Thinking…" indicator on the first message's streaming bubble.

## Architecture

One seam changes: **lifecycle**.

```
                ┌─────────────────────────────────┐
                │ MitraApp : Application          │
                │                                 │
                │  val brainHolder = BrainHolder( │
                │      ctx = this,                │
                │      scope = appScope,          │
                │      modelFile = …,             │
                │      cacheDir = …,              │
                │  )                              │
                │                                 │
                │  // onCreate kicks off construction
                │  // in background; survives Activity
                │  // recreation; killed only on
                │  // process death.              │
                └────────────┬────────────────────┘
                             │
                             │ (application as MitraApp).brainHolder
                             ▼
                ┌─────────────────────────────────┐
                │ MainActivity / AppRoot          │
                │                                 │
                │  brain = brainHolder.get()      │
                │  // suspends until construction │
                │  // completes; returns the same │
                │  // instance across every       │
                │  // Activity recreation.        │
                └─────────────────────────────────┘
```

`BrainHolder.get()` is the only public surface. It:
- Returns the cached `LiteRtBrain` instantly if construction succeeded.
- Suspends on the `Job` if construction is in flight.
- Returns `null` if construction failed (matches today's `brain == null` fallthrough to IntentParser-only mode).
- Never re-attempts construction within the same process lifetime (failure is sticky).

## Components

### New: `MitraApp.kt`

```kotlin
package com.mitra

import android.app.Application
import com.mitra.inference.BrainHolder
import com.mitra.inference.ModelRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import java.io.File

class MitraApp : Application() {
    lateinit var brainHolder: BrainHolder
        private set

    override fun onCreate() {
        super.onCreate()
        val scope = CoroutineScope(SupervisorJob())
        val modelFile = File(getExternalFilesDir(null), ModelRegistry.MODEL_FILE)
        brainHolder =
            BrainHolder(
                modelPath = modelFile.absolutePath,
                cacheDir = cacheDir.path,
                scope = scope,
            )
        // Eager prewarm ONLY if the model file is already on disk. On a fresh install the
        // file doesn't exist yet — calling prewarm now would let the factory fail, and
        // BrainHolder's failure is sticky for the process lifetime. AppRoot calls prewarm
        // itself once ModelDownloader confirms the download completes, so the cold-install
        // path still gets the eager-construction benefit on every launch AFTER the first.
        if (com.mitra.inference.ModelDownloader(modelFile).isComplete()) {
            brainHolder.prewarm()
        }
    }
}
```

Registered in `AndroidManifest.xml` via `android:name=".MitraApp"` on the `<application>` element.

### New: `inference/BrainHolder.kt`

```kotlin
package com.mitra.inference

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Process-lifetime singleton for the LiteRtBrain instance. Constructed lazily on first [get]
 * (or eagerly if MitraApp.onCreate decides to warm it). Survives Activity recreation; killed
 * only when the OS terminates the process. Failure is sticky for the lifetime of the process —
 * a corrupt model or OOM is not something we retry on a per-Activity basis.
 *
 * Test seam: the constructor takes a factory lambda so unit tests can inject a FakeBrain
 * without touching the real LiteRT-LM JNI surface (which doesn't load on the JVM).
 */
class BrainHolder(
    private val factory: () -> LiteRtBrain?,
    private val scope: CoroutineScope,
) {
    constructor(
        modelPath: String,
        cacheDir: String,
        scope: CoroutineScope,
    ) : this(
        factory = {
            runCatching { LiteRtBrain(modelPath, cacheDir) }.getOrNull()
        },
        scope = scope,
    )

    private val mutex = Mutex()
    private var deferred: CompletableDeferred<LiteRtBrain?>? = null

    /** Eagerly start construction. Safe to call from MitraApp.onCreate to overlap the work with
     *  the Activity boot path. Subsequent calls are no-ops. */
    fun prewarm() {
        scope.launch { ensureStarted() }
    }

    /** Returns the singleton brain, or null on construction failure. Suspends if construction is
     *  in flight. */
    suspend fun get(): LiteRtBrain? = ensureStarted().await()

    private suspend fun ensureStarted(): CompletableDeferred<LiteRtBrain?> {
        deferred?.let { return it }
        mutex.withLock {
            deferred?.let { return it }
            val d = CompletableDeferred<LiteRtBrain?>()
            deferred = d
            scope.launch(Dispatchers.IO) {
                d.complete(factory())
            }
            return d
        }
    }
}
```

### Modified: `MainActivity.kt`

- `onCreate` no longer constructs `LiteRtBrain`. Reads `(application as MitraApp).brainHolder`.
- `AppRoot` parameter `cacheDir: String` removed (unused once brain construction moves out).
- The `LaunchedEffect(phase)` block that loads the brain in `withContext(Dispatchers.IO) { LiteRtBrain(...) }` becomes `brain = (application as MitraApp).brainHolder.get()`.
- When `Phase.LOADING` first enters (i.e., the user just completed the model download on a fresh install OR returned to the app after the model was already present), call `brainHolder.prewarm()`. On launches AFTER the first download has completed, `MitraApp.onCreate` already prewarmed at process start so this call is a no-op.
- The `warmupScope.launch(Dispatchers.IO) { runCatching { b.warmup() } }` block deleted.

### Modified: `inference/LiteRtBrain.kt`

- `warmup()` method deleted.
- `warmupComplete: Boolean` field deleted.
- `systemInstruction` property hoisted in the previous warmup attempt stays (still cleaner shape; future per-conversation reuse remains an option).

### Modified: `ui/ChatScreen.kt`

- `isWarmingUp: () -> Boolean` constructor parameter removed.
- The polling `LaunchedEffect(Unit) { while (warming) { delay(400); warming = isWarmingUp() } }` removed.
- The `var warming by remember { mutableStateOf(isWarmingUp()) }` removed.
- The spinner pill above `FloatingInputBar` ("Warming up the brain — first message will take a few seconds.") removed.
- `FloatingInputBar(enabled = !busy && !warming)` reverts to `FloatingInputBar(enabled = !busy)`.
- Existing per-turn `busy` flag continues to gate send while a turn is mid-flight.

### Untouched

- `AgentRuntime`, `IntentParser`, `IntentParserPlanner`, `ToolRegistry`, every `Tool` impl, every `Tool` test, every existing `AgentRuntimeTest`/`AuditLogTest`/`GateCoverageTest`/`ManagerApiBackendTest`/`ModelDownloaderTest` scenario, every UI screen other than `ChatScreen`.

## Data flow

### Cold install (model not yet downloaded)

1. OS starts process → `MitraApp.onCreate` fires → `ModelDownloader.isComplete()` returns false → `brainHolder.prewarm()` is **NOT** called yet (factory would fail; failure is sticky).
2. `MainActivity.onCreate` → `AppRoot` → Welcome → PrivacyPromise → ModelDownload screens.
3. Download completes → phase transitions to `LOADING` → AppRoot's loading-phase `LaunchedEffect` calls `brainHolder.prewarm()` then `brainHolder.get()`. Construction begins now.
4. Construction completes (~5–10s on the dev Dimensity for engine init + paging the freshly-downloaded weights). `get()` resumes with the instance.
5. User reaches Chat, types `hi` → `AgentRuntime.run` → `brain.chatStream("hi")` → prefill of the system prompt (~10–15s) + decode (~1–3s). Single visible wait, surfaced as the existing "Mitra is thinking…" streaming-bubble shape (no new UI).
6. Subsequent turns reuse the prefilled KV cache. ~1–3s per turn from then on.

### Warm cold-start (process newly created, but model already on disk)

1. OS starts process → `MitraApp.onCreate` fires → `ModelDownloader.isComplete()` returns true → `brainHolder.prewarm()` launches construction on `Dispatchers.IO`.
2. `MainActivity.onCreate` runs in parallel. `AppRoot` skips Welcome/Download/Permissions (onboarding flags already set), enters LOADING, calls `brainHolder.get()` which suspends on the in-flight `Job`.
3. Construction completes (typically before or during the LOADING screen flash). `get()` resumes with the instance.
4. Same first-message prefill cost as the cold-install path (~10–15s). KV cache is fresh because this is a new process; nothing in the previous process survived.

### Hot relaunch (process alive)

Any path where the OS keeps the Mitra process alive but the user's Activity is destroyed and recreated — rotation, theme change, dark/light flip, swipe-up multitasking + reopen within minutes, configuration-change activity restart.

1. `MitraApp.onCreate` does NOT fire again (process unchanged).
2. New `MainActivity.onCreate` → `AppRoot` → `brainHolder.get()` returns the cached instance instantly.
3. Loading screen flashes briefly (existing code) then advances to chat.
4. The `conversation` object is still alive with whatever KV-cache state the previous Activity left it in. The first user message in the new Activity is fast.

### Process death

When the OS terminates the process (LMK under memory pressure, user force-stop, system reboot). Next launch follows the cold-install path. Chat history is in-memory only and is lost — that's the existing behavior, not a regression.

## Error handling

| Failure mode | Today | After this change |
|---|---|---|
| `LiteRtBrain(...)` constructor throws (corrupt model, OOM, JNI init failure) | `withContext(IO) { try { LiteRtBrain(...) } catch (t: Throwable) { null } }` → brain is null → AppRoot falls through to IntentParser-only chat mode | `BrainHolder` factory returns null → `get()` resolves to null → AppRoot takes the same fallthrough path. No new error UX. |
| `brainHolder.get()` called before `prewarm()` had a chance to run | Not applicable (no holder) | The first `get()` itself kicks off construction (`ensureStarted` is idempotent). Slight latency penalty if Activity wins the race; the user sees the existing LOADING screen until it resolves. |
| Activity backgrounded mid-construction | New Activity reconstructs from scratch | Construction continues in `MitraApp`'s `SupervisorJob` scope (not tied to Activity lifecycle). Next `get()` returns the completed instance. |
| Concurrent `get()` from rapid Activity recreation | Each construction races, wastes memory | `Mutex` + `CompletableDeferred` guarantee single construction; all callers await the same result. |
| Process death mid-chat | Chat history lost (in-memory only) | Same. Not addressed here. Persisted chat history is its own future ticket. |

## Tests

### Unit (`BrainHolderTest`)

Uses the `factory: () -> LiteRtBrain?` constructor seam so no LiteRT-LM JNI surface is touched.

Cases:
- **Single construction across concurrent calls:** launch 10 `async { holder.get() }`, assert the factory lambda was invoked exactly once.
- **Failure is sticky:** factory returns null on first call. Second `get()` does NOT re-invoke the factory; returns null directly.
- **`prewarm` does not block:** call `prewarm()`, immediately assert the call returned. Background construction proceeds.
- **`get` after `prewarm` returns the prewarmed instance:** call `prewarm()`, give the factory a chance to complete, call `get()`, assert it returns the instance without further factory calls.

These match the existing test convention (JUnit 4, runBlocking, no Robolectric, no Android Context). Lives at `app/src/test/kotlin/com/mitra/inference/BrainHolderTest.kt`.

### Manual on-device

Walked on the dev Realme CPH2401 (Android 14 / ColorOS) after install:

1. Fresh install, first launch, type `hi`: confirm reply lands in roughly the same ~15s envelope as today's "warmup + first send" combined (cold prefill is unavoidable; we just removed the duplication, not the physics).
2. Rotate device mid-chat: confirm chat history persists, no loading screen flash, next message ~1–3s.
3. Background app for 30s, reopen: confirm no reload, next message ~1–3s.
4. Force-stop via Settings → relaunch: confirm cold-path repeats correctly (~15s on first `hi`).
5. Send 5 messages back-to-back: confirm second through fifth are decode-speed (~1–3s each).

Logged as a follow-up in `docs/research/2026-06-16-application-singleton-manual-test.md` after the plan lands.

## Migration & rollback

- **Migration path:** no data migration; no schema change; no model re-download. Pure code path swap.
- **Rollback:** revert the `MitraApp` registration + the `BrainHolder` ctor wiring in `MainActivity`; restore the deleted `warmup()` + `warmupComplete` + ChatScreen pill code. Five-file revert. No persisted state to clean up.
- **Feature-flag:** none. Adding a flag would just create an untested code path — the simpler shape IS the new behavior. Per the Mitra no-over-engineering principle (`mitra-no-overengineering` memory).

## What this design does NOT do

- **No Foreground Service.** Brain dies on OS process kill. A `specialUse` FGS would survive longer (only killed under severe memory pressure), but it's its own ticket — needs the ongoing-notification UX from `docs/design/permissions.md`, the manifest entry, and a notification builder. Tracked separately in plan.md week-0 fact-correction #9.
- **No system-prompt shrink.** Approach B from the brainstorm would cut prefill cost across every cold start, not just hot relaunches, but it changes brain behavior in ways that need an eval harness to verify safely — and we don't have one yet (M3 work). Defer.
- **No KV-cache snapshot to disk.** LiteRT-LM 0.13 docs don't expose a way to serialize + restore prefilled cache. If it lands in a future runtime version, it's the silver bullet — but we don't depend on it.
- **No persisted chat history.** Process death still wipes the in-memory list. Separate ticket.
- **No fake warmup of any kind.** First user message IS the warmup. Honest single cost.

## Risks

- **`Application` subclass adds a process-lifetime singleton that holds 2.6 GB of model weights in JVM memory.** Manifested via mmap, not heap, so the headline figure isn't on the JVM heap — but if a future tool stashes references to large state on the same singleton, it can pin memory across configuration changes that today freely reclaim. Mitigation: keep `BrainHolder` doing only one thing (own the brain).
- **`Mutex` + `CompletableDeferred` is the wrong abstraction if more lifecycle nuance is ever needed** (e.g., explicit `reload()`, hot-swap of the model). Today's needs are smaller than that surface. If we ever need reload, swap in a more general primitive then. YAGNI for V1.
- **Removing the warming pill removes an observability signal.** A future regression where construction silently stalls would now be invisible until the user tries to send. Mitigation: the LOADING screen still appears on cold start, so a stuck construction is at least visible there.

## Open questions

None. Approach A was approved in the brainstorm session over Approach C (which would have added a system-prompt shrink) because the prompt shrink needs an eval harness we don't have. Approach B (singleton alone, without the FGS) is what this spec scopes.

## Done when

- All files listed under §Components are in their stated state on `main`.
- `BrainHolderTest` passes locally (`./gradlew :app:test`).
- Manual on-device test log filed at `docs/research/2026-06-16-application-singleton-manual-test.md` with results for all 5 scenarios above.
- `plan.md` M2 right-now task #3 ticked with "shipped 2026-06-16" + spec link.
- ARCHITECTURE.md's "Module breakdown" section gets a one-line update under `inference/` noting the `BrainHolder` singleton.
