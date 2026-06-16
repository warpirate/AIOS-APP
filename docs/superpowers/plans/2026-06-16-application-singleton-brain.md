# Application-Singleton Brain Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move `LiteRtBrain` construction from `MainActivity.onCreate` into a process-scoped `Application` singleton so the brain survives Activity recreation, and drop the fake `warmup()` that doubled the first-message cost.

**Architecture:** New `MitraApp : Application` holds a single `BrainHolder` instance. `BrainHolder` owns the `LiteRtBrain` reference, constructs it lazily in a background coroutine, suspends `get()` callers while construction is in flight, and treats failure as sticky for the process lifetime. `MitraApp.onCreate` calls `prewarm()` only if the model file is already on disk (sticky-failure consideration); `AppRoot` calls `prewarm()` itself when entering `Phase.LOADING` (covers the freshly-downloaded path). The `warmup()` method on `LiteRtBrain` and all UI plumbing for the "warming up" pill are deleted.

**Tech Stack:** Kotlin 2.2, Coroutines + Flow, AndroidX Compose, LiteRT-LM 0.13.0 (`com.google.ai.edge.litertlm`), JUnit 4. No new dependencies.

**Spec:** [`docs/superpowers/specs/2026-06-16-application-singleton-brain-design.md`](../specs/2026-06-16-application-singleton-brain-design.md).

---

## File Structure

**Create:**
- `app/src/main/kotlin/com/mitra/MitraApp.kt` — `Application` subclass, holds `brainHolder`.
- `app/src/main/kotlin/com/mitra/inference/BrainHolder.kt` — singleton wrapper, lazy construction, single-flight, sticky failure.
- `app/src/test/kotlin/com/mitra/inference/BrainHolderTest.kt` — unit tests via factory seam.
- `docs/research/2026-06-16-application-singleton-manual-test.md` — on-device walk log.

**Modify:**
- `app/src/main/AndroidManifest.xml` — add `android:name=".MitraApp"` on `<application>`.
- `app/src/main/kotlin/com/mitra/MainActivity.kt` — replace inline `LiteRtBrain(...)` construction with `brainHolder.get()`; remove `warmupScope`; add `prewarm()` on phase transition into LOADING.
- `app/src/main/kotlin/com/mitra/inference/LiteRtBrain.kt` — delete `fun warmup()` and `var warmupComplete`.
- `app/src/main/kotlin/com/mitra/ui/ChatScreen.kt` — remove `isWarmingUp` param, the `warming` state, the polling `LaunchedEffect`, the spinner pill, and the `!warming` send-disable guard.
- `plan.md` — tick the M2 cold-start sub-item.
- `ARCHITECTURE.md` — one-line note under `inference/` about the `BrainHolder` singleton.
- `docs/superpowers/specs/2026-06-16-application-singleton-brain-design.md` — add `Implementation status: shipped <date> in <sha-range>` footer.

**Delete:** none — every change is in-place or additive.

---

## Task 1: BrainHolder with sticky-failure single-flight construction

**Files:**
- Create: `app/src/main/kotlin/com/mitra/inference/BrainHolder.kt`
- Create: `app/src/test/kotlin/com/mitra/inference/BrainHolderTest.kt`

- [ ] **Step 1: Write the failing tests**

Create `app/src/test/kotlin/com/mitra/inference/BrainHolderTest.kt`:

```kotlin
package com.mitra.inference

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class BrainHolderTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @After fun tearDown() { scope.coroutineContext[kotlinx.coroutines.Job]?.cancel() }

    @Test
    fun `get returns the instance produced by the factory`() = runBlocking {
        val brain = fakeBrain()
        val holder = BrainHolder(factory = { brain }, scope = scope)
        assertSame(brain, holder.get())
    }

    @Test
    fun `factory is invoked exactly once across many concurrent get calls`() = runBlocking {
        val invocations = AtomicInteger(0)
        val brain = fakeBrain()
        val holder =
            BrainHolder(
                factory = {
                    invocations.incrementAndGet()
                    Thread.sleep(20) // simulate slow construction
                    brain
                },
                scope = scope,
            )
        val results = (1..10).map { async { holder.get() } }.awaitAll()
        assertEquals(1, invocations.get())
        results.forEach { assertSame(brain, it) }
    }

    @Test
    fun `failure is sticky - second get does not re-invoke the factory`() = runBlocking {
        val invocations = AtomicInteger(0)
        val holder =
            BrainHolder(
                factory = {
                    invocations.incrementAndGet()
                    null
                },
                scope = scope,
            )
        assertNull(holder.get())
        assertNull(holder.get())
        assertEquals(1, invocations.get())
    }

    @Test
    fun `prewarm starts construction without suspending the caller`() = runBlocking {
        val started = AtomicInteger(0)
        val brain = fakeBrain()
        val holder =
            BrainHolder(
                factory = {
                    started.incrementAndGet()
                    Thread.sleep(50)
                    brain
                },
                scope = scope,
            )
        // prewarm must NOT block; the subsequent get() should complete quickly because work is
        // already in flight.
        holder.prewarm()
        // Give the launched coroutine a tick to claim the slot.
        delay(5)
        withTimeout(500) { assertSame(brain, holder.get()) }
        assertEquals(1, started.get())
    }

    /** Returns a non-null sentinel without instantiating the real LiteRT-LM JNI surface (which
     *  cannot load on the JVM). Using `null as LiteRtBrain?` is awkward at the call sites; we
     *  instead use a Mockito-free sentinel via reflection-free unsafeCast trick. */
    private fun fakeBrain(): LiteRtBrain = SENTINEL

    companion object {
        // We can't construct a real LiteRtBrain on the JVM (engine.create throws). Tests only
        // care about referential identity, so a single shared sentinel cast via the JVM-level
        // type system is enough. unsafe cast is fine inside tests.
        @Suppress("UNCHECKED_CAST")
        private val SENTINEL: LiteRtBrain = Any() as LiteRtBrain
    }
}
```

- [ ] **Step 2: Confirm tests fail (BrainHolder doesn't exist yet)**

Run: `cd d:/AIOS/mitra && ./gradlew :app:compileDebugUnitTestKotlin 2>&1 | tail -10`
Expected: compilation FAILS with `Unresolved reference 'BrainHolder'`.

If anything else fails, stop and read.

- [ ] **Step 3: Write BrainHolder.kt**

Create `app/src/main/kotlin/com/mitra/inference/BrainHolder.kt`:

```kotlin
package com.mitra.inference

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Process-lifetime singleton for the [LiteRtBrain] instance. Constructed lazily on first [get]
 * (or eagerly via [prewarm] when the caller knows the construction prerequisites are met).
 * Survives Activity recreation; killed only when the OS terminates the process. Failure is
 * sticky for the lifetime of the process — a corrupt model, missing model file, or JNI init
 * failure is not something we retry on a per-Activity basis.
 *
 * Test seam: the constructor takes a `factory` lambda so unit tests can inject sentinel values
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
        factory = { runCatching { LiteRtBrain(modelPath, cacheDir) }.getOrNull() },
        scope = scope,
    )

    private val mutex = Mutex()
    @Volatile private var deferred: CompletableDeferred<LiteRtBrain?>? = null

    /** Eagerly start construction. Safe to call from MitraApp.onCreate or AppRoot's LOADING
     *  branch to overlap construction with the Activity boot path. Subsequent calls are
     *  no-ops because the [CompletableDeferred] is single-flight. */
    fun prewarm() {
        scope.launch { ensureStarted() }
    }

    /** Returns the singleton brain, or null on construction failure. Suspends if construction
     *  is in flight. */
    suspend fun get(): LiteRtBrain? = ensureStarted().await()

    private suspend fun ensureStarted(): CompletableDeferred<LiteRtBrain?> {
        deferred?.let { return it }
        return mutex.withLock {
            deferred?.let { return@withLock it }
            val d = CompletableDeferred<LiteRtBrain?>()
            deferred = d
            scope.launch(Dispatchers.IO) {
                d.complete(factory())
            }
            d
        }
    }
}
```

- [ ] **Step 4: Run tests, confirm pass**

Run: `cd d:/AIOS/mitra && ./gradlew :app:testDebugUnitTest --tests "com.mitra.inference.BrainHolderTest" 2>&1 | tail -10`
Expected: BUILD SUCCESSFUL, all 4 tests pass.

- [ ] **Step 5: Commit**

```
git add app/src/main/kotlin/com/mitra/inference/BrainHolder.kt app/src/test/kotlin/com/mitra/inference/BrainHolderTest.kt
git commit -m "feat(inference): process-scoped BrainHolder singleton

Lazy + single-flight construction of LiteRtBrain via a factory seam.
Failure is sticky for the process lifetime — matches the spec's intent
that a corrupt model isn't worth re-attempting per-Activity. 4 unit tests
cover single-flight, sticky failure, and prewarm-then-get semantics; the
LiteRtBrain itself stays JNI-only (no Robolectric dep added).

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

## Task 2: MitraApp Application subclass + manifest registration

**Files:**
- Create: `app/src/main/kotlin/com/mitra/MitraApp.kt`
- Modify: `app/src/main/AndroidManifest.xml`

- [ ] **Step 1: Write MitraApp.kt**

Create `app/src/main/kotlin/com/mitra/MitraApp.kt`:

```kotlin
package com.mitra

import android.app.Application
import com.mitra.inference.BrainHolder
import com.mitra.inference.ModelDownloader
import com.mitra.inference.ModelRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import java.io.File

/**
 * Process-lifetime container for the singleton [BrainHolder]. Activity recreation (rotation,
 * theme change, finish/relaunch, swipe-up + reopen within minutes) no longer reloads the
 * 2.6 GB Gemma 4 E2B model — the brain instance + its conversation KV cache stay alive for
 * as long as the OS keeps the process.
 *
 * Eager prewarm only happens when the model file is already on disk: on a fresh install the
 * file doesn't exist yet, the factory would fail, and [BrainHolder] treats failure as sticky.
 * For the freshly-downloaded path, AppRoot calls prewarm() itself once the user reaches
 * Phase.LOADING.
 */
class MitraApp : Application() {
    lateinit var brainHolder: BrainHolder
        private set

    override fun onCreate() {
        super.onCreate()
        val appScope = CoroutineScope(SupervisorJob())
        val modelFile = File(getExternalFilesDir(null), ModelRegistry.MODEL_FILE)
        brainHolder =
            BrainHolder(
                modelPath = modelFile.absolutePath,
                cacheDir = cacheDir.path,
                scope = appScope,
            )
        if (ModelDownloader(modelFile).isComplete()) {
            brainHolder.prewarm()
        }
    }
}
```

- [ ] **Step 2: Register in manifest**

Open `app/src/main/AndroidManifest.xml`. On the existing `<application ...>` opening tag, add the attribute `android:name=".MitraApp"`. Don't change any other attribute. The result should look like:

```xml
<application
    android:name=".MitraApp"
    android:allowBackup="true"
    ... (existing attributes unchanged)
    >
```

- [ ] **Step 3: Build to verify wiring**

Run: `cd d:/AIOS/mitra && ./gradlew :app:assembleDebug 2>&1 | tail -8`
Expected: BUILD SUCCESSFUL.

If the build fails with a manifest merger error, double-check the `android:name` attribute is well-formed and the package path matches (`.MitraApp` resolves to `com.mitra.MitraApp` via the manifest package declaration).

- [ ] **Step 4: Commit**

```
git add app/src/main/kotlin/com/mitra/MitraApp.kt app/src/main/AndroidManifest.xml
git commit -m "feat(app): MitraApp Application subclass holds the BrainHolder

Registered via android:name in the manifest. onCreate eagerly prewarms
when the model file is already on disk; cold-install path defers to
AppRoot's LOADING-phase prewarm call (Task 4).

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

## Task 3: Remove warmup() from LiteRtBrain

**Files:**
- Modify: `app/src/main/kotlin/com/mitra/inference/LiteRtBrain.kt`

- [ ] **Step 1: Locate the warmup-related code**

Run: `grep -n "warmup\|warmupComplete" d:/AIOS/mitra/app/src/main/kotlin/com/mitra/inference/LiteRtBrain.kt`

Expected output: lines for `@Volatile var warmupComplete: Boolean = false`, the doc-comment block above `suspend fun warmup()`, the body of `warmup()`, and the `warmupComplete = true` flip in its `finally` block.

- [ ] **Step 2: Delete the warmupComplete property**

Find this block (will be near line 238, right after the `conversation` property):

```kotlin
    @Volatile var warmupComplete: Boolean = false
        private set
```

Delete the entire block (two lines + the blank line above if it leaves a double blank).

- [ ] **Step 3: Delete the warmup() method and its docstring**

Find the `/** Silent background warmup. ... */` doc-comment block immediately followed by `suspend fun warmup() { ... }` (will be around lines 233–280 after Step 2). Delete the entire range including the doc-comment, the function signature, the body, and the closing brace. Do not leave trailing blank lines.

- [ ] **Step 4: Build to verify compile (will break callers)**

Run: `cd d:/AIOS/mitra && ./gradlew :app:compileDebugKotlin 2>&1 | tail -20`

Expected: BUILD FAILS with `Unresolved reference 'warmup'` (from MainActivity) and `Unresolved reference 'warmupComplete'` (from MainActivity's `isWarmingUp` lambda). Those callers are fixed in Tasks 4 and 5.

If anything else fails, stop and read.

- [ ] **Step 5: Commit (compile-broken intermediate)**

We deliberately leave the build red at this commit because the next two tasks fix the callers atomically. The diff is cleaner reviewed in three commits than in one.

```
git add app/src/main/kotlin/com/mitra/inference/LiteRtBrain.kt
git commit -m "refactor(inference): remove warmup() and warmupComplete

The sibling-conversation warmup prefilled the wrong KV cache, so the
user's first real message paid the full system-prompt prefill again.
Dropping the method outright. Callers (MainActivity, ChatScreen) are
fixed in the next two commits; this commit leaves the build red on
purpose so the cleanup reads as a sequence of focused diffs.

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

## Task 4: Wire MainActivity to read brain from MitraApp

**Files:**
- Modify: `app/src/main/kotlin/com/mitra/MainActivity.kt`

- [ ] **Step 1: Remove the inline brain construction from onCreate**

In `MainActivity.onCreate`, find this block:

```kotlin
        val audit = AuditLog()
        val context = TurnOnlyContextStore()
        // Read the user's confirmation aggressiveness per-step so a mid-conversation Settings
        // change takes effect on the next dispatch (vs. requiring an app restart).
        val requiresGate: (com.mitra.tools.SideEffect) -> Boolean = { side ->
            when (UserPrefs.confirmationMode(applicationContext)) {
                ConfirmationMode.STRICT -> side != com.mitra.tools.SideEffect.None
                ConfirmationMode.BALANCED -> side == com.mitra.tools.SideEffect.Irreversible
            }
        }
        // The runtime is constructed below once the brain is (or isn't) loaded — see AppRoot.
        val modelFile = File(applicationContext.getExternalFilesDir(null), ModelRegistry.MODEL_FILE)
        val cacheDir = applicationContext.cacheDir.path
```

Replace `val modelFile = ...` and `val cacheDir = ...` with a single reference to the singleton:

```kotlin
        val audit = AuditLog()
        val context = TurnOnlyContextStore()
        val requiresGate: (com.mitra.tools.SideEffect) -> Boolean = { side ->
            when (UserPrefs.confirmationMode(applicationContext)) {
                ConfirmationMode.STRICT -> side != com.mitra.tools.SideEffect.None
                ConfirmationMode.BALANCED -> side == com.mitra.tools.SideEffect.Irreversible
            }
        }
        val brainHolder = (application as MitraApp).brainHolder
        val modelFile = File(applicationContext.getExternalFilesDir(null), ModelRegistry.MODEL_FILE)
```

(Keep `modelFile` — `AppRoot` still uses it for the `ModelDownloader.isComplete()` initial-phase decision. Drop `cacheDir` entirely.)

- [ ] **Step 2: Update setContent { AppRoot(...) } call site**

Find the existing `AppRoot(modelFile = modelFile, cacheDir = cacheDir, ...)` call inside `setContent { MitraTheme { ... } }`. Replace with:

```kotlin
                AppRoot(
                    modelFile = modelFile,
                    brainHolder = brainHolder,
                    sideEffectOf = sideEffectOf,
                    auditEntries = { audit.entries() },
                    buildRuntime = { brain, _ ->
                        AgentRuntime(
                            brain = brain,
                            parser = IntentParser(),
                            sideEffectOf = sideEffectOf,
                            backends = listOf(backend),
                            context = context,
                            audit = audit,
                            requiresGate = requiresGate,
                        )
                    },
                )
```

Note: `isWarmingUp` is removed from the `ChatScreen` call in Task 5; do not add it here.

- [ ] **Step 3: Update AppRoot signature + body**

Find the `@Composable private fun AppRoot(...)` declaration. Replace its parameter list and the brain-loading `LaunchedEffect(phase)` block:

```kotlin
@Composable
private fun AppRoot(
    modelFile: File,
    brainHolder: com.mitra.inference.BrainHolder,
    sideEffectOf: (String) -> com.mitra.tools.SideEffect,
    auditEntries: () -> List<com.mitra.safety.AuditLog.Entry>,
    buildRuntime: (LiteRtBrain?, (String) -> Unit) -> AgentRuntime,
) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var phase by remember { mutableStateOf(Phase.BOOT) }
    var brain by remember { mutableStateOf<LiteRtBrain?>(null) }

    var downloaded by remember { mutableLongStateOf(0L) }
    var total by remember { mutableLongStateOf(0L) }
    var paused by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        phase = if (ModelDownloader(modelFile).isComplete()) Phase.LOADING else Phase.WELCOME
    }
```

Then replace the entire existing `LaunchedEffect(phase)` block that constructs the brain (currently runs `LiteRtBrain(modelFile.absolutePath, cacheDir)` and launches the warmup) with:

```kotlin
    LaunchedEffect(phase) {
        if (phase == Phase.LOADING) {
            // Cover the freshly-downloaded path: MitraApp.onCreate skipped its eager prewarm
            // because the model file didn't exist yet. Now it does, so kick construction off.
            // On launches AFTER the first download, prewarm is a no-op (single-flight).
            brainHolder.prewarm()
            brain = brainHolder.get()
            phase = if (Onboarding.isComplete(ctx)) Phase.CHAT else Phase.PERMISSIONS
        }
    }
```

Remove the now-unused `warmupScope = rememberCoroutineScope()` line at the top of `AppRoot` and the `withContext(Dispatchers.IO)` import if no other usage remains. Remove the `Dispatchers` import only if grep confirms zero remaining references in this file.

- [ ] **Step 4: Add the MitraApp import**

At the top of `MainActivity.kt`, add (alphabetised among the existing `import com.mitra.*` lines):

```kotlin
import com.mitra.MitraApp
```

Same package as the class itself, so technically optional, but explicit improves readability. (If your style enforces no-same-package imports, skip this step.)

- [ ] **Step 5: Build**

Run: `cd d:/AIOS/mitra && ./gradlew :app:compileDebugKotlin 2>&1 | tail -15`

Expected: BUILD FAILS with errors only in `ChatScreen.kt` referencing `isWarmingUp` / `warmupComplete`. Those are fixed in Task 5. No errors anywhere else.

- [ ] **Step 6: Commit (still build-broken — fixes in Task 5)**

```
git add app/src/main/kotlin/com/mitra/MainActivity.kt
git commit -m "refactor(app): MainActivity reads brain from MitraApp.brainHolder

AppRoot no longer constructs LiteRtBrain inline. Loading-phase
LaunchedEffect calls brainHolder.prewarm() + get() so the singleton
covers both the cold-install (download-then-construct) and warm-launch
(already-prewarmed-from-Application.onCreate) paths. Build still red on
ChatScreen's isWarmingUp param; fixed in the next commit.

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

## Task 5: Remove warmup UI from ChatScreen

**Files:**
- Modify: `app/src/main/kotlin/com/mitra/ui/ChatScreen.kt`

- [ ] **Step 1: Remove the isWarmingUp parameter**

Find the `ChatScreen` Composable signature:

```kotlin
@Composable
fun ChatScreen(
    brainReady: Boolean,
    isWarmingUp: () -> Boolean = { false },
    buildRuntime: (onChunk: (String) -> Unit) -> AgentRuntime,
    onOpenSettings: () -> Unit = {},
) {
```

Drop the `isWarmingUp` parameter:

```kotlin
@Composable
fun ChatScreen(
    brainReady: Boolean,
    buildRuntime: (onChunk: (String) -> Unit) -> AgentRuntime,
    onOpenSettings: () -> Unit = {},
) {
```

- [ ] **Step 2: Remove the warming state and polling LaunchedEffect**

Find this block (added during the previous warmup attempt, will be inside ChatScreen near the top of the body):

```kotlin
    // Poll the brain's warmup state until it completes. The flag is a Volatile var that Compose
    // can't subscribe to natively, so we mirror it into a State<Boolean> and stop polling once
    // it flips. 400ms is well under the cost of being wrong about the hint's visibility.
    var warming by remember { mutableStateOf(isWarmingUp()) }
    LaunchedEffect(Unit) {
        while (warming) {
            kotlinx.coroutines.delay(400)
            warming = isWarmingUp()
        }
    }
```

Delete it entirely.

- [ ] **Step 3: Remove the spinner pill above FloatingInputBar**

Find the `if (warming) { Row(...) { ... CircularProgressIndicator ... "Warming up the brain..." ... } }` block immediately before `FloatingInputBar(...)`. Delete the whole block.

- [ ] **Step 4: Restore FloatingInputBar's enabled prop**

Replace the multi-line `FloatingInputBar(... enabled = !busy && !warming)` call with the single-line form:

```kotlin
            FloatingInputBar(value = input, onValueChange = { input = it }, onSend = { send() }, enabled = !busy)
```

- [ ] **Step 5: Build**

Run: `cd d:/AIOS/mitra && ./gradlew :app:compileDebugKotlin 2>&1 | tail -8`

Expected: BUILD SUCCESSFUL. Warnings about deprecated `Icons.Filled.*` are pre-existing and OK.

- [ ] **Step 6: Run all tests**

Run: `cd d:/AIOS/mitra && ./gradlew :app:testDebugUnitTest 2>&1 | tail -10`

Expected: BUILD SUCCESSFUL. Includes BrainHolderTest (new) plus every previously-passing test (AgentRuntimeTest, AuditLogTest, ConfirmationGateTest, GateCoverageTest, IntentParserTest, IntentParserPlannerTest, ManagerApiBackendTest, ModelDownloaderTest, PlanTest, TurnOnlyContextStoreTest, AutomationBackendTest).

- [ ] **Step 7: Run assembleDebug as the final sanity check**

Run: `cd d:/AIOS/mitra && ./gradlew :app:assembleDebug 2>&1 | tail -8`

Expected: BUILD SUCCESSFUL.

- [ ] **Step 8: Commit**

```
git add app/src/main/kotlin/com/mitra/ui/ChatScreen.kt
git commit -m "refactor(ui): drop warming pill + polling now that warmup is gone

The fake warmup that this UI surfaced is removed in the prior commits;
its visible affordance (spinner pill + 400ms-poll LaunchedEffect +
isWarmingUp lambda) follows. Input bar reverts to !busy gating only.
First real message still pays the honest prefill cost but does so under
the existing 'Mitra is thinking…' streaming-bubble shape, not under a
duplicated warmup wait.

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

## Task 6: On-device verification + manual test log

**Files:**
- Create: `docs/research/2026-06-16-application-singleton-manual-test.md`

- [ ] **Step 1: Install on the connected device**

Run: `cd d:/AIOS/mitra && ./gradlew :app:installDebug 2>&1 | tail -5`
Expected: `Installed on 1 device.`

If no device is connected, run `adb devices` first and reconnect wireless debugging per the project's existing flow.

- [ ] **Step 2: Force-stop and relaunch to get a clean cold start**

Run: `adb shell am force-stop com.mitra && adb shell monkey -p com.mitra -c android.intent.category.LAUNCHER 1 2>&1 | tail -2`
Expected: `Events injected: 1`.

- [ ] **Step 3: Walk the 5 scenarios from the spec**

For each scenario below, **time the relevant wait with a stopwatch** (a watch app on a second device, or your laptop's clock — the order-of-magnitude is what matters, not millisecond precision). Note any unexpected behaviour.

| # | Scenario | Setup | Action | Expected |
|---|---|---|---|---|
| 1 | First cold cost is paid once | Fresh install (or force-stop). Wait for chat to appear. | Type `hi`, send. | Reply lands in ~10–20s. No "warming up" pill ever appears. |
| 2 | Rotation survives | After scenario 1, rotate device to landscape. | Wait for layout to settle. Type `hi` again. | Reply lands in ~1–3s. No loading screen flash. Chat history intact. |
| 3 | Background + reopen survives | After scenario 2, press home. Wait 30 sec. Reopen Mitra. | Type `hi`. | Reply lands in ~1–3s. No loading screen flash. |
| 4 | Force-stop costs the full prefill | Settings → Apps → Mitra → Force stop. Relaunch. Wait for chat. | Type `hi`. | Reply lands in ~10–20s again (cold prefill). |
| 5 | Successive messages stay fast | After scenario 1 or 4, send 5 messages back-to-back. | `flashlight on`, `flashlight off`, `set brightness to 30`, `set brightness to 80`, `hi`. | Each reply lands in ~1–3s. |

- [ ] **Step 4: Create the test log**

Create `docs/research/2026-06-16-application-singleton-manual-test.md` populated with your actual observations. Template:

```markdown
# Application-Singleton Brain — Manual Device Test (2026-06-16)

Device: <Realme CPH2401 / ColorOS Android 14>. Brain: Gemma 4 E2B (CPU).
Commit under test: `<short sha of last commit in this branch>`.

Spec: [docs/superpowers/specs/2026-06-16-application-singleton-brain-design.md](../superpowers/specs/2026-06-16-application-singleton-brain-design.md).

## 1. First cold cost paid once

Setup: <fresh install OR adb force-stop + relaunch>.
Action: typed `hi`, tapped send.
Measured wait to first reply: <X seconds>.
Warming pill appeared: <yes/no — expected no>.
Pass: <yes/no>.

## 2. Rotation survives

Setup: after scenario 1, rotated to landscape.
Action: typed `hi`.
Measured wait: <X seconds>.
Loading screen flashed: <yes/no — expected no>.
Chat history persisted: <yes/no — expected yes>.
Pass: <yes/no>.

## 3. Background + reopen survives

Setup: after scenario 2, pressed home, waited ~30s, reopened.
Action: typed `hi`.
Measured wait: <X seconds>.
Loading screen flashed: <yes/no — expected no>.
Pass: <yes/no>.

## 4. Force-stop costs the full prefill

Setup: Settings → Apps → Mitra → Force stop, relaunched.
Action: typed `hi`.
Measured wait: <X seconds>.
Pass: <yes/no — expected ~10–20s>.

## 5. Successive messages stay fast

Setup: after scenario 1 or 4.
Action: 5 back-to-back commands.
Measured per-message wait: <list>.
Pass: <yes/no — expected ~1–3s each>.

## Overall

<n>/5 pass. <Notes on any unexpected behaviour, e.g. AppRoot's loading-screen flash duration, any Activity-recreation regression with the action-card UI from earlier today.>
```

- [ ] **Step 5: Commit log**

```
git add docs/research/2026-06-16-application-singleton-manual-test.md
git commit -m "docs(research): manual on-device test log for BrainHolder

Walked the 5 spec scenarios on the dev device after the singleton landed.
Capture file-of-record for the perf claim in the design.

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

## Task 7: Doc sync — plan.md + ARCHITECTURE.md + spec footer

**Files:**
- Modify: `plan.md`
- Modify: `ARCHITECTURE.md`
- Modify: `docs/superpowers/specs/2026-06-16-application-singleton-brain-design.md`

Per the Keep-docs-honest rule in CLAUDE.md.

- [ ] **Step 1: Compute the commit range for this work**

Run: `cd d:/AIOS/mitra && git log --oneline -10`
Identify the first commit from Task 1 and the most recent commit from Task 6. Note them as `<first-sha>..<last-sha>` for the doc updates below. If the project's last `plan.md` "shipped <date>" entry mentions today's date, you can reuse `2026-06-16`.

- [ ] **Step 2: Update plan.md Right-now task #3**

In `plan.md`, find the existing item that begins `3. ~~**M2 safety landed:** debug-only history screen reading...~~`. Append a new sub-strike at the end of that bullet block:

```markdown
~~Cold-start lifecycle: Application-singleton brain to survive Activity recreation~~ Shipped 2026-06-16 in commits `<first-sha>..<last-sha>`. `MitraApp : Application` + `inference/BrainHolder` (4 unit tests, single-flight + sticky-failure semantics) replace the per-Activity `LiteRtBrain` construction in `MainActivity`. Fake-warmup conversation + warming-pill UI deleted. First user message pays the honest ~10–15s prefill cost exactly once per process lifetime; rotation / background / reopen reuse the cached KV state. Spec: [docs/superpowers/specs/2026-06-16-application-singleton-brain-design.md](docs/superpowers/specs/2026-06-16-application-singleton-brain-design.md). Manual test log: [docs/research/2026-06-16-application-singleton-manual-test.md](docs/research/2026-06-16-application-singleton-manual-test.md).
```

- [ ] **Step 3: Update ARCHITECTURE.md inference/ section**

In `ARCHITECTURE.md` find the `### `inference/`` heading. After the existing paragraph describing `LiteRtBrain` and `ModelDownloader`, insert a new line:

```markdown
A process-lifetime `BrainHolder` singleton (owned by `MitraApp : Application`) constructs and caches the `LiteRtBrain` so the 2.6 GB model is paged in once per process, not once per Activity. Activity recreation (rotation, theme change, background+reopen) reuses the same instance + its conversation KV cache.
```

- [ ] **Step 4: Stamp the spec footer**

In `docs/superpowers/specs/2026-06-16-application-singleton-brain-design.md`, find the existing `**Status:** Approved 2026-06-16 (brainstorm).` line near the top. Immediately after it, add:

```markdown
**Implementation status:** Shipped 2026-06-16 in commits `<first-sha>..<last-sha>`. Implementation plan: [docs/superpowers/plans/2026-06-16-application-singleton-brain.md](../plans/2026-06-16-application-singleton-brain.md). Manual test log: [docs/research/2026-06-16-application-singleton-manual-test.md](../../research/2026-06-16-application-singleton-manual-test.md).
```

- [ ] **Step 5: Commit**

```
git add plan.md ARCHITECTURE.md docs/superpowers/specs/2026-06-16-application-singleton-brain-design.md
git commit -m "docs: tick singleton-brain landing; stamp spec + arch doc

Per the Keep-docs-honest rule in CLAUDE.md.

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

## Self-Review Checklist

**Spec coverage:**

- §Architecture lifecycle change → Tasks 1, 2, 4.
- §Architecture warmup removal → Tasks 3, 5.
- §Components `MitraApp.kt` → Task 2.
- §Components `BrainHolder.kt` → Task 1.
- §Components `MainActivity.kt` modifications → Task 4.
- §Components `LiteRtBrain.kt` deletions → Task 3.
- §Components `ChatScreen.kt` deletions → Task 5.
- §Data flow cold-install / warm-cold-start / hot-relaunch → verified in Task 6 scenarios 1–4.
- §Error handling sticky failure / single-flight / mid-construction recreate → Task 1 tests + Task 4 wiring.
- §Tests `BrainHolderTest` 4 cases → Task 1.
- §Tests manual on-device 5 scenarios → Task 6.
- §Migration & rollback → no migration needed, no flag wiring, rollback is a five-file revert (matches Tasks 1–5 commit boundaries).
- §Done when bullets → all 5 bullets map to Tasks 1–7.

**Type consistency:**

- `BrainHolder.factory: () -> LiteRtBrain?` defined in Task 1 step 3, used identically in Task 1 step 1 tests.
- `BrainHolder.prewarm(): Unit` defined Task 1 step 3, called from `MitraApp.onCreate` (Task 2 step 1) and `AppRoot` LOADING branch (Task 4 step 3).
- `BrainHolder.get(): LiteRtBrain?` defined Task 1, called Task 4 step 3.
- `MitraApp.brainHolder: BrainHolder` exposed Task 2 step 1, accessed Task 4 step 1 (`(application as MitraApp).brainHolder`).
- `AppRoot` parameter list: `(modelFile, brainHolder, sideEffectOf, auditEntries, buildRuntime)` matches the call site in `setContent` (Task 4 step 2) and the declaration (Task 4 step 3).
- `ChatScreen` parameter list (after Task 5): `(brainReady, buildRuntime, onOpenSettings)` matches the call from `AppRoot.CHAT` branch (unchanged from current code — `isWarmingUp` was only added during the abandoned warmup-fix attempt and is being rolled back here).
- `LiteRtBrain.warmup()` / `LiteRtBrain.warmupComplete` removed Task 3 step 2–3; no remaining caller after Task 4 (MainActivity) and Task 5 (ChatScreen).

**No placeholders:** every step has either exact code or an exact command + expected output. No "TBD", no "add appropriate handling", no "fill in details". All file paths are absolute project-relative.

**Commit boundaries:** seven commits, each shippable as a focused diff. Tasks 3 + 4 intentionally leave the build red mid-sequence — flagged explicitly in their commit messages so the reviewer isn't surprised.

---

## Execution Handoff

Plan complete and saved to `docs/superpowers/plans/2026-06-16-application-singleton-brain.md`. Two execution options:

1. **Subagent-Driven** — dispatch a fresh subagent per task, two-stage review between tasks, fast iteration. Recommended for cross-cutting refactors like this one where each task touches a different layer.
2. **Inline Execution** — execute tasks in this session using executing-plans, batch execution with checkpoints. Simpler if you want to stay in the loop turn-by-turn.

Which approach?
